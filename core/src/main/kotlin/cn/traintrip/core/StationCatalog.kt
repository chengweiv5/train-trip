package cn.traintrip.core

class StationCatalog(rawStations: List<Station>) {
    val stations = rawStations.map(AdministrativeCatalog::normalize)
    val byCode = stations.associateBy { it.code }
    val unmappedStations = stations.filter { it.domestic && it.cityId.startsWith("unmapped:") }
    val provinces = AdministrativeCatalog.provinces
    private val grouped = stations.filter { it.domestic }.groupBy { it.cityId }
    val cities = AdministrativeCatalog.cities.map { it.copy(stations=grouped[it.id].orEmpty()) }
    val byCity = cities.associateBy { it.id }
    fun resolveCityId(id: String): String? = AdministrativeCatalog.resolveId(id)
    fun searchDestinations(query: String): List<City> = cities.filter { query.isBlank() || it.province.matches(query) || it.matches(query) }
    fun resolveDestinationIds(ids: Set<String>): Set<String> = ids.flatMap { id ->
        if(id in byCity) listOf(id) else stations.filter { it.railwayCityId == id && it.cityId in byCity }.map { it.cityId }
            .ifEmpty { listOfNotNull(resolveCityId(id)) }
    }.toSet()
    fun normalize(filters: SearchFilters): SearchFilters {
        val selectedOrigins=filters.originStations.mapNotNull { byCode[it] }.filter {
            it.railwayCityId == filters.originCityId || it.cityId == filters.originCityId
        }.map { it.cityId }.distinct()
        val origin = selectedOrigins.singleOrNull()?.takeIf { it in byCity }
            ?: resolveCityId(filters.originCityId)?.takeIf { byCity[it]?.supported == true } ?: "110000"
        return filters.copy(originCityId=origin,originStations=filters.originStations.filter { byCode[it]?.cityId == origin }.toSet(),
            destinationCityIds=resolveDestinationIds(filters.destinationCityIds)-origin)
    }
    fun initialDestinations(origin: String): Set<String> {
        val names = setOf("天津","石家庄","秦皇岛","承德","保定","济南","青岛","太原","大同","呼和浩特","沈阳","大连","长春","哈尔滨","郑州","西安","南京","上海","杭州","武汉","长沙","合肥","洛阳","泰安")
        return cities.filter { it.id != resolveCityId(origin) && it.name in names && it.supported }.map { it.id }.toSet()
    }
    fun plan(filters: SearchFilters): List<QueryUnit> {
        val city = requireNotNull(byCity[filters.originCityId]) { "无法识别出发城市" }
        val origins = if(filters.originStations.isEmpty()) city.queryStations else city.stations.filter { it.code in filters.originStations }
        require(origins.isNotEmpty()) { "请重新选择出发车站" }
        val destinations = cities.filter { it.id in filters.destinationCityIds && it.id != city.id }
        require(destinations.isNotEmpty()) { "请至少选择一个查询目的地" }
        require(destinations.all { it.supported }) { "所选目的地暂无可查询车站，请调整目的地" }
        return filters.dates().flatMap { d -> destinations.flatMap { c -> c.queryStations.flatMap { to -> origins.map { QueryUnit(d,it,to) } } } }.distinctBy { it.key }
    }
    companion object {
        fun parse(raw: String): StationCatalog {
            val content = Regex("station_names\\s*=\\s*['\"]([^'\"]+)").find(raw)?.groupValues?.get(1) ?: error("车站字典格式变化")
            val stations = content.split('@').filter { it.isNotBlank() }.map { record ->
                val f = record.split('|')
                require(f.size >= 10 && f[2].matches(Regex("[A-Z0-9]{3}")) && f[6].isNotBlank() && f[7].isNotBlank()) { "车站记录缺少城市信息" }
                Station(f[2],f[1],f[6],f[7],f[3],f[8].isBlank() && f[9].isBlank())
            }
            require(stations.size > 100 && stations.any { it.code == "BJP" }) { "车站字典不完整" }
            return StationCatalog(stations)
        }
        fun bundled(): StationCatalog = parse(requireNotNull(StationCatalog::class.java.getResourceAsStream("/station_name.js")).bufferedReader().use { it.readText() })
    }
}
