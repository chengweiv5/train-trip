package cn.traintrip.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DestinationState(
    val cityId: String? = null, val guide: DestinationGuide? = null, val loading: Boolean = false,
    val stage: String = "", val error: String? = null, val configured: Boolean = false,
    val settingsError: String? = null, val modelName: String = DeepSeekGuideGenerator.MODEL,
    val settingsBusy: Boolean = false, val settingsMessage: String? = null, val tavilyConfigured: Boolean = false, val guides: Map<String, DestinationGuide> = DestinationGuides.all.associateBy { it.cityId }
)

class DestinationViewModel @JvmOverloads constructor(app: Application,
    private val credentials: GuideCredentials = DeepSeekSettings(app),
    private val store: GuideStore = AndroidGuideStore(app),
    source: GuideMaterialSource? = null, generator: GuideGenerator? = null,
    private val searchCredentials: GuideCredentials = TavilySettings(app),
    private val modelPreference: GuideModelPreference = DeepSeekModelSettings(app)
) : AndroidViewModel(app) {
    private val repository = GuideRepository(source ?: TavilyGuideSource { searchCredentials.read() }, generator ?: DeepSeekGuideGenerator { modelPreference.read() }, store)
    private val mutable = MutableStateFlow(DestinationState())
    val state = mutable.asStateFlow()
    private var active: City? = null
    private var job: Job? = null
    private var requestId = 0L
    init {
        viewModelScope.launch(Dispatchers.IO) {
            refreshConfiguration()
            val cached = (store as? AndroidGuideStore)?.all().orEmpty()
            mutable.update { it.copy(guides = it.guides + cached) }
        }
    }
    fun open(city: City) {
        if (active?.id == city.id && mutable.value.cityId == city.id) return
        cancel()
        active = city
        mutable.update { it.copy(cityId = city.id, guide = it.guides[city.id], error = null, stage = "", loading = true) }
        val id = ++requestId
        job = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { repository.cached(city.id) }
            if (requestId != id) return@launch
            mutable.update { it.copy(guide = cached, loading = false) }
            if (cached == null) {
                val attempted = withContext(Dispatchers.IO) { repository.attempted(city.id) }
                if (requestId != id) return@launch
                if (attempted) mutable.update { it.copy(error = "上次整理未完成，可点重试。") }
                else generate(city)
            }
        }
    }
    fun retry() { active?.let(::generate) }
    private fun generate(city: City) {
        if (mutable.value.loading || active?.id != city.id) return
        val id = ++requestId
        mutable.update { it.copy(loading = true, error = null, stage = "准备获取资料…") }
        job = viewModelScope.launch {
            try {
                val (key, searchKey) = withContext(Dispatchers.IO) { credentials.read() to searchCredentials.read() }
                if (requestId != id) return@launch
                mutable.update { it.copy(configured = key != null, tavilyConfigured = searchKey != null) }
                if (key == null || searchKey == null) { mutable.update { it.copy(loading = false, stage = "") }; return@launch }
                val guide = withContext(Dispatchers.IO) {
                    repository.generate(city, key, { stage -> if (requestId == id) mutable.update { it.copy(stage = stage) } }) { guide ->
                        (store as? AndroidGuideStore)?.preparePhoto(guide) ?: guide
                    }
                }
                if (requestId == id) mutable.update { it.copy(loading = false, guide = guide, error = null, stage = "", guides = it.guides + (city.id to guide)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (requestId == id) mutable.update { it.copy(loading = false, stage = "", error = if (e is java.io.IOException) e.message else "整理失败，请检查配置后重试") }
            }
        }
    }
    fun cancel() {
        requestId++
        job?.cancel()
        job = null
        mutable.update { it.copy(loading = false, stage = "", error = if (it.loading) "整理已暂停，可点重试。" else it.error) }
    }
    fun leave() {
        cancel()
        active = null
    }
    private fun refreshConfiguration() {
        val deepSeek = runCatching { credentials.read() != null }
        val tavily = runCatching { searchCredentials.read() != null }
        mutable.update { it.copy(configured = deepSeek.getOrDefault(false), tavilyConfigured = tavily.getOrDefault(false), modelName = modelPreference.read(),
            settingsError = if (deepSeek.isFailure || tavily.isFailure) "无法读取已保存的密钥，请重新配置" else it.settingsError) }
    }
    fun saveKey(value: String, complete: () -> Unit) = saveKeys(value, "", complete)
    fun saveModelSettings(model: String, key: String, complete: () -> Unit) = saveConfiguration(key, "", model, complete)
    fun saveSearchSettings(key: String, complete: () -> Unit) = saveConfiguration("", key, null, complete)
    fun clearSettingsFeedback() { mutable.update { it.copy(settingsError = null, settingsMessage = null) } }
    fun saveKeys(deepSeek: String, tavily: String, complete: () -> Unit) = saveConfiguration(deepSeek, tavily, null, complete)
    private fun saveConfiguration(deepSeek: String, tavily: String, model: String?, complete: () -> Unit) {
        if (mutable.value.settingsBusy) return
        cancel()
        mutable.update { it.copy(settingsBusy = true, settingsError = null, settingsMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (model != null) validateGuideModel(model.trim())
                    if (deepSeek.isNotBlank()) validateGuideKey(deepSeek.trim(), "sk-", "DeepSeek")
                    if (tavily.isNotBlank()) validateGuideKey(tavily.trim(), "tvly-", "Tavily")
                    if (deepSeek.isNotBlank()) credentials.save(deepSeek.trim())
                    if (tavily.isNotBlank()) searchCredentials.save(tavily.trim())
                    if (model != null) modelPreference.save(model.trim())
                }
                mutable.update { it.copy(settingsError = null) }
                withContext(Dispatchers.IO) { refreshConfiguration() }
                mutable.update { it.copy(settingsBusy = false, settingsMessage = "设置已保存") }
                complete()
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { refreshConfiguration() }
                mutable.update { it.copy(settingsBusy = false, settingsError = "配置未全部保存，请检查输入和各项状态后重试") }
            }
        }
    }
    fun removeKey(complete: () -> Unit) = remove(credentials, complete)
    fun removeTavilyKey(complete: () -> Unit) = remove(searchCredentials, complete)
    private fun remove(target: GuideCredentials, complete: () -> Unit) {
        if (mutable.value.settingsBusy) return
        cancel()
        mutable.update { it.copy(settingsBusy = true, settingsError = null, settingsMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { target.remove(); refreshConfiguration() }
                mutable.update { it.copy(settingsBusy = false, settingsError = null, settingsMessage = "密钥已移除") }; complete()
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { refreshConfiguration() }
                mutable.update { it.copy(settingsBusy = false, settingsError = "移除配置失败，请重试") }
            }
        }
    }
}
