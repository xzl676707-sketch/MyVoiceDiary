package com.jingwen.voicediary.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DiaryBlockType { TEXT, MEDIA }

data class DiaryContentBlock(
    val blockUuid: String = UUID.randomUUID().toString(),
    val type: DiaryBlockType,
    val text: String = "",
    val mediaUuid: String = ""
)

data class MediaInsertion(val blocks: List<DiaryContentBlock>, val trailingTextBlockUuid: String)
data class InlineTextInsertion(
    val blocks: List<DiaryContentBlock>,
    val textBlockUuid: String,
    val cursorOffset: Int
)
data class InlineStickerMigration(
    val blocks: List<DiaryContentBlock>,
    val removedStickerMedia: List<DiaryMediaEntity>
)

object DiaryContentLayout {
    fun insertInlineText(
        source: List<DiaryContentBlock>,
        targetTextBlockUuid: String?,
        requestedOffset: Int,
        value: String
    ): InlineTextInsertion {
        val normalized = normalize(source)
        val targetIndex = normalized.indexOfFirst {
            it.blockUuid == targetTextBlockUuid && it.type == DiaryBlockType.TEXT
        }.takeIf { it >= 0 } ?: normalized.indexOfLast { it.type == DiaryBlockType.TEXT }
        val target = normalized[targetIndex]
        val offset = requestedOffset.coerceIn(0, target.text.length)
        val changed = target.copy(text = target.text.substring(0, offset) + value + target.text.substring(offset))
        val blocks = normalized.toMutableList().apply { set(targetIndex, changed) }
        return InlineTextInsertion(blocks, changed.blockUuid, offset + value.length)
    }

    /** Converts V0.6 block stickers to V0.7 inline characters the first time an old diary is opened. */
    fun migrateLegacyStickers(
        source: List<DiaryContentBlock>,
        media: List<DiaryMediaEntity>
    ): InlineStickerMigration {
        val stickerByUuid = media.filter { it.mediaType == DiaryMediaType.STICKER }.associateBy { it.mediaUuid }
        if (stickerByUuid.isEmpty()) return InlineStickerMigration(normalize(source), emptyList())
        val removed = mutableListOf<DiaryMediaEntity>()
        val migrated = source.map { block ->
            val oldSticker = if (block.type == DiaryBlockType.MEDIA) stickerByUuid[block.mediaUuid] else null
            val asset = oldSticker?.let { StickerCatalog.fromLegacyLabel(it.originalName) }
            if (oldSticker != null && asset != null) {
                removed += oldSticker
                DiaryContentBlock(blockUuid = block.blockUuid, type = DiaryBlockType.TEXT, text = asset.inlineChar.toString())
            } else block
        }
        return InlineStickerMigration(normalize(migrated), removed)
    }

    fun insertMedia(
        source: List<DiaryContentBlock>,
        targetTextBlockUuid: String?,
        requestedOffset: Int,
        mediaUuids: List<String>
    ): MediaInsertion {
        val normalized = normalize(source)
        if (mediaUuids.isEmpty()) {
            val lastText = normalized.last { it.type == DiaryBlockType.TEXT }
            return MediaInsertion(normalized, lastText.blockUuid)
        }
        val targetIndex = normalized.indexOfFirst {
            it.blockUuid == targetTextBlockUuid && it.type == DiaryBlockType.TEXT
        }.takeIf { it >= 0 } ?: normalized.indexOfLast { it.type == DiaryBlockType.TEXT }
        val target = normalized[targetIndex]
        val offset = requestedOffset.coerceIn(0, target.text.length)
        val inserted = mutableListOf<DiaryContentBlock>()
        inserted += target.copy(text = target.text.substring(0, offset))
        mediaUuids.forEach { uuid ->
            inserted += DiaryContentBlock(type = DiaryBlockType.MEDIA, mediaUuid = uuid)
        }
        inserted += DiaryContentBlock(type = DiaryBlockType.TEXT, text = target.text.substring(offset))
        val blocks = normalized.toMutableList().apply {
            removeAt(targetIndex)
            addAll(targetIndex, inserted)
        }
        return MediaInsertion(normalize(blocks), inserted.last().blockUuid)
    }

    fun removeMedia(source: List<DiaryContentBlock>, mediaUuid: String): List<DiaryContentBlock> =
        normalize(source.filterNot { it.type == DiaryBlockType.MEDIA && it.mediaUuid == mediaUuid })

    fun initial(content: String, media: List<DiaryMediaEntity>): List<DiaryContentBlock> {
        val blocks = mutableListOf(DiaryContentBlock(type = DiaryBlockType.TEXT, text = content))
        media.sortedBy { it.sortIndex }.forEach { item ->
            blocks += DiaryContentBlock(type = DiaryBlockType.MEDIA, mediaUuid = item.mediaUuid)
            blocks += DiaryContentBlock(type = DiaryBlockType.TEXT)
        }
        return normalize(blocks)
    }

    fun decode(json: String, content: String, media: List<DiaryMediaEntity>): List<DiaryContentBlock> {
        if (json.isBlank()) return initial(content, media)
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val type = runCatching { DiaryBlockType.valueOf(item.getString("type")) }
                        .getOrDefault(DiaryBlockType.TEXT)
                    add(DiaryContentBlock(
                        blockUuid = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = type,
                        text = item.optString("text"),
                        mediaUuid = item.optString("mediaUuid")
                    ))
                }
            }
        }.getOrElse { initial(content, media) }.let(::normalize)
    }

    fun encode(blocks: List<DiaryContentBlock>): String {
        val array = JSONArray()
        normalize(blocks).forEach { block ->
            array.put(JSONObject().apply {
                put("id", block.blockUuid)
                put("type", block.type.name)
                if (block.type == DiaryBlockType.TEXT) put("text", block.text)
                else put("mediaUuid", block.mediaUuid)
            })
        }
        return array.toString()
    }

    fun plainText(blocks: List<DiaryContentBlock>): String =
        blocks.filter { it.type == DiaryBlockType.TEXT }.joinToString(separator = "") { it.text }

    fun normalize(source: List<DiaryContentBlock>): List<DiaryContentBlock> {
        val merged = mutableListOf<DiaryContentBlock>()
        source.forEach { block ->
            if (block.type == DiaryBlockType.TEXT && merged.lastOrNull()?.type == DiaryBlockType.TEXT) {
                val previous = merged.removeAt(merged.lastIndex)
                merged += previous.copy(text = previous.text + block.text)
            } else {
                merged += block
            }
        }
        // Older versions inserted an empty text block between every selected image.
        // It did not accept useful text and prevented the images from becoming a grid.
        val result = merged.filterIndexed { index, block ->
            !(block.type == DiaryBlockType.TEXT && block.text.isEmpty() &&
                merged.getOrNull(index - 1)?.type == DiaryBlockType.MEDIA &&
                merged.getOrNull(index + 1)?.type == DiaryBlockType.MEDIA)
        }.toMutableList()
        if (result.isEmpty()) result += DiaryContentBlock(type = DiaryBlockType.TEXT)
        if (result.first().type != DiaryBlockType.TEXT) result.add(0, DiaryContentBlock(type = DiaryBlockType.TEXT))
        if (result.last().type != DiaryBlockType.TEXT) result += DiaryContentBlock(type = DiaryBlockType.TEXT)
        return result
    }
}
