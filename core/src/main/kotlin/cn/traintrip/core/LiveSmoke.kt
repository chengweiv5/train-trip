package cn.traintrip.core

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val source = OfficialTicketSource()
    val info = source.initialize()
    println("SOURCE catalog=${info.catalog.stations.size},cities=${info.catalog.cities.size},sale=${info.saleStart}..${info.saleEnd},route=${info.queryPath}")
    val origin = info.catalog.byCode.getValue("BJP")
    val day = today().plusDays(1)
    for(code in listOf("TJP","SJP","BEP")) {
        val station = info.catalog.byCode.getValue(code)
        val result = source.query(QueryUnit(day,origin,station))
        when(result) {
            is QueryResult.Success -> {
                val filters = SearchFilters(startDate=day,endDate=day)
                println("LIVE ${station.cityName}: options=${result.trips.size}, trains=${result.trips.map { it.trainKey }.distinct().size}, available=${result.trips.count { it.confirmed(filters) }}, receivedAt=${result.receivedAt}")
            }
            else -> error("Query did not succeed: $result")
        }
        kotlinx.coroutines.delay(1500)
    }
}
