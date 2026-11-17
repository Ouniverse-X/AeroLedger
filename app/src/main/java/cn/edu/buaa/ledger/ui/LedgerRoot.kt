package cn.edu.buaa.ledger.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.*
import java.util.UUID

private enum class Destination(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BILLS("账单", Icons.Outlined.ReceiptLong),
    STATISTICS("统计", Icons.Outlined.DonutLarge),
    PLANS("计划", Icons.Outlined.EventNote),
    SETTINGS("设置", Icons.Outlined.Settings)
}

@Composable private fun LedgerBottomBar(selected: Destination, ready: Boolean, onSelect: (Destination)->Unit, onRecord: ()->Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().padding(top=18.dp).background(MaterialTheme.colorScheme.surface))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(98.dp),verticalAlignment=Alignment.Bottom) {
            for(position in 0..4) {
                if(position==2) {
                    Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.TopCenter) {
                        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(4.dp)) {
                            FilledIconButton(onClick=onRecord,enabled=ready,
                                modifier=Modifier.fillMaxSize().testTag("primary-add").semantics {contentDescription="记账"}) {
                                Icon(Icons.Outlined.Add,null,Modifier.size(28.dp))
                            }
                        }
                        Text("记账",Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val destination=Destination.entries[if(position<2)position else position-1]
                    NavigationBarItem(selected=selected==destination,onClick={onSelect(destination)},
                        modifier=Modifier.height(80.dp).testTag("nav-${destination.name}"),
                        icon={Icon(destination.icon,null)},label={Text(destination.title)},
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,
                            indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerRoot(vm:LedgerViewModel,openedPlan:Long?,clearPlan:()->Unit,onDark:(Boolean)->Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val themeColor by vm.themeColor.collectAsStateWithLifecycle()
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(settingsPage != null) { settingsPage=null }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val startupError by vm.startupError.collectAsStateWithLifecycle()
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val dark=theme=="dark"||theme=="system"&&isSystemInDarkTheme()
    SideEffect {onDark(dark)}
    var tab by rememberSaveable {mutableStateOf(Destination.BILLS)}
    var filter by remember {mutableStateOf(vm.filter)}
    fun change(next:FilterSpec) {filter=next;vm.filter=next}
    val today = rememberDeviceDate()
    var lastToday by remember { mutableStateOf(today) }
    LaunchedEffect(today, tab) {
        if (today != lastToday && filter.month == YearMonth.from(lastToday)) {
            change(filter.copy(month=YearMonth.from(today),day=null))
        }
        lastToday = today
        if (tab == Destination.BILLS && filter.month > YearMonth.from(today)) {
            change(filter.copy(month=EntryDates.homeMonth(filter.month,today),day=null))
        }
    }
    var sheet by rememberSaveable {mutableStateOf<String?>(null)}
    var rawText by rememberSaveable {mutableStateOf("")}
    var exportAll by rememberSaveable {mutableStateOf(true)}
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
    LaunchedEffect(openedPlan,data.ready) {
        if(openedPlan!=null&&data.ready) {tab=Destination.PLANS;if(data.plans.any{it.id==openedPlan})sheet="plan:$openedPlan";clearPlan()}
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
            vm.repository.previewCsv(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(),name)
        }
        vm.importPreview.value=result;sheet="import-preview"
    }}
    val exportFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->if(uri!=null)vm.task {
        val snapshot=if(exportAll)vm.data.value.entries else filter.select(vm.data.value.entries)
        withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(CsvCodec.encode(snapshot.map{it.toEntry()}).toByteArray(StandardCharsets.UTF_8))}?:error("无法写入所选位置")}
        vm.messages.emit("已导出 ${snapshot.size} 笔账目")
    }}
    fun export(all:Boolean) {exportAll=all;sheet=null;exportFile.launch("航小账-${if(all)"全部账目" else filter.label}.csv")}

    LedgerTheme(theme,themeColor) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            topBar={
                Surface(color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height(if(landscape)44.dp else 56.dp),contentAlignment=Alignment.Center) {
                        if(settingsPage != null) IconButton({settingsPage=null},Modifier.align(Alignment.CenterStart).testTag(if(settingsPage=="外观")"appearance-back" else "settings-back")) { Icon(Icons.Outlined.ArrowBack,"返回设置") }
                        Text(settingsPage ?: tab.title,style=MaterialTheme.typography.titleLarge)
                    }
                }
            },
            bottomBar={if(!landscape&&settingsPage==null)LedgerBottomBar(tab,data.ready,{tab=it},{sheet="entry:new"})},
            snackbarHost={SnackbarHost(snack)}
        ) {padding->
            // Let page content extend behind the raised button instead of reserving a blank strip.
            val contentBottom=(padding.calculateBottomPadding()-if(landscape||settingsPage!=null)0.dp else 18.dp).coerceAtLeast(0.dp)
            Row(Modifier.padding(start=padding.calculateStartPadding(LocalLayoutDirection.current),
                end=padding.calculateEndPadding(LocalLayoutDirection.current),top=padding.calculateTopPadding(),bottom=contentBottom).fillMaxSize()) {
                if(landscape&&settingsPage==null)NavigationRail(containerColor=MaterialTheme.colorScheme.surface,windowInsets=WindowInsets(0,0,0,0)) {
                    FilledIconButton(onClick={sheet="entry:new"},enabled=data.ready,
                        modifier=Modifier.size(48.dp).testTag("primary-add").semantics {contentDescription="记账"}) {Icon(Icons.Outlined.Add,null)}
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(selected=tab==destination,onClick={tab=destination},
                            modifier=Modifier.testTag("nav-${destination.name}"),
                            icon={Icon(destination.icon,null)},label={Text(destination.title)},
                            colors=NavigationRailItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                    }
                }
              Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState=settingsPage,
                    modifier=Modifier.fillMaxSize(),
                    transitionSpec={
                        val direction=if(targetState==null)-1 else 1
                        (slideInHorizontally(tween(280)) { direction * it / 8 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280)) { -direction * it / 8 } + fadeOut(tween(160))) using
                            SizeTransform(clip=false)
                    },
                    label="settings-navigation"
                ) { page ->
                when {
                    startupError!=null->Column(Modifier.padding(20.dp)){EmptyState("暂时无法打开账本",startupError!!,"重试",vm::load,Icons.Outlined.ErrorOutline)}
                    !data.ready->Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Subtitle("正在加载…")}
                    page=="外观"->AppearanceScreen(theme,themeColor,vm::changeTheme,vm::changeThemeColor)
                    page=="管理权限"->PermissionsScreen(notifications,exact,::notifyPermission,::exactPermission)
                    page=="关于"->AboutScreen()
                    page=="数据导入与导出"->DataTransferScreen(busy,{importFile.launch(arrayOf("*/*"))},{export(true)},{export(false)})
                    tab==Destination.BILLS->BillsScreen(data,filter,::change,{sheet="entry:${it.id}"},{tab=Destination.SETTINGS;settingsPage="数据导入与导出"},{sheet="text"},
                        {sheet="budget"},{tab=Destination.PLANS})
                    tab==Destination.STATISTICS->StatisticsScreen(data,filter,::change,{change(it);tab=Destination.BILLS},{sheet="review"})
                    tab==Destination.PLANS->PaymentsScreen(data,notifications,exact,::notifyPermission,::exactPermission,{sheet="plan:${it.id}"},{vm.savePlan(it)},
                        {sheet="record-plan:${it.id}"},{sheet="plan:new"})
                    tab==Destination.SETTINGS->SettingsScreen({settingsPage="外观"},{settingsPage="管理权限"},{tab=Destination.SETTINGS;settingsPage="数据导入与导出"},{settingsPage="关于"})
                }
                }
                if(busy&&sheet==null)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
              }
            }
        }
        if(sheet!=null&&data.ready) ModalBottomSheet(onDismissRequest={if(!busy)sheet=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=MaterialTheme.colorScheme.surface,dragHandle={BottomSheetDefaults.DragHandle()}) {
            key(sheet) {
                val route=sheet!!
                val close:()->Unit={sheet=null}
                when {
                    route.startsWith("entry:")||route.startsWith("record-plan:")||route=="parsed"->{
                        val id=route.substringAfter(':',"")
                        val original=data.entries.firstOrNull{it.id==id}
                        val plan=if(route.startsWith("record-plan:"))data.plans.firstOrNull{it.id.toString()==id} else null
                        EntryEditor(original,EntryDates.defaultDate(filter,today),busy,
                            {entry,done->vm.saveEntry(entry){val date=LocalDate.ofEpochDay(entry.dateEpochDay);change(filter.copy(month=YearMonth.from(date),day=if(filter.day!=null)date else null,type=null,category=null));done()}},close,vm::deleteEntry,
                            raw=if(route=="parsed")rawText else "",parsed=if(route=="parsed")CampusParser.parse(rawText,LocalDate.now()) else null,prefill=plan)
                    }
                    route=="budget"->BudgetEditor(filter.month,data.budget(filter.month),busy){vm.setBudget(filter.month,it,close)}
                    route.startsWith("plan:")->PaymentEditor(data.plans.firstOrNull{it.id.toString()==route.substringAfter(':')},busy){vm.savePlan(it,close)}
                    route=="review"->ReviewEditor(filter.month,data,busy,{vm.saveReview(it,close)},{code->change(filter.copy(day=null,category=code,type=if(code==null)null else "expense"));tab=Destination.BILLS;close()})
                    route=="import-preview"->preview?.let{ImportPreviewSheet(it,busy){keep->vm.task{val outcome=vm.repository.commitImport(it,keep);vm.importPreview.value=null;sheet="import-result:${outcome.added}:${outcome.skipped}:${outcome.invalid}"}}}
                        ?:EmptyState("预览已经关闭","请重新选择 CSV 文件。")
                    route.startsWith("import-result:")->ImportResultSheet(route.split(':').drop(1).map{it.toInt()},close){sheet=null;settingsPage=null;tab=Destination.BILLS;change(filter.copy(type=null,category=null))}
                    route=="text"->TextEntrySheet(rawText,{rawText=it}){keyboard?.hide();sheet="parsed"}
                }
            }
        }
    }
}

