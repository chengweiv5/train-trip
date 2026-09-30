package cn.traintrip.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface WaitlistDraftStore {
    suspend fun load(): WaitlistFilters?
    suspend fun save(filters: WaitlistFilters)
}

enum class WaitlistPage { FILTERS, RESULTS, SELECTED, AUTHENTICATION, PROGRESS }

data class WaitlistUiState(
    val catalog: StationCatalog = StationCatalog.bundled(),
    val draft: WaitlistFilters = WaitlistFilters(),
    val applied: WaitlistFilters? = null,
    val sourceInfo: SourceInfo? = null,
    val page: WaitlistPage = WaitlistPage.FILTERS,
    val ready: Boolean = false,
    val loading: Boolean = false,
    val progress: SearchProgress? = null,
    val selected: List<WaitlistChoice> = emptyList(),
    val error: String? = null,
    val formError: String? = null,
    val storageError: String? = null,
    val operationError: String? = null,
    val notice: String? = null,
    val operation: WaitlistState? = null,
) {
    val choices get() = applied?.choices(progress?.trips.orEmpty()).orEmpty()
    val hiddenSelected get() = selected.filter { selected -> choices.none { it.demand == selected.demand } }
    val busy get() = loading || progress?.running == true
    val queryComplete get() = !loading && error == null && progress?.complete == true
}

/** Read-only anonymous query is intentionally independent of any authenticated order session. */
class WaitlistViewModel(
    private val source: TicketSource,
    private val drafts: WaitlistDraftStore,
    private val coordinator: WaitlistCoordinator? = null,
) : ViewModel() {
    private val mutable = MutableStateFlow(WaitlistUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var generation = 0L
    private val draftMutex = Mutex()
    private var draftRevision = 0L

    init {
        viewModelScope.launch {
            try {
                val saved = drafts.load()
                mutable.update { it.copy(draft = saved ?: it.draft, ready = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(ready = true, storageError = "候补条件读取失败，未覆盖原记录；请重试保存") }
            }
        }
        coordinator?.let { flow ->
            viewModelScope.launch { flow.state.collect { value -> mutable.update { it.copy(operation = value) } } }
            viewModelScope.launch {
                try { flow.restore() }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutable.update { it.copy(operationError = "候补进度读取失败，已禁止后续提交；请勿重复下单") }
                }
            }
        }
    }

    fun updateFilters(filters: WaitlistFilters) {
        if (!state.value.ready) return
        mutable.update { it.copy(draft = filters, formError = null) }
        saveDraft()
    }

    fun saveDraft() {
        val revision = ++draftRevision
        val draft = state.value.draft
        viewModelScope.launch {
            draftMutex.withLock {
                if (revision != draftRevision) return@withLock
                try {
                    drafts.save(draft)
                    if (revision == draftRevision) mutable.update { it.copy(storageError = null) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (revision == draftRevision) mutable.update { it.copy(storageError = "候补条件未能保存，当前输入仍保留在本页，请重试") }
                }
            }
        }
    }

    fun edit() { mutable.update { it.copy(page = WaitlistPage.FILTERS) } }
    fun showSelected() { mutable.update { it.copy(page = WaitlistPage.SELECTED) } }
    fun showProgress() { mutable.update { it.copy(page = WaitlistPage.PROGRESS) } }
    fun next() {
        if (state.value.selected.isNotEmpty()) mutable.update { it.copy(page = WaitlistPage.AUTHENTICATION) }
    }
    fun back() {
        mutable.update { it.copy(page = when (it.page) {
            WaitlistPage.AUTHENTICATION -> WaitlistPage.SELECTED
            WaitlistPage.SELECTED -> WaitlistPage.RESULTS
            WaitlistPage.RESULTS -> WaitlistPage.FILTERS
            WaitlistPage.PROGRESS -> WaitlistPage.FILTERS
            WaitlistPage.FILTERS -> if (it.applied != null) WaitlistPage.RESULTS else WaitlistPage.FILTERS
        }) }
    }

    fun toggle(choice: WaitlistChoice) {
        val demand = choice.demand ?: return
        mutable.update { current ->
            if (current.selected.any { it.demand == demand })
                current.copy(selected = current.selected.filterNot { it.demand == demand })
            else if (current.choices.any { it.demand == demand && it.eligibility == WaitlistEligibility.AVAILABLE })
                current.copy(selected = current.selected + choice)
            else current
        }
    }

    fun search() = runSearch(state.value.draft)
    fun retryQuery() = runSearch(state.value.applied ?: state.value.draft)
    fun openDestination(cityId: String) {
        updateFilters(state.value.draft.copy(destinationCityId = cityId, destinationStations = emptySet()))
        edit()
    }

    private fun runSearch(filters: WaitlistFilters) {
        val current = state.value
        if (!current.ready) return
        filters.validate(current.catalog)?.let { error ->
            mutable.update { it.copy(formError = error) }; return
        }
        query?.cancel()
        val request = ++generation
        val compatible = current.selected.filter(filters::compatible)
        mutable.update { it.copy(applied = filters, page = WaitlistPage.RESULTS, progress = null,
            loading = true, error = null, formError = null, selected = compatible,
            notice = if (compatible.size < current.selected.size)
                "路线、车站或日期已变化，移除 ${current.selected.size - compatible.size} 项不兼容需求，请重新选择" else null) }
        query = viewModelScope.launch {
            try {
                val info = source.initialize()
                ensureActive()
                if (request != generation) return@launch
                mutable.update { it.copy(catalog = info.catalog, sourceInfo = info, loading = false) }
                val plan = filters.plan(info)
                SearchEngine(source).search(plan).collect { progress ->
                    if (request == generation) mutable.update { it.copy(progress = progress) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == generation) mutable.update { it.copy(loading = false,
                    error = e.message?.take(180) ?: "查询失败，请稍后重试") }
            }
        }
    }

    fun pauseForegroundWork() {
        query?.cancel()
        generation++
        mutable.update { it.copy(loading = false, progress = it.progress?.copy(running = false,
            stopped = it.progress.running || it.progress.stopped)) }
        stopOperation(WaitlistPause.BACKGROUND)
    }

    fun stopOperation(reason: WaitlistPause = WaitlistPause.STOPPED) {
        coordinator?.let { flow ->
            viewModelScope.launch {
                try { flow.stop(reason) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutable.update { it.copy(operationError = "候补进度未能保存，已停止发送后续请求；未决订单仍需核对") }
                }
            }
        }
    }
}
