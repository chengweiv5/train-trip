package cn.traintrip.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CoreTest {
    private val day = LocalDate.of(2030,10,1)
    private val now = Instant.parse("2030-09-30T00:00:00Z")
    private val catalog = StationCatalog.bundled()
    private val unit = QueryUnit(day,catalog.byCode.getValue("BJP"),catalog.byCode.getValue("TJP"))
    private fun trip(seats: Map<SeatType,SeatAvailability> = mapOf(SeatType.SECOND to TicketParser.availability("有"))) = Trip(day,"internal1","G1",catalog.byCode.getValue("VNP"),catalog.byCode.getValue("TJP"),LocalTime.of(23,59),LocalTime.of(1,25),86,SaleState.OPEN,"预订",seats,now)
    private fun filters() = SearchFilters(startDate=day,endDate=day)
    @Test fun inventoryDoesNotInventCountsOrCombineSeats() {
        assertTrue(trip().confirmed(filters(),now)); assertTrue(trip().confirmed(filters().copy(people=2),now))
        val t=trip(mapOf(SeatType.SECOND to TicketParser.availability("1"),SeatType.FIRST to TicketParser.availability("1")))
        assertFalse(t.confirmed(filters().copy(people=2),now))
        for(raw in listOf("无","0","候补","","--","异常")) assertFalse(TicketParser.availability(raw).confirmedFor(1))
        assertTrue(TicketParser.availability("3").confirmedFor(3)); assertFalse(TicketParser.availability("3").confirmedFor(4))
    }
    @Test fun midnightAndEndpoints() {
        val f=filters().copy(startMinute=22*60,endMinute=6*60)
        assertTrue(f.acceptsTime(22*60)); assertTrue(f.acceptsTime(0)); assertFalse(f.acceptsTime(6*60)); assertFalse(f.acceptsTime(12*60))
        assertEquals(1,trip().arrivalDayOffset)
        assertEquals(2,filters().copy(endDate=day.plusDays(1)).dates().size)
    }
    @Test fun cityGroupingAndDuplicates() {
        val t=trip(); val b=t.copy(to=catalog.byCode.getValue("YKP"),durationMinutes=110)
        val results=aggregate(listOf(t,t,b),filters(),now=now)
        assertEquals(1,results.size); assertEquals("天津",results.first().cityName)
        assertEquals(1,results.first().trainCount); assertEquals(2,results.first().trips.size)
        assertTrue(aggregate(listOf(t.copy(saleState=SaleState.SUSPENDED)),filters(),now=now).isEmpty())
        assertFalse(t.confirmed(filters().copy(originStations=setOf("BXP")),now))
        assertFalse(t.confirmed(filters(),day.atTime(23,59).atZone(BEIJING_ZONE).toInstant()))
    }
    @Test fun sourceParserRejectsUnknownProtocolAndPreservesBoardingDate() {
        val f=MutableList(56){""}; f[2]="train";f[3]="K1";f[6]="VNP";f[7]="TJP";f[8]="23:59";f[9]="01:25";f[10]="01:26";f[11]="Y";f[13]="20300930";f[30]="有"
        fun payload() = "{\"status\":true,\"data\":{\"result\":[\"${f.joinToString("|")}\"]}}"
        val result=TicketParser.parse(payload(),unit,catalog,now) as QueryResult.Success
        assertEquals(day,result.trips.single().date); assertEquals(1,result.trips.single().arrivalDayOffset)
        f[6]="ZZZ";assertTrue(TicketParser.parse(payload(),unit,catalog,now) is QueryResult.Failure)
        assertTrue(TicketParser.parse("<html>failure</html>",unit,catalog,now) is QueryResult.Failure)
        assertTrue(TicketParser.parse("{\"status\":true,\"data\":{}}",unit,catalog,now) is QueryResult.Failure)
    }
    @Test fun searchFailureDoesNotBlockAndRetryKeepsSuccesses() = runBlocking {
        val units=(0..2).map { unit.copy(date=day.plusDays(it.toLong())) }
        var count=0
        val source=object:TicketSource {
            override suspend fun initialize():SourceInfo = error("unused")
            override suspend fun query(unit:QueryUnit):QueryResult { count++;return if(count==2) QueryResult.Failure("network") else QueryResult.Success(emptyList(),now) }
        }
        val end=SearchEngine(source,0).search(units).toList().last()
        assertFalse(end.complete);assertEquals(0,end.remainingCount);assertEquals(1,end.failureCount)
        assertEquals(2,end.successCount);assertFalse(end.stopped);assertFalse(end.running)
        val resume=SearchEngine(source,0).search(units,end.outcomes.filterValues { it !is QueryResult.Failure }).toList().last()
        assertTrue(resume.complete);assertEquals(4,count)
    }
    @Test fun cancellationDoesNotBecomeAnEmptySuccess() = runBlocking {
        val source=object:TicketSource {
            override suspend fun initialize():SourceInfo = error("unused")
            override suspend fun query(unit:QueryUnit):QueryResult { delay(10000);return QueryResult.Success(emptyList(),now) }
        }
        var latest:SearchProgress?=null
        val job=launch { SearchEngine(source,0).search(listOf(unit)).collect { latest=it } }
        yield();job.cancelAndJoin()
        assertEquals(0,latest?.outcomes?.size);assertFalse(latest?.complete ?: true)
    }
    @Test fun historicalOfficialFixtureMatchesKnownFields() {
        val body=javaClass.getResourceAsStream("/official-sample-sanitized.json")!!.bufferedReader().use { it.readText() }
        val result=TicketParser.parse(body,unit,catalog,now) as QueryResult.Success
        val c=result.trips.first { it.trainCode=="C2551" && it.to.code=="TJP" }
        assertEquals("北京南",c.from.name)
        assertEquals(LocalTime.of(6,0),c.departure)
        assertEquals(30,c.durationMinutes)
        assertEquals(AvailabilityKind.AVAILABLE,c.seats.getValue(SeatType.SECOND).kind)
        assertEquals(7,result.trips.first { it.trainCode=="K2601" }.seats.getValue(SeatType.HARD_SEAT).count)
        assertEquals(1,result.trips.first { it.trainCode=="K411" }.arrivalDayOffset)
    }
}