@Composable private fun DataTransferScreen(busy:Boolean,onImport:()->Unit,onAll:()->Unit,onFiltered:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Subtitle("通过系统文件选择器导入或导出 UTF-8 CSV。只包含账目，预算、计划与回顾不随文件转移。")
        PrimaryButton("选择文件并预览导入",!busy,onClick=onImport)
        OutlinedButton(onAll,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy){Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(8.dp));Text("导出全部账目")}
        OutlinedButton(onFiltered,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy){Text("导出当前筛选账目")}
        Panel {Text("CSV 文件格式",style=MaterialTheme.typography.titleMedium);Text("id,type,amount,date,category,note,customCategory,transactionTime,transactionZone",style=MaterialTheme.typography.bodyMedium);Subtitle("type：expense / income\namount：元，正数，最多两位小数\ndate：YYYY-MM-DD\ncategory：应用预设类别\nid：1～80 位字母、数字、短横线或下划线\n兼容旧版六列和七列文件；transactionTime 为可选 HH:mm，transactionZone 为发生时的时区。\ncustomCategory 为其他的自填名称，最多 20 字。\n备注最多 200 字，最多 10000 条、2 MB。")}
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun ImportPreviewSheet(preview:ImportPreview,busy:Boolean,onConfirm:(Boolean)->Unit) {
    var keep by rememberSaveable{mutableStateOf(false)}
    EditorFrame("导入预览",preview.filename,busy,"确认导入 ${preview.valid+if(keep)preview.suspicious else 0} 笔",{onConfirm(keep)}) {
        Panel {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){Text("${preview.valid}",style=MaterialTheme.typography.headlineSmall);Subtitle("校验通过")};Column(Modifier.weight(1f)){Text("${preview.duplicate}",style=MaterialTheme.typography.headlineSmall);Subtitle("确定重复")}}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){Text("${preview.suspicious}",style=MaterialTheme.typography.headlineSmall);Subtitle("疑似重复")};Column(Modifier.weight(1f)){Text("${preview.invalid}",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.error);Subtitle("无效记录")}}
        }
        if(preview.suspicious>0) Row(Modifier.fillMaxWidth().clickable{keep=!keep},verticalAlignment=Alignment.CenterVertically){Checkbox(keep,{keep=it});Text("保留疑似重复为独立真实消费",style=MaterialTheme.typography.bodyMedium)}
        Subtitle("确定重复会跳过且不覆盖。以下展示前 100 条，最终按全部 ${preview.rows.size} 条执行。")
        preview.rows.take(100).forEach{row->Panel{
            Tag("第 ${row.ordinal} 条 · ${when(row.state){"valid"->"可导入";"duplicate"->"重复";"suspicious"->"需核对";else->"无效"}}",if(row.state=="invalid")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            row.entry?.let{e->Text("${e.customCategory.ifBlank { categoryName(e.categoryCode) }} · ${if(e.type=="income")"收入" else "支出"} ¥${e.amountCents.money()}",style=MaterialTheme.typography.titleMedium);Subtitle("${LocalDate.ofEpochDay(e.dateEpochDay)} · ${e.note}")}
            if(row.raw.isNotBlank())Subtitle(row.raw.take(220))
            Subtitle(row.message)
        }}
    }
}

