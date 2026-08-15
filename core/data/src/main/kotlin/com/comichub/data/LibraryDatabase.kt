package com.comichub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ComicEntity::class,
        ChapterEntity::class,
        LibraryEntryEntity::class,
        ReadingProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ComicHubDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): ComicHubDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ComicHubDatabase::class.java,
                "comichub.db"
            ).build()
    }
}
