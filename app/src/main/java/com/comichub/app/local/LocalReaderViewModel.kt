package com.comichub.app.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comichub.data.LocalComic
import com.comichub.data.LocalComicRepository
import com.comichub.data.RoomLocalComicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

enum class LocalReaderStatus { LOADING, SUCCESS, EMPTY, ERROR }

data class LocalReaderPageContent(
    val page: LocalPageDescriptor,
    val bitmap: Bitmap? = null,
    val text: String? = null
)

data class LocalReaderState(
    val status: LocalReaderStatus = LocalReaderStatus.LOADING,
    val comic: LocalComic? = null,
    val pages: List<LocalPageDescriptor> = emptyList(),
    val currentPage: Int = 1,
    val content: LocalReaderPageContent? = null,
    val isPageLoading: Boolean = true,
    val errorMessage: String? = null,
    val pageErrorMessage: String? = null,
    val progressErrorMessage: String? = null
) {
    val pageCount: Int get() = pages.size
    val canGoPrevious: Boolean get() = currentPage > 1 && !isPageLoading
    val canGoNext: Boolean get() = currentPage < pageCount && !isPageLoading
}

class LocalReaderViewModel(
    private val comicId: String,
    private val repository: LocalComicRepository,
    private val parser: LocalComicParser = LocalComicParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    var state by mutableStateOf(LocalReaderState())
        private set

    private var pageJob: Job? = null

    init {
        viewModelScope.launch { loadBook() }
    }

    fun previousPage() {
        if (state.currentPage <= 1 || state.isPageLoading) return
        showPage(state.currentPage - 1)
    }

    fun nextPage() {
        if (state.currentPage >= state.pageCount || state.isPageLoading) return
        showPage(state.currentPage + 1)
    }

    fun retry() {
        if (state.status == LocalReaderStatus.ERROR) {
            viewModelScope.launch { loadBook() }
        } else {
            showPage(state.currentPage)
        }
    }

    private suspend fun loadBook() {
        pageJob?.cancel()
        state = LocalReaderState(status = LocalReaderStatus.LOADING)
        try {
            val comic = withContext(ioDispatcher) { repository.findById(comicId) }
                ?: throw IllegalStateException("本地漫画不存在，可能已被删除")
            val pages = withContext(ioDispatcher) {
                parser.readManifest(File(comic.localPath))
            }
            if (pages.isEmpty()) {
                state = LocalReaderState(status = LocalReaderStatus.EMPTY, comic = comic)
                return
            }
            val initialPage = comic.currentPage.coerceIn(1, pages.size)
            state = LocalReaderState(
                status = LocalReaderStatus.SUCCESS,
                comic = comic,
                pages = pages,
                currentPage = initialPage,
                isPageLoading = true
            )
            showPage(initialPage)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state = LocalReaderState(
                status = LocalReaderStatus.ERROR,
                errorMessage = error.message ?: "本地漫画打开失败"
            )
        }
    }

    private fun showPage(pageNumber: Int) {
        val book = state.comic ?: return
        val pages = state.pages
        if (pageNumber !in 1..pages.size) return
        pageJob?.cancel()
        val descriptor = pages[pageNumber - 1]
        state = state.copy(
            currentPage = pageNumber,
            content = null,
            isPageLoading = true,
            pageErrorMessage = null,
            progressErrorMessage = null
        )
        pageJob = viewModelScope.launch {
            val progressJob = launch(ioDispatcher) {
                runCatching { repository.updateProgress(book.id, pageNumber) }
                    .onFailure { error ->
                        if (error !is CancellationException) {
                            state = state.copy(
                                progressErrorMessage = "阅读进度保存失败：${error.message ?: "未知错误"}"
                            )
                        }
                    }
            }
            try {
                val content = withContext(ioDispatcher) { loadContent(book, descriptor) }
                state = state.copy(
                    content = content,
                    isPageLoading = false,
                    pageErrorMessage = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state = state.copy(
                    content = null,
                    isPageLoading = false,
                    pageErrorMessage = "第 ${pageNumber} 页加载失败：${error.message ?: "文件损坏或不可读"}"
                )
            } finally {
                progressJob.join()
            }
        }
    }

    private fun loadContent(comic: LocalComic, descriptor: LocalPageDescriptor): LocalReaderPageContent {
        val root = File(comic.localPath).canonicalFile
        return when (descriptor) {
            is LocalPageDescriptor.Image -> {
                val file = resolveChild(root, descriptor.relativePath)
                LocalReaderPageContent(descriptor, bitmap = decodeSampledBitmap(file))
            }
            is LocalPageDescriptor.Text -> {
                val file = resolveChild(root, descriptor.relativePath)
                LocalReaderPageContent(
                    descriptor,
                    text = file.inputStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                        reader.readText().take(MAX_TEXT_PAGE_CHARS)
                    }
                )
            }
            is LocalPageDescriptor.Pdf -> {
                val file = resolveChild(root, descriptor.relativePath)
                LocalReaderPageContent(descriptor, bitmap = renderPdfPage(file, descriptor.pageIndex))
            }
        }
    }

    private fun decodeSampledBitmap(file: File): Bitmap {
        if (!file.isFile) throw IllegalStateException("图片文件不存在")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("图片损坏或格式不受支持")
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("图片解码失败")
    }

    private fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
        if (!file.isFile) throw IllegalStateException("PDF 文件不存在")
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
        try {
            if (pageIndex !in 0 until renderer.pageCount) throw IllegalStateException("PDF 页码不存在")
            val page = renderer.openPage(pageIndex)
            try {
                val scale = minOf(1f, MAX_RENDER_WIDTH.toFloat() / page.width.coerceAtLeast(1))
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private fun resolveChild(root: File, relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        require(file.path == root.path || file.path.startsWith(root.path + File.separator)) {
            "页面路径越界"
        }
        return file
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DECODE_WIDTH || height / sample > MAX_DECODE_HEIGHT) sample *= 2
        return sample
    }

    override fun onCleared() {
        pageJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_DECODE_WIDTH = 1800
        private const val MAX_DECODE_HEIGHT = 2600
        private const val MAX_RENDER_WIDTH = 1800
        private const val MAX_TEXT_PAGE_CHARS = 2_000_000

        fun factory(context: android.content.Context, comicId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LocalReaderViewModel(
                        comicId = comicId,
                        repository = RoomLocalComicRepository.create(context.applicationContext)
                    ) as T
            }
    }
}
