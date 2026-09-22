package cn.traintrip.core

import java.io.IOException
import java.net.URLDecoder

/** Reject resources, never transliterate quotations or relabel a traditional source. */
object SimplifiedGuidePolicy {
    private val traditional by lazy {
        val data = requireNotNull(javaClass.getResourceAsStream("/traditional_only_characters.txt"))
            .bufferedReader().use { it.readText() }
        data.codePoints().filter { !Character.isWhitespace(it) }.toArray().toSet()
    }
    private val traditionalVersion = Regex(
        "(?:^|[./?&#=:_-])(?:big5|gb2big5|zh[-_](?:hant(?:[-_](?:tw|hk|mo))?|tw|hk|mo)|cht|tc|traditional|fantizi)(?:$|[./?&#=:_-])",
        RegexOption.IGNORE_CASE
    )

    fun textAllowed(text: String): Boolean = text.codePoints().noneMatch { it in traditional }
    fun urlAllowed(url: String): Boolean = runCatching {
        val decoded = URLDecoder.decode(url, "UTF-8")
        textAllowed(decoded) && !traditionalVersion.containsMatchIn(decoded)
    }.getOrDefault(false)

    fun documentAllowed(doc: SourceDocument): Boolean = urlAllowed(doc.url) &&
        listOf(doc.title, doc.content).all(::textAllowed) && doc.images.all { textAllowed(it.description) && urlAllowed(it.url) }

    fun materialAllowed(material: GuideMaterial): Boolean =
        listOf(material.name, material.province).all(::textAllowed) &&
            material.sources.all { textAllowed(it.title) && urlAllowed(it.url) } &&
            material.documents.all(::documentAllowed) &&
            material.places.all { p -> listOf(p.name, p.introduction, p.address).all(::textAllowed) &&
                urlAllowed(p.url) && (p.imageUrl?.let(::urlAllowed) != false) } &&
            material.foods.all { f -> listOf(f.name, f.context, f.description).all(::textAllowed) && urlAllowed(f.url) }

    fun guideAllowed(guide: DestinationGuide): Boolean = with(guide) {
        listOf(name, tagline, suggestedDays, pace, season, arrivalAdvice).all(::textAllowed) && tags.all(::textAllowed) &&
            experiences.all { e -> listOfNotNull(e.name, e.reason, e.duration, e.location, e.evidence).all(::textAllowed) && e.sourceUrl?.let(::urlAllowed) != false } &&
            foods.all { f -> listOfNotNull(f.name, f.description, f.evidence).all(::textAllowed) && f.sourceUrl?.let(::urlAllowed) != false } &&
            plans.all { p -> listOf(p.title, p.note).all(::textAllowed) && p.schedule.all { d -> listOfNotNull(d.label, d.description, d.evidence).all(::textAllowed) && d.sourceUrl?.let(::urlAllowed) != false } } &&
            sources.all { textAllowed(it.title) && urlAllowed(it.url) } &&
            gallery.all { p -> listOfNotNull(p.description, p.credit, p.license, p.subject?.name).all(::textAllowed) &&
                listOfNotNull(p.sourceUrl, p.licenseUrl, p.remoteUrl).all(::urlAllowed) }
    }

    fun requireMaterial(material: GuideMaterial) {
        if (!materialAllowed(material)) throw IOException("资料包含繁体内容，已排除，请重试获取简体中文资料")
    }
    fun requireGuide(guide: DestinationGuide) {
        if (!guideAllowed(guide)) throw IOException("介绍包含繁体内容，未保存，请重试整理简体中文介绍")
    }
}
