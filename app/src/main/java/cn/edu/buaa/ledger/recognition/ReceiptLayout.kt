package cn.edu.buaa.ledger.recognition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class ReceiptRow(val text: String, val top: Int, val bottom: Int)

/** Joins aligned OCR pieces before associating labels and amounts. No platform templates. */
internal class ReceiptLayout {
    fun rows(lines: List<OcrLine>): List<ReceiptRow> {
        val groups = mutableListOf<MutableList<OcrLine>>()
        lines.sortedBy { it.top }.forEach { line ->
            val group = groups.lastOrNull()
            val ref = group?.firstOrNull()
            val same = ref != null && abs((ref.top + ref.bottom) - (line.top + line.bottom)) <=
                max(ref.bottom - ref.top, line.bottom - line.top)
            if (same) group!!.add(line) else groups.add(mutableListOf(line))
        }
        return groups.map { group -> ReceiptRow(group.sortedBy { it.left }.joinToString(" ") { it.text }
            .replace(Regex("(?<=^|\\s)一(?=\\d+\\.\\d{2}(?:$|\\s))"), "-"),
            group.minOf { it.top }, group.maxOf { it.bottom }) }
    }

    /** Money equality is never a grouping or deduplication key. */
    fun regions(rows: List<ReceiptRow>): List<List<ReceiptRow>> {
        val signed = Regex("(?:^|\\s)[+−-]\\s*\\d+\\.\\d{2}(?:$|\\s)")
        val anchors = rows.indices.filter { signed.containsMatchIn(rows[it].text) &&
            !Regex("优惠|红包|折扣|运费|券|商品|补贴").containsMatchIn(rows[it].text) }
        if (anchors.size >= 2 && Regex("账单|支出|收入|筛选").containsMatchIn(rows.take(8).joinToString { it.text })) {
            return anchors.mapIndexed { i, a ->
                val start = if (i == 0) max(0, a - 1) else (anchors[i-1] + a) / 2 + 1
                val end = if (i == anchors.lastIndex) min(rows.size, a + 3) else (a + anchors[i+1]) / 2 + 1
                rows.subList(start, end)
            }
        }
        // Repeated card status or receipt heading is stronger evidence than repeated amount labels.
        for (pattern in listOf("^订单\\s*[A-Z一二三0-9]+", "交易完成|已送达|已完成", "使用零[钱线]支付|通过零[钱线]扣款|付款成功|付散成功")) {
            val boundaries = rows.indices.filter { Regex(pattern).containsMatchIn(rows[it].text) }
            if (boundaries.size >= 2) return boundaries.mapIndexed { i, start ->
                val from = if (pattern.contains("使用零")) max(0, start - 1) else start
                val end = if (i == boundaries.lastIndex) rows.size else if (pattern.contains("使用零")) max(start + 1, boundaries[i+1] - 1) else boundaries[i+1]
                rows.subList(from, end)
            }
        }
        return listOf(rows)
    }
}
