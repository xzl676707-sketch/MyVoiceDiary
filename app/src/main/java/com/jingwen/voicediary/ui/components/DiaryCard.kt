package com.jingwen.voicediary.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.data.DiaryEntity
import com.jingwen.voicediary.data.SampleDiaries
import com.jingwen.voicediary.data.DiaryMediaFiles
import com.jingwen.voicediary.data.DiaryMediaType
import com.jingwen.voicediary.data.StickerCatalog
import com.jingwen.voicediary.ui.previewText
import com.jingwen.voicediary.ui.timeLabel
import com.jingwen.voicediary.ui.theme.*

@Composable
fun DiaryCard(diary: DiaryEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = when {
        diary.id == -3L -> listOf(Color(0xFFF8E7EB), Color(0xFFF0E9F7))
        diary.id == -2L || diary.id == -4L -> listOf(Color(0xFFFFFAF1), Color(0xFFF9ECE0))
        else -> listOf(Color(0xFFFFF8F7), Color(0xFFF9E8E7))
    }
    Row(
        modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(21.dp),
                ambientColor = DeepRose.copy(alpha = 0.06f), spotColor = DeepRose.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(21.dp)).background(Brush.linearGradient(colors))
            .clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            if (diary.entryType == "QUICK") {
                Text("✦ 随手记", color = Rose, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(if (diary.entryType == "QUICK" && diary.content.isBlank()) "拍下这一刻" else previewText(diary.title, diary.content), fontSize = 18.sp, lineHeight = 24.sp,
                fontFamily = FontFamily.Serif, color = CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (diary.content.isNotBlank()) Text(StickerCatalog.readableText(diary.content), color = CardBody,
                fontSize = 13.sp, lineHeight = 21.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (diary.coverThumbnailName.isNotBlank()) {
            val context = LocalContext.current
            val path = remember(diary.coverThumbnailName) {
                DiaryMediaFiles(context.applicationContext).thumbnail(diary.coverThumbnailName).absolutePath
            }
            val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(17.dp)).background(Peach)) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
                if (diary.coverMediaType == DiaryMediaType.VIDEO.name) {
                    Box(Modifier.align(Alignment.Center).size(30.dp).clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp), tint = Rose)
                    }
                }
                if (diary.mediaCount > 1) {
                    Text("+" + (diary.mediaCount - 1), color = Color.White, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).background(DeepRose.copy(alpha = .72f),
                            RoundedCornerShape(topStart = 9.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        } else {
            SavedDiaryIllustration(diary.id, Modifier.size(78.dp))
        }
    }
}

@Composable
fun TimelineEntry(diary: DiaryEntity, onClick: () -> Unit, last: Boolean, showTime: Boolean) {
    Row(Modifier.fillMaxWidth().drawBehind {
        val x = 7.dp.toPx()
        val y = 24.dp.toPx()
        drawLine(Color(0xFFE7CACC), Offset(x, 0f),
            Offset(x, if (last) y else size.height), 1.dp.toPx())
        drawCircle(Cream, 8.dp.toPx(), Offset(x, y))
        drawCircle(Color(0xFFE899A6), 4.dp.toPx(), Offset(x, y))
    }.padding(start = 24.dp, bottom = 12.dp)) {
        if (showTime) {
            Text(diary.timeLabel(), Modifier.width(57.dp).padding(top = 15.dp),
                fontSize = 14.sp, color = MutedBrown)
            Spacer(Modifier.width(8.dp))
        }
        DiaryCard(diary, onClick, Modifier.weight(1f))
    }
}

@Composable
fun EmptyDiaryHint(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteHeading(title)
        Text(description, color = MutedBrown, fontSize = 14.sp, lineHeight = 23.sp)
        Text("♡", fontSize = 30.sp, color = SoftPink)
    }
}
