package com.jingwen.voicediary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class DiaryMediaType { IMAGE, VIDEO, STICKER }

@Entity(
    tableName = "diary_media",
    foreignKeys = [ForeignKey(
        entity = DiaryEntity::class,
        parentColumns = ["id"],
        childColumns = ["diaryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("diaryId"), Index(value = ["mediaUuid"], unique = true)]
)
data class DiaryMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val diaryId: Long,
    val mediaUuid: String = UUID.randomUUID().toString(),
    val type: String,
    /** Relative path below files/diary_media. */
    val fileName: String,
    /** Relative path below files/diary_media/thumbs. */
    val thumbnailName: String,
    val mimeType: String,
    val originalName: String,
    val sortIndex: Int,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)

val DiaryMediaEntity.mediaType: DiaryMediaType
    get() = runCatching { DiaryMediaType.valueOf(type) }.getOrDefault(DiaryMediaType.IMAGE)
