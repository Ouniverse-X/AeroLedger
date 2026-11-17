package cn.edu.buaa.ledger

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.edu.buaa.ledger.domain.FilterSpec
import cn.edu.buaa.ledger.ui.MonthPicker
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EntryFlowUiTests {
    @get:Rule val compose = createComposeRule()

    @Test fun homeMonthCannotAdvancePastTodayButCanReturnFromHistory() {
        val today = LocalDate.now()
        compose.setContent {
            var filter by remember { mutableStateOf(FilterSpec(YearMonth.from(today))) }
            LedgerTheme { MonthPicker(filter,{filter=it},latestDate=today) }
        }
        compose.onNodeWithContentDescription("下个月").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上个月").performClick()
        compose.onNodeWithContentDescription("下个月").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("下个月").assertIsNotEnabled()
    }
}
