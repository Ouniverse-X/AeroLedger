package cn.edu.buaa.ledger.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.money
import java.time.LocalDate

fun categoryColor(code: String): Color = when(code) {
    "food" -> Color(0xFF2257B8); "shopping" -> Color(0xFF0F766E); "transport" -> Color(0xFF8B5CF6)
    "study" -> Color(0xFFB45309); "bills" -> Color(0xFFC24168); "other" -> Color(0xFF64748B)
    else -> Color(0xFF0F766E)
}
fun categoryIcon(code: String): ImageVector = when(code) {
    "food" -> Icons.Outlined.Restaurant; "shopping" -> Icons.Outlined.ShoppingBag
    "transport" -> Icons.Outlined.DirectionsTransit; "study" -> Icons.Outlined.MenuBook
    "bills" -> Icons.Outlined.Bolt; "allowance" -> Icons.Outlined.AccountBalanceWallet
    "scholarship" -> Icons.Outlined.School; "work" -> Icons.Outlined.WorkOutline
    else -> Icons.Outlined.MoreHoriz
}
fun categoryName(code: String) = campusCategories.firstOrNull { it.code == code }?.name ?: code

@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable fun SectionHeader(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick) { Text(action); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp)) }
    }
}
@Composable fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}
@Composable fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
@Composable fun Money(cents: Long, modifier: Modifier = Modifier, size: Int = 24, color: Color = MaterialTheme.colorScheme.onSurface,
    sign: String = "", symbol: Boolean = true) {
    val value = (if (symbol) "¥ " else "") + sign + cents.money()
    val scale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        val adjusted = minOf(size.toFloat(), (maxWidth.value / (value.length * .62f * scale)).coerceAtLeast(12f))
        Text(value, fontSize = adjusted.sp, fontWeight = FontWeight.Bold, color = color,
            style = fontFeatureSettingsStyle(), maxLines = 2)
    }
}
private fun fontFeatureSettingsStyle() = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

@Composable fun CategoryAvatar(code: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminanceValue() < .2f
    val color = categoryColor(code).let { if (dark) androidx.compose.ui.graphics.lerp(it, Color.White, .38f) else it }
    Box(modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = if(selected) .23f else .10f)), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(code), categoryName(code), Modifier.size(23.dp), tint = color)
    }
}
private fun Color.luminanceValue() = red * .2126f + green * .7152f + blue * .0722f

@Composable fun EntryRow(item: TransactionEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(vertical=12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryAvatar(item.categoryCode)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(categoryName(item.categoryCode), style = MaterialTheme.typography.titleMedium)
            Text(item.note.ifBlank { LocalDate.ofEpochDay(item.dateEpochDay).toString() }, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(item.sourceType == "text") Text("文本辅助录入", style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Money(item.amountCents, Modifier.widthIn(max=128.dp), size=19, sign=if(item.type=="expense") "−" else "+", symbol=false,
                color=if(item.type=="income") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
            Text(if(item.type=="income") "收入" else "支出", style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun EmptyState(title: String, description: String, action: String? = null, onAction: () -> Unit = {},
    icon: ImageVector = Icons.Outlined.ReceiptLong) {
    Panel {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(78.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment=Alignment.Center) {
                Icon(icon,null,Modifier.size(34.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Text(title,style=MaterialTheme.typography.titleMedium)
            Subtitle(description)
            if(action!=null) FilledTonalButton(onAction, shape=RoundedCornerShape(12.dp)) { Text(action) }
        }
    }
}

@Composable fun PrimaryButton(text: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Button(onClick, enabled=enabled && !busy, modifier=Modifier.fillMaxWidth().heightIn(min=52.dp), shape=RoundedCornerShape(14.dp),
        contentPadding=PaddingValues(14.dp)) {
        if(busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth=2.dp); Spacer(Modifier.width(8.dp)) }
        Text(text)
    }
}

@Composable fun FieldError(error: String?) {
    if(error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline,null,tint=MaterialTheme.colorScheme.error,modifier=Modifier.size(20.dp))
            Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun LedgerChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null) {
    FilterChip(selected,onClick,label=label,leadingIcon=leadingIcon,trailingIcon=trailingIcon,
        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer,selectedLeadingIconColor=MaterialTheme.colorScheme.primary))
}
