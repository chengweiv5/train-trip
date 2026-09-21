package cn.traintrip.app

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.time.Instant

class V061Test {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var folder:File
    private lateinit var context:ContextWrapper
    private val time=Instant.parse("2026-09-21T04:00:00Z")
    private val release=AppRelease(ReleaseVersion(0,6,1),releaseUrl("0.6.1"),"测试说明",true)
    @Before fun setup() {
        folder=File(compose.activity.cacheDir,"v061-${System.nanoTime()}").apply { mkdirs() }
        context=object:ContextWrapper(compose.activity) { override fun getFilesDir()=folder }
    }
    @After fun cleanup() { folder.deleteRecursively() }
    private fun photo(index:Int)=DestinationPhoto("test_$index.jpg","天津 · 测试景点 $index","测试来源","https://you.ctrip.com/place/tianjin154.html",remoteUrl="https://dimg04.c-ctrip.com/images/$index.jpg")
    private fun guide()=DestinationGuides.all.first().copy(generatedAt=time.toString(),model="test-model").withPhotos((1..3).map(::photo))
    private fun image():ByteArray {
        val bitmap=Bitmap.createBitmap(60,30,Bitmap.Config.ARGB_8888)
        return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
    }
    @Test fun galleryPartialFailureAndReopenRetainEverySuccessfulPhoto()=runBlocking {
        val store=AndroidGuideStore(context) { url -> if(url.endsWith("/2.jpg"))throw IOException();image() }
        val input=guide();val checkpoints=mutableListOf<Int>()
        val result=store.prepareUpdate(input) { checkpoints+=store.read(input.cityId)!!.gallery.size }
        assertEquals(listOf(0,1,2),checkpoints);assertEquals(2,result.gallery.size)
        assertEquals(result,AndroidGuideStore(context).read(input.cityId))
        assertTrue(store.entries().single().hasPhoto)
        assertEquals(2,File(folder,"destination-guides").listFiles()!!.count { it.extension=="jpg" })
        assertTrue(store.delete(input.cityId));assertTrue(store.entries().isEmpty())
    }
    @Test fun cancelSecondImageRetainsCommittedFirstAndReplacesOldAlbum()=runBlocking {
        val second=CompletableDeferred<Unit>()
        val store=AndroidGuideStore(context) { url -> if(url.endsWith("/2.jpg")){second.complete(Unit);awaitCancellation()};image() }
        val old=guide().withPhotos(listOf(photo(9)));store.save(old)
        val oldFile=File(folder,"destination-guides/${photo(9).assetName}").apply { writeBytes(image()) }
        val task=launch(Dispatchers.IO) { try { store.prepareUpdate(guide().copy(tagline="新的介绍")) } finally { store.cleanupUnreferencedImages(old.cityId) } }
        second.await();task.cancelAndJoin()
        val saved=AndroidGuideStore(context).read(old.cityId)!!
        assertEquals("新的介绍",saved.tagline);assertEquals(1,saved.gallery.size);assertFalse(oldFile.exists())
        assertTrue(File(folder,"destination-guides/${saved.gallery.single().assetName}").exists())
    }
    @Test fun oldSinglePhotoJsonRemainsReadableAndSharedSecondPhotoIsProtected() {
        val store=AndroidGuideStore(context);val old=guide().copy(photos=null,photo=photo(1));store.save(old)
        val legacy=File(folder,"destination-guides/${old.cityId}.json")
        assertFalse(legacy.readText().contains("\"photos\""));assertEquals(listOf(photo(1)),store.read(old.cityId)!!.gallery)
        val shared=photo(2).copy(assetName="${old.cityId}_shared.jpg");store.save(guide().withPhotos(listOf(photo(1),shared)))
        store.save(guide().copy(cityId="130600",name="保定").withPhotos(listOf(shared)))
        val f=File(folder,"destination-guides/${shared.assetName}").apply { writeBytes(image()) }
        assertTrue(store.delete(old.cityId));assertTrue(f.exists());assertNotNull(store.read("130600"))
        assertTrue(store.delete("130600"));assertFalse(f.exists())
    }
    @Test fun successfulHistorySurvivesLeaveAndNewViewModelAndFailure() {
        val store=AndroidUpdateHistoryStore(context)
        val vm=UpdateViewModel(UpdateSource { release },store) { time }
        compose.runOnIdle { vm.check() }
        compose.waitUntil(5000) { store.read().checkedAt==time }
        compose.runOnIdle { vm.leave() }
        assertEquals(time,vm.state.value.checkedAt)
        val retry=UpdateViewModel(UpdateSource { throw IOException() },AndroidUpdateHistoryStore(context)) { time.plusSeconds(60) }
        assertEquals(release,retry.state.value.release);assertTrue(retry.state.value.historical)
        compose.runOnIdle { retry.check() }
        compose.waitUntil(5000) { store.read().error!=null }
        val restored=AndroidUpdateHistoryStore(context).read()
        assertEquals(time,restored.checkedAt);assertEquals(time.plusSeconds(60),restored.completedAt)
        assertEquals(release,restored.release);assertNotNull(restored.error)
    }
    @Test fun cancellationKeepsPriorCompletedHistoryAndRejectsLateResult() {
        val store=AndroidUpdateHistoryStore(context)
        store.write(UpdateState(release=release,checkedAt=time,completedAt=time))
        val entered=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>();val returned=CompletableDeferred<Unit>()
        val vm=UpdateViewModel(UpdateSource { entered.complete(Unit);withContext(NonCancellable){gate.await()};returned.complete(Unit);release.copy(version=ReleaseVersion(99,0,0)) },store)
        compose.runOnIdle { vm.check() };compose.waitUntil(5000){entered.isCompleted}
        compose.runOnIdle { vm.leave();gate.complete(Unit) };compose.waitUntil(5000){returned.isCompleted}
        compose.runOnIdle { assertEquals(time,vm.state.value.completedAt);assertEquals(release,vm.state.value.release) }
        assertEquals(time,AndroidUpdateHistoryStore(context).read().completedAt)
    }
    @Test fun corruptHistoryDoesNotBlockManualCheck() {
        File(folder,"update-history.json").writeText("corrupt")
        assertEquals(UpdateState(),AndroidUpdateHistoryStore(context).read())
    }
    @Test fun narrowChildHeaderAndHistoricalAboutKeepActionsReachable() {
        var title by mutableStateOf("大模型和搜索引擎设置")
        var about by mutableStateOf(false);var clicks=0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
                TrainTripTheme { Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                    if(about)AboutScreen(UpdateState(release=release,checkedAt=time,completedAt=time,historical=true),{}, {clicks++})
                    else AppTopBar(title,{},"修改",{clicks++})
                } }
            }
        }
        val back=compose.onNodeWithTag("navigate-back").fetchSemanticsNode().boundsInRoot
        val text=compose.onNodeWithTag("page-title",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.left>=back.right);compose.onNodeWithText("修改").assertIsDisplayed().performClick()
        capture("header-large")
        compose.runOnIdle { assertEquals(1,clicks);about=true }
        compose.onNodeWithText("上次检查未发现更新").assertIsDisplayed()
        compose.onNodeWithTag("check-update").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2,clicks) };capture("about-large")
    }
    @Test fun gallerySwipeEnlargeAndSourcesWorkOffline() {
        val base=DestinationGuides.all.first()
        val photos=listOf(base.photo!!,DestinationGuides.all.first { it.photo!!.assetName!=base.photo!!.assetName }.photo!!)
        var source=""
        compose.setContent { TrainTripTheme { Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp)) { DestinationGallery(photos,{source=it}) } } }
        compose.onNodeWithTag("gallery-count").assertTextEquals("1/2")
        compose.onNodeWithTag("gallery-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("gallery-count").assertTextEquals("2/2")
        compose.onNodeWithText("图片来源").performClick();assertEquals(photos[1].sourceUrl,source)
        compose.onNodeWithTag("gallery-pager").performClick()
        compose.onNodeWithText("图片 2/2").assertIsDisplayed();capture("gallery-full")
        compose.onNodeWithTag("close-gallery").performClick();compose.onNodeWithTag("gallery-count").assertIsDisplayed()
    }
    @Test fun galleryInsideGuideSwipesWithoutChangingContentTab() {
        val base=DestinationGuides.all.first()
        val other=DestinationGuides.all.first { it.photo!!.assetName!=base.photo!!.assetName }.photo!!
        val data=base.withPhotos(listOf(base.photo!!,other))
        compose.setContent { TrainTripTheme { DestinationGuideScreen(data.name,data,{}, {}, {}) } }
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
        compose.onNodeWithTag("gallery-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("gallery-count").assertTextEquals("2/2")
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
        capture("guide-gallery")
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.onNodeWithTag("guide-tab-food").assertIsSelected()
    }
    private fun capture(name:String) {
        val bmp=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null),"v061-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
