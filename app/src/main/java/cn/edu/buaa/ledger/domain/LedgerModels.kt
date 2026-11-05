package cn.edu.buaa.ledger.domain

import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.data.*
import java.time.LocalDate
import java.time.YearMonth

data class LedgerData(val entries: List<TransactionEntity> = emptyList(), val budgets: List<BudgetEntity> = emptyList(),
    val plans: List<PaymentPlan> = emptyList(), val reviews: List<ReviewEntity> = emptyList(), val ready: Boolean = false)

data class FilterSpec(val month: YearMonth = YearMonth.now(), val day: LocalDate? = null,
    val type: String? = null, val category: String? = null) {
    fun matches(e: TransactionEntity): Boolean {
        val date = LocalDate.ofEpochDay(e.dateEpochDay)
        return (if (day == null) YearMonth.from(date) == month else date == day) &&
            (type == null || e.type == type) && (category == null || e.categoryCode == category)
    }
    fun select(entries: List<TransactionEntity>) = entries.filter(::matches)
    val label: String get() = day?.toString() ?: "${month.year} 年 ${month.monthValue} 月"
}

data class Totals(val expense: Long, val income: Long, val count: Int) {
    val balance get() = income - expense
}
fun List<TransactionEntity>.totals() = Totals(filter { it.type == "expense" }.sumOf { it.amountCents },
    filter { it.type == "income" }.sumOf { it.amountCents }, size)
fun Long.money() = LedgerRules.money(this)
fun LedgerData.monthEntries(month: YearMonth) = entries.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
fun LedgerData.budget(month: YearMonth) = budgets.firstOrNull { it.month == month.toString() }?.amountCents ?: 0L
fun reviewSignature(entries: List<TransactionEntity>, budget: Long): String = buildString {
    append(budget); entries.sortedBy { it.id }.forEach { append('|'); append(it.toEntry().fingerprint()) }
}

data class ImportRow(val ordinal: Int, val entry: TransactionEntity?, val state: String,
    val message: String = "", val raw: String = "")
data class ImportPreview(val hash: String, val filename: String, val rows: List<ImportRow>) {
    val valid get() = rows.count { it.state == "valid" }
    val duplicate get() = rows.count { it.state == "duplicate" }
    val suspicious get() = rows.count { it.state == "suspicious" }
    val invalid get() = rows.count { it.state == "invalid" }
}
data class ImportOutcome(val added: Int, val skipped: Int, val invalid: Int)
