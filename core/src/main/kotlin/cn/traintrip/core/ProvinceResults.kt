package cn.traintrip.core

/** Query coverage is independent of ticket availability. An empty partial group is never labelled no tickets. */
data class ProvinceResult(val province: Province, val cities: List<CityResult>, val status: String, val incomplete: Boolean)

fun groupResults(catalog: StationCatalog, filters: SearchFilters, progress: SearchProgress?, byCount: Boolean = false): List<ProvinceResult> {
    val results=aggregate(progress?.trips.orEmpty(),filters)
    val selected=catalog.cities.filter { it.id in filters.destinationCityIds }.groupBy { it.province.id }
    return catalog.provinces.mapNotNull { province ->
        val scope=selected[province.id].orEmpty()
        val cities=results.filter { catalog.byCity[it.cityId]?.province?.id == province.id }.sortedWith(
            (if(byCount) compareByDescending<CityResult> { it.trainCount } else compareBy { it.shortestMinutes })
                .thenBy { catalog.byCity[it.cityId]?.pinyin.orEmpty() }.thenBy { it.cityId })
        if(scope.isEmpty() && cities.isEmpty()) return@mapNotNull null
        val units=progress?.plan.orEmpty().filter { catalog.byCity[it.destination.cityId]?.province?.id == province.id }
        val outcomes=units.map { progress?.outcomes?.get(it.key) }
        val failed=outcomes.count { it is QueryResult.Failure }
        val unopened=outcomes.count { it is QueryResult.NotOnSale }
        val remaining=outcomes.count { it == null }
        val incomplete=units.isEmpty() || failed>0 || unopened>0 || remaining>0
        val status=when {
            scope.any { !it.supported } -> "所选城市暂无可查询车站，请调整目的地"
            units.isEmpty() -> "该省查询尚未开始"
            failed>0 -> "查询未完成 · $failed 项失败${if(remaining>0) " · $remaining 项待查" else ""}"
            remaining>0 -> if(progress?.running==true) "该省仍在查询 · $remaining 项待查" else "查询未完成 · $remaining 项待查"
            unopened>0 -> "该省有 $unopened 项尚未开售"
            cities.isEmpty() -> "该省暂无符合条件的票"
            else -> "所选范围查询完成"
        }
        ProvinceResult(province,cities,status,incomplete)
    }
}
