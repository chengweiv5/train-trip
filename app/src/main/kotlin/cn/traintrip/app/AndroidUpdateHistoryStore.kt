package cn.traintrip.app

import android.content.Context
import android.util.AtomicFile
import cn.traintrip.core.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.time.Instant

interface UpdateHistoryStore {
    fun read():UpdateState
    fun write(state:UpdateState)
}

/** Contains only public release metadata; never service configuration or credentials. */
class AndroidUpdateHistoryStore(context:Context):UpdateHistoryStore {
    private val file=AtomicFile(File(context.filesDir,"update-history.json"))
    override fun read():UpdateState=runCatching {
        val bytes=file.readFully();require(bytes.size<=16*1024)
        val j=JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(j["schemaVersion"].asInt==1)
        val completed=Instant.parse(j["completedAt"].asString)
        val checked=j["checkedAt"]?.asString?.let(Instant::parse)
        require(checked==null || !checked.isAfter(completed))
        val release=j.getAsJsonObject("release")?.let {
            val version=requireNotNull(ReleaseVersion.parse(it["version"].asString))
            val url=it["url"].asString;require(url==releaseUrl(version.toString()))
            AppRelease(version,url,it["summary"].asString.take(400),it["downloadable"].asBoolean)
        }
        require((release==null)==(checked==null))
        UpdateState(release=release,checkedAt=checked,completedAt=completed,
            error=if(j["failed"].asBoolean)"上次检查未完成，请重新检查" else null,historical=true)
    }.getOrDefault(UpdateState())
    override fun write(state:UpdateState) {
        val j=JsonObject().apply {
            addProperty("schemaVersion",1);addProperty("completedAt",requireNotNull(state.completedAt).toString())
            state.checkedAt?.let { addProperty("checkedAt",it.toString()) }
            addProperty("failed",state.error!=null)
            state.release?.let { r -> add("release",JsonObject().apply {
                addProperty("version",r.version.toString());addProperty("url",r.url)
                addProperty("summary",r.summary.take(400));addProperty("downloadable",r.downloadable)
            }) }
        }
        val out=file.startWrite()
        try { out.write(j.toString().toByteArray());file.finishWrite(out) }
        catch(e:Exception) { file.failWrite(out);throw IOException("检查记录未保存",e) }
    }
}
