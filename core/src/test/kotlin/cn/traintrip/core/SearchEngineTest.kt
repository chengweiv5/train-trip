package cn.traintrip.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEngineTest {
    private val catalog = StationCatalog.bundled()
    private val at = Instant.parse("2026-09-19T01:36:00Z")
    private val units = (0L..4L).map {
        QueryUnit(LocalDate.of(2026, 10, 1).plusDays(it), catalog.byCode.getValue("BJP"), catalog.byCode.getValue("TJP"))
    }
    private val success = QueryResult.Success(emptyList(), at)
    private fun source(query: suspend (QueryUnit) -> QueryResult) = object : TicketSource {
        override suspend fun initialize(): SourceInfo = error("unused")
        override suspend fun query(unit: QueryUnit) = query.invoke(unit)
    }

    @Test fun parserFailureDoesNotBlockLaterDates() = runTest {
        val fields = MutableList(39) { "" }.apply {
            this[1] = "12点45分起售"; this[2] = "train"; this[3] = "C2551"
            this[6] = "VNP"; this[7] = "TJP"; this[11] = "IS_TIME_NOT_BUY"; this[30] = "*"
        }
        val body = "{\"status\":true,\"data\":{\"result\":[\"${fields.joinToString("|")}\"]}}"
        val failure = TicketParser.parse(body, units[0], catalog, at) as QueryResult.Failure
        assertEquals("余票数据无法解析：出现未识别的席别状态，不能确定余票", failure.message)
        val calls = mutableListOf<QueryUnit>()
        val states = SearchEngine(source { unit ->
            calls += unit
            if (unit == units[0]) failure else success
        }, 0).search(units.take(3)).toList()

        assertEquals(units.take(3), calls)
        assertTrue(states.first { it.failureCount == 1 }.running)
        val end = states.last()
        assertEquals(0, end.remainingCount)
        assertEquals(1, end.failureCount)
        assertEquals(2, end.successCount)
        assertSame(failure, end.outcomes[units[0].key])
        assertFalse(end.running)
        assertFalse(end.stopped)
        assertFalse(end.complete)
    }

    @Test fun repeatedFailuresAndNotOnSaleDoNotStopThePlan() = runTest {
        val calls = mutableListOf<QueryUnit>()
        val end = SearchEngine(source { unit ->
            calls += unit
            when (unit) {
                units[3] -> QueryResult.NotOnSale("尚未开售")
                units[4] -> success
                else -> QueryResult.Failure("network")
            }
        }, 0).search(units).toList().last()

        assertEquals(units, calls)
        assertEquals(3, end.failureCount)
        assertEquals(1, end.unopenedCount)
        assertEquals(1, end.successCount)
        assertEquals(0, end.remainingCount)
        assertFalse(end.complete)
        assertFalse(end.stopped)
    }

    @Test fun thrownQueryExceptionIsRecordedAndLaterQueryRuns() = runTest {
        val end = SearchEngine(source { unit ->
            if (unit == units[0]) throw IOException("connection reset")
            success
        }, 0).search(units.take(2)).toList().last()

        assertEquals("connection reset", (end.outcomes[units[0].key] as QueryResult.Failure).message)
        assertSame(success, end.outcomes[units[1].key])
        assertEquals(0, end.remainingCount)
        assertFalse(end.stopped)
    }

    @Test fun cancellationKeepsCompletedResultsAndSkipsLaterQueries() = runTest {
        val calls = mutableListOf<QueryUnit>()
        var latest: SearchProgress? = null
        val job = launch {
            SearchEngine(source { unit ->
                calls += unit
                if (unit == units[1]) awaitCancellation()
                success
            }, 0).search(units.take(3)).collect { latest = it }
        }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(units.take(2), calls)
        assertEquals(mapOf(units[0].key to success), latest?.outcomes)
        assertFalse(latest?.complete ?: true)
    }

    @Test fun sourceCancellationPropagatesWithoutRecordingFailure() = runTest {
        val cancelled = CancellationException("cancel query")
        val states = mutableListOf<SearchProgress>()
        val calls = mutableListOf<QueryUnit>()
        try {
            SearchEngine(source { unit -> calls += unit; throw cancelled }, 0)
                .search(units.take(2)).toList(states)
            fail("Cancellation must propagate")
        } catch (e: CancellationException) {
            assertSame(cancelled, e)
        }
        assertEquals(units.take(1), calls)
        assertTrue(states.last().outcomes.isEmpty())
    }

    @Test fun defaultSpacingIsPreservedAfterFailure() = runTest {
        val callTimes = mutableListOf<Long>()
        SearchEngine(source { unit ->
            callTimes += currentTime
            if (unit == units[0]) QueryResult.Failure("network") else success
        }).search(units.take(2)).toList()
        assertEquals(listOf(1100L, 2200L), callTimes)
    }
}
