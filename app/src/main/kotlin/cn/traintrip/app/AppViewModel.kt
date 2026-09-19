package cn.traintrip.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Page { FILTERS, RESULTS, DETAIL }
data class UiState(
    val catalog:StationCatalog, val filters:SearchFilters, val applied:SearchFilters?=null,
    val sourceInfo:SourceInfo?=null, val loading:Boolean=false, val error:String?=null,
    val page:Page=Page.FILTERS, val progress:SearchProgress?=null, val cityId:String?=null,
    val selectedTripKey:String?=null, val selectedSeat:SeatType?=null, val rechecking:Boolean=false,
    val notice:String?=null, val handoffReady:Boolean=false, val handoffText:String?=null,
    val citySortByCount:Boolean=false, val searchSession:Long=0
)
class AppViewModel(app:Application):AndroidViewModel(app) {
    private val preferences=Preferences(app)
    private val source:TicketSource=OfficialTicketSource()
    private val initialCatalog=StationCatalog.bundled()
    private val mutable=MutableStateFlow(UiState(initialCatalog,preferences.load(initialCatalog)))
    val state:StateFlow<UiState> = mutable.asStateFlow()
    private var searchJob:Job?=null
    private var checkJob:Job?=null
    fun updateFilters(f:SearchFilters) { preferences.save(f);mutable.update { it.copy(filters=f,error=null) } }
    fun showFilters() { stopSearch();checkJob?.cancel();mutable.update { it.copy(page=Page.FILTERS,rechecking=false) } }
    fun showResults() { checkJob?.cancel();mutable.update { it.copy(page=Page.RESULTS,rechecking=false,selectedTripKey=null,selectedSeat=null) } }
    fun showCity(id:String) { mutable.update { it.copy(page=Page.DETAIL,cityId=id,selectedTripKey=null,selectedSeat=null) } }
    fun sortCities() { mutable.update { it.copy(citySortByCount=!it.citySortByCount) } }
    fun dismissNotice() { mutable.update { it.copy(notice=null,handoffReady=false) } }
    fun select(trip:Trip,seat:SeatType) {
        checkJob?.cancel()
        mutable.update { it.copy(selectedTripKey=trip.key,selectedSeat=seat,rechecking=false,handoffReady=false,handoffText=null) }
    }
    fun stopSearch() { searchJob?.cancel();mutable.update { it.copy(loading=false,progress=it.progress?.copy(running=false,stopped=it.progress.running || it.progress.stopped)) } }
    fun pauseForegroundWork() {
        stopSearch()
        checkJob?.cancel()
        mutable.update { it.copy(rechecking=false) }
    }
    fun search(resume:Boolean=false,retryFailed:Boolean=false,refresh:Boolean=false) {
        val current=mutable.value
        val filters=if(resume || retryFailed || refresh) current.applied ?: current.filters else current.filters
        filters.validate()?.let { error -> mutable.update { it.copy(error=error) };return }
        searchJob?.cancel();checkJob?.cancel()
        val previous=if(resume || retryFailed) current.progress else null
        mutable.update { it.copy(searchSession=if(resume || retryFailed || refresh) it.searchSession else it.searchSession+1,applied=filters,page=Page.RESULTS,loading=true,error=null,progress=previous,selectedTripKey=null,selectedSeat=null,rechecking=false) }
        searchJob=viewModelScope.launch {
            try {
                val info=source.initialize()
                val plan=info.catalog.plan(filters)
                val existing=previous?.outcomes?.filterValues { it !is QueryResult.Failure && it !is QueryResult.NotOnSale } ?: emptyMap()
                mutable.update { it.copy(catalog=info.catalog,sourceInfo=info,loading=false) }
                SearchEngine(source).search(plan,existing).collect { progress -> mutable.update { it.copy(progress=progress) } }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.update { it.copy(loading=false,error=e.message ?: "查询失败，请稍后重试",progress=it.progress?.copy(running=false,stopped=true)) } }
        }
    }
    fun recheck() {
        val s=mutable.value;val f=s.applied ?: return
        val trip=s.progress?.trips?.firstOrNull { it.key==s.selectedTripKey } ?: return
        val seat=s.selectedSeat ?: return
        stopSearch()
        checkJob?.cancel();mutable.update { it.copy(rechecking=true,notice=null,handoffReady=false,handoffText=null) }
        checkJob=viewModelScope.launch {
            try {
                source.initialize()
                val result=source.query(QueryUnit(trip.date,trip.from,trip.to))
                when(result) {
                    is QueryResult.Success -> {
                        val fresh=result.trips.firstOrNull { it.key==trip.key }
                        mutable.update { old ->
                            val outcomes=old.progress?.outcomes?.mapValues { (_,v) -> if(v is QueryResult.Success) v.copy(trips=v.trips.mapNotNull { t -> if(t.key==trip.key) fresh else t }) else v }
                            old.copy(progress=old.progress?.copy(outcomes=outcomes ?: emptyMap()))
                        }
                        val available=fresh?.takeIf { it.isSaleable() }?.seats?.get(seat)
                        when {
                            available?.confirmedFor(f.people)==true -> mutable.update { it.copy(rechecking=false,handoffReady=true,handoffText=itinerary(fresh,seat,f.people),notice="已核验当前余票。请在 12306 完成登录与购票。") }
                            available?.uncertainFor(f.people)==true -> mutable.update { it.copy(rechecking=false,handoffReady=true,handoffText=itinerary(fresh,seat,f.people),notice="有票，但没有具体张数，尚不能确认足够 ${f.people} 人。请到 12306 确认。") }
                            else -> mutable.update { it.copy(rechecking=false,selectedTripKey=null,selectedSeat=null,notice="这趟车当前不满足所选席别和人数，请选择其他车次。") }
                        }
                    }
                    is QueryResult.Failure -> mutable.update { it.copy(rechecking=false,notice="没能确认最新余票：${result.message}\n旧结果时间：${formatTime(trip.queriedAt)}。",handoffText=itinerary(trip,seat,f.people)) }
                    is QueryResult.NotOnSale -> mutable.update { it.copy(rechecking=false,notice=result.message,selectedTripKey=null,selectedSeat=null) }
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.update { it.copy(rechecking=false,notice="核验失败：${e.message}。旧结果时间：${formatTime(trip.queriedAt)}。",handoffText=itinerary(trip,seat,f.people)) } }
        }
    }
}
fun formatTime(at:java.time.Instant):String = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(BEIJING_ZONE).format(at)
fun itinerary(t:Trip,seat:SeatType,people:Int) = "${t.date} ${t.trainCode}\n${t.from.name} ${t.departure} → ${t.to.name} ${t.arrival}${t.arrivalDayOffset?.takeIf { it>0 }?.let { "（+$it 天）" } ?: ""}\n${seat.label} · $people 位成人\n查询时间：${formatTime(t.queriedAt)}\n余票以 12306 实时结果为准"
