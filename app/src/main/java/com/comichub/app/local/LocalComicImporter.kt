package com.comichub.app.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.comichub.data.LocalComic
import com.comichub.data.LocalComicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

sealed class LocalImportResult {
    data class Success(val comic: LocalComic) : LocalImportResult()
    data class Duplicate(val existing: LocalComic) : LocalImportResult()
    data class Empty(val message: String) : LocalImportResult()
    data class Error(val message: String, val cause: Throwable? = null) : LocalImportResult()
}

/** Copies SAF content into filesDir before any parsing takes place. */
class LocalComicImporter(
    private val context: Context,
    private val repository: LocalComicRepository,
    private val parser: LocalComicParser = LocalComicParser()
) {
    suspend fun importFiles(uris: List<Uri>, titleOverride: String? = null): LocalImportResult =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext LocalImportResult.Empty("没有选择文件")
            val names = uris.map { queryDisplayName(context.contentResolver, it) ?: "comic" }
            val format = if (uris.size > 1) {
                if (names.any { !isImageName(it) }) {
                    return@withContext LocalImportResult.Error("多选导入只支持 JPG、JPEG、PNG、WEBP、GIF 图片")
                }
                LocalComicFormat.IMAGE
            } else {
                detectFormat(names.single())
                    ?: return@withContext LocalImportResult.Error(
                        "不支持的文件格式：${names.single().substringAfterLast('.', "未知")}"
                    )
            }
            importCopiedUris(uris, names, format, titleOverride ?: titleFromName(names.first()))
        }

    suspend fun importFolder(treeUri: Uri): LocalImportResult = withContext(Dispatchers.IO) {
        val imageUris = collectImageUris(context.contentResolver, treeUri)
        if (imageUris.isEmpty()) {
            LocalImportResult.Empty("文件夹中没有 JPG、JPEG、PNG、WEBP 或 GIF 图片")
        } else {
            importFiles(imageUris, queryDisplayName(context.contentResolver, treeUri) ?: "本地漫画")
        }
    }

    private suspend fun importCopiedUris(
        uris: List<Uri>,
        names: List<String>,
        format: LocalComicFormat,
        title: String
    ): LocalImportResult {
        val root = File(context.filesDir, "local-comics")
        root.mkdirs()
        val workDir = File(root, ".import-${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            val inputDir = File(workDir, "input").apply { mkdirs() }
            val copied = uris.mapIndexed { index, uri ->
                val file = File(inputDir, inputFileName(names[index], index))
                val digest = copyUri(context.contentResolver, uri, file)
                CopiedInput(file, names[index], digest, file.length())
            }
            val fileHash = compositeHash(copied, uris.size > 1)
            repository.findByHash(fileHash)?.let { existing ->
                workDir.deleteRecursively()
                return LocalImportResult.Duplicate(existing)
            }
            val parsed = parser.parse(workDir, copied.map { it.file }, format)
            File(workDir, "input").deleteRecursively()
            val id = UUID.randomUUID().toString()
            val finalDir = File(root, id)
            check(workDir.renameTo(finalDir)) { "无法保存本地漫画文件" }
            val now = System.currentTimeMillis()
            val comic = LocalComic(
                id = id,
                title = title.ifBlank { "本地漫画" },
                fileName = if (uris.size == 1) names.single() else "${title.ifBlank { "本地漫画" }} (${uris.size}张图片)",
                format = format.name,
                localPath = finalDir.absolutePath,
                coverPath = parsed.coverRelativePath?.let { File(finalDir, it).absolutePath },
                pageCount = parsed.pageCount,
                currentPage = 1,
                createdAt = now,
                updatedAt = now,
                fileSize = copied.sumOf { it.size },
                fileHash = fileHash
            )
            try {
                repository.insert(comic)
            } catch (error: Throwable) {
                finalDir.deleteRecursively()
                throw error
            }
            LocalImportResult.Success(comic)
        } catch (error: LocalComicParseException) {
            workDir.deleteRecursively()
            LocalImportResult.Error(error.message ?: "文件解析失败", error)
        } catch (error: Throwable) {
            workDir.deleteRecursively()
            LocalImportResult.Error("导入失败：${error.message ?: "未知错误"}", error)
        }
    }

    private fun copyUri(resolver: ContentResolver, uri: Uri, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取文件：$uri")
        input.use { raw ->
            BufferedInputStream(raw, COPY_BUFFER_SIZE).use { buffered ->
                BufferedOutputStream(FileOutputStream(target), COPY_BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val count = buffered.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        return digest.digest().toHex()
    }

    private fun compositeHash(inputs: List<CopiedInput>, multiple: Boolean): String {
        if (!multiple) return inputs.single().hash
        val digest = MessageDigest.getInstance("SHA-256")
        inputs.sortedWith { left, right -> naturalCompare(left.displayName, right.displayName) }
            .forEach { input ->
            digest.update(input.displayName.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(input.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(input.hash.toByteArray(Charsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toHex()
    }

    private fun collectImageUris(resolver: ContentResolver, treeUri: Uri): List<Uri> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val result = mutableListOf<Uri>()
        walkTree(resolver, treeUri, rootUri, result)
        return result.sortedWith { left, right ->
            naturalCompare(
                queryDisplayName(resolver, left).orEmpty(),
                queryDisplayName(resolver, right).orEmpty()
            )
        }
    }

    private fun walkTree(
        resolver: ContentResolver,
        treeUri: Uri,
        directoryUri: Uri,
        result: MutableList<Uri>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val mime = cursor.getString(mimeColumn)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTree(resolver, treeUri, childUri, result)
                } else if (queryDisplayName(resolver, childUri)?.let(::isImageName) == true) {
                    result += childUri
                }
            }
        } ?: throw IllegalStateException("无法读取所选文件夹")
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun detectFormat(name: String): LocalComicFormat? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif" -> LocalComicFormat.IMAGE
        "pdf" -> LocalComicFormat.PDF
        "epub" -> LocalComicFormat.EPUB
        "mobi", "prc" -> LocalComicFormat.MOBI
        "cbz", "zip" -> LocalComicFormat.CBZ
        else -> null
    }

    private fun titleFromName(name: String): String = name.substringBeforeLast('.', name)
        .replace(Regex("[_-]+"), " ").trim().ifBlank { "本地漫画" }

    private fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "gif")

    private fun safeName(value: String): String = value.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(96).ifBlank { "file" }

    private fun inputFileName(name: String, index: Int): String {
        val safe = safeName(name)
        val dot = safe.lastIndexOf('.')
        return if (dot > 0) {
            safe.substring(0, dot) + "__" + index + safe.substring(dot)
        } else {
            "${safe}__${index}"
        }
    }

    private fun naturalCompare(left: String, right: String): Int {
        val leftParts = Regex("\\d+|\\D+").findAll(left.lowercase()).map { it.value }.toList()
        val rightParts = Regex("\\d+|\\D+").findAll(right.lowercase()).map { it.value }.toList()
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
        return leftParts.size.compareTo(rightParts.size).takeIf { it != 0 } ?: left.compareTo(right, true)
    }

    private data class CopiedInput(
        val file: File,
        val displayName: String,
        val hash: String,
        val size: Long
    )

    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
