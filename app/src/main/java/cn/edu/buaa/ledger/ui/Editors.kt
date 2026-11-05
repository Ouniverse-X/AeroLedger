package cn.edu.buaa.ledger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import cn.edu.buaa.ledger.LedgerRules
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.ui.components.*
import java.time.*
import java.util.UUID

@Composable fun EditorFrame(title: String,subtitle: String,busy: Boolean,action: String,onAction:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding().padding(horizontal=20.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(5.dp));Subtitle(subtitle);Spacer(Modifier.height(18.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
        Spacer(Modifier.height(12.dp));PrimaryButton(action,busy=busy,onClick=onAction);Spacer(Modifier.height(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EntryEditor(original:TransactionEntity?,defaultDate:LocalDate,busy:Boolean,onSave:(TransactionEntity,()->Unit)->Unit,
    onClose:()->Unit,onDelete:(String,()->Unit)->Unit,raw:String="",parsed:CampusParser.Result?=null,prefill:PaymentPlan?=null,
    onDirty:(Boolean)->Unit={}) {
    val draftKey="entry:"+(original?.id ?: prefill?.let{"plan-${it.id}"} ?: if(raw.isNotEmpty())"text-${raw.hashCode()}" else "new")
    val initial=remember { mapOf("amount" to (original?.amountCents?.money()?:prefill?.amountCents?.money()?:parsed?.amounts?.singleOrNull()?:""),
        "date" to (original?.let{LocalDate.ofEpochDay(it.dateEpochDay).toString()} ?: if(parsed!=null) parsed.dates.singleOrNull()?:"" else defaultDate.toString()),
        "type" to (original?.type?:parsed?.type?:"expense"),"category" to (original?.categoryCode?:parsed?.category?:if(prefill!=null)"bills" else "food"),
        "note" to (original?.note?:prefill?.name?:raw.take(200))) }
    val draft=rememberFormDraft(draftKey)
    var amount by rememberSaveable { mutableStateOf(draft["amount"]?:initial.getValue("amount")) }
    var date by rememberSaveable { mutableStateOf(draft["date"]?:initial.getValue("date")) }
    var type by rememberSaveable { mutableStateOf(draft["type"]?:initial.getValue("type")) }
    var category by rememberSaveable { mutableStateOf(draft["category"]?:initial.getValue("category")) }
    var note by rememberSaveable { mutableStateOf(draft["note"]?:initial.getValue("note")) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var dateError by rememberSaveable { mutableStateOf<String?>(null) }
    var keepAdding by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val context=LocalContext.current
    TrackFormDraft(draftKey,mapOf("amount" to amount,"date" to date,"type" to type,"category" to category,"note" to note),initial,onDirty)
    if(deleteConfirm && original!=null) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("删除这笔账目？")},text={Text("删除后预算、汇总与图表会重新计算，可以在下方提示中撤销。")},
        confirmButton={TextButton({onDelete(original.id){DraftStore(context).clear(draftKey);onDirty(false);deleteConfirm=false;onClose()}}){Text("删除账目",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({deleteConfirm=false}){Text("保留")}})
    EditorFrame(if(original!=null)"编辑账目" else if(parsed!=null)"核对后，记下这一笔" else "记一笔校园日常",
        if(parsed!=null)"从原文中提取的内容，需要你确认后才保存。" else "金额精确到分，日常开销清清楚楚。",busy,
        if(keepAdding&&original==null)"保存并继续" else "保存账目",onAction={
            val cents=try {LedgerRules.parseMoney(amount)} catch(e:IllegalArgumentException){amountError=e.message;return@EditorFrame}
            val day=try {LocalDate.parse(date).also {require(it.year in 1900..2100)}} catch(e:Exception){dateError="请选择有效日期，范围为 1900～2100 年";return@EditorFrame}
            val entity=TransactionEntity(original?.id?:UUID.randomUUID().toString(),type,cents,day.toEpochDay(),category,note,
                original?.sourceType?:if(parsed!=null)"text" else "manual",original?.sourceText?:raw,
                createdAt=original?.createdAt?:System.currentTimeMillis())
            onSave(entity) { DraftStore(context).clear(draftKey);onDirty(false);if(keepAdding&&original==null){amount="";note="";amountError=null} else onClose() }
        }) {
        if(draft.isNotEmpty())Subtitle("已恢复上次未保存的草稿，确认保存后才会入账。")
        if(original==null && parsed==null) {
            Subtitle("常用模板 · 保留当前金额与日期")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(Triple("食堂用餐","food","食堂用餐"),Triple("通勤出行","transport","通勤出行"),
                    Triple("资料打印","study","课程资料打印"),Triple("宿舍缴费","bills","宿舍缴费")).forEach{(label,code,text)->
                    SuggestionChip({type="expense";category=code;note=text},label={Text(label)},icon={Icon(categoryIcon(code),null,Modifier.size(16.dp))})
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("expense" to "支出","income" to "收入").forEach { (value,label) ->
                LedgerChip(type==value,{type=value;category=campusCategories.first{it.type==value}.code},label={Text(label)},
                    leadingIcon={Icon(if(value=="expense")Icons.Outlined.NorthEast else Icons.Outlined.SouthWest,null,Modifier.size(17.dp))})
            }
        }
        if(raw.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) { Subtitle("录入原文");Text(raw,style=MaterialTheme.typography.bodyMedium);if(parsed!=null)Text(parsed.reason,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium) }
        }
        if(parsed!=null && parsed.amounts.size>1) {
            Subtitle("检测到多个金额，请选出本次交易金额")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { parsed.amounts.forEach { candidate -> LedgerChip(amount==candidate,{amount=candidate;amountError=null},label={Text("¥$candidate")}) } }
        }
        OutlinedTextField(amount,{amount=it.take(12);amountError=null},Modifier.fillMaxWidth(),label={Text("金额（元）")},
            prefix={Text("¥ ")},textStyle=MaterialTheme.typography.headlineMedium,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
            singleLine=true,isError=amountError!=null,supportingText={Text(amountError?:"0.01～999999.99 元，最多两位小数")},shape=RoundedCornerShape(14.dp))
        Text("选择类别",style=MaterialTheme.typography.titleMedium)
        val categories=campusCategories.filter{it.type==type}
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            categories.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Surface(Modifier.weight(1f).clickable {category=c.code},shape=RoundedCornerShape(14.dp),
                        color=if(category==c.code)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border=if(category==c.code) BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            CategoryAvatar(c.code,selected=category==c.code)
                            Text(c.name,style=MaterialTheme.typography.bodyMedium,fontWeight=if(category==c.code)FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                repeat(3-row.size){Spacer(Modifier.weight(1f))}
            } }
        }
        OutlinedTextField(date,{date=it.take(10);dateError=null},Modifier.fillMaxWidth(),label={Text("日期 YYYY-MM-DD")},singleLine=true,
            placeholder={Text(if(parsed!=null)"原文日期不明确，请选择" else "选择日期")},isError=dateError!=null,
            supportingText={if(dateError!=null)Text(dateError!!)},shape=RoundedCornerShape(14.dp),trailingIcon={IconButton({
                val current=runCatching{LocalDate.parse(date)}.getOrDefault(defaultDate)
                DatePickerDialog(context,{_,y,m,d->date=LocalDate.of(y,m+1,d).toString();dateError=null},current.year,current.monthValue-1,current.dayOfMonth).show()
            }){Icon(Icons.Outlined.CalendarToday,"选择日期")}})
        if(parsed!=null && parsed.dates.size>1) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { parsed.dates.forEach { candidate -> LedgerChip(date==candidate,{date=candidate;dateError=null},label={Text(candidate)}) } }
        OutlinedTextField(note,{note=it.take(200)},Modifier.fillMaxWidth(),label={Text("备注")},placeholder={Text("例如：学一食堂午餐")},minLines=2,
            supportingText={Text("${note.length}/200")},shape=RoundedCornerShape(14.dp))
        if(original?.sourceText?.isNotBlank()==true && raw.isBlank()) Panel { Subtitle("录入来源 · ${original.sourceType}");Text(original.sourceText,style=MaterialTheme.typography.bodyMedium) }
        if(original==null) Row(Modifier.fillMaxWidth().clickable {keepAdding=!keepAdding},verticalAlignment=Alignment.CenterVertically) { Checkbox(keepAdding,{keepAdding=it});Text("保存后继续记下一笔",style=MaterialTheme.typography.bodyMedium) }
        else TextButton({deleteConfirm=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.error);Spacer(Modifier.width(8.dp));Text("删除这笔账目",color=MaterialTheme.colorScheme.error)}
    }
}

@Composable fun BudgetEditor(month:YearMonth,current:Long,busy:Boolean,onSave:(Long,()->Unit)->Unit,onDirty:(Boolean)->Unit={}) {
    val key="budget:$month";val draft=rememberFormDraft(key);val context=LocalContext.current
    val original=remember {if(current==0L)"" else current.money()}
    var value by rememberSaveable {mutableStateOf(draft["value"]?:original)}
    TrackFormDraft(key,mapOf("value" to value),mapOf("value" to original),onDirty)
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    EditorFrame("安排这个月的预算","${month.year} 年 ${month.monthValue} 月 · 总支出预算",busy,"保存预算",{
        try{onSave(LedgerRules.parseMoney(value)){DraftStore(context).clear(key);onDirty(false)}}catch(e:IllegalArgumentException){error=e.message}
    }) {
        OutlinedTextField(value,{value=it.take(12);error=null},Modifier.fillMaxWidth(),label={Text("预算（元）")},prefix={Text("¥ ")},
            singleLine=true,textStyle=MaterialTheme.typography.headlineMedium,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
            shape=RoundedCornerShape(14.dp),isError=error!=null,supportingText={Text(error?:"0.01～999999.99 元")})
        Panel { Icon(Icons.Outlined.TipsAndUpdates,null,tint=MaterialTheme.colorScheme.primary);Text("给生活留一点余量",style=MaterialTheme.typography.titleMedium);Subtitle("预算只计算这个月实际记录的支出，收入不会抵扣已支出。修改账目后，剩余额度会自动更新。") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PaymentEditor(plan:PaymentPlan?,busy:Boolean,onSave:(PaymentPlan,()->Unit)->Unit,onDirty:(Boolean)->Unit={}) {
    val initial=plan?.let{Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime()}?:LocalDate.now().plusDays(1).atTime(9,0)
    val key="plan:${plan?.id?:"new"}";val draft=rememberFormDraft(key)
    val original=remember {mapOf("name" to (plan?.name?:""),"amount" to (plan?.amountCents?.money()?:""),"date" to initial.toLocalDate().toString(),
        "time" to initial.toLocalTime().withSecond(0).withNano(0).toString(),"status" to (plan?.status?:"pending"),"enabled" to (plan?.reminderEnabled?:true).toString())}
    var name by rememberSaveable {mutableStateOf(draft["name"]?:original.getValue("name"))}
    var amount by rememberSaveable {mutableStateOf(draft["amount"]?:original.getValue("amount"))}
    var date by rememberSaveable {mutableStateOf(draft["date"]?:original.getValue("date"))}
    var time by rememberSaveable {mutableStateOf(draft["time"]?:original.getValue("time"))}
    var status by rememberSaveable {mutableStateOf(draft["status"]?:original.getValue("status"))}
    var enabled by rememberSaveable {mutableStateOf((draft["enabled"]?:original.getValue("enabled")).toBoolean())}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    var confirmCancel by remember {mutableStateOf(false)}
    val context=LocalContext.current
    TrackFormDraft(key,mapOf("name" to name,"amount" to amount,"date" to date,"time" to time,"status" to status,"enabled" to enabled.toString()),original,onDirty)
    val saved:()->Unit={DraftStore(context).clear(key);onDirty(false)}
    if(confirmCancel) AlertDialog(onDismissRequest={confirmCancel=false},title={Text("取消此计划？")},text={Text("保留历史记录，撤销系统通知和到期提醒，不生成账目。")},confirmButton={TextButton({
        plan?.let {onSave(it.copy(status="cancelled"),saved)};confirmCancel=false
    }){Text("取消计划")}},dismissButton={TextButton({confirmCancel=false}){Text("保留")}})
    EditorFrame(if(plan==null)"安排一项生活缴费" else "管理缴费计划","记下到期日，把琐事交给提醒。",busy,"保存计划",{
        try {
            require(name.isNotBlank()) {"请填写缴费项目"}
            val cents=LedgerRules.parseMoney(amount)
            val day=LocalDate.parse(date);require(day.year in 1900..2100){"日期范围为 1900～2100 年"}
            val requested=day.atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val at=if(plan!=null && date==initial.toLocalDate().toString() && time==initial.toLocalTime().withSecond(0).withNano(0).toString()) plan.dueAt else requested
            onSave(PaymentPlan(plan?.id?:0,name,cents,at,status=status,reminderEnabled=enabled),saved)
        }catch(e:Exception){error=e.message?:"请检查日期、时间和金额"}
    }) {
        OutlinedTextField(name,{name=it.take(40);error=null},Modifier.fillMaxWidth(),label={Text("缴费项目")},placeholder={Text("例如：宿舍电费")},singleLine=true,shape=RoundedCornerShape(14.dp))
        OutlinedTextField(amount,{amount=it.take(12);error=null},Modifier.fillMaxWidth(),label={Text("金额（元）")},prefix={Text("¥ ")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(14.dp))
        OutlinedTextField(date,{date=it.take(10);error=null},Modifier.fillMaxWidth(),label={Text("到期日期")},singleLine=true,shape=RoundedCornerShape(14.dp),trailingIcon={IconButton({
            val d=runCatching{LocalDate.parse(date)}.getOrDefault(LocalDate.now())
            DatePickerDialog(context,{_,y,m,day->date=LocalDate.of(y,m+1,day).toString()},d.year,d.monthValue-1,d.dayOfMonth).show()
        }){Icon(Icons.Outlined.CalendarToday,"选择到期日期")}})
        OutlinedTextField(time,{time=it.take(5);error=null},Modifier.fillMaxWidth(),label={Text("提醒时间 HH:mm")},singleLine=true,shape=RoundedCornerShape(14.dp),trailingIcon={IconButton({
            val t=runCatching{LocalTime.parse(time)}.getOrDefault(LocalTime.of(9,0))
            TimePickerDialog(context,{_,h,m->time="%02d:%02d".format(java.util.Locale.ROOT,h,m)},t.hour,t.minute,true).show()
        }){Icon(Icons.Outlined.Schedule,"选择提醒时间")}})
        Subtitle("按当前时区 ${ZoneId.systemDefault().id} 保存。过去的到期时间会尽快提醒。")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("pending" to "待缴","paid" to "已缴","cancelled" to "已取消").forEach{(v,label)->LedgerChip(status==v,{status=v},label={Text(label)})}
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Column(Modifier.weight(1f)){Text("开启到期提醒",style=MaterialTheme.typography.titleMedium);Subtitle("仅待缴状态发送通知")};Switch(enabled,{enabled=it}) }
        FieldError(error)
        Panel { Subtitle("缴费计划不会自动入账。已缴、取消或关闭提醒会撤销通知；实际支出可通过“另记一笔”确认记录。") }
        if(plan?.status=="pending") TextButton({confirmCancel=true},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("取消此缴费计划",color=MaterialTheme.colorScheme.error)}
    }
}

@Composable fun ReviewEditor(month:YearMonth,data:LedgerData,busy:Boolean,onSave:(ReviewEntity,()->Unit)->Unit,onDetails:(String?)->Unit,onDirty:(Boolean)->Unit={}) {
    val entries=remember(data.entries,month){data.monthEntries(month)}
    val budget=data.budget(month);val totals=remember(entries){entries.totals()}
    val signature=remember(entries,budget){reviewSignature(entries,budget)}
    val categories=remember(entries){entries.filter{it.type=="expense"}.groupBy{it.categoryCode}.mapValues{(_,items)->items.sumOf{it.amountCents}}}
    val saved=data.reviews.firstOrNull{it.month==month.toString()}
    fun generate()=LedgerRules.review(month.toString(),LedgerRules.summarize(entries.map{it.toEntry()}),budget)
    val key="review:$month";val draft=rememberFormDraft(key);val context=LocalContext.current
    val original=remember {mapOf("body" to (saved?.body?:generate()),"basis" to (saved?.statisticsSnapshot?:signature))}
    var body by rememberSaveable {mutableStateOf(draft["body"]?:original.getValue("body"))}
    var basis by rememberSaveable {mutableStateOf(draft["basis"]?:original.getValue("basis"))}
    var generatedAt by rememberSaveable {mutableLongStateOf(draft["generatedAt"]?.toLongOrNull()?:saved?.generatedAt?:System.currentTimeMillis())}
    TrackFormDraft(key,mapOf("body" to body,"basis" to basis,"generatedAt" to generatedAt.toString()),
        original+("generatedAt" to generatedAt.toString()),onDirty)
    var confirm by remember {mutableStateOf(false)}
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("按最新账目重新生成？")},text={Text("将替换当前编辑框内容。点击“保存回顾”后才会覆盖已保存的回顾。")},
        confirmButton={TextButton({body=generate();basis=signature;generatedAt=System.currentTimeMillis();confirm=false}){Text("重新生成")}},dismissButton={TextButton({confirm=false}){Text("保留编辑")}})
    EditorFrame("给这个月，写一份回顾","$month · 全月 / 全部类别 · 本地统计生成",busy,"保存回顾",{onSave(ReviewEntity(month.toString(),body,basis,generatedAt)){DraftStore(context).clear(key);onDirty(false)}}) {
        Panel {
            SectionHeader("当前统计依据","查看明细"){onDetails(null)}
            Text("${totals.count} 笔账目",style=MaterialTheme.typography.titleMedium)
            Subtitle("收入 ¥${totals.income.money()} · 支出 ¥${totals.expense.money()}")
            Subtitle(if(budget==0L)"尚未设置月度预算" else "预算 ¥${budget.money()} · 剩余额度 ¥${(budget-totals.expense).money()}")
            categories.forEach{(code,cents)->TextButton({onDetails(code)},contentPadding=PaddingValues(0.dp)) {Text("${categoryName(code)} ¥${cents.money()}");Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp))}}
        }
        if(basis!=signature) FieldError("账目或预算已经变化。当前编辑稿已保留，建议核对后重新生成。")
        OutlinedTextField(body,{body=it.take(10000)},Modifier.fillMaxWidth(),label={Text("回顾与预算建议 · 可编辑")},minLines=12,shape=RoundedCornerShape(14.dp),supportingText={Text("${body.length}/10000 · 建议不会直接修改预算")})
        OutlinedButton({confirm=true},Modifier.fillMaxWidth(),enabled=!busy){Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("按当前账目重新生成")}
    }
}
