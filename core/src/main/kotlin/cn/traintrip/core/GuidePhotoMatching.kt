package cn.traintrip.core

/** Only generic venue suffixes may be omitted; a street inside a park stays a separate subject. */
internal object GuidePhotoMatching {
    fun names(name: String): List<String> {
        val full = name.replace(Regex("\\s+"), "")
        val base = full.substringBefore('（').substringBefore('(')
        val suffix = listOf("风景名胜区", "旅游风景区", "旅游景区", "主题乐园", "风景区", "景区")
            .firstOrNull { base.endsWith(it) && base.length - it.length >= 3 }
        val museum = if (base.endsWith("博物馆")) {
            if (base.contains("市博物馆")) base.replace("市博物馆","博物馆")
            else if (base.length >= 5) base.removeSuffix("博物馆") + "市博物馆" else null
        } else null
        return listOfNotNull(full, base, suffix?.let { base.removeSuffix(it) }, museum).distinct()
    }
    fun mentions(evidence: String, name: String): Boolean = names(name).any { evidence.contains(it) }
    fun queries(city: City, subject: PhotoSubject): List<String> = listOf(
        "${city.name}${subject.name}",
        "${city.name} ${names(subject.name).last()} ${if (subject.kind == "food") "美食 成品" else "实景"}"
    ).map { it.take(100) }.distinct()
}
