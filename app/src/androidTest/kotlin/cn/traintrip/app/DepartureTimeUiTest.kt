package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DepartureTimeUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val base=SearchFilters(destinationCityIds=setOf("120000"),people=3,seats=setOf(SeatType.SECOND))
    private val pair=setOf(DeparturePeriod.MORNING,DeparturePeriod.EVENING)

    @Test fun shortcutsToggleIndependentlyAndAllDayResets() {
        val allPeriods=base.copy(startMinute=360,endMinute=720,departurePeriods=DeparturePeriod.entries.toSet())
        assertEquals("全天出发",timeRange(allPeriods));assertEquals("00:00–24:00",timeIntervalsText(allPeriods))
        var filters by mutableStateOf(base)
        compose.setContent { TrainTripTheme { FiltersScreen(UiState(catalog,filters),{filters=it},{}) } }
        compose.onNodeWithTag("time-MORNING").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("time-EVENING").performClick().assertIsOn()
        compose.onNodeWithTag("time-MORNING").assertIsOn();compose.onNodeWithTag("time-AFTERNOON").assertIsOff();compose.onNodeWithTag("time-all").assertIsOff()
        compose.onNodeWithTag("time-intervals").assertTextContains("06:00–12:00、18:00–24:00",substring=true)
        compose.runOnIdle { assertEquals(pair,filters.departurePeriods);assertFalse(filters.acceptsTime(900));assertEquals("早上、晚上",timeRange(filters)) }
        capture("shortcuts")
        compose.onNodeWithTag("time-MORNING").performClick().assertIsOff()
        compose.onNodeWithTag("time-EVENING").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("time-all").assertIsOn()
        compose.onNodeWithTag("time-AFTERNOON").performClick()
        compose.onNodeWithTag("time-all").performClick().assertIsOn()
        compose.onNodeWithTag("time-AFTERNOON").assertIsOff()
        for(period in DeparturePeriod.entries)compose.onNodeWithTag("time-${period.name}").performClick()
        compose.onNodeWithTag("time-all").assertIsOn()
        for(period in DeparturePeriod.entries)compose.onNodeWithTag("time-${period.name}").assertIsOff()
    }

    @Test fun customCancelPreservesMultiSelectAndDoneReplacesIt() {
        var filters by mutableStateOf(base.withDeparturePeriods(pair))
        compose.setContent { TrainTripTheme { FiltersScreen(UiState(catalog,filters),{filters=it},{}) } }
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        compose.onNodeWithText("完成后将替换已选快捷时段").assertIsDisplayed()
        compose.onNodeWithText("06:00 – 12:00").assertIsDisplayed()
        chooseWheel("开始分钟",1)
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(pair,filters.departurePeriods) }
        compose.onNodeWithTag("time-EVENING").assertIsOn()
        compose.onNodeWithText("自定义").performClick()
        chooseWheel("开始分钟",1)
        compose.onNodeWithTag("apply-time").performClick()
        compose.runOnIdle { assertEquals(361,filters.startMinute);assertEquals(720,filters.endMinute);assertTrue(filters.departurePeriods.isEmpty()) }
        compose.onNodeWithTag("time-MORNING").assertIsOff();compose.onNodeWithTag("time-EVENING").assertIsOff()
        compose.onNodeWithTag("time-intervals").assertTextContains("06:01–12:00",substring=true)
    }

    @Test fun savedSelectionRestoresInFreshPreferencesAndViewModelWithoutPollutingHome() {
        val saved=base.withDeparturePeriods(pair)
        Preferences(compose.activity).save(saved)
        assertEquals(saved,Preferences(compose.activity).load(catalog))
        val source=object:TicketSource {
            override suspend fun initialize():SourceInfo=error("no network expected")
            override suspend fun query(unit:QueryUnit):QueryResult=error("no network expected")
        }
        val vm=AppViewModel(compose.activity.application,source)
        assertEquals(pair,vm.state.value.filters.departurePeriods)
        vm.openCityQuery("120000")
        assertEquals(pair,vm.state.value.cityQueryFilters!!.departurePeriods)
        vm.updateFilters(vm.state.value.cityQueryFilters!!.toggleDeparturePeriod(DeparturePeriod.MORNING))
        assertEquals(setOf(DeparturePeriod.EVENING),vm.state.value.cityQueryFilters!!.departurePeriods)
        assertEquals(pair,vm.state.value.filters.departurePeriods)
        vm.back();assertEquals(saved,Preferences(compose.activity).load(catalog))
    }

    @Test fun legacyAndMalformedPeriodFieldsPreserveOtherPreferences() {
        val prefs=compose.activity.getSharedPreferences("travel-filters",0)
        val legacy=base.withCustomTime(1320,390)
        Preferences(compose.activity).save(legacy)
        val original=JSONObject(prefs.getString("filters",null)!!).apply { remove("periods") }
        fun load(value:Any?) :SearchFilters {
            val j=JSONObject(original.toString())
            if(value!=null)j.put("periods",value)
            prefs.edit().putString("filters",j.toString()).commit()
            return Preferences(compose.activity).load(catalog)
        }
        for(value in listOf(null,JSONArray(),"broken",JSONArray(listOf("MORNING","UNKNOWN")),JSONArray(listOf("MORNING",42)))) {
            assertEquals(legacy,load(value))
        }
        val valid=load(JSONArray(listOf("EVENING","MORNING")))
        assertEquals(pair,valid.departurePeriods);assertEquals(3,valid.people);assertEquals(base.seats,valid.seats)
        assertEquals(base.destinationCityIds,valid.destinationCityIds)
        Preferences(compose.activity).save(valid)
        assertEquals("[\"MORNING\",\"EVENING\"]",JSONObject(prefs.getString("filters",null)!!).getJSONArray("periods").toString())
        assertTrue(load(JSONArray(DeparturePeriod.entries.map { it.name })).isAllDay)
    }

    @Test fun multiSelectSummaryFitsNarrowScreenWithLargeFont() {
        var filters by mutableStateOf(base.withDeparturePeriods(pair))
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.3f)) {
                TrainTripTheme { Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                    FiltersScreen(UiState(catalog,filters),{filters=it},{})
                } }
            }
        }
        compose.onNodeWithTag("time-intervals").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("time-MORNING").assertIsOn();compose.onNodeWithTag("time-EVENING").assertIsOn()
        val texts=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in texts.fetchSemanticsNodes().indices) {
            if(!texts[i].isDisplayed())continue
            texts[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                for(layout in layouts)for(line in 0 until layout.lineCount) {
                    assertFalse(layout.isLineEllipsized(line))
                    assertTrue("Width: ${layout.layoutInput.text}",layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                    assertTrue("Height: ${layout.layoutInput.text}",layout.getLineBottom(line)<=layout.size.height+1)
                }
            }
        }
        capture("large-font")
        compose.onNodeWithText("自定义").performClick()
        compose.onNodeWithTag("apply-time").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("search-cities").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("time-multi")!!.apply { mkdirs() }
        File(dir,"$name.jpg").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG,90,it) }
    }
}
