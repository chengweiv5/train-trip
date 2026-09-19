package cn.traintrip.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*

@Composable internal fun ActionIcon(kind:String,color:Color) {
    Canvas(Modifier.size(20.dp)) {
        val w=size.width; val h=size.height; val stroke=Stroke(1.7.dp.toPx(),cap=StrokeCap.Round)
        when(kind) {
            "phone" -> {
                drawRoundRect(color,Offset(w*.24f,h*.08f),Size(w*.52f,h*.84f),CornerRadius(w*.08f),style=stroke)
                drawLine(color,Offset(w*.43f,h*.76f),Offset(w*.57f,h*.76f),stroke.width,StrokeCap.Round)
            }
            else -> {
                drawLine(color,Offset(w*.15f,h*.5f),Offset(w*.4f,h*.75f),stroke.width,StrokeCap.Round)
                drawLine(color,Offset(w*.4f,h*.75f),Offset(w*.86f,h*.23f),stroke.width,StrokeCap.Round)
            }
        }
    }
}

@Composable internal fun SeatAvailabilityLabel(text:String) {
    Text(text,Modifier.padding(horizontal=8.dp,vertical=6.dp),color=Forest,style=MaterialTheme.typography.bodyMedium)
}

@Composable internal fun DetailActions(selected:Trip?,people:Int,notice:String?,onOpenApp:()->Unit) {
    val fontScale=LocalDensity.current.fontScale
    Surface(color=Color.White) {
        Column {
            HorizontalDivider(color=Line)
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(selected!=null) Column(Modifier.fillMaxWidth().testTag("selected-summary"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("${selected.trainCode} · ${selected.date.monthValue}月${selected.date.dayOfMonth}日",style=MaterialTheme.typography.titleSmall,color=Forest)
                    val arrivalDay=selected.arrivalDayOffset?.takeIf { it>0 }?.let { "（+$it 天）" }.orEmpty()
                    Text("${selected.from.name} ${selected.departure} → ${selected.to.name} ${selected.arrival}$arrivalDay · ${people}位成人",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
                notice?.let { Text(it,Modifier.fillMaxWidth().testTag("detail-notice").semantics { liveRegion=LiveRegionMode.Polite },style=MaterialTheme.typography.bodySmall,color=Amber) }
                Button(onOpenApp,Modifier.fillMaxWidth().heightIn(min=if(fontScale>1.15f) 60.dp else 56.dp).testTag("open-12306"),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(12.dp)) {
                    ActionIcon("phone",LocalContentColor.current);Spacer(Modifier.width(8.dp));Text("打开 12306 App")
                }
                Text("在 12306 App 内填写条件并购票",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
    }
}

@Composable internal fun CityRefreshStatus(s:UiState,onRefresh:()->Unit,onRetry:()->Unit) {
    val cityPlan=s.progress?.plan.orEmpty().filter { it.destination.cityId==s.cityId }
    val data=s.progress?.successfulData.orEmpty().filterKeys { key->cityPlan.any { it.key==key } }
    val times=data.values.map { it.receivedAt }.distinct().sorted()
    val refresh=s.cityRefresh
    val failed=cityPlan.filter { s.progress?.outcomes?.get(it.key) is QueryResult.Failure || s.progress?.outcomes?.get(it.key) is QueryResult.NotOnSale }
    Column(Modifier.semantics { liveRegion=LiveRegionMode.Polite },verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(when {
            times.isEmpty()->"尚无成功查询记录"
            times.size==1->"上次成功更新 ${formatTime(times.first())}"
            else->"各项更新时间 ${formatTime(times.first())} — ${formatTime(times.last())}"
        },style=MaterialTheme.typography.bodySmall,color=Muted)
        TextButton(onRefresh,Modifier.heightIn(min=48.dp).testTag("refresh-tickets"),enabled=refresh?.running!=true,contentPadding=PaddingValues(horizontal=0.dp)) {
            Text(if(refresh?.running==true) "正在刷新余票…" else "刷新余票")
        }
        if(refresh?.running==true) Text("已完成 ${refresh.outcomes.size} / ${refresh.plan.size}",style=MaterialTheme.typography.bodySmall,color=Muted)
        if(failed.isNotEmpty() || refresh?.stopped==true) {
            Text(if(refresh?.stopped==true) "刷新已停止 · 保留已有结果" else "余票未更新 · ${failed.size} / ${cityPlan.size} 项未成功",color=Amber,style=MaterialTheme.typography.bodySmall)
            failed.forEach { unit->
                val result=s.progress?.outcomes?.get(unit.key)
                val reason=when(result) { is QueryResult.Failure->result.message;is QueryResult.NotOnSale->result.message;else->"" }
                Text("${unit.date} · ${unit.origin.name} → ${unit.destination.name}：$reason",color=Muted,style=MaterialTheme.typography.bodySmall)
            }
            if(refresh!=null) TextButton(onRetry,enabled=!refresh.running,modifier=Modifier.heightIn(min=48.dp)) { Text("重试刷新") }
        }
    }
}
