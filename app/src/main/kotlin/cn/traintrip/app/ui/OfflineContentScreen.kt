package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*
import java.util.Locale

fun storageSize(bytes:Long):String=if(bytes<1024*1024)String.format(Locale.CHINA,"%.1f KB",bytes/1024.0) else String.format(Locale.CHINA,"%.1f MB",bytes/(1024.0*1024))
@Composable fun OfflineContentScreen(state:DestinationState,catalog:StationCatalog,onBack:()->Unit,onRead:(String)->Unit,onDelete:(String)->Unit,onReload:()->Unit) {
    var deleting by remember { mutableStateOf<OfflineEntry?>(null) }
    Scaffold(containerColor=PageBackground,contentWindowInsets=WindowInsets(0,0,0,0),topBar={AppTopBar("离线内容",onBack)}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("offline-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { ContentCard(Modifier.fillMaxWidth()) {
                Text("已下载 ${state.offline.size} 个城市",style=MaterialTheme.typography.titleMedium)
                Text(storageSize(state.offline.sumOf { it.bytes }),style=MaterialTheme.typography.headlineMedium,color=Primary)
                Text("保存后，目的地介绍可离线阅读",style=MaterialTheme.typography.bodySmall,color=Muted)
            } }
            if(state.offlineLoading)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.offlineError?.let { item { Hint(it,true);TextButton(onReload){Text("刷新列表")} } }
            state.offlineMessage?.let { item { Hint(it) } }
            if(!state.offlineLoading && state.offline.isEmpty())item { Hint("暂无下载内容，内置介绍仍可查看。") }
            items(state.offline,key={it.cityId}) { entry ->
                ContentCard(Modifier.fillMaxWidth().testTag("offline-${entry.cityId}"),onClick={onRead(entry.cityId)}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(catalog.byCity[entry.cityId]?.name ?: entry.guide?.name ?: entry.cityId,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text(storageSize(entry.bytes),style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    Text(entry.guide?.let { "更新于 ${it.generatedAt?.take(10).orEmpty()} · ${if(entry.hasPhoto)"介绍和图片" else "仅文字"}" } ?: "未完成的本地文件，可重新清理",style=MaterialTheme.typography.bodySmall,color=Muted)
                    HorizontalDivider(color=Line)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton({onRead(entry.cityId)},enabled=state.deleting==null,modifier=Modifier.heightIn(min=48.dp)) { Text("查看 / 更新介绍") }
                        TextButton({deleting=entry;onReload()},enabled=state.deleting==null,modifier=Modifier.heightIn(min=48.dp).testTag("delete-${entry.cityId}")) {
                            Text(if(state.deleting==entry.cityId)"删除中…" else "删除",color=Muted)
                        }
                    }
                }
            }
            item { Text("随应用提供的资料",style=MaterialTheme.typography.titleSmall)
                Text("内置城市介绍无需下载，随应用更新；不计入上方可清理空间。",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=Muted) }
            item { Text("删除后保留想去记录和服务设置，不会自动重新整理。手动更新会消耗搜索和模型额度。",style=MaterialTheme.typography.bodySmall,color=Muted) }
        }
    }
    deleting?.let { entry -> AlertDialog(onDismissRequest={deleting=null},title={Text("删除${catalog.byCity[entry.cityId]?.name.orEmpty()}的离线内容？")},text={
        Text("将删除此设备保存的介绍和图片，预计释放 ${storageSize(state.offline.firstOrNull { it.cityId==entry.cityId }?.bytes ?: entry.bytes)}。收藏记录和服务设置会保留。\n\n删除后不会自动重新整理，需要时可手动获取。")
    },confirmButton={TextButton({deleting=null;onDelete(entry.cityId)},Modifier.testTag("confirm-delete-offline")){Text("删除离线内容",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({deleting=null}){Text("取消")}}) }
}
