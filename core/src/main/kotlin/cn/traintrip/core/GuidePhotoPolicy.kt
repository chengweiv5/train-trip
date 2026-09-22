package cn.traintrip.core

/** A subject belongs to its guide's city; names, not generated p1/p2 IDs, survive refreshes. */
data class PhotoSubject(val kind: String, val name: String)

object GuidePhotoPolicy {
    const val PER_ITEM = 3
    const val PER_CITY = 100
    const val CANDIDATES_PER_ITEM = 6
    const val CANDIDATES_PER_CITY = 200

    fun subjects(guide: DestinationGuide): List<PhotoSubject> =
        guide.experiences.map { PhotoSubject("place", it.name) } + guide.foods.map { PhotoSubject("food", it.name) }

    fun subject(guide: DestinationGuide, photo: DestinationPhoto): PhotoSubject? {
        val assigned = photo.subject
        return if (assigned != null) {
            if (assigned.kind !in listOf("place", "food") || assigned.name.isNullOrBlank()) return null
            GuideItemIdentity.unique(assigned.name, subjects(guide).filter { it.kind == assigned.kind }) { it.name }
        } else {
            // Only the old source-generated, complete city/item label carries enough ownership evidence.
            val name = photo.description.removePrefix("${guide.name} · ")
            if (name == photo.description) return null
            GuideItemIdentity.unique(name, subjects(guide)) { it.name }
        }
    }

    /** Round-robin selection gives each subject a first photo before a second or third. */
    fun bounded(photos: List<DestinationPhoto>, perItem: Int = PER_ITEM, perCity: Int = PER_CITY): List<DestinationPhoto> {
        val groups = photos.filter { it.subject != null }.groupBy { it.subject }
        val result = mutableListOf<DestinationPhoto>()
        val urls = mutableSetOf<String>()
        val assets = mutableSetOf<String>()
        val remaining = groups.mapValues { it.value.toMutableList() }
        val counts = mutableMapOf<PhotoSubject?, Int>()
        while (result.size < perCity) {
            var added = false
            for ((subject, queue) in remaining) {
                if (result.size >= perCity) break
                if (counts.getOrDefault(subject, 0) >= perItem) continue
                while (queue.isNotEmpty()) {
                    val photo = queue.removeAt(0)
                    val identity = photo.remoteUrl ?: photo.assetName
                    if (identity in urls || photo.assetName in assets) continue
                    urls += identity; assets += photo.assetName
                    result += photo; counts[subject] = counts.getOrDefault(subject, 0) + 1
                    added = true
                    break
                }
            }
            if (!added) break
        }
        return result
    }

    private fun inGuideOrder(guide: DestinationGuide, photos: List<DestinationPhoto>): List<DestinationPhoto> {
        val order = subjects(guide).withIndex().associate { it.value to it.index }
        return photos.mapNotNull { photo -> subject(guide, photo)?.let { photo.copy(subject = it) } }
            .sortedBy { order[it.subject] }
    }

    fun select(guide: DestinationGuide, photos: List<DestinationPhoto> = guide.gallery): List<DestinationPhoto> =
        bounded(inGuideOrder(guide, photos))

    fun candidates(guide: DestinationGuide, photos: List<DestinationPhoto>): List<DestinationPhoto> =
        bounded(inGuideOrder(guide, photos), CANDIDATES_PER_ITEM, CANDIDATES_PER_CITY)

    fun missing(guide: DestinationGuide): List<PhotoSubject> {
        if (select(guide).size >= PER_CITY) return emptyList()
        val present = select(guide).map { it.subject }.toSet()
        return subjects(guide).filter { it !in present }
    }

    fun forItem(guide: DestinationGuide, kind: String, name: String): List<DestinationPhoto> =
        select(guide).filter { it.subject == PhotoSubject(kind, name) }
}
