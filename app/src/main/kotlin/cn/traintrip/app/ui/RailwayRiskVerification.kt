package cn.traintrip.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.traintrip.core.waitlist.RailwayRiskChallenge
import cn.traintrip.core.waitlist.RailwayRiskProof
import com.google.gson.JsonParser
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Official risk widget only. It receives no native session cookies, password or passenger data. */
@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun RailwayRiskVerification(
    challenge: RailwayRiskChallenge,
    onVerified: (RailwayRiskProof) -> Unit,
    onFailure: () -> Unit,
) {
    var web by remember(challenge.previewId) { mutableStateOf<WebView?>(null) }
    var completed by remember(challenge.previewId) { mutableStateOf(false) }
    var runtimeReady by remember(challenge.previewId) { mutableStateOf(false) }
    val latestVerified by rememberUpdatedState(onVerified)
    val latestFailure by rememberUpdatedState(onFailure)
    Column {
        Text("12306 官方安全验证 · 不会在此输入账号密码或自动提交订单", color = Muted)
        AndroidView(modifier = Modifier.fillMaxWidth().height(180.dp), factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                        if (request?.url?.host == "mobile.12306.cn" && response?.statusCode != 200)
                            view?.post { if (!completed) latestFailure() }
                    }
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        if (request?.isForMainFrame == true &&
                            request.url.toString() == "https://kyfw.12306.cn/otn/view/lineUp_toPay.html") return null
                        if (request != null && riskResourceAllowed(request.url)) return null
                        return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                            emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                        handler?.cancel()
                        view?.post { if (!completed) latestFailure() }
                    }
                }
                postDelayed({
                    if (!completed && !runtimeReady) {
                        completed = true
                        latestFailure()
                    }
                }, 25_000)
                loadDataWithBaseURL("https://kyfw.12306.cn/otn/view/lineUp_toPay.html",
                    riskHtml(challenge), "text/html", "UTF-8", null)
                web = this
            }
        })
    }
    LaunchedEffect(challenge.previewId, web) {
        val view = web ?: return@LaunchedEffect
        repeat(120) {
            if (completed) return@LaunchedEffect
            delay(1000)
            view.evaluateJavascript("JSON.stringify(window.railwayProof ? window.railwayProof() : null)") { result ->
                if (!completed) runCatching {
                    val text = JsonParser.parseString(result).asString
                    val proof = JsonParser.parseString(text).asJsonObject
                    if (proof.get("ua")?.asString?.isNotBlank() == true) runtimeReady = true
                    if (proof.get("failed")?.asBoolean == true) {
                        completed = true
                        latestFailure()
                    }
                    if (proof.get("ready")?.asBoolean == true) {
                        val ua = proof.get("ua").asString
                        val session = proof.get("session").asString
                        val signature = proof.get("sig").asString
                        if (ua.isNotBlank() && ua.length <= 200_000 &&
                            (!challenge.requiresSlide || (session.isNotBlank() && signature.isNotBlank()))) {
                            completed = true
                            latestVerified(RailwayRiskProof(challenge.previewId, ua, session, signature, Instant.now()))
                        }
                    }
                }
            }
        }
        if (!completed) latestFailure()
    }
    DisposableEffect(challenge.previewId) {
        onDispose {
            completed = true
            web?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            web = null
        }
    }
}

internal fun riskResourceAllowed(uri: Uri): Boolean {
    if (uri.scheme != "https" || uri.port !in setOf(-1, 443) || uri.userInfo != null) return false
    return when (uri.host) {
        "mobile.12306.cn" -> uri.path?.startsWith("/otsmobile/antcaptcha/") == true
        "g.alicdn.com", "aeis.alicdn.com", "at.alicdn.com", "img.alicdn.com", "gtms02.alicdn.com",
        "cf.aliyun.com", "cf2.aliyun.com", "ynuf.aliapp.org", "diablo.alibaba.com" -> true
        else -> false
    }
}

private fun riskHtml(challenge: RailwayRiskChallenge): String {
    val token = Gson().toJson(challenge.token)
    val hour = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMddHH"))
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' https://mobile.12306.cn https://g.alicdn.com https://aeis.alicdn.com https://cf.aliyun.com https://cf2.aliyun.com https://ynuf.aliapp.org https://diablo.alibaba.com; connect-src https://mobile.12306.cn https://cf.aliyun.com https://cf2.aliyun.com https://ynuf.aliapp.org https://diablo.alibaba.com; img-src data: https://g.alicdn.com https://at.alicdn.com https://img.alicdn.com https://gtms02.alicdn.com https://cf.aliyun.com https://diablo.alibaba.com; style-src 'unsafe-inline'; frame-src https://cf.aliyun.com">
<style>body{margin:12px;font:14px sans-serif}#slide{min-height:48px}p{line-height:1.5}</style></head>
<body><p id="status">正在加载官方验证组件…</p><div id="slide"></div>
<script>var railwaySession="",railwaySig="",railwayStarted=Date.now();window.railwayProof=function(){
var ua="";try{ua=window.json_ua.toString()}catch(e){}
return {ready:!!ua&&(${!challenge.requiresSlide}||!!railwaySession),failed:!ua&&Date.now()-railwayStarted>20000,ua:ua,session:railwaySession,sig:railwaySig};};
function startRailwayWidget(){try{
if(${challenge.requiresSlide}) {new noCaptcha({renderTo:"#slide",appkey:"FFFF0N000000000085DE",scene:"nc_login",token:$token,
customWidth:280,trans:{key1:"code0"},elementID:[],is_Opt:0,language:"cn",isEnabled:true,timeout:3000,times:5,apimap:{},
callback:function(r){railwaySession=r.csessionid;railwaySig=r.sig;document.getElementById("status").textContent="验证通过，请回到原生按钮确认";}});}
document.getElementById("status").textContent=${if (challenge.requiresSlide) "\"请手动完成官方滑块验证\"" else "\"正在核验设备，请稍候…\""};
}catch(e){document.getElementById("status").textContent="官方组件未能加载，请返回重试";}}</script>
<script src="https://mobile.12306.cn/otsmobile/antcaptcha/ua_rds.js?t=$hour"></script>
<script src="https://g.alicdn.com/sd/ncpc/nc.js?t=2019121212" onload="startRailwayWidget()"></script>
</body></html>"""
}
