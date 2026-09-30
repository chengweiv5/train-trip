package cn.traintrip.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.app.*
import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*

@Composable fun WaitlistScreen(s: WaitlistUiState, vm: WaitlistViewModel,
    onTicket: (WaitlistChoice) -> Unit, onOpenRailway: () -> Unit) {
    BackHandler(s.page != WaitlistPage.FILTERS) { vm.back() }
    val pages = rememberSaveableStateHolder()
    pages.SaveableStateProvider(s.page.name) { when (s.page) {
        WaitlistPage.FILTERS -> WaitlistFiltersScreen(s, vm)
        WaitlistPage.RESULTS -> WaitlistResultsScreen(s, vm, onTicket)
        WaitlistPage.SELECTED -> WaitlistSelectedScreen(s, vm)
        WaitlistPage.AUTHENTICATION -> WaitlistAuthenticationScreen(s, vm, onOpenRailway)
        WaitlistPage.PROGRESS -> WaitlistProgressScreen(s, vm, onOpenRailway)
    } }
}

@Composable private fun WaitlistResultsScreen(s: WaitlistUiState, vm: WaitlistViewModel, onTicket: (WaitlistChoice) -> Unit) {
    val choices = s.choices
    val groups = choices.groupBy { it.trip.key }.values.toList()
    Column(Modifier.fillMaxSize()) {
        AppTopBar("候补车次", vm::edit, "修改条件", vm::edit, backTag = "waitlist-back")
        LazyColumn(Modifier.weight(1f).testTag("waitlist-results"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("summary") { s.applied?.let { WaitlistConditionSummary(it, s.catalog) } }
            s.notice?.let { item("notice") { WaitlistNotice(it) } }
            if (s.busy) item("loading") {
                Text("正在查询候补车次…", color = Primary)
                s.progress?.let { Text("已查询 ${it.outcomes.size} / ${it.plan.size} 个日期区间", color = Muted) }
                SecondaryButton("停止查询", vm::pauseForegroundWork)
            }
            if (s.error != null || s.progress?.failureCount?.let { it > 0 } == true) item("failure") {
                WaitlistNotice(s.error ?: "部分查询失败，不能判断失败区间是否有候补车次。已成功的区间仍可查看。")
                SecondaryButton("重试本次查询", { vm.retryQuery() }, Modifier.testTag("waitlist-retry"))
            }
            if (s.progress?.stopped == true || (!s.busy && s.error == null && s.progress == null)) item("stopped") {
                WaitlistNotice("查询已暂停，未查询区间不能当作没有车次")
                SecondaryButton("继续查询", vm::retryQuery)
            }
            if (s.progress?.unopenedCount?.let { it > 0 } == true) item("unopened") {
                WaitlistNotice("部分日期尚未起售，不能判断是否有候补车次，请调整日期或稍后重新查询")
                SecondaryButton("修改日期", vm::edit)
            }
            if (s.queryComplete && groups.isEmpty()) item("empty") {
                WaitlistNotice("没有符合当前条件的候补车次。可调整日期、席别或时段；关闭“只看可加入候补”可查看受限席别。")
                SecondaryButton("调整查询条件", vm::edit)
            }
            items(groups, key = { it.first().trip.key }) { group ->
                val train = group.first().trip
                ContentCard(Modifier.fillMaxWidth()) {
                    Text("${train.date} · ${train.trainCode}", style = MaterialTheme.typography.titleMedium)
                    TripTiming(train)
                    Text("查询于 ${formatTime(train.queriedAt)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                    group.forEach { choice ->
                        val selected = s.selected.any { it.demand != null && it.demand == choice.demand }
                        val available = choice.eligibility == WaitlistEligibility.AVAILABLE
                        HorizontalDivider(color = Line)
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .then(if (available) Modifier.clickable { vm.toggle(choice) } else Modifier)
                            .testTag("waitlist-choice-${train.key}-${choice.seat.name}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selected, if (available) { _ -> vm.toggle(choice) } else null, enabled = available)
                            Column(Modifier.weight(1f)) {
                                Text(choice.seat.label, style = MaterialTheme.typography.bodyLarge)
                                Text(if (selected) "已选 · ${choice.eligibility.label}" else choice.eligibility.label,
                                    style = MaterialTheme.typography.bodySmall, color = if (available) Primary else Muted)
                            }
                            if (choice.eligibility == WaitlistEligibility.HAS_TICKETS)
                                TextButton({ onTicket(choice) }) { Text("查票") }
                        }
                    }
                }
            }
        }
        WaitlistSelectionFooter(s, vm)
    }
}

@Composable private fun WaitlistConditionSummary(f: WaitlistFilters, catalog: StationCatalog) {
    ContentCard(Modifier.fillMaxWidth().testTag("waitlist-applied-summary")) {
        Text("${catalog.byCity[f.originCityId]?.name} → ${catalog.byCity[f.destinationCityId]?.name}",
            style = MaterialTheme.typography.titleMedium)
        Text("${stationSummary(catalog, f.originStations)} → ${stationSummary(catalog, f.destinationStations)}",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Text("${dateRange(f.controls())} · ${timeRange(f.controls())}", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text("${f.seats.sortedBy { it.ordinal }.joinToString("、") { it.label }} · ${f.trainKind.label} · " +
            "${f.maxMinutes?.let(::durationText) ?: "车程不限"} · ${f.sort.label} · " +
            if (f.onlyAvailable) "只看可加入候补" else "包含受限席别",
            style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable private fun WaitlistSelectionFooter(s: WaitlistUiState, vm: WaitlistViewModel) {
    Surface(color = PageBackground, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${s.selected.size} 组候补需求", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(vm::showSelected, Modifier.testTag("waitlist-view-selected")) { Text("查看已选") }
            }
            if (s.hiddenSelected.isNotEmpty()) Text("其中 ${s.hiddenSelected.size} 项未出现在当前列表，仍保留在已选清单",
                Modifier.testTag("waitlist-hidden"), style = MaterialTheme.typography.bodySmall, color = Muted)
            PrimaryButton("下一步 · 选乘车人", vm::next, s.selected.isNotEmpty(), Modifier.testTag("waitlist-next"))
        }
    }
}

@Composable private fun WaitlistSelectedScreen(s: WaitlistUiState, vm: WaitlistViewModel) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar("已选候补需求", vm::back, backTag = "waitlist-back")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("共 ${s.selected.size} 组，不是乘车人数", color = Muted) }
            items(s.selected, key = { "${it.trip.key}/${it.seat}" }) { choice ->
                ContentCard(Modifier.fillMaxWidth()) {
                    Text("${choice.trip.date} · ${choice.trip.trainCode} · ${choice.seat.label}", style = MaterialTheme.typography.titleMedium)
                    Text("${choice.trip.from.name} → ${choice.trip.to.name}", color = Muted)
                    Text("选择时查询于 ${formatTime(choice.trip.queriedAt)}；提交前仍需重新核验", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (choice in s.hiddenSelected) Text("当前筛选未显示此项", color = Muted)
                    SecondaryButton("移除", { vm.toggle(choice) }, Modifier.testTag("waitlist-remove-${choice.trip.key}-${choice.seat.name}"))
                }
            }
            if (s.selected.isEmpty()) item { WaitlistNotice("还没有选择候补需求，请返回结果列表勾选") }
        }
        Column(Modifier.padding(16.dp)) { PrimaryButton("下一步 · 选乘车人", vm::next, s.selected.isNotEmpty()) }
    }
}

@Composable private fun WaitlistAuthenticationScreen(s: WaitlistUiState, vm: WaitlistViewModel, onOpenRailway: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar("登录与乘车人", vm::back, backTag = "waitlist-back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ContentCard(Modifier.fillMaxWidth()) {
                Text("已保留 ${s.selected.size} 组候补需求", style = MaterialTheme.typography.titleLarge)
                Text("真实认证与提交暂未接通", color = Primary, style = MaterialTheme.typography.titleMedium)
                Text("当前版本已接入匿名查询和选择，尚不能读取你的 12306 登录状态或乘车人，也不会发送候补订单。",
                    style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text("打开外部 12306 App 不等于本应用登录成功。选中的日期、车次和席别不会自动传入；可在 12306 中自行办理。",
                    style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            PrimaryButton("登录并选择乘车人（待接通）", {}, enabled = false, modifier = Modifier.testTag("waitlist-auth-disabled"))
            SecondaryButton("打开 12306 自行办理", onOpenRailway)
            SecondaryButton("返回检查已选需求", vm::showSelected)
            Text("接通后将按官方组合限制核验全部需求；不会暗中拆单，也不会使用占位乘客或模拟提交。",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable private fun WaitlistProgressScreen(s: WaitlistUiState, vm: WaitlistViewModel, onOpenRailway: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar("候补进度", vm::back, backTag = "waitlist-back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val flow = s.operation
            if (flow == null) WaitlistNotice("没有已保存的候补任务")
            else {
                ContentCard(Modifier.fillMaxWidth()) {
                    Text(when (flow.phase) {
                        WaitlistPhase.ORDER_CREATED -> "候补订单已创建"
                        WaitlistPhase.EXHAUSTED -> "全部候选已被明确排除"
                        WaitlistPhase.SUBMITTING -> "正在提交全部剩余需求"
                        WaitlistPhase.CHECKING_ORDER -> "本轮结果待核对"
                        WaitlistPhase.PAUSED -> if (flow.pending != null) "已暂停续提，本轮结果待核对" else "候补处理已暂停"
                    }, style = MaterialTheme.typography.titleLarge)
                    Text("最初 ${flow.request.demands.size} 项 · 已排除 ${flow.exclusions.size} 项 · 剩余 ${flow.remaining.size} 项")
                    Text("已发起 ${flow.attemptCount} 轮 · 绑定 ${flow.request.binding.passengers.size} 位乘车人", color = Muted)
                    flow.notice?.let { Text(it, color = Muted) }
                    flow.order?.let { Text("订单号：${it.reference}"); Text("订单已创建不等于支付成功或候补兑现；请到 12306 核对及支付。", color = Muted) }
                }
                flow.order?.demands.orEmpty().ifEmpty { flow.remaining }.forEach { WaitlistDemandLine(it, s.catalog) }
                flow.exclusions.forEach { exclusion -> ContentCard(Modifier.fillMaxWidth()) {
                    Text("已排除 · ${exclusion.attemptId}", color = Primary)
                    WaitlistDemandLine(exclusion.demand, s.catalog)
                    Text(exclusion.reason, color = Muted)
                } }
                if (flow.pending != null) WaitlistNotice("必须先用同一账号核对未决订单。当前认证和查单未接通，不能再次提交；打开 12306 不会自动改变本页状态。")
                s.operationError?.let { WaitlistNotice(it) }
                if (flow.autoContinue) SecondaryButton("停止后续提交", { vm.stopOperation() })
                SecondaryButton("打开 12306 核对", onOpenRailway)
                Text("停止或退后台不会取消已发请求和已创建订单。", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable private fun WaitlistDemandLine(demand: WaitlistDemand, catalog: StationCatalog) {
    Text("${demand.date} · ${demand.trainId} · ${SeatType.entries.firstOrNull { it.waitlistCode == demand.seatCode }?.label ?: demand.seatCode}\n" +
        "${catalog.byCode[demand.fromStation]?.name ?: demand.fromStation} → ${catalog.byCode[demand.toStation]?.name ?: demand.toStation}",
        style = MaterialTheme.typography.bodyMedium)
}
