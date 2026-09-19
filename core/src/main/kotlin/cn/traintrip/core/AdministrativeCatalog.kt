package cn.traintrip.core

import com.google.gson.JsonParser
import java.util.Locale

/** Bundled administrative snapshot; provenance and update rules are in docs/data/destinations.md. */
object AdministrativeCatalog {
    private fun resource(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() }).asJsonObject
    private val directory = resource("destinations.json")
    val provinces = directory.getAsJsonArray("provinces").map { it.asJsonObject.let { p ->
        Province(p["id"].asString,p["name"].asString,p["shortName"].asString,p["pinyin"].asString,p["fullPinyin"].asString)
    } }.sortedBy { it.pinyin }
    private val byProvince = provinces.associateBy { it.id }
    val cities = directory.getAsJsonArray("cities").map { it.asJsonObject.let { c ->
        City(c["id"].asString,c["name"].asString,emptyList(),byProvince.getValue(c["provinceId"].asString),c["pinyin"].asString,c["aliases"].asJsonArray.map { a -> a.asString })
    } }.sortedWith(compareBy<City> { it.province.pinyin }.thenBy { it.pinyin }.thenBy { it.id })
    private val byId = cities.associateBy { it.id }
    private val mapping = resource("station-city-mapping.json")
    private val legacy = mapping.getAsJsonArray("legacyCities").associate { it.asJsonObject.let { m -> m["legacyId"].asString to (m["legacyName"].asString to m["cityId"].asString) } }
    private val stationOverrides = mapping.getAsJsonObject("stationOverrides").entrySet().associate { it.key to it.value.asString }
    fun resolveId(id: String): String? = if(id in byId) id else legacy[id]?.second
    fun normalize(station: Station): Station {
        if(!station.domestic) return station
        val id = stationOverrides[station.code] ?: legacy[station.railwayCityId]?.takeIf { it.first == station.railwayCityName }?.second
            ?: station.cityId.takeIf { it in byId }
        val city = byId[id] ?: return station.copy(cityId="unmapped:${station.railwayCityId}")
        return station.copy(cityId=city.id,cityName=city.name)
    }
}

fun Province.matches(query: String): Boolean {
    val q=query.trim().lowercase(Locale.ROOT).replace(" ","")
    return name.contains(q) || shortName.contains(q) || pinyin.contains(q) || fullPinyin.contains(q)
}
fun City.matches(query: String): Boolean {
    val q=query.trim().lowercase(Locale.ROOT).replace(" ","")
    return name.contains(q) || pinyin.contains(q) || aliases.any { it.contains(q) }
}
