package cn.traintrip.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.TrainTripApp
import cn.traintrip.app.ui.TrainTripTheme
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NotYetSaleFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun unopenedStarDoesNotHideOpenTrainOrBecomeAnAvailableTrain() {
        val catalog = StationCatalog.bundled()
        val tianjin = catalog.byCode.getValue("TJP").cityId
        fun row(code: String, flag: String, text: String, seat: String): String = MutableList(39) { "" }.apply {
            this[1] = text; this[2] = "offline-$code"; this[3] = code
            this[6] = "VNP"; this[7] = "TJP"
            this[8] = "08:00"; this[9] = "08:30"; this[10] = "00:30"
            this[11] = flag; this[30] = seat
        }.joinToString("|")
        val unopened = row("C2551", "IS_TIME_NOT_BUY", "12点45分起售", "*")
        val opened = row("G101", "Y", "预订", "8")
        val body = "{\"status\":true,\"data\":{\"result\":[\"$unopened\",\"$opened\"]}}"
        val source = object : TicketSource {
            override suspend fun initialize() = SourceInfo("offline-test", today(), today().plusDays(15), catalog, Instant.now())
            override suspend fun query(unit: QueryUnit) = TicketParser.parse(body, unit, catalog, Instant.now())
        }
        val vm = AppViewModel(compose.activity.application, source)
        compose.setContent { TrainTripTheme { TrainTripApp(vm) } }
        compose.runOnIdle {
            vm.updateFilters(SearchFilters(people = 2, destinationCityIds = setOf(tianjin)))
            vm.search()
        }
        compose.waitUntil(10000) { vm.state.value.progress?.let { !it.running && it.remainingCount == 0 } == true }
        compose.runOnIdle {
            val progress = vm.state.value.progress!!
            assertTrue(progress.complete)
            assertEquals(0, progress.failureCount)
            assertEquals(2, progress.trips.size)
            val notYet = progress.trips.single { it.trainCode == "C2551" }
            assertEquals(SaleState.NOT_YET, notYet.saleState)
            assertEquals("12点45分起售", notYet.saleText)
            assertEquals(AvailabilityKind.NOT_YET, notYet.seats.getValue(SeatType.SECOND).kind)
        }
        compose.onNodeWithText("1个城市有票").assertExists()
        compose.onNodeWithText("查看原因").assertDoesNotExist()
        compose.onNodeWithText("重试查询").assertDoesNotExist()
        compose.onNodeWithTag("trains-$tianjin").performScrollTo().performClick()
        compose.onNodeWithText("1趟车次",substring=true).assertExists()
        compose.onNodeWithText("G101").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("二等座 8 张").assertExists()
        compose.onNodeWithText("C2551").assertDoesNotExist()
        compose.runOnIdle { vm.pauseForegroundWork() }
    }
}
