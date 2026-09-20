package cn.traintrip.app

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import cn.traintrip.app.ui.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class OfflineManagementTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var folder:File
    private lateinit var context:ContextWrapper
    private val city=StationCatalog.bundled().cities.first { it.name=="苏州" }
    private fun guide()=DestinationGuides.all.first().copy(cityId=city.id,name=city.name,photo=null,foods=emptyList(),plans=emptyList(),generatedAt=Instant.now().toString(),model=DeepSeekGuideGenerator.MODEL)
    private fun photoGuide()=guide().copy(photo=DestinationGuides.all.first { it.photo!=null }.photo!!.copy(assetName="remote_test.jpg",remoteUrl="https://dimg04.c-ctrip.com/images/test.jpg"))
    private class Credentials(var key:String?="test-only"):GuideCredentials {
        override fun read()=key
        override fun save(value:String) { key=value }
        override fun remove() { key=null }
    }
    private val source=object:GuideMaterialSource { override suspend fun fetch(city:City,stage:(String)->Unit)=GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),emptyList()) }
    @Before fun setUp() {
        folder=File(compose.activity.cacheDir,"offline-test-${System.nanoTime()}").apply { mkdirs() }
        context=object:ContextWrapper(compose.activity) { override fun getFilesDir()=folder }
    }
    @After fun tearDown() { folder.deleteRecursively() }
    @Test fun partialDeleteRetainsLegacyImageOwnershipAndDoesNotTouchOtherData() {
        var fail=true
        val store=AndroidGuideStore(context,removeFile={if(it.name=="remote_test.jpg" && fail)false else it.delete()})
        val old=photoGuide();store.save(old)
        val dir=File(folder,"destination-guides")
        val photo=File(dir,"remote_test.jpg").apply { writeBytes(ByteArray(25)) }
        val other=guide().copy(cityId="130600",name="保定");store.save(other)
        File(folder,"private-key").writeText("test-only")
        AndroidWishlistStore(context).write(listOf(WishCity(city.id,city.name,city.province.name,10)))
        assertFalse(store.delete(city.id));assertNull(store.read(city.id));assertTrue(photo.exists())
        assertNull(store.entries().first { it.cityId==city.id }.guide)
        assertTrue(store.entries().first { it.cityId==city.id }.bytes>=25)
        fail=false;assertTrue(store.delete(city.id));assertFalse(photo.exists())
        assertFalse(store.entries().any { it.cityId==city.id });assertEquals(other,store.read(other.cityId))
        assertEquals(1,AndroidWishlistStore(context).read().size);assertEquals("test-only",File(folder,"private-key").readText())
    }
    @Test fun backupRecoveryAndCorruptResidualCanBeCleared() {
        val store=AndroidGuideStore(context);store.save(guide())
        val file=File(folder,"destination-guides/${city.id}.json")
        assertTrue(file.renameTo(File(file.path+".bak")))
        assertEquals(guide().cityId,store.all()[city.id]?.cityId)
        File(folder,"destination-guides/130600.json").writeText("broken")
        File(folder,"destination-guides/130600_orphan.jpg").writeBytes(ByteArray(17))
        assertNull(store.entries().first { it.cityId=="130600" }.guide)
        assertTrue(store.delete("130600"));assertNotNull(store.read(city.id))
    }
    @Test fun failedPhotoKeepsNewTextAndRemovesOldPhoto()=runBlocking {
        val store=AndroidGuideStore(context) { throw IOException("test photo failure") }
        val old=photoGuide();store.save(old)
        val image=File(folder,"destination-guides/remote_test.jpg").apply { writeBytes(ByteArray(13)) }
        var committed=false
        val updated=old.copy(tagline="新的苏州介绍")
        val prepared=store.prepareUpdate(updated) { committed=true }
        assertTrue(committed);assertNull(prepared.photo);assertFalse(image.exists())
        assertEquals("新的苏州介绍",store.read(city.id)?.tagline);assertNull(store.read(city.id)?.photo)
    }
    @Test fun cancellationAfterTextCommitRetainsNewTextAndCleansTemporaryPhoto()=runBlocking {
        val entered=CompletableDeferred<Unit>()
        val store=AndroidGuideStore(context) { entered.complete(Unit);awaitCancellation() }
        store.save(guide())
        val task=launch(Dispatchers.IO) { try { store.prepareUpdate(photoGuide().copy(tagline="新正文已保存")) } finally { store.cleanupUnreferencedImages(city.id) } }
        entered.await();task.cancelAndJoin()
        assertEquals("新正文已保存",store.read(city.id)?.tagline);assertNull(store.read(city.id)?.photo)
        assertFalse(File(folder,"destination-guides").listFiles()!!.any { it.extension=="jpg" })
    }
    @Test fun unattachedPhotoCleanupPreservesCommittedPhotoAndSharedLegacyPhoto() {
        val store=AndroidGuideStore(context);val old=photoGuide();store.save(old)
        store.save(old.copy(cityId="130600",name="保定"))
        val dir=File(folder,"destination-guides")
        val shared=File(dir,"remote_test.jpg").apply { writeBytes(ByteArray(17)) }
        File(dir,"${city.id}_unfinished.jpg").writeBytes(ByteArray(19))
        store.cleanupUnreferencedImages(city.id)
        assertTrue(shared.exists());assertFalse(File(dir,"${city.id}_unfinished.jpg").exists())
        assertTrue(store.delete(city.id));assertTrue(shared.exists());assertNotNull(store.read("130600"))
    }
    @Test fun deleteWaitsForCancelledGeneratorAndReopenNeverGenerates() {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        var calls=0
        val store=AndroidGuideStore(context);store.save(guide())
        val creds=Credentials()
        val generator=object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide {
            calls++;entered.complete(Unit);withContext(NonCancellable){release.await()};return guide()
        } }
        val vm=DestinationViewModel(compose.activity.application,creds,store,source,generator,Credentials())
        compose.runOnIdle { vm.open(city) };compose.waitUntil(5000){!vm.state.value.loading}
        compose.runOnIdle { vm.retry() };compose.waitUntil(5000){entered.isCompleted}
        compose.runOnIdle { vm.deleteOffline(city.id);release.complete(Unit) }
        compose.waitUntil(5000){vm.state.value.deleting==null && vm.state.value.offlineMessage!=null}
        assertNull(store.read(city.id));assertNull(vm.state.value.guide);assertEquals("test-only",creds.read())
        compose.runOnIdle { vm.leave();vm.open(city) };compose.waitUntil(5000){!vm.state.value.loading}
        assertEquals(1,calls);assertNull(vm.state.value.error)
    }
    @Test fun deletingDownloadedBundledCityRestoresBundledVersion() {
        val bundled=DestinationGuides.all.first()
        val store=AndroidGuideStore(context);store.save(bundled.copy(generatedAt=Instant.now().toString(),model=DeepSeekGuideGenerator.MODEL,photo=null))
        val vm=DestinationViewModel(compose.activity.application,Credentials(null),store,source,object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide=error("must not call") },Credentials(null))
        compose.waitUntil(5000){!vm.state.value.offlineLoading}
        compose.runOnIdle { vm.deleteOffline(bundled.cityId) }
        compose.waitUntil(5000){vm.state.value.deleting==null && vm.state.value.offlineMessage!=null}
        assertEquals(bundled,vm.state.value.guides[bundled.cityId]);assertTrue(store.entries().isEmpty())
    }
    @Test fun wishlistAtomicReloadRejectsCorruptionInsteadOfLosingFavorites() {
        val store=AndroidWishlistStore(context);val item=WishCity(city.id,city.name,city.province.name,12)
        store.write(listOf(item));assertEquals(listOf(item),AndroidWishlistStore(context).read())
        val base=File(folder,"wishlist.json");assertTrue(base.renameTo(File(folder,"wishlist.json.bak")))
        assertEquals(listOf(item),store.read())
        base.writeText("corrupt");assertTrue(runCatching { store.read() }.isFailure)
    }
    @Test fun offlineUiCancelKeepsContentAndConfirmRemovesOnlyDownloadedContent() {
        val store=AndroidGuideStore(context);store.save(guide())
        val vm=DestinationViewModel(compose.activity.application,Credentials(null),store,source,
            object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String):DestinationGuide=error("must not call") },Credentials(null))
        compose.setContent { val state by vm.state.collectAsState();TrainTripTheme { OfflineContentScreen(state,StationCatalog.bundled(),{}, {},vm::deleteOffline,vm::refreshOffline) } }
        compose.waitUntil(5000){vm.state.value.offline.size==1}
        fun capture(name:String) {
            File(compose.activity.getExternalFilesDir(null),"v05-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
        capture("offline-downloaded")
        compose.onNodeWithTag("delete-${city.id}").performClick()
        compose.onNodeWithText("取消").performClick();assertNotNull(store.read(city.id))
        compose.onNodeWithTag("delete-${city.id}").performClick()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { image ->
            File(compose.activity.getExternalFilesDir(null),"v05-delete-dialog.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        compose.onNodeWithTag("confirm-delete-offline").performClick()
        compose.waitUntil(5000){vm.state.value.offlineMessage!=null}
        assertNull(store.read(city.id));compose.onNodeWithText("暂无下载内容，内置介绍仍可查看。").assertIsDisplayed()
    }

    @Test fun photoDownloadShowsCommittedTextAndCancellationKeepsPartialSuccess() {
        val downloading=CompletableDeferred<Unit>()
        val store=AndroidGuideStore(context) { downloading.complete(Unit);awaitCancellation() }
        store.save(guide())
        val vm=DestinationViewModel(compose.activity.application,Credentials(),store,source,
            object:GuideGenerator { override suspend fun generate(material:GuideMaterial,apiKey:String)=photoGuide().copy(tagline="正文已更新") },Credentials())
        compose.runOnIdle { vm.open(city) };compose.waitUntil(5000){!vm.state.value.loading}
        compose.runOnIdle { vm.retry() };compose.waitUntil(5000){downloading.isCompleted}
        assertEquals("正文已更新",vm.state.value.guide?.tagline);assertNull(vm.state.value.guide?.photo)
        compose.runOnIdle { vm.cancel() }
        compose.waitUntil(5000){vm.state.value.contentMessage!=null}
        assertNull(vm.state.value.error);assertEquals("正文已更新",vm.state.value.guide?.tagline)
    }

}
