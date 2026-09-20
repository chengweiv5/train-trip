package cn.traintrip.app

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.traintrip.core.StationCatalog
import cn.traintrip.core.WishlistRepository
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidWishlistStoreTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory:File
    private lateinit var context:ContextWrapper
    @Before fun prepare() {
        directory=File(app.cacheDir,"wishlist-store-test-${System.nanoTime()}").apply { mkdirs() }
        context=object:ContextWrapper(app) { override fun getFilesDir()=directory }
    }
    @After fun cleanup() { directory.deleteRecursively() }
    @Test fun simultaneousChangesFromDifferentStoreInstancesKeepAllCities() {
        val cities=StationCatalog.bundled().cities.take(12)
        val repositories=cities.map { WishlistRepository(AndroidWishlistStore(context)).apply { load() } }
        val start=CountDownLatch(1)
        val workers=Executors.newFixedThreadPool(cities.size)
        try {
            val writes=repositories.mapIndexed { index,repo -> workers.submit {
                check(start.await(5,TimeUnit.SECONDS))
                repo.add(listOf(cities[index]),100L+index)
            } }
            start.countDown()
            writes.forEach { it.get(5,TimeUnit.SECONDS) }
            assertEquals(cities.map { it.id }.toSet(),AndroidWishlistStore(context).read().map { it.cityId }.toSet())
            val removals=repositories.take(6).mapIndexed { index,repo -> workers.submit { repo.remove(cities[index].id) } }
            removals.forEach { it.get(5,TimeUnit.SECONDS) }
            assertEquals(cities.drop(6).map { it.id }.toSet(),AndroidWishlistStore(context).read().map { it.cityId }.toSet())
        } finally {
            start.countDown()
            workers.shutdownNow()
            workers.awaitTermination(5,TimeUnit.SECONDS)
        }
    }
    @Test fun unreadableLatestFileIsNotOverwrittenByAnOlderRepository() {
        val repo=WishlistRepository(AndroidWishlistStore(context));repo.load()
        val file=File(directory,"wishlist.json")
        val invalid="{invalid wishlist data"
        file.writeText(invalid)
        assertTrue(runCatching { repo.add(StationCatalog.bundled().cities.take(1),100) }.isFailure)
        assertEquals(invalid,file.readText())
        assertTrue(repo.items.isEmpty())
    }
}
