package cn.traintrip.app

import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaitlistViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalog = StationCatalog.bundled()
    private val date = today().plusDays(1)
    private val from = catalog.byCode.getValue("VNP")
    private val to = catalog.byCode.getValue("JGK")
    private val filters = WaitlistFilters(from.cityId, to.cityId, setOf("VNP"), setOf("JGK"), date, date)
    private val train = Trip(date, "id", "G37", from, to, LocalTime.of(8, 0), LocalTime.of(10, 0),
        120, SaleState.OPEN, "", mapOf(SeatType.SECOND to TicketParser.availability("无")),
        Instant.now(), true, waitlistContextAvailable = true)
    private class Draft(var value: WaitlistFilters) : WaitlistDraftStore {
        override suspend fun load() = value
        override suspend fun save(filters: WaitlistFilters) { value = filters }
    }
    private inner class Source : TicketSource {
        var result: QueryResult = QueryResult.Success(listOf(train), Instant.now())
        var calls = 0
        override suspend fun initialize() = SourceInfo("query", today(), date.plusDays(5), catalog, Instant.now())
        override suspend fun query(unit: QueryUnit): QueryResult { calls++; return result }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun backStopsAtWaitlistHomeWithoutLosingResultsDraftOrSelection() = runTest(dispatcher) {
        val source = Source()
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        vm.toggle(vm.state.value.choices.single())
        val results = vm.state.value.progress
        val selected = vm.state.value.selected
        vm.back()
        assertEquals(WaitlistPage.FILTERS, vm.state.value.page)
        val draft = filters.copy(seats = setOf(SeatType.FIRST))
        vm.updateFilters(draft)
        repeat(3) { vm.back() }
        assertEquals("Home must not navigate back into its results", WaitlistPage.FILTERS, vm.state.value.page)
        assertEquals(draft, vm.state.value.draft)
        assertEquals(filters, vm.state.value.applied)
        assertEquals(results, vm.state.value.progress)
        assertEquals(selected, vm.state.value.selected)
        assertEquals(1, source.calls)
    }

    @Test fun lastResultsRequiresAnAppliedQueryAndDoesNotApplyDraftOrStartAnotherQuery() = runTest(dispatcher) {
        val source = Source()
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.showLastResults()
        assertEquals(WaitlistPage.FILTERS, vm.state.value.page)
        assertEquals(0, source.calls)
        vm.search(); advanceUntilIdle()
        vm.toggle(vm.state.value.choices.single())
        val results = vm.state.value.progress
        val selected = vm.state.value.selected
        vm.edit()
        val draft = filters.copy(seats = setOf(SeatType.FIRST))
        vm.updateFilters(draft)
        vm.showLastResults()
        assertEquals(WaitlistPage.RESULTS, vm.state.value.page)
        assertEquals(filters, vm.state.value.applied)
        assertEquals(draft, vm.state.value.draft)
        assertEquals(results, vm.state.value.progress)
        assertEquals(selected, vm.state.value.selected)
        assertEquals(1, source.calls)
    }

    @Test fun openingOfficialAppDoesNotAuthenticateOrLoseSelectedDemands() = runTest(dispatcher) {
        val vm = WaitlistViewModel(Source(), Draft(filters))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        vm.toggle(vm.state.value.choices.single())
        vm.openAccount()
        vm.reportRailwayLoginLaunch(AppLaunchResult.OPENED)
        advanceUntilIdle()
        assertNull(vm.state.value.account)
        assertFalse(vm.state.value.accountBusy)
        assertTrue(vm.state.value.passengerSelection.isEmpty())
        assertEquals(1, vm.state.value.selected.size)
        assertTrue(vm.state.value.railwayAppNotice!!.contains("授权同步尚未接通"))
        vm.reviewOrder()
        assertEquals(WaitlistPage.AUTHENTICATION, vm.state.value.page)
    }

    @Test fun failedOfficialAppLaunchKeepsUserInAppWithoutWebFallback() = runTest(dispatcher) {
        val vm = WaitlistViewModel(Source(), Draft(filters))
        advanceUntilIdle()
        vm.openAccount()
        vm.reportRailwayLoginLaunch(AppLaunchResult.NOT_INSTALLED)
        assertTrue(vm.state.value.railwayAppNotice!!.contains("未找到 12306 App"))
        vm.reportRailwayLoginLaunch(AppLaunchResult.FAILED)
        assertTrue(vm.state.value.railwayAppNotice!!.contains("无法打开 12306 App"))
        assertTrue(vm.state.value.railwayAppNotice!!.contains("不会跳转网页登录"))
        assertNull(vm.state.value.account)
        assertEquals(WaitlistPage.AUTHENTICATION, vm.state.value.page)
    }

    @Test fun nativeQrRequiresApprovalAndVerifiedPassengersBeforeMarkingConnected() = runTest(dispatcher) {
        var status = "0"
        val paths = mutableListOf<String>()
        val transport = object : RailwayTransport {
            override suspend fun post(path: String, fields: Map<String, String>): String {
                paths += path
                return when (path) {
                    "/passport/web/create-qr64" -> """{"result_code":"0","uuid":"synthetic-id","image":"aW1hZ2U="}"""
                    "/passport/web/checkqr" -> """{"result_code":"$status"}"""
                    "/passport/web/auth/uamtk" -> """{"result_code":0,"newapptk":"synthetic-token"}"""
                    "/otn/uamauthclient" -> """{"result_code":0}"""
                    "/otn/login/checkUser" -> """{"status":true,"data":{"flag":true}}"""
                    "/otn/modifyUser/initQueryUserInfoApi" -> """{"status":true,"data":{"userDTO":{"loginUserDTO":{"user_name":"synthetic-account"}}}}"""
                    "/otn/confirmPassenger/getPassengerDTOs" -> """{"status":true,"data":{"normal_passengers":[]}}"""
                    else -> error("unexpected endpoint")
                }
            }
        }
        val vm = WaitlistViewModel(Source(), Draft(filters),
            accountService = RailwayAccountService(transport) { "opaque" },
            qrLogin = RailwayQrLogin(transport) { testScheduler.currentTime })
        runCurrent()
        vm.openAccount()
        assertTrue(paths.isEmpty())
        vm.startQrLogin(); runCurrent()
        assertNull(vm.state.value.account)
        assertEquals(RailwayQrUiPhase.WAITING_SCAN, vm.state.value.qrPhase)
        status = "1"
        advanceTimeBy(2001); runCurrent()
        assertNull(vm.state.value.account)
        assertEquals(RailwayQrUiPhase.WAITING_CONFIRMATION, vm.state.value.qrPhase)
        assertFalse(paths.any { it.contains("Passenger") })
        status = "2"
        advanceTimeBy(2001); runCurrent()
        assertEquals(RailwayQrUiPhase.CONNECTED, vm.state.value.qrPhase)
        assertNotNull(vm.state.value.account)
        assertNull(vm.state.value.qrChallenge)
        assertTrue(paths.contains("/otn/confirmPassenger/getPassengerDTOs"))
        assertFalse(paths.any { it.contains("confirmHB") || it.contains("submitOrder") })
    }

    @Test fun nativePasswordLoginNeedsExplicitInputAndConfirmedAccountBeforeShowingPassengers() = runTest(dispatcher) {
        val paths = mutableListOf<String>()
        val transport = object : RailwayTransport {
            override suspend fun post(path: String, fields: Map<String, String>): String {
                paths += path
                return when (path) {
                    "/passport/web/checkLoginVerify" -> """{"login_check_code":"0"}"""
                    "/passport/web/login", "/otn/uamauthclient" -> """{"result_code":0}"""
                    "/passport/web/auth/uamtk" -> """{"result_code":0,"newapptk":"synthetic-token"}"""
                    "/otn/login/checkUser" -> """{"status":true,"data":{"flag":true}}"""
                    "/otn/modifyUser/initQueryUserInfoApi" -> """{"status":true,"data":{"userDTO":{"loginUserDTO":{"user_name":"synthetic-account"}}}}"""
                    "/otn/confirmPassenger/getPassengerDTOs" -> """{"status":true,"data":{"normal_passengers":[]}}"""
                    else -> error("unexpected endpoint")
                }
            }
        }
        val vm = WaitlistViewModel(Source(), Draft(filters),
            accountService = RailwayAccountService(transport) { "opaque" },
            passwordLogin = RailwayPasswordLogin(transport))
        runCurrent()
        vm.openAccount()
        assertTrue(paths.isEmpty())
        val secret = "synthetic-password".toCharArray()
        vm.loginWithPassword("synthetic-account", secret)
        advanceUntilIdle()
        assertNotNull(vm.state.value.account)
        assertTrue(secret.all { it == '\u0000' })
        assertFalse(vm.state.value.toString().contains("synthetic-password"))
        assertFalse(vm.state.value.toString().contains("synthetic-account"))
        assertEquals(RailwayPasswordUiPhase.CONNECTED, vm.state.value.passwordPhase)
        assertFalse(paths.any { it.contains("qr") || it.contains("confirmHB") || it.contains("submitOrder") })
    }

    @Test fun draftDoesNotRelabelOldResultsAndHiddenSelectionsRemainExplicit() = runTest(dispatcher) {
        val source = Source()
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        vm.toggle(vm.state.value.choices.single())
        vm.edit()
        vm.updateFilters(filters.copy(seats = setOf(SeatType.FIRST)))
        assertEquals(filters, vm.state.value.applied)
        assertEquals(1, source.calls)
        vm.search(); advanceUntilIdle()
        assertEquals(1, vm.state.value.selected.size)
        assertEquals(1, vm.state.value.hiddenSelected.size)
        assertTrue(vm.state.value.choices.isEmpty())
        vm.showSelected()
        assertEquals(WaitlistPage.SELECTED, vm.state.value.page)
        vm.updateFilters(filters.copy(startDate = date.plusDays(1), endDate = date.plusDays(1)))
        vm.search(); advanceUntilIdle()
        assertTrue(vm.state.value.selected.isEmpty())
        assertTrue(vm.state.value.notice!!.contains("1"))
    }

    @Test fun failedQueryIsNotAnEmptySuccessAndRetryKeepsAppliedConditions() = runTest(dispatcher) {
        val source = Source().apply { result = QueryResult.Failure("网络异常") }
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        assertFalse(vm.state.value.queryComplete)
        assertEquals(1, vm.state.value.progress?.failureCount)
        assertEquals(filters, vm.state.value.applied)
        source.result = QueryResult.Success(emptyList(), Instant.now())
        vm.search(); advanceUntilIdle()
        assertTrue(vm.state.value.queryComplete)
        assertTrue(vm.state.value.choices.isEmpty())
    }

    @Test fun retryUsesAppliedSnapshotNotAnUnappliedDraft() = runTest(dispatcher) {
        val source = Source().apply { result = QueryResult.Failure("offline") }
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        vm.edit()
        vm.updateFilters(filters.copy(seats = setOf(SeatType.FIRST)))
        vm.showLastResults()
        vm.retryQuery(); advanceUntilIdle()
        assertEquals(filters, vm.state.value.applied)
        assertEquals(setOf(SeatType.FIRST), vm.state.value.draft.seats)
    }

    @Test fun cannotAddRestrictedItemAndSameDemandIsNeverAddedTwice() = runTest(dispatcher) {
        val source = Source()
        val vm = WaitlistViewModel(source, Draft(filters.copy(onlyAvailable = false)))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        val choice = vm.state.value.choices.first { it.seat == SeatType.SECOND }
        vm.toggle(choice); vm.toggle(choice)
        assertTrue(vm.state.value.selected.isEmpty())
        vm.toggle(choice.copy(trip = train.copy(trainId = "outside-query")))
        assertTrue(vm.state.value.selected.isEmpty())
        source.result = QueryResult.Success(listOf(train.copy(waitlistSeatLimit = "O")), Instant.now())
        vm.search(); advanceUntilIdle()
        vm.toggle(vm.state.value.choices.first { it.seat == SeatType.SECOND })
        assertTrue(vm.state.value.selected.isEmpty())
    }

    @Test fun backgroundCancelsAnonymousQueryAndDoesNotDisplayCompletedEmptyResult() = runTest(dispatcher) {
        val source = Source()
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.search(); runCurrent()
        vm.pauseForegroundWork(); advanceUntilIdle()
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.queryComplete)
        assertEquals(0, source.calls)
    }

    @Test fun unappliedDraftEditsDoNotEraseInitializationFailureFromOldResults() = runTest(dispatcher) {
        val source = object : TicketSource {
            override suspend fun initialize(): SourceInfo = throw IllegalStateException("官方查询入口不可用")
            override suspend fun query(unit: QueryUnit): QueryResult = error("not reached")
        }
        val vm = WaitlistViewModel(source, Draft(filters))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        vm.edit()
        vm.updateFilters(filters.copy(onlyAvailable = false))
        vm.back()
        assertEquals("官方查询入口不可用", vm.state.value.error)
        assertFalse(vm.state.value.queryComplete)
    }

    @Test fun invalidSaleDateKeepsOfficialDateRangeForCorrectionWithoutQuerying() = runTest(dispatcher) {
        val source = Source()
        val vm = WaitlistViewModel(source, Draft(filters.copy(startDate = date.plusDays(6), endDate = date.plusDays(6))))
        advanceUntilIdle()
        vm.search(); advanceUntilIdle()
        assertEquals(date.plusDays(5), vm.state.value.sourceInfo?.saleEnd)
        assertTrue(vm.state.value.error!!.contains("官方可查询日期"))
        assertEquals(0, source.calls)
    }
}