@Composable private fun ImportResultSheet(values:List<Int>,onClose:()->Unit,onView:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Icon(Icons.Outlined.TaskAlt,null,tint=MaterialTheme.colorScheme.secondary,modifier=Modifier.size(42.dp))
        Text("导入结果",style=MaterialTheme.typography.headlineSmall)
        Panel{Text("成功导入 ${values[0]} 笔",style=MaterialTheme.typography.titleLarge);Subtitle("跳过重复 ${values[1]} 笔 · 无效记录 ${values[2]} 笔");Subtitle("新账目会按文件中的日期显示，可切换月份查看。预算、计划与回顾未改变。")}
        PrimaryButton("前往账单",onClick=onView)
        TextButton(onClose,Modifier.fillMaxWidth()){Text("完成")}
    }
}

@Composable private fun TextEntrySheet(raw:String,change:(String)->Unit,onParse:()->Unit) {
    EditorFrame("文本记账","本地规则提取，不发送到网络；结果由你确认。",false,"提取并核对账目",onParse) {
        OutlinedTextField(raw,{change(it.take(2000))},Modifier.fillMaxWidth(),label={Text("粘贴或输入消费记录")},placeholder={Text("例如：2026-11-04 学一食堂午餐 12.5 元")},minLines=6,shape=RoundedCornerShape(14.dp),supportingText={Text("${raw.length}/2000")})
        Panel {Text("示例",style=MaterialTheme.typography.titleMedium);listOf("${LocalDate.now()} 学一食堂午餐 12.5 元","今天地铁通勤 6 元","昨天收到助教劳务 500 元").forEach{example->TextButton({change(example)},Modifier.fillMaxWidth()){Text(example)}}}
        Subtitle("多个金额、日期缺失或格式不清时会留待核对；确认前不会生成正式账目。")
    }
}

