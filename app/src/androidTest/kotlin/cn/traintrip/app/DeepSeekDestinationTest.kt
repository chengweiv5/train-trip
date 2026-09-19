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
    private fun vm(credentials:Credentials,store:Store,generator:GuideGenerator)=DestinationViewModel(compose.activity.application as Application,credentials,store,source,generator)
    @Test fun newCityGeneratesOnceAndRestartUsesSavedContent() {
        val store=Store();var calls=0
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;return guide() } }
        val model=vm(Credentials(),store,generator)
        compose.runOnIdle { model.open(city) }
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
        compose.waitUntil(5000) { model.state.value.error!=null && !model.state.value.loading }
        val restarted=vm(Credentials(),store,generator)
        compose.runOnIdle { restarted.open(city) }
        compose.waitUntil(5000) { restarted.state.value.error!=null && !restarted.state.value.loading }
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
    @Test fun missingKeyDoesNotCallSourceAndSavingStartsGeneration() {
        var calls=0;val credentials=Credentials(null)
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide { calls++;return guide() } }
        val model=vm(credentials,Store(),generator)
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.cityId!=null }
        assertEquals(0,calls)
        compose.runOnIdle { model.saveKey("fake-key") {} }
        compose.waitUntil(5000) { model.state.value.guide!=null }
        assertEquals(1,calls)
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
        compose.runOnIdle { model.leave();model.saveKey("fake-key") { saved=true } }
        compose.waitUntil(5000) { saved }
        assertEquals(0,calls)
        compose.runOnIdle { model.open(city) }
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
        val stored=context.getSharedPreferences("destination-ai",0).getString("encryptedKey",null)
        assertFalse(stored.orEmpty().contains(fake))
        settings.remove()
        assertNull(settings.read())
        val store=AndroidGuideStore(context)
        store.save(guide())
        assertEquals(city.id,AndroidGuideStore(context).read(city.id)?.cityId)
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
