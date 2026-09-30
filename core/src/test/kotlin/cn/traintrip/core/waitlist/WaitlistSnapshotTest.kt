package cn.traintrip.core.waitlist

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WaitlistSnapshotTest {
    private val demand = WaitlistDemand(LocalDate.of(2026, 10, 1), "id", "VNP", "JGK", "O")
    private val binding = WaitlistBinding("local-account", listOf(WaitlistPassenger("local-passenger", "adult")))
    private val state = WaitlistFlow.start(
        WaitlistRequest("request", binding, listOf(demand), Instant.parse("2026-10-01T00:00:00Z")),
        WaitlistLimits(1, 1, 1), Instant.parse("2026-09-30T00:00:00Z")).state

    @Test fun snapshotRoundTripsPendingAttemptAndRejectsCorruptionOrUnknownVersion() {
        val encoded = WaitlistSnapshotCodec.encode(state)
        assertEquals(state, WaitlistSnapshotCodec.decode(encoded))
        assertThrows(Exception::class.java) { WaitlistSnapshotCodec.decode("{}") }
        assertThrows(Exception::class.java) { WaitlistSnapshotCodec.decode(encoded.replace("\"version\":1", "\"version\":9")) }
        assertThrows(Exception::class.java) { WaitlistSnapshotCodec.decode(encoded.replace("\"SUBMITTING\"", "\"BOGUS\"")) }
        assertThrows(Exception::class.java) { WaitlistSnapshotCodec.decode(encoded.take(encoded.length / 2)) }
    }

    @Test fun unauthenticatedRestoreShowsPendingWithoutSendingOrQuerying() = runTest {
        val store = object : WaitlistStore {
            var saved = state
            override suspend fun load() = saved
            override suspend fun save(state: WaitlistState) { saved = state }
        }
        val gateway = object : WaitlistGateway {
            override suspend fun submit(attempt: WaitlistAttempt): WaitlistSubmissionResult = error("must not submit")
            override suspend fun queryOrder(attempt: WaitlistAttempt): WaitlistOrderResult = error("must not query without authentication")
        }
        val coordinator = WaitlistCoordinator(gateway, store)
        coordinator.restore()
        assertEquals(state.pending, coordinator.state.value?.pending)
        assertFalse(coordinator.state.value!!.autoContinue)
        coordinator.restore()
        assertEquals(state.pending, coordinator.state.value?.pending)
    }
}
