package com.jingwen.voicediary

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jingwen.voicediary.data.DiaryDatabase
import com.jingwen.voicediary.data.DiaryRepository
import com.jingwen.voicediary.data.DiaryIllustrations
import com.jingwen.voicediary.data.DiaryMediaEntity
import com.jingwen.voicediary.data.DiaryMediaFiles
import com.jingwen.voicediary.data.DiaryMediaType
import com.jingwen.voicediary.data.DiaryBlockType
import com.jingwen.voicediary.data.DiaryContentBlock
import com.jingwen.voicediary.data.DiaryContentLayout
import com.jingwen.voicediary.data.StickerAsset
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EditorState(
    val id: Long? = null,
    val title: String = "",
    val content: String = "",
    val date: String = LocalDate.now().toString(),
    val createdAt: Long = 0L,
    val ready: Boolean = false,
    val dirty: Boolean = false,
    val sample: Boolean = false,
    val illustrationDeleted: Boolean = false,
    val media: List<DiaryMediaEntity> = emptyList(),
    val blocks: List<DiaryContentBlock> = listOf(DiaryContentBlock(type = DiaryBlockType.TEXT)),
    val focusTextBlockUuid: String? = null,
    val focusRequestId: Long = 0L,
    val focusCursorOffset: Int = 0,
    val status: String = "读取中…",
    val error: String? = null,
    val closing: Boolean = false
)

