package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DoubaoGuideSource internal constructor(private val key: () -> String?, private val endpoint: String,
    client: OkHttpClient) : GuideMaterialSource, GuidePhotoSource {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    constructor(key: () -> String?) : this(key, ENDPOINT, OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(50, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build())

    private val requestLock = Mutex()
    private var lastRequestNanos = 0L

    private fun credential(): String = key()?.takeIf { runCatching { validateDoubaoSearchKey(it) }.isSuccess }
        ?: throw IOException("请先配置有效的豆包搜索 API Key")

    override suspend fun fetch(city: City, stage: (String) -> Unit): GuideMaterial {
        val apiKey = credential()
        stage("豆包正在搜索${city.name}旅行游记…")
        val places = search(city, "places", "旅游 游记 景点", apiKey,true)
        val placeDocs = if (places.size >= 3) places else places + optional { search(city,"places","旅游攻略 必去景点 游记",apiKey) }.orEmpty()
        if (placeDocs.isEmpty()) throw IOException("暂未检索到${city.name}可用的简体中文旅行游记，请稍后重试")
        stage("豆包正在搜索${city.name}美食体验…")
        val foodDocs = optional {
            val first = search(city, "food", "美食 小吃 体验", apiKey,true)
            if (first.size >= 3) first else first + optional { search(city,"food","特色美食 小吃 攻略",apiKey) }.orEmpty()
        }.orEmpty()
        val routeGroups = listOf("一日游 攻略", "两天一夜 自由行 游记").map { terms ->
            stage("豆包正在搜索${city.name}${terms.substringBefore(' ')}攻略…")
            optional { search(city,"routes",terms,apiKey,true) }.orEmpty().let { selectDocuments(it) }
        }
        // Reserve room for both day lengths instead of letting the first query consume every slot.
        val routeDocs = (0 until 6).flatMap { index -> routeGroups.mapNotNull { it.getOrNull(index) } }
        val documents = (selectDocuments(placeDocs) + selectDocuments(foodDocs) + selectDocuments(routeDocs,sort = false))
            .mapIndexed { index, doc -> doc.copy(id = "s${index + 1}") }
        val checked = LocalDate.now(BEIJING_ZONE).toString()
        return GuideMaterial(city.id, city.name, city.province.name, emptyList(), emptyList(),
            documents.distinctBy { it.url }.map { GuideSource(it.title, it.url, checked) }, documents)
    }

    private suspend fun search(city: City, kind: String, terms: String, apiKey: String, targeted: Boolean = false): List<SourceDocument> {
        val filters = mutableMapOf<String,Any>("NeedContent" to true, "NeedUrl" to true)
        if (targeted) filters["Sites"] = "you.ctrip.com|www.mafengwo.cn|www.qyer.com|post.smzdm.com|www.sina.cn|www.xiaohongshu.com"
        val result = request(mapOf("Query" to "${city.name}$terms", "SearchType" to "web", "Count" to 10,
            "Filter" to filters, "ContentFormats" to "text"), apiKey)
        return try { documents(result, city, kind) }
        catch (_: Exception) { throw IOException("豆包搜索返回的资料格式不完整，请稍后重试") }
    }

    override suspend fun fetch(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates {
        val apiKey = runCatching { credential() }.getOrNull() ?: return PhotoCandidates(emptyList())
        require(city.id == guide.cityId && city.name == guide.name)
        val photos = mutableListOf<DestinationPhoto>()
        var failed = false
        for (subject in GuidePhotoPolicy.missing(guide)) {
            stage("豆包正在搜索${subject.name}的图片…")
            for (query in GuidePhotoMatching.queries(city,subject)) {
                val found = optional {
                    val result = request(mapOf("Query" to query, "SearchType" to "image", "Count" to 5,
                        "Filter" to mapOf("ImageWidthMin" to 600, "ImageHeightMin" to 400)), apiKey)
                    images(result, city, subject)
                }
                if (found == null) failed = true else photos += found
                if (!found.isNullOrEmpty()) break
            }
        }
        return PhotoCandidates(GuidePhotoPolicy.candidates(guide, photos), failed)
    }

    private suspend fun request(payload: Map<String, Any>, apiKey: String): JsonObject = requestLock.withLock {
        repeat(3) { attempt ->
            val interval = 1_100L - (System.nanoTime() - lastRequestNanos) / 1_000_000
            if (interval > 0) delay(interval)
            if (attempt > 0) delay(attempt * 1_500L)
            lastRequestNanos = System.nanoTime()
            val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $apiKey")
                .post(Gson().toJson(payload).toRequestBody("application/json".toMediaType())).build()
            val response = client.newCall(request).boundedResponse(4 * 1024 * 1024)
            val raw = String(response.bytes, Charsets.UTF_8)
            val limited = response.code == 429 || runCatching {
                val error = JsonParser.parseString(raw).asJsonObject.obj("ResponseMetadata").obj("Error")
                error.text("Code") in listOf("700429","rate_limit_exceeded") || error.text("CodeN") == "700429"
            }.getOrDefault(false)
            if (limited && attempt < 2) return@repeat
            if (response.code !in 200..299) throw failure(response.code.toString())
            return@withLock try { result(raw) }
                catch (e: IOException) { throw e }
                catch (_: Exception) { throw IOException("豆包搜索返回的资料格式不完整，请稍后重试") }
        }
        throw failure("429")
    }

    companion object {
        const val ENDPOINT = "https://open.feedcoopapi.com/search_api/web_search"
        private fun failure(code: String) = IOException(when (code) {
            "401", "403", "10401", "10403" -> "豆包搜索密钥无效或无权限，请检查配置"
            "402", "10406", "10408", "10410", "10412" -> "豆包搜索额度不足或服务未开通，请检查账户后重试"
            "10402", "10409" -> "请使用已开通豆包搜索 Custom 版的 API Key"
            "429", "700429", "rate_limit_exceeded" -> "豆包搜索请求过于频繁，请稍后重试"
            else -> "豆包搜索暂时无法检索，请稍后重试"
        })
        internal fun result(raw: String): JsonObject {
            val root = JsonParser.parseString(raw).asJsonObject
            require(root["ResponseMetadata"]?.isJsonObject == true)
            val error = root.obj("ResponseMetadata")["Error"]
            if (error != null && !error.isJsonNull) {
                require(error.isJsonObject)
                throw failure(error.asJsonObject.text("Code").ifBlank { error.asJsonObject.text("CodeN") })
            }
            require(root["Result"]?.isJsonObject == true)
            return root.obj("Result")
        }
        internal fun parse(raw: String, city: City, kind: String) = documents(result(raw), city, kind)
        private fun documents(result: JsonObject, city: City, kind: String): List<SourceDocument> {
            require(result["WebResults"]?.isJsonArray == true)
            return result.objects("WebResults").take(10).mapNotNull { item ->
                val title = item.text("Title")
                val rawContent = item.text("Content")
                val url = item.text("Url")
                if (!GuideSearchPolicy.primaryArticle(url) || title.isBlank() || rawContent.length < 30 ||
                    !SimplifiedGuidePolicy.textAllowed(title) || !SimplifiedGuidePolicy.textAllowed(rawContent)) return@mapNotNull null
                val content = rawContent.take(6000).trim()
                if (!(title + content).contains(city.name.removeSuffix("市"))) return@mapNotNull null
                // Directory rows cannot satisfy the decoder's continuous-sentence evidence contract.
                if (kind != "routes" && !Regex("[^。！？\\n]{20,260}[。！？]").containsMatchIn(content)) return@mapNotNull null
                val images = item.objects("InlineImages").mapNotNull { image ->
                    val imageUrl = image.text("ImageUrl")
                    val alt = image.text("Alt")
                    if (supportedPhoto(imageUrl) && alt.length in 5..300 && SimplifiedGuidePolicy.textAllowed(alt))
                        SourceImage(imageUrl, alt) else null
                }.distinctBy { it.url }.take(12)
                SourceDocument("", title.take(180), url, content, kind, images)
            }.distinctBy { it.url }
        }
        private fun selectDocuments(documents: List<SourceDocument>, sort: Boolean = true): List<SourceDocument> {
            val counts = mutableMapOf<String,Int>()
            val unique = documents.distinctBy { it.url }
            return (if(sort)unique.sortedByDescending { GuideSearchPolicy.community(it.url) } else unique).filter {
                val site = GuideSearchPolicy.site(it.url)
                val count = counts.getOrDefault(site,0)
                if (count >= 2) false else { counts[site] = count + 1; true }
            }.take(6)
        }
        internal fun images(result: JsonObject, city: City, subject: PhotoSubject): List<DestinationPhoto> {
            require(result["ImageResults"]?.isJsonArray == true)
            return result.objects("ImageResults").take(5).mapNotNull { item ->
                val image = item.obj("Image")
                val title = item.text("Title")
                val description = image.obj("Features").text("Description")
                val evidence = "$title $description".trim()
                val url = image.text("Url")
                val source = item.text("Url")
                if (!GuideSearchPolicy.sourceUrl(source) || !supportedPhoto(url) ||
                    !SimplifiedGuidePolicy.textAllowed(evidence) || evidence.length !in 5..600 ||
                    (subject.kind != "food" && !evidence.contains(city.name.removeSuffix("市"))) || !GuidePhotoMatching.mentions(evidence,subject.name) ||
                    image.text("Width").toIntOrNull() !in 600..20000 || image.text("Height").toIntOrNull() !in 400..20000 ||
                    image.text("BlurDes") == "模糊" ||
                    Regex("数据图|截图|海报|艺术画|CG|地图|示意图|二维码|标志|图标").containsMatchIn(evidence + image.obj("Features").text("StyleType")) ||
                    (subject.kind == "food" && Regex("店面|门店|门头|环境|菜单|招牌|大厅|餐厅外观").containsMatchIn(evidence))) return@mapNotNull null
                sourcedPhoto(url, if(subject.kind == "food") "${subject.name} · 菜品参考图" else "${city.name} · ${subject.name}", source,
                    "豆包搜索检索图片；未提供摄影者署名", subject)
            }.distinctBy { it.remoteUrl }
        }
    }
}
