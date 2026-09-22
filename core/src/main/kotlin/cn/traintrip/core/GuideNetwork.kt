package cn.traintrip.core

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** No credential is ever attached to a source or image request. */
class GuideNetwork(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
    .callTimeout(35, TimeUnit.SECONDS).build()) {
    suspend fun html(url: String): String = String(download(url, false), Charsets.UTF_8)
    suspend fun photo(url: String): ByteArray = download(url, true)

    private suspend fun download(initial: String, photo: Boolean): ByteArray {
        var url = initial
        repeat(4) {
            if (!SimplifiedGuidePolicy.urlAllowed(url) || !(if (photo) isPhotoUrl(url) else isPageUrl(url))) throw IOException("资料链接不受支持")
            val request = Request.Builder().url(url).header("User-Agent", if (photo) "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36" else "Mozilla/5.0").build()
            val result = client.newCall(request).boundedResponse((if (photo) 10 else 5) * 1024 * 1024)
            if (result.code in 300..399) {
                url = url.toHttpUrlOrNull()?.resolve(result.location.orEmpty())?.toString() ?: throw IOException("资料链接重定向失败")
                if (url.toHttpUrlOrNull()?.host == "verify.ctrip.com") throw IOException("携程暂时要求网页验证，无法自动读取。请稍后重试；本次未进入内容整理。")
            } else {
                if (result.code !in 200..299) throw IOException("资料网站暂时不可用（${result.code}），请稍后重试")
                if (photo && !result.contentType.startsWith("image/")) throw IOException("未取得有效城市图片")
                return result.bytes
            }
        }
        throw IOException("资料网站重定向过多，请稍后重试")
    }

    companion object {
        fun isPageUrl(url: String): Boolean = url.toHttpUrlOrNull()?.let {
            it.isHttps && it.host == "you.ctrip.com" && it.username.isEmpty() && it.password.isEmpty() && it.port == 443
        } == true
        fun isPhotoUrl(url: String): Boolean = url.toHttpUrlOrNull()?.let {
            it.isHttps && (TavilyGuideSource.domesticHost(it.host) || it.host.endsWith(".c-ctrip.com")) && it.username.isEmpty() && it.password.isEmpty() && it.port == 443
        } == true
    }
}

internal data class GuideHttpResponse(val code: Int, val bytes: ByteArray, val location: String?, val contentType: String)
internal suspend fun Call.boundedResponse(limit: Int): GuideHttpResponse = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(IOException("网络连接失败，请稍后重试"))
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                try {
                    val body = it.body ?: throw IOException("服务响应为空")
                    if (body.contentLength() > limit) throw IOException("服务响应过大")
                    val output = java.io.ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (output.size() <= limit) {
                            val n = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    }
                    val bytes = output.toByteArray()
                    if (bytes.size > limit) throw IOException("服务响应过大")
                    val result = GuideHttpResponse(it.code, bytes, it.header("Location"), it.header("Content-Type").orEmpty())
                    if (cont.isActive) cont.resume(result)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(IOException("读取服务响应失败"))
                }
            }
        }
    })
}
