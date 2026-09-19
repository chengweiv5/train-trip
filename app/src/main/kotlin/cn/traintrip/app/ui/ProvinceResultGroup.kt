package cn.traintrip.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cn.traintrip.core.*

@Composable fun ProvinceResultGroup(group:ProvinceResult,expanded:Boolean,onToggle:()->Unit,onCollapse:()->Unit,
    content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().testTag("province-group-${group.province.id}").animateContentSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button,onClick=onToggle)
            .semantics(mergeDescendants=true) { stateDescription=if(expanded) "已展开" else "已收起" }
            .testTag("province-toggle-${group.province.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(group.province.name,style=MaterialTheme.typography.titleMedium)
                if(!expanded) Text("${group.cities.size} 个城市 · "+group.cities.take(3).joinToString("、") { it.cityName }+if(group.cities.size>3) "等" else "",style=MaterialTheme.typography.bodySmall,color=Muted)
                if(group.incomplete || group.cities.isEmpty()) Text(group.status,style=MaterialTheme.typography.bodySmall,color=if(group.incomplete) Amber else Muted)
            }
            if(expanded) Text("${group.cities.size} 个城市",style=MaterialTheme.typography.bodySmall,color=Muted)
            Text(if(expanded) "收起" else "展开",color=if(expanded) Subtle else Primary,style=MaterialTheme.typography.bodySmall)
            UiIcon(if(expanded) "up" else "down",color=if(expanded) Subtle else Primary)
        }
        if(expanded) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            content()
            TextButton(onCollapse,Modifier.fillMaxWidth().testTag("province-collapse-${group.province.id}")) { Text("收起${group.province.name}");UiIcon("up") }
        }
    }
}
