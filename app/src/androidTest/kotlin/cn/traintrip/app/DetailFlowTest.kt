package cn.traintrip.app

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class DetailFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val city=catalog.byCode.getValue("TJP").cityId
    private val date=today().plusDays(1)
    private val old=Instant.now().minusSeconds(600)
    private var launchResult=AppLaunchResult.OPENED
    private var launches=0
    private class Source(val catalog:StationCatalog,val old:Instant):TicketSource {
        val calls=mutableListOf<QueryUnit>()
        var mode="initial"
        var gate:CompletableDeferred<Unit>?=null
        override suspend fun initialize()=SourceInfo("offline",today(),today().plusDays(15),catalog,Instant.now())
        override suspend fun query(unit:QueryUnit):QueryResult {
            calls+=unit
            gate?.await()
            if(mode=="failure" || mode=="partial" && unit.date==today().plusDays(2)) return QueryResult.Failure("测试网络中断")
            if(mode=="empty") return QueryResult.Success(emptyList(),Instant.now())
            val at=if(mode=="initial") old else Instant.now()
            return QueryResult.Success((1..3).map { index -> Trip(unit.date,"G$index","G$index",unit.origin,
                if(index==3 && unit.destination.cityName=="天津") catalog.byCode.getValue("TIP") else unit.destination,
                LocalTime.of(7+index,0),LocalTime.of(8+index,0),60-index,SaleState.OPEN,"",
                mapOf(SeatType.SECOND to SeatAvailability("有",AvailabilityKind.AVAILABLE),SeatType.FIRST to SeatAvailability("8",AvailabilityKind.COUNT,8)),at) },at)
        }
    }
    private fun start(twoDates:Boolean=false,large:Boolean=false,restoration:StateRestorationTester?=null):Pair<AppViewModel,Source> {
        val source=Source(catalog,old)
        val vm=AppViewModel(compose.activity.application,source)
        val content:@Composable ()->Unit={
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large) 1.3f else 1f)) {
                TrainTripTheme { Box(Modifier.width(if(large) 320.dp else 412.dp).fillMaxHeight()) { TrainTripApp(vm) { launches++;launchResult } } }
            }
        }
        if(restoration!=null) restoration.setContent(content) else compose.setContent(content)
        compose.runOnIdle {
            vm.updateFilters(SearchFilters(startDate=date,endDate=if(twoDates) date.plusDays(1) else date,people=2,seats=setOf(SeatType.SECOND,SeatType.FIRST),destinationCityIds=setOf(city)))
            vm.search()
        }
        compose.waitUntil(12000) { vm.state.value.progress?.complete==true }
        compose.runOnIdle { vm.showCity(city) }
        return vm to source
    }
    private fun selectFirst(vm:AppViewModel) {
        val trip=vm.state.value.progress!!.trips.first()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("trip-${trip.key}"))
        compose.onNode(hasText("二等座 有票") and hasAnyAncestor(hasTestTag("trip-${trip.key}"))).performScrollTo().performClick().assertIsSelected()
    }
    private fun refresh(vm:AppViewModel) {
        compose.onNodeWithTag("refresh-tickets").performScrollTo().performClick()
        compose.waitUntil(12000) { vm.state.value.cityRefresh?.running==false }
    }
    @Test fun appTapNeverQueriesOrCopiesAndFailureRetainsSelection() {
        val (vm,source)=start()
        compose.onNodeWithTag("open-12306").assertIsNotEnabled()
        capture("01-unselected")
        selectFirst(vm)
        val clipboard=compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("test","unchanged")) }
        compose.onNodeWithTag("open-12306").performClick()
        compose.runOnIdle {
            assertEquals(1,launches);assertEquals(1,source.calls.size)
            assertEquals("unchanged",clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertNotNull(vm.state.value.selectedTripKey)
        }
        capture("02-selected")
        for(result in listOf(AppLaunchResult.NOT_INSTALLED,AppLaunchResult.FAILED)) {
            compose.runOnIdle { launchResult=result }
            compose.onNodeWithTag("open-12306").performClick()
            compose.onNodeWithTag("detail-notice").assertIsDisplayed()
            compose.onNodeWithTag("copy-trip").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1,source.calls.size);assertNotNull(vm.state.value.selectedTripKey) }
            capture("03-${result.name.lowercase()}")
        }
        compose.onNodeWithTag("copy-trip").performClick()
        compose.runOnIdle {
            val text=clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("G1"));assertTrue(text.contains("2 位成人"));assertTrue(text.contains("查询时间"))
            vm.pauseForegroundWork()
        }
    }
    @Test fun partialRefreshRetainsOldTimestampAndRetriesOnlyFailure() {
        val (vm,source)=start(twoDates=true)
        selectFirst(vm)
        compose.runOnIdle { source.mode="partial" }
        refresh(vm)
        compose.runOnIdle {
            val state=vm.state.value
            assertEquals(4,source.calls.size);assertEquals(1,state.progress!!.failureCount)
            assertFalse(state.progress.complete);assertNotNull(state.selectedTripKey)
            assertTrue(state.progress.trips.filter { it.date==date.plusDays(1) }.all { it.queriedAt==old })
            source.mode="success"
        }
        compose.onNodeWithText("余票未更新",substring=true).performScrollTo().assertIsDisplayed()
        capture("04-partial-refresh")
        compose.onNodeWithText("重试刷新").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.cityRefresh?.running==false }
        compose.runOnIdle {
            assertEquals(5,source.calls.size);assertEquals(date.plusDays(1),source.calls.last().date)
            assertTrue(vm.state.value.progress!!.complete);assertTrue(vm.state.value.progress!!.retainedSuccesses.isEmpty())
        }
    }
    @Test fun duplicateRefreshIsPreventedAndCancellationPreservesData() {
        val (vm,source)=start()
        selectFirst(vm)
        compose.runOnIdle { source.gate=CompletableDeferred();vm.refreshCity();vm.refreshCity() }
        compose.waitUntil(5000) { source.calls.size==2 }
        compose.onNodeWithTag("open-12306").assertIsEnabled()
        compose.onNodeWithTag("refresh-tickets").performScrollTo().assertIsNotEnabled()
        capture("05-refreshing")
        compose.runOnIdle {
            vm.pauseForegroundWork();source.gate!!.complete(Unit)
            assertNotNull(vm.state.value.selectedTripKey)
            assertTrue(vm.state.value.progress!!.trips.all { it.queriedAt==old })
            assertTrue(vm.state.value.cityRefresh!!.stopped)
        }
    }
    @Test fun successfulRefreshClearsDisappearedSelection() {
        val (vm,source)=start()
        selectFirst(vm)
        compose.runOnIdle { source.mode="empty" }
        refresh(vm)
        compose.onNodeWithTag("open-12306").assertIsNotEnabled()
        compose.onNodeWithText("所选席别当前不满足人数，请重新选择一个席别").assertIsDisplayed()
        compose.runOnIdle { assertNull(vm.state.value.selectedTripKey);assertTrue(vm.state.value.progress!!.trips.isEmpty()) }
        capture("06-invalid-selection")
    }
    @Test fun filtersClearHiddenSelectionWhileSortAndRestorationKeepIt() {
        val restoration=StateRestorationTester(compose)
        val (vm,_)=start(twoDates=true,restoration=restoration)
        selectFirst(vm)
        compose.onNodeWithText("出发时间 ↑").performScrollTo().performClick()
        val key=vm.state.value.selectedTripKey
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("车程最短 ↑").assertExists()
        compose.runOnIdle { assertEquals(key,vm.state.value.selectedTripKey) }
        compose.onNodeWithText(dateLabel(date.plusDays(1))).performScrollTo().performClick()
        compose.onNodeWithTag("open-12306").assertIsNotEnabled()
        compose.runOnIdle { assertNull(vm.state.value.selectedTripKey) }
        compose.onNodeWithText("全部日期").performScrollTo().performClick()
        selectFirst(vm)
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("到达站筛选"))
        compose.onNodeWithText("到达站筛选").performClick()
        compose.onNode(hasText(catalog.byCode.getValue("TIP").name) and hasClickAction()).performClick()
        compose.runOnIdle { assertNull(vm.state.value.selectedTripKey) }
        compose.onNodeWithTag("open-12306").assertIsNotEnabled()
    }
    @Test fun compactLargeFontKeepsSummaryCopyAndLastCardReachable() {
        val restoration=StateRestorationTester(compose)
        val (vm,_)=start(large=true,restoration=restoration)
        selectFirst(vm)
        compose.onNodeWithTag("open-12306").assertIsDisplayed()
        compose.onNodeWithTag("copy-trip").assertIsDisplayed()
        val summary=compose.onNodeWithTag("selected-summary").fetchSemanticsNode().boundsInRoot
        val copy=compose.onNodeWithTag("copy-trip").fetchSemanticsNode().boundsInRoot
        assertTrue(copy.top>=summary.bottom)
        assertVisibleTextFits()
        val density=compose.activity.resources.displayMetrics.density
        for(tag in listOf("open-12306","copy-trip")) {
            val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag target height",bounds.height>=48*density-1)
        }
        capture("07-compact-selected")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("余票随时变化，以 12306 实时结果为准。"))
        compose.onNodeWithText("余票随时变化，以 12306 实时结果为准。").assertIsDisplayed()
        val before=compose.onNodeWithTag("detail-list").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle { vm.pauseForegroundWork() }
        restoration.emulateSavedInstanceStateRestore()
        val after=compose.onNodeWithTag("detail-list").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before,after,.1f)
        compose.onNodeWithTag("open-12306").assertIsEnabled().assertIsDisplayed()
        capture("08-compact-bottom")
    }
    private fun assertVisibleTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(index in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[index].isDisplayed()) continue
            val bounds=nodes[index].fetchSemanticsNode().boundsInRoot
            nodes[index].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                action(layouts)
                layouts.forEach { layout ->
                    // Compose may retain the wider paragraph constraint after sizing a short label.
                    // Check rendered line bounds, not the paragraph allocation width.
                    for(line in 0 until layout.lineCount) {
                        val message="Text clipped: ${layout.layoutInput.text}; size=${layout.size}; bounds=$bounds"
                        assertFalse(message,layout.isLineEllipsized(line))
                        assertTrue(message,layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                        assertTrue(message,layout.getLineBottom(line)<=layout.size.height+1)
                    }
                }
            }
        }
    }
    private fun capture(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val dir=compose.activity.getExternalFilesDir("v0.4.0")!!;dir.mkdirs()
        File(dir,"$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,88,it) }
    }
}
