package cn.traintrip.core.waitlist

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RailwayWaitlistGatewayTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val demand = WaitlistDemand(LocalDate.parse("2026-10-02"), "internal-37", "VNP", "JGK", "O")
    private class Transport : RailwayTransport {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        var confirm = """{"status":true,"data":{"flag":true,"isAsync":true}}"""
        var queue = """{"status":true,"data":{"flag":true,"isAsync":true,"status":"0"}}"""
        var orders = """{"status":true,"data":{"list":[]}}"""
        var account = "synthetic-user"
        var restriction = ""
        var failSubmit = false
        var firstSeat = false
        var init = """{"status":true,"data":{"hbTrainList":[{"train_no":"internal-37","station_train_code":"G37","train_date":"2026-10-02","from_station_telecode":"VNP","to_station_telecode":"JGK","seat_type_code":"O","start_time":"08:00","isForegin":"0"}],"hb_passenger_max_num":19,"if_check_slide_passcode":"0","checkcode":"0","jzdhDiffSelect":[360,120,60]}}"""
        override suspend fun get(path: String, fields: Map<String, String>): String {
            calls += path to fields
            return when (path) {
                "/otn/leftTicket/init" -> """var CLeftTicketUrl = 'leftTicket/queryG';"""
                "/otn/leftTicket/queryG" -> {
                    val row = MutableList(49) { "" }
                    mapOf(0 to "synthetic-secret", 2 to "internal-37", 3 to "G37", 6 to "VNP",
                        7 to "JGK", 8 to "08:00", 11 to "Y", 30 to "无", 37 to "1").forEach { (i, v) -> row[i] = v }
                    row[38] = restriction
                    if (firstSeat) row[31] = "无"
                    """{"status":true,"data":{"result":["${row.joinToString("|")}"]}}"""
                }
                else -> error("Unexpected GET $path")
            }
        }
        override suspend fun post(path: String, fields: Map<String, String>): String {
            calls += path to fields
            return when (path) {
                "/otn/login/checkUser" -> """{"status":true,"data":{"flag":true}}"""
                "/otn/modifyUser/initQueryUserInfoApi" -> """{"status":true,"data":{"userDTO":{"loginUserDTO":{"user_name":"$account"}}}}"""
                "/otn/confirmPassenger/getPassengerDTOs" -> """{"status":true,"data":{"normal_passengers":[{"passenger_name":"张测试","passenger_id_no":"123456789","passenger_id_type_code":"1","passenger_type":"1","passenger_type_name":"成人","allEncStr":"synthetic-passenger-secret","passenger_uuid":"synthetic-uuid","total_times":"99","is_buy_ticket":"Y","isOldThan60":"N"}]}}"""
                "/otn/afterNate/submitOrderRequest" -> """{"status":true,"data":{"flag":true}}"""
                "/otn/afterNate/passengerInitApi" -> init
                "/otn/afterNate/confirmHB" -> if (failSubmit) throw java.io.IOException("synthetic transport failure") else confirm
                "/otn/afterNate/queryQueue", "/otn/afterNateOrder/queryQueue" -> queue
                "/otn/afterNateOrder/queryUnHonourHOrder" -> orders
                else -> error("Unexpected POST $path")
            }
        }
    }

    @Test fun multiSeatSelectionIsInitializedAndSubmittedAsOneCompleteCombination() = runTest {
        val t = Transport().apply {
            firstSeat = true
            val train = """{"train_no":"internal-37","station_train_code":"G37","train_date":"2026-10-02","from_station_telecode":"VNP","to_station_telecode":"JGK","seat_type_code":"M","start_time":"08:00","isForegin":"0"}"""
            init = init.replace("}],\"hb_passenger", "},$train],\"hb_passenger")
        }
        val a = RailwayAccountService(t) { it.hashCode().toString() }
        val account = a.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val s = RailwayWaitlistGateway(t, a, Clock.fixed(now, ZoneOffset.UTC))
        val demands = listOf(demand.copy(seatCode = "M"), demand)
        val p = s.prepare(binding, demands)
        assertEquals(demands, p.trains.map { it.demand })
        assertEquals("synthetic-secret#M|synthetic-secret#O|",
            t.calls.single { it.first.endsWith("/submitOrderRequest") }.second["secretList"])
        val r = s.request(p.id, 60)
        s.authorize(p.id, r, 60, RailwayRiskProof(p.id, "synthetic-runtime", "", "", now))
        assertEquals(WaitlistSubmissionResult.Unknown, s.submit(WaitlistFlow.start(r, p.limits, now).state.pending!!))
        assertEquals(1, t.calls.count { it.first.endsWith("/confirmHB") })
    }

    @Test fun preparationUsesFreshSameSessionContextAndServerDeadlineOptionsWithoutPlacingOrder() = runTest {
        val transport = Transport()
        val accountService = RailwayAccountService(transport) { it.hashCode().toString() }
        val account = accountService.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val service = RailwayWaitlistGateway(transport, accountService, Clock.fixed(now, ZoneOffset.UTC))
        val preview = service.prepare(binding, listOf(demand))
        assertEquals(listOf(360, 120, 60), preview.deadlineMinutes)
        assertEquals(19, preview.limits.maxPassengers)
        assertEquals("G37", preview.trains.single().trainCode)
        assertEquals("synthetic-secret#O|", transport.calls.single {
            it.first == "/otn/afterNate/submitOrderRequest"
        }.second["secretList"])
        assertTrue(transport.calls.none { it.first == "/otn/afterNate/confirmHB" })
        assertFalse(preview.toString().contains("synthetic-secret"))
        assertFalse(preview.toString().contains("123456789"))
    }

    @Test fun onlyExplicitAuthorizationWithFreshRuntimeProofCanSubmitOnceAndEmptyOrdersStayUnresolved() = runTest {
        val transport = Transport()
        val accounts = RailwayAccountService(transport) { it.hashCode().toString() }
        val account = accounts.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val service = RailwayWaitlistGateway(transport, accounts, Clock.fixed(now, ZoneOffset.UTC))
        val preview = service.prepare(binding, listOf(demand))
        val request = service.request(preview.id, 60)
        val attempt = WaitlistFlow.start(request, preview.limits, now).state.pending!!
        assertTrue(service.submit(attempt) is WaitlistSubmissionResult.Rejected)
        assertTrue(transport.calls.none { it.first.endsWith("/confirmHB") })
        service.authorize(preview.id, request, 60, RailwayRiskProof(preview.id, "synthetic-runtime", "", "", now))
        assertEquals(WaitlistSubmissionResult.Unknown, service.submit(attempt))
        assertEquals(WaitlistOrderResult.Unresolved, service.queryOrder(attempt))
        assertEquals(WaitlistSubmissionResult.Unknown, service.submit(attempt))
        val submissions = transport.calls.filter { it.first.endsWith("/confirmHB") }
        assertEquals(1, submissions.size)
        assertEquals("1#张测试#1#123456789#synthetic-passenger-secret#0;", submissions.single().second["passengerInfo"])
        assertEquals("60", submissions.single().second["realize_limit_time_diff"])
        assertEquals("synthetic-runtime", submissions.single().second["encryptedData"])
        assertEquals("N", submissions.single().second["add_train_flag"])
    }

    @Test fun authorizationRejectsRequestsNotIssuedForTheCurrentPreview() = runTest {
        val transport = Transport()
        val accounts = RailwayAccountService(transport) { it.hashCode().toString() }
        val account = accounts.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val service = RailwayWaitlistGateway(transport, accounts, Clock.fixed(now, ZoneOffset.UTC))
        val preview = service.prepare(binding, listOf(demand))
        val issued = service.request(preview.id, 60)
        val proof = RailwayRiskProof(preview.id, "synthetic-runtime", "", "", now)
        for (changed in listOf(issued.copy(orderContext = null), issued.copy(id = "not-issued"),
            issued.copy(orderContext = issued.orderContext!!.copy(authorizedAt = now.minusSeconds(30))))) {
            try {
                service.authorize(preview.id, changed, 60, proof)
                fail("An altered request must not authorize a production submission")
            } catch (_: RailwayOrderException) {}
        }
        assertTrue(transport.calls.none { it.first.endsWith("/confirmHB") })
        service.authorize(preview.id, issued, 60, proof)
        assertEquals(WaitlistSubmissionResult.Unknown,
            service.submit(WaitlistFlow.start(issued, preview.limits, now).state.pending!!))
    }

    @Test fun consumedPreviewCannotIssueANewRequestWithTheSameVerification() = runTest {
        val transport = Transport()
        val accounts = RailwayAccountService(transport) { it.hashCode().toString() }
        val account = accounts.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val service = RailwayWaitlistGateway(transport, accounts, Clock.fixed(now, ZoneOffset.UTC))
        val preview = service.prepare(binding, listOf(demand))
        val request = service.request(preview.id, 60)
        service.authorize(preview.id, request, 60, RailwayRiskProof(preview.id, "synthetic-runtime", "", "", now))
        service.submit(WaitlistFlow.start(request, preview.limits, now).state.pending!!)
        try {
            service.request(preview.id, 60)
            fail("Sent preview must be consumed even when the network outcome is unresolved")
        } catch (_: RailwayOrderException) {}
        assertEquals(1, transport.calls.count { it.first.endsWith("/confirmHB") })
    }

    @Test fun orderRecoveryRequiresExactTrainSeatPassengerAndTimeAndSurvivesGatewayRecreation() = runTest {
        val transport = Transport()
        val accounts = RailwayAccountService(transport) { it.hashCode().toString() }
        val account = accounts.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val service = RailwayWaitlistGateway(transport, accounts, Clock.fixed(now, ZoneOffset.UTC))
        val preview = service.prepare(binding, listOf(demand))
        val request = service.request(preview.id, 60)
        val attempt = WaitlistFlow.start(request, preview.limits, now).state.pending!!
        val encoded = WaitlistSnapshotCodec.encode(WaitlistFlow.start(request, preview.limits, now).state)
        val restored = WaitlistSnapshotCodec.decode(encoded).pending!!
        assertEquals(attempt, restored)
        val order = """{"reserve_no":"SYNTHETIC-ORDER","reserve_time":"2026-09-30 20:00:01","realize_limit_time":"2026-10-02 07:00:00","needs":[{"board_train_code":"G37","train_date":"2026-10-02","from_tele_code":"VNP","to_tele_code":"JGK","seat_name":"二等座"}],"passengers":[{"passenger_name":"张测试","passenger_id_name":"居民身份证","passenger_id_no":"123456789","ticket_type":"成人票"}]}"""
        transport.orders = """{"status":true,"data":{"list":[$order]}}"""
        val reopened = RailwayWaitlistGateway(transport, accounts, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
        assertEquals(WaitlistOrderResult.Created(WaitlistOrder("SYNTHETIC-ORDER", listOf(demand))), reopened.queryOrder(restored))
        transport.orders = transport.orders.replace("123456789", "987654321")
        assertEquals(WaitlistOrderResult.Unresolved, reopened.queryOrder(restored))
    }

    @Test fun expiredProofCannotSendAndLateUnrelatedOrderIsNotClaimed() = runTest {
        val t = Transport()
        val a = RailwayAccountService(t) { it.hashCode().toString() }
        val account = a.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val s = RailwayWaitlistGateway(t, a, Clock.fixed(now, ZoneOffset.UTC))
        val p = s.prepare(binding, listOf(demand))
        val r = s.request(p.id, 60)
        try {
            s.authorize(p.id, r, 60, RailwayRiskProof(p.id, "synthetic-runtime", "", "", now.minusSeconds(91)))
            fail("stale proof cannot authorize")
        } catch (_: RailwayOrderException) {}
        val attempt = WaitlistFlow.start(r, p.limits, now).state.pending!!
        t.orders = """{"status":true,"data":{"list":[{"reserve_no":"UNRELATED","reserve_time":"2026-09-30 21:00:00","realize_limit_time":"2026-10-02 07:00:00","needs":[{"board_train_code":"G37","train_date":"2026-10-02","from_tele_code":"VNP","to_tele_code":"JGK","seat_name":"二等座"}],"passengers":[{"passenger_name":"张测试","passenger_id_name":"居民身份证","passenger_id_no":"123456789","ticket_type":"成人票"}]}]}}"""
        val reopened = RailwayWaitlistGateway(t, a, Clock.fixed(now.plusSeconds(3605), ZoneOffset.UTC))
        assertEquals(WaitlistOrderResult.Unresolved, reopened.queryOrder(attempt))
    }

    @Test fun platformMismatchRestrictionsAndVerificationNeverBecomeSilentPartialSubmission() = runTest {
        for (change in listOf<(Transport) -> Unit>(
            { it.restriction = "O" },
            { it.init = it.init.replace("internal-37", "different-train") },
            { it.init = it.init.replace("\"checkcode\":\"0\"", "\"checkcode\":\"94\"") },
            { it.init = it.init.replace("\"if_check_slide_passcode\":\"0\"", "\"if_check_slide_passcode\":\"1\"") },
            { it.init = it.init.replace("\"hb_passenger_max_num\":19", "\"hb_passenger_max_num\":0") },
        )) {
            val t = Transport().also(change)
            val a = RailwayAccountService(t) { it.hashCode().toString() }
            val account = a.refresh()
            val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
            val s = RailwayWaitlistGateway(t, a, Clock.fixed(now, ZoneOffset.UTC))
            try { s.prepare(binding, listOf(demand)); fail("unsafe preparation must stop") }
            catch (_: IllegalStateException) {}
            assertTrue(t.calls.none { it.first.endsWith("/confirmHB") })
        }
    }

    @Test fun timedOutSubmissionAndAmbiguousRejectionNeverReplayOrClaimFailure() = runTest {
        for (timeout in listOf(true, false)) {
            val t = Transport().apply {
                failSubmit = timeout
                confirm = """{"status":true,"data":{"flag":false,"msg":"候补人数过多，包含乘客原始信息"}}"""
            }
            val a = RailwayAccountService(t) { it.hashCode().toString() }
            val account = a.refresh()
            val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
            val s = RailwayWaitlistGateway(t, a, Clock.fixed(now, ZoneOffset.UTC))
            val p = s.prepare(binding, listOf(demand))
            val r = s.request(p.id, 60)
            s.authorize(p.id, r, 60, RailwayRiskProof(p.id, "synthetic-runtime", "", "", now))
            val attempt = WaitlistFlow.start(r, p.limits, now).state.pending!!
            assertEquals(WaitlistSubmissionResult.Unknown, s.submit(attempt))
            assertEquals(WaitlistSubmissionResult.Unknown, s.submit(attempt))
            assertEquals(1, t.calls.count { it.first.endsWith("/confirmHB") })
            assertEquals(WaitlistOrderResult.Unresolved, s.queryOrder(attempt))
        }
    }

    @Test fun switchedAccountAndMissingRuntimeProofNeverSendTheOrder() = runTest {
        val t = Transport()
        val a = RailwayAccountService(t) { it.hashCode().toString() }
        val account = a.refresh()
        val binding = WaitlistBinding(account.reference, account.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        val s = RailwayWaitlistGateway(t, a, Clock.fixed(now, ZoneOffset.UTC))
        val p = s.prepare(binding, listOf(demand))
        val r = s.request(p.id, 60)
        try { s.authorize(p.id, r, 60, RailwayRiskProof(p.id, "", "", "", now)); fail("empty runtime must stop") }
        catch (_: RailwayOrderException) {}
        s.authorize(p.id, r, 60, RailwayRiskProof(p.id, "synthetic-runtime", "", "", now))
        t.account = "different-user"
        assertEquals(WaitlistSubmissionResult.AuthenticationRequired, s.submit(WaitlistFlow.start(r, p.limits, now).state.pending!!))
        assertTrue(t.calls.none { it.first.endsWith("/confirmHB") })
    }
}
