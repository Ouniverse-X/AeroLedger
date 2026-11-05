package cn.edu.buaa.ledger.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
import java.time.*

/** The same components used by the app form the high-fidelity design baseline. */
private fun designData(): LedgerData {
    val m=YearMonth.of(2026,11)
    val entries=listOf(
        TransactionEntity("1","expense",1250,m.atDay(4).toEpochDay(),"food","学一食堂午餐（示例）"),
        TransactionEntity("2","expense",5990,m.atDay(4).toEpochDay(),"shopping","宿舍日用品（示例）"),
        TransactionEntity("3","expense",600,m.atDay(3).toEpochDay(),"transport","地铁通勤（示例）"),
        TransactionEntity("4","income",200000,m.atDay(1).toEpochDay(),"allowance","生活费（示例）"))
    return LedgerData(entries,listOf(BudgetEntity("2026-11",150000)),ready=true)
}
@Preview(name="总览 · 浅色",widthDp=411,heightDp=950,showBackground=true)
@Composable private fun OverviewLightPreview() {LedgerTheme("light"){Surface {Overview(designData(),FilterSpec(YearMonth.of(2026,11)),{}, {}, {}, {}, {}, {})}}}
@Preview(name="总览 · 深色",widthDp=411,heightDp=950,showBackground=true)
@Composable private fun OverviewDarkPreview() {LedgerTheme("dark"){Surface {Overview(designData(),FilterSpec(YearMonth.of(2026,11)),{}, {}, {}, {}, {}, {})}}}
@Preview(name="统计 · 浅色",widthDp=411,heightDp=1100,showBackground=true)
@Composable private fun StatsPreview() {LedgerTheme("light"){Surface {StatisticsScreen(designData(),FilterSpec(YearMonth.of(2026,11)),{}, {}, {})}}}
@Preview(name="记账 · 浅色",widthDp=411,heightDp=950,showBackground=true)
@Composable private fun EditorPreview() {LedgerTheme("light"){Surface {EntryEditor(null,LocalDate.of(2026,11,4),false,{_,_->},{},{_,_->})}}}
