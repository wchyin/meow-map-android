package cn.edu.meowmap.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiFailure(val status: Int, message: String) : Exception(message)

/** Matches server/index.js, rather than the older proposed /api/v1 contract. */
class OriginalApi(private val endpoint: String, private val token: () -> String?) {
    suspend fun request(route: String, method: String = "GET", body: ByteArray? = null, binary: Boolean = false, authenticated: Boolean = true): ByteArray = withContext(Dispatchers.IO) {
        require(route.startsWith('/') && !route.startsWith("//"))
        val connection = URL(endpoint.trimEnd('/') + route).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000; connection.readTimeout = if (route == "/cat-assistant") 25_000 else 20_000
            connection.instanceFollowRedirects = false
            if (authenticated) token()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", if (binary) "application/octet-stream" else "application/json")
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val bytes = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: byteArrayOf()
            if (status !in 200..299) throw ApiFailure(status, runCatching {
                JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject.text("error")
            }.getOrDefault("服务请求失败（$status）").ifBlank { "服务请求失败（$status）" })
            bytes
        } finally { connection.disconnect() }
    }
    suspend fun json(route: String, method: String = "GET", body: JsonElement? = null, authenticated: Boolean = true): JsonElement =
        JsonParser.parseString(request(route, method, body?.toString()?.toByteArray(), authenticated = authenticated).toString(Charsets.UTF_8))
}

/** Only encrypted credentials are persisted; account data is stored separately. */
class CloudSession(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("meow-cloud-session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("meow-cloud-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String? = runCatching {
        val encoded = prefs.getString("credential", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            .doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }.getOrNull()
    fun save(value: String?) {
        if (value == null) { prefs.edit().remove("credential").commit(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        check(prefs.edit().putString("credential", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "登录凭据保存失败" }
    }
}
