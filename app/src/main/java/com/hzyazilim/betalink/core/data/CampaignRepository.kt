package com.hzyazilim.betalink.core.data

import android.content.Context
import com.hzyazilim.betalink.BuildConfig
import com.hzyazilim.betalink.core.auth.SecureSessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CampaignMember(val id: String, val testerId: String, val status: String)\ndata class CampaignTask(val id: String, val title: String, val description: String, val completed: Boolean)\ndata class CampaignTracking(val status: String, val elapsedDays: Int, val requiredDays: Int, val activeTesters: Int, val requiredTesters: Int)

data class Campaign(
    val id: String,
    val appName: String,
    val description: String,
    val category: String,
    val optInUrl: String,
    val targetTesters: Int,
    val status: String,
    val minimumAndroid: Int?,
    val membershipStatus: String? = null
)

class CampaignRepository(context: Context) {
    private val sessions = SecureSessionStore(context.applicationContext)

    fun listOpenCampaigns(): Pair<List<Campaign>, String?> {
        val token = sessions.read()?.first ?: return emptyList<Campaign>() to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/campaigns?select=id,app_name,description,category,play_opt_in_url,target_testers,status,minimum_android_version&status=eq.open&order=created_at.desc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<Campaign>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val j = array.getJSONObject(i)
            Campaign(
                j.getString("id"), j.getString("app_name"), j.optString("description"),
                j.optString("category"), j.getString("play_opt_in_url"),
                j.optInt("target_testers", 12), j.optString("status"),
                if (j.isNull("minimum_android_version")) null else j.optInt("minimum_android_version")
            )
        } to null
    }

    fun createCampaign(appName: String, optInUrl: String, description: String, category: String, target: Int, minAndroid: Int?): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val developerId = JSONObject(user.second).optString("id")
        if (developerId.isBlank()) return "Kullanıcı bilgisi alınamadı."
        val body = JSONObject()
            .put("developer_id", developerId)
            .put("app_name", appName.trim())
            .put("play_opt_in_url", optInUrl.trim())
            .put("description", description.trim())
            .put("category", category)
            .put("target_testers", target)
            .put("status", "open")
        if (minAndroid != null) body.put("minimum_android_version", minAndroid)
        val (status, response) = call("/rest/v1/campaigns", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun applyToCampaign(campaignId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        if (userId.isBlank()) return "Kullanıcı bilgisi alınamadı."
        val body = JSONObject().put("campaign_id", campaignId).put("tester_id", userId)
            .put("status", "applied").put("is_backup", false)
        val (status, response) = call("/rest/v1/campaign_members", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }





    fun trackingSnapshot(campaignId: String): Pair<CampaignTracking?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/rpc/get_betalink_campaign_tracking", "POST", token,
            JSONObject().put("p_campaign_id", campaignId)
        )
        if (status !in 200..299) return null to message(body)
        val array = JSONArray(body)
        if (array.length() == 0) return null to "Takip kaydı bulunamadı."
        val row = array.getJSONObject(0)
        return CampaignTracking(
            row.optString("tracking_status"),
            row.optInt("elapsed_days"),
            row.optInt("required_days", 14),
            row.optInt("active_testers"),
            row.optInt("required_testers", 12)
        ) to null
    }

    fun tasksForCampaign(campaignId: String): Pair<List<CampaignTask>, String?> {
        val token = sessions.read()?.first ?: return emptyList<CampaignTask>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<CampaignTask>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (taskStatus, taskBody) = call(
            "/rest/v1/campaign_tasks?select=id,title,description,position&campaign_id=eq.$campaignId&order=position.asc",
            "GET", token
        )
        if (taskStatus !in 200..299) return emptyList<CampaignTask>() to message(taskBody)
        val (doneStatus, doneBody) = call(
            "/rest/v1/task_completions?select=task_id,status&tester_id=eq.$userId",
            "GET", token
        )
        if (doneStatus !in 200..299) return emptyList<CampaignTask>() to message(doneBody)
        val done = mutableSetOf<String>()
        val doneArray = JSONArray(doneBody)
        for (i in 0 until doneArray.length()) {
            val row = doneArray.getJSONObject(i)
            if (row.optString("status") in listOf("approved", "submitted")) done += row.optString("task_id")
        }
        val tasks = JSONArray(taskBody)
        return (0 until tasks.length()).map { i ->
            val j = tasks.getJSONObject(i)
            CampaignTask(j.getString("id"), j.getString("title"), j.optString("description"), done.contains(j.getString("id")))
        } to null
    }

    fun submitTask(taskId: String, note: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val body = JSONObject().put("task_id", taskId).put("tester_id", userId)
            .put("status", "submitted").put("notes", note)
        val (status, response) = call("/rest/v1/task_completions", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun sendFeedback(campaignId: String, bodyText: String, rating: Int): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val body = JSONObject().put("campaign_id", campaignId).put("tester_id", userId)
            .put("feedback_type", "experience").put("rating", rating).put("body", bodyText.trim())
        val (status, response) = call("/rest/v1/feedback", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun myDeveloperCampaigns(): Pair<List<Campaign>, String?> {
        val token = sessions.read()?.first ?: return emptyList<Campaign>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<Campaign>() to "Oturum doğrulanamadı."
        val id = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/campaigns?select=id,app_name,description,category,play_opt_in_url,target_testers,status,minimum_android_version&developer_id=eq.$id&order=created_at.desc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<Campaign>() to message(body)
        val array = JSONArray(body)
        val result = (0 until array.length()).map { i ->
            val j = array.getJSONObject(i)
            Campaign(
                j.getString("id"), j.getString("app_name"), j.optString("description"),
                j.optString("category"), j.getString("play_opt_in_url"),
                j.optInt("target_testers", 12), j.optString("status"),
                if (j.isNull("minimum_android_version")) null else j.optInt("minimum_android_version")
            )
        }
        return result to null
    }

    fun applicants(campaignId: String): Pair<List<CampaignMember>, String?> {
        val token = sessions.read()?.first ?: return emptyList<CampaignMember>() to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/campaign_members?select=id,tester_id,status&campaign_id=eq.$campaignId&order=joined_at.asc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<CampaignMember>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val j = array.getJSONObject(i)
            CampaignMember(j.getString("id"), j.getString("tester_id"), j.optString("status"))
        } to null
    }

    fun reviewApplicant(memberId: String, statusValue: String): String? {
        if (statusValue !in listOf("accepted","rejected","waitlisted")) return "Geçersiz başvuru durumu."
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject().put("status", statusValue)
        val (status, response) = call(
            "/rest/v1/campaign_members?id=eq.$memberId", "PATCH", token, body
        )
        return if (status in 200..299) null else message(response)
    }

    fun confirmPlayOptIn(campaignId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (status, response) = call(
            "/rest/v1/rpc/confirm_betalink_play_opt_in", "POST", token,
            JSONObject().put("p_campaign_id", campaignId)
        )
        return if (status in 200..299) null else message(response)
    }

    fun checkInToday(campaignId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val body = JSONObject().put("campaign_id", campaignId).put("tester_id", userId)
            .put("note", "Uygulama içi günlük test onayı")
        val (status, response) = call("/rest/v1/campaign_daily_checkins", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun myCampaigns(): Pair<List<Campaign>, String?> {
        val token = sessions.read()?.first ?: return emptyList<Campaign>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<Campaign>() to "Oturum doğrulanamadı."
        val id = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/campaign_members?select=status,campaigns(id,app_name,description,category,play_opt_in_url,target_testers,status,minimum_android_version)&tester_id=eq.$id&order=joined_at.desc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<Campaign>() to message(body)
        val array = JSONArray(body)
        val result = mutableListOf<Campaign>()
        for (i in 0 until array.length()) {
            val campaign = array.getJSONObject(i).optJSONObject("campaigns") ?: continue
            result += Campaign(
                campaign.getString("id"), campaign.getString("app_name"), campaign.optString("description"),
                campaign.optString("category"), campaign.getString("play_opt_in_url"),
                campaign.optInt("target_testers", 12), campaign.optString("status"),
                if (campaign.isNull("minimum_android_version")) null else campaign.optInt("minimum_android_version"),
                array.getJSONObject(i).optString("status")
            )
        }
        return result to null
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

    private fun message(raw: String): String = try {
        val j = JSONObject(raw)
        j.optString("message").ifBlank { j.optString("hint").ifBlank { "İşlem tamamlanamadı." } }
    } catch (_: Exception) { raw.ifBlank { "Sunucuya bağlanılamadı." } }
}
