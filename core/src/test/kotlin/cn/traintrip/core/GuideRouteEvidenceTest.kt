package cn.traintrip.core

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GuideRouteEvidenceTest {
    private val quote = "第一天：先去丛台公园，然后步行游览邯郸道，晚上在城区品尝美食。"
    private val material = GuideMaterial("130400","邯郸","河北省",emptyList(),emptyList(),emptyList(),
        listOf(SourceDocument("r1","邯郸两日游攻略","https://post.smzdm.com/p/123/",quote,"routes")))
    private val places=listOf("丛台公园","邯郸道").mapIndexed { i,n -> SourcePlace("p${i+1}",n,quote,"邯郸","https://www.mafengwo.cn/i/1.html",null) }
    private fun raw(ids:String="\"p1\",\"p2\"",evidence:String=quote) = JsonParser.parseString("""{"days":1,"schedule":[{"sourceId":"r1","quote":"$evidence","experienceIds":[$ids]}]}""").asJsonObject
    @Test fun quotedItineraryKeepsCorrectSourceAndOrder() {
        val route=GuideRouteEvidence.plans(listOf(raw()),material,places).single()
        assertEquals(listOf("p1","p2"),route.schedule.single().experienceIds)
        assertEquals(material.documents.single().url,route.schedule.single().sourceUrl)
        assertEquals(quote,route.schedule.single().evidence)
        assertEquals(quote,route.schedule.single().description)
    }
    @Test fun inventedReversedOrDirectoryRoutesAreRejectedIndependently() {
        assertTrue(GuideRouteEvidence.plans(listOf(raw("\"p2\",\"p1\"")),material,places).isEmpty())
        assertTrue(GuideRouteEvidence.plans(listOf(raw(evidence="第一天：丛台公园到邯郸道免费班车每十分钟发车。")),material,places).isEmpty())
        val directory=material.copy(documents=material.documents.map { it.copy(title="邯郸景点名录",content="推荐丛台公园和邯郸道，两处景点可供参考。") })
        assertTrue(GuideRouteEvidence.plans(listOf(raw(evidence=directory.documents.single().content)),directory,places).isEmpty())
        assertEquals(1,GuideRouteEvidence.plans(listOf(raw("\"missing\""),raw()),material,places).size)
    }
    @Test fun explicitDayArrowLinesRecoverWithoutModelAndDoNotMergeArticles() {
        val content="第一天：丛台公园→邯郸道，按攻略游览。\n第二天：邯郸道，休闲漫步→丛台公园。"
        val doc=material.documents.single().copy(content=content)
        val recovered=GuideRouteEvidence.plans(emptyList(),material.copy(documents=listOf(doc)),places).single()
        assertEquals(2,recovered.days)
        assertEquals(listOf("p1","p2"),recovered.schedule[0].experienceIds)
        assertEquals(listOf("p2","p1"),recovered.schedule[1].experienceIds)
        assertTrue(recovered.schedule.all { content.contains(it.evidence!!) })
    }

}
