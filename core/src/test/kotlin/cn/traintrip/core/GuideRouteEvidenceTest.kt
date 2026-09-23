package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GuideRouteEvidenceTest {
    private val material = GuideMaterial("130400", "邯郸", "河北省", emptyList(), emptyList(), emptyList(),
        listOf(SourceDocument("r1", "邯郸一日游攻略", "https://post.smzdm.com/p/123/", "城区漫步安排。", "routes"),
            SourceDocument("r2", "邯郸三日游", "https://www.mafengwo.cn/i/234.html", "多日游记参考。", "routes")))
    private fun raw(days: Int = 1, source: String = "r1") = JsonParser.parseString(Gson().toJson(mapOf(
        "days" to days, "sourceId" to source, "schedule" to (1..days).map { mapOf(
            "stops" to listOf("未收录街区$it", "当地小吃街"), "description" to "根据游记概括的第${it}天参考安排。") }))).asJsonObject

    @Test fun independentStopsAndParaphraseDoNotNeedSightIdsOrVerbatimQuotes() {
        val route = GuideRouteEvidence.plans(listOf(raw()), material, emptyList()).single()
        assertEquals(listOf("未收录街区1", "当地小吃街"), route.schedule.single().stops)
        assertEquals(material.documents.first().url, route.schedule.single().sourceUrl)
        assertNull(route.schedule.single().evidence)
    }
    @Test fun separateDurationsCanUseDifferentArticlesIncludingNDays() {
        val plans = GuideRouteEvidence.plans(listOf(raw(), raw(3, "r2"), raw(12, "r2")), material, emptyList())
        assertEquals(listOf(1, 3, 12), plans.map { it.days })
        assertNotEquals(plans[0].schedule.first().sourceUrl, plans[1].schedule.first().sourceUrl)
        assertEquals(12, plans.last().schedule.size)
    }
    @Test fun mixedArticlesInsideOnePlanAreRejectedButOtherPlansSurvive() {
        val mixed = raw(2).apply { getAsJsonArray("schedule")[1].asJsonObject.addProperty("sourceId", "r2") }
        assertEquals(listOf(1), GuideRouteEvidence.plans(listOf(mixed, raw()), material, emptyList()).map { it.days })
    }
    @Test fun missingDayUnknownSourceAndBlankDescriptionAreRejected() {
        val missing = raw(2).apply { getAsJsonArray("schedule").remove(1) }
        val blank = raw().apply { getAsJsonArray("schedule")[0].asJsonObject.addProperty("description", "") }
        assertTrue(GuideRouteEvidence.plans(listOf(missing, raw(source = "missing"), blank), material, emptyList()).isEmpty())
    }
    @Test fun legacyIdsAreOnlyAnOptionalNameLookup() {
        val legacy = JsonParser.parseString("""{"days":1,"sourceId":"r1","schedule":[{"experienceIds":["p1","missing"],"quote":"旧版本缓存的完整日程说明。"}]}""").asJsonObject
        val place = SourcePlace("p1", "老城区", "介绍", "邯郸", material.documents.first().url, null)
        val day = GuideRouteEvidence.plans(listOf(legacy), material, listOf(place)).single().schedule.single()
        assertEquals(listOf("老城区"), day.stops)
        assertTrue(day.experienceIds.isEmpty())
    }
    @Test fun itineraryDurationsAreNotLimitedToTwoDays() {
        assertEquals(listOf(3, 12), GuideRouteEvidence.suggestedDays(material.documents.last().copy(content = "十二天游记")))
    }
    @Test fun numberedDaySectionsDiscoverThreeDaysWithoutATitleDuration() {
        val doc = material.documents.last().copy(title = "邯郸深度攻略", content = "第一天：市区。\n第二天：山间。\n🗿 第三天：石窟。")
        assertEquals(listOf(3), GuideRouteEvidence.suggestedDays(doc))
        assertEquals(listOf(3), GuideRouteEvidence.suggestedDays(doc.copy(content = "Day1:市区\nDay2:山间\nDay3:石窟")))
        assertEquals(listOf(2), GuideRouteEvidence.suggestedDays(doc.copy(title = "两日游", content = "如果时间充裕，第三天可加玩周边。")))
    }

    @Test fun atMostThreeDurationsAreKeptShortestFirst() {
        val plans = GuideRouteEvidence.plans(listOf(raw(5), raw(3), raw(1), raw(2), raw(4)), material, emptyList())
        assertEquals(listOf(1, 2, 3), plans.map { it.days })
    }

}
