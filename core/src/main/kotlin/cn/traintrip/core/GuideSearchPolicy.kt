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
    fun sourceUrl(url: String) = url.toHttpUrlOrNull()?.let {
        domesticHost(it.host) && SimplifiedGuidePolicy.urlAllowed(url) && it.username.isEmpty() && it.password.isEmpty() &&
            ((it.isHttps && it.port == 443) || (!it.isHttps && it.port == 80))
    } == true
}

fun validateDoubaoSearchKey(value: String) {
    require(value.length in 20..200 && value.all { it.code in 33..126 } && !value.startsWith("tvly-")) {
        "请输入有效的豆包搜索 API Key"
    }
}
