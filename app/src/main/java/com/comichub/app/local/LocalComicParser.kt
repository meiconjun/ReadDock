package com.comichub.app.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

enum class LocalComicFormat(val label: String) {
    IMAGE("图片"),
    PDF("PDF"),
    EPUB("EPUB"),
    MOBI("MOBI"),
    CBZ("CBZ")
}

sealed class LocalPageDescriptor {
    data class Image(val relativePath: String) : LocalPageDescriptor()
    data class Text(val relativePath: String) : LocalPageDescriptor()
    data class Pdf(val relativePath: String, val pageIndex: Int) : LocalPageDescriptor()
}

data class ParsedLocalComic(
    val format: LocalComicFormat,
    val pageCount: Int,
    val coverRelativePath: String?,
    val fileSize: Long,
    val pages: List<LocalPageDescriptor>
)

class LocalComicParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses local books entirely from app-private files.  The parser writes a
 * small manifest and extracted EPUB/MOBI/CBZ page resources, while PDFs stay
 * as PDFs and are rendered one page at a time by the reader.
 */
class LocalComicParser {
    fun parse(
        workDir: File,
        inputFiles: List<File>,
        format: LocalComicFormat
    ): ParsedLocalComic {
        return try {
            require(inputFiles.isNotEmpty()) { "没有可读取的本地文件" }
            workDir.mkdirs()
            val pages = when (format) {
                LocalComicFormat.IMAGE -> parseImages(workDir, inputFiles)
                LocalComicFormat.PDF -> parsePdf(workDir, inputFiles.single())
                LocalComicFormat.EPUB -> parseEpub(workDir, inputFiles.single())
                LocalComicFormat.MOBI -> parseMobi(workDir, inputFiles.single())
                LocalComicFormat.CBZ -> parseZip(workDir, inputFiles.single())
            }
            if (pages.isEmpty()) throw LocalComicParseException("文件中没有可读取的页面")
            writeManifest(workDir, pages)
            ParsedLocalComic(
                format = format,
                pageCount = pages.size,
                coverRelativePath = pages.first().file?.takeIf { it.name == "cover.png" }
                    ?.let { relativePath(workDir, it) }
                    ?: pages.first().coverPath(),
                fileSize = inputFiles.sumOf(File::length),
                pages = pages.map { it.descriptor }
            )
        } catch (error: LocalComicParseException) {
            throw error
        } catch (error: Throwable) {
            throw LocalComicParseException(
                "${format.label} 文件损坏或无法解析：${error.message ?: "未知错误"}",
                error
            )
        }
    }

    fun readManifest(directory: File): List<LocalPageDescriptor> {
        val manifest = File(directory, MANIFEST_NAME)
        if (!manifest.isFile) throw LocalComicParseException("本地漫画索引缺失，请重新导入")
        return try {
            manifest.readLines(StandardCharsets.UTF_8).mapIndexedNotNull { lineNumber, line ->
                val parts = line.split('|')
                when (parts.firstOrNull()) {
                    "IMAGE" -> LocalPageDescriptor.Image(requireRelative(parts, 1, lineNumber))
                    "TEXT" -> LocalPageDescriptor.Text(requireRelative(parts, 1, lineNumber))
                    "PDF" -> LocalPageDescriptor.Pdf(
                        requireRelative(parts, 1, lineNumber),
                        parts.getOrNull(2)?.toIntOrNull()
                            ?: throw LocalComicParseException("本地漫画索引第 ${lineNumber + 1} 行页码无效")
                    )
                    else -> throw LocalComicParseException("本地漫画索引第 ${lineNumber + 1} 行格式无效")
                }
            }
        } catch (error: LocalComicParseException) {
            throw error
        } catch (error: Throwable) {
            throw LocalComicParseException("本地漫画索引损坏：${error.message ?: "未知错误"}", error)
        }
    }

