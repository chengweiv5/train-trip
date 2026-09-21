package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TavilyGuideTest {
    private val city = StationCatalog.bundled().cities.first { it.name == "泰安" }
    private val quote = "岱庙位于泰安市区，是了解当地历史文化与古代建筑的游览地点，庭院内保存着传统建筑。"
    private fun result(url: String = "https://tsgw.taian.gov.cn/art/1.html", content: String = quote) =
        mapOf("title" to "泰安岱庙介绍", "url" to url, "content" to content)
    private fun response(vararg results: Map<String, Any>) = Gson().toJson(mapOf("results" to results.toList()))
    private fun source(server: MockWebServer) = TavilyGuideSource({ "tvly-test-only" }, server.url("/search").toString(), OkHttpClient())
    private fun material() = GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),
        listOf(GuideSource("岱庙介绍","https://tsgw.taian.gov.cn/art/1.html","2026-09-19")),
        listOf(SourceDocument("s1","泰安岱庙介绍","https://tsgw.taian.gov.cn/art/1.html",quote,"places")))
    private fun content() = """{"tagline":"漫步历史古建","tags":["古建","文化"],"suggestedDays":"1 天","pace":"慢逛","experiences":[{"id":"p1","sourceId":"s1","name":"岱庙","quote":"$quote","duration":"1–2 小时"}],"foods":[],"plans":[]}"""

    @Test fun rootImagesRequireCityAndPlaceAndKeepHonestAttribution() {
        val images=(1..7).map { mapOf("url" to "https://tsgw.taian.gov.cn/picture/p$it.jpg","description" to "泰安岱庙庭院建筑实景图片") }+
            listOf(mapOf("url" to "https://tsgw.taian.gov.cn/picture/wrong.jpg","description" to "北京故宫实景图片"),
                mapOf("url" to "https://tsgw.taian.gov.cn/picture/zh.jpg","description" to "泰安岱廟傳統建築"),
                mapOf("url" to "https://tsgw.taian.gov.cn/logo.jpg","description" to "泰安岱庙图标说明"))
        val docs=TavilyGuideSource.parse(Gson().toJson(mapOf("results" to listOf(result()),"images" to images)),city,"places")
            .map { it.copy(id="s1") }
        val guide=SearchGuideDecoder.decode(content(),material().copy(documents=docs))
        assertEquals(5,guide.gallery.size)
        assertEquals(guide.gallery.first(),guide.photo)
        assertTrue(guide.gallery.all { it.sourceUrl==it.remoteUrl && it.credit.startsWith("Tavily") })
        assertEquals(5,guide.gallery.map { it.remoteUrl }.distinct().size)
    }
    @Test fun legacySinglePhotoAndNewGalleryRoundTripAndRejectTraditionalCaptions() {
        val legacy=DestinationGuides.all.first()
        val raw=JsonParser.parseString(Gson().toJson(legacy)).asJsonObject.apply { remove("photos") }
        val old=Gson().fromJson(raw,DestinationGuide::class.java)
        assertEquals(listOf(old.photo),old.gallery)
        val updated=legacy.copy(generatedAt=java.time.Instant.now().toString(),model="test-model").withPhotos(listOf(
            legacy.photo!!,legacy.photo!!.copy(assetName="second.jpg")))
        val restored=Gson().fromJson(Gson().toJson(updated),DestinationGuide::class.java)
        assertEquals(2,DestinationGuides.validateGenerated(restored,legacy.cityId).gallery.size)
        assertTrue(runCatching { DestinationGuides.validateGenerated(updated.withPhotos(listOf(legacy.photo!!.copy(description="傳統建築"))),legacy.cityId) }.isFailure)
    }
    @Test fun basicSearchRestrictsDomainsAndSendsKeyOnlyInHeader() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(result())))
            server.enqueue(MockResponse().setBody(response()))
            val material = source(server).fetch(city) {}
            assertEquals(1,material.documents.size)
            repeat(2) {
                val request = server.takeRequest()
                assertEquals("Bearer tvly-test-only",request.getHeader("Authorization"))
                val body = request.body.readUtf8()
                assertFalse(body.contains("tvly-test-only"))
                val json = JsonParser.parseString(body).asJsonObject
                assertEquals("basic",json["search_depth"].asString)
                assertEquals("restrict",json["include_domains_mode"].asString)
                assertFalse(json["include_answer"].asBoolean)
                assertFalse(json["auto_parameters"].asBoolean)
                assertTrue(json["query"].asString.contains(city.name))
            }
        }
    }
    @Test fun foreignSpoofedAndWrongCitySourcesAreExcluded() {
        val raw = response(result(),result("https://gov.cn.evil.test/a"),result("https://evilgov.cn/a"),
            result("https://user:pass@taian.gov.cn/a"),result("https://taian.gov.cn:8443/a"),
            mapOf("title" to "雄安", "url" to "https://www.xiongan.gov.cn/a", "content" to "雄安的旅游资料和当地特色美食介绍"))
        assertEquals(1,TavilyGuideSource.parse(raw,city,"places").size)
        assertFalse(GuideNetwork.isPhotoUrl("https://dimg.c-ctrip.com.evil.test/a.jpg"))
        assertTrue(GuideNetwork.isPhotoUrl("https://tsgw.taian.gov.cn/picture/a.jpg"))
    }
    @Test fun traditionalVersionTitleBodyAndImageMetadataAreExcluded() {
        val original=result()
        val image=mapOf("url" to "https://tsgw.taian.gov.cn/picture/a.jpg", "description" to "岱庙傳統建築照片")
        val raw=response(result("http://he.people.com.cn/BIG5/n2/2021/a.html"),
            original + ("title" to "泰安遊覽"),original + ("content" to quote+"傳統建築"),
            original + ("content" to quote.repeat(100)+"傳統建築"),
            original + ("images" to listOf(image)))
        val docs=TavilyGuideSource.parse(raw,city,"places")
        assertEquals(1,docs.size)
        assertTrue(docs.single().images.isEmpty())
        assertTrue(SimplifiedGuidePolicy.documentAllowed(docs.single()))
    }
    @Test fun traditionalMaterialAndModelOutputCannotBeCached() {
        assertTrue(runCatching { SearchGuideDecoder.decode(content(),material().copy(documents=material().documents.map { it.copy(content=it.content+"風景") })) }.isFailure)
        assertTrue(runCatching { SearchGuideDecoder.decode(content().replace("漫步历史古建","漫步歷史古建"),material()) }.isFailure)
        assertTrue(runCatching { SearchGuideDecoder.decode(content(),material().copy(sources=material().sources.map { it.copy(title="泰安旅遊") })) }.isFailure)
    }
    @Test fun onlyTraditionalResultsStopBeforeMoreSearchesOrModelCalls() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(result("https://he.people.com.cn/BIG5/a"))))
            val error=runCatching { source(server).fetch(city) {} }.exceptionOrNull()
            assertTrue(error!!.message.orEmpty().contains("简体中文"))
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun errorsAndEmptyPlacesStopBeforeSecondRequestWithoutLeakingResponse() = runBlocking {
        for (code in listOf(401,402,429,432,503)) MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("secret-response tvly-test-only"))
            val error = runCatching { source(server).fetch(city) {} }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error!!.message.orEmpty().contains("secret-response"))
            assertEquals(1,server.requestCount)
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response()))
            assertTrue(runCatching { source(server).fetch(city) {} }.isFailure)
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun redirectsNeverReceiveCredential() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { other ->
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location",other.url("/stolen")))
            assertTrue(runCatching { source(first).fetch(city) {} }.isFailure)
            assertEquals(0,other.requestCount)
        } }
    }
    @Test fun extractedItemsRetainExactEvidenceAndRejectHallucinations() {
        val guide = SearchGuideDecoder.decode(content(),material())
        assertEquals(quote,guide.experiences.single().reason)
        assertEquals(quote,guide.experiences.single().evidence)
        assertEquals(material().documents.single().url,guide.experiences.single().sourceUrl)
        assertTrue(guide.foods.isEmpty());assertNull(guide.photo)
        for (raw in listOf(content().replace("s1","unknown"),content().replace("\"name\":\"岱庙\"","\"name\":\"故宫\""),
            content().replace(quote,"岱庙每晚举办免费演出且提供独家美食体验，完全由模型编造。"))) {
            assertTrue(runCatching { SearchGuideDecoder.decode(raw,material()) }.isFailure)
        }
    }
    @Test fun incompleteOptionalRouteIsDroppedWithoutLosingVerifiedContent() {
        val raw = content().replace("\"plans\":[]", "\"plans\":[{\"days\":2,\"schedule\":[{\"experienceIds\":[\"p1\"]},{\"experienceIds\":[]}]}]")
        val guide=SearchGuideDecoder.decode(raw,material())
        assertEquals(1,guide.experiences.size);assertTrue(guide.plans.isEmpty())
        val partial = content().replace(quote,quote+"后面被截断的残句")
        assertTrue(runCatching { SearchGuideDecoder.decode(partial,material()) }.isFailure)
        val partialSource=material().copy(documents=material().documents.map { it.copy(content=it.content+"后面被截断的残句") })
        assertEquals(quote,SearchGuideDecoder.decode(partial,partialSource).experiences.single().reason)
    }
    @Test fun adjacentSectionHeadingCanAnchorAQuoteButDistantNameCannot() {
        val paragraph="位于泰安市区，是了解当地历史文化与古代建筑的游览地点，庭院内保存着传统建筑。"
        val raw=content().replace(quote,paragraph)
        val adjacent=material().copy(documents=material().documents.map { it.copy(content="【岱庙】"+paragraph) })
        assertEquals("【岱庙】"+paragraph,SearchGuideDecoder.decode(raw,adjacent).experiences.single().evidence)
        val distant=material().copy(documents=material().documents.map { it.copy(content="岱庙相关资料。【其他景点】"+paragraph) })
        assertTrue(runCatching { SearchGuideDecoder.decode(raw,distant) }.isFailure)
    }
    @Test fun oneInvalidItemDoesNotRejectOtherGroundedPlaces() {
        val root=JsonParser.parseString(content()).asJsonObject
        val invented=root["experiences"].asJsonArray[0].deepCopy().asJsonObject
        invented.addProperty("id","p2");invented.addProperty("sourceId","invented")
        root["experiences"].asJsonArray.add(invented)
        assertEquals(1,SearchGuideDecoder.decode(root.toString(),material()).experiences.size)
    }
    @Test fun unsupportedFoodAndPhotoAreNotInvented() {
        val foodRaw = content().replace("\"foods\":[]","\"foods\":[{\"sourceId\":\"s1\",\"name\":\"岱庙\",\"quote\":\"$quote\"}]")
        assertTrue(SearchGuideDecoder.decode(foodRaw,material()).foods.isEmpty())
        val doc = material().documents.single()
        val withLogo = material().copy(documents=listOf(doc.copy(images=listOf(SourceImage("https://tsgw.taian.gov.cn/logo.jpg","岱庙建筑标志照片")))))
        assertNull(SearchGuideDecoder.decode(content(),withLogo).photo)
        val withPhoto = material().copy(documents=listOf(doc.copy(images=listOf(SourceImage("https://tsgw.taian.gov.cn/picture/1.jpg","岱庙庭院及其传统建筑")))))
        assertNotNull(SearchGuideDecoder.decode(content(),withPhoto).photo)
    }
}
