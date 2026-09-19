package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cn.traintrip.app.DestinationState

@Composable fun DeepSeekSettingsDialog(state: DestinationState, onSave: (String, String) -> Unit,
    onRemoveDeepSeek: () -> Unit, onRemoveTavily: () -> Unit, onClose: () -> Unit) {
    var deepSeek by remember { mutableStateOf("") }
    var tavily by remember { mutableStateOf("") }
    fun clear() { deepSeek = ""; tavily = "" }
    AlertDialog(onDismissRequest = { clear(); onClose() },
        properties = DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        title = { Text("目的地内容设置") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tavily 搜索国内资料，DeepSeek 整理，成功后保存到本机。", style = MaterialTheme.typography.bodySmall)
                KeyInput("DeepSeek V4.1 Flash",state.configured,deepSeek,{ deepSeek=it },"deepseek-key")
                if (state.configured) TextButton({ clear(); onRemoveDeepSeek() }) { Text("移除 DeepSeek 密钥") }
                KeyInput("Tavily",state.tavilyConfigured,tavily,{ tavily=it },"tavily-key")
                if (state.tavilyConfigured) TextButton({ clear(); onRemoveTavily() }) { Text("移除 Tavily 密钥") }
                Text("首次整理或手动更新通常需要 2 次 Tavily 基础搜索和 1 次 DeepSeek 调用，会消耗各自额度。已缓存内容无需重复调用。", style = MaterialTheme.typography.bodySmall,color=Muted)
                Text("密钥仅加密保存在此设备。留空保留已有配置。", style = MaterialTheme.typography.bodySmall,color=Muted)
                state.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton({ val d=deepSeek; val t=tavily; clear(); onSave(d,t) }, Modifier.testTag("save-deepseek")) { Text("保存") } },
        dismissButton = { TextButton({ clear(); onClose() }) { Text("取消") } })
}

@Composable private fun KeyInput(provider: String,configured: Boolean,key: String,onChange: (String)->Unit,tag: String) {
    Text("$provider · ${if(configured) "已配置" else "未配置"}",style=MaterialTheme.typography.titleSmall)
    OutlinedTextField(key,onChange,label={ Text("$provider API Key") },singleLine=true,
        modifier=Modifier.fillMaxWidth().testTag(tag),visualTransformation=PasswordVisualTransformation(),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false))
}
