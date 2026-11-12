package cn.edu.buaa.ledger.recognition

import android.app.Application
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
    val candidates: List<ReceiptCandidate> = emptyList(), val activeId: String? = null,
    val message: String? = null, val elapsedMs: Long = 0
) : Serializable {
    val draft get() = candidates.firstOrNull { it.draft.id == activeId }?.draft ?: candidates.firstOrNull()?.draft
    val editing get() = activeId != null
    val saved get() = candidates.isNotEmpty() && candidates.all { it.saved }
    fun change(id: String, transform: (ReceiptCandidate) -> ReceiptCandidate) = copy(candidates = candidates.map { if (it.draft.id == id) transform(it) else it })
}

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
    fun showMessage(message: String) { update(state.value.copy(message = message)) }
    fun updateDraft(draft: EntryDraft) {
        val current = state.value
        if (current.editing && current.activeId == draft.id) update(current.change(draft.id) { if (it.saved) it else it.copy(draft = draft) })
    }
    fun select(id: String, selected: Boolean) {
        if (!working.value && !state.value.editing) update(state.value.change(id) { if (it.saved) it else it.copy(selected = selected) })
    }
    fun edit() {
        if (working.value) return
        val current = state.value
        val next = current.candidates.firstOrNull { !it.saved && (it.selected || current.candidates.size == 1) } ?: return
        update(current.change(next.draft.id) { it.copy(selected = true) }.copy(activeId = next.draft.id, message = null))
    }
    fun stopEditing() { if (!working.value) update(state.value.copy(activeId = null)) }

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
                    try { ChineseOcrEngine().use { it.recognizeReceipt(bitmap) } } finally { bitmap.recycle() }
                }
                require(lines.any { it.text.isNotBlank() }) { "未识别到文字，请选择清晰账单，或手动记账" }
                require(lines.none { Regex("content hidden.*security|内容.*隐藏|屏幕.*保护", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }) {
                    "画面受到系统或应用保护，无法识别。请使用可导出的图片、相册或手动记账。"
                }
                require(lines.sumOf { it.text.length } <= 12000) { "图片文字过多，请裁剪到目标订单后重试" }
                update(RecognitionState(candidates = parser.candidates(lines), elapsedMs = android.os.SystemClock.elapsedRealtime() - started))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                update(RecognitionState(message = failure.message ?: "图片识别失败，请重新选择或手动记账"))
            } finally { working.value = false }
        }
    }

    fun save(entry: TransactionEntity) {
        val current = state.value
        val candidate = current.candidates.firstOrNull { it.draft.id == entry.id } ?: return
        if (working.value || candidate.saved || !candidate.selected || current.activeId != entry.id) return
        working.value = true
        viewModelScope.launch {
            try {
                val app = getApplication<LedgerApp>()
                app.ensureInitialized()
                app.repository.saveEntry(entry)
                val updated = current.change(entry.id) { it.copy(saved = true, selected = false) }
                val next = updated.candidates.firstOrNull { it.selected && !it.saved }
                update(updated.copy(activeId = next?.draft?.id, message = "已保存这一笔账目"))
                // The queue owns navigation: EntryEditor's close callback must not dismiss the next draft.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                update(current.copy(message = failure.message ?: "保存失败，请重试"))
            } finally { working.value = false }
        }
    }
}
