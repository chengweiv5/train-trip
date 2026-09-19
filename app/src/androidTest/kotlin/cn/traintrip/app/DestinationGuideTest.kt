package cn.traintrip.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class DestinationGuideTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val catalog = StationCatalog.bundled()
    private val tianjin = catalog.byCode.getValue("TJP").cityId
    private val shanghai = catalog.cities.first { it.name == "上海" }.id
    private val guide = DestinationGuides.find(tianjin)!!

    private class OfflineSource(val catalog: StationCatalog) : TicketSource {
        var queries = 0
        override suspend fun initialize() = SourceInfo("offline-test", today(), today().plusDays(15), catalog, Instant.now())
        override suspend fun query(unit: QueryUnit): QueryResult {
            queries++
            val trip = Trip(unit.date, "offline-${unit.destination.cityId}", "G123", unit.origin, unit.destination,
                LocalTime.of(8, 0), LocalTime.of(9, 0), 60, SaleState.OPEN, "",
                mapOf(SeatType.SECOND to SeatAvailability("8", AvailabilityKind.COUNT, 8)), Instant.now())
            return QueryResult.Success(listOf(trip), Instant.now())
        }
    }

    private fun startResults(): Pair<AppViewModel, OfflineSource> {
        val source = OfflineSource(catalog)
        val vm = AppViewModel(compose.activity.application as Application, source)
        compose.setContent { TrainTripTheme { TrainTripApp(vm) } }
        compose.runOnIdle {
            vm.updateFilters(SearchFilters(destinationCityIds = setOf(tianjin, shanghai)))
            vm.search()
        }
        compose.waitUntil(15000) { vm.state.value.progress?.complete == true }
        return vm to source
    }

    @Test fun guidePathPreservesPlanAndCanRefreshTickets() {
        val (vm, source) = startResults()
        revealCity(tianjin)
        compose.onNodeWithText("了解目的地").performScrollTo().assertIsDisplayed()
        capture("01-city-card")
        compose.onNodeWithText("了解目的地").performClick()
        compose.onNodeWithText(guide.tagline).assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription(guide.photo.description).fetchSemanticsNodes().isNotEmpty() }
        capture("02-destination")
        compose.onNodeWithTag("guide-tab-plans").performClick()
        page("plans").performScrollToNode(hasTestTag("plan-2"))
        compose.onNodeWithTag("plan-2").performClick()
        page("plans").performScrollToNode(hasText("两天多一点津味"))
        compose.onNodeWithText("两天多一点津味").assertIsDisplayed()
        capture("03-two-days")
        compose.onNodeWithTag("guide-trains").performClick()
        compose.onNodeWithText("去天津").assertIsDisplayed()
        val tripKey = vm.state.value.progress!!.trips.first { it.to.cityId == tianjin }.key
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("trip-$tripKey"))
        compose.onNodeWithTag("trip-$tripKey").performClick().assertIsSelected()
        compose.onNodeWithText("刷新余票").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.state.value.cityRefresh?.running == false }
        Espresso.pressBack()
        compose.onNodeWithText("两天多一点津味").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("了解目的地").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(Page.RESULTS, vm.state.value.page)
            assertEquals(3, source.queries) // two query units plus one explicit city refresh
        }
    }

    @Test fun directTrainPathAndUncoveredCityKeepOriginalBehavior() {
        val (vm, _) = startResults()
        revealCity(tianjin)
        compose.onNodeWithTag("trains-$tianjin").performScrollTo().performClick()
        compose.onNodeWithText("去天津").assertIsDisplayed()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(Page.RESULTS, vm.state.value.page) }
        revealCity(shanghai)
        compose.onNodeWithTag("city-card-$shanghai").performClick()
        compose.onNodeWithText("去上海").assertIsDisplayed()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(Page.RESULTS, vm.state.value.page) }
    }

    @Test fun missingGuideStillReachesTrains() {
        var opened = false
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DestinationGuideScreen("上海", null, {}, { opened = true }, {}) } } }
        compose.onNodeWithText("暂无目的地介绍，可先查看车次。").assertIsDisplayed()
        compose.onNodeWithTag("guide-trains").performClick()
        compose.runOnIdle { assertTrue(opened) }
        capture("04-missing-guide")
    }

    @Test fun domesticSourcesOpenCorrectUrlsWithoutClaimingPhotoLicense() {
        var opened = ""
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DestinationGuideScreen("天津", guide, {}, {}, { opened = it }) } } }
        page("places").performScrollToNode(hasText("资料来源与图片署名"))
        compose.onNodeWithText("资料来源与图片署名").performClick()
        compose.onNodeWithText(guide.sources.first().title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(guide.sources.first().url, opened) }
        compose.onNodeWithText("查看原图与作者").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(guide.photo.sourceUrl, opened) }
        compose.onNodeWithText("图片仅用于个人离线浏览，权利归原权利人所有。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("查看图片许可").assertDoesNotExist()
        compose.onNodeWithText("改编正文：CC BY-SA 4.0").assertDoesNotExist()
        assertTextFits()
        capture("05-sources")
    }

    @Test fun explicitlyLicensedPhotoKeepsLicenseLink() {
        val url = "https://creativecommons.org/licenses/by/4.0/"
        val licensedGuide = guide.copy(photo = guide.photo.copy(license = "CC BY 4.0", licenseUrl = url))
        var opened = ""
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DestinationGuideScreen("天津", licensedGuide, {}, {}, { opened = it })
        } } }
        page("places").performScrollToNode(hasText("资料来源与图片署名"))
        compose.onNodeWithText("资料来源与图片署名").performClick()
        compose.onNodeWithText("查看图片许可").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(url, opened) }
        compose.onNodeWithText("图片仅用于个人离线浏览，权利归原权利人所有。").assertDoesNotExist()
    }

    @Test fun compactLargeFontAndRestorationKeepPlanAndActionReachable() {
        val restoration = StateRestorationTester(compose)
        val datong = DestinationGuides.all.first { it.name == "大同" }
        restoration.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                TrainTripTheme {
                    Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                        DestinationGuideScreen("大同", datong, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("guide-trains").assertIsDisplayed()
        page("places").performScrollToNode(hasText("值得去的地方"))
        compose.onNodeWithText("值得去的地方").assertIsDisplayed()
        assertTextFits()
        assertControlsReachable()
        capture("06-large-font")
        compose.onNodeWithTag("guide-tab-plans").performClick()
        page("plans").performScrollToNode(hasTestTag("plan-2"))
        compose.onNodeWithTag("plan-2").performClick()
        restoration.emulateSavedInstanceStateRestore()
        page("plans").performScrollToNode(hasText("两天看石窟与古城"))
        compose.onNodeWithText("两天看石窟与古城").assertIsDisplayed()
        compose.onNodeWithTag("guide-trains").assertIsDisplayed()
        assertTextFits()
        assertControlsReachable()
        capture("07-large-font-plan")
        page("plans").performScrollToNode(hasText("资料来源与图片署名"))
        compose.onNodeWithText("资料来源与图片署名").assertIsDisplayed()
        assertTextFits()
    }

    @Test fun tabsAndSwipeShowOneTopicAndStopAtEnds() {
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DestinationGuideScreen("天津", guide, {}, {}, {}, "天津市 · 直辖市") } } }
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
        compose.onNodeWithText("值得去的地方").assertIsDisplayed()
        compose.onNodeWithText("尝尝当地味道").assertIsNotDisplayed()
        assertControlsReachable()
        capture("08-places")
        compose.onNodeWithTag("guide-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.onNodeWithTag("guide-tab-food").assertIsSelected()
        compose.onNodeWithText("尝尝当地味道").assertIsDisplayed()
        compose.onNodeWithText("值得去的地方").assertIsNotDisplayed()
        compose.onNodeWithText("煎饼馃子").assertIsDisplayed()
        assertTextFits()
        assertControlsReachable()
        capture("09-food")
        compose.onNodeWithTag("guide-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("guide-tab-plans").assertIsSelected()
        compose.onNodeWithTag("plan-2").performClick()
        page("plans").performScrollToNode(hasText("两天多一点津味"))
        capture("10-plans")
        compose.onNodeWithTag("guide-tab-tips").performClick()
        page("tips").performScrollToNode(hasText("到站后怎么走"))
        compose.onNodeWithText("到站后怎么走").assertIsDisplayed()
        capture("11-tips")
        compose.onNodeWithTag("guide-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("guide-tab-tips").assertIsSelected()
        compose.onNodeWithTag("guide-tab-plans").performClick()
        compose.onNodeWithText("两天多一点津味").assertIsDisplayed()
        compose.onNodeWithTag("guide-trains").assertIsDisplayed()
    }

    @Test fun eachPageKeepsScrollAndRestoresSelectedTab() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DestinationGuideScreen("天津",guide,{},{},{}) } } }
        page("places").performScrollToNode(hasTestTag("guide-sources-places"))
        val before=compose.onNodeWithTag("guide-sources-places").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.onNodeWithText("尝尝当地味道").assertIsDisplayed()
        compose.onNodeWithTag("guide-tab-places").performClick()
        compose.onNodeWithTag("guide-sources-places").assertIsDisplayed()
        assertEquals(before,compose.onNodeWithTag("guide-sources-places").fetchSemanticsNode().boundsInRoot.top,2f)
        compose.onNodeWithTag("guide-tab-plans").performClick()
        page("plans").performScrollToNode(hasTestTag("plan-2"))
        compose.onNodeWithTag("plan-2").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("guide-tab-plans").assertIsSelected()
        page("plans").performScrollToNode(hasText("两天多一点津味"))
        compose.onNodeWithText("两天多一点津味").assertIsDisplayed()
    }

    @Test fun differentCitiesAndNewSearchDoNotReuseTab() {
        val (vm,_)=startResults()
        revealCity(tianjin)
        compose.onNodeWithText("了解目的地").performClick()
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.runOnIdle { vm.showDestination("140200") }
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
        compose.runOnIdle { vm.showDestination(tianjin) }
        compose.onNodeWithTag("guide-tab-food").assertIsSelected()
        compose.runOnIdle { vm.search() }
        compose.waitUntil(15000) { vm.state.value.progress?.complete==true }
        compose.runOnIdle { vm.showDestination(tianjin) }
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
    }

    @Test fun shortViewportKeepsNavigationActionAndLastContentReachable() {
        compose.setContent { TrainTripTheme {
            Box(Modifier.width(320.dp).height(430.dp).safeDrawingPadding()) { DestinationGuideScreen("天津",guide,{},{},{}) }
        } }
        listOf("places","food","plans","tips").forEach { section->
            compose.onNodeWithTag("guide-tab-$section").assertIsDisplayed().performClick()
            page(section).performScrollToNode(hasTestTag("guide-sources-$section"))
            compose.onNodeWithTag("guide-sources-$section").assertIsDisplayed()
            compose.onNodeWithTag("guide-trains").assertIsDisplayed()
            assertTextFits()
        }
        capture("12-short-viewport")
    }

    private fun assertControlsReachable() {
        val density=compose.activity.resources.displayMetrics.density
        (listOf("places","food","plans","tips").map { "guide-tab-$it" }+"guide-trains").forEach { tag->
            val node=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$tag touch height",node.boundsInRoot.height >= 48*density-1)
        }
        val action=compose.onNodeWithTag("guide-trains").fetchSemanticsNode().boundsInRoot
        val viewport=compose.onNodeWithTag("destination-guide").fetchSemanticsNode().boundsInRoot
        assertTrue("Action outside viewport",action.bottom<=viewport.bottom+1)
    }

    private fun page(section:String)=compose.onNodeWithTag("guide-page-$section")
    private fun assertTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[i].isDisplayed())continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                layouts.forEach { l->for(line in 0 until l.lineCount) {
                    assertFalse("Ellipsized: ${l.layoutInput.text}",l.isLineEllipsized(line))
                    assertTrue("Text too wide: ${l.layoutInput.text}",l.getLineRight(line)-l.getLineLeft(line)<=l.size.width+1)
                    assertTrue("Text too tall: ${l.layoutInput.text}",l.getLineBottom(line)<=l.size.height+1)
                } }
            }
        }
    }

    private fun revealCity(cityId: String) {
        val province = catalog.byCity.getValue(cityId).province.id
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-$province"))
        val toggle = compose.onNodeWithTag("province-toggle-$province")
        if (toggle.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] != "已展开") toggle.performClick()
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("city-card-$cityId"))
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = compose.activity.getExternalFilesDir("destination-inspiration")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
