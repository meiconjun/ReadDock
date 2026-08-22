package com.readdock.app.local

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.readdock.app.reader.ReaderBitmapCache
import com.readdock.app.reader.SampledBitmapDecoder
import com.readdock.data.LocalComic
import com.readdock.data.LocalComicRepository
import com.readdock.data.RoomLocalComicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val canGoPrevious: Boolean get() = currentPage > 1
    val canGoNext: Boolean get() = currentPage < pageCount
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
    private var pageRequestId = 0L
    private val progressMutex = Mutex()
    private val bitmapDecoder = SampledBitmapDecoder(ReaderBitmapCache())

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

    fun goToPage(pageNumber: Int) {
        if (state.isPageLoading || pageNumber !in 1..state.pageCount) return
        if (pageNumber != state.currentPage) showPage(pageNumber)
    }

    fun retry() {
        if (state.status == LocalReaderStatus.ERROR) {
            viewModelScope.launch { loadBook() }
        } else {
            showPage(state.currentPage)
        }
    }

    private suspend fun loadBook() {
        pageRequestId += 1
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
                errorMessage = "本地漫画打开失败，请返回书架后重新导入"
            )
        }
    }

    private fun showPage(pageNumber: Int) {
        val book = state.comic ?: return
        val pages = state.pages
        if (pageNumber !in 1..pages.size) return
        pageRequestId += 1
        val requestId = pageRequestId
        pageJob?.cancel()
        val descriptor = pages[pageNumber - 1]
        state = state.copy(
            currentPage = pageNumber,
            content = null,
            isPageLoading = true,
            pageErrorMessage = null,
            progressErrorMessage = null
        )
        persistProgress(book, pageNumber)
        pageJob = viewModelScope.launch {
            try {
                val content = withContext(ioDispatcher) { loadContent(book, descriptor) }
                if (requestId != pageRequestId) return@launch
                state = state.copy(
                    content = content,
                    isPageLoading = false,
                    pageErrorMessage = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (requestId != pageRequestId) return@launch
                state = state.copy(
                    content = null,
                    isPageLoading = false,
                    pageErrorMessage = "第 ${pageNumber} 页加载失败，请检查文件后重试"
                )
            }
        }
    }

    /**
     * Progress writes must not be children of pageJob.  pageJob is deliberately
     * cancelled when the user changes pages; making the database write its child
     * could cancel the write as the reader navigates quickly or is closed.
     * Serialising writes also prevents an older page from racing a newer page.
     */
    private fun persistProgress(book: LocalComic, pageNumber: Int) {
        viewModelScope.launch {
            try {
                progressMutex.withLock {
                    withContext(NonCancellable + ioDispatcher) {
                        repository.updateProgress(book.id, pageNumber)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (state.comic?.id == book.id && state.currentPage == pageNumber) {
                    state = state.copy(
                        progressErrorMessage = "阅读进度保存失败，请稍后重试"
                    )
                }
            }
        }
    }

    private fun loadContent(comic: LocalComic, descriptor: LocalPageDescriptor): LocalReaderPageContent {
        val root = File(comic.localPath).canonicalFile
        return when (descriptor) {
            is LocalPageDescriptor.Image -> {
                val file = resolveChild(root, descriptor.relativePath)
                LocalReaderPageContent(
                    descriptor,
                    bitmap = bitmapDecoder.decodeFile(
                        key = "${comic.id}:${descriptor.relativePath}",
                        file = file
                    )
                )
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

    override fun onCleared() {
        pageRequestId += 1
        pageJob?.cancel()
        bitmapDecoder.clear()
        super.onCleared()
    }

    companion object {
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
