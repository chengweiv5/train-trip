package cn.traintrip.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface GuideCredentials {
    fun read(): String?
    fun save(value: String)
    fun remove()
}

class DeepSeekSettings(context: Context) : EncryptedGuideSettings(context, "destination-ai", "train-trip-deepseek", "sk-", "DeepSeek")
class TavilySettings(context: Context) : EncryptedGuideSettings(context, "destination-search", "train-trip-tavily", "tvly-", "Tavily")

open class EncryptedGuideSettings(context: Context, preference: String, private val alias: String,
    private val prefix: String, private val provider: String) : GuideCredentials {
    private val prefs = context.getSharedPreferences(preference, Context.MODE_PRIVATE)
    private fun encryptionKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): String? {
        val data = prefs.getString("encryptedKey", null) ?: return null
        return try {
            val parts = data.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("无法读取已保存的密钥，请重新配置") }
    }
    override fun save(value: String) {
        validateGuideKey(value, prefix, provider)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val data = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString("encryptedKey", data).commit()) { "密钥保存失败，请重试" }
    }
    override fun remove() { check(prefs.edit().remove("encryptedKey").commit()) { "移除配置失败" } }
}

internal fun validateGuideKey(value: String, prefix: String, provider: String) {
    require(value.startsWith(prefix) && value.length in 20..200 && value.none { it.isWhitespace() }) { "请输入有效的 $provider API Key" }
}
