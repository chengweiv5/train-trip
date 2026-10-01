package cn.traintrip.core.waitlist

import java.time.Instant
import java.time.LocalDate

/** References are app-local identifiers, never passwords, cookies, or encrypted passenger tokens. */
data class WaitlistPassenger(val reference: String, val ticketType: String)
data class WaitlistBinding(val accountReference: String, val passengers: List<WaitlistPassenger>) {
    fun matches(other: WaitlistBinding): Boolean =
        accountReference == other.accountReference && passengers.toSet() == other.passengers.toSet()
}

data class WaitlistDemand(
    val date: LocalDate,
    val trainId: String,
    val fromStation: String,
    val toStation: String,
    val seatCode: String,
)

data class WaitlistRequest(
    val id: String,
    val binding: WaitlistBinding,
    val demands: List<WaitlistDemand>,
    val fulfilmentDeadline: Instant,
    val orderContext: WaitlistOrderContext? = null,
)

/** Non-secret receipt matching data survives process death; never stores platform request tokens. */
data class WaitlistOrderTrain(val demand: WaitlistDemand, val trainCode: String)
data class WaitlistOrderContext(val authorizedAt: Instant, val trains: List<WaitlistOrderTrain>)

/** Supplied by a verified platform capability check; there are deliberately no guessed defaults. */
data class WaitlistLimits(val maxDemands: Int, val maxDates: Int, val maxPassengers: Int)

data class WaitlistAttempt(
    val id: String,
    val number: Int,
    val binding: WaitlistBinding,
    val demands: List<WaitlistDemand>,
    val fulfilmentDeadline: Instant,
    val orderContext: WaitlistOrderContext? = null,
)

data class WaitlistExclusion(val demand: WaitlistDemand, val attemptId: String, val reason: String)
data class WaitlistOrder(val reference: String, val demands: List<WaitlistDemand>)
enum class WaitlistPhase { SUBMITTING, CHECKING_ORDER, PAUSED, ORDER_CREATED, EXHAUSTED }
enum class WaitlistPause {
    STOPPED, BACKGROUND, ATTRIBUTION_UNKNOWN, IDENTITY_CHANGED, EXPIRED, AUTHENTICATION,
    NOT_CREATED, OTHER_REJECTION,
}

data class WaitlistState(
    val request: WaitlistRequest,
    val remaining: List<WaitlistDemand>,
    val exclusions: List<WaitlistExclusion> = emptyList(),
    val pending: WaitlistAttempt? = null,
    val attemptCount: Int = 0,
    val phase: WaitlistPhase = WaitlistPhase.SUBMITTING,
    val order: WaitlistOrder? = null,
    val notice: String? = null,
    val autoContinue: Boolean = true,
    val pauseReason: WaitlistPause? = null,
)

sealed interface WaitlistEffect {
    data class Submit(val attempt: WaitlistAttempt) : WaitlistEffect
    data class QueryOrder(val attempt: WaitlistAttempt) : WaitlistEffect
}

sealed interface WaitlistSubmissionResult {
    data class Created(val order: WaitlistOrder) : WaitlistSubmissionResult
    /** Exact demand keys only; adapters must not infer keys from ambiguous free text. */
    data class Restricted(val demands: Set<WaitlistDemand>, val reason: String) : WaitlistSubmissionResult
    data object Unknown : WaitlistSubmissionResult
    data object AuthenticationRequired : WaitlistSubmissionResult
    /** Only for a definite, non-accepting rejection. Uncertain responses must use Unknown. */
    data class Rejected(val reason: String) : WaitlistSubmissionResult
}

sealed interface WaitlistOrderResult {
    data class Created(val order: WaitlistOrder) : WaitlistOrderResult
    /** Includes a temporarily empty order list; absence does not prove non-acceptance. */
    data object Unresolved : WaitlistOrderResult
    /** Requires authoritative attempt-specific evidence; an empty list is Unresolved instead. */
    data object ConfirmedNotCreated : WaitlistOrderResult
}

