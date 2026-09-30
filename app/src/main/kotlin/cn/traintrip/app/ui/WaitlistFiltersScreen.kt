@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*

@Composable internal fun WaitlistFiltersScreen(s: WaitlistUiState, vm: WaitlistViewModel) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val f = s.draft
    Column(Modifier.fillMaxSize()) {
        AppTopBar("候补", onBack = if (s.applied != null) vm::back else null, backTag = "waitlist-back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
            .testTag("waitlist-filters"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("选好车次，自动继续", style = MaterialTheme.typography.headlineMedium, color = Primary)
            Text("先查询和选择候补需求，再登录并选择真实乘车人", style = MaterialTheme.typography.bodyMedium, color = Muted)
            ContentCard(Modifier.fillMaxWidth()) {
                WaitlistField("出发地 · ${s.catalog.byCity[f.originCityId]?.name.orEmpty()}",
                    stationSummary(s.catalog, f.originStations), "waitlist-origin") { sheet = "origin" }
                HorizontalDivider(color = Line)
                WaitlistField("目的地 · ${s.catalog.byCity[f.destinationCityId]?.name.orEmpty()}",
                    stationSummary(s.catalog, f.destinationStations), "waitlist-destination") { sheet = "destination" }
                HorizontalDivider(color = Line)
                WaitlistField("出发日期", dateRange(f.controls()), "waitlist-dates") { sheet = "dates" }
                s.sourceInfo?.let { Text("官方查询范围：${dateLabel(it.saleStart)}—${dateLabel(it.saleEnd)}",
                    style = MaterialTheme.typography.bodySmall, color = Muted) }
                HorizontalDivider(color = Line)
                SectionTitle("出发时段 · 可多选", "自定义") { sheet = "time" }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("全天", f.controls().isAllDay, { vm.updateFilters(f.withControls(f.controls().withCustomTime(0, 1440))) })
                    DeparturePeriod.entries.forEach { period ->
                        Choice(period.label, period in f.controls().selectedDeparturePeriods,
                            { vm.updateFilters(f.withControls(f.controls().toggleDeparturePeriod(period))) })
                    }
                }
                Text("${timeIntervalsText(f.controls())} · 所选日期每天适用", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            ContentCard(Modifier.fillMaxWidth()) {
                WaitlistField("席别", f.seats.sortedBy { it.ordinal }.joinToString("、") { it.label }, "waitlist-seats") { sheet = "seats" }
                HorizontalDivider(color = Line)
                WaitlistField("车种", f.trainKind.label, "waitlist-kind") { sheet = "kind" }
                HorizontalDivider(color = Line)
                WaitlistField("最长车程", f.maxMinutes?.let(::durationText) ?: "不限", "waitlist-duration") { sheet = "duration" }
                HorizontalDivider(color = Line)
                WaitlistField("排序", f.sort.label, "waitlist-sort") { sheet = "sort" }
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("只看可加入候补", style = MaterialTheme.typography.bodyLarge)
                        Text("受限席别仍可展开查看，但不可勾选", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(f.onlyAvailable, { vm.updateFilters(f.copy(onlyAvailable = it)) }, Modifier.testTag("waitlist-only-available"))
                }
            }
            s.operation?.let { ContentCard(Modifier.fillMaxWidth(), onClick = vm::showProgress) {
                Text("查看已保存的候补进度", color = Primary)
                Text("未决订单先核对，不重复提交", style = MaterialTheme.typography.bodySmall, color = Muted)
            } }
            s.formError?.let { WaitlistNotice(it) }
            s.storageError?.let { WaitlistNotice(it); SecondaryButton("重试保存条件", vm::saveDraft) }
            s.operationError?.let { WaitlistNotice(it) }
            PrimaryButton("查询候补车次", vm::search, s.ready, Modifier.testTag("waitlist-search"))
            Text("匿名查询不下单。可加入候补不代表指定乘客一定能提交；真实认证和自动提交尚未接通。",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    when (sheet) {
        "origin", "destination" -> {
            val origin = sheet == "origin"
            WaitlistLocationSheet(s.catalog, if (origin) f.originCityId else f.destinationCityId,
                if (origin) f.originStations else f.destinationStations, origin, { sheet = null }) { city, stations ->
                vm.updateFilters(if (origin) f.copy(originCityId = city, originStations = stations)
                    else f.copy(destinationCityId = city, destinationStations = stations))
                sheet = null
            }
        }
        "dates" -> DateFilterSheet(UiState(s.catalog, f.controls(), sourceInfo = s.sourceInfo),
            { sheet = null }, { vm.updateFilters(f.withControls(it)); sheet = null },
            allowedRange = s.sourceInfo?.let { it.saleStart..it.saleEnd })
        "time" -> TimeFilterSheet(f.controls(), { sheet = null }, { vm.updateFilters(f.withControls(it)); sheet = null })
        "duration" -> DurationFilterSheet(f.controls(), { sheet = null }, { vm.updateFilters(f.withControls(it)); sheet = null })
        "seats", "kind", "sort" -> WaitlistOptionsSheet(sheet!!, f, { sheet = null }) {
            vm.updateFilters(it); sheet = null
        }
    }
}

@Composable private fun WaitlistField(title: String, value: String, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        UiIcon("next", color = Muted)
    }
}

internal fun stationSummary(catalog: StationCatalog, stations: Set<String>) =
    if (stations.isEmpty()) "同城各站" else stations.joinToString("、") { catalog.byCode[it]?.name ?: it }

@Composable internal fun WaitlistNotice(message: String) {
    ContentCard(Modifier.fillMaxWidth()) { Text(message, color = Ink, style = MaterialTheme.typography.bodyMedium) }
}

@Composable private fun WaitlistLocationSheet(catalog: StationCatalog, cityId: String, stations: Set<String>,
    origin: Boolean, onDismiss: () -> Unit, onApply: (String, Set<String>) -> Unit) {
    var city by rememberSaveable { mutableStateOf(cityId) }
    var selected by remember { mutableStateOf(stations) }
    var query by rememberSaveable { mutableStateOf("") }
    FilterPanel(if (origin) "出发城市与车站" else "目的地城市与车站", onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("waitlist-city-search"),
                    label = { Text("搜索城市") }, singleLine = true)
                if (query.isNotBlank()) catalog.cities.filter {
                    it.supported && (it.matches(query) || it.province.matches(query))
                }.take(30).forEach { candidate ->
                    TextButton({ if (city != candidate.id) selected = emptySet(); city = candidate.id; query = "" },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("waitlist-city-${candidate.id}")) { Text(candidate.name) }
                }
                Text("${catalog.byCity[city]?.name.orEmpty()} · 车站", style = MaterialTheme.typography.titleMedium)
                WaitlistCheck("全部同城车站", selected.isEmpty()) { selected = emptySet() }
                catalog.byCity[city]?.stations?.forEach { station ->
                    WaitlistCheck(station.name, station.code in selected) {
                        selected = if (station.code in selected) selected - station.code else selected + station.code
                    }
                }
            }
            PrimaryButton("完成", { onApply(city, selected) }, modifier = Modifier.testTag("waitlist-location-apply"))
        }
    }
}

