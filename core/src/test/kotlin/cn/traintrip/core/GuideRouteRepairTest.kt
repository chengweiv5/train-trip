package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GuideRouteRepairTest {
    private val gson = Gson()
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/handan-route-repair/$name.json")).readText()
    private val material get() = gson.fromJson(fixture("material"), GuideMaterial::class.java)
    private fun initial() = JsonParser.parseString(fixture("initial")).asJsonObject.apply { add("plans", gson.toJsonTree(emptyList<Any>())) }.toString()
    private fun response(content: String) = MockResponse().setBody(gson.toJson(mapOf("choices" to listOf(
        mapOf("finish_reason" to "stop", "message" to mapOf("content" to content))))))
    private fun generator(server: MockWebServer) = DeepSeekGuideGenerator(server.url("/chat/completions").toString(), OkHttpClient())
    private fun repair(source: String, days: Int = 1) = gson.toJson(mapOf("plans" to listOf(mapOf("days" to days,
        "sourceId" to source, "schedule" to (1..days).map { mapOf("stops" to listOf("独立路线地点$it"), "description" to "当天的参考安排。") }))))

    @Test fun retryUsesArticleWithoutAnyAcceptedSightAndDoesNotReceiveSightIds() = runBlocking {
        val doc = SourceDocument("independent", "邯郸三日游", "https://www.mafengwo.cn/i/987.html", "独立城区漫步路线及周边旅行安排。", "routes")
        val input = material.copy(documents = material.documents + doc, sources = material.sources + GuideSource(doc.title, doc.url, "2026-09-23"))
        MockWebServer().use { server ->
            server.enqueue(response(initial())); server.enqueue(response(repair(doc.id, 3)))
            val guide = generator(server).generate(input, "test-key")
            assertEquals(3, guide.plans.single().days)
            assertEquals(listOf("独立路线地点3"), guide.plans.single().schedule.last().stops)
            server.takeRequest()
            val request = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val inputJson = JsonParser.parseString(request["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
            assertFalse(inputJson.has("places"))
            assertTrue(inputJson["documents"].asJsonArray.any { it.asJsonObject["id"].asString == doc.id })
            assertTrue(inputJson["requestedDays"].asJsonArray.any { it.asInt == 3 })
        }
    }
    @Test fun malformedOrFailedRetryPreservesContentAndNeverLoops() = runBlocking {
        val original = SearchGuideDecoder.decode(initial(), material)
        for (repair in listOf(response("{\"plans\":[]}"), response("not-json"), response(repair("unknown")), MockResponse().setResponseCode(503))) {
            MockWebServer().use { server ->
                server.enqueue(response(initial())); server.enqueue(repair)
                val guide = generator(server).generate(material, "test-key")
                assertEquals(original.experiences, guide.experiences)
                assertEquals(original.foods, guide.foods)
                assertTrue(guide.plans.isEmpty())
                assertEquals(2, server.requestCount)
            }
        }
    }
    @Test fun existingOneDayPlanIsNotReplacedWhileMissingLongerPlanIsAdded() {
        val input = material.copy(documents = material.documents.map { it.copy(title = "邯郸三日游") })
        val initial = SearchGuideDecoder.decode(initial(), input)
        val one = GuideRouteRepair.apply(repair(input.documents.first().id), input, initial)
        val withThree = GuideRouteRepair.apply(repair(input.documents.first().id, 3), input, one)
        assertEquals(listOf(1, 3), withThree.plans.map { it.days })
        assertEquals(one.plans.single(), withThree.plans.first())
    }
    @Test fun previousPhoneOutputIsNoLongerRejectedForSightOrderOrShortQuote() {
        val input = gson.fromJson(fixture("phone-material"), GuideMaterial::class.java)
        assertTrue(SearchGuideDecoder.decode(fixture("phone-initial"), input).plans.isNotEmpty())
    }
    @Test fun routeSearchCanStillSupplySightDescriptions() {
        val input = gson.fromJson(fixture("two-day-material"), GuideMaterial::class.java)
        val guide = SearchGuideDecoder.decode(fixture("two-day-initial"), input)
        assertEquals("响堂山石窟", guide.experiences.single { it.id == "p5" }.name)
    }
    @Test fun validRouteUpdatesEvenWhenNoSightDescriptionPassesExtraction() = runBlocking {
        val input = material
        val root = JsonParser.parseString(initial()).asJsonObject.apply {
            add("experiences", gson.toJsonTree(emptyList<Any>()))
            add("foods", gson.toJsonTree(emptyList<Any>()))
            add("plans", JsonParser.parseString(repair(input.documents.first().id)).asJsonObject["plans"])
        }.toString()
        MockWebServer().use { server ->
            server.enqueue(response(root))
            val guide = generator(server).generate(input, "test-key")
            assertTrue(guide.experiences.isEmpty())
            assertEquals(listOf("独立路线地点1"), guide.plans.single().schedule.single().stops)
        }
        val old = SearchGuideDecoder.decode(initial(), input)
        MockWebServer().use { server ->
            server.enqueue(response(root))
            val refreshed = generator(server).refresh(input, "test-key", old)
            assertEquals(old.experiences, refreshed.experiences)
            assertEquals(listOf("独立路线地点1"), refreshed.plans.single().schedule.single().stops)
        }
    }

}
