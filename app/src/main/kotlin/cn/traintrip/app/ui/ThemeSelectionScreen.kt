package cn.traintrip.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.traintrip.app.ThemeChoice
import cn.traintrip.app.ThemeUiState
import kotlinx.coroutines.delay

@Composable internal fun ThemeSettingsRow(choice:ThemeChoice,onClick:()->Unit) {
    ContentCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button,onClick=onClick)
            .testTag("settings-theme"),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(20.dp),shape=CircleShape,color=PrimaryTint) {
                Box(Modifier.padding(5.dp).background(Primary,CircleShape))
            }
            Text("主题配色",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Box(Modifier.size(16.dp).background(choice.palette.primary,CircleShape))
            Text(choice.label,style=MaterialTheme.typography.bodySmall,color=Muted)
            UiIcon("next",color=Muted)
        }
    }
}

@Composable internal fun ThemeSelectionScreen(
    state:ThemeUiState,
    onSelect:(ThemeChoice)->Unit,
    onRetry:()->Unit,
    onDismissMessage:(Long)->Unit,
) {
    val accessibility=LocalAccessibilityManager.current
    LaunchedEffect(state.event,state.message) {
        if(state.message!=null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(4000,containsIcons=true,containsText=true,containsControls=false) ?: 4000)
            onDismissMessage(state.event)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("theme-screen")) {
        val compact=maxWidth<360.dp || LocalDensity.current.fontScale>1.15f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("选一种喜欢的颜色",style=MaterialTheme.typography.titleLarge)
                Text("点选后立即生效，下次打开仍会保留。",style=MaterialTheme.typography.bodyMedium,color=Muted)
            }
            ThemePreview(state.choice)
            Column(Modifier.fillMaxWidth().selectableGroup().testTag(if(compact)"theme-list" else "theme-grid"),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(compact) ThemeChoice.entries.forEach { choice->
                    ThemeOption(choice,state.choice==choice,state.ready,onSelect,Modifier.fillMaxWidth(),compact=true)
                } else ThemeChoice.entries.chunked(2).forEach { pair->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        pair.forEach { choice->ThemeOption(choice,state.choice==choice,state.ready,onSelect,
                            Modifier.weight(1f).fillMaxHeight(),compact=false) }
                    }
                }
            }
            Text("有票、提醒等状态色保持一致，便于辨认。",style=MaterialTheme.typography.bodySmall,color=Muted)
            if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("theme-loading"))
            state.error?.let { error->
                ThemeFeedback(error,true)
                if(!state.ready) SecondaryButton("重新读取主题",onRetry,Modifier.testTag("theme-retry"))
            }
            state.message?.let { ThemeFeedback(it,false) }
        }
    }
}

@Composable private fun ThemeOption(choice:ThemeChoice,selected:Boolean,enabled:Boolean,onSelect:(ThemeChoice)->Unit,
    modifier:Modifier,compact:Boolean) {
    val palette=choice.palette
    Surface(modifier.heightIn(min=90.dp).selectable(selected,enabled,Role.RadioButton) { onSelect(choice) }
        .semantics { stateDescription=if(selected)"使用中" else "未选择" }.testTag("theme-${choice.id}"),
        shape=RoundedCornerShape(12.dp),color=palette.card,contentColor=palette.ink,
        border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)palette.primary else palette.cardBorder)) {
        if(compact) Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(palette.primary,CircleShape))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(choice.label,style=MaterialTheme.typography.titleMedium)
                ThemeOptionStatus(choice,selected)
            }
            ThemeRadio(selected,palette)
        } else Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(choice.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                ThemeRadio(selected,palette)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(palette.primary,palette.tint,Color.White).forEach { color->
                    Box(Modifier.weight(1f).height(22.dp).background(color,RoundedCornerShape(4.dp)))
                }
            }
            ThemeOptionStatus(choice,selected)
        }
    }
}

@Composable private fun ThemeRadio(selected:Boolean,palette:ThemePalette) {
    RadioButton(selected,onClick=null,modifier=Modifier.size(22.dp).clearAndSetSemantics {},
        colors=RadioButtonDefaults.colors(selectedColor=palette.primary,unselectedColor=palette.muted))
}

@Composable private fun ThemeOptionStatus(choice:ThemeChoice,selected:Boolean) {
    Text(if(selected)"使用中" else if(choice==ThemeChoice.BLUE)"默认配色" else "点选使用",
        style=MaterialTheme.typography.bodySmall,color=if(selected)choice.palette.primary else choice.palette.muted)
}

@Composable private fun ThemePreview(choice:ThemeChoice) {
    ContentCard(Modifier.fillMaxWidth().testTag("theme-preview")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("效果预览",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Muted)
            Text("当前 · ${choice.label}",style=MaterialTheme.typography.bodySmall,color=Primary)
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("北京 → 天津",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            Surface(color=AvailableTint,shape=RoundedCornerShape(5.dp)) {
                Text("✓ 有票",Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=AvailableGreen)
            }
        }
        Text("10月1日 — 10月3日 · 2 人",style=MaterialTheme.typography.bodySmall,color=Muted)
        Surface(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("theme-preview-button"),
            shape=RoundedCornerShape(8.dp),color=Primary,contentColor=Color.White) {
            Box(Modifier.padding(12.dp),contentAlignment=Alignment.Center) {
                Text("查询有票城市",style=MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun ThemeFeedback(message:String,error:Boolean) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion=LiveRegionMode.Polite }.testTag("theme-feedback"),
        color=if(error)AmberBg else PrimaryTint,shape=RoundedCornerShape(8.dp)) {
        Text((if(error)"ⓘ " else "✓ ")+message,Modifier.padding(14.dp),style=MaterialTheme.typography.bodyMedium,
            color=if(error)Amber else Primary)
    }
}
