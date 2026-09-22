package cn.traintrip.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DestinationState(
    val cityId: String? = null, val guide: DestinationGuide? = null, val loading: Boolean = false,
    val stage: String = "", val error: String? = null, val configured: Boolean = false,
    val settingsError: String? = null, val modelName: String = DeepSeekGuideGenerator.MODEL,
    val settingsBusy: Boolean = false, val settingsMessage: String? = null, val searchConfigured: Boolean = false,
    val offline:List<OfflineEntry> = emptyList(),val offlineLoading:Boolean=false,val offlineError:String?=null,
    val deleting:String?=null,val offlineMessage:String?=null,val contentMessage:String?=null, val guides: Map<String, DestinationGuide> = DestinationGuides.all.associateBy { it.cityId }
)

class DestinationViewModel @JvmOverloads constructor(app: Application,
    private val credentials: GuideCredentials = DeepSeekSettings(app),
    private val store: GuideStore = AndroidGuideStore(app),
    source: GuideMaterialSource? = null, generator: GuideGenerator? = null,
    private val searchCredentials: GuideCredentials = DoubaoSearchSettings(app),
    private val modelPreference: GuideModelPreference = DeepSeekModelSettings(app),
    private val photoSource: GuidePhotoSource = FallbackPhotoSource(DoubaoGuideSource { searchCredentials.read() }, CtripPhotoSource())
) : AndroidViewModel(app) {
    private val repository = GuideRepository(source ?: DoubaoGuideSource { searchCredentials.read() }, generator ?: DeepSeekGuideGenerator { modelPreference.read() }, store)
    private val mutable = MutableStateFlow(DestinationState())
    val state = mutable.asStateFlow()
    private var active: City? = null
    private var job: Job? = null
    private var requestId = 0L
    private val contentLock=Mutex()
    init { refreshOffline() }
    fun refreshOffline() {
        viewModelScope.launch {
            mutable.update { it.copy(offlineLoading=true,offlineError=null) }
            try { contentLock.withLock { withContext(Dispatchers.IO) { refreshConfiguration();refreshSnapshot() } } }
            catch(e:Exception) { if(e is CancellationException)throw e;mutable.update { it.copy(offlineLoading=false,offlineError="无法读取离线内容，请重试") } }
        }
    }
    private fun refreshSnapshot() {
        val android=store as? AndroidGuideStore
        val downloaded=android?.all().orEmpty()
        val entries=android?.entries().orEmpty()
        mutable.update { old ->
            val guides=if(android!=null)DestinationGuides.all.associateBy { it.cityId }+downloaded else old.guides
            old.copy(guides=guides,offline=entries,offlineLoading=false,
                guide=old.cityId?.let { if(android!=null)guides[it] else repository.cached(it) })
        }
    }
    fun deleteOffline(cityId:String) {
        if(mutable.value.deleting!=null)return
        val pending=job
        cancel()
        mutable.update { it.copy(deleting=cityId,offlineError=null,offlineMessage=null) }
        viewModelScope.launch {
            pending?.join()
            contentLock.withLock {
                try {
                    val complete=withContext(Dispatchers.IO) {
                        val disk=store as? AndroidGuideStore ?: throw java.io.IOException()
                        val success=disk.delete(cityId);refreshSnapshot();success
                    }
                    mutable.update { it.copy(deleting=null,offlineMessage=if(complete)"已删除离线内容，收藏仍保留" else null,
                        offlineError=if(complete)null else "部分文件未能清理，可重试。以实际占用为准。",contentMessage=null,error=null) }
                } catch(e:Exception) { if(e is CancellationException)throw e;mutable.update { it.copy(deleting=null,offlineError="删除未完成，请重试") } }
            }
        }
    }
    fun open(city: City) {
        if (active?.id == city.id && mutable.value.cityId == city.id) return
        cancel()
        active = city
        mutable.update { it.copy(cityId = city.id, guide = it.guides[city.id], error = null, contentMessage=null, stage = "", loading = true) }
        val id = ++requestId
        job = viewModelScope.launch {
            try {
                val cached = contentLock.withLock { withContext(Dispatchers.IO) { repository.cached(city.id) } }
                if (requestId != id) return@launch
                mutable.update { it.copy(guide = cached, loading = false) }
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                if(requestId==id)mutable.update { it.copy(loading=false,error="无法读取本地介绍，请重试") }
            }

        }
    }
    fun retry() { active?.let(::generate) }
    private fun generate(city: City) {
        if (mutable.value.loading || mutable.value.deleting!=null || active?.id != city.id) return
        val id = ++requestId
        mutable.update { it.copy(loading = true, error = null, contentMessage=null, stage = "准备获取资料…") }
        job = viewModelScope.launch {
            var committed=false
            try {
                val (key, searchKey) = withContext(Dispatchers.IO) { credentials.read() to searchCredentials.read() }
                if (requestId != id) return@launch
                mutable.update { it.copy(configured = key != null, searchConfigured = searchKey != null) }
                if (key == null || searchKey == null) { mutable.update { it.copy(loading = false, stage = "") }; return@launch }
                val guide = contentLock.withLock { withContext(Dispatchers.IO) {
                    try { repository.generate(city, key, { stage -> if (requestId == id) mutable.update { it.copy(stage = stage) } }) { draft ->
                        val disk=store as? AndroidGuideStore
                        if(disk==null)draft else {
                            val text=disk.saveTextKeepingPhotos(draft,repository.cached(city.id)) { committed=true;refreshSnapshot() }
                            if(GuidePhotoPolicy.missing(text).isEmpty())text else updatePhotos(city,text,id,draft.gallery).guide
                        }
                    } } finally { (store as? AndroidGuideStore)?.let { runCatching { it.cleanupUnreferencedImages(city.id) } } }
                } }
                contentLock.withLock { withContext(Dispatchers.IO) { refreshSnapshot() } }
                if (requestId == id) mutable.update { it.copy(loading = false, guide = guide, error = null, stage = "", guides = it.guides + (city.id to guide)) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { contentLock.withLock { runCatching { refreshSnapshot() } } }
                    if(committed && requestId==id+1 && active?.id==city.id)mutable.update { it.copy(error=null,contentMessage="介绍已保存，已完成的图片已保留") }
                }
                throw e
            } catch (e: Exception) {
                contentLock.withLock { withContext(Dispatchers.IO) { runCatching { refreshSnapshot() } } }
                if (requestId == id) mutable.update { it.copy(loading = false, stage = "",contentMessage=if(committed)"介绍已保存，已完成的图片已保留" else null,
                    error=if(committed)null else "整理未完成，请检查配置后重试") }
            }
        }
    }
    private suspend fun updatePhotos(city:City,guide:DestinationGuide,id:Long,initial:List<DestinationPhoto> = emptyList()):PhotoSaveResult {
        val disk=store as? AndroidGuideStore ?: return PhotoSaveResult(guide,0,0)
        if(requestId==id)mutable.update { it.copy(stage="正在补充目的地图片…") }
        val found=optionalPhotos(city,guide) { stage -> if(requestId==id)mutable.update { it.copy(stage=stage) } }
        currentCoroutineContext().ensureActive()
        val candidates=GuidePhotoPolicy.candidates(guide, found.photos + initial)
        if(requestId==id)mutable.update { it.copy(stage="正在保存图片…") }
        var result=disk.refreshPhotos(guide,candidates) { refreshSnapshot() }
        var sourceFailed=found.failed
        if(GuidePhotoPolicy.missing(result.guide).isNotEmpty() && found.fallbackAvailable) {
            val fallback=try { photoSource.fetchFallback(city,result.guide) { stage ->
                if(requestId==id)mutable.update { it.copy(stage=stage) }
            } } catch(e:CancellationException) { throw e } catch(_:Exception) { PhotoCandidates(emptyList(),true) }
            sourceFailed=sourceFailed || fallback.failed
            currentCoroutineContext().ensureActive()
            val attempted=candidates.mapNotNull { it.remoteUrl }.toSet()
            if(requestId==id)mutable.update { it.copy(stage="正在保存备用来源图片…") }
            val recovered=disk.refreshPhotos(result.guide,fallback.photos.filter { it.remoteUrl !in attempted }) { refreshSnapshot() }
            result=recovered.copy(saved=result.saved+recovered.saved,failed=result.failed+recovered.failed)
        }
        val message=when {
            result.saved>0 -> "介绍已更新，已保存 ${result.guide.gallery.size} 张图片" + if(GuidePhotoPolicy.missing(result.guide).isNotEmpty())"；部分条目暂未取得图片。" else "。"
            result.failed>0 -> "介绍已更新，图片暂未取得，可稍后更新重试。"
            candidates.isNotEmpty() -> "介绍已更新。"
            sourceFailed -> "介绍已更新，图片来源暂时无法读取。"
            else -> "介绍已更新，暂未找到合适图片。"
        }
        if(requestId==id)mutable.update { it.copy(contentMessage=message) }
        return result
    }

    private suspend fun optionalPhotos(city:City,guide:DestinationGuide,stage:(String)->Unit):PhotoCandidates =
        try { photoSource.fetch(city,guide,stage) }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { PhotoCandidates(emptyList(),true) }

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
        val doubao = runCatching { searchCredentials.read() != null }
        mutable.update { it.copy(configured = deepSeek.getOrDefault(false), searchConfigured = doubao.getOrDefault(false), modelName = modelPreference.read(),
            settingsError = if (deepSeek.isFailure || doubao.isFailure) "无法读取已保存的密钥，请重新配置" else it.settingsError) }
    }
    fun saveKey(value: String, complete: () -> Unit) = saveKeys(value, "", complete)
    fun saveModelSettings(model: String, key: String, complete: () -> Unit) = saveConfiguration(key, "", model, complete)
    fun saveSearchSettings(key: String, complete: () -> Unit) = saveConfiguration("", key, null, complete)
    fun clearSettingsFeedback() { mutable.update { it.copy(settingsError = null, settingsMessage = null) } }
    fun saveKeys(deepSeek: String, doubao: String, complete: () -> Unit) = saveConfiguration(deepSeek, doubao, null, complete)
    private fun saveConfiguration(deepSeek: String, doubao: String, model: String?, complete: () -> Unit) {
        if (mutable.value.settingsBusy) return
        cancel()
        mutable.update { it.copy(settingsBusy = true, settingsError = null, settingsMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (model != null) validateGuideModel(model.trim())
                    if (deepSeek.isNotBlank()) validateGuideKey(deepSeek.trim(), "sk-", "DeepSeek")
                    if (doubao.isNotBlank()) validateDoubaoSearchKey(doubao.trim())
                    if (deepSeek.isNotBlank()) credentials.save(deepSeek.trim())
                    if (doubao.isNotBlank()) searchCredentials.save(doubao.trim())
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
    fun removeSearchKey(complete: () -> Unit) = remove(searchCredentials, complete)
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