/** Keeps one editor session across rotation; serializes writes independently of the debounce timer. */
class EditorViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = DiaryRepository(DiaryDatabase.getInstance(application).diaryDao())
    private val illustrations = DiaryIllustrations.get(application)
    private val mediaFiles = DiaryMediaFiles(application)
    private val mutex = Mutex()
    private var debounce: Job? = null
    private var loading = false
    private var revision = 0L
    private var activeTextBlockId: String? = null
    private var activeTextOffset: Int = 0
    private val pendingMediaDeletes = mutableListOf<DiaryMediaEntity>()
    var state by mutableStateOf(EditorState())
        private set

    fun load(sourceId: Long?, diaries: DiaryViewModel) {
        if (loading || state.ready) return
        loading = true
        if (saved.get<Boolean>("ready") == true) {
            val restoredContent = saved["content"] ?: ""
            state = EditorState(
                id = saved.get<Long>("savedId") ?: sourceId,
                title = saved["title"] ?: "", content = restoredContent,
                blocks = DiaryContentLayout.decode(saved["layoutJson"] ?: "", restoredContent, emptyList()),
                date = saved["date"] ?: LocalDate.now().toString(), createdAt = saved["createdAt"] ?: 0L,
                ready = true, dirty = saved["dirty"] ?: false, sample = saved["sample"] ?: false,
                illustrationDeleted = saved["illustrationDeleted"] ?: false,
                status = if (saved.get<Boolean>("dirty") == true) "等待保存…" else "已保存"
            )
            loading = false
            state.id?.let { savedId ->
                viewModelScope.launch {
                    val media = repository.getMedia(savedId)
                    val decoded = DiaryContentLayout.decode(saved["layoutJson"] ?: "", state.content, media)
                    applyLoadedLayout(decoded, media)
                    ensureInsertionPoint()
                }
            }
            if (state.dirty) scheduleSave()
            return
        }
        viewModelScope.launch {
            try {
                val diary = sourceId?.let { diaries.getDiary(it) }
                if (sourceId != null && diary == null) {
                    state = state.copy(status = "无法打开日记", error = "日记不存在，请返回后重试。")
                } else {
                    state = if (diary == null) EditorState(ready = true, status = "自动保存已开启")
                    else {
                        val media = repository.getMedia(diary.id)
                        val decoded = DiaryContentLayout.decode(diary.layoutJson, diary.content, media)
                        val migration = DiaryContentLayout.migrateLegacyStickers(decoded, media)
                        if (migration.removedStickerMedia.isNotEmpty()) {
                            pendingMediaDeletes += migration.removedStickerMedia
                        }
                        EditorState(
                            id = diary.id, title = diary.title,
                            content = DiaryContentLayout.plainText(migration.blocks),
                            date = diary.diaryDate, createdAt = diary.createdAt,
                            media = media - migration.removedStickerMedia.toSet(),
                            blocks = migration.blocks,
                            ready = true, dirty = migration.removedStickerMedia.isNotEmpty(), sample = diary.id < 0,
                            illustrationDeleted = illustrations.isRemoved(diary.id), status = "已保存")
                    }
                    ensureInsertionPoint()
                    rememberDraft()
                    if (state.dirty) scheduleSave()
                }
            } catch (_: Exception) {
                state = state.copy(error = "读取失败，请返回后重试。", status = "读取失败")
            } finally { loading = false }
        }
    }

    fun setTitle(value: String) = change { it.copy(title = value) }
    fun setDate(value: String) = change { it.copy(date = value) }
    fun removeIllustration() = change { it.copy(illustrationDeleted = true) }
    fun restoreIllustration() = change { it.copy(illustrationDeleted = false) }

    fun setBlockText(blockUuid: String, value: String, cursor: Int) {
        activeTextBlockId = blockUuid
        activeTextOffset = cursor.coerceIn(0, value.length)
        change { current ->
            val blocks = current.blocks.map { block ->
                if (block.blockUuid == blockUuid && block.type == DiaryBlockType.TEXT) block.copy(text = value) else block
            }
            current.copy(blocks = blocks, content = DiaryContentLayout.plainText(blocks))
        }
    }

    fun setInsertionPoint(blockUuid: String, cursor: Int) {
        val block = state.blocks.firstOrNull { it.blockUuid == blockUuid && it.type == DiaryBlockType.TEXT } ?: return
        activeTextBlockId = blockUuid
        activeTextOffset = cursor.coerceIn(0, block.text.length)
    }

    fun addMedia(uri: Uri, type: DiaryMediaType) = addMedia(listOf(uri), type)

    fun addMedia(uris: List<Uri>, type: DiaryMediaType) {
        if (uris.isEmpty()) return
        if (!state.ready || state.closing) return
        state = state.copy(status = "正在导入…", error = null)
        viewModelScope.launch {
            try {
                val imported = uris.map { mediaFiles.import(it, type) }
                change { current ->
                    val insertion = DiaryContentLayout.insertMedia(
                        current.blocks, activeTextBlockId, activeTextOffset, imported.map { it.mediaUuid }
                    )
                    activeTextBlockId = insertion.trailingTextBlockUuid
                    activeTextOffset = 0
                    current.copy(
                        media = current.media + imported,
                        blocks = insertion.blocks,
                        content = DiaryContentLayout.plainText(insertion.blocks),
                        focusTextBlockUuid = insertion.trailingTextBlockUuid,
                        focusRequestId = current.focusRequestId + 1,
                        focusCursorOffset = 0
                    )
                }
            } catch (_: Exception) {
                state = state.copy(status = "导入失败", error = "无法读取所选文件，请换一个文件重试。")
            }
        }
    }

    fun addSticker(sticker: StickerAsset) {
        if (!state.ready || state.closing) return
        change { current ->
            val insertion = DiaryContentLayout.insertInlineText(
                current.blocks, activeTextBlockId, activeTextOffset, sticker.inlineChar.toString()
            )
            activeTextBlockId = insertion.textBlockUuid
            activeTextOffset = insertion.cursorOffset
            current.copy(
                blocks = insertion.blocks,
                content = DiaryContentLayout.plainText(insertion.blocks),
                focusTextBlockUuid = insertion.textBlockUuid,
                focusRequestId = current.focusRequestId + 1,
                focusCursorOffset = insertion.cursorOffset
            )
        }
    }

    fun removeMedia(item: DiaryMediaEntity) {
        if (item !in state.media) return
        pendingMediaDeletes += item
        change { current ->
            val blocks = DiaryContentLayout.removeMedia(current.blocks, item.mediaUuid)
            ensureInsertionPoint(blocks)
            current.copy(
                media = current.media.filterNot { it.mediaUuid == item.mediaUuid },
                blocks = blocks,
                content = DiaryContentLayout.plainText(blocks)
            )
        }
    }

    fun mediaThumbnailPath(item: DiaryMediaEntity): String =
        if (item.thumbnailName.isBlank()) "" else mediaFiles.thumbnail(item.thumbnailName).absolutePath

    private fun change(update: (EditorState) -> EditorState) {
        if (!state.ready || state.closing) return
        val updated = update(state)
        if (updated == state) return
        revision++
        state = updated.copy(dirty = true, status = "等待保存…", error = null)
        rememberDraft()
        scheduleSave()
    }

    private fun scheduleSave() {
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(700)
            // A new keystroke may cancel this timer, but never an in-flight database transaction.
            flush()
        }
    }

    fun flush() {
        viewModelScope.launch { persist() }
    }

    private suspend fun persist(): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            val draft = state
            if (!draft.ready) return@withLock false
            if (!draft.dirty) return@withLock true
            if (draft.id == null && draft.title.isBlank() && draft.content.isBlank() && draft.media.isEmpty()) {
                state = state.copy(dirty = false, status = "空白日记不保存")
                rememberDraft()
                return@withLock true
            }
            val writingRevision = revision
            state = state.copy(status = "保存中…")
            val created = draft.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            try {
                val layout = DiaryContentLayout.normalize(draft.blocks)
                val orderedMediaIds = layout.filter { it.type == DiaryBlockType.MEDIA }.map { it.mediaUuid }
                val orderedMedia = orderedMediaIds.mapNotNull { uuid -> draft.media.firstOrNull { it.mediaUuid == uuid } }
                val id = repository.save(
                    draft.id, draft.title, DiaryContentLayout.plainText(layout), draft.date, created,
                    DiaryContentLayout.encode(layout)
                )
                // Capture identity immediately, even if the separate decoration preference fails.
                state = state.copy(id = id, createdAt = created)
                repository.replaceMedia(id, orderedMedia)
                pendingMediaDeletes.toList().also {
                    mediaFiles.deleteAll(it)
                    pendingMediaDeletes.removeAll(it.toSet())
                }
                illustrations.setRemoved(id, draft.illustrationDeleted)
                val changedWhileWriting = revision != writingRevision
                state = state.copy(
                    id = id, createdAt = created, sample = id < 0,
                    blocks = layout,
                    content = DiaryContentLayout.plainText(layout),
                    media = orderedMedia.mapIndexed { index, media ->
                        media.copy(diaryId = id, sortIndex = index)
                    },
                    dirty = changedWhileWriting, error = null,
                    status = if (changedWhileWriting) "等待保存…" else "已自动保存")
                rememberDraft()
                true
            } catch (_: Exception) {
                state = state.copy(status = "保存失败", error = "保存失败，请重试。内容仍保留在编辑页。", dirty = true)
                rememberDraft()
                false
            }
        }
    }

    fun finish(onSaved: () -> Unit) {
        if (state.closing) return
        if (!state.ready) { onSaved(); return }
        debounce?.cancel()
        state = state.copy(closing = true)
        viewModelScope.launch {
            if (persist()) onSaved() else state = state.copy(closing = false)
        }
    }

    private fun rememberDraft() {
        saved["ready"] = state.ready
        saved["savedId"] = state.id
        saved["title"] = state.title
        saved["content"] = state.content
        saved["layoutJson"] = DiaryContentLayout.encode(state.blocks)
        saved["date"] = state.date
        saved["createdAt"] = state.createdAt
        saved["dirty"] = state.dirty
        saved["sample"] = state.sample
        saved["illustrationDeleted"] = state.illustrationDeleted
    }

    private fun ensureInsertionPoint(blocks: List<DiaryContentBlock> = state.blocks) {
        val active = blocks.firstOrNull { it.blockUuid == activeTextBlockId && it.type == DiaryBlockType.TEXT }
            ?: blocks.lastOrNull { it.type == DiaryBlockType.TEXT }
        activeTextBlockId = active?.blockUuid
        activeTextOffset = active?.text?.length ?: 0
    }

    private fun applyLoadedLayout(decoded: List<DiaryContentBlock>, media: List<DiaryMediaEntity>) {
        val migration = DiaryContentLayout.migrateLegacyStickers(decoded, media)
        if (migration.removedStickerMedia.isNotEmpty()) pendingMediaDeletes += migration.removedStickerMedia
        state = state.copy(
            media = media - migration.removedStickerMedia.toSet(),
            blocks = migration.blocks,
            content = DiaryContentLayout.plainText(migration.blocks),
            dirty = state.dirty || migration.removedStickerMedia.isNotEmpty()
        )
        rememberDraft()
        if (state.dirty) scheduleSave()
    }
}
