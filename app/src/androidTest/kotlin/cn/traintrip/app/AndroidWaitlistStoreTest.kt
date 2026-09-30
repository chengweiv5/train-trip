package cn.traintrip.app

import androidx.test.platform.app.InstrumentationRegistry
import cn.traintrip.core.waitlist.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AndroidWaitlistStoreTest {
    @Test fun durableStoreCanBeRecreatedAndCorruptionDoesNotBecomeAnEmptyTask() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-waitlist-${System.nanoTime()}.json"
        val path = File(context.noBackupFilesDir, name)
        try {
            val demand = WaitlistDemand(LocalDate.of(2026, 10, 1), "id", "VNP", "JGK", "O")
            val request = WaitlistRequest("run", WaitlistBinding("local-account", listOf(WaitlistPassenger("local-person", "adult"))),
                listOf(demand), Instant.parse("2026-10-01T01:00:00Z"))
            val state = WaitlistFlow.start(request, WaitlistLimits(1, 1, 1), Instant.parse("2026-09-30T01:00:00Z")).state
            assertNull(AndroidWaitlistStore(context, name).load())
            AndroidWaitlistStore(context, name).save(state)
            assertEquals(state, AndroidWaitlistStore(context, name).load())
            path.writeText("{broken")
            try { AndroidWaitlistStore(context, name).load(); fail("Corruption cannot be ignored") }
            catch (_: com.google.gson.JsonParseException) { }
        } finally { path.delete() }
    }

    @Test fun candidateFiltersPersistBothCityStationSelections() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-waitlist-filters-${System.nanoTime()}.json"
        try {
            val filters = WaitlistFilters(originStations = setOf("VNP"), destinationStations = setOf("JGK"),
                onlyAvailable = false, trainKind = WaitlistTrainKind.FAST, sort = WaitlistSort.DURATION)
            AndroidWaitlistDraftStore(context, name).save(filters)
            assertEquals(filters, AndroidWaitlistDraftStore(context, name).load())
        } finally { File(context.noBackupFilesDir, name).delete() }
    }
}
