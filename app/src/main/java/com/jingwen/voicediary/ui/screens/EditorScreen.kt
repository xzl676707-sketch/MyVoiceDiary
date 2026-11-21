package com.jingwen.voicediary.ui.screens

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jingwen.voicediary.DiaryViewModel
import com.jingwen.voicediary.EditorViewModel
import com.jingwen.voicediary.data.SampleDiaries
import com.jingwen.voicediary.data.DiaryMediaFiles
import com.jingwen.voicediary.data.DiaryMediaEntity
import com.jingwen.voicediary.data.DiaryMediaType
import com.jingwen.voicediary.data.mediaType
import com.jingwen.voicediary.data.DiaryBlockType
import com.jingwen.voicediary.data.StickerCatalog
import com.jingwen.voicediary.data.DiaryContentBlock
import com.jingwen.voicediary.ui.components.*
import com.jingwen.voicediary.ui.theme.*
import com.jingwen.voicediary.ui.toChineseDate
import com.jingwen.voicediary.ui.toLocalDateOrNull
import java.time.LocalDate

@Composable
fun EditorScreen(diaryId: Long?, viewModel: DiaryViewModel, editor: EditorViewModel, quick: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mediaFiles = remember { DiaryMediaFiles(context.applicationContext) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        editor.addMedia(uris, DiaryMediaType.IMAGE)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { editor.addMedia(it, DiaryMediaType.VIDEO) }
    }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showStickerPicker by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let { editor.addMedia(it, DiaryMediaType.IMAGE) }
        cameraUri = null
    }
    LaunchedEffect(diaryId, quick) { editor.load(diaryId, viewModel, quick) }
    DisposableEffect(lifecycle, editor) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                editor.flush()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    val state = editor.state
    val leave = { editor.finish(onBack) }
    BackHandler { leave() }

    Box(Modifier.fillMaxSize().background(Cream)) {
    Column(Modifier.fillMaxSize().imePadding()) {
        SiteTopBar(state.status, leave, Icons.Outlined.Close) {
            SiteButton(if (state.closing) "保存中" else "完成", leave, filled = true, enabled = !state.closing)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            val date = state.date.toLocalDateOrNull() ?: LocalDate.now()
            if (state.entryType == "QUICK") Text("随手记 · 拍下此刻，写几句话", color = DeepRose,
                fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth().clickable(enabled = state.ready && !state.closing && state.entryType != "QUICK") {
                DatePickerDialog(context, { _, year, month, day ->
                    editor.setDate(LocalDate.of(year, month + 1, day).toString())
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }.padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = Color(0xFFAE989C))
                Text("  " + date.toChineseDate(), color = Color(0xFFAE989C), fontSize = 12.sp)
            }
            if (state.entryType != "QUICK") BasicTextField(
                value = state.title, onValueChange = editor::setTitle,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = state.ready && !state.closing, singleLine = true,
                textStyle = TextStyle(color = DeepRose, fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 32.sp),
                cursorBrush = SolidColor(Rose),
                decorationBox = { field -> Box {
                    if (state.title.isEmpty()) Text("标题（可以不填）", color = Color(0xFFC9B7B9),
                        fontSize = 23.sp, lineHeight = 32.sp, fontFamily = FontFamily.Serif)
                    field()
                } }
            )
            var blockIndex = 0
            while (blockIndex < state.blocks.size) {
                val block = state.blocks[blockIndex]
                when (block.type) {
                    DiaryBlockType.TEXT -> DiaryTextEditorBlock(
                        block = block,
                        showPlaceholder = state.content.isEmpty() && state.media.isEmpty() && blockIndex == 0,
                        isLast = blockIndex == state.blocks.lastIndex,
                        enabled = state.ready && !state.closing,
                        focusRequestId = if (state.focusTextBlockUuid == block.blockUuid) state.focusRequestId else 0L,
                        focusCursorOffset = state.focusCursorOffset,
                        onChange = { text, cursor -> editor.setBlockText(block.blockUuid, text, cursor) },
                        onCursor = { cursor -> editor.setInsertionPoint(block.blockUuid, cursor) }
                    )
                    DiaryBlockType.MEDIA -> {
                        val item = state.media.firstOrNull { it.mediaUuid == block.mediaUuid }
                        if (item?.mediaType == DiaryMediaType.STICKER) {
                            DiarySticker(item, editor.mediaThumbnailPath(item), onRemove = editor::removeMedia)
                        } else if (item != null) {
                            val group = mutableListOf<DiaryMediaEntity>()
                            var nextIndex = blockIndex
                            while (nextIndex < state.blocks.size && state.blocks[nextIndex].type == DiaryBlockType.MEDIA) {
                                val candidate = state.media.firstOrNull {
                                    it.mediaUuid == state.blocks[nextIndex].mediaUuid
                                } ?: break
                                if (candidate.mediaType == DiaryMediaType.STICKER) break
                                group += candidate
                                nextIndex++
                            }
                            DiaryMediaGrid(
                                media = group,
                                thumbnailPath = editor::mediaThumbnailPath,
                                columns = adaptiveMediaColumns(group.size),
                                onRemove = editor::removeMedia,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            blockIndex = nextIndex
                            continue
                        }
                    }
                }
                blockIndex++
            }
            state.id?.let { id -> SampleDiaries.visual(id) }?.let { visual ->
                if (!state.illustrationDeleted) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        DiaryIllustration(visual, Modifier.fillMaxWidth().height(170.dp), large = true)
                        SiteIconButton(Icons.Outlined.Close, "删除这张表情配图", editor::removeIllustration,
                            Modifier.align(Alignment.TopEnd).padding(8.dp).background(Cream, RoundedCornerShape(22.dp)),
                            tint = Rose)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("已删除表情配图", Modifier.weight(1f), color = MutedBrown, fontSize = 12.sp)
                        SiteButton("撤销", editor::restoreIllustration, enabled = state.ready && !state.closing)
                    }
                }
            }
            if (state.sample) Text("这是示例日记，修改会保存回本篇。",
                color = MutedBrown, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            state.error?.let {
                Text(it, color = Rose, fontSize = 13.sp)
                SiteButton("重试保存", { editor.flush() }, Modifier.padding(vertical = 8.dp))
            }
        }
        val suggestions = if (state.entryType == "DIARY") StickerCatalog.recommend(state.content) else emptyList()
        if (suggestions.isNotEmpty()) {
            Text("根据文字推荐 · 点选才会插入", color = MutedBrown,
                fontSize = 11.sp, modifier = Modifier.padding(start = 24.dp, top = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { sticker ->
                    val bitmap = remember(sticker.assetPath) {
                        context.assets.open(sticker.assetPath).use(BitmapFactory::decodeStream)
                    }
                    Row(Modifier.clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF0F1)).clickable { editor.addSticker(sticker) }
                        .padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        bitmap?.let { Image(it.asImageBitmap(), sticker.label, Modifier.size(33.dp), contentScale = ContentScale.Fit) }
                        Text(sticker.label, color = DeepRose, fontSize = 11.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = WarmLine)
        Row(Modifier.fillMaxWidth().background(Color(0xFFFFFDFB))
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val tools = listOf(
                "相册" to Icons.Outlined.Photo,
                "拍照" to Icons.Outlined.CameraAlt,
                "视频" to Icons.Outlined.Videocam,
                "表情" to Icons.Outlined.EmojiEmotions
            )
            tools.forEach { (label, icon) ->
                EditorTool(label, icon, Modifier.weight(1f)) {
                    when (label) {
                        "相册" -> imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        "视频" -> videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        "拍照" -> {
                            val target = mediaFiles.createCameraTarget()
                            cameraUri = target.second
                            camera.launch(target.second)
                        }
                        "表情" -> showStickerPicker = true
                    }
                }
            }
        }
    }
    if (showStickerPicker) {
        StickerPickerSheet(
            onDismiss = { showStickerPicker = false },
            onSelect = { sticker ->
                showStickerPicker = false
                editor.addSticker(sticker)
            }
        )
    }
    }
}

@Composable
private fun DiaryTextEditorBlock(
    block: DiaryContentBlock,
    showPlaceholder: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    focusRequestId: Long,
    focusCursorOffset: Int,
    onChange: (String, Int) -> Unit,
    onCursor: (Int) -> Unit
) {
    InlineStickerEditorText(
        value = block.text,
        placeholder = if (showPlaceholder) "在这里，写下此刻的心情……" else "",
        enabled = enabled,
        isLast = isLast,
        focusRequestId = focusRequestId,
        focusCursorOffset = focusCursorOffset,
        onChange = onChange,
        onCursor = onCursor,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = if (isLast) 72.dp else 44.dp)
            .padding(bottom = if (isLast) 12.dp else 4.dp)
    )
}

@Composable
private fun EditorTool(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp))
        .background(Color.Transparent)
        .clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, Modifier.size(23.dp), tint = WarmBrown)
        Text(label, fontSize = 9.sp, color = MutedBrown, modifier = Modifier.padding(top = 3.dp))
    }
}
