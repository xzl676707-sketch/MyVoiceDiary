package com.jingwen.voicediary.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Preview fixtures live outside Room. Negative IDs cannot collide with real diary IDs. */
object SampleDiaries {
    fun create(today: LocalDate = LocalDate.now()): List<DiaryEntity> {
        fun entry(id: Long, date: LocalDate, time: String, title: String, body: String): DiaryEntity {
            val stamp = date.atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            // A preview's illustrative clock can be in the future; recent real edits must still sort first.
            val updated = minOf(stamp, System.currentTimeMillis() - 60_000L)
            return DiaryEntity(
                id = id,
                title = title,
                content = body,
                diaryDate = date.toString(),
                createdAt = stamp,
                updatedAt = updated
            )
        }
        return listOf(
            entry(-1, today, "08:23", "早安，新的一天 ☀",
                "阳光从窗帘缝里溜进来，空气里都是温柔的味道。这样的日子，让人忍不住想记录下来。生活虽然平凡，但总有一些小确幸，值得好好珍藏。"),
            entry(-2, today, "12:17", "午间小确幸",
                "一杯喜欢的拿铁，和刚读到的一句话。生活其实很简单。今天和好朋友一起去了喜欢的咖啡店，聊聊天，看看书，享受这份温柔的日常。"),
            entry(-3, today, "21:40", "有些话，只说给自己听",
                "今天的我，已经很棒了。谢谢自己，一直这么努力。慢一点也没关系。"),
            entry(-4, today.minusDays(1), "20:11", "和好朋友的夜聊",
                "聊了好多，笑得好开心。这样的夜晚真好。"),
            entry(-5, today.minusDays(4), "16:20", "雨停以后",
                "雨停以后空气很清新，窗外的树叶像被重新擦亮了一遍。"),
            entry(-6, today.minusDays(8), "17:36", "路边的小花",
                "回来的路上看见一簇很小的花，风吹过来的时候轻轻晃着。"),
            entry(-7, today.minusMonths(1).withDayOfMonth(13), "14:26", "夏日的冰咖啡",
                "天气很热，但坐在窗边喝冰咖啡的时候很安静。"),
            entry(-8, today.minusMonths(1).withDayOfMonth(20), "21:12", "整理照片的周末",
                "在家整理照片，也把这个月发生的小事情重新看了一遍。"),
            entry(-9, today.minusMonths(4).withDayOfMonth(18), "11:08", "五月的花",
                "校园里的花开得很好，路过时忍不住多看了一会儿。"),
            entry(-10, today.minusYears(1).withMonth(12).withDayOfMonth(31), "23:10", "年末的小愿望",
                "希望新的一年也能好好生活，多记录一些真正让自己开心的瞬间。"),
            entry(-11, today.minusYears(3), "18:32", "傍晚的风",
                "下课的时候天还是亮的，走到校门口，风突然变得很温柔。我买了一杯热奶茶，慢慢走回去。路上的人不多，树叶被风吹得沙沙响。那一刻没有什么特别的事情发生，却觉得心里很安静。"),
            entry(-12, today.minusYears(1), "22:06", "普通却安心的一天",
                "没有发生什么特别的事，但晚上整理照片的时候，还是觉得今天很好。午后看了一会儿书，傍晚出去散步，回来以后给自己做了简单的晚饭。普通的一天，也有让人安心的重量。")
        )
    }

    fun visual(id: Long): String? = when (id) {
        -1L -> "☕"
        -2L -> "🌷"
        -3L -> "🌙"
        -4L -> "🕯"
        -5L -> "🌧"
        -6L -> "🌼"
        -7L -> "🧊"
        -8L -> "📷"
        -9L -> "🌸"
        -10L -> "✨"
        -11L -> "🍵"
        -12L -> "📖"
        else -> null
    }
}
