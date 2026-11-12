package cn.edu.buaa.ledger

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import cn.edu.buaa.ledger.recognition.ChineseOcrEngine
import cn.edu.buaa.ledger.recognition.ReceiptParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReceiptFixturesTests {
    @Test fun exportAndRecognizeTwentyFictionalScenarios() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApp>()
        val folder = File(app.filesDir, "fictional-samples").apply { mkdirs() }
        val rows = mutableListOf<List<String>>()
        listOf("支付宝", "微信支付", "美团外卖", "淘宝购物", "淘宝闪购", "京东购物", "手机支付提醒").forEach { source ->
            rows += listOf("虚构测试 $source", "早餐店", "原价 ￥30.00", "优惠 ￥5.00", "实际支付 ￥25.00", "支付时间 2026-11-12")
            rows += listOf("虚构测试 $source", "实际支付 ￥17.00", "昨天 12:20")
        }
        rows += listOf(listOf("虚构缺金额", "优惠 ￥5.00"), listOf("虚构待支付", "预计扣费 ￥28.00", "实付 ￥0", "待扣款"),
            listOf("虚构外币", "实付 USD 20.00"), listOf("虚构日期冲突", "实付 ￥25.00", "2026-11-11", "2026-11-12"),
            listOf("订单 A", "实付 ￥25.00", "支付时间 2026-11-12", "订单 B", "实付 ￥25.00", "支付时间 2026-11-12"),
            listOf("虚构无关内容", "天气晴", "气温20度"))
        val evidence = StringBuilder("id\tamount\tdate\telapsedMs\tpssKb\n")
        ChineseOcrEngine().use { engine ->
            rows.forEachIndexed { index, lines ->
                val start = android.os.SystemClock.elapsedRealtime()
                val bitmap = Bitmap.createBitmap(1000, 900, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f }
                    lines.forEachIndexed { i, text -> canvas.drawText(text, 40f, 90f + i * 110, paint) }
                    File(folder, "fictional-%02d.png".format(index+1)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val ocr = engine.recognize(bitmap)
                    val draft = ReceiptParser().draft(ocr)
                    if (index < 14) assertEquals("fixture $index", if (index % 2 == 0) "25.00" else "17.00", draft.amount)
                    if (index < 14 && index % 2 == 1) assertEquals("", draft.date)
                    if (index in listOf(14,15,16,19)) assertEquals("", draft.amount)
                    if (index == 18) assertEquals(listOf("25.00", "25.00"), ReceiptParser().candidates(ocr).map { it.draft.amount })
                    evidence.append("${index+1}\t${draft.amount}\t${draft.date}\t${android.os.SystemClock.elapsedRealtime()-start}\t${android.os.Debug.getPss()}\n")
                } finally { bitmap.recycle() }
            }
        }
        File(folder, "results.tsv").writeText(evidence.toString())
    }
}
