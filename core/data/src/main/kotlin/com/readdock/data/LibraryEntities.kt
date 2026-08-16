package com.readdock.data

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "comics", primaryKeys = ["sourceId", "comicId"])
data class ComicEntity(
    val sourceId: String,
    val comicId: String,
    val title: String,
    val coverUrl: String?,
    val tags: String,
    val author: String?,
    val description: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["sourceId", "comicId", "chapterId"],
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["sourceId", "comicId"],
            childColumns = ["sourceId", "comicId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChapterEntity(
    val sourceId: String,
    val comicId: String,
    val chapterId: String,
    val title: String,
    val number: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "library_entries",
    primaryKeys = ["sourceId", "comicId"],
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["sourceId", "comicId"],
            childColumns = ["sourceId", "comicId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LibraryEntryEntity(
    val sourceId: String,
    val comicId: String,
    val addedAt: Long
)

@Entity(
    tableName = "reading_progress",
    primaryKeys = ["sourceId", "comicId", "chapterId"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["sourceId", "comicId", "chapterId"],
            childColumns = ["sourceId", "comicId", "chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReadingProgressEntity(
    val sourceId: String,
    val comicId: String,
    val chapterId: String,
    val currentPage: Int,
    val totalPages: Int,
    val lastReadAt: Long,
    val completed: Boolean
)

/**
 * A locally imported comic is deliberately independent from the online comic
 * tables.  It has no source/plugin identity and can therefore never be routed
 * through an online ComicSource or WebView.
 */
@Entity(
    tableName = "local_comics",
    indices = [androidx.room.Index(value = ["fileHash"])]
)
data class LocalComicEntity(
    @androidx.room.PrimaryKey val id: String,
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
