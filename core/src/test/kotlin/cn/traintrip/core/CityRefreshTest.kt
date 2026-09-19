package cn.traintrip.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CityRefreshTest {
    private val catalog = StationCatalog.bundled()
    private val a = QueryUnit(today().plusDays(1), catalog.byCode.getValue("VNP"), catalog.byCode.getValue("TJP"))
    private val b = a.copy(date = a.date.plusDays(1))
    private val c = a.copy(destination = catalog.cities.first { it.name == "上海" }.representative)
    private val old = Instant.parse("2026-09-18T01:00:00Z")
    private fun success(u: QueryUnit, at: Instant = old) = QueryResult.Success(listOf(Trip(u.date, "G1", "G1", u.origin, u.destination,
        LocalTime.of(8, 0), LocalTime.of(9, 0), 60, SaleState.OPEN, "", mapOf(SeatType.SECOND to SeatAvailability("有", AvailabilityKind.AVAILABLE)), at)), at)
    private fun baseline() = SearchProgress(listOf(a, b, c), listOf(a,b,c).associate { it.key to success(it) })

    @Test fun partialFailureRetainsOldDataAndTimestampWithoutClaimingComplete() {
        val fresh = success(a, old.plusSeconds(100))
        val result = baseline().mergeRefresh(SearchProgress(listOf(a,b), mapOf(a.key to fresh, b.key to QueryResult.Failure("offline"))))
        assertEquals(3, result.trips.size)
        assertEquals(old, result.trips.first { it.date == b.date }.queriedAt)
        assertEquals(success(c), result.outcomes[c.key])
        assertEquals(1, result.failureCount)
        assertFalse(result.complete)
        assertEquals(setOf(b.key), result.retainedSuccesses.keys)
    }

    @Test fun successfulEmptyRetryRemovesCachedTrip() {
        val failed = baseline().mergeRefresh(SearchProgress(listOf(a), mapOf(a.key to QueryResult.Failure("offline"))))
        val result = failed.mergeRefresh(SearchProgress(listOf(a), mapOf(a.key to QueryResult.Success(emptyList(), old.plusSeconds(100)))))
        assertFalse(result.trips.any { it.date == a.date && it.to.code == a.destination.code })
        assertTrue(result.retainedSuccesses.isEmpty())
        assertTrue(result.complete)
    }

    @Test fun unfinishedRefreshOnlyChangesCompletedUnits() {
        val result = baseline().mergeRefresh(SearchProgress(listOf(a,b), mapOf(a.key to success(a, old.plusSeconds(100))), running=true))
        assertEquals(success(b), result.outcomes[b.key])
        assertEquals(success(c), result.outcomes[c.key])
        assertEquals(3, result.trips.size)
    }

    @Test fun globalRetryKeepsCachedTicketsUntilSuccessfulResponse() = runTest {
        val source = object : TicketSource {
            override suspend fun initialize() = error("unused")
            override suspend fun query(unit: QueryUnit) = QueryResult.Failure("still offline")
        }
        val emissions = SearchEngine(source, 0).search(listOf(a), retainedSuccesses=mapOf(a.key to success(a))).toList()
        assertTrue(emissions.all { it.trips.single().queriedAt == old })
        assertFalse(emissions.last().complete)
    }
}
