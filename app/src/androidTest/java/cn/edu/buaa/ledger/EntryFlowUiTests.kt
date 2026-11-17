package cn.edu.buaa.ledger

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.edu.buaa.ledger.domain.FilterSpec
import cn.edu.buaa.ledger.ui.MonthPicker
import cn.edu.buaa.ledger.ui.EntryEditor
import cn.edu.buaa.ledger.data.TransactionEntity
import org.junit.Assert.*
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

    @Test fun otherNameAndOptionalTimeCanBeSavedAndCleared() {
        var result: TransactionEntity? = null
        compose.setContent {
            LedgerTheme {
                EntryEditor(null, LocalDate.now(), false, {entry,_->result=entry}, {}, {})
            }
        }
        compose.onNodeWithText("金额（元）").performTextInput("12.50")
        compose.onNodeWithText("其他").performScrollTo().performClick()
        compose.onNodeWithText("具体分类（可选）").performScrollTo().performTextInput("社团活动")
        compose.onNodeWithText("日期 YYYY-MM-DD").performScrollTo().assertTextContains(LocalDate.now().toString())
        compose.onNodeWithText("添加具体时间（可选）").performScrollTo().performClick()
        compose.onNodeWithText("具体时间 HH:mm").performScrollTo().performTextReplacement("09:35")
        compose.onNodeWithText("保存账目").performClick()
        compose.runOnIdle {
            assertEquals("社团活动", result!!.customCategory)
            assertEquals("09:35", result!!.transactionTime)
            assertEquals(java.time.ZoneId.systemDefault().id, result!!.transactionZone)
        }
        compose.onNodeWithText("仅记录日期").performScrollTo().performClick()
        compose.onNodeWithText("保存账目").performClick()
        compose.runOnIdle {
            assertEquals("", result!!.transactionTime)
            assertEquals("", result!!.transactionZone)
        }
    }
}
