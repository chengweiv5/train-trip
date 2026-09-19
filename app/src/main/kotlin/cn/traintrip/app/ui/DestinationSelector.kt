package cn.traintrip.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.traintrip.app.UiState
import cn.traintrip.core.*

class DestinationBrowserState(initialProvince: String = "") {
    var provinceId by mutableStateOf(initialProvince)
    val lists = mutableMapOf<String, LazyListState>()
    fun list(id: String) = lists.getOrPut(id) { LazyListState() }
    companion object {
        val Saver = Saver<DestinationBrowserState, ArrayList<String>>(
            save = { state -> arrayListOf(state.provinceId).apply { state.lists.forEach { (id,list) -> add("$id:${list.firstVisibleItemIndex}:${list.firstVisibleItemScrollOffset}") } } },
            restore = { values -> DestinationBrowserState(values.first()).apply { values.drop(1).forEach { value ->
                val parts=value.split(':'); lists[parts[0]]=LazyListState(parts[1].toInt(),parts[2].toInt())
            } } }
        )
    }
}

@Composable fun DestinationSelector(s: UiState, onDismiss: () -> Unit, onApply: (SearchFilters) -> Unit,
    browser: DestinationBrowserState = rememberSaveable(saver=DestinationBrowserState.Saver) { DestinationBrowserState() }) {
    val density=LocalDensity.current
    val catalog=s.catalog
    var selected by rememberSaveable { mutableStateOf(s.filters.destinationCityIds.toList()) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var provinceMenu by remember { mutableStateOf(false) }
    val province=catalog.provinces.firstOrNull { it.id==browser.provinceId }
        ?: catalog.cities.firstOrNull { it.id in selected }?.province ?: catalog.provinces.first()
    LaunchedEffect(Unit) {
        if(browser.provinceId.isBlank()) {
            browser.provinceId=province.id
            browser.list("navigation").scrollToItem(catalog.provinces.indexOf(province))
        }
    }
    val selectedCities=catalog.cities.filter { it.id in selected }
    val selectedProvinceCount=selectedCities.map { it.province.id }.distinct().size
    fun selectable(city: City)=city.supported && city.id!=s.filters.originCityId
    fun toggle(city: City) { selected=if(city.id in selected) selected-city.id else selected+city.id }
    fun toggleProvince(cities: List<City>) {
        val ids=cities.filter(::selectable).map { it.id }
        selected=if(ids.all { it in selected }) selected-ids.toSet() else (selected+ids).distinct()
    }
    Dialog(onDismissRequest={if(selectedOnly) selectedOnly=false else onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        CompositionLocalProvider(LocalDensity provides density) {
        BackHandler { if(selectedOnly) selectedOnly=false else onDismiss() }
        Surface(Modifier.fillMaxSize().testTag("destination-selector"),color=Cream) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton({if(selectedOnly) selectedOnly=false else onDismiss()}) { Text(if(selectedOnly) "‹ 继续选择" else "取消") }
                    Text(if(selectedOnly) "已选城市" else "选择目的地",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                }
                if(!selectedOnly) {
                    OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=20.dp).testTag("destination-search"),
                        label={Text("搜索省份或城市")},singleLine=true,
                        trailingIcon={if(search.isNotEmpty()) TextButton({search=""}) { Text("清除") }})
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton({selected=catalog.initialDestinations(s.filters.originCityId).toList()}) { Text("恢复默认范围") }
                        TextButton({selected=emptyList()}) { Text("清空选择") }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compact=maxWidth<360.dp || LocalDensity.current.fontScale>1.15f
                    if(selectedOnly || search.isNotBlank()) {
                        val cities=if(selectedOnly) selectedCities else catalog.searchDestinations(search)
                        LazyColumn(Modifier.fillMaxSize().testTag("destination-search-results"),state=rememberLazyListState(),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=12.dp)) {
                            if(cities.isEmpty()) item { Hint(if(selectedOnly) "还没有选择城市" else "没有找到匹配的省份或城市") }
                            cities.groupBy { it.province }.forEach { (p,group) ->
                                item("heading-${p.id}") {
                                    DestinationProvinceHeader(p,group.size,
                                        if(selectedOnly) "移除本省" else if(p.matches(search)) provinceAction(group.filter(::selectable),selected) else "查看全省",
                                        { if(selectedOnly) selected=selected-group.map { it.id }.toSet()
                                          else if(p.matches(search)) toggleProvince(group)
                                          else {browser.provinceId=p.id;search=""} })
                                }
                                items(group,key={it.id}) { city -> DestinationCityRow(city,city.id in selected,selectable(city),s.filters.originCityId) { toggle(city) } }
                            }
                        }
                    } else {
                        val cities=catalog.cities.filter { it.province.id==province.id }
                        Column {
                            if(compact) Box(Modifier.padding(horizontal=20.dp)) {
                                OutlinedButton({provinceMenu=true},Modifier.fillMaxWidth().testTag("province-dropdown")) { Text("${province.name}  ▾",Modifier.weight(1f)) }
                                DropdownMenu(provinceMenu,{provinceMenu=false}) {
                                    catalog.provinces.forEach { p -> DropdownMenuItem(text={Text(p.name)},onClick={browser.provinceId=p.id;provinceMenu=false}) }
                                }
                            }
                            Row(Modifier.weight(1f)) {
                                if(!compact) LazyColumn(Modifier.width(94.dp).fillMaxHeight().background(Sage.copy(alpha=.45f)).testTag("province-navigation"),state=browser.list("navigation")) {
                                    items(catalog.provinces,key={it.id}) { p ->
                                        val count=selectedCities.count { it.province.id==p.id }
                                        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).background(if(p==province) Sage else Cream.copy(alpha=0f))
                                            .clickable { browser.provinceId=p.id }.padding(horizontal=12.dp,vertical=12.dp).testTag("province-nav-${p.id}"),verticalAlignment=Alignment.CenterVertically) {
                                            Text(p.shortName,Modifier.weight(1f),color=if(p==province) Forest else Muted)
                                            if(count>0) Text("$count",style=MaterialTheme.typography.bodySmall,color=Forest)
                                        }
                                    }
                                }
                                key(province.id) {
                                    LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal=12.dp).testTag("destination-city-list"),state=browser.list(province.id),contentPadding=PaddingValues(bottom=12.dp)) {
                                        item { DestinationProvinceHeader(province,cities.size,provinceAction(cities.filter(::selectable),selected),{toggleProvince(cities)}) }
                                        items(cities,key={it.id}) { city -> DestinationCityRow(city,city.id in selected,selectable(city),s.filters.originCityId) { toggle(city) } }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(color=Cream,shadowElevation=3.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextButton({selectedOnly=!selectedOnly},Modifier.fillMaxWidth(),contentPadding=PaddingValues(0.dp)) {
                            Text(if(selected.isEmpty()) "请至少选择一个城市" else "已选 $selectedProvinceCount 个省级地区 · ${selected.size} 个城市  ›",Modifier.weight(1f))
                        }
                        PrimaryButton("完成",{onApply(s.filters.copy(destinationCityIds=selected.toSet()))},selected.isNotEmpty(),Modifier.testTag("apply-destinations"))
                        Text("目录为 2023 年行政区划快照；暂无车站的城市不可选。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                }
            }
        }
    }
    }
}

private fun provinceAction(cities: List<City>, selected: List<String>) = if(cities.isNotEmpty() && cities.all { it.id in selected }) "取消全选" else "全选"

@Composable private fun DestinationProvinceHeader(province: Province,count: Int,action: String,onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=4.dp)) {
        Text(province.name,style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("$count 个城市",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Muted)
            TextButton(onAction,contentPadding=PaddingValues(horizontal=8.dp)) { Text(action) }
        }
    }
}

@Composable private fun DestinationCityRow(city: City,checked: Boolean,enabled: Boolean,origin: String,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("destination-${city.id}")
        .toggleable(value=checked,enabled=enabled,role=Role.Checkbox,onValueChange={onClick()}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=6.dp)) {
            Text(city.name,color=if(enabled) Ink else Muted)
            if(!enabled) Text(if(city.id==origin) "当前出发城市" else city.unavailableReason,style=MaterialTheme.typography.bodySmall,color=Muted)
            else if(city.province.municipality) Text("直辖市",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        Checkbox(checked,onCheckedChange=null,enabled=enabled)
    }
    HorizontalDivider(color=Line.copy(alpha=.5f))
}
