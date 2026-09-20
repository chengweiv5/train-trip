package cn.traintrip.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.traintrip.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun PrimaryButton(text:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier) {
    Button(onClick,modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(12.dp)) { Text(text) }
}
@Composable fun SecondaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    OutlinedButton(onClick,modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,Line),contentPadding=PaddingValues(14.dp)) { Text(text) }
}
@Composable fun ContentCard(modifier:Modifier=Modifier,selected:Boolean=false,onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.then(if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier),shape=RoundedCornerShape(12.dp),color=CardBackground,border=if(selected) BorderStroke(1.5.dp,Primary) else BorderStroke(1.dp,CardBorder)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun SectionTitle(text:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(text,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);if(action!=null) TextButton(onAction) { Text(action) } }
}
@Composable fun Hint(text:String,warning:Boolean=false,modifier:Modifier=Modifier) {
    Surface(modifier.fillMaxWidth(),color=if(warning) AmberBg else PrimaryTint,shape=RoundedCornerShape(8.dp)) { Text(text,Modifier.padding(14.dp),style=MaterialTheme.typography.bodyMedium,color=if(warning) Amber else Primary) }
}
@Composable fun Choice(text:String,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,contentPadding:PaddingValues=PaddingValues(12.dp),maxLines:Int=Int.MAX_VALUE) {
    Surface(modifier.heightIn(min=48.dp).clickable(onClick=onClick),shape=RoundedCornerShape(8.dp),color=if(selected) PrimaryTint else ControlBackground,border=BorderStroke(1.dp,if(selected) Primary else Line)) {
        Box(Modifier.padding(contentPadding),contentAlignment=Alignment.Center) { Text(text,color=if(selected) Primary else Muted,style=MaterialTheme.typography.bodyMedium,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,maxLines=maxLines) }
    }
}
@Composable fun BackHeader(text:String,onBack:()->Unit) = AppTopBar(text,onBack)

fun dateLabel(d:LocalDate):String=d.format(DateTimeFormatter.ofPattern("M月d日"))
fun dateRange(f:SearchFilters):String=if(f.startDate==f.endDate) dateLabel(f.startDate) else "${dateLabel(f.startDate)}–${dateLabel(f.endDate)}"
fun timeText(m:Int):String="%02d:%02d".format(m/60,m%60)
fun timeRange(f:SearchFilters):String=if(f.startMinute==0 && f.endMinute==1440) "全天出发" else "${timeText(f.startMinute)}–${timeText(f.endMinute)}"
fun durationText(minutes:Int?):String=if(minutes==null) "时刻待定" else if(minutes<60) "$minutes 分钟" else "${minutes/60} 小时${if(minutes%60==0) "" else " ${minutes%60} 分"}"
fun seatSummary(f:SearchFilters):String=if(f.seats.size==SeatType.entries.size) "不限，含无座" else f.seats.sortedBy { it.ordinal }.joinToString("、") { it.label }

@Composable fun SearchProgressBar(progress:Float,modifier:Modifier=Modifier) {
    LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).testTag("query-progress"),
        color=Primary,trackColor=Line,strokeCap=StrokeCap.Butt,gapSize=0.dp,drawStopIndicator={})
}

@Composable fun TripTiming(trip:Trip,modifier:Modifier=Modifier) {
    val density=LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked=maxWidth<300.dp || density.fontScale>1.15f
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(trip.departure?.toString() ?: "--:--",fontSize=28.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Text(trip.from.name,style=MaterialTheme.typography.bodySmall,color=Muted)
                }
                if(!stacked) JourneyInfo(trip,Modifier.weight(1.25f))
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                    Text(trip.arrival?.toString() ?: "--:--",fontSize=28.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Text(trip.to.name,style=MaterialTheme.typography.bodySmall,color=Muted,textAlign=TextAlign.End)
                }
            }
            if(stacked) {
                HorizontalDivider(color=Line)
                Text("${compactDuration(trip.durationMinutes)} · ${arrivalLabel(trip)}",Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=Muted,textAlign=TextAlign.Center)
            }
        }
    }
}

@Composable private fun JourneyInfo(trip:Trip,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(compactDuration(trip.durationMinutes),Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=Muted,textAlign=TextAlign.Center)
        HorizontalDivider(color=Line)
        Text(arrivalLabel(trip),Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=Muted,textAlign=TextAlign.Center)
    }
}
private fun compactDuration(minutes:Int?)=when {
    minutes==null->"历时待定"
    minutes<60->"${minutes}分钟"
    minutes%60==0->"${minutes/60}小时"
    else->"${minutes/60}小时${minutes%60}分"
}
private fun arrivalLabel(trip:Trip)=when(val offset=trip.arrivalDayOffset) { null->"到达日待定";0->"当日到达";else->"+$offset 天到达" }
