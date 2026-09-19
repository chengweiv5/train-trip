package cn.traintrip.core

import com.google.gson.JsonParser
import java.time.*

object TicketParser {
    fun availability(raw: String): SeatAvailability = when {
        raw == "有" -> SeatAvailability(raw, AvailabilityKind.AVAILABLE)
        raw == "无" || raw == "0" -> SeatAvailability(raw, AvailabilityKind.NONE)
        raw == "候补" -> SeatAvailability(raw, AvailabilityKind.WAITLIST)
        raw.isBlank() || raw == "--" -> SeatAvailability(raw, AvailabilityKind.NOT_OFFERED)
        raw.toIntOrNull()?.let { it > 0 } == true -> SeatAvailability(raw, AvailabilityKind.COUNT,raw.toInt())
        else -> SeatAvailability(raw, AvailabilityKind.UNKNOWN)
    }
    fun parse(body: String, unit: QueryUnit, catalog: StationCatalog, at: Instant): QueryResult {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if(root.get("status")?.asBoolean != true) return QueryResult.Failure(root.get("messages")?.toString()?.take(180) ?: "12306 未返回成功结果",true)
            val data = root.getAsJsonObject("data") ?: return QueryResult.Failure("查询数据缺失，不能确定是否有票",true)
            val rows = data.getAsJsonArray("result") ?: return QueryResult.Failure("余票协议已变化，请到 12306 查询",true)
            val trips = rows.map { element ->
                val f = element.asString.split('|')
                require(f.size >= 39) { "余票字段数量变化" }
                require(f[2].isNotBlank() && f[3].isNotBlank()) { "缺少列车标识" }
                val from = requireNotNull(catalog.byCode[f[6]]) { "无法识别出发站 ${f[6]}" }
                val to = requireNotNull(catalog.byCode[f[7]]) { "无法识别到达站 ${f[7]}" }
                require((from.cityId==unit.origin.cityId || from.railwayCityId==unit.origin.railwayCityId) && (to.cityId==unit.destination.cityId || to.railwayCityId==unit.destination.railwayCityId)) { "返回车站不属于本次查询城市" }
                require(f[11] in setOf("Y","N","IS_TIME_NOT_BUY")) { "未知发售状态" }
                val sale = when {
                    f[11] == "Y" -> SaleState.OPEN
                    f[1].contains("起售") || f[1].contains("未开售") -> SaleState.NOT_YET
                    f[1].contains("停") || f[11] == "IS_TIME_NOT_BUY" -> SaleState.SUSPENDED
                    else -> SaleState.CLOSED
                }
                val depart = time(f[8]); val arrive = time(f[9]); val duration = duration(f[10])
                require(sale != SaleState.OPEN || (depart != null && arrive != null && duration != null && (depart.toSecondOfDay()/60+duration)%1440 == arrive.toSecondOfDay()/60)) { "车次时刻格式变化" }
                val seats=SeatType.entries.associateWith { availability(f[it.field]) }
                require(seats.values.none { it.kind==AvailabilityKind.UNKNOWN }) { "出现未识别的席别状态，不能确定余票" }
                Trip(unit.date,f[2],f[3],from,to,depart,arrive,duration,sale,f[1],seats,at,f[37] == "1",f.getOrNull(48)?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
            }
            QueryResult.Success(trips,at)
        } catch(e: Exception) { QueryResult.Failure("余票数据无法解析：${e.message?.take(100) ?: "格式异常"}",true) }
    }
    private fun time(s: String): LocalTime? = runCatching { if(!s.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))) null else LocalTime.parse(s) }.getOrNull()
    private fun duration(s: String): Int? = if(s.matches(Regex("\\d{1,2}:[0-5]\\d")) && s != "99:59") s.substringBefore(':').toInt()*60+s.substringAfter(':').toInt() else null
}
