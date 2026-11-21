package com.jingwen.voicediary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiaryMediaFiles(private val context: Context) {
    private val mediaDir = File(context.filesDir, "diary_media").apply { mkdirs() }
    private val thumbDir = File(mediaDir, "thumbs").apply { mkdirs() }
    private val cameraDir = File(context.cacheDir, "camera_pending").apply { mkdirs() }

    suspend fun import(uri: Uri, type: DiaryMediaType): DiaryMediaEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: if (type == DiaryMediaType.VIDEO) "video/mp4" else "image/jpeg"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: if (type == DiaryMediaType.VIDEO) "mp4" else "jpg"
        val uuid = UUID.randomUUID().toString()
        val fileName = "$uuid.$extension"
        val destination = File(mediaDir, fileName)
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选文件" }
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            require(destination.length() > 0L) { "所选文件内容为空" }
            val thumbnailName = createThumbnail(destination, uuid, type)
            DiaryMediaEntity(
                diaryId = 0,
                mediaUuid = uuid,
                type = type.name,
                fileName = fileName,
                thumbnailName = thumbnailName,
                mimeType = mime,
                originalName = displayName(uri) ?: fileName,
                sortIndex = 0,
                sizeBytes = destination.length()
            )
        } catch (error: Exception) {
            destination.delete()
            File(thumbDir, "$uuid.jpg").delete()
            throw error
        }
    }

    suspend fun importSticker(sticker: StickerAsset): DiaryMediaEntity = withContext(Dispatchers.IO) {
        require(StickerCatalog.packs.flatMap { it.stickers }.any { it.id == sticker.id && it.assetPath == sticker.assetPath }) {
            "未知表情包资源"
        }
        val uuid = UUID.randomUUID().toString()
        val fileName = "$uuid.png"
        val thumbnailName = "$uuid.png"
        val destination = File(mediaDir, fileName)
        val thumbnail = File(thumbDir, thumbnailName)
        try {
            context.assets.open(sticker.assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            context.assets.open(sticker.assetPath).use { input ->
                FileOutputStream(thumbnail).use { output -> input.copyTo(output) }
            }
            require(destination.length() > 0L) { "表情文件为空" }
            DiaryMediaEntity(
                diaryId = 0,
                mediaUuid = uuid,
                type = DiaryMediaType.STICKER.name,
                fileName = fileName,
                thumbnailName = thumbnailName,
                mimeType = "image/png",
                originalName = sticker.label,
                sortIndex = 0,
                sizeBytes = destination.length()
            )
        } catch (error: Exception) {
            destination.delete()
            thumbnail.delete()
            throw error
        }
    }

    fun createCameraTarget(): Pair<File, Uri> {
        val file = File(cameraDir, "camera-${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return file to uri
    }

    fun file(fileName: String): File = File(mediaDir, File(fileName).name)

    fun thumbnail(thumbnailName: String): File = File(thumbDir, File(thumbnailName).name)

    fun shareUri(item: DiaryMediaEntity): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file(item.fileName))

    fun delete(item: DiaryMediaEntity) {
        file(item.fileName).delete()
        if (item.thumbnailName.isNotBlank()) thumbnail(item.thumbnailName).delete()
    }

    fun deleteAll(items: List<DiaryMediaEntity>) = items.forEach(::delete)

    private fun displayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun createThumbnail(source: File, uuid: String, type: DiaryMediaType): String {
        val bitmap = if (type == DiaryMediaType.VIDEO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(source.absolutePath)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            decodeSampled(source.absolutePath, 1200)
        } ?: return ""
        val scaled = scaleDown(bitmap, 1200)
        val name = "$uuid.jpg"
        FileOutputStream(File(thumbDir, name)).use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        return name
    }

    private fun decodeSampled(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxSide) return source
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
