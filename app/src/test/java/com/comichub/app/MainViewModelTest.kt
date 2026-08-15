package com.comichub.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comichub.data.LibraryComic
import com.comichub.data.LibraryRepository
import com.comichub.data.ReadingHistoryItem
import com.comichub.data.ReadingProgress
import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicSummary
import com.comichub.source.runtime.MyComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLibraryRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = FakeLibraryRepository()
    }

    private fun createViewModel(): MainViewModel = MainViewModel(
        context,
        repository,
        MyComicSource { url ->
            when {
                url == "https://mycomic.com/cn/comics?page=1" -> """
                    <a href="/cn/comics/1769">
                      <img src="https://biccam.com/comics/1769-cover.jpg" alt="烙印战士">
                    </a>
                """.trimIndent()
                url == MyComicSource.COMIC_URL -> """
                    <html><head><title>烙印战士 - MYCOMIC</title></head><body>
                      <img src="https://biccam.com/comics/1769-cover.jpg" alt="烙印战士">
                      <a href="/cn/chapters/15444">第01卷</a>
                    </body></html>
                """.trimIndent()
                else -> error("missing MYCOMIC test fixture: $url")
            }
        }
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `detail, bookshelf and reader update the injected repository`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val comic = ComicSummary(
            id = "sky-courier",
            sourceId = "com.comichub.mock",
            title = "星海信使"
        )
        viewModel.openComic(comic)
        advanceUntilIdle()
        assertEquals(AppScreen.DETAIL, viewModel.screen)

        viewModel.toggleSaved()
        advanceUntilIdle()
        assertTrue(viewModel.isSaved(comic))
        assertEquals(comic.id, repository.library.value.single().comicId)

        val chapter = viewModel.selectedDetail!!.chapters.first()
        viewModel.openChapter(chapter)
        advanceUntilIdle()
        assertEquals(AppScreen.READER, viewModel.screen)
        assertEquals(1, repository.progress.value?.currentPage)

        viewModel.updateReadingProgress(3)
        advanceUntilIdle()
        assertEquals(3, repository.progress.value?.currentPage)
        assertEquals(3, repository.history.value.first().currentPage)
    }

    @Test
    fun `mycomic chapter opens in the browser session reader`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val comic = viewModel.results.single { it.sourceId == MyComicSource.SOURCE_ID }
        viewModel.openComic(comic)
        advanceUntilIdle()
        viewModel.openChapter(viewModel.selectedDetail!!.chapters.single())
        advanceUntilIdle()

        assertEquals(AppScreen.WEB_READER, viewModel.screen)
        assertEquals(MyComicSource.FIRST_CHAPTER_URL, viewModel.webReaderUrl)
    }

    @Test
    fun `invalid repository url becomes an error message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRepositoryUrl("http://insecure.example/index.json")
        viewModel.refreshRepository()

        assertEquals("仓库地址必须使用 HTTPS", viewModel.repositoryMessage?.text)
        assertEquals(MessageTone.ERROR, viewModel.repositoryMessage?.tone)
    }

    @Test
    fun `invalid plugin import becomes an error message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.installPlugin("{\"notAPlugin\":true}")
        advanceUntilIdle()

        assertEquals(MessageTone.ERROR, viewModel.pluginMessage?.tone)
        assertTrue(viewModel.pluginMessage?.text.orEmpty().contains("插件未安装"))
    }
}

private class FakeLibraryRepository : LibraryRepository {
    val library = MutableStateFlow<List<LibraryComic>>(emptyList())
    val history = MutableStateFlow<List<ReadingHistoryItem>>(emptyList())
    val progress = MutableStateFlow<ReadingProgress?>(null)
    private val details = mutableMapOf<String, ComicDetail>()

    override fun observeLibrary(): Flow<List<LibraryComic>> = library

    override fun observeHistory(): Flow<List<ReadingHistoryItem>> = history

    override suspend fun saveComic(detail: ComicDetail) {
        details[comicKey(detail.summary)] = detail
    }

    override suspend fun setSaved(comic: ComicSummary, saved: Boolean) {
        if (saved) {
            val detail = details[comicKey(comic)]
            library.value = listOf(
                LibraryComic(
                    sourceId = comic.sourceId,
                    comicId = comic.id,
                    title = comic.title,
                    coverUrl = comic.coverUrl,
                    tags = comic.tags,
                    author = detail?.author,
                    description = detail?.description,
                    addedAt = 1L
                )
            )
        } else {
            library.value = library.value.filterNot {
                it.sourceId == comic.sourceId && it.comicId == comic.id
            }
        }
    }

    override suspend fun recordChapterOpened(
        comic: ComicSummary,
        chapter: Chapter,
        totalPages: Int
    ): ReadingProgress {
        val next = ReadingProgress(
            sourceId = chapter.sourceId,
            comicId = chapter.comicId,
            chapterId = chapter.id,
            currentPage = progress.value?.currentPage ?: 1,
            totalPages = totalPages,
            lastReadAt = 1L,
            completed = false
        )
        progress.value = next
        history.value = listOf(
            ReadingHistoryItem(
                sourceId = comic.sourceId,
                comicId = comic.id,
                chapterId = chapter.id,
                comicTitle = comic.title,
                coverUrl = comic.coverUrl,
                chapterTitle = chapter.title,
                chapterNumber = chapter.number,
                currentPage = next.currentPage,
                totalPages = next.totalPages,
                lastReadAt = next.lastReadAt,
                completed = next.completed
            )
        )
        return next
    }

    override suspend fun updateProgress(
        chapter: Chapter,
        currentPage: Int,
        totalPages: Int
    ): ReadingProgress {
        val next = progress.value!!.copy(
            currentPage = currentPage,
            totalPages = totalPages,
            completed = currentPage >= totalPages
        )
        progress.value = next
        history.value = history.value.map {
            it.copy(currentPage = currentPage, totalPages = totalPages)
        }
        return next
    }

    override suspend fun getProgress(chapter: Chapter): ReadingProgress? = progress.value

    private fun comicKey(comic: ComicSummary): String = "${comic.sourceId}::${comic.id}"
}
