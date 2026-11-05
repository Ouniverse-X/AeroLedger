package cn.edu.buaa.ledger.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.ui.components.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun PageTitle(title: String, subtitle: String) {
    if(LocalConfiguration.current.screenHeightDp<500)return
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium)
        Subtitle(subtitle)
    }
}

@Composable fun MonthPicker(filter: FilterSpec, change: (FilterSpec) -> Unit) {
    val context=LocalContext.current
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({ if(filter.month.year>1900 || filter.month.monthValue>1) change(filter.copy(month=filter.month.minusMonths(1),day=null)) }) {
                Icon(Icons.Outlined.ChevronLeft,"上个月")
            }
            TextButton({
                DatePickerDialog(context,{_,y,m,_->change(filter.copy(month=YearMonth.of(y,m+1),day=null))},filter.month.year,filter.month.monthValue-1,1)
                    .apply { setTitle("选择月份（忽略日）"); datePicker.minDate=LocalDate.of(1900,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); datePicker.maxDate=LocalDate.of(2100,12,31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()
            },modifier=Modifier.weight(1f)) {
                Icon(Icons.Outlined.CalendarMonth,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("${filter.month.year} 年 ${filter.month.monthValue} 月",style=MaterialTheme.typography.titleMedium)
                Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
            }
            IconButton({if(filter.month.year<2100 || filter.month.monthValue<12) change(filter.copy(month=filter.month.plusMonths(1),day=null))}) { Icon(Icons.Outlined.ChevronRight,"下个月") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun BudgetCard(budget: Long, expense: Long, edit: () -> Unit) {
    val remaining=budget-expense
    Panel {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AccountBalanceWallet,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
                Text("月度预算",style=MaterialTheme.typography.titleMedium)
            }
            TextButton(edit) { Text(if(budget==0L) "设置预算" else "调整") }
        }
        if(budget==0L) {
            Text("给这个月留一点余量",style=MaterialTheme.typography.titleLarge)
            Subtitle("设定月度支出目标，让日常开销更有计划。")
        } else {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Tag(when { remaining<0->"已超支";remaining==0L->"预算已用完";else->"剩余额度" },if(remaining<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Money(kotlin.math.abs(remaining),Modifier.weight(1f),size=26,color=if(remaining<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            LinearProgressIndicator(progress={(expense.toFloat()/budget).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(8.dp),
                color=if(remaining<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surfaceVariant)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Subtitle("预算 ¥${budget.money()}")
                Subtitle("已用 %.1f%%".format(java.util.Locale.CHINA,expense*100.0/budget))
            }
        }
    }
}

@Composable fun Overview(data: LedgerData, filter: FilterSpec, change: (FilterSpec)->Unit, onBudget:()->Unit,
    onEntry:(TransactionEntity)->Unit, onAll:()->Unit, onPayments:()->Unit, onDemo:()->Unit) {
    val monthEntries=remember(data.entries,data.recentEntries,filter.month) { data.recentEntries ?: data.monthEntries(filter.month) }
    val total=data.monthlyTotals ?: remember(monthEntries) { monthEntries.totals() }
    val compact=LocalConfiguration.current.screenHeightDp<500
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(!compact)item { PageTitle("校园日常，心中有数。","记下每一笔，留点余量给喜欢的生活。") }
        item { MonthPicker(filter,change) }
        item {
          Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(modifier=if(compact)Modifier.weight(1f) else Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Color.Transparent) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF2257B8),Color(0xFF153F86)))).padding(if(compact)16.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 14.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("本月支出",color=Color(0xFFD6E4FF),style=MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Outlined.FlightTakeoff,null,tint=Color(0xFFBCD3FF),modifier=Modifier.size(25.dp))
                    }
                    Money(total.expense,Modifier.fillMaxWidth(),size=if(compact)30 else 38,color=Color.White)
                    if(!compact)Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color=Color.White.copy(alpha=.18f))
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text("本月收入",color=Color(0xFFD6E4FF),style=MaterialTheme.typography.bodyMedium)
                            Money(total.income,Modifier.fillMaxWidth(),size=22,color=Color.White)
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text("收支结余",color=Color(0xFFD6E4FF),style=MaterialTheme.typography.bodyMedium)
                            Money(total.balance,Modifier.fillMaxWidth(),size=22,color=Color.White)
                        }
                    }
                }
            }
            if(compact)Box(Modifier.weight(1f)){BudgetCard(data.budget(filter.month),total.expense,onBudget)}
          }
        }
        if(!compact)item { BudgetCard(data.budget(filter.month),total.expense,onBudget) }
        val pending=data.plans.filter { it.status=="pending" }.sortedBy { it.dueAt }
        if(pending.isNotEmpty()) item {
            val p=pending.first()
            Panel(Modifier.clickable(onClick=onPayments)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.NotificationsNone,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) { Text("${pending.size} 项待缴",style=MaterialTheme.typography.titleMedium); Subtitle("${p.name} · ${formatDue(p.dueAt)}") }
                    Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader("最近账目","查看全部",onAll) }
        if(monthEntries.isEmpty()) item { EmptyState("这个月，从第一笔开始","一顿食堂午餐、一笔生活费，都值得记下。","载入校园示例",onDemo) }
        else item { Panel {
            monthEntries.take(5).forEachIndexed { i,e -> if(i>0) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f)); EntryRow(e) {onEntry(e)} }
        } }
        item { Subtitle("数据保存在本机 · 示例账目均已单独标注") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun Filters(filter: FilterSpec, change: (FilterSpec)->Unit) {
    val context=LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    MonthPicker(filter,change)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        LedgerChip(filter.day==null,{change(filter.copy(day=null))},label={Text("整月")},leadingIcon={Icon(Icons.Outlined.DateRange,null,Modifier.size(16.dp))})
        LedgerChip(filter.day!=null,{
            val day=filter.day ?: filter.month.atDay(1)
            DatePickerDialog(context,{_,y,m,d->val date=LocalDate.of(y,m+1,d);change(filter.copy(month=YearMonth.from(date),day=date))},day.year,day.monthValue-1,day.dayOfMonth).show()
        },label={Text(filter.day?.let{"${it.monthValue}月${it.dayOfMonth}日"}?:"按日")})
        Box {
            LedgerChip(filter.category!=null,{expanded=true},label={Text(filter.category?.let(::categoryName)?:"全部类别")},trailingIcon={Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp))})
            DropdownMenu(expanded,{expanded=false}) {
                DropdownMenuItem({Text("全部类别")},{change(filter.copy(category=null));expanded=false})
                campusCategories.filter { filter.type==null||filter.type==it.type }.forEach { c ->
                    DropdownMenuItem({Text("${if(c.type=="income")"收入" else "支出"} · ${c.name}")},{change(filter.copy(category=c.code));expanded=false},leadingIcon={Icon(categoryIcon(c.code),null)})
                }
            }
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(null to "全部收支","expense" to "支出","income" to "收入").forEach { (type,name) ->
            LedgerChip(filter.type==type,{change(filter.copy(type=type,category=if(type!=null && campusCategories.firstOrNull{it.code==filter.category}?.type!=type) null else filter.category))},label={Text(name)})
        }
    }
    OutlinedTextField(filter.query,{change(filter.copy(query=it.take(200)))},Modifier.fillMaxWidth(),
        label={Text("搜索备注")},placeholder={Text("例如：食堂、打印、地铁")},singleLine=true,
        leadingIcon={Icon(Icons.Outlined.Search,null)},shape=RoundedCornerShape(14.dp),
        trailingIcon={if(filter.query.isNotEmpty())IconButton({change(filter.copy(query=""))}){Icon(Icons.Outlined.Close,"清除搜索")}})
}

