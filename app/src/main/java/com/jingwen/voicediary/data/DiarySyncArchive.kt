package com.jingwen.voicediary.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SyncSummary(val diaryCount: Int, val mediaCount: Int)

/** Internal phone/PC transfer format. It is not exposed as a phone export feature. */
class DiarySyncArchive(private val context: Context, private val dao: DiaryDao) {
    suspend fun create(destination: File): SyncSummary = withContext(Dispatchers.IO) {
        val diaries = dao.getAllOnce()
        val media = dao.getAllMediaOnce()
        validateCurrentFiles(media)
        destination.parentFile?.mkdirs()
        val manifest = buildManifest(diaries, media)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            media.forEach { item ->
                addFile(zip, "media/${safeName(item.fileName)}", mediaFile(item.fileName))
                if (item.thumbnailName.isNotBlank()) {
                    addFile(zip, "thumbs/${safeName(item.thumbnailName)}", thumbnailFile(item.thumbnailName))
                }
            }
        }
        SyncSummary(diaries.size, media.size)
    }

    suspend fun restore(source: File): SyncSummary = withContext(Dispatchers.IO) {
        require(source.isFile && source.length() > 0L) { "电脑返回的同步数据为空。" }
        val stageRoot = File(context.filesDir, "sync-restore-${UUID.randomUUID()}")
        val stageMedia = File(stageRoot, "diary_media")
        val stageThumbs = File(stageMedia, "thumbs")
        stageThumbs.mkdirs()
        try {
            val restored = readAndValidate(source, stageMedia, stageThumbs)
            replaceAtomically(stageRoot, stageMedia, restored.diaries, restored.media)
            SyncSummary(restored.diaries.size, restored.media.size)
        } catch (error: Exception) {
            stageRoot.deleteRecursively()
            throw IllegalArgumentException(error.message ?: "电脑备份损坏，手机数据没有改变。", error)
        }
    }

    private data class RestoredData(
        val diaries: List<DiaryEntity>,
        val media: List<DiaryMediaEntity>,
        val expectedFiles: LinkedHashMap<String, String>
    )

    private fun readAndValidate(source: File, stageMedia: File, stageThumbs: File): RestoredData {
        var restored: RestoredData? = null
        val seen = linkedSetOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            requireNotNull(entry) { "电脑备份内容为空。" }
            require(entry.name == MANIFEST_ENTRY) { "电脑备份缺少日记清单。" }
            val manifestText = zip.readLimited(MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
            restored = parseManifest(JSONObject(manifestText))
            zip.closeEntry()
            entry = zip.nextEntry
            while (entry != null) {
                val expected = restored!!.expectedFiles
                require(!entry.isDirectory && entry.name in expected) { "电脑备份包含未知文件。" }
                require(seen.add(entry.name)) { "电脑备份包含重复文件。" }
                val destination = when {
                    entry.name.startsWith("media/") -> File(stageMedia, safeName(entry.name.removePrefix("media/")))
                    entry.name.startsWith("thumbs/") -> File(stageThumbs, safeName(entry.name.removePrefix("thumbs/")))
                    else -> error("电脑备份包含不安全的文件路径。")
                }
                FileOutputStream(destination).use { zip.copyTo(it) }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val data = requireNotNull(restored)
        require(seen == data.expectedFiles.keys) { "电脑备份中的图片或视频不完整。" }
        data.expectedFiles.forEach { (entryName, digest) ->
            val file = if (entryName.startsWith("media/")) {
                File(stageMedia, safeName(entryName.removePrefix("media/")))
            } else {
                File(stageThumbs, safeName(entryName.removePrefix("thumbs/")))
            }
            require(file.isFile && sha256(file) == digest) { "电脑备份文件校验失败。" }
        }
        return data
    }

    private fun parseManifest(json: JSONObject): RestoredData {
        require(json.getInt("formatVersion") == FORMAT_VERSION) { "电脑备份版本不兼容。" }
        val diaryArray = json.getJSONArray("diaries")
        val mediaArray = json.getJSONArray("media")
        val diaries = List(diaryArray.length()) { diaryArray.getJSONObject(it).toDiary() }
        val expected = linkedMapOf<String, String>()
        val media = List(mediaArray.length()) { index ->
            val mediaJson = mediaArray.getJSONObject(index)
            val item = mediaJson.toMedia()
            putUnique(expected, "media/${item.fileName}", mediaJson.getString("fileSha256"))
            if (item.thumbnailName.isNotBlank()) {
                putUnique(expected, "thumbs/${item.thumbnailName}", mediaJson.getString("thumbnailSha256"))
            }
            item
        }
        require(diaries.map { it.id }.toSet().size == diaries.size) { "电脑备份存在重复日记。" }
        require(diaries.map { it.uuid }.toSet().size == diaries.size) { "电脑备份存在重复日记标识。" }
        require(media.map { it.mediaUuid }.toSet().size == media.size) { "电脑备份存在重复媒体标识。" }
        val diaryIds = diaries.map { it.id }.toSet()
        require(media.all { it.diaryId in diaryIds }) { "电脑备份中的媒体没有对应日记。" }
        return RestoredData(diaries, media, expected)
    }

    private suspend fun replaceAtomically(
        stageRoot: File,
        stageMedia: File,
        diaries: List<DiaryEntity>,
        media: List<DiaryMediaEntity>
    ) {
        val current = File(context.filesDir, "diary_media")
        val rollback = File(context.filesDir, "diary_media.sync-rollback")
        rollback.deleteRecursively()
        if (current.exists()) require(current.renameTo(rollback)) { "无法暂存手机现有图片和视频。" }
        try {
            require(stageMedia.renameTo(current)) { "无法写入恢复后的图片和视频。" }
            dao.replaceAll(diaries, media)
            rollback.deleteRecursively()
            stageRoot.deleteRecursively()
        } catch (error: Exception) {
            current.deleteRecursively()
            if (rollback.exists()) rollback.renameTo(current)
            throw error
        }
    }

    private fun buildManifest(diaries: List<DiaryEntity>, media: List<DiaryMediaEntity>) = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("createdAt", System.currentTimeMillis())
        put("diaries", JSONArray().apply { diaries.forEach { put(it.toJson()) } })
        put("media", JSONArray().apply {
            media.forEach { item ->
                put(item.toJson().apply {
                    put("fileSha256", sha256(mediaFile(item.fileName)))
                    put("thumbnailSha256", if (item.thumbnailName.isBlank()) "" else sha256(thumbnailFile(item.thumbnailName)))
                })
            }
        })
    }

    private fun validateCurrentFiles(media: List<DiaryMediaEntity>) {
        media.forEach { item ->
            require(mediaFile(item.fileName).isFile) { "日记“${item.originalName}”的原文件已丢失，无法同步。" }
            if (item.thumbnailName.isNotBlank()) {
                require(thumbnailFile(item.thumbnailName).isFile) { "日记“${item.originalName}”的预览图已丢失，无法同步。" }
            }
        }
    }

    private fun DiaryEntity.toJson() = JSONObject().apply {
        put("id", id); put("uuid", uuid); put("title", title); put("content", content)
        put("layoutJson", layoutJson); put("coverThumbnailName", coverThumbnailName)
        put("coverMediaType", coverMediaType); put("mediaCount", mediaCount)
        put("diaryDate", diaryDate); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toDiary() = DiaryEntity(
        id = getLong("id"), uuid = getString("uuid"), title = getString("title"), content = getString("content"),
        layoutJson = getString("layoutJson"), coverThumbnailName = getString("coverThumbnailName"),
        coverMediaType = getString("coverMediaType"), mediaCount = getInt("mediaCount"),
        diaryDate = getString("diaryDate"), createdAt = getLong("createdAt"), updatedAt = getLong("updatedAt")
    )

    private fun DiaryMediaEntity.toJson() = JSONObject().apply {
        put("id", id); put("diaryId", diaryId); put("mediaUuid", mediaUuid); put("type", type)
        put("fileName", fileName); put("thumbnailName", thumbnailName); put("mimeType", mimeType)
        put("originalName", originalName); put("sortIndex", sortIndex); put("sizeBytes", sizeBytes)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toMedia() = DiaryMediaEntity(
        id = getLong("id"), diaryId = getLong("diaryId"), mediaUuid = getString("mediaUuid"),
        type = getString("type"), fileName = safeName(getString("fileName")),
        thumbnailName = getString("thumbnailName").let { if (it.isBlank()) "" else safeName(it) },
        mimeType = getString("mimeType"), originalName = getString("originalName"),
        sortIndex = getInt("sortIndex"), sizeBytes = getLong("sizeBytes"), createdAt = getLong("createdAt")
    )

    private fun addFile(zip: ZipOutputStream, name: String, source: File) {
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(source).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun mediaFile(name: String) = File(File(context.filesDir, "diary_media"), safeName(name))
    private fun thumbnailFile(name: String) = File(File(context.filesDir, "diary_media/thumbs"), safeName(name))

    private fun safeName(name: String): String {
        require(name.isNotBlank() && name == File(name).name && '/' !in name && '\\' !in name) {
            "同步数据包含不安全的文件名。"
        }
        return name
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun InputStream.readLimited(max: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= max) { "电脑备份清单异常过大。" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun putUnique(target: LinkedHashMap<String, String>, name: String, digest: String) {
        require(digest.matches(Regex("[0-9a-f]{64}"))) { "电脑备份校验信息无效。" }
        require(target.put(name, digest) == null) { "电脑备份存在重复文件名。" }
    }

    companion object {
        const val FORMAT_VERSION = 1
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
    }
}
