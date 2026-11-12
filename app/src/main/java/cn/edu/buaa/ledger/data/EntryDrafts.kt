package cn.edu.buaa.ledger.data

import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.domain.CampusParser
import cn.edu.buaa.ledger.domain.EntryDraft
import java.time.LocalDate

/** One prefill boundary for manual, text, payment-plan and image entry. */
object EntryDrafts {
    fun create(
        original: TransactionEntity?, defaultDate: LocalDate, raw: String = "",
        parsed: CampusParser.Result? = null, plan: PaymentPlan? = null,
        image: EntryDraft? = null
    ): EntryDraft {
        if (original != null) return EntryDraft(
            id = original.id, amount = LedgerRules.money(original.amountCents),
            date = LocalDate.ofEpochDay(original.dateEpochDay).toString(), type = original.type,
            category = original.categoryCode, note = original.note,
            source = original.sourceType, sourceText = original.sourceText
        )
        if (image != null) return image
        return EntryDraft(
            amount = plan?.let { LedgerRules.money(it.amountCents) } ?: parsed?.amounts?.singleOrNull().orEmpty(),
            date = if (parsed != null) parsed.dates.singleOrNull().orEmpty() else defaultDate.toString(),
            type = parsed?.type ?: "expense",
            category = parsed?.category ?: if (plan != null) "bills" else "food",
            note = plan?.name ?: raw.take(200), source = if (parsed != null) "text" else "manual",
            sourceText = raw
        )
    }
}
