package cn.edu.buaa.ledger

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.buaa.ledger.domain.CategoryAmount
import cn.edu.buaa.ledger.ui.components.CategoryDonut
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartUiTests {
    @get:Rule val compose=createComposeRule()
    @Test fun selectedCategoryRemovedByFilterDoesNotCrash() {
        val values=mutableStateOf(listOf(CategoryAmount("food",100),CategoryAmount("shopping",100)))
        compose.setContent{LedgerTheme("light"){CategoryDonut(emptyList(),values.value){}}}
        compose.onNodeWithText("购物").performClick()
        compose.onAllNodesWithText("50.0%").onFirst().assertExists()
        compose.runOnUiThread{values.value=listOf(CategoryAmount("transport",1))}
        compose.onNodeWithText("分类支出合计").assertExists()
        compose.onNodeWithText("交通").assertExists()
        compose.runOnUiThread{values.value=emptyList()}
        compose.onNodeWithText("当前范围暂无支出，收入不会计入支出占比。").assertExists()
        compose.runOnUiThread{values.value=listOf(CategoryAmount("food",99_999_999),CategoryAmount("other",1))}
        compose.onNodeWithText("其他支出").performClick()
        compose.onAllNodesWithText("<0.1%").onFirst().assertExists()
        compose.onNodeWithText("查看其他支出明细").assertExists()
    }
}
