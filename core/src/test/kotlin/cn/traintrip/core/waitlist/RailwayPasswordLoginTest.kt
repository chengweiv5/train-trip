package cn.traintrip.core.waitlist

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RailwayPasswordLoginTest {
    private class Transport : RailwayTransport {
        var mode = "0"
        var loginCode = "0"
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun post(path: String, fields: Map<String, String>): String {
            calls += path to fields
            return when (path) {
                "/passport/web/checkLoginVerify" -> """{"login_check_code":"$mode"}"""
                "/passport/web/getMessageCode" -> """{"result_code":"0"}"""
                "/passport/web/login" -> """{"result_code":$loginCode}"""
                "/passport/web/auth/uamtk" -> """{"result_code":0,"newapptk":"synthetic-token"}"""
                "/otn/uamauthclient" -> """{"result_code":0}"""
                else -> error("unexpected endpoint")
            }
        }
    }

    @Test fun passwordEncodingMatchesOfficialScriptSyntheticVectors() {
        assertEquals("@NjQc0TZhH8uX3oz7y3tDIz3TFq/xcowVYjwPOcgTuic=",
            RailwayPasswordEncoding.encode("TestPassword123!".toCharArray()))
        assertEquals("@Kl75cikg3jE+jKIcAfqCQA==",
            RailwayPasswordEncoding.encode("测试密码123".toCharArray()))
        assertEquals("@CVPRqtdzdC5zVLgdBgwQGz3TFq/xcowVYjwPOcgTuic=",
            RailwayPasswordEncoding.encode("1234567890abcdef".toCharArray()))
    }

    @Test fun loginWithoutChallengeOnlyUsesEncryptedPasswordAndExchangesOwnSession() = runTest {
        val transport = Transport()
        val login = RailwayPasswordLogin(transport)
        val password = "TestPassword123!".toCharArray()
        assertEquals(RailwayPasswordResult.SESSION_ESTABLISHED, login.login("test-user", password))
        assertTrue(password.all { it == '\u0000' })
        val fields = transport.calls.single { it.first == "/passport/web/login" }.second
        assertEquals("@NjQc0TZhH8uX3oz7y3tDIz3TFq/xcowVYjwPOcgTuic=", fields["password"])
        assertEquals("", fields["checkMode"])
        assertFalse(transport.calls.toString().contains("TestPassword123!"))
        assertEquals("/otn/uamauthclient", transport.calls.last().first)
    }

    @Test fun smsIsExplicitAccountBoundAndRateLimited() = runTest {
        val transport = Transport().apply { mode = "3" }
        var now = 1000L
        val login = RailwayPasswordLogin(transport) { now }
        assertEquals(RailwayPasswordResult.SMS_REQUIRED, login.login("alice", "secret1".toCharArray()))
        assertFalse(transport.calls.any { it.first == "/passport/web/getMessageCode" })
        login.sendSms("alice", "123X")
        assertEquals(mapOf("appid" to "otn", "username" to "alice", "castNum" to "123X"),
            transport.calls.last().second)
        try { login.sendSms("alice", "123X"); fail("must not resend immediately") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.SMS_COOLDOWN, e.reason) }
        assertEquals(RailwayPasswordResult.SMS_REQUIRED, login.login("bob", "secret1".toCharArray(), "123456"))
        assertFalse(transport.calls.any { it.first == "/passport/web/login" })
        assertEquals(RailwayPasswordResult.SESSION_ESTABLISHED, login.login("alice", "secret1".toCharArray(), "123456"))
        assertEquals("0", transport.calls.single { it.first == "/passport/web/login" }.second["checkMode"])
        now += 60001
        login.sendSms("alice", "123X")
        now += 300001
        assertEquals(RailwayPasswordResult.SMS_REQUIRED, login.login("alice", "secret1".toCharArray(), "123456"))
    }

    @Test fun slideOnlyAndUnknownModesNeverFallBackToUnverifiedSmsOrLogin() = runTest {
        val transport = Transport().apply { mode = "2" }
        val login = RailwayPasswordLogin(transport)
        assertEquals(RailwayPasswordResult.SLIDE_REQUIRED, login.login("alice", "secret1".toCharArray()))
        try { login.sendSms("alice", "1234"); fail("slide-only is not SMS") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.VERIFICATION_REQUIRED, e.reason) }
        transport.mode = "999"
        try { login.login("alice", "secret1".toCharArray()); fail("unknown mode must stop") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.VERIFICATION_REQUIRED, e.reason) }
        assertTrue(transport.calls.all { it.first == "/passport/web/checkLoginVerify" })
    }

    @Test fun rejectedPasswordDoesNotExchangeSessionAndClearsCallerBuffer() = runTest {
        val transport = Transport().apply { loginCode = "1" }
        val login = RailwayPasswordLogin(transport)
        val password = "secret1".toCharArray()
        try { login.login("alice", password); fail("must not accept rejected login") }
        catch (e: RailwayException) { assertEquals(RailwayFailure.LOGIN_REJECTED, e.reason) }
        assertTrue(password.all { it == '\u0000' })
        assertFalse(transport.calls.any { it.first.contains("uam") })
    }
}
