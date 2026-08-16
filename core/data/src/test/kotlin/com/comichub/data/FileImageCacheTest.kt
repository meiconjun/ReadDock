package com.comichub.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class FileImageCacheTest {
    @Test
    fun `stores reads and clears image bytes`() {
        val directory = Files.createTempDirectory("comichub-image-cache").toFile()
        try {
            val cache = FileImageCache(directory)
            val bytes = byteArrayOf(0, 1, 2, 127, -1)

            cache.put("https://example.com/a.jpg", bytes)

            assertContentEquals(bytes, cache.get("https://example.com/a.jpg"))
            assertEquals(bytes.size.toLong(), cache.sizeBytes())
            cache.clear()
            assertNull(cache.get("https://example.com/a.jpg"))
            assertEquals(0L, cache.sizeBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `evicts oldest files over the configured limit`() {
        val directory = Files.createTempDirectory("comichub-image-eviction").toFile()
        try {
            val cache = FileImageCache(directory, maxBytes = 8)
            val firstUrl = "https://example.com/first.jpg"
            val secondUrl = "https://example.com/second.jpg"

            cache.put(firstUrl, ByteArray(8) { 1 })
            Thread.sleep(50)
            cache.put(secondUrl, ByteArray(8) { 2 })

            assertEquals(8L, cache.sizeBytes())
            assertNull(cache.get(firstUrl))
            assertContentEquals(ByteArray(8) { 2 }, cache.get(secondUrl))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects one entry larger than the per-file limit`() {
        val directory = Files.createTempDirectory("comichub-image-entry-limit").toFile()
        try {
            val cache = FileImageCache(directory, maxBytes = 32, maxEntryBytes = 4)
            assertFailsWith<IllegalArgumentException> {
                cache.put("https://example.com/large.jpg", ByteArray(5))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
