package cn.edu.buaa.ledger.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EntryDatesTest {
    private val today = LocalDate.of(2026, 11, 17)
    @Test fun currentMonthIgnoresPreviouslySelectedDay() {
        assertEquals(today, EntryDates.defaultDate(FilterSpec(YearMonth.from(today), today.minusDays(5)), today))
    }
    @Test fun historicalBackfillKeepsExplicitDay() {
        val day = today.minusMonths(1).withDayOfMonth(3)
        assertEquals(day, EntryDates.defaultDate(FilterSpec(YearMonth.from(day),day),today))
        assertEquals(day.withDayOfMonth(1),EntryDates.defaultDate(FilterSpec(YearMonth.from(day)),today))
    }
    @Test fun homeStopsAtCurrentMonthIncludingYearBoundary() {
        assertEquals(YearMonth.from(today),EntryDates.homeMonth(YearMonth.of(2027,1),today))
        val january=LocalDate.of(2027,1,1)
        assertEquals(YearMonth.of(2026,12),EntryDates.homeMonth(YearMonth.of(2026,12),january))
        assertEquals(january,EntryDates.defaultDate(FilterSpec(YearMonth.from(january)),january))
    }
}
