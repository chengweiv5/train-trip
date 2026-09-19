@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.traintrip.app.ui

import android.os.Build
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.traintrip.core.SearchFilters

@Composable fun TimeFilterSheet(filters:SearchFilters,onDismiss:()->Unit,onApply:(SearchFilters)->Unit) {
    var start by rememberSaveable { mutableIntStateOf(filters.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(filters.endMinute) }
    val scrolling=remember { mutableStateMapOf<String,Boolean>() }
    val valid=start!=end
    val settled=scrolling.values.none { it }
    val fontScale=LocalDensity.current.fontScale
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=PageBackground) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=20.dp).testTag("time-sheet"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("选择出发时段",style=MaterialTheme.typography.titleLarge)
            Text("北京时间 · 所选日期每天适用",color=Muted,style=MaterialTheme.typography.bodyMedium)
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("${timeText(start)} – ${timeText(end)}",Modifier.testTag("time-summary"),color=Primary,style=MaterialTheme.typography.titleLarge)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val startWheels:@Composable (Modifier)->Unit={m->TimeWheels("开始",start,false,m,{start=it}) { key,moving->scrolling["start-$key"]=moving } }
                    val endWheels:@Composable (Modifier)->Unit={m->TimeWheels("结束",end,true,m,{end=it}) { key,moving->scrolling["end-$key"]=moving } }
                    if(maxWidth<340.dp || fontScale>1.15f) {
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { startWheels(Modifier.fillMaxWidth());endWheels(Modifier.fillMaxWidth()) }
                    } else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { startWheels(Modifier.weight(1f));endWheels(Modifier.weight(1f)) }
                }
                Text(when {
                    !valid->"开始和结束不能相同；不限时段请选“恢复全天”。"
                    start==0 && end==1440->"全天出发，不限制时间。"
                    end<start->"跨午夜：包含 ${timeText(start)} 之后和 ${timeText(end)} 之前的出发车次。"
                    else->"筛选 ${timeText(start)}（含）至 ${timeText(end)}（不含）出发的车次。"
                },color=if(valid) Muted else Amber,style=MaterialTheme.typography.bodyMedium)
                TextButton({start=0;end=1440},enabled=settled) { Text("恢复全天 00:00–24:00") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                SecondaryButton("取消",onDismiss,Modifier.weight(1f))
                PrimaryButton("完成",{onApply(filters.copy(startMinute=start,endMinute=end))},valid && settled,Modifier.weight(1f).testTag("apply-time"))
            }
        }
    }
}

@Composable private fun TimeWheels(label:String,value:Int,allow24:Boolean,modifier:Modifier,onChange:(Int)->Unit,onScroll:(String,Boolean)->Unit) {
    Surface(modifier,color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(label,style=MaterialTheme.typography.titleMedium,color=Primary)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("小时",style=MaterialTheme.typography.bodySmall,color=Muted)
                    TimeWheel("${label}小时",value/60,if(allow24) 24 else 23,true,{hour->onChange(if(hour==24) 1440 else hour*60+value%60)}) { onScroll("hour",it) }
                }
                Text(":",style=MaterialTheme.typography.titleLarge,color=Muted)
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("分钟",style=MaterialTheme.typography.bodySmall,color=Muted)
                    TimeWheel("${label}分钟",value%60,59,value!=1440,{minute->onChange(value/60*60+minute)}) { onScroll("minute",it) }
                }
            }
            if(value==1440) Text("24 点结束，分钟固定为 00",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
    }
}

@Composable private fun TimeWheel(label:String,value:Int,max:Int,enabled:Boolean,onChange:(Int)->Unit,onScroll:(Boolean)->Unit) {
    val change by rememberUpdatedState(onChange)
    val scroll by rememberUpdatedState(onScroll)
    val fontScale=LocalDensity.current.fontScale
    AndroidView(modifier=Modifier.fillMaxWidth().height((144*fontScale.coerceAtLeast(1f)).dp),factory={context->
        object:NumberPicker(context) {
            override fun dispatchTouchEvent(event:MotionEvent):Boolean {
                if(event.actionMasked==MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
                val handled=super.dispatchTouchEvent(event)
                if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
                return handled
            }
        }.apply {
            minValue=0
            maxValue=max
            wrapSelectorWheel=true
            descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setFormatter { "%02d".format(it) }
            contentDescription=label
            setOnValueChangedListener { _,_,next->change(next) }
            setOnScrollListener { _,state->scroll(state!=NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) }
        }
    },update={picker->
        picker.isEnabled=enabled
        picker.alpha=if(enabled) 1f else .45f
        if(picker.value!=value) picker.value=value
        if(Build.VERSION.SDK_INT>=29) {
            picker.textColor=Primary.toArgb()
            picker.textSize=22f*picker.resources.displayMetrics.density*fontScale
        }
    })
}
