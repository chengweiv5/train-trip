package cn.traintrip.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import cn.traintrip.core.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class PhotoSaveResult(val guide:DestinationGuide,val saved:Int,val failed:Int)

data class OfflineEntry(val cityId:String,val guide:DestinationGuide?,val bytes:Long,val hasPhoto:Boolean)

class AndroidGuideStore(context: Context, private val removeFile:(File)->Boolean = { it.delete() }, private val loadPhoto: suspend (String) -> ByteArray = GuideNetwork()::photo) : GuideStore {
    private companion object { const val MAX_CACHE_BYTES = 16 * 1024 * 1024 }
    private val directory = File(context.filesDir, "destination-guides").apply { mkdirs() }
    private fun file(cityId: String, suffix: String): File {
        require(cityId.matches(Regex("[0-9]{6}")))
        return File(directory, "$cityId.$suffix")
    }
    private fun storedGuide(cityId: String): DestinationGuide? = runCatching {
        val raw = AtomicFile(file(cityId, "json")).readFully()
        require(raw.size <= MAX_CACHE_BYTES)
        val root = JsonParser.parseString(String(raw, Charsets.UTF_8)).asJsonObject
        require(root["schemaVersion"].asInt == 1)
        Gson().fromJson(root["guide"], DestinationGuide::class.java)
    }.getOrNull()
    override fun read(cityId: String): DestinationGuide? = runCatching {
        storedGuide(cityId)?.let { legacy ->
            val counts = legacy.gallery.mapNotNull { GuidePhotoPolicy.subject(legacy, it) }.groupingBy { it }.eachCount()
            val readable = if (legacy.gallery.all { it.subject == null } && counts.values.any { it > GuidePhotoPolicy.PER_ITEM })
                legacy.withPhotos(GuidePhotoPolicy.select(legacy)) else legacy
            // Read old unlimited caches through the new bound without destroying their original bytes.
            // The next explicit refresh persists the bounded guide through the normal atomic save.
            DestinationGuides.validateCached(GuideItemPolicy.limit(readable), cityId)
        }
    }.getOrNull()
    fun all(): Map<String, DestinationGuide> = directory.listFiles().orEmpty()
        .mapNotNull { Regex("^([0-9]{6})\\.json(?:\\.bak)?$").matchEntire(it.name)?.groupValues?.get(1) }
        .distinct().mapNotNull(::read).associateBy { it.cityId }
    override fun attempted(cityId: String): Boolean {
        if (file(cityId, "simplified-attempt").exists()) return true
        // Old rejected resources may be regenerated once. Keep files for rollback,
        // and record the new attempt before I/O so failures never loop on restart.
        val traditional = runCatching { storedGuide(cityId)?.let { !SimplifiedGuidePolicy.guideAllowed(it) } == true }.getOrDefault(false)
        return !traditional && file(cityId, "attempt").exists()
    }
    override fun markAttempted(cityId: String) {
        atomic(file(cityId, "simplified-attempt"), byteArrayOf(1))
        atomic(file(cityId, "attempt"), byteArrayOf(1))
    }
    override fun save(guide: DestinationGuide) {
        DestinationGuides.validateCached(guide, guide.cityId)
        val bytes = Gson().toJson(mapOf("schemaVersion" to 1, "guide" to guide)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_CACHE_BYTES) { "离线介绍超出可保存大小，原缓存已保留" }
        atomic(file(guide.cityId, "json"), bytes)
    }
    private fun ownedImages(cityId:String):Set<String> {
        val saved=runCatching { AtomicFile(file(cityId,"images")).readFully().toString(Charsets.UTF_8).lines() }.getOrDefault(emptyList())
        return (saved+storedGuide(cityId)?.gallery.orEmpty().map { it.assetName })
            .filter { it.matches(Regex("[a-z0-9_]+\\.jpg")) }.toSet()
    }
    private fun sharedImage(cityId:String,name:String):Boolean = directory.listFiles().orEmpty()
        .mapNotNull { Regex("^([0-9]{6})\\.json(?:\\.bak)?$").matchEntire(it.name)?.groupValues?.get(1) }
        .distinct().filter { it!=cityId }.any { storedGuide(it)?.gallery.orEmpty().any { image -> image.assetName==name } }
    // Keep legacy image ownership after replacing or deleting the JSON, including failed deletions.
    private fun rememberImages(cityId:String) {
        val images=ownedImages(cityId).filter { !sharedImage(cityId,it) }
        if(images.isNotEmpty())atomic(file(cityId,"images"),images.joinToString("\n").toByteArray())
    }
    private fun cityFiles(cityId:String):List<File> {
        file(cityId,"json")
        val images=ownedImages(cityId).filterNot { sharedImage(cityId,it) }.toSet()
        return directory.listFiles().orEmpty().filter { f ->
            val base=f.name.removeSuffix(".bak").removeSuffix(".new")
            val owned=f.name.startsWith("$cityId.") || f.name.startsWith("${cityId}_") || base in images
            owned && !(base.endsWith(".jpg") && sharedImage(cityId,base))
        }
    }
    fun cleanupUnreferencedImages(cityId:String) {
        val current=storedGuide(cityId)?.gallery.orEmpty().map { it.assetName }.toSet()
        cityFiles(cityId).filter { it.name.matches(Regex(".*\\.jpg(?:\\.bak|\\.new)?$")) && it.name !in current }
            .forEach { removeFile(it) }
        val remaining=ownedImages(cityId).filter { name -> name !in current &&
            listOf(name,"$name.bak","$name.new").any { File(directory,it).exists() } }
        if(remaining.isEmpty()) {
            listOf("images","images.bak","images.new").forEach { removeFile(file(cityId,it)) }
        } else atomic(file(cityId,"images"),remaining.joinToString("\n").toByteArray())
    }
    fun entries():List<OfflineEntry> {
        val files=directory.listFiles() ?: if(directory.exists())throw IOException("无法读取离线目录") else emptyArray()
        val ids=files.mapNotNull { Regex("^([0-9]{6})[._]").find(it.name)?.groupValues?.get(1) }.distinct()
        return ids.mapNotNull { id ->
            val content=cityFiles(id).filterNot { it.name.endsWith(".attempt") || it.name.endsWith(".simplified-attempt") }
            if(content.isEmpty())null else {
                val guide=read(id)
                OfflineEntry(id,guide,content.sumOf { it.length() },guide?.gallery.orEmpty().any { File(directory,it.assetName).isFile })
            }
        }.sortedByDescending { it.guide?.generatedAt.orEmpty() }
    }
    fun delete(cityId:String):Boolean {
        rememberImages(cityId)
        val manifest=setOf("$cityId.images","$cityId.images.bak","$cityId.images.new")
        cityFiles(cityId).filterNot { it.name in manifest }.forEach { if(it.exists())removeFile(it) }
        if(cityFiles(cityId).none { it.name !in manifest }) {
            manifest.forEach { val target=File(directory,it);if(target.exists())removeFile(target) }
        }
        return cityFiles(cityId).isEmpty()
    }
    /** Every image reference is committed before starting the next download. */
    suspend fun prepareUpdate(guide:DestinationGuide,onCommitted:()->Unit={}):DestinationGuide {
        coroutineContext.ensureActive()
        rememberImages(guide.cityId)
        var saved=guide.withPhotos(emptyList())
        save(saved)
        onCommitted()
        cleanupUnreferencedImages(guide.cityId)
        saved = refreshPhotos(saved, guide.gallery, onCommitted).guide
        return saved
    }
    /** Full refresh keeps usable saved photos; missing files do not count as an album. */
    suspend fun saveTextKeepingPhotos(draft:DestinationGuide,previous:DestinationGuide?,onCommitted:()->Unit={}):DestinationGuide {
        coroutineContext.ensureActive()
        require(previous==null || previous.cityId==draft.cityId)
        val photos = if (previous == null) emptyList() else GuidePhotoPolicy.select(draft, usablePhotos(previous))
        val text=draft.withPhotos(photos)
        rememberImages(draft.cityId)
        save(text)
        onCommitted()
        cleanupUnreferencedImages(draft.cityId)
        return text
    }

