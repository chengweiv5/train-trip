package cn.traintrip.core.waitlist

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RailwayAccountServiceTest {
    private class Transport : RailwayTransport {
        val paths = mutableListOf<String>()
        var loggedIn = true
        var account = "alice"
        var passengerResponse = """{"status":true,"data":{"normal_passengers":[{"passenger_name":"张测试","passenger_id_no":"123456789","passenger_id_type_code":"1","passenger_type":"1","passenger_type_name":"成人","allEncStr":"secret-do-not-log","passenger_uuid":"server-id","total_times":"99","is_buy_ticket":"Y"}]}}"""
        override suspend fun post(path: String, fields: Map<String, String>): String {
            paths += path
            return when (path) {
                "/otn/login/checkUser" -> """{"status":true,"data":{"flag":$loggedIn}}"""
                "/otn/modifyUser/initQueryUserInfoApi" -> """{"status":true,"data":{"userDTO":{"loginUserDTO":{"user_name":"$account"}}}}"""
                "/otn/confirmPassenger/getPassengerDTOs" -> passengerResponse
                else -> error("Unexpected $path")
            }
        }
    }
    @Test fun verifiedLoginLoadsRealPassengerButNeverExposesCredentialsInUiOrLogs() = runTest {
        val transport = Transport()
        val service = RailwayAccountService(transport) { "local-" + it.hashCode() }
        val account = service.refresh()
        assertEquals(1, account.passengers.size)
        assertEquals("张**", account.passengers.single().displayName)
        assertFalse(account.toString().contains("secret-do-not-log"))
        assertFalse(account.toString().contains("123456789"))
        assertTrue(account.passengers.single().selectable)
        assertTrue(transport.paths.none { it.contains("submit") || it.contains("confirmHB") })
    }

    @Test fun expiredLoginDoesNotReadPassengersAndDoesNotReturnStaleIdentity() = runTest {
        val transport = Transport()
        val service = RailwayAccountService(transport) { it.hashCode().toString() }
        service.refresh()
        transport.loggedIn = false
        transport.paths.clear()
        try { service.refresh(); fail("login must fail") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.LOGIN_REQUIRED, e.reason) }
        assertFalse(transport.paths.contains("/otn/confirmPassenger/getPassengerDTOs"))
    }

    @Test fun verifiedAdultsCanBeSelectedWithoutAnIsBuyTicketYFlag() = runTest {
        for (replacement in listOf("\"is_buy_ticket\":\"N\"", "\"unused\":\"\"")) {
            val transport = Transport().apply {
                passengerResponse = passengerResponse.replace("\"is_buy_ticket\":\"Y\"", replacement)
            }
            val service = RailwayAccountService(transport) { "local-" + it.hashCode() }
            val account = service.refresh()
            val person = account.passengers.single()
            assertTrue("Official waitlist selection does not use this flag as an eligibility gate", person.selectable)
            service.verifyBinding(WaitlistBinding(account.reference,
                listOf(WaitlistPassenger(person.reference, person.ticketType))))
            assertTrue(transport.paths.none { it.contains("submit") || it.contains("confirmHB") })
        }
    }

    @Test fun removingTheIncorrectFlagGateDoesNotEnableUnverifiedOrIncompletePassengers() = runTest {
        for (replacement in listOf(
            "\"total_times\":\"99\"" to "\"total_times\":\"92\"",
            "\"passenger_type\":\"1\"" to "\"passenger_type\":\"3\"",
            "\"allEncStr\":\"secret-do-not-log\"" to "\"allEncStr\":\"\"",
            "\"passenger_id_no\":\"123456789\"" to "\"passenger_id_no\":\"\"",
        )) {
            val transport = Transport().apply {
                passengerResponse = passengerResponse
                    .replace("\"is_buy_ticket\":\"Y\"", "\"is_buy_ticket\":\"N\"")
                    .replace(replacement.first, replacement.second)
            }
            val service = RailwayAccountService(transport) { it.hashCode().toString() }
            assertFalse(service.refresh().passengers.single().selectable)
        }
    }

    @Test fun unsupportedTicketTypesAreVisibleButNotSelectableAndInvalidSchemaFailsClosed() = runTest {
        val transport = Transport()
        val service = RailwayAccountService(transport) { it.hashCode().toString() }
        transport.passengerResponse = transport.passengerResponse.replace("\"passenger_type\":\"1\"", "\"passenger_type\":\"3\"")
        assertFalse(service.refresh().passengers.single().selectable)
        transport.passengerResponse = """{"status":true,"data":{"passengers":[]}}"""
        try { service.refresh(); fail("unknown schema must not look like no passengers") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.SCHEMA_CHANGED, e.reason) }
    }

    @Test fun accountChangeCannotReuseTheOtherAccountsPassengerBinding() = runTest {
        val transport = Transport()
        val service = RailwayAccountService(transport) { it.hashCode().toString() }
        val a = service.refresh()
        val binding = WaitlistBinding(a.reference, a.passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
        service.verifyBinding(binding)
        transport.account = "bob"
        try { service.verifyBinding(binding); fail("must not reuse binding") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.IDENTITY_CHANGED, e.reason) }
    }
}
