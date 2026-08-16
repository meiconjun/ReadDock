package com.comichub.data

import com.comichub.source.api.Chapter
import com.comichub.source.api.ComicDetail
import com.comichub.source.api.ComicSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LibraryComic(
    val sourceId: String,
    val comicId: String,
    val title: String,
    val coverUrl: String?,
    val tags: List<String>,
    val author: String?,
    val description: String?,
    val addedAt: Long
) {
    fun toSummary(): ComicSummary = ComicSummary(
        id = comicId,
        sourceId = sourceId,
        title = title,
        coverUrl = coverUrl,
        tags = tags
    )
}

data class ReadingProgress(
    val sourceId: String,
    val comicId: String,
    val chapterId: String,
    val currentPage: Int,
    val totalPages: Int,
    val lastReadAt: Long,
    val completed: Boolean
)

data class ReadingHistoryItem(
    val sourceId: String,
    val comicId: String,
    val chapterId: String,
    val comicTitle: String,
    val coverUrl: String?,
    val chapterTitle: String,
    val chapterNumber: Int,
    val currentPage: Int,
    val totalPages: Int,
    val lastReadAt: Long,
    val completed: Boolean
) {
    fun toSummary(): ComicSummary = ComicSummary(
        id = comicId,
        sourceId = sourceId,
        title = comicTitle,
        coverUrl = coverUrl
    )
}

data class LocalComic(
    val id: String,
    val title: String,
    val fileName: String,
    val format: String,
    val localPath: String,
    val coverPath: String?,
    val pageCount: Int,
    val currentPage: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val fileSize: Long,
    val fileHash: String
)

interface LocalComicRepository {
    fun observeLocalComics(): Flow<List<LocalComic>>
    suspend fun findById(id: String): LocalComic?
    suspend fun findByHash(fileHash: String): LocalComic?
    suspend fun insert(comic: LocalComic)
    suspend fun updateProgress(id: String, currentPage: Int): LocalComic?
    suspend fun delete(id: String)
}

interface LibraryRepository {
    fun observeLibrary(): Flow<List<LibraryComic>>

    fun observeHistory(): Flow<List<ReadingHistoryItem>>

    suspend fun saveComic(detail: ComicDetail)

    suspend fun setSaved(comic: ComicSummary, saved: Boolean)

    suspend fun recordChapterOpened(
        comic: ComicSummary,
        chapter: Chapter,
        totalPages: Int
    ): ReadingProgress

    suspend fun updateProgress(
        chapter: Chapter,
        currentPage: Int,
        totalPages: Int
    ): ReadingProgress

    suspend fun getProgress(chapter: Chapter): ReadingProgress?
}

class RoomLibraryRepository(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis
) : LibraryRepository {
    companion object {
        fun create(context: android.content.Context): RoomLibraryRepository =
            RoomLibraryRepository(PageLoomDatabase.create(context).libraryDao())
    }

    override fun observeLibrary(): Flow<List<LibraryComic>> = dao.observeLibrary().map { comics ->
        comics.map { it.toLibraryComic() }
    }

    override fun observeHistory(): Flow<List<ReadingHistoryItem>> = dao.observeHistory().map { rows ->
        rows.map { it.toHistoryItem() }
    }

    override suspend fun saveComic(detail: ComicDetail) {
        val now = clock()
        // Do not use SQLite REPLACE here. REPLACE deletes the old parent row
        // before inserting the new one, which triggers ON DELETE CASCADE on
        // library_entries and reading_progress.
        dao.insertComicIfMissing(detail.summary.toEntity(now, detail.author, detail.description))
        dao.updateComic(detail.summary.toEntity(now, detail.author, detail.description))

        val chapters = detail.chapters.map { it.toEntity(now) }
        dao.insertChaptersIfMissing(chapters)
        dao.updateChapters(chapters)
    }

    override suspend fun setSaved(comic: ComicSummary, saved: Boolean) {
        val now = clock()
        dao.insertComicIfMissing(comic.toEntity(now))
        if (saved) {
            dao.upsertLibraryEntry(
                LibraryEntryEntity(
                    sourceId = comic.sourceId,
                    comicId = comic.id,
                    addedAt = now
                )
            )
        } else {
            dao.deleteLibraryEntry(comic.sourceId, comic.id)
        }
    }

    override suspend fun recordChapterOpened(
        comic: ComicSummary,
        chapter: Chapter,
        totalPages: Int
    ): ReadingProgress {
        dao.insertComicIfMissing(comic.toEntity(clock()))
        val existing = dao.findProgress(chapter.sourceId, chapter.comicId, chapter.id)
        val pages = totalPages.coerceAtLeast(1)
        val progress = ReadingProgressEntity(
            sourceId = chapter.sourceId,
            comicId = chapter.comicId,
            chapterId = chapter.id,
            currentPage = existing?.currentPage?.coerceIn(1, pages) ?: 1,
            totalPages = pages,
            lastReadAt = clock(),
            completed = existing?.completed == true && existing.currentPage >= pages
        )
        dao.upsertProgress(progress)
        return progress.toProgress()
    }

    override suspend fun updateProgress(
        chapter: Chapter,
        currentPage: Int,
        totalPages: Int
    ): ReadingProgress {
        val pages = totalPages.coerceAtLeast(1)
        val progress = ReadingProgressEntity(
            sourceId = chapter.sourceId,
            comicId = chapter.comicId,
            chapterId = chapter.id,
            currentPage = currentPage.coerceIn(1, pages),
            totalPages = pages,
            lastReadAt = clock(),
            completed = currentPage >= pages
        )
        dao.upsertProgress(progress)
        return progress.toProgress()
    }

    override suspend fun getProgress(chapter: Chapter): ReadingProgress? =
        dao.findProgress(chapter.sourceId, chapter.comicId, chapter.id)?.toProgress()
}

