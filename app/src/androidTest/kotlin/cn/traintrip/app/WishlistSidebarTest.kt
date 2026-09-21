package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WishlistSidebarTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private fun wish(id:String,time:Long=1)=catalog.byCity.getValue(id).let { WishCity(id,it.name,it.province.name,time) }
    private fun province(id:String)=catalog.byCity.getValue(id).province.name
    private fun select(id:String) { compose.onNodeWithTag("wish-province-${province(id)}").performScrollTo().performClick().assertIsSelected() }
    private val sample=listOf("130800","130600","130300","320500","320100","370200","370100","370900","140100","140200","120000")
        .mapIndexed { index,id->wish(id,Instant.parse("2026-09-21T08:00:00Z").toEpochMilli()-index*86400000L) }

    @Composable private fun Screen(state:WishlistState,large:Boolean=false,onToggle:(City)->Unit={},onGuide:(String)->Unit={},onQuery:(String)->Unit={},onRetry:()->Unit={},
        browser:WishlistBrowserState=rememberSaveable(saver=WishlistBrowserState.Saver) { WishlistBrowserState() }) {
        val density=LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density,if(large)1.3f else 1f)) {
            TrainTripTheme {
                Column(Modifier.width(if(large)320.dp else 390.dp).fillMaxHeight().safeDrawingPadding()) {
                    Box(Modifier.weight(1f)) { WishlistScreen(state,catalog,emptyMap(),emptyList(),{},onToggle,onGuide,onQuery,onRetry,browser) }
                    RootNavigation(Page.WISHLIST,{})
                }
            }
        }
    }

    @Test fun allIsPinnedAndCombinesProvincesByCollectionTimeWithLiveCount() {
        val original=listOf(wish("130600",1),wish("320500",3),wish("130800",2))
        var state by mutableStateOf(WishlistState(original,loading=false))
        compose.setContent { Screen(state,onToggle={city->state=state.copy(items=state.items.filterNot { it.cityId==city.id })}) }
        compose.onNodeWithTag("wish-province-all").assertIsSelected().assertTextContains("3")
        compose.onNodeWithText("3 个想去城市 · 2 个省级地区").assertIsDisplayed()
        compose.onNodeWithText("全部城市 · 3 个城市").assertIsDisplayed()
        val suzhou=compose.onNodeWithTag("wish-320500").fetchSemanticsNode().boundsInRoot
        val chengde=compose.onNodeWithTag("wish-130800").fetchSemanticsNode().boundsInRoot
        assertTrue(suzhou.top<chengde.top)
        compose.onNodeWithTag("wish-320500").assertTextContains("江苏省")
        assertTextFits();capture("all")
        compose.onNodeWithTag("favorite-320500").performClick()
        compose.onNodeWithTag("wish-province-all").assertIsSelected().assertTextContains("2")
        compose.runOnIdle { state=state.copy(items=original) }
        compose.onNodeWithTag("wish-province-all").assertIsSelected().assertTextContains("3")
        select("130600")
        compose.onNodeWithTag("wish-320500").assertDoesNotExist()
        compose.onNodeWithTag("wish-province-all").performClick()
        compose.onNodeWithTag("wish-320500").assertIsDisplayed()
    }

    @Test fun onlySavedProvincesShowTheirOwnCitiesInCollectionOrder() {
        var guide="";var query=""
        compose.setContent { Screen(WishlistState(sample,loading=false),onGuide={guide=it},onQuery={query=it}) }
        compose.onNodeWithText("11 个想去城市 · 5 个省级地区").assertIsDisplayed()
        compose.onNodeWithTag("wish-province-北京市").assertDoesNotExist()
        select("130800")
        compose.onNodeWithTag("wish-320500").assertDoesNotExist()
        val chengde=compose.onNodeWithTag("wish-130800").fetchSemanticsNode().boundsInRoot
        val baoding=compose.onNodeWithTag("wish-130600").fetchSemanticsNode().boundsInRoot
        assertTrue(chengde.top<baoding.top)
        compose.onNodeWithTag("wish-130800").performClick()
        compose.onNodeWithTag("wish-query-130800").performClick()
        compose.runOnIdle { assertEquals("130800",guide);assertEquals("130800",query) }
        assertTextFits();capture("hebei")
        select("370200")
        compose.onNodeWithTag("wish-130800").assertDoesNotExist()
        compose.onNodeWithTag("wish-guide-370200").performClick()
        compose.runOnIdle { assertEquals("370200",guide) }
        assertTextFits();capture("shandong")
    }

    @Test fun removalChoosesNextThenPreviousAndUndoKeepsCurrentProvinceAndTimestamp() {
        val original=listOf(wish("130800",4),wish("130600",3),wish("320500",2),wish("370200",1))
        var state by mutableStateOf(WishlistState(original,loading=false))
        compose.setContent { Screen(state,onToggle={city->state=state.copy(items=state.items.filterNot { it.cityId==city.id })}) }
        select("320500")
        compose.onNodeWithTag("favorite-320500").performClick()
        compose.onNodeWithTag("wish-province-${province("370200")}").assertIsSelected()
        compose.runOnIdle { state=state.copy(items=original) }
        compose.onNodeWithTag("wish-province-${province("370200")}").assertIsSelected()
        select("130800")
        compose.onNodeWithTag("favorite-130800").performClick()
        compose.runOnIdle { state=state.copy(items=original) }
        assertTrue(compose.onNodeWithTag("wish-130800").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithTag("wish-130600").fetchSemanticsNode().boundsInRoot.top)
        select("370200")
        compose.onNodeWithTag("favorite-370200").performClick()
        compose.onNodeWithTag("wish-province-${province("320500")}").assertIsSelected()
        compose.runOnIdle { state=state.copy(items=emptyList()) }
        compose.onNodeWithText("先收藏一座想去的城市").assertIsDisplayed()
    }

    @Test fun provinceAndIndependentScrollPositionsSurviveNavigationAndStateRestoration() {
        val entries=catalog.cities.filter { it.province.id in setOf("13","37") }.mapIndexed { i,c->wish(c.id,i.toLong()) }
        val restoration=StateRestorationTester(compose)
        var visible by mutableStateOf(true)
        lateinit var browser:WishlistBrowserState
        restoration.setContent {
            val holder=rememberSaveableStateHolder()
            if(visible) holder.SaveableStateProvider("wishlist") {
                browser=rememberSaveable(saver=WishlistBrowserState.Saver) { WishlistBrowserState() }
                Screen(WishlistState(entries,loading=false),browser=browser)
            }
        }
        compose.onNodeWithTag("wish-province-all").assertIsSelected()
        compose.onNodeWithTag("wishlist-list").performScrollToIndex(8)
        val all=compose.runOnIdle { browser.list("").let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset } }
        select("130800")
        compose.onNodeWithTag("wishlist-list").performScrollToIndex(4)
        val hb=compose.runOnIdle { browser.list(province("130800")).let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset } }
        select("370200")
        compose.onNodeWithTag("wishlist-list").performScrollToIndex(6)
        val sd=compose.runOnIdle { browser.list(province("370200")).let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset } }
        compose.runOnIdle { visible=false }
        compose.runOnIdle { visible=true }
        compose.onNodeWithTag("wish-province-${province("370200")}").assertIsSelected()
        compose.runOnIdle { assertEquals(sd,browser.list(province("370200")).let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("wish-province-${province("370200")}").assertIsSelected()
        select("130800")
        compose.runOnIdle { assertEquals(hb,browser.list(province("130800")).let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }) }
        select("370200")
        compose.runOnIdle { assertEquals(sd,browser.list(province("370200")).let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }) }
        compose.onNodeWithTag("wish-province-all").performClick()
        compose.runOnIdle { assertEquals(all,browser.list("").let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("wish-province-all").assertIsSelected()
        compose.runOnIdle { assertEquals(all,browser.list("").let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }) }
    }

    @Test fun provinceRailScrollSurvivesStateRestoration() {
        val entries=catalog.provinces.map { p->catalog.cities.first { it.province==p } }.map { wish(it.id) }
        val restoration=StateRestorationTester(compose)
        lateinit var browser:WishlistBrowserState
        restoration.setContent {
            browser=rememberSaveable(saver=WishlistBrowserState.Saver) { WishlistBrowserState() }
            Screen(WishlistState(entries,loading=false),browser=browser)
        }
        compose.onNodeWithTag("wish-province-navigation").performScrollToIndex(20)
        compose.onNodeWithTag("wish-province-all").assertIsDisplayed()
        val position=compose.runOnIdle { browser.navigation.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset } }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(position,browser.navigation.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }) }
    }

    @Test fun reloadAndReadFailurePreserveSelectionWithoutShowingFalseEmptyState() {
        var state by mutableStateOf(WishlistState(sample,loading=false));var retries=0
        compose.setContent { Screen(state,onRetry={retries++}) }
        select("370200")
        compose.runOnIdle { state=state.copy(loading=true) }
        compose.onNodeWithTag("wish-province-${province("370200")}").assertIsSelected()
        compose.runOnIdle { state=state.copy(loading=false,error="读取失败") }
        compose.onNodeWithText("先收藏一座想去的城市").assertDoesNotExist()
        compose.onNodeWithText("重新读取").performClick()
        compose.runOnIdle { assertEquals(1,retries);state=state.copy(error=null) }
        compose.onNodeWithTag("wish-province-${province("370200")}").assertIsSelected()
    }

    @Test fun compactDropdownKeepsActionsAccessibleAndUnavailableRecordsVisible() {
        var state by mutableStateOf(WishlistState(sample,loading=false));var guide=""
        compose.setContent { Screen(state,large=true,onGuide={guide=it}) }
        compose.onNodeWithTag("wish-province-navigation").assertDoesNotExist()
        compose.onNodeWithTag("wish-province-dropdown").assertTextContains("全部城市 · 11 个城市")
        assertTextFits();capture("all-compact")
        compose.onNodeWithTag("wish-province-dropdown").performClick()
        compose.onNodeWithTag("wish-province-menu-${province("370200")}").performClick()
        for(tag in listOf("favorite-370200","wish-guide-370200","wish-query-370200")) {
            val bounds=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val min=48*compose.activity.resources.displayMetrics.density
            assertTrue("Touch target $tag",bounds.width>=min-1 && bounds.height>=min-1)
        }
        assertTextFits();capture("compact")
        compose.onNodeWithTag("wish-province-dropdown").performClick()
        compose.onNodeWithTag("wish-province-menu-all").performClick()
        compose.onNodeWithTag("wish-province-dropdown").assertTextContains("全部城市 · 11 个城市")
        compose.onNodeWithTag("wish-130800").assertIsDisplayed()
        compose.runOnIdle { state=WishlistState(listOf(WishCity("999999","旧城市","旧省份",1)),loading=false) }
        compose.onNodeWithTag("wish-query-999999").assertIsNotEnabled()
        compose.onNodeWithText("当前目录暂未收录此城市").assertIsDisplayed()
        compose.onNodeWithTag("wish-guide-999999").performClick()
        compose.runOnIdle { assertEquals("999999",guide);state=state.copy(items=listOf(wish("623000"))) }
        compose.onNodeWithTag("wish-query-623000").assertIsNotEnabled()
        assertTextFits()
    }

    private fun assertTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[i].isDisplayed())continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                layouts.forEach { layout->
                    assertFalse("Text ellipsized: ${layout.layoutInput.text}",layout.isLineEllipsized(layout.lineCount-1))
                    for(line in 0 until layout.lineCount) {
                        assertTrue("Text width: ${layout.layoutInput.text} ${layout.size}",layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                        assertTrue("Text height: ${layout.layoutInput.text} ${layout.size}",layout.getLineBottom(line)<=layout.size.height+1)
                    }
                }
            }
        }
    }
    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("wishlist-sidebar")!!;dir.mkdirs()
        File(dir,"$name.jpg").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG,90,it)
        }
    }
}
