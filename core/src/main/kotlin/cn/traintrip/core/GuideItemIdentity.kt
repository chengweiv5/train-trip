package cn.traintrip.core

internal object GuideItemIdentity {
    private fun normalized(name: String) = name.replace('（', '(').replace('）', ')').replace(Regex("\\s+"), "")
    fun exact(first: String, second: String) = normalized(first) == normalized(second)

    fun <T> unique(name: String, items: List<T>, label: (T) -> String): T? {
        val exact = items.filter { exact(name, label(it)) }
        if (exact.isNotEmpty()) return exact.singleOrNull()
        return items.filter { CtripPhotoSource.samePlace(name, label(it)) }.singleOrNull()
    }

    /** Exact matches win before mutually unique aliases; no existing item can be claimed twice. */
    fun matches(old: List<String>, fresh: List<String>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        for (match in listOf<(String, String) -> Boolean>(::exact, CtripPhotoSource::samePlace)) {
            val oldRemaining = old.indices.filter { it !in result.values }
            val newRemaining = fresh.indices.filter { it !in result }
            for (index in newRemaining) {
                val candidate = oldRemaining.filter { match(old[it], fresh[index]) }.singleOrNull() ?: continue
                if (newRemaining.count { match(old[candidate], fresh[it]) } == 1) result[index] = candidate
            }
        }
        return result
    }
}
