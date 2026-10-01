package cn.traintrip.core.waitlist

import cn.traintrip.core.SeatType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RailwayPreparedTrain(val demand: WaitlistDemand, val trainCode: String, val departure: Instant)
data class RailwayOrderPreview(
    val id: String, val binding: WaitlistBinding, val trains: List<RailwayPreparedTrain>,
    val deadlineMinutes: List<Int>, val limits: WaitlistLimits, val expiresAt: Instant,
    val requiresSlide: Boolean,
) {
    fun deadline(minutes: Int): Instant {
        require(minutes in deadlineMinutes)
        return trains.minOf { it.departure }.minusSeconds(minutes * 60L)
    }
}

class RailwayOrderException(message: String) : IllegalStateException(message)

/** Runtime output, not serializable UI state. No generated toString over verification material. */
class RailwayRiskProof(
    val previewId: String, internal val encryptedData: String, internal val sessionId: String,
    internal val signature: String, internal val createdAt: Instant,
)
class RailwayRiskChallenge(val previewId: String, val requiresSlide: Boolean, val token: String)

/** Preparation establishes context, never creates an order. Secrets never leave this service. */
class RailwayWaitlistGateway(
    private val transport: RailwayTransport,
    private val accounts: RailwayAccountService,
    private val clock: Clock = Clock.systemUTC(),
) : WaitlistGateway {
    private val mutex = Mutex()
    private var prepared: RailwayOrderPreview? = null
    private var issuedRequest: WaitlistRequest? = null
    private var slideToken: String = ""
    private class Authorization(val request: WaitlistRequest, val preview: RailwayOrderPreview,
        val minutes: Int, val proof: RailwayRiskProof)
    private var authorization: Authorization? = null
    private val sent = mutableSetOf<String>()

    suspend fun prepare(binding: WaitlistBinding, demands: List<WaitlistDemand>): RailwayOrderPreview =
        mutex.withLock {
            prepared = null
            issuedRequest = null
            authorization = null
            slideToken = ""
            require(demands.isNotEmpty() && demands.distinct().size == demands.size)
            accounts.passengersFor(binding)
            val html = transport.get("/otn/leftTicket/init")
            val route = Regex("""var\s+CLeftTicketUrl\s*=\s*['"](leftTicket/query[A-Za-z]*)['"]""")
                .find(html)?.groupValues?.get(1)
                ?: throw RailwayOrderException("官方车次查询入口已变化，请稍后重新核验")
            val context = mutableMapOf<WaitlistDemand, Pair<String, RailwayPreparedTrain>>()
            for ((unit, group) in demands.groupBy { Triple(it.date, it.fromStation, it.toStation) }) {
                val rows = data(transport.get("/otn/$route", mapOf(
                    "leftTicketDTO.train_date" to unit.first.toString(),
                    "leftTicketDTO.from_station" to unit.second, "leftTicketDTO.to_station" to unit.third,
                    "purpose_codes" to "ADULT",
                ))).getAsJsonArray("result") ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
                for (demand in group) {
                    val candidates = rows.map { it.asString.split('|') }.filter {
                        it.size >= 39 && it[2] == demand.trainId && it[6] == demand.fromStation && it[7] == demand.toStation
                    }
                    if (candidates.size != 1) throw RailwayOrderException("选中车次已变化，请返回查询重新选择")
                    val row = candidates.single()
                    val seat = SeatType.entries.singleOrNull { it.waitlistCode == demand.seatCode }
                        ?: throw RailwayOrderException("所选席别暂不支持提交")
                    if (row[11] != "Y" || row[37] != "1" || row[seat.field] !in setOf("无", "0") ||
                        demand.seatCode in row[38] || row[0].isBlank() || row[0] == "null")
                        throw RailwayOrderException("选中需求的余票或候补状态已变化，请返回查询重新确认；未发送订单")
                    if (row[0].any { it in "#|,;\r\n" }) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
                    val departure = demand.date.atTime(LocalTime.parse(row[8])).atZone(CHINA).toInstant()
                    context[demand] = row[0] to RailwayPreparedTrain(demand, row[3], departure)
                }
            }
            val initialized = data(transport.post("/otn/afterNate/submitOrderRequest", mapOf(
                "secretList" to demands.joinToString("") { "${context.getValue(it).first}#${it.seatCode}|" },
            )))
            if (initialized.get("flag")?.asBoolean != true)
                throw RailwayOrderException("12306 未接受候补初始化；请在官方 App 核验身份后重试，未提交订单")
            val init = data(transport.post("/otn/afterNate/passengerInitApi"))
            val trains = init.getAsJsonArray("hbTrainList")
                ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            val actual = trains.map { item ->
                val t = item.asJsonObject
                if (t.value("isForegin") !in setOf("", "0")) throw RailwayOrderException("跨境候补规则尚未核验，请到 12306 办理")
                val match = demands.singleOrNull {
                    it.trainId == t.value("train_no") && it.date.toString() == t.value("train_date") &&
                        it.fromStation == t.value("from_station_telecode") && it.toStation == t.value("to_station_telecode") &&
                        it.seatCode == t.value("seat_type_code")
                } ?: throw RailwayOrderException("12306 返回的候补组合与所选需求不一致，已停止")
                val expected = context.getValue(match).second
                if (expected.trainCode != t.value("station_train_code") ||
                    expected.departure != match.date.atTime(LocalTime.parse(t.value("start_time"))).atZone(CHINA).toInstant())
                    throw RailwayOrderException("车次时刻发生变化，请重新确认")
                expected
            }
            if (actual.size != demands.size || actual.map { it.demand }.toSet() != demands.toSet())
                throw RailwayOrderException("平台未接纳全部需求；不会静默拆组或遗漏车次")
            val passengerLimit = init.value("hb_passenger_max_num").toIntOrNull()
                ?.takeIf { it > 0 } ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            if (binding.passengers.size > passengerLimit) throw RailwayOrderException("乘车人数超过平台本次允许的上限")
            if (init.value("checkcode") == "94")
                throw RailwayOrderException("12306 要求额外人证验证，请在官方 App 办理；本次未发送订单")
            val slide = init.value("if_check_slide_passcode")
            if (slide !in setOf("0", "1")) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            slideToken = init.value("if_check_slide_passcode_token")
            if (slide == "1" && slideToken.isBlank()) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            val options = init.getAsJsonArray("jzdhDiffSelect")?.map { it.asInt }?.distinct()
                ?.filter { it > 0 && actual.minOf { t -> t.departure }.minusSeconds(it * 60L).isAfter(clock.instant()) }
                ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
            if (options.isEmpty()) throw RailwayOrderException("当前没有有效的截止兑现时间，请重新查询")
            accounts.verifyBinding(binding)
            RailwayOrderPreview(UUID.randomUUID().toString(), binding,
                demands.map { wanted -> actual.single { it.demand == wanted } }, options,
                // Capability for this accepted combination only, not a guessed global platform limit.
                WaitlistLimits(demands.size, demands.map { it.date }.distinct().size, passengerLimit),
                clock.instant().plusSeconds(180), slide == "1").also { prepared = it }
        }

    suspend fun riskChallenge(previewId: String): RailwayRiskChallenge = mutex.withLock {
        val p = validPreview(previewId)
        RailwayRiskChallenge(p.id, p.requiresSlide, slideToken)
    }

    suspend fun request(previewId: String, minutes: Int): WaitlistRequest = mutex.withLock {
        val p = validPreview(previewId)
        authorization = null
        WaitlistRequest(UUID.randomUUID().toString(), p.binding, p.trains.map { it.demand }, p.deadline(minutes),
            WaitlistOrderContext(clock.instant().truncatedTo(ChronoUnit.SECONDS),
                p.trains.map { WaitlistOrderTrain(it.demand, it.trainCode) })).also { issuedRequest = it }
    }

    suspend fun authorize(previewId: String, request: WaitlistRequest, minutes: Int, proof: RailwayRiskProof) =
        mutex.withLock {
            val p = validPreview(previewId)
            if (request != issuedRequest)
                throw RailwayOrderException("候补请求与当前核验不一致，请重新核验；未发送订单")
            require(request.binding.matches(p.binding) && request.demands == p.trains.map { it.demand })
            require(request.fulfilmentDeadline == p.deadline(minutes))
            if (proof.previewId != p.id || proof.encryptedData.isBlank() || proof.encryptedData.length > 200_000 ||
                proof.createdAt.isAfter(clock.instant()) || proof.createdAt.plusSeconds(90).isBefore(clock.instant()) ||
                (p.requiresSlide && (proof.sessionId.isBlank() || proof.signature.isBlank())))
                throw RailwayOrderException("官方验证尚未完成或已过期，请重新验证")
            authorization = Authorization(request, p, minutes, proof)
        }

    suspend fun invalidate() = mutex.withLock {
        prepared = null; issuedRequest = null; authorization = null; slideToken = ""
    }

    private fun validPreview(id: String): RailwayOrderPreview =
        prepared?.takeIf { it.id == id && it.expiresAt.isAfter(clock.instant()) }
            ?: throw RailwayOrderException("候补核验已失效，请重新核验全部需求")

    override suspend fun submit(attempt: WaitlistAttempt): WaitlistSubmissionResult = mutex.withLock {
        if (attempt.id in sent) return@withLock WaitlistSubmissionResult.Unknown
        val a = authorization
        if (a == null || attempt.number != 1 || attempt.id != "${a.request.id}/1" ||
            attempt.orderContext != a.request.orderContext || !attempt.binding.matches(a.request.binding) ||
            attempt.demands != a.request.demands || attempt.fulfilmentDeadline != a.request.fulfilmentDeadline ||
            !a.preview.expiresAt.isAfter(clock.instant()) ||
            a.proof.createdAt.plusSeconds(90).isBefore(clock.instant()))
            return@withLock WaitlistSubmissionResult.Rejected("需要重新核验全部需求并完成官方验证；未发送订单")
        val people = try { accounts.passengersFor(attempt.binding) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { return@withLock WaitlistSubmissionResult.AuthenticationRequired }
        if (!attempt.fulfilmentDeadline.isAfter(clock.instant()) ||
            !a.preview.expiresAt.isAfter(clock.instant()) ||
            a.proof.createdAt.plusSeconds(90).isBefore(clock.instant()))
            return@withLock WaitlistSubmissionResult.Rejected("核验或截止时间已失效；未发送订单")
        if (people.any { p -> listOf(p.ticketType, p.name, p.idType, p.id, p.encrypted).any { value ->
                value.isBlank() || value.any { it in "#;\r\n" }
            } }) return@withLock WaitlistSubmissionResult.Rejected("乘车人资料格式无法核验；未发送订单")
        val passengerInfo = people.joinToString(separator = ";", postfix = ";") { p ->
            val parts = listOf(p.ticketType, p.name, p.idType, p.id, p.encrypted)
            // No lower-berth preference is silently opted in.
            parts.joinToString("#") + "#0"
        }
        sent += attempt.id
        authorization = null // A proof authorizes one network submission, never an implicit replay.
        prepared = null
        issuedRequest = null
        slideToken = ""
        try {
            transport.post("/otn/afterNate/confirmHB", mapOf(
                "passengerInfo" to passengerInfo, "jzParam" to "", "hbTrain" to "", "lkParam" to "",
                "sessionId" to a.proof.sessionId, "sig" to a.proof.signature, "scene" to "nc_login",
                "encryptedData" to a.proof.encryptedData, "if_receive_wseat" to "N",
                "realize_limit_time_diff" to a.minutes.toString(), "plans" to "",
                "tmp_train_date" to "", "tmp_train_time" to "", "add_train_flag" to "N",
                "add_train_seat_type_code" to "",
            ))
            // Even flag=true is queue acceptance, not proof of an order. Free-text errors are
            // deliberately NOT mapped to a demand or treated as proof of non-creation.
            WaitlistSubmissionResult.Unknown
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { WaitlistSubmissionResult.Unknown }
    }

    override suspend fun queryOrder(attempt: WaitlistAttempt): WaitlistOrderResult = mutex.withLock {
        try {
            val people = accounts.passengersFor(attempt.binding)
            val candidates = mutableListOf<JsonObject>()
            // Neither queue status -1 nor a blank list is attempt-specific non-creation evidence.
            safeQuery("/otn/afterNate/queryQueue")
            safeQuery("/otn/afterNateOrder/queryQueue")?.getAsJsonObject("order")?.let(candidates::add)
            var page = 0
            var complete = true
            do {
                val result = safeQuery("/otn/afterNateOrder/queryUnHonourHOrder", mapOf(
                    "page_no" to page.toString(),
                    "query_start_date" to clock.instant().atZone(CHINA).toLocalDate().minusDays(29).toString(),
                    "query_end_date" to attempt.demands.maxOf { it.date }.toString(),
                ))
                val list = result?.getAsJsonArray("list")
                if (list == null) { complete = false; break }
                candidates += list.map { it.asJsonObject }
                val pages = list.firstOrNull()?.asJsonObject?.value("total_page")?.toIntOrNull() ?: 1
                page++
                if (page == 10 && page < pages) complete = false
            } while (page < pages && page < 10)
            accounts.verifyBinding(attempt.binding)
            val matches = candidates.filter { matchesOrder(it, attempt, people) }
                .map { it.value("reserve_no") }.distinct()
            if (complete && matches.size == 1) WaitlistOrderResult.Created(WaitlistOrder(matches.single(), attempt.demands))
            else WaitlistOrderResult.Unresolved
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { WaitlistOrderResult.Unresolved }
    }

    private suspend fun safeQuery(path: String, fields: Map<String, String> = emptyMap()): JsonObject? =
        try { data(transport.post(path, fields)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }

    private fun matchesOrder(order: JsonObject, attempt: WaitlistAttempt,
        people: List<RailwayPassengerCredential>): Boolean = try {
        val context = attempt.orderContext
        if (context == null || context.trains.map { it.demand }.toSet() != attempt.demands.toSet() ||
            order.value("reserve_no").isBlank()) false
        else {
            val time = LocalDateTime.parse(order.value("reserve_time"), SERVER_TIME).atZone(CHINA).toInstant()
            val deadline = LocalDateTime.parse(order.value("realize_limit_time"), SERVER_TIME).atZone(CHINA).toInstant()
            val needs = order.getAsJsonArray("needs").flatMap { item ->
                val n = item.asJsonObject
                val codes = n.value("seat_type").ifBlank { n.value("seat_type_code") }
                val seats = if (codes.isNotBlank()) codes.map(Char::toString)
                    else listOfNotNull(SeatType.entries.singleOrNull { it.label == n.value("seat_name") }?.waitlistCode)
                if (seats.isEmpty() || seats.distinct().size != seats.size) throw IllegalArgumentException()
                seats.map { code -> listOf(n.value("board_train_code"), n.value("train_date"),
                    n.value("from_tele_code"), n.value("to_tele_code"), code) }
            }
            val passengers = order.getAsJsonArray("passengers").map { it.asJsonObject }
            !time.isBefore(context.authorizedAt) && !time.isAfter(context.authorizedAt.plusSeconds(180)) &&
                !time.isAfter(clock.instant().plusSeconds(5)) &&
                deadline == attempt.fulfilmentDeadline &&
                needs.size == attempt.demands.size && context.trains.all { expected ->
                    needs.count { it == listOf(expected.trainCode, expected.demand.date.toString(),
                        expected.demand.fromStation, expected.demand.toStation, expected.demand.seatCode) } == 1
                } && passengers.size == people.size && people.all { p ->
                    passengers.count { other ->
                        other.value("passenger_name") == p.name && other.value("passenger_id_no") == p.id &&
                            (other.value("passenger_id_type_code") == p.idType ||
                                (p.idType == "1" && other.value("passenger_id_name") == "居民身份证")) &&
                            (other.value("ticket_type") == p.ticketType ||
                                (p.ticketType == "1" && other.value("ticket_type") in setOf("成人", "成人票")))
                    } == 1
                }
        }
    } catch (_: Exception) { false }

    private fun data(raw: String): JsonObject = try {
        val root = JsonParser.parseString(raw).asJsonObject
        if (root.get("status")?.asBoolean != true) throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
        root.getAsJsonObject("data") ?: throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
    } catch (e: CancellationException) { throw e }
    catch (e: RailwayException) { throw e }
    catch (_: Exception) { throw RailwayException(RailwayFailure.SCHEMA_CHANGED) }

    companion object {
        val CHINA: ZoneId = ZoneId.of("Asia/Shanghai")
        private val SERVER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
