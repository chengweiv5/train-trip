package cn.traintrip.core

import java.time.*

val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
fun today(): LocalDate = LocalDate.now(BEIJING_ZONE)

enum class SeatType(val label: String, val field: Int) {
    SECOND("二等座",30), FIRST("一等座",31), PREFERRED("优选一等座",20), BUSINESS("商务座",32), SPECIAL("特等座",25),
    HARD_SEAT("硬座",29), SOFT_SEAT("软座",24), HARD_SLEEPER("硬卧",28), SOFT_SLEEPER("软卧",23),
    PREMIUM_SLEEPER("高级软卧",21), MOVING_SLEEPER("动卧",33), STANDING("无座",26), OTHER("其他席别",22), YB("包厢硬卧",27)
}
enum class AvailabilityKind { COUNT, AVAILABLE, NONE, WAITLIST, NOT_OFFERED, UNKNOWN }
data class SeatAvailability(val raw: String, val kind: AvailabilityKind, val count: Int? = null) {
    fun confirmedFor(people: Int) = (kind == AvailabilityKind.COUNT && (count ?: 0) >= people) || (kind == AvailabilityKind.AVAILABLE && people == 1)
    fun uncertainFor(people: Int) = kind == AvailabilityKind.AVAILABLE && people > 1
    fun label(people: Int) = when(kind) {
        AvailabilityKind.COUNT -> "$count 张"
        AvailabilityKind.AVAILABLE -> if(people == 1) "有票" else "有票，数量待核验"
        AvailabilityKind.NONE -> "无票"
        AvailabilityKind.WAITLIST -> "候补"
        AvailabilityKind.NOT_OFFERED -> "本车次不提供"
        AvailabilityKind.UNKNOWN -> "状态待确认：$raw"
    }
}
data class Station(val code: String, val name: String, val cityId: String, val cityName: String, val pinyin: String, val domestic: Boolean = true)
data class City(val id: String, val name: String, val stations: List<Station>) {
    val representative: Station get() = stations.firstOrNull { it.name == name } ?: stations.first()
}
data class SearchFilters(
    val originCityId: String = "0357", val originStations: Set<String> = emptySet(),
    val startDate: LocalDate = today().plusDays(1), val endDate: LocalDate = startDate,
    val startMinute: Int = 0, val endMinute: Int = 1440,
    val seats: Set<SeatType> = SeatType.entries.toSet(), val people: Int = 1,
    val maxMinutes: Int? = null, val destinationCityIds: Set<String> = emptySet()
) {
    fun acceptsTime(minute: Int): Boolean = when {
        startMinute == 0 && endMinute == 1440 -> true
        startMinute < endMinute -> minute >= startMinute && minute < endMinute
        else -> minute >= startMinute || minute < endMinute
    }
    fun dates(): List<LocalDate> = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
    fun validate(): String? = when {
        startDate < today() -> "出发日期不能早于今天"
        endDate < startDate -> "结束日期不能早于开始日期"
        java.time.temporal.ChronoUnit.DAYS.between(startDate,endDate) > 30 -> "一次最多查询 31 天，请缩短日期范围"
        people !in 1..20 -> "乘车人数需为 1–20 人"
        startMinute !in 0..1439 || endMinute !in 0..1440 || startMinute == endMinute -> "请设置有效的出发时段"
        seats.isEmpty() -> "请至少选择一种席别"
        maxMinutes != null && maxMinutes <= 0 -> "最长车程需大于零"
        else -> null
    }
}
data class QueryUnit(val date: LocalDate, val origin: Station, val destination: Station) {
    val key get() = "$date/${origin.code}/${destination.code}"
}
enum class SaleState { OPEN, NOT_YET, SUSPENDED, CLOSED }
data class Trip(
    val date: LocalDate, val trainId: String, val trainCode: String, val from: Station, val to: Station,
    val departure: LocalTime?, val arrival: LocalTime?, val durationMinutes: Int?,
    val saleState: SaleState, val saleText: String, val seats: Map<SeatType, SeatAvailability>,
    val queriedAt: Instant, val waitlistTrainFlag: Boolean = false, val stopCheckMinutes: Int = 0
) {
    val key get() = "$date/$trainId/${from.code}/${to.code}"
    val trainKey get() = "$date/$trainId"
    val arrivalDayOffset: Int? get() = if(departure != null && durationMinutes != null) (departure.toSecondOfDay()/60 + durationMinutes)/1440 else null
    fun isSaleable(now: Instant = Instant.now()): Boolean = saleState == SaleState.OPEN && departure != null && durationMinutes != null &&
        date.atTime(departure).atZone(BEIJING_ZONE).toInstant().minusSeconds(stopCheckMinutes.toLong()*60).isAfter(now)
    fun matchesConditions(f: SearchFilters): Boolean = date in f.startDate..f.endDate && from.cityId == f.originCityId &&
        (f.originStations.isEmpty() || from.code in f.originStations) && to.cityId != f.originCityId &&
        departure != null && f.acceptsTime(departure.toSecondOfDay()/60) && durationMinutes != null &&
        (f.maxMinutes == null || durationMinutes <= f.maxMinutes)
    fun confirmed(f: SearchFilters, now: Instant = Instant.now()) = matchesConditions(f) && isSaleable(now) && f.seats.any { seats[it]?.confirmedFor(f.people) == true }
    fun uncertain(f: SearchFilters, now: Instant = Instant.now()) = matchesConditions(f) && isSaleable(now) && !confirmed(f,now) && f.seats.any { seats[it]?.uncertainFor(f.people) == true }
}
data class CityResult(val cityId: String, val cityName: String, val trips: List<Trip>) {
    val trainCount get() = trips.map { it.trainKey }.distinct().size
    val shortestMinutes get() = trips.mapNotNull { it.durationMinutes }.minOrNull() ?: Int.MAX_VALUE
}
fun aggregate(trips: List<Trip>, filters: SearchFilters, uncertain: Boolean = false, now: Instant = Instant.now()): List<CityResult> =
    trips.distinctBy { it.key }.filter { if(uncertain) it.uncertain(filters,now) else it.confirmed(filters,now) }
        .groupBy { it.to.cityId }.map { (id, list) -> CityResult(id,list.first().to.cityName,list) }.sortedBy { it.shortestMinutes }

data class SourceInfo(val queryPath: String, val saleStart: LocalDate, val saleEnd: LocalDate, val catalog: StationCatalog, val fetchedAt: Instant)
sealed interface QueryResult {
    data class Success(val trips: List<Trip>, val receivedAt: Instant): QueryResult
    data class Failure(val message: String, val stopSearch: Boolean = false): QueryResult
    data class NotOnSale(val message: String): QueryResult
}
data class SearchProgress(val plan: List<QueryUnit>, val outcomes: Map<String, QueryResult> = emptyMap(), val running: Boolean = false, val stopped: Boolean = false) {
    val successCount get() = outcomes.values.count { it is QueryResult.Success }
    val failureCount get() = outcomes.values.count { it is QueryResult.Failure }
    val unopenedCount get() = outcomes.values.count { it is QueryResult.NotOnSale }
    val remainingCount get() = plan.size - outcomes.size
    val trips get() = outcomes.values.filterIsInstance<QueryResult.Success>().flatMap { it.trips }.distinctBy { it.key }
    val complete get() = !running && remainingCount == 0 && failureCount == 0 && unopenedCount == 0
}
