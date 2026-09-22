package cn.traintrip.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException

class DeepSeekGuideTest {
    private val city = StationCatalog.bundled().cities.first { it.name == "苏州" }
    private val material = GuideMaterial(city.id, city.name, city.province.name,
        listOf(SourcePlace("p1", "示例园林", "具有水景和庭院的园林，适合漫步并了解当地历史文化。", "苏州市区", "https://you.ctrip.com/sight/suzhou11/1.html", null)),
        emptyList(), listOf(GuideSource("携程 · 示例园林", "https://you.ctrip.com/sight/suzhou11/1.html", "2026-09-19")))
    private val content = """{"tagline":"看园林与水景","tags":["园林","漫步"],"suggestedDays":"1 天","pace":"慢逛","season":"出发前查询天气。","arrivalAdvice":"按到站位置安排接驳。","experiences":[{"id":"p1","reason":"沿庭院与水景漫步，了解当地文化。","duration":"1–2 小时"}],"foods":[],"plans":[],"sources":[{"url":"https://invented.invalid"}]}"""
    private fun response(finish: String = "stop", body: String = content) = Gson().toJson(mapOf("choices" to listOf(mapOf("finish_reason" to finish,"message" to mapOf("content" to body)))))

    @Test fun outputUsesOnlyObservedPlacesAndSourcesAndAllowsMissingSections() {
        val guide = DeepSeekGuideGenerator.decode(content, material)
        assertEquals("示例园林", guide.experiences.single().name)
        assertEquals(material.sources, guide.sources)
        assertNull(guide.photo)
        assertTrue(guide.plans.isEmpty())
        assertTrue(guide.foods.isEmpty())
        assertEquals(DeepSeekGuideGenerator.MODEL, guide.model)
    }
    @Test fun traditionalInputIsRejectedBeforeRequestAndOutputIsRejectedBeforeSave() = runBlocking {
        MockWebServer().use { server ->
            val generator=DeepSeekGuideGenerator(server.url("/chat/completions").toString(),OkHttpClient())
            val traditional=material.copy(places=material.places.map { it.copy(introduction="傳統園林風光") })
            assertTrue(runCatching { generator.generate(traditional,"test-key") }.isFailure)
            assertEquals(0,server.requestCount)
            assertTrue(runCatching { DeepSeekGuideGenerator.decode(content.replace("慢逛","遊覽"),material) }.isFailure)
        }
    }
    @Test fun inventedPlaceAndInvalidPlanAreRejected() {
        listOf(content.replace("p1", "unknown"),content.replace("\"plans\":[]", "\"plans\":[{\"days\":1,\"title\":\"路线\",\"schedule\":[{\"label\":\"当天\",\"experienceIds\":[\"missing\"],\"description\":\"走走\"}],\"note\":\"建议\"}]"),content.replace("\"foods\":[]", "\"foods\":null")).forEach { raw ->
            assertTrue(runCatching { DeepSeekGuideGenerator.decode(raw, material) }.isFailure)
        }
    }
    @Test fun requestsCorrectModelAndMapsErrorsWithoutLeakingResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response()))
            val generator = DeepSeekGuideGenerator(server.url("/chat/completions").toString(),OkHttpClient())
            generator.generate(material,"test-key")
            val request = server.takeRequest()
            assertEquals("Bearer test-key",request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"model\":\"deepseek-flash\""))
            assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"))
            assertFalse(body.contains("web_search"))
            val payload = JsonParser.parseString(body).asJsonObject
            assertEquals(8192, payload["max_tokens"].asInt)
            val prompt = payload["messages"].asJsonArray[0].asJsonObject["content"].asString
            assertTrue(prompt.contains("1-10 个景点")); assertTrue(prompt.contains("0-10 种美食"))
            for(code in listOf(401,402,429,503)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("secret-service-detail test-key"))
                val failure = runCatching { generator.generate(material,"test-key") }.exceptionOrNull()
                assertTrue(failure is IOException)
                assertFalse(failure!!.message.orEmpty().contains("test-key"))
                assertFalse(failure.message.orEmpty().contains("secret-service-detail"))
            }
        }
    }
    @Test fun documentGenerationKeepsTenGroundedPlacesAndFoodsIncludingP10() = runBlocking {
        val url = "https://www.suzhou.gov.cn/travel.html"
        val placeQuotes = (1..11).map { "苏州景点$it 位于苏州市区，是了解当地历史文化与古代建筑的游览地点。" }
        val foodQuotes = (1..11).map { "苏州美食$it 是本地资料介绍的特色菜品，可结合个人口味选择。" }
        val input = material.copy(places = emptyList(), foods = emptyList(),
            sources = listOf(GuideSource("苏州旅游", url, "2026-09-22")),
            documents = listOf(SourceDocument("s1", "苏州景点", url, placeQuotes.joinToString(""), "places", emptyList()),
                SourceDocument("s2", "苏州美食", url, foodQuotes.joinToString(""), "food", emptyList())))
        val raw = JsonParser.parseString(content).asJsonObject.apply {
            add("experiences", Gson().toJsonTree((1..11).map { mapOf("id" to "p$it", "sourceId" to "s1", "name" to "苏州景点$it",
                "quote" to placeQuotes[it - 1], "location" to "", "duration" to "1小时") }))
            add("foods", Gson().toJsonTree((1..11).map { mapOf("sourceId" to "s2", "name" to "苏州美食$it", "quote" to foodQuotes[it - 1]) }))
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(body = raw.toString())))
            val guide = DeepSeekGuideGenerator(server.url("/chat/completions").toString(), OkHttpClient()).generate(input, "test-key")
            assertEquals((1..10).map { "p$it" }, guide.experiences.map { it.id })
            assertEquals(placeQuotes.take(10), guide.experiences.map { it.evidence })
            assertEquals(foodQuotes.take(10), guide.foods.map { it.evidence })
            val request = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
            val prompt = request["messages"].asJsonArray[0].asJsonObject["content"].asString
            assertTrue(prompt.contains("1-10 个景点")); assertTrue(prompt.contains("0-10 种具体美食"))
            assertTrue(prompt.contains("p1..p10"))
        }
    }
    @Test fun structuredMaterialsAlsoAllowTenPlacesAndTenFoods() {
        val input = material.copy(places = (1..10).map { material.places.single().copy(id = "p$it", name = "园林$it") },
            foods = (1..10).map { SourceFood("f$it", "美食$it", "原文介绍", material.sources.single().url) })
        val raw = JsonParser.parseString(content).asJsonObject.apply {
            add("experiences", Gson().toJsonTree((1..10).map { mapOf("id" to "p$it", "reason" to "原文中的园林介绍", "duration" to "1小时") }))
            add("foods", Gson().toJsonTree((1..10).map { mapOf("id" to "f$it") }))
        }
        val guide = DeepSeekGuideGenerator.decode(raw.toString(), input)
        assertEquals(input.places.map { it.name }, guide.experiences.map { it.name })
        assertEquals(input.foods.map { it.name }, guide.foods.map { it.name })
    }
    @Test fun configuredModelSnapshotIsUsedForRequestAndCache() = runBlocking {
        MockWebServer().use { server ->
            var model="custom-model"
            server.enqueue(MockResponse().setBody(response()))
            val generator=DeepSeekGuideGenerator(server.url("/chat/completions").toString(),OkHttpClient()) { model }
            val guide=generator.generate(material,"test-key")
            assertEquals(model,guide.model)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"model\":\"custom-model\""))
            model="invalid model"
            assertTrue(runCatching { generator.generate(material,"test-key") }.isFailure)
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun truncatedAndMalformedResponsesNeverProduceCacheableContent() = runBlocking {
        MockWebServer().use { server ->
            val generator = DeepSeekGuideGenerator(server.url("/chat/completions").toString(),OkHttpClient())
            for(body in listOf(response("length"),response(body="not-json"),"{}")) {
                server.enqueue(MockResponse().setBody(body))
                assertTrue(runCatching { generator.generate(material,"test-key") }.isFailure)
            }
        }
    }
    @Test fun malformedArrayMembersAreRejectedInsteadOfSilentlyDropped() {
        for(raw in listOf(content.replace("\"foods\":[]", "\"foods\":[null]"),content.replace("\"plans\":[]", "\"plans\":[1]"),content.replace("\"tags\":[\"园林\",\"漫步\"]", "\"tags\":[\"园林\",42]"))) {
            assertTrue(runCatching { DeepSeekGuideGenerator.decode(raw,material) }.isFailure)
        }
    }
    @Test fun foodDescriptionComesFromSourceEvenWhenModelAddsUnsupportedClaims() {
        val source=SourceFood("f1","示例菜","餐馆菜单中列出","https://you.ctrip.com/yougourmet/restdetail/suzhou11/1.html","携程餐馆页面收录的推荐菜。")
        val raw=content.replace("\"foods\":[]","\"foods\":[{\"id\":\"f1\",\"description\":\"模型编造的口味\"}]")
        val guide=DeepSeekGuideGenerator.decode(raw,material.copy(foods=listOf(source)))
        assertEquals(source.description,guide.foods.single().description)
    }
    @Test fun cacheAndAttemptRecordSurviveFailedRefresh() = runBlocking {
        val guide = DeepSeekGuideGenerator.decode(content, material)
        val store = MemoryStore().apply { save(guide) }
        var calls=0
        val repository = GuideRepository(object:GuideMaterialSource {
            override suspend fun fetch(city:City,stage:(String)->Unit)=material
        },object:GuideGenerator {
            override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;throw IOException("failure") }
        },store)
        assertEquals(guide,repository.cached(city.id))
        assertEquals(0,calls)
        assertTrue(runCatching { repository.generate(city,"test",{}) }.isFailure)
        assertEquals(guide,repository.cached(city.id))
        assertTrue(repository.attempted(city.id))
    }

    private class MemoryStore:GuideStore {
        val data=mutableMapOf<String,DestinationGuide>();val attempts=mutableSetOf<String>()
        override fun read(cityId:String)=data[cityId]
        override fun attempted(cityId:String)=cityId in attempts
        override fun markAttempted(cityId:String) { attempts+=cityId }
        override fun save(guide:DestinationGuide) { data[guide.cityId]=guide }
    }
}