    fun usablePhotos(guide: DestinationGuide): List<DestinationPhoto> = GuidePhotoPolicy.select(guide, guide.gallery.filter { photo ->
        if (photo.remoteUrl == null) true else runCatching {
            val file = File(directory, photo.assetName)
            if (!file.isFile || file.length() == 0L) false else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth !in 1..20000 || bounds.outHeight !in 1..20000) false else {
                    val options = BitmapFactory.Options().apply { inSampleSize = 16 }
                    BitmapFactory.decodeFile(file.path, options)?.let { it.recycle(); true } ?: false
                }
            }
        }.getOrDefault(false)
    })

    /** A batch only fills subjects that were empty when it started. Existing albums stay untouched. */
    suspend fun refreshPhotos(guide:DestinationGuide,candidates:List<DestinationPhoto>,onCommitted:()->Unit={}):PhotoSaveResult {
        DestinationGuides.validateCached(guide,guide.cityId)
        var saved = guide.withPhotos(usablePhotos(guide))
        val missing = GuidePhotoPolicy.missing(saved).toSet()
        var downloaded = 0
        var failed = 0
        for (candidate in GuidePhotoPolicy.candidates(guide, candidates)) {
            coroutineContext.ensureActive()
            if (saved.gallery.size >= GuidePhotoPolicy.PER_CITY) break
            val subject = candidate.subject ?: continue
            if (subject !in missing || saved.gallery.count { it.subject == subject } >= GuidePhotoPolicy.PER_ITEM) continue
            if (candidate.remoteUrl == null || saved.gallery.any { it.remoteUrl == candidate.remoteUrl || it.assetName == candidate.assetName }) continue
            val photo=candidate.copy(assetName="${guide.cityId}_${java.util.UUID.randomUUID().toString().replace("-", "")}.jpg")
            if(!downloadPhoto(photo)) { failed++;continue }
            coroutineContext.ensureActive()
            val next = saved.withPhotos(saved.gallery + photo)
            rememberImages(guide.cityId)
            save(next)
            downloaded++
            saved=next
            onCommitted()
            cleanupUnreferencedImages(guide.cityId)
        }
        return PhotoSaveResult(saved,downloaded,failed)
    }

    private suspend fun downloadPhoto(photo:DestinationPhoto):Boolean {
        val url=photo.remoteUrl ?: return false
        return try {
            val bytes = loadPhoto(url)
            coroutineContext.ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..20000 || bounds.outHeight !in 1..20000) throw IOException("图片尺寸无效")
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
            }
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("图片无法解码")
            try {
                val ratio = minOf(1.0, 1200.0 / maxOf(original.width, original.height))
                val scaled = if (ratio < 1) Bitmap.createScaledBitmap(original, (original.width * ratio).toInt().coerceAtLeast(1), (original.height * ratio).toInt().coerceAtLeast(1), true) else original
                try {
                    val output = java.io.ByteArrayOutputStream()
                    if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw IOException("图片保存失败")
                    coroutineContext.ensureActive()
                    atomic(File(directory, photo.assetName), output.toByteArray())
                } finally { if (scaled !== original) scaled.recycle() }
            } finally { original.recycle() }
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }
    private fun atomic(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw IOException("离线内容保存失败") }
    }
}
