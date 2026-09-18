package cn.traintrip.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.traintrip.app.*
import cn.traintrip.core.OfficialTicketSource

@Composable fun TrainTripApp(vm:AppViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val screenState=rememberSaveableStateHolder()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP) vm.pauseForegroundWork() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val copy:(String)->Unit={text->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("火车行程",text))
        Toast.makeText(context,"已复制行程信息",Toast.LENGTH_SHORT).show()
    }
    fun openOfficial() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(OfficialTicketSource.INIT))) }
            .onFailure { Toast.makeText(context,"未找到浏览器，请手动打开 12306",Toast.LENGTH_LONG).show() }
    }
    BackHandler(s.page!=Page.FILTERS) { if(s.page==Page.DETAIL) vm.showResults() else vm.showFilters() }
    Surface(Modifier.fillMaxSize(),color=Cream) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            screenState.SaveableStateProvider(s.page.name) { when(s.page) {
                Page.FILTERS->FiltersScreen(s,vm::updateFilters,{vm.search()})
                Page.RESULTS->ResultsScreen(s,vm::showFilters,vm::showCity,{vm.search()},vm::stopSearch,{vm.search(resume=true)},{vm.search(retryFailed=true)},vm::sortCities)
                Page.DETAIL->DetailScreen(s,vm::showResults,vm::select,vm::recheck,copy)
            } }
        }
    }
    s.notice?.let { message->AlertDialog(onDismissRequest=vm::dismissNotice,title={Text(if(s.handoffReady) "前往 12306" else "余票核验结果")},text={Text(message)},
        confirmButton={TextButton({if(s.handoffReady) {s.handoffText?.let(copy);openOfficial()};vm.dismissNotice()}) { Text(if(s.handoffReady) "复制行程并打开 12306" else "知道了") }},
        dismissButton={if(!s.handoffReady && s.handoffText!=null) TextButton({copy(s.handoffText!!);openOfficial();vm.dismissNotice()}) { Text("打开 12306 手动查") } else TextButton(vm::dismissNotice) { Text("留在这里") }}) }
}
