package cn.traintrip.app

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.traintrip.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class GuideRefreshRetentionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var folder: File
    private lateinit var context: ContextWrapper
    private val city = StationCatalog.bundled().cities.first { it.name == "邯郸" }
    @Before fun setup() {
        folder = File(compose.activity.cacheDir, "guide-retention-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(compose.activity) { override fun getFilesDir() = folder }
    }
    @After fun cleanup() { folder.deleteRecursively() }
    private fun guide(places: List<DestinationExperience>) = DestinationGuides.all.first().copy(cityId = city.id, name = city.name,
        experiences = places, foods = emptyList(), plans = emptyList(), generatedAt = "2026-09-22T00:00:00Z", model = "test").withPhotos(emptyList())
    private class Credentials : GuideCredentials {
        override fun read() = "test-only"
        override fun save(value: String) {}
        override fun remove() {}
    }
    @Test fun fullRefreshKeepsCachedGuangfuWhenNewSearchOmitsIt() {
        val oldPlace = DestinationExperience("p1", "广府古城", "缓存中的广府古城介绍", "1小时", "邯郸")
        val museum = DestinationExperience("p2", "邯郸市博物馆", "旧博物馆介绍", "1小时", "邯郸")
        val photo = DestinationPhoto("cached_guangfu.jpg", "邯郸 · 广府古城", "测试图片", "https://you.ctrip.com/sight/handan495/1.html",
            remoteUrl = "https://dimg04.c-ctrip.com/images/guangfu.jpg", subject = PhotoSubject("place", oldPlace.name))
        val old = DestinationGuides.all.first().copy(cityId = city.id, name = city.name, experiences = listOf(oldPlace, museum),
            foods = emptyList(), plans = emptyList(), generatedAt = "2026-09-22T00:00:00Z", model = "test").withPhotos(listOf(photo))
        val fresh = old.copy(tagline = "新的邯郸介绍", experiences = listOf(museum.copy(id = "p1", reason = "本次更新的博物馆介绍")),
            generatedAt = "2026-09-22T01:00:00Z").withPhotos(emptyList())
        val store = AndroidGuideStore(context)
        store.save(old)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val image = File(folder, "destination-guides/${photo.assetName}")
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val before = image.readBytes()
        val model = DestinationViewModel(compose.activity.application, Credentials(), store,
            source = object : GuideMaterialSource { override suspend fun fetch(city: City, stage: (String) -> Unit) =
                GuideMaterial(city.id, city.name, city.province.name, emptyList(), emptyList(), emptyList()) },
            generator = object : GuideGenerator { override suspend fun generate(material: GuideMaterial, apiKey: String) = fresh },
            searchCredentials = Credentials(), photoSource = GuidePhotoSource { _, _, _ -> PhotoCandidates(emptyList()) })
        compose.runOnIdle { model.open(city) }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.guide != null }
        compose.runOnIdle { model.retry() }
        compose.waitUntil(5000) { !model.state.value.loading }
        assertNull(model.state.value.error)
        val saved = AndroidGuideStore(context).read(city.id)!!
        assertTrue("刷新丢失了已缓存的广府古城：${saved.experiences.map { it.name }}", saved.experiences.any { it.name == oldPlace.name })
        assertEquals("本次更新的博物馆介绍", saved.experiences.single { it.name == museum.name }.reason)
        assertEquals(photo, saved.gallery.single())
        assertArrayEquals(before, image.readBytes())
        compose.runOnIdle { model.leave() }
    }
    @Test fun cumulativeItemsStayWithinTenAfterPersistAndReopen() = runBlocking {
        val old = guide((1..10).map { DestinationExperience("p$it", "缓存景点$it", "已有介绍", "1小时", "邯郸") })
            .copy(foods = (1..10).map { DestinationFood("缓存美食$it", "已有美食介绍") })
        val fresh = guide(listOf(DestinationExperience("p1", "新增景点", "新介绍", "1小时", "邯郸")))
            .copy(foods = listOf(DestinationFood("新增美食", "新美食介绍")))
        val store = AndroidGuideStore(context); store.save(old)
        val merged = GuideRefreshPolicy.merge(old, fresh)
        store.saveTextKeepingPhotos(merged, old)
        val reopened = AndroidGuideStore(context).read(city.id)!!
        assertEquals(10, reopened.experiences.size); assertEquals(10, reopened.foods.size)
        assertEquals(old.experiences, reopened.experiences); assertEquals(old.foods, reopened.foods)
        assertEquals(merged, reopened)
    }
    @Test fun legacyUnlimitedCacheReadsAsTenWithoutOverwritingOriginalFile() {
        val legacy = guide((1..12).map { DestinationExperience("p$it", "缓存景点$it", "已有介绍", "1小时", "邯郸") })
            .copy(foods = (1..12).map { DestinationFood("缓存美食$it", "已有美食介绍") })
        AndroidGuideStore(context)
        val file = File(folder, "destination-guides/${city.id}.json")
        val bytes = com.google.gson.Gson().toJson(mapOf("schemaVersion" to 1, "guide" to legacy)).toByteArray()
        file.writeBytes(bytes)
        val store = AndroidGuideStore(context)
        val bounded = store.read(city.id)!!
        assertEquals(legacy.experiences.take(10), bounded.experiences)
        assertEquals(legacy.foods.take(10), bounded.foods)
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(runCatching { store.save(legacy) }.isFailure)
        assertArrayEquals(bytes, file.readBytes())
        store.save(bounded)
        assertEquals(bounded, AndroidGuideStore(context).read(city.id))
    }
    @Test fun cacheAboveHalfMegabyteReopensAndOversizeWritePreservesPreviousBytes() {
        val data = guide((1..10).map { DestinationExperience("p$it", "缓存景点$it", "字".repeat(1700), "1小时", "邯郸") })
            .copy(sources = listOf(GuideSource("资料".repeat(100_000), "https://you.ctrip.com/a", "2026-09-22")))
        val store = AndroidGuideStore(context); store.save(data)
        val file = File(folder, "destination-guides/${city.id}.json")
        val before = file.readBytes()
        assertTrue(before.size > 512 * 1024)
        assertEquals(data, AndroidGuideStore(context).read(city.id))
        val tooLarge = data.copy(sources = listOf(GuideSource("x".repeat(16 * 1024 * 1024), "https://you.ctrip.com/a", "2026-09-22")))
        assertTrue(runCatching { store.save(tooLarge) }.isFailure)
        assertArrayEquals(before, file.readBytes())
        assertEquals(data, AndroidGuideStore(context).read(city.id))
    }
}
