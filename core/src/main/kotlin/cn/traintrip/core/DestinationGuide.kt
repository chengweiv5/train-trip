package cn.traintrip.core

import com.google.gson.Gson
import java.io.Reader
import java.time.LocalDate

/** Offline editorial content, independent of ticket inventory and departure dates. */
data class GuideSource(val title: String, val url: String, val checkedOn: String)
data class DestinationPhoto(
    val assetName: String, val description: String, val author: String,
    val sourceUrl: String, val license: String, val licenseUrl: String
)
data class DestinationExperience(
    val id: String, val name: String, val reason: String, val duration: String, val location: String
)
data class DestinationFood(val name: String, val description: String)
data class PlanDay(val label: String, val experienceIds: List<String>, val description: String)
data class DayPlan(val days: Int, val title: String, val schedule: List<PlanDay>, val note: String)
data class DestinationGuide(
    val cityId: String, val name: String, val tagline: String, val tags: List<String>,
    val suggestedDays: String, val pace: String, val season: String, val arrivalAdvice: String,
    val experiences: List<DestinationExperience>, val foods: List<DestinationFood>,
    val plans: List<DayPlan>, val sources: List<GuideSource>, val photo: DestinationPhoto
)

object DestinationGuides {
    const val CONTENT_LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
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
                    require(source.title.isNotBlank() && source.url.startsWith("https://"))
                    LocalDate.parse(source.checkedOn)
                }
                val photo = guide.photo
                require(photo.assetName.matches(Regex("[a-z_]+\\.jpg")))
                require(listOf(photo.description, photo.author, photo.license).all { it.isNotBlank() })
                require(photo.sourceUrl.startsWith("https://") && photo.licenseUrl.startsWith("https://"))
            }
        }
    }.getOrElse { emptyList() }
}
