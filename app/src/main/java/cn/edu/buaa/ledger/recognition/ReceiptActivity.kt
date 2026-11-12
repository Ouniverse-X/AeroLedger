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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.buaa.ledger.ui.EntryEditor
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
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
            LedgerTheme("system") {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { if (state.editing) model.stopEditing() else finish() }, enabled = !busy) { Text("返回") }
                            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !busy && !state.editing) { Text("从相册导入") }
                        }
                        if (state.editing && state.draft != null) {
                            state.message?.let { Text(it, Modifier.padding(horizontal = 20.dp)) }
                            key(state.draft!!.id) {
                                EntryEditor(original = null, defaultDate = LocalDate.now(), busy = busy,
                                    onSave = model::save, onClose = model::stopEditing, onDelete = {}, draft = state.draft,
                                    onDraftChange = model::updateDraft)
                            }
                        } else {
                            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("图片记账", style = MaterialTheme.typography.headlineMedium)
                                Text("选择账单截图，或在相册中分享给航小账。文字识别在本机完成，核对确认后才会入账。")
                                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.message?.let { Text(it) }
                                state.draft?.let { draft ->
                                    Text("识别用时 ${state.elapsedMs} ms · 请核对原文", style = MaterialTheme.typography.titleMedium)
                                    draft.prompts.forEach { Text(it) }
                                    Button(onClick = model::edit, enabled = !busy && !state.saved, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (state.saved) "已保存" else "核对这一笔")
                                    }
                                    Text(draft.sourceText)
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
