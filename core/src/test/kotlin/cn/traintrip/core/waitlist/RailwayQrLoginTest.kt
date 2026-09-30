package cn.traintrip.core.waitlist

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RailwayQrLoginTest {
    private class Transport : RailwayTransport {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        var status = "0"
        var tokenCode = "0"
        override suspend fun post(path: String, fields: Map<String, String>): String {
            calls += path to fields
            return when (path) {
                "/passport/web/create-qr64" -> """{"result_code":"0","uuid":"challenge-secret","image":"aW1hZ2U="}"""
                "/passport/web/checkqr" -> """{"result_code":"$status"}"""
                "/passport/web/auth/uamtk" -> """{"result_code":$tokenCode,"newapptk":"exchange-secret"}"""
                "/otn/uamauthclient" -> """{"result_code":0}"""
                else -> error("unexpected endpoint")
            }
        }
    }

    @Test fun scannedQrDoesNotExchangeSessionUntilUserActuallyAuthorizes() = runTest {
        val transport = Transport()
        val login = RailwayQrLogin(transport)
        val challenge = login.create()
        assertEquals("aW1hZ2U=", challenge.imageBase64)
        assertFalse(challenge.toString().contains("challenge-secret"))
        assertEquals(RailwayQrStatus.WAITING_SCAN, login.poll(challenge))
        transport.status = "1"
        assertEquals(RailwayQrStatus.WAITING_CONFIRMATION, login.poll(challenge))
        assertFalse(transport.calls.any { it.first.contains("uam") })
        transport.status = "2"
        assertEquals(RailwayQrStatus.SESSION_ESTABLISHED, login.poll(challenge))
        assertEquals(listOf("/passport/web/auth/uamtk", "/otn/uamauthclient"),
            transport.calls.takeLast(2).map { it.first })
        assertEquals(mapOf("tk" to "exchange-secret"), transport.calls.last().second)
        val count = transport.calls.size
        assertEquals(RailwayQrStatus.SESSION_ESTABLISHED, login.poll(challenge))
        assertEquals(count, transport.calls.size)
    }

    @Test fun newQrInvalidatesOldQrAndExpiredQrNeverChecksOrExchanges() = runTest {
        val transport = Transport()
        var now = 1000L
        val login = RailwayQrLogin(transport) { now }
        val first = login.create()
        val second = login.create()
        val count = transport.calls.size
        assertEquals(RailwayQrStatus.EXPIRED, login.poll(first))
        now += 120_001
        assertEquals(RailwayQrStatus.EXPIRED, login.poll(second))
        assertEquals(count, transport.calls.size)
    }

    @Test fun additionalVerificationDoesNotBecomeSessionOrReplayTokenExchange() = runTest {
        val transport = Transport().apply { status = "2"; tokenCode = "91" }
        val login = RailwayQrLogin(transport)
        val qr = login.create()
        try { login.poll(qr); fail("must stop for additional verification") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.VERIFICATION_REQUIRED, e.reason) }
        assertFalse(transport.calls.any { it.first == "/otn/uamauthclient" })
        val count = transport.calls.size
        assertEquals(RailwayQrStatus.EXPIRED, login.poll(qr))
        assertEquals(count, transport.calls.size)
    }

    @Test fun unknownStatusStopsInsteadOfInterpretingItAsAuthorization() = runTest {
        val transport = Transport().apply { status = "999" }
        val login = RailwayQrLogin(transport)
        val qr = login.create()
        try { login.poll(qr); fail("unknown status must not authorize") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.VERIFICATION_REQUIRED, e.reason) }
        assertFalse(transport.calls.any { it.first.contains("uam") })
    }
}
