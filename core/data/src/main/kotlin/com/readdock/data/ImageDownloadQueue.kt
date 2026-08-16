package com.readdock.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

data class ImageDownloadTask(
    val id: String,
    val url: String,
    val status: DownloadStatus,
    val error: String? = null
)

/** Bounded-concurrency image downloader that reuses the file cache. */
class ImageDownloadQueue(
    private val cache: FileImageCache,
    private val fetch: suspend (String) -> ByteArray,
    concurrency: Int = 2
) {
    private val semaphore = Semaphore(concurrency.also {
        require(it > 0) { "concurrency must be positive" }
    })
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<ImageDownloadTask>>()
    private val _tasks = MutableStateFlow<List<ImageDownloadTask>>(emptyList())

    val tasks: StateFlow<List<ImageDownloadTask>> = _tasks.asStateFlow()

    /** Ensures one image is cached, deduplicating concurrent requests for the same URL. */
    suspend fun downloadOne(url: String): ImageDownloadTask {
        val task = ImageDownloadTask(
            id = "image-${UUID.randomUUID()}",
            url = url,
            status = if (cache.getFile(url) != null) DownloadStatus.COMPLETED else DownloadStatus.QUEUED
        )
        if (task.status == DownloadStatus.COMPLETED) return task

        val deferred = inFlightMutex.withLock {
            inFlight[url] ?: kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).async {
                runSingle(task)
            }.also { created ->
                inFlight[url] = created
            }
        }
        return try {
            deferred.await()
        } finally {
            inFlightMutex.withLock {
                if (inFlight[url] === deferred) inFlight.remove(url)
            }
        }
    }

    suspend fun download(urls: List<String>): List<ImageDownloadTask> {
        val initial = urls.distinct().mapIndexed { index, url ->
            ImageDownloadTask(
                id = "image-$index",
                url = url,
                status = DownloadStatus.QUEUED
            )
        }
        _tasks.value = initial

        return coroutineScope {
            initial.map { task ->
                async { runSingle(task) }
            }.awaitAll()
        }
    }

    private suspend fun runSingle(task: ImageDownloadTask): ImageDownloadTask = semaphore.withPermit {
        update(task.copy(status = DownloadStatus.RUNNING))
        try {
            if (cache.getFile(task.url) == null) {
                cache.put(task.url, fetch(task.url))
            }
            task.copy(status = DownloadStatus.COMPLETED).also(::update)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            task.copy(
                status = DownloadStatus.FAILED,
                error = error.message ?: "图片下载失败"
            ).also(::update)
        }
    }

    private fun update(task: ImageDownloadTask) {
        _tasks.value = _tasks.value.map { current ->
            if (current.id == task.id) task else current
        }
    }
}
