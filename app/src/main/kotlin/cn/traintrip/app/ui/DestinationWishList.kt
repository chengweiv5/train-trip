package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.app.WishlistState
import cn.traintrip.core.*

@Composable internal fun DestinationWishList(state:WishlistState,wishes:List<WishCity>,catalog:StationCatalog,
    selected:List<String>,origin:String,listState:LazyListState,onRetry:()->Unit,onToggleAll:()->Unit,onToggle:(City)->Unit,modifier:Modifier=Modifier) {
    val ready=!state.loading && state.error==null
    val available=wishes.mapNotNull { catalog.byCity[it.cityId] }.filter { it.supported && it.id!=origin }
    val allSelected=available.isNotEmpty() && available.all { it.id in selected }
    LazyColumn(modifier.padding(horizontal=12.dp).testTag("destination-wishlist-list"),state=listState,contentPadding=PaddingValues(bottom=12.dp)) {
        item("header") {
            Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=4.dp)) {
                Text("想去的城市",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("${wishes.size} 个城市",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Muted)
                    TextButton(onToggleAll,enabled=ready && available.isNotEmpty(),modifier=Modifier.testTag("wish-select-all"),contentPadding=PaddingValues(horizontal=8.dp)) {
                        Text(if(allSelected)"取消全选" else "全选")
                    }
                }
            }
        }
        when {
            state.error!=null->item("error") {
                Hint(state.error,true)
                TextButton(onRetry,Modifier.heightIn(min=48.dp).testTag("wish-retry")) { Text("重新读取") }
            }
            state.loading->item("loading") { Text("正在读取想去清单…",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodyMedium,color=Muted) }
            wishes.isEmpty()->item("empty") {
                Column(Modifier.padding(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("还没有想去的城市",style=MaterialTheme.typography.titleMedium)
                    Text("可在首页的「想去」中添加城市",style=MaterialTheme.typography.bodyMedium,color=Muted)
                }
            }
        }
        if(state.error==null) items(wishes,key={it.cityId}) { wish->
            val city=catalog.byCity[wish.cityId]
            if(city!=null) DestinationCityRow(city,city.id in selected,city.supported && city.id!=origin,origin,
                showProvince=true,interactionEnabled=ready) { onToggle(city) }
            else {
                Row(Modifier.fillMaxWidth().heightIn(min=54.dp).padding(vertical=8.dp).testTag("destination-${wish.cityId}"),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end=6.dp)) {
                        Text(wish.name,color=Muted)
                        Text(wish.province,style=MaterialTheme.typography.bodySmall,color=Muted)
                        Text("当前目录暂未收录此城市",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    Checkbox(wish.cityId in selected,onCheckedChange=null,enabled=false)
                }
                HorizontalDivider(color=Line.copy(alpha=.5f))
            }
        }
    }
}
