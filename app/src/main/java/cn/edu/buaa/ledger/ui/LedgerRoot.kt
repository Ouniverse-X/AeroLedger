package cn.edu.buaa.ledger.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.buaa.ledger.*
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import cn.edu.buaa.ledger.reminder.PaymentReminders
import cn.edu.buaa.ledger.ui.components.*
import cn.edu.buaa.ledger.ui.theme.LedgerTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerRoot(vm:LedgerViewModel,openedPlan:Long?,clearPlan:()->Unit,onDark:(Boolean)->Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val startupError by vm.startupError.collectAsStateWithLifecycle()
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val restorePreview by vm.restorePreview.collectAsStateWithLifecycle()
    val deleted by vm.undoEntry.collectAsStateWithLifecycle()
    val dark=theme=="dark"||theme=="system"&&isSystemInDarkTheme()
    SideEffect {onDark(dark)}
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var filter by remember {mutableStateOf(vm.filter)}
    fun change(next:FilterSpec) {filter=next;vm.filter=next}
    var sheet by rememberSaveable {mutableStateOf<String?>(null)}
    var rawText by rememberSaveable {mutableStateOf(vm.drafts.read("text")["raw"]?:"")}
    var dirty by remember(sheet) {mutableStateOf(false)}
    var confirmExit by rememberSaveable(sheet) {mutableStateOf(false)}
    fun requestClose(){if(!busy){if(dirty)confirmExit=true else sheet=null}}
    fun draftKey():String?=when {
        sheet=="parsed"->"entry:text-${rawText.hashCode()}"
        sheet?.startsWith("record-plan:")==true->"entry:plan-${sheet!!.substringAfter(':')}"
        sheet=="budget"->"budget:${filter.month}"
        sheet=="review"->"review:${filter.month}"
        sheet=="text"->"text"
        sheet?.startsWith("entry:")==true||sheet?.startsWith("plan:")==true->sheet
        else->null
    }
    BackHandler(sheet!=null && dirty && !busy){requestClose()}
    var exportAll by rememberSaveable {mutableStateOf(true)}
    var confirmDemo by remember {mutableStateOf(false)}
    var confirmReminder by remember {mutableStateOf(false)}
    val snack=remember{SnackbarHostState()}
    val context=LocalContext.current
    val config=LocalConfiguration.current
    val landscape=config.screenWidthDp>config.screenHeightDp && config.screenHeightDp<500
    val keyboard=LocalSoftwareKeyboardController.current
    var notifications by remember {mutableStateOf(PaymentReminders.allowed(context))}
    var exact by remember {mutableStateOf(PaymentReminders.exactAllowed(context))}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME){notifications=PaymentReminders.allowed(context);exact=PaymentReminders.exactAllowed(context)}}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(Unit) {vm.messages.collect{snack.showSnackbar(it)}}
    LaunchedEffect(deleted?.id) {
        val item=deleted ?: return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val result=snack.showSnackbar("账目已删除，统计已更新",actionLabel="撤销",withDismissAction=true,duration=SnackbarDuration.Long)
        if(result==SnackbarResult.ActionPerformed)vm.undoDelete()
        else if(vm.undoEntry.value?.id==item.id)vm.undoEntry.value=null
    }
    LaunchedEffect(openedPlan,data.ready) {
        if(openedPlan!=null&&data.ready) {tab=3;if(data.plans.any{it.id==openedPlan})sheet="plan:$openedPlan";clearPlan()}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {notifications=PaymentReminders.allowed(context);vm.restoreReminders()}
    fun notifyPermission() {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
            !context.getSharedPreferences("permissions",0).getBoolean("asked",false)) {
            context.getSharedPreferences("permissions",0).edit().putBoolean("asked",true).apply();permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else runCatching{context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
            .onFailure{vm.messages.tryEmit("请在系统设置中允许航小账通知")}
    }
    fun exactPermission() {
        if(Build.VERSION.SDK_INT>=31) runCatching {context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}
            .onFailure{vm.messages.tryEmit("此设备未提供精确提醒设置，将使用弹性提醒")}
    }
    val importFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) vm.task {
        val result=withContext(Dispatchers.IO) {
            val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else "账目文件.csv"}?:"账目文件.csv"
            val bytes=context.contentResolver.openInputStream(uri)?.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=2_000_000){"文件超过 2 MB"};output.write(buffer,0,count)}
                output.toByteArray()
            }?:error("无法读取所选文件")
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() to name
        }
        vm.previewImport(result.first,result.second);sheet="import-preview"
    }}
    val exportFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->if(uri!=null)vm.task {
        val snapshot=if(exportAll)vm.repository.dao.transactions() else vm.repository.filteredEntries(filter)
        withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(CsvCodec.encode(snapshot.map{it.toEntry()}).toByteArray(StandardCharsets.UTF_8))}?:error("无法写入所选位置")}
        vm.messages.emit("已导出 ${snapshot.size} 笔账目")
    }}
    fun export(all:Boolean) {exportAll=all;sheet=null;exportFile.launch("航小账-${if(all)"全部账目" else filter.label}.csv")}
    val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)vm.task{
        withContext(Dispatchers.IO){
            val backup=vm.repository.createBackup(vm.appearance.theme.first())
            val text=BackupCodec.encode(backup)
            context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray(StandardCharsets.UTF_8))}?:error("无法写入备份文件")
        }
        vm.messages.emit("完整账本已备份，包含预算、计划、回顾与主题")
    }}
    val backupImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)vm.task{
        val (text,name)=withContext(Dispatchers.IO){
            val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else "backup.json"}?:"backup.json"
            val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytesLimited(BackupCodec.MAX_BYTES)}?:error("无法读取备份文件")
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() to name
        }
        vm.previewRestore(text,name);sheet="restore-preview"
    }}

    LedgerTheme(theme) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            topBar={
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=if(landscape)4.dp else 12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(13.dp)) {Icon(Icons.Outlined.FlightTakeoff,null,Modifier.padding(if(landscape)6.dp else 10.dp).size(if(landscape)19.dp else 23.dp),tint=MaterialTheme.colorScheme.onPrimary)}
                    Column(Modifier.weight(1f)) {Text("航小账",style=MaterialTheme.typography.titleLarge);if(!landscape)Text("北航校园生活记账本",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton({sheet="settings"}){Icon(Icons.Outlined.Tune,"设置与数据管理")}
                }
            },
            bottomBar={if(!landscape)NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                val icons=listOf(Icons.Outlined.SpaceDashboard,Icons.Outlined.ReceiptLong,Icons.Outlined.DonutLarge,Icons.Outlined.EventNote)
                for(position in 0..4) {
                    if(position==2) {
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            FilledIconButton(onClick={sheet=if(tab==3)"plan:new" else "entry:new"},enabled=data.ready,
                                modifier=Modifier.size(52.dp).testTag("primary-add").semantics {contentDescription=if(tab==3)"添加计划" else "记一笔"}) {
                                Icon(Icons.Outlined.Add,null,Modifier.size(28.dp))
                            }
                            Text(if(tab==3)"添加计划" else "记一笔",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        }
                        continue
                    }
                    val i=if(position<2)position else position-1
                    NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],null)},label={Text(listOf("总览","账目","统计","缴费")[i])},
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,
                            indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                }
            }},
            snackbarHost={SnackbarHost(snack)}
        ) {padding->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if(landscape)NavigationRail(containerColor=MaterialTheme.colorScheme.surface,windowInsets=WindowInsets(0,0,0,0)) {
                    FilledIconButton(onClick={sheet=if(tab==3)"plan:new" else "entry:new"},enabled=data.ready,
                        modifier=Modifier.size(48.dp).testTag("primary-add").semantics {contentDescription=if(tab==3)"添加计划" else "记一笔"}) {Icon(Icons.Outlined.Add,null)}
                    listOf(Icons.Outlined.SpaceDashboard,Icons.Outlined.ReceiptLong,Icons.Outlined.DonutLarge,Icons.Outlined.EventNote).forEachIndexed{i,icon->
                        NavigationRailItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,listOf("总览","账目","统计","缴费")[i])},
                            modifier=Modifier.height(48.dp),colors=NavigationRailItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                    }
                }
              Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    startupError!=null->Column(Modifier.padding(20.dp)){EmptyState("暂时无法打开账本",startupError!!,"重试",vm::load,Icons.Outlined.ErrorOutline)}
                    !data.ready->Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Subtitle("正在打开你的校园账本…")}
                    tab==0->Overview(data,filter,::change,{sheet="budget"},{sheet="entry:${it.id}"},{change(filter.copy(day=null,category=null,type=null,query=""));tab=1},{tab=3},{confirmDemo=true})
                    tab==1->EntriesScreen(data,filter,::change,{sheet="entry:${it.id}"},{sheet="csv"},{sheet="text"},vm::loadMore)
                    tab==2->StatisticsScreen(data,filter,::change,{change(it);tab=1},{sheet="review"})
                    else->PaymentsScreen(data,notifications,exact,::notifyPermission,::exactPermission,{sheet="plan:${it.id}"},{vm.savePlan(it)},
                        {confirmReminder=true},{sheet="record-plan:${it.id}"})
                }
                if(busy&&sheet==null)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
              }
            }
        }
        if(confirmDemo) AlertDialog(onDismissRequest={confirmDemo=false},icon={Icon(Icons.Outlined.School,null)},title={Text("载入校园示例数据")},
            text={Text("添加 20 笔虚构记录，均标注“示例”，用于查看六个月的收支趋势。当前月支出 ¥205.40、收入 ¥2500.00。保留已有账目，仅可载入一次。")},
            confirmButton={TextButton({vm.seedDemo();change(FilterSpec());confirmDemo=false}){Text("载入示例")}},dismissButton={TextButton({confirmDemo=false}){Text("返回")}})
        if(confirmReminder) AlertDialog(onDismissRequest={confirmReminder=false},title={Text("创建通知验收计划")},text={Text("创建一条 1 分钟后到期的 ¥0.01 待缴示例。请开启系统通知，允许精确提醒后可更准确地验收。退出到桌面观察真实系统通知。")},
            confirmButton={TextButton({vm.savePlan(PaymentPlan(name="通知验收（示例）",amountCents=1,dueAt=System.currentTimeMillis()+60_000));confirmReminder=false}){Text("创建计划")}},dismissButton={TextButton({confirmReminder=false}){Text("返回")}})

        if(confirmExit)AlertDialog(onDismissRequest={confirmExit=false},title={Text("还没有保存这次编辑")},text={Text("可以保留草稿，下次打开继续编辑；也可以放弃更改。草稿不会计入正式账目。")},
            confirmButton={TextButton({confirmExit=false;sheet=null}){Text("保留草稿并退出")}},
            dismissButton={Row{TextButton({draftKey()?.let(vm.drafts::clear);if(sheet=="text")rawText="";confirmExit=false;sheet=null}){Text("放弃更改",color=MaterialTheme.colorScheme.error)};TextButton({confirmExit=false}){Text("继续编辑")}}})
        if(sheet!=null&&data.ready) ModalBottomSheet(onDismissRequest={requestClose()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,
            confirmValueChange={value->if(value==SheetValue.Hidden && dirty){confirmExit=true;false}else !busy}),
            containerColor=MaterialTheme.colorScheme.surface,dragHandle={BottomSheetDefaults.DragHandle()}) {
            key(sheet) {
                val route=sheet!!
                val close:()->Unit={sheet=null}
                when {
                    route.startsWith("entry:")||route.startsWith("record-plan:")||route=="parsed"->{
                        val id=route.substringAfter(':',"")
                        val original=(data.entries+(data.recentEntries?:emptyList())).firstOrNull{it.id==id}
                        val plan=if(route.startsWith("record-plan:"))data.plans.firstOrNull{it.id.toString()==id} else null
                        EntryEditor(original,filter.day?:if(filter.month==YearMonth.now())LocalDate.now() else filter.month.atDay(1),busy,
                            {entry,done->vm.saveEntry(entry){val date=LocalDate.ofEpochDay(entry.dateEpochDay);change(filter.copy(month=YearMonth.from(date),day=if(filter.day!=null)date else null,type=null,category=null,query=""));if(route=="parsed"){vm.drafts.clear("text");rawText=""};done()}},close,{id,done->vm.deleteEntry(id,done)},
                            raw=if(route=="parsed")rawText else "",parsed=if(route=="parsed")CampusParser.parse(rawText,LocalDate.now()) else null,prefill=plan,onDirty={dirty=it})
                    }
                    route=="budget"->BudgetEditor(filter.month,data.budget(filter.month),busy,{cents,done->vm.setBudget(filter.month,cents){done();close()}},onDirty={dirty=it})
                    route.startsWith("plan:")->PaymentEditor(data.plans.firstOrNull{it.id.toString()==route.substringAfter(':')},busy,{plan,done->vm.savePlan(plan){done();close()}},onDirty={dirty=it})
                    route=="review"->{
                        val reviewEntries by produceState<List<TransactionEntity>?>(null,filter.month){
                            vm.repository.dao.observeMonth(filter.month.atDay(1).toEpochDay(),filter.month.atEndOfMonth().toEpochDay()).collect{value=it}
                        }
                        reviewEntries?.let{all->ReviewEditor(filter.month,data.copy(entries=all),busy,{review,done->vm.saveReview(review){done();close()}},{code->change(filter.copy(day=null,category=code,type=if(code==null)null else "expense",query=""));tab=1;close()},onDirty={dirty=it})}
                            ?:CircularProgressIndicator(Modifier.padding(24.dp))
                    }
                    route=="csv"->CsvMenu(busy,{sheet=null;importFile.launch(arrayOf("*/*"))},{export(true)},{export(false)},
                        resume=if(preview!=null){{sheet="import-preview"}}else null)
                    route=="import-preview"->preview?.let{ImportPreviewSheet(it,busy){keep->vm.task{val outcome=vm.completeImport(keep);sheet="import-result:${outcome.added}:${outcome.skipped}:${outcome.invalid}"}}}
                        ?:EmptyState("预览已经关闭","请重新选择 CSV 文件。")
                    route.startsWith("import-result:")->ImportResultSheet(route.split(':').drop(1).map{it.toInt()},close){sheet=null;tab=1;change(filter.copy(type=null,category=null))}
                    route=="text"->{
                        TrackFormDraft("text",mapOf("raw" to rawText),mapOf("raw" to ""),{dirty=it})
                        TextEntrySheet(rawText,{rawText=it}){keyboard?.hide();sheet="parsed"}
                    }
                    route=="backup"->BackupMenu(busy,{close();backupExport.launch("AeroLedger-backup-${LocalDate.now()}.json")},{close();backupImport.launch(arrayOf("*/*"))},if(restorePreview!=null){{sheet="restore-preview"}}else null)
                    route=="restore-preview"->restorePreview?.let{RestoreSheet(it,busy){mode,appearance->vm.restore(mode,appearance){rawText="";change(FilterSpec());close()}}}
                        ?:EmptyState("恢复预览未保留","请重新选择备份文件。","选择备份",{sheet="backup"})
                    route=="settings"->SettingsSheet(theme,vm::changeTheme,{close();confirmDemo=true},{sheet="csv"},::notifyPermission,notifications,{sheet="backup"})
                }
            }
        }
    }
}

