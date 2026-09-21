package cn.traintrip.core

enum class DeparturePeriod(val label:String,val startMinute:Int,val endMinute:Int) {
    EARLY("凌晨",0,360), MORNING("早上",360,720), AFTERNOON("下午",720,1080), EVENING("晚上",1080,1440)
}

val SearchFilters.isAllDay:Boolean
    get()=departurePeriods.size==DeparturePeriod.entries.size ||
        (departurePeriods.isEmpty() && startMinute==0 && endMinute==1440)

val SearchFilters.selectedDeparturePeriods:Set<DeparturePeriod>
    get()=when {
        isAllDay->emptySet()
        departurePeriods.isNotEmpty()->departurePeriods
        else->DeparturePeriod.entries.filter { it.startMinute==startMinute && it.endMinute==endMinute }.toSet()
    }

fun SearchFilters.withCustomTime(start:Int,end:Int)=copy(startMinute=start,endMinute=end,departurePeriods=emptySet())

fun SearchFilters.withDeparturePeriods(periods:Set<DeparturePeriod>):SearchFilters {
    if(periods.isEmpty() || periods.size==DeparturePeriod.entries.size)return withCustomTime(0,1440)
    val first=periods.minBy { it.startMinute }
    return copy(startMinute=first.startMinute,endMinute=first.endMinute,departurePeriods=periods.toSet())
}

fun SearchFilters.toggleDeparturePeriod(period:DeparturePeriod):SearchFilters {
    val current=selectedDeparturePeriods
    return withDeparturePeriods(if(period in current)current-period else current+period)
}
