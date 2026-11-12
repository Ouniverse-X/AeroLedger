package cn.edu.buaa.ledger.recognition

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) : Serializable

/** The only replaceable boundary: production uses the bundled offline Chinese model. */
interface OcrEngine : Closeable {
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
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
