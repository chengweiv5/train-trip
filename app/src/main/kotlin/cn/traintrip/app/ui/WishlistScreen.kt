package cn.traintrip.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*

@Composable internal fun FavoriteButton(city:City,selected:Boolean,onClick:()->Unit,enabled:Boolean=true) {
    Column(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("favorite-${city.id}")
        .toggleable(selected,enabled=enabled,role=Role.Checkbox,onValueChange={onClick()})
        .semantics { contentDescription=if(selected) "取消想去${city.name}" else "加入想去${city.name}" },
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        UiIcon(if(selected)"star-filled" else "star",color=if(selected)Primary else Muted)
        Text(if(selected)"已想去" else "想去",style=MaterialTheme.typography.labelSmall,color=if(selected)Primary else Muted)
    }
}
@Composable fun RootNavigation(selected:Page,onSelect:(Page)->Unit) {
    Surface(color=androidx.compose.ui.graphics.Color.White) {
        Column {
            HorizontalDivider(color=Line)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                listOf(Page.FILTERS to "查票",Page.WISHLIST to "想去").forEach { (page,label) ->
                    Column(Modifier.weight(1f).heightIn(min=56.dp).testTag("tab-${page.name}")
                        .selectable(selected==page,role=Role.Tab,onClick={onSelect(page)}).padding(vertical=6.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        UiIcon(if(page==Page.FILTERS)"train" else "star",color=if(page==selected)Primary else Muted)
                        Text(label,style=MaterialTheme.typography.labelMedium,color=if(page==selected)Primary else Muted)
                    }
                }
            }
        }
    }
}
@Composable fun WishlistScreen(state:WishlistState,catalog:StationCatalog,guides:Map<String,DestinationGuide>,offline:List<OfflineEntry>,
    onAdd:()->Unit,onToggle:(City)->Unit,onGuide:(String)->Unit,onQuery:(String)->Unit,onRetry:()->Unit) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        WishlistBrandHeader(when {
            state.loading && state.items.isEmpty()->"正在读取想去清单…"
            state.error!=null->"清单读取失败，请重试"
            state.items.isEmpty()->"把心动的城市，留给下次出发"
            else->"${state.items.size} 个想去城市 · 最近收藏优先"
        },onAdd)
        LazyColumn(Modifier.weight(1f).testTag("wishlist-list"),state=rememberLazyListState(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.loading && state.items.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if(state.error!=null) item { Hint(state.error,true);SecondaryButton("重新读取",onRetry) }
            else if(state.items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top=72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    UiIcon("star",Modifier.size(38.dp))
                    Text("先收藏一座想去的城市",style=MaterialTheme.typography.titleLarge)
                    Text("点亮城市星标，加入想去清单。\n也可以直接添加城市。",color=Muted)
                    PrimaryButton("添加想去城市",onAdd)
                }
            } else {
                items(state.items,key={it.cityId}) { wish ->
                    val city=catalog.byCity[wish.cityId]
                    val guide=guides[wish.cityId]
                    ContentCard(Modifier.fillMaxWidth().testTag("wish-${wish.cityId}"),onClick={onGuide(wish.cityId)}) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(city?.name ?: wish.name,style=MaterialTheme.typography.titleLarge)
                                Text(city?.province?.name ?: wish.province,style=MaterialTheme.typography.bodySmall,color=Muted)
                            }
                            if(city!=null) FavoriteButton(city,true,{onToggle(city)},!state.busy)
                        }
                        Text(guide?.tagline ?: "先留在清单里，下次再了解",color=Muted,style=MaterialTheme.typography.bodyMedium)
                        Text(offlineLabel(wish.cityId,guide,offline),style=MaterialTheme.typography.bodySmall,color=if(guide==null)Muted else ContentReady)
                        HorizontalDivider(color=Line)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton({onGuide(wish.cityId)},Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp)) { Text("了解目的地",Modifier.fillMaxWidth()) }
                            OutlinedButton({onQuery(wish.cityId)},Modifier.heightIn(min=48.dp).testTag("wish-query-${wish.cityId}"),
                                shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,ActionBorder),
                                colors=ButtonDefaults.outlinedButtonColors(containerColor=PrimaryTint),enabled=city?.supported==true) {
                                UiIcon("train",color=LocalContentColor.current);Spacer(Modifier.width(6.dp));Text("查车票")
                            }
                        }
                        if(city?.supported!=true)Text(city?.unavailableReason ?: "当前目录暂未收录此城市",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                }
                item { Text("想去清单保存在本机",style=MaterialTheme.typography.bodySmall,color=Muted) }
            }
        }
    }
}
fun offlineLabel(id:String,guide:DestinationGuide?,offline:List<OfflineEntry>):String = when {
    guide==null->"介绍尚未下载"
    guide.generatedAt==null->"内置介绍"
    offline.firstOrNull { it.cityId==id }?.hasPhoto==true->"介绍可离线查看"
    else->"仅文字可离线查看"
}
@Composable fun AddCityScreen(catalog:StationCatalog,state:WishlistState,onBack:()->Unit,onAdd:(List<City>,()->Unit)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val existing=state.items.map { it.cityId }.toSet()
    val cities=remember(query,catalog){catalog.searchDestinations(query)}
    val keyboard=LocalSoftwareKeyboardController.current
    Scaffold(containerColor=PageBackground,contentWindowInsets=WindowInsets(0,0,0,0),topBar={AppTopBar("添加想去城市",onBack)},bottomBar={
        Surface { Column(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
            val ids=selected.filterNot { it in existing }
            PrimaryButton(if(state.busy)"添加中…" else if(ids.isEmpty())"添加城市" else "添加 ${ids.size} 个城市",{
                keyboard?.hide();onAdd(catalog.cities.filter { it.id in ids },onBack)
            },!state.loading && state.error==null && !state.busy && ids.isNotEmpty(),Modifier.testTag("add-wishes"))
        } }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("wish-search"),placeholder={Text("搜索城市、省份或拼音")},singleLine=true) }
            item { Text("选择城市后点“添加”，已有收藏会标为已添加。",style=MaterialTheme.typography.bodySmall,color=Muted) }
            if(cities.isEmpty())item { Hint("没有匹配的城市，请换一个名称或拼音。");TextButton({query=""}){Text("清空搜索")} }
            cities.groupBy { it.province }.forEach { (province,group) ->
                item("province-${province.id}"){Text(province.name,Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=Muted)}
                items(group,key={it.id}) { city ->
                    Surface(color=if(city.id in selected)PrimaryTint else CardBackground,shape=RoundedCornerShape(12.dp),
                        border=BorderStroke(1.dp,if(city.id in selected)Primary else CardBorder)) {
                        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("add-city-${city.id}")
                            .toggleable(city.id in selected,enabled=city.id !in existing && !state.busy,role=Role.Checkbox,onValueChange={selected=if(it)selected+city.id else selected-city.id})
                            .padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(city.name,Modifier.weight(1f))
                            if(city.id in existing)Text("已添加",style=MaterialTheme.typography.bodySmall,color=Muted)
                            else Checkbox(city.id in selected,null,enabled=!state.busy)
                        }
                    }
                }
            }
        }
    }
}
