package com.jingwen.voicediary.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.DiaryViewModel
import com.jingwen.voicediary.data.DiaryEntity
import com.jingwen.voicediary.data.DiaryMediaEntity
import com.jingwen.voicediary.data.DiaryBlockType
import com.jingwen.voicediary.data.DiaryContentLayout
import com.jingwen.voicediary.data.DiaryMediaType
import com.jingwen.voicediary.data.mediaType
import com.jingwen.voicediary.data.diaryLatestFirst
import com.jingwen.voicediary.data.SampleDiaries
import com.jingwen.voicediary.ui.*
import com.jingwen.voicediary.ui.components.*
import com.jingwen.voicediary.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(diaryId: Long, diaries: List<DiaryEntity>, viewModel: DiaryViewModel,
                 showTimes: Boolean, onBack: () -> Unit, onEdit: (Long) -> Unit,
                 onOpenDiary: (Long) -> Unit) {
    val diary = diaries.firstOrNull { it.id == diaryId }
    val context = LocalContext.current
    var media by remember(diaryId) { mutableStateOf<List<DiaryMediaEntity>>(emptyList()) }
    LaunchedEffect(diaryId) { media = viewModel.getMedia(diaryId) }
    var confirmDelete by rememberSaveable(diaryId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ordered = diaries.sortedWith(diaryLatestFirst.reversed())
    val index = ordered.indexOfFirst { it.id == diaryId }
    val previous = if (index >= 0) ordered.getOrNull(index - 1) else null
    val next = if (index >= 0) ordered.getOrNull(index + 1) else null

    Column(Modifier.fillMaxSize().background(Cream)) {
        SiteTopBar(if (diary?.entryType == "QUICK") "随手记" else "日记详情", onBack, Icons.Outlined.ChevronLeft) {
            if (diary != null) SiteButton(if (diary.entryType == "QUICK") "编辑" else "继续写", { onEdit(diary.id) }, filled = true)
        }
        if (diary == null) {
            Text("日记正在读取，或已被移除。", Modifier.padding(24.dp), color = MutedBrown)
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 25.dp, vertical = 22.dp)) {
                val date = diary.diaryDate.toLocalDateOrNull()
                Text((date?.let { it.year.toString() + "年" + it.toShortChineseDate() } ?: diary.diaryDate) +
                    if (showTimes) "　" + diary.timeLabel() else "",
                    color = MutedBrown, fontSize = 12.sp)
                if (showTimes && diary.updatedAt != diary.createdAt) {
                    Text("最后修改 · " + diary.modifiedDateTimeLabel(), color = MutedBrown, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                if (diary.entryType != "QUICK") SiteHeading(previewText(diary.title, diary.content), Modifier.padding(vertical = 7.dp), 26)
                val blocks = remember(diary.layoutJson, diary.content, media) {
                    val decoded = DiaryContentLayout.decode(diary.layoutJson, diary.content, media)
                    DiaryContentLayout.migrateLegacyStickers(decoded, media).blocks
                }
                var blockIndex = 0
                while (blockIndex < blocks.size) {
                    val block = blocks[blockIndex]
                    when (block.type) {
                        DiaryBlockType.TEXT -> if (block.text.isNotEmpty()) {
                            InlineStickerBodyText(
                                block.text,
                                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            )
                        }
                        DiaryBlockType.MEDIA -> {
                            val item = media.firstOrNull { it.mediaUuid == block.mediaUuid }
                            if (item?.mediaType == DiaryMediaType.STICKER) {
                                DiarySticker(item, viewModel.mediaThumbnailPath(item))
                            } else if (item != null) {
                                val group = mutableListOf<DiaryMediaEntity>()
                                var nextIndex = blockIndex
                                while (nextIndex < blocks.size && blocks[nextIndex].type == DiaryBlockType.MEDIA) {
                                    val candidate = media.firstOrNull {
                                        it.mediaUuid == blocks[nextIndex].mediaUuid
                                    } ?: break
                                    if (candidate.mediaType == DiaryMediaType.STICKER) break
                                    group += candidate
                                    nextIndex++
                                }
                                DiaryMediaGrid(
                                    media = group,
                                    thumbnailPath = viewModel::mediaThumbnailPath,
                                    columns = adaptiveMediaColumns(group.size),
                                    onOpen = { selected ->
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(viewModel.mediaUri(selected), selected.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            })
                                        }
                                    },
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                blockIndex = nextIndex
                                continue
                            }
                        }
                    }
                    blockIndex++
                }
                if (diary.content.isBlank() && media.isEmpty()) {
                    Text("（这篇日记还没有正文）", fontFamily = FontFamily.Serif,
                        color = MutedBrown, fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
                SavedDiaryIllustration(diary.id, Modifier.fillMaxWidth().height(210.dp), large = true)
                if (diary.id < 0) Text("示例日记 · 修改会保存在本篇",
                    color = MutedBrown, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SiteIconButton(Icons.Outlined.DeleteOutline, "删除日记", { confirmDelete = true }, tint = MutedBrown)
                }
                failure?.let { Text(it, color = Rose, fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SiteButton("‹ 上一篇", { previous?.let { onOpenDiary(it.id) } },
                    Modifier.weight(1f), enabled = previous != null)
                SiteButton("下一篇 ›", { next?.let { onOpenDiary(it.id) } },
                    Modifier.weight(1f), enabled = next != null)
            }
        }
    }
    if (confirmDelete && diary != null) AlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text(if (diary.id < 0) "隐藏这篇示例？" else "删除这篇日记？") },
        text = { Text(if (diary.id < 0) "这不会影响你的真实日记。" else "删除后无法恢复。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    viewModel.deleteDiary(diary)
                    confirmDelete = false
                    onBack()
                } catch (_: Exception) {
                    failure = "删除失败，请重试。"
                    confirmDelete = false
                } finally { busy = false }
            }
        }) { Text(if (busy) "处理中…" else "确定") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("取消") } }
    )
}