@Composable private fun CsvMenu(busy:Boolean,onImport:()->Unit,onAll:()->Unit,onFiltered:()->Unit,resume:(()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("账目随你，妥善保存",style=MaterialTheme.typography.headlineSmall)
        Subtitle("通过系统文件选择器导入或导出 UTF-8 CSV。只包含账目，预算、计划与回顾不随文件转移。")
        if(resume!=null)OutlinedButton(resume,Modifier.fillMaxWidth(),enabled=!busy){Text("继续上次的 CSV 导入预览")}
        PrimaryButton("选择文件并预览导入",!busy,onClick=onImport)
        OutlinedButton(onAll,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy){Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(8.dp));Text("导出全部账目")}
        OutlinedButton(onFiltered,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy){Text("导出当前筛选账目")}
        Panel {Text("V1 文件格式",style=MaterialTheme.typography.titleMedium);Text("id,type,amount,date,category,note",style=MaterialTheme.typography.bodyMedium);Subtitle("type：expense / income\namount：元，正数，最多两位小数\ndate：YYYY-MM-DD\ncategory：应用预设类别\nid：1～80 位字母、数字、短横线或下划线\n备注最多 200 字，最多 10000 条、2 MB。")}
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun ImportPreviewSheet(preview:ImportPreview,busy:Boolean,onConfirm:(Boolean)->Unit) {
    var keep by rememberSaveable{mutableStateOf(false)}
    EditorFrame("先核对，再导入",preview.filename,busy,"确认导入 ${preview.valid+if(keep)preview.suspicious else 0} 笔",{onConfirm(keep)}) {
        Panel {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){Text("${preview.valid}",style=MaterialTheme.typography.headlineSmall);Subtitle("校验通过")};Column(Modifier.weight(1f)){Text("${preview.duplicate}",style=MaterialTheme.typography.headlineSmall);Subtitle("确定重复")}}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){Text("${preview.suspicious}",style=MaterialTheme.typography.headlineSmall);Subtitle("疑似重复")};Column(Modifier.weight(1f)){Text("${preview.invalid}",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.error);Subtitle("无效记录")}}
        }
        if(preview.suspicious>0) Row(Modifier.fillMaxWidth().clickable{keep=!keep},verticalAlignment=Alignment.CenterVertically){Checkbox(keep,{keep=it});Text("保留疑似重复为独立真实消费",style=MaterialTheme.typography.bodyMedium)}
        Subtitle("确定重复会跳过且不覆盖。以下展示前 100 条，最终按全部 ${preview.rows.size} 条执行。")
        preview.rows.take(100).forEach{row->Panel{
            Tag("第 ${row.ordinal} 条 · ${when(row.state){"valid"->"可导入";"duplicate"->"重复";"suspicious"->"需核对";else->"无效"}}",if(row.state=="invalid")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            row.entry?.let{e->Text("${categoryName(e.categoryCode)} · ${if(e.type=="income")"收入" else "支出"} ¥${e.amountCents.money()}",style=MaterialTheme.typography.titleMedium);Subtitle("${LocalDate.ofEpochDay(e.dateEpochDay)} · ${e.note}")}
            if(row.raw.isNotBlank())Subtitle(row.raw.take(220))
            Subtitle(row.message)
        }}
    }
}

