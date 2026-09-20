package cn.traintrip.core

import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReleaseVersion(val major:Int,val minor:Int,val patch:Int):Comparable<ReleaseVersion> {
    override fun compareTo(other:ReleaseVersion)=compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    override fun toString()="$major.$minor.$patch"
    companion object { fun parse(value:String):ReleaseVersion? {
        val m=Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(value) ?: return null
        return runCatching { ReleaseVersion(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt()) }.getOrNull()
    } }
}
data class AppRelease(val version:ReleaseVersion,val url:String,val summary:String,val downloadable:Boolean)
fun interface UpdateSource { suspend fun latest():AppRelease }
const val PROJECT_URL="https://github.com/chengweiv5/train-trip"
const val RELEASES_URL="$PROJECT_URL/releases"
fun releaseUrl(version:String)="$PROJECT_URL/releases/tag/v${version.removePrefix("v")}"
fun trustedReleaseUrl(url:String):Boolean=runCatching {
    val u=url.toHttpUrl()
    u.isHttps && u.host=="github.com" && u.port==443 && u.username.isEmpty() && u.password.isEmpty() &&
        (u.encodedPath=="/chengweiv5/train-trip" || u.encodedPath=="/chengweiv5/train-trip/releases" || u.encodedPath.startsWith("/chengweiv5/train-trip/releases/"))
}.getOrDefault(false)
class GitHubUpdateSource(private val endpoint:String="https://api.github.com/repos/chengweiv5/train-trip/releases",
    private val client:OkHttpClient=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).followRedirects(false).build()):UpdateSource {
    override suspend fun latest():AppRelease=withContext(Dispatchers.IO) { withTimeout(30000) {
        val base=endpoint.toHttpUrl()
        var next:HttpUrl?=base.newBuilder().addQueryParameter("per_page","100").build()
        val visited=mutableSetOf<String>();val releases=mutableListOf<AppRelease>()
        while(next!=null) {
            val url=next
            if(!visited.add(url.toString()) || visited.size>100)throw IOException("版本列表无法完整读取")
            val r=client.newCall(Request.Builder().url(url).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").build()).releasePage()
            run {
                val array=try { JsonParser.parseString(r.body).asJsonArray } catch(_:Exception){throw IOException("版本信息格式异常")}
                for(e in array) {
                    val j=e.asJsonObject
                    if(j["draft"].asBoolean || j["prerelease"].asBoolean)continue
                    val version=ReleaseVersion.parse(j["tag_name"].asString) ?: continue
                    val target=j["html_url"].asString
                    if(!trustedReleaseUrl(target) || target!=releaseUrl(version.toString()))throw IOException("版本链接无效")
                    val names=j.getAsJsonArray("assets").map { it.asJsonObject["name"].asString }.toSet()
                    val apk="train-trip-v$version-release.apk"
                    val summary=j["body"]?.takeUnless { it.isJsonNull }?.asString.orEmpty().lineSequence().filter { it.isNotBlank() }.take(3).joinToString("\n").take(400)
                    releases+=AppRelease(version,target,summary,apk in names && "$apk.sha256" in names)
                }
                val link=r.link.orEmpty().split(',').firstOrNull { it.contains("rel=\"next\"") }
                next=link?.let { Regex("<([^>]+)>").find(it)?.groupValues?.get(1)?.toHttpUrl() ?: throw IOException("版本分页无效") }
                next?.let { if(it.scheme!=base.scheme || it.host!=base.host || it.port!=base.port || it.encodedPath!=base.encodedPath || it.username.isNotEmpty() || it.password.isNotEmpty())throw IOException("版本分页链接无效") }
            }
        }
        releases.maxByOrNull { it.version } ?: throw IOException("暂未取得正式版本信息")
    } }
}
private data class ReleasePage(val body:String,val link:String?)
private suspend fun Call.releasePage():ReleasePage=suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object:Callback {
        override fun onFailure(call:Call,e:IOException) {
            if(continuation.isActive)continuation.resumeWithException(IOException("网络或 GitHub 暂不可用，请稍后重试"))
        }
        override fun onResponse(call:Call,response:Response) {
            response.use { r ->
                try {
                    if(!r.isSuccessful)throw IOException("网络或 GitHub 暂不可用，请稍后重试")
                    val body=r.body ?: throw IOException("版本信息为空")
                    val source=body.source();source.request(2*1024*1024+1L)
                    if(source.buffer.size>2*1024*1024)throw IOException("版本信息过大")
                    val page=ReleasePage(body.string(),r.header("Link"))
                    if(continuation.isActive)continuation.resume(page)
                } catch(e:Exception) {
                    if(continuation.isActive)continuation.resumeWithException(IOException("版本信息读取失败，请稍后重试"))
                }
            }
        }
    })
}
