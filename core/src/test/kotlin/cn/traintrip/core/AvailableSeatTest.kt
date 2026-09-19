package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AvailableSeatTest {
    private val catalog = StationCatalog.bundled()
    private val day = LocalDate.of(2030, 10, 1)
    private val now = Instant.parse("2030-09-30T00:00:00Z")
    private val unit = QueryUnit(day, catalog.byCode.getValue("BJP"), catalog.byCode.getValue("TJP"))
    private val filters = SearchFilters(startDate = day, endDate = day, seats = setOf(SeatType.SECOND), destinationCityIds = setOf("120000"))

    private fun parsedTrip(raw: String = "有"): Trip {
        val fields = MutableList(39) { "" }.apply {
            this[1] = "预订"; this[2] = "available-G1"; this[3] = "G1"
            this[6] = "VNP"; this[7] = "TJP"
            this[8] = "08:00"; this[9] = "08:30"; this[10] = "00:30"
            this[11] = "Y"; this[30] = raw
        }
        val body = "{\"status\":true,\"data\":{\"result\":[\"${fields.joinToString("|")}\"]}}"
        return (TicketParser.parse(body, unit, catalog, now) as QueryResult.Success).trips.single()
    }

    @Test fun availableCountsForEverySupportedPartySizeWithoutInventingCounts() {
        val trip = parsedTrip()
        val seat = trip.seats.getValue(SeatType.SECOND)
        assertEquals("有", seat.raw)
        assertEquals(AvailabilityKind.AVAILABLE, seat.kind)
        assertNull(seat.count)
        assertEquals("有票", seat.label())
        for (people in 1..20) {
            val cities = aggregate(listOf(trip), filters.copy(people = people), now = now)
            assertEquals("party size $people", listOf("120000"), cities.map { it.cityId })
            assertEquals(1, cities.single().trainCount)
        }
    }

    @Test fun explicitCountsStillRequireEnoughSeatsInOneSeatType() {
        assertTrue(aggregate(listOf(parsedTrip("1")), filters.copy(people = 2), now = now).isEmpty())
        assertEquals(1, aggregate(listOf(parsedTrip("2")), filters.copy(people = 2), now = now).size)
        val split = parsedTrip("1").copy(seats = mapOf(SeatType.SECOND to TicketParser.availability("1"), SeatType.FIRST to TicketParser.availability("1")))
        assertTrue(aggregate(listOf(split), filters.copy(people = 2, seats = setOf(SeatType.SECOND, SeatType.FIRST)), now = now).isEmpty())
        for (raw in listOf("无", "0", "候补", "--", "")) {
            assertTrue("seat $raw", aggregate(listOf(parsedTrip(raw)), filters, now = now).isEmpty())
        }
    }

    @Test fun availableStillRespectsSaleStateCutoffAndFilters() {
        val trip = parsedTrip()
        for (state in listOf(SaleState.NOT_YET, SaleState.SUSPENDED, SaleState.CLOSED)) {
            assertTrue(aggregate(listOf(trip.copy(saleState = state)), filters, now = now).isEmpty())
        }
        val departure = day.atTime(8, 0).atZone(BEIJING_ZONE).toInstant()
        assertTrue(aggregate(listOf(trip), filters, now = departure).isEmpty())
        assertTrue(aggregate(listOf(trip.copy(stopCheckMinutes = 5)), filters, now = departure.minusSeconds(300)).isEmpty())
        for (restricted in listOf(
            filters.copy(seats = setOf(SeatType.FIRST)), filters.copy(originStations = setOf("BXP")),
            filters.copy(destinationCityIds = setOf("370200")), filters.copy(maxMinutes = 20),
            filters.copy(startMinute = 540, endMinute = 600), filters.copy(startDate = day.plusDays(1), endDate = day.plusDays(1))
        )) assertTrue(aggregate(listOf(trip), restricted, now = now).isEmpty())
    }
}
