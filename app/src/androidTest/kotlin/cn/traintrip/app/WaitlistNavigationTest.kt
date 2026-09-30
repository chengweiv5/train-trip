package cn.traintrip.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WaitlistNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun rootSwitchKeepsTicketFiltersIndependentAndWaitlistDraftSurvivesRecreation() {
        val app = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val waitlist = ViewModelProvider(compose.activity,
            waitlistViewModelFactory(compose.activity))[WaitlistViewModel::class.java]
        compose.waitUntil(5000) { waitlist.state.value.ready }
        val ordinary = app.state.value.filters
        compose.onNodeWithTag("tab-WAITLIST").performClick()
        compose.onNodeWithTag("waitlist-origin").assertIsDisplayed()
        compose.runOnIdle { waitlist.updateFilters(waitlist.state.value.draft.copy(
            destinationCityId = "120000", destinationStations = setOf("TJP"))) }
        compose.onNodeWithTag("tab-FILTERS").performClick()
        compose.onNodeWithTag("search-cities").assertExists()
        compose.runOnIdle { assertEquals(ordinary, app.state.value.filters) }
        compose.onNodeWithTag("tab-WAITLIST").performClick()
        compose.onNodeWithText("目的地 · 天津").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-WAITLIST").performClick()
        compose.onNodeWithText("目的地 · 天津").assertExists()
        compose.runOnIdle { assertEquals(ordinary, app.state.value.filters) }
    }
}
