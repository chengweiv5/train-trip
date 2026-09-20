package cn.traintrip.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DeepSeekDestinationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val city=StationCatalog.bundled().cities.first { it.name=="苏州" }
    private fun guide()=DestinationGuides.all.first().copy(cityId=city.id,name=city.name,photo=null,foods=emptyList(),plans=emptyList(),generatedAt=Instant.now().toString(),model=DeepSeekGuideGenerator.MODEL)
    private class Credentials(var key:String?="fake-key"):GuideCredentials {
        override fun read()=key
        override fun save(value:String) { key=value }
        override fun remove() { key=null }
    }
    private class Store:GuideStore {
        val guides=mutableMapOf<String,DestinationGuide>();val attempts=mutableSetOf<String>()
        override fun read(cityId:String)=guides[cityId]
        override fun attempted(cityId:String)=cityId in attempts
        override fun markAttempted(cityId:String) { attempts+=cityId }
        override fun save(guide:DestinationGuide) { guides[guide.cityId]=guide }
    }
    private val source=object:GuideMaterialSource {
        override suspend fun fetch(city:City,stage:(String)->Unit)=GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),emptyList())
    }
    private fun vm(credentials:Credentials,store:Store,generator:GuideGenerator)=DestinationViewModel(compose.activity.application as Application,credentials,store,source,generator,Credentials("tvly-test-only-123456789"))
    @Test fun newCityWaitsForExplicitActionAndRestartUsesSavedContent() {
        val store=Store();var calls=0
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;return guide() } }
        val model=vm(Credentials(),store,generator)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading }
        assertEquals(0,calls)
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { model.state.value.guide?.generatedAt!=null && !model.state.value.loading }
        compose.runOnIdle { model.cancel();model.open(city) }
        assertEquals(1,calls)
        val restarted=vm(Credentials(),store,generator)
        compose.runOnIdle { restarted.open(city) }
        compose.waitUntil(5000) { restarted.state.value.guide!=null && !restarted.state.value.loading }
        assertEquals(1,calls)
        assertTrue(store.attempted(city.id))
    }
    @Test fun failureDoesNotAutomaticallyRetryAfterRestartAndRefreshKeepsOldGuide() {
        val store=Store();var calls=0
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;throw IOException("测试失败") } }
        val model=vm(Credentials(),store,generator)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading }
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { model.state.value.error!=null && !model.state.value.loading }
        val restarted=vm(Credentials(),store,generator)
        compose.runOnIdle { restarted.open(city) }
        compose.waitUntil(5000) { !restarted.state.value.loading }
        assertNull(restarted.state.value.error)
        assertEquals(1,calls)
        store.save(guide())
        val cached=vm(Credentials(),store,generator)
        compose.runOnIdle { cached.open(city) }
        compose.waitUntil(5000) { cached.state.value.guide!=null && !cached.state.value.loading }
        compose.runOnIdle { cached.retry() }
        compose.waitUntil(5000) { cached.state.value.error!=null && !cached.state.value.loading }
        assertNotNull(cached.state.value.guide)
        assertNotNull(store.read(city.id))
        assertEquals(2,calls)
    }
    @Test fun missingKeyDoesNotCallSourceAndSavingRequiresExplicitRetry() {
        var calls=0;val credentials=Credentials(null)
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;return guide() } }
        val model=vm(credentials,Store(),generator)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.cityId!=null }
        assertEquals(0,calls)
        var saved=false
        compose.runOnIdle { model.saveKey("sk-test-only-1234567890") { saved=true } }
        compose.waitUntil(5000) { saved }
        assertEquals(0,calls)
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { model.state.value.guide!=null }
        assertEquals(1,calls)
    }
    @Test fun missingTavilyDoesNotSearchOrMarkAttemptAndSavePreservesDeepSeek() {
        var calls=0;val store=Store();val deep=Credentials("sk-test-existing-123456789");val search=Credentials(null)
        val countingSource=object:GuideMaterialSource {
            override suspend fun fetch(city:City,stage:(String)->Unit):GuideMaterial { calls++;return source.fetch(city,stage) }
        }
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String)=guide() }
        val model=DestinationViewModel(compose.activity.application as Application,deep,store,countingSource,generator,search)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { model.state.value.cityId!=null && !model.state.value.loading }
        assertEquals(0,calls);assertFalse(store.attempted(city.id))
        var saved=false
        compose.runOnIdle { model.saveKeys("","tvly-test-new-123456789") { saved=true } }
        compose.waitUntil(5000) { saved }
        assertEquals(0,calls)
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { model.state.value.guide!=null }
        assertEquals("sk-test-existing-123456789",deep.read());assertEquals(1,calls)
        var removed=false
        compose.runOnIdle { model.removeTavilyKey { removed=true } }
        compose.waitUntil(5000) { removed }
        assertTrue(model.state.value.configured);assertFalse(model.state.value.tavilyConfigured)
        assertNotNull(store.read(city.id));assertEquals("sk-test-existing-123456789",deep.read())
    }
    @Test fun cancellationDoesNotPublishOrSaveLateResult() {
        val store=Store()
        val entered=kotlinx.coroutines.CompletableDeferred<Unit>()
        val release=kotlinx.coroutines.CompletableDeferred<Unit>()
        val returned=kotlinx.coroutines.CompletableDeferred<Unit>()
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide {
            entered.complete(Unit)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { release.await() }
            returned.complete(Unit)
            return guide()
        } }
        val model=vm(Credentials(),store,generator)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading }
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { entered.isCompleted }
        compose.runOnIdle { model.cancel();release.complete(Unit) }
        compose.waitUntil(5000) { returned.isCompleted && !model.state.value.loading }
        assertNull(model.state.value.guide)
        assertNull(store.read(city.id))
        assertTrue(store.attempted(city.id))
    }
    @Test fun savingKeyAfterLeavingDoesNotGenerateForPreviousCity() {
        var calls=0
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;return guide() } }
        val model=vm(Credentials(null),Store(),generator)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.cityId!=null }
        var saved=false
        compose.runOnIdle { model.leave();model.saveKey("sk-test-only-1234567890") { saved=true } }
        compose.waitUntil(5000) { saved }
        assertEquals(0,calls)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading }
        assertEquals(0,calls)
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { model.state.value.guide!=null }
        assertEquals(1,calls)
    }
    @Test fun emptyOptionalSectionsRemainNavigable() {
        val guide=guide()
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DestinationGuideScreen(city.name,guide,{}, {}, {},runtime=DestinationState(city.id,guide,configured=true)) } } }
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.onNodeWithText("暂未取得可靠的美食资料。").assertIsDisplayed()
        compose.onNodeWithTag("guide-tab-plans").performClick()
        compose.onNodeWithText("暂未整理出可靠路线，可先按景点安排游览。").assertIsDisplayed()
        compose.onNodeWithTag("guide-trains").assertIsDisplayed()
    }
    @Test fun androidStoreAndKeystoreRoundTripPreserveContentWithoutPlaintextKey() {
        val context=compose.activity
        val settings=DeepSeekSettings(context)
        val fake="sk-instrumentation-test-only-123456"
        settings.save(fake)
        assertEquals(fake,settings.read())
        val tavily=TavilySettings(context)
        val searchFake="tvly-instrumentation-test-only-123456"
        tavily.save(searchFake)
        assertEquals(searchFake,tavily.read())
        assertEquals(fake,DeepSeekSettings(context).read())
        tavily.remove()
        assertNull(tavily.read())
        assertEquals(fake,settings.read())
        val stored=context.getSharedPreferences("destination-ai",0).getString("encryptedKey",null)
        assertFalse(stored.orEmpty().contains(fake))
        settings.remove()
        assertNull(settings.read())
        val store=AndroidGuideStore(context)
        store.save(guide())
        assertEquals(city.id,AndroidGuideStore(context).read(city.id)?.cityId)
    }
    @Test fun legacyTraditionalCacheIsHiddenAndGetsOnlyOneAutomaticRetry() {
        val context=compose.activity
        val legacy=guide().copy(cityId="130600",name="保定",tagline="保定文化名城待卿來",
            sources=listOf(GuideSource("河北保定古城", "http://he.people.com.cn/BIG5/n2/2021/1003/c192235-34942447.html","2026-09-19")))
        val directory=java.io.File(context.filesDir,"destination-guides").apply { mkdirs() }
        val cache=java.io.File(directory,"130600.json")
        val old=if(cache.exists())cache.readBytes() else null
        val attempt=java.io.File(directory,"130600.attempt")
        val oldAttempt=if(attempt.exists())attempt.readBytes() else null
        val current=java.io.File(directory,"130600.simplified-attempt")
        val oldCurrent=if(current.exists())current.readBytes() else null
        try {
            current.delete()
            cache.writeText(com.google.gson.Gson().toJson(mapOf("schemaVersion" to 1,"guide" to legacy)))
            val raw=cache.readBytes();attempt.writeBytes(byteArrayOf(1))
            val store=AndroidGuideStore(context)
            assertNull(store.read("130600"));assertFalse(store.all().containsKey("130600"))
            assertFalse(store.attempted("130600"))
            store.markAttempted("130600")
            assertTrue(AndroidGuideStore(context).attempted("130600"))
            assertArrayEquals(raw,cache.readBytes())
            assertTrue(runCatching { store.save(legacy) }.isFailure)
            val valid=guide().copy(cityId="130600",name="保定")
            store.save(valid)
            assertEquals(valid,AndroidGuideStore(context).read("130600"))
        } finally {
            fun restore(file:java.io.File,bytes:ByteArray?) { if(bytes==null)file.delete() else file.writeBytes(bytes) }
            restore(cache,old);restore(attempt,oldAttempt);restore(current,oldCurrent)
        }
    }
    @Test fun sourcePhotoIsDecodedAndSavedAndInvalidPhotoIsOptional() = kotlinx.coroutines.runBlocking {
        val bitmap=android.graphics.Bitmap.createBitmap(1800,900,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        bitmap.recycle()
        val photo=DestinationPhoto("remote_test_photo.jpg","苏州测试配图","测试资料","https://you.ctrip.com/place/suzhou11.html",remoteUrl="https://dimg04.c-ctrip.com/images/test.jpg")
        val original=guide().copy(photo=photo)
        val store=AndroidGuideStore(compose.activity) { bytes }
        assertNotNull(store.preparePhoto(original).photo)
        val saved=java.io.File(compose.activity.filesDir,"destination-guides/${photo.assetName}")
        val decoded=android.graphics.BitmapFactory.decodeFile(saved.path)
        assertNotNull(decoded)
        assertTrue(decoded.width<=1200)
        decoded.recycle()
        assertNull(AndroidGuideStore(compose.activity) { byteArrayOf(1,2) }.preparePhoto(original).photo)
    }
}
