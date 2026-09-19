package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class UiPolishTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val initial=SearchFilters(startDate=today().plusMonths(1).withDayOfMonth(1),destinationCityIds=setOf("tianjin"))

    @Test fun rangeCalendarSelectsBothDatesAndCancelsDraft() {
        var applied=initial
        var open by mutableStateOf(true)
        compose.setContent { TrainTripTheme {
            if(open) DateFilterSheet(UiState(catalog,applied),{open=false},{applied=it;open=false})
        } }
        compose.onNodeWithText("日期范围").performClick()
        compose.onNodeWithTag("apply-dates").assertIsNotEnabled()
        day(initial.startDate).performClick()
        compose.onNodeWithTag("apply-dates").assertIsNotEnabled()
        day(initial.startDate.plusDays(2)).performClick()
        compose.onNodeWithTag("apply-dates").assertIsEnabled()
        capture("date-range", "date-sheet")
        compose.onNodeWithTag("apply-dates").performClick()
        compose.runOnIdle {
            assertEquals(initial.startDate,applied.startDate)
            assertEquals(initial.startDate.plusDays(2),applied.endDate)
            open=true
        }
        day(initial.startDate.plusDays(3)).performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(initial.startDate.plusDays(2),applied.endDate) }
    }

    @Test fun rangeCalendarCrossesMonth() {
        val start=initial.startDate.withDayOfMonth(initial.startDate.lengthOfMonth()-1)
        val end=start.plusDays(3)
        var applied=initial.copy(startDate=start,endDate=start)
        compose.setContent { TrainTripTheme { DateFilterSheet(UiState(catalog,applied),{}, {applied=it}) } }
        compose.onNodeWithText("日期范围").performClick()
        day(start).performScrollTo().performClick()
        val monthList=hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("range-calendar"))
        compose.onNode(monthList).performScrollToIndex((end.year-1900)*12+end.monthValue-1)
        day(end).performScrollTo().performClick()
        capture("date-cross-month", "date-sheet")
        compose.onNodeWithTag("apply-dates").performClick()
        compose.runOnIdle { assertEquals(start,applied.startDate);assertEquals(end,applied.endDate) }
    }

    @Test fun dateDraftSurvivesRestoreAndLongRangesStayInvalid() {
        val restoration=StateRestorationTester(compose)
        var applied=initial
        restoration.setContent { TrainTripTheme { DateFilterSheet(UiState(catalog,applied),{}, {applied=it}) } }
        compose.onNodeWithText("日期范围").performClick()
        day(initial.startDate).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("apply-dates").assertIsNotEnabled()
        day(initial.startDate.plusDays(2)).performClick()
        compose.onNodeWithTag("apply-dates").assertIsEnabled()
        compose.onNodeWithTag("apply-dates").performClick()
        compose.runOnIdle { assertEquals(initial.startDate.plusDays(2),applied.endDate) }
        day(initial.startDate).performClick()
        val end=initial.startDate.plusMonths(1).plusDays(2)
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("range-calendar"))).performScrollToIndex((end.year-1900)*12+end.monthValue-1)
        day(end).performScrollTo().performClick()
        compose.onNodeWithTag("apply-dates").assertIsNotEnabled()
        compose.onNodeWithText("一次最多查询 31 天，请缩短日期范围").assertIsDisplayed()
    }

    @Test fun continuousProgressHasNoGapOrEndpointDot() {
        var amount by mutableFloatStateOf(0f)
        compose.setContent { TrainTripTheme {
            Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("查询进度",style=MaterialTheme.typography.titleLarge)
                SearchProgressBar(amount)
                Text("已处理 ${(amount*60).toInt()} / 60 个查询项")
            }
        } }
        for(value in listOf(0f,.1f,.5f,1f)) {
            compose.runOnIdle { amount=value }
            compose.onNodeWithTag("query-progress").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(value,0f..1f))
            val pixels=compose.onNodeWithTag("query-progress").captureToImage().toPixelMap()
            val y=pixels.height/2
            for(x in 2 until pixels.width-2) {
                val expected=if(x<pixels.width*value-1) Primary else if(x>pixels.width*value+1) Line else continue
                val actual=pixels[x,y]
                assertEquals("progress=$value x=$x",expected.red,actual.red,.015f)
                assertEquals("progress=$value x=$x",expected.green,actual.green,.015f)
            }
        }
        compose.runOnIdle { amount=.5f }
        capture("progress-half")
    }

    @Test fun journeyTimingFitsNormalAndLargeFont() {
        var font by mutableFloatStateOf(1f)
        val trip=Trip(initial.startDate,"sample","G123",catalog.byCode.getValue("BJP"),catalog.byCode.getValue("TJP"),LocalTime.of(23,30),LocalTime.of(1,5),95,SaleState.OPEN,"",emptyMap(),Instant.now())
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,font)) {
                TrainTripTheme {
                    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("车次时间布局",style=MaterialTheme.typography.titleLarge)
                        ContentCard(Modifier.fillMaxWidth().testTag("timing-card")) {
                            Text("G123 · 跨日示例")
                            TripTiming(trip)
                        }
                        ContentCard(Modifier.width(300.dp)) { TripTiming(trip.copy(durationMinutes=2999)) }
                    }
                }
            }
        }
        compose.onNodeWithText("1小时35分",substring=true).assertIsDisplayed()
        compose.onNodeWithText("+1 天到达",substring=true).assertIsDisplayed()
        capture("timing-normal")
        compose.runOnIdle { font=1.3f }
        compose.onNodeWithText("1小时35分 · +1 天到达").assertIsDisplayed()
        compose.onNodeWithText("49小时59分 · +3 天到达").assertIsDisplayed()
        capture("timing-large-font")
    }

    private fun day(date:LocalDate)=compose.onNode(hasText(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())),substring=true) and hasClickAction())

    private fun capture(name:String,tag:String?=null) {
        val bitmap=(if(tag==null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val dir=compose.activity.getExternalFilesDir("ui-polish")!!
        dir.mkdirs()
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
