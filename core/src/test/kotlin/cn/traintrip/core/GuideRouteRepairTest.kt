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
    private fun response(content: String) = MockResponse().setBody(gson.toJson(mapOf("choices" to listOf(
        mapOf("finish_reason" to "stop", "message" to mapOf("content" to content))))))
    private fun generator(server: MockWebServer) = DeepSeekGuideGenerator(server.url("/chat/completions").toString(), OkHttpClient())

    @Test fun refreshRepairsAgainstFinalTenRetainedSightsBeforeSaving() = runBlocking {
        val input = gson.fromJson(fixture("two-day-material"), GuideMaterial::class.java)
        val previous = gson.fromJson(fixture("two-day-previous"), DestinationGuide::class.java)
        val city = StationCatalog.bundled().cities.single { it.id == previous.cityId }
        // Fresh extraction omitted day two's sight, which still exists in the full local cache.
        val initial = JsonParser.parseString(fixture("two-day-partial")).asJsonObject.apply {
            add("experiences", gson.toJsonTree(getAsJsonArray("experiences").filter { it.asJsonObject["id"].asString != "p5" }))
        }.toString()
        var saved = previous
        val store = object : GuideStore {
            override fun read(cityId: String) = saved
            override fun attempted(cityId: String) = true
            override fun markAttempted(cityId: String) {}
            override fun save(guide: DestinationGuide) { saved = guide }
        }
        MockWebServer().use { server ->
            server.enqueue(response(initial))
            server.enqueue(response(fixture("two-day-retained")))
            val repository = GuideRepository(object : GuideMaterialSource {
                override suspend fun fetch(city: City, stage: (String) -> Unit) = input
            }, generator(server), store)
            val guide = repository.generate(city, "test-key", {})
            assertEquals(listOf(1, 2), guide.plans.map { it.days }.sorted())
            assertEquals(previous.experiences.map { it.id to it.name }, guide.experiences.map { it.id to it.name })
            assertEquals(listOf("p4"), guide.plans.single { it.days == 2 }.schedule[1].experienceIds)
            assertEquals(guide, saved)
            server.takeRequest()
            val request = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val repairInput = JsonParser.parseString(request["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
            assertEquals(previous.experiences.map { it.id }, repairInput["places"].asJsonArray.map { it.asJsonObject["id"].asString })
        }
    }

    @Test fun secondDaySightIsKeptWhenItsDescriptionWasFoundByRouteSearch() {
        val input = gson.fromJson(fixture("two-day-material"), GuideMaterial::class.java)
        val guide = SearchGuideDecoder.decode(fixture("two-day-initial"), input)
        assertEquals(listOf("p1", "p2", "p3", "p5"), guide.experiences.map { it.id })
        val sight = guide.experiences.single { it.id == "p5" }
        assertEquals("响堂山石窟", sight.name)
        assertTrue(input.documents.single { it.id == "s16" }.content.contains(requireNotNull(sight.evidence)))
    }

    @Test fun missingTwoDayPlanIsRepairedWithoutReplacingValidOneDayPlan() = runBlocking {
        val input = gson.fromJson(fixture("two-day-material"), GuideMaterial::class.java)
        val initial = SearchGuideDecoder.decode(fixture("two-day-partial"), input)
        assertEquals(listOf(1), initial.plans.map { it.days })
        MockWebServer().use { server ->
            server.enqueue(response(fixture("two-day-partial")))
            server.enqueue(response(fixture("two-day-corrected")))
            val guide = generator(server).generate(input, "test-key")
            assertEquals(listOf(1, 2), guide.plans.map { it.days }.sorted())
            assertEquals(initial.plans.single(), guide.plans.single { it.days == 1 })
            val two = guide.plans.single { it.days == 2 }
            assertEquals(listOf("p5"), two.schedule[1].experienceIds)
            assertEquals(1, two.schedule.map { it.sourceUrl }.distinct().size)
            assertEquals(2, server.requestCount)
            server.takeRequest()
            val request = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val repairInput = JsonParser.parseString(request["messages"].asJsonArray[1].asJsonObject["content"].asString).asJsonObject
            assertEquals(listOf(2), repairInput["requestedDays"].asJsonArray.map { it.asInt })
            assertTrue(repairInput["places"].asJsonArray.any { it.asJsonObject["name"].asString == "响堂山石窟" })
        }
    }

    @Test fun failedSecondDayRepairKeepsExistingPlanAndDoesNotRepeat() = runBlocking {
        val input = gson.fromJson(fixture("two-day-material"), GuideMaterial::class.java)
        val initial = SearchGuideDecoder.decode(fixture("two-day-partial"), input)
        for (repair in listOf(response("{\"plans\":[]}"), response(fixture("phone-repaired")),
            MockResponse().setResponseCode(503))) {
            MockWebServer().use { server ->
                server.enqueue(response(fixture("two-day-partial")))
                server.enqueue(repair)
                val guide = generator(server).generate(input, "test-key")
                assertEquals(initial.plans, guide.plans)
                assertEquals(initial.experiences, guide.experiences)
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test fun incompleteHandanQuoteIsRepairedAgainstAcceptedPlacesAndOriginalArticle() = runBlocking {
        assertTrue(SearchGuideDecoder.decode(fixture("initial"), material).plans.isEmpty())
        MockWebServer().use { server ->
            server.enqueue(response(fixture("initial")))
            server.enqueue(response(fixture("repaired")))
            val guide = generator(server).generate(material, "test-key")
            assertEquals(1, guide.plans.size)
            assertEquals(listOf("p1", "p2"), guide.plans.single().schedule.single().experienceIds)
            val quote = JsonParser.parseString(fixture("repaired")).asJsonObject["plans"].asJsonArray[0].asJsonObject
                .getAsJsonArray("schedule")[0].asJsonObject["quote"].asString
            assertEquals(quote, guide.plans.single().schedule.single().evidence)
            assertTrue(material.documents.single().content.contains(quote))
            assertEquals(material.documents.single().url, guide.plans.single().schedule.single().sourceUrl)
            assertEquals(2, server.requestCount)
            server.takeRequest()
            val request = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val messages = request["messages"].asJsonArray
            val input = JsonParser.parseString(messages[1].asJsonObject["content"].asString).asJsonObject
            assertEquals(listOf("p1", "p2"), input["places"].asJsonArray.map { it.asJsonObject["id"].asString })
            assertEquals(material.documents.single().content, input["documents"].asJsonArray.single().asJsonObject["content"].asString)
            assertFalse(messages.toString().contains("test-key"))
        }
    }

    @Test fun invalidOrFailedRepairPreservesInitialContentAndNeverLoops() = runBlocking {
        val original = SearchGuideDecoder.decode(fixture("initial"), material)
        val invented = fixture("repaired").replace("先看城市历史主线", "地铁免费接送所有游客")
        val unknown = fixture("repaired").replace("\"p2\"", "\"missing\"")
        val reversed = JsonParser.parseString(fixture("repaired")).asJsonObject.apply {
            getAsJsonArray("plans")[0].asJsonObject.getAsJsonArray("schedule")[0].asJsonObject
                .add("experienceIds", gson.toJsonTree(listOf("p2", "p1")))
        }.toString()
        for (repair in listOf(response(invented), response(unknown), response(reversed), response("{\"plans\":[]}"),
            response("not-json"), MockResponse().setResponseCode(503))) {
            MockWebServer().use { server ->
                server.enqueue(response(fixture("initial")))
                server.enqueue(repair)
                val guide = generator(server).generate(material, "test-key")
                assertEquals(original.experiences, guide.experiences)
                assertEquals(original.foods, guide.foods)
                assertTrue(guide.plans.isEmpty())
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test fun completeAvailablePlansAndMaterialWithoutItineraryDoNotTriggerRepair() = runBlocking {
        val root = JsonParser.parseString(fixture("initial")).asJsonObject.apply {
            add("plans", JsonParser.parseString(fixture("repaired")).asJsonObject["plans"])
        }
        val noItinerary = material.copy(documents = material.documents.map { it.copy(title = "邯郸景点介绍",
            content = it.content.substringBefore("15:20")) })
        val oneDayOnly = noItinerary.copy(documents = noItinerary.documents.map { it.copy(title = "邯郸一日游攻略") })
        for ((input, content) in listOf(oneDayOnly to root.toString(), noItinerary to fixture("initial"))) {
            MockWebServer().use { server ->
                server.enqueue(response(content))
                generator(server).generate(input, "test-key")
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun recordedPhoneResponseRepairsReversedRouteAndRejectsUnsupportedSecondDay() = runBlocking {
        val input = gson.fromJson(fixture("phone-material"), GuideMaterial::class.java)
        val initial = SearchGuideDecoder.decode(fixture("phone-initial"), input)
        assertTrue(initial.plans.isEmpty())
        MockWebServer().use { server ->
            server.enqueue(response(fixture("phone-initial")))
            server.enqueue(response(fixture("phone-repaired")))
            val guide = generator(server).generate(input, "test-key")
            assertEquals(initial.experiences, guide.experiences)
            assertEquals(1, guide.plans.size)
            assertEquals(1, guide.plans.single().days)
            val day = guide.plans.single().schedule.single()
            assertEquals(listOf("p1", "p2", "p3"), day.experienceIds)
            assertEquals(input.documents.first { it.id == "s13" }.url, day.sourceUrl)
            assertTrue(input.documents.first { it.id == "s13" }.content.replace(Regex("\\s+"), "")
                .contains(requireNotNull(day.evidence).replace(Regex("\\s+"), "")))
            assertEquals(2, server.requestCount)
        }
    }
}
