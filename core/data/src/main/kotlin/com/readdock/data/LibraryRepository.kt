package com.readdock.data

import com.readdock.source.api.Chapter
import com.readdock.source.api.ComicDetail
import com.readdock.source.api.ComicSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

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
    val fileHash: String,
    val hasBeenOpened: Boolean = false,
    val completed: Boolean = false,
    val lastReadAt: Long = 0L,
    val categoryIds: List<String> = emptyList()
)

data class LocalCategory(
    val id: String,
    val name: String,
    val createdAt: Long
)

interface LocalComicRepository {
    fun observeLocalComics(): Flow<List<LocalComic>>
    suspend fun findById(id: String): LocalComic?
    suspend fun findByHash(fileHash: String): LocalComic?
    suspend fun insert(comic: LocalComic)
    suspend fun updateProgress(id: String, currentPage: Int): LocalComic?
    suspend fun delete(id: String)

    fun observeCategories(): Flow<List<LocalCategory>> = flowOf(emptyList())
    suspend fun createCategory(name: String): LocalCategory? = null
    suspend fun renameCategory(id: String, name: String): LocalCategory? = null
    suspend fun deleteCategory(id: String) = Unit
    suspend fun setCategoryMembership(comicId: String, categoryId: String, included: Boolean) = Unit
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

    suspend fun clearHistory() = Unit
}

class RoomLibraryRepository(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis
) : LibraryRepository {
    companion object {
        fun create(context: android.content.Context): RoomLibraryRepository =
            RoomLibraryRepository(ReadDockDatabase.create(context).libraryDao())
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

    override suspend fun clearHistory() {
        dao.deleteAllHistory()
    }
}

class RoomLocalComicRepository(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis
) : LocalComicRepository {
    companion object {
        fun create(context: android.content.Context): RoomLocalComicRepository =
            RoomLocalComicRepository(ReadDockDatabase.create(context).libraryDao())
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
        dao.updateLocalProgress(id, page, clock(), page >= comic.pageCount.coerceAtLeast(1))
        return dao.findLocalComic(id)?.toLocalComic()
    }

    override suspend fun delete(id: String) {
        dao.deleteLocalComic(id)
    }

    override fun observeCategories(): Flow<List<LocalCategory>> =
        dao.observeLocalCategories().map { rows -> rows.map(LocalCategoryEntity::toLocalCategory) }

    override suspend fun createCategory(name: String): LocalCategory? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null
        val category = LocalCategory(
            id = "category-${UUID.randomUUID()}",
            name = normalized,
            createdAt = clock()
        )
        dao.insertLocalCategory(category.toEntity())
        return category
    }

    override suspend fun renameCategory(id: String, name: String): LocalCategory? {
        val normalized = name.trim()
        val current = dao.findLocalCategory(id) ?: return null
        if (normalized.isEmpty()) return current.toLocalCategory()
        val renamed = current.copy(name = normalized)
        dao.updateLocalCategory(renamed)
        return renamed.toLocalCategory()
    }

    override suspend fun deleteCategory(id: String) {
        dao.observeLocalComics().first().forEach { comic ->
            val ids = comic.categoryIds.splitIds().filterNot { it == id }.joinToString(CATEGORY_SEPARATOR)
            dao.updateLocalComicCategories(comic.id, ids)
        }
        dao.deleteLocalCategory(id)
    }

    override suspend fun setCategoryMembership(comicId: String, categoryId: String, included: Boolean) {
        val comic = dao.findLocalComic(comicId) ?: return
        val ids = comic.categoryIds.splitIds().toMutableSet()
        if (included) ids += categoryId else ids -= categoryId
        dao.updateLocalComicCategories(comicId, ids.joinToString(CATEGORY_SEPARATOR))
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
    fileHash = fileHash,
    hasBeenOpened = hasBeenOpened,
    completed = completed,
    lastReadAt = lastReadAt,
    categoryIds = categoryIds.joinToString(CATEGORY_SEPARATOR)
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
    fileHash = fileHash,
    hasBeenOpened = hasBeenOpened,
    completed = completed,
    lastReadAt = lastReadAt,
    categoryIds = categoryIds.splitIds()
)

private fun LocalCategory.toEntity() = LocalCategoryEntity(id, name, createdAt)

private fun LocalCategoryEntity.toLocalCategory() = LocalCategory(id, name, createdAt)

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
private const val CATEGORY_SEPARATOR = "\u001E"

private fun String.splitIds(): List<String> =
    if (isEmpty()) emptyList() else split(CATEGORY_SEPARATOR).filter(String::isNotBlank)
