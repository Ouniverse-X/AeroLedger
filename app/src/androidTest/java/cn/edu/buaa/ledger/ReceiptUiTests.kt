package cn.edu.buaa.ledger

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.buaa.ledger.recognition.ReceiptActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.anyOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReceiptUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<LedgerApp>()

    private fun image(): Uri {
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AeroLedger-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AeroLedgerTests")
        })!!
        val bitmap = Bitmap.createBitmap(900, 640, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
            listOf("虚构测试账单", "食堂午餐", "实际支付 ￥12.50", "2026-11-12")
                .forEachIndexed { row, text -> canvas.drawText(text, 50f, 90f + row * 100, paint) }
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        return uri
    }

    private fun share(uri: Uri) = Intent(app, ReceiptActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun awaitDraft() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("核对这一笔").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun coldShareWarmShareAndRecreationPreserveUnconfirmedEdits() {
        val uri = image()
        val before = runBlocking { app.ensureInitialized(); app.repository.dao.transactions().map { it.id }.toSet() }
        try {
            ActivityScenario.launch<ReceiptActivity>(share(uri)).use { scenario ->
                awaitDraft()
                compose.onNodeWithText("核对这一笔").performClick()
                compose.onNodeWithText("金额（元）").performTextReplacement("14.75")
                scenario.recreate()
                compose.onNodeWithText("金额（元）").assertTextContains("14.75")
                scenario.onActivity { it.startActivity(share(uri).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                compose.onNodeWithText("金额（元）").assertTextContains("14.75")
                compose.onNodeWithText("返回").performClick()
                scenario.onActivity { it.startActivity(share(uri).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                awaitDraft()
            }
            val after = runBlocking { app.repository.dao.transactions().map { it.id }.toSet() }
            assertEquals(before, after)
        } finally { app.contentResolver.delete(uri, null, null) }
    }

    @Test fun galleryResultUsesSameEditorAndSavesOnlyAfterConfirmation() {
        val uri = image()
        val note = "图片流程测试-${UUID.randomUUID()}"
        Intents.init()
        try {
            Intents.intending(anyOf(hasAction(MediaStore.ACTION_PICK_IMAGES), hasAction(Intent.ACTION_OPEN_DOCUMENT)))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            ActivityScenario.launch<ReceiptActivity>(Intent(app, ReceiptActivity::class.java)).use { scenario ->
                compose.onNodeWithText("从相册导入").performClick()
                awaitDraft()
                compose.onNodeWithText("核对这一笔").performClick()
                compose.onNodeWithText("金额（元）").assertTextContains("12.50")
                compose.onNodeWithText("备注").performScrollTo().performTextReplacement(note)
                compose.onNodeWithText("保存账目").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithText("已保存这一笔账目").fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                compose.onNodeWithText("已保存").assertIsNotEnabled()
                val entries = runBlocking { app.repository.dao.transactions().filter { it.note == note } }
                assertEquals(1, entries.size)
                assertEquals(1250L, entries.single().amountCents)
                assertEquals("image", entries.single().sourceType)
            }
        } finally {
            Intents.release()
            app.contentResolver.delete(uri, null, null)
            runBlocking { app.repository.dao.transactions().filter { it.note == note }.forEach { app.repository.dao.deleteTransaction(it.id) } }
        }
    }
}
