package com.jingwen.voicediary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DiaryEntity::class, DiaryMediaEntity::class], version = 4, exportSchema = false)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var instance: DiaryDatabase? = null

        fun getInstance(context: Context): DiaryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "voice-diary.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diaries ADD COLUMN coverThumbnailName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diaries ADD COLUMN coverMediaType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diaries ADD COLUMN mediaCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE diaries SET uuid = lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || " +
                        "substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || " +
                        "lower(hex(randomblob(6))) WHERE uuid = ''"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diaries_uuid ON diaries(uuid)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS diary_media (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "diaryId INTEGER NOT NULL, mediaUuid TEXT NOT NULL, type TEXT NOT NULL, " +
                        "fileName TEXT NOT NULL, thumbnailName TEXT NOT NULL, mimeType TEXT NOT NULL, " +
                        "originalName TEXT NOT NULL, sortIndex INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(diaryId) REFERENCES diaries(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diary_media_diaryId ON diary_media(diaryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diary_media_mediaUuid ON diary_media(mediaUuid)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN layoutJson TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN entryType TEXT NOT NULL DEFAULT 'DIARY'")
            }
        }
    }
}
