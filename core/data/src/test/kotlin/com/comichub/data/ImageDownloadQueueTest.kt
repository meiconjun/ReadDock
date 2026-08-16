package com.comichub.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImageDownloadQueueTest {
    @Test
    fun `deduplicates urls and reports completed tasks`() = runBlocking {
        val directory = Files.createTempDirectory("comichub-download-queue").toFile()
        try {
            val fetchCount = AtomicInteger(0)
            val cache = FileImageCache(directory)
            val queue = ImageDownloadQueue(
                cache = cache,
                fetch = { url ->
                    fetchCount.incrementAndGet()
                    url.toByteArray()
                },
                concurrency = 2
            )

            val tasks = queue.download(listOf("a", "b", "a"))

            assertEquals(2, tasks.size)
            assertEquals(listOf(DownloadStatus.COMPLETED, DownloadStatus.COMPLETED), tasks.map { it.status })
            assertEquals(2, fetchCount.get())
            assertNotNull(cache.get("a"))
            assertNotNull(cache.get("b"))
            assertEquals(tasks, queue.tasks.value)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `downloadOne deduplicates concurrent requests for one url`() = runBlocking {
        val directory = Files.createTempDirectory("comichub-download-one").toFile()
        try {
            val fetchCount = AtomicInteger(0)
            val cache = FileImageCache(directory)
            val queue = ImageDownloadQueue(
                cache = cache,
                fetch = {
                    fetchCount.incrementAndGet()
                    Thread.sleep(20)
                    byteArrayOf(1, 2, 3)
                }
            )

            val results = coroutineScope {
                listOf(
                    async { queue.downloadOne("same") },
                    async { queue.downloadOne("same") }
                ).map { it.await() }
            }

            assertEquals(1, fetchCount.get())
            assertEquals(listOf(DownloadStatus.COMPLETED, DownloadStatus.COMPLETED), results.map { it.status })
        } finally {
            directory.deleteRecursively()
        }
    }
}