    private fun parseImages(workDir: File, inputFiles: List<File>): List<WrittenPage> {
        val pagesDir = File(workDir, "pages").apply { mkdirs() }
        val sorted = inputFiles.sortedWith(naturalFileComparator)
        val readable = sorted.mapIndexedNotNull { index, file ->
            val target = File(pagesDir, "%05d_%s".format(Locale.ROOT, index + 1, safeName(file.name)))
            file.copyTo(target, overwrite = true)
            // Bounds-only decode avoids loading all pages and still verifies that
            // at least one page is a real, decodable image.
            if (isReadableImage(target)) {
                WrittenPage(LocalPageDescriptor.Image(relativePath(workDir, target)), target)
            } else {
                null
            }
        }
        if (readable.isEmpty()) throw LocalComicParseException("没有可读取的图片页面，图片可能已损坏")
        return readable
    }

    private fun parsePdf(workDir: File, input: File): List<WrittenPage> {
        val target = File(workDir, "source.pdf")
        input.copyTo(target, overwrite = true)
        val descriptor = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw LocalComicParseException("PDF 无法打开或已损坏：${error.message ?: "未知错误"}", error)
        }
        try {
            if (renderer.pageCount <= 0) throw LocalComicParseException("PDF 没有可读取的页面")
            val cover = renderPdfPage(renderer, 0, File(workDir, "cover.png"))
            return (0 until renderer.pageCount).map { page ->
                WrittenPage(LocalPageDescriptor.Pdf("source.pdf", page), if (page == 0) cover else null)
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private fun renderPdfPage(renderer: PdfRenderer, pageIndex: Int, output: File): File {
        val page = renderer.openPage(pageIndex)
        try {
            val scale = minOf(1f, PDF_PREVIEW_WIDTH.toFloat() / page.width.coerceAtLeast(1))
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                FileOutputStream(output).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)) {
                        "PDF 封面写入失败"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            return output
        } finally {
            page.close()
        }
    }

    private fun parseEpub(workDir: File, input: File): List<WrittenPage> {
        val pagesDir = File(workDir, "pages").apply { mkdirs() }
        ZipFile(input).use { zip ->
            val container = zip.readTextEntry("META-INF/container.xml")
                ?: throw LocalComicParseException("EPUB 缺少 META-INF/container.xml")
            val packagePath = attr(
                Regex("<rootfile\\b[^>]*>", RegexOption.IGNORE_CASE).find(container)?.value,
                "full-path"
            ) ?: throw LocalComicParseException("EPUB 根文件路径无效")
            val packageEntry = zip.getEntry(packagePath)
                ?: throw LocalComicParseException("EPUB 根文件不存在：$packagePath")
            val packageXml = zip.getInputStream(packageEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val packageDir = packagePath.substringBeforeLast('/', "")
            val manifest = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
                .findAll(packageXml)
                .mapNotNull { tag ->
                    val value = tag.value
                    val id = attr(value, "id") ?: return@mapNotNull null
                    val href = attr(value, "href") ?: return@mapNotNull null
                    id to (normalizeZipPath(packageDir, href) to (attr(value, "media-type") ?: ""))
                }.toMap()
            if (manifest.isEmpty()) throw LocalComicParseException("EPUB manifest 为空或非法")
            val spineIds = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE)
                .findAll(packageXml)
                .mapNotNull { attr(it.value, "idref") }
                .toList()
            if (spineIds.isEmpty()) throw LocalComicParseException("EPUB spine 为空或非法")

            val pages = mutableListOf<WrittenPage>()
            var pageNumber = 0
            spineIds.forEach { id ->
                val (entryPath, mediaType) = manifest[id]
                    ?: throw LocalComicParseException("EPUB spine 引用了缺失的 manifest：$id")
                val entry = zip.getEntry(entryPath)
                    ?: throw LocalComicParseException("EPUB 资源不存在：$entryPath")
                val xhtml = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val imageRefs = Regex(
                    "<(?:img|image)\\b[^>]*(?:src|href|xlink:href|data-src)=['\"]([^'\"]+)['\"]",
                    setOf(RegexOption.IGNORE_CASE)
                ).findAll(xhtml).map { it.groupValues[1] }.toList()
                var addedImage = false
                imageRefs.forEach { ref ->
                    val resourcePath = normalizeZipPath(entryPath.substringBeforeLast('/', ""), ref)
                    val resourceEntry = zip.getEntry(resourcePath) ?: return@forEach
                    val extension = extensionOf(resourcePath)
                    if (!isImageExtension(extension)) return@forEach
                    val target = File(pagesDir, "%05d.%s".format(Locale.ROOT, ++pageNumber, extension))
                    copyZipEntry(resourceEntry, zip, target)
                    pages += WrittenPage(LocalPageDescriptor.Image(relativePath(workDir, target)), target)
                    addedImage = true
                }
                if (!addedImage) {
                    val text = stripMarkup(xhtml).trim()
                    if (text.isNotEmpty() && mediaType.contains("html", ignoreCase = true)) {
                        val target = File(pagesDir, "%05d.txt".format(Locale.ROOT, ++pageNumber))
                        target.writeText(text, StandardCharsets.UTF_8)
                        pages += WrittenPage(LocalPageDescriptor.Text(relativePath(workDir, target)), target)
                    }
                }
            }
            if (pages.isEmpty()) throw LocalComicParseException("EPUB 没有可读取的图片或文字页面")
            return pages
        }
    }

    private fun parseZip(workDir: File, input: File): List<WrittenPage> {
        val pagesDir = File(workDir, "pages").apply { mkdirs() }
        ZipFile(input).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { entry ->
                    entry.name.split('/').none { it.startsWith('.') } &&
                        isImageExtension(extensionOf(entry.name))
                }
                .sortedWith(compareBy(naturalPathComparator) { it.name })
                .toList()
            if (entries.isEmpty()) throw LocalComicParseException("压缩包中没有可读取的图片")
            return entries.mapIndexedNotNull { index, entry ->
                val extension = extensionOf(entry.name)
                val target = File(pagesDir, "%05d.%s".format(Locale.ROOT, index + 1, extension))
                copyZipEntry(entry, zip, target)
                if (isReadableImage(target)) {
                    WrittenPage(LocalPageDescriptor.Image(relativePath(workDir, target)), target)
                } else {
                    null
                }
            }.also { pages ->
                if (pages.isEmpty()) throw LocalComicParseException("压缩包中的图片全部损坏")
            }
        }
    }

