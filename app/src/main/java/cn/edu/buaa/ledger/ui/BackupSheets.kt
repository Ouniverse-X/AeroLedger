package cn.edu.buaa.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun BackupMenu(busy:Boolean,onExport:()->Unit,onImport:()->Unit,onResume:(()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("把整本账，妥善带走",style=MaterialTheme.typography.headlineSmall)
        Subtitle("完整备份包含账目、月度预算、缴费计划、消费回顾、导入记录与主题设置。")
        Panel {
            Icon(Icons.Outlined.Inventory2,null,tint=MaterialTheme.colorScheme.primary)
            Text("一份文件，保留完整生活记录",style=MaterialTheme.typography.titleMedium)
            Subtitle("恢复前可以查看记录数量与冲突，选择合并或完整替换。备份以 JSON 保存，请妥善保管含个人记录的文件。")
        }
        PrimaryButton("导出完整备份",busy=busy,onClick=onExport)
        if(onResume!=null)OutlinedButton(onResume,Modifier.fillMaxWidth(),enabled=!busy){Text("继续上次的恢复预览")}
        OutlinedButton(onImport,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!busy) { Icon(Icons.Outlined.Restore,null);Spacer(Modifier.width(8.dp));Text("选择备份文件并预览") }
    }
}

@Composable fun RestoreSheet(preview:RestorePreview,busy:Boolean,onRestore:(RestoreMode,Boolean)->Unit) {
    var modeName by rememberSaveable {mutableStateOf(RestoreMode.KEEP_LOCAL.name)}
    val mode=RestoreMode.valueOf(modeName)
    var restoreTheme by rememberSaveable {mutableStateOf(false)}
    var confirm by rememberSaveable {mutableStateOf(false)}
    var details by rememberSaveable {mutableStateOf(false)}
    val backup=preview.backup
    if(confirm)AlertDialog(onDismissRequest={if(!busy)confirm=false},title={Text("确认恢复这份账本？")},
        text={Text(when(mode){RestoreMode.REPLACE_ALL->"将删除本机现有 ${preview.currentRecords} 项记录，并采用备份中的完整账本。此操作无法撤销，请确认已保存需要保留的备份。"
            RestoreMode.USE_BACKUP->"新增备份记录；相同标识的账目、预算、计划和回顾采用备份内容。恢复时重新核对当前数据。"
            RestoreMode.KEEP_LOCAL->"新增本机没有的记录，冲突保留本机内容。不会自动生成缴费账目。"})},
        confirmButton={TextButton({confirm=false;onRestore(mode,restoreTheme)},enabled=!busy){Text(if(mode==RestoreMode.REPLACE_ALL)"确认替换" else "确认恢复")}},
        dismissButton={TextButton({confirm=false}){Text("返回核对")}})
    EditorFrame("先核对，再恢复",preview.filename,busy,"恢复这份账本",{confirm=true}) {
        Panel {
            Subtitle("备份时间 · "+DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(backup.createdAt).atZone(ZoneId.systemDefault())))
            Text("${backup.entries.size} 笔账目",style=MaterialTheme.typography.headlineSmall)
            Subtitle("${backup.budgets.size} 个月度预算 · ${backup.plans.size} 项缴费计划 · ${backup.reviews.size} 份消费回顾")
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)){Text("${preview.newRecords}",style=MaterialTheme.typography.headlineSmall);Subtitle("新增记录")}
                Column(Modifier.weight(1f)){Text("${preview.conflicts}",style=MaterialTheme.typography.headlineSmall,color=if(preview.conflicts>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);Subtitle("内容冲突")}
                Column(Modifier.weight(1f)){Text("${preview.identical}",style=MaterialTheme.typography.headlineSmall);Subtitle("内容相同")}
            }
        }
        if(preview.conflicts>0) {
            TextButton({details=!details}){Text(if(details)"收起冲突明细" else "查看冲突明细")}
            if(details)Panel {
                Subtitle("显示前 ${preview.differences.size} 项。恢复时会重新核对当前数据。")
                preview.differences.forEach { Text(it,style=MaterialTheme.typography.bodySmall);HorizontalDivider() }
            }
        }
        Text("选择恢复方式",style=MaterialTheme.typography.titleMedium)
        RestoreMode.entries.forEach { item ->
            Panel(Modifier.clickable(enabled=!busy){modeName=item.name}) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(mode==item,{modeName=item.name},enabled=!busy)
                    Column(Modifier.weight(1f)){Text(item.label,style=MaterialTheme.typography.titleSmall);Subtitle(when(item){
                        RestoreMode.KEEP_LOCAL->"适合补充记录，已有内容优先保留。"
                        RestoreMode.USE_BACKUP->"适合采用另一份账本的修改结果。"
                        RestoreMode.REPLACE_ALL->"适合换机恢复，将移除本机独有内容。"})}
                }
            }
        }
        Row(Modifier.fillMaxWidth().clickable {restoreTheme=!restoreTheme},verticalAlignment=Alignment.CenterVertically) {
            Checkbox(restoreTheme,{restoreTheme=it});Text("同时恢复备份中的主题设置")
        }
        Subtitle("文件完整性与字段已校验。相同记录按标识合并；恢复完成后重新安排有效的待缴提醒，不会自动新增支出。未保存的编辑草稿不包含在备份中。")
    }
}
