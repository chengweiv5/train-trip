package cn.traintrip.core

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class NotYetSaleTest {
    private val catalog = StationCatalog.bundled()
    private val at = Instant.parse("2026-09-19T01:38:25Z")
    private val day = LocalDate.of(2026, 10, 3)
    private val unit = QueryUnit(day, catalog.byCode.getValue("BJP"), catalog.byCode.getValue("TJP"))
    private val filters = SearchFilters(startDate = day, endDate = day, people = 2, maxMinutes = 150)

    private fun row(code: String = "C2551", flag: String = "IS_TIME_NOT_BUY", text: String = "12点45分起售", seat: String = "*"): MutableList<String> =
        MutableList(39) { "" }.apply {
            this[1] = text; this[2] = "internal-$code"; this[3] = code
            this[6] = "VNP"; this[7] = "TJP"
            this[8] = "06:00"; this[9] = "06:30"; this[10] = "00:30"
            this[11] = flag; this[30] = seat
        }

    private fun parse(vararg rows: List<String>): QueryResult = TicketParser.parse(
        Gson().toJson(mapOf("status" to true, "data" to mapOf("result" to rows.map { it.joinToString("|") }))), unit, catalog, at)

    private fun requireSuccess(result: QueryResult): QueryResult.Success {
        assertTrue("Expected successful parse, got $result", result is QueryResult.Success)
        return result as QueryResult.Success
    }

    @Test fun c2551StarPreservesNotYetSaleAndOpeningTime() {
        val trip = requireSuccess(parse(row())).trips.single()
        assertEquals(SaleState.NOT_YET, trip.saleState)
        assertEquals("12点45分起售", trip.saleText)
        val seat = trip.seats.getValue(SeatType.SECOND)
        assertEquals("*", seat.raw)
        assertEquals("尚未起售", seat.label())
        assertNull(seat.count)
        for (people in listOf(1, 2)) {
            assertFalse(seat.confirmedFor(people))
        }
        assertFalse(trip.isSaleable(at))
    }

    @Test fun capturedOfficialResponseRetainsAllRowsWithoutUnknownSeats() {
        val body = javaClass.getResourceAsStream("/official-not-yet-sale-sanitized.json")!!.bufferedReader().use { it.readText() }
        val trips = requireSuccess(TicketParser.parse(body, unit, catalog, at)).trips
        assertEquals(310, trips.size)
        assertEquals(291, trips.count { it.saleState == SaleState.NOT_YET })
        assertEquals(6, trips.count { it.saleState == SaleState.OPEN })
        val placeholders = trips.flatMap { it.seats.values }.filter { it.raw == "*" }
        assertEquals(1173, placeholders.size)
        assertTrue(placeholders.all { it.label() == "尚未起售" && !it.confirmedFor(2) })
        assertTrue(trips.none { trip -> trip.seats.values.any { it.kind == AvailabilityKind.UNKNOWN } })
        val c2551 = trips.single { it.trainCode == "C2551" && it.to.code == "TJP" }
        assertEquals("12点45分起售", c2551.saleText)
        assertTrue(aggregate(trips, filters, now = at).flatMap { it.trips }.all { it.saleState == SaleState.OPEN })
    }

    @Test fun mixedResponseKeepsOpenSeatsAndExcludesAllUnopenedTripsFromResults() {
        val trips = requireSuccess(parse(row(), row("G101", "Y", "预订", "8"), row("C2001", seat = "8"))).trips
        assertEquals(3, trips.size)
        assertEquals(listOf("G101"), aggregate(trips, filters, now = at).single().trips.map { it.trainCode })
        val unopened = trips.filter { it.saleState == SaleState.NOT_YET }
        assertEquals(2, unopened.size)
        assertTrue(aggregate(unopened, filters, now = at).isEmpty())
    }

    @Test fun everySeatFieldAcceptsStarOnlyForUnopenedTrain() {
        val fields = row().apply { SeatType.entries.forEach { this[it.field] = "*" } }
        val trip = requireSuccess(parse(fields)).trips.single()
        assertEquals(SeatType.entries.size, trip.seats.size)
        assertTrue(trip.seats.values.all { it.raw == "*" && it.label() == "尚未起售" })
    }

    @Test fun starWithoutUnopenedContextStillFails() {
        assertEquals(AvailabilityKind.UNKNOWN, TicketParser.availability("*").kind)
        for ((flag, text) in listOf("Y" to "预订", "Y" to "12点45分起售", "N" to "预订", "IS_TIME_NOT_BUY" to "列车运行图调整,暂停发售")) {
            val result = parse(row(flag = flag, text = text))
            assertTrue("$flag / $text should reject star", result is QueryResult.Failure)
            assertEquals("余票数据无法解析：出现未识别的席别状态，不能确定余票", (result as QueryResult.Failure).message)
        }
    }

    @Test fun otherUnknownValuesAndStructuralErrorsStillFailBeforeSale() {
        for (raw in listOf("未知状态", "**", "-1")) {
            assertTrue(parse(row(seat = raw)) is QueryResult.Failure)
        }
        assertTrue(parse(row().apply { this[6] = "ZZZ" }) is QueryResult.Failure)
        assertTrue(parse(row().apply { this[11] = "NEW_STATE" }) is QueryResult.Failure)
    }
}
