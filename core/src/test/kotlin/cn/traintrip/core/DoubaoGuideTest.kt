package cn.traintrip.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DoubaoGuideTest {
    private val city = StationCatalog.bundled().cities.first { it.name == "泰安" }
    private val quote = "岱庙位于泰安市区，是了解当地历史文化与古代建筑的游览地点，庭院内保存着传统建筑。"
    private fun result(url: String = "https://you.ctrip.com/art/1.html", content: String = quote) =
        mapOf("Title" to "泰安岱庙介绍", "Url" to url, "Content" to content)
    private fun response(vararg results: Map<String, Any>) = Gson().toJson(mapOf("ResponseMetadata" to emptyMap<String, Any>(), "Result" to mapOf("WebResults" to results.toList())))
    private fun source(server: MockWebServer) = DoubaoGuideSource({ "doubao-test-only-123456789" }, server.url("/search").toString(), OkHttpClient())
    private fun material() = GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),
        listOf(GuideSource("岱庙介绍","https://you.ctrip.com/art/1.html","2026-09-19")),
        listOf(SourceDocument("s1","泰安岱庙介绍","https://you.ctrip.com/art/1.html",quote,"places")))
    private fun content() = """{"tagline":"漫步历史古建","tags":["古建","文化"],"suggestedDays":"1 天","pace":"慢逛","experiences":[{"id":"p1","sourceId":"s1","name":"岱庙","quote":"$quote","duration":"1–2 小时"}],"foods":[],"plans":[]}"""

    private fun image(title: String = "泰安岱庙庭院建筑", url: String = "https://p11-volcsearch-sign.byteimg.com/photo.jpeg?x-signature=test", source: String = "https://you.ctrip.com/sight/taian746/1.html") =
        mapOf("Title" to title, "Url" to source, "Image" to mapOf("Url" to url, "Width" to 900, "Height" to 600))
    private fun images(vararg items: Map<String, Any>) = Gson().toJson(mapOf("ResponseMetadata" to emptyMap<String, Any>(),
        "Result" to mapOf("ImageResults" to items.toList())))
    @Test fun communityArticlesAndRealFoodImagesAreUsable() {
        val docs = DoubaoGuideSource.parse(response(result("https://blog.sina.com.cn/s/blog_123.html")),city,"places")
        assertEquals(1, docs.size)
        val food = images(image(title="泰安煎饼成品实拍",source="https://post.smzdm.com/p/123/"))
        assertEquals(1, DoubaoGuideSource.images(DoubaoGuideSource.result(food),city,PhotoSubject("food","煎饼")).size)
    }
    @Test fun genericAttractionSuffixDoesNotDiscardAnOtherwiseMatchingPhoto() {
        val handan=StationCatalog.bundled().cities.first { it.name == "邯郸" }
        val raw = images(image(title="邯郸娲皇宫山地景观"))
        assertEquals(1, DoubaoGuideSource.images(DoubaoGuideSource.result(raw),handan,PhotoSubject("place","娲皇宫景区")).size)
        assertTrue(DoubaoGuideSource.images(DoubaoGuideSource.result(raw),handan,PhotoSubject("place","娲皇宫步行街")).isEmpty())
    }
    @Test fun photoSearchTargetsMissingItemsAndUsesTitleWithoutRequiringDescription() = runTest {
        val guide = SearchGuideDecoder.decode(content(), material()).let { original ->
            original.copy(experiences = listOf("岱庙", "泰山", "天外村").mapIndexed { index, name ->
                original.experiences.single().copy(id = "p${index+1}", name = name) })
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(images(image(), image(title = "北京岱庙庭院建筑"))))
            server.enqueue(MockResponse().setResponseCode(503).setBody("private upstream detail"))
            server.enqueue(MockResponse().setBody(images()))
            server.enqueue(MockResponse().setBody(images()))
            server.enqueue(MockResponse().setBody(images()))
            val photos = source(server).fetch(city, guide) {}
            assertEquals(1, photos.photos.size); assertTrue(photos.failed)
            assertEquals(5, server.requestCount)
            assertTrue(photos.photos.single().sourceUrl.startsWith("https://you.ctrip.com/"))
            assertTrue(photos.photos.single().credit.startsWith("豆包搜索"))
            for (query in listOf("泰安岱庙", "泰安泰山", "泰安 泰山 实景", "泰安天外村", "泰安 天外村 实景")) {
                val request = server.takeRequest()
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals("image", body["SearchType"].asString)
                assertEquals(query, body["Query"].asString)
                assertEquals(5, body["Count"].asInt)
            }
        }
        MockWebServer().use { server ->
            assertTrue(DoubaoGuideSource({ null }, server.url("/search").toString(), OkHttpClient()).fetch(city, guide) {}.photos.isEmpty())
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun imageOwnershipAndSourceChecksRejectUnrelatedDecorativeOrFoodEnvironmentImages() {
        val subject = PhotoSubject("place", "岱庙")
        val raw = images(image(), image(source = "https://you.ctrip.com.evil.test/a"), image(title = "泰安岱廟傳統建築"),
            image(url = "https://evil.byteimg.com/a.jpg"), image(title = "泰安岱庙地图"), image(title = "泰安岱庙二维码"),
            image(title = "北京岱庙建筑"), image(source = ""))
        assertEquals(1, DoubaoGuideSource.images(DoubaoGuideSource.result(raw), city, subject).size)
        val food = images(image(title = "泰安煎饼门店环境"), image(title = "泰安煎饼成品实拍"))
        assertEquals(1, DoubaoGuideSource.images(DoubaoGuideSource.result(food), city, PhotoSubject("food", "煎饼")).size)
    }
    @Test fun inlineImagesKeepOriginalAttributionAndPerItemCap() {
        val inline = (1..5).map { mapOf("ImageUrl" to "https://you.ctrip.com/picture/p$it.jpg", "Alt" to "泰安岱庙庭院建筑实景") }
        val docs = DoubaoGuideSource.parse(response(result() + ("InlineImages" to inline)), city, "places").map { it.copy(id="s1") }
        val guide = SearchGuideDecoder.decode(content(), material().copy(documents=docs))
        assertEquals(3, guide.gallery.size)
        assertTrue(guide.gallery.all { it.sourceUrl == docs.single().url && it.credit.startsWith("原文页面") })
    }
    @Test fun businessErrorsAndInvalidKeysNeverLeakDetailsOrMakeExtraRequests() = runTest {
        MockWebServer().use { server ->
            for (code in listOf("10400", "10401", "10403", "10406", "10408", "10409", "10410", "10412", "10500")) {
                server.enqueue(MockResponse().setBody(Gson().toJson(mapOf("ResponseMetadata" to mapOf("Error" to mapOf("Code" to code, "Message" to "secret-response")), "Result" to null))))
                val failure = runCatching { source(server).fetch(city) {} }.exceptionOrNull()
                assertNotNull(failure); assertFalse(failure!!.message.orEmpty().contains("secret-response"))
            }
            assertEquals(9, server.requestCount)
            for (key in listOf("tvly-old-key-123456789", "bad key", "中文密钥")) {
                assertTrue(runCatching { DoubaoGuideSource({ key }, server.url("/search").toString(), OkHttpClient()).fetch(city) {} }.isFailure)
            }
            assertEquals(9, server.requestCount)
        }
    }
    @Test fun snippetOrSummaryAloneCannotBecomeOriginalEvidence() {
        assertTrue(DoubaoGuideSource.parse(response(result(content="") + mapOf("Summary" to quote, "Snippet" to quote)),city,"places").isEmpty())
    }
    @Test fun directoryRowsWithoutCompleteSentencesAreExcludedBeforeGeneration() {
        val rows = "泰安景区名录\n1 岱庙 泰安市区\n2 泰山 国家级风景区 泰安市区\n3 天外村 泰安市区\n"
        val docs = DoubaoGuideSource.parse(response(result(content=rows),result(url="https://you.ctrip.com/detail.html")),city,"places")
        assertEquals(listOf(quote),docs.map { it.content })
    }
    @Test fun cancellationStopsBeforeFoodSearch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch(Dispatchers.Default) { source(server).fetch(city) {} }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(1,server.requestCount)
        }
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
    @Test fun webSearchUsesCommunityArticlesAndSeparateRouteQueries() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(result(),result("https://www.mafengwo.cn/i/1.html"),result("https://post.smzdm.com/p/1/"))))
            repeat(4) { server.enqueue(MockResponse().setBody(response())) }
            val material = source(server).fetch(city) {}
            assertEquals(3,material.documents.size)
            assertTrue(material.documents.all { GuideSearchPolicy.community(it.url) })
            val queries = (1..5).map {
                val request = server.takeRequest()
                assertEquals("Bearer doubao-test-only-123456789",request.getHeader("Authorization"))
                val body = request.body.readUtf8()
                assertFalse(body.contains("doubao-test-only-123456789"))
                val json = JsonParser.parseString(body).asJsonObject
                assertEquals(10,json["Count"].asInt)
                assertTrue(json["Filter"].asJsonObject["NeedContent"].asBoolean)
                json
            }
            assertTrue(queries.first()["Filter"].asJsonObject["Sites"].asString.contains("you.ctrip.com"))
            assertTrue(queries[3]["Query"].asString.contains("一日游"))
            assertTrue(queries[4]["Query"].asString.contains("两天一夜"))
        }
    }
    @Test fun rateLimitedImageSearchRetriesBusinessErrorThenAcceptsResult() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ResponseMetadata":{"Error":{"Code":"rate_limit_exceeded","CodeN":700429}},"Result":null}"""))
            server.enqueue(MockResponse().setBody(images(image())))
            val photos=source(server).fetch(city,SearchGuideDecoder.decode(content(),material())) {}
            assertEquals(1,photos.photos.size)
            assertFalse(photos.failed)
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun governmentAndOfficialMediaDoNotConsumeCommunityArticleSlots() {
        val raw=response(result("https://www.taian.gov.cn/a"),result("https://www.news.cn/a"),result("https://blog.sina.com.cn/a"))
        assertEquals(listOf("https://blog.sina.com.cn/a"),DoubaoGuideSource.parse(raw,city,"places").map { it.url })
    }
    @Test fun foreignSpoofedAndWrongCitySourcesAreExcluded() {
        val raw = response(result(),result("https://gov.cn.evil.test/a"),result("https://192.168.1.2/a"),
            result("https://user:pass@taian.gov.cn/a"),result("https://taian.gov.cn:8443/a"),
            mapOf("Title" to "雄安", "Url" to "https://www.xiongan.gov.cn/a", "Content" to "雄安的旅游资料和当地特色美食介绍"))
        assertEquals(1,DoubaoGuideSource.parse(raw,city,"places").size)
        assertFalse(GuideNetwork.isPhotoUrl("https://dimg.c-ctrip.com.evil.test/a.jpg"))
        assertTrue(GuideNetwork.isPhotoUrl("https://you.ctrip.com/picture/a.jpg"))
    }
    @Test fun traditionalVersionTitleBodyAndImageMetadataAreExcluded() {
        val original=result()
        val image=mapOf("ImageUrl" to "https://you.ctrip.com/picture/a.jpg", "Alt" to "岱庙傳統建築照片")
        val raw=response(result("http://he.people.com.cn/BIG5/n2/2021/a.html"),
            original + ("Title" to "泰安遊覽"),original + ("Content" to quote+"傳統建築"),
            original + ("Content" to quote.repeat(100)+"傳統建築"),
            original + ("InlineImages" to listOf(image)))
        val docs=DoubaoGuideSource.parse(raw,city,"places")
        assertEquals(1,docs.size)
        assertTrue(docs.single().images.isEmpty())
        assertTrue(SimplifiedGuidePolicy.documentAllowed(docs.single()))
    }
    @Test fun traditionalMaterialAndModelOutputCannotBeCached() {
        assertTrue(runCatching { SearchGuideDecoder.decode(content(),material().copy(documents=material().documents.map { it.copy(content=it.content+"風景") })) }.isFailure)
        assertTrue(runCatching { SearchGuideDecoder.decode(content().replace("漫步历史古建","漫步歷史古建"),material()) }.isFailure)
        assertTrue(runCatching { SearchGuideDecoder.decode(content(),material().copy(sources=material().sources.map { it.copy(title="泰安旅遊") })) }.isFailure)
    }
    @Test fun onlyTraditionalResultsStopBeforeMoreSearchesOrModelCalls() = runTest {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(response(result("https://he.people.com.cn/BIG5/a")))) }
            val error=runCatching { source(server).fetch(city) {} }.exceptionOrNull()
            assertTrue(error!!.message.orEmpty().contains("简体中文"))
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun errorsAndEmptyPlacesStopBeforeSecondRequestWithoutLeakingResponse() = runTest {
        for (code in listOf(401,402,432,503)) MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("secret-response doubao-test-only-123456789"))
            val error = runCatching { source(server).fetch(city) {} }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error!!.message.orEmpty().contains("secret-response"))
            assertEquals(1,server.requestCount)
        }
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody(response())) }
            assertTrue(runCatching { source(server).fetch(city) {} }.isFailure)
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun redirectsNeverReceiveCredential() = runTest {
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
        val withLogo = material().copy(documents=listOf(doc.copy(images=listOf(SourceImage("https://you.ctrip.com/logo.jpg","岱庙建筑标志照片")))))
        assertNull(SearchGuideDecoder.decode(content(),withLogo).photo)
        val withPhoto = material().copy(documents=listOf(doc.copy(images=listOf(SourceImage("https://you.ctrip.com/picture/1.jpg","泰安岱庙庭院及其传统建筑")))))
        assertNotNull(SearchGuideDecoder.decode(content(),withPhoto).photo)
    }
}
