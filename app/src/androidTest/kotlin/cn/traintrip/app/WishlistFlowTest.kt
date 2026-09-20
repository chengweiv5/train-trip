package cn.traintrip.app

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WishlistFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val city=catalog.cities.first { it.name=="苏州" }
    private class Store:WishlistStore {
        var items=emptyList<WishCity>();var fail=false
        override fun read()=items
        override fun write(items:List<WishCity>) { if(fail)throw IOException("test");this.items=items }
    }
    private class EmptyCredentials:GuideCredentials {
        override fun read():String?=null
        override fun save(value:String) {}
        override fun remove() {}
    }
    private class Tickets(val catalog:StationCatalog):TicketSource {
        var calls=0
        override suspend fun initialize()=SourceInfo("test offline",today(),today().plusDays(15),catalog,Instant.now())
        override suspend fun query(unit:QueryUnit):QueryResult { calls++;return QueryResult.Success(emptyList(),Instant.now()) }
    }
    private fun app(large:Boolean=false):Triple<AppViewModel,WishlistViewModel,Tickets> {
        val tickets=Tickets(catalog);val vm=AppViewModel(compose.activity.application,tickets)
        val wishes=WishlistViewModel(compose.activity.application,Store())
        val destination=DestinationViewModel(compose.activity.application,EmptyCredentials(),searchCredentials=EmptyCredentials())
        val updates=UpdateViewModel(UpdateSource { error("should not call") })
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.3f else 1f)) {
                TrainTripTheme { Box(Modifier.width(if(large)320.dp else 412.dp).fillMaxHeight()) { TrainTripApp(vm,destination,wishes,updates) } }
            }
        }
        compose.waitUntil(5000){!wishes.state.value.loading && !destination.state.value.offlineLoading}
        return Triple(vm,wishes,tickets)
    }
    private fun capture(name:String) {
        val file=File(compose.activity.getExternalFilesDir(null),"v05-$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun multiSelectPersistsAcrossSearchAndUndoRestoresOriginalOrder() {
        val (vm,wishes,tickets)=app()
        compose.onNodeWithTag("tab-WISHLIST").performClick();capture("empty")
        compose.onNodeWithText("添加想去城市").performClick()
        compose.onNodeWithTag("wish-search").performTextInput("苏州")
        compose.onNodeWithTag("add-city-${city.id}").performClick()
        compose.onNodeWithTag("wish-search").performTextReplacement("保定")
        compose.onNodeWithTag("add-city-130600").performClick();capture("add")
        compose.onNodeWithTag("add-wishes").performClick()
        compose.waitUntil(5000){wishes.state.value.items.size==2 && vm.state.value.page==Page.WISHLIST}
        assertEquals(0,tickets.calls);capture("wishlist")
        val before=wishes.state.value.items
        compose.onNodeWithTag("wishlist-list").performScrollToNode(hasTestTag("favorite-${city.id}"))
        compose.onNodeWithTag("favorite-${city.id}").performClick()
        compose.waitUntil(5000){wishes.state.value.items.size==1}
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5000){wishes.state.value.items.size==2}
        assertEquals(before,wishes.state.value.items)
        compose.onNodeWithTag("tab-FILTERS").performClick()
        compose.onNodeWithTag("content-settings").performClick()
        compose.onNodeWithTag("settings-about").performClick()
        compose.onNodeWithText("尚未检查更新").assertIsDisplayed();capture("about")
        compose.onNodeWithTag("navigate-back").performClick()
        compose.onNodeWithTag("settings-offline").performClick();capture("offline")
        compose.onNodeWithTag("navigate-back").performClick()
        assertEquals(Page.SETTINGS,vm.state.value.page)
    }
    @Test fun singleCityDraftAndGuideReturnPreserveHomeConditionsWithoutAutomaticRequests() {
        val (vm,wishes,tickets)=app(true)
        val home=SearchFilters(startDate=today().plusDays(1),endDate=today().plusDays(1),people=2,destinationCityIds=setOf("120000"))
        compose.runOnIdle { vm.updateFilters(home);wishes.add(listOf(city));vm.selectRoot(Page.WISHLIST) }
        compose.waitUntil(5000){wishes.state.value.items.size==1}
        capture("wishlist-large")
        compose.onNodeWithTag("wish-query-${city.id}").performClick()
        assertEquals(Page.CITY_QUERY,vm.state.value.page);capture("query-large")
        compose.runOnIdle { vm.updateFilters(vm.state.value.cityQueryFilters!!.copy(people=3));vm.search() }
        compose.waitUntil(12000){vm.state.value.progress?.complete==true}
        assertEquals(setOf(city.id),vm.state.value.applied!!.destinationCityIds)
        assertEquals(3,vm.state.value.applied!!.people);assertEquals(home,vm.state.value.filters)
        compose.runOnIdle { vm.showDestination(city.id) }
        compose.onNodeWithTag("favorite-${city.id}").assertIsOn()
        compose.onNodeWithTag("guide-trains").assertIsDisplayed();capture("guide-large")
        compose.runOnIdle { vm.back();vm.showFilters() }
        assertEquals(Page.CITY_QUERY,vm.state.value.page);assertEquals(3,vm.state.value.cityQueryFilters!!.people)
        compose.runOnIdle { vm.back() }
        assertEquals(Page.WISHLIST,vm.state.value.page);assertEquals(home,vm.state.value.filters)
        val calls=tickets.calls
        compose.runOnIdle { vm.showDestination(city.id) }
        compose.onNodeWithTag("guide-trains").performClick()
        assertEquals(Page.CITY_QUERY,vm.state.value.page);assertEquals(calls,tickets.calls)
        compose.runOnIdle { vm.back();vm.back();vm.selectRoot(Page.FILTERS) }
        assertEquals(home,vm.state.value.filters)
    }
    @Test fun failedAddKeepsSelectionForRetryAndCancelDoesNotWrite() {
        val store=Store();store.fail=true
        val wishes=WishlistViewModel(compose.activity.application,store)
        var closed=false
        compose.setContent { val state by wishes.state.collectAsState();TrainTripTheme { AddCityScreen(catalog,state,{closed=true},wishes::add) } }
        compose.waitUntil(5000){!wishes.state.value.loading}
        compose.onNodeWithTag("wish-search").performTextInput("苏州")
        compose.onNodeWithTag("add-city-${city.id}").performClick()
        compose.onNodeWithTag("add-wishes").performClick()
        compose.waitUntil(5000){wishes.state.value.notice!=null}
        assertFalse(closed);assertTrue(store.items.isEmpty());compose.onNodeWithTag("add-city-${city.id}").assertIsOn()
        store.fail=false;compose.onNodeWithTag("add-wishes").performClick()
        compose.waitUntil(5000){closed};assertEquals(1,store.items.size)
    }
    @Test fun updateStatesAreManualAndFailureNeverClaimsLatest() {
        var calls=0;var fail=false
        val gate=CompletableDeferred<Unit>()
        val vm=UpdateViewModel(UpdateSource { calls++;gate.await();if(fail)throw IOException();AppRelease(ReleaseVersion(99,0,0),releaseUrl("99.0.0"),"更新说明",true) })
        compose.setContent { val state by vm.state.collectAsState();TrainTripTheme { AboutScreen(state,{},vm::check) } }
        compose.onNodeWithText("尚未检查更新").assertIsDisplayed();assertEquals(0,calls)
        compose.onNodeWithTag("check-update").performClick()
        compose.onNodeWithText("正在检查更新").assertIsDisplayed();compose.onNodeWithTag("check-update").assertIsNotEnabled()
        compose.runOnIdle { gate.complete(Unit) };compose.waitUntil(5000){vm.state.value.release!=null}
        compose.onNodeWithText("前往 GitHub 下载").assertIsDisplayed();capture("update-new")
        fail=true;compose.onNodeWithText("再次检查").performClick();compose.waitUntil(5000){vm.state.value.error!=null}
        compose.onNodeWithText("暂时无法检查更新").assertIsDisplayed();compose.onNodeWithText("已是最新正式版").assertDoesNotExist();capture("update-failed")
        compose.runOnIdle { vm.leave() };compose.onNodeWithText("尚未检查更新").assertIsDisplayed()
    }
    @Test fun lateUpdateAfterLeavingCannotPublishState() {
        val entered=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>();val returned=CompletableDeferred<Unit>()
        val vm=UpdateViewModel(UpdateSource { entered.complete(Unit);withContext(NonCancellable){gate.await()};returned.complete(Unit);AppRelease(ReleaseVersion(1,0,0),releaseUrl("1.0.0"),"",true) })
        compose.runOnIdle { vm.check() };compose.waitUntil(5000){entered.isCompleted}
        compose.runOnIdle { vm.leave();gate.complete(Unit) };compose.waitUntil(5000){returned.isCompleted}
        compose.runOnIdle { assertEquals(UpdateState(),vm.state.value) }
    }
    @Test fun leavingAddDiscardsDraftAndStartingAgainIsEmpty() {
        val (vm,wishes,_)=app(true)
        compose.onNodeWithTag("tab-WISHLIST").performClick()
        compose.onNodeWithText("添加想去城市").performClick()
        compose.onNodeWithTag("wish-search").performTextInput("苏州")
        compose.onNodeWithTag("add-city-${city.id}").performClick()
        compose.onNodeWithTag("navigate-back").performClick()
        assertTrue(wishes.state.value.items.isEmpty())
        compose.onNodeWithText("添加想去城市").performClick()
        compose.onNodeWithTag("add-wishes").assertIsNotEnabled()
        compose.onNodeWithTag("wish-search").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        compose.runOnIdle { vm.back() };capture("empty-large")
    }
    @Test fun aboutLatestAndIncompleteAssetsStayAccurateOnLargeFont() {
        var state by mutableStateOf(UpdateState(release=AppRelease(ReleaseVersion(0,4,0),releaseUrl("0.4.0"),"",true),checkedAt=Instant.now()))
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                TrainTripTheme { Box(Modifier.width(320.dp).fillMaxHeight()) { AboutScreen(state,{}, {}) } }
            }
        }
        compose.onNodeWithText("已是最新正式版").assertIsDisplayed();capture("update-latest-large")
        compose.runOnIdle { state=UpdateState(release=AppRelease(ReleaseVersion(99,0,0),releaseUrl("99.0.0"),"新增收藏与离线内容管理",false)) }
        compose.onNodeWithText("新版本暂未提供完整安装包").assertIsDisplayed()
        compose.onNodeWithText("前往 GitHub 下载").assertDoesNotExist()
        compose.onNodeWithTag("check-update").performScrollTo().assertIsEnabled();capture("update-incomplete-large")
    }

}
