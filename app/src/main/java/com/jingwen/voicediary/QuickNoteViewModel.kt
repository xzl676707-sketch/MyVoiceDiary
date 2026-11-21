package com.jingwen.voicediary

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jingwen.voicediary.data.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val QUICK_NOTE_MEDIA_LIMIT = 9

data class QuickNoteDraft(
    val caption: String = "",
    val media: List<DiaryMediaEntity> = emptyList(),
    val ready: Boolean = false,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
) {
    val hasContent: Boolean get() = caption.isNotBlank() || media.isNotEmpty()
}

class QuickNoteViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DiaryDatabase.getInstance(application)
    private val repository = DiaryRepository(db.diaryDao())
    val files = DiaryMediaFiles(application)
    private var initialized = false
    private var cleared = false
    private var saving = false
    private var existing: DiaryEntity? = null
    private var originalMedia = emptyList<DiaryMediaEntity>()
    var draft by mutableStateOf(QuickNoteDraft())
        private set

    fun load(id: Long?) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            try {
                if (id == null) {
                    draft = QuickNoteDraft(ready = true)
                    return@launch
                }
                val note = requireNotNull(repository.getById(id)) { "这条随手记已经不存在。" }
                require(note.entryType == "QUICK") { "这不是随手记。" }
                existing = note
                originalMedia = repository.getMedia(id)
                val blocks = DiaryContentLayout.decode(note.layoutJson, note.content, originalMedia)
                val migration = DiaryContentLayout.migrateLegacyStickers(blocks, originalMedia)
                val caption = migration.blocks.filter { it.type == DiaryBlockType.TEXT && it.text.isNotEmpty() }
                    .joinToString("\n") { it.text }
                draft = QuickNoteDraft(
                    caption = caption,
                    media = originalMedia - migration.removedStickerMedia.toSet(),
                    ready = true
                )
            } catch (error: Exception) {
                draft = draft.copy(error = error.message ?: "随手记读取失败。")
            }
        }
    }

    fun setCaption(text: String) {
        if (draft.ready && !draft.busy && draft.caption != text) {
            draft = draft.copy(caption = text, dirty = true, error = null)
        }
    }

    fun importMedia(uris: List<Uri>, type: DiaryMediaType, temporary: File? = null) {
        val available = (QUICK_NOTE_MEDIA_LIMIT - draft.media.size).coerceAtLeast(0)
        if (!draft.ready || draft.busy || available == 0 || uris.isEmpty()) {
            temporary?.delete()
            return
        }
        draft = draft.copy(busy = true, error = null)
        viewModelScope.launch {
            withContext(NonCancellable) {
                val imported = mutableListOf<DiaryMediaEntity>()
                try {
                    for (uri in uris.take(available)) imported += files.import(uri, type)
                    if (cleared) files.deleteAll(imported)
                    else draft = draft.copy(
                        media = draft.media + imported,
                        dirty = true,
                        error = if (uris.size > available) "已添加前 ${available} 个附件，每条最多 9 个。" else null
                    )
                } catch (_: Exception) {
                    files.deleteAll(imported)
                    if (!cleared) draft = draft.copy(error = "无法导入所选文件，请重试。")
                } finally {
                    temporary?.delete()
                    if (!cleared) draft = draft.copy(busy = false)
                }
            }
        }
    }

    fun remove(item: DiaryMediaEntity) {
        if (!draft.ready || draft.busy || item !in draft.media) return
        if (item.diaryId == 0L) files.delete(item)
        draft = draft.copy(media = draft.media - item, dirty = true, error = null)
    }

    fun showError(message: String) {
        draft = draft.copy(error = message)
    }

    fun save(onSaved: () -> Unit) {
        val snapshot = draft
        if (!snapshot.ready || snapshot.busy || !snapshot.hasContent) return
        if (existing != null && !snapshot.dirty) {
            onSaved()
            return
        }
        draft = snapshot.copy(busy = true, error = null)
        saving = true
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    val note = existing
                    val layout = listOf(DiaryContentBlock(type = DiaryBlockType.TEXT, text = snapshot.caption)) +
                        snapshot.media.map { DiaryContentBlock(type = DiaryBlockType.MEDIA, mediaUuid = it.mediaUuid) }
                    db.withTransaction {
                        val id = repository.save(
                            note?.id, note?.title.orEmpty(), snapshot.caption,
                            note?.diaryDate ?: LocalDate.now().toString(), note?.createdAt,
                            DiaryContentLayout.encode(DiaryContentLayout.normalize(layout)), "QUICK"
                        )
                        repository.replaceMedia(id, snapshot.media)
                    }
                    val retained = snapshot.media.mapTo(hashSetOf()) { it.mediaUuid }
                    files.deleteAll(originalMedia.filterNot { it.mediaUuid in retained })
                    draft = draft.copy(media = emptyList(), dirty = false, busy = false)
                    if (!cleared) onSaved()
                } catch (_: Exception) {
                    if (cleared) files.deleteAll(snapshot.media.filter { it.diaryId == 0L })
                    else draft = draft.copy(busy = false, error = "保存失败，内容还在，可以重试。")
                } finally {
                    saving = false
                }
            }
        }
    }

    override fun onCleared() {
        cleared = true
        if (!saving) files.deleteAll(draft.media.filter { it.diaryId == 0L })
        super.onCleared()
    }
}
