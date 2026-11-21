package com.jingwen.voicediary.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.data.DiaryMediaEntity
import com.jingwen.voicediary.data.DiaryMediaType
import com.jingwen.voicediary.data.mediaType
import com.jingwen.voicediary.ui.theme.MutedBrown
import com.jingwen.voicediary.ui.theme.Peach
import com.jingwen.voicediary.ui.theme.Rose

@Composable
fun DiaryMediaGrid(
    media: List<DiaryMediaEntity>,
    thumbnailPath: (DiaryMediaEntity) -> String,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onRemove: ((DiaryMediaEntity) -> Unit)? = null,
    onOpen: ((DiaryMediaEntity) -> Unit)? = null
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        media.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    MediaTile(item, thumbnailPath(item), Modifier.weight(1f), onRemove, onOpen)
                }
            }
        }
    }
}

/** A single item is large; consecutive media never becomes smaller than two columns. */
fun adaptiveMediaColumns(count: Int): Int = if (count <= 1) 1 else 2

@Composable
private fun MediaTile(
    item: DiaryMediaEntity,
    path: String,
    modifier: Modifier,
    onRemove: ((DiaryMediaEntity) -> Unit)?,
    onOpen: ((DiaryMediaEntity) -> Unit)?
) {
    val bitmap = remember(path) { path.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile) }
    Box(
        modifier.aspectRatio(1.18f).clip(RoundedCornerShape(18.dp)).background(Peach)
            .clickable(enabled = onOpen != null) { onOpen?.invoke(item) }
    ) {
        if (bitmap != null) {
            // A diary is also a photo record: always show the complete source image.
            // Fit may leave a small background margin when aspect ratios differ, but
            // never cuts faces, text, scenery, or the edges of portrait photos.
            Image(bitmap.asImageBitmap(), item.originalName, Modifier.fillMaxSize().padding(3.dp),
                contentScale = ContentScale.Fit)
        } else {
            Text(if (item.mediaType == DiaryMediaType.VIDEO) "视频" else "图片",
                color = MutedBrown, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (item.mediaType == DiaryMediaType.VIDEO) {
            Box(Modifier.align(Alignment.Center).size(44.dp).clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = .82f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, "播放视频", tint = Rose)
            }
        }
        onRemove?.let { remove ->
            SiteIconButton(
                Icons.Outlined.Close,
                "从日记中删除",
                { remove(item) },
                Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .background(Color.White.copy(alpha = .9f), RoundedCornerShape(20.dp)),
                tint = Rose
            )
        }
    }
}
