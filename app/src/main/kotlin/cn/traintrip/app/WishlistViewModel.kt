package cn.traintrip.app

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.traintrip.core.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

class AndroidWishlistStore(context:Context):WishlistStore {
    private val file=AtomicFile(File(context.filesDir,"wishlist.json"))
    private val fileLock=locks.getOrPut(file.baseFile.canonicalPath) { Any() }
    override fun read():List<WishCity> = synchronized(fileLock) {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return@synchronized emptyList()
        val bytes=file.readFully();require(bytes.size<=1024*1024)
        val root=JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(root["schemaVersion"].asInt==1)
        return@synchronized root["cities"].asJsonArray.map { e -> e.asJsonObject.let { j ->
            WishCity(j["cityId"].asString,j["name"].asString,j["province"].asString,j["addedAt"].asLong).also {
                require(it.cityId.matches(Regex("[0-9]{6}")) && it.name.isNotBlank() && it.province.isNotBlank())
            }
        } }
    }
    override fun write(items:List<WishCity>):Unit = synchronized(fileLock) {
        val stream=file.startWrite()
        try { stream.write(Gson().toJson(mapOf("schemaVersion" to 1,"cities" to items)).toByteArray());file.finishWrite(stream) }
        catch(e:Exception) { file.failWrite(stream);throw IOException("想去清单未能保存",e) }
    }
    override fun update(change:(List<WishCity>)->List<WishCity>):List<WishCity> = synchronized(fileLock) {
        super<WishlistStore>.update(change)
    }
    companion object {
        private val locks=java.util.concurrent.ConcurrentHashMap<String,Any>()
    }
}
data class WishlistState(val items:List<WishCity> = emptyList(),val loading:Boolean=true,val busy:Boolean=false,
    val error:String?=null,val notice:String?=null,val undo:WishCity?=null,val event:Long=0)
class WishlistViewModel @JvmOverloads constructor(app:Application,store:WishlistStore=AndroidWishlistStore(app)):AndroidViewModel(app) {
    private val repository=WishlistRepository(store)
    private val lock=Mutex()
    private val mutable=MutableStateFlow(WishlistState())
    val state=mutable.asStateFlow()
    init { reload() }
    fun reload() { viewModelScope.launch { lock.withLock {
        mutable.update { it.copy(loading=true,error=null) }
        try { val items=withContext(Dispatchers.IO){repository.load()};mutable.update { it.copy(items=items,loading=false) } }
        catch(e:Exception) { if(e is CancellationException)throw e;mutable.update { it.copy(loading=false,error="想去清单暂时无法读取，请重试") } }
    } } }
    private fun mutate(action:()->Pair<String,WishCity?>,done:()->Unit={},keepUndoOnFailure:Boolean=false) { viewModelScope.launch { lock.withLock {
        if(mutable.value.loading || mutable.value.error!=null)return@withLock
        mutable.update { it.copy(busy=true) }
        try { val result=withContext(Dispatchers.IO){action()};mutable.update { it.copy(items=repository.items,busy=false,notice=result.first,undo=result.second,event=it.event+1) };done() }
        catch(e:Exception) { if(e is CancellationException)throw e;mutable.update { it.copy(busy=false,notice="保存失败，请重试",undo=if(keepUndoOnFailure)it.undo else null,event=it.event+1) } }
    } } }
    fun add(cities:List<City>,done:()->Unit={})=mutate({repository.add(cities,System.currentTimeMillis());"已添加想去城市" to null},done)
    fun toggle(city:City)=mutate({
        val removed=repository.toggle(city,System.currentTimeMillis())
        if(removed!=null) "已取消想去「${city.name}」" to removed else "已加入想去「${city.name}」" to null
    })
    fun undo(record:WishCity)=mutate({repository.restore(record);"已恢复想去「${record.name}」" to null},keepUndoOnFailure=true)
    fun dismiss(event:Long) { mutable.update { if(it.event==event)it.copy(notice=null,undo=null) else it } }
}
