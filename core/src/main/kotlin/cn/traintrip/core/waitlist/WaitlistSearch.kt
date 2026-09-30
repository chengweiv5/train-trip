package cn.traintrip.core.waitlist

import cn.traintrip.core.*
import java.time.LocalDate
import java.time.Instant

/** Only mappings verified in the official query UI. Unknown seat families stay unselectable. */
val SeatType.waitlistCode: String?
    get() = when (this) {
        SeatType.SECOND -> "O"
        SeatType.FIRST -> "M"
        SeatType.BUSINESS -> "9"
        SeatType.SPECIAL -> "P"
        SeatType.PREMIUM_SLEEPER -> "6"
        SeatType.SOFT_SLEEPER -> "4"
        SeatType.MOVING_SLEEPER -> "F"
        SeatType.HARD_SLEEPER -> "3"
        SeatType.SOFT_SEAT -> "2"
        SeatType.HARD_SEAT -> "1"
        else -> null
    }

enum class WaitlistEligibility(val label: String) {
    AVAILABLE("可加入候补"), RESTRICTED("候补订单较多"), HAS_TICKETS("有票，可直接购票"),
    UNSUPPORTED("暂不支持候补"), NOT_ON_SALE("尚未起售"), UNKNOWN("候补状态待核验"),
}

fun Trip.waitlistEligibility(seat: SeatType, now: Instant = Instant.now()): WaitlistEligibility {
    if (saleState == SaleState.NOT_YET) return WaitlistEligibility.NOT_ON_SALE
    if (saleState != SaleState.OPEN) return WaitlistEligibility.UNSUPPORTED
    val availability = seats[seat] ?: return WaitlistEligibility.UNSUPPORTED
    if (availability.confirmedFor(1)) return WaitlistEligibility.HAS_TICKETS
    if (availability.kind == AvailabilityKind.UNKNOWN) return WaitlistEligibility.UNKNOWN
    if (availability.kind !in setOf(AvailabilityKind.NONE, AvailabilityKind.WAITLIST))
        return WaitlistEligibility.UNSUPPORTED
    if (seat == SeatType.STANDING || seat == SeatType.OTHER) return WaitlistEligibility.UNSUPPORTED
    if (!waitlistTrainFlag) return WaitlistEligibility.UNSUPPORTED
    val departsAt = departure?.let { date.atTime(it).atZone(BEIJING_ZONE).toInstant() }
        ?: return WaitlistEligibility.UNKNOWN
    // Official query UI's cl() excludes departures within 20 minutes. This is only an
    // anonymous entry gate, never a guarantee of the authenticated deadline or capacity.
    if (!departsAt.isAfter(now.plusSeconds(maxOf(20, stopCheckMinutes).toLong() * 60)))
        return WaitlistEligibility.UNSUPPORTED
    val code = seat.waitlistCode ?: return WaitlistEligibility.UNKNOWN
    if (!waitlistContextAvailable) return WaitlistEligibility.UNKNOWN
    return if (code in waitlistSeatLimit) WaitlistEligibility.RESTRICTED else WaitlistEligibility.AVAILABLE
}

fun Trip.waitlistDemand(seat: SeatType): WaitlistDemand? =
    seat.waitlistCode?.let { WaitlistDemand(date, trainId, from.code, to.code, it) }

enum class WaitlistTrainKind(val label: String) { ALL("不限"), FAST("高铁 / 动车"), REGULAR("普通列车") }
enum class WaitlistSort(val label: String) { DEPARTURE("出发最早"), DURATION("车程最短") }

