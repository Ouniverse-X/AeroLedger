package cn.edu.buaa.ledger.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ReceiptImage(val bitmap: Bitmap, val sourceName: String? = null)

/** Bounded decoding; only the active session keeps a private copy for visual confirmation. */
class ReceiptImageReader(private val context: Context) {
    private val sessions get() = File(context.cacheDir, "receipt-sessions").apply { mkdirs() }

    suspend fun read(uri: Uri): Bitmap = load(uri, false).bitmap
    suspend fun readForSession(uri: Uri): ReceiptImage = load(uri, true)
    suspend fun preview(name: String): Bitmap = withContext(Dispatchers.IO) {
        val file = sourceFile(name)
        require(file.isFile) { "原图缓存已被系统清理，请重新导入图片。已核对的字段仍保留。" }
        decode(file)
    }
    fun pruneSources(except: String?) {
        val oldest = System.currentTimeMillis() - 60 * 60 * 1000
        sessions.listFiles()?.filter { it.name != except && it.lastModified() < oldest }?.forEach { it.delete() }
    }
    fun deleteSource(name: String?) { if (name != null) runCatching { sourceFile(name).delete() } }
    private fun sourceFile(name: String): File {
        require(Regex("[a-f0-9-]+\\.image").matches(name)) { "无效的图片缓存" }
        return File(sessions, name)
    }

    private suspend fun load(uri: Uri, retain: Boolean): ReceiptImage = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" || uri.scheme == "android.resource") { "请通过相册或系统分享选择图片" }
        val file = File(context.cacheDir, "receipt-${UUID.randomUUID()}.image")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "图片超过 20 MB，请裁剪后重试" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("图片无法读取，请重新选择")
            val bitmap = decode(file)
            if (!retain) ReceiptImage(bitmap) else {
                val name = "${UUID.randomUUID()}.image"
                try {
                    check(file.renameTo(sourceFile(name))) { "无法保留核对原图，请重试" }
                    ReceiptImage(bitmap, name)
                } catch (failure: Exception) { bitmap.recycle(); throw failure }
            }
        } finally { file.delete() }
    }

    private fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片损坏或格式不支持" }
        require(bounds.outWidth <= 12000 && bounds.outHeight <= 30000 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000) { "图片尺寸过大，请裁剪后重试" }
        var sample = 1
        while (bounds.outWidth / sample > 1600 ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 12_000_000) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("图片损坏或格式不支持")
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val matrix = Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        if (matrix.isIdentity) return bitmap
        try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        } catch (failure: Exception) { bitmap.recycle(); throw failure }
    }

    companion object { const val MAX_BYTES = 20 * 1024 * 1024 }
}
