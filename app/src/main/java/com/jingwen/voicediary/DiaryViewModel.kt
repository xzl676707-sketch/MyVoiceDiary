package com.jingwen.voicediary

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jingwen.voicediary.data.DiaryDatabase
import com.jingwen.voicediary.data.ComputerSyncClient
import com.jingwen.voicediary.data.DiaryEntity
import com.jingwen.voicediary.data.DiaryMediaEntity
import com.jingwen.voicediary.data.DiaryMediaFiles
import com.jingwen.voicediary.data.DiaryRepository
import com.jingwen.voicediary.data.SampleDiaries
import com.jingwen.voicediary.data.visibleDiaries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = DiaryDatabase.getInstance(application).diaryDao()
    private val repository = DiaryRepository(dao)
    private val syncClient = ComputerSyncClient(application, dao)
    private val preferences = application.getSharedPreferences("diary-display", 0)
    private val mediaFiles = DiaryMediaFiles(application)
    private val preview = MutableStateFlow(preferences.getBoolean("show-samples", true))
    private val times = MutableStateFlow(preferences.getBoolean("show-times", true))
    private val hidden = preferences.getStringSet("hidden-samples", emptySet()).orEmpty()
    private val samples = MutableStateFlow(
        SampleDiaries.create().filter { it.id.toString() !in hidden }
    )

    val showSamples: StateFlow<Boolean> = preview
    val showTimes: StateFlow<Boolean> = times
    private val sync = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = sync
    private val computer = MutableStateFlow(preferences.getString("computer-address", "").orEmpty())
    private val pairing = MutableStateFlow(preferences.getString("computer-code", "").orEmpty())
    val computerAddress: StateFlow<String> = computer
    val pairingCode: StateFlow<String> = pairing

    val diaries: StateFlow<List<DiaryEntity>> = combine(
        repository.observeAll(), preview, samples
    ) { real, show, demo ->
        val hiddenIds = preferences.getStringSet("hidden-samples", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()
        visibleDiaries(real, demo, show, hiddenIds)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        visibleDiaries(emptyList(), samples.value, preview.value))

    fun setShowSamples(value: Boolean) {
        preferences.edit().putBoolean("show-samples", value).apply()
        preview.value = value
    }

    fun setShowTimes(value: Boolean) {
        preferences.edit().putBoolean("show-times", value).apply()
        times.value = value
    }

    fun setComputerAddress(value: String) {
        computer.value = value
        preferences.edit().putString("computer-address", value).apply()
    }

    fun setPairingCode(value: String) {
        val digits = value.filter(Char::isDigit).take(6)
        pairing.value = digits
        preferences.edit().putString("computer-code", digits).apply()
    }

    fun findComputer() {
        if (sync.value.running) return
        sync.value = SyncUiState(running = true, message = "正在同一 Wi-Fi 下寻找电脑…")
        viewModelScope.launch {
            runCatching { syncClient.discover() }
                .onSuccess { found ->
                    setComputerAddress(found.address)
                    sync.value = SyncUiState(message = "已找到：${found.name}（${found.address}）")
                }
                .onFailure { error ->
                    sync.value = SyncUiState(message = syncError(error, "没有找到电脑。请确认电脑程序已启动，且手机和电脑连接同一个 Wi-Fi。"), isError = true)
                }
        }
    }

    fun backupToComputer() {
        if (sync.value.running) return
        sync.value = SyncUiState(running = true, message = "正在把手机日记备份到电脑…")
        viewModelScope.launch {
            runCatching { syncClient.upload(computer.value, pairing.value) }
                .onSuccess { result ->
                    sync.value = SyncUiState(message = "备份完成：电脑现在保存 ${result.diaryCount} 篇日记和 ${result.mediaCount} 个图片或视频。")
                }
                .onFailure { error ->
                    sync.value = SyncUiState(message = syncError(error, "无法连接电脑，请检查电脑程序、地址、配对码和 Wi-Fi。"), isError = true)
                }
        }
    }

    fun restoreFromComputer() {
        if (sync.value.running) return
        sync.value = SyncUiState(running = true, message = "正在从电脑下载并校验全部日记…")
        viewModelScope.launch {
            runCatching { syncClient.downloadAndRestore(computer.value, pairing.value) }
                .onSuccess { result ->
                    sync.value = SyncUiState(message = "恢复完成：手机现在有 ${result.diaryCount} 篇日记和 ${result.mediaCount} 个图片或视频。")
                }
                .onFailure { error ->
                    sync.value = SyncUiState(message = syncError(error, "从电脑恢复失败，手机原有日记没有改变。"), isError = true)
                }
        }
    }

    fun clearSyncMessage() {
        if (!sync.value.running) sync.value = SyncUiState()
    }

    private fun syncError(error: Throwable, fallback: String): String =
        if (error is IllegalArgumentException) error.message?.takeIf(String::isNotBlank) ?: fallback else fallback

    suspend fun getDiary(id: Long): DiaryEntity? =
        repository.getById(id) ?: if (id < 0) samples.value.firstOrNull { it.id == id } else null

    suspend fun getMedia(id: Long): List<DiaryMediaEntity> = repository.getMedia(id)

    fun mediaPath(item: DiaryMediaEntity): String = mediaFiles.file(item.fileName).absolutePath

    fun mediaThumbnailPath(item: DiaryMediaEntity): String =
        if (item.thumbnailName.isBlank()) "" else mediaFiles.thumbnail(item.thumbnailName).absolutePath

    fun mediaUri(item: DiaryMediaEntity): Uri = mediaFiles.shareUri(item)

    suspend fun saveDiary(id: Long?, title: String, content: String, diaryDate: String, createdAt: Long?): Long =
        repository.save(id, title, content, diaryDate, createdAt)

    suspend fun deleteDiary(diary: DiaryEntity) {
        if (diary.id < 0) {
            // Delete any persisted edit; the hidden marker prevents the fixture from reappearing.
            repository.getById(diary.id)?.let {
                val media = repository.getMedia(diary.id)
                repository.delete(it)
                mediaFiles.deleteAll(media)
            }
            val deleted = preferences.getStringSet("hidden-samples", emptySet()).orEmpty() + diary.id.toString()
            preferences.edit().putStringSet("hidden-samples", deleted).apply()
            samples.value = samples.value.filterNot { it.id == diary.id }
        } else {
            val media = repository.getMedia(diary.id)
            repository.delete(diary)
            mediaFiles.deleteAll(media)
        }
    }
}
