@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.traintrip.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import cn.traintrip.app.UiState
import cn.traintrip.core.*
import java.time.LocalDate

@Composable fun FilterSheet(kind:String,s:UiState,onDismiss:()->Unit,onApply:(SearchFilters)->Unit) {
    var draft by remember(kind) { mutableStateOf(s.filters) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var startText by remember { mutableStateOf(timeText(draft.startMinute)) }
    var endText by remember { mutableStateOf(timeText(draft.endMinute)) }
    var durationText by remember { mutableStateOf(draft.maxMinutes?.toString() ?: "") }
    var range by remember { mutableStateOf(draft.startDate != draft.endDate) }
    val context=LocalContext.current
    fun pickDate(current:LocalDate,onPick:(LocalDate)->Unit) {
        DatePickerDialog(context,{_,y,m,d->onPick(LocalDate.of(y,m+1,d))},current.year,current.monthValue-1,current.dayOfMonth).show()
    }
    val title=mapOf("origin" to "出发城市与车站","dates" to "选择出发日期","time" to "出发时段","seats" to "可接受的席别","people" to "几个人出发","duration" to "最长车程","scope" to "查询目的地")[kind].orEmpty()
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Cream) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=20.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                when(kind) {
                    "dates" -> {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Choice("单日",!range,{range=false;draft=draft.copy(endDate=draft.startDate)},Modifier.weight(1f))
                            Choice("日期范围",range,{range=true},Modifier.weight(1f))
                        }
                        SecondaryButton("开始：${draft.startDate}",{pickDate(draft.startDate) { d->draft=draft.copy(startDate=d,endDate=if(!range || draft.endDate<d)d else draft.endDate) }})
                        if(range) SecondaryButton("结束：${draft.endDate}",{pickDate(draft.endDate) { d->draft=draft.copy(endDate=d) }})
                        Text("按你的上车日期查询，范围两端均包含。",style=MaterialTheme.typography.bodyMedium,color=Muted)
                        s.sourceInfo?.let { Text("当前已开售范围：${it.saleStart} 至 ${it.saleEnd}",style=MaterialTheme.typography.bodySmall,color=Muted) }
                    }
                    "time" -> {
                        Text("北京时间 · 所选日期每天适用",color=Muted)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(startText,{startText=it},Modifier.weight(1f),label={Text("开始 HH:mm")},singleLine=true)
                            OutlinedTextField(endText,{endText=it},Modifier.weight(1f),label={Text("结束 HH:mm")},singleLine=true)
                        }
                        Text("结束早于开始时按跨午夜筛选。例如 22:00–06:00 包含晚上十点后和早上六点前。",style=MaterialTheme.typography.bodyMedium,color=Muted)
                        SecondaryButton("恢复全天 00:00–24:00",{startText="00:00";endText="24:00"})
                    }
                    "seats" -> {
                        CheckRow("不限（含无座）",draft.seats.size==SeatType.entries.size) { draft=draft.copy(seats=SeatType.entries.toSet()) }
                        SeatType.entries.forEach { seat->CheckRow(seat.label,draft.seats.size!=SeatType.entries.size && seat in draft.seats) { selected->
                            val base=if(draft.seats.size==SeatType.entries.size) emptySet() else draft.seats
                            draft=draft.copy(seats=if(selected || draft.seats.size==SeatType.entries.size) base+seat else base-seat)
                        } }
                        Text("候补不计入有票结果。多人需要同一车次、同一席别的数量足够。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    "people" -> {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            OutlinedButton({draft=draft.copy(people=(draft.people-1).coerceAtLeast(1))},enabled=draft.people>1) { Text("−") }
                            Text("${draft.people} 位成人",style=MaterialTheme.typography.titleLarge)
                            OutlinedButton({draft=draft.copy(people=(draft.people+1).coerceAtMost(20))},enabled=draft.people<20) { Text("＋") }
                        }
                        Text("多人出行时，需同一车次、同一席别余票足够；“有”但未公布张数的车次会单独提示。",color=Muted)
                    }
                    "duration" -> {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("不限" to "","4 小时" to "240","8 小时" to "480").forEach { (label,value)->Choice(label,durationText==value,{durationText=value},Modifier.weight(1f)) }
                        }
                        OutlinedTextField(durationText,{durationText=it},Modifier.fillMaxWidth(),label={Text("自定义分钟数，留空为不限")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                    }
                    "origin" -> {
                        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("搜索出发城市")},singleLine=true)
                        if(search.isNotBlank()) s.catalog.cities.filter { it.name.contains(search) || it.representative.pinyin.contains(search,true) }.take(30).forEach { city->
                            TextButton({draft=draft.copy(originCityId=city.id,originStations=emptySet(),destinationCityIds=draft.destinationCityIds-city.id);search=""},Modifier.fillMaxWidth()) { Text(city.name) }
                        }
                        Text("${s.catalog.byCity[draft.originCityId]?.name} · 出发车站",style=MaterialTheme.typography.titleMedium)
                        CheckRow("全部同城车站",draft.originStations.isEmpty()) {draft=draft.copy(originStations=emptySet())}
                        s.catalog.byCity[draft.originCityId]?.stations?.forEach { station->CheckRow(station.name,station.code in draft.originStations) { selected->draft=draft.copy(originStations=if(selected) draft.originStations+station.code else draft.originStations-station.code) } }
                        Text("车站字典可能包含非当日运营站；是否有班次以实际查询为准。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    "scope" -> {
                        Hint("首版按城市代表站逐个查询，同城覆盖尚未全面验证。这里的列表不是已确认的全国直达目的地全集。")
                        Text("已选 ${draft.destinationCityIds.size} 个城市",style=MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton({draft=draft.copy(destinationCityIds=s.catalog.initialDestinations(draft.originCityId))}) { Text("恢复默认范围") }
                            TextButton({draft=draft.copy(destinationCityIds=emptySet())}) { Text("清空选择") }
                        }
                        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("搜索目的地城市")},singleLine=true)
                        s.catalog.cities.filter { it.id!=draft.originCityId && (search.isBlank() || it.name.contains(search) || it.representative.pinyin.contains(search,true)) }
                            .sortedWith(compareBy<City> { it.id !in draft.destinationCityIds }.thenBy { it.name }).forEach { city->
                                CheckRow(city.name,city.id in draft.destinationCityIds) { selected->draft=draft.copy(destinationCityIds=if(selected) draft.destinationCityIds+city.id else draft.destinationCityIds-city.id) }
                            }
                    }
                }
            }
            error?.let { Text(it,color=Amber) }
            PrimaryButton("完成",{
                var candidate=draft
                if(kind=="time") {
                    val start=parseMinute(startText,false);val end=parseMinute(endText,true)
                    if(start==null || end==null || start==end) {error="请按 HH:mm 输入有效时段，开始和结束不能相同";return@PrimaryButton}
                    candidate=candidate.copy(startMinute=start,endMinute=end)
                }
                if(kind=="duration") {
                    val n=durationText.toIntOrNull()
                    if(durationText.isNotBlank() && (n==null || n<=0 || n>6000)) {error="请填写 1–6000 分钟，或留空为不限";return@PrimaryButton}
                    candidate=candidate.copy(maxMinutes=n)
                }
                val validation=candidate.validate() ?: if(candidate.destinationCityIds.isEmpty()) "请至少选择一个目的地城市" else null
                if(validation!=null) error=validation else onApply(candidate)
            })
        }
    }
}
@Composable private fun CheckRow(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { onChange(!checked) },verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked,onCheckedChange=onChange);Text(label,Modifier.weight(1f))
    }
}
private fun parseMinute(s:String,allow24:Boolean):Int? {
    if(allow24 && s=="24:00") return 1440
    if(!s.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))) return null
    return s.substringBefore(':').toInt()*60+s.substringAfter(':').toInt()
}
