package cn.traintrip.core.waitlist

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WaitlistFlowTest {
    private val now = Instant.parse("2026-09-30T01:00:00Z")
    private val a = demand("G374")
    private val b = demand("G652")
    private val c = demand("G654")
    private val binding = WaitlistBinding("account-ref", listOf(WaitlistPassenger("person-ref", "ADULT")))
    private val request = WaitlistRequest("run-1", binding, listOf(a, b, c), now.plusSeconds(3600))
    private val limits = WaitlistLimits(maxDemands = 3, maxDates = 2, maxPassengers = 5)

    @Test fun `one approval submits all remaining demands until an order is created`() {
        val first = WaitlistFlow.start(request, limits, now)
        assertEquals(listOf(a, b, c), (first.effect as WaitlistEffect.Submit).attempt.demands)

        val second = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Restricted(setOf(a), "该席别候补人数过多"),
        ), now)
        assertEquals(listOf(b, c), (second.effect as WaitlistEffect.Submit).attempt.demands)
        assertEquals(listOf(a), second.state.exclusions.map { it.demand })

        val third = WaitlistFlow.accept(second.state, WaitlistEvent.Submission(
            second.state.pending!!.id, WaitlistSubmissionResult.Restricted(setOf(b), "该席别候补人数过多"),
        ), now)
        assertEquals(listOf(c), (third.effect as WaitlistEffect.Submit).attempt.demands)

        val created = WaitlistFlow.accept(third.state, WaitlistEvent.Submission(
            third.state.pending!!.id,
            WaitlistSubmissionResult.Created(WaitlistOrder("order-ref", listOf(c))),
        ), now)
        assertEquals(WaitlistPhase.ORDER_CREATED, created.state.phase)
        assertEquals("order-ref", created.state.order!!.reference)
        assertNull(created.effect)
    }

    @Test fun `ambiguous or foreign rejection pauses without removing any demand`() {
        for (keys in listOf(emptySet(), setOf(demand("OTHER")), setOf(a, demand("OTHER")))) {
            val first = WaitlistFlow.start(request, limits, now)
            val result = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
                first.state.pending!!.id, WaitlistSubmissionResult.Restricted(keys, "未能定位"),
            ), now)
            assertEquals(listOf(a, b, c), result.state.remaining)
            assertTrue(result.state.exclusions.isEmpty())
            assertNull(result.effect)
            assertEquals(WaitlistPhase.PAUSED, result.state.phase)
        }
    }

    @Test fun `unknown result checks the same attempt and temporary absence never resubmits`() {
        val first = WaitlistFlow.start(request, limits, now)
        val pending = first.state.pending!!
        val checking = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            pending.id, WaitlistSubmissionResult.Unknown,
        ), now)
        assertEquals(pending, (checking.effect as WaitlistEffect.QueryOrder).attempt)
        assertEquals(WaitlistPhase.CHECKING_ORDER, checking.state.phase)
        val absent = WaitlistFlow.accept(checking.state, WaitlistEvent.OrderChecked(
            pending.id, WaitlistOrderResult.Unresolved,
        ), now)
        assertEquals(pending, absent.state.pending)
        assertNull(absent.effect)
        val late = WaitlistFlow.accept(absent.state, WaitlistEvent.OrderChecked(
            pending.id, WaitlistOrderResult.Created(WaitlistOrder("late-order", listOf(c))),
        ), now)
        assertEquals("late-order", late.state.order!!.reference)
        assertNull(late.effect)
    }

    @Test fun `stop prevents further submission but still accepts a pending order`() {
        val first = WaitlistFlow.start(request, limits, now)
        val stopped = WaitlistFlow.accept(first.state, WaitlistEvent.Pause(WaitlistPause.STOPPED), now)
        assertFalse(stopped.state.autoContinue)
        assertNull(stopped.effect)
        val created = WaitlistFlow.accept(stopped.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Created(WaitlistOrder("in-flight", listOf(a))),
        ), now)
        assertEquals(WaitlistPhase.ORDER_CREATED, created.state.phase)
        assertEquals("in-flight", created.state.order!!.reference)
        assertNull(created.effect)
    }

    @Test fun `background pause keeps rejection progress without a next submit`() {
        val first = WaitlistFlow.start(request, limits, now)
        val paused = WaitlistFlow.accept(first.state, WaitlistEvent.Pause(WaitlistPause.BACKGROUND), now)
        val refused = WaitlistFlow.accept(paused.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Restricted(setOf(a), "受限"),
        ), now)
        assertEquals(listOf(b, c), refused.state.remaining)
        assertNull(refused.effect)
        assertEquals(WaitlistPhase.PAUSED, refused.state.phase)
        val resumed = WaitlistFlow.accept(refused.state, WaitlistEvent.Continue(binding), now)
        assertEquals(listOf(b, c), (resumed.effect as WaitlistEffect.Submit).attempt.demands)
    }

    @Test fun `same passenger count cannot resume another account or passenger set`() {
        val first = WaitlistFlow.start(request, limits, now)
        val paused = WaitlistFlow.accept(first.state, WaitlistEvent.Pause(WaitlistPause.STOPPED), now)
        for (changed in listOf(
            binding.copy(accountReference = "other-account"),
            binding.copy(passengers = listOf(WaitlistPassenger("other-person", "ADULT"))),
            binding.copy(passengers = listOf(WaitlistPassenger("person-ref", "STUDENT"))),
        )) {
            val resumed = WaitlistFlow.accept(paused.state, WaitlistEvent.Continue(changed), now)
            assertNull(resumed.effect)
            assertEquals(first.state.pending, resumed.state.pending)
            assertFalse(resumed.state.autoContinue)
        }
        val same = WaitlistFlow.accept(paused.state, WaitlistEvent.Continue(binding), now)
        assertTrue(same.effect is WaitlistEffect.QueryOrder)
    }

    @Test fun `auth expiry preserves the pending request and reauthentication checks it first`() {
        val first = WaitlistFlow.start(request, limits, now)
        val expired = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.AuthenticationRequired,
        ), now)
        assertNull(expired.effect)
        assertEquals(first.state.pending, expired.state.pending)
        assertEquals(WaitlistPause.AUTHENTICATION, expired.state.pauseReason)
        val restored = WaitlistFlow.accept(expired.state, WaitlistEvent.Continue(binding), now)
        assertTrue(restored.effect is WaitlistEffect.QueryOrder)
    }

    @Test fun `foreign or malformed order does not end the pending request`() {
        val first = WaitlistFlow.start(request, limits, now)
        for (order in listOf(
            WaitlistOrder("", listOf(a)),
            WaitlistOrder("wrong", listOf(demand("OTHER"))),
            WaitlistOrder("empty", emptyList()),
            WaitlistOrder("duplicate", listOf(a, a)),
        )) {
            val result = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
                first.state.pending!!.id, WaitlistSubmissionResult.Created(order),
            ), now)
            assertNull(result.state.order)
            assertEquals(first.state.pending, result.state.pending)
            assertEquals(WaitlistPhase.CHECKING_ORDER, result.state.phase)
            assertNull(result.effect)
        }
    }

    @Test fun `request validation rejects duplicate passenger identities and missing station keys`() {
        val invalid = listOf(
            request.copy(binding = binding.copy(passengers = listOf(
                WaitlistPassenger("person-ref", "ADULT"), WaitlistPassenger("person-ref", "STUDENT"),
            ))),
            request.copy(binding = binding.copy(accountReference = "")),
            request.copy(binding = binding.copy(passengers = listOf(WaitlistPassenger("", "ADULT")))),
            request.copy(demands = listOf(a.copy(fromStation = ""))),
            request.copy(demands = listOf(a.copy(toStation = a.fromStation))),
        )
        for (bad in invalid) assertThrows(IllegalArgumentException::class.java) {
            WaitlistFlow.start(bad, limits, now)
        }
    }

    @Test fun `all exact rejections exhaust the selection and stale events are ignored`() {
        val first = WaitlistFlow.start(request, limits, now)
        val next = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Restricted(setOf(a), "受限"),
        ), now)
        val stale = WaitlistFlow.accept(next.state, WaitlistEvent.Submission(
            first.state.pending.id, WaitlistSubmissionResult.Created(WaitlistOrder("stale", listOf(a))),
        ), now)
        assertEquals(next.state, stale.state)
        assertNull(stale.effect)
        val exhausted = WaitlistFlow.accept(next.state, WaitlistEvent.Submission(
            next.state.pending!!.id, WaitlistSubmissionResult.Restricted(setOf(b, c), "受限"),
        ), now)
        assertEquals(WaitlistPhase.EXHAUSTED, exhausted.state.phase)
        assertTrue(exhausted.state.remaining.isEmpty())
        assertFalse(exhausted.state.autoContinue)
        assertNull(exhausted.effect)
    }

    @Test fun `deadline passing after a refusal prevents the next automatic attempt`() {
        val first = WaitlistFlow.start(request, limits, now)
        val expired = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Restricted(setOf(a), "受限"),
        ), request.fulfilmentDeadline)
        assertEquals(listOf(b, c), expired.state.remaining)
        assertNull(expired.effect)
        assertEquals(WaitlistPause.EXPIRED, expired.state.pauseReason)
    }

    @Test fun `authoritative non creation still waits for explicit continuation`() {
        val first = WaitlistFlow.start(request, limits, now)
        val unknown = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Unknown,
        ), now)
        val absent = WaitlistFlow.accept(unknown.state, WaitlistEvent.OrderChecked(
            first.state.pending.id, WaitlistOrderResult.ConfirmedNotCreated,
        ), now)
        assertNull(absent.state.pending)
        assertNull(absent.effect)
        assertFalse(absent.state.autoContinue)
        val retry = WaitlistFlow.accept(absent.state, WaitlistEvent.Continue(binding), now)
        assertEquals(listOf(a, b, c), (retry.effect as WaitlistEffect.Submit).attempt.demands)
        assertEquals("run-1/2", retry.state.pending!!.id)
    }

    @Test fun `other definite rejections stop without guessing an excluded demand`() {
        val first = WaitlistFlow.start(request, limits, now)
        val refused = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Rejected("组合不满足要求"),
        ), now)
        assertNull(refused.state.pending)
        assertEquals(listOf(a, b, c), refused.state.remaining)
        assertNull(refused.effect)
        assertNull(WaitlistFlow.accept(refused.state, WaitlistEvent.Continue(binding), now).effect)
    }

    @Test fun `stop cannot clear a rejection that requires the user to adjust requirements`() {
        val first = WaitlistFlow.start(request, limits, now)
        val rejected = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Rejected("组合不符合要求"),
        ), now)
        val stopped = WaitlistFlow.accept(rejected.state, WaitlistEvent.Pause(WaitlistPause.STOPPED), now)
        assertEquals(WaitlistPause.OTHER_REJECTION, stopped.state.pauseReason)
        assertNull(WaitlistFlow.accept(stopped.state, WaitlistEvent.Continue(binding), now).effect)
    }

    @Test fun `platform limits are enforced without splitting an oversized request`() {
        for (invalid in listOf(
            request.copy(demands = emptyList()),
            request.copy(demands = listOf(a, a)),
            request.copy(demands = listOf(a, b, c, demand("G1"))),
            request.copy(binding = binding.copy(passengers = emptyList())),
            request.copy(fulfilmentDeadline = now),
        )) assertThrows(IllegalArgumentException::class.java) {
            WaitlistFlow.start(invalid, limits, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaitlistFlow.start(request.copy(demands = listOf(a, b.copy(date = b.date.plusDays(1)))),
                limits.copy(maxDates = 1), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaitlistFlow.start(request, limits.copy(maxPassengers = 0), now)
        }
    }

    @Test fun `input collections can change without changing the confirmed request`() {
        val demands = mutableListOf(a, b, c)
        val passengers = binding.passengers.toMutableList()
        val result = WaitlistFlow.start(request.copy(
            demands = demands, binding = binding.copy(passengers = passengers),
        ), limits, now)
        demands.clear()
        passengers.clear()
        assertEquals(listOf(a, b, c), result.state.request.demands)
        assertEquals(binding, result.state.request.binding)
        assertEquals(listOf(a, b, c), (result.effect as WaitlistEffect.Submit).attempt.demands)
    }

    private fun demand(train: String) = WaitlistDemand(
        LocalDate.of(2026, 10, 7), train, "LYF", "BXP", "SECOND",
    )
}
