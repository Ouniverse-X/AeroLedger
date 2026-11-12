package cn.edu.buaa.ledger.recognition

import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.domain.CampusParser
import cn.edu.buaa.ledger.domain.EntryDraft
import java.time.LocalDate

/** Conservative first-stage prefill. Ambiguous amounts/dates deliberately stay empty. */
class ReceiptParser {
    fun draft(lines: List<OcrLine>): EntryDraft {
        val text = lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left }).joinToString("\n") { it.text }
        val paid = Regex("(?:实付|实际支付|支付金额|实付款|付款金额)[：:\\s]*[¥￥]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.])")
        val amounts = paid.findAll(text).map { it.groupValues[1] }
            .filter { runCatching { LedgerRules.parseMoney(it) }.isSuccess }.toList()
        val dates = Regex("(?<![0-9])(20[0-9]{2}|19[0-9]{2}|2100)[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?")
            .findAll(text).mapNotNull { match -> runCatching {
                LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt()).toString()
            }.getOrNull() }.distinct().toList()
        val classification = CampusParser.parse(text.take(2000), LocalDate.now())
        val prompts = mutableListOf("请对照图片核对金额、日期、收支方向和类别，确认后才会入账。")
        if (amounts.size != 1) prompts += "未找到唯一的实付金额，请手动填写；多订单请分别裁剪后导入。"
        if (dates.size != 1) prompts += "日期缺失或存在多个日期，请补齐交易日期。"
        if (Regex("待支付|待付款|已关闭|交易关闭|退款|美元|USD|HKD|港币").containsMatchIn(text))
            prompts += "可能包含未支付、退款或外币信息，请核实是否应入账。"
        return EntryDraft(amount = amounts.singleOrNull().orEmpty(), date = dates.singleOrNull().orEmpty(),
            type = classification.type, category = classification.category, source = "image",
            sourceText = text.take(12000), prompts = prompts)
    }
}
