package cn.edu.buaa.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.ui.components.*
import java.time.LocalDate

@Composable fun DataTransferScreen(busy:Boolean,onImport:()->Unit,onExport:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Panel { TransferAction("导入账目",false,!busy,onImport) }
        Panel { TransferAction("导出账目",true,!busy,onExport) }
    }
}

@Composable private fun TransferAction(label:String,download:Boolean,enabled:Boolean,onClick:()->Unit) {
    TextButton(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled) {
        Icon(if(download) Icons.Outlined.Outbox else Icons.Outlined.MoveToInbox,null)
        Spacer(Modifier.width(12.dp));Text(label,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
    }
}

@Composable private fun ImportEntry(row:ImportRow) {
    row.entry?.let { entry ->
        Text("${entry.customCategory.ifBlank { categoryName(entry.categoryCode) }} · ${if(entry.type=="income")"收入" else "支出"} ¥${entry.amountCents.money()}",style=MaterialTheme.typography.titleMedium)
        Text(LocalDate.ofEpochDay(entry.dateEpochDay).toString(),style=MaterialTheme.typography.bodyMedium)
        if(entry.note.isNotBlank()) Text(entry.note,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun ImportPreviewScreen(preview:ImportPreview,busy:Boolean,onConfirm:(Set<Int>)->Unit) {
    var restored by rememberSaveable(preview.hash) { mutableStateOf(listOf<Int>()) }
    var showSkipped by rememberSaveable(preview.hash) { mutableStateOf(false) }
    var showInvalid by rememberSaveable(preview.hash) { mutableStateOf(false) }
    val pending=preview.rows.filter { it.state=="valid" || it.ordinal in restored }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text(preview.filename,style=MaterialTheme.typography.bodyMedium) }
            if(preview.invalid>0) item { Panel {
                Text("部分账目无法导入")
                TextButton({showInvalid=!showInvalid}) { Text(if(showInvalid)"收起" else "查看原因") }
            } }
            if(showInvalid) items(preview.rows.filter{it.state=="invalid"},key={"invalid-${it.ordinal}"}) { row -> Panel {
                Text("第 ${row.ordinal} 行",style=MaterialTheme.typography.titleMedium)
                Text(row.message)
            } }
            if(preview.duplicate+preview.suspicious>0) item {
                TextButton({showSkipped=!showSkipped}) { Text(if(showSkipped)"收起跳过的账目" else "查看跳过的账目") }
            }
            if(showSkipped) items(preview.rows.filter{it.state in listOf("duplicate","suspicious") && it.ordinal !in restored},key={"skipped-${it.ordinal}"}) { row -> Panel {
                ImportEntry(row)
                Text(if(row.state=="duplicate")"重复账目" else "与其他账目内容相同",style=MaterialTheme.typography.bodySmall)
                if(row.state=="suspicious") TextButton({restored=restored+row.ordinal}) {Text("仍然导入")}
            } }
            if(pending.isEmpty()) item { Text("没有需要导入的账目") }
            items(pending,key={"pending-${it.ordinal}"}) { row -> Panel {
                ImportEntry(row)
                if(row.ordinal in restored) TextButton({restored=restored-row.ordinal}) {Text("跳过")}
            } }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("导入账目",enabled=pending.isNotEmpty(),busy=busy,onClick={onConfirm(restored.toSet())})
    }
}

@Composable fun ImportResultScreen(outcome:ImportOutcome?,onClose:()->Unit,onView:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(if(outcome==null)"请重新选择文件" else if(outcome.added>0)"账目已导入" else "没有新增账目",style=MaterialTheme.typography.titleMedium)
        if(outcome!=null && outcome.invalid>0) Text("部分账目未能导入")
        if(outcome!=null && outcome.added>0) PrimaryButton("查看账单",onClick=onView)
        TextButton(onClose) {Text("完成")}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ExportSelectionScreen(entries:List<TransactionEntity>,busy:Boolean,onExport:(List<TransactionEntity>)->Unit) {
    var customDates by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var customCategories by rememberSaveable { mutableStateOf(false) }
    var categories by rememberSaveable { mutableStateOf(listOf<String>()) }
    val from=LocalDate.parse(start);val until=LocalDate.parse(end)
    val validDates=!customDates || from<=until
    val selected=entries.filter { e -> (!customDates || e.dateEpochDay in from.toEpochDay()..until.toEpochDay()) &&
        ( !customCategories || e.categoryCode in categories) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Panel {
                Text("日期范围",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!customDates,{customDates=false},label={Text("全部日期")})
                    FilterChip(customDates,{customDates=true},modifier=Modifier.testTag("export-custom-dates"),label={Text("自定义")})
                }
                if(customDates) {
                    ExportDate("开始日期",start){start=it}
                    ExportDate("结束日期",end){end=it}
                    if(!validDates) Text("结束日期不能早于开始日期",color=MaterialTheme.colorScheme.error)
                }
            }
            Panel {
                Text("类别",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!customCategories,{customCategories=false},label={Text("全部类型")})
                    FilterChip(customCategories,{customCategories=true},modifier=Modifier.testTag("export-custom-categories"),label={Text("自定义")})
                }
                if(customCategories) {
                    listOf("expense" to "支出","income" to "收入").forEach { (type,title) ->
                        Text(title,style=MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            campusCategories.filter{it.type==type}.forEach { c ->
                                FilterChip(c.code in categories,{
                                    categories=if(c.code in categories) categories-c.code else categories+c.code
                                },label={Text(c.name)})
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button({onExport(selected)},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!busy && validDates && selected.isNotEmpty()) {
            Icon(Icons.Outlined.Outbox,null);Spacer(Modifier.width(8.dp));Text("导出 ${selected.size} 笔账目")
        }
    }
}

@Composable private fun ExportDate(label:String,value:String,onChange:(String)->Unit) {
    val context=LocalContext.current
    OutlinedButton({val date=LocalDate.parse(value);DatePickerDialog(context,{_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()},Modifier.fillMaxWidth()) {
        Text(label,Modifier.weight(1f));Text(value);Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.CalendarToday,null)
    }
}