sealed interface WaitlistEvent {
    data class Submission(val attemptId: String, val result: WaitlistSubmissionResult) : WaitlistEvent
    data class OrderChecked(val attemptId: String, val result: WaitlistOrderResult) : WaitlistEvent
    data class Pause(val reason: WaitlistPause) : WaitlistEvent
    data class Continue(val binding: WaitlistBinding) : WaitlistEvent
}

data class WaitlistTransition(val state: WaitlistState, val effect: WaitlistEffect? = null)

object WaitlistFlow {
    fun start(request: WaitlistRequest, limits: WaitlistLimits, now: Instant): WaitlistTransition {
        require(request.id.isNotBlank())
        require(limits.maxDemands > 0 && limits.maxDates > 0 && limits.maxPassengers > 0)
        require(request.binding.accountReference.isNotBlank())
        require(request.binding.passengers.all { it.reference.isNotBlank() && it.ticketType.isNotBlank() })
        require(request.binding.passengers.map { it.reference }.distinct().size == request.binding.passengers.size)
        require(request.demands.all {
            it.trainId.isNotBlank() && it.fromStation.isNotBlank() && it.toStation.isNotBlank() &&
                it.fromStation != it.toStation && it.seatCode.isNotBlank()
        })
        require(request.demands.isNotEmpty() && request.demands.size <= limits.maxDemands)
        require(request.demands.distinct().size == request.demands.size)
        require(request.demands.map { it.date }.distinct().size <= limits.maxDates)
        require(request.binding.passengers.isNotEmpty() && request.binding.passengers.size <= limits.maxPassengers)
        require(request.fulfilmentDeadline.isAfter(now))
        request.orderContext?.let {
            require(!it.authorizedAt.isAfter(now))
            require(it.trains.map { t -> t.demand }.toSet() == request.demands.toSet())
            require(it.trains.size == request.demands.size && it.trains.all { t -> t.trainCode.isNotBlank() })
        }
        val frozen = request.copy(
            demands = request.demands.toList(),
            binding = request.binding.copy(passengers = request.binding.passengers.toList()),
            orderContext = request.orderContext?.copy(trains = request.orderContext.trains.toList()),
        )
        return submit(WaitlistState(frozen, frozen.demands))
    }

