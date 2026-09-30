package cn.traintrip.core.waitlist

import com.google.gson.*
import java.time.Instant
import java.time.LocalDate

/** Versioned, explicit schema: no Java reflection over credentials or platform session objects. */
object WaitlistSnapshotCodec {
    fun encode(state: WaitlistState): String = Gson().toJson(mapOf("version" to 1, "state" to mapOf(
        "request" to mapOf("id" to state.request.id, "binding" to binding(state.request.binding),
            "demands" to state.request.demands.map(::demand), "deadline" to state.request.fulfilmentDeadline.toString()),
        "remaining" to state.remaining.map(::demand),
        "exclusions" to state.exclusions.map { mapOf("demand" to demand(it.demand), "attempt" to it.attemptId, "reason" to it.reason) },
        "pending" to state.pending?.let { mapOf("id" to it.id, "number" to it.number, "binding" to binding(it.binding),
            "demands" to it.demands.map(::demand), "deadline" to it.fulfilmentDeadline.toString()) },
        "attemptCount" to state.attemptCount, "phase" to state.phase.name,
        "order" to state.order?.let { mapOf("reference" to it.reference, "demands" to it.demands.map(::demand)) },
        "notice" to state.notice, "autoContinue" to state.autoContinue, "pauseReason" to state.pauseReason?.name,
    )))

    fun decode(raw: String): WaitlistState {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root.get("version").asInt == 1) { "不支持的候补记录版本" }
        val s = root.getAsJsonObject("state")
        val r = s.getAsJsonObject("request")
        val request = WaitlistRequest(r.text("id"), readBinding(r.getAsJsonObject("binding")),
            demands(r, "demands"), Instant.parse(r.text("deadline")))
        val state = WaitlistState(request, demands(s, "remaining"),
            s.getAsJsonArray("exclusions").map { item -> item.asJsonObject.let {
                WaitlistExclusion(readDemand(it.getAsJsonObject("demand")), it.text("attempt"), it.text("reason"))
            } },
            s.optional("pending")?.asJsonObject?.let {
                WaitlistAttempt(it.text("id"), it.get("number").asInt, readBinding(it.getAsJsonObject("binding")),
                    demands(it, "demands"), Instant.parse(it.text("deadline")))
            }, s.get("attemptCount").asInt, WaitlistPhase.valueOf(s.text("phase")),
            s.optional("order")?.asJsonObject?.let { WaitlistOrder(it.text("reference"), demands(it, "demands")) },
            s.optional("notice")?.asString, s.get("autoContinue").asBoolean,
            s.optional("pauseReason")?.asString?.let(WaitlistPause::valueOf))
        require(request.demands.isNotEmpty() && request.demands.distinct().size == request.demands.size)
        require(state.attemptCount >= 0)
        require(state.remaining.distinct().size == state.remaining.size && request.demands.containsAll(state.remaining))
        require(state.exclusions.map { it.demand }.distinct().size == state.exclusions.size)
        require(state.exclusions.all { it.demand in request.demands && it.demand !in state.remaining })
        require((state.remaining + state.exclusions.map { it.demand }).toSet() == request.demands.toSet())
        state.pending?.let {
            require(it.binding.matches(request.binding) && it.demands == state.remaining &&
                it.fulfilmentDeadline == request.fulfilmentDeadline && it.number == state.attemptCount && it.number > 0)
        }
        require(state.phase !in setOf(WaitlistPhase.SUBMITTING, WaitlistPhase.CHECKING_ORDER) || state.pending != null)
        require(state.phase != WaitlistPhase.ORDER_CREATED || state.order != null)
        require(state.phase != WaitlistPhase.EXHAUSTED || (state.remaining.isEmpty() && state.pending == null))
        return state
    }

    private fun demand(d: WaitlistDemand) = mapOf("date" to d.date.toString(), "train" to d.trainId,
        "from" to d.fromStation, "to" to d.toStation, "seat" to d.seatCode)
    private fun binding(b: WaitlistBinding) = mapOf("account" to b.accountReference,
        "passengers" to b.passengers.map { mapOf("reference" to it.reference, "ticketType" to it.ticketType) })
    private fun readDemand(j: JsonObject) = WaitlistDemand(LocalDate.parse(j.text("date")),
        j.text("train"), j.text("from"), j.text("to"), j.text("seat"))
    private fun readBinding(j: JsonObject) = WaitlistBinding(j.text("account"),
        j.getAsJsonArray("passengers").map { it.asJsonObject.let { p ->
            WaitlistPassenger(p.text("reference"), p.text("ticketType"))
        } }).also {
        require(it.passengers.isNotEmpty() && it.passengers.distinctBy { p -> p.reference }.size == it.passengers.size)
    }
    private fun demands(j: JsonObject, name: String) = j.getAsJsonArray(name).map { readDemand(it.asJsonObject) }
    private fun JsonObject.text(name: String) = get(name).asString.also { require(it.isNotBlank()) }
    private fun JsonObject.optional(name: String) = get(name)?.takeUnless { it.isJsonNull }
}

/** Only filter preferences use Gson; live order snapshots above have a strict explicit schema. */
object WaitlistFiltersCodec {
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { date, _, _ -> JsonPrimitive(date.toString()) })
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ -> LocalDate.parse(json.asString) })
        .create()
    fun encode(filters: WaitlistFilters): String = gson.toJson(filters)
    fun decode(raw: String): WaitlistFilters = gson.fromJson(raw, WaitlistFilters::class.java).also {
        require(it.originCityId.isNotBlank() && it.destinationCityId.isNotBlank())
        requireNotNull(it.seats); requireNotNull(it.startDate); requireNotNull(it.endDate)
        requireNotNull(it.sort); requireNotNull(it.trainKind); requireNotNull(it.departurePeriods)
        requireNotNull(it.originStations); requireNotNull(it.destinationStations)
    }
}