@Composable private fun WaitlistOptionsSheet(kind: String, filters: WaitlistFilters,
    onDismiss: () -> Unit, onApply: (WaitlistFilters) -> Unit) {
    var draft by remember { mutableStateOf(filters) }
    FilterPanel(when (kind) { "seats" -> "选择席别"; "kind" -> "选择车种"; else -> "结果排序" }, onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (kind) {
                    "seats" -> SeatType.entries.forEach { seat ->
                        WaitlistCheck(seat.label, seat in draft.seats) {
                            draft = draft.copy(seats = if (seat in draft.seats) draft.seats - seat else draft.seats + seat)
                        }
                    }
                    "kind" -> WaitlistTrainKind.entries.forEach { option ->
                        WaitlistCheck(option.label, option == draft.trainKind) { draft = draft.copy(trainKind = option) }
                    }
                    else -> WaitlistSort.entries.forEach { option ->
                        WaitlistCheck(option.label, option == draft.sort) { draft = draft.copy(sort = option) }
                    }
                }
            }
            if (kind == "seats") Text("未核验候补映射的席别仅展示状态，不开放勾选需求", color = Muted, style = MaterialTheme.typography.bodySmall)
            PrimaryButton("完成", { onApply(draft) }, enabled = draft.seats.isNotEmpty())
        }
    }
}

@Composable private fun WaitlistCheck(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onClick() }); Text(label, Modifier.weight(1f))
    }
}
