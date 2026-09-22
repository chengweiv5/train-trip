package cn.traintrip.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.traintrip.app.DestinationState

private enum class SettingsPage { MENU, MODEL, SEARCH }

@Composable fun SettingsScreen(state: DestinationState, onClose: () -> Unit,
    onSaveModel: (String, String, () -> Unit) -> Unit,
    onSaveSearch: (String, () -> Unit) -> Unit,
    onRemoveModelKey: (() -> Unit) -> Unit, onRemoveSearchKey: (() -> Unit) -> Unit,
    onClearFeedback: () -> Unit = {},onOffline:()->Unit={},onAbout:()->Unit={},offlineBytes:Long=0,offlineCount:Int=0,applySafeInsets:Boolean=true) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MENU) }
    fun back() {
        if (state.settingsBusy) return
        onClearFeedback()
        if (page == SettingsPage.MENU) onClose() else page = SettingsPage.MENU
    }
    BackHandler { back() }
    SecureSettingsWindow(page != SettingsPage.MENU)
    Surface(Modifier.fillMaxSize().testTag("settings-screen"), color = PageBackground) {
        Column(Modifier.fillMaxSize().then(if(applySafeInsets)Modifier.safeDrawingPadding() else Modifier).imePadding()) {
            AppTopBar(when(page) {SettingsPage.MENU->"设置";SettingsPage.MODEL->"大模型设置";SettingsPage.SEARCH->"搜索引擎设置"},
                onBack={back()},enabled=!state.settingsBusy,backTag="settings-back")
            key(page) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if (page == SettingsPage.MENU) {
                        Text("管理目的地内容服务",style=MaterialTheme.typography.bodyMedium,color=Muted)
                        ContentCard(Modifier.fillMaxWidth()) {
                        SettingsRow("大模型","DeepSeek · ${state.modelName}",state.configured,"settings-model") {
                            onClearFeedback();page=SettingsPage.MODEL
                        }
                        HorizontalDivider(color=Line)
                        SettingsRow("搜索引擎","豆包搜索 · 网页与图片",state.searchConfigured,"settings-search") {
                            onClearFeedback();page=SettingsPage.SEARCH
                        }
                        }
                        Text("本机内容",style=MaterialTheme.typography.bodySmall,color=Muted)
                        ContentCard(Modifier.fillMaxWidth()) {
                            TextButton(onOffline,Modifier.fillMaxWidth().heightIn(min=60.dp).testTag("settings-offline")) {
                                Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start) {
                                    Text("离线内容");Text("${offlineCount} 个已下载城市",style=MaterialTheme.typography.bodySmall,color=Muted)
                                };Text(storageSize(offlineBytes),style=MaterialTheme.typography.bodySmall,color=Muted);UiIcon("next")
                            }
                        }
                        ContentCard(Modifier.fillMaxWidth()) {
                            TextButton(onAbout,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("settings-about")) {
                                Text("关于与更新",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Start);UiIcon("next")
                            }
                        }
                        state.settingsMessage?.let { Hint(it) }
                        state.settingsError?.let { Hint(it,true) }
                        Text("密钥加密保存在本机。已缓存的城市内容可继续离线查看。",style=MaterialTheme.typography.bodySmall,color=Muted)
                    } else {
                        val modelPage=page == SettingsPage.MODEL
                        val provider=if(modelPage) "DeepSeek" else "豆包搜索"
                        val configured=if(modelPage) state.configured else state.searchConfigured
                        var model by remember { mutableStateOf(state.modelName) }
                        var key by remember { mutableStateOf("") }
                        Text(provider,style=MaterialTheme.typography.titleLarge)
                        Text(if(modelPage) "整理目的地资料，生成中文介绍。" else "检索国内公开的景点与美食资料。",
                            style=MaterialTheme.typography.bodyMedium,color=Muted)
                        if(modelPage) {
                            OutlinedTextField(model,{model=it},label={Text("模型名称")},singleLine=true,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                modifier=Modifier.fillMaxWidth().testTag("settings-model-name"),enabled=!state.settingsBusy,
                                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,autoCorrectEnabled=false))
                            Text("填写 DeepSeek 账户支持的模型 ID，默认 deepseek-flash。",style=MaterialTheme.typography.bodySmall,color=Muted)
                        } else Text("分别检索景点、美食和路线攻略，缺图时按条目补查",style=MaterialTheme.typography.bodyMedium,color=Muted)
                        Text(if(configured) "密钥已配置；留空保留现有密钥" else "尚未配置密钥",style=MaterialTheme.typography.bodySmall,color=Muted)
                        OutlinedTextField(key,{key=it},label={Text("API Key")},singleLine=true,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            modifier=Modifier.fillMaxWidth().testTag(if(modelPage) "deepseek-key" else "doubao-search-key"),
                            enabled=!state.settingsBusy,visualTransformation=PasswordVisualTransformation(),
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false))
                        Text(if(modelPage) "保存只更新配置，下次整理使用所填模型。调用按服务商实际用量计费。"
                            else "首次整理或手动更新会消耗搜索额度。已有缓存无需再次搜索。",
                            style=MaterialTheme.typography.bodySmall,color=Muted)
                        state.settingsError?.let { Hint(it,true) }
                        PrimaryButton(if(state.settingsBusy) "正在保存…" else "保存",{
                            val done={key="";page=SettingsPage.MENU;Unit}
                            if(modelPage) onSaveModel(model,key,done) else onSaveSearch(key,done)
                        },enabled=!state.settingsBusy,modifier=Modifier.testTag("save-settings"))
                        if(configured) TextButton({
                            val done={key="";page=SettingsPage.MENU;Unit}
                            if(modelPage) onRemoveModelKey(done) else onRemoveSearchKey(done)
                        },enabled=!state.settingsBusy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("remove-settings-key")) {
                            Text("移除 $provider 密钥",color=Amber)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsRow(title:String,detail:String,configured:Boolean,tag:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).clickable(onClick=onClick).testTag(tag).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        Text(if(configured) "已配置" else "未配置",style=MaterialTheme.typography.bodySmall,color=if(configured) ContentReady else Muted)
        UiIcon("next",color=Subtle)
    }
}

@Composable private fun SecureSettingsWindow(enabled: Boolean) {
    val context=LocalContext.current
    DisposableEffect(enabled,context) {
        val window=context.activity()?.window
        val alreadySecure=window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)!=0
        if(enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if(enabled && !alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
private tailrec fun Context.activity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
