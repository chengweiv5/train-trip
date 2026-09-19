@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.traintrip.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun DestinationPhoto(photo: DestinationPhoto, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(null, photo.assetName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("destinations/${photo.assetName}").use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), color = Sage) {
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
            Surface(color = Sage, shape = RoundedCornerShape(6.dp)) {
                Text(tag, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Forest,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable fun DestinationGuideScreen(
    cityName: String, guide: DestinationGuide?, onBack: () -> Unit,
    onTrains: () -> Unit, onSource: (String) -> Unit
) {
    var days by rememberSaveable(guide?.cityId) { mutableIntStateOf(1) }
    var sourcesOpen by rememberSaveable(guide?.cityId) { mutableStateOf(false) }
    Scaffold(containerColor = Cream, bottomBar = {
        Surface(color = Cream, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryButton("查看车次  →", onTrains, modifier = Modifier.testTag("guide-trains"))
            }
        }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("destination-guide"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item("header") { BackHeader("目的地灵感", onBack) }
            item("title") {
                Text(cityName, style = MaterialTheme.typography.headlineLarge)
                if (guide != null) Text(guide.tagline, Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.titleMedium, color = Forest)
            }
            if (guide == null) {
                item("missing") { Hint("暂无目的地介绍，可先查看车次。") }
            } else {
                item("photo") {
                    DestinationPhoto(guide.photo, Modifier.height(200.dp))
                    Text(guide.photo.description, Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                item("overview") {
                    DestinationTags(guide)
                    Text("建议 ${guide.suggestedDays} · ${guide.pace}", Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton({ sourcesOpen = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("资料来源与图片署名", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item("experiences-title") { SectionTitle("值得去的地方") }
                items(guide.experiences, key = { "experience-${it.id}" }) { experience ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(experience.name, style = MaterialTheme.typography.titleMedium)
                        Text(experience.reason, style = MaterialTheme.typography.bodyMedium)
                        Text("建议 ${experience.duration} · ${experience.location}",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                        HorizontalDivider(Modifier.padding(top = 7.dp), color = Line)
                    }
                }
                item("food-title") { SectionTitle("尝尝当地味道") }
                item("food") {
                    ContentCard(Modifier.fillMaxWidth()) {
                        guide.foods.forEach { food ->
                            Text(food.name, style = MaterialTheme.typography.titleSmall, color = Forest)
                            Text(food.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item("plan-title") {
                    SectionTitle("可以这样玩")
                    Text("按完整游玩日安排，抵达较晚可少选一站。", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        guide.plans.forEach { plan ->
                            Choice("${plan.days} 日玩法", days == plan.days, { days = plan.days },
                                Modifier.testTag("plan-${plan.days}"))
                        }
                    }
                }
                item("plan") {
                    val plan = guide.plans.first { it.days == days }
                    ContentCard(Modifier.fillMaxWidth().testTag("selected-plan")) {
                        Text(plan.title, style = MaterialTheme.typography.titleMedium)
                        plan.schedule.forEach { day ->
                            Text(day.label, style = MaterialTheme.typography.labelLarge, color = Forest)
                            Text(day.experienceIds.joinToString(" → ") { id ->
                                guide.experiences.first { it.id == id }.name
                            }, style = MaterialTheme.typography.bodyMedium)
                            Text(day.description, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                        Text(plan.note, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                item("advice") {
                    SectionTitle("出发前知道这些")
                    Text(guide.season, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(guide.arrivalAdvice, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    Text("玩法与耗时为参考建议；门票、开放及预约要求请出发前查看景区官方信息。",
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                item("sources") {
                    Text("资料核对：${guide.sources.maxOf { it.checkedOn }}",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    TextButton({ sourcesOpen = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("查看来源与图片署名")
                    }
                }
            }
        }
    }
    if (sourcesOpen && guide != null) GuideSourcesDialog(guide, onSource) { sourcesOpen = false }
}

@Composable private fun GuideSourcesDialog(guide: DestinationGuide, onSource: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("资料来源与图片署名") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("景点与美食参考以下资料整理改写；玩法、停留时长与强度为编辑建议。来源内容可能早于核对日期。",
                    style = MaterialTheme.typography.bodySmall)
            }
            items(guide.sources) { source ->
                TextButton({ onSource(source.url) }, contentPadding = PaddingValues(0.dp)) { Text(source.title) }
                Text("核对 ${source.checkedOn}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            item {
                TextButton({ onSource(DestinationGuides.CONTENT_LICENSE_URL) }, contentPadding = PaddingValues(0.dp)) {
                    Text("改编正文：CC BY-SA 4.0")
                }
                HorizontalDivider(color = Line)
                Text(guide.photo.description, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                Text("摄影：${guide.photo.author}\n${guide.photo.license} · 已缩放，展示时裁剪",
                    style = MaterialTheme.typography.bodySmall)
                TextButton({ onSource(guide.photo.sourceUrl) }, contentPadding = PaddingValues(0.dp)) { Text("查看原图与作者") }
                TextButton({ onSource(guide.photo.licenseUrl) }, contentPadding = PaddingValues(0.dp)) { Text("查看图片许可") }
            }
        }
    }, confirmButton = { TextButton(onClose) { Text("关闭") } })
}
