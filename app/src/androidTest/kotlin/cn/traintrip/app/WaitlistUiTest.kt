package cn.traintrip.app

import android.graphics.Bitmap
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
import cn.traintrip.core.waitlist.*
import java.io.File
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaitlistUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val catalog = StationCatalog.bundled()
    private val date = today().plusDays(1)
    private val from = catalog.byCode.getValue("VNP")
    private val to = catalog.byCode.getValue("JGK")
    private val filters = WaitlistFilters(from.cityId, to.cityId, setOf("VNP"), setOf("JGK"), date, date,
        seats = setOf(SeatType.SECOND, SeatType.FIRST))
    private class Draft(var value: WaitlistFilters) : WaitlistDraftStore {
        override suspend fun load() = value
        override suspend fun save(filters: WaitlistFilters) { value = filters }
    }
    private inner class Source : TicketSource {
        var fail = false
        var calls = 0
        override suspend fun initialize() = SourceInfo("query", today(), date.plusDays(5), catalog, Instant.now())
        override suspend fun query(unit: QueryUnit): QueryResult {
            calls++
            if (fail) return QueryResult.Failure("测试网络异常")
            return QueryResult.Success(listOf(Trip(date, "train-37", "G37", from, to,
                LocalTime.of(8, 0), LocalTime.of(10, 0), 120, SaleState.OPEN, "预订",
                mapOf(SeatType.SECOND to TicketParser.availability("无"), SeatType.FIRST to TicketParser.availability("无")),
                Instant.now(), true, waitlistSeatLimit = "O", waitlistContextAvailable = true)), Instant.now())
        }
    }
    private fun start(source: Source = Source(), narrow: Boolean = false,
        theme: State<ThemeChoice> = mutableStateOf(ThemeChoice.BLUE),
        onOpenRailway: () -> Unit = {},
        authTransport: RailwayTransport? = null): WaitlistViewModel {
        val vm = WaitlistViewModel(source, Draft(filters),
            accountService = authTransport?.let { RailwayAccountService(it) { "opaque-test" } },
            passwordLogin = authTransport?.let { RailwayPasswordLogin(it) })
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (narrow) 1.6f else 1f)) {
                TrainTripTheme(theme.value) {
                    Column(Modifier.width(if (narrow) 320.dp else 412.dp).fillMaxHeight().safeDrawingPadding()) {
                        val s by vm.state.collectAsState()
                        Box(Modifier.weight(1f)) { WaitlistScreen(s, vm, {}, onOpenRailway) }
                        RootNavigation(Page.WAITLIST, {})
                    }
                }
            }
        }
        compose.waitUntil(5000) { vm.state.value.ready }
        return vm
    }
    @Test fun homeHasNoBackArrowAndLastResultsIsAnExplicitNonQueryActionInEveryTheme() {
        val theme = mutableStateOf(ThemeChoice.BLUE)
        val source = Source()
        val vm = start(source, narrow = true, theme = theme)
        compose.onNodeWithTag("page-title").assertTextEquals("候补")
        compose.onNodeWithTag("waitlist-back").assertDoesNotExist()
        compose.onNodeWithTag("waitlist-last-results").assertDoesNotExist()
        compose.onNodeWithTag("waitlist-search").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.queryComplete }
        compose.onNodeWithTag("waitlist-choice-$date/train-37/VNP/JGK-FIRST").performScrollTo().performClick()
        val applied = vm.state.value.applied
        val results = vm.state.value.progress
        val selected = vm.state.value.selected
        val draft = filters.copy(seats = setOf(SeatType.SECOND))
        for (choice in ThemeChoice.entries) {
            compose.runOnIdle { theme.value = choice }
            compose.onNodeWithTag("waitlist-back").performClick()
            compose.onNodeWithTag("page-title").assertTextEquals("候补")
            compose.onNodeWithTag("waitlist-back").assertDoesNotExist()
            compose.runOnIdle { vm.updateFilters(draft) }
            compose.onNodeWithTag("waitlist-last-results").performScrollTo().assertTextContains("查看上次结果")
            assertTextFits(); capture("home-navigation-${choice.id}")
            compose.onNodeWithTag("waitlist-last-results").performClick()
            compose.onNodeWithTag("page-title").assertTextEquals("候补车次")
            compose.onNodeWithTag("waitlist-back").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1, source.calls)
                assertEquals(draft, vm.state.value.draft)
                assertEquals(applied, vm.state.value.applied)
                assertEquals(results, vm.state.value.progress)
                assertEquals(selected, vm.state.value.selected)
            }
            assertTextFits(); capture("results-navigation-${choice.id}")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("page-title").assertTextEquals("候补")
            compose.onNodeWithTag("waitlist-back").assertDoesNotExist()
            compose.onNodeWithTag("waitlist-last-results").performScrollTo().performClick()
        }
    }

    @Test fun verifiedAdultWithBuyFlagNCanBeCheckedAndUncheckedInEveryTheme() {
        val theme = mutableStateOf(ThemeChoice.BLUE)
        val calls = mutableListOf<String>()
        val transport = object : RailwayTransport {
            override suspend fun post(path: String, fields: Map<String, String>): String {
                calls += path
                return when (path) {
                    "/otn/login/checkUser" -> """{"status":true,"data":{"flag":true}}"""
                    "/otn/modifyUser/initQueryUserInfoApi" ->
                        """{"status":true,"data":{"userDTO":{"loginUserDTO":{"user_name":"synthetic-account"}}}}"""
                    "/otn/confirmPassenger/getPassengerDTOs" ->
                        """{"status":true,"data":{"normal_passengers":[{"passenger_name":"张测试","passenger_id_no":"synthetic-id","passenger_id_type_code":"1","passenger_type":"1","passenger_type_name":"成人","allEncStr":"synthetic-secret","passenger_uuid":"synthetic-person","total_times":"99","is_buy_ticket":"N"}]}}"""
                    else -> error("This test must not call authentication or order submission")
                }
            }
        }
        val vm = start(narrow = true, theme = theme, authTransport = transport)
        compose.runOnIdle { vm.openAccount(); vm.refreshAccount() }
        compose.waitUntil(5000) { vm.state.value.account != null }
        for (choice in ThemeChoice.entries) {
            compose.runOnIdle { theme.value = choice }
            compose.onNode(isToggleable()).performScrollTo().assertIsEnabled().assertIsOff()
            compose.onNode(isToggleable()).performClick().assertIsOn()
            compose.waitUntil { vm.state.value.passengerSelection.size == 1 }
            compose.onNode(isToggleable()).performClick().assertIsOff()
            compose.waitUntil { vm.state.value.passengerSelection.isEmpty() }
            // Tapping the passenger row must toggle exactly once, not double-toggle with the checkbox.
            compose.onNodeWithText("张** · 成人").performScrollTo().performTouchInput { click() }
            compose.onNode(isToggleable()).assertIsOn()
            compose.onNodeWithText("张** · 成人").performTouchInput { click() }
            compose.onNode(isToggleable()).assertIsOff()
            assertTextFits()
        }
        assertTrue(calls.all { it in setOf("/otn/login/checkUser", "/otn/modifyUser/initQueryUserInfoApi",
            "/otn/confirmPassenger/getPassengerDTOs") })
    }

    @Test fun nativePasswordScreenSupportsSinglePhoneSmsAndNeverShowsQrInAllThemes() {
        val theme = mutableStateOf(ThemeChoice.BLUE)
        val calls = mutableListOf<String>()
        val transport = object : RailwayTransport {
            override suspend fun post(path: String, fields: Map<String, String>): String {
                calls += path
                return when (path) {
                    "/passport/web/checkLoginVerify" -> """{"login_check_code":"3"}"""
                    "/passport/web/getMessageCode" -> """{"result_code":"0"}"""
                    else -> error("no authorized account or order calls allowed")
                }
            }
        }
        val vm = start(narrow = true, theme = theme, authTransport = transport)
        compose.onNodeWithTag("waitlist-account").performScrollTo().performClick()
        assertTrue(calls.isEmpty())
        for (choice in ThemeChoice.entries) {
            compose.runOnIdle { theme.value = choice }
            compose.onNodeWithTag("railway-username").performScrollTo().performTextReplacement("synthetic-account")
            compose.onNodeWithTag("railway-password").performScrollTo().performTextReplacement("synthetic-password")
            compose.onNodeWithTag("railway-password-submit").performScrollTo().performClick()
            compose.waitUntil(5000) { vm.state.value.passwordPhase == RailwayPasswordUiPhase.SMS_REQUIRED }
            compose.onNodeWithTag("railway-password").assertTextContains("12306 密码")
            compose.onNodeWithTag("railway-identity-last-four").performScrollTo().performTextReplacement("123X")
            compose.onNodeWithTag("railway-send-sms").performScrollTo().assertIsEnabled()
            assertTextFits()
            compose.onNodeWithTag("railway-qr-start").assertDoesNotExist()
            compose.onNodeWithTag("waitlist-auth-disabled").performScrollTo().assertIsNotEnabled()
            assertNull(vm.state.value.account)
        }
        compose.onNodeWithTag("railway-send-sms").performScrollTo().performClick()
        compose.waitUntil { vm.state.value.passwordPhase == RailwayPasswordUiPhase.SMS_SENT }
        compose.onNodeWithTag("railway-send-sms").assertIsNotEnabled()
        compose.onNodeWithTag("railway-sms-code").performScrollTo().performTextInput("123456")
        assertTextFits()
        assertEquals(1, calls.count { it == "/passport/web/getMessageCode" })
        assertTrue(calls.all { it in setOf("/passport/web/checkLoginVerify", "/passport/web/getMessageCode") })
    }
    @Test fun appLoginEntryOpensOfficialAppWithoutClaimingAccountSynchronization() {
        val theme = mutableStateOf(ThemeChoice.BLUE)
        var launches = 0
        lateinit var vm: WaitlistViewModel
        vm = start(narrow = true, theme = theme, onOpenRailway = {
            launches++
            vm.reportRailwayLoginLaunch(AppLaunchResult.OPENED)
        })
        for (choice in ThemeChoice.entries) {
            compose.runOnIdle { theme.value = choice; vm.edit() }
            compose.onNodeWithTag("waitlist-account").performScrollTo().performClick()
            assertTextFits()
            capture("app-login-${choice.id}-top")
            compose.onNodeWithTag("railway-login").performScrollTo().performClick()
            compose.onNodeWithText("授权同步尚未接通", substring = true).assertExists()
            compose.onNodeWithTag("waitlist-auth-disabled").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("前往 12306 官方登录").assertDoesNotExist()
            assertNull(vm.state.value.account)
            assertTrue(vm.state.value.passengerSelection.isEmpty())
            assertTextFits()
            capture("app-login-${choice.id}-bottom")
        }
        assertEquals(ThemeChoice.entries.size, launches)
    }
    @Test fun selectionRespectsSeatRestrictionAndAuthenticationIsExplicitlyUnavailable() {
        val vm = start()
        compose.onNodeWithTag("waitlist-search").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.queryComplete }
        compose.onNodeWithText("二等座", substring = false).assertDoesNotExist()
        val choiceTag = "waitlist-choice-$date/train-37/VNP/JGK-FIRST"
        compose.onNodeWithTag(choiceTag).performScrollTo().performClick()
        compose.onNodeWithText("已选 1 组候补需求").assertIsDisplayed()
        assertTextFits(); capture("results-selected")
        compose.onNodeWithTag("waitlist-next").performClick()
        compose.onNodeWithTag("waitlist-auth-disabled").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("真实认证与提交暂未接通").assertExists()
        assertEquals(1, vm.state.value.selected.size)
        assertTextFits(); capture("authentication-unavailable")
        compose.runOnIdle { vm.edit(); vm.updateFilters(filters.copy(seats = setOf(SeatType.SECOND))); vm.search() }
        compose.waitUntil(10000) { vm.state.value.queryComplete }
        compose.onNodeWithTag("waitlist-hidden").assertIsDisplayed()
        compose.onNodeWithTag("waitlist-view-selected").performClick()
        compose.onNodeWithText("当前筛选未显示此项").assertExists()
        compose.onNodeWithText("移除").performScrollTo().performClick()
        compose.waitUntil { vm.state.value.selected.isEmpty() }
    }

    @Test fun locationCancelDoesNotApplyAndNewCityDropsOldStation() {
        val vm = start()
        compose.onNodeWithTag("waitlist-destination").performClick()
        compose.onNodeWithTag("waitlist-city-search").performTextInput("天津")
        compose.onNodeWithTag("waitlist-city-120000").performClick()
        compose.onNodeWithText("完成").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.runOnIdle { assertEquals(setOf("JGK"), vm.state.value.draft.destinationStations) }
        compose.onNodeWithTag("waitlist-destination").performClick()
        compose.onNodeWithTag("waitlist-city-search").performTextInput("天津")
        compose.onNodeWithTag("waitlist-city-120000").performClick()
        compose.onNodeWithTag("waitlist-location-apply").performClick()
        compose.runOnIdle {
            assertEquals("120000", vm.state.value.draft.destinationCityId)
            assertTrue(vm.state.value.draft.destinationStations.isEmpty())
            assertEquals(setOf("VNP"), vm.state.value.draft.originStations)
        }
    }

    @Test fun failedQueryIsNotPresentedAsNoTrainsAndCanRetry() {
        val source = Source().apply { fail = true }
        val vm = start(source)
        compose.runOnIdle { vm.search() }
        compose.waitUntil(10000) { vm.state.value.progress?.running == false }
        compose.onNodeWithTag("waitlist-retry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("没有符合当前条件的候补车次。", substring = true).assertDoesNotExist()
        source.fail = false
        compose.onNodeWithTag("waitlist-retry").performClick()
        compose.waitUntil(10000) { vm.state.value.queryComplete }
        assertEquals(1, vm.state.value.choices.size)
    }

    @Test fun verifiedOfficialDateRangeIsEnforcedBySharedDatePicker() {
        val vm = start()
        compose.runOnIdle {
            vm.updateFilters(filters.copy(startDate = date.plusDays(6), endDate = date.plusDays(6)))
            vm.search()
        }
        compose.waitUntil(10000) { vm.state.value.error != null }
        compose.runOnIdle { vm.edit() }
        compose.onNodeWithTag("waitlist-dates").performScrollTo().performClick()
        compose.onNodeWithTag("apply-dates").assertIsNotEnabled()
        compose.onNodeWithText("请选择官方范围", substring = true).assertExists()
    }

    @Test fun fourThemesAndNarrowLargeTextKeepControlsAndSelectedSummaryReachable() {
        val theme = mutableStateOf(ThemeChoice.BLUE)
        val vm = start(narrow = true, theme = theme)
        for (choice in ThemeChoice.entries) {
            compose.runOnIdle { theme.value = choice; vm.edit() }
            compose.onNodeWithTag("waitlist-origin").performScrollTo()
            assertTextFits(); capture("filters-${choice.id}-large")
            compose.onNodeWithTag("waitlist-search").performScrollTo().assertIsDisplayed()
            assertTextFits(); capture("filters-bottom-${choice.id}-large")
            compose.onNodeWithTag("waitlist-search").performClick()
            compose.waitUntil(10000) { vm.state.value.queryComplete }
            compose.onNodeWithTag("waitlist-next").assertIsDisplayed()
            assertTextFits(); capture("results-${choice.id}-large")
            compose.onNodeWithTag("waitlist-choice-$date/train-37/VNP/JGK-FIRST").performScrollTo()
            assertTextFits(); capture("result-card-${choice.id}-large")
        }
    }

    private fun assertTextFits() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        for (i in nodes.fetchSemanticsNodes().indices) {
            if (!nodes[i].isDisplayed()) continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); get(results)
                results.forEach { layout -> for (line in 0 until layout.lineCount) {
                    assertFalse("Ellipsized: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                    assertTrue("Text too wide: ${layout.layoutInput.text}", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1)
                    assertTrue("Text too tall: ${layout.layoutInput.text}", layout.getLineBottom(line) <= layout.size.height + 1)
                } }
            }
        }
    }
    private fun capture(name: String) {
        val dir = compose.activity.getExternalFilesDir("v2.0-integration")!!.apply { mkdirs() }
        val original = compose.onRoot().captureToImage().asAndroidBitmap()
        val ratio = minOf(1f, 1280f / maxOf(original.width, original.height))
        val bitmap = Bitmap.createScaledBitmap(original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true)
        File(dir, "$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}
