package cn.edu.buaa.ledger.recognition

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) : Serializable

/** The only replaceable boundary: production uses the bundled offline Chinese model. */
interface OcrEngine : Closeable {
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
}

/** Shared production/test path for bounded-height OCR; only overlapping spatial duplicates are removed. */
suspend fun OcrEngine.recognizeReceipt(bitmap: Bitmap): List<OcrLine> {
    // Normal phone screenshots fit this budget. Splitting them degraded amount OCR and added latency.
    if (bitmap.height <= 3600 && bitmap.width.toLong() * bitmap.height <= 6_000_000)
        return withContext(NonCancellable) { recognize(bitmap) }
    val result = mutableListOf<OcrLine>()
    var top = 0
    while (top < bitmap.height) {
        currentCoroutineContext().ensureActive()
        val height = minOf(2200, bitmap.height - top)
        val strip = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
        // The native Task owns the bitmap until it completes, including coroutine cancellation.
        val lines = try { withContext(NonCancellable) { recognize(strip) } }
            finally { if (strip !== bitmap) strip.recycle() }
        result += lines.map { it.copy(top = it.top + top, bottom = it.bottom + top) }
            .filter { line -> result.none { old -> old.text == line.text &&
                kotlin.math.abs(old.top - line.top) < 40 && kotlin.math.abs(old.left - line.left) < 40 } }
        if (top + height >= bitmap.height) break
        top += height - 160
    }
    return result
}

class ChineseOcrEngine : OcrEngine {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> = suspendCancellableCoroutine { continuation ->
        // ML Kit has no cancellation token here. Image lifetime is owned by the caller until completion.
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line -> line.boundingBox?.let { box ->
                        OcrLine(line.text, box.left, box.top, box.right, box.bottom)
                    } }
                })
            }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }

    override fun close() = recognizer.close()
}
