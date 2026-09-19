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

class AndroidGuideStore(context: Context, private val loadPhoto: suspend (String) -> ByteArray = GuideNetwork()::photo) : GuideStore {
    private val directory = File(context.filesDir, "destination-guides").apply { mkdirs() }
    private fun file(cityId: String, suffix: String): File {
        require(cityId.matches(Regex("[0-9]{6}")))
        return File(directory, "$cityId.$suffix")
    }
    private fun storedGuide(cityId: String): DestinationGuide? = runCatching {
        val raw = AtomicFile(file(cityId, "json")).readFully()
        require(raw.size <= 512 * 1024)
        val root = JsonParser.parseString(String(raw, Charsets.UTF_8)).asJsonObject
        require(root["schemaVersion"].asInt == 1)
        Gson().fromJson(root["guide"], DestinationGuide::class.java)
    }.getOrNull()
    override fun read(cityId: String): DestinationGuide? = runCatching {
        storedGuide(cityId)?.let { DestinationGuides.validateGenerated(it, cityId) }
    }.getOrNull()
    fun all(): Map<String, DestinationGuide> = directory.listFiles().orEmpty().filter { it.name.matches(Regex("[0-9]{6}\\.json")) }
        .mapNotNull { read(it.nameWithoutExtension) }.associateBy { it.cityId }
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
        DestinationGuides.validateGenerated(guide, guide.cityId)
        atomic(file(guide.cityId, "json"), Gson().toJson(mapOf("schemaVersion" to 1, "guide" to guide)).toByteArray(Charsets.UTF_8))
    }
    suspend fun preparePhoto(guide: DestinationGuide): DestinationGuide {
        val photo = guide.photo ?: return guide
        val url = photo.remoteUrl ?: return guide
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
            guide
        } catch (e: CancellationException) { throw e } catch (_: Exception) { guide.copy(photo = null) }
    }
    private fun atomic(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw IOException("离线内容保存失败") }
    }
}
