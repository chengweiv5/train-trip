@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import cn.traintrip.app.UiState
import cn.traintrip.core.*

@Composable fun FilterSheet(kind:String,s:UiState,onDismiss:()->Unit,onApply:(SearchFilters)->Unit) {
    if(kind=="scope") { DestinationSelector(s,onDismiss,onApply); return }
    if(kind=="dates") {
        DateFilterSheet(s,onDismiss,onApply)
        return
    }
    if(kind=="time") {
        TimeFilterSheet(s.filters,onDismiss,onApply)
        return
    }
    var draft by remember(kind) { mutableStateOf(s.filters) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var durationText by remember { mutableStateOf(draft.maxMinutes?.toString() ?: "") }
    val title=mapOf("origin" to "出发城市与车站","dates" to "选择出发日期","time" to "出发时段","seats" to "选择席别","people" to "几个人出发","duration" to "最长车程","scope" to "查询目的地")[kind].orEmpty()
    FilterPanel(title,onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                when(kind) {
                    "seats" -> {
                        CheckRow("不限（含无座）",draft.seats.size==SeatType.entries.size) { draft=draft.copy(seats=SeatType.entries.toSet()) }
                        SeatType.entries.forEach { seat->CheckRow(seat.label,draft.seats.size!=SeatType.entries.size && seat in draft.seats) { selected->
                            val base=if(draft.seats.size==SeatType.entries.size) emptySet() else draft.seats
                            draft=draft.copy(seats=if(selected || draft.seats.size==SeatType.entries.size) base+seat else base-seat)
                        } }
                        Text("候补不计入有票结果。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    "people" -> {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            OutlinedButton({draft=draft.copy(people=(draft.people-1).coerceAtLeast(1))},enabled=draft.people>1) { Text("−") }
                            Text("${draft.people} 位成人",style=MaterialTheme.typography.titleLarge)
                            OutlinedButton({draft=draft.copy(people=(draft.people+1).coerceAtMost(20))},enabled=draft.people<20) { Text("＋") }
                        }
                        Text("多人出行按同一车次、同一席别匹配。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    "duration" -> {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("不限" to "","4 小时" to "240","8 小时" to "480").forEach { (label,value)->Choice(label,durationText==value,{durationText=value},Modifier.weight(1f)) }
                        }
                        OutlinedTextField(durationText,{durationText=it},Modifier.fillMaxWidth(),label={Text("自定义分钟数，留空为不限")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                    }
                    "origin" -> {
                        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("搜索出发城市")},singleLine=true)
                        if(search.isNotBlank()) s.catalog.cities.filter { it.supported && (it.matches(search) || it.province.matches(search)) }.take(30).forEach { city->
                            TextButton({draft=draft.copy(originCityId=city.id,originStations=emptySet(),destinationCityIds=draft.destinationCityIds-city.id);search=""},Modifier.fillMaxWidth()) { Text(city.name) }
                        }
                        Text("${s.catalog.byCity[draft.originCityId]?.name} · 出发车站",style=MaterialTheme.typography.titleMedium)
                        CheckRow("全部同城车站",draft.originStations.isEmpty()) {draft=draft.copy(originStations=emptySet())}
                        s.catalog.byCity[draft.originCityId]?.stations?.forEach { station->CheckRow(station.name,station.code in draft.originStations) { selected->draft=draft.copy(originStations=if(selected) draft.originStations+station.code else draft.originStations-station.code) } }
                    }

                }
            }
            error?.let { Text(it,color=Amber) }
            PrimaryButton("完成",{
                var candidate=draft
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
