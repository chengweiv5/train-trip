package cn.traintrip.core

import com.google.gson.Gson
import java.io.Reader
import java.net.URI
import java.time.LocalDate

/** Offline editorial content, independent of ticket inventory and departure dates. */
data class GuideSource(val title: String, val url: String, val checkedOn: String)
data class DestinationPhoto(
    val assetName: String, val description: String, val credit: String,
    val sourceUrl: String, val license: String? = null, val licenseUrl: String? = null,
    val remoteUrl: String? = null, val subject: PhotoSubject? = null
)
data class DestinationExperience(
    val id: String, val name: String, val reason: String, val duration: String, val location: String,
    val sourceUrl: String? = null, val evidence: String? = null
)
data class DestinationFood(val name: String, val description: String, val sourceUrl: String? = null, val evidence: String? = null)
data class PlanDay(val label: String, val experienceIds: List<String>, val description: String)
data class DayPlan(val days: Int, val title: String, val schedule: List<PlanDay>, val note: String)
data class DestinationGuide(
    val cityId: String, val name: String, val tagline: String, val tags: List<String>,
    val suggestedDays: String, val pace: String, val season: String, val arrivalAdvice: String,
    val experiences: List<DestinationExperience>, val foods: List<DestinationFood>,
    val plans: List<DayPlan>, val sources: List<GuideSource>, val photo: DestinationPhoto?,
    val generatedAt: String? = null, val model: String? = null,
    val photos: List<DestinationPhoto>? = null
) {
    // Nullable because Gson reads older JSON without invoking Kotlin defaults.
    val gallery: List<DestinationPhoto> get() = photos ?: listOfNotNull(photo)
    fun withPhotos(images:List<DestinationPhoto>) = copy(photo=images.firstOrNull(),photos=images)
}

object DestinationGuides {
    val all: List<DestinationGuide> by lazy {
        DestinationGuides::class.java.getResourceAsStream("/destination_guides.json")
            ?.bufferedReader()?.use(::parse).orEmpty()
    }
    private val byCity by lazy { all.associateBy { it.cityId } }
    fun find(cityId: String): DestinationGuide? = byCity[cityId]

    /** A missing or damaged catalogue must never prevent searching for train tickets. */
    internal fun parse(reader: Reader): List<DestinationGuide> = runCatching {
        Gson().fromJson(reader, Array<DestinationGuide>::class.java).toList().also { guides ->
            require(guides.map { it.cityId }.distinct().size == guides.size)
            guides.forEach { guide ->
                SimplifiedGuidePolicy.requireGuide(guide)
                require(listOf(guide.cityId, guide.name, guide.tagline, guide.suggestedDays,
                    guide.pace, guide.season, guide.arrivalAdvice).all { it.isNotBlank() })
                require(guide.tags.size in 2..3 && guide.tags.all { it.isNotBlank() })
                require(guide.experiences.size in 3..5 && guide.foods.isNotEmpty())
                val ids = guide.experiences.map { it.id }.toSet()
                require(ids.size == guide.experiences.size)
                guide.experiences.forEach { e ->
                    require(listOf(e.id, e.name, e.reason, e.duration, e.location).all { it.isNotBlank() })
                }
                guide.foods.forEach { require(it.name.isNotBlank() && it.description.isNotBlank()) }
                require(guide.plans.map { it.days }.sorted() == listOf(1, 2))
                guide.plans.forEach { plan ->
                    require(plan.title.isNotBlank() && plan.note.isNotBlank() && plan.schedule.size == plan.days)
                    plan.schedule.forEach { day ->
                        require(day.label.isNotBlank() && day.description.isNotBlank())
                        require(day.experienceIds.isNotEmpty() && day.experienceIds.all { it in ids })
                    }
                }
                require(guide.sources.isNotEmpty())
                guide.sources.forEach { source ->
                    require(source.title.isNotBlank() && isWebUrl(source.url))
                    LocalDate.parse(source.checkedOn)
                }
                val photo = requireNotNull(guide.photo)
                require(photo.assetName.matches(Regex("[a-z_]+\\.jpg")))
                require(listOf(photo.description, photo.credit).all { it.isNotBlank() })
                require(isWebUrl(photo.sourceUrl))
                require((photo.license == null && photo.licenseUrl == null) ||
                    (!photo.license.isNullOrBlank() && photo.licenseUrl?.let(::isWebUrl) == true))
            }
        }
    }.getOrElse { emptyList() }

    fun validateGenerated(guide: DestinationGuide, cityId: String): DestinationGuide {
        SimplifiedGuidePolicy.requireGuide(guide)
        require(cityId.matches(Regex("[0-9]{6}")) && guide.cityId == cityId)
        require(listOf(guide.name, guide.tagline, guide.suggestedDays, guide.pace, guide.season, guide.arrivalAdvice)
            .all { it.isNotBlank() && it.length <= 1800 })
        require(guide.tags.size in 2..3 && guide.tags.all { it.isNotBlank() && it.length <= 20 })
        require(guide.experiences.size in 1..5 && guide.foods.size <= 6)
        val ids = guide.experiences.map { it.id }.toSet()
        require(ids.size == guide.experiences.size)
        guide.experiences.forEach { require(listOf(it.id, it.name, it.reason, it.duration, it.location).all { s -> s.isNotBlank() && s.length <= 1800 }) }
        guide.foods.forEach { require(it.name.isNotBlank() && it.description.isNotBlank() && it.description.length <= 1800) }
        require(guide.foods.map { it.name }.distinct().size == guide.foods.size)
        require(guide.plans.size <= 2 && guide.plans.map { it.days }.distinct().size == guide.plans.size)
        guide.plans.forEach { p ->
            require(p.days in 1..2 && p.schedule.size == p.days && p.title.isNotBlank() && p.note.isNotBlank())
            p.schedule.forEach { d -> require(d.label.isNotBlank() && d.description.isNotBlank() && d.experienceIds.isNotEmpty() && d.experienceIds.all { it in ids }) }
        }
        require(guide.sources.size in 1..10)
        guide.sources.forEach { require(it.title.isNotBlank() && isWebUrl(it.url)); LocalDate.parse(it.checkedOn) }
        java.time.Instant.parse(requireNotNull(guide.generatedAt))
        require(!guide.model.isNullOrBlank())
        require(guide.gallery.size <= GuidePhotoPolicy.PER_CITY)
        val assigned = guide.gallery.mapNotNull { GuidePhotoPolicy.subject(guide, it) }
        require(assigned.groupingBy { it }.eachCount().values.all { it <= GuidePhotoPolicy.PER_ITEM })
        require(guide.gallery.all { it.subject == null || GuidePhotoPolicy.subject(guide, it) != null })
        require(guide.gallery.map { it.assetName }.distinct().size == guide.gallery.size)
        require(guide.gallery.map { it.remoteUrl ?: it.assetName }.distinct().size == guide.gallery.size)
        guide.gallery.forEach { p ->
            require(p.assetName.matches(Regex("[a-z0-9_]+\\.jpg")) && p.description.isNotBlank() && p.credit.isNotBlank())
            require(isWebUrl(p.sourceUrl))
            require(p.remoteUrl == null || GuideNetwork.isPhotoUrl(p.remoteUrl))
            require((p.license == null && p.licenseUrl == null) || (!p.license.isNullOrBlank() && p.licenseUrl?.let(::isWebUrl) == true))
        }
        return guide
    }

    private fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
