package cn.edu.buaa.ledger.domain

import java.time.LocalDate
import java.time.YearMonth

object EntryDates {
    fun defaultDate(filter: FilterSpec, today: LocalDate): LocalDate =
        if (filter.month == YearMonth.from(today)) today else filter.day ?: filter.month.atDay(1)

    fun homeMonth(month: YearMonth, today: LocalDate): YearMonth = minOf(month, YearMonth.from(today))
}
