@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*
import java.time.LocalDate

@Composable fun DetailScreen(s:UiState,onBack:()->Unit,onSelect:(Trip,SeatType)->Unit,onCheck:()->Unit,onCopy:(String)->Unit) {
    val f=s.applied ?: s.filters
    val all=remember(s.progress,f,s.cityId) { s.progress?.trips.orEmpty().filter { it.to.cityId==s.cityId && it.confirmed(f) }.distinctBy { it.key } }
    var selectedDate by remember(s.cityId) { mutableStateOf<LocalDate?>(null) }
    var station by remember(s.cityId) { mutableStateOf<String?>(null) }
    var shortest by remember(s.cityId) { mutableStateOf(false) }
    var stationsOpen by remember { mutableStateOf(false) }
    var seatTrip by remember { mutableStateOf<Trip?>(null) }
    val trips=all.filter { (selectedDate==null || it.date==selectedDate) && (station==null || it.to.code==station) }.let { list->if(shortest) list.sortedBy { it.durationMinutes } else list.sortedWith(compareBy<Trip> { it.date }.thenBy { it.departure }) }
    val selected=s.progress?.trips?.firstOrNull { it.key==s.selectedTripKey }
    Scaffold(containerColor=Cream,bottomBar={
        Surface(color=Cream,shadowElevation=3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(selected!=null) "已选 ${selected.trainCode} · ${s.selectedSeat?.label} · ${selected.date} ${selected.departure}" else "选择车次和席别后核验余票",style=MaterialTheme.typography.bodySmall,color=Muted)
                PrimaryButton(if(s.rechecking) "正在核验余票…" else "核验余票并去 12306",onCheck,selected!=null && s.selectedSeat!=null && !s.rechecking)
                if(selected!=null && s.selectedSeat!=null) TextButton({onCopy(itinerary(selected,s.selectedSeat,f.people))},Modifier.fillMaxWidth()) { Text("复制车次信息") }
            }
        }
    }) { padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { BackHeader("${s.catalog.byCity[f.originCityId]?.name}出发 · 只看直达",onBack) }
            item { Text("去${s.catalog.byCity[s.cityId]?.name.orEmpty()}",style=MaterialTheme.typography.headlineLarge);Text(s.catalog.byCity[s.cityId]?.provinceLabel.orEmpty(),color=Muted,style=MaterialTheme.typography.bodyMedium);Text("${all.map { it.trainKey }.distinct().size} 趟车次 · 余票随时变化",color=Muted,style=MaterialTheme.typography.bodyMedium) }
            item {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Choice("全部日期",selectedDate==null,{selectedDate=null})
                    f.dates().forEach { d->Choice(dateLabel(d),selectedDate==d,{selectedDate=d}) }
                }
            }
            item { Row(verticalAlignment=Alignment.CenterVertically) { TextButton({shortest=!shortest},Modifier.weight(1f)) { Text(if(shortest) "车程最短 ↑" else "出发时间 ↑") };TextButton({stationsOpen=true}) { Text(station?.let { s.catalog.byCode[it]?.name } ?: "到达站筛选") } } }
            if(trips.isEmpty()) item { Hint("当前没有符合筛选条件的车次，请调整日期或到达站。") }
            items(trips,key={it.key}) { t->
                ContentCard(Modifier.fillMaxWidth(),selected=t.key==s.selectedTripKey) {
                    Row { Text(t.trainCode,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text("直达",style=MaterialTheme.typography.bodySmall,color=Muted) }
                    TripTiming(t)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        f.seats.filter { t.seats[it]?.confirmedFor(f.people)==true }.sortedBy { it.ordinal }.forEach { seat->
                            Choice("${seat.label} ${t.seats.getValue(seat).label()}",s.selectedTripKey==t.key && s.selectedSeat==seat,{onSelect(t,seat)})
                        }
                    }
                    TextButton({seatTrip=t},contentPadding=PaddingValues(0.dp)) { Text("更多席别与余票 ›",style=MaterialTheme.typography.bodySmall) }
                    Text("${t.date} · ${formatTime(t.queriedAt)} 查询",style=MaterialTheme.typography.bodySmall,color=Muted)
                }
            }
            item { Text("登录、购票和支付在 12306 完成。打开官方查询入口后可使用已复制的行程手动查询。",style=MaterialTheme.typography.bodySmall,color=Muted) }
        }
    }
    if(stationsOpen) AlertDialog(onDismissRequest={stationsOpen=false},title={Text("到达车站")},text={Column {
        TextButton({station=null;stationsOpen=false}) { Text("全部到达站") }
        all.map { it.to }.distinctBy { it.code }.forEach { st->TextButton({station=st.code;stationsOpen=false}) { Text(st.name) } }
    }},confirmButton={TextButton({stationsOpen=false}) { Text("关闭") }})
    seatTrip?.let { t->AlertDialog(onDismissRequest={seatTrip=null},title={Text("${t.trainCode} · 席别余票")},text={
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("${t.date} ${t.departure}\n${t.from.name} → ${t.to.name}",style=MaterialTheme.typography.bodyMedium) }
            items(SeatType.entries) { seat->Row { Text(seat.label,Modifier.weight(1f));Text(t.seats[seat]?.label() ?: "未提供",color=Muted) } }
            item { Text("查询时间：${formatTime(t.queriedAt)}\n“有”表示有票，不显示具体张数。候补资格请在 12306 确认。${if(t.waitlistTrainFlag) "\n票源标记该车支持候补，具体席别以官方显示为准。" else ""}",style=MaterialTheme.typography.bodySmall,color=Muted) }
        }
    },confirmButton={TextButton({seatTrip=null}) { Text("知道了") }}) }
}
