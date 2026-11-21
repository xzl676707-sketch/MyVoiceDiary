package com.jingwen.voicediary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.data.DiaryEntity
import com.jingwen.voicediary.data.StickerCatalog
import com.jingwen.voicediary.ui.*
import com.jingwen.voicediary.ui.components.*
import com.jingwen.voicediary.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    diaries: List<DiaryEntity>,
    showTimes: Boolean,
    onOpenDiary: (Long) -> Unit,
    onNewDiary: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var expandedMonth by rememberSaveable { mutableStateOf("") }
    val scroll = rememberLazyListState()
    val today = LocalDate.now()
    val groups = diaries.groupBy { it.diaryDate }.toSortedMap(reverseOrder())
    val dated = diaries.mapNotNull { diary -> diary.diaryDate.toLocalDateOrNull()?.let { it to diary } }
    val archives = dated.groupBy { it.first.year }.toSortedMap(reverseOrder())
    val memories = dated.filter {
        it.first.year < today.year && it.first.monthValue == today.monthValue && it.first.dayOfMonth == today.dayOfMonth
    }.sortedBy { it.first }

    Column(modifier.fillMaxSize().background(SitePageGradient)) {
        LazyColumn(
            state = scroll,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 24.dp)
        ) {
            item(key = "brand") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("我的日记本", color = DeepRose, fontSize = 28.sp, lineHeight = 36.sp,
                            fontFamily = FontFamily.Serif, maxLines = 1, letterSpacing = 0.3.sp)
                        Text("把日常，认真地留在这里 ♡", color = Color(0xFFD58B96),
                            fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 7.dp))
                    }
                    SiteIconButton(Icons.Outlined.Search, "搜索日记", onSearch)
                    SiteIconButton(Icons.Outlined.Settings, "我的与设置", onSettings)
                }
                Column(Modifier.padding(start = 4.dp, top = 25.dp, bottom = 18.dp)) {
                    Text("“平凡的日子\n也值得被温柔记录。”", color = Color(0xFFB77B83),
                        fontSize = 14.sp, lineHeight = 24.5.sp, fontFamily = FontFamily.Serif)
                    Box(Modifier.padding(top = 8.dp).size(24.dp, 2.dp).background(Color(0xFFDF9BA5)))
                }
                Row(
                    Modifier.fillMaxWidth()
                        .shadow(9.dp, RoundedCornerShape(28.dp),
                            ambientColor = Rose.copy(alpha = 0.22f), spotColor = Rose.copy(alpha = 0.22f))
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF4B5BF), Color(0xFFDF8797))))
                        .clickable(onClick = onNewDiary).padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.82f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.EditNote, null, Modifier.size(25.dp), tint = Rose)
                    }
                    Spacer(Modifier.width(15.dp))
                    Column {
                        Text("新建日记 ›", color = Color.White, fontSize = 22.sp,
                            fontFamily = FontFamily.Serif)
                        Text("用文字和影像 · 记录此刻", color = Color.White.copy(alpha = 0.86f),
                            fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            item(key = "tabs") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.72f))
                        .border(1.dp, DeepRose.copy(alpha = 0.06f), RoundedCornerShape(19.dp))
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("时间线", "日历", "年/月", "几年前的今天").forEachIndexed { index, label ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                            .background(if (tab == index) Color(0xFFF9DFE2) else Color.Transparent)
                            .clickable { tab = index }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Text(label, color = if (tab == index) DeepRose else MutedBrown,
                                fontSize = 12.sp, maxLines = 1,
                                fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            when (tab) {
                0 -> {
                    if (diaries.isEmpty()) item { EmptyDiaryHint("从今天开始", "点上方“新建日记”，写下属于你的第一篇。") }
                    groups.forEach { (date, dayEntries) ->
                        item(key = "date-" + date) {
                            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.Bottom) {
                                SiteHeading(date.toLocalDateOrNull()?.toShortChineseDate() ?: date, size = 25)
                                Text(dayEntries.size.toString() + "篇", Modifier.padding(start = 8.dp, bottom = 3.dp),
                                    fontSize = 13.sp, color = MutedBrown)
                                Spacer(Modifier.weight(1f))
                                Text(if (date == today.toString()) "生活很柔软 ✿" else "好好爱自己 ♡",
                                    fontSize = 11.sp, color = Color(0xFFBC8F95),
                                    modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                        items(dayEntries, key = { it.id }) { diary ->
                            TimelineEntry(diary, { onOpenDiary(diary.id) },
                                last = diary.id == dayEntries.last().id, showTime = showTimes)
                        }
                    }
                }
                1 -> {
                    item(key = "calendar") {
                        val month = YearMonth.parse(monthText)
                        CalendarPanel(month, selectedDate, dated.map { it.first }.toSet(),
                            onMonth = {
                                monthText = it.toString()
                                selectedDate = it.atDay(1).toString()
                            },
                            onSelect = { selectedDate = it.toString() })
                        Spacer(Modifier.height(18.dp))
                        SitePanel {
                            val entries = diaries.filter { it.diaryDate == selectedDate }
                            Text((selectedDate.toLocalDateOrNull()?.toShortChineseDate() ?: selectedDate) +
                                " · " + entries.size + "篇", color = DeepRose, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold)
                            if (entries.isEmpty()) {
                                Text("这一天还没有日记", Modifier.fillMaxWidth().padding(top = 18.dp),
                                    textAlign = TextAlign.Center, color = MutedBrown, fontSize = 13.sp)
                            }
                            entries.forEachIndexed { index, diary ->
                                MiniDiaryRow(diary, { onOpenDiary(diary.id) }, showTimes)
                                if (index < entries.lastIndex) HorizontalDivider(color = WarmLine)
                            }
                        }
                    }
                }
                2 -> {
                    item { SiteHeading("年月档案", Modifier.padding(bottom = 18.dp)) }
                    if (archives.isEmpty()) item { EmptyDiaryHint("还没有记录", "写过的日子会在这里慢慢积累。") }
                    archives.forEach { (year, yearly) ->
                        item(key = "year-" + year) {
                            SitePanel(Modifier.padding(bottom = 14.dp)) {
                                SiteHeading(year.toString(), size = 28)
                                Spacer(Modifier.height(10.dp))
                                yearly.groupBy { it.first.monthValue }.toSortedMap(reverseOrder())
                                    .forEach { (month, monthly) ->
                                        val key = year.toString() + "-" + month
                                        val opened = expandedMonth == key
                                        HorizontalDivider(color = WarmLine)
                                        Row(Modifier.fillMaxWidth().clickable {
                                            expandedMonth = if (opened) "" else key
                                        }.padding(vertical = 15.dp, horizontal = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text(month.toString() + "月", fontSize = 15.sp)
                                            Spacer(Modifier.weight(1f))
                                            Text(monthly.size.toString() + "篇", fontSize = 13.sp,
                                                color = if (opened) Rose else MutedBrown)
                                            Icon(if (opened) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                                                null, Modifier.size(20.dp), tint = MutedBrown)
                                        }
                                        if (opened) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                                            .padding(start = 4.dp, bottom = 7.dp)) {
                                            Box(Modifier.width(2.dp).fillMaxHeight().background(Color(0xFFF4D9DD)))
                                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                                monthly.forEachIndexed { index, (_, diary) ->
                                                    MiniDiaryRow(diary, { onOpenDiary(diary.id) }, showTimes, showDate = true)
                                                    if (index < monthly.lastIndex) HorizontalDivider(color = WarmLine)
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
                else -> {
                    item { SiteHeading("几年前的今天", Modifier.padding(bottom = 18.dp)) }
                    if (memories.isEmpty()) item {
                        EmptyDiaryHint("等一次重逢", "往年同月同日写下的故事，会在这里与你重逢。")
                    }
                    items(memories, key = { it.second.id }) { (date, diary) ->
                        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFFFF1EF))))
                            .border(1.dp, DeepRose.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
                            .clickable { onOpenDiary(diary.id) }) {
                            Text("✿", Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 22.dp),
                                fontSize = 95.sp, color = Rose.copy(alpha = 0.11f))
                            Column(Modifier.padding(18.dp)) {
                                val years = today.year - date.year
                                val ago = when (years) { 1 -> "一"; 2 -> "两"; 3 -> "三"; else -> years.toString() }
                                Text(ago + "年前的今天 · " + date.year + "年" + date.toShortChineseDate(),
                                    fontSize = 13.sp, color = Rose)
                                SiteHeading(previewText(diary.title, diary.content), Modifier.padding(vertical = 8.dp), 22)
                                Text(StickerCatalog.readableText(diary.content), color = Color(0xFF735F63), fontSize = 15.sp,
                                    lineHeight = 28.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                Text("查看完整日记 ›", Modifier.padding(top = 12.dp),
                                    color = Rose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        HomeBottomBar(onHome = { tab = 0 }, onSettings = onSettings)
    }
}

@Composable
private fun CalendarPanel(month: YearMonth, selected: String, dates: Set<LocalDate>,
                          onMonth: (YearMonth) -> Unit, onSelect: (LocalDate) -> Unit) {
    // The approved site uses Sunday as the first column.
    val offset = month.atDay(1).dayOfWeek.value % 7
    val cells = List<LocalDate?>(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    SitePanel {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            SiteIconButton(Icons.Outlined.ChevronLeft, "上个月", { onMonth(month.minusMonths(1)) })
            SiteHeading(month.toChineseMonth(), Modifier.weight(1f), 24)
            SiteIconButton(Icons.Outlined.ChevronRight, "下个月", { onMonth(month.plusMonths(1)) })
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, Modifier.weight(1f).padding(vertical = 7.dp), fontSize = 12.sp,
                    color = MutedBrown, textAlign = TextAlign.Center)
            }
        }
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else {
                        val chosen = date.toString() == selected
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .background(if (chosen) Rose else Color.Transparent)
                            .clickable { onSelect(date) }, contentAlignment = Alignment.Center) {
                            Text(date.dayOfMonth.toString(), color = if (chosen) Color.White else WarmBrown, fontSize = 14.sp)
                            if (date in dates) Box(Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp).size(5.dp).clip(CircleShape)
                                .background(if (chosen) Color.White else SoftPink))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDiaryRow(diary: DiaryEntity, onClick: () -> Unit, showTime: Boolean, showDate: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (showTime || showDate) {
            Column(Modifier.width(if (showDate) 62.dp else 50.dp)) {
                if (showDate) Text(diary.diaryDate.toLocalDateOrNull()?.toShortChineseDate().orEmpty(),
                    color = MutedBrown, fontSize = 11.sp)
                if (showTime) Text(diary.timeLabel(), color = MutedBrown, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(previewText(diary.title, diary.content), Modifier.weight(1f),
            color = WarmBrown, fontSize = 15.sp, fontFamily = FontFamily.Serif,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = Color(0xFFC18D96))
    }
}

@Composable
private fun HomeBottomBar(onHome: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.background(Color(0xFFFFFDFB))) {
        HorizontalDivider(color = DeepRose.copy(alpha = 0.08f))
        Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 54.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            listOf(Triple("首页", Icons.Outlined.Home, onHome),
                Triple("我的", Icons.Outlined.FavoriteBorder, onSettings)).forEachIndexed { index, item ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = item.third),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(item.second, null, Modifier.size(24.dp), tint = if (index == 0) Rose else MutedBrown)
                    Spacer(Modifier.height(5.dp))
                    Text(item.first, fontSize = 11.sp, color = if (index == 0) Rose else MutedBrown,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
