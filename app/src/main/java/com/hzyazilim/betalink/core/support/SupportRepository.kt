package com.hzyazilim.betalink.core.support

import android.content.Context
import com.hzyazilim.betalink.BuildConfig
import com.hzyazilim.betalink.core.auth.SecureSessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SupportTicket(val id: String, val subject: String, val message: String, val status: String, val adminReply: String?, val ticketType: String)

class SupportRepository(context: Context) {
    private val sessions = SecureSessionStore(context.applicationContext)

    fun listTickets(): Pair<List<SupportTicket>, String?> {
        val token = sessions.read()?.first ?: return emptyList<SupportTicket>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<SupportTicket>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/support_tickets?select=id,subject,message,status,admin_reply,ticket_type&user_id=eq.$userId&order=created_at.desc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<SupportTicket>() to error(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            SupportTicket(row.getString("id"), row.optString("subject"), row.optString("message"), row.optString("status"), row.optString("admin_reply").takeIf { it.isNotBlank() }, row.optString("ticket_type", "support"))
        } to null
    }

    fun createTicket(subject: String, message: String, ticketType: String): String? {
        val cleanSubject = subject.trim()
        val cleanMessage = message.trim()
        if (cleanSubject.length !in 3..120) return "Konu 3-120 karakter olmalı."
        if (cleanMessage.length !in 5..5000) return "Mesaj 5-5000 karakter olmalı."
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val body = JSONObject().put("user_id", userId).put("subject", cleanSubject).put("message", cleanMessage).put("ticket_type", when (ticketType) { "complaint" -> "complaint"; "appeal" -> "appeal"; else -> "support" })
        val (status, response) = call("/rest/v1/support_tickets", "POST", token, body)
        return if (status in 200..299) null else error(response)
    }

    private fun call(path: String, method: String, token: String, body: JSONObject? = null): Pair<Int, String> {
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "return=minimal")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            0 to (e.message ?: "Bağlantı hatası")
        } finally {
            connection.disconnect()
        }
    }

    private fun error(raw: String): String = try {
        JSONObject(raw).optString("message").ifBlank { "İşlem tamamlanamadı." }
    } catch (_: Exception) { raw.ifBlank { "Sunucuya bağlanılamadı." } }
}
