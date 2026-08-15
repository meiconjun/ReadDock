package com.comichub.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    private val _tasks = MutableStateFlow<List<ImageDownloadTask>>(emptyList())

    val tasks: StateFlow<List<ImageDownloadTask>> = _tasks.asStateFlow()

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
                async {
                    semaphore.withPermit {
                        update(task.copy(status = DownloadStatus.RUNNING))
                        try {
                            if (cache.get(task.url) == null) {
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
                }
            }.awaitAll()
        }
    }

    private fun update(task: ImageDownloadTask) {
        _tasks.value = _tasks.value.map { current ->
            if (current.id == task.id) task else current
        }
    }
}
