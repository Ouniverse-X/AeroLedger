package cn.edu.buaa.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        TransferIcon(download)
        Spacer(Modifier.width(12.dp));Text(label,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
    }
}

@Composable private fun TransferIcon(export:Boolean) {
    val color=LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val unit=size.width/24f
        val tray=Path().apply {
            moveTo(3*unit,14*unit);lineTo(3*unit,21*unit)
            lineTo(21*unit,21*unit);lineTo(21*unit,14*unit)
        }
        drawPath(tray,color,style=Stroke(2*unit,cap=StrokeCap.Round))
        drawLine(color,Offset(12*unit,3*unit),Offset(12*unit,16*unit),2*unit,StrokeCap.Round)
        val tip=if(export)3f else 16f
        val wing=if(export)8f else 11f
        drawLine(color,Offset(7*unit,wing*unit),Offset(12*unit,tip*unit),2*unit,StrokeCap.Round)
        drawLine(color,Offset(17*unit,wing*unit),Offset(12*unit,tip*unit),2*unit,StrokeCap.Round)
    }
}

@Composable private fun TransferEntry(entry:TransactionEntity) {
    Text("${entry.customCategory.ifBlank { categoryName(entry.categoryCode) }} · ${if(entry.type=="income")"收入" else "支出"} ¥${entry.amountCents.money()}",style=MaterialTheme.typography.titleMedium)
    Text(LocalDate.ofEpochDay(entry.dateEpochDay).toString(),style=MaterialTheme.typography.bodyMedium)
    if(entry.note.isNotBlank()) Text(entry.note,style=MaterialTheme.typography.bodyMedium)
}

@Composable private fun ExpandEntries(expanded:Boolean,onClick:()->Unit) {
    TextButton(onClick,Modifier.fillMaxWidth()) {
        Text(if(expanded)"收起" else "展开")
        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
    }
}

@Composable private fun ImportIssue(label:String,onDetails:()->Unit) {
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f))
            TextButton(onDetails) { Text("查看原因");Icon(Icons.Outlined.ChevronRight,null) }
        }
    }
}

@Composable fun ImportPreviewScreen(preview:ImportPreview,busy:Boolean,restored:Set<Int>,
    onInvalid:()->Unit,onSkipped:()->Unit,onConfirm:()->Unit) {
    var expanded by rememberSaveable(preview.hash) { mutableStateOf(false) }
    val pending=preview.rows.filter { it.state=="valid" || it.ordinal in restored }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text(preview.filename,style=MaterialTheme.typography.bodyMedium) }
            if(preview.invalid>0) item { ImportIssue("部分账目无法导入",onInvalid) }
            if(preview.rows.any{it.state in listOf("duplicate","suspicious") && it.ordinal !in restored})
                item { ImportIssue("部分账目已跳过",onSkipped) }
            if(pending.isEmpty()) item { Text("没有需要导入的账目") }
            items(if(expanded)pending else pending.take(3),key={it.ordinal}) { row ->
                Panel { row.entry?.let { TransferEntry(it) } }
            }
            if(pending.size>3) item { ExpandEntries(expanded){expanded=!expanded} }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("导入账目",enabled=pending.isNotEmpty(),busy=busy,onClick=onConfirm)
    }
}

@Composable fun ImportReasonsScreen(preview:ImportPreview,skipped:Boolean,restored:Set<Int>,busy:Boolean,onRestore:(Int)->Unit) {
    val rows=preview.rows.filter { if(skipped)it.state in listOf("duplicate","suspicious") else it.state=="invalid" }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(rows,key={it.ordinal}) { row -> Panel {
            if(row.entry!=null) TransferEntry(row.entry) else Text("第 ${row.ordinal} 行",style=MaterialTheme.typography.titleMedium)
            Text(when(row.state) {
                "duplicate" -> "与本机或文件中的账目重复"
                "suspicious" -> "与其他账目内容相同"
                else -> row.message
            })
            if(row.state=="suspicious") TextButton({onRestore(row.ordinal)},enabled=!busy) {
                Text(if(row.ordinal in restored)"跳过" else "仍然导入")
            }
        } }
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
    var type by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var customCategories by rememberSaveable { mutableStateOf(false) }
    var categories by rememberSaveable { mutableStateOf(listOf<String>()) }
    val from=LocalDate.parse(start);val until=LocalDate.parse(end)
    val validDates=!customDates || from<=until
    val selected=entries.filter { e -> (!customDates || e.dateEpochDay in from.toEpochDay()..until.toEpochDay()) &&
        (type.isEmpty() || e.type==type) && (!customCategories || e.categoryCode in categories) }
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
                Text("收支类型",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("" to "全部","income" to "收入","expense" to "支出").forEach { (value,label) ->
                        FilterChip(type==value,{type=value;categories=emptyList();expanded=false},
                            modifier=Modifier.testTag("export-type-$value"),label={Text(label)})
                    }
                }
            }
            Panel {
                Text("类别",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!customCategories,{customCategories=false},label={Text(when(type){"income"->"全部收入类型";"expense"->"全部支出类型";else->"全部类型"})})
                    FilterChip(customCategories,{customCategories=true},modifier=Modifier.testTag("export-custom-categories"),label={Text("自定义")})
                }
                if(customCategories) {
                    listOf("expense" to "支出","income" to "收入").filter{type.isEmpty() || it.first==type}.forEach { (group,title) ->
                        Text(title,style=MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            campusCategories.filter{it.type==group}.forEach { c ->
                                FilterChip(c.code in categories,{
                                    categories=if(c.code in categories) categories-c.code else categories+c.code
                                },label={Text(c.name)})
                            }
                        }
                    }
                }
            }
            Text("预览账目",style=MaterialTheme.typography.titleMedium)
            if(selected.isEmpty()) Text("暂无符合条件的账目")
            (if(expanded)selected else selected.take(3)).forEach { entry -> Panel { TransferEntry(entry) } }
            if(selected.size>3) ExpandEntries(expanded){expanded=!expanded}
        }
        Spacer(Modifier.height(12.dp))
        Button({onExport(selected)},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!busy && validDates && selected.isNotEmpty()) {
            TransferIcon(true);Spacer(Modifier.width(8.dp));Text("导出账目")
        }
    }
}

@Composable private fun ExportDate(label:String,value:String,onChange:(String)->Unit) {
    val context=LocalContext.current
    OutlinedButton({val date=LocalDate.parse(value);DatePickerDialog(context,{_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()},Modifier.fillMaxWidth()) {
        Text(label,Modifier.weight(1f));Text(value);Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.CalendarToday,null)
    }
}