/** Independent of ordinary-ticket people count and destination exploration. */
data class WaitlistFilters(
    val originCityId: String = "110000",
    val destinationCityId: String = "370100",
    val originStations: Set<String> = emptySet(),
    val destinationStations: Set<String> = emptySet(),
    val startDate: LocalDate = today().plusDays(1),
    val endDate: LocalDate = startDate,
    val startMinute: Int = 0,
    val endMinute: Int = 1440,
    val departurePeriods: Set<DeparturePeriod> = emptySet(),
    val seats: Set<SeatType> = SeatType.entries.filter { it.waitlistCode != null }.toSet(),
    val trainKind: WaitlistTrainKind = WaitlistTrainKind.ALL,
    val maxMinutes: Int? = null,
    val onlyAvailable: Boolean = true,
    val sort: WaitlistSort = WaitlistSort.DEPARTURE,
) {
    /** A value copy solely for the shared date/time controls; it never touches normal search state. */
    fun controls() = SearchFilters(originCityId, originStations, startDate, endDate, startMinute,
        endMinute, seats, 1, maxMinutes, setOf(destinationCityId), departurePeriods)

    fun withControls(value: SearchFilters) = copy(startDate = value.startDate, endDate = value.endDate,
        startMinute = value.startMinute, endMinute = value.endMinute, departurePeriods = value.departurePeriods,
        seats = value.seats, maxMinutes = value.maxMinutes)

    fun validate(catalog: StationCatalog): String? {
        controls().validate()?.let { return it }
        val origin = catalog.byCity[originCityId]
        val destination = catalog.byCity[destinationCityId]
        return when {
            origin?.supported != true -> "请选择可查询的出发城市"
            destination?.supported != true -> "请选择可查询的目的地城市"
            originCityId == destinationCityId -> "出发地和目的地不能相同"
            !origin.stations.map { it.code }.containsAll(originStations) -> "请重新选择出发车站"
            !destination.stations.map { it.code }.containsAll(destinationStations) -> "请重新选择到达车站"
            else -> null
        }
    }

    fun plan(source: SourceInfo): List<QueryUnit> {
        require(validate(source.catalog) == null) { validate(source.catalog).orEmpty() }
        require(startDate >= source.saleStart && endDate <= source.saleEnd) {
            "当前官方可查询日期为 ${source.saleStart} 至 ${source.saleEnd}，请调整日期"
        }
        val from = source.catalog.byCity.getValue(originCityId)
        val to = source.catalog.byCity.getValue(destinationCityId)
        val origins = if (originStations.isEmpty()) from.queryStations else from.stations.filter { it.code in originStations }
        val destinations = if (destinationStations.isEmpty()) to.queryStations else to.stations.filter { it.code in destinationStations }
        return controls().dates().flatMap { date ->
            origins.flatMap { origin -> destinations.map { destination -> QueryUnit(date, origin, destination) } }
        }.distinctBy { it.key }
    }

    fun compatible(choice: WaitlistChoice): Boolean = with(choice.trip) {
        date in startDate..endDate && from.cityId == originCityId && to.cityId == destinationCityId &&
            (originStations.isEmpty() || from.code in originStations) &&
            (destinationStations.isEmpty() || to.code in destinationStations)
    }

    fun choices(trips: List<Trip>): List<WaitlistChoice> {
        val filters = controls()
        val matching = trips.distinctBy { it.key }.flatMap { trip ->
            seats.sortedBy { it.ordinal }.map { WaitlistChoice(trip, it) }
        }.filter { choice ->
            val trip = choice.trip
            compatible(choice) && trip.departure?.let { filters.acceptsTime(it.hour * 60 + it.minute) } == true &&
                (maxMinutes == null || trip.durationMinutes?.let { it <= maxMinutes } == true) &&
                when (trainKind) {
                    WaitlistTrainKind.ALL -> true
                    WaitlistTrainKind.FAST -> trip.trainCode.firstOrNull() in listOf('G', 'D', 'C')
                    WaitlistTrainKind.REGULAR -> trip.trainCode.firstOrNull() !in listOf('G', 'D', 'C')
                } && (!onlyAvailable || choice.eligibility == WaitlistEligibility.AVAILABLE)
        }
        return when (sort) {
            WaitlistSort.DEPARTURE -> matching.sortedWith(compareBy({ it.trip.date }, { it.trip.departure }, { it.trip.trainCode }, { it.seat.ordinal }))
            WaitlistSort.DURATION -> matching.sortedWith(compareBy({ it.trip.durationMinutes ?: Int.MAX_VALUE }, { it.trip.date }, { it.trip.departure }))
        }
    }
}

data class WaitlistChoice(val trip: Trip, val seat: SeatType) {
    val demand: WaitlistDemand? get() = trip.waitlistDemand(seat)
    val eligibility: WaitlistEligibility get() = trip.waitlistEligibility(seat)
}
