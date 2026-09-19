package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
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
class DestinationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val filters=SearchFilters(destinationCityIds=setOf("130100","370100","120000"))

    @Test fun provinceSearchSelectAllAndDraftCancel() {
        var applied=filters
        var open by mutableStateOf(true)
        compose.setContent { TrainTripTheme { if(open) DestinationSelector(UiState(catalog,filters),{open=false},{applied=it;open=false}) } }
        compose.onNodeWithText("清空选择").performClick()
        compose.onNodeWithTag("apply-destinations").assertIsNotEnabled()
        compose.onNodeWithTag("destination-search").performTextInput("hebei")
        compose.onNodeWithText("河北省").assertIsDisplayed()
        compose.onNodeWithText("11 个城市").assertIsDisplayed()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithTag("destination-search-results").performScrollToNode(hasTestTag("destination-130100"))
        compose.onNodeWithTag("destination-130100").assertIsOn()
        capture("province-search","destination-selector")
        compose.onNodeWithTag("destination-search").performTextReplacement("tianjin")
        compose.onNodeWithTag("destination-120000").performClick()
        compose.onNodeWithText("已选 2 个省级地区 · 12 个城市  ›").assertExists()
        compose.onNodeWithTag("apply-destinations").performClick()
        compose.runOnIdle { assertEquals(12,applied.destinationCityIds.size); open=true }
        compose.onNodeWithText("清空选择").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(12,applied.destinationCityIds.size) }
    }

    @Test fun selectedListRemovesProvinceAndMunicipalityHasSingleChoice() {
        var applied=filters
        compose.setContent { TrainTripTheme { DestinationSelector(UiState(catalog,filters),{},{applied=it}) } }
        compose.onNodeWithText("已选 3 个省级地区 · 3 个城市  ›").performClick()
        compose.onAllNodesWithText("移除本省")[0].performClick()
        compose.onNodeWithText("‹ 继续选择").performClick()
        compose.onNodeWithTag("destination-search").performTextInput("重庆")
        compose.onNodeWithText("1 个城市").assertExists()
        compose.onNodeWithTag("destination-500000").performClick()
        compose.onNodeWithTag("destination-search").performTextReplacement("北京")
        compose.onNodeWithTag("destination-110000").assertIsNotEnabled()
        compose.onNodeWithText("当前出发城市").assertExists()
        compose.onNodeWithTag("apply-destinations").performClick()
        compose.runOnIdle { assertTrue("500000" in applied.destinationCityIds);assertFalse("130100" in applied.destinationCityIds) }
    }

    @Test fun largeFontUsesDropdownAndUnavailableCitiesRemainVisible() {
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,1.3f)) {
                TrainTripTheme { DestinationSelector(UiState(catalog,filters),{},{}) }
            }
        }
        capture("selector-dropdown-diagnostic","destination-selector")
        compose.onNodeWithTag("province-dropdown").assertIsDisplayed()
        compose.onNodeWithTag("province-navigation").assertDoesNotExist()
        compose.onNodeWithTag("destination-search").performTextInput("甘南")
        compose.onNodeWithTag("destination-623000").assertIsNotEnabled()
        compose.onNodeWithText("暂无可查询车站").assertIsDisplayed()
        compose.onNodeWithTag("apply-destinations").assertIsDisplayed()
        capture("selector-large-font","destination-selector")
    }

    @Test fun browsingProvinceAndScrollSurviveReopen() {
        var open by mutableStateOf(true)
        val browser=DestinationBrowserState("13")
        compose.setContent { TrainTripTheme { if(open) DestinationSelector(UiState(catalog,filters),{open=false},{},browser) } }
        compose.onNodeWithTag("province-navigation").performScrollToNode(hasTestTag("province-nav-37"))
        compose.onNodeWithTag("province-nav-37").performClick()
        compose.onNodeWithTag("destination-city-list").performScrollToNode(hasTestTag("destination-370600"))
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { open=true }
        compose.onNodeWithTag("destination-370600").assertIsDisplayed()
        compose.runOnIdle { assertEquals("37",browser.provinceId) }
        capture("selector-provinces","destination-selector")
    }

    @Test fun resultGroupsToggleIndependentlyAndSurviveRestoreRefreshSortAndDetail() {
        var s by mutableStateOf(resultState())
        var clicked:String?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent { TrainTripTheme { ResultsScreen(s,{}, {clicked=it}, {s=s.copy(progress=s.progress?.copy(outcomes=emptyMap()))}, {}, {}, {}, {s=s.copy(citySortByCount=!s.citySortByCount)}) } }
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-13"))
        compose.onNodeWithTag("province-toggle-13").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已展开"))
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("city-card-130100"))
        compose.onNodeWithTag("city-card-130100").performClick()
        compose.runOnIdle { assertEquals("130100",clicked) }
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-37"))
        compose.onNodeWithTag("province-toggle-37").performClick()
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("city-card-370100"))
        compose.onNodeWithTag("city-card-370100").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("city-card-370100").assertIsDisplayed()
        compose.runOnIdle { s=s.copy(citySortByCount=true) }
        compose.onNodeWithTag("city-card-370100").assertIsDisplayed()
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-13"))
        compose.onNodeWithTag("province-toggle-13").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已展开"))
        capture("results-expanded")
        compose.onNodeWithTag("province-toggle-13").performClick()
        compose.onNodeWithTag("city-card-130100").assertDoesNotExist()
        compose.runOnIdle { s=s.copy(progress=s.progress?.copy(outcomes=emptyMap())) }
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-37"))
        compose.onNodeWithTag("province-toggle-37").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已展开"))
        compose.runOnIdle { s=resultState().copy(searchSession=2) }
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-13"))
        compose.onNodeWithTag("province-toggle-13").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已展开"))
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-37"))
        compose.onNodeWithTag("province-toggle-37").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已收起"))
    }

    @Test fun largeFontResultControlsAndBottomCollapseAreReachable() {
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,1.3f)) {
                TrainTripTheme { ResultsScreen(resultState(),{},{},{},{},{},{},{}) }
            }
        }
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-collapse-13"))
        compose.onNodeWithTag("province-collapse-13").assertIsDisplayed().performClick()
        compose.onNodeWithTag("province-toggle-13").assertIsDisplayed()
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("province-toggle-37"))
        compose.onNodeWithTag("province-toggle-37").performClick()
        compose.onNodeWithTag("results-list").performScrollToNode(hasTestTag("city-card-370100"))
        capture("results-large-font")
    }

    @Test fun incompleteQueryStatusNeverLooksCompleteAndKeepsAvailableCities() {
        val complete=resultState()
        val progress=complete.progress!!
        val first=progress.plan.first()
        val second=progress.plan[1]
        var state by mutableStateOf(complete.copy(progress=progress.copy(outcomes=mapOf(first.key to progress.outcomes.getValue(first.key)),running=true)))
        compose.setContent { TrainTripTheme { ResultsScreen(state,{},{},{},{},{},{},{}) } }
        compose.onNodeWithText("正在查询 1 / ${progress.plan.size}").assertExists()
        compose.onNodeWithText("查询完成").assertDoesNotExist()
        compose.runOnIdle { state=state.copy(progress=state.progress!!.copy(running=false,stopped=true)) }
        compose.onNodeWithText("查询已停止\n${progress.plan.size-1} 项待查询").assertExists()
        compose.onNodeWithText("继续查询").assertExists()
        val successful=progress.plan.last()
        val mixed=progress.plan.associate { unit->unit.key to when(unit) {
            first->QueryResult.Failure("测试失败")
            second->QueryResult.NotOnSale("明日开售")
            successful->progress.outcomes.getValue(unit.key)
            else->QueryResult.Success(emptyList(),Instant.now())
        } }
        compose.runOnIdle { state=complete.copy(progress=progress.copy(outcomes=mixed)) }
        compose.onNodeWithText("部分查询未完成\n1 项失败 · 1 项未开售").assertExists()
        compose.onNodeWithTag("results-list").performScrollToNode(hasText("1个城市有票"))
        compose.onNodeWithText("1个城市有票").assertIsDisplayed()
        compose.onNodeWithText("查看原因").performScrollTo().performClick()
        compose.onNodeWithText("测试失败",substring=true).assertIsDisplayed()
        compose.onNodeWithText("明日开售",substring=true).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { state=complete }
        compose.onNodeWithText("查询完成").assertExists()
        compose.onNodeWithText("查看原因").assertDoesNotExist()
        compose.onNodeWithText("3个城市有票").assertExists()
        compose.onNodeWithTag("results-list").performScrollToIndex(0)
        capture("results-copy-cleanup")
    }

    @Test fun oldPreferencesMigrateWithoutResettingOtherConditions() {
        val prefs=compose.activity.getSharedPreferences("travel-filters",0)
        val old="""{"origin":"0357","stations":["VNP"],"start":"${today().plusDays(1)}","end":"${today().plusDays(1)}","from":1320,"to":360,"seats":["SECOND"],"people":3,"max":480,"destinations":["1717","3102","0914"]}"""
        prefs.edit().putString("filters",old).commit()
        val migrated=Preferences(compose.activity).load(catalog)
        assertEquals("110000",migrated.originCityId);assertEquals(setOf("500000","330100"),migrated.destinationCityIds)
        assertEquals(setOf("VNP"),migrated.originStations);assertEquals(3,migrated.people);assertEquals(1320,migrated.startMinute)
        Preferences(compose.activity).save(migrated)
        assertEquals(migrated,Preferences(compose.activity).load(catalog))
    }

    private fun resultState():UiState {
        val f=filters.copy(startDate=today().plusDays(1))
        val plan=catalog.plan(f)
        val outcomes=plan.associate { u -> u.key to QueryResult.Success(listOf(Trip(u.date,u.key,"G123",u.origin,u.destination,
            LocalTime.of(9,0),LocalTime.of(10,30),90,SaleState.OPEN,"",mapOf(SeatType.SECOND to TicketParser.availability("8")),Instant.now())),Instant.now()) }
        return UiState(catalog,f,applied=f,progress=SearchProgress(plan,outcomes),searchSession=1)
    }
    private fun capture(name:String,tag:String?=null) {
        val bitmap=(if(tag==null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val dir=compose.activity.getExternalFilesDir("destination-verification")!!;dir.mkdirs()
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
