package cn.edu.buaa.ledger.recognition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.buaa.ledger.ui.EntryEditor
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
import cn.edu.buaa.ledger.capture.CaptureActivity
import cn.edu.buaa.ledger.capture.CaptureStore
import java.time.LocalDate

/** Gallery and ACTION_SEND both enter the same confirmation screen, cold or warm. */
class ReceiptActivity : ComponentActivity() {
    private val model: RecognitionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptShare(intent)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val busy by model.busy.collectAsStateWithLifecycle()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) model.recognize(uri)
            }
            BackHandler(state.editing || busy) { if (!busy) model.stopEditing() }
            var showOriginal by rememberSaveable { mutableStateOf(false) }
            LedgerTheme("system") {
                if (showOriginal && state.sourceName != null)
                    ReceiptOriginal(state.sourceName!!) { showOriginal = false }
                if (state.pendingImport != null) AlertDialog(
                    onDismissRequest = model::cancelReplacement,
                    title = { Text("替换未保存的候选？") },
                    text = { Text("当前仍有未保存的订单。新图识别成功后会替换这些候选；如果识别失败，原候选和修改会保留。") },
                    confirmButton = { TextButton(onClick = model::confirmReplacement) { Text("识别新图并替换") } },
                    dismissButton = { TextButton(onClick = model::cancelReplacement) { Text("保留当前候选") } }
                )
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { if (state.editing) model.stopEditing() else finish() }, enabled = !busy) { Text("返回") }
                            if (state.sourceName != null) TextButton(onClick = { showOriginal = true }) { Text("查看原图") }
                            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !busy && !state.editing) { Text("从相册导入") }
                        }
                        if (state.editing && state.draft != null) {
                            state.message?.let { Text(it, Modifier.padding(horizontal = 20.dp)) }
                            key(state.draft!!.id) {
                                EntryEditor(original = null, defaultDate = LocalDate.now(), busy = busy,
                                    onSave = { entry, _ -> model.save(entry) }, onClose = model::stopEditing, onDelete = {}, draft = state.draft,
                                    onDraftChange = model::updateDraft)
                            }
                        } else {
                            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("图片记账", style = MaterialTheme.typography.headlineMedium)
                                Text("选择账单截图，或在相册中分享给航小账。文字识别在本机完成，核对确认后才会入账。")
                                TextButton(onClick = { startActivity(Intent(this@ReceiptActivity, CaptureActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) }, enabled = !busy) { Text("授权截取一张账单") }
                                TextButton(onClick = {
                                    val store = CaptureStore(this@ReceiptActivity)
                                    val uri = store.result()
                                    if (uri != null) model.recognize(uri) else model.showMessage(store.message() ?: "暂无截图。可在快捷设置中添加‘截屏记账’磁贴，在目标应用内发起截图。")
                                }, enabled = !busy) { Text("打开最近截图") }
                                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.message?.let { Text(it) }
                                state.draft?.let {
                                    Text("识别用时 ${state.elapsedMs} ms · 请核对原文", style = MaterialTheme.typography.titleMedium)
                                    Text("已保存 ${state.candidates.count { it.saved }} / ${state.candidates.size} 笔；每笔都需要核对并按保存。")
                                    state.candidates.forEachIndexed { index, candidate ->
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row {
                                                    if (state.candidates.size > 1) Checkbox(candidate.selected,
                                                        { model.select(candidate.draft.id, it) }, enabled = !busy && !candidate.saved,
                                                        modifier = Modifier.testTag("candidate-$index"))
                                                    Text("候选 ${index + 1} · ${if (candidate.draft.type == "income") "收入" else "支出"} ¥${candidate.draft.amount.ifEmpty { "待填写" }} · ${candidate.draft.date.ifEmpty { "日期待填写" }}${if (candidate.saved) " · 已入账" else ""}")
                                                }
                                                candidate.draft.prompts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                                if (candidate.draft.sourceText.isNotBlank()) Text(candidate.draft.sourceText, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    Button(onClick = model::edit, enabled = !busy && !state.saved &&
                                        (state.candidates.size == 1 || state.candidates.any { it.selected && !it.saved }), modifier = Modifier.fillMaxWidth()) {
                                        Text(if (state.saved) "已保存" else if (state.candidates.size == 1) "核对这一笔" else "核对选中的订单")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }

    private fun acceptShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) model.recognize(uri)
    }
}
