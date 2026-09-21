package cn.traintrip.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.traintrip.core.DestinationPhoto

@Composable internal fun DestinationGallery(photos:List<DestinationPhoto>,onSource:(String)->Unit) {
    if(photos.isEmpty())return
    val pager=rememberPagerState { photos.size }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current=photos[pager.currentPage.coerceIn(photos.indices)]
    Column(Modifier.fillMaxWidth().testTag("destination-gallery"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HorizontalPager(pager,Modifier.fillMaxWidth().testTag("gallery-pager"),pageSpacing=8.dp,key={photos[it].assetName}) { page ->
            DestinationPhoto(photos[page],Modifier.fillMaxWidth().height(180.dp)
                .clickable(onClickLabel="查看大图") { expanded=true })
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(current.description,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Muted)
            Text("${pager.currentPage+1}/${photos.size}",Modifier.testTag("gallery-count"),style=MaterialTheme.typography.labelMedium,color=Primary)
        }
        TextButton({onSource(current.sourceUrl)},Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)) { Text("图片来源",style=MaterialTheme.typography.bodySmall) }
    }
    if(expanded)Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val full=rememberPagerState(initialPage=pager.currentPage.coerceIn(photos.indices)) { photos.size }
        val photo=photos[full.currentPage.coerceIn(photos.indices)]
        Surface(Modifier.fillMaxSize(),color=PageBackground) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                AppTopBar("图片 ${full.currentPage+1}/${photos.size}",{expanded=false},backTag="close-gallery")
                HorizontalPager(full,Modifier.fillMaxWidth().weight(1f),key={photos[it].assetName}) { index ->
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
