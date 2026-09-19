package cn.traintrip.app

import android.content.Context
import cn.traintrip.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("travel-filters",Context.MODE_PRIVATE)
    fun load(catalog: StationCatalog): SearchFilters = runCatching {
        val j=JSONObject(prefs.getString("filters",null) ?: return defaults(catalog))
        val origin=j.optString("origin","110000")
        val start=LocalDate.parse(j.getString("start")).coerceAtLeast(today())
        val end=LocalDate.parse(j.getString("end")).coerceAtLeast(start)
        val migrated=catalog.normalize(SearchFilters(origin,j.strings("stations"),start,end,j.optInt("from",0),j.optInt("to",1440),
            j.strings("seats").mapNotNull { runCatching { SeatType.valueOf(it) }.getOrNull() }.toSet().ifEmpty { SeatType.entries.toSet() },
            j.optInt("people",1).coerceIn(1,20),j.optInt("max",0).takeIf { it>0 },j.strings("destinations")))
        migrated.copy(destinationCityIds=migrated.destinationCityIds.ifEmpty { catalog.initialDestinations(migrated.originCityId) })
    }.getOrElse { defaults(catalog) }
    fun save(f:SearchFilters) {
        val j=JSONObject().put("origin",f.originCityId).put("stations",JSONArray(f.originStations.toList())).put("start",f.startDate.toString()).put("end",f.endDate.toString())
            .put("from",f.startMinute).put("to",f.endMinute).put("seats",JSONArray(f.seats.map { it.name })).put("people",f.people).put("max",f.maxMinutes ?: 0).put("destinations",JSONArray(f.destinationCityIds.toList()))
        prefs.edit().putString("filters",j.toString()).apply()
    }
    private fun defaults(c:StationCatalog)=SearchFilters(destinationCityIds=c.initialDestinations("110000"))
    private fun JSONObject.strings(key:String):Set<String> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
}
