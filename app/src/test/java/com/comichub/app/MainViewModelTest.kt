package com.comichub.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comichub.data.LibraryComic
import com.comichub.data.LibraryRepository
import com.comichub.data.LocalComic
import com.comichub.data.LocalComicRepository
import com.comichub.data.ReadingHistoryItem
import com.comichub.data.ReadingProgress
import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicSummary
import com.comichub.source.runtime.MyComicSource
import com.comichub.app.local.LocalReaderStatus
import com.comichub.app.local.LocalReaderViewModel
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
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLibraryRepository
    private lateinit var localRepository: FakeLocalComicRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = FakeLibraryRepository()
        localRepository = FakeLocalComicRepository()
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
                url == "https://mycomic.com/cn/comics?q=%E4%B8%8D%E5%AD%98%E5%9C%A8&page=1" -> """
                    <html><head><title>MYCOMIC 搜索</title></head>
                    <body><main><p>没有结果</p></main></body></html>
                """.trimIndent()
                url == MyComicSource.COMIC_URL -> """
                    <html><head><title>烙印战士 - MYCOMIC</title></head><body>
                      <img src="https://biccam.com/comics/1769-cover.jpg" alt="烙印战士">
                      <div class="mt-8 mb-12">
                        <div x-data="{ chapters: true }">
                          <a href="/cn/chapters/15444">第01卷</a>
                        </div>
                      </div>
                    </body></html>
                """.trimIndent()
                url == MyComicSource.FIRST_CHAPTER_URL -> """
                    <html><body>
                      <img class="page" src="https://biccam.com/chapters/15444/1.jpg">
                      <img class="page" src="https://biccam.com/chapters/15444/2.jpg">
                    </body></html>
                """.trimIndent()
                else -> error("missing MYCOMIC test fixture: $url")
            }
        },
        imageFetcher = { byteArrayOf(1, 2, 3) },
        localComicRepository = localRepository
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
    fun `mycomic chapter uses the browser session for data and app reader for images`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val comic = viewModel.results.single { it.sourceId == MyComicSource.SOURCE_ID }
        viewModel.openComic(comic)
        advanceUntilIdle()
        viewModel.openChapter(viewModel.selectedDetail!!.chapters.single())
        advanceUntilIdle()

        assertEquals(AppScreen.READER, viewModel.screen, viewModel.errorMessage)
        assertEquals(MyComicSource.FIRST_CHAPTER_URL, viewModel.selectedChapter?.id)
        assertEquals(2, viewModel.pages.size)
    }

    @Test
    fun `back returns from detail to search instead of finishing the app`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openComic(viewModel.results.first { it.sourceId == "com.comichub.mock" })
        advanceUntilIdle()
        assertEquals(AppScreen.DETAIL, viewModel.screen)

        viewModel.back()

        assertEquals(AppScreen.SEARCH, viewModel.screen)
    }

    @Test
    fun `favorite is observable, persistent and removable from the library`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val comic = viewModel.results.first { it.sourceId == "com.comichub.mock" }

        viewModel.openComic(comic)
        advanceUntilIdle()
        viewModel.toggleSaved()
        advanceUntilIdle()

        assertTrue(viewModel.isSaved(comic))
        viewModel.showLibrary()
        assertEquals(AppScreen.LIBRARY, viewModel.screen)
        assertEquals(comic.id, repository.library.value.single().comicId)
        viewModel.back()
        assertEquals(AppScreen.SEARCH, viewModel.screen)

        val reopened = createViewModel()
        advanceUntilIdle()
        reopened.openComic(comic)
        advanceUntilIdle()
        assertTrue(reopened.isSaved(comic))
        reopened.toggleSaved()
        advanceUntilIdle()
        assertTrue(repository.library.value.isEmpty())
        assertTrue(!reopened.isSaved(comic))
    }

    @Test
    fun `back stack returns reader to detail and rejects a foreign chapter`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val comic = viewModel.results.first { it.sourceId == "com.comichub.mock" }
        viewModel.openComic(comic)
        advanceUntilIdle()
        val detail = viewModel.selectedDetail!!
        viewModel.openChapter(detail.chapters.first())
        advanceUntilIdle()
        assertEquals(AppScreen.READER, viewModel.screen)
        viewModel.back()
        assertEquals(AppScreen.DETAIL, viewModel.screen)

        val foreign = Chapter(
            id = "foreign",
            sourceId = detail.summary.sourceId,
            comicId = "another-comic",
            title = "不属于当前漫画",
            number = 99
        )
        viewModel.openChapter(foreign)
        advanceUntilIdle()
        assertEquals(AppScreen.DETAIL, viewModel.screen)
        assertTrue(viewModel.errorMessage.orEmpty().contains("不属于当前漫画"))
    }

    @Test
    fun `reader next chapter follows chapter numbers when source is newest first`() = runTest {
        val chapters = listOf(
            Chapter("chapter-3", "source", "comic", "第03话", 3),
            Chapter("chapter-2", "source", "comic", "第02话", 2),
            Chapter("chapter-1", "source", "comic", "第01话", 1)
        )

        assertEquals("chapter-2", adjacentChapter(chapters, "chapter-1", 1)?.id)
        assertEquals("chapter-1", adjacentChapter(chapters, "chapter-2", -1)?.id)
        assertEquals(null, adjacentChapter(chapters, "chapter-1", -1))
        assertEquals(null, adjacentChapter(chapters, "chapter-3", 1))
    }

    @Test
    fun `favorite storage failure gives visible feedback and rolls back optimistic state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val comic = viewModel.results.first { it.sourceId == "com.comichub.mock" }
        viewModel.openComic(comic)
        advanceUntilIdle()
        repository.failWrites = true

        viewModel.toggleSaved()
        assertTrue(viewModel.isSaved(comic))
        advanceUntilIdle()

        assertTrue(!viewModel.isSaved(comic))
        assertEquals(MessageTone.ERROR, viewModel.actionMessage?.tone)
    }

    @Test
    fun `empty search and source failure expose user visible states`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("不存在")
        advanceUntilIdle()
        assertTrue(viewModel.results.isEmpty())
        assertEquals(null, viewModel.errorMessage)

        viewModel.openComic(
            ComicSummary(
                id = "missing",
                sourceId = "com.comichub.mock",
                title = "不存在的详情"
            )
        )
        advanceUntilIdle()
        assertEquals(AppScreen.SEARCH, viewModel.screen)
        assertTrue(viewModel.errorMessage.orEmpty().contains("打开漫画失败"))
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

    @Test
    fun `local comic opens in an independent reader and obeys page boundaries`() = runTest {
        val directory = createTempDir(prefix = "local-reader-test-")
        File(directory, "pages.manifest").writeText("TEXT|pages/00001.txt\nTEXT|pages/00002.txt\n")
        File(directory, "pages").mkdirs()
        File(directory, "pages/00001.txt").writeText("第一页")
        File(directory, "pages/00002.txt").writeText("第二页")
        val comic = LocalComic(
            id = "local-test",
            title = "本地测试漫画",
            fileName = "test.epub",
            format = "EPUB",
            localPath = directory.absolutePath,
            coverPath = null,
            pageCount = 2,
            currentPage = 1,
            createdAt = 1,
            updatedAt = 1,
            fileSize = 10,
            fileHash = "local-hash"
        )
        localRepository.items.value = listOf(comic)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showLibrary()
        viewModel.openLocalComic(comic)
        assertEquals(AppScreen.LOCAL_READER, viewModel.screen)

        val reader = LocalReaderViewModel(comic.id, localRepository, ioDispatcher = dispatcher)
        advanceUntilIdle()
        assertEquals(LocalReaderStatus.SUCCESS, reader.state.status)
        assertEquals(1, reader.state.currentPage)
        reader.previousPage()
        assertEquals(1, reader.state.currentPage)
        reader.nextPage()
        advanceUntilIdle()
        assertEquals(2, reader.state.currentPage)
        reader.nextPage()
        assertEquals(2, reader.state.currentPage)
        assertEquals(2, localRepository.items.value.single().currentPage)
    }
}

private class FakeLibraryRepository : LibraryRepository {
    val library = MutableStateFlow<List<LibraryComic>>(emptyList())
    val history = MutableStateFlow<List<ReadingHistoryItem>>(emptyList())
    val progress = MutableStateFlow<ReadingProgress?>(null)
    private val details = mutableMapOf<String, ComicDetail>()
    var failWrites: Boolean = false

    override fun observeLibrary(): Flow<List<LibraryComic>> = library

    override fun observeHistory(): Flow<List<ReadingHistoryItem>> = history

    override suspend fun saveComic(detail: ComicDetail) {
        check(!failWrites) { "test storage failure" }
        details[comicKey(detail.summary)] = detail
    }

    override suspend fun setSaved(comic: ComicSummary, saved: Boolean) {
        check(!failWrites) { "test storage failure" }
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

private class FakeLocalComicRepository : LocalComicRepository {
    val items = MutableStateFlow<List<LocalComic>>(emptyList())

    override fun observeLocalComics(): Flow<List<LocalComic>> = items

    override suspend fun findById(id: String): LocalComic? = items.value.firstOrNull { it.id == id }

    override suspend fun findByHash(fileHash: String): LocalComic? =
        items.value.firstOrNull { it.fileHash == fileHash }

    override suspend fun insert(comic: LocalComic) {
        items.value = items.value.filterNot { it.id == comic.id } + comic
    }

    override suspend fun updateProgress(id: String, currentPage: Int): LocalComic? {
        val updated = items.value.map { comic ->
            if (comic.id == id) comic.copy(currentPage = currentPage, updatedAt = comic.updatedAt + 1) else comic
        }
        items.value = updated
        return updated.firstOrNull { it.id == id }
    }

    override suspend fun delete(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }
}
