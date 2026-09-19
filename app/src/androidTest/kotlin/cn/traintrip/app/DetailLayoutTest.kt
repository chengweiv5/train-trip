package cn.traintrip.app

import android.graphics.Bitmap
import android.util.Log
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class DetailLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val date=today().plusYears(1).withMonth(10).withDayOfMonth(29)
    private val from=catalog.byCode.getValue("BJP")
    private val to=catalog.byCode.getValue("TJP")
    private val seats=mapOf(SeatType.SECOND to SeatAvailability("有",AvailabilityKind.AVAILABLE),SeatType.FIRST to SeatAvailability("8",AvailabilityKind.COUNT,8))
    private var state by mutableStateOf(UiState(catalog,SearchFilters()))
    private fun start(days:Int,large:Boolean=false) {
        val f=SearchFilters(startDate=date,endDate=date.plusDays(days-1L),seats=seats.keys,destinationCityIds=setOf(to.cityId))
        val units=f.dates().map { QueryUnit(it,from,to) }
        val at=Instant.parse("2026-09-19T04:00:00Z")
        val outcomes=units.associate { u->u.key to QueryResult.Success((1..3).map { n->
            Trip(u.date,"G$n","G$n",from,to,LocalTime.of(23,30),LocalTime.of(1,5),95,SaleState.OPEN,"",seats,at)
        },at) }
        state=UiState(catalog,f,applied=f,progress=SearchProgress(units,outcomes),cityId=to.cityId)
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,if(large) 1.3f else 1f)) {
                TrainTripTheme { Box(Modifier.width(if(large) 320.dp else (1260f/3.25f).dp).fillMaxHeight()) {
                    DetailScreen(state,{}, { t,s->state=state.copy(selectedTripKey=t.key,selectedSeat=s) },{},{},
                        {state=state.copy(selectedTripKey=null,selectedSeat=null)},{})
                } }
            }
        }
    }
    private fun firstCard()=compose.onNodeWithTag("trip-${date}/G1/${from.code}/${to.code}")
    private fun selectFirst() {
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("trip-${date}/G1/${from.code}/${to.code}"))
        compose.onNode(hasText("二等座 有票") and hasAnyAncestor(hasTestTag("trip-${date}/G1/${from.code}/${to.code}"))).performScrollTo().performClick()
    }
    @Test fun threeDatesFitAndCardUsesLessVerticalSpace() {
        start(3)
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("trip-${date}/G1/${from.code}/${to.code}"))
        val cardHeight=firstCard().fetchSemanticsNode().boundsInRoot.height/compose.activity.resources.displayMetrics.density
        Log.i("DetailLayout","cardHeightDp=$cardHeight")
        capture("normal-card")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("detail-dates"))
        capture("three-dates")
        val labels=listOf("全部日期")+(0L..2L).map { compactDate(date.plusDays(it)) }
        val bounds=labels.map { compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        bounds.forEach { assertEquals(bounds.first().top,it.top,1f) }
        val row=compose.onNodeWithTag("detail-dates").fetchSemanticsNode().boundsInRoot
        bounds.forEach { assertTrue("Date clipped by row",it.left>=row.left && it.right<=row.right) }
        Log.i("DetailLayout","dateBounds=$bounds; rowBounds=$row")
        assertTextFits()
        assertTrue("Card too tall: $cardHeight",cardHeight<=230f)
        selectFirst()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("detail-dates"))
        compose.onNodeWithText(compactDate(date.plusDays(2))).performScrollTo().performClick()
        compose.runOnIdle { assertNull(state.selectedTripKey) }
        firstCard().assertDoesNotExist()
        compose.onNodeWithText("全部日期").performClick()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("trip-${date}/G1/${from.code}/${to.code}"))
        firstCard().assertIsDisplayed()
    }
    @Test fun largeFontCanReachLastDateAndSeatDetails() {
        start(31,large=true)
        compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("detail-dates"))
        compose.onNodeWithText(compactDate(date.plusDays(30))).performScrollTo().performClick()
        compose.onNodeWithText(compactDate(date.plusDays(30))).assertIsDisplayed()
        capture("large-last-date")
        assertTextFits()
        compose.onNodeWithText("全部日期").performScrollTo().performClick()
        selectFirst()
        compose.onNode(hasText("1小时35分 · +1 天到达") and hasAnyAncestor(hasTestTag("trip-${date}/G1/${from.code}/${to.code}"))).assertExists()
        val seatNode=compose.onNode(hasText("二等座 有票") and hasAnyAncestor(hasTestTag("trip-${date}/G1/${from.code}/${to.code}")))
        seatNode.performScrollTo().assertIsDisplayed()
        val seat=seatNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Seat target smaller than 48dp",seat.height>=48*compose.activity.resources.displayMetrics.density-1)
        assertTextFits()
        capture("large-selected")
        val more=compose.onNode(hasText("更多席别与余票 ›") and hasAnyAncestor(hasTestTag("trip-${date}/G1/${from.code}/${to.code}")))
        more.performScrollTo().assertIsDisplayed()
        assertTextFits()
        capture("large-card-footer")
        more.performClick()
        compose.onNodeWithText("G1 · 席别余票").assertIsDisplayed()
    }
    private fun compactDate(d:LocalDate)="${d.monthValue}月${d.dayOfMonth}日"
    private fun assertTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[i].isDisplayed()) continue
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
    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("detail-compact")!!;dir.mkdirs()
        File(dir,"$name.jpg").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG,88,it) }
    }
}
