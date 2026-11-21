package com.jingwen.voicediary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jingwen.voicediary.data.DiaryIllustrations
import com.jingwen.voicediary.data.SampleDiaries
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.ui.theme.*

@Composable
fun SiteHeading(text: String, modifier: Modifier = Modifier, size: Int = 24) {
    Text(text, modifier, color = DeepRose, fontSize = size.sp,
        lineHeight = (size + 7).sp, fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal)
}

@Composable
fun SitePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp), ambientColor = DeepRose.copy(alpha = 0.09f),
                spotColor = DeepRose.copy(alpha = 0.09f))
            .clip(RoundedCornerShape(24.dp))
            .background(Paper.copy(alpha = 0.90f))
            .border(1.dp, DeepRose.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun SiteIconButton(icon: ImageVector, description: String, onClick: () -> Unit,
                   modifier: Modifier = Modifier, tint: Color = WarmBrown) {
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(23.dp), tint = tint)
    }
}

@Composable
fun SiteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
               filled: Boolean = false, enabled: Boolean = true) {
    Box(
        modifier.defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) Rose else Paper)
            .border(if (filled) 0.dp else 1.dp, if (filled) Color.Transparent else WarmLine, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp,
            color = if (!enabled) MutedBrown.copy(alpha = 0.45f) else if (filled) Color.White else DeepRose,
            textAlign = TextAlign.Center)
    }
}

@Composable
fun SiteTopBar(title: String, onBack: () -> Unit, backIcon: ImageVector,
               action: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SiteIconButton(backIcon, "返回", onBack)
            Text(title, Modifier.weight(1f), fontSize = 15.sp, textAlign = TextAlign.Center,
                color = WarmBrown)
            Row(Modifier.widthIn(min = 44.dp), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically, content = action)
        }
        HorizontalDivider(color = DeepRose.copy(alpha = 0.06f))
    }
}

@Composable
fun DiaryIllustration(emoji: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(if (large) 22.dp else 15.dp)).background(
        Brush.linearGradient(listOf(Color(0xFFEAC5B8), Color(0xFFFFF1DD), Color(0xFFD3A69C)))),
        contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = if (large) 50.sp else 30.sp)
    }
}

val SitePageGradient: Brush
    get() = Brush.verticalGradient(listOf(Color(0xFFFFFDFA), Color(0xFFFFF9F7)))

@Composable
fun SavedDiaryIllustration(diaryId: Long, modifier: Modifier = Modifier, large: Boolean = false) {
    val hidden by DiaryIllustrations.get(LocalContext.current).hiddenIds.collectAsStateWithLifecycle()
    if (diaryId !in hidden) {
        SampleDiaries.visual(diaryId)?.let { DiaryIllustration(it, modifier, large) }
    }
}
