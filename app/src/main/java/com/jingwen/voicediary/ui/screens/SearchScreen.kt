package com.jingwen.voicediary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.data.DiaryEntity
import com.jingwen.voicediary.ui.components.SiteButton
import com.jingwen.voicediary.ui.components.SiteTopBar
import com.jingwen.voicediary.ui.previewText
import com.jingwen.voicediary.ui.theme.*

@Composable
fun SearchScreen(diaries: List<DiaryEntity>, onBack: () -> Unit, onOpenDiary: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val needle = query.trim()
    val results = if (needle.isBlank()) emptyList() else diaries.filter {
        it.title.contains(needle, true) || it.content.contains(needle, true)
    }
    Column(Modifier.fillMaxSize().background(Cream).imePadding()) {
        SiteTopBar("搜索日记", onBack, Icons.Outlined.ChevronLeft)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(query, { query = it },
                modifier = Modifier.weight(1f).background(Paper, RoundedCornerShape(18.dp))
                    .border(1.dp, WarmLine, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 13.dp),
                singleLine = true, textStyle = TextStyle(color = WarmBrown, fontSize = 15.sp),
                cursorBrush = SolidColor(Rose),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) Text("搜索标题和正文", color = MutedBrown, fontSize = 15.sp)
                        field()
                    }
                })
            SiteButton("搜索", { keyboard?.hide() }, filled = true)
        }
        if (results.isEmpty()) {
            Text(if (needle.isBlank()) "输入你记得的文字，就能找到那篇日记。" else "没有找到包含“" + needle + "”的日记",
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 50.dp),
                color = MutedBrown, fontSize = 14.sp, textAlign = TextAlign.Center)
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp)) {
            items(results, key = { it.id }) { diary ->
                Column(Modifier.fillMaxWidth().clickable { keyboard?.hide(); onOpenDiary(diary.id) }
                    .padding(horizontal = 4.dp, vertical = 16.dp)) {
                    Text(previewText(diary.title, diary.content), color = DeepRose, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(diary.content, color = MutedBrown, fontSize = 13.sp, lineHeight = 21.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
                HorizontalDivider(color = WarmLine)
            }
        }
    }
}
