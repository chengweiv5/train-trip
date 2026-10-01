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

enum class WaitlistPage { FILTERS, RESULTS, SELECTED, AUTHENTICATION, CONFIRMATION, PROGRESS }
enum class RailwayQrUiPhase { IDLE, CREATING, WAITING_SCAN, WAITING_CONFIRMATION, VERIFYING, CONNECTED, EXPIRED, ERROR }
enum class RailwayPasswordUiPhase { IDLE, CHECKING, SMS_REQUIRED, SMS_SENDING, SMS_SENT, SLIDE_REQUIRED, VERIFYING, CONNECTED, ERROR }

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
    val account: RailwayAccount? = null,
    val accountBusy: Boolean = false,
    val accountError: String? = null,
    val railwayAppNotice: String? = null,
    val passengerSelection: Set<String> = emptySet(),
    val qrChallenge: RailwayQrChallenge? = null,
    val qrPhase: RailwayQrUiPhase = RailwayQrUiPhase.IDLE,
    val passwordPhase: RailwayPasswordUiPhase = RailwayPasswordUiPhase.IDLE,
    val smsRemainingSeconds: Int = 0,
    val orderPreview: RailwayOrderPreview? = null,
    val orderBusy: Boolean = false,
    val deadlineMinutes: Int? = null,
    val riskVisible: Boolean = false,
    val riskVerified: Boolean = false,
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
    private val accountService: RailwayAccountService? = null,
    private val accountDiagnostic: (String, Int) -> Unit = { _, _ -> },
    private val qrLogin: RailwayQrLogin? = null,
    private val qrPreview: (String?) -> Unit = {},
    private val passwordLogin: RailwayPasswordLogin? = null,
    private val orders: RailwayWaitlistGateway? = null,
) : ViewModel() {
    private val mutable = MutableStateFlow(WaitlistUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var generation = 0L
    private val draftMutex = Mutex()
    private var draftRevision = 0L
    private var accountJob: Job? = null
    private var qrJob: Job? = null
    private var authGeneration = 0L
    private var smsCountdown: Job? = null
    private var orderJob: Job? = null
    private var riskProof: RailwayRiskProof? = null
    private var riskChallenge: RailwayRiskChallenge? = null
    private var orderRevision = 0L

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
        clearOrderPreparation()
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

    fun edit() { clearOrderPreparation(); mutable.update { it.copy(page = WaitlistPage.FILTERS) } }
    /** Reopen the applied snapshot explicitly; editing a draft never changes existing results. */
    fun showLastResults() {
        mutable.update { s ->
            if (s.page == WaitlistPage.FILTERS && s.applied != null && !s.orderBusy)
                s.copy(page = WaitlistPage.RESULTS)
            else s
        }
    }
    fun showSelected() { mutable.update { it.copy(page = WaitlistPage.SELECTED) } }
    fun showProgress() { mutable.update { it.copy(page = WaitlistPage.PROGRESS) } }
    fun next() {
        if (state.value.selected.isNotEmpty()) openAccount()
    }
    val authenticationAvailable get() = accountService != null
    val nativeQrAvailable get() = qrLogin != null && accountService != null
    val nativePasswordAvailable get() = passwordLogin != null && accountService != null
    /** A successful external launch is not an authentication result or an account grant. */
    fun reportRailwayLoginLaunch(result: AppLaunchResult) {
        mutable.update { it.copy(railwayAppNotice = when (result) {
            AppLaunchResult.OPENED ->
                if (nativePasswordAvailable) "已打开 12306 App。若需本应用读取乘车人，请返回并在本应用登录；仅打开官方 App 不会建立本应用会话。"
                else if (nativeQrAvailable) "已打开 12306 App；请扫描本应用生成的二维码，并在官方 App 确认。本应用核验账号后才算连接成功。"
                else "已打开 12306 App，请在其中完成登录。授权同步尚未接通，返回本应用不会自动登录或同步乘车人。"
            AppLaunchResult.NOT_INSTALLED ->
                "未找到 12306 App，请先安装官方应用。已选需求仍保留，不会跳转网页登录。"
            AppLaunchResult.FAILED ->
                "无法打开 12306 App，请手动打开。已选需求仍保留，不会跳转网页登录。"
        }) }
    }
    fun openAccount() {
        if (state.value.orderBusy) return
        clearOrderPreparation()
        mutable.update { it.copy(page = WaitlistPage.AUTHENTICATION) }
        // Opening a page or another app must not trigger authentication implicitly.
    }
    private fun diagnostic(status: String, count: Int = 0) {
        runCatching { accountDiagnostic(status, count) }
    }
    private fun preview(image: String?) { runCatching { qrPreview(image) } }

    fun resetPasswordForm() {
        if (state.value.orderBusy) return
        clearOrderPreparation()
        if (state.value.accountBusy) return
        mutable.update { it.copy(account = null, passengerSelection = emptySet(),
            accountError = null, passwordPhase = RailwayPasswordUiPhase.IDLE) }
    }

    fun loginWithPassword(username: String, password: CharArray, smsCode: String = "") {
        if (state.value.orderBusy) { password.fill('\u0000'); return }
        clearOrderPreparation()
        val login = passwordLogin
        val service = accountService
        if (login == null || service == null || state.value.accountBusy) {
            password.fill('\u0000')
            return
        }
        // No credentials in StateFlow or saved state. This request owns an erasable copy.
        val secret = password.copyOf()
        password.fill('\u0000')
        val revision = ++authGeneration
        mutable.update { it.copy(accountBusy = true, account = null, passengerSelection = emptySet(),
            accountError = null, passwordPhase = RailwayPasswordUiPhase.CHECKING) }
        diagnostic("PASSWORD_CHECKING")
        accountJob = viewModelScope.launch {
            try {
                val result = login.login(username, secret, smsCode)
                ensureActive()
                if (revision != authGeneration) return@launch
                when (result) {
                    RailwayPasswordResult.SMS_REQUIRED -> {
                        mutable.update { it.copy(accountBusy = false, passwordPhase = RailwayPasswordUiPhase.SMS_REQUIRED) }
                        diagnostic("SMS_REQUIRED")
                    }
                    RailwayPasswordResult.SLIDE_REQUIRED -> {
                        mutable.update { it.copy(accountBusy = false, passwordPhase = RailwayPasswordUiPhase.SLIDE_REQUIRED,
                            accountError = "12306 当前要求滑块验证，原生验证组件尚未接通；已停止登录，不会跳过验证。") }
                        diagnostic("SLIDE_REQUIRED")
                    }
                    RailwayPasswordResult.SESSION_ESTABLISHED -> {
                        ensureActive()
                        if (revision != authGeneration) return@launch
                        mutable.update { it.copy(passwordPhase = RailwayPasswordUiPhase.VERIFYING) }
                        diagnostic("VERIFYING_ACCOUNT")
                        val account = service.refresh()
                        ensureActive()
                        if (revision != authGeneration) return@launch
                        mutable.update { it.copy(account = account, accountBusy = false, accountError = null,
                            passwordPhase = RailwayPasswordUiPhase.CONNECTED) }
                        diagnostic("AUTHENTICATED", account.passengers.size)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (revision == authGeneration) {
                    mutable.update { it.copy(accountBusy = false, account = null, passengerSelection = emptySet(),
                        passwordPhase = if (smsCode.isNotBlank()) RailwayPasswordUiPhase.SMS_REQUIRED else RailwayPasswordUiPhase.ERROR,
                        accountError = (e as? RailwayException)?.message ?: "登录响应未能核验，请重试") }
                    diagnostic((e as? RailwayException)?.reason?.name ?: "ERROR")
                }
            } finally { secret.fill('\u0000') }
        }.also { job -> job.invokeOnCompletion { secret.fill('\u0000') } }
    }

    fun requestLoginSms(username: String, identityLastFour: String) {
        val login = passwordLogin ?: return
        if (state.value.accountBusy || state.value.smsRemainingSeconds > 0) return
        val revision = ++authGeneration
        mutable.update { it.copy(accountBusy = true, accountError = null,
            passwordPhase = RailwayPasswordUiPhase.SMS_SENDING) }
        diagnostic("SMS_SENDING")
        accountJob = viewModelScope.launch {
            try {
                login.sendSms(username, identityLastFour)
                ensureActive()
                if (revision != authGeneration) return@launch
                mutable.update { it.copy(accountBusy = false, passwordPhase = RailwayPasswordUiPhase.SMS_SENT) }
                diagnostic("SMS_SENT")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (revision == authGeneration) {
                    mutable.update { it.copy(accountBusy = false, passwordPhase = RailwayPasswordUiPhase.SMS_REQUIRED,
                        accountError = (e as? RailwayException)?.message ?: "短信请求结果未能核验，请等待后重试") }
                    diagnostic((e as? RailwayException)?.reason?.name ?: "ERROR")
                }
            } finally {
                // Even timeout may have sent SMS. Prevent immediate user-triggered duplicate sends.
                if (revision == authGeneration) startSmsCountdown()
            }
        }
    }

    private fun startSmsCountdown() {
        smsCountdown?.cancel()
        smsCountdown = viewModelScope.launch {
            for (remaining in 60 downTo 1) {
                mutable.update { it.copy(smsRemainingSeconds = remaining) }
                delay(1000)
            }
            mutable.update { it.copy(smsRemainingSeconds = 0) }
        }
    }

    fun startQrLogin() {
        val login = qrLogin ?: return
        val service = accountService ?: return
        cancelQrLogin()
        accountJob?.cancel()
        val revision = ++authGeneration
        mutable.update { it.copy(qrPhase = RailwayQrUiPhase.CREATING, qrChallenge = null,
            account = null, passengerSelection = emptySet(), accountError = null, accountBusy = false,
            railwayAppNotice = null) }
        diagnostic("QR_CREATING")
        qrJob = viewModelScope.launch {
            try {
                val challenge = login.create()
                ensureActive()
                if (revision != authGeneration) return@launch
                preview(challenge.imageBase64)
                mutable.update { it.copy(qrChallenge = challenge, qrPhase = RailwayQrUiPhase.WAITING_SCAN) }
                diagnostic("QR_WAITING_SCAN")
                while (isActive && revision == authGeneration) {
                    when (login.poll(challenge)) {
                        RailwayQrStatus.WAITING_SCAN -> {
                            mutable.update { it.copy(qrPhase = RailwayQrUiPhase.WAITING_SCAN) }
                            diagnostic("QR_WAITING_SCAN")
                        }
                        RailwayQrStatus.WAITING_CONFIRMATION -> {
                            mutable.update { it.copy(qrPhase = RailwayQrUiPhase.WAITING_CONFIRMATION) }
                            diagnostic("QR_WAITING_CONFIRMATION")
                        }
                        RailwayQrStatus.EXPIRED -> {
                            mutable.update { it.copy(qrChallenge = null, qrPhase = RailwayQrUiPhase.EXPIRED) }
                            diagnostic("QR_EXPIRED")
                            return@launch
                        }
                        RailwayQrStatus.SESSION_ESTABLISHED -> {
                            preview(null)
                            mutable.update { it.copy(qrChallenge = null, qrPhase = RailwayQrUiPhase.VERIFYING,
                                accountBusy = true) }
                            diagnostic("VERIFYING_ACCOUNT")
                            val account = service.refresh()
                            ensureActive()
                            if (revision != authGeneration) return@launch
                            mutable.update { it.copy(account = account, accountBusy = false, accountError = null,
                                qrPhase = RailwayQrUiPhase.CONNECTED) }
                            diagnostic("AUTHENTICATED", account.passengers.size)
                            return@launch
                        }
                    }
                    delay(2000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (revision == authGeneration) {
                    mutable.update { it.copy(qrChallenge = null, qrPhase = RailwayQrUiPhase.ERROR,
                        account = null, accountBusy = false, passengerSelection = emptySet(),
                        accountError = (e as? RailwayException)?.message ?: "扫码认证未能核验，请重试") }
                    diagnostic((e as? RailwayException)?.reason?.name ?: "ERROR")
                }
            } finally {
                if (revision == authGeneration) preview(null)
            }
        }
    }
    fun cancelQrLogin() {
        authGeneration++
        qrJob?.cancel()
        qrJob = null
        preview(null)
        mutable.update { it.copy(qrChallenge = null,
            qrPhase = if (it.account != null) RailwayQrUiPhase.CONNECTED else RailwayQrUiPhase.IDLE,
            accountBusy = false) }
    }
    fun refreshAccount() {
        if (state.value.orderBusy) return
        clearOrderPreparation()
        val service = accountService ?: return
        if (accountJob?.isActive == true || qrJob?.isActive == true) return
        val revision = ++authGeneration
        val previous = state.value.account
        mutable.update { it.copy(accountBusy = true, accountError = null, account = null) }
        diagnostic("CHECKING")
        accountJob = viewModelScope.launch {
            try {
                val account = service.refresh()
                ensureActive()
                if (revision != authGeneration) return@launch
                mutable.update { it.copy(account = account, accountBusy = false,
                    qrPhase = RailwayQrUiPhase.CONNECTED,
                    passwordPhase = RailwayPasswordUiPhase.CONNECTED,
                    passengerSelection = if (previous?.reference == account.reference)
                        it.passengerSelection.intersect(account.passengers.filter { p -> p.selectable }.map { p -> p.reference }.toSet())
                    else emptySet()) }
                diagnostic("AUTHENTICATED", account.passengers.size)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (revision != authGeneration) return@launch
                val message = (e as? RailwayException)?.message ?: "登录状态未能核验，请重试"
                mutable.update { it.copy(account = null, accountBusy = false, accountError = message,
                    passengerSelection = emptySet(), qrPhase = RailwayQrUiPhase.ERROR,
                    passwordPhase = RailwayPasswordUiPhase.ERROR) }
                diagnostic((e as? RailwayException)?.reason?.name ?: "ERROR")
            }
        }
    }
    fun togglePassenger(reference: String) {
        if (state.value.orderBusy) return
        clearOrderPreparation()
        mutable.update { s ->
            if (s.account?.passengers?.any { it.reference == reference && it.selectable } != true) s
            else s.copy(passengerSelection = if (reference in s.passengerSelection)
                s.passengerSelection - reference else s.passengerSelection + reference)
        }
    }
    fun reviewOrder() {
        if (state.value.selected.isNotEmpty() && state.value.passengerSelection.isNotEmpty())
            mutable.update { it.copy(page = WaitlistPage.CONFIRMATION) }
    }
    val nativeOrdersAvailable get() = orders != null && coordinator != null
    fun currentRiskChallenge(): RailwayRiskChallenge? = riskChallenge

    private fun selectedBinding(): WaitlistBinding? {
        val s = state.value
        val account = s.account ?: return null
        val passengers = account.passengers.filter { it.reference in s.passengerSelection && it.selectable }
        if (passengers.isEmpty() || passengers.size != s.passengerSelection.size) return null
        return WaitlistBinding(account.reference, passengers.map { WaitlistPassenger(it.reference, it.ticketType) })
    }

    fun prepareOrder() {
        val service = orders ?: return
        if (orderJob?.isActive == true) return
        val binding = selectedBinding() ?: return
        val demands = state.value.selected.mapNotNull { it.demand }
        if (demands.isEmpty()) return
        val existing = state.value.operation
        if (existing != null && existing.phase !in setOf(WaitlistPhase.ORDER_CREATED, WaitlistPhase.EXHAUSTED)) {
            mutable.update { it.copy(page = WaitlistPage.PROGRESS, operationError = "请先核对已有候补任务，不能另起订单") }
            return
        }
        val revision = ++orderRevision
        riskProof = null; riskChallenge = null
        mutable.update { it.copy(orderBusy = true, orderPreview = null, riskVisible = false,
            riskVerified = false, deadlineMinutes = null, operationError = null) }
        orderJob = viewModelScope.launch {
            try {
                val preview = service.prepare(binding, demands)
                if (revision != orderRevision || selectedBinding()?.matches(binding) != true ||
                    state.value.selected.mapNotNull { it.demand } != demands) return@launch
                mutable.update { it.copy(orderPreview = preview, orderBusy = false) }
                diagnostic("ORDER_PREPARED", binding.passengers.size)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (revision == orderRevision) mutable.update { it.copy(orderBusy = false,
                    operationError = orderError(e)) }
                diagnostic("ORDER_PREPARE_FAILED")
            }
        }
    }

    fun selectDeadline(minutes: Int) {
        if (minutes !in state.value.orderPreview?.deadlineMinutes.orEmpty() || state.value.orderBusy) return
        riskProof = null; riskChallenge = null
        mutable.update { it.copy(deadlineMinutes = minutes, riskVisible = false, riskVerified = false) }
    }

    fun beginRiskVerification() {
        val service = orders ?: return
        val preview = state.value.orderPreview ?: return
        if (state.value.deadlineMinutes == null || state.value.orderBusy || orderJob?.isActive == true) return
        val revision = orderRevision
        orderJob = viewModelScope.launch {
            try {
                riskChallenge = service.riskChallenge(preview.id)
                if (revision != orderRevision) { riskChallenge = null; return@launch }
                riskProof = null
                mutable.update { it.copy(riskVisible = true, riskVerified = false, operationError = null) }
                diagnostic("ORDER_VERIFYING")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(operationError = orderError(e)) } }
        }
    }

    fun riskVerificationCompleted(proof: RailwayRiskProof) {
        if (!state.value.riskVisible || proof.previewId != state.value.orderPreview?.id) return
        riskProof = proof
        riskChallenge = null
        mutable.update { it.copy(riskVisible = false, riskVerified = true) }
        diagnostic("ORDER_VERIFIED")
    }

    fun riskVerificationFailed() {
        riskProof = null; riskChallenge = null
        mutable.update { it.copy(riskVisible = false, riskVerified = false,
            operationError = "官方安全验证未完成，请重新验证；没有发送订单") }
        diagnostic("ORDER_VERIFICATION_FAILED")
    }

    fun confirmOrder() {
        val service = orders ?: return
        val flow = coordinator ?: return
        val s = state.value
        val preview = s.orderPreview ?: return
        val minutes = s.deadlineMinutes ?: return
        val proof = riskProof ?: return
        if (s.page != WaitlistPage.CONFIRMATION || orderJob?.isActive == true || s.orderBusy ||
            selectedBinding()?.matches(preview.binding) != true ||
            s.selected.mapNotNull { it.demand } != preview.trains.map { it.demand }) return
        mutable.update { it.copy(orderBusy = true, operationError = null) }
        orderJob = viewModelScope.launch {
            try {
                val request = service.request(preview.id, minutes)
                service.authorize(preview.id, request, minutes, proof)
                if (state.value.page != WaitlistPage.CONFIRMATION || !state.value.orderBusy) return@launch
                riskProof = null
                mutable.update { it.copy(page = WaitlistPage.PROGRESS, riskVerified = false, riskVisible = false) }
                flow.start(request, preview.limits) // Durable attempt precedes any confirmHB call.
                diagnostic("ORDER_${flow.state.value?.phase?.name ?: "UNKNOWN"}")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(operationError = orderError(e)) } }
            finally { mutable.update { it.copy(orderBusy = false) } }
        }
    }

    fun checkPendingOrder() {
        val flow = coordinator ?: return
        val account = state.value.account
        val pending = state.value.operation?.pending ?: return
        if (orderJob?.isActive == true) return
        if (account?.reference != pending.binding.accountReference) {
            mutable.update { it.copy(operationError = "请使用原候补账号重新登录，再核对未决订单") }
            return
        }
        orderJob = viewModelScope.launch {
            mutable.update { it.copy(orderBusy = true, operationError = null) }
            try {
                flow.checkOrder(pending.binding)
                diagnostic("ORDER_${flow.state.value?.phase?.name ?: "UNKNOWN"}")
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(operationError = orderError(e)) } }
            finally { mutable.update { it.copy(orderBusy = false) } }
        }
    }

    private fun orderError(e: Exception) = when (e) {
        is RailwayOrderException, is RailwayException, is WaitlistPersistenceException ->
            e.message ?: "候补操作未完成，请核对订单后重试"
        else -> "候补操作未完成；已发请求可能仍在处理，请先核对订单，不要重复提交"
    }

    private fun clearOrderPreparation() {
        orderRevision++
        riskProof = null; riskChallenge = null
        // Do not cancel an in-flight submission; its persisted attempt must be reconciled.
        if (state.value.page != WaitlistPage.PROGRESS) orderJob?.cancel()
        mutable.update { it.copy(orderPreview = null, riskVisible = false, riskVerified = false,
            deadlineMinutes = null, orderBusy = if (it.page == WaitlistPage.PROGRESS) it.orderBusy else false) }
    }
    fun back() {
        if (state.value.page == WaitlistPage.CONFIRMATION) clearOrderPreparation()
        if (state.value.page == WaitlistPage.AUTHENTICATION) {
            cancelQrLogin()
            accountJob?.cancel()
        }
        mutable.update { it.copy(page = when (it.page) {
            WaitlistPage.AUTHENTICATION -> WaitlistPage.SELECTED
            WaitlistPage.CONFIRMATION -> WaitlistPage.AUTHENTICATION
            WaitlistPage.SELECTED -> WaitlistPage.RESULTS
            WaitlistPage.RESULTS -> WaitlistPage.FILTERS
            WaitlistPage.PROGRESS -> WaitlistPage.FILTERS
            WaitlistPage.FILTERS -> WaitlistPage.FILTERS
        }) }
    }

    fun toggle(choice: WaitlistChoice) {
        if (state.value.orderBusy) return
        clearOrderPreparation()
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
        clearOrderPreparation()
        if (nativePasswordAvailable && state.value.accountBusy) {
            authGeneration++
            accountJob?.cancel()
            mutable.update { it.copy(accountBusy = false, accountError = "已暂停登录操作，请返回后重新确认",
                passwordPhase = RailwayPasswordUiPhase.IDLE) }
        }
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

    override fun onCleared() {
        preview(null)
        super.onCleared()
    }
}
