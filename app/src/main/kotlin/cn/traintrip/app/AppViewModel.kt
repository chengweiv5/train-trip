package cn.traintrip.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Page { FILTERS, RESULTS, DESTINATION, DETAIL }
data class UiState(
    val catalog:StationCatalog, val filters:SearchFilters, val applied:SearchFilters?=null,
    val sourceInfo:SourceInfo?=null, val loading:Boolean=false, val error:String?=null,
    val page:Page=Page.FILTERS, val progress:SearchProgress?=null, val cityId:String?=null,
    val selectedTripKey:String?=null, val selectedSeat:SeatType?=null,
    val notice:String?=null, val cityRefresh:SearchProgress?=null,
    val citySortByCount:Boolean=false, val searchSession:Long=0, val detailFromGuide:Boolean=false
)
class AppViewModel @JvmOverloads constructor(app:Application,private val source:TicketSource=OfficialTicketSource()):AndroidViewModel(app) {
    private val preferences=Preferences(app)
    private val initialCatalog=StationCatalog.bundled()
    private val mutable=MutableStateFlow(UiState(initialCatalog,preferences.load(initialCatalog)))
    val state:StateFlow<UiState> = mutable.asStateFlow()
    private var searchJob:Job?=null
    private var refreshJob:Job?=null
    fun updateFilters(f:SearchFilters) { preferences.save(f);mutable.update { it.copy(filters=f,error=null) } }
    fun showFilters() { stopSearch();refreshJob?.cancel();mutable.update { it.copy(page=Page.FILTERS,cityRefresh=it.cityRefresh?.copy(running=false,stopped=it.cityRefresh.running || it.cityRefresh.stopped)) } }
    fun showResults() { refreshJob?.cancel();mutable.update { it.copy(page=Page.RESULTS,cityRefresh=it.cityRefresh?.copy(running=false,stopped=it.cityRefresh.running || it.cityRefresh.stopped),selectedTripKey=null,selectedSeat=null) } }
    fun showCity(id:String) { mutable.update { it.copy(page=Page.DETAIL,cityId=id,selectedTripKey=null,selectedSeat=null,cityRefresh=null,notice=null,detailFromGuide=false) } }
    fun showDestination(id:String) { mutable.update { it.copy(page=Page.DESTINATION,cityId=id,selectedTripKey=null,selectedSeat=null,cityRefresh=null,notice=null) } }
    fun showDestinationTrains() { mutable.update { it.copy(page=Page.DETAIL,detailFromGuide=true,selectedTripKey=null,selectedSeat=null) } }
    fun backFromCity() {
        refreshJob?.cancel()
        mutable.update { it.copy(page=if(it.page==Page.DETAIL && it.detailFromGuide) Page.DESTINATION else Page.RESULTS,
            cityRefresh=it.cityRefresh?.copy(running=false,stopped=it.cityRefresh.running || it.cityRefresh.stopped),selectedTripKey=null,selectedSeat=null) }
    }
    fun sortCities() { mutable.update { it.copy(citySortByCount=!it.citySortByCount) } }
    fun dismissNotice() { mutable.update { it.copy(notice=null) } }
    fun clearSelection() { mutable.update { it.copy(selectedTripKey=null,selectedSeat=null,notice=null) } }
    fun select(trip:Trip,seat:SeatType) {
        mutable.update { it.copy(selectedTripKey=trip.key,selectedSeat=seat,notice=null) }
    }
    fun reportAppLaunch(result:AppLaunchResult) {
        mutable.update { it.copy(notice=when(result) {
            AppLaunchResult.OPENED -> null
            AppLaunchResult.NOT_INSTALLED -> "未安装铁路12306\n安装后重试，已选车次会保留"
            AppLaunchResult.FAILED -> "暂时无法打开 12306 App\n已选车次会保留，请稍后重试"
        }) }
    }
    fun stopSearch() { searchJob?.cancel();mutable.update { it.copy(loading=false,progress=it.progress?.copy(running=false,stopped=it.progress.running || it.progress.stopped)) } }
    fun pauseForegroundWork() {
        stopSearch()
        refreshJob?.cancel()
        mutable.update { it.copy(cityRefresh=it.cityRefresh?.copy(running=false,stopped=it.cityRefresh.running || it.cityRefresh.stopped)) }
    }
    fun search(resume:Boolean=false,retryFailed:Boolean=false,refresh:Boolean=false) {
        val current=mutable.value
        val filters=if(resume || retryFailed || refresh) current.applied ?: current.filters else current.filters
        filters.validate()?.let { error -> mutable.update { it.copy(error=error) };return }
        searchJob?.cancel();refreshJob?.cancel()
        val previous=if(resume || retryFailed) current.progress else null
        mutable.update { it.copy(searchSession=if(resume || retryFailed || refresh) it.searchSession else it.searchSession+1,applied=filters,page=Page.RESULTS,loading=true,error=null,progress=previous,selectedTripKey=null,selectedSeat=null,cityRefresh=null,notice=null) }
        searchJob=viewModelScope.launch {
            try {
                val info=source.initialize()
                val plan=info.catalog.plan(filters)
                val existing=previous?.outcomes?.filterValues { it !is QueryResult.Failure && it !is QueryResult.NotOnSale } ?: emptyMap()
                mutable.update { it.copy(catalog=info.catalog,sourceInfo=info,loading=false) }
                SearchEngine(source).search(plan,existing,previous?.retainedSuccesses.orEmpty()).collect { progress -> mutable.update { it.copy(progress=progress) } }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.update { it.copy(loading=false,error=e.message ?: "查询失败，请稍后重试",progress=it.progress?.copy(running=false,stopped=true)) } }
        }
    }
    fun refreshCity(retryFailed:Boolean=false) {
        val current=mutable.value
        if(current.cityRefresh?.running==true || current.page!=Page.DETAIL) return
        val city=current.cityId ?: return
        val progress=current.progress ?: return
        val retryKeys=current.cityRefresh?.let { refresh ->
            refresh.plan.filter { refresh.outcomes[it.key] !is QueryResult.Success }.map { it.key }.toSet()
        }.orEmpty()
        val plan=progress.plan.filter { it.destination.cityId==city && (!retryFailed || it.key in retryKeys) }
        if(plan.isEmpty()) return
        stopSearch()
        mutable.update { it.copy(cityRefresh=SearchProgress(plan,running=true),notice=null) }
        refreshJob=viewModelScope.launch {
            fun apply(update:SearchProgress) {
                mutable.update { old ->
                    val merged=old.progress?.mergeRefresh(update)
                    val affected=update.outcomes.filterValues { it is QueryResult.Success }.keys.any { key ->
                        old.progress?.successfulData?.get(key)?.trips?.any { it.key==old.selectedTripKey }==true
                    }
                    val chosen=merged?.trips?.firstOrNull { it.key==old.selectedTripKey }
                    val invalid=affected && old.selectedTripKey!=null && (chosen==null || !chosen.confirmed(old.applied ?: old.filters) ||
                        chosen.seats[old.selectedSeat]?.confirmedFor((old.applied ?: old.filters).people)!=true)
                    old.copy(progress=merged,cityRefresh=update,
                        selectedTripKey=if(invalid) null else old.selectedTripKey,
                        selectedSeat=if(invalid) null else old.selectedSeat,
                        notice=if(invalid) "所选席别当前不满足人数，请重新选择一个席别" else old.notice)
                }
            }
            try {
                source.initialize()
                SearchEngine(source).search(plan).collect { apply(it) }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                apply(SearchProgress(plan,plan.associate { it.key to QueryResult.Failure(e.message ?: "查询失败，请稍后重试") }))
            }
        }
    }

}
fun formatTime(at:java.time.Instant):String = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(BEIJING_ZONE).format(at)
