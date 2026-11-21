package com.jingwen.voicediary.data

import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val dao: DiaryDao) {
    fun observeAll(): Flow<List<DiaryEntity>> = dao.observeAll()

    suspend fun getById(id: Long): DiaryEntity? = dao.getById(id)

    suspend fun getMedia(id: Long): List<DiaryMediaEntity> = dao.getMedia(id)

    suspend fun save(
        id: Long?,
        title: String,
        content: String,
        diaryDate: String,
        createdAt: Long?,
        layoutJson: String = "",
        entryType: String = "DIARY"
    ): Long {
        val now = System.currentTimeMillis()
        val existing = id?.takeIf { it != 0L }?.let { dao.getById(it) }
        val nextUpdatedAt = maxOf(now, dao.getMaxUpdatedAt() + 1L, (existing?.updatedAt ?: 0L) + 1L)
        return if (id == null || id == 0L) {
            dao.insert(
                DiaryEntity(
                    title = title,
                    content = content,
                    layoutJson = layoutJson,
                    entryType = entryType,
                    diaryDate = diaryDate,
                    createdAt = createdAt ?: now,
                    updatedAt = nextUpdatedAt
                )
            )
        } else {
            // Keep the same ID for BOTH real diaries and preview diaries.
            // First edit of a fixture materializes that ID once; later edits update it.
            val changed = DiaryEntity(
                id = id,
                uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                title = title,
                content = content,
                layoutJson = layoutJson.ifBlank { existing?.layoutJson.orEmpty() },
                entryType = existing?.entryType ?: entryType,
                coverThumbnailName = existing?.coverThumbnailName.orEmpty(),
                coverMediaType = existing?.coverMediaType.orEmpty(),
                mediaCount = existing?.mediaCount ?: 0,
                diaryDate = diaryDate,
                createdAt = existing?.createdAt ?: createdAt ?: now,
                updatedAt = nextUpdatedAt
            )
            if (existing == null) {
                check(id < 0 && SampleDiaries.visual(id) != null) { "原日记不存在，无法保存修改。" }
                dao.insert(changed)
            } else {
                check(dao.update(changed) == 1) { "原日记已删除，无法保存修改。" }
            }
            id
        }
    }

    suspend fun delete(diary: DiaryEntity) = dao.delete(diary)

    suspend fun replaceMedia(diaryId: Long, media: List<DiaryMediaEntity>) =
        dao.replaceMedia(diaryId, media)
}
