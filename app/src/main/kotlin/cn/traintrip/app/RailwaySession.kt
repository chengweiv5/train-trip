package cn.traintrip.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cn.traintrip.core.waitlist.*
import java.net.HttpURLConnection
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dedicated, process-memory-only session. No WebView, shared global jar or other app's data. */
class RailwaySession : RailwayTransport {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val mutex = Mutex()

    override suspend fun post(path: String, fields: Map<String, String>): String = mutex.withLock {
      withContext(Dispatchers.IO) {
        require(path in allowedPaths) { "Unsupported railway operation" }
        val url = "$ORIGIN$path"
        val uri = URI(url)
        currentCoroutineContext().ensureActive()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("Referer", "$ORIGIN/otn/resources/login.html")
            connection.setRequestProperty("Origin", ORIGIN)
            connection.setRequestProperty("User-Agent", USER_AGENT)
            cookies.get(uri, emptyMap()).forEach { (key, values) ->
                if (key.equals("Cookie", true) || key.equals("Cookie2", true))
                    connection.setRequestProperty(key, values.joinToString("; "))
            }
            connection.doOutput = true
            val body = fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            currentCoroutineContext().ensureActive()
            if (connection.responseCode != 200) throw RailwayException(
                if (connection.responseCode in 300..399) RailwayFailure.LOGIN_REQUIRED else RailwayFailure.NETWORK)
            cookies.put(uri, connection.headerFields.filterKeys { it != null })
            connection.inputStream.bufferedReader().use { reader ->
                val value = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (value.length + count > 2_000_000)
                        throw RailwayException(RailwayFailure.SCHEMA_CHANGED)
                    value.append(buffer, 0, count)
                }
                currentCoroutineContext().ensureActive()
                value.toString()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: RailwayException) { throw e }
        catch (_: Exception) { throw RailwayException(RailwayFailure.NETWORK) }
        finally { connection.disconnect() }
      }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    companion object {
        const val ORIGIN = "https://kyfw.12306.cn"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        private val allowedPaths = setOf(
            "/passport/web/checkLoginVerify", "/passport/web/getMessageCode", "/passport/web/login",
            "/passport/web/auth/uamtk", "/otn/uamauthclient",
            "/otn/login/checkUser", "/otn/modifyUser/initQueryUserInfoApi",
            "/otn/confirmPassenger/getPassengerDTOs",
        )
    }
}

/** Stable opaque local references, without persisting usernames or documents to task snapshots. */
class RailwayLocalReferences {
    @Synchronized fun reference(input: String): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generateKey()
        }
        return Mac.getInstance("HmacSHA256").run {
            init(key); doFinal(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
    companion object { private const val ALIAS = "railway-local-references-v1" }
}