    private fun parseMobi(workDir: File, input: File): List<WrittenPage> {
        val pagesDir = File(workDir, "pages").apply { mkdirs() }
        RandomAccessFile(input, "r").use { file ->
            if (file.length() < PALM_HEADER_SIZE) throw LocalComicParseException("MOBI 文件头不完整")
            val recordCount = readU16(file, 76)
            if (recordCount < 2) throw LocalComicParseException("MOBI 没有正文记录")
            val offsets = (0 until recordCount).map { index -> readU32(file, 78 + index * 8).toLong() }
            val firstRecord = readRecord(file, offsets, 0)
            val mobiOffset = indexOf(firstRecord, byteArrayOf('M'.code.toByte(), 'O'.code.toByte(), 'B'.code.toByte(), 'I'.code.toByte()))
            if (mobiOffset < 0) throw LocalComicParseException("不是有效的 MOBI 文件")
            val compression = readU16(firstRecord, 0)
            val textLength = readU32(firstRecord, 4).toInt()
            val textRecordCount = readU16(firstRecord, 8)
            val encryption = readU16(firstRecord, 12)
            if (encryption != 0) throw LocalComicParseException("加密 MOBI 暂不支持")
            val firstImageRecord = readU32(firstRecord, mobiOffset + 0x6c).toInt()
            val text = ByteArrayOutputStreamCompat()
            for (index in 1..textRecordCount) {
                if (index >= offsets.size) break
                val bytes = readRecord(file, offsets, index)
                text.write(if (compression == 1) bytes else if (compression == 2) palmDocDecompress(bytes) else {
                    throw LocalComicParseException("MOBI 使用了不支持的压缩格式")
                })
            }
            val html = decodeText(text.toByteArray().copyOf(textLength.coerceAtMost(text.size)))
            val imageRecords = Regex("(?:recindex|record|image)[=: ]+['\"]?(\\d+)", RegexOption.IGNORE_CASE)
                .findAll(html)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .map { value -> if (value >= firstImageRecord) value else firstImageRecord + value }
                .distinct()
                .filter { it in offsets.indices }
                .toList()
            val candidates = if (imageRecords.isNotEmpty()) imageRecords else {
                (firstImageRecord.coerceAtLeast(1) until offsets.size).toList()
            }
            val pages = mutableListOf<WrittenPage>()
            candidates.forEachIndexed { index, recordIndex ->
                val bytes = readRecord(file, offsets, recordIndex)
                val extension = imageExtension(bytes) ?: return@forEachIndexed
                val target = File(pagesDir, "%05d.%s".format(Locale.ROOT, pages.size + 1, extension))
                target.writeBytes(bytes)
                pages += WrittenPage(LocalPageDescriptor.Image(relativePath(workDir, target)), target)
            }
            if (pages.isEmpty()) {
                val textContent = stripMarkup(html).trim()
                if (textContent.isNotEmpty()) {
                    val target = File(pagesDir, "00001.txt")
                    target.writeText(textContent, StandardCharsets.UTF_8)
                    pages += WrittenPage(LocalPageDescriptor.Text(relativePath(workDir, target)), target)
                }
            }
            if (pages.isEmpty()) throw LocalComicParseException("MOBI 中没有可读取的图片或文字页面")
            return pages
        }
    }

