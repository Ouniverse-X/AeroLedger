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
import androidx.lifecycle.ViewModelProvider
import cn.edu.buaa.ledger.recognition.RecognitionViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cn.edu.buaa.ledger.recognition.ReceiptActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.anyOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReceiptUiTests {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<LedgerApp>()

    @Before fun settlePreviousActivityTransitions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        UiDevice.getInstance(instrumentation).waitForIdle()
        instrumentation.waitForIdleSync()
        // Remove only UUID-tagged entries owned by an earlier interrupted run of this test.
        runBlocking {
            app.ensureInitialized()
            app.repository.dao.transactions().filter {
                it.sourceType == "image" && Regex("^多单测试-[0-9a-f-]{36}-[AC]$").matches(it.note)
            }.forEach { app.repository.dao.deleteTransaction(it.id) }
        }
    }

    private fun recreate(scenario: ActivityScenario<ReceiptActivity>) {
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        scenario.recreate()
        compose.waitForIdle()
    }

    private fun image(lines: List<String> = listOf("虚构测试账单", "食堂午餐", "实际支付 ￥12.50", "2026-11-12")): Uri {
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AeroLedger-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AeroLedgerTests")
        })!!
        val bitmap = Bitmap.createBitmap(1000, maxOf(640, lines.size * 100 + 100), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
            lines.forEachIndexed { row, text -> canvas.drawText(text, 50f, 90f + row * 100, paint) }
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
                recreate(scenario)
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
                recreate(scenario)
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

    @Test fun selectedOrdersSaveSequentiallyWithoutMixingEditedValues() {
        val uri = image(listOf("订单 A", "实际支付 ￥12.50", "支付时间 2026-11-12",
            "订单 B", "实际支付 ￥12.50", "支付时间 2026-11-12",
            "订单 C", "实际支付 ￥12.50", "支付时间 2026-11-12"))
        val prefix = "多单测试-${UUID.randomUUID()}"
        val before = runBlocking { app.ensureInitialized(); app.repository.dao.transactions().map { it.id }.toSet() }
        try {
            ActivityScenario.launch<ReceiptActivity>(share(uri)).use { scenario ->
                compose.waitUntil(30000) { compose.onAllNodesWithTag("candidate-2").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("candidate-1").performScrollTo().performClick()
                compose.onNodeWithText("核对选中的订单").performScrollTo().performClick()
                compose.onNodeWithText("金额（元）").performScrollTo().performTextReplacement("14.75")
                compose.onNodeWithText("备注").performScrollTo().performTextReplacement("$prefix-A")
                compose.onNodeWithText("保存账目").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithText("已保存这一笔账目").fetchSemanticsNodes().isNotEmpty() }
                recreate(scenario)
                compose.onNodeWithText("金额（元）").performScrollTo().assertTextContains("12.50")
                compose.onNodeWithText("金额（元）").performTextReplacement("23.75")
                compose.onNodeWithText("备注").performScrollTo().performTextReplacement("$prefix-C")
                compose.onNodeWithText("保存账目").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithTag("candidate-1").fetchSemanticsNodes().isNotEmpty() }
                recreate(scenario)
                compose.onNodeWithTag("candidate-0").assertIsNotEnabled()
                compose.onNodeWithTag("candidate-2").assertIsNotEnabled()
                val added = runBlocking { app.repository.dao.transactions().filter { it.id !in before } }
                assertEquals(2, added.size)
                assertEquals(setOf(1475L,2375L), added.map { it.amountCents }.toSet())
            }
        } finally {
            app.contentResolver.delete(uri,null,null)
            runBlocking { app.repository.dao.transactions().filter { it.note.startsWith(prefix) }.forEach { app.repository.dao.deleteTransaction(it.id) } }
        }
    }

    @Test fun realSystemPhotoPickerCanImportNewestFictionalImage() {
        val uri = image()
        val before = runBlocking { app.ensureInitialized(); app.repository.dao.transactions().map { it.id }.toSet() }
        try {
            ActivityScenario.launch<ReceiptActivity>(Intent(app, ReceiptActivity::class.java)).use {
                compose.onNodeWithText("从相册导入").performClick()
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val thumbnail = device.wait(Until.findObject(By.res("com.google.android.providers.media.module", "icon_thumbnail")), 10000)
                    ?: device.findObject(By.res("com.android.providers.media.module", "icon_thumbnail"))
                if (thumbnail == null) device.dumpWindowHierarchy(java.io.File(app.filesDir,"photo-picker.xml"))
                requireNotNull(thumbnail) { "Photo Picker thumbnail unavailable" }.click()
                awaitDraft()
                compose.onNodeWithText("核对这一笔").performScrollTo().performClick()
                compose.onNodeWithText("金额（元）").performScrollTo().assertTextContains("12.50")
            }
            assertEquals(before, runBlocking { app.repository.dao.transactions().map { it.id }.toSet() })
        } finally { app.contentResolver.delete(uri,null,null) }
    }
    @Test fun originalPreviewAndReplacementProtectUnsavedEdits() {
        val first = image()
        val replacement = image(listOf("虚构测试账单", "实际支付 ￥20.00", "支付时间 2026-11-12"))
        val before = runBlocking { app.ensureInitialized(); app.repository.dao.transactions().map { it.id }.toSet() }
        try {
            ActivityScenario.launch<ReceiptActivity>(share(first)).use { scenario ->
                lateinit var model: RecognitionViewModel
                scenario.onActivity { model = ViewModelProvider(it)[RecognitionViewModel::class.java] }
                awaitDraft()
                val originalId = model.state.value.draft!!.id
                val originalSource = model.state.value.sourceName!!
                compose.onNodeWithText("核对这一笔").performClick()
                compose.onNodeWithText("金额（元）").performTextReplacement("14.75")
                compose.waitUntil { model.state.value.draft?.amount == "14.75" }
                compose.onNodeWithText("查看原图").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("本次导入的账单原图").fetchSemanticsNodes().isNotEmpty() }
                recreate(scenario)
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("本次导入的账单原图").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("本次导入的账单原图").assertExists()
                compose.onNodeWithText("关闭原图").performClick()
                compose.onNodeWithText("金额（元）").assertTextContains("14.75")
                compose.onNodeWithText("返回").performClick()
                val invalid = Uri.parse("content://cn.edu.buaa.ledger.invalid/missing.png")
                scenario.onActivity { it.startActivity(share(invalid)) }
                compose.onNodeWithText("替换未保存的候选？").assertExists()
                recreate(scenario)
                compose.onNodeWithText("保留当前候选").performClick()
                assertEquals(originalId, model.state.value.draft!!.id)
                assertEquals("14.75", model.state.value.draft!!.amount)
                scenario.onActivity { it.startActivity(share(invalid)) }
                compose.onNodeWithText("识别新图并替换").performClick()
                compose.waitUntil(10000) { !model.busy.value && model.state.value.message.orEmpty().contains("原候选及修改已保留") }
                assertEquals(originalId, model.state.value.draft!!.id)
                assertEquals(originalSource, model.state.value.sourceName)
                assertEquals("14.75", model.state.value.draft!!.amount)
                scenario.onActivity { it.startActivity(share(replacement)) }
                compose.onNodeWithText("识别新图并替换").performClick()
                compose.waitUntil(30000) { !model.busy.value && model.state.value.draft?.amount == "20.00" }
                assertEquals(false, java.io.File(app.cacheDir, "receipt-sessions/$originalSource").exists())
                assertEquals(before, runBlocking { app.repository.dao.transactions().map { it.id }.toSet() })
            }
        } finally {
            app.contentResolver.delete(first, null, null)
            app.contentResolver.delete(replacement, null, null)
        }
    }
}
