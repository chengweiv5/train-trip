package cn.traintrip.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Page { FILTERS, WISHLIST, ADD_CITY, CITY_QUERY, RESULTS, DESTINATION, DETAIL, SETTINGS, OFFLINE, ABOUT }
data class UiState(
    val catalog:StationCatalog, val filters:SearchFilters, val applied:SearchFilters?=null,
    val sourceInfo:SourceInfo?=null, val loading:Boolean=false, val error:String?=null,
    val page:Page=Page.FILTERS, val progress:SearchProgress?=null, val cityId:String?=null,
    val selectedTripKey:String?=null,
    val notice:String?=null, val cityRefresh:SearchProgress?=null,
    val citySortByCount:Boolean=false, val searchSession:Long=0, val detailFromGuide:Boolean=false,
    val queryCityId:String?=null,val cityQueryFilters:SearchFilters?=null,val guideFromResults:Boolean=false,val navigationEntry:Long=0
)
class AppViewModel @JvmOverloads constructor(app:Application,private val source:TicketSource=OfficialTicketSource()):AndroidViewModel(app) {
    private val preferences=Preferences(app)
    private val initialCatalog=StationCatalog.bundled()
    private val mutable=MutableStateFlow(UiState(initialCatalog,preferences.load(initialCatalog)))
    val state:StateFlow<UiState> = mutable.asStateFlow()
    private var searchJob:Job?=null
    private var refreshJob:Job?=null
    private data class Location(val page:Page,val cityId:String?,val fromGuide:Boolean,val guideFromResults:Boolean,val queryCityId:String?,val draft:SearchFilters?,val entry:Long)
    private val history=java.util.ArrayDeque<Location>()
    private var nextEntry=0L
    fun navigate(page:Page,cityId:String?=mutable.value.cityId) {
        val current=mutable.value
        history.addLast(Location(current.page,current.cityId,current.detailFromGuide,current.guideFromResults,current.queryCityId,current.cityQueryFilters,current.navigationEntry))
        mutable.update { it.copy(page=page,cityId=cityId,navigationEntry=++nextEntry,selectedTripKey=null,notice=null,
            guideFromResults=if(page==Page.DESTINATION)current.page==Page.RESULTS else it.guideFromResults) }
    }
    fun back() {
        pauseForegroundWork()
        val previous=history.pollLast()
        mutable.update { if(previous==null)it.copy(page=Page.FILTERS,queryCityId=null,cityQueryFilters=null)
            else it.copy(page=previous.page,cityId=previous.cityId,detailFromGuide=previous.fromGuide,
                guideFromResults=previous.guideFromResults,queryCityId=previous.queryCityId,cityQueryFilters=previous.draft,navigationEntry=previous.entry,selectedTripKey=null) }
    }
    fun selectRoot(page:Page) {
        require(page==Page.FILTERS || page==Page.WISHLIST)
        if(mutable.value.page==page)return
        history.clear();mutable.update { it.copy(page=page,queryCityId=null,cityQueryFilters=null,error=null) }
    }
    fun openCityQuery(id:String) {
        val current=mutable.value
        val date=current.filters.startDate.takeIf { it>=today() } ?: today().plusDays(1)
        val end=current.filters.endDate.takeIf { current.filters.startDate>=today() && it>=date } ?: date
        navigate(Page.CITY_QUERY,id)
        mutable.update { it.copy(queryCityId=id,cityQueryFilters=current.filters.copy(startDate=date,endDate=end,destinationCityIds=setOf(id)),error=null) }
    }
    fun updateFilters(f:SearchFilters) {
        if(mutable.value.page==Page.CITY_QUERY) mutable.update { it.copy(cityQueryFilters=f.copy(destinationCityIds=setOfNotNull(it.queryCityId)),error=null) }
        else { preferences.save(f);mutable.update { it.copy(filters=f,error=null) } }
    }
    fun showFilters() {
        if(mutable.value.page==Page.RESULTS) back()
        else { pauseForegroundWork();mutable.update { it.copy(page=Page.FILTERS) } }
    }
    fun showResults() { refreshJob?.cancel();mutable.update { it.copy(page=Page.RESULTS,selectedTripKey=null) } }
    fun showCity(id:String) { navigate(Page.DETAIL,id);mutable.update { it.copy(cityRefresh=null,detailFromGuide=false) } }
    fun showDestination(id:String) { navigate(Page.DESTINATION,id);mutable.update { it.copy(cityRefresh=null) } }
    fun showDestinationTrains() {
        val current=mutable.value
        if(current.guideFromResults) { navigate(Page.DETAIL);mutable.update { it.copy(detailFromGuide=true) } }
        else current.cityId?.let(::openCityQuery)
    }
    fun backFromCity() = back()
    fun sortCities() { mutable.update { it.copy(citySortByCount=!it.citySortByCount) } }
    fun dismissNotice() { mutable.update { it.copy(notice=null) } }
    fun clearSelection() { mutable.update { it.copy(selectedTripKey=null,notice=null) } }
    fun select(trip:Trip) {
        mutable.update { it.copy(selectedTripKey=trip.key,notice=null) }
    }
    fun reportAppLaunch(result:AppLaunchResult) {
        mutable.update { it.copy(notice=when(result) {
            AppLaunchResult.OPENED -> null
            AppLaunchResult.NOT_INSTALLED -> if(it.selectedTripKey!=null) "未安装铁路12306\n安装后重试，已选车次会保留" else "未安装铁路12306\n安装后重试"
            AppLaunchResult.FAILED -> if(it.selectedTripKey!=null) "暂时无法打开 12306 App\n已选车次会保留，请稍后重试" else "暂时无法打开 12306 App\n请稍后重试"
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
        val filters=if(resume || retryFailed || refresh) current.applied ?: current.filters else current.cityQueryFilters ?: current.filters
        filters.validate()?.let { error -> mutable.update { it.copy(error=error) };return }
        if(current.queryCityId!=null && filters.originCityId==current.queryCityId) {
            mutable.update { it.copy(error="出发地和目的地不能相同，请修改出发地") };return
        }
        searchJob?.cancel();refreshJob?.cancel()
        if(current.page!=Page.RESULTS)navigate(Page.RESULTS)
        val previous=if(resume || retryFailed) current.progress else null
        mutable.update { it.copy(searchSession=if(resume || retryFailed || refresh) it.searchSession else it.searchSession+1,applied=filters,page=Page.RESULTS,loading=true,error=null,progress=previous,selectedTripKey=null,cityRefresh=null,notice=null) }
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
                    val invalid=affected && old.selectedTripKey!=null && (chosen==null || !chosen.confirmed(old.applied ?: old.filters))
                    old.copy(progress=merged,cityRefresh=update,
                        selectedTripKey=if(invalid) null else old.selectedTripKey,
                        notice=if(invalid) "所选车次已不符合当前条件，请重新选择" else old.notice)
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
