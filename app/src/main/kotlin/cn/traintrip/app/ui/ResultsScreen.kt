package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*

@Composable fun ResultsScreen(s:UiState,onBack:()->Unit,onCity:(String)->Unit,onRefresh:()->Unit,onStop:()->Unit,onResume:()->Unit,onRetry:()->Unit,onSort:()->Unit) {
    val f=s.applied ?: s.filters
    val progress=s.progress
    val cities=remember(progress,f,s.citySortByCount) { aggregate(progress?.trips ?: emptyList(),f).let { list->if(s.citySortByCount) list.sortedByDescending { it.trainCount } else list } }
    val uncertain=remember(progress,f) { aggregate(progress?.trips ?: emptyList(),f,uncertain=true) }
    var showScope by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { BackHeader("${s.catalog.byCity[f.originCityId]?.name}出发 · ${dateRange(f)}",onBack) }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("这些城市，有票可去",style=MaterialTheme.typography.headlineLarge)
                Text("找到 ${cities.size} 个城市${if(progress?.running==true) " · 查询中" else ""}",color=Muted)
                Text("${timeRange(f)} · ${f.people} 人 · ${if(f.seats.size==SeatType.entries.size) "全部席别" else "已选 ${f.seats.size} 类席别"}",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
        item { TextButton({showScope=true},contentPadding=PaddingValues(0.dp)) { Text("查看本次 ${f.destinationCityIds.size} 个城市范围 · 可能未覆盖全部目的地") } }
        if(s.loading) item { Hint("正在连接 12306，读取开售日期与车站信息…");LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onStop) { Text("停止查询") } }
        s.error?.let { error->item { Hint("这次没能查到余票\n$error",true);SecondaryButton("重试查询",onRefresh) } }
        if(progress!=null) {
            item {
                val label=when {progress.running->"正在查询";progress.complete->"当前所选范围查询完成";progress.stopped->"查询已停止 · 保留部分结果";else->"部分结果"}
                Hint("$label\n成功 ${progress.successCount} / ${progress.plan.size} 项 · 失败 ${progress.failureCount} · 未开售 ${progress.unopenedCount} · 待查 ${progress.remainingCount}",warning=progress.failureCount>0)
                if(progress.running) {
                    LinearProgressIndicator(progress={if(progress.plan.isEmpty()) 0f else progress.outcomes.size.toFloat()/progress.plan.size},modifier=Modifier.fillMaxWidth().padding(top=10.dp))
                    TextButton(onStop) { Text("停止查询，保留结果") }
                } else {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(progress.remainingCount>0) TextButton(onResume) { Text("继续查询") }
                        if(progress.failureCount>0 || progress.unopenedCount>0) TextButton(onRetry) { Text("重试未成功项") }
                        if(progress.failureCount>0 || progress.unopenedCount>0) TextButton({showErrors=true}) { Text("查看原因") }
                    }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { TextButton(onSort,Modifier.weight(1f),contentPadding=PaddingValues(0.dp)) { Text(if(s.citySortByCount) "车次数最多优先 ↓" else "车程最短优先 ↓") };TextButton(onRefresh,enabled=!s.loading && progress?.running!=true) { Text("刷新") } } }
        items(cities,key={it.cityId}) { city->CityCard(city,f,onCity) }
        if(cities.isEmpty() && !s.loading && s.error==null && progress?.complete==true) item { Hint("暂时没有符合条件的票\n当前所选范围已查完。试试换一天，或放宽时段、席别。");SecondaryButton("调整出行条件",onBack) }
        if(uncertain.isNotEmpty()) {
            item { Text("数量待核验 · 未计入上方城市数",style=MaterialTheme.typography.titleMedium);Text("这些车次返回“有”但未公布张数，尚不能确认满足 ${f.people} 人。",style=MaterialTheme.typography.bodySmall,color=Amber) }
            items(uncertain,key={"uncertain-${it.cityId}"}) { city->CityCard(city,f,onCity,true) }
        }
        item { SecondaryButton("修改出行条件",onBack);Text("余票随时变化。选择车次后再次核验，并以 12306 购票结果为准。",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall,color=Muted) }
    }
    if(showScope) AlertDialog(onDismissRequest={showScope=false},title={Text("本次查询范围")},text={
        Column {Text(f.destinationCityIds.mapNotNull { s.catalog.byCity[it]?.name }.sorted().joinToString("、"));Spacer(Modifier.height(12.dp));Text("每个日期和出发站分别查询。采用城市代表站，同城覆盖尚未全面验证；完成只代表这些查询项完成。",style=MaterialTheme.typography.bodySmall) }
    },confirmButton={TextButton({showScope=false}) { Text("知道了") }})
    if(showErrors) AlertDialog(onDismissRequest={showErrors=false},title={Text("未成功查询项")},text={
        androidx.compose.foundation.lazy.LazyColumn { items(progress?.plan?.filter { progress.outcomes[it.key] is QueryResult.Failure || progress.outcomes[it.key] is QueryResult.NotOnSale } ?: emptyList()) { u->
            val outcome=progress?.outcomes?.get(u.key)
            Text("${u.date} · ${u.destination.cityName}\n${when(outcome){is QueryResult.Failure->outcome.message;is QueryResult.NotOnSale->outcome.message;else->""}}",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
        } }
    },confirmButton={TextButton({showErrors=false}) { Text("关闭") }})
}
@Composable private fun CityCard(city:CityResult,f:SearchFilters,onCity:(String)->Unit,uncertain:Boolean=false) {
    ContentCard(Modifier.fillMaxWidth(),onClick={onCity(city.cityId)}) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(city.cityName,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            Text(if(uncertain) "${city.trainCount} 趟待核验" else "${city.trainCount} 趟有票",color=if(uncertain) Amber else Forest,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
            Text("  ›",color=Muted)
        }
        Text("最快 ${durationText(city.shortestMinutes)} · ${city.trips.map { it.date }.distinct().sorted().joinToString("、") { dateLabel(it) }}",style=MaterialTheme.typography.bodyMedium)
        Text(city.trips.flatMap { t-> f.seats.filter { t.seats[it]?.let { a->a.confirmedFor(f.people) || a.uncertainFor(f.people) }==true } }.distinct().sortedBy { it.ordinal }.joinToString(" / ") { it.label },style=MaterialTheme.typography.bodySmall,color=Forest)
        Text("${city.trips.map { it.to.name }.distinct().take(4).joinToString(" / ")} · ${formatTime(city.trips.maxOf { it.queriedAt })} 查询",style=MaterialTheme.typography.bodySmall,color=Muted)
    }
}
