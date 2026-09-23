package cn.traintrip.core

/** Generation and cumulative caches share the same per-city item limits. */
object GuideItemPolicy {
    const val MAX_PLACES = 10
    const val MAX_FOODS = 10
    const val MAX_PLANS = 3

    /** Keep the existing order; snapshot legacy route names before trimming the sights. */
    fun limit(guide: DestinationGuide): DestinationGuide {
        val independent = guide.withIndependentPlans().let { it.copy(plans = it.plans.distinctBy { p -> p.days }.sortedBy { p -> p.days }.take(MAX_PLANS)) }
        if (guide.experiences.size <= MAX_PLACES && guide.foods.size <= MAX_FOODS) return independent
        val places = guide.experiences.take(MAX_PLACES)
        val bounded = independent.copy(experiences = places, foods = guide.foods.take(MAX_FOODS))
        return bounded.withPhotos(GuidePhotoPolicy.select(bounded))
    }
}
