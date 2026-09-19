package cn.traintrip.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.onNodeWithTag("destination-guide").performScrollToNode(hasTestTag("plan-2"))
        compose.onNodeWithTag("plan-2").performClick()
        compose.onNodeWithTag("destination-guide").performScrollToNode(hasText("两天多一点津味"))
        compose.onNodeWithText("两天多一点津味").assertIsDisplayed()
        capture("03-two-days")
        compose.onNodeWithTag("guide-trains").performClick()
        compose.onNodeWithText("去天津").assertIsDisplayed()
        compose.onNodeWithText("二等座 8 张").performScrollTo().performClick()
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
        compose.setContent { TrainTripTheme { DestinationGuideScreen("上海", null, {}, { opened = true }, {}) } }
        compose.onNodeWithText("这个城市的旅行资料还在整理中。\n可以先查看车次，选择合适的出发时间。").assertIsDisplayed()
        compose.onNodeWithTag("guide-trains").performClick()
        compose.runOnIdle { assertTrue(opened) }
        capture("04-missing-guide")
    }

    @Test fun sourcesAndPhotoLicenseOpenTheCorrectUrls() {
        var opened = ""
        compose.setContent { TrainTripTheme { DestinationGuideScreen("天津", guide, {}, {}, { opened = it }) } }
        compose.onNodeWithText("资料来源与图片署名").performScrollTo().performClick()
        compose.onNodeWithText(guide.sources.first().title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(guide.sources.first().url, opened) }
        compose.onNodeWithText("查看原图与作者").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(guide.photo.sourceUrl, opened) }
        compose.onNodeWithText("查看图片许可").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(guide.photo.licenseUrl, opened) }
        capture("05-sources")
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
        compose.onNodeWithText("值得去的地方").performScrollTo().assertIsDisplayed()
        capture("06-large-font")
        compose.onNodeWithTag("destination-guide").performScrollToNode(hasTestTag("plan-2"))
        compose.onNodeWithTag("plan-2").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("destination-guide").performScrollToNode(hasText("两天看石窟与古城"))
        compose.onNodeWithText("两天看石窟与古城").assertIsDisplayed()
        compose.onNodeWithTag("guide-trains").assertIsDisplayed()
        capture("07-large-font-plan")
        compose.onNodeWithTag("destination-guide").performScrollToNode(hasText("查看来源与图片署名"))
        compose.onNodeWithText("查看来源与图片署名").assertIsDisplayed()
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
