package cn.edu.buaa.ledger.recognition

import org.junit.Assert.*
import org.junit.Test

class ReceiptScenariosTest {
    private fun parse(vararg rows: String) = ReceiptParser().candidates(rows.mapIndexed { i, s -> OcrLine(s, 0, i*80, 900, i*80+40) })
    @Test fun twentyFictionalSourceAndRiskScenarios() {
        val sources = listOf("支付宝", "微信支付", "美团外卖", "淘宝购物", "淘宝闪购", "京东购物", "手机支付提醒")
        for (source in sources) {
            val complete = parse(source, "商户：虚构早餐店", "原价 30.00", "优惠 5.00", "实际支付 ￥25.00", "支付时间 2026-11-12").single().draft
            assertEquals(source, "25.00", complete.amount)
            assertEquals("2026-11-12", complete.date)
            assertEquals("food", complete.category)
            val missing = parse(source, "实付 ￥17.00", "昨天 12:20").single().draft
            assertEquals("17.00", missing.amount); assertEquals("", missing.date)
        }
        assertEquals("", parse("仅有优惠 ￥5.00").single().draft.amount)
        assertEquals("", parse("预计扣费金额 ￥28.00", "实付 ￥0", "待扣款").single().draft.amount)
        assertEquals("", parse("实付 USD 20.00").single().draft.amount)
        assertEquals("", parse("实付 ￥25.00", "2026-11-11", "2026-11-12").single().draft.date)
        assertTrue(parse("退款成功", "实付 ￥25.00").single().draft.prompts.any { it.contains("退款") })
        assertEquals("", parse("天气晴", "气温20度").single().draft.amount)
    }
    @Test fun sameAmountDifferentOrdersAndItemRowsAreDistinctCases() {
        val two = parse("订单A", "实付 ￥25.00", "支付时间 2026-11-11", "订单B", "实付 ￥25.00", "支付时间 2026-11-12")
        assertEquals(2, two.size); assertNotEquals(two[0].draft.id, two[1].draft.id)
        assertEquals(listOf("25.00", "25.00"), two.map { it.draft.amount })
        assertEquals(listOf("2026-11-11", "2026-11-12"), two.map { it.draft.date })
        val items = parse("订单详情", "商品 实付 ￥30.90", "配送 实付 ￥0", "价格明细 总优惠 ￥21.90 实付 ￥32.80")
        assertEquals(1, items.size); assertEquals("32.80", items.single().draft.amount)
    }
    @Test fun paymentDateWinsAndGenericServiceCopyIsNotRefundState() {
        val draft = parse("实付款 ￥33.00", "极速退款", "付款时间 2026-10-16", "成交时间 2026-10-26").single().draft
        assertEquals("2026-10-16", draft.date)
        assertFalse(draft.prompts.any { it.contains("退款") })
        assertEquals("", parse("实付 ￥8.00", "下单 2026-11-11").single().draft.date)
        assertEquals("", parse("实付 ￥8.00", "消費时间 2026-11-11").single().draft.date)
        assertEquals("", parse("实付 ￥12,50").single().draft.amount)
        assertEquals("1234.50", parse("实付 ￥1,234.50").single().draft.amount)
    }
    @Test fun monthlyLedgerInheritsOnlyExplicitYear() {
        val list = parse("账单", "2026年11月", "商户甲 -25.00", "11月10日 13:00", "商户乙 -25.00", "11月9日 14:00")
        assertEquals(2, list.size)
        assertEquals(listOf("2026-11-10", "2026-11-09"), list.map { it.draft.date })
    }
    @Test fun selectionAndSavedFlagsStayWithCandidateIds() {
        val items = parse("订单A", "实付 ￥25.00", "订单B", "实付 ￥25.00")
        val state = RecognitionState(candidates = items)
        val changed = state.change(items[1].draft.id) { it.copy(selected = true, draft = it.draft.copy(amount = "26.00")) }
        assertEquals("25.00", changed.candidates[0].draft.amount)
        assertEquals("26.00", changed.candidates[1].draft.amount)
        assertFalse(changed.candidates[0].selected)
    }
    @Test fun textAndPaymentPlanStillUseSharedPrefill() {
        val date = java.time.LocalDate.of(2026,11,12)
        val parsed = cn.edu.buaa.ledger.domain.CampusParser.parse("食堂午餐12.50元 2026-11-12", date)
        val text = cn.edu.buaa.ledger.data.EntryDrafts.create(null,date,"食堂午餐12.50元 2026-11-12",parsed)
        assertEquals("12.50", text.amount); assertEquals("food", text.category); assertEquals("text",text.source)
        val plan = cn.edu.buaa.ledger.data.PaymentPlan(name="虚构电费", amountCents=2800,dueAt=0)
        val bill = cn.edu.buaa.ledger.data.EntryDrafts.create(null,date,plan=plan)
        assertEquals("28.00",bill.amount); assertEquals("bills",bill.category); assertEquals("虚构电费",bill.note)
        assertEquals("2026-11-12",bill.date)
    }
    @Test fun tooManyRegionsAskForCropInsteadOfDroppingOrders() {
        val rows=(1..21).flatMap { listOf("订单 $it", "实际支付 ￥1.00") }
        val failure=runCatching { parse(*rows.toTypedArray()) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("超过20"))
    }
    @Test fun headerAndFooterStatusesRetainEveryOrdersAmountAndDate() {
        for (status in listOf("交易完成", "已送达", "已完成", "付款成功")) {
            val footer = parse("商户：早餐店A", "实付 ￥12.00", "支付时间 2026-11-10", status,
                "商户：早餐店B", "实付 ￥34.00", "支付时间 2026-11-11", status)
            assertEquals(status, listOf("12.00", "34.00"), footer.map { it.draft.amount })
            assertEquals(status, listOf("2026-11-10", "2026-11-11"), footer.map { it.draft.date })
            assertEquals(status, listOf("早餐店A", "早餐店B"), footer.map { it.draft.note })
            val header = parse("订单列表", status, "实付 ￥12.00", "支付时间 2026-11-10",
                status, "实付 ￥34.00", "支付时间 2026-11-11")
            assertEquals(status, listOf("12.00", "34.00"), header.map { it.draft.amount })
        }
    }

}
