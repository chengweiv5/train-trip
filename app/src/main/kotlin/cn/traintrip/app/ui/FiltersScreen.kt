package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.traintrip.app.UiState
import cn.traintrip.core.*

@Composable fun FiltersScreen(s:UiState,onUpdate:(SearchFilters)->Unit,onSearch:()->Unit) {
    var sheet by remember { mutableStateOf<String?>(null) }
    val destinationBrowser=rememberSaveable(saver=DestinationBrowserState.Saver) { DestinationBrowserState() }
    val f=s.filters
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=12.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("▧  TRAIN TRIP",style=MaterialTheme.typography.labelSmall,color=Forest)
            Text("有票，就出发。",style=MaterialTheme.typography.headlineLarge)
            Text("先看看能去哪，再决定去哪。",style=MaterialTheme.typography.bodyMedium,color=Muted)
        }
        ContentCard(Modifier.fillMaxWidth(),onClick={sheet="origin"}) {
            Text("从哪里出发",style=MaterialTheme.typography.bodySmall,color=Muted)
            Row(verticalAlignment=Alignment.CenterVertically) { Text(s.catalog.byCity[f.originCityId]?.name ?: "北京",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);Text("›",color=Muted) }
            Text(if(f.originStations.isEmpty()) "同城各站 · 可指定出发站" else f.originStations.mapNotNull { s.catalog.byCode[it]?.name }.joinToString("、"),style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SectionTitle("什么时候出发")
            ContentCard(Modifier.fillMaxWidth(),onClick={sheet="dates"}) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(dateLabel(f.startDate),style=MaterialTheme.typography.titleMedium)
                    Text("→",color=Muted)
                    Text(dateLabel(f.endDate),style=MaterialTheme.typography.titleMedium)
                }
                Text(if(f.startDate==f.endDate) "单日出发" else "连续日期 · 两端均包含",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            SectionTitle("几点出发","自定义") { sheet="time" }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(Triple("全天",0,1440),Triple("早上",360,720),Triple("下午",720,1080),Triple("晚上",1080,1440)).forEach { (name,start,end) ->
                    Choice(name,f.startMinute==start && f.endMinute==end,{onUpdate(f.copy(startMinute=start,endMinute=end))},Modifier.weight(1f))
                }
            }
            Text("${timeText(f.startMinute)}–${timeText(f.endMinute)} · 所选日期每天适用",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        Column {
            FilterRow("席别",if(f.seats.size==SeatType.entries.size) "不限，含无座" else "已选 ${f.seats.size} 类") { sheet="seats" }
            FilterRow("乘车人数","${f.people} 人") { sheet="people" }
            FilterRow("最长车程",f.maxMinutes?.let { durationText(it) } ?: "不限") { sheet="duration" }
            FilterRow("查询目的地","${f.destinationCityIds.size} 个城市 · ${f.destinationCityIds.mapNotNull { s.catalog.byCity[it]?.province?.id }.distinct().size} 个省级地区") { sheet="scope" }
        }
        Text("首版按所选城市查询；可能未覆盖全部目的地。可在“查询目的地”中查看和调整范围。",style=MaterialTheme.typography.bodySmall,color=Muted)
        s.error?.let { Hint(it,true) }
        PrimaryButton("找找有票的城市  →",onSearch)
        Text("只看直达去程 · 购票在 12306 完成",style=MaterialTheme.typography.bodySmall,color=Muted)
    }
    sheet?.let { key ->
        if(key=="scope") DestinationSelector(s,{sheet=null},{onUpdate(it);sheet=null},destinationBrowser)
        else FilterSheet(key,s,onDismiss={sheet=null},onApply={onUpdate(it);sheet=null})
    }
}
@Composable private fun FilterRow(title:String,value:String,onClick:()->Unit) {
    TextButton(onClick,Modifier.fillMaxWidth().heightIn(min=56.dp),contentPadding=PaddingValues(0.dp)) {
        Text(title,Modifier.weight(1f),color=Ink);Text(value,color=Muted);Spacer(Modifier.width(10.dp));Text("›",color=Muted)
    }
    HorizontalDivider(color=Line)
}
