package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class SourceImage(val url: String, val description: String)
data class SourceDocument(val id: String, val title: String, val url: String, val content: String,
    val kind: String, val images: List<SourceImage> = emptyList())

class TavilyGuideSource internal constructor(private val key: () -> String?, private val endpoint: String,
    client: OkHttpClient) : GuideMaterialSource {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    constructor(key: () -> String?) : this(key,"https://api.tavily.com/search",OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(50,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).build())

    override suspend fun fetch(city: City, stage: (String) -> Unit): GuideMaterial {
        val apiKey = key()?.takeIf { it.startsWith("tvly-") && it.none(Char::isWhitespace) }
            ?: throw IOException("请先配置有效的 Tavily API Key")
        stage("Tavily 正在搜索${city.name}景点资料…")
        val places = search(city,"places","旅游 景点 介绍",apiKey)
        if (places.isEmpty()) throw IOException("暂未检索到${city.name}可用的简体中文景点资料，请稍后重试")
        stage("Tavily 正在搜索${city.name}美食资料…")
        val foods = search(city,"food","特色美食 小吃 介绍",apiKey)
        // The same page can contain both kinds of evidence; retain both roles with unique IDs.
        val docs = (places.take(5) + foods.take(5)).distinctBy { it.kind to it.url }
            .mapIndexed { i,d -> d.copy(id="s${i+1}") }
        val checked = LocalDate.now(BEIJING_ZONE).toString()
        return GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),
            docs.distinctBy { it.url }.map { GuideSource(it.title,it.url,checked) },docs)
    }
    private suspend fun search(city: City, kind: String, terms: String, apiKey: String): List<SourceDocument> {
        val payload = mapOf("query" to "${city.province.name} ${city.name} $terms 简体中文 -inurl:BIG5 -inurl:zh-hant -inurl:zh-tw", "topic" to "general",
            "search_depth" to "basic", "max_results" to 6, "include_answer" to false,
            "include_raw_content" to false,"include_images" to true,"include_image_descriptions" to true,
            "include_usage" to true,"country" to "china","language" to "zh-cn",
            "include_domains" to DOMAINS,"include_domains_mode" to "restrict","auto_parameters" to false)
        val request = Request.Builder().url(endpoint).header("Authorization","Bearer $apiKey")
            .post(Gson().toJson(payload).toRequestBody("application/json".toMediaType())).build()
        val response = client.newCall(request).boundedResponse(2 * 1024 * 1024)
        if (response.code !in 200..299) throw IOException(when(response.code) {
            401,403 -> "Tavily 密钥无效或无权限，请检查配置"
            402,432,433 -> "Tavily 搜索额度不足，请检查账户后重试"
            429 -> "Tavily 请求过于频繁，请稍后重试"
            else -> "Tavily 暂时无法检索（${response.code}），请稍后重试"
        })
        return try { parse(String(response.bytes,Charsets.UTF_8),city,kind) }
        catch (_: Exception) { throw IOException("Tavily 返回的资料格式不完整，请稍后重试") }
    }
    companion object {
        internal val DOMAINS = listOf("gov.cn","ctrip.com","mafengwo.cn","people.com.cn","xinhuanet.com","news.cn","cnr.cn","cctv.com")
        internal fun domesticHost(host: String) = DOMAINS.any { host == it || host.endsWith(".$it") }
        internal fun sourceUrl(url: String) = url.toHttpUrlOrNull()?.let {
            domesticHost(it.host) && SimplifiedGuidePolicy.urlAllowed(url) && it.username.isEmpty() && it.password.isEmpty() &&
                ((it.isHttps && it.port == 443) || (!it.isHttps && it.port == 80))
        } == true
        internal fun parse(raw: String, city: City, kind: String): List<SourceDocument> {
            val root = JsonParser.parseString(raw).asJsonObject
            require(root["results"]?.isJsonArray == true)
            val cityName = city.name.removeSuffix("市")
            return root.objects("results").take(6).mapNotNull { r ->
                if (!SimplifiedGuidePolicy.textAllowed(r.text("title")) || !SimplifiedGuidePolicy.textAllowed(r.text("content"))) return@mapNotNull null
                val title = r.text("title").take(180)
                val content = r.text("content").take(2400).trim()
                val url = r.text("url")
                if (!sourceUrl(url) || title.isBlank() || content.length < 30 || !(title+content).contains(cityName)) return@mapNotNull null
                val images = r.objects("images").mapNotNull { image ->
                    val description = image.text("description")
                    val imageUrl = image.text("url")
                    if (description.length in 5..300 && SimplifiedGuidePolicy.textAllowed(description) && SimplifiedGuidePolicy.urlAllowed(imageUrl) && GuideNetwork.isPhotoUrl(imageUrl) && !DECORATION.containsMatchIn(imageUrl))
                        SourceImage(imageUrl,description) else null
                }.take(5)
                SourceDocument("",title,url,content,kind,images)
            }.distinctBy { it.url }
        }
        internal val DECORATION = Regex("logo|footer|banner|icon|qrcode|beian|ewm|guohui|wxzftb",RegexOption.IGNORE_CASE)
    }
}
