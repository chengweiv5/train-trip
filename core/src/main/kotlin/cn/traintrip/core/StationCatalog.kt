package cn.traintrip.core

class StationCatalog(val stations: List<Station>) {
    val byCode = stations.associateBy { it.code }
    val cities = stations.filter { it.domestic }.groupBy { it.cityId }.map { (id,list) -> City(id,list.first().cityName,list) }
    val byCity = cities.associateBy { it.id }
    fun initialDestinations(origin: String): Set<String> {
        val names = setOf("天津","石家庄","秦皇岛","承德","保定","济南","青岛","太原","大同","呼和浩特","沈阳","大连","长春","哈尔滨","郑州","西安","南京","上海","杭州","武汉","长沙","合肥","洛阳","泰安")
        return cities.filter { it.id != origin && it.name in names }.map { it.id }.toSet()
    }
    fun plan(filters: SearchFilters): List<QueryUnit> {
        val city = requireNotNull(byCity[filters.originCityId]) { "无法识别出发城市" }
        val origins = if(filters.originStations.isEmpty()) listOf(city.representative) else city.stations.filter { it.code in filters.originStations }
        require(origins.isNotEmpty()) { "请重新选择出发车站" }
        val destinations = cities.filter { it.id in filters.destinationCityIds && it.id != city.id }
        require(destinations.isNotEmpty()) { "请至少选择一个查询目的地" }
        return filters.dates().flatMap { d -> destinations.flatMap { c -> origins.map { QueryUnit(d,it,c.representative) } } }
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
