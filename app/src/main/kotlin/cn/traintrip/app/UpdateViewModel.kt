package cn.traintrip.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.traintrip.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class UpdateState(val checking:Boolean=false,val release:AppRelease?=null,val checkedAt:Instant?=null,val error:String?=null,
    val completedAt:Instant?=null,val historical:Boolean=false,val historyError:String?=null)
class UpdateViewModel(private val source:UpdateSource=GitHubUpdateSource(),private val history:UpdateHistoryStore?=null,
    private val now:()->Instant=Instant::now):ViewModel() {
    private val mutable=MutableStateFlow(history?.read() ?: UpdateState())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    private var requestId=0L
    private val historyLock=Mutex()
    fun check() {
        if(mutable.value.checking)return
        mutable.update { it.copy(checking=true,historyError=null) }
        val id=++requestId
        job=viewModelScope.launch {
            try {
                val release=source.latest();ensureActive();if(id!=requestId)return@launch
                val time=now()
                complete(UpdateState(release=release,checkedAt=time,completedAt=time),id)
            } catch(e:CancellationException) {
                if(e is TimeoutCancellationException && id==requestId)complete(mutable.value.copy(checking=false,completedAt=now(),error="检查超时，请稍后重试",historical=false),id)
                else throw e
            } catch(_:Exception) {
                if(id==requestId)complete(mutable.value.copy(checking=false,completedAt=now(),error="网络或 GitHub 暂不可用，请稍后重试",historical=false),id)
            }
        }
    }
    private suspend fun complete(result:UpdateState,id:Long) {
        // Once a request completed, finish its tiny history write even if the page closes.
        mutable.value=result
        val saved=withContext(NonCancellable) { historyLock.withLock { withContext(Dispatchers.IO) { runCatching { history?.write(result) }.isSuccess } } }
        if(!saved && id==requestId)mutable.update { it.copy(historyError="检查结果已显示，但记录未能保存，请重试") }
    }
    fun leave() {
        requestId++;job?.cancel();job=null
        mutable.update { it.copy(checking=false,historical=it.completedAt!=null || it.checkedAt!=null) }
    }
    companion object {
        fun factory(context:Context)=viewModelFactory { initializer { UpdateViewModel(history=AndroidUpdateHistoryStore(context.applicationContext)) } }
    }
}
