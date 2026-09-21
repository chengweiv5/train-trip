package cn.traintrip.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.traintrip.app.UiState
import cn.traintrip.app.WishlistState
import cn.traintrip.core.*
import java.time.format.DateTimeFormatter

@Composable fun FiltersScreen(s:UiState,onUpdate:(SearchFilters)->Unit,onSearch:()->Unit,onContentSettings:()->Unit={},onBack:(()->Unit)?=null,wishlist:WishlistState=WishlistState(loading=false),onReloadWishlist:()->Unit={}) {
    var sheet by remember { mutableStateOf<String?>(null) }
    val destinationBrowser=rememberSaveable(saver=DestinationBrowserState.Saver) { DestinationBrowserState() }
    val f=s.filters
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        if(s.queryCityId==null) HomeBrandHeader(onContentSettings)
        else AppTopBar("去${s.catalog.byCity[s.queryCityId]?.name.orEmpty()} · 查车票",onBack=onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,bottom=16.dp,top=if(s.queryCityId==null)0.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(s.queryCityId!=null) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("确认日期，再查去${s.catalog.byCity[s.queryCityId]?.name.orEmpty()}的车票",style=MaterialTheme.typography.titleMedium)
                Text("仅查询此城市 · 不改变首页的目的地范围",style=MaterialTheme.typography.bodyMedium,color=Muted)
            }
            ContentCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable { sheet="origin" }.testTag("filter-origin"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("出发地",style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text(s.catalog.byCity[f.originCityId]?.name ?: "北京",fontSize=26.sp,fontWeight=FontWeight.SemiBold)
                    }
                    Text(if(f.originStations.isEmpty()) "同城各站" else f.originStations.mapNotNull { s.catalog.byCode[it]?.name }.joinToString("、"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Muted)
                    UiIcon("next",color=Subtle)
                }
                HorizontalDivider(color=Line)
                Column(Modifier.fillMaxWidth().clickable { sheet="dates" }.testTag("filter-dates").padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("出发日期",style=MaterialTheme.typography.bodySmall,color=Muted)
                    HomeDateRange(f)
                }
                HorizontalDivider(color=Line)
                SectionTitle("出发时段 · 可多选","自定义") { sheet="time" }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Choice("全天",f.isAllDay,{onUpdate(f.withCustomTime(0,1440))},Modifier.weight(1f).testTag("time-all"),contentPadding=PaddingValues(horizontal=2.dp,vertical=10.dp),toggle=true)
                    DeparturePeriod.entries.forEach { period ->
                        Choice(period.label,period in f.selectedDeparturePeriods,{onUpdate(f.toggleDeparturePeriod(period))},Modifier.weight(1f).testTag("time-${period.name}"),contentPadding=PaddingValues(horizontal=2.dp,vertical=10.dp),toggle=true)
                    }
                }
                Text("${timeIntervalsText(f)} · 所选日期每天适用",Modifier.testTag("time-intervals"),style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            ContentCard(Modifier.fillMaxWidth()) {
                FilterRow("席别",seatSummary(f)) { sheet="seats" }
                HorizontalDivider(color=Line)
                FilterRow("乘车人数","${f.people} 人") { sheet="people" }
                HorizontalDivider(color=Line)
                FilterRow("最长车程",f.maxMinutes?.let { durationText(it) } ?: "不限") { sheet="duration" }
                HorizontalDivider(color=Line)
                if(s.queryCityId==null) FilterRow("查询目的地","${f.destinationCityIds.size} 个城市") { sheet="scope" }
                else Text("查询目的地：${s.catalog.byCity[s.queryCityId]?.name.orEmpty()}",Modifier.padding(vertical=12.dp))
            }
            s.error?.let { Hint(it,true) }
            PrimaryButton(if(s.queryCityId==null) "查询有票城市" else "查询去${s.catalog.byCity[s.queryCityId]?.name.orEmpty()}的车票",onSearch,modifier=Modifier.testTag("search-cities"))
            Text("只看直达去程 · 购票在12306 App完成",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
    }
    sheet?.let { key ->
        if(key=="scope") DestinationSelector(s,{sheet=null},{onUpdate(it);sheet=null},destinationBrowser,wishlist,onReloadWishlist)
        else FilterSheet(key,s,onDismiss={sheet=null},onApply={onUpdate(it);sheet=null})
    }
}
@Composable private fun HomeDateRange(f:SearchFilters) {
    val fontScale=LocalDensity.current.fontScale
    @Composable fun date(value:java.time.LocalDate,tag:String,modifier:Modifier=Modifier,end:Boolean=false) {
        Column(modifier,horizontalAlignment=if(end) Alignment.End else Alignment.Start) {
            Text(dateLabel(value),Modifier.testTag(tag),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text(value.format(DateTimeFormatter.ofPattern("EEEE",java.util.Locale.CHINA)),style=MaterialTheme.typography.bodySmall,color=Muted)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact=maxWidth<300.dp || fontScale>1.15f
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(fontScale>=1.6f) {
                date(f.startDate,"home-start-date")
                Text("至",style=MaterialTheme.typography.bodySmall,color=Subtle)
                date(f.endDate,"home-end-date")
            } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                date(f.startDate,"home-start-date",Modifier.weight(1f))
                if(!compact) Text("共${f.dates().size}天",style=MaterialTheme.typography.bodySmall,color=Subtle)
                date(f.endDate,"home-end-date",Modifier.weight(1f),end=true)
            }
            if(compact) Text("共${f.dates().size}天",style=MaterialTheme.typography.bodySmall,color=Subtle)
        }
    }
}
@Composable private fun FilterRow(title:String,value:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=onClick),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(.85f),style=MaterialTheme.typography.bodyLarge)
        Text(value,Modifier.weight(1.3f),style=MaterialTheme.typography.bodyMedium,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.End)
        UiIcon("next",color=Subtle)
    }
}