    private fun writeManifest(workDir: File, pages: List<WrittenPage>) {
        File(workDir, MANIFEST_NAME).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            pages.forEach { page ->
                when (val descriptor = page.descriptor) {
                    is LocalPageDescriptor.Image -> writer.append("IMAGE|${descriptor.relativePath}")
                    is LocalPageDescriptor.Text -> writer.append("TEXT|${descriptor.relativePath}")
                    is LocalPageDescriptor.Pdf -> writer.append("PDF|${descriptor.relativePath}|${descriptor.pageIndex}")
                }
                writer.newLine()
            }
        }
    }

    private fun ZipFile.readTextEntry(name: String): String? {
        val entry = getEntry(name) ?: return null
        return getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun copyZipEntry(entry: java.util.zip.ZipEntry, zip: ZipFile, target: File) {
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            BufferedOutputStream(FileOutputStream(target)).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
        }
    }

    private fun readRecord(file: RandomAccessFile, offsets: List<Long>, index: Int): ByteArray {
        val start = offsets[index]
        val end = if (index + 1 < offsets.size) offsets[index + 1] else file.length()
        val length = (end - start).toInt()
        require(length >= 0 && length <= MAX_RECORD_BYTES) { "MOBI 记录长度无效" }
        val bytes = ByteArray(length)
        file.seek(start)
        file.readFully(bytes)
        return bytes
    }

    private fun palmDocDecompress(bytes: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream(bytes.size * 2)
        var index = 0
        while (index < bytes.size) {
            val value = bytes[index++].toInt() and 0xff
            when {
                value == 0 -> output.write(0)
                value in 1..8 -> {
                    repeat(value) {
                        if (index < bytes.size) output.write(bytes[index++].toInt())
                    }
                }
                value in 9..0x7f -> output.write(value)
                value in 0x80..0xbf -> {
                    output.write(value)
                    if (index < bytes.size) output.write(bytes[index++].toInt())
                }
                else -> {
                    output.write(' '.code)
                    output.write(value xor 0x80)
                }
            }
        }
        return output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(StandardCharsets.UTF_8)
        return if (utf8.count { it == '\uFFFD' } > bytes.size / 100) {
            bytes.toString(Charsets.ISO_8859_1)
        } else utf8
    }

    private fun readU16(file: RandomAccessFile, offset: Int): Int {
        file.seek(offset.toLong())
        return (file.readUnsignedByte() shl 8) or file.readUnsignedByte()
    }

    private fun readU32(file: RandomAccessFile, offset: Int): Long {
        file.seek(offset.toLong())
        return (file.readUnsignedByte().toLong() shl 24) or
            (file.readUnsignedByte().toLong() shl 16) or
            (file.readUnsignedByte().toLong() shl 8) or
            file.readUnsignedByte().toLong()
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0
        return ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun indexOf(bytes: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        return bytes.indices.firstOrNull { index ->
            index + needle.size <= bytes.size && needle.indices.all { bytes[index + it] == needle[it] }
        } ?: -1
    }

    private fun attr(tag: String?, name: String): String? {
        if (tag == null) return null
        return Regex("\\b${Regex.escape(name)}\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.getOrNull(1)?.let(::decodeXml)
    }

    private fun normalizeZipPath(base: String, raw: String): String {
        val decoded = URLDecoder.decode(raw.substringBefore('#'), StandardCharsets.UTF_8.name())
        require(!decoded.contains("://") && !decoded.startsWith("data:", ignoreCase = true)) {
            "外部 EPUB 资源不受支持"
        }
        val parts = (if (base.isEmpty()) decoded else "$base/$decoded").split('/')
        val result = ArrayDeque<String>()
        parts.forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (result.isNotEmpty()) result.removeLast() else throw LocalComicParseException("EPUB 资源路径越界")
                else -> result.addLast(part)
            }
        }
        return result.joinToString("/")
    }

    private fun stripMarkup(value: String): String = decodeXml(
        value.replace(Regex("<style\\b[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
    )

    private fun decodeXml(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)

    private fun requireRelative(parts: List<String>, index: Int, lineNumber: Int): String {
        val value = parts.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?: throw LocalComicParseException("本地漫画索引第 ${lineNumber + 1} 行缺少路径")
        require(!File(value).isAbsolute && !value.split('/').contains("..")) {
            "本地漫画索引包含越界路径"
        }
        return value
    }

    private fun relativePath(root: File, file: File): String =
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun imageExtension(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "jpg"
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "png"
        bytes.size >= 6 && bytes.copyOfRange(0, 6).toString(StandardCharsets.US_ASCII).startsWith("GIF") -> "gif"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(StandardCharsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(StandardCharsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
    }

    private fun isImageExtension(extension: String): Boolean = extension in IMAGE_EXTENSIONS

    private fun isReadableImage(file: File): Boolean {
        return runCatching {
            val signature = FileInputStream(file).use { input ->
                val bytes = ByteArray(12)
                val count = input.read(bytes)
                if (count <= 0) return@runCatching false
                bytes.copyOf(count)
            }
            if (imageExtension(signature) == null) return@runCatching false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
            val sample = sampleSize(bounds.outWidth, bounds.outHeight)
            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching false
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > 64 || height / sample > 64) sample *= 2
        return sample
    }

    private fun safeName(value: String): String = value.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(96).ifBlank { "page" }

    private data class WrittenPage(val descriptor: LocalPageDescriptor, val file: File?) {
        fun coverPath(): String? = when (descriptor) {
            is LocalPageDescriptor.Image -> descriptor.relativePath
            is LocalPageDescriptor.Text -> null
            is LocalPageDescriptor.Pdf -> null
        }
    }

    private val naturalFileComparator = Comparator<File> { a, b -> naturalCompare(a.name, b.name) }
    private val naturalPathComparator = Comparator<String> { a, b -> naturalCompare(a, b) }

    private fun naturalCompare(left: String, right: String): Int {
        val leftParts = Regex("\\d+|\\D+").findAll(left.lowercase(Locale.ROOT)).map { it.value }.toList()
        val rightParts = Regex("\\d+|\\D+").findAll(right.lowercase(Locale.ROOT)).map { it.value }.toList()
        for (index in 0 until minOf(leftParts.size, rightParts.size)) {
            val a = leftParts[index]
            val b = rightParts[index]
            val result = if (a.first().isDigit() && b.first().isDigit()) {
                a.trimStart('0').ifEmpty { "0" }.length.compareTo(b.trimStart('0').ifEmpty { "0" }.length)
                    .takeIf { it != 0 }
                    ?: a.trimStart('0').ifEmpty { "0" }.compareTo(b.trimStart('0').ifEmpty { "0" })
            } else a.compareTo(b)
            if (result != 0) return result
        }
        return leftParts.size.compareTo(rightParts.size).takeIf { it != 0 } ?: left.compareTo(right, ignoreCase = true)
    }

    private companion object {
        const val MANIFEST_NAME = "pages.manifest"
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val PDF_PREVIEW_WIDTH = 1600
        const val MAX_RECORD_BYTES = 16 * 1024 * 1024
        const val PALM_HEADER_SIZE = 78
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}

private class ByteArrayOutputStreamCompat {
    private val output = java.io.ByteArrayOutputStream()
    var size: Int = 0
        private set

    fun write(bytes: ByteArray) {
        output.write(bytes)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = output.toByteArray()
}
