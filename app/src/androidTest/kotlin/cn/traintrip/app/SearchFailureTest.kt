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
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SearchFailureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failureDoesNotBlockOtherCitiesAndRetryOnlyQueriesFailedItem() {
        val catalog = StationCatalog.bundled()
        val filters = SearchFilters(destinationCityIds = setOf("TJP", "SJP", "QDK").map { catalog.byCode.getValue(it).cityId }.toSet())
        val plan = catalog.plan(filters)
        val calls = mutableListOf<QueryUnit>()
        var retry = false
        val error = "余票数据无法解析：出现未识别的席别状态，不能确定余票"
        val source = object : TicketSource {
            override suspend fun initialize() = SourceInfo("offline-test", today(), today().plusDays(15), catalog, Instant.now())
            override suspend fun query(unit: QueryUnit): QueryResult {
                calls += unit
                if (unit == plan.first() && !retry) return QueryResult.Failure(error)
                val trip = Trip(unit.date, unit.key, "G123", unit.origin, unit.destination,
                    LocalTime.of(8, 0), LocalTime.of(9, 0), 60, SaleState.OPEN, "预订",
                    mapOf(SeatType.SECOND to SeatAvailability("8", AvailabilityKind.COUNT, 8)), Instant.now())
                return QueryResult.Success(listOf(trip), Instant.now())
            }
        }
        val vm = AppViewModel(compose.activity.application, source)
        compose.setContent { TrainTripTheme { TrainTripApp(vm) } }
        compose.runOnIdle { vm.updateFilters(filters); vm.search() }
        compose.waitUntil(15000) { vm.state.value.progress?.let { !it.running && it.remainingCount == 0 } == true }
        compose.runOnIdle {
            assertEquals(plan, calls)
            val p = vm.state.value.progress!!
            assertEquals(2, p.successCount)
            assertEquals(1, p.failureCount)
            assertFalse(p.stopped)
            assertFalse(p.complete)
        }
        compose.onNodeWithText("找到 2 个城市").assertExists()
        compose.onNodeWithText("继续查询").assertDoesNotExist()
        compose.onNodeWithText("查看原因").performScrollTo().performClick()
        compose.onNodeWithText("查询未完成的原因").assertIsDisplayed()
        compose.onNodeWithText(error, substring = true).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { retry = true }
        compose.onAllNodesWithText("重试查询")[0].performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.progress?.complete == true }
        compose.runOnIdle {
            assertEquals(plan + plan.first(), calls)
            assertEquals(3, vm.state.value.progress!!.successCount)
            vm.pauseForegroundWork()
        }
        compose.onNodeWithText("找到 3 个城市").assertExists()
    }
}
