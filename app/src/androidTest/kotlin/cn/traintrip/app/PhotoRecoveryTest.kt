package cn.traintrip.app

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException

class PhotoRecoveryTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var folder:File
    private lateinit var context:ContextWrapper
    private val city=StationCatalog.bundled().cities.first { it.name=="保定" }
    @Before fun setup() {
        folder=File(compose.activity.cacheDir,"photo-recovery-${System.nanoTime()}").apply { mkdirs() }
        context=object:ContextWrapper(compose.activity) { override fun getFilesDir()=folder }
    }
    @After fun cleanup() { folder.deleteRecursively() }
    private fun photo(index:Int)=DestinationPhoto("test_$index.jpg","保定 · 直隶总督署","测试资料","https://you.ctrip.com/place/baoding459.html",remoteUrl="https://dimg04.c-ctrip.com/images/$index.jpg")
    private fun guide()=DestinationGuides.all.first().copy(cityId=city.id,name=city.name,generatedAt="2026-09-22T00:00:00Z",model="test").withPhotos(emptyList())
    private fun image():ByteArray {
        val bitmap=Bitmap.createBitmap(60,30,Bitmap.Config.ARGB_8888)
        return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
    }
    private fun addOld(store:AndroidGuideStore):DestinationGuide {
        val old=guide().withPhotos(listOf(photo(9)));store.save(old)
        File(folder,"destination-guides/${photo(9).assetName}").writeBytes(image());return old
    }
    @Test fun partialDownloadsKeepTextAndOldPhotoAndSurviveReopen()=runBlocking {
        val store=AndroidGuideStore(context) { url -> if(url.endsWith("2.jpg"))throw IOException();image() }
        val old=addOld(store);val checkpoints=mutableListOf<Int>()
        val result=store.refreshPhotos(old,listOf(photo(1),photo(2),photo(3))) { checkpoints+=store.read(city.id)!!.gallery.size }
        assertEquals(2,result.saved);assertEquals(1,result.failed);assertEquals(listOf(2,3),checkpoints)
        assertEquals(old.withPhotos(emptyList()),result.guide.withPhotos(emptyList()))
        assertEquals(photo(9),result.guide.gallery.last());assertEquals(result.guide,AndroidGuideStore(context).read(city.id))
        assertTrue(store.entries().single().hasPhoto)
    }
    @Test fun noCandidatesAndAllDownloadsFailLeaveOriginalBytesUntouched()=runBlocking {
        val store=AndroidGuideStore(context) { throw IOException() };val old=addOld(store)
        val json=File(folder,"destination-guides/${city.id}.json");val before=json.readBytes()
        for(candidates in listOf(emptyList(),listOf(photo(1),photo(2)))) {
            assertEquals(0,store.refreshPhotos(old,candidates).saved);assertArrayEquals(before,json.readBytes())
            assertTrue(File(folder,"destination-guides/${photo(9).assetName}").exists())
        }
    }
    @Test fun cancelDuringSecondDownloadKeepsFirstAndOldPhoto()=runBlocking {
        val gate=CompletableDeferred<Unit>()
        val store=AndroidGuideStore(context) { url -> if(url.endsWith("2.jpg")){gate.complete(Unit);awaitCancellation()};image() }
        val old=addOld(store)
        val job=launch(Dispatchers.IO) { try { store.refreshPhotos(old,listOf(photo(1),photo(2))) } finally { store.cleanupUnreferencedImages(city.id) } }
        gate.await();job.cancelAndJoin()
        val saved=store.read(city.id)!!;assertEquals(2,saved.gallery.size);assertEquals(old.withPhotos(emptyList()),saved.withPhotos(emptyList()))
        assertTrue(saved.gallery.all { File(folder,"destination-guides/${it.assetName}").exists() })
    }
    @Test fun missingCachedImageCanBeDownloadedAgainAndCandidateFailuresDoNotConsumeSlots()=runBlocking {
        val store=AndroidGuideStore(context) { url -> if(url.endsWith("1.jpg"))throw IOException();image() }
        val old=guide().withPhotos(listOf(photo(9)));store.save(old)
        val result=store.refreshPhotos(old,listOf(photo(1),photo(9))+ (2..7).map(::photo))
        assertEquals(5,result.saved);assertEquals(5,result.guide.gallery.size);assertEquals(1,result.failed)
        assertTrue(result.guide.gallery.any { it.remoteUrl==photo(9).remoteUrl })
    }
    private class Credentials:GuideCredentials {
        override fun read():String?=null
        override fun save(value:String) {}
        override fun remove() {}
    }
    private fun vm(store:AndroidGuideStore,photos:GuidePhotoSource)=DestinationViewModel(compose.activity.application,
        Credentials(),store,source=object:GuideMaterialSource { override suspend fun fetch(city:City,stage:(String)->Unit):GuideMaterial=error("text search must not run") },
        generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide=error("model must not run") },
        searchCredentials=Credentials(),photoSource=photos)
    @Test fun photoOnlyNeedsNoModelKeyAndKeepsTextAcrossNewViewModel() {
        val store=AndroidGuideStore(context) { image() };val old=guide();store.save(old);var calls=0
        val model=vm(store,GuidePhotoSource { _,_,_ -> calls++;PhotoCandidates(listOf(photo(1))) })
        compose.runOnIdle { model.open(city) };compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide!=null }
        compose.runOnIdle { model.refreshPhotos() };compose.waitUntil(5000) { !model.state.value.photoLoading && model.state.value.photoMessage!=null }
        assertEquals(1,calls);assertEquals(1,model.state.value.guide!!.gallery.size)
        assertEquals(old,model.state.value.guide!!.withPhotos(emptyList()));assertTrue(model.state.value.photoMessage!!.contains("1 张"))
        val restarted=vm(AndroidGuideStore(context),GuidePhotoSource { _,_,_ -> error("must use cache") })
        compose.runOnIdle { restarted.open(city) };compose.waitUntil(5000) { !restarted.state.value.loading && restarted.state.value.guide!=null }
        assertEquals(1,restarted.state.value.guide!!.gallery.size)
    }
    @Test fun leavingCityDiscardsLateUncooperativeSource() {
        val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>();val done=CompletableDeferred<Unit>()
        val store=AndroidGuideStore(context) { image() };store.save(guide())
        val model=vm(store,GuidePhotoSource { _,_,_ -> entered.complete(Unit);withContext(NonCancellable) { gate.await() };done.complete(Unit);PhotoCandidates(listOf(photo(1))) })
        compose.runOnIdle { model.open(city) };compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide!=null }
        compose.runOnIdle { model.refreshPhotos() };compose.waitUntil(5000) { entered.isCompleted }
        compose.runOnIdle { model.leave();gate.complete(Unit) };compose.waitUntil(5000) { done.isCompleted }
        assertTrue(store.read(city.id)!!.gallery.isEmpty());assertFalse(model.state.value.photoLoading)
    }
    @Test fun brokenPrimaryDownloadsUseFallbackAndSuccessfulPrimarySkipsIt() {
        for (broken in listOf(true, false)) {
            var fallbackCalls = 0
            val store = AndroidGuideStore(context) { url ->
                if (broken && !url.endsWith("8.jpg")) throw IOException()
                image()
            }
            store.save(guide())
            val photos = FallbackPhotoSource(GuidePhotoSource { _,_,_ -> PhotoCandidates((1..5).map(::photo)) },
                GuidePhotoSource { _,_,_ -> fallbackCalls++; PhotoCandidates(listOf(photo(8))) })
            val model = vm(store, photos)
            compose.runOnIdle { model.open(city) }
            compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide!=null }
            compose.runOnIdle { model.refreshPhotos() }
            compose.waitUntil(5000) { !model.state.value.photoLoading && model.state.value.photoMessage!=null }
            assertEquals(if (broken) 1 else 0, fallbackCalls)
            assertEquals(if (broken) 1 else 5, store.read(city.id)!!.gallery.size)
            assertEquals(guide(), store.read(city.id)!!.withPhotos(emptyList()))
            compose.runOnIdle { model.leave() }
        }
    }

    @Test fun replacingAlbumKeepsImagesReferencedByAnotherCity() = runBlocking {
        val store = AndroidGuideStore(context) { image() }
        val old = addOld(store)
        val other = StationCatalog.bundled().cities.first { it.name=="邯郸" }
        store.save(old.copy(cityId=other.id, name=other.name))
        val updated = store.refreshPhotos(old, (1..5).map(::photo))
        assertEquals(5, updated.saved)
        assertTrue(File(folder,"destination-guides/${photo(9).assetName}").isFile)
        assertEquals(photo(9), store.read(other.id)!!.gallery.single())
    }

    @Test fun successfulAlbumShowsPhotoAndUpdateAction() {
        val withPhoto = DestinationGuides.all.first().copy(generatedAt="2026-09-22T00:00:00Z",model="ui-fixture")
        var clicks = 0
        compose.setContent { TrainTripTheme {
            DestinationGuideScreen(withPhoto.name,withPhoto,{}, {}, {},runtime=DestinationState(photoMessage="已保存 1 张新图片，共 1 张。"),onPhotos={clicks++})
        } }
        compose.onNodeWithTag("refresh-photos").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("只更新图片").assertIsDisplayed()
        compose.onNodeWithTag("photo-message").assertIsDisplayed()
        val bmp=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null),"photo-recovery-success.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun compactPhotoActionsRemainUsableWithLargeFont() {
        var current by mutableStateOf(DestinationState());var clicks=0
        val preview = DestinationGuides.all.first().copy(generatedAt="2026-09-22T00:00:00Z",model="ui-fixture").withPhotos(emptyList())
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
            TrainTripTheme { Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                DestinationGuideScreen(preview.name,preview,{}, {}, {},runtime=current,onPhotos={clicks++},onCancelPhotos={current=current.copy(photoLoading=false)})
            } }
        } }
        compose.onNodeWithTag("refresh-photos").performScrollTo().assertIsDisplayed().performClick();assertEquals(1,clicks)
        compose.runOnIdle { current=current.copy(photoLoading=true,photoStage="正在读取古文化街的图片…") }
        compose.onNodeWithTag("cancel-photos").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { current=current.copy(photoMessage="图片下载失败，已保留原图片，可重试。") }
        compose.onNodeWithTag("photo-message").performScrollTo().assertIsDisplayed()
        val bmp=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null),"photo-recovery-large.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