@Composable private fun SettingsScreen(onAppearance:()->Unit,onPermissions:()->Unit,onCsv:()->Unit,onAbout:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Panel { SettingsAction(Icons.Outlined.Palette,"外观",onAppearance) }
        Panel { SettingsAction(Icons.Outlined.AdminPanelSettings,"管理权限",onPermissions) }
        Panel { SettingsAction(Icons.Outlined.ImportExport,"数据导入与导出",onCsv) }
        Panel { SettingsAction(Icons.Outlined.Info,"关于",onAbout) }
    }
}

@Composable private fun PermissionsScreen(notifications:Boolean,exact:Boolean,onNotify:()->Unit,onExact:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Panel {
            Text("通知",style=MaterialTheme.typography.titleMedium)
            Subtitle("用于发送缴费到期提醒和截屏记账结果通知。")
            SettingsAction(Icons.Outlined.NotificationsNone,if(notifications)"已开启 · 管理通知" else "未开启 · 允许通知",onNotify)
        }
        Panel {
            Text("精确提醒",style=MaterialTheme.typography.titleMedium)
            Subtitle("用于按设定时间提醒缴费。未授权时使用弹性提醒，系统可能延迟。")
            if(Build.VERSION.SDK_INT>=31) SettingsAction(Icons.Outlined.Alarm,if(exact)"已允许 · 管理精确提醒" else "未允许 · 设置精确提醒",onExact)
            else Subtitle("当前系统无需单独授权。")
        }
    }
}

@Composable private fun AboutScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Panel {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("版本",style=MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.VERSION_NAME,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel {
            Text("数据与隐私",style=MaterialTheme.typography.titleMedium)
            Subtitle("账目、预算、缴费计划和消费回顾保存在本机，图片识别在本机完成。")
            Subtitle("卸载应用会删除本地数据。请提前导出需要保留的账目；CSV 不包含预算、缴费计划和消费回顾。")
        }
    }
}

@Composable private fun SettingsAction(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,action:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
