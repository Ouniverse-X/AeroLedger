package cn.edu.buaa.ledger.recognition

import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.data.EntryDrafts
import cn.edu.buaa.ledger.data.TransactionEntity
import cn.edu.buaa.ledger.domain.CategoryCatalog
import cn.edu.buaa.ledger.domain.EntryDraft
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private fun parse(vararg text: String) = ReceiptParser().draft(text.mapIndexed { i, line -> OcrLine(line, 0, i * 50, 500, i * 50 + 40) })

    @Test fun paidAmountWinsOverDiscountAndBalance() {
        val draft = parse("食堂午餐", "原价 30.00", "优惠 5.00", "实际支付 ￥25.00", "余额 600.00", "2026年11月12日")
        assertEquals("25.00", draft.amount)
        assertEquals("2026-11-12", draft.date)
        assertEquals("food", draft.category)
    }

    @Test fun missingAndConflictingDatesStayEmpty() {
        assertEquals("", parse("实付 12.50").date)
        assertEquals("", parse("实付 12.50", "2026-11-11", "2026-11-12").date)
        assertEquals("", parse("实付 12.50", "2026-02-30").date)
    }

    @Test fun ambiguityDoesNotChooseArbitraryMoney() {
        assertEquals("", parse("优惠 12.50", "余额 ￥50.00").amount)
        assertEquals("", parse("实付 12.50", "实际支付 50.00").amount)
        assertEquals("", parse("实付 12.50", "实付 12.50").amount)
        assertEquals("", parse("实付 0").amount)
    }

    @Test fun suspiciousStatusGetsPrompt() {
        assertTrue(parse("退款中", "实付 12.50").prompts.any { it.contains("退款") })
    }

    @Test fun categoriesRemainCompatibleWithPersistedRecords() {
        assertEquals(10, CategoryCatalog.ALL.size)
        assertEquals(10, CategoryCatalog.ALL.map { it.code }.distinct().size)
        assertArrayEquals(arrayOf("餐饮", "购物", "交通", "学习", "生活缴费", "其他支出"), LedgerRules.EXPENSE_CATEGORIES)
        assertArrayEquals(arrayOf("生活费", "奖助学金", "兼职", "其他收入"), LedgerRules.INCOME_CATEGORIES)
    }

    @Test fun originalIdentityAndSourceSurvivePrefill() {
        val original = TransactionEntity("existing", "expense", 1250, 20000, "food", "午餐", "csv", "原始数据")
        val draft = EntryDrafts.create(original, LocalDate.now(), image = EntryDraft(amount = "99.00"))
        assertEquals(original.id, draft.id)
        assertEquals("csv", draft.source)
        assertEquals("原始数据", draft.sourceText)
        assertEquals("12.50", draft.amount)
    }

    @Test fun imageDraftKeepsIdentityAndMissingFields() {
        val image = EntryDraft(amount = "8.00", source = "image")
        assertEquals(image, EntryDrafts.create(null, LocalDate.now(), image = image))
        assertEquals("", image.date)
        assertNotEquals(image.id, EntryDraft().id)
    }
}
