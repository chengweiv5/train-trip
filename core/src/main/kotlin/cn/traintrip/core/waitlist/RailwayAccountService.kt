package cn.traintrip.core.waitlist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The Android implementation owns a private in-memory HTTP session. Never log bodies or fields. */
interface RailwayTransport {
    suspend fun post(path: String, fields: Map<String, String> = emptyMap()): String
}

enum class RailwayFailure {
    LOGIN_REQUIRED, NETWORK, SCHEMA_CHANGED, IDENTITY_CHANGED, VERIFICATION_REQUIRED,
    INVALID_LOGIN_INPUT, LOGIN_REJECTED, SMS_COOLDOWN, SMS_REJECTED,
}
class RailwayException(val reason: RailwayFailure) : IllegalStateException(when (reason) {
    RailwayFailure.LOGIN_REQUIRED -> "本应用会话未登录，请重新登录"
    RailwayFailure.NETWORK -> "12306 暂未返回可用响应，请稍后重试"
    RailwayFailure.SCHEMA_CHANGED -> "12306 返回结构暂无法核验，已停止后续操作"
    RailwayFailure.IDENTITY_CHANGED -> "账号或乘车人已变更，请重新确认"
    RailwayFailure.VERIFICATION_REQUIRED -> "12306 要求额外验证或暂不接受此请求，未建立授权会话"
    RailwayFailure.INVALID_LOGIN_INPUT -> "请检查账号、密码、证件后四位或六位短信验证码的格式"
    RailwayFailure.LOGIN_REJECTED -> "12306 未接受本次登录，请检查账号、密码或验证码；不会自动重试"
    RailwayFailure.SMS_COOLDOWN -> "短信请求间隔不足一分钟，请稍后再试"
    RailwayFailure.SMS_REJECTED -> "12306 未接受短信请求，请检查账号和绑定证件后四位；不会自动重发"
})

data class RailwayPassenger(
    val reference: String, val displayName: String, val maskedId: String,
    val ticketType: String, val ticketLabel: String, val selectable: Boolean, val status: String,
)
data class RailwayAccount(val reference: String, val displayName: String, val passengers: List<RailwayPassenger>)

/** Sensitive records are private, ephemeral, and intentionally do not have generated toString. */
internal class RailwayPassengerCredential(
    val reference: String, val name: String, val idType: String, val id: String,
    val encrypted: String, val ticketType: String, val oldPassenger: String,
)

class RailwayAccountService(
    private val transport: RailwayTransport,
    private val localReference: (String) -> String,
) {
    private val mutex = Mutex()
    private var accountReference: String? = null
    private var credentials = emptyMap<String, RailwayPassengerCredential>()
    private var selectableReferences = emptySet<String>()

    suspend fun refresh(): RailwayAccount = mutex.withLock {
        credentials = emptyMap()
        selectableReferences = emptySet()
        accountReference = null
        val reference = verifiedAccount()
        val data = response("/otn/confirmPassenger/getPassengerDTOs")
        val records = data.getAsJsonArray("normal_passengers")
            ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
        val secrets = mutableMapOf<String, RailwayPassengerCredential>()
        val passengers = records.map { item ->
            val p = item.asJsonObject
            val name = p.value("passenger_name")
            val id = p.value("passenger_id_no")
            val idType = p.value("passenger_id_type_code")
            val ticket = p.value("passenger_type")
            val encrypted = p.value("allEncStr")
            val uuid = p.value("passenger_uuid")
            if (name.isBlank() || idType.isBlank() || ticket.isBlank() || (uuid.isBlank() && id.isBlank()))
                throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            val key = localReference("passenger/$reference/${uuid.ifBlank { "$idType/$id" }}")
            if (key in secrets) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            val verified = p.value("total_times") in setOf("93", "95", "97", "99")
            // Ticket-type rules beyond an existing adult record require explicit platform validation.
            val selectable = verified && p.value("is_buy_ticket") == "Y" && ticket == "1" && encrypted.isNotBlank()
            secrets[key] = RailwayPassengerCredential(key, name, idType, id, encrypted, ticket, p.value("isOldThan60"))
            RailwayPassenger(key, maskName(name), maskId(id), ticket, p.value("passenger_type_name").ifBlank { "票种待核验" },
                selectable, if (selectable) "已核验 · 成人票" else "请在 12306 核验资格或票种")
        }
        // A second identity read detects login changes while the passenger request was in flight.
        if (verifiedAccount() != reference) throw RailwayException(RailwayFailure.IDENTITY_CHANGED)
        accountReference = reference
        credentials = secrets
        selectableReferences = passengers.filter { it.selectable }.map { it.reference }.toSet()
        RailwayAccount(reference, "12306 已登录", passengers)
    }

    suspend fun verifyBinding(binding: WaitlistBinding) = mutex.withLock {
        if (verifiedAccount() != binding.accountReference || accountReference != binding.accountReference)
            throw RailwayException(RailwayFailure.IDENTITY_CHANGED)
        if (binding.passengers.isEmpty() || binding.passengers.any {
                it.reference !in selectableReferences || credentials[it.reference]?.ticketType != it.ticketType
            }) throw RailwayException(RailwayFailure.IDENTITY_CHANGED)
    }

    private suspend fun verifiedAccount(): String {
        val status = response("/otn/login/checkUser")
        if (status.get("flag")?.asBoolean != true) throw RailwayException(RailwayFailure.LOGIN_REQUIRED)
        val info = response("/otn/modifyUser/initQueryUserInfoApi")
        val name = info.getAsJsonObject("userDTO")?.getAsJsonObject("loginUserDTO")?.value("user_name")
        if (name.isNullOrBlank()) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
        return localReference("account/$name")
    }

    internal suspend fun response(path: String, fields: Map<String, String> = emptyMap()): JsonObject = try {
        val root = JsonParser.parseString(transport.post(path, fields)).asJsonObject
        if (root.get("status")?.asBoolean != true) throw RailwayException(RailwayFailure.LOGIN_REQUIRED)
        root.getAsJsonObject("data") ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
    } catch (e: CancellationException) { throw e }
    catch (e: RailwayException) { throw e }
    catch (_: Exception) { throw RailwayException(RailwayFailure.SCHEMA_CHANGED) }

    private fun maskName(name: String) = name.take(1) + "*".repeat((name.length - 1).coerceIn(1, 6))
    private fun maskId(id: String) = if (id.length > 4) "•••• ${id.takeLast(4)}" else "证件已隐藏"
}

internal fun JsonObject.value(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
