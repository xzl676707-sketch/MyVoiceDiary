package com.jingwen.voicediary.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import com.jingwen.voicediary.QUICK_NOTE_MEDIA_LIMIT
import com.jingwen.voicediary.QuickNoteViewModel
import com.jingwen.voicediary.data.DiaryMediaType
import com.jingwen.voicediary.data.mediaType
import com.jingwen.voicediary.ui.components.InlineStickerEditorText
import com.jingwen.voicediary.ui.theme.*
import java.io.File

@Composable
fun QuickNoteScreen(id: Long?, composer: QuickNoteViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val draft = composer.draft
    var showMediaMenu by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(QUICK_NOTE_MEDIA_LIMIT)
    ) { uris -> composer.importMedia(uris, DiaryMediaType.IMAGE) }
    val singlePhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { composer.importMedia(listOf(it), DiaryMediaType.IMAGE) } }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { composer.importMedia(listOf(it), DiaryMediaType.VIDEO) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraPath?.let { File(it) }
        cameraPath = null
        if (file != null) {
            if (ok) {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                composer.importMedia(listOf(uri), DiaryMediaType.IMAGE, file)
            } else file.delete()
        }
    }
    val cancel = {
        if (!draft.busy) {
            if (draft.dirty) confirmDiscard = true else onBack()
        }
    }

    LaunchedEffect(id) { composer.load(id) }
    BackHandler(onBack = cancel)
    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = cancel, enabled = !draft.busy) { Text("取消", color = WarmBrown) }
            Button(
                onClick = { composer.save(onBack) },
                enabled = draft.ready && draft.hasContent && !draft.busy,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Rose)
            ) { Text("保存") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            if (draft.ready) {
                InlineStickerEditorText(
                    value = draft.caption,
                    placeholder = "这一刻，想说些什么？",
                    enabled = !draft.busy,
                    isLast = false,
                    focusRequestId = 0L,
                    focusCursorOffset = 0,
                    onChange = { value, _ -> composer.setCaption(value) },
                    onCursor = {},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp)
                )
                Spacer(Modifier.height(18.dp))
                val showAdd = draft.media.size < QUICK_NOTE_MEDIA_LIMIT
                val cellCount = draft.media.size + if (showAdd) 1 else 0
                (0 until cellCount).chunked(3).forEachIndexed { index, row ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { cell ->
                            val item = draft.media.getOrNull(cell)
                            if (item == null) {
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFFF5F2F2))
                                        .clickable(enabled = !draft.busy, role = Role.Button, onClickLabel = "添加照片或视频") { showMediaMenu = true },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Outlined.Add, "添加照片或视频", Modifier.size(32.dp), tint = MutedBrown) }
                            } else {
                                val bitmap = remember(item.thumbnailName) {
                                    item.thumbnailName.takeIf { it.isNotBlank() }
                                        ?.let { BitmapFactory.decodeFile(composer.files.thumbnail(it).absolutePath) }
                                }
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(Peach)) {
                                    if (bitmap != null) Image(bitmap.asImageBitmap(), null,
                                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    if (item.mediaType == DiaryMediaType.VIDEO) Icon(
                                        Icons.Outlined.PlayArrow, "视频", Modifier.align(Alignment.Center), tint = Color.White
                                    )
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.62f))
                                            .clickable(enabled = !draft.busy, role = Role.Button) { composer.remove(item) },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Outlined.Close, "移除附件", Modifier.size(16.dp), tint = Color.White) }
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("仅保存到自己的日记本", color = MutedBrown, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp))
            }
            if (draft.busy) Text("处理中…", color = MutedBrown, modifier = Modifier.padding(top = 12.dp))
            draft.error?.let { Text(it, color = Rose, modifier = Modifier.padding(top = 12.dp)) }
        }
    }

    if (showMediaMenu) {
        AlertDialog(
            onDismissRequest = { showMediaMenu = false },
            title = { Text("添加照片或视频") },
            text = {
                Column {
                    TextButton(onClick = {
                        showMediaMenu = false
                        val target = runCatching { composer.files.createCameraTarget() }.getOrNull()
                        if (target == null) composer.showError("无法准备拍照，请从相册选择。")
                        else {
                            cameraPath = target.first.absolutePath
                            runCatching { camera.launch(target.second) }.onFailure {
                                cameraPath = null
                                target.first.delete()
                                composer.showError("无法打开相机，请从相册选择。")
                            }
                        }
                    }) { Text("拍一张") }
                    TextButton(onClick = {
                        showMediaMenu = false
                        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        if (draft.media.size == QUICK_NOTE_MEDIA_LIMIT - 1) singlePhotoPicker.launch(request)
                        else photoPicker.launch(request)
                    }) { Text("从相册选照片") }
                    TextButton(onClick = {
                        showMediaMenu = false
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }) { Text("从相册选视频") }
                }
            },
            confirmButton = { TextButton(onClick = { showMediaMenu = false }) { Text("取消") } }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃这次编辑？") },
            text = { Text("未保存的文字和照片不会写入日记本。") },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
            confirmButton = { TextButton(onClick = onBack) { Text("放弃") } }
        )
    }
}
