package cn.traintrip.core

/** Generation and cumulative caches share the same per-city item limits. */
object GuideItemPolicy {
    const val MAX_PLACES = 10
    const val MAX_FOODS = 10

    /** Keep the existing order and discard references to items beyond the limit. */
    fun limit(guide: DestinationGuide): DestinationGuide {
        if (guide.experiences.size <= MAX_PLACES && guide.foods.size <= MAX_FOODS) return guide
        val places = guide.experiences.take(MAX_PLACES)
        val ids = places.map { it.id }.toSet()
        val bounded = guide.copy(experiences = places, foods = guide.foods.take(MAX_FOODS),
            plans = guide.plans.filter { plan -> plan.schedule.all { day -> day.experienceIds.all { it in ids } } })
        return bounded.withPhotos(GuidePhotoPolicy.select(bounded))
    }
}
