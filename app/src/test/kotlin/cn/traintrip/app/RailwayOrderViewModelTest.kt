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
class RailwayOrderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalog = StationCatalog.bundled()
    private val date = today().plusDays(2)
    private val from = catalog.byCode.getValue("VNP")
    private val to = catalog.byCode.getValue("JGK")
    private val filters = WaitlistFilters(from.cityId, to.cityId, setOf("VNP"), setOf("JGK"), date, date,
        seats = setOf(SeatType.SECOND))
    private class Store : WaitlistStore {
        var value: WaitlistState? = null
        override suspend fun load() = value
        override suspend fun save(state: WaitlistState) { value = state }
    }
    private inner class Fixture {
        val store = Store()
        var submits = 0
        val diagnostics = mutableListOf<String>()
        val source = object : TicketSource {
            override suspend fun initialize() = SourceInfo("query", today(), date.plusDays(5), catalog, Instant.now())
            override suspend fun query(unit: QueryUnit) = QueryResult.Success(listOf(Trip(date, "id-37", "G37",
                from, to, LocalTime.of(8, 0), LocalTime.of(10, 0), 120, SaleState.OPEN, "",
                mapOf(SeatType.SECOND to TicketParser.availability("无")), Instant.now(), true,
                waitlistContextAvailable = true)), Instant.now())
        }
        val transport = object : RailwayTransport {
            override suspend fun get(path: String, fields: Map<String, String>): String = when (path) {
                "/otn/leftTicket/init" -> "var CLeftTicketUrl = 'leftTicket/queryG';"
                else -> {
                    val row = MutableList(49) { "" }
                    mapOf(0 to "synthetic-secret", 2 to "id-37", 3 to "G37", 6 to "VNP", 7 to "JGK",
                        8 to "08:00", 11 to "Y", 30 to "无", 37 to "1").forEach { (i, v) -> row[i] = v }
                    """{"status":true,"data":{"result":["${row.joinToString("|")}"]}}"""
                }
            }
            override suspend fun post(path: String, fields: Map<String, String>): String = when (path) {
                "/otn/login/checkUser" -> """{"status":true,"data":{"flag":true}}"""
                "/otn/modifyUser/initQueryUserInfoApi" -> """{"status":true,"data":{"userDTO":{"loginUserDTO":{"user_name":"synthetic-user"}}}}"""
                "/otn/confirmPassenger/getPassengerDTOs" -> """{"status":true,"data":{"normal_passengers":[{"passenger_name":"张测试","passenger_id_no":"123456789","passenger_id_type_code":"1","passenger_type":"1","passenger_type_name":"成人","allEncStr":"synthetic-passenger-secret","passenger_uuid":"synthetic-uuid","total_times":"99","is_buy_ticket":"Y"}]}}"""
                "/otn/afterNate/submitOrderRequest" -> """{"status":true,"data":{"flag":true}}"""
                "/otn/afterNate/passengerInitApi" -> """{"status":true,"data":{"hbTrainList":[{"train_no":"id-37","station_train_code":"G37","train_date":"$date","from_station_telecode":"VNP","to_station_telecode":"JGK","seat_type_code":"O","start_time":"08:00"}],"hb_passenger_max_num":19,"if_check_slide_passcode":"0","jzdhDiffSelect":[360,60]}}"""
                "/otn/afterNate/confirmHB" -> {
                    assertNotNull("must persist pending before sending", store.value?.pending)
                    submits++
                    """{"status":true,"data":{"flag":true,"isAsync":true}}"""
                }
                "/otn/afterNateOrder/queryUnHonourHOrder" -> """{"status":true,"data":{"list":[]}}"""
                else -> """{"status":true,"data":{"flag":true,"status":"0"}}"""
            }
        }
        val accounts = RailwayAccountService(transport) { it.hashCode().toString() }
        val gateway = RailwayWaitlistGateway(transport, accounts)
        val vm = WaitlistViewModel(source, object : WaitlistDraftStore {
            override suspend fun load() = filters
            override suspend fun save(filters: WaitlistFilters) {}
        }, WaitlistCoordinator(gateway, store), accounts,
            accountDiagnostic = { status, _ -> diagnostics += status }, orders = gateway)
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private fun TestScope.prepare(f: Fixture) {
        advanceUntilIdle()
        f.vm.search(); advanceUntilIdle()
        f.vm.toggle(f.vm.state.value.choices.single())
        f.vm.refreshAccount(); advanceUntilIdle()
        f.vm.togglePassenger(f.vm.state.value.account!!.passengers.single().reference)
        f.vm.reviewOrder()
        f.vm.prepareOrder(); advanceUntilIdle()
        assertNotNull(f.vm.state.value.orderPreview)
    }

    @Test fun userMustSelectDeadlineVerifyThenExplicitlyConfirmAndPendingIsDurable() = runTest(dispatcher) {
        val f = Fixture()
        prepare(f)
        assertEquals(0, f.submits)
        f.vm.confirmOrder(); advanceUntilIdle()
        assertEquals(0, f.submits)
        f.vm.selectDeadline(60)
        f.vm.beginRiskVerification(); advanceUntilIdle()
        val id = f.vm.state.value.orderPreview!!.id
        f.vm.riskVerificationCompleted(RailwayRiskProof(id, "synthetic-runtime", "", "", Instant.now()))
        assertEquals(0, f.submits)
        f.vm.confirmOrder(); f.vm.confirmOrder(); advanceUntilIdle()
        assertEquals(1, f.submits)
        assertEquals(WaitlistPhase.CHECKING_ORDER, f.vm.state.value.operation?.phase)
        f.vm.checkPendingOrder(); advanceUntilIdle()
        assertEquals(1, f.submits)
        assertFalse(f.vm.state.value.toString().contains("synthetic-runtime"))
        assertFalse(f.vm.state.value.toString().contains("123456789"))
    }

    @Test fun backgroundAndPassengerChangesInvalidatePreviewAndLateProofWithoutSubmitting() = runTest(dispatcher) {
        val f = Fixture()
        prepare(f)
        f.vm.selectDeadline(60)
        f.vm.beginRiskVerification(); advanceUntilIdle()
        val proof = RailwayRiskProof(f.vm.state.value.orderPreview!!.id, "synthetic-runtime", "", "", Instant.now())
        f.vm.pauseForegroundWork(); advanceUntilIdle()
        f.vm.riskVerificationCompleted(proof)
        f.vm.confirmOrder(); advanceUntilIdle()
        assertNull(f.vm.state.value.orderPreview)
        assertFalse(f.vm.state.value.riskVerified)
        assertEquals(0, f.submits)
        f.vm.prepareOrder(); advanceUntilIdle()
        assertNotNull(f.vm.state.value.orderPreview)
        f.vm.togglePassenger(f.vm.state.value.account!!.passengers.single().reference)
        assertNull(f.vm.state.value.orderPreview)
    }

    @Test fun failedOfficialVerificationReportsOnlyStatusAndNeverSendsAnOrder() = runTest(dispatcher) {
        val f = Fixture()
        prepare(f)
        f.vm.selectDeadline(60)
        f.vm.beginRiskVerification(); advanceUntilIdle()
        f.vm.riskVerificationFailed()
        f.vm.confirmOrder(); advanceUntilIdle()
        assertEquals("ORDER_VERIFICATION_FAILED", f.diagnostics.last())
        assertFalse(f.vm.state.value.riskVerified)
        assertFalse(f.vm.state.value.riskVisible)
        assertEquals(0, f.submits)
        assertNull(f.store.value)
    }
}