@Composable fun EntriesScreen(data: LedgerData,filter: FilterSpec,change:(FilterSpec)->Unit,onEntry:(TransactionEntity)->Unit,onCsv:()->Unit,onText:()->Unit,onMore:()->Unit={}) {
    val entries=remember(data.entries,filter) { filter.select(data.entries) }
    val total=data.filteredTotals ?: remember(entries) { entries.totals() }
    val days=remember(entries) { entries.groupBy { it.dateEpochDay } }
    val daily=remember(data.dailyTotals) { data.dailyTotals.associateBy { it.dateEpochDay } }
    LazyColumn(Modifier.testTag("entries-list"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("每一笔，都有迹可循。","按日期与类别，找到想核对的日常。") }
        item { Column { Filters(filter,change) } }
        item { Panel {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { Subtitle("筛选支出");Money(total.expense,Modifier.fillMaxWidth(),size=23) }
                Column(Modifier.weight(1f)) { Subtitle("筛选收入");Money(total.income,Modifier.fillMaxWidth(),size=23,color=MaterialTheme.colorScheme.secondary) }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Subtitle("${filter.label} · ${total.count} 笔 · 结余 ¥${total.balance.money()}")
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onText,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("文本记账") }
            OutlinedButton(onCsv,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.ImportExport,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("导入导出") }
        } }
        if(entries.isEmpty()) item { EmptyState("没有符合条件的账目","试试调整日期或类别，也可以新记一笔。") }
        days.forEach { (date,rows) ->
            item(key="date-$date") {
                val t=daily[date]?.let { Totals(it.expense,it.income,it.count) } ?: remember(rows) { rows.totals() }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(LocalDate.ofEpochDay(date).format(DateTimeFormatter.ofPattern("M 月 d 日")),style=MaterialTheme.typography.titleMedium)
                    Subtitle("支 ¥${t.expense.money()} · 收 ¥${t.income.money()}")
                }
            }
            items(rows,key={it.id},contentType={"entry"}) { e ->
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))) {
                    Box(Modifier.padding(horizontal=16.dp)) { EntryRow(e) {onEntry(e)} }
                }
            }
        }
        if(total.count>entries.size) item { OutlinedButton(onMore,Modifier.fillMaxWidth()) { Text("继续显示 · 已加载 ${entries.size} / ${total.count} 笔") } }
    }
}

