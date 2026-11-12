package cn.edu.buaa.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerUiTests {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun recordEditBudgetAndScreens() {
      val note="界面测试午餐-"+java.util.UUID.randomUUID().toString().take(8)
      try {
        compose.waitUntil(30000) {compose.onAllNodesWithTag("primary-add",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("primary-add",useUnmergedTree=true).performClick()
        compose.onNodeWithText("金额（元）").performTextInput("0")
        compose.onNodeWithText("保存账目").performClick()
        compose.onNodeWithText("金额需大于 0，上限 999999.99 元").assertExists()
        compose.onNodeWithText("金额（元）").performTextClearance()
        compose.onNodeWithText("金额（元）").performTextInput("12.50")
        compose.onNodeWithText("备注").performScrollTo().performTextInput(note)
        compose.onNodeWithText("保存账目").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("记一笔校园日常").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("账目",useUnmergedTree=true).performClick()
        compose.onNodeWithText(note).assertExists()
        compose.onNodeWithText(note).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000){compose.onAllNodesWithText("编辑账目").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("金额（元）").performTextClearance()
        compose.onNodeWithText("金额（元）").performTextInput("8.50")
        compose.onNodeWithText("保存账目").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("编辑账目").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("统计",useUnmergedTree=true).performClick()
        compose.onNodeWithText("支出构成").assertExists()
        compose.onNodeWithText("缴费",useUnmergedTree=true).performClick()
        compose.onNodeWithText("系统通知已开启").assertExists()
        compose.onNodeWithText("账目",useUnmergedTree=true).performClick()
        compose.onNodeWithText(note).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("删除这笔账目").performScrollTo().performClick()
        compose.onNodeWithText("删除账目").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("编辑账目").fetchSemanticsNodes().isEmpty()}
        compose.onAllNodesWithText(note).assertCountEquals(0)
      } finally {
        runBlocking {
            val app=ApplicationProvider.getApplicationContext<LedgerApp>();app.ensureInitialized()
            app.repository.dao.transactions().filter{it.note==note}.forEach{app.repository.dao.deleteTransaction(it.id)}
        }
      }
    }
}
