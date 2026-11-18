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
        compose.onNodeWithText("餐饮",substring=false).assertDoesNotExist()
        compose.onNodeWithTag("export-custom-dates").performClick()
        compose.onNodeWithTag("export-custom-categories").performScrollTo().performClick()
        compose.onNodeWithText("餐饮",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense),selected) }
        compose.onNodeWithText("兼职",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense,income),selected) }
        compose.onNodeWithText("全部类型").performScrollTo().performClick()
        compose.onNodeWithText("餐饮",substring=false).assertDoesNotExist()
        compose.onNodeWithText("导出账目").assertIsEnabled()
        compose.onNodeWithTag("export-type-income").performScrollTo().performClick()
        compose.onNodeWithText("全部收入类型").assertIsDisplayed()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(income),selected) }
        compose.onNodeWithTag("export-type-expense").performScrollTo().performClick()
        compose.onNodeWithText("全部支出类型").assertIsDisplayed()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense),selected) }
    }

    @Test fun importReasonsRestoreOnlyChosenEntry() {
        val rows=listOf(ImportRow(2,entry("new"),"valid"),ImportRow(3,entry("restore"),"suspicious"),
            ImportRow(4,entry("skip"),"suspicious"),ImportRow(5,entry("duplicate"),"duplicate"))
        var restored:Int?=null
        compose.setContent { LedgerTheme { ImportReasonsScreen(ImportPreview("test","账目.csv",rows),true,emptySet(),false){restored=it} } }
        compose.onAllNodesWithText("仍然导入")[0].performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3,restored) }
    }

    @Test fun importPreviewCollapsesEntriesAndOpensReasons() {
        val rows=(2..6).map { ImportRow(it,entry("item-$it"),"valid") }+
            ImportRow(7,null,"invalid","金额无效")+ImportRow(8,entry("duplicate"),"duplicate")
        var invalid=false;var skipped=false
        compose.setContent { LedgerTheme { ImportPreviewScreen(ImportPreview("test","账目.csv",rows),false,emptySet(),
            {invalid=true},{skipped=true},{}) } }
        compose.onAllNodesWithText("查看原因")[0].performClick()
        compose.onAllNodesWithText("查看原因")[1].performClick()
        compose.runOnIdle { assertTrue(invalid);assertTrue(skipped) }
        compose.onNodeWithText("账目-item-6").assertDoesNotExist()
        compose.onNodeWithText("展开").performScrollTo().performClick()
        compose.onNodeWithText("账目-item-6").performScrollTo().assertIsDisplayed()
    }
    @Test fun exportPreviewExpandsEntries() {
        val entries=(1..5).map { entry("item-$it") }
        compose.setContent { LedgerTheme { ExportSelectionScreen(entries,false){} } }
        compose.onNodeWithText("账目-item-5").assertDoesNotExist()
        compose.onNodeWithText("展开").performScrollTo().performClick()
        compose.onNodeWithText("账目-item-5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收起").performScrollTo().performClick()
        compose.onNodeWithText("账目-item-5").assertDoesNotExist()
    }

}
