package cn.traintrip.core.waitlist

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WaitlistCoordinatorTest {
    private val now = Instant.parse("2026-09-30T01:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val a = WaitlistDemand(LocalDate.of(2026, 10, 7), "G374", "LYF", "BXP", "SECOND")
    private val b = a.copy(trainId = "G652")
    private val binding = WaitlistBinding("account", listOf(WaitlistPassenger("person", "ADULT")))
    private val request = WaitlistRequest("run", binding, listOf(a, b), now.plusSeconds(3600))
    private val limits = WaitlistLimits(3, 2, 5)

    @Test fun `failed initial persistence sends nothing to the platform`() = runTest {
        val store = MemoryStore(failWrites = true)
        val gateway = ScriptedGateway()
        val flow = WaitlistCoordinator(gateway, store, clock)
        try {
            flow.start(request, limits)
            fail("Persistence failure must be reported")
        } catch (_: WaitlistPersistenceException) {
            assertTrue(gateway.submitted.isEmpty())
            assertNull(flow.state.value)
        }
    }

    @Test fun `each complete demand set is durable before the platform receives it`() = runTest {
        val store = MemoryStore()
        val gateway = ScriptedGateway(
            onSubmit = { attempt ->
                assertEquals(attempt, store.saved!!.pending)
                if (attempt.number == 1) WaitlistSubmissionResult.Restricted(setOf(a), "受限")
                else WaitlistSubmissionResult.Created(WaitlistOrder("order", listOf(b)))
            },
        )
        val flow = WaitlistCoordinator(gateway, store, clock)
        flow.start(request, limits)
        assertEquals(listOf(listOf(a, b), listOf(b)), gateway.submitted.map { it.demands })
        assertEquals(WaitlistPhase.ORDER_CREATED, flow.state.value!!.phase)
        assertEquals(flow.state.value, store.saved)
    }

    @Test fun `process recovery only queries a pending request and does not replay submit`() = runTest {
        val store = MemoryStore().apply { saved = WaitlistFlow.start(request, limits, now).state }
        val gateway = ScriptedGateway(onQuery = {
            WaitlistOrderResult.Created(WaitlistOrder("recovered", listOf(b)))
        })
        val flow = WaitlistCoordinator(gateway, store, clock)
        flow.recover(binding)
        assertTrue(gateway.submitted.isEmpty())
        assertEquals(listOf("run/1"), gateway.queried.map { it.id })
        assertEquals("recovered", flow.state.value!!.order!!.reference)
    }

    @Test fun `stop is responsive while submit is suspended and prevents a second attempt`() = runTest {
        val response = CompletableDeferred<WaitlistSubmissionResult>()
        val gateway = ScriptedGateway(onSubmit = { response.await() })
        val flow = WaitlistCoordinator(gateway, MemoryStore(), clock)
        val work = launch { flow.start(request, limits) }
        runCurrent()
        flow.stop()
        assertFalse(flow.state.value!!.autoContinue)
        response.complete(WaitlistSubmissionResult.Restricted(setOf(a), "受限"))
        work.join()
        assertEquals(1, gateway.submitted.size)
        assertEquals(listOf(b), flow.state.value!!.remaining)
        assertEquals(WaitlistPhase.PAUSED, flow.state.value!!.phase)
    }

    @Test fun `cancellation leaves durable pending state for recovery not a blind retry`() = runTest {
        val response = CompletableDeferred<WaitlistSubmissionResult>()
        val store = MemoryStore()
        val gateway = ScriptedGateway(onSubmit = { response.await() })
        val flow = WaitlistCoordinator(gateway, store, clock)
        val work = launch { flow.start(request, limits) }
        runCurrent()
        work.cancelAndJoin()
        assertNotNull(store.saved!!.pending)
        val restored = WaitlistCoordinator(gateway, store, clock)
        restored.recover(binding)
        assertEquals(1, gateway.submitted.size)
        assertEquals(1, gateway.queried.size)
        assertEquals(WaitlistPhase.CHECKING_ORDER, restored.state.value!!.phase)
    }

    @Test fun `network error becomes an unresolved order and does not loop or retry`() = runTest {
        val gateway = ScriptedGateway(
            onSubmit = { throw IOException("network error with a private payload") },
            onQuery = { throw IOException("query failed") },
        )
        val flow = WaitlistCoordinator(gateway, MemoryStore(), clock)
        flow.start(request, limits)
        assertEquals(1, gateway.submitted.size)
        assertEquals(1, gateway.queried.size)
        assertEquals(WaitlistPhase.CHECKING_ORDER, flow.state.value!!.phase)
        assertFalse(flow.state.value!!.notice.orEmpty().contains("private"))
    }

    @Test fun `failed persistence after rejection cannot dispatch remaining demands`() = runTest {
        val store = MemoryStore()
        val gateway = ScriptedGateway(onSubmit = {
            store.failWrites = true
            WaitlistSubmissionResult.Restricted(setOf(a), "受限")
        })
        val flow = WaitlistCoordinator(gateway, store, clock)
        try {
            flow.start(request, limits)
            fail("Saving next attempt must fail")
        } catch (_: WaitlistPersistenceException) {
            assertEquals(1, gateway.submitted.size)
            assertEquals("run/1", store.saved!!.pending!!.id)
        }
        store.failWrites = false
        try {
            flow.continueRemaining(binding)
            fail("A failed coordinator must not continue with stale memory")
        } catch (_: IllegalStateException) {
            assertEquals(1, gateway.submitted.size)
        }
        WaitlistCoordinator(gateway, store, clock).recover(binding)
        assertEquals(1, gateway.queried.size)
        assertEquals(1, gateway.submitted.size)
    }

    @Test fun `double start does not create two submissions`() = runTest {
        val response = CompletableDeferred<WaitlistSubmissionResult>()
        val gateway = ScriptedGateway(onSubmit = { response.await() })
        val flow = WaitlistCoordinator(gateway, MemoryStore(), clock)
        val first = launch { flow.start(request, limits) }
        runCurrent()
        try {
            flow.start(request.copy(id = "second-run"), limits)
            fail("Existing unresolved request must prevent a new run")
        } catch (_: IllegalStateException) {
            assertEquals(1, gateway.submitted.size)
        }
        response.complete(WaitlistSubmissionResult.Unknown)
        first.join()
    }

    @Test fun `wrong identity on recovery never queries or submits another account`() = runTest {
        val store = MemoryStore().apply { saved = WaitlistFlow.start(request, limits, now).state }
        val gateway = ScriptedGateway()
        val flow = WaitlistCoordinator(gateway, store, clock)
        flow.recover(binding.copy(accountReference = "other"))
        assertTrue(gateway.submitted.isEmpty())
        assertTrue(gateway.queried.isEmpty())
        assertNotNull(flow.state.value!!.pending)
        flow.checkOrder(binding)
        assertEquals(1, gateway.queried.size)
    }

    @Test fun `recovery does not turn an ambiguous rejection into a resumable background pause`() = runTest {
        val first = WaitlistFlow.start(request, limits, now)
        val rejected = WaitlistFlow.accept(first.state, WaitlistEvent.Submission(
            first.state.pending!!.id, WaitlistSubmissionResult.Restricted(emptySet(), "无法定位"),
        ), now).state
        val store = MemoryStore().apply { saved = rejected }
        val gateway = ScriptedGateway()
        val flow = WaitlistCoordinator(gateway, store, clock)
        flow.recover(binding)
        flow.continueRemaining(binding)
        assertEquals(WaitlistPause.ATTRIBUTION_UNKNOWN, flow.state.value!!.pauseReason)
        assertTrue(gateway.submitted.isEmpty())
    }

    private class MemoryStore(var failWrites: Boolean = false) : WaitlistStore {
        var saved: WaitlistState? = null
        override suspend fun load() = saved
        override suspend fun save(state: WaitlistState) {
            if (failWrites) throw IOException("disk unavailable")
            saved = state
        }
    }

    private class ScriptedGateway(
        val onSubmit: suspend (WaitlistAttempt) -> WaitlistSubmissionResult = {
            WaitlistSubmissionResult.Unknown
        },
        val onQuery: suspend (WaitlistAttempt) -> WaitlistOrderResult = {
            WaitlistOrderResult.Unresolved
        },
    ) : WaitlistGateway {
        val submitted = mutableListOf<WaitlistAttempt>()
        val queried = mutableListOf<WaitlistAttempt>()
        override suspend fun submit(attempt: WaitlistAttempt): WaitlistSubmissionResult {
            submitted += attempt
            return onSubmit(attempt)
        }
        override suspend fun queryOrder(attempt: WaitlistAttempt): WaitlistOrderResult {
            queried += attempt
            return onQuery(attempt)
        }
    }
}
