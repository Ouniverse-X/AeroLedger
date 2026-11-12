package cn.edu.buaa.ledger.recognition

import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.domain.CampusParser
import cn.edu.buaa.ledger.domain.EntryDraft
import java.io.Serializable
import java.time.LocalDate
import java.util.Locale

data class ReceiptCandidate(val draft: EntryDraft, val selected: Boolean = false, val saved: Boolean = false) : Serializable

/** Extracts conservative, editable suggestions. It cannot write to the ledger. */
class ReceiptParser {
    private val layout = ReceiptLayout()
    private val number = "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.,])"
    private val paid = Regex("(?:实际支付|实付款|实付价|实付|支付金额|付款金额)[：:\\s]*[¥￥yY长]?\\s*$number")
    private val fullDate = Regex("(?<![0-9])(20[0-9]{2}|19[0-9]{2}|2100)[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?")
    private val pending = Regex("待扣款|预计扣[费款]|待付款|待支付|交易关闭|订单已取消|已关闭")

    private val failedOrCancelled = Regex("支付失败|付款失败|交易失败|扣款失败|未支付|未付款|已取消|已撤销")

    fun candidates(lines: List<OcrLine>): List<ReceiptCandidate> {
        val rows = layout.rows(lines)
        val year = Regex("(20\\d{2})年\\s*\\d{1,2}月").find(rows.take(10).joinToString(" ") { it.text })?.groupValues?.get(1)?.toInt()
        val regions = layout.regions(rows)
        require(regions.size <= 20) { "图片包含超过20个订单区域，请裁剪目标订单后重试。" }
        return regions.map { region ->
            val draft = extract(region, year)
            ReceiptCandidate(draft, selected = draft.amount.isNotEmpty() && draft.date.isNotEmpty() && draft.prompts.size == 1)
        }
    }

    fun draft(lines: List<OcrLine>): EntryDraft {
        val candidates = candidates(lines)
        return if (candidates.size == 1) candidates.single().draft else EntryDraft(source = "image",
            prompts = listOf("识别到多笔候选，请逐笔选择核对。"))
    }

