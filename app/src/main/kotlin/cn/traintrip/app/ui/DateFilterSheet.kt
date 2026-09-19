@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.traintrip.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.traintrip.app.UiState
import cn.traintrip.core.SearchFilters
import cn.traintrip.core.today
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Composable fun DateFilterSheet(s:UiState,onDismiss:()->Unit,onApply:(SearchFilters)->Unit) {
    var range by rememberSaveable { mutableStateOf(s.filters.startDate!=s.filters.endDate) }
    val earliest=remember { today() }
    val selectable=remember(earliest) { object:SelectableDates {
        override fun isSelectableDate(utcTimeMillis:Long)=calendarDate(utcTimeMillis)>=earliest
        override fun isSelectableYear(year:Int)=year>=earliest.year
    } }
    val single=rememberDatePickerState(initialSelectedDateMillis=calendarMillis(s.filters.startDate),selectableDates=selectable)
    val dates=rememberDateRangePickerState(
        initialSelectedStartDateMillis=calendarMillis(s.filters.startDate),
        initialSelectedEndDateMillis=if(range) calendarMillis(s.filters.endDate) else null,
        selectableDates=selectable
    )
    val start=(if(range) dates.selectedStartDateMillis else single.selectedDateMillis)?.let(::calendarDate)
    val end=(if(range) dates.selectedEndDateMillis else single.selectedDateMillis)?.let(::calendarDate)
    val candidate=if(start!=null && end!=null) s.filters.copy(startDate=start,endDate=end) else null
    val error=candidate?.validate()
    val days=if(start!=null && end!=null) ChronoUnit.DAYS.between(start,end)+1 else null
    val calendarColors=DatePickerDefaults.colors(dayInSelectionRangeContainerColor=Sage,dayInSelectionRangeContentColor=Forest)
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Cream) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).testTag("date-sheet")) {
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("选择出发日期",style=MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Choice("单日",!range,{
                        if(range) dates.selectedStartDateMillis?.let { single.selectedDateMillis=it;single.displayedMonthMillis=it }
                        range=false
                    },Modifier.weight(1f))
                    Choice("日期范围",range,{
                        if(!range) { dates.setSelection(null,null);dates.displayedMonthMillis=single.displayedMonthMillis }
                        range=true
                    },Modifier.weight(1f))
                }
                Text(when { !range->"点选出发日期";start==null->"先选开始日期，再选结束日期";end==null->"已选 ${dateLabel(start)}，请选择结束日期";else->"${dateLabel(start)} – ${dateLabel(end)} · 共 $days 天" },
                    Modifier.testTag("date-selection-summary"),style=MaterialTheme.typography.bodyMedium,color=Forest)
            }
            if(range) DateRangePicker(dates,Modifier.weight(1f).testTag("range-calendar"),title=null,headline=null,showModeToggle=false,colors=calendarColors)
            else DatePicker(single,Modifier.weight(1f).testTag("single-calendar"),title=null,headline=null,showModeToggle=false,colors=calendarColors)
            Column(Modifier.padding(horizontal=20.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(error ?: "范围两端均包含，最多 31 天。未开售日期会在查询时提示。",color=if(error!=null) Amber else Muted,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("取消",onDismiss,Modifier.weight(1f))
                    PrimaryButton(if(days!=null) "完成 · $days 天" else "完成",{candidate?.let(onApply)},candidate!=null && error==null,Modifier.weight(1f).testTag("apply-dates"))
                }
            }
        }
    }
}

private fun calendarMillis(date:LocalDate)=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun calendarDate(millis:Long)=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
