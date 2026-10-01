package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeatSelectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val catalog = StationCatalog.bundled()
    private val initial = SearchFilters(
        seats = setOf(SeatType.SECOND, SeatType.FIRST),
        destinationCityIds = setOf("120000"),
    )
    private val allSeats = setOf(
        SeatType.SECOND, SeatType.FIRST, SeatType.PREFERRED, SeatType.BUSINESS,
        SeatType.SPECIAL, SeatType.HARD_SEAT, SeatType.SOFT_SEAT, SeatType.HARD_SLEEPER,
        SeatType.SOFT_SLEEPER, SeatType.PREMIUM_SLEEPER, SeatType.MOVING_SLEEPER,
        SeatType.STANDING, SeatType.OTHER, SeatType.YB,
    )

    @Test fun ticketBulkSelectionIsOnlySavedOnDoneAndEmptySelectionCannotBeSaved() {
        var filters by mutableStateOf(initial)
        compose.setContent {
            TrainTripTheme {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    FiltersScreen(UiState(catalog, filters), { filters = it }, {})
                }
            }
        }
        openTicketSeats()
        compose.onNodeWithText("全选").performClick()
        allSeats.forEach { seat -> seatRow(seat).assertIsOn() }
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(allSeats, filters.seats) }

        openTicketSeats()
        seatRow(SeatType.SECOND).performClick()
        seatRow(SeatType.SECOND).assertIsOff()
        seatRow(SeatType.FIRST).assertIsOn()
        compose.onNodeWithText("全不选").performClick()
        allSeats.forEach { seat -> seatRow(seat).assertIsOff() }
        compose.onNodeWithText("请至少选择一种席别").assertIsDisplayed()
        compose.onNodeWithText("完成").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(allSeats, filters.seats) }
        seatRow(SeatType.FIRST).performClick()
        compose.onNodeWithText("完成").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(setOf(SeatType.FIRST), filters.seats) }

        openTicketSeats()
        compose.onNodeWithText("全不选").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(setOf(SeatType.FIRST), filters.seats) }
        openTicketSeats()
        seatRow(SeatType.FIRST).assertIsOn()
        seatRow(SeatType.SECOND).assertIsOff()
        compose.onNodeWithText("全选").performClick()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(setOf(SeatType.FIRST), filters.seats) }
        openTicketSeats()
        seatRow(SeatType.FIRST).assertIsOn()
        seatRow(SeatType.SECOND).assertIsOff()
    }

    @Test fun bothEntriesKeepIndependentSelectionsAndBulkActionsFitAllThemesWithLargeText() {
        val waitlistInitial = WaitlistFilters(seats = setOf(SeatType.HARD_SEAT))
        val drafts = object : WaitlistDraftStore {
            private var value = waitlistInitial
            override suspend fun load() = value
            override suspend fun save(filters: WaitlistFilters) { value = filters }
        }
        val source = object : TicketSource {
            override suspend fun initialize() =
                SourceInfo("synthetic", today(), today().plusDays(14), catalog, Instant.now())
            override suspend fun query(unit: QueryUnit): QueryResult =
                error("Seat editing must not query tickets")
        }
        val vm = WaitlistViewModel(source, drafts)
        var filters by mutableStateOf(initial)
        var theme by mutableStateOf(ThemeChoice.BLUE)
        var page by mutableStateOf(Page.WAITLIST)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                TrainTripTheme(theme) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Box(Modifier.weight(1f)) {
                            if (page == Page.WAITLIST) {
                                val state by vm.state.collectAsState()
                                WaitlistScreen(state, vm, {}, {})
                            } else FiltersScreen(UiState(catalog, filters), { filters = it }, {})
                        }
                        RootNavigation(page, { page = it })
                    }
                }
            }
        }
        compose.waitUntil(5000) { vm.state.value.ready }
        for (choice in ThemeChoice.entries) {
            compose.runOnIdle {
                theme = choice
                filters = initial
                vm.updateFilters(waitlistInitial)
                page = Page.WAITLIST
            }
            openWaitlistSeats()
            compose.onNodeWithText("全选").performClick()
            allSeats.forEach { seat -> seatRow(seat).assertIsOn() }
            compose.onNodeWithText("已选 14 / 14 种席别").assertIsDisplayed()
            assertTextFits()
            capture("waitlist-${choice.id}-all")
            seatRow(SeatType.YB).performScrollTo().assertIsDisplayed()
            assertTextFits()
            capture("waitlist-${choice.id}-bottom")
            compose.onNodeWithText("全不选").assertIsDisplayed().performClick()
            allSeats.forEach { seat -> seatRow(seat).assertIsOff() }
            compose.onNodeWithText("请至少选择一种席别").assertIsDisplayed()
            compose.onNodeWithText("完成").assertIsNotEnabled()
            assertTextFits()
            capture("waitlist-${choice.id}-empty")
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle {
                assertEquals(waitlistInitial, vm.state.value.draft)
                assertEquals(initial, filters)
            }

            openWaitlistSeats()
            seatRow(SeatType.HARD_SEAT).assertIsOn()
            compose.onNodeWithText("全选").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle { assertEquals(allSeats, vm.state.value.draft.seats) }
            openWaitlistSeats()
            compose.onNodeWithText("全不选").performClick()
            // The checkbox indicator and the entire row must each toggle exactly once.
            val checkboxCenterX = with(compose.density) { 24.dp.toPx() }
            seatRow(SeatType.FIRST).performTouchInput { click(Offset(checkboxCenterX, center.y)) }
            seatRow(SeatType.FIRST).assertIsOn().performClick().assertIsOff()
            seatRow(SeatType.FIRST).performClick()
            compose.onNodeWithText("完成").assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(setOf(SeatType.FIRST), vm.state.value.draft.seats)
                assertEquals(initial, filters)
            }

            compose.onNodeWithTag("tab-FILTERS").performClick()
            openTicketSeats()
            seatRow(SeatType.SECOND).assertIsOn()
            seatRow(SeatType.FIRST).assertIsOn()
            compose.onNodeWithText("全选").performClick()
            allSeats.forEach { seat -> seatRow(seat).assertIsOn() }
            assertTextFits()
            capture("ticket-${choice.id}-all")
            compose.onNodeWithText("全不选").performClick()
            compose.onNodeWithText("完成").assertIsNotEnabled()
            seatRow(SeatType.STANDING).performScrollTo().performClick()
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle {
                assertEquals(setOf(SeatType.STANDING), filters.seats)
                assertEquals(setOf(SeatType.FIRST), vm.state.value.draft.seats)
            }
            compose.onNodeWithTag("tab-WAITLIST").performClick()
            openWaitlistSeats()
            seatRow(SeatType.FIRST).assertIsOn()
            seatRow(SeatType.STANDING).assertIsOff()
            compose.onNodeWithText("取消").performClick()
        }
    }

    private fun openWaitlistSeats() {
        compose.onNodeWithTag("waitlist-seats").performScrollTo().performClick()
        compose.onNodeWithText("选择席别").assertIsDisplayed()
    }

    private fun openTicketSeats() {
        compose.onNodeWithText("席别").performScrollTo().performClick()
        compose.onNodeWithText("选择席别").assertIsDisplayed()
    }

    private fun seatRow(seat: SeatType) = compose.onNode(hasText(seat.label) and isToggleable())

    private fun assertTextFits() {
        val nodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true,
        )
        for (i in nodes.fetchSemanticsNodes().indices) {
            if (!nodes[i].isDisplayed()) continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                get(results)
                results.forEach { layout ->
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Ellipsized: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                        assertTrue("Text too wide: ${layout.layoutInput.text}",
                            layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1)
                        assertTrue("Text too tall: ${layout.layoutInput.text}",
                            layout.getLineBottom(line) <= layout.size.height + 1)
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        val dir = compose.activity.getExternalFilesDir("seat-selection")!!.apply { mkdirs() }
        compose.waitForIdle()
        // The seat editor is a Dialog window; capture the display, not the Activity behind it.
        val original = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val ratio = minOf(1f, 1280f / maxOf(original.width, original.height))
        val bitmap = Bitmap.createScaledBitmap(
            original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true,
        )
        File(dir, "$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}