    private fun extract(rows: List<ReceiptRow>, year: Int?): EntryDraft {
        val text = rows.joinToString("\n") { it.text }
        val warnings = mutableListOf("请对照原图核对；类别为建议，确认保存后才会入账。")
        val unpaid = failedOrCancelled.containsMatchIn(text) || (pending.containsMatchIn(text) || Regex("先用后付.*实付[：: ]*[¥￥]?0(?:\\D|$)").containsMatchIn(text)) && !Regex("已自动支付|已自动扣款|已通过.*扣款").containsMatchIn(text)
        val totalRows = rows.filter { Regex("总优惠|价格明细|实付款").containsMatchIn(it.text) && paid.containsMatchIn(it.text) }
        var amounts = paid.findAll(if (totalRows.isEmpty()) text else totalRows.joinToString("\n") { it.text })
            .mapNotNull { money(it.groupValues[1]) }.toList()
        if (amounts.isEmpty()) {
            amounts = rows.flatMap { row ->
                if (Regex("实付款|[合台]计").containsMatchIn(row.text) && !Regex("拼单价|商品总价").containsMatchIn(row.text)) {
                    val values = Regex("[¥￥yY长]\\s*$number").findAll(row.text).mapNotNull { money(it.groupValues[1]) }.toList()
                    listOfNotNull(values.lastOrNull())
                } else emptyList()
            }
        }
        if (amounts.isEmpty()) amounts = rows.indices.mapNotNull { i ->
            if (i > 0 && Regex("^实付价$").matches(rows[i].text.trim()))
                Regex("^[¥￥]\\s*$number$").matchEntire(rows[i-1].text.trim())?.let { money(it.groupValues[1]) } else null
        }.distinct()
        // One detail page can repeat a total; separate orders must have structural boundaries.
        if (amounts.size > 1 && amounts.distinct().size == 1 && Regex("订单详情|实付价|实付款|价格明细").containsMatchIn(text)) amounts = amounts.distinct()
        val signed = rows.filterNot { Regex("优惠|红包|券|运费").containsMatchIn(it.text) }
            .firstNotNullOfOrNull { Regex("(?:^|\\s)([+−-])\\s*$number(?:$|\\s)").find(it.text) }
        val type = if (signed?.groupValues?.get(1) == "+" || Regex("你已收款|已收款|转账收入|收款成功").containsMatchIn(text)) "income" else "expense"
        if (amounts.isEmpty() && signed != null) amounts = listOfNotNull(money(signed.groupValues[2]))
        if (amounts.isEmpty() && Regex("送达|交易完成|订单完成|已用券").containsMatchIn(text))
            amounts = Regex("[合台]计[：:\\s]*[¥￥]?\\s*$number").findAll(text).mapNotNull { money(it.groupValues[1]) }.toList()
        if (amounts.isEmpty() && Regex("零[钱线]支付|零[钱线]扣款|支付成功|付款成功|付散成功|交易成功|你已收款|已自动支付").containsMatchIn(text))
            amounts = rows.mapNotNull { row -> Regex("^[¥￥*]?\\s*$number(?:元)?$").matchEntire(row.text.trim())?.let { money(it.groupValues[1]) } }
        if (unpaid) { amounts = emptyList(); warnings += "未确认已付款：交易可能待支付、已失败或已取消，请核对交易状态。" }
        if (Regex("退款中|退款成功|退款完成|已退款").containsMatchIn(text)) warnings += "退款状态需要人工核对原账目及收支方向。"
        if (Regex("USD|HKD|EUR|美元|港币|欧元|[€$]\\s*[0-9]", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            amounts = emptyList(); warnings += "检测到外币，当前仅支持人民币，请手动处理，不自动换算。"
        }
        if (amounts.size != 1) warnings += "未找到唯一实付金额，或无法可靠分单；请裁剪目标订单后重试，也可手动填写。"
        val date = paymentDate(rows, year)
        if (date.isEmpty()) warnings += "支付日期缺失或冲突，请补齐；下单、发货、签收日期不会替代支付日期。"
        val relevant = rows.filterNot { Regex("优惠|推荐|广告|收货人|订单编号|订单号|交易号|地址|电话|CDKEY|激活|复制", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            .joinToString(" ") { it.text }.take(2000)
        val classified = CampusParser.parse(relevant, LocalDate.of(2000, 1, 1))
        val category = when {
            type == "income" -> "other_income"
            Regex("餐饮|外卖|咖啡|午餐|晚餐|早餐|牛肉饭|汉堡|奶茶|肯德基|吉野家").containsMatchIn(relevant) -> "food"
            Regex("洗衣|水费|电费|话费|网费").containsMatchIn(relevant) -> "bills"
            Regex("日用百货|耳机|耳套|键盘|电脑|沐浴|旗舰店").containsMatchIn(relevant) -> "shopping"
            classified.type == type -> classified.category
            else -> "other"
        }
        if (!Regex("支付|实付|付款|收款|交易完成|已送达|已用券|[+−-]\\d+\\.\\d{2}").containsMatchIn(text)) warnings += "收支方向不明确，请人工核对。"
        // Keep only field evidence, never activation keys or the entire receipt in the saved entry.
        val evidence = rows.filter { paid.containsMatchIn(it.text) || fullDate.containsMatchIn(it.text) }
            .filterNot { Regex("订单号|编号|地址|收货|电话|CDKEY|激活", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            .take(12).joinToString("\n") { it.text }.take(1200)
        val note = rows.firstOrNull { Regex("^(商户|商家|商品说明|商品名称)[：:]").containsMatchIn(it.text) }
            ?.text?.substringAfter(':')?.substringAfter('：')?.take(100).orEmpty()
        return EntryDraft(amount = amounts.singleOrNull().orEmpty(), date = if (unpaid) "" else date, type = type, category = category,
            note = note, source = "image", sourceText = evidence, prompts = warnings)
    }

    private fun money(value: String): String? = runCatching {
        val cents = LedgerRules.parseMoney(value.replace(",", ""))
        String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100)
    }.getOrNull()

    private fun paymentDate(rows: List<ReceiptRow>, year: Int?): String {
        fun dates(text: String) = fullDate.findAll(text).mapNotNull { match -> runCatching {
            LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt()).toString()
        }.getOrNull() }.distinct().toList()
        val labelled = rows.indices.filter { Regex("支付时间|付款时间|收款时间|交易时间").containsMatchIn(rows[it].text) }
            .flatMap { i -> dates(rows[i].text).ifEmpty { if (i + 1 < rows.size && !Regex("时间|日期").containsMatchIn(rows[i+1].text)) dates(rows[i+1].text) else emptyList() } }.distinct()
        if (rows.any { Regex("支付时间|付款时间|收款时间|交易时间").containsMatchIn(it.text) })
            return labelled.singleOrNull().orEmpty()
        val excludedLabel = Regex("下单|创建|发货|签收|拼单|成交时间|消[费費]时间|预计|送达")
        val excluded = mutableSetOf<Int>()
        rows.forEachIndexed { index, row ->
            if (excludedLabel.containsMatchIn(row.text)) {
                excluded += index
                // OCR commonly places a field label and its value on separate lines.
                // Keep the value associated with its non-payment label as well.
                if (dates(row.text).isEmpty()) {
                    val next = (index + 1 until rows.size).firstOrNull { rows[it].text.isNotBlank() }
                    if (next != null && !Regex("时间|日期").containsMatchIn(rows[next].text)) excluded += next
                }
            }
        }
        val safe = rows.filterIndexed { index, _ -> index !in excluded }
        val standalone = safe.flatMap { dates(it.text) }.distinct()
        if (standalone.isNotEmpty()) return standalone.singleOrNull().orEmpty()
        if (year != null) {
            val partial = Regex("(?<![0-9-])(\\d{1,2})[月/-](\\d{1,2})(?:日|\\s|$)")
            return safe.flatMap { partial.findAll(it.text).mapNotNull { m -> runCatching {
                LocalDate.of(year, m.groupValues[1].toInt(), m.groupValues[2].toInt()).toString()
            }.getOrNull() }.toList() }.distinct().singleOrNull().orEmpty()
        }
        return ""
    }
}