@Composable private fun ImportResultSheet(values:List<Int>,onClose:()->Unit,onView:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Icon(Icons.Outlined.TaskAlt,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(42.dp))
        Text("账目已整理完成",style=MaterialTheme.typography.headlineSmall)
        Panel{Text("成功导入 ${values[0]} 笔",style=MaterialTheme.typography.titleLarge);Subtitle("跳过重复 ${values[1]} 笔 · 无效记录 ${values[2]} 笔");Subtitle("新账目会按文件中的日期显示，可切换月份查看。预算、计划与回顾未改变。")}
        PrimaryButton("前往账目",onClick=onView)
        TextButton(onClose,Modifier.fillMaxWidth()){Text("完成")}
    }
}

@Composable private fun TextEntrySheet(raw:String,change:(String)->Unit,onParse:()->Unit) {
    EditorFrame("把一句日常，变成账目","本地规则提取，不发送到网络；结果由你确认。",false,"提取并核对账目",onParse) {
        OutlinedTextField(raw,{change(it.take(2000))},Modifier.fillMaxWidth(),label={Text("粘贴或输入消费记录")},placeholder={Text("例如：2026-11-04 学一食堂午餐 12.5 元")},minLines=6,shape=RoundedCornerShape(14.dp),supportingText={Text("${raw.length}/2000")})
        Panel {Text("试试这些校园日常",style=MaterialTheme.typography.titleMedium);listOf("${LocalDate.now()} 学一食堂午餐 12.5 元","今天地铁通勤 6 元","昨天收到助教劳务 500 元").forEach{example->TextButton({change(example)},Modifier.fillMaxWidth()){Text(example)}}}
        Subtitle("多个金额、日期缺失或格式不清时会留待核对；确认前不会生成正式账目。")
    }
}

