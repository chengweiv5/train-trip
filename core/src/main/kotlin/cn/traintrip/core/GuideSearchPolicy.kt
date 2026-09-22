package cn.traintrip.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SourceImage(val url: String, val description: String, val fromSearch: Boolean = false)
data class SourceDocument(val id: String, val title: String, val url: String, val content: String,
    val kind: String, val images: List<SourceImage> = emptyList())

internal object GuideSearchPolicy {
    val DOMAINS = listOf("gov.cn", "ctrip.com", "mafengwo.cn", "people.com.cn", "xinhuanet.com", "news.cn", "cnr.cn", "cctv.com")
    val DECORATION = Regex("logo|footer|banner|icon|qrcode|beian|ewm|guohui|wxzftb", RegexOption.IGNORE_CASE)
    fun domesticHost(host: String) = DOMAINS.any { host == it || host.endsWith(".$it") }
    fun searchImageHost(host: String) = host.matches(Regex("p[0-9]+-volcsearch-sign\\.byteimg\\.com"))
    private val officialMedia = listOf("people.com.cn", "xinhuanet.com", "news.cn", "cnr.cn", "cctv.com", "thepaper.cn", "chinanews.com.cn", "chinanews.com", "hebnews.cn", "hebei.com.cn", "handannews.com.cn")
    private val communities = listOf("ctrip.com", "mafengwo.cn", "qyer.com", "smzdm.com", "xiaohongshu.com", "douban.com", "zhihu.com", "sina.com.cn", "sina.cn", "sohu.com", "163.com", "toutiao.com", "bilibili.com", "trip.com")
    private fun within(host: String, domain: String) = host == domain || host.endsWith(".$domain")
    fun community(url: String) = url.toHttpUrlOrNull()?.host?.let { host -> communities.any { within(host,it) } } == true
    fun primaryArticle(url: String) = sourceUrl(url) && url.toHttpUrlOrNull()?.host?.let { host ->
        !within(host,"gov.cn") && officialMedia.none { within(host,it) }
    } == true
    fun site(url: String): String = url.toHttpUrlOrNull()?.host.orEmpty().split('.').let {
        it.takeLast(if (it.takeLast(2) == listOf("com","cn")) 3 else 2).joinToString(".")
    }
    private fun publicHost(host: String) = host.contains('.') && !host.contains(':') &&
        !host.all { it.isDigit() || it == '.' } &&
        listOf("localhost", "local", "internal", "test", "invalid", "example", "onion").none { within(host,it) }
    fun sourceUrl(url: String) = url.toHttpUrlOrNull()?.let {
        publicHost(it.host) && SimplifiedGuidePolicy.urlAllowed(url) && it.username.isEmpty() && it.password.isEmpty() &&
            ((it.isHttps && it.port == 443) || (!it.isHttps && it.port == 80))
    } == true
}

fun validateDoubaoSearchKey(value: String) {
    require(value.length in 20..200 && value.all { it.code in 33..126 } && !value.startsWith("tvly-")) {
        "请输入有效的豆包搜索 API Key"
    }
}
