package com.comichub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomLibraryRepositoryTest {
    private lateinit var database: PageLoomDatabase
    private lateinit var repository: RoomLibraryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PageLoomDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = RoomLibraryRepository(database.libraryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persists library, history and resumes chapter progress`() = runBlocking {
        val comic = ComicSummary(
            id = "comic-1",
            sourceId = "source-1",
            title = "持久化测试漫画",
            tags = listOf("测试", "冒险")
        )
        val chapter = Chapter(
            id = "chapter-1",
            sourceId = comic.sourceId,
            comicId = comic.id,
            title = "第一章",
            number = 1
        )
        val detail = ComicDetail(
            summary = comic,
            author = "测试作者",
            description = "测试描述",
            chapters = listOf(chapter)
        )

        repository.saveComic(detail)
        repository.setSaved(comic, saved = true)

        val saved = repository.observeLibrary().first()
        assertEquals(1, saved.size)
        assertEquals(comic.id, saved.single().comicId)
        assertEquals(listOf("测试", "冒险"), saved.single().tags)
        assertEquals("测试作者", saved.single().author)

        val firstOpen = repository.recordChapterOpened(comic, chapter, totalPages = 6)
        assertEquals(1, firstOpen.currentPage)
        assertFalse(firstOpen.completed)

        val updated = repository.updateProgress(chapter, currentPage = 4, totalPages = 6)
        assertEquals(4, updated.currentPage)
        assertEquals(6, updated.totalPages)

        val history = repository.observeHistory().first().single()
        assertEquals(comic.title, history.comicTitle)
        assertEquals(chapter.title, history.chapterTitle)
        assertEquals(4, history.currentPage)
        assertEquals(6, history.totalPages)

        val reopened = repository.recordChapterOpened(comic, chapter, totalPages = 6)
        assertEquals(4, reopened.currentPage)

        // Re-entering a detail page refreshes comic/chapter metadata. This
        // must not delete the favorite or its reading progress via cascades.
        repository.saveComic(detail)
        assertEquals(1, repository.observeLibrary().first().size)
        assertEquals(1, repository.observeHistory().first().size)
        assertEquals(4, repository.observeHistory().first().single().currentPage)

        repository.setSaved(comic, saved = false)
        assertTrue(repository.observeLibrary().first().isEmpty())
        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun `persists local comics, deduplicates by hash, resumes and deletes`() = runBlocking {
        val localRepository = RoomLocalComicRepository(database.libraryDao())
        val comic = LocalComic(
            id = "local-1",
            title = "本地测试",
            fileName = "pages.zip",
            format = "CBZ",
            localPath = "/data/local-1",
            coverPath = "/data/local-1/pages/00001.png",
            pageCount = 8,
            currentPage = 1,
            createdAt = 1L,
            updatedAt = 1L,
            fileSize = 1024L,
            fileHash = "sha256-test"
        )

        localRepository.insert(comic)
        assertEquals(comic.id, localRepository.observeLocalComics().first().single().id)
        assertEquals(comic.id, localRepository.findByHash("sha256-test")?.id)

        val resumed = localRepository.updateProgress(comic.id, 5)
        assertEquals(5, resumed?.currentPage)
        assertEquals(5, localRepository.findById(comic.id)?.currentPage)

        localRepository.delete(comic.id)
        assertTrue(localRepository.observeLocalComics().first().isEmpty())
        assertEquals(null, localRepository.findByHash("sha256-test"))
    }
}