    fun accept(state: WaitlistState, event: WaitlistEvent, now: Instant): WaitlistTransition {
        if (state.phase == WaitlistPhase.ORDER_CREATED || state.phase == WaitlistPhase.EXHAUSTED)
            return WaitlistTransition(state)
        return when (event) {
            is WaitlistEvent.Pause -> WaitlistTransition(state.copy(
                autoContinue = false,
                pauseReason = state.pauseReason ?: event.reason,
                phase = if (state.pending == null) WaitlistPhase.PAUSED else WaitlistPhase.CHECKING_ORDER,
                notice = "已停止后续提交，保留当前处理进度",
            ))
            is WaitlistEvent.Continue -> {
                if (!state.request.binding.matches(event.binding)) {
                    return WaitlistTransition(state.copy(
                        autoContinue = false, pauseReason = WaitlistPause.IDENTITY_CHANGED,
                        notice = "账号或乘车人已变更，请切回原身份核对订单",
                    ))
                }
                if (state.pending != null) return WaitlistTransition(
                    state.copy(phase = WaitlistPhase.CHECKING_ORDER, autoContinue = false),
                    WaitlistEffect.QueryOrder(state.pending),
                )
                if (state.pauseReason !in setOf(
                    WaitlistPause.STOPPED, WaitlistPause.BACKGROUND, WaitlistPause.NOT_CREATED,
                ))
                    return WaitlistTransition(state)
                continueOrPause(state.copy(autoContinue = true, pauseReason = null), now)
            }
            is WaitlistEvent.OrderChecked -> {
                if (state.pending?.id != event.attemptId) return WaitlistTransition(state)
                when (val result = event.result) {
                    is WaitlistOrderResult.Created -> created(state, result.order)
                    WaitlistOrderResult.ConfirmedNotCreated -> WaitlistTransition(state.copy(
                        pending = null, phase = WaitlistPhase.PAUSED, autoContinue = false,
                        pauseReason = WaitlistPause.NOT_CREATED,
                        notice = "已确认本轮未创建订单，可继续剩余需求",
                    ))
                    WaitlistOrderResult.Unresolved -> WaitlistTransition(state.copy(
                        phase = WaitlistPhase.CHECKING_ORDER,
                        notice = "订单结果尚未确定，请稍后核对；不会重复提交",
                    ))
                }
            }
            is WaitlistEvent.Submission -> {
                if (state.pending?.id != event.attemptId) return WaitlistTransition(state)
                when (val result = event.result) {
                    is WaitlistSubmissionResult.Created -> created(state, result.order)
                    is WaitlistSubmissionResult.Rejected -> WaitlistTransition(state.copy(
                        pending = null, phase = WaitlistPhase.PAUSED, autoContinue = false,
                        pauseReason = WaitlistPause.OTHER_REJECTION, notice = result.reason,
                    ))
                    WaitlistSubmissionResult.AuthenticationRequired -> WaitlistTransition(state.copy(
                        autoContinue = false, phase = WaitlistPhase.PAUSED,
                        pauseReason = WaitlistPause.AUTHENTICATION,
                        notice = "请重新登录原账号，登录后先核对未决订单",
                    ))
                    WaitlistSubmissionResult.Unknown -> WaitlistTransition(
                        state.copy(phase = WaitlistPhase.CHECKING_ORDER, notice = "正在核对订单"),
                        WaitlistEffect.QueryOrder(state.pending),
                    )
                    is WaitlistSubmissionResult.Restricted -> {
                        if (result.demands.isEmpty() || !state.pending.demands.containsAll(result.demands)) {
                            return WaitlistTransition(state.copy(
                                pending = null, phase = WaitlistPhase.PAUSED,
                                autoContinue = false, pauseReason = WaitlistPause.ATTRIBUTION_UNKNOWN,
                                notice = "未能准确定位受限需求，请核对后调整",
                            ))
                        }
                        val remaining = state.remaining.filterNot { it in result.demands }
                        val next = state.copy(
                            remaining = remaining,
                            exclusions = state.exclusions + result.demands.map {
                                WaitlistExclusion(it, event.attemptId, result.reason)
                            },
                            pending = null,
                        )
                        if (remaining.isEmpty()) WaitlistTransition(next.copy(
                            phase = WaitlistPhase.EXHAUSTED, autoContinue = false,
                        ))
                        else continueOrPause(next, now)
                    }
                }
            }
        }
    }

    private fun created(state: WaitlistState, order: WaitlistOrder): WaitlistTransition {
        if (order.reference.isBlank() || order.demands.isEmpty() ||
            order.demands.distinct().size != order.demands.size ||
            state.pending?.demands?.containsAll(order.demands) != true) {
            return WaitlistTransition(state.copy(
                phase = WaitlistPhase.CHECKING_ORDER, autoContinue = false,
                notice = "订单返回与本轮需求不一致，请核对订单",
            ))
        }
        return WaitlistTransition(state.copy(
            pending = null, phase = WaitlistPhase.ORDER_CREATED,
            order = order.copy(demands = order.demands.toList()), autoContinue = false, notice = null,
        ))
    }

    private fun continueOrPause(state: WaitlistState, now: Instant): WaitlistTransition = when {
        !state.request.fulfilmentDeadline.isAfter(now) -> WaitlistTransition(state.copy(
            phase = WaitlistPhase.PAUSED, autoContinue = false, pauseReason = WaitlistPause.EXPIRED,
            notice = "兑现截止时间已过，请重新选择需求",
        ))
        !state.autoContinue -> WaitlistTransition(state.copy(phase = WaitlistPhase.PAUSED))
        else -> submit(state)
    }

    private fun submit(state: WaitlistState): WaitlistTransition {
        val number = state.attemptCount + 1
        val attempt = WaitlistAttempt(
            "${state.request.id}/$number", number, state.request.binding,
            state.remaining.toList(), state.request.fulfilmentDeadline,
            state.request.orderContext,
        )
        return WaitlistTransition(
            state.copy(pending = attempt, attemptCount = number, phase = WaitlistPhase.SUBMITTING),
            WaitlistEffect.Submit(attempt),
        )
    }
}