@Composable fun StatisticsScreen(data: LedgerData,filter: FilterSpec,change:(FilterSpec)->Unit,onDetails:(FilterSpec)->Unit,onReview:()->Unit) {
    val entries=remember(data.entries,filter) { filter.select(data.entries) }
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle("花在哪里，一目了然。","把数字看清楚，把生活安排好。") }
        item { Column { Filters(filter,change) } }
        item { Subtitle("分类图范围：${filter.label} · ${filter.category?.let(::categoryName)?:"全部类别"}") }
        item { CategoryDonut(entries,data.categoryTotals) {code->onDetails(filter.copy(category=code,type="expense"))} }
        item { MonthlyTrend(data,filter) {m->onDetails(filter.copy(month=m,day=null))} }
        item { Panel {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.AutoStories,null,Modifier.padding(12.dp),tint=MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) { Text("月度消费回顾",style=MaterialTheme.typography.titleLarge);Subtitle("${filter.month} · 全月全部类别") }
            }
            Subtitle("从真实账目出发，看看这个月的支出与预算。可以核对依据，也可以写下自己的想法。")
            PrimaryButton(if(data.reviews.any{it.month==filter.month.toString()}) "查看已保存回顾" else "生成本月回顾",onClick=onReview)
        } }
    }
}

fun formatDue(at: Long) = DateTimeFormatter.ofPattern("MM-dd HH:mm").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

