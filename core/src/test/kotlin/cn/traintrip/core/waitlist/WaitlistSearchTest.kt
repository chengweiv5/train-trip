package cn.traintrip.core.waitlist

import cn.traintrip.core.*
import com.google.gson.Gson
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class WaitlistSearchTest {
    private val catalog = StationCatalog.bundled()
    private val date = today().plusDays(1)
    private val from = catalog.byCode.getValue("VNP")
    private val to = catalog.byCode.getValue("JGK")
    private fun trip() = Trip(date, "train-1", "G37", from, to, LocalTime.of(8, 0),
        LocalTime.of(10, 0), 120, SaleState.OPEN, "预订",
        mapOf(SeatType.SECOND to TicketParser.availability("无"), SeatType.FIRST to TicketParser.availability("无")),
        Instant.now(), waitlistTrainFlag = true, waitlistContextAvailable = true)

    @Test fun parserRetainsSeatSpecificRestrictionWithoutDiscardingOtherSeats() {
        val row = MutableList(49) { "" }
        row[0] = "anonymous-context"; row[1] = "预订"; row[2] = "train-1"; row[3] = "G37"
        row[6] = "VNP"; row[7] = "JGK"; row[8] = "08:00"; row[9] = "10:00"
        row[10] = "02:00"; row[11] = "Y"; row[30] = "无"; row[31] = "无"
        row[37] = "1"; row[38] = "O"
        val body = Gson().toJson(mapOf("status" to true, "data" to mapOf("result" to listOf(row.joinToString("|")))))
        val parsed = (TicketParser.parse(body, QueryUnit(date, from, to), catalog, Instant.now()) as QueryResult.Success).trips.single()
        assertEquals(WaitlistEligibility.RESTRICTED, parsed.waitlistEligibility(SeatType.SECOND))
        assertEquals(WaitlistEligibility.AVAILABLE, parsed.waitlistEligibility(SeatType.FIRST))
        assertEquals("O", parsed.waitlistSeatLimit)
    }

    @Test fun filtersKeepOtherSeatAndHiddenSelectionButRemoveIncompatibleRouteOnApply() {
        val train = trip().copy(waitlistSeatLimit = "O")
        val filters = WaitlistFilters(originCityId = from.cityId, destinationCityId = to.cityId,
            startDate = date, endDate = date, seats = setOf(SeatType.SECOND, SeatType.FIRST))
        val visible = filters.choices(listOf(train))
        assertEquals(listOf(SeatType.FIRST), visible.map { it.seat })
        val selected = visible.single()
        val hiddenFilters = filters.copy(seats = setOf(SeatType.SECOND))
        assertTrue(hiddenFilters.choices(listOf(train)).isEmpty())
        assertTrue(hiddenFilters.compatible(selected))
        assertFalse(hiddenFilters.copy(destinationCityId = from.cityId).compatible(selected))
        assertFalse(hiddenFilters.copy(startDate = date.plusDays(1), endDate = date.plusDays(1)).compatible(selected))
        assertFalse(hiddenFilters.copy(originStations = setOf("BJP")).compatible(selected))
    }

    @Test fun unsupportedOrUnknownSeatsNeverBecomeSelectableAndCurrentTicketsStaySeparate() {
        val train = trip()
        assertEquals(WaitlistEligibility.UNKNOWN, train.copy(waitlistContextAvailable = false).waitlistEligibility(SeatType.FIRST))
        assertEquals(WaitlistEligibility.UNSUPPORTED, train.copy(waitlistTrainFlag = false).waitlistEligibility(SeatType.FIRST))
        assertEquals(WaitlistEligibility.HAS_TICKETS,
            train.copy(seats = mapOf(SeatType.FIRST to TicketParser.availability("1"))).waitlistEligibility(SeatType.FIRST))
        assertEquals(WaitlistEligibility.UNKNOWN,
            train.copy(seats = mapOf(SeatType.PREFERRED to TicketParser.availability("无"))).waitlistEligibility(SeatType.PREFERRED))
    }

    @Test fun pastDepartureAndOfficialCutoffCannotBeSelected() {
        val now = Instant.now()
        val local = now.atZone(BEIJING_ZONE)
        val tooLate = trip().copy(date = local.toLocalDate(), departure = local.toLocalTime().minusMinutes(1))
        assertEquals(WaitlistEligibility.UNSUPPORTED, tooLate.waitlistEligibility(SeatType.FIRST, now))
        val near = now.plusSeconds(10 * 60).atZone(BEIJING_ZONE)
        val nearTrip = trip().copy(date = near.toLocalDate(), departure = near.toLocalTime())
        assertEquals(WaitlistEligibility.UNSUPPORTED, nearTrip.waitlistEligibility(SeatType.FIRST, now))
        assertEquals(WaitlistEligibility.UNSUPPORTED,
            trip().copy(seats = mapOf(SeatType.STANDING to TicketParser.availability("无"))).waitlistEligibility(SeatType.STANDING))
    }

    @Test fun queryPlanUsesBothStationSetsAndRejectsOutOfSaleDatesAndForeignStations() {
        val filters = WaitlistFilters(originCityId = from.cityId, destinationCityId = to.cityId,
            originStations = setOf("VNP"), destinationStations = setOf("JGK"), startDate = date, endDate = date)
        val source = SourceInfo("query", today(), date, catalog, Instant.now())
        assertEquals(listOf(QueryUnit(date, from, to)), filters.plan(source))
        assertThrows(IllegalArgumentException::class.java) { filters.copy(destinationStations = setOf("BJP")).plan(source) }
        assertThrows(IllegalArgumentException::class.java) { filters.copy(endDate = date.plusDays(1)).plan(source) }
    }
}
