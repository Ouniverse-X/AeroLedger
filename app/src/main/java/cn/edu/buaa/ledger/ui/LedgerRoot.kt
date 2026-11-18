package cn.edu.buaa.ledger.ui

import android.Manifest
import android.content.pm.PackageManager

import android.app.AlarmManager
import android.content.Intent
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
    val appearance by vm.appearanceSettings.collectAsStateWithLifecycle()
    val settings = appearance ?: return
    val theme = settings.mode
    val themeColor = settings.color
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    fun backPage() { settingsPage=when(settingsPage) {
        "导出账目", "导入账目", "导入结果" -> "数据导入与导出"
        "无法导入的账目", "跳过的账目" -> "导入账目"
        "预览账目" -> "导出账目"
        else -> null
    } }
    BackHandler(settingsPage != null) { backPage() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val startupError by vm.startupError.collectAsStateWithLifecycle()
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val importOutcome by vm.importOutcome.collectAsStateWithLifecycle()
    val dark=theme=="dark"||theme=="system"&&isSystemInDarkTheme()
    LaunchedEffect(dark) {onDark(dark)}
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
    val snack=remember{SnackbarHostState()}
    val context=LocalContext.current
    val config=LocalConfiguration.current
    val landscape=config.screenWidthDp>config.screenHeightDp && config.screenHeightDp<500
    val keyboard=LocalSoftwareKeyboardController.current
    var notifications by remember {mutableStateOf(PaymentReminders.allowed(context))}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME){
            val current=PaymentReminders.allowed(context)
            notifications=current
        }}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(Unit) {vm.messages.collect{snack.showSnackbar(it)}}
    LaunchedEffect(openedPlan,data.ready) {
        if(openedPlan!=null&&data.ready) {tab=Destination.PLANS;if(data.plans.any{it.id==openedPlan})sheet="plan:$openedPlan";clearPlan()}
    }
    val firstNotificationRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications=PaymentReminders.allowed(context)
        if(notifications) vm.restoreReminders()
    }
    LaunchedEffect(Unit) {
        val preferences=context.getSharedPreferences("permissions",0)
        if(!preferences.getBoolean("asked",false)) {
            preferences.edit().putBoolean("asked",true).apply()
            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                firstNotificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun notifyPermission() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }.onFailure { vm.messages.tryEmit("请在系统设置中允许航小账通知") }
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
        vm.importPreview.value=result;settingsPage="导入账目"
    }}
    val exportFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->if(uri!=null)vm.task {
        val content=vm.pendingExport ?: error("请重新选择导出范围")
        withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(content.toByteArray(StandardCharsets.UTF_8))}?:error("无法写入所选位置")}
        vm.pendingExport=null
        vm.messages.emit("已导出账目")
    }}
    fun export(entries:List<TransactionEntity>) {
        vm.pendingExport=CsvCodec.encode(entries.map{it.toEntry()})
        exportFile.launch("航小账-账目.csv")
    }

    LedgerTheme(theme,themeColor) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            topBar={
                Surface(color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().height(if(landscape)44.dp else 56.dp),contentAlignment=Alignment.Center) {
                        if(settingsPage != null) IconButton({backPage()},Modifier.align(Alignment.CenterStart).testTag(if(settingsPage=="外观")"appearance-back" else "settings-back")) { Icon(Icons.Outlined.ArrowBack,"返回设置") }
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
                val pageStateHolder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                AnimatedContent(
                    targetState=settingsPage,
                    modifier=Modifier.fillMaxSize(),
                    transitionSpec={
                        fun depth(page:String?)=when(page) {
                            "无法导入的账目", "跳过的账目", "预览账目" -> 3
                            null -> 0
                            "导出账目", "导入账目", "导入结果" -> 2
                            else -> 1
                        }
                        val direction=if(depth(targetState)<depth(initialState))-1 else 1
                        (slideInHorizontally(tween(280)) { direction * it / 8 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280)) { -direction * it / 8 } + fadeOut(tween(160))) using
                            SizeTransform(clip=false)
                    },
                    label="settings-navigation"
                ) { page ->
                pageStateHolder.SaveableStateProvider(page ?: "root") {
                when {
                    startupError!=null->Column(Modifier.padding(20.dp)){EmptyState("暂时无法打开账本",startupError!!,"重试",vm::load,Icons.Outlined.ErrorOutline)}
                    page=="外观"->AppearanceScreen(theme,themeColor,vm::changeTheme,vm::changeThemeColor)
                    page=="管理权限"->PermissionsScreen(::notifyPermission)
                    page=="关于"->AboutScreen()
                    page=="数据导入与导出"->DataTransferScreen(busy||!data.ready,{vm.importPreview.value=null;settingsPage="导入账目"},{settingsPage="导出账目"})
                    page=="导出账目"->ExportSelectionScreen(data.entries,busy||!data.ready,{vm.exportPreview=it;settingsPage="预览账目"},::export)
                    page=="预览账目"->ExportPreviewScreen(vm.exportPreview)
                    page=="导入账目"->ImportFileScreen(preview,busy,{
                        vm.importPreview.value=null;importFile.launch(arrayOf("*/*"))
                    },{settingsPage="无法导入的账目"},{settingsPage="跳过的账目"}) {
                        preview?.let { selected -> vm.task {
                            vm.importOutcome.value=vm.repository.commitImport(selected)
                            settingsPage="导入结果"
                        } }
                    }
                    page in listOf("无法导入的账目","跳过的账目")->preview?.let { ImportReasonsScreen(it,page=="跳过的账目") }
                    page=="导入结果"->ImportResultScreen(importOutcome,{settingsPage="数据导入与导出"}) {
                        settingsPage=null;tab=Destination.BILLS;change(filter.copy(type=null,category=null))
                    }
                    page==null && tab==Destination.SETTINGS->SettingsScreen({settingsPage="外观"},{settingsPage="管理权限"},{settingsPage="数据导入与导出"},{settingsPage="关于"})
                    !data.ready->Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Subtitle("正在加载…")}
                    tab==Destination.BILLS->BillsScreen(data,filter,::change,{sheet="entry:${it.id}"},{tab=Destination.SETTINGS;settingsPage="数据导入与导出"},{sheet="text"},
                        {sheet="budget"},{tab=Destination.PLANS})
                    tab==Destination.STATISTICS->StatisticsScreen(data,filter,::change,{change(it);tab=Destination.BILLS},{sheet="review"})
                    tab==Destination.PLANS->PaymentsScreen(data,notifications,::notifyPermission,{sheet="plan:${it.id}"},{vm.savePlan(it)},
                        {sheet="record-plan:${it.id}"},{sheet="plan:new"})
                }
                }
                }
                if(busy&&sheet==null)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
              }
            }
        }
        val activeSheet = sheet
        if(activeSheet!=null&&data.ready) ModalBottomSheet(onDismissRequest={if(!busy)sheet=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=MaterialTheme.colorScheme.surface,dragHandle={BottomSheetDefaults.DragHandle()}) {
            key(activeSheet) {
                val route=activeSheet
                val close:()->Unit={sheet=null}
                when {
                    route.startsWith("entry:")||route.startsWith("record-plan:")||route=="parsed"->{
                        val id=route.substringAfter(':',"")
                        val original=data.entries.firstOrNull{it.id==id}
                        val plan=if(route.startsWith("record-plan:"))data.plans.firstOrNull{it.id.toString()==id} else null
                        EntryEditor(original,EntryDates.defaultDate(filter,today),busy,
                            {entry,done->vm.saveEntry(entry,original!=null){val date=LocalDate.ofEpochDay(entry.dateEpochDay);change(filter.copy(month=YearMonth.from(date),day=if(filter.day!=null)date else null,type=null,category=null));done()}},close,vm::deleteEntry,
                            raw=if(route=="parsed")rawText else "",parsed=if(route=="parsed")CampusParser.parse(rawText,LocalDate.now()) else null,prefill=plan)
                    }
                    route=="budget"->BudgetEditor(filter.month,data.budget(filter.month),busy){vm.setBudget(filter.month,it,close)}
                    route.startsWith("plan:")->PaymentEditor(data.plans.firstOrNull{it.id.toString()==route.substringAfter(':')},busy){vm.savePlan(it,close)}
                    route=="review"->ReviewEditor(filter.month,data,busy,{vm.saveReview(it,close)},{code->change(filter.copy(day=null,category=code,type=if(code==null)null else "expense"));tab=Destination.BILLS;close()})
                    route=="text"->TextEntrySheet(rawText,{rawText=it}){keyboard?.hide();sheet="parsed"}
                }
            }
        }
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

@Composable private fun PermissionsScreen(onNotify:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Panel {
            SettingsAction(Icons.Outlined.NotificationsNone,"通知权限",onNotify)
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
