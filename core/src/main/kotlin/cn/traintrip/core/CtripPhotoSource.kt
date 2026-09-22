package cn.traintrip.core

import com.google.gson.JsonObject
import kotlinx.coroutines.withTimeoutOrNull

/** Uses structured city/POI ownership rather than requiring generated image captions. */
class CtripPhotoSource(private val load: suspend (String) -> String = GuideNetwork()::html) : GuidePhotoSource {
    private var directory: String? = null

    override suspend fun fetch(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates =
        withTimeoutOrNull(60_000) { optional { read(city, guide, stage) } }
            ?: PhotoCandidates(emptyList(), true)

    private suspend fun read(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates {
        require(city.id == guide.cityId && city.name == guide.name)
        stage("正在查找${city.name}的图片…")
        val directoryHtml = directory ?: load("https://you.ctrip.com/place").also { directory = it }
        var cityPage: JsonObject? = null
        var cityUrl = ""
        var failed = false
        for (url in CtripGuideSource.cityLinks(directoryHtml, city.name).take(3)) {
            val page = optional { CtripGuideSource.state(load(url)) }
            if (page == null) { failed = true; continue }
            if (CtripGuideSource.matchesCity(page.obj("districtInfo"), city)) { cityPage = page; cityUrl = url; break }
        }
        val page = cityPage ?: return PhotoCandidates(emptyList(), failed)
        val district = page.obj("districtInfo")
        val pois = page.objects("moduleList").firstOrNull { it.text("name") == "mustDo" }
            ?.obj("mustDoModule")?.objects("mustDoTabList")?.firstOrNull { it.text("tabType") == "SIGHT" }
            ?.objects("poiList").orEmpty()
        val photos = mutableListOf<DestinationPhoto>()
        for (place in guide.experiences.take(5)) {
            val candidate = pois.firstOrNull { samePlace(place.name, it.text("name")) } ?: continue
            val url = CtripGuideSource.pageUrl(candidate.text("jumpUrl")) ?: continue
            stage("正在读取${place.name}的图片…")
            val detail = optional { CtripGuideSource.state(load(url)).obj("poiDetail") }
            if (detail == null) { failed = true; continue }
            if (detail.text("poiId").isBlank() || detail.text("poiId") != candidate.text("poiId") ||
                !samePlace(place.name, detail.text("poiName")) || !belongsToCity(detail, district, city)) continue
            val images = detail.obj("imageInfo").objects("poiPhotoImageList").map { it.text("imageUrl") } + candidate.text("coverImage")
            images.filter(::supportedPhoto).distinct().take(3).forEach { image ->
                photos += sourcedPhoto(image, "${city.name} · ${place.name}", url, "携程景点页刊载；页面未提供摄影者署名")
            }
        }
        district.text("coverImage").takeIf(::supportedPhoto)?.let { image ->
            photos += sourcedPhoto(image, "${city.name} · 城市资料页配图", cityUrl, "携程城市页刊载；页面未提供摄影者署名")
        }
        return PhotoCandidates(photos.distinctBy { it.remoteUrl }.take(12), failed)
    }

    companion object {
        private fun belongsToCity(detail: JsonObject, district: JsonObject, city: City): Boolean =
            detail.text("districtId").isNotBlank() && (detail.text("districtId") == district.text("districtId") ||
                detail.text("address").removePrefix("中国").removePrefix(city.province.name).removePrefix(city.province.shortName)
                    .startsWith(city.name.removeSuffix("市") + "市"))

        internal fun samePlace(first: String, second: String): Boolean {
            fun names(raw: String): Set<String> {
                val simplified = raw.replace('（', '(').replace('）', ')').replace(Regex("\\s+"), "")
                val variants = listOf(simplified, simplified.substringBefore('(')) +
                    Regex("\\(([^()]+)\\)").findAll(simplified).map { it.groupValues[1] }.toList()
                return variants.filter { it.length >= 2 && SimplifiedGuidePolicy.textAllowed(it) }.toSet()
            }
            return names(first).intersect(names(second)).isNotEmpty()
        }
    }
}
