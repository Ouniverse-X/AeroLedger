package cn.edu.buaa.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerUiTests {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun awaitNavigation() {
        compose.waitUntil(30000) {compose.onAllNodesWithTag("nav-BILLS").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(30000) {compose.onAllNodesWithText("正在加载…").fetchSemanticsNodes().isEmpty()}
    }

    @Test fun navigationKeepsLabelsAlignedAndRecordRaised() {
        awaitNavigation()
        val tabs=listOf("BILLS","STATISTICS","PLANS","SETTINGS")
        tabs.forEach { compose.onNodeWithTag("nav-$it").assertIsDisplayed() }
        val labels=listOf("账单","统计","记账","计划","设置").map { label ->
            compose.onAllNodesWithText(label,useUnmergedTree=true).fetchSemanticsNodes().last().boundsInRoot
        }
        labels.drop(1).forEach { org.junit.Assert.assertEquals(labels.first().top,it.top,1f) }
        val button=compose.onNodeWithTag("primary-add",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val nav=compose.onNodeWithTag("nav-BILLS").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("记账按钮应向上突出导航栏",button.top<nav.top)
        compose.onNodeWithTag("nav-PLANS").performClick()
        compose.onNodeWithText("添加计划").performScrollTo().performClick()
        compose.onNodeWithText("添加缴费计划").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("primary-add",useUnmergedTree=true).performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, 4f)) }
        compose.onNodeWithText("金额（元）").assertIsDisplayed()
        compose.onNodeWithText("缴费项目").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithText("外观").assertIsDisplayed().performClick()
        compose.onNodeWithText("主题色").assertIsDisplayed()
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithText("浅色").performClick()
        compose.onNodeWithText("跟随系统").performClick()
        compose.onNodeWithTag("appearance-back").performClick()
        compose.onNodeWithText("数据导入与导出").performScrollTo().performClick()
        compose.onNodeWithText("导出全部账目").assertIsDisplayed()
        compose.onNodeWithTag("nav-BILLS").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("nav-STATISTICS").performClick()
        compose.onNodeWithText("支出构成").assertExists()
        compose.onNodeWithTag("nav-BILLS").performClick()
        compose.onNodeWithTag("bills-list").performScrollToNode(hasText("账单明细"))
        compose.onNodeWithText("账单明细").assertIsDisplayed()
        compose.onNodeWithText("查看全部").assertDoesNotExist()
    }

    @Test fun settingsSeparatesPermissionsDataAndAbout() {
        awaitNavigation()
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithText("航小账 1.0").assertDoesNotExist()
        compose.onNodeWithText("载入校园示例数据").assertDoesNotExist()
        compose.onNodeWithText("数据存储").assertDoesNotExist()
        compose.onNodeWithText("示例与开源参考").assertDoesNotExist()
        compose.onNodeWithText("管理权限").performClick()
        compose.onNodeWithText("通知").assertIsDisplayed()
        compose.onNodeWithText("用于截屏结果通知和缴费到期提醒。").assertDoesNotExist()
        compose.onNodeWithText("精确提醒").assertDoesNotExist()
        compose.onNodeWithTag("nav-BILLS").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("精确提醒").assertDoesNotExist()
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithText("关于").performClick()
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        compose.onNodeWithText("数据与隐私").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("数据导入与导出").performClick()
        compose.onNodeWithText("导出全部账目").assertIsDisplayed()
        compose.onNodeWithTag("nav-BILLS").assertDoesNotExist()
        compose.onNodeWithText("外观").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("导出全部账目").assertIsDisplayed()
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
        compose.onNodeWithTag("nav-PLANS").performClick()
        compose.onNodeWithText("创建 1 分钟通知验收计划").assertDoesNotExist()
    }

    @Test fun notificationManagementOpensSystemSettingsDirectly() {
        awaitNavigation()
        androidx.test.espresso.intent.Intents.init()
        try {
            val settingsIntent=androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            androidx.test.espresso.intent.Intents.intending(settingsIntent)
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null))
            compose.onNodeWithTag("nav-SETTINGS").performClick()
            compose.onNodeWithText("管理权限").performClick()
            compose.onNode(hasText("管理通知",substring=true) or hasText("允许通知",substring=true)).performClick()
            androidx.test.espresso.intent.Intents.intended(settingsIntent)
        } finally { androidx.test.espresso.intent.Intents.release() }
    }

    @Test fun appearanceSelectionPersistsAcrossRecreation() {
        awaitNavigation()
        val app=ApplicationProvider.getApplicationContext<LedgerApp>()
        val store=cn.edu.buaa.ledger.data.AppearanceStore(app)
        val originalMode=runBlocking { store.theme.first() }
        val originalColor=runBlocking { store.themeColor.first() }
        try {
            compose.onNodeWithTag("nav-SETTINGS").performClick()
            compose.onNodeWithText("外观").performClick()
            compose.onNodeWithTag("nav-BILLS").assertDoesNotExist()
            compose.onNodeWithTag("theme-color-purple").performClick()
            compose.onNodeWithTag("theme-mode-dark").performScrollTo().performClick()
            compose.waitUntil(10000) { runBlocking { store.themeColor.first()=="purple" && store.theme.first()=="dark" } }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("theme-color-purple").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("theme-mode-dark").performScrollTo().assertIsSelected()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("nav-SETTINGS").assertIsSelected()
            compose.onNodeWithText("外观").performClick()
            compose.onNodeWithTag("theme-color-blue").performClick()
            compose.onNodeWithTag("theme-mode-light").performScrollTo().performClick()
            compose.onNodeWithTag("theme-color-blue").performScrollTo().assertIsSelected()
        } finally { runBlocking { store.setTheme(originalMode);store.setThemeColor(originalColor) } }
    }

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
        compose.waitUntil(10000){compose.onAllNodesWithText("金额（元）").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("nav-BILLS").performClick()
        compose.onNodeWithTag("bills-list").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).assertExists()
        compose.onNodeWithText(note).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000){compose.onAllNodesWithText("编辑账目").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("金额（元）").performTextClearance()
        compose.onNodeWithText("金额（元）").performTextInput("8.50")
        compose.onNodeWithText("保存账目").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("编辑账目").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("nav-STATISTICS").performClick()
        compose.onNodeWithText("支出构成").assertExists()
        compose.onNodeWithTag("nav-PLANS").performClick()
        compose.onNodeWithText("待缴计划",substring=true).assertExists()
        compose.onNodeWithTag("nav-BILLS").performClick()
        compose.onNodeWithTag("bills-list").performScrollToNode(hasText(note))
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