class RoomLocalComicRepository(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis
) : LocalComicRepository {
    companion object {
        fun create(context: android.content.Context): RoomLocalComicRepository =
            RoomLocalComicRepository(PageLoomDatabase.create(context).libraryDao())
    }

    override fun observeLocalComics(): Flow<List<LocalComic>> =
        dao.observeLocalComics().map { rows -> rows.map(LocalComicEntity::toLocalComic) }

    override suspend fun findById(id: String): LocalComic? = dao.findLocalComic(id)?.toLocalComic()

    override suspend fun findByHash(fileHash: String): LocalComic? =
        dao.findLocalComicByHash(fileHash)?.toLocalComic()

    override suspend fun insert(comic: LocalComic) {
        dao.upsertLocalComic(comic.toEntity())
    }

    override suspend fun updateProgress(id: String, currentPage: Int): LocalComic? {
        val comic = dao.findLocalComic(id) ?: return null
        val page = currentPage.coerceIn(1, comic.pageCount.coerceAtLeast(1))
        dao.updateLocalProgress(id, page, clock())
        return dao.findLocalComic(id)?.toLocalComic()
    }

    override suspend fun delete(id: String) {
        dao.deleteLocalComic(id)
    }
}

private fun LocalComic.toEntity() = LocalComicEntity(
    id = id,
    title = title,
    fileName = fileName,
    format = format,
    localPath = localPath,
    coverPath = coverPath,
    pageCount = pageCount,
    currentPage = currentPage,
    createdAt = createdAt,
    updatedAt = updatedAt,
    fileSize = fileSize,
    fileHash = fileHash
)

private fun LocalComicEntity.toLocalComic() = LocalComic(
    id = id,
    title = title,
    fileName = fileName,
    format = format,
    localPath = localPath,
    coverPath = coverPath,
    pageCount = pageCount,
    currentPage = currentPage,
    createdAt = createdAt,
    updatedAt = updatedAt,
    fileSize = fileSize,
    fileHash = fileHash
)

private fun ComicSummary.toEntity(
    now: Long,
    author: String? = null,
    description: String? = null
) = ComicEntity(
    sourceId = sourceId,
    comicId = id,
    title = title,
    coverUrl = coverUrl,
    tags = tags.joinToString(TAG_SEPARATOR),
    author = author,
    description = description,
    updatedAt = now
)

private fun Chapter.toEntity(now: Long) = ChapterEntity(
    sourceId = sourceId,
    comicId = comicId,
    chapterId = id,
    title = title,
    number = number,
    updatedAt = now
)

private fun LibraryRow.toLibraryComic() = LibraryComic(
    sourceId = sourceId,
    comicId = comicId,
    title = title,
    coverUrl = coverUrl,
    tags = tags.splitTags(),
    author = author,
    description = description,
    addedAt = addedAt
)

private fun ReadingHistoryRow.toHistoryItem() = ReadingHistoryItem(
    sourceId = sourceId,
    comicId = comicId,
    chapterId = chapterId,
    comicTitle = comicTitle,
    coverUrl = coverUrl,
    chapterTitle = chapterTitle,
    chapterNumber = chapterNumber,
    currentPage = currentPage,
    totalPages = totalPages,
    lastReadAt = lastReadAt,
    completed = completed
)

private fun ReadingProgressEntity.toProgress() = ReadingProgress(
    sourceId = sourceId,
    comicId = comicId,
    chapterId = chapterId,
    currentPage = currentPage,
    totalPages = totalPages,
    lastReadAt = lastReadAt,
    completed = completed
)

private fun String.splitTags(): List<String> =
    if (isEmpty()) emptyList() else split(TAG_SEPARATOR)

private const val TAG_SEPARATOR = "\u001F"
