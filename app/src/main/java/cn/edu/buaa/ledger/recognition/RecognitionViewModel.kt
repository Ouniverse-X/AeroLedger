package cn.edu.buaa.ledger.recognition

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cn.edu.buaa.ledger.LedgerApp
import cn.edu.buaa.ledger.data.TransactionEntity
import cn.edu.buaa.ledger.domain.EntryDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Serializable

data class RecognitionState(
    val draft: EntryDraft? = null, val editing: Boolean = false,
    val saved: Boolean = false, val message: String? = null, val elapsedMs: Long = 0
) : Serializable

/** Owns one import session. Recognition never writes to the ledger. */
class RecognitionViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val state = savedState.getStateFlow("receipt", RecognitionState())
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val reader = ReceiptImageReader(application)
    private val parser = ReceiptParser()

    init {
        if (state.value.draft == null && state.value.message == "正在离线识别，请稍候…") {
            update(RecognitionState(message = "上次识别被系统中断，请重新选择图片。"))
        }
    }

    private fun update(next: RecognitionState) { savedState["receipt"] = next }
    fun updateDraft(draft: EntryDraft) {
        val current = state.value
        if (current.editing && !current.saved && current.draft?.id == draft.id) update(current.copy(draft = draft))
    }
    fun edit() { if (!working.value && !state.value.saved) update(state.value.copy(editing = true)) }
    fun stopEditing() { if (!working.value) update(state.value.copy(editing = false)) }

    fun recognize(uri: Uri) {
        if (working.value) return
        if (state.value.editing) {
            update(state.value.copy(message = "当前核对内容已保留。请返回后再导入下一张图片。"))
            return
        }
        // Set synchronously to prevent two rapid share/picker events starting overlapping OCR jobs.
        working.value = true
        update(RecognitionState(message = "正在离线识别，请稍候…"))
        viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                val lines = withContext(Dispatchers.Default) {
                    val bitmap = reader.read(uri)
                    try { readStrips(bitmap) } finally { bitmap.recycle() }
                }
                require(lines.any { it.text.isNotBlank() }) { "未识别到文字，请选择清晰账单，或手动记账" }
                require(lines.sumOf { it.text.length } <= 12000) { "图片文字过多，请裁剪到目标订单后重试" }
                update(RecognitionState(draft = parser.draft(lines), elapsedMs = android.os.SystemClock.elapsedRealtime() - started))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                update(RecognitionState(message = failure.message ?: "图片识别失败，请重新选择或手动记账"))
            } finally { working.value = false }
        }
    }

    private suspend fun readStrips(bitmap: Bitmap): List<OcrLine> {
        val result = mutableListOf<OcrLine>()
        ChineseOcrEngine().use { engine ->
            var top = 0
            while (top < bitmap.height) {
                currentCoroutineContext().ensureActive()
                val height = minOf(2200, bitmap.height - top)
                val strip = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
                // ML Kit keeps the Bitmap until its task finishes; do not recycle it on coroutine cancellation.
                val lines = try { withContext(NonCancellable) { engine.recognize(strip) } }
                    finally { if (strip !== bitmap) strip.recycle() }
                result += lines.map { it.copy(top = it.top + top, bottom = it.bottom + top) }
                    .filter { line -> result.none { old -> old.text == line.text && kotlin.math.abs(old.top - line.top) < 40 } }
                if (top + height >= bitmap.height) break
                top += height - 160
            }
        }
        return result
    }

    fun save(entry: TransactionEntity, done: () -> Unit) {
        val current = state.value
        if (working.value || current.saved || current.draft?.id != entry.id) return
        working.value = true
        viewModelScope.launch {
            try {
                val app = getApplication<LedgerApp>()
                app.ensureInitialized()
                app.repository.saveEntry(entry)
                update(current.copy(saved = true, editing = false, message = "已保存这一笔账目"))
                done()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                update(current.copy(message = failure.message ?: "保存失败，请重试"))
            } finally { working.value = false }
        }
    }
}
