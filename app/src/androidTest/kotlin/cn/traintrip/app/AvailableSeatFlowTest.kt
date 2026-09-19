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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AvailableSeatFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val catalog = StationCatalog.bundled()
    private val tianjin = "120000"
    private val calls = AtomicInteger()

    private fun searchAndSelect(refreshedSeat: String): AppViewModel {
        val source = object : TicketSource {
            override suspend fun initialize() = SourceInfo("offline-test", today(), today().plusDays(15), catalog, Instant.now())
            override suspend fun query(unit: QueryUnit): QueryResult {
                val raw = if (calls.incrementAndGet() == 1) "有" else refreshedSeat
                val row = MutableList(39) { "" }.apply {
                    this[1] = "预订"; this[2] = "offline-G101"; this[3] = "G101"
                    this[6] = "VNP"; this[7] = "TJP"
                    this[8] = "08:00"; this[9] = "08:30"; this[10] = "00:30"
                    this[11] = "Y"; this[30] = raw
                }.joinToString("|")
                return TicketParser.parse("{\"status\":true,\"data\":{\"result\":[\"$row\"]}}", unit, catalog, Instant.now())
            }
        }
        val vm = AppViewModel(compose.activity.application, source)
        compose.setContent { TrainTripTheme { TrainTripApp(vm) } }
        compose.runOnIdle {
            vm.updateFilters(SearchFilters(people = 2, seats = setOf(SeatType.SECOND), destinationCityIds = setOf(tianjin)))
            vm.search()
        }
        compose.waitUntil(10000) { vm.state.value.progress?.complete == true }
        compose.onNodeWithText("找到 1 个城市").assertExists()
        compose.onNodeWithText("数量待核验 · 未计入上方城市数").assertDoesNotExist()
        compose.onNodeWithTag("city-card-$tianjin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 趟有票").assertExists()
        compose.onNodeWithTag("trains-$tianjin").performScrollTo().performClick()
        compose.onNodeWithText("二等座 有票").performScrollTo().performClick()
        compose.onNodeWithText("刷新余票").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.cityRefresh?.running == false }
        compose.runOnIdle { assertEquals("manual refresh queries selected city", 2, calls.get()) }
        return vm
    }

    @Test fun availableCountsForTwoPeopleAndRefreshesNormally() {
        val vm = searchAndSelect("有")
        compose.runOnIdle {
            assertNotNull(vm.state.value.selectedTripKey)
            assertEquals(2, vm.state.value.applied!!.people)
            assertNull(vm.state.value.progress!!.trips.single().seats.getValue(SeatType.SECOND).count)
            vm.pauseForegroundWork()
        }
    }

    @Test fun refreshRejectsExplicitCountBelowPartySize() { assertRefreshRejected("1") }

    @Test fun refreshRejectsTicketsThatBecameUnavailable() { assertRefreshRejected("无") }

    private fun assertRefreshRejected(raw: String) {
        val vm = searchAndSelect(raw)
        compose.onNodeWithText("所选席别当前不满足人数，请重新选择一个席别").assertExists()
        compose.runOnIdle {
            val state = vm.state.value
            assertNull(state.selectedTripKey)
            assertNull(state.selectedSeat)
            assertTrue(aggregate(state.progress!!.trips, state.applied!!).isEmpty())
            vm.dismissNotice()
            vm.showResults()
        }
        compose.onNodeWithText("找到 0 个城市").assertExists()
        compose.onNodeWithTag("city-card-$tianjin").assertDoesNotExist()
        compose.runOnIdle { vm.pauseForegroundWork() }
    }
}
