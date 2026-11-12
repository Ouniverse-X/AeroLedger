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

/** Bounds encoded bytes and decoded pixels before allocating. Never retains the external URI. */
class ReceiptImageReader(private val context: Context) {
    suspend fun read(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
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
            if (matrix.isIdentity) bitmap else {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            }
        } finally { file.delete() }
    }

    companion object { const val MAX_BYTES = 20 * 1024 * 1024 }
}
