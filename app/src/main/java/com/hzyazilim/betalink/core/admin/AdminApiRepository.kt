package com.hzyazilim.betalink.core.admin

import android.content.Context
import com.hzyazilim.betalink.BuildConfig
import com.hzyazilim.betalink.core.auth.SecureSessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AdminReport(
    val id: String,
    val targetUserId: String?,
    val campaignId: String?,
    val reason: String,
    val details: String,
    val status: String
)

data class AdminSupportTicket(val id: String, val userId: String, val subject: String, val message: String, val status: String)

data class AdminDashboard(
    val users: Int,
    val campaigns: Int,
    val reports: Int,
    val groups: Int,
    val pendingReports: List<AdminReport>,
    val supportTickets: List<AdminSupportTicket>
)

class AdminApiRepository(context: Context) {
    private val sessions = SecureSessionStore(context.applicationContext)

    fun isAdmin(): Boolean {
        val token = sessions.read()?.first ?: return false
        val (status, _) = call("GET", token)
        return status in 200..299
    }

    fun dashboard(): Pair<AdminDashboard?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("GET", token)
        if (status !in 200..299) return null to error(body)
        val json = JSONObject(body)
        val stats = json.optJSONObject("stats") ?: JSONObject()
        val array = json.optJSONArray("pendingReports") ?: JSONArray()
        val supportArray = json.optJSONArray("supportTickets") ?: JSONArray()
        val support = (0 until supportArray.length()).map { i ->
            val row = supportArray.getJSONObject(i)
            AdminSupportTicket(row.getString("id"), row.getString("user_id"), row.optString("subject"), row.optString("message"), row.optString("status"))
        }
        val reports = (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            AdminReport(
                row.getString("id"),
                row.optString("target_user_id").takeIf { it.isNotBlank() && it != "null" },
                row.optString("campaign_id").takeIf { it.isNotBlank() && it != "null" },
                row.optString("reason"), row.optString("details"), row.optString("status")
            )
        }
        return AdminDashboard(
            stats.optInt("users"), stats.optInt("campaigns"), stats.optInt("reports"),
            stats.optInt("groups"), reports, support
        ) to null
    }


    fun resolveSupportTicket(id: String, reply: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (code, body) = call("POST", token, JSONObject()
            .put("action", "resolve_support_ticket").put("ticket_id", id).put("reply", reply))
        return if (code in 200..299) null else error(body)
    }

    fun resolveReport(id: String, status: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (code, body) = call("POST", token, JSONObject()
            .put("action", "resolve_report").put("report_id", id).put("status", status))
        return if (code in 200..299) null else error(body)
    }

    fun blockUser(userId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (code, body) = call("POST", token, JSONObject().put("action", "block_user").put("user_id", userId))
        return if (code in 200..299) null else error(body)
    }

    fun announce(title: String, bodyText: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (code, body) = call("POST", token, JSONObject().put("action", "announce").put("title", title).put("body", bodyText))
        return if (code in 200..299) null else error(body)
    }

    private fun call(method: String, token: String, body: JSONObject? = null): Pair<Int, String> {
        val connection = URL(BuildConfig.SUPABASE_URL + "/functions/v1/admin-api").openConnection() as HttpURLConnection
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
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: Exception) {
            0 to (e.message ?: "Sunucuya bağlanılamadı.")
        } finally {
            connection.disconnect()
        }
    }

    private fun error(raw: String): String = try {
        JSONObject(raw).optString("error").ifBlank { "Yönetici işlemi başarısız." }
    } catch (_: Exception) { raw.ifBlank { "Yönetici işlemi başarısız." } }
}
