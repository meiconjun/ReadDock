package com.comichub.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ReadingHistoryRow(
    val sourceId: String,
    val comicId: String,
    val chapterId: String,
    val currentPage: Int,
    val totalPages: Int,
    val lastReadAt: Long,
    val completed: Boolean,
    val comicTitle: String,
    val coverUrl: String?,
    val chapterTitle: String,
    val chapterNumber: Int
)

data class LibraryRow(
    val sourceId: String,
    val comicId: String,
    val title: String,
    val coverUrl: String?,
    val tags: String,
    val author: String?,
    val description: String?,
    val addedAt: Long
)

@Dao
interface LibraryDao {
    @Query(
        """
        SELECT
            c.sourceId AS sourceId,
            c.comicId AS comicId,
            c.title AS title,
            c.coverUrl AS coverUrl,
            c.tags AS tags,
            c.author AS author,
            c.description AS description,
            l.addedAt AS addedAt
        FROM comics c
        INNER JOIN library_entries l
            ON c.sourceId = l.sourceId AND c.comicId = l.comicId
        ORDER BY l.addedAt DESC
        """
    )
    fun observeLibrary(): Flow<List<LibraryRow>>

    @Query(
        """
        SELECT
            p.sourceId AS sourceId,
            p.comicId AS comicId,
            p.chapterId AS chapterId,
            p.currentPage AS currentPage,
            p.totalPages AS totalPages,
            p.lastReadAt AS lastReadAt,
            p.completed AS completed,
            c.title AS comicTitle,
            c.coverUrl AS coverUrl,
            ch.title AS chapterTitle,
            ch.number AS chapterNumber
        FROM reading_progress p
        INNER JOIN comics c
            ON c.sourceId = p.sourceId AND c.comicId = p.comicId
        INNER JOIN chapters ch
            ON ch.sourceId = p.sourceId
            AND ch.comicId = p.comicId
            AND ch.chapterId = p.chapterId
        ORDER BY p.lastReadAt DESC
        """
    )
    fun observeHistory(): Flow<List<ReadingHistoryRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComic(comic: ComicEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertComicIfMissing(comic: ComicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryEntry(entry: LibraryEntryEntity)

    @Query("DELETE FROM library_entries WHERE sourceId = :sourceId AND comicId = :comicId")
    suspend fun deleteLibraryEntry(sourceId: String, comicId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query(
        "SELECT * FROM reading_progress " +
            "WHERE sourceId = :sourceId AND comicId = :comicId AND chapterId = :chapterId " +
            "LIMIT 1"
    )
    suspend fun findProgress(sourceId: String, comicId: String, chapterId: String): ReadingProgressEntity?
}
