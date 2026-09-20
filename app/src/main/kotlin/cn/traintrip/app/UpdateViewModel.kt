package cn.traintrip.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

data class UpdateState(val checking:Boolean=false,val release:AppRelease?=null,val checkedAt:Instant?=null,val error:String?=null)
class UpdateViewModel(private val source:UpdateSource=GitHubUpdateSource()):ViewModel() {
    private val mutable=MutableStateFlow(UpdateState())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    private var requestId=0L
    fun check() {
        if(mutable.value.checking)return
        mutable.update { it.copy(checking=true,error=null,release=null) }
        val id=++requestId
        job=viewModelScope.launch {
            try { val release=source.latest();ensureActive();if(id!=requestId)return@launch;mutable.value=UpdateState(release=release,checkedAt=Instant.now()) }
            catch(e:CancellationException) { if(e is TimeoutCancellationException && id==requestId)mutable.update { it.copy(checking=false,error="检查超时，请稍后重试") } else throw e }
            catch(_:Exception) { if(id==requestId)mutable.update { it.copy(checking=false,release=null,error="网络或 GitHub 暂不可用，请稍后重试") } }
        }
    }
    fun leave() { requestId++;job?.cancel();job=null;mutable.value=UpdateState() }
}
