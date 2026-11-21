package com.jingwen.voicediary.data

data class StickerAsset(
    val id: String,
    val label: String,
    val assetPath: String,
    /** One private-use character stored in the diary text for this inline sticker. */
    val inlineChar: Char
)

data class StickerPack(
    val id: String,
    val name: String,
    val stickers: List<StickerAsset>
)

object StickerCatalog {
    /**
     * Never reorder existing keys: the index is persisted as a private-use
     * character inside diary text. New stickers are appended after V0.9's 24
     * entries so diaries written by older versions keep the same pictures.
     */
    private val stableStickerKeys = listOf(
        "bunny/happy", "bunny/sad", "bunny/angry", "bunny/hug",
        "bunny/goodnight", "bunny/cheer", "bunny/melon", "bunny/speechless",
        "bunny/shocked", "bunny/heart", "bunny/thanks", "bunny/flat",
        "cat/yay", "cat/cry", "cat/angry", "cat/love",
        "cat/sleepy", "cat/okay", "cat/drink", "cat/daze",
        "cat/shy", "cat/praise", "cat/peek", "cat/lazy",
        "bunny/drained", "bunny/heartbroken", "bunny/brave_smile", "bunny/pressure",
        "bunny/hold_tears", "bunny/breakdown", "bunny/self_doubt", "bunny/social_anxiety",
        "bunny/embarrassed", "bunny/missing", "bunny/jealous", "bunny/comforted",
        "bunny/sick", "bunny/insomnia", "bunny/blank", "bunny/courage",
        "cat/study", "cat/deadline", "cat/slack_fish", "cat/wake_up",
        "cat/commute", "cat/meal", "cat/bubble_tea", "cat/workout",
        "cat/bath", "cat/drama", "cat/gaming", "cat/parcel",
        "cat/shopping", "cat/cleaning", "cat/celebrate_task", "cat/recharge"
    )

    val packs: List<StickerPack> = listOf(
        StickerPack("bunny", "奶油垂耳兔", listOf(
            sticker("bunny", "happy", "开心"),
            sticker("bunny", "sad", "委屈"),
            sticker("bunny", "angry", "生气了"),
            sticker("bunny", "hug", "抱抱"),
            sticker("bunny", "goodnight", "晚安"),
            sticker("bunny", "cheer", "加油"),
            sticker("bunny", "melon", "吃瓜"),
            sticker("bunny", "speechless", "无语"),
            sticker("bunny", "shocked", "震惊"),
            sticker("bunny", "heart", "比心"),
            sticker("bunny", "thanks", "谢谢"),
            sticker("bunny", "flat", "躺平"),
            sticker("bunny", "drained", "心累"),
            sticker("bunny", "heartbroken", "心碎"),
            sticker("bunny", "brave_smile", "强颜欢笑"),
            sticker("bunny", "pressure", "压力山大"),
            sticker("bunny", "hold_tears", "忍住眼泪"),
            sticker("bunny", "breakdown", "崩溃大哭"),
            sticker("bunny", "self_doubt", "自我怀疑"),
            sticker("bunny", "social_anxiety", "社恐"),
            sticker("bunny", "embarrassed", "尴尬"),
            sticker("bunny", "missing", "想念"),
            sticker("bunny", "jealous", "吃醋"),
            sticker("bunny", "comforted", "被安慰"),
            sticker("bunny", "sick", "生病"),
            sticker("bunny", "insomnia", "失眠"),
            sticker("bunny", "blank", "放空"),
            sticker("bunny", "courage", "重新振作")
        )),
        StickerPack("cat", "奶油小猫", listOf(
            sticker("cat", "yay", "好耶"),
            sticker("cat", "cry", "呜呜"),
            sticker("cat", "angry", "哼"),
            sticker("cat", "love", "爱你"),
            sticker("cat", "sleepy", "困困"),
            sticker("cat", "okay", "可以"),
            sticker("cat", "drink", "喝一口"),
            sticker("cat", "daze", "发呆"),
            sticker("cat", "shy", "害羞"),
            sticker("cat", "praise", "太棒啦"),
            sticker("cat", "peek", "看看"),
            sticker("cat", "lazy", "不想动"),
            sticker("cat", "study", "努力学习"),
            sticker("cat", "deadline", "赶作业"),
            sticker("cat", "slack_fish", "摸鱼"),
            sticker("cat", "wake_up", "起床困难"),
            sticker("cat", "commute", "出门"),
            sticker("cat", "meal", "吃饭"),
            sticker("cat", "bubble_tea", "喝奶茶"),
            sticker("cat", "workout", "健身"),
            sticker("cat", "bath", "洗澡"),
            sticker("cat", "drama", "追剧"),
            sticker("cat", "gaming", "打游戏"),
            sticker("cat", "parcel", "拆快递"),
            sticker("cat", "shopping", "购物"),
            sticker("cat", "cleaning", "打扫卫生"),
            sticker("cat", "celebrate_task", "完成啦"),
            sticker("cat", "recharge", "休息充电")
        ))
    )

    val all: List<StickerAsset> by lazy { packs.flatMap { it.stickers } }

    fun fromInlineChar(value: Char): StickerAsset? = all.firstOrNull { it.inlineChar == value }

    fun fromLegacyLabel(label: String): StickerAsset? = all.firstOrNull { it.label == label }

    fun containsInlineSticker(text: String): Boolean = text.any { fromInlineChar(it) != null }

    fun readableText(text: String): String = buildString(text.length) {
        text.forEach { value -> append(if (fromInlineChar(value) == null) value else ' ') }
    }.replace(Regex("[ \\t]+"), " ").trim()

    private fun sticker(pack: String, id: String, label: String): StickerAsset {
        val stableIndex = stableStickerKeys.indexOf("$pack/$id")
        require(stableIndex >= 0) { "未知表情：$pack/$id" }
        return StickerAsset(
            "$pack-$id",
            label,
            "stickers/$pack/$id.png",
            ('\uE000'.code + stableIndex).toChar()
        )
    }
}
