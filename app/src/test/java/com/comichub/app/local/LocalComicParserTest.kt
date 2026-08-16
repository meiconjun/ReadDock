package com.comichub.app.local

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LocalComicParserTest {
    private val parser = LocalComicParser()

    @Test
    fun `image pages use stable natural filename order`() {
        val root = tempDir()
        val input = File(root, "input").apply { mkdirs() }
        val files = listOf("page10.png", "page2.png", "page1.png").map { name ->
            File(input, name).also { writePng(it) }
        }

        val parsed = parser.parse(File(root, "book"), files, LocalComicFormat.IMAGE)
        assertEquals(3, parsed.pageCount)
        assertEquals(
            listOf("pages/00001_page1.png", "pages/00002_page2.png", "pages/00003_page10.png"),
            parsed.pages.map { (it as LocalPageDescriptor.Image).relativePath }
        )
    }

    @Test
    fun `one corrupt image does not hide other readable pages`() {
        val root = tempDir()
        val good = File(root, "good.png").also { writePng(it) }
        val broken = File(root, "broken.png").also { it.writeText("not an image") }

        val parsed = parser.parse(File(root, "book"), listOf(broken, good), LocalComicFormat.IMAGE)
        assertEquals(1, parsed.pageCount)
    }

    @Test
    fun `epub follows spine and resolves relative image paths`() {
        val root = tempDir()
        val epub = File(root, "comic.epub")
        ZipOutputStream(FileOutputStream(epub)).use { zip ->
            zipEntry(zip, "META-INF/container.xml", """
                <container><rootfiles><rootfile full-path="OEBPS/package.opf"/></rootfiles></container>
            """.trimIndent())
            zipEntry(zip, "OEBPS/package.opf", """
                <package><manifest>
                  <item id="page" href="text/page.xhtml" media-type="application/xhtml+xml"/>
                  <item id="image" href="images/p.png" media-type="image/png"/>
                </manifest><spine><itemref idref="page"/></spine></package>
            """.trimIndent())
            zipEntry(zip, "OEBPS/text/page.xhtml", """<html><body><img src='../images/p.png'/></body></html>""")
            zipEntry(zip, "OEBPS/images/p.png", PNG_BYTES)
        }

        val parsed = parser.parse(File(root, "book"), listOf(epub), LocalComicFormat.EPUB)
        assertEquals(1, parsed.pageCount)
        assertTrue(parsed.pages.single() is LocalPageDescriptor.Image)
    }

    @Test
    fun `cbz ignores directories and sorts image entries`() {
        val root = tempDir()
        val cbz = File(root, "comic.cbz")
        val validPng = File(root, "valid.png").also { writePng(it) }.readBytes()
        ZipOutputStream(FileOutputStream(cbz)).use { zip ->
            zip.putNextEntry(ZipEntry("folder/")); zip.closeEntry()
            zipEntry(zip, "folder/page10.png", validPng)
            zipEntry(zip, "folder/page2.png", validPng)
            zipEntry(zip, "folder/.hidden.png", validPng)
        }

        val parsed = parser.parse(File(root, "book"), listOf(cbz), LocalComicFormat.CBZ)
        assertEquals(2, parsed.pageCount)
    }

    @Test
    fun `mobi palm database with image record is readable`() {
        val root = tempDir()
        val mobi = File(root, "comic.mobi").apply { writeBytes(minimalMobi()) }

        val parsed = parser.parse(File(root, "book"), listOf(mobi), LocalComicFormat.MOBI)
        assertEquals(1, parsed.pageCount)
        assertTrue(parsed.pages.single() is LocalPageDescriptor.Image)
    }

    @Test
    fun `corrupt pdf reports a parsing error`() {
        val root = tempDir()
        val pdf = File(root, "broken.pdf").also { it.writeText("not a pdf") }
        val error = assertFailsWith<LocalComicParseException> {
            parser.parse(File(root, "book"), listOf(pdf), LocalComicFormat.PDF)
        }
        assertTrue(error.message.orEmpty().contains("PDF"))
    }

    private fun tempDir(): File = createTempDir(prefix = "local-comic-test-")

    private fun writePng(file: File) {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun zipEntry(zip: ZipOutputStream, name: String, content: String) =
        zipEntry(zip, name, content.toByteArray())

    private fun zipEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    private fun minimalMobi(): ByteArray {
        val text = "<html><body><img recindex=\"0\"/></body></html>".toByteArray()
        val header = ByteArray(128)
        putU16(header, 0, 1)
        putU32(header, 4, text.size.toLong())
        putU16(header, 8, 1)
        putU16(header, 12, 0)
        header[16] = 'M'.code.toByte(); header[17] = 'O'.code.toByte()
        header[18] = 'B'.code.toByte(); header[19] = 'I'.code.toByte()
        putU32(header, 16 + 0x6c, 2)
        val records = listOf(header, text, PNG_BYTES)
        val dataStart = 78 + records.size * 8
        val output = java.io.ByteArrayOutputStream()
        output.write(ByteArray(78))
        val dbHeader = output.toByteArray()
        putU16(dbHeader, 76, records.size)
        output.reset(); output.write(dbHeader)
        var offset = dataStart
        records.forEach { record ->
            val entry = ByteArray(8)
            putU32(entry, 0, offset.toLong())
            output.write(entry)
            offset += record.size
        }
        records.forEach(output::write)
        return output.toByteArray()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    companion object {
        // A tiny valid 1x1 PNG fixture.
        private val PNG_BYTES = intArrayOf(
            137, 80, 78, 71, 13, 10, 26, 10,
            0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1,
            8, 6, 0, 0, 0, 31, 21, 196, 137,
            0, 0, 0, 13, 73, 68, 65, 84, 120, 156, 99, 0,
            1, 0, 0, 5, 0, 1, 13, 10, 45, 181,
            0, 0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130
        ).map { it.toByte() }.toByteArray()
    }
}
