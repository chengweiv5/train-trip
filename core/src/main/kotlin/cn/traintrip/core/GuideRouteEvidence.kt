package cn.traintrip.core

import com.google.gson.JsonObject
import com.google.gson.JsonArray

/** A route is an attributed extract of an itinerary, not a model-invented ordering of a directory. */
internal object GuideRouteEvidence {
    private val itinerary = Regex("[一二两三四五六七\\d]+[天日]游|[一二两三四五六七\\d]+天[一二两三四五六七\\d]*夜|第[一二两三四五六七\\d]+天|Day\\s*[1-7]|D[1-7][：: ]|游览路线|游玩路线|行程安排|路线推荐",RegexOption.IGNORE_CASE)
    fun isItinerary(doc:SourceDocument) = itinerary.containsMatchIn(doc.title + doc.content)
    private fun normalized(value:String) = value.replace(Regex("\\s+"), "")
    fun plans(raw:List<JsonObject>,material:GuideMaterial,places:List<SourcePlace>):List<DayPlan> =
        (raw + extracted(material,places)).mapNotNull { plan ->
        runCatching {
            val days = plan.text("days").toInt()
            val schedule = plan.objects("schedule")
            require(days in 1..2 && schedule.size == days)
            val checked = schedule.mapIndexed { index, day ->
                val doc = requireNotNull(material.documents.find { it.id == day.text("sourceId") && isItinerary(it) })
                require(GuideSearchPolicy.primaryArticle(doc.url) && itinerary.containsMatchIn(doc.title + doc.content))
                val quote = day.text("quote").trim()
                require(quote.length in 10..1200 && normalized(doc.content).contains(normalized(quote)))
                val ids = day.strings("experienceIds")
                require(ids.isNotEmpty() && ids.distinct().size == ids.size)
                val offsets = ids.map { id ->
                    val place = requireNotNull(places.find { it.id == id })
                    GuidePhotoMatching.names(place.name).map { normalized(quote).indexOf(normalized(it)) }.filter { it >= 0 }.minOrNull()
                        ?: throw IllegalArgumentException("Route place absent from source")
                }
                require(offsets.zipWithNext().all { (a,b) -> a < b })
                PlanDay(if(days == 1) "当天" else "第${index+1}天",ids,quote,doc.url,quote)
            }
            require(checked.map { it.sourceUrl }.distinct().size == 1)
            val source = material.documents.first { it.url == checked.first().sourceUrl }
            val dayOffsets = checked.map { normalized(source.content).indexOf(normalized(requireNotNull(it.evidence))) }
            require(dayOffsets.zipWithNext().all { (first,second) -> first < second })
            DayPlan(days,"${days} 日游攻略参考",checked,"来自旅行攻略的参考安排；请结合地图、体力及景区最新信息调整。")
        }.getOrNull()
    }.distinctBy { it.days }.take(2)

    /** Recover explicit source day blocks when the model omitted or malformed an otherwise usable route. */
    private fun extracted(material:GuideMaterial,places:List<SourcePlace>):List<JsonObject> {
        fun day(doc:SourceDocument, text:String):JsonObject? {
            val quote = text.trim().take(1200)
            if (!quote.contains('→') && !quote.contains("——")) return null
            if(quote.length < 10)return null
            val ids=places.mapNotNull { p ->
                GuidePhotoMatching.names(p.name).map { quote.indexOf(it) }.filter { it>=0 }.minOrNull()?.let { p.id to it }
            }.sortedBy { it.second }.map { it.first }
            if(ids.isEmpty())return null
            return JsonObject().apply {
                addProperty("sourceId",doc.id);addProperty("quote",quote)
                add("experienceIds",JsonArray().apply { ids.forEach(::add) })
            }
        }
        fun plan(days:List<JsonObject>)=JsonObject().apply {
            addProperty("days",days.size);add("schedule",JsonArray().apply { days.forEach(::add) })
        }
        return material.documents.filter(::isItinerary).flatMap { doc ->
            val found=mutableListOf<JsonObject>()
            val headings=Regex("(?:第([一二三123])天|Day\\s*([123])|D([123]))[：: ]",RegexOption.IGNORE_CASE).findAll(doc.content).toList()
            val first=headings.indexOfFirst { it.groupValues.drop(1).any { n -> n=="一" || n=="1" } }
            if(first>=0 && first+1<headings.size) {
                val one=headings[first];val two=headings[first+1]
                if(two.groupValues.drop(1).any { it=="二" || it=="2" }) {
                    val after=headings.getOrNull(first+2)?.range?.first ?: doc.content.length
                    val d1=day(doc,doc.content.substring(one.range.first,two.range.first))
                    val d2=day(doc,doc.content.substring(two.range.first,after))
                    if(d1!=null && d2!=null)found+=plan(listOf(d1,d2))
                }
            }
            if(Regex("一日游|一天游|1日游").containsMatchIn(doc.title)) {
                val lines=doc.content.split('\n').filter { it.contains('→') || it.contains("——") }
                val route=lines.firstOrNull { line -> places.count { GuidePhotoMatching.mentions(line,it.name) }>=2 }
                if(route!=null) day(doc,route)?.let { found+=plan(listOf(it)) }
            }
            found
        }
    }

}