@Composable private fun SettingsSheet(theme:String,onTheme:(String)->Unit,onDemo:()->Unit,onCsv:()->Unit,onNotify:()->Unit,notifications:Boolean,onBackup:()->Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal=20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("你的账本，按你的习惯",style=MaterialTheme.typography.headlineSmall)
        Subtitle("航小账 ${BuildConfig.VERSION_NAME} · ${when(BuildConfig.BUILD_TYPE){"release"->"固定签名版";"demo"->"课程兼容版";else->"开发版"}}")
        Panel {Text("外观",style=MaterialTheme.typography.titleMedium)
            listOf("system" to "跟随系统","light" to "浅色","dark" to "深色").forEach{(v,label)->Row(Modifier.fillMaxWidth().clickable{onTheme(v)},verticalAlignment=Alignment.CenterVertically){RadioButton(theme==v,{onTheme(v)});Text(label)}}
        }
        Panel {Text("数据与提醒",style=MaterialTheme.typography.titleMedium)
            SettingsAction(Icons.Outlined.NotificationsNone,if(notifications)"通知已开启 · 管理通知" else "开启系统通知",onNotify)
            SettingsAction(Icons.Outlined.ImportExport,"CSV 导入与导出",onCsv)
            SettingsAction(Icons.Outlined.Inventory2,"完整备份与恢复",onBackup)
            SettingsAction(Icons.Outlined.School,"载入校园示例数据",onDemo)
        }
        Panel {Text("本机保存，自己掌握",style=MaterialTheme.typography.titleMedium)
            Subtitle("账目、预算、计划和回顾均保存于本机，无需注册。CSV 用于交换账目；完整备份可一起保存预算、计划、回顾和主题。卸载或换机前请先导出完整备份。")
            Subtitle("缴费计划不会自动入账。通知权限用于到期提醒，精确提醒授权用于准时送达，开机权限用于恢复计划；文件由系统选择器授权。系统强行停止后需重新打开应用恢复提醒。")
        }
        Panel {Text("示例与开源参考",style=MaterialTheme.typography.titleMedium)
            Subtitle("所有示例记录均为项目自制虚构数据，不代表北航实际价格。应用不连接银行、支付账户或校园平台。消费回顾与文本录入均采用本地规则，无模型调用或费用。")
            Subtitle("功能与架构参考 MoneyBook（MIT，AndersOnLin4）及 AutoAccounting（GPL-3.0，AutoAccountingOrg）。本应用独立实现，来源说明见项目文档。")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun SettingsAction(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,action:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
