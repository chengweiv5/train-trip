package cn.traintrip.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.traintrip.core.DestinationPhoto
import kotlinx.coroutines.launch

@Composable internal fun DestinationGallery(photos:List<DestinationPhoto>,onSource:(String)->Unit) {
    var unavailable by remember(photos) { mutableStateOf(emptySet<String>()) }
    val visible = photos.filter { it.assetName !in unavailable }
    if (visible.isEmpty()) return
    key(visible.map { it.assetName }) {
        ItemGallery(visible,onSource) { unavailable = unavailable + it }
    }
}

@Composable private fun ItemGallery(photos:List<DestinationPhoto>,onSource:(String)->Unit,onUnavailable:(String)->Unit) {
    val pager=rememberPagerState { photos.size }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current=photos[pager.currentPage.coerceIn(photos.indices)]
    val scope = rememberCoroutineScope()
    val containSwipe = remember { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
        override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
    } }
    Column(Modifier.fillMaxWidth().testTag("destination-gallery"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().nestedScroll(containSwipe)) {
            HorizontalPager(pager,Modifier.fillMaxWidth().testTag("gallery-pager"),pageSpacing=8.dp,key={photos[it].assetName}) { page ->
                val photo=photos[page]
                DestinationPhoto(photo,Modifier.fillMaxWidth().height(180.dp)
                    .clickable(onClickLabel="查看${photo.description}大图") { expanded=true },onUnavailable={onUnavailable(photo.assetName)})
            }
            if(photos.size>1)Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),shape=RoundedCornerShape(12.dp),color=PageBackground.copy(alpha=.9f)) {
                Text("${pager.currentPage+1}/${photos.size}",Modifier.padding(horizontal=9.dp,vertical=4.dp).testTag("gallery-count"),
                    style=MaterialTheme.typography.labelMedium,color=Ink)
            }
        }
        TextButton({onSource(current.sourceUrl)},Modifier.heightIn(min=48.dp).testTag("gallery-source"),contentPadding=PaddingValues(0.dp)) {
            Text("图片来源",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(expanded) {
        val full=rememberPagerState(initialPage=pager.currentPage.coerceIn(photos.indices)) { photos.size }
        val photo=photos[full.currentPage.coerceIn(photos.indices)]
        val close:()->Unit = { scope.launch { pager.scrollToPage(full.currentPage.coerceIn(photos.indices));expanded=false } }
        Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize(),color=PageBackground) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    AppTopBar(if(photos.size>1)"图片 ${full.currentPage+1}/${photos.size}" else "查看图片",close,backTag="close-gallery")
                    HorizontalPager(full,Modifier.fillMaxWidth().weight(1f).testTag("gallery-full-pager"),key={photos[it].assetName}) { index ->
                        DestinationPhoto(photos[index],Modifier.fillMaxSize().padding(16.dp),ContentScale.Fit)
                    }
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(photo.description,style=MaterialTheme.typography.titleMedium)
                        Text(photo.credit,style=MaterialTheme.typography.bodySmall,color=Muted)
                        TextButton({onSource(photo.sourceUrl)}) { Text("查看图片来源") }
                    }
                }
            }
        }
    }
}
