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
        compose.setContent { LedgerTheme { ExportSelectionScreen(listOf(expense,income,old),false,{}){selected=it} } }
        compose.onNodeWithText("餐饮",substring=false).assertDoesNotExist()
        compose.onNodeWithTag("export-custom-dates").performClick()
        compose.onNodeWithTag("export-custom-categories").performScrollTo().performClick()
        compose.onNodeWithText("餐饮",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense),selected) }
        compose.onNodeWithText("兼职",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense,income),selected) }
        compose.onNodeWithText("全部类别").performScrollTo().performClick()
        compose.onNodeWithText("餐饮",substring=false).assertDoesNotExist()
        compose.onNodeWithText("导出账目").assertIsEnabled()
        compose.onNodeWithTag("export-type-income").performScrollTo().performClick()
        compose.onNodeWithText("全部类别").assertIsDisplayed()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(income),selected) }
        compose.onNodeWithTag("export-type-expense").performScrollTo().performClick()
        compose.onNodeWithText("全部类别").assertIsDisplayed()
        compose.onNodeWithText("导出账目").performClick()
        compose.runOnIdle { assertEquals(listOf(expense),selected) }
    }

    @Test fun importReasonsHaveNoRestoreAction() {
        val rows=listOf(ImportRow(2,entry("duplicate"),"duplicate"))
        compose.setContent { LedgerTheme { ImportReasonsScreen(ImportPreview("test","账目.csv",rows),true) } }
        compose.onNodeWithText("这笔账目已经存在，已跳过。").assertIsDisplayed()
        compose.onNodeWithText("仍然导入").assertDoesNotExist()
    }

    @Test fun importStartsWithFileSelectionAndDisabledAction() {
        var choose=false
        compose.setContent { LedgerTheme { ImportFileScreen(null,false,{choose=true},{},{},{}) } }
        compose.onNodeWithText("导入账目").assertIsNotEnabled()
        compose.onNodeWithText("选择文件").performClick()
        compose.runOnIdle { assertTrue(choose) }
    }

    @Test fun importPreviewCollapsesEntriesAndOpensReasons() {
        val rows=(2..6).map { ImportRow(it,entry("item-$it"),"valid") }+
            ImportRow(7,null,"invalid","金额无效")+ImportRow(8,entry("duplicate"),"duplicate")
        var invalid=false;var skipped=false
        compose.setContent { LedgerTheme { ImportPreviewScreen(ImportPreview("test","账目.csv",rows),false,{},
            {invalid=true},{skipped=true},{}) } }
        compose.onAllNodesWithText("查看")[0].performClick()
        compose.onAllNodesWithText("查看")[1].performClick()
        compose.runOnIdle { assertTrue(invalid);assertTrue(skipped) }
        compose.onNodeWithText("账目-item-6").assertDoesNotExist()
        compose.onNodeWithText("展开").performScrollTo().performClick()
        compose.onNodeWithText("账目-item-6").performScrollTo().assertIsDisplayed()
    }
    @Test fun exportPreviewExpandsEntries() {
        val entries=(1..5).map { entry("item-$it") }
        compose.setContent { LedgerTheme { ExportPreviewScreen(entries) } }
        compose.onNodeWithText("账目-item-5").assertDoesNotExist()
        compose.onNodeWithText("展开").performScrollTo().performClick()
        compose.onNodeWithText("账目-item-5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收起").performScrollTo().performClick()
        compose.onNodeWithText("账目-item-5").assertDoesNotExist()
    }

    @Test fun importWithOnlyExistingEntriesStaysDisabled() {
        val preview=ImportPreview("existing","账目.csv",listOf(ImportRow(2,entry("old"),"duplicate")))
        compose.setContent { LedgerTheme { ImportFileScreen(preview,false,{},{},{},{}) } }
        compose.onNodeWithText("导入账目").assertIsNotEnabled()
        compose.onNodeWithText("查看").assertIsDisplayed()
    }

    @Test fun importSuccessOffersBothDestinationsWithoutExtraNotice() {
        var view=false;var close=false
        compose.setContent { LedgerTheme { ImportResultScreen(ImportOutcome(1,1,1),{close=true},{view=true}) } }
        compose.onNodeWithText("导入成功").assertIsDisplayed()
        compose.onNodeWithText("部分账目未能导入").assertDoesNotExist()
        compose.onNodeWithText("查看账单").performClick()
        compose.onNodeWithText("返回导入导出").performClick()
        compose.runOnIdle { assertTrue(view);assertTrue(close) }
    }

}
