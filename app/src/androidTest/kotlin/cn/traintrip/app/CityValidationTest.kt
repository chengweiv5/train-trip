package cn.traintrip.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.FiltersScreen
import cn.traintrip.app.ui.TrainTripTheme
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CityValidationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val catalog = StationCatalog.bundled()
    private val initial = SearchFilters(originStations = setOf("BJP"), destinationCityIds = setOf("120000"))
    private val sameCityError = "出发地和目的地不能相同，请修改出发地或目的地"

    private class RecordingSource(private val catalog: StationCatalog) : TicketSource {
        var initializations = 0
        val queries = mutableListOf<QueryUnit>()
        override suspend fun initialize(): SourceInfo {
            initializations++
            return SourceInfo("city-validation-test", today(), today().plusDays(15), catalog, Instant.now())
        }
        override suspend fun query(unit: QueryUnit): QueryResult {
            queries += unit
            return QueryResult.Success(emptyList(), Instant.now())
        }
    }

    private fun showFilters(filters: SearchFilters = initial): Pair<AppViewModel, RecordingSource> {
        val source = RecordingSource(catalog)
        val vm = AppViewModel(compose.activity.application, source)
        compose.runOnIdle { vm.updateFilters(filters) }
        compose.setContent {
            val state by vm.state.collectAsState()
            TrainTripTheme {
                FiltersScreen(state.copy(filters = state.cityQueryFilters ?: state.filters),
                    vm::updateFilters, { vm.search() })
            }
        }
        return vm to source
    }

    private fun chooseOrigin(name: String) {
        compose.onNodeWithTag("filter-origin").performScrollTo().performClick()
        compose.onNodeWithText("搜索出发城市").performTextInput(name)
        compose.onNode(hasText(name) and !hasSetTextAction()).performClick()
        compose.onNodeWithText("完成").performClick()
    }

    private fun chooseOnlyDestination(name: String, id: String) {
        compose.onNodeWithText("查询目的地").performScrollTo().performClick()
        compose.onNodeWithText("清空选择").performClick()
        compose.onNodeWithTag("destination-search").performTextInput(name)
        compose.onNodeWithTag("destination-$id").assertIsEnabled().performClick()
        compose.onNodeWithTag("apply-destinations").performClick()
        compose.onNodeWithTag("destination-selector").assertDoesNotExist()
    }

    @Test fun changingOriginToTheOnlyDestinationWaitsUntilSearchToReportConflict() {
        val (vm, source) = showFilters()
        chooseOrigin("天津")
        compose.onNodeWithText("出发城市与车站").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("120000", vm.state.value.filters.originCityId)
            assertEquals(setOf("120000"), vm.state.value.filters.destinationCityIds)
            assertTrue(vm.state.value.filters.originStations.isEmpty())
            assertNull(vm.state.value.error)
            assertEquals(0, source.initializations)
            assertTrue(source.queries.isEmpty())
        }
        compose.onNodeWithTag("search-cities").performScrollTo().performClick()
        compose.onNodeWithText(sameCityError).assertExists()
        compose.runOnIdle {
            assertEquals(Page.FILTERS, vm.state.value.page)
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.applied)
            assertEquals(0, source.initializations)
            assertTrue(source.queries.isEmpty())
        }
    }

    @Test fun selectingTheOriginAsDestinationIsAllowedUntilSearch() {
        val (vm, source) = showFilters()
        chooseOnlyDestination("北京", "110000")
        compose.runOnIdle {
            assertEquals(setOf("110000"), vm.state.value.filters.destinationCityIds)
            assertNull(vm.state.value.error)
            assertEquals(0, source.initializations)
        }
        compose.onNodeWithTag("search-cities").performScrollTo().performClick()
        compose.onNodeWithText(sameCityError).assertExists()
        compose.runOnIdle {
            assertEquals(Page.FILTERS, vm.state.value.page)
            assertEquals(0, source.initializations)
            assertTrue(source.queries.isEmpty())
        }
    }

    @Test fun reopeningTheAppPreservesSameCitySelectionWithoutShowingAnError() {
        val saved = initial.copy(destinationCityIds = setOf("110000"))
        val source = RecordingSource(catalog)
        compose.runOnIdle {
            AppViewModel(compose.activity.application, source).updateFilters(saved)
            val reopened = AppViewModel(compose.activity.application, source)
            assertEquals(saved, reopened.state.value.filters)
            assertNull(reopened.state.value.error)
            assertEquals(0, source.initializations)
            assertTrue(source.queries.isEmpty())
        }
    }

    @Test fun changingDestinationAfterAConflictClearsTheErrorAndQueriesNormally() {
        val (vm, source) = showFilters(initial.copy(destinationCityIds = setOf("110000")))
        compose.onNodeWithTag("search-cities").performScrollTo().performClick()
        compose.onNodeWithText(sameCityError).assertExists()
        chooseOnlyDestination("天津", "120000")
        compose.onNodeWithText(sameCityError).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, source.initializations) }
        searchAndAssertDestination(vm, source, "120000")
    }

    @Test fun singleCityQueryAllowsSameOriginButOnlyBlocksWhenSearching() {
        val (vm, source) = showFilters()
        compose.runOnIdle { vm.openCityQuery("120000") }
        chooseOrigin("天津")
        compose.onNodeWithText("出发城市与车站").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("120000", vm.state.value.cityQueryFilters?.originCityId)
            assertEquals(setOf("120000"), vm.state.value.cityQueryFilters?.destinationCityIds)
            assertEquals(initial, vm.state.value.filters)
            assertNull(vm.state.value.error)
        }
        compose.onNodeWithTag("search-cities").performScrollTo().performClick()
        compose.onNodeWithText("出发地和目的地不能相同，请修改出发地").assertExists()
        compose.runOnIdle {
            assertEquals(Page.CITY_QUERY, vm.state.value.page)
            assertEquals(0, source.initializations)
            assertTrue(source.queries.isEmpty())
        }
        chooseOrigin("北京")
        compose.runOnIdle { assertNull(vm.state.value.error) }
        searchAndAssertDestination(vm, source, "120000")
        compose.runOnIdle { assertEquals(initial, vm.state.value.filters) }
    }

    @Test fun mixedDestinationsKeepTheSameCitySelectionButOnlyQueryOtherCities() {
        val filters = initial.copy(destinationCityIds = setOf("110000", "120000"))
        val (vm, source) = showFilters(filters)
        searchAndAssertDestination(vm, source, "120000")
        compose.runOnIdle {
            assertEquals(filters, vm.state.value.filters)
            assertEquals(filters, vm.state.value.applied)
            assertEquals(filters, AppViewModel(compose.activity.application, source).state.value.filters)
        }
    }

    @Test fun emptyDestinationDoesNotBlockEditingOriginButBlocksSearchBeforeRequests() {
        val (vm, source) = showFilters(initial.copy(destinationCityIds = emptySet()))
        chooseOrigin("天津")
        compose.onNodeWithText("出发城市与车站").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("120000", vm.state.value.filters.originCityId)
            assertTrue(vm.state.value.filters.destinationCityIds.isEmpty())
            assertNull(vm.state.value.error)
        }
        compose.onNodeWithTag("search-cities").performScrollTo().performClick()
        compose.onNodeWithText("请至少选择一个目的地城市").assertExists()
        compose.runOnIdle {
            assertEquals(Page.FILTERS, vm.state.value.page)
            assertEquals(0, source.initializations)
            assertTrue(source.queries.isEmpty())
        }
    }

    @Test fun cancellingAnOriginChangePreservesTheSavedRoute() {
        val (vm, source) = showFilters()
        compose.onNodeWithTag("filter-origin").performScrollTo().performClick()
        compose.onNodeWithText("搜索出发城市").performTextInput("天津")
        compose.onNode(hasText("天津") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertEquals(initial, vm.state.value.filters)
            assertNull(vm.state.value.error)
            assertEquals(0, source.initializations)
        }
    }

    private fun searchAndAssertDestination(vm: AppViewModel, source: RecordingSource, destination: String) {
        compose.onNodeWithTag("search-cities").performScrollTo().performClick()
        compose.waitUntil(15000) { vm.state.value.progress?.let { !it.running && it.remainingCount == 0 } == true }
        compose.runOnIdle {
            assertNull(vm.state.value.error)
            assertEquals(Page.RESULTS, vm.state.value.page)
            assertEquals(1, source.initializations)
            assertTrue(source.queries.isNotEmpty())
            assertTrue(source.queries.all { it.destination.cityId == destination && it.origin.cityId != destination })
        }
    }
}
