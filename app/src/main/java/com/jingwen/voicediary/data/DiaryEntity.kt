package com.jingwen.voicediary.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "diaries",
    indices = [Index("diaryDate"), Index("updatedAt"), Index(value = ["uuid"], unique = true)]
)
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String,
    /** Ordered text/media layout. Blank means legacy text followed by media. */
    val layoutJson: String = "",
    val coverThumbnailName: String = "",
    val coverMediaType: String = "",
    val mediaCount: Int = 0,
    val entryType: String = "DIARY",
    /** ISO-8601 date, for example 2026-09-16. */
    val diaryDate: String,
    val createdAt: Long,
    val updatedAt: Long
)
