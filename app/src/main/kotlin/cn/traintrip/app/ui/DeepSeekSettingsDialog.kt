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

@Composable fun DeepSeekSettingsDialog(state: DestinationState, onSave: (String) -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { key = ""; onClose() },
        properties = DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        title = { Text("目的地内容设置") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DeepSeek V4.1 Flash", style = MaterialTheme.typography.titleMedium)
                Text("手机获取国内资料后交给 DeepSeek 整理，成功后保存到本机。首次整理和手动更新会产生 API 用量。", style = MaterialTheme.typography.bodySmall)
                Text(if (state.configured) "已配置密钥，留空保留现有配置" else "请填写个人 API Key", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("deepseek-key"), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
                Text("密钥仅加密保存在此设备。已缓存内容无需联网或重复调用。", style = MaterialTheme.typography.bodySmall, color = Muted)
                state.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.configured) TextButton({ key = ""; onRemove() }) { Text("移除密钥") }
            }
        }, confirmButton = { TextButton({ val input = key; key = ""; onSave(input) }, Modifier.testTag("save-deepseek")) { Text("保存") } },
        dismissButton = { TextButton({ key = ""; onClose() }) { Text("取消") } })
}
