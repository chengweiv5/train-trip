package cn.traintrip.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import cn.traintrip.core.StationCatalog
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.core.WishCity
import cn.traintrip.core.WishlistRepository
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises MainActivity's default ViewModel scope and real AtomicFile persistence. */
@RunWith(AndroidJUnit4::class)
class WishlistPersistenceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var store:AndroidWishlistStore
    private var before=emptyList<WishCity>()
    private val expected=setOf("130600","130800")
    @Before fun prepare() {
        store=AndroidWishlistStore(compose.activity)
        before=store.read()
        store.write(emptyList())
        val model=ViewModelProvider(compose.activity)[WishlistViewModel::class.java]
        compose.runOnIdle { model.reload() }
        compose.waitUntil(5000) { !model.state.value.loading && model.state.value.items.isEmpty() }
    }
    @After fun restore() { store.write(before) }
    @Test fun committedBaodingAndChengdeSurviveRootSwitchAndActivityRecreation() {
        compose.onNodeWithTag("tab-WISHLIST").performClick()
        compose.onNodeWithText("添加想去城市").performClick()
        compose.onNodeWithTag("wish-search").performTextInput("河北")
        for(id in expected)compose.onNodeWithTag("add-city-$id").performScrollTo().performClick()
        compose.onNodeWithTag("add-wishes").performClick()
        compose.waitUntil(5000){ViewModelProvider(compose.activity)[WishlistViewModel::class.java].state.value.items.map { it.cityId }.toSet()==expected}
        for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
        repeat(3) {
            compose.onNodeWithTag("tab-FILTERS").performClick()
            compose.onNodeWithTag("tab-WISHLIST").performClick()
            for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
            assertEquals(expected,store.read().map { it.cityId }.toSet())
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-WISHLIST").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithTag("wish-130600").fetchSemanticsNodes().isNotEmpty()}
        for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
        compose.onNodeWithText("先收藏一座想去的城市").assertDoesNotExist()
        assertEquals(expected,AndroidWishlistStore(compose.activity).read().map { it.cityId }.toSet())
    }
    @Test fun savedCitiesRemainVisibleAfterBackgroundResume() {
        compose.onNodeWithTag("tab-WISHLIST").performClick()
        val cities=StationCatalog.bundled().cities.filter { it.id in expected }
        compose.runOnIdle { ViewModelProvider(compose.activity)[WishlistViewModel::class.java].add(cities) }
        compose.waitUntil(5000){ViewModelProvider(compose.activity)[WishlistViewModel::class.java].state.value.items.map { it.cityId }.toSet()==expected}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
        assertEquals(expected,store.read().map { it.cityId }.toSet())
    }
    @Test fun enteringWishlistRefreshesCitiesSavedWhileHomeWasVisible() {
        val original=ViewModelProvider(compose.activity)[WishlistViewModel::class.java]
        assertTrue(original.state.value.items.isEmpty())
        val another=WishlistRepository(AndroidWishlistStore(compose.activity));another.load()
        another.add(StationCatalog.bundled().cities.filter { it.id in expected },100)
        compose.onNodeWithTag("tab-WISHLIST").performClick()
        compose.waitUntil(3000){compose.onAllNodesWithTag("wish-130600").fetchSemanticsNodes().isNotEmpty()}
        for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
        compose.onNodeWithText("先收藏一座想去的城市").assertDoesNotExist()
    }
    @Test fun returningToExistingActivityAfterAnotherActivityAddsCitiesShowsSavedList() {
        val original=compose.activity
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        compose.onNodeWithTag("tab-WISHLIST").performClick()
        compose.onNodeWithText("先收藏一座想去的城市").assertIsDisplayed()
        compose.runOnIdle { original.startActivity(Intent(original,MainActivity::class.java)) }
        val second=instrumentation.waitForMonitorWithTimeout(monitor,5000) as MainActivity
        try {
            val model=ViewModelProvider(second)[WishlistViewModel::class.java]
            compose.waitUntil(5000){!model.state.value.loading}
            val cities=StationCatalog.bundled().cities.filter { it.id in expected }
            instrumentation.runOnMainSync { model.add(cities) }
            compose.waitUntil(5000){model.state.value.items.map { it.cityId }.toSet()==expected}
            assertEquals(expected,store.read().map { it.cityId }.toSet())
            instrumentation.runOnMainSync { second.finish() }
            compose.waitUntil(5000){original.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
            compose.waitUntil(3000){compose.onAllNodesWithTag("wish-130600").fetchSemanticsNodes().isNotEmpty()}
            for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
            compose.onNodeWithTag("tab-FILTERS").performClick()
            compose.onNodeWithTag("tab-WISHLIST").performClick()
            compose.waitUntil(3000){compose.onAllNodesWithTag("wish-130600").fetchSemanticsNodes().isNotEmpty()}
            for(id in expected)compose.onNodeWithTag("wish-$id").assertExists()
            compose.onNodeWithText("先收藏一座想去的城市").assertDoesNotExist()
        } finally {
            instrumentation.runOnMainSync { if(!second.isFinishing)second.finish() }
            instrumentation.removeMonitor(monitor)
        }
    }

}