@Composable fun PaymentsScreen(data: LedgerData,notifications: Boolean,exact: Boolean,onPermission:()->Unit,onExact:()->Unit,
    onEdit:(PaymentPlan)->Unit,onChange:(PaymentPlan)->Unit,onDemo:()->Unit,onRecord:(PaymentPlan)->Unit) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PaymentPlan?>(null) }
    val pending=data.plans.filter {it.status=="pending"}.sortedBy{it.dueAt}
    val history=data.plans.filter {it.status!="pending"}.sortedByDescending {it.dueAt}
    confirm?.let { plan -> AlertDialog(onDismissRequest={confirm=null},icon={Icon(Icons.Outlined.TaskAlt,null)},title={Text("确认已经缴费？")},
        text={Text("${plan.name} · ¥${plan.amountCents.money()}\n将撤销提醒，不自动生成账目。如需记账，可点击“另记一笔”。")},
        confirmButton={TextButton({onChange(plan.copy(status="paid"));confirm=null}){Text("标记已缴")}},dismissButton={TextButton({confirm=null}){Text("返回")}}) }
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle("到期之前，心里有底。","电费、网费与生活计划，都妥善安排。") }
        item { Panel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(if(notifications)Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,null,tint=MaterialTheme.colorScheme.primary)
                Text(if(notifications)"系统通知已开启" else "开启通知，及时提醒",style=MaterialTheme.typography.titleMedium)
            }
            Subtitle(if(!notifications)"计划会保留在本机。允许通知后，未提醒的到期项目会补发。" else if(exact)"到期发送系统通知；已缴、取消或关闭后撤销。" else "当前使用弹性提醒，系统可能延迟。允许精确提醒后可准时验收。")
            if(!notifications) PrimaryButton("开启系统通知",onClick=onPermission)
            if(!exact) TextButton(onExact) { Text("允许精确提醒");Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp)) }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("待缴计划",style=MaterialTheme.typography.titleLarge);Tag("${pending.size} 项") } }
        if(pending.isEmpty()) item { EmptyState("暂时没有待缴项目","用右下角的加号，安排下一笔生活缴费。",icon=Icons.Outlined.EventAvailable) }
        items(pending,key={it.id}) { plan -> PaymentCard(plan,onEdit,onChange,{confirm=it},onRecord,notifications) }
        if(history.isNotEmpty()) {
            item { SectionHeader("历史计划 · ${history.size} 项",if(showHistory)"收起" else "展开") {showHistory=!showHistory} }
            if(showHistory) items(history,key={it.id}) { plan -> PaymentCard(plan,onEdit,onChange,{confirm=it},onRecord,notifications) }
        }
        item { OutlinedButton(onDemo,Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Timer,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text("创建 1 分钟通知验收计划") } }
        item { Subtitle("验收项目金额 ¥0.01，为虚构示例，不产生支付或账目。") }
    }
}

@Composable private fun PaymentCard(plan: PaymentPlan,onEdit:(PaymentPlan)->Unit,onChange:(PaymentPlan)->Unit,onPaid:(PaymentPlan)->Unit,
    onRecord:(PaymentPlan)->Unit,notifications: Boolean) {
    val pending=plan.status=="pending";val overdue=pending&&plan.dueAt<System.currentTimeMillis()
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(plan.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Tag(when {plan.status=="paid"->"已缴";plan.status=="cancelled"->"已取消";overdue->"已到期待缴";else->"待缴"},if(overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Money(plan.amountCents,Modifier.fillMaxWidth(),size=28)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Schedule,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Subtitle("${formatDue(plan.dueAt)} 到期 · ${ZoneId.systemDefault().id}")
        }
        if(pending) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("到期提醒",style=MaterialTheme.typography.bodyMedium);Subtitle(when { !plan.reminderEnabled->"此项目提醒已关闭";!notifications->"等待开启系统通知";plan.notifiedRevision==plan.revision->"已发送系统通知";else->"提醒已安排" }) }
                Switch(plan.reminderEnabled,{onChange(plan.copy(reminderEnabled=it))})
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button({onPaid(plan)},Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)) {Text("标记已缴")}
                OutlinedButton({onEdit(plan)},Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)) {Text("管理计划")}
            }
        } else {
            Subtitle("此项目提醒已撤销")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton({onEdit(plan)},Modifier.weight(1f)) {Text("查看计划")}
                if(plan.status=="paid") TextButton({onRecord(plan)},Modifier.weight(1f)) {Text("另记一笔")}
            }
        }
    }
}
