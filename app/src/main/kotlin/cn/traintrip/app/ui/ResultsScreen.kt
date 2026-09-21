package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*

@Composable fun ResultsScreen(s:UiState,onBack:()->Unit,onCity:(String)->Unit,onRefresh:()->Unit,onStop:()->Unit,onResume:()->Unit,onRetry:()->Unit,onSort:()->Unit,onGuide:(String)->Unit=onCity,guides:Map<String,DestinationGuide> = DestinationGuides.all.associateBy { it.cityId },favorites:Set<String> = emptySet(),onFavorite:(City)->Unit={}) {
    val f=s.applied ?: s.filters
    val progress=s.progress
    val groups=remember(progress,f,s.citySortByCount,s.catalog) { groupResults(s.catalog,f,progress,s.citySortByCount) }
    val cities=groups.flatMap { it.cities }
    var expanded by rememberSaveable(s.searchSession) { mutableStateOf(emptyList<String>()) }
    var initialized by rememberSaveable(s.searchSession) { mutableStateOf(false) }
    val listState=rememberLazyListState()
    val coroutine=rememberCoroutineScope()
    var scrollSession by rememberSaveable { mutableLongStateOf(-1) }
    LaunchedEffect(s.searchSession) {
        if(scrollSession!=s.searchSession) {
            expanded=groups.firstOrNull { it.cities.isNotEmpty() }?.let { listOf(it.province.id) }.orEmpty()
            initialized=expanded.isNotEmpty()
            listState.scrollToItem(0);scrollSession=s.searchSession
        }
    }
    LaunchedEffect(groups,initialized) {
        if(!initialized) groups.firstOrNull { it.cities.isNotEmpty() }?.let { expanded=expanded+it.province.id;initialized=true }
    }
    fun toggle(id:String) { expanded=if(id in expanded) expanded-id else expanded+id }
    fun collapse(id:String) {
        val index=listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key=="province-$id" }?.index
        expanded=expanded-id
        if(index!=null) coroutine.launch { listState.scrollToItem(index) }
    }
    var showErrors by remember { mutableStateOf(false) }
    Scaffold(containerColor=PageBackground,contentWindowInsets=WindowInsets(0,0,0,0),topBar={AppTopBar("有票城市",onBack,"修改",onBack)}) { padding->
    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("results-list"),state=listState,contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("${s.catalog.byCity[f.originCityId]?.name}出发 · ${dateRange(f)}",style=MaterialTheme.typography.titleMedium)
                Text("${timeRange(f)} · ${f.people} 人 · ${seatSummary(f)}${f.maxMinutes?.let { " · ${durationText(it)}内" }.orEmpty()}",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("${cities.size}个城市有票",Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleLarge)
            }
        }
        if(s.loading) item { Hint("正在连接 12306…");SearchProgressBar(0f,Modifier.padding(top=10.dp));TextButton(onStop) { Text("停止查询") } }
        s.error?.let { error->item { Hint("这次没能查到余票\n$error",true);SecondaryButton("重试查询",onRefresh) } }
        if(progress!=null && !s.loading) {
            item {
                val label=when {progress.running->"正在查询 ${progress.outcomes.size} / ${progress.plan.size}";progress.complete->"查询完成";progress.stopped->"查询已停止";else->"部分查询未完成"}
                val details=buildList {
                    if(progress.failureCount>0) add("${progress.failureCount} 项失败")
                    if(progress.unopenedCount>0) add("${progress.unopenedCount} 项未开售")
                    if(!progress.running && progress.remainingCount>0) add("${progress.remainingCount} 项待查询")
                }.joinToString(" · ")
                if(progress.complete) Text(label,style=MaterialTheme.typography.bodySmall,color=Muted)
                else Hint(if(details.isEmpty()) label else "$label\n$details",warning=progress.failureCount>0)
                if(progress.running) {
                    SearchProgressBar(if(progress.plan.isEmpty()) 0f else progress.outcomes.size.toFloat()/progress.plan.size,Modifier.padding(top=10.dp))
                    TextButton(onStop) { Text("停止查询，保留结果") }
                } else {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(progress.remainingCount>0) TextButton(onResume) { Text("继续查询") }
                        if(progress.failureCount>0 || progress.unopenedCount>0) TextButton(onRetry) { Text("重试查询") }
                        if(progress.failureCount>0 || progress.unopenedCount>0) TextButton({showErrors=true}) { Text("查看原因") }
                    }
                }
            }
        }
        item {
            val actionPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onSort,Modifier.weight(1f).heightIn(min=48.dp),contentPadding=actionPadding) {
                    Text(if(s.citySortByCount) "省内 · 车次数最多 ↓" else "省内 · 车程最短 ↓",
                        Modifier.fillMaxWidth(),textAlign=TextAlign.Start)
                }
                TextButton(onRefresh,Modifier.heightIn(min=48.dp),enabled=!s.loading && progress?.running!=true,contentPadding=actionPadding) { UiIcon("refresh");Spacer(Modifier.width(6.dp));Text("刷新") }
            }
        }
        items(groups,key={"province-${it.province.id}"}) { group ->
            ProvinceResultGroup(group,group.province.id in expanded,{toggle(group.province.id)},{collapse(group.province.id)},content={
                group.cities.forEach { city -> CityCard(city,f,onCity,onGuide,guides[city.cityId],s.catalog.byCity[city.cityId],city.cityId in favorites,onFavorite) }
                if(group.incomplete && progress?.running!=true && progress!=null) TextButton(onRetry) { Text("重试查询") }
            })
        }
        if(cities.isEmpty() && !s.loading && s.error==null && progress?.complete==true) item { Hint("没有符合条件的车票\n试试换一天，或放宽时段、席别。") }
        item { Text("余票随时变化，可在车次页刷新。购票以 12306 App 实时结果为准。",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall,color=Muted) }
    }
    }
    if(showErrors) AlertDialog(onDismissRequest={showErrors=false},title={Text("查询未完成的原因")},text={
        androidx.compose.foundation.lazy.LazyColumn { items(progress?.plan?.filter { progress.outcomes[it.key] is QueryResult.Failure || progress.outcomes[it.key] is QueryResult.NotOnSale } ?: emptyList()) { u->
            val outcome=progress?.outcomes?.get(u.key)
            Text("${u.date} · ${u.destination.cityName}\n${when(outcome){is QueryResult.Failure->outcome.message;is QueryResult.NotOnSale->outcome.message;else->""}}",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
        } }
    },confirmButton={TextButton({showErrors=false}) { Text("关闭") }})
}
@Composable private fun CityCard(city:CityResult,f:SearchFilters,onCity:(String)->Unit,onGuide:(String)->Unit,guide:DestinationGuide?,catalogCity:City?,favorite:Boolean,onFavorite:(City)->Unit) {
    ContentCard(Modifier.fillMaxWidth().testTag("city-card-${city.cityId}"),onClick={onGuide(city.cityId)}) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            guide?.gallery?.firstOrNull()?.let { Box(Modifier.size(60.dp)) { DestinationPhoto(it,Modifier.fillMaxSize()) } }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(city.cityName,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    Text("${city.trainCount} 趟有票",color=AvailableGreen,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
                }
                guide?.let { Text(it.tagline,style=MaterialTheme.typography.bodyMedium,color=Muted) }
            }
            if(catalogCity!=null)FavoriteButton(catalogCity,favorite,{onFavorite(catalogCity)})
        }
        Text("最快 ${durationText(city.shortestMinutes)}${guide?.let { " · 建议玩${it.suggestedDays}" }.orEmpty()}",style=MaterialTheme.typography.titleSmall)
        Text(city.trips.flatMap { t->f.seats.filter { t.seats[it]?.confirmedFor(f.people)==true } }.distinct().sortedBy { it.ordinal }.joinToString(" / ") { it.label } +
            " · " + city.trips.map { it.date }.distinct().sorted().joinToString("、") { dateLabel(it) },style=MaterialTheme.typography.bodySmall,color=Muted)
        HorizontalDivider(color=Line)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton({onGuide(city.cityId)},Modifier.weight(1f).testTag("guide-${city.cityId}"),contentPadding=PaddingValues(vertical=10.dp)) { Text("了解目的地",Modifier.fillMaxWidth(),textAlign=TextAlign.Start) }
            TextButton({onCity(city.cityId)},Modifier.weight(1f).testTag("trains-${city.cityId}"),contentPadding=PaddingValues(vertical=10.dp)) { Text("查看车次",Modifier.weight(1f),textAlign=TextAlign.End);UiIcon("next") }
        }
    }
}
