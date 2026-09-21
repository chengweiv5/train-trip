package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class BlueWhiteUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val date=today().plusDays(2)
    private val origin=catalog.byCode.getValue("BJP")
    private val destination=catalog.byCode.getValue("TJP")
    private val guide=DestinationGuides.find(destination.cityId)!!
    private val f=SearchFilters(startDate=date,endDate=date.plusDays(1),seats=setOf(SeatType.SECOND,SeatType.FIRST),destinationCityIds=setOf(destination.cityId),maxMinutes=180)
    private val trips=f.dates().flatMap { d->(1..3).map { n->Trip(d,"G$n","G$n",origin,destination,LocalTime.of(6+n,20),LocalTime.of(8+n,13),113,SaleState.OPEN,"",mapOf(SeatType.SECOND to SeatAvailability("有",AvailabilityKind.AVAILABLE),SeatType.FIRST to SeatAvailability("8",AvailabilityKind.COUNT,8)),Instant.parse("2026-09-19T01:41:00Z")) } }
    private val units=f.dates().map { QueryUnit(it,origin,destination) }
    private val initial=UiState(catalog,f,applied=f,progress=SearchProgress(units,units.associate { u->u.key to QueryResult.Success(trips.filter { it.date==u.date },Instant.parse("2026-09-19T01:41:00Z")) }),cityId=destination.cityId)

    @Test fun homeAndSettingsKeepActionsReachable() {
        var settings by mutableStateOf(false)
        var filters by mutableStateOf(f)
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(settings) SettingsScreen(DestinationState(configured=true,tavilyConfigured=true),{settings=false},{_,_,done->done()},{_,done->done()},{done->done()},{done->done()})
            else FiltersScreen(UiState(catalog,filters),{filters=it},{},{settings=true})
        } } }
        compose.onNodeWithText("有票再出发").assertIsDisplayed()
        assertTextFits();capture("home-top")
        compose.onNodeWithTag("search-cities").performScrollTo().assertIsDisplayed()
        assertTextFits();capture("home-bottom")
        compose.onNodeWithTag("content-settings").performClick()
        compose.onNodeWithTag("settings-model").assertIsDisplayed()
        assertTextFits();capture("settings-menu")
        compose.onNodeWithTag("settings-model").performClick()
        compose.onNodeWithTag("deepseek-key").performScrollTo().assertIsDisplayed()
        assertTextFits();capture("model-settings")
        compose.onNodeWithTag("save-settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("settings-search").performClick()
        compose.onNodeWithTag("tavily-key").performScrollTo().assertIsDisplayed()
        assertTextFits();capture("search-settings")
    }

    @Test fun resultsAndTrainSelectionHaveClearSeparateActions() {
        var state by mutableStateOf(initial)
        var detail by mutableStateOf(false)
        var opened=0
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(detail) DetailScreen(state,{detail=false},{state=state.copy(selectedTripKey=it.key)},{},{},{state=state.copy(selectedTripKey=null)},{opened++})
            else ResultsScreen(state,{},{detail=true},{},{},{},{},{state=state.copy(citySortByCount=!state.citySortByCount)})
        } } }
        assertTextFits();capture("results")
        val sort=compose.onNodeWithText("省内 · 车程最短 ↓",useUnmergedTree=true)
        val refresh=compose.onNodeWithText("刷新",useUnmergedTree=true)
        val a=sort.fetchSemanticsNode().boundsInRoot
        val b=refresh.fetchSemanticsNode().boundsInRoot
        val screen=compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("Sort/refresh outer margins",a.left-screen.left,screen.right-b.right,2f)
        assertEquals("Page and button inset",32f*compose.activity.resources.displayMetrics.density,a.left-screen.left,2f)
        sort.performClick()
        compose.onNodeWithText("省内 · 车次数最多 ↓").assertIsDisplayed()
        compose.onNodeWithTag("trains-${destination.cityId}").performScrollTo().performClick()
        compose.onNodeWithTag("selected-summary").assertDoesNotExist()
        compose.onNodeWithTag("open-12306").assertIsDisplayed().performClick()
        assertEquals(1,opened)
        assertTextFits();capture("trains-unmarked")
        val tag="trip-${trips.first().key}"
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick().assertIsSelected()
        compose.onNodeWithTag("selected-summary").assertIsDisplayed()
        val seats=compose.onNode(hasText("二等座 有票") and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true)
        seats.assertHasNoClickAction()
        assertTextFits();capture("trains-marked")
        compose.onNodeWithTag("seat-details-${trips.first().key}").performScrollTo().performClick()
        compose.onNodeWithText("G1 · 席别余票").assertIsDisplayed()
        assertTextFits();capture("seat-details")
    }

    @Test fun guideTabsAndFootersStayReachable() {
        compose.setContent { TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DestinationGuideScreen("天津",guide,{},{},{},"天津市",DestinationState(configured=true,tavilyConfigured=true))
        } } }
        for(section in listOf("places","food","plans","tips")) {
            compose.onNodeWithTag("guide-tab-$section").assertIsDisplayed().performClick().assertIsSelected()
            compose.onNodeWithTag("guide-trains").assertIsDisplayed()
            assertTextFits();capture("guide-$section")
            compose.onNodeWithTag("guide-page-$section").performScrollToNode(hasTestTag("refresh-guide"))
            compose.onNode(hasTestTag("refresh-guide") and hasAnyAncestor(hasTestTag("guide-page-$section"))).assertIsDisplayed()
            compose.onNodeWithTag("guide-page-$section").performScrollToNode(hasTestTag("guide-sources-$section"))
            compose.onNodeWithTag("guide-sources-$section").assertIsDisplayed()
        }
    }

    @Test fun fullScreenDateAndSeatPanelsApplyOnlyOnDone() {
        var filters by mutableStateOf(f)
        var kind by mutableStateOf("dates")
        var open by mutableStateOf(true)
        compose.setContent { TrainTripTheme { if(open) FilterSheet(kind,UiState(catalog,filters),{open=false},{filters=it;open=false}) } }
        compose.onNodeWithTag("apply-dates").assertIsDisplayed()
        capture("dates");assertTextFits()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(f,filters);kind="seats";open=true }
        compose.onNodeWithText("完成").assertIsDisplayed()
        compose.onNodeWithText("二等座").performClick()
        compose.runOnIdle { assertEquals(f,filters) }
        assertTextFits();capture("seat-selector")
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(setOf(SeatType.FIRST),filters.seats) }
    }

    @Test fun longDateRangeFitsWithDoubleFont() {
        val start=LocalDate.of(2026,11,18)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                TrainTripTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    FiltersScreen(UiState(catalog,f.copy(startDate=start,endDate=start.plusDays(30))),{},{})
                } }
            }
        }
        for(tag in listOf("home-start-date","home-end-date")) {
            compose.onNodeWithTag(tag,useUnmergedTree=true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag(tag,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                layouts.forEach { assertEquals("Date stays on one line",1,it.lineCount) }
            }
        }
        assertTextFits();capture("long-dates-double-font")
        compose.onNodeWithTag("search-cities").performScrollTo().assertIsDisplayed()
    }

    private fun assertTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[i].isDisplayed())continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                layouts.forEach { l->
                    for(line in 0 until l.lineCount) {
                        assertFalse("Ellipsized: ${l.layoutInput.text}",l.isLineEllipsized(line))
                        assertTrue("Text too wide: ${l.layoutInput.text}",l.getLineRight(line)-l.getLineLeft(line)<=l.size.width+1)
                        assertTrue("Text too tall: ${l.layoutInput.text}, bottom=${l.getLineBottom(line)}, height=${l.size.height}",l.getLineBottom(line)<=l.size.height+1)
                    }
                }
            }
        }
    }
    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("blue-white")!!;dir.mkdirs()
        compose.waitForIdle()
        val bitmap=if(name in setOf("dates","seat-selector","seat-details")) {
            android.os.SystemClock.sleep(350)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,88,it) }
    }
}
