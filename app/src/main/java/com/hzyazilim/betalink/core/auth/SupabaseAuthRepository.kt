package com.hzyazilim.betalink.core.auth

import android.content.Context
import android.net.Uri
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

data class SessionRestoreResult(
    val user: BetalinkUser? = null,
    val message: String? = null,
    val retryable: Boolean = false
)

class SupabaseAuthRepository(context: Context) {
    private val sessions = SecureSessionStore(context.applicationContext)

    fun restoreSession(): SessionRestoreResult {
        val saved = sessions.read() ?: return SessionRestoreResult()
        val response = request("/auth/v1/user", "GET", token = saved.first)
        if (response.first in 200..299) {
            val user = runCatching { parseUser(JSONObject(response.second)) }.getOrNull()
            return if (user != null) SessionRestoreResult(user = user)
            else SessionRestoreResult(message = "Hesap bilgileri okunamadı. Yeniden giriş yap.")
        }

        // Do not destroy a valid refresh token because of a temporary network outage.
        if (response.first !in setOf(401, 403)) {
            if (isTransientStatus(response.first)) {
                return SessionRestoreResult(
                    message = "Oturum kontrol edilemedi. İnternet bağlantını kontrol edip tekrar dene.",
                    retryable = true
                )
            }
            sessions.clear()
            return SessionRestoreResult()
        }

        val refreshBody = JSONObject().put("refresh_token", saved.second)
        val refreshed = request("/auth/v1/token?grant_type=refresh_token", "POST", refreshBody)
        if (refreshed.first !in 200..299) {
            if (isTransientStatus(refreshed.first)) {
                return SessionRestoreResult(
                    message = "Oturum yenilenemedi. İnternet bağlantını kontrol edip tekrar dene.",
                    retryable = true
                )
            }
            if (refreshed.first in 400..499) sessions.clear()
            return SessionRestoreResult()
        }

        val json = runCatching { JSONObject(refreshed.second) }.getOrNull()
            ?: return SessionRestoreResult(
                message = "Sunucudan geçersiz oturum yanıtı alındı. Tekrar dene.",
                retryable = true
            )
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) {
            return SessionRestoreResult(
                message = "Oturum yenileme bilgileri eksik. Tekrar dene.",
                retryable = true
            )
        }
        sessions.save(access, refresh)
        val user = runCatching { json.optJSONObject("user")?.let(::parseUser) }.getOrNull()
        if (user != null) return SessionRestoreResult(user = user)

