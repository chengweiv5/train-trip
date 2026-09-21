@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.core.SearchFilters

@Composable fun DurationFilterSheet(filters:SearchFilters,onDismiss:()->Unit,onApply:(SearchFilters)->Unit) {
    var mode by rememberSaveable { mutableStateOf(when(filters.maxMinutes) { null->"unlimited";180->"three";300->"five";else->"custom" }) }
    var customMinutes by rememberSaveable { mutableIntStateOf((filters.maxMinutes ?: 180).coerceIn(0,6000)) }
    val scrolling=remember { mutableStateMapOf<String,Boolean>() }
    val selected=when(mode) { "unlimited"->null;"three"->180;"five"->300;else->customMinutes }
    val valid=selected==null || selected in 1..6000
    val settled=scrolling.values.none { it }
    val fontScale=LocalDensity.current.fontScale
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=PageBackground) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=20.dp).testTag("duration-sheet"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("最长车程",style=MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("按单程列车运行时长筛选",style=MaterialTheme.typography.bodyMedium,color=Muted)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val options=listOf("unlimited" to "不限","three" to "3 小时","five" to "5 小时","custom" to "自定义")
                    val rows=if(maxWidth<340.dp || fontScale>1.15f)options.chunked(2) else listOf(options)
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row->
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach { (value,label)->
                                    Choice(label,mode==value,{
                                        if(mode!=value) { scrolling.clear();mode=value }
                                    },Modifier.weight(1f).testTag("duration-$value"),contentPadding=PaddingValues(horizontal=6.dp,vertical=12.dp))
                                }
                            }
                        }
                    }
                }
                Text(selected?.let { "最多 ${durationText(it)}" } ?: "不限车程",Modifier.testTag("duration-summary"),style=MaterialTheme.typography.titleLarge,color=Primary)
                if(mode=="custom") {
                    ContentCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("小时",style=MaterialTheme.typography.bodyMedium,color=Muted)
                                TimeWheel("车程小时",customMinutes/60,100,true,{hour->
                                    customMinutes=if(hour==100)6000 else hour*60+customMinutes%60
                                    if(hour==100)scrolling["minute"]=false
                                },wrap=false) { scrolling["hour"]=it }
                            }
                            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("分钟",style=MaterialTheme.typography.bodyMedium,color=Muted)
                                TimeWheel("车程分钟",customMinutes%60,if(customMinutes==6000)0 else 59,customMinutes!=6000,{minute->
                                    customMinutes=customMinutes/60*60+minute
                                }) { scrolling["minute"]=it }
                            }
                        }
                    }
                    Text(when {
                        !valid->"请至少选择 1 分钟"
                        customMinutes==6000->"100 小时时，分钟固定为 00"
                        else->"滑动选择小时和分钟"
                    },style=MaterialTheme.typography.bodySmall,color=if(valid)Muted else Amber)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                SecondaryButton("取消",onDismiss,Modifier.weight(1f))
                PrimaryButton("完成",{onApply(filters.copy(maxMinutes=selected))},valid && settled,Modifier.weight(1f).testTag("apply-duration"))
            }
        }
    }
}
