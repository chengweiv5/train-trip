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
    val settingsError: String? = null, val guides: Map<String, DestinationGuide> = DestinationGuides.all.associateBy { it.cityId }
)

class DestinationViewModel @JvmOverloads constructor(app: Application,
    private val credentials: GuideCredentials = DeepSeekSettings(app),
    private val store: GuideStore = AndroidGuideStore(app),
    source: GuideMaterialSource = CtripGuideSource(), generator: GuideGenerator = DeepSeekGuideGenerator()
) : AndroidViewModel(app) {
    private val repository = GuideRepository(source, generator, store)
    private val mutable = MutableStateFlow(DestinationState())
    val state = mutable.asStateFlow()
    private var active: City? = null
    private var job: Job? = null
    private var requestId = 0L
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try { val configured = credentials.read() != null; mutable.update { it.copy(configured = configured) } }
            catch (_: Exception) { mutable.update { it.copy(settingsError = "无法读取已保存的密钥，请重新配置") } }
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
                val key = withContext(Dispatchers.IO) { credentials.read() }
                if (requestId != id) return@launch
                if (key == null) { mutable.update { it.copy(loading = false, configured = false, stage = "") }; return@launch }
                mutable.update { it.copy(configured = true) }
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
    fun saveKey(value: String, complete: () -> Unit) {
        viewModelScope.launch {
            try {
                val configured = withContext(Dispatchers.IO) {
                    if (value.isNotBlank()) credentials.save(value.trim())
                    credentials.read() != null
                }
                mutable.update { it.copy(configured = configured, settingsError = null) }
                complete()
                if (configured && mutable.value.guide == null && active != null) retry()
            } catch (_: Exception) { mutable.update { it.copy(settingsError = "密钥保存失败，请检查输入后重试") } }
        }
    }
    fun removeKey(complete: () -> Unit) {
        cancel()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { credentials.remove() }
                mutable.update { it.copy(configured = false, settingsError = null) }; complete()
            } catch (_: Exception) { mutable.update { it.copy(settingsError = "移除配置失败，请重试") } }
        }
    }
}
