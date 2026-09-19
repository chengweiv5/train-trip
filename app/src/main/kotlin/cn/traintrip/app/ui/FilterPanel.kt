package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun FilterPanel(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=PageBackground) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Surface(color=Color.White) {
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onDismiss,Modifier.width(64.dp).heightIn(min=48.dp)) { Text("取消") }
                        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.width(64.dp))
                    }
                }
                content()
            }
        }
    }
}
