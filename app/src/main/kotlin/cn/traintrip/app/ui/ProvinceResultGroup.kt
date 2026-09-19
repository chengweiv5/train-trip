package cn.traintrip.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.traintrip.core.*

@Composable fun ProvinceResultGroup(group: ProvinceResult,expanded: Boolean,onToggle: () -> Unit,onCollapse: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val angle by animateFloatAsState(if(expanded) 180f else 0f,tween(200),label="省份展开箭头")
    val count="${group.cities.size} 个有票城市"
    Surface(Modifier.fillMaxWidth().testTag("province-group-${group.province.id}"),
        shape=RoundedCornerShape(20.dp),color=if(expanded) Sage.copy(alpha=.65f) else Color.White,border=BorderStroke(1.dp,Line)) {
        Column(Modifier.animateContentSize(tween(200))) {
            Column(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onToggle)
                .semantics(mergeDescendants=true) { stateDescription=if(expanded) "已展开" else "已收起" }
                .testTag("province-toggle-${group.province.id}").padding(horizontal=16.dp,vertical=12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(group.province.name,style=MaterialTheme.typography.titleMedium)
                        Text(count,style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(expanded) "收起" else "展开",color=Forest,style=MaterialTheme.typography.labelLarge)
                        Text("⌄",Modifier.rotate(angle),color=Forest)
                    }
                }
                if(!expanded && group.cities.isNotEmpty()) {
                    val preview=group.cities.take(3).joinToString("、") { it.cityName } + if(group.cities.size>3) " 等 ${group.cities.size} 个城市" else ""
                    Text(preview,Modifier.padding(top=6.dp),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=Muted)
                }
                if(group.incomplete || group.cities.isEmpty()) Text(group.status,Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall,color=if(group.incomplete) Amber else Muted)
            }
            if(expanded) Column(Modifier.padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                content()
                TextButton(onCollapse,Modifier.fillMaxWidth().testTag("province-collapse-${group.province.id}")) { Text("收起${group.province.name}  ⌃") }
            }
        }
    }
}
