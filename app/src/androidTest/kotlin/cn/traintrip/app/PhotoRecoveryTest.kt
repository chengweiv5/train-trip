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
    private fun photo(index:Int)=DestinationPhoto("test_$index.jpg","保定 · 直隶总督署","测试资料","https://you.ctrip.com/place/baoding459.html",remoteUrl="https://dimg04.c-ctrip.com/images/$index.jpg",subject=PhotoSubject("place",if(index==9)"古莲花池" else "直隶总督署"))
    private fun guide()=DestinationGuides.all.first().copy(cityId=city.id,name=city.name,experiences=listOf(DestinationExperience("p1","直隶总督署","保定景点介绍","1小时","保定"),DestinationExperience("p2","古莲花池","保定景点介绍","1小时","保定")),foods=emptyList(),plans=emptyList(),generatedAt="2026-09-22T00:00:00Z",model="test").withPhotos(emptyList())
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
        assertEquals(photo(9),result.guide.gallery.first());assertEquals(result.guide,AndroidGuideStore(context).read(city.id))
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
        assertEquals(4,result.saved);assertEquals(4,result.guide.gallery.size);assertEquals(1,result.failed)
        assertTrue(result.guide.gallery.any { it.remoteUrl==photo(9).remoteUrl })
    }
    @Test fun illustratedItemsNeverDownloadAgainAndCorruptImageDoesNotBlockReplacement()=runBlocking {
        var downloads=0
        val store=AndroidGuideStore(context) { downloads++;image() }
        val old=addOld(store)
        val unchanged=store.refreshPhotos(old,listOf(photo(9),photo(10).copy(subject=photo(9).subject)))
        assertEquals(0,unchanged.saved);assertEquals(0,downloads)
        File(folder,"destination-guides/${photo(9).assetName}").writeText("not an image")
        val replaced=store.refreshPhotos(old,listOf(photo(9)))
        assertEquals(1,replaced.saved);assertEquals(1,downloads)
        assertEquals(1,store.usablePhotos(replaced.guide).size)
    }
    @Test fun twoSubjectsCanSaveSixImagesAndASecondBatchDoesNotTopThemUp()=runBlocking {
        var downloads=0
        val store=AndroidGuideStore(context) { downloads++;image() }
        val input=guide();store.save(input)
        val subjects=listOf(PhotoSubject("place","直隶总督署"),PhotoSubject("place","古莲花池"))
        val candidates=subjects.flatMapIndexed { index,subject -> (1..4).map { n -> photo(index*10+n).copy(subject=subject) } }
        val result=store.refreshPhotos(input,candidates)
        assertEquals(6,result.saved);assertEquals(6,downloads)
        assertEquals(listOf(3,3),result.guide.gallery.groupingBy { it.subject }.eachCount().values.toList())
        assertEquals(6,AndroidGuideStore(context).read(city.id)!!.gallery.size)
        assertEquals(0,store.refreshPhotos(result.guide,candidates).saved);assertEquals(6,downloads)
    }
    @Test fun oldCityGalleryWithFiveOfOnePlaceIsReadAsThreeWithoutLosingText() {
        val store=AndroidGuideStore(context)
        val legacy=guide().withPhotos((1..5).map { photo(it).copy(subject=null) })
        File(folder,"destination-guides/${city.id}.json").writeText(com.google.gson.Gson().toJson(mapOf("schemaVersion" to 1,"guide" to legacy)))
        val read=store.read(city.id)!!
        assertEquals(3,read.gallery.size);assertEquals(legacy.withPhotos(emptyList()),read.withPhotos(emptyList()))
    }
    private class Credentials:GuideCredentials {
        override fun read()="test-only"
        override fun save(value:String) {}
        override fun remove() {}
    }
    private fun updated()=guide().copy(tagline="新的目的地正文",generatedAt="2026-09-22T01:00:00Z")
    private fun vm(store:AndroidGuideStore,photos:GuidePhotoSource)=DestinationViewModel(compose.activity.application,
        Credentials(),store,source=object:GuideMaterialSource { override suspend fun fetch(city:City,stage:(String)->Unit)=
            GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),emptyList()) },
        generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String)=updated() },
        searchCredentials=Credentials(),photoSource=photos)
    private fun open(model:DestinationViewModel) {
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide!=null }
    }
    @Test fun fullUpdateWithoutPicturesSavesTextThenAddsPhotosAndReopenDoesNotFetch() {
        val store=AndroidGuideStore(context) { image() };store.save(guide());var calls=0
        val model=vm(store,GuidePhotoSource { _,draft,_ ->
            calls++;assertEquals(updated(),draft);assertEquals(updated(),store.read(city.id))
            PhotoCandidates(listOf(photo(1)))
        })
        open(model)
        compose.runOnIdle { model.retry() };compose.waitUntil(5000) { !model.state.value.loading && model.state.value.contentMessage!=null }
        assertEquals(1,calls);assertEquals(1,model.state.value.guide!!.gallery.size)
        assertEquals(updated(),model.state.value.guide!!.withPhotos(emptyList()))
        val restarted=vm(AndroidGuideStore(context),GuidePhotoSource { _,_,_ -> error("must use cache") })
        open(restarted);assertEquals(1,restarted.state.value.guide!!.gallery.size)
    }
    @Test fun fullUpdateKeepsExistingSubjectAndFetchesMissingSubject() {
        var downloads=0
        var searches=0
        val store=AndroidGuideStore(context) { downloads++;image() };val old=addOld(store)
        val file=File(folder,"destination-guides/${photo(9).assetName}");val bytes=file.readBytes()
        val model=vm(store,GuidePhotoSource { _,draft,_ -> searches++;assertEquals(listOf(PhotoSubject("place","直隶总督署")),GuidePhotoPolicy.missing(draft));PhotoCandidates(listOf(photo(1))) })
        open(model);compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide?.tagline==updated().tagline }
        assertEquals(updated(),store.read(city.id)!!.withPhotos(emptyList()));assertEquals(1,downloads);assertEquals(1,searches)
        assertEquals(photo(9),store.read(city.id)!!.gallery.first());assertEquals(2,store.read(city.id)!!.gallery.size)
        assertArrayEquals(bytes,file.readBytes());assertNull(model.state.value.error)
    }
    @Test fun fullUpdateWhenEverySubjectHasOnePhotoSkipsSearchAndDownload() {
        var searches=0;var downloads=0
        val store=AndroidGuideStore(context) { downloads++;image() }
        val old=guide().withPhotos(listOf(photo(1),photo(9)));store.save(old)
        old.gallery.forEach { File(folder,"destination-guides/${it.assetName}").writeBytes(image()) }
        val model=vm(store,GuidePhotoSource { _,_,_ -> searches++;error("must not fetch") })
        open(model);compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide?.tagline==updated().tagline }
        assertEquals(0,searches);assertEquals(0,downloads);assertEquals(updated().withPhotos(old.gallery),store.read(city.id))
    }
    @Test fun fullUpdateWithOnlyMissingImageFetchesReplacement() {
        val store=AndroidGuideStore(context) { image() };store.save(guide().withPhotos(listOf(photo(9))));var calls=0
        val model=vm(store,GuidePhotoSource { _,_,_ -> calls++;PhotoCandidates(listOf(photo(1))) })
        open(model);compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.contentMessage!=null }
        assertEquals(1,calls);assertEquals(photo(1).remoteUrl,store.read(city.id)!!.gallery.single().remoteUrl)
    }
    @Test fun photoFailureStillKeepsUpdatedText() {
        val store=AndroidGuideStore(context) { throw IOException() };store.save(guide())
        val model=vm(store,GuidePhotoSource { _,_,_ -> PhotoCandidates(listOf(photo(1))) })
        open(model);compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.contentMessage!=null }
        assertEquals(updated(),store.read(city.id));assertNull(model.state.value.error)
    }
    @Test fun leavingCityDiscardsLateUncooperativeSource() {
        val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>();val done=CompletableDeferred<Unit>()
        val store=AndroidGuideStore(context) { image() };store.save(guide())
        val model=vm(store,GuidePhotoSource { _,_,_ -> entered.complete(Unit);withContext(NonCancellable) { gate.await() };done.complete(Unit);PhotoCandidates(listOf(photo(1))) })
        compose.runOnIdle { model.open(city) };compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide!=null }
        compose.runOnIdle { model.retry() };compose.waitUntil(5000) { entered.isCompleted }
        compose.runOnIdle { model.leave();gate.complete(Unit) };compose.waitUntil(5000) { done.isCompleted }
        assertTrue(store.read(city.id)!!.gallery.isEmpty());assertFalse(model.state.value.loading)
    }
    @Test fun brokenPrimaryDownloadsUseFallbackAndSuccessfulPrimarySkipsIt() {
        for (broken in listOf(true, false)) {
            var fallbackCalls = 0
            val store = AndroidGuideStore(context) { url ->
                if (broken && !url.endsWith("8.jpg")) throw IOException()
                image()
            }
            store.save(guide())
            val photos = FallbackPhotoSource(GuidePhotoSource { _,_,_ -> PhotoCandidates((1..5).map(::photo)+photo(9)) },
                GuidePhotoSource { _,_,_ -> fallbackCalls++; PhotoCandidates(listOf(photo(8))) })
            val model = vm(store, photos)
            compose.runOnIdle { model.open(city) }
            compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide!=null }
            compose.runOnIdle { model.retry() }
            compose.waitUntil(5000) { !model.state.value.loading && model.state.value.contentMessage!=null }
            assertEquals(if (broken) 1 else 0, fallbackCalls)
            assertEquals(if (broken) 1 else 4, store.read(city.id)!!.gallery.size)
            assertEquals(updated(), store.read(city.id)!!.withPhotos(emptyList()))
            compose.runOnIdle { model.leave() }
        }
    }

    @Test fun replacingAlbumKeepsImagesReferencedByAnotherCity() = runBlocking {
        val store = AndroidGuideStore(context) { image() }
        val old = addOld(store)
        val other = StationCatalog.bundled().cities.first { it.name=="邯郸" }
        store.save(old.copy(cityId=other.id, name=other.name))
        val updated = store.refreshPhotos(old, (1..5).map(::photo))
        assertEquals(3, updated.saved)
        assertTrue(File(folder,"destination-guides/${photo(9).assetName}").isFile)
        assertEquals(photo(9), store.read(other.id)!!.gallery.single())
    }

    @Test fun existingAlbumHasOnlyFullUpdateAction() {
        val preview=DestinationGuides.all.first().copy(generatedAt="2026-09-22T00:00:00Z",model="ui-fixture")
        var clicks=0
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DestinationGuideScreen(preview.name,preview,{}, {}, {},
                runtime=DestinationState(configured=true,searchConfigured=true),onRefresh={clicks++})
        } } }
        compose.onNodeWithTag("guide-page-places").performScrollToNode(hasTestTag("refresh-guide"))
        compose.onNodeWithTag("refresh-guide").assertIsDisplayed().performClick()
        assertEquals(1,clicks)
        compose.onNodeWithText("更新目的地介绍").assertIsDisplayed()
        compose.onNodeWithTag("refresh-photos").assertDoesNotExist()
        compose.onNodeWithText("只更新图片").assertDoesNotExist()
        capture("full-update-existing")
    }
    @Test fun fullUpdateActionRemainsUsableWithLargeFontAndNoPhotoControls() {
        val preview=DestinationGuides.all.first().copy(generatedAt="2026-09-22T00:00:00Z",model="ui-fixture").withPhotos(emptyList())
        var clicks=0
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
            TrainTripTheme { Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                DestinationGuideScreen(preview.name,preview,{}, {}, {},
                    runtime=DestinationState(configured=true,searchConfigured=true),onRefresh={clicks++})
            } }
        } }
        compose.onNodeWithTag("guide-page-places").performScrollToNode(hasTestTag("refresh-guide"))
        compose.onNodeWithTag("refresh-guide").assertIsDisplayed().performClick()
        assertEquals(1,clicks)
        compose.onNodeWithTag("refresh-photos").assertDoesNotExist()
        compose.onNodeWithTag("cancel-photos").assertDoesNotExist()
        compose.onNodeWithText("补充图片").assertDoesNotExist()
        capture("full-update-large")
    }
    private fun capture(name:String) {
        val bmp=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null),"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