        val verified = request("/auth/v1/user", "GET", token = access)
        if (verified.first in 200..299) {
            val verifiedUser = runCatching { parseUser(JSONObject(verified.second)) }.getOrNull()
            if (verifiedUser != null) return SessionRestoreResult(user = verifiedUser)
        }
        if (isTransientStatus(verified.first)) {
            return SessionRestoreResult(
                message = "Oturum yenilendi ancak hesap doğrulanamadı. Tekrar dene.",
                retryable = true
            )
        }
        sessions.clear()
        return SessionRestoreResult()
    }

    private fun isTransientStatus(status: Int): Boolean =
        status == 0 || status == 408 || status == 425 || status == 429 || status >= 500

    fun signIn(email: String, password: String, rememberMe: Boolean = true): AuthResult {
        val rawEmail = email.trim()
        val canonicalEmail = canonicalGmail(rawEmail) ?: rawEmail
        val candidates = listOf(canonicalEmail, rawEmail).distinct()
        var response: Pair<Int, String>? = null
        for (candidate in candidates) {
            val body = JSONObject().put("email", candidate).put("password", password)
            val attempt = request("/auth/v1/token?grant_type=password", "POST", body)
            if (attempt.first in 200..299) {
                response = attempt
                break
            }
            response = attempt
        }
        val result = response ?: return AuthResult(message = "Giriş bilgileri kontrol edilemedi.")
        if (result.first !in 200..299) return AuthResult(message = errorMessage(result.second))
        val json = JSONObject(result.second)
        val userJson = json.optJSONObject("user") ?: JSONObject()
        if (userJson.optString("email_confirmed_at").isBlank()) {
            sessions.clear()
            return AuthResult(message = "Devam etmek için Gmail adresini gelen doğrulama bağlantısıyla onaylamalısın.")
        }
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) return AuthResult(message = "Oturum bilgileri alınamadı.")
        sessions.save(access, refresh, rememberMe)
        return AuthResult(user = parseUser(userJson))
    }

    fun signInWithGoogleIdToken(idToken: String, rememberMe: Boolean = true): AuthResult {
        val body = JSONObject().put("provider", "google").put("id_token", idToken)
        val response = request("/auth/v1/token?grant_type=id_token", "POST", body)
        if (response.first !in 200..299) return AuthResult(message = errorMessage(response.second))
        val json = JSONObject(response.second)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) return AuthResult(message = "Google oturumu Supabase tarafından doğrulanamadı.")
        sessions.save(access, refresh, rememberMe)
        return AuthResult(user = parseUser(json.optJSONObject("user") ?: JSONObject()))
    }

    fun signUp(email: String, password: String, displayName: String): AuthResult {
        val normalizedEmail = canonicalGmail(email)
            ?: return AuthResult(message = "Kayıt için geçerli bir @gmail.com adresi kullanmalısın. Nokta ve +etiket varyasyonları aynı Gmail hesabı olarak kabul edilir.")
        if (!PasswordPolicy.isValid(password)) return AuthResult(message = PasswordPolicy.ERROR_MESSAGE)
        val metadata = JSONObject().put("display_name", displayName.trim())
        val body = JSONObject()
            .put("email", normalizedEmail)
            .put("password", password)
            .put("data", metadata)
        val redirect = URLEncoder.encode("betalink://auth-callback", "UTF-8")
        val response = request("/auth/v1/signup?redirect_to=$redirect", "POST", body)
        if (response.first !in 200..299) return AuthResult(message = errorMessage(response.second))
        val json = JSONObject(response.second)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isNotBlank() && refresh.isNotBlank()) {
            val userJson = json.optJSONObject("user") ?: JSONObject()
            if (userJson.optString("email_confirmed_at").isBlank()) {
                sessions.clear()
                return AuthResult(message = "Kayıt isteğin alındı. Hesabın açılmadan önce Gmail adresine gönderilen bağlantıyı doğrulamalısın.")
            }
            sessions.save(access, refresh)
            return AuthResult(user = parseUser(userJson))
        }
        return AuthResult(message = "Kayıt isteğin alındı. Gmail adresine gönderilen doğrulama bağlantısını açmadan hesabın kullanıma açılamaz.")
    }

    fun completeEmailConfirmation(callbackUri: String): AuthResult {
        val uri = Uri.parse(callbackUri)
        val params = Uri.parse("https://callback.invalid/?${uri.fragment.orEmpty()}")
        val errorCode = params.getQueryParameter("error_code").orEmpty()
        if (errorCode.isNotBlank()) {
            val message = if (errorCode == "otp_expired") {
                "Doğrulama bağlantısının süresi dolmuş veya daha önce açılmış. Yeni doğrulama e-postası isteyebilirsin."
            } else {
                params.getQueryParameter("error_description")?.replace("+", " ")
                    ?: "E-posta doğrulaması tamamlanamadı. Yeni bağlantı isteyip tekrar dene."
            }
            return AuthResult(message = message)
        }
        val access = params.getQueryParameter("access_token").orEmpty()
        val refresh = params.getQueryParameter("refresh_token").orEmpty()
        if (access.isBlank() || refresh.isBlank()) {
            return AuthResult(message = "Doğrulama bağlantısı açıldı ancak oturum bilgisi alınamadı. Yeni e-posta isteyip tekrar dene.")
        }
        sessions.save(access, refresh)
        val response = request("/auth/v1/user", "GET", token = access)
        if (response.first !in 200..299) {
            sessions.clear()
            return AuthResult(message = "E-posta bağlantısı doğrulandı ancak oturum açılamadı. Uygulamadan giriş yap.")
        }
        return AuthResult(user = parseUser(JSONObject(response.second)))
    }

    fun resendSignupConfirmation(email: String): AuthResult {
        if (email.isBlank()) return AuthResult(message = "Önce kayıt e-posta adresini yaz.")
        val normalizedEmail = canonicalGmail(email)
            ?: return AuthResult(message = "Doğrulama için kayıtlı @gmail.com adresini yaz.")
        val redirect = URLEncoder.encode("betalink://auth-callback", "UTF-8")
        val body = JSONObject().put("type", "signup").put("email", normalizedEmail)
        val response = request("/auth/v1/resend?redirect_to=$redirect", "POST", body)
        return if (response.first in 200..299) {
            AuthResult(message = "Yeni doğrulama e-postası gönderildi. Gelen kutunu ve spam klasörünü kontrol et.")
        } else {
            AuthResult(message = errorMessage(response.second))
        }
    }

    fun isPasswordRecovery(callbackUri: String): Boolean {
        val uri = Uri.parse(callbackUri)
        val params = Uri.parse("https://callback.invalid/?${uri.fragment.orEmpty()}")
        return params.getQueryParameter("type") == "recovery"
    }

    fun updatePassword(newPassword: String): AuthResult {
        if (!PasswordPolicy.isValid(newPassword)) return AuthResult(message = PasswordPolicy.ERROR_MESSAGE)
        val saved = sessions.read() ?: return AuthResult(message = "Şifre sıfırlama oturumu bulunamadı. Yeni bağlantı iste.")
        val response = request("/auth/v1/user", "PUT", JSONObject().put("password", newPassword), saved.first)
        if (response.first !in 200..299) return AuthResult(message = errorMessage(response.second))
        val user = runCatching { parseUser(JSONObject(response.second)) }.getOrNull()
        if (user != null) return AuthResult(user = user)
        val verified = request("/auth/v1/user", "GET", token = saved.first)
        return if (verified.first in 200..299) AuthResult(user = parseUser(JSONObject(verified.second)))
        else AuthResult(message = "Şifre güncellendi ancak oturum doğrulanamadı. Yeniden giriş yap.")
    }

    fun sendPasswordReset(email: String): AuthResult {
        val normalizedEmail = canonicalGmail(email)
            ?: return AuthResult(message = "Şifre sıfırlama için kayıtlı @gmail.com adresini yaz.")
        val redirect = URLEncoder.encode("betalink://auth-callback", "UTF-8")
        val body = JSONObject().put("email", normalizedEmail)
        val response = request("/auth/v1/recover?redirect_to=$redirect", "POST", body)
        return if (response.first in 200..299) {
            AuthResult(message = "Şifre sıfırlama bağlantısı gönderildi. Gelen kutunu ve spam klasörünü kontrol et.")
        } else AuthResult(message = errorMessage(response.second))
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

    // Gmail ignores dots and everything after '+' in consumer @gmail.com addresses.
    // Canonicalizing before sign-up prevents creating multiple Betalink accounts for aliases
    // that all deliver to the same Gmail inbox.
    private fun canonicalGmail(value: String): String? {
        val email = value.trim().lowercase(java.util.Locale.ROOT)
        if (!email.matches(Regex("^[a-z0-9][a-z0-9._+-]{4,29}@gmail\\.com$"))) return null
        val local = email.substringBefore("@").substringBefore("+").replace(".", "")
        if (local.length !in 6..30 || local.isBlank()) return null
        return "$local@gmail.com"
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
        } catch (e: Exception) {
            0 to (e.message ?: "Sunucuya bağlanılamadı.")
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
