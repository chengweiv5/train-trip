package cn.traintrip.core

/** Update known items first, then append new items while preserving the first ten of each kind. */
object GuideRefreshPolicy {
    fun merge(previous: DestinationGuide?, fresh: DestinationGuide): DestinationGuide {
        DestinationGuides.validateGenerated(fresh, fresh.cityId)
        if (previous == null) return fresh
        require(previous.cityId == fresh.cityId && previous.name == fresh.name)
        val places = previous.experiences.toMutableList()
        val placeMatches = GuideItemIdentity.matches(places.map { it.name }, fresh.experiences.map { it.name })
        val usedIds = places.map { it.id }.toMutableSet()
        val idMapping = mutableMapOf<String, String>()
        var nextId = 1
        for ((index, item) in fresh.experiences.withIndex()) {
            val match = placeMatches[index]
            if (match != null) {
                val old = places[match]
                places[match] = item.copy(id = old.id, name = old.name,
                    sourceUrl = item.sourceUrl ?: old.sourceUrl, evidence = item.evidence ?: old.evidence)
                idMapping[item.id] = old.id
            } else {
                var id = item.id
                while (id in usedIds) id = "p${nextId++}"
                usedIds += id
                places += item.copy(id = id)
                idMapping[item.id] = id
            }
        }
        val foods = previous.foods.toMutableList()
        val foodMatches = GuideItemIdentity.matches(foods.map { it.name }, fresh.foods.map { it.name })
        for ((index, item) in fresh.foods.withIndex()) {
            val match = foodMatches[index]
            if (match == null) foods += item else {
                val old = foods[match]
                foods[match] = item.copy(name = old.name, sourceUrl = item.sourceUrl ?: old.sourceUrl, evidence = item.evidence ?: old.evidence)
            }
        }
        val sources = previous.sources.associateBy { it.url }.toMutableMap()
        fresh.sources.forEach { sources[it.url] = it }
        val bounded = GuideItemPolicy.limit(fresh.copy(experiences = places, foods = foods,
            plans = emptyList(), sources = sources.values.toList()))
        val retainedIds = bounded.experiences.map { it.id }.toSet()
        fun retained(plan: DayPlan) = plan.schedule.all { day -> day.experienceIds.all { it in retainedIds } }
        val plans = previous.plans.filter(::retained).associateBy { it.days }.toMutableMap()
        fresh.plans.forEach { plan ->
            val mapped = plan.copy(schedule = plan.schedule.map { day ->
                day.copy(experienceIds = day.experienceIds.map { requireNotNull(idMapping[it]) })
            })
            if (retained(mapped)) plans[plan.days] = mapped
        }
        val merged = bounded.copy(plans = plans.values.sortedBy { it.days })
        // Storage retains usable old images separately; carry only candidates from this generation here.
        val photos = fresh.gallery.mapNotNull { photo ->
            val subject = GuidePhotoPolicy.subject(fresh, photo) ?: return@mapNotNull null
            val name = if (subject.kind == "place") {
                val item = fresh.experiences.single { it.name == subject.name }
                places.single { it.id == idMapping[item.id] }.name
            } else {
                val index = fresh.foods.indexOfFirst { it.name == subject.name }
                foodMatches[index]?.let { foods[it].name } ?: subject.name
            }
            photo.copy(subject = subject.copy(name = name))
        }
        return DestinationGuides.validateCached(merged.withPhotos(GuidePhotoPolicy.select(merged, photos)), fresh.cityId)
    }
}
