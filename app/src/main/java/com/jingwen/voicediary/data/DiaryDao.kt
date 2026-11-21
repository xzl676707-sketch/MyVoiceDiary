package com.jingwen.voicediary.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY diaryDate DESC, createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryEntity?

    @Query("SELECT * FROM diaries ORDER BY id")
    suspend fun getAllOnce(): List<DiaryEntity>

    @Query("SELECT * FROM diary_media ORDER BY diaryId, sortIndex, id")
    suspend fun getAllMediaOnce(): List<DiaryMediaEntity>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM diaries")
    suspend fun getMaxUpdatedAt(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(diary: DiaryEntity): Long

    @Update
    suspend fun update(diary: DiaryEntity): Int

    @Delete
    suspend fun delete(diary: DiaryEntity)

    @Query("SELECT * FROM diary_media WHERE diaryId = :diaryId ORDER BY sortIndex, id")
    suspend fun getMedia(diaryId: Long): List<DiaryMediaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedia(media: List<DiaryMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllDiaries(diaries: List<DiaryEntity>)

    @Query("DELETE FROM diary_media WHERE diaryId = :diaryId")
    suspend fun deleteMediaForDiary(diaryId: Long)

    @Query("UPDATE diaries SET coverThumbnailName = :thumbnailName, coverMediaType = :mediaType, mediaCount = :count WHERE id = :diaryId")
    suspend fun updateMediaSummary(diaryId: Long, thumbnailName: String, mediaType: String, count: Int)

    @Query("DELETE FROM diaries")
    suspend fun deleteAllDiaries()

    @Transaction
    suspend fun replaceAll(diaries: List<DiaryEntity>, media: List<DiaryMediaEntity>) {
        deleteAllDiaries()
        if (diaries.isNotEmpty()) insertAllDiaries(diaries)
        if (media.isNotEmpty()) insertMedia(media)
    }

    @Transaction
    suspend fun replaceMedia(diaryId: Long, media: List<DiaryMediaEntity>) {
        deleteMediaForDiary(diaryId)
        if (media.isNotEmpty()) insertMedia(media.mapIndexed { index, item ->
            item.copy(id = 0, diaryId = diaryId, sortIndex = index)
        })
        val cover = media.firstOrNull()
        updateMediaSummary(diaryId, cover?.thumbnailName.orEmpty(), cover?.type.orEmpty(), media.size)
    }
}
