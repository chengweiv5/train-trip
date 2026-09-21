package cn.traintrip.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import cn.traintrip.app.R
import cn.traintrip.app.*
import cn.traintrip.core.*

@Composable fun AboutScreen(state:UpdateState,onBack:()->Unit,onCheck:()->Unit) {
    val context=LocalContext.current
    val version=remember { context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty() }
    var failedUrl by remember { mutableStateOf<String?>(null) }
    fun open(url:String) { if(trustedReleaseUrl(url))runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri())) }.onFailure { failedUrl=url } }
    val release=state.release
    val notesUrl=release?.takeIf { !state.checking && state.error==null && it.version==ReleaseVersion.parse(version) }?.url ?: RELEASES_URL
    val newer=release!=null && ReleaseVersion.parse(version)?.let { release.version>it }==true
    Scaffold(containerColor=PageBackground,contentWindowInsets=WindowInsets(0,0,0,0),topBar={AppTopBar("关于与更新",onBack)}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineSmall)
                Text("先看哪里有票，再决定去哪里",style=MaterialTheme.typography.bodySmall,color=Muted)
                Text("当前版本 v$version",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            ContentCard(Modifier.fillMaxWidth()) {
                Text(when {state.checking->"正在检查更新";state.error!=null->"暂时无法检查更新";release==null->"尚未检查更新";newer->"${if(state.historical)"上次发现" else "发现"}新版本 ${release.version}";state.historical->"上次检查未发现更新";else->"已是最新正式版"},
                    Modifier.testTag("update-status"),style=MaterialTheme.typography.titleMedium,color=if(state.error!=null)Amber else Ink)
                if(state.checking)LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=Muted) }
                state.historyError?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=Amber) }
                if(newer && !state.checking) {
                    if(release.summary.isNotBlank())Text(release.summary,style=MaterialTheme.typography.bodyMedium)
                    if(!release.downloadable)Hint("新版本暂未提供完整安装包")
                }
                if(state.error!=null)state.completedAt?.let { Text("上次检查 ${formatTime(it)} · 未成功",style=MaterialTheme.typography.bodySmall,color=Muted) }
                state.checkedAt?.let { Text("上次成功检查 ${formatTime(it)}",style=MaterialTheme.typography.bodySmall,color=Muted) }
                PrimaryButton(when {state.checking->"检查中…";state.error!=null->"重新检查";newer&&release?.downloadable==true->"前往 GitHub 下载";newer->"查看发布说明";else->"检查更新"},
                    {if(newer && state.error==null)open(release.url) else onCheck()},!state.checking,Modifier.testTag("check-update"))
                if(newer && state.error==null)TextButton(onCheck,enabled=!state.checking){Text("再次检查")}
            }
            ContentCard(Modifier.fillMaxWidth()) {
                TextButton({open(notesUrl)},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("更新说明",Modifier.weight(1f));UiIcon("next")}
                HorizontalDivider(color=Line)
                TextButton({open(PROJECT_URL)},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("GitHub 项目",Modifier.weight(1f));UiIcon("next")}
            }
            Text("发现新版本时，可前往 GitHub 下载正式安装包。安装由系统确认。",style=MaterialTheme.typography.bodySmall,color=Muted)
        }
    }
    failedUrl?.let { url -> AlertDialog(onDismissRequest={failedUrl=null},title={Text("暂时无法打开发布页面")},text={Text("可以重试，或复制链接后在浏览器打开。")},
        confirmButton={TextButton({failedUrl=null;open(url)}){Text("重试")}},dismissButton={TextButton({
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("发布链接",url));failedUrl=null
        }){Text("复制链接")}}) }
}
