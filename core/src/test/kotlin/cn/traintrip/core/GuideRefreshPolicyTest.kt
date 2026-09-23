package cn.traintrip.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GuideRefreshPolicyTest {
    private val city = StationCatalog.bundled().cities.first { it.name == "邯郸" }
    private fun place(id: String, name: String) = DestinationExperience(id, name, "$name 的缓存介绍", "1小时", "邯郸")
    private fun food(name: String) = DestinationFood(name, "$name 的缓存介绍")
    private fun source(index: Int) = GuideSource("资料$index", "https://you.ctrip.com/sight/handan495/$index.html", "2026-09-21")
    private fun guide(places: List<DestinationExperience>, foods: List<DestinationFood> = emptyList()) =
        DestinationGuides.all.first().copy(cityId = city.id, name = city.name, experiences = places, foods = foods,
            plans = emptyList(), sources = listOf(source(1)), generatedAt = "2026-09-22T00:00:00Z", model = "test").withPhotos(emptyList())
    private fun plan(id: String) = DayPlan(1, "一天参考", listOf(PlanDay("当天", listOf(id), "参考安排")), "参考建议")
    @Test fun omittedPlacesAndFoodsRemainWhileMatchingTextUpdatesAndNewItemsAppend() {
        val old = guide(listOf(place("p1", "广府古城"), place("p2", "邯郸市博物馆")), listOf(food("拽面")))
        val fresh = guide(listOf(place("p1", "邯郸市博物馆").copy(reason = "新介绍"), place("p2", "丛台公园")), listOf(food("酥鱼")))
            .copy(plans = listOf(plan("p1")))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(listOf("广府古城", "邯郸市博物馆", "丛台公园"), merged.experiences.map { it.name })
        assertEquals("新介绍", merged.experiences[1].reason)
        assertEquals("邯郸市博物馆", merged.plans.single().schedule.single().stops!!.single())
        assertEquals(3, merged.experiences.map { it.id }.distinct().size)
        assertEquals(listOf("拽面", "酥鱼"), merged.foods.map { it.name })
        assertEquals(merged, GuideRefreshPolicy.merge(merged, fresh))
    }
    @Test fun refreshAtCapacityUpdatesMatchesButDoesNotAppendOrReplaceOldItems() {
        val old = guide((1..10).map { place("p$it", "旧景点$it") }, (1..10).map { food("旧美食$it") })
            .copy(plans = listOf(plan("p10")), sources = (1..10).map(::source))
        val fresh = guide(listOf(place("p1", "新增景点"), place("p2", "旧景点10").copy(reason = "新介绍")),
            listOf(food("新增美食"), food("旧美食10").copy(description = "新美食介绍")))
            .copy(plans = listOf(plan("p1")), sources = listOf(source(11)))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(old.experiences.map { it.name }, merged.experiences.map { it.name })
        assertEquals(old.foods.map { it.name }, merged.foods.map { it.name })
        assertEquals("新介绍", merged.experiences.last().reason)
        assertEquals("新美食介绍", merged.foods.last().description)
        assertEquals(listOf("新增景点"), merged.plans.single().schedule.single().stops)
        assertEquals(11, merged.sources.size)
        assertEquals(merged, DestinationGuides.validateCached(merged, city.id))
        assertEquals(merged, GuideRefreshPolicy.merge(merged, fresh))
    }
    @Test fun partialCacheAppendsOnlyUntilBothKindsReachTenAndDropsUnretainedPhotos() {
        val old = guide((1..9).map { place("p$it", "旧景点$it") }, (1..9).map { food("旧美食$it") })
        val fresh = guide(listOf(place("p1", "新增景点甲"), place("p2", "新增景点乙")),
            listOf(food("新增美食甲"), food("新增美食乙")))
            .withPhotos(listOf("甲", "乙").flatMap { suffix -> listOf("place" to "新增景点$suffix", "food" to "新增美食$suffix") }
                .mapIndexed { index, (kind, name) -> DestinationPhoto("new_$index.jpg", "邯郸 · $name", "来源", source(1).url,
                    subject = PhotoSubject(kind, name)) })
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(10, merged.experiences.size); assertEquals(10, merged.foods.size)
        assertEquals("新增景点甲", merged.experiences.last().name)
        assertEquals("新增美食甲", merged.foods.last().name)
        assertEquals(setOf(PhotoSubject("place", "新增景点甲"), PhotoSubject("food", "新增美食甲")), merged.gallery.map { it.subject }.toSet())
        assertEquals(merged, GuideRefreshPolicy.merge(merged, fresh))
    }
    @Test fun generatedAndCachedValidationAcceptTenAndRejectEitherKindAtEleven() {
        val ten = guide((1..10).map { place("p$it", "景点$it") }, (1..10).map { food("美食$it") })
        for (validate in listOf(DestinationGuides::validateGenerated, DestinationGuides::validateCached)) {
            assertEquals(ten, validate(ten, city.id))
            assertTrue(runCatching { validate(ten.copy(experiences = ten.experiences + place("p11", "额外景点")), city.id) }.isFailure)
            assertTrue(runCatching { validate(ten.copy(foods = ten.foods + food("额外美食")), city.id) }.isFailure)
        }
    }
    @Test fun oldUnlimitedCacheKeepsFirstTenAndPreservesRouteNamesForDiscardedSights() {
        val legacy = guide((1..12).map { place("p$it", "旧景点$it") }, (1..12).map { food("旧美食$it") })
            .copy(plans = listOf(plan("p11")))
            .withPhotos(listOf(1, 11).map { index -> DestinationPhoto("old_$index.jpg", "邯郸 · 旧景点$index", "来源", source(1).url,
                subject = PhotoSubject("place", "旧景点$index")) })
        val bounded = GuideItemPolicy.limit(legacy)
        assertEquals(legacy.experiences.take(10), bounded.experiences)
        assertEquals(legacy.foods.take(10), bounded.foods)
        assertEquals(listOf("旧景点11"), bounded.plans.single().schedule.single().stops)
        assertEquals(listOf(legacy.gallery.first()), bounded.gallery)
        assertEquals(bounded, DestinationGuides.validateCached(bounded, city.id))
        assertEquals(bounded, GuideItemPolicy.limit(bounded))
        val fresh = guide(listOf(place("p1", "新增景点")), listOf(food("新增美食")))
        val merged = GuideRefreshPolicy.merge(legacy, fresh)
        assertEquals(bounded.experiences, merged.experiences)
        assertEquals(bounded.foods, merged.foods)
        assertEquals(bounded.plans, merged.plans)
    }
    @Test fun exactNameWinsOverSharedAliasAndOldPhotoOwnershipIsUnambiguous() {
        val old = guide(listOf(place("p1", "甲馆（老馆）")))
        val fresh = guide(listOf(place("p1", "甲馆（老馆）"), place("p2", "乙馆（老馆）")))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(listOf("甲馆（老馆）", "乙馆（老馆）"), merged.experiences.map { it.name })
        val photo = DestinationPhoto("old.jpg", "邯郸 · 甲馆（老馆）", "来源", source(1).url, subject = PhotoSubject("place", "甲馆（老馆）"))
        assertEquals(photo.subject, GuidePhotoPolicy.subject(merged, photo))
        assertEquals(merged, GuideRefreshPolicy.merge(merged, fresh))
    }
    @Test fun ambiguousAliasNeverLetsTwoNewItemsOverwriteOneOldItem() {
        val old = guide(listOf(place("p1", "甲馆（老馆）")))
        val fresh = guide(listOf(place("p1", "乙馆（老馆）"), place("p2", "丙馆（老馆）")))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(listOf("甲馆（老馆）", "乙馆（老馆）", "丙馆（老馆）"), merged.experiences.map { it.name })
        assertEquals(merged, GuideRefreshPolicy.merge(merged, fresh))
    }
    @Test fun whitespaceAndBracketNormalizationStillPrefersExactName() {
        val old = guide(listOf(place("p1", "甲馆（老馆）"), place("p2", "乙馆（老馆）")))
        val fresh = guide(listOf(place("p1", "甲馆 ( 老馆 )").copy(reason = "新介绍")))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(listOf("甲馆（老馆）", "乙馆（老馆）"), merged.experiences.map { it.name })
        assertEquals("新介绍", merged.experiences.first().reason)
        assertEquals(old.experiences.last(), merged.experiences.last())
    }
    @Test fun generatedAliasPhotoIsMappedToRetainedCanonicalName() {
        val old = guide(listOf(place("p1", "广府古城（永年广府）")))
        val photo = DestinationPhoto("new.jpg", "邯郸 · 永年广府", "来源", source(1).url,
            subject = PhotoSubject("place", "永年广府"))
        val fresh = guide(listOf(place("p1", "永年广府"))).withPhotos(listOf(photo))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(PhotoSubject("place", "广府古城（永年广府）"), merged.gallery.single().subject)
        assertEquals(photo.assetName, merged.gallery.single().assetName)
    }
    @Test fun uniqueAliasPreservesOldNameIdAndSourceWhenMissingInRefresh() {
        val previous = place("historic", "广府古城（永年广府）").copy(sourceUrl = source(1).url, evidence = "旧原文证据")
        val old = guide(listOf(previous)).copy(plans = listOf(plan(previous.id)))
        val fresh = guide(listOf(place("p1", "永年广府").copy(reason = "新介绍")))
        val merged = GuideRefreshPolicy.merge(old, fresh)
        assertEquals(previous.copy(reason = "新介绍"), merged.experiences.single())
        assertEquals(old.withIndependentPlans().plans, merged.plans)
    }
    @Test fun crossCityInputRejectedAndFreshSourceDateWins() {
        val old = guide(listOf(place("p1", "广府古城")))
        val fresh = old.copy(sources = listOf(source(1).copy(checkedOn = "2026-09-22")))
        assertEquals(fresh.sources, GuideRefreshPolicy.merge(old, fresh).sources)
        assertTrue(runCatching { GuideRefreshPolicy.merge(old.copy(cityId = "130600", name = "保定"), fresh) }.isFailure)
    }
    @Test fun repositoryPreparesMergedContentBeforeAnySave() = runBlocking {
        val old = guide(listOf(place("p1", "广府古城")))
        val fresh = guide(listOf(place("p1", "邯郸市博物馆")))
        var saved = old
        val store = object : GuideStore {
            override fun read(cityId: String) = saved
            override fun attempted(cityId: String) = true
            override fun markAttempted(cityId: String) {}
            override fun save(guide: DestinationGuide) { saved = guide }
        }
        val repository = GuideRepository(object : GuideMaterialSource {
            override suspend fun fetch(city: City, stage: (String) -> Unit) = GuideMaterial(city.id, city.name, city.province.name, emptyList(), emptyList(), emptyList())
        }, object : GuideGenerator { override suspend fun generate(material: GuideMaterial, apiKey: String) = fresh }, store)
        repository.generate(city, "test", {}) { prepared ->
            assertTrue(prepared.experiences.any { it.name == "广府古城" }); prepared
        }
        assertEquals(listOf("广府古城", "邯郸市博物馆"), saved.experiences.map { it.name })
    }
}
