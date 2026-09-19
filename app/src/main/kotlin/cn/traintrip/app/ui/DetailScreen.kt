@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*
import java.time.LocalDate

@Composable fun DetailScreen(s:UiState,onBack:()->Unit,onSelect:(Trip,SeatType)->Unit,onRefresh:()->Unit,onRetry:()->Unit,onClearSelection:()->Unit,onOpenApp:()->Unit) {
    val f=s.applied ?: s.filters
    val all=remember(s.progress,f,s.cityId) { s.progress?.trips.orEmpty().filter { it.to.cityId==s.cityId && it.confirmed(f) }.distinctBy { it.key } }
    var dateKey by rememberSaveable(s.cityId,s.searchSession) { mutableStateOf<String?>(null) }
    val selectedDate=dateKey?.let(LocalDate::parse)
    var station by rememberSaveable(s.cityId,s.searchSession) { mutableStateOf<String?>(null) }
    var shortest by rememberSaveable(s.cityId,s.searchSession) { mutableStateOf(false) }
    var stationsOpen by remember { mutableStateOf(false) }
    var seatTrip by remember { mutableStateOf<Trip?>(null) }
    val trips=all.filter { (selectedDate==null || it.date==selectedDate) && (station==null || it.to.code==station) }.let { list->if(shortest) list.sortedBy { it.durationMinutes } else list.sortedWith(compareBy<Trip> { it.date }.thenBy { it.departure }) }
    val selected=trips.firstOrNull { it.key==s.selectedTripKey }
    val listState=rememberLazyListState()
    val compact=LocalDensity.current.fontScale>1.15f
    fun changeDate(date:LocalDate?) {
        if(selected!=null && date!=null && selected.date!=date) onClearSelection()
        dateKey=date?.toString()
    }
    fun changeStation(code:String?) {
        if(selected!=null && code!=null && selected.to.code!=code) onClearSelection()
        station=code;stationsOpen=false
    }
    Scaffold(containerColor=Cream,bottomBar={
        DetailActions(selected,s.selectedSeat,f.people,s.notice,onOpenApp)
    }) { padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("detail-list"),state=listState,contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { BackHeader("${s.catalog.byCity[f.originCityId]?.name}出发 · 只看直达",onBack) }
            item { Text("去${s.catalog.byCity[s.cityId]?.name.orEmpty()}",style=MaterialTheme.typography.headlineLarge);Text(s.catalog.byCity[s.cityId]?.provinceLabel.orEmpty(),color=Muted,style=MaterialTheme.typography.bodyMedium);Text("${all.map { it.trainKey }.distinct().size} 趟车次 · 余票随时变化",color=Muted,style=MaterialTheme.typography.bodyMedium) }
            item { CityRefreshStatus(s,onRefresh,onRetry) }
            item { Text("点选席别，底部查看已选车次",style=MaterialTheme.typography.bodyMedium,color=Forest) }
            item {
                BoxWithConstraints {
                  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),maxItemsInEachRow=if(compact || maxWidth<300.dp) 1 else Int.MAX_VALUE) {
                    Choice("全部日期",selectedDate==null,{changeDate(null)})
                    f.dates().forEach { d->Choice(dateLabel(d),selectedDate==d,{changeDate(d)}) }
                  }
                }
            }
            item {
                BoxWithConstraints {
                    val sort:@Composable ()->Unit={ TextButton({shortest=!shortest},Modifier.heightIn(min=48.dp)) { Text(if(shortest) "车程最短 ↑" else "出发时间 ↑") } }
                    val filter:@Composable ()->Unit={ TextButton({stationsOpen=true},Modifier.heightIn(min=48.dp)) { Text(station?.let { s.catalog.byCode[it]?.name } ?: "到达站筛选") } }
                    if(compact || maxWidth<300.dp) Column { sort();filter() }
                    else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { sort();filter() }
                }
            }
            if(trips.isEmpty()) item { Hint("当前没有符合筛选条件的车次，请调整日期或到达站。") }
            items(trips,key={it.key}) { t->
                Surface(Modifier.fillMaxWidth().testTag("trip-${t.key}"),shape=RoundedCornerShape(18.dp),color=if(t.key==s.selectedTripKey) Sage else Color.White,border=if(t.key==s.selectedTripKey) BorderStroke(1.5.dp,Forest) else null) {
                  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row { Text(t.trainCode,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text(if(t.key==s.selectedTripKey) "已选择" else "直达",style=MaterialTheme.typography.bodySmall,color=Muted) }
                    TripTiming(t)
                    BoxWithConstraints {
                      FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),maxItemsInEachRow=if(compact || maxWidth<260.dp) 1 else Int.MAX_VALUE) {
                        f.seats.filter { t.seats[it]?.confirmedFor(f.people)==true }.sortedBy { it.ordinal }.forEach { seat->
                            SeatChoice("${seat.label} ${t.seats.getValue(seat).label()}",s.selectedTripKey==t.key && s.selectedSeat==seat,{onSelect(t,seat)})
                        }
                    }
                    }
                    TextButton({seatTrip=t},contentPadding=PaddingValues(0.dp)) { Text("更多席别与余票 ›",style=MaterialTheme.typography.bodySmall) }
                    Text("${t.date} · ${formatTime(t.queriedAt)} 查询",style=MaterialTheme.typography.bodySmall,color=Muted)
                  }
                }
            }
            item { Text("余票随时变化，以 12306 实时结果为准。",style=MaterialTheme.typography.bodySmall,color=Muted) }
        }
    }
    if(stationsOpen) AlertDialog(onDismissRequest={stationsOpen=false},title={Text("到达车站")},text={Column {
        TextButton({changeStation(null)}) { Text("全部到达站") }
        all.map { it.to }.distinctBy { it.code }.forEach { st->TextButton({changeStation(st.code)}) { Text(st.name) } }
    }},confirmButton={TextButton({stationsOpen=false}) { Text("关闭") }})
    seatTrip?.let { t->AlertDialog(onDismissRequest={seatTrip=null},title={Text("${t.trainCode} · 席别余票")},text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("${t.date} ${t.departure}\n${t.from.name} → ${t.to.name}",style=MaterialTheme.typography.bodyMedium) }
            items(SeatType.entries) { seat->Row { Text(seat.label,Modifier.weight(1f));Text(t.seats[seat]?.label() ?: "未提供",color=Muted) } }
            item { Text("查询时间：${formatTime(t.queriedAt)}\n“有”表示有票，不显示具体张数。候补资格请在 12306 确认。${if(t.waitlistTrainFlag) "\n票源标记该车支持候补，具体席别以官方显示为准。" else ""}",style=MaterialTheme.typography.bodySmall,color=Muted) }
        }
    },confirmButton={TextButton({seatTrip=null}) { Text("知道了") }}) }
}
