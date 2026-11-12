package cn.edu.buaa.ledger

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.buaa.ledger.recognition.ChineseOcrEngine
import cn.edu.buaa.ledger.recognition.ReceiptImageReader
import cn.edu.buaa.ledger.recognition.recognizeReceipt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptDeviceTests {
    private val app get() = ApplicationProvider.getApplicationContext<LedgerApp>()

    @Test fun bundledChineseOcrWorksRepeatedlyWithoutSaving() = runBlocking {
        app.ensureInitialized()
        val before = app.repository.dao.transactions().map { it.id }.toSet()
        ChineseOcrEngine().use { engine ->
            repeat(10) { index ->
                val started = android.os.SystemClock.elapsedRealtime()
                val image = Bitmap.createBitmap(900, 640, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(image)
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
                    listOf("虚构测试账单", "食堂午餐", "实际支付 ￥12.50", "2026-11-12", "测试序号 $index")
                        .forEachIndexed { row, text -> canvas.drawText(text, 50f, 90f + row * 100, paint) }
                    val text = engine.recognize(image).joinToString("\n") { it.text }
                    assertTrue(text, text.contains("12.50"))
                    assertTrue(text, text.contains("实际支付"))
                    // This is an offline/robustness smoke test, not a claim of perfect OCR accuracy.
                    android.util.Log.i("ReceiptEvidence", "sample=$index elapsedMs=${android.os.SystemClock.elapsedRealtime() - started} pssKb=${android.os.Debug.getPss()} text=$text")
                } finally { image.recycle() }
            }
        }
        assertEquals(before, app.repository.dao.transactions().map { it.id }.toSet())
    }

    @Test fun unreadableAndCorruptImagesGiveUsefulErrors() = runBlocking {
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AeroLedger-corrupt-${java.util.UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AeroLedgerTests")
        })!!
        try {
            resolver.openOutputStream(uri)!!.use { it.write("not an image".toByteArray()) }
            val failure = runCatching { ReceiptImageReader(app).read(uri) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains("损坏"))
        } finally { resolver.delete(uri, null, null) }
    }

    @Test fun oversizedAndBlankImagesHaveBoundedFailurePaths() = runBlocking {
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AeroLedger-limits-${java.util.UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AeroLedgerTests")
        })!!
        try {
            val wide = Bitmap.createBitmap(12001, 1, Bitmap.Config.ARGB_8888)
            try { resolver.openOutputStream(uri, "wt")!!.use { wide.compress(Bitmap.CompressFormat.PNG,100,it) } }
            finally { wide.recycle() }
            assertTrue(runCatching { ReceiptImageReader(app).read(uri) }.exceptionOrNull()?.message.orEmpty().contains("尺寸过大"))
            resolver.openOutputStream(uri, "wt")!!.use { output -> repeat(21) { output.write(ByteArray(1024*1024)) } }
            assertTrue(runCatching { ReceiptImageReader(app).read(uri) }.exceptionOrNull()?.message.orEmpty().contains("20 MB"))
            val blank = Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try { ChineseOcrEngine().use { assertTrue(it.recognize(blank).isEmpty()) } } finally { blank.recycle() }
            assertTrue(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("receipt-") && it.extension == "image" })
        } finally { resolver.delete(uri,null,null) }
    }
    @Test fun longImageRetainsFooterCoordinatesAndDeduplicatesOverlap() = runBlocking {
        val bitmap = Bitmap.createBitmap(900,5000,Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=48f }
            canvas.drawText("虚构长图账单",40f,100f,paint)
            canvas.drawText("实际支付 ￥12.50",40f,2160f,paint)
            canvas.drawText("支付时间 2026-11-12",40f,4750f,paint)
            val lines = ChineseOcrEngine().use { it.recognizeReceipt(bitmap) }
            assertEquals(1,lines.count { it.text.contains("12.50") })
            assertTrue(lines.any { it.text.contains("2026") && it.top>4500 })
            val draft=cn.edu.buaa.ledger.recognition.ReceiptParser().draft(lines)
            assertEquals("12.50",draft.amount); assertEquals("2026-11-12",draft.date)
        } finally { bitmap.recycle() }
    }
}
