package cn.traintrip.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.LocalDate

data class SourcePlace(val id: String, val name: String, val introduction: String, val address: String,
    val url: String, val imageUrl: String?)
data class SourceFood(val id: String, val name: String, val context: String, val url: String,
    val description: String = "资料源收录的菜品，可结合个人口味选择。")
data class GuideMaterial(val cityId: String, val name: String, val province: String,
    val places: List<SourcePlace>, val foods: List<SourceFood>, val sources: List<GuideSource>,
    val documents: List<SourceDocument> = emptyList())
interface GuideMaterialSource { suspend fun fetch(city: City, stage: (String) -> Unit): GuideMaterial }
interface GuideGenerator { suspend fun generate(material: GuideMaterial, apiKey: String): DestinationGuide }

class CtripGuideSource(private val load: suspend (String) -> String = GuideNetwork()::html) : GuideMaterialSource {
    private var directory: String? = null
    override suspend fun fetch(city: City, stage: (String) -> Unit): GuideMaterial {
        stage("正在查找${city.name}的国内资料…")
        val directoryHtml = directory ?: load("https://you.ctrip.com/place").also { directory = it }
        val urls = cityLinks(directoryHtml, city.name)
        if (urls.isEmpty()) throw IOException("资料源暂未收录${city.name}，可稍后重试")
        var cityPage: JsonObject? = null
        var sourceUrl = ""
        for (url in urls.take(3)) {
            val page = optional { state(load(url)) } ?: continue
            if (matchesCity(page.obj("districtInfo"), city)) { cityPage = page; sourceUrl = url; break }
        }
        val page = cityPage ?: throw IOException("暂未找到归属明确的${city.name}资料")
        val district = page.obj("districtInfo")
        val tabs = page.objects("moduleList").firstOrNull { it.text("name") == "mustDo" }
            ?.obj("mustDoModule")?.objects("mustDoTabList").orEmpty()
        val candidates = tabs.firstOrNull { it.text("tabType") == "SIGHT" }?.objects("poiList").orEmpty()
        val checked = LocalDate.now(BEIJING_ZONE).toString()
        val sources = mutableListOf(GuideSource("携程 · ${city.name}目的地", sourceUrl, checked))
        val places = mutableListOf<SourcePlace>()
        stage("正在读取景点与美食介绍…")
        for (candidate in candidates.take(5)) {
            val url = pageUrl(candidate.text("jumpUrl")) ?: continue
            val detail = optional { state(load(url)).obj("poiDetail") } ?: continue
            if (detail.text("districtId") != district.text("districtId") || detail.text("poiId") != candidate.text("poiId")) continue
            val name = detail.text("poiName")
            val intro = plain(detail.text("introduction"))
            val address = detail.text("address")
            if (name.isBlank() || intro.length < 20 || address.isBlank()) continue
            val image = detail.obj("imageInfo").objects("poiPhotoImageList").map { it.text("imageUrl") }
                .firstOrNull(GuideNetwork::isPhotoUrl)
            places += SourcePlace("p${candidate.text("poiId")}", name, intro.take(1800), address.take(180), url, image)
            sources += GuideSource("携程 · $name", url, checked)
        }
        if (places.isEmpty()) throw IOException("景点资料暂时无法读取，请稍后重试")
        val foods = mutableListOf<SourceFood>()
        val restaurants = tabs.firstOrNull { it.text("tabType") == "RESTAURANT" }?.objects("poiList").orEmpty()
            .sortedByDescending { it.text("commentCount").toIntOrNull() ?: 0 }.take(2)
        for (candidate in restaurants) {
            val url = pageUrl(candidate.text("jumpUrl")) ?: continue
            val restaurantPage = optional { state(load(url)) } ?: continue
            val detail = restaurantPage.obj("restaurantDetail")
            val info = restaurantPage.obj("commentInfo").obj("poiInfo")
            if (info.text("districtId") != district.text("districtId") || detail.text("poiId") != candidate.text("poiId")) continue
            val intro = plain(detail.text("introduction")).take(700)
            detail.strings("foods").take(8).forEach { name ->
                if (name.isNotBlank() && foods.none { it.name == name }) foods += SourceFood("f${foods.size}", name,
                    "${detail.text("name")}介绍：$intro", url, "携程「${detail.text("name")}」列出的推荐菜，可结合个人口味选择。")
            }
            if (foods.any { it.url == url }) sources += GuideSource("携程 · ${detail.text("name")}", url, checked)
        }
        return GuideMaterial(city.id, city.name, city.province.name, places, foods.take(12), sources)
    }

    companion object {
        internal fun state(html: String): JsonObject {
            val script = Regex("<script\\b[^>]*\\bid=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(html)?.groupValues?.get(1) ?: throw IOException("资料网页结构已变化")
            return JsonParser.parseString(script).asJsonObject.obj("props").obj("pageProps").obj("initialState")
        }
        internal fun cityLinks(html: String, city: String): List<String> = Regex("<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).filter { plain(it.groupValues[2]).removeSuffix("旅游攻略").trim() == city }
            .mapNotNull { pageUrl(it.groupValues[1]) }.filter { it.toHttpUrlOrNull()?.encodedPath?.matches(Regex("/place/[a-z]+[0-9]+\\.html")) == true }.distinct().toList()
        internal fun matchesCity(d: JsonObject, city: City): Boolean =
            d.text("name").removeSuffix("市") == city.name.removeSuffix("市") &&
                d.get("isInChina")?.asBoolean == true &&
                d.text("parentDistrictName").let { it == city.province.shortName || it == city.province.name ||
                    (city.id in setOf("110000", "120000", "310000", "500000") && it == "中国") }
        internal fun pageUrl(raw: String): String? {
            val value = when {
                raw.startsWith("http://you.ctrip.com/") -> "https://" + raw.removePrefix("http://")
                raw.startsWith("//you.ctrip.com/") -> "https:$raw"
                raw.startsWith("/") && !raw.startsWith("//") -> "https://you.ctrip.com$raw"
                else -> raw
            }
            return value.takeIf(GuideNetwork::isPageUrl)
        }
        internal fun plain(html: String): String = html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()
    }
}

internal fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
internal fun JsonObject.obj(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
internal fun JsonObject.objects(key: String): List<JsonObject> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }.orEmpty()
internal fun JsonObject.strings(key: String): List<String> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { it.isJsonPrimitive }?.asString }.orEmpty()
internal suspend fun <T> optional(block: suspend () -> T): T? = try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
