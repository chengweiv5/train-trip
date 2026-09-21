package cn.traintrip.app.ui

import android.os.Build
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable internal fun TimeWheel(label:String,value:Int,max:Int,enabled:Boolean,onChange:(Int)->Unit,wrap:Boolean=true,onScroll:(Boolean)->Unit) {
    val change by rememberUpdatedState(onChange)
    val scroll by rememberUpdatedState(onScroll)
    DisposableEffect(Unit) { onDispose { scroll(false) } }
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
            wrapSelectorWheel=wrap
            descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setFormatter { "%02d".format(it) }
            contentDescription=label
            setOnValueChangedListener { _,_,next->change(next) }
            setOnScrollListener { _,state->scroll(state!=NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) }
        }
    },update={picker->
        if(picker.maxValue!=max) picker.maxValue=max
        picker.wrapSelectorWheel=wrap
        picker.isEnabled=enabled
        picker.alpha=if(enabled) 1f else .45f
        if(picker.value!=value) picker.value=value
        if(Build.VERSION.SDK_INT>=29) {
            picker.textColor=Primary.toArgb()
            picker.textSize=22f*picker.resources.displayMetrics.density*fontScale
        }
    })
}
