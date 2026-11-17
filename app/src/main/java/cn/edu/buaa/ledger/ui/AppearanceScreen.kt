package cn.edu.buaa.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.edu.buaa.ledger.ui.components.Panel
import cn.edu.buaa.ledger.ui.theme.ThemeColor

@Composable fun AppearanceScreen(mode: String, colorKey: String, onMode: (String)->Unit, onColor: (String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Panel {
            Text("主题色",style=MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                ThemeColor.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach { color ->
                            val selected=ThemeColor.fromKey(colorKey)==color
                            Column(Modifier.weight(1f).testTag("theme-color-${color.key}")
                                .selectable(selected,role=Role.RadioButton,onClick={onColor(color.key)}),
                                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Surface(shape=RoundedCornerShape(14.dp)) {
                                    Box(Modifier.fillMaxWidth().height(64.dp)
                                        .background(Brush.linearGradient(listOf(color.start,color.end))),contentAlignment=Alignment.Center) {
                                        if(selected) Icon(Icons.Outlined.Check,"已选择",tint=Color.White)
                                    }
                                }
                                Text(color.label,style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Panel {
            Text("显示模式",style=MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                listOf("system" to "跟随系统","light" to "浅色","dark" to "深色").forEach { (value,label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("theme-mode-$value")
                        .selectable(mode==value,role=Role.RadioButton,onClick={onMode(value)}),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(mode==value,onClick=null)
                        Spacer(Modifier.width(12.dp))
                        Text(label,style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
