@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.traintrip.core.DestinationGuide

internal enum class GuideSection(val id: String, val label: String, val title: String) {
    PLACES("places", "景点", "值得去的地方"),
    FOOD("food", "美食", "尝尝当地味道"),
    PLANS("plans", "玩法", "可以这样玩"),
    TIPS("tips", "贴士", "出发前知道这些")
}

internal fun LazyListScope.guideSectionContent(
    section: GuideSection, guide: DestinationGuide, days: Int, onDays: (Int) -> Unit
) {
    item("section-title") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, Modifier.weight(1f).semantics { heading() },
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            val count = when (section) {
                GuideSection.PLACES -> "${guide.experiences.size} 个推荐"
                GuideSection.FOOD -> "${guide.foods.size} 种风味"
                else -> null
            }
            count?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
    when (section) {
        GuideSection.PLACES -> itemsIndexed(guide.experiences, key = { _, e -> e.id }) { index, experience ->
            GuideContentCard(experience.name, Modifier.testTag("guide-experience-${experience.id}"), index + 1) {
                Text(experience.reason, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.padding(top = 3.dp), color = Line.copy(alpha = .55f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("建议 ${experience.duration}", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(experience.location, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        GuideSection.FOOD -> { if(guide.foods.isEmpty()) item { Hint("暂未取得可靠的美食资料。") }
            itemsIndexed(guide.foods, key = { _, food -> food.name }) { index, food ->
            GuideContentCard(food.name, Modifier.testTag("guide-food-$index"), index + 1) {
                Text(food.description, style = MaterialTheme.typography.bodyMedium)
            }
        } }
        GuideSection.PLANS -> {
            if(guide.plans.isEmpty()) { item { Hint("暂未整理出可靠路线，可先按景点安排游览。") };return }
            item("day-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("按完整游玩日安排，抵达较晚可少选一站。", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        guide.plans.forEach { plan ->
                            val selected = days == plan.days
                            Surface(Modifier.heightIn(min = 48.dp).testTag("plan-${plan.days}")
                                .selectable(selected, role = Role.RadioButton, onClick = { onDays(plan.days) }),
                                color = if (selected) Sage else Color.Transparent,
                                border = BorderStroke(1.dp, if (selected) Forest else Line), shape = RoundedCornerShape(10.dp)) {
                                Box(Modifier.padding(horizontal = 17.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text("${plan.days} 日玩法", color = if (selected) Forest else Muted,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            val plan = guide.plans.firstOrNull { it.days == days } ?: guide.plans.first()
            item("plan-title") {
                Text(plan.title, Modifier.testTag("selected-plan"), style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(plan.schedule, key = { index, _ -> "plan-day-$index" }) { _, day ->
                GuideContentCard(day.label) {
                    Text(day.experienceIds.joinToString(" → ") { id -> guide.experiences.first { it.id == id }.name },
                        style = MaterialTheme.typography.titleMedium, color = Forest)
                    Text(day.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item("plan-note") { Text(plan.note, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        GuideSection.TIPS -> {
            item("season") {
                GuideContentCard("季节与出行") { Text(guide.season, style = MaterialTheme.typography.bodyMedium) }
            }
            item("arrival") {
                GuideContentCard("到站后怎么走") { Text(guide.arrivalAdvice, style = MaterialTheme.typography.bodyMedium) }
            }
            item("advice") {
                Text("玩法与耗时为参考建议；门票、开放及预约要求请出发前查看景区官方信息。",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable private fun GuideContentCard(
    title: String, modifier: Modifier = Modifier, number: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White,
        border = BorderStroke(1.dp, Line.copy(alpha = .65f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (number != null) {
                    Surface(color = Sage, shape = RoundedCornerShape(9.dp)) {
                        Box(Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp).padding(4.dp),
                            contentAlignment = Alignment.Center) {
                            Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = Forest)
                        }
                    }
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
