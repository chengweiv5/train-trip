package cn.traintrip.core.waitlist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No generated toString/copy: challenge identifiers and image contents are credentials. */
class RailwayQrChallenge internal constructor(
    internal val uuid: String,
    val imageBase64: String,
    internal val startedAt: Long,
)

enum class RailwayQrStatus { WAITING_SCAN, WAITING_CONFIRMATION, SESSION_ESTABLISHED, EXPIRED }

/**
 * Uses the public official QR flow with explicit user approval in the 12306 app.
 * Establishing a session is NOT proof of account identity or passenger eligibility.
 */
class RailwayQrLogin(
    private val transport: RailwayTransport,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var current: RailwayQrChallenge? = null
    private var established = false

    suspend fun create(): RailwayQrChallenge = mutex.withLock {
        current = null
        established = false
        val data = response("/passport/web/create-qr64", mapOf("appid" to "otn"))
        if (data.value("result_code") != "0") throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
        val uuid = data.value("uuid")
        val image = data.value("image")
        if (uuid.isBlank() || uuid.length > 512 || image.isBlank() || image.length > 1_500_000 ||
            !image.matches(Regex("[A-Za-z0-9+/=\\r\\n]+")))
            throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
        RailwayQrChallenge(uuid, image, nowMillis()).also { current = it }
    }

    suspend fun poll(challenge: RailwayQrChallenge): RailwayQrStatus = mutex.withLock {
        if (current !== challenge) return@withLock RailwayQrStatus.EXPIRED
        if (established) return@withLock RailwayQrStatus.SESSION_ESTABLISHED
        val age = nowMillis() - challenge.startedAt
        if (age !in 0..120_000) {
            current = null
            return@withLock RailwayQrStatus.EXPIRED
        }
        when (response("/passport/web/checkqr", mapOf("uuid" to challenge.uuid, "appid" to "otn"))
            .value("result_code")) {
            "0" -> RailwayQrStatus.WAITING_SCAN
            "1" -> RailwayQrStatus.WAITING_CONFIRMATION
            "3" -> { current = null; RailwayQrStatus.EXPIRED }
            "2" -> {
                // A failed or unknown exchange must start a new challenge, not silently replay.
                current = null
                val auth = response("/passport/web/auth/uamtk", mapOf("appid" to "otn"))
                if (auth.value("result_code") != "0")
                    throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
                val token = auth.value("newapptk").ifBlank { auth.value("apptk") }
                if (token.isBlank() || token.length > 4096)
                    throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
                val result = response("/otn/uamauthclient", mapOf("tk" to token))
                if (result.value("result_code") != "0")
                    throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
                established = true
                current = challenge
                RailwayQrStatus.SESSION_ESTABLISHED
            }
            else -> {
                current = null
                throw RailwayException(RailwayFailure.VERIFICATION_REQUIRED)
            }
        }
    }

    private suspend fun response(path: String, fields: Map<String, String>): JsonObject = try {
        JsonParser.parseString(transport.post(path, fields)).asJsonObject.also {
            if (!it.has("result_code")) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
        }
    } catch (e: CancellationException) { throw e }
    catch (e: RailwayException) { throw e }
    catch (_: Exception) { throw RailwayException(RailwayFailure.SCHEMA_CHANGED) }
}
