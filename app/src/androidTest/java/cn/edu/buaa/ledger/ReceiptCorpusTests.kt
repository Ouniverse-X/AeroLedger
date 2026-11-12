package cn.edu.buaa.ledger

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.edu.buaa.ledger.recognition.ChineseOcrEngine
import cn.edu.buaa.ledger.recognition.ReceiptParser
import cn.edu.buaa.ledger.recognition.recognizeReceipt
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in private corpus: adb run-as writes into filesDir/corpus. No images are bundled. */
class ReceiptCorpusTests {
    @Test fun evaluateLocalCorpusWithoutWritingLedger() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("privateCorpus") == "true")
        val app = ApplicationProvider.getApplicationContext<LedgerApp>()
        app.ensureInitialized()
        val before = app.repository.dao.transactions().map { it.id }.toSet()
        val folder = File(app.filesDir, "corpus")
        folder.mkdirs()
        val files = requireNotNull(folder.listFiles { file -> file.extension.equals("png", true) }) {
            "Cannot read corpus folder ${folder.path}: exists=${folder.exists()} readable=${folder.canRead()}"
        }.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        val results = JSONArray()
        ChineseOcrEngine().use { engine ->
            for (file in files) {
                val start = android.os.SystemClock.elapsedRealtime()
                val bitmap = BitmapFactory.decodeFile(file.path)
                val lines = try { engine.recognizeReceipt(bitmap) } finally { bitmap.recycle() }
                val draft = ReceiptParser().draft(lines)
                results.put(JSONObject().put("file", file.name)
                    .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start)
                    .put("pssKb", android.os.Debug.getPss())
                    .put("amount", draft.amount).put("date", draft.date)
                    .put("candidates", JSONArray(ReceiptParser().candidates(lines).map { c -> JSONObject()
                        .put("amount", c.draft.amount).put("date", c.draft.date).put("type", c.draft.type)
                        .put("category", c.draft.category).put("prompts", JSONArray(c.draft.prompts)) }))
                    .put("lines", JSONArray(lines.map { JSONObject().put("text", it.text)
                        .put("left", it.left).put("top", it.top).put("right", it.right).put("bottom", it.bottom) })))
            }
        }
        File(folder, "ocr-results.json").writeText(results.toString(2))
        assertEquals(before, app.repository.dao.transactions().map { it.id }.toSet())
    }
}
