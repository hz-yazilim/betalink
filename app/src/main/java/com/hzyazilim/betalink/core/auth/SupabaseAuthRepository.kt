package com.hzyazilim.betalink.core.auth

import android.content.Context
import com.hzyazilim.betalink.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BetalinkUser(
    val id: String,
    val email: String,
    val displayName: String
)

data class AuthResult(
    val user: BetalinkUser? = null,
    val message: String? = null
)

class SupabaseAuthRepository(context: Context) {
    private val sessions = SecureSessionStore(context.applicationContext)

    fun restoreSession(): BetalinkUser? {
        val saved = sessions.read() ?: return null
        val response = request("/auth/v1/user", "GET", token = saved.first)
        if (response.first in 200..299) return parseUser(response.second)
        if (response.first != 401) return null

        val refreshBody = JSONObject().put("refresh_token", saved.second)
        val refreshed = request("/auth/v1/token?grant_type=refresh_token", "POST", refreshBody)
        if (refreshed.first !in 200..299) {
            sessions.clear()
            return null
        }
        val json = JSONObject(refreshed.second)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) {
            sessions.clear()
            return null
        }
        sessions.save(access, refresh)
        val userJson = json.optJSONObject("user") ?: return null
        return parseUser(userJson)
    }

    fun signIn(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val response = request("/auth/v1/token?grant_type=password", "POST", body)
        if (response.first !in 200..299) return AuthResult(message = errorMessage(response.second))
        val json = JSONObject(response.second)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) return AuthResult(message = "Oturum bilgileri alınamadı.")
        sessions.save(access, refresh)
        return AuthResult(user = parseUser(json.optJSONObject("user") ?: JSONObject()))
    }

    fun signUp(email: String, password: String, displayName: String): AuthResult {
        val metadata = JSONObject().put("display_name", displayName.trim())
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", metadata)
        val response = request("/auth/v1/signup", "POST", body)
        if (response.first !in 200..299) return AuthResult(message = errorMessage(response.second))
        val json = JSONObject(response.second)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isNotBlank() && refresh.isNotBlank()) {
            sessions.save(access, refresh)
            return AuthResult(user = parseUser(json.optJSONObject("user") ?: JSONObject()))
        }
        return AuthResult(message = "Kayıt oluşturuldu. E-posta doğrulama bağlantısını kontrol et; doğruladıktan sonra giriş yap.")
    }

    fun deleteAccount(): String? {
        val saved = sessions.read() ?: return "Oturum bulunamadı. Yeniden giriş yap."
        val response = request("/functions/v1/delete-account", "POST", JSONObject(), saved.first)
        if (response.first in 200..299) {
            sessions.clear()
            return null
        }
        return errorMessage(response.second)
    }

    fun signOut() {
        val saved = sessions.read()
        if (saved != null) request("/auth/v1/logout", "POST", token = saved.first)
        sessions.clear()
    }

    private fun parseUser(json: JSONObject): BetalinkUser? {
        val id = json.optString("id")
        if (id.isBlank()) return null
        val metadata = json.optJSONObject("user_metadata") ?: JSONObject()
        val displayName = metadata.optString("display_name").ifBlank {
            json.optString("email").substringBefore("@")
        }
        return BetalinkUser(id, json.optString("email"), displayName)
    }

    private fun request(path: String, method: String, body: JSONObject? = null, token: String? = null): Pair<Int, String> {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(raw: String): String {
        return try {
            val json = JSONObject(raw)
            json.optString("msg").ifBlank {
                json.optString("message").ifBlank {
                    json.optString("error_description").ifBlank { "İşlem tamamlanamadı. Bilgilerini kontrol edip tekrar dene." }
                }
            }
        } catch (_: Exception) {
            "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et."
        }
    }

    companion object {
        private const val UNUSED = "unused"
    }
}
