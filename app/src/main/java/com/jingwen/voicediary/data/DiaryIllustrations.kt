package com.jingwen.voicediary.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Deletions of the preview's standalone illustrations survive app restarts. */
class DiaryIllustrations(private val preferences: SharedPreferences) {
    private val removed = MutableStateFlow(
        preferences.getStringSet("removed-illustrations", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()
    )
    val hiddenIds: StateFlow<Set<Long>> = removed

    fun isRemoved(id: Long): Boolean = id in removed.value

    fun setRemoved(id: Long, deleted: Boolean) {
        val updated = if (deleted) removed.value + id else removed.value - id
        if (updated == removed.value) return
        check(preferences.edit().putStringSet("removed-illustrations",
            updated.map { it.toString() }.toSet()).commit()) { "表情删除状态未能保存，请重试。" }
        removed.value = updated
    }

    companion object {
        @Volatile private var instance: DiaryIllustrations? = null
        fun get(context: Context): DiaryIllustrations = instance ?: synchronized(this) {
            instance ?: DiaryIllustrations(context.applicationContext.getSharedPreferences("diary-display", 0))
                .also { instance = it }
        }
    }
}
