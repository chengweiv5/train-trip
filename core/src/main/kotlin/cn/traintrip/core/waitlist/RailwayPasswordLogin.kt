package cn.traintrip.core.waitlist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.crypto.engines.SM4Engine
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.params.KeyParameter

/** Official client-side encoding, not a replacement for TLS or a secret key managed by this app. */
object RailwayPasswordEncoding {
    fun encode(password: CharArray): String {
        val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val input = ByteArray(encoded.remaining()).also { encoded.get(it) }
        val cipher = PaddedBufferedBlockCipher(SM4Engine(), PKCS7Padding())
        cipher.init(true, KeyParameter("tiekeyuankp12306".toByteArray(StandardCharsets.US_ASCII)))
        val output = ByteArray(cipher.getOutputSize(input.size))
        try {
            val count = cipher.processBytes(input, 0, input.size, output, 0)
            val total = count + cipher.doFinal(output, count)
            val result = output.copyOf(total)
            return try { "@" + Base64.getEncoder().encodeToString(result) }
            finally { result.fill(0) }
        } finally {
            input.fill(0)
            output.fill(0)
            if (encoded.hasArray()) encoded.array().fill(0)
            cipher.reset()
        }
    }
}

enum class RailwayPasswordResult { SMS_REQUIRED, SLIDE_REQUIRED, SESSION_ESTABLISHED }

/** Only explicitly invoked calls; never automatically sends an SMS, retries login or places orders. */
class RailwayPasswordLogin(
    private val transport: RailwayTransport,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var smsAccount: String? = null
    private var smsSentAt: Long? = null
    private var lastSmsAttempt: Long? = null

    suspend fun login(username: String, password: CharArray, smsCode: String = ""): RailwayPasswordResult {
        try {
            return mutex.withLock {
                val account = validatedAccount(username)
                if (password.size !in 6..128) throw RailwayException(RailwayFailure.INVALID_LOGIN_INPUT)
                val mode = verificationMode(account)
                if (mode == "2") return@withLock RailwayPasswordResult.SLIDE_REQUIRED
                if (mode in setOf("1", "3")) {
                    val sent = smsSentAt
                    if (smsCode.isBlank() || smsAccount != account || sent == null ||
                        nowMillis() - sent !in 0..300_000)
                        return@withLock RailwayPasswordResult.SMS_REQUIRED
                    if (!smsCode.matches(Regex("[0-9]{6}")))
                        throw RailwayException(RailwayFailure.INVALID_LOGIN_INPUT)
                } else if (mode != "0") throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)

                val fields = mapOf(
                    "username" to account, "password" to RailwayPasswordEncoding.encode(password),
                    "appid" to "otn", "checkMode" to if (mode == "0") "" else "0",
                    "randCode" to if (mode == "0") "" else smsCode,
                    "sessionId" to "", "sig" to "", "if_check_slide_passcode_token" to "", "scene" to "",
                )
                val result = response("/passport/web/login", fields).value("result_code")
                if (result != "0") {
                    if (result in setOf("91", "92", "94", "95", "97", "101"))
                        throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
                    throw RailwayException(RailwayFailure.LOGIN_REJECTED)
                }
                smsAccount = null
                smsSentAt = null
                val auth = response("/passport/web/auth/uamtk", mapOf("appid" to "otn"))
                if (auth.value("result_code") != "0")
                    throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
                val token = auth.value("newapptk").ifBlank { auth.value("apptk") }
                if (token.isBlank() || token.length > 4096)
                    throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
                if (response("/otn/uamauthclient", mapOf("tk" to token)).value("result_code") != "0")
                    throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
                RailwayPasswordResult.SESSION_ESTABLISHED
            }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun sendSms(username: String, identityLastFour: String) = mutex.withLock {
        val account = validatedAccount(username)
        if (!identityLastFour.matches(Regex("[A-Za-z0-9]{4}")))
            throw RailwayException(RailwayFailure.INVALID_LOGIN_INPUT)
        val now = nowMillis()
        if (lastSmsAttempt?.let { now - it < 60_000 } == true)
            throw RailwayException(RailwayFailure.SMS_COOLDOWN)
        val mode = verificationMode(account)
        if (mode !in setOf("1", "3")) throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
        // A timed-out send may still deliver. Apply cooldown before I/O; never auto-resend.
        lastSmsAttempt = now
        smsAccount = null
        smsSentAt = null
        if (response("/passport/web/getMessageCode",
                mapOf("appid" to "otn", "username" to account, "castNum" to identityLastFour))
                .value("result_code") != "0")
            throw RailwayException(RailwayFailure.SMS_REJECTED)
        smsAccount = account
        smsSentAt = now
    }

    private fun validatedAccount(username: String): String {
        val value = username.trim()
        if (value.isEmpty() || value.length > 128 || value.any(Char::isISOControl))
            throw RailwayException(RailwayFailure.INVALID_LOGIN_INPUT)
        return value
    }

    private suspend fun verificationMode(account: String): String =
        response("/passport/web/checkLoginVerify", mapOf("username" to account, "appid" to "otn"),
            "login_check_code").value("login_check_code")

    private suspend fun response(path: String, fields: Map<String, String>,
        required: String = "result_code"): JsonObject = try {
        JsonParser.parseString(transport.post(path, fields)).asJsonObject.also {
            if (!it.has(required) || !it.get(required).isJsonPrimitive)
                throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
        }
    } catch (e: CancellationException) { throw e }
    catch (e: RailwayException) { throw e }
    catch (_: Exception) { throw RailwayException(RailwayFailure.SCHEMA_CHANGED) }
}
