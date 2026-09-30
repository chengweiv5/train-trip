package cn.traintrip.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WaitlistNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun productionAccountEntryDoesNotActivateWebLoginOrClaimAuthenticatedAfterRecreation() {
        fun verifyAccountEntry() {
            compose.onNodeWithTag("tab-WAITLIST").performClick()
            val waitlist = ViewModelProvider(compose.activity,
                waitlistViewModelFactory(compose.activity))[WaitlistViewModel::class.java]
            // ViewModels retain the open account page across activity recreation.
            if (waitlist.state.value.page == WaitlistPage.FILTERS)
                compose.onNodeWithTag("waitlist-account").performScrollTo().performClick()
            compose.onNodeWithTag("railway-login").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("waitlist-auth-disabled").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle {
                assertTrue(waitlist.nativePasswordAvailable)
                assertFalse(waitlist.nativeQrAvailable)
                assertNull(waitlist.state.value.account)
                assertFalse(waitlist.state.value.accountBusy)
                assertEquals(RailwayPasswordUiPhase.IDLE, waitlist.state.value.passwordPhase)
            }
        }
        verifyAccountEntry()
        try {
            compose.activity.packageManager.getActivityInfo(
                android.content.ComponentName(compose.activity, "cn.traintrip.app.RailwayLoginActivity"), 0)
            fail("Web login must not be registered")
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            // The installed manifest, not only a callback mock, excludes web authentication.
        }
        compose.activityRule.scenario.recreate()
        verifyAccountEntry()
    }

    @Test fun passwordAndVerificationInputAreNotRestoredAcrossActivityRecreation() {
        compose.onNodeWithTag("tab-WAITLIST").performClick()
        compose.onNodeWithTag("waitlist-account").performScrollTo().performClick()
        compose.onNodeWithTag("railway-username").performScrollTo().performTextInput("synthetic-user")
        compose.onNodeWithTag("railway-password").performScrollTo().performTextInput("synthetic-secret")
        assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab-WAITLIST").performClick()
        compose.onNodeWithTag("railway-username").assertTextContains("12306 账号 / 手机号 / 邮箱")
        compose.onNodeWithTag("railway-password").assertTextContains("12306 密码")
        compose.onNodeWithTag("railway-password-submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("railway-qr-start").assertDoesNotExist()
        val vm = ViewModelProvider(compose.activity,
            waitlistViewModelFactory(compose.activity))[WaitlistViewModel::class.java]
        assertFalse(vm.state.value.toString().contains("synthetic-user"))
        assertFalse(vm.state.value.toString().contains("synthetic-secret"))
    }

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
