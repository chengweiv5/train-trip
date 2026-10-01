package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.traintrip.core.SeatType

/** Shared editor; each caller owns its saved selection. Dismissing discards the local draft. */
@Composable fun SeatFilterSheet(
    selected: Set<SeatType>,
    onDismiss: () -> Unit,
    onApply: (Set<SeatType>) -> Unit,
    supportingText: String,
) {
    var draft by remember { mutableStateOf(selected) }
    FilterPanel("选择席别", onDismiss) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(16.dp).testTag("seat-filter-sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("全选", { draft = SeatType.entries.toSet() }, Modifier.weight(1f))
                SecondaryButton("全不选", { draft = emptySet() }, Modifier.weight(1f))
            }
            Text(
                if (draft.isEmpty()) "请至少选择一种席别" else "已选 ${draft.size} / ${SeatType.entries.size} 种席别",
                color = if (draft.isEmpty()) MaterialTheme.colorScheme.error else Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SeatType.entries.forEach { seat ->
                    val checked = seat in draft
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(checked, role = Role.Checkbox) { selected ->
                                draft = if (selected) draft + seat else draft - seat
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
                        Text(seat.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            PrimaryButton("完成", { if (draft.isNotEmpty()) onApply(draft) }, enabled = draft.isNotEmpty())
        }
    }
}
