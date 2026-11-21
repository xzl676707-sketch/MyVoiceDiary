package com.jingwen.voicediary.data

/** The diary date is retained; editing never changes a diary's position in its day. */
val diaryLatestFirst: Comparator<DiaryEntity> =
    compareByDescending<DiaryEntity> { it.diaryDate }
        .thenByDescending { it.createdAt }
        .thenByDescending { it.id }

/** Saved versions replace matching fixtures instead of becoming extra cards. */
fun visibleDiaries(
    stored: List<DiaryEntity>,
    samples: List<DiaryEntity>,
    showSamples: Boolean,
    hiddenSampleIds: Set<Long> = emptySet()
): List<DiaryEntity> {
    val persistedIds = stored.map { it.id }.toSet()
    val savedVisible = stored.filter {
        it.id > 0 || (showSamples && it.id !in hiddenSampleIds)
    }
    val unsavedSamples = if (showSamples) samples.filter {
        it.id !in persistedIds && it.id !in hiddenSampleIds
    } else emptyList()
    return (savedVisible + unsavedSamples).sortedWith(diaryLatestFirst)
}
