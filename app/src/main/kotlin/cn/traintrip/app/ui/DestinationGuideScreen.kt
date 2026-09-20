@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.traintrip.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.core.*
import cn.traintrip.app.DestinationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun DestinationPhoto(photo: DestinationPhoto, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, photo.assetName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val stream = if(photo.remoteUrl!=null) java.io.File(context.filesDir,"destination-guides/${photo.assetName}").inputStream()
                    else context.assets.open("destinations/${photo.assetName}")
                stream.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), color = PrimaryTint) {
        bitmap?.let {
            Image(it, contentDescription = photo.description, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(photo.description, Modifier.padding(16.dp), color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun DestinationTags(guide: DestinationGuide) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        guide.tags.forEach { tag ->
            Surface(color = PrimaryTint, shape = RoundedCornerShape(6.dp)) {
                Text(tag, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Primary,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable fun DestinationGuideScreen(
    cityName: String, guide: DestinationGuide?, onBack: () -> Unit,
    onTrains: () -> Unit, onSource: (String) -> Unit, provinceLabel: String = "",
    runtime: DestinationState? = null, onRefresh: () -> Unit = {}, onSettings: () -> Unit = {},
    city:City?=null,favorite:Boolean=false,onFavorite:()->Unit={},queryAction:Boolean=false
) = key(cityName) {
    DestinationGuidePage(cityName, guide, onBack, onTrains, onSource, provinceLabel,runtime,onRefresh,onSettings,city,favorite,onFavorite,queryAction)
}

@Composable private fun DestinationGuidePage(
    cityName: String, guide: DestinationGuide?, onBack: () -> Unit,
    onTrains: () -> Unit, onSource: (String) -> Unit, provinceLabel: String,
    runtime: DestinationState?, onRefresh: () -> Unit, onSettings: () -> Unit,
    city:City?,favorite:Boolean,onFavorite:()->Unit,queryAction:Boolean
) {
    var days by rememberSaveable { mutableIntStateOf(1) }
    var sourcesOpen by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState { GuideSection.entries.size }
    val pageLists = GuideSection.entries.map { key(it) { rememberLazyListState() } }
    val scope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale
    // Use the whole safe viewport, independent of the header's measured height.
    BoxWithConstraints(Modifier.fillMaxSize().testTag("destination-guide")) {
        val scrollOverview = maxHeight < 640.dp || fontScale > 1.15f
        Scaffold(containerColor = PageBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
            Surface(color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PrimaryButton(if(queryAction)"查去${cityName}的车票" else "查看车次", onTrains, enabled=!queryAction || city?.supported!=false, modifier = Modifier.testTag("guide-trains"))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                AppTopBar("了解目的地",onBack,trailing=if(city!=null) { { FavoriteButton(city,favorite,onFavorite) } } else null)
                if (guide == null) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Text(cityName, style = MaterialTheme.typography.titleLarge) }
                        item {
                            if(runtime==null) Hint("暂无目的地介绍，可先查看车次。")
                            else ContentCard(Modifier.fillMaxWidth()) {
                                Text("还没有保存这座城市的介绍",style=MaterialTheme.typography.titleMedium)
                                Text("可以先查车票，也可以整理一份景点、美食和玩法介绍。",style=MaterialTheme.typography.bodyMedium,color=Muted)
                                GuideRuntimeStatus(guide,runtime,onRefresh,onSettings)
                            }
                        }
                    }
                } else {
                    if (!scrollOverview) {
                        GuideOverview(cityName, provinceLabel, guide, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                    GuideTabs(pager.currentPage) { page ->
                        scope.launch { pager.animateScrollToPage(page) }
                    }
                    HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f).testTag("guide-pager"),
                        verticalAlignment = Alignment.Top, key = { GuideSection.entries[it].id }) { page ->
                        val section = GuideSection.entries[page]
                        LazyColumn(Modifier.fillMaxSize().testTag("guide-page-${section.id}"),
                            state = pageLists[page], contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (scrollOverview) {
                                item("overview") { GuideOverview(cityName, provinceLabel, guide, Modifier.padding(bottom = 8.dp)) }
                            }
                            guideSectionContent(section, guide, days, onSource) { days = it }
                            if(runtime!=null) item("runtime") { GuideRuntimeStatus(guide,runtime,onRefresh,onSettings) }
                            item("sources") {
                                TextButton({ sourcesOpen = true }, Modifier.testTag("guide-sources-${section.id}"),
                                    contentPadding = PaddingValues(vertical = 12.dp)) {
                                    Text("资料来源与图片署名", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (sourcesOpen && guide != null) GuideSourcesDialog(guide, onSource) { sourcesOpen = false }
}

@Composable private fun GuideOverview(cityName: String, provinceLabel: String, guide: DestinationGuide, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (provinceLabel.isNotBlank()) Text(provinceLabel, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(cityName, style = MaterialTheme.typography.titleLarge)
                Text("建议 ${guide.suggestedDays} · ${guide.pace}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            guide.photo?.let { photo -> Box(Modifier.width(72.dp).height(72.dp)) { DestinationPhoto(photo, Modifier.fillMaxSize()) } }
        }
        Text(guide.tagline, style = MaterialTheme.typography.bodyMedium, color = Muted)
        DestinationTags(guide)
    }
}

@Composable private fun GuideTabs(selected:Int,onSelect:(Int)->Unit) {
    Surface(color=Color.White) {
        Column {
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                GuideSection.entries.forEachIndexed { index,section->
                    val active=selected==index
                    Column(Modifier.weight(1f).testTag("guide-tab-${section.id}").selectable(active,role=Role.Tab,onClick={onSelect(index)}),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.heightIn(min=48.dp).padding(vertical=12.dp),contentAlignment=Alignment.Center) {
                            Text(section.label,color=if(active) Primary else Muted,style=MaterialTheme.typography.bodyLarge,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Surface(Modifier.width(26.dp).height(3.dp),shape=RoundedCornerShape(2.dp),color=if(active) Primary else Color.Transparent) {}
                    }
                }
            }
            HorizontalDivider(color=Line)
        }
    }
}

@Composable private fun GuideSourcesDialog(guide: DestinationGuide, onSource: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("资料来源与图片署名") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("景点与美食根据国内公开资料整理；玩法、停留时长与强度为编辑建议。核对日期不代表原文发布日期。",
                    style = MaterialTheme.typography.bodySmall)
                guide.generatedAt?.let { Text("DeepSeek 整理 · ${it.take(10)}\n内容供行程参考，出行信息请以原文及景区最新公告为准。",style=MaterialTheme.typography.bodySmall) }
            }
            items(guide.sources) { source ->
                TextButton({ onSource(source.url) }, contentPadding = PaddingValues(0.dp)) { Text(source.title) }
                Text("${if(guide.generatedAt!=null) "检索" else "核对"} ${source.checkedOn}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            guide.photo?.let { photo -> item {
                HorizontalDivider(color = Line)
                Text(photo.description, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                Text("${photo.credit}\n已缩放，展示时裁剪",
                    style = MaterialTheme.typography.bodySmall)
                Text(photo.license ?: "图片仅用于个人离线浏览，权利归原权利人所有。",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton({ onSource(photo.sourceUrl) }, contentPadding = PaddingValues(0.dp)) { Text("查看原图与作者") }
                photo.licenseUrl?.let { url ->
                    TextButton({ onSource(url) }, contentPadding = PaddingValues(0.dp)) { Text("查看图片许可") }
                }
            } }
        }
    }, confirmButton = { TextButton(onClose) { Text("关闭") } })
}

@Composable private fun GuideRuntimeStatus(guide: DestinationGuide?, state: DestinationState, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.stage.ifBlank { "正在读取离线内容…" },style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        state.error?.let { Hint(it,true) }
        state.contentMessage?.let { Hint(it) }
        guide?.generatedAt?.let { Text("DeepSeek 整理 · ${it.take(10)} · 已保存到本机",style=MaterialTheme.typography.bodySmall,color=Muted) }
        if(!state.loading) {
            if(!state.configured || !state.tavilyConfigured) {
                if(guide==null) Text("配置 Tavily 和 DeepSeek 后，可按需整理新城市。",style=MaterialTheme.typography.bodyMedium)
                TextButton(onSettings) { Text("配置内容服务") }
            } else TextButton(onRefresh,Modifier.heightIn(min = 48.dp).testTag("refresh-guide"),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                Text(if(guide!=null) "更新目的地介绍" else if(state.error!=null) "重试整理" else "整理目的地介绍")
            }
            Text("手动整理会使用 Tavily 搜索和 DeepSeek 模型额度。",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
    }
}
