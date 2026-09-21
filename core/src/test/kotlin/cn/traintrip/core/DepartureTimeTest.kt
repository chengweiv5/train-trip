package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DepartureTimeTest {
    private val day=LocalDate.of(2030,10,1)
    private val base=SearchFilters(startDate=day)

    @Test fun everyPresetCombinationMatchesOnlyItsUnion() {
        val periods=DeparturePeriod.entries
        for(mask in 1..15) {
            val chosen=periods.filterIndexed { i,_->mask and (1 shl i)!=0 }.toSet()
            val filters=base.withDeparturePeriods(chosen)
            for(minute in 0..1439) assertEquals("mask=$mask minute=$minute",
                chosen.any { minute in it.startMinute until it.endMinute },filters.acceptsTime(minute))
            assertNull(filters.validate())
            assertFalse(filters.acceptsTime(-1));assertFalse(filters.acceptsTime(1440))
        }
    }

    @Test fun togglesNormalizeAllDayAndKeepLegacySingleRangeSelection() {
        val morning=base.toggleDeparturePeriod(DeparturePeriod.MORNING)
        assertEquals(setOf(DeparturePeriod.MORNING),morning.selectedDeparturePeriods)
        val multi=morning.toggleDeparturePeriod(DeparturePeriod.EVENING)
        assertEquals(setOf(DeparturePeriod.MORNING,DeparturePeriod.EVENING),multi.selectedDeparturePeriods)
        assertFalse(multi.isAllDay)
        assertEquals(360,multi.startMinute);assertEquals(720,multi.endMinute)
        assertTrue(morning.toggleDeparturePeriod(DeparturePeriod.MORNING).isAllDay)
        val all=base.withDeparturePeriods(DeparturePeriod.entries.toSet())
        assertTrue(all.isAllDay);assertTrue(all.departurePeriods.isEmpty())
        val legacy=base.copy(startMinute=720,endMinute=1080)
        assertEquals(setOf(DeparturePeriod.AFTERNOON),legacy.selectedDeparturePeriods)
        assertTrue(legacy.toggleDeparturePeriod(DeparturePeriod.AFTERNOON).isAllDay)
        assertFalse(base.acceptsTime(1440))
    }

    @Test fun customRangeReplacesPresetsAndStillWrapsMidnight() {
        val multi=base.withDeparturePeriods(setOf(DeparturePeriod.MORNING,DeparturePeriod.EVENING))
        val custom=multi.withCustomTime(1320,390)
        assertTrue(custom.departurePeriods.isEmpty());assertTrue(custom.selectedDeparturePeriods.isEmpty())
        for(minute in listOf(1320,1439,0,389))assertTrue(custom.acceptsTime(minute))
        for(minute in listOf(390,720,1319))assertFalse(custom.acceptsTime(minute))
        assertEquals(setOf(DeparturePeriod.AFTERNOON),custom.toggleDeparturePeriod(DeparturePeriod.AFTERNOON).selectedDeparturePeriods)
        assertTrue(multi.withCustomTime(0,1440).isAllDay)
        assertNotNull(multi.withCustomTime(800,800).validate())
        assertNull(multi.copy(startMinute=-1,endMinute=-1).validate())
        assertEquals(multi.departurePeriods,multi.copy(people=3).departurePeriods)
    }

    @Test fun aggregationUsesBothPeriodsWithoutFillingGapOrDuplicatingTrips() {
        val catalog=StationCatalog.bundled();val now=Instant.parse("2030-09-30T00:00:00Z")
        val origin=catalog.byCode.getValue("BJP");val destination=catalog.byCode.getValue("TJP")
        val f=base.copy(destinationCityIds=setOf(destination.cityId)).withDeparturePeriods(setOf(DeparturePeriod.MORNING,DeparturePeriod.EVENING))
        val trips=listOf(360,719,720,1079,1080,1439).map { m ->
            Trip(day,"G$m","G$m",origin,destination,LocalTime.of(m/60,m%60),LocalTime.NOON,90,SaleState.OPEN,"",
                mapOf(SeatType.SECOND to SeatAvailability("有",AvailabilityKind.AVAILABLE)),now)
        }
        val found=aggregate(trips+trips,f,now).single()
        assertEquals(4,found.trainCount)
        assertEquals(setOf("G360","G719","G1080","G1439"),found.trips.map { it.trainCode }.toSet())
    }
}
