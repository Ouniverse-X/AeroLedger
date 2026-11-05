package cn.edu.buaa.ledger.domain

import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.data.*
import java.time.LocalDate
import java.time.YearMonth

data class LedgerData(val entries: List<TransactionEntity> = emptyList(), val budgets: List<BudgetEntity> = emptyList(),
    val plans: List<PaymentPlan> = emptyList(), val reviews: List<ReviewEntity> = emptyList(), val ready: Boolean = false,
    val filteredTotals: Totals? = null, val monthlyTotals: Totals? = null,
    val categoryTotals: List<CategoryAmount>? = null, val trendTotals: List<MonthAmount>? = null,
    val dailyTotals: List<DayAmount> = emptyList(), val recentEntries: List<TransactionEntity>? = null)

data class FilterSpec(val month: YearMonth = YearMonth.now(), val day: LocalDate? = null,
    val type: String? = null, val category: String? = null, val query: String = "") {
    fun matches(e: TransactionEntity): Boolean {
        val date = LocalDate.ofEpochDay(e.dateEpochDay)
        return (if (day == null) YearMonth.from(date) == month else date == day) &&
            (type == null || e.type == type) && (category == null || e.categoryCode == category) &&
            e.note.contains(query.trim(), ignoreCase = true)
    }
    fun select(entries: List<TransactionEntity>) = entries.filter(::matches)
    val label: String get() = day?.toString() ?: "${month.year} 年 ${month.monthValue} 月"
}

data class Totals(val expense: Long, val income: Long, val count: Int) {
    val balance get() = income - expense
}
data class CategoryAmount(val categoryCode: String, val amountCents: Long)
data class MonthAmount(val month: String, val expense: Long, val income: Long)
data class DayAmount(val dateEpochDay: Long, val expense: Long, val income: Long, val count: Int)
fun List<TransactionEntity>.totals() = Totals(filter { it.type == "expense" }.sumOf { it.amountCents },
    filter { it.type == "income" }.sumOf { it.amountCents }, size)
fun Long.money() = LedgerRules.money(this)
fun LedgerData.monthEntries(month: YearMonth) = entries.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
fun LedgerData.budget(month: YearMonth) = budgets.firstOrNull { it.month == month.toString() }?.amountCents ?: 0L
fun reviewSignature(entries: List<TransactionEntity>, budget: Long): String {
    val digest=java.security.MessageDigest.getInstance("SHA-256")
    digest.update(budget.toString().toByteArray(Charsets.UTF_8))
    entries.sortedBy { it.id }.forEach {
        digest.update(0.toByte());digest.update(it.id.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte());digest.update(it.toEntry().fingerprint().toByteArray(Charsets.UTF_8))
    }
    return "sha256:"+digest.digest().joinToString(""){"%02x".format(it)}
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
