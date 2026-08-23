package com.readdock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ComicEntity::class,
        ChapterEntity::class,
        LibraryEntryEntity::class,
        ReadingProgressEntity::class,
        LocalComicEntity::class,
        LocalCategoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ReadDockDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): ReadDockDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ReadDockDatabase::class.java,
                "readdock.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_comics (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        format TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        coverPath TEXT,
                        pageCount INTEGER NOT NULL,
                        currentPage INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        fileSize INTEGER NOT NULL,
                        fileHash TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_comics_fileHash ON local_comics(fileHash)")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_comics ADD COLUMN hasBeenOpened INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_comics ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_comics ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_comics ADD COLUMN categoryIds TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
