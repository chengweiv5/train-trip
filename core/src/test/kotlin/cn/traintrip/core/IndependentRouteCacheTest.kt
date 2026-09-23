package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class IndependentRouteCacheTest {
    private val gson = Gson()
    private fun guide() = DestinationGuides.all.first().copy(generatedAt = "2026-09-23T00:00:00Z", model = "test").withPhotos(emptyList())

    @Test fun gsonLegacyMissingStopsAndUnknownIdsStillKeepReadableDescription() {
        val old = guide()
        val root = JsonParser.parseString(gson.toJson(old)).asJsonObject
        root["plans"].asJsonArray.forEach { p -> p.asJsonObject["schedule"].asJsonArray.forEach { d -> d.asJsonObject.remove("stops") } }
        val restored = gson.fromJson(root, DestinationGuide::class.java)
        assertNull(restored.plans.first().schedule.first().stops)
        val independent = GuideItemPolicy.limit(restored)
        assertEquals(old.plans.first().schedule.first().routeStops(old.experiences), independent.plans.first().schedule.first().stops)
        assertEquals(independent, DestinationGuides.validateCached(independent, independent.cityId))
        val missing = restored.copy(plans = listOf(DayPlan(1, "一天", listOf(PlanDay("当天", listOf("missing"), "仍可阅读的旧安排")), "参考")))
        assertEquals("仍可阅读的旧安排", GuideItemPolicy.limit(missing).plans.single().schedule.single().description)
    }
    @Test fun independentMultiDayCacheRoundTripsWithoutASightListJoin() {
        val source = "https://www.mafengwo.cn/i/234.html"
        val plan = DayPlan(3, "三天参考", (1..3).map { PlanDay("第${it}天", emptyList(), "参考安排$it", source, stops = listOf("独立地点$it")) }, "参考")
        val current = guide().copy(plans = listOf(plan))
        val restored = gson.fromJson(gson.toJson(current), DestinationGuide::class.java)
        assertEquals(current, DestinationGuides.validateCached(restored, current.cityId))
        val mixed = current.copy(plans = listOf(plan.copy(schedule = plan.schedule.mapIndexed { i, day ->
            if (i == 2) day.copy(sourceUrl = "https://you.ctrip.com/travels/123.html") else day
        })))
        assertTrue(runCatching { DestinationGuides.validateCached(mixed, current.cityId) }.isFailure)
    }
    @Test fun generationAndCumulativeCachesHaveAtMostThreeDurations() {
        val source = "https://www.mafengwo.cn/i/234.html"
        fun plan(days: Int) = DayPlan(days, "$days 天参考", (1..days).map {
            PlanDay("第${it}天", emptyList(), "当天参考安排", source, stops = listOf("地点$it"))
        }, "参考")
        val legacy = guide().copy(plans = listOf(plan(5), plan(4), plan(3), plan(2)))
        val limited = GuideItemPolicy.limit(legacy)
        assertEquals(listOf(2, 3, 4), limited.plans.map { it.days })
        assertTrue(runCatching { DestinationGuides.validateGenerated(legacy, legacy.cityId) }.isFailure)
        val merged = GuideRefreshPolicy.merge(limited, guide().copy(plans = listOf(plan(1))))
        assertEquals(listOf(1, 2, 3), merged.plans.map { it.days })
        assertEquals(merged, DestinationGuides.validateCached(merged, merged.cityId))
    }

}
