package cn.edu.buaa.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.edu.buaa.ledger.data.TransactionEntity
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.ui.*
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DataTransferUiTests {
    @get:Rule val compose=createComposeRule()
    private fun entry(id:String,type:String="expense",category:String="food",date:LocalDate=LocalDate.now()) =
        TransactionEntity(id,type,1250,date.toEpochDay(),category,"账目-$id")

    @Test fun exportFiltersLocallyAndPassesOnlySelectedEntries() {
        val expense=entry("food")
        val income=entry("income","income","work")
        val old=entry("old",date=LocalDate.now().minusYears(1))
        var selected:List<TransactionEntity>?=null
        compose.setContent { LedgerTheme { ExportSelectionScreen(listOf(expense,income,old),false){selected=it} } }
        compose.onNodeWithText("自定义").performClick()
        compose.onNodeWithText("支出",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("餐饮",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("导出 1 笔账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense),selected) }
    }

    @Test fun importRestoresOnlyChosenSkippedEntry() {
        val rows=listOf(ImportRow(2,entry("new"),"valid"),ImportRow(3,entry("restore"),"suspicious"),
            ImportRow(4,entry("skip"),"suspicious"),ImportRow(5,entry("duplicate"),"duplicate"),
            ImportRow(6,null,"invalid","金额无效"))
        var restored:Set<Int>?=null
        compose.setContent { LedgerTheme { ImportPreviewScreen(ImportPreview("test","账目.csv",rows),false){restored=it} } }
        compose.onNodeWithText("部分账目无法导入").assertIsDisplayed()
        compose.onNodeWithText("校验通过").assertDoesNotExist()
        compose.onNodeWithText("查看跳过的账目").performScrollTo().performClick()
        compose.onAllNodesWithText("仍然导入")[0].performScrollTo().performClick()
        compose.onNodeWithText("导入账目").performClick()
        compose.runOnIdle { assertEquals(setOf(3),restored) }
    }
}
