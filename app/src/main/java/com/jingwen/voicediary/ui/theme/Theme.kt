package com.jingwen.voicediary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val DiaryColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Blush,
    onPrimaryContainer = DeepRose,
    secondary = DeepRose,
    background = Cream,
    onBackground = WarmBrown,
    surface = Paper,
    onSurface = WarmBrown,
    surfaceVariant = Peach,
    onSurfaceVariant = MutedBrown,
    outline = WarmLine,
    error = Color(0xFFB3261E)
)

@Composable
fun MyVoiceDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DiaryColors,
        typography = Typography(
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp
            )
        ),
        content = content
    )
}
