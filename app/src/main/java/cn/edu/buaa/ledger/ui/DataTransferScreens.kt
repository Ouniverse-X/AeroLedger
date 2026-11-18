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
    Text(LocalDate.ofEpochDay(entry.dateEpochDay).toString()+if(entry.transactionTime.isBlank()) "" else " · ${entry.transactionTime}",style=MaterialTheme.typography.bodyMedium)
    if(entry.note.isNotBlank()) Text(entry.note,style=MaterialTheme.typography.bodyMedium)
}

@Composable private fun ExpandEntries(expanded:Boolean,onClick:()->Unit) {
    TextButton(onClick,Modifier.fillMaxWidth()) {
        Text(if(expanded)"收起" else "展开")
        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
    }
}

@Composable private fun ImportIssue(label:String,onDetails:()->Unit) {
    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f))
            TextButton(onDetails) { Text("查看");Icon(Icons.Outlined.ChevronRight,null) }
        }
    }
}

@Composable fun ImportFileScreen(preview:ImportPreview?,busy:Boolean,onChoose:()->Unit,
    onInvalid:()->Unit,onSkipped:()->Unit,onConfirm:()->Unit) {
    if (preview == null) {
        Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            FileCard(null,onChoose)
            Spacer(Modifier.weight(1f))
            TransferButton(false,false,{})
        }
    } else ImportPreviewScreen(preview,busy,onChoose,onInvalid,onSkipped,onConfirm)
}

@Composable private fun TransferButton(export:Boolean,enabled:Boolean,onClick:()->Unit) {
    Button(onClick,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled) {
        TransferIcon(export);Spacer(Modifier.width(8.dp));Text(if(export)"导出账目" else "导入账目")
    }
}

@Composable private fun FileCard(preview:ImportPreview?,onChoose:()->Unit) {
    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.InsertDriveFile,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(preview?.filename ?: "选择账目文件",style=MaterialTheme.typography.titleMedium)
            }
            TextButton(onChoose) { Text(if(preview==null) "选择文件" else "重新选择") }
        }
    }
}

@Composable fun ImportPreviewScreen(preview:ImportPreview,busy:Boolean,onChoose:()->Unit,
    onInvalid:()->Unit,onSkipped:()->Unit,onConfirm:()->Unit) {
    var expanded by rememberSaveable(preview.hash) { mutableStateOf(false) }
    val pending=preview.rows.filter { it.state=="valid" }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { FileCard(preview,onChoose) }
            if(preview.invalid>0) item { ImportIssue("部分账目无法导入",onInvalid) }
            if(preview.duplicate>0)
                item { ImportIssue("部分账目已存在",onSkipped) }
            if(pending.isEmpty()) item { Text("没有需要导入的账目") }
            items(if(expanded)pending else pending.take(3),key={it.ordinal}) { row ->
                Panel { row.entry?.let { TransferEntry(it) } }
            }
            if(pending.size>3) item { ExpandEntries(expanded){expanded=!expanded} }
        }
        Spacer(Modifier.height(12.dp))
        TransferButton(false,pending.isNotEmpty()&&!busy,onConfirm)
    }
}

@Composable fun ImportReasonsScreen(preview:ImportPreview,skipped:Boolean) {
    val rows=preview.rows.filter { if(skipped)it.state=="duplicate" else it.state=="invalid" }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(rows,key={it.ordinal}) { row -> Panel {
            if(row.entry!=null) TransferEntry(row.entry) else Text("第 ${row.ordinal} 条",style=MaterialTheme.typography.titleMedium)
            Text(when(row.state) {
                "duplicate" -> "这笔账目已经存在，已跳过。"
                else -> row.message
            })
        } }
    }
}

@Composable fun ImportResultScreen(outcome:ImportOutcome?,onClose:()->Unit,onView:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Panel {
            Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.CheckCircle,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
                Text(if(outcome==null)"请重新选择文件" else "导入成功",style=MaterialTheme.typography.titleLarge)
            }
        }
        if(outcome!=null) Button(onView,Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("查看最新账单") }
        OutlinedButton(onClose,Modifier.fillMaxWidth().heightIn(min=52.dp),
            border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.primary),
            colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Text("继续导入账目")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ExportSelectionScreen(entries:List<TransactionEntity>,busy:Boolean,onPreview:(List<TransactionEntity>)->Unit,onExport:(List<TransactionEntity>)->Unit) {
    var customDates by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var end by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var type by rememberSaveable { mutableStateOf("") }
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
                    listOf("" to "全部类型","income" to "收入","expense" to "支出").forEach { (value,label) ->
                        FilterChip(type==value,{type=value;categories=emptyList()},
                            modifier=Modifier.testTag("export-type-$value"),label={Text(label)})
                    }
                }
            }
            Panel {
                Text("类别",style=MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!customCategories,{customCategories=false},label={Text("全部类别")})
                    FilterChip(customCategories,{customCategories=true},modifier=Modifier.testTag("export-custom-categories"),label={Text("自定义")})
                }
                if(customCategories) {
                    listOf("expense" to "支出","income" to "收入").filter{type.isEmpty() || it.first==type}.forEach { (group,title) ->
                        if(type.isEmpty()) Text(title,style=MaterialTheme.typography.titleSmall)
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
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton({onPreview(selected)},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!busy && validDates && selected.isNotEmpty()) {
            Icon(Icons.Outlined.Visibility,null);Spacer(Modifier.width(8.dp));Text("预览账目")
        }
        Spacer(Modifier.height(8.dp))
        TransferButton(true,!busy && validDates && selected.isNotEmpty()){onExport(selected)}
    }
}

@Composable fun ExportPreviewScreen(entries:List<TransactionEntity>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(if(expanded)entries else entries.take(3),key={it.id}) { Panel { TransferEntry(it) } }
        if(entries.size>3) item { ExpandEntries(expanded){expanded=!expanded} }
    }
}

@Composable private fun ExportDate(label:String,value:String,onChange:(String)->Unit) {
    val context=LocalContext.current
    OutlinedButton({val date=LocalDate.parse(value);DatePickerDialog(context,{_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()},Modifier.fillMaxWidth()) {
        Text(label,Modifier.weight(1f));Text(value);Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.CalendarToday,null)
    }
}

