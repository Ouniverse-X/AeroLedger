package cn.edu.buaa.ledger.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import cn.edu.buaa.ledger.data.*
import cn.edu.buaa.ledger.domain.*
import java.time.YearMonth
import java.util.Locale
import kotlin.math.*

private fun percentage(value:Long,total:Long):String = when {
    total<=0 -> "0%"
    value>0 && value*100.0/total<.1 -> "<0.1%"
    else -> "%.1f%%".format(Locale.CHINA,value*100.0/total)
}

@Composable fun CategoryDonut(entries: List<TransactionEntity>, totals: List<CategoryAmount>? = null,
    onDetails: (String) -> Unit) {
    val groups = remember(entries,totals) { totals?.map { it.categoryCode to it.amountCents } ?: entries.filter { it.type=="expense" }.groupBy { it.categoryCode }
        .mapValues { (_,v) -> v.sumOf { it.amountCents } }.toList().sortedByDescending { it.second } }
    val total = groups.sumOf { it.second }
    val dark=MaterialTheme.colorScheme.background.luminance()<.2f
    val colors=groups.associate { (code,_) -> code to categoryColor(code).let{if(dark)lerp(it,Color.White,.35f)else it} }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = groups.firstOrNull { it.first == selected }
    val active = selectedGroup?.first
    LaunchedEffect(groups) { if(groups.none { it.first==selected }) selected=null }
    val motion = remember { Animatable(0f) }
    LaunchedEffect(groups) { motion.snapTo(0f); motion.animateTo(1f,tween(350)) }
    val track = MaterialTheme.colorScheme.outlineVariant
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("支出构成",style=MaterialTheme.typography.titleLarge)
            Tag("${groups.size} 个类别")
        }
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(225.dp).semantics {
                contentDescription="支出分类图，合计${total.money()}元。"+groups.joinToString { "${categoryName(it.first)}${it.second.money()}元" }
            }.pointerInput(groups) {
                detectTapGestures { point ->
                    val x=point.x-size.width/2f; val y=point.y-size.height/2f
                    val radius=sqrt(x*x+y*y)
                    val outer=size.width/2f-10.dp.toPx()
                    if(radius<outer-27.dp.toPx() || radius>outer+2.dp.toPx()) { selected=null; return@detectTapGestures }
                    val angle=((atan2(y,x)*180/PI+450)%360).toFloat()
                    var cursor=0f
                    for((code,value) in groups) {
                        cursor += value*360f/total
                        if(angle<cursor) { selected=if(selected==code) null else code; break }
                    }
                }
            }) {
                val stroke=25.dp.toPx(); val inset=stroke/2+10.dp.toPx()
                val bounds=Size(size.width-inset*2,size.height-inset*2)
                drawArc(track,-90f,360f,false,Offset(inset,inset),bounds,style=Stroke(stroke))
                var start=-90f
                groups.forEach { (code,value) ->
                    val sweep=value*360f/total
                    val color=colors.getValue(code).copy(alpha=if(active==null || active==code) 1f else .25f)
                    drawArc(color,start, (sweep-minOf(1.5f,sweep*.12f))*motion.value,false,Offset(inset,inset),bounds,
                        style=Stroke(if(active==code) stroke+4.dp.toPx() else stroke))
                    start+=sweep
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(160.dp)) {
                Subtitle(active?.let(::categoryName) ?: "分类支出合计")
                Spacer(Modifier.height(7.dp))
                Money(selectedGroup?.second ?: total,Modifier.fillMaxWidth(),size=27)
                Spacer(Modifier.height(4.dp))
                Text(if(selectedGroup!=null && total>0) percentage(selectedGroup.second,total) else "点击分类查看",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(total==0L) Subtitle("当前范围暂无支出，收入不会计入支出占比。")
        groups.forEach { (code,value) ->
            Surface(Modifier.fillMaxWidth().semantics { this.selected=active==code; stateDescription=if(active==code)"已选中" else "未选中" }
                .clickable { selected=if(active==code) null else code },
                color=if(active==code) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,shape=RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal=10.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.size(9.dp)) { drawCircle(colors.getValue(code)) }
                        Text(categoryName(code),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                        Text("¥${value.money()}",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                        Text(percentage(value,total),modifier=Modifier.width(52.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(progress={value.toFloat()/total},modifier=Modifier.fillMaxWidth().height(4.dp),
                        color=colors.getValue(code),trackColor=MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
        active?.let { code -> OutlinedButton({onDetails(code)},modifier=Modifier.fillMaxWidth()) { Text("查看${categoryName(code)}明细") } }
    }
}

@Composable fun MonthlyTrend(data: LedgerData, filter: FilterSpec, onDetails: (YearMonth) -> Unit) {
    val months=(5 downTo 0).map { filter.month.minusMonths(it.toLong()) }
    val values=remember(data.entries,data.trendTotals,filter) { months.map { m ->
        data.trendTotals?.firstOrNull { it.month==m.toString() }?.let { Totals(it.expense,it.income,0) }
            ?: if(data.trendTotals!=null) Totals(0,0,0) else data.entries.filter { e ->
        YearMonth.from(java.time.LocalDate.ofEpochDay(e.dateEpochDay))==m &&
            (filter.type==null||filter.type==e.type) && (filter.category==null||filter.category==e.categoryCode) && e.note.contains(filter.query.trim(),true)
    }.totals() } }
    val motion=remember { Animatable(1f) }
    LaunchedEffect(values){motion.snapTo(0f);motion.animateTo(1f,tween(250))}
    var selected by rememberSaveable(filter.month.toString(),filter.category,filter.type) { mutableIntStateOf(5) }
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    val grid=MaterialTheme.colorScheme.outlineVariant
    val expense=MaterialTheme.colorScheme.primary
    val income=MaterialTheme.colorScheme.secondary
    val highlight=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.5f)
    val maxValue=values.maxOfOrNull { maxOf(it.income,it.expense) }?.coerceAtLeast(100L) ?: 100L
    val magnitude=10.0.pow(floor(log10(maxValue.toDouble())))
    val ceiling=ceil(maxValue/magnitude)*magnitude
    Panel {
        Text("近 6 月收支",style=MaterialTheme.typography.titleLarge)
        Subtitle("${months.first()} 至 ${months.last()} · ${filter.category?.let(::categoryName)?:"全部类别"} · ${when(filter.type){"expense"->"支出";"income"->"收入";else->"全部收支"}}${if(filter.query.isBlank())"" else " · 备注含「${filter.query}」"}")
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            ChartLegend("收入",income); ChartLegend("支出",expense)
        }
        Subtitle("金额（元） · 点击柱形或下方月份核对")
        Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {
            contentDescription="近六个月收支趋势。"+months.indices.joinToString { "${months[it]}收入${values[it].income.money()}元，支出${values[it].expense.money()}元" }
        }.pointerInput(months,values) {
            detectTapGestures { point -> selected=((point.x-44.dp.toPx())/((size.width-52.dp.toPx())/6)).toInt().coerceIn(0,5) }
        }) {
            val left=44.dp.toPx(); val bottom=size.height-28.dp.toPx(); val top=12.dp.toPx()
            val height=bottom-top; val width=(size.width-left-8.dp.toPx())/6
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink.toArgb(); textSize=11.sp.toPx() }
            for(i in 0..3) {
                val y=bottom-height*i/3
                drawLine(grid,Offset(left,y),Offset(size.width,y),strokeWidth=1.dp.toPx())
                val yuan=ceiling*i/3/100
                val label=when { yuan>=1e12 -> "%.1f万亿".format(Locale.ROOT,yuan/1e12); yuan>=1e8 -> "%.1f亿".format(Locale.ROOT,yuan/1e8); yuan>=10000 -> "%.1f万".format(Locale.ROOT,yuan/10000); yuan>=1000 -> "%.1f千".format(Locale.ROOT,yuan/1000); yuan==floor(yuan)->yuan.toInt().toString(); else->"%.2f".format(Locale.ROOT,yuan) }
                drawContext.canvas.nativeCanvas.drawText(label,0f,y+4.dp.toPx(),paint)
            }
            for(i in 0..5) {
                val x=left+i*width
                if(selected==i) drawRoundRect(highlight,Offset(x+2.dp.toPx(),top),Size(width-4.dp.toPx(),height),CornerRadius(7.dp.toPx()))
                val bar=width*.22f
                val a=(values[i].income/ceiling*height).toFloat()*motion.value; val b=(values[i].expense/ceiling*height).toFloat()*motion.value
                if(a>0) drawRoundRect(income,Offset(x+width*.22f,bottom-a),Size(bar,a),CornerRadius(3.dp.toPx()))
                if(b>0) drawRoundRect(expense,Offset(x+width*.55f,bottom-b),Size(bar,b),CornerRadius(3.dp.toPx()))
                paint.textAlign=Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText("${months[i].monthValue}月",x+width/2,bottom+20.dp.toPx(),paint)
                paint.textAlign=Paint.Align.LEFT
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("${months[selected].monthValue} 月",style=MaterialTheme.typography.titleMedium)
            TextButton({onDetails(months[selected])}) { Text("查看明细") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Subtitle("收入"); Money(values[selected].income,size=20,color=income) }
            Column(Modifier.weight(1f)) { Subtitle("支出"); Money(values[selected].expense,size=20) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            months.indices.forEach { i -> TextButton({selected=i},contentPadding=PaddingValues(2.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {
                this.selected=selected==i
                contentDescription="选择${months[i]}，收入${values[i].income.money()}元，支出${values[i].expense.money()}元"
            }) { Text("${months[i].monthValue}月",fontSize=12.sp) } }
        }
    }
}

@Composable private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
