package cn.traintrip.core

import com.google.gson.JsonObject

/** Whole-article itinerary suggestions; sights are independent of the recommendation catalogue. */
internal object GuideRouteEvidence {
    private val duration = Regex("([一二两三四五六七八九十百\\d]+)[天日](?:游|[一二两三四五六七八九十\\d]*夜)")
    private val itinerary = Regex("[一二两三四五六七八九十百\\d]+[天日]游|[一二两三四五六七八九十百\\d]+天[一二两三四五六七八九十\\d]*夜|第[一二两三四五六七八九十百\\d]+天|Day\\s*\\d+|D\\d+[：: ]|游览路线|游玩路线|行程安排|路线推荐", RegexOption.IGNORE_CASE)
    fun isItinerary(doc: SourceDocument) = doc.kind == "routes" || itinerary.containsMatchIn(doc.title + doc.content)

    fun suggestedDays(doc: SourceDocument): List<Int> {
        val text = doc.title + "\n" + doc.content
        val named = duration.findAll(text).mapNotNull { number(it.groupValues[1]) }.filter { it > 0 }.toList()
        val headings = Regex("(?:第([一二两三四五六七八九十百\\d]+)天|Day\\s*(\\d+)|D(\\d+))\\s*[：:]", RegexOption.IGNORE_CASE)
            .findAll(text).mapNotNull { match -> number(match.groupValues.drop(1).first { it.isNotEmpty() }) }
            .distinct().sorted().toList()
        // Discover numbered multi-day articles even if the title never says “三日游”.
        // This only selects retry durations; it does not validate or rewrite the model's itinerary.
        val complete = headings.withIndex().takeWhile { it.value == it.index + 1 }.lastOrNull()?.value
        return (named + listOfNotNull(complete)).distinct().sorted()
    }

    private fun number(value: String) = value.toIntOrNull() ?: chineseNumber(value)

    private fun chineseNumber(value: String): Int? {
        val digits = mapOf('一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (value.length == 1) return if (value == "十") 10 else digits[value.single()]
        val parts = value.split('十')
        if (parts.size != 2) return null
        val tens = if (parts[0].isEmpty()) 1 else parts[0].singleOrNull()?.let(digits::get) ?: return null
        val ones = if (parts[1].isEmpty()) 0 else parts[1].singleOrNull()?.let(digits::get) ?: return null
        return tens * 10 + ones
    }

    fun plans(raw: List<JsonObject>, material: GuideMaterial, places: List<SourcePlace>): List<DayPlan> = raw.mapNotNull { plan ->
        runCatching {
            val days = plan.text("days").toInt()
            require(plan["schedule"]?.isJsonArray == true && plan["schedule"].asJsonArray.all { it.isJsonObject })
            val schedule = plan.objects("schedule")
            require(days > 0 && schedule.size == days)
            val checked = schedule.mapIndexed { index, day ->
                // Different plans may use different articles; every day inside this plan must share one.
                val sourceId = day.text("sourceId").ifBlank { plan.text("sourceId") }
                val doc = requireNotNull(material.documents.find { it.id == sourceId })
                require(GuideSearchPolicy.primaryArticle(doc.url))
                val stops = if (day.has("stops")) {
                    require(day["stops"].isJsonArray && day["stops"].asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })
                    day.strings("stops").map(String::trim)
                } else day.strings("experienceIds").mapNotNull { id -> places.find { it.id == id }?.name }
                require(stops.all { it.isNotBlank() && it.length <= 180 })
                val description = day.text("description").ifBlank { day.text("quote") }.trim()
                require(description.isNotBlank() && description.length <= 1800)
                PlanDay(if (days == 1) "当天" else "第${index + 1}天", emptyList(), description, doc.url, stops = stops)
            }
            require(checked.map { it.sourceUrl }.distinct().size == 1)
            DayPlan(days, plan.text("title").ifBlank { "${days} 日游攻略参考" }, checked,
                "来自同一篇旅行攻略的参考安排；请结合地图、体力及景区最新信息调整。")
        }.getOrNull()
    }.distinctBy { it.days }.sortedBy { it.days }.take(GuideItemPolicy.MAX_PLANS)
}
