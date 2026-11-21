package com.jingwen.voicediary.ui

import com.jingwen.voicediary.data.DiaryEntity
import com.jingwen.voicediary.data.StickerCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val chineseDate = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
private val shortChineseDate = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val monthTitle = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

fun LocalDate.toChineseDate(): String = format(chineseDate)
fun LocalDate.toShortChineseDate(): String = format(shortChineseDate)
fun YearMonth.toChineseMonth(): String = format(monthTitle)
fun DiaryEntity.timeLabel(): String = Instant.ofEpochMilli(createdAt)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
fun DiaryEntity.modifiedTimeLabel(): String = Instant.ofEpochMilli(updatedAt)
    .atZone(ZoneId.systemDefault()).format(timeFormat)
fun DiaryEntity.modifiedDateTimeLabel(): String = Instant.ofEpochMilli(updatedAt)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA))
fun String.toLocalDateOrNull(): LocalDate? = try { LocalDate.parse(this) }
    catch (_: DateTimeParseException) { null }
fun previewText(title: String, content: String): String =
    title.ifBlank { StickerCatalog.readableText(content).lineSequence().firstOrNull().orEmpty().take(24) }
        .ifBlank { "一篇安静的日记" }
