package com.hzyazilim.betalink.core.data

import android.content.Context
import android.os.Build
import com.hzyazilim.betalink.BuildConfig
import com.hzyazilim.betalink.core.auth.SecureSessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CampaignMember(val id: String, val testerId: String, val status: String)
data class TesterGroup(val id: String, val name: String, val description: String, val isPrivate: Boolean, val membershipStatus: String?, val isCreator: Boolean)
data class GroupMember(val id: String, val userId: String, val status: String)
data class DirectoryPerson(val id: String, val displayName: String, val canReceiveInvites: Boolean)
data class GroupInvitation(
    val id: String, val groupId: String, val groupName: String, val groupDescription: String,
    val inviterId: String, val message: String, val token: String, val createdAt: String
)
data class GroupTestEmail(val displayName: String, val email: String)
data class GroupSnapshot(val status: String, val approvedMembers: Int, val requiredMembers: Int, val elapsedDays: Int, val requiredDays: Int, val startedAt: String?, val myEmailShared: Boolean, val myPlayTestEmail: String, val emails: List<GroupTestEmail>)
data class CampaignTask(val id: String, val title: String, val description: String, val completed: Boolean)
data class CampaignTracking(val status: String, val elapsedDays: Int, val requiredDays: Int, val activeTesters: Int, val requiredTesters: Int, val startedAt: String? = null)
data class TaskSubmission(val id: String, val taskTitle: String, val testerId: String, val status: String, val notes: String)
data class FeedbackItem(val id: String, val testerId: String, val rating: Int?, val body: String, val status: String, val reply: String?)
data class BetalinkNotification(val id: String, val title: String, val body: String, val read: Boolean, val createdAt: String)
data class BetaWallet(val balance: Int, val lifetimeEarned: Int, val completedSupportTests: Int)
data class TesterReliability(val testsJoined: Int, val testsCompleted: Int, val testsDropped: Int, val dailyCheckins: Int, val missedDays: Int, val reputationPoints: Int) {
    val attendancePercent: Int get() = if (dailyCheckins + missedDays == 0) 0 else (dailyCheckins * 100 / (dailyCheckins + missedDays)).coerceIn(0, 100)
}
data class CampaignReport(val activeTesters: Int, val backupTesters: Int, val droppedTesters: Int, val totalCheckins: Int, val feedbackCount: Int, val averageRating: Double?, val bugReports: Int, val suggestions: Int, val acceptedTesters: Int)
data class FeedbackSummary(val feedbackCount: Int, val averageRating: Double?, val bugs: Int, val experiences: Int, val suggestions: Int, val resolved: Int)

data class Campaign(
    val id: String,
    val appName: String,
    val description: String,
    val category: String,
    val optInUrl: String,
    val targetTesters: Int,
    val status: String,
    val minimumAndroid: Int?,
    val membershipStatus: String? = null,
    val joinedAt: String? = null
)

class CampaignRepository(context: Context) {
    private val sessions = SecureSessionStore(context.applicationContext)

    companion object {
        const val CURRENT_TERMS_VERSION = "2026-11-08"
        const val TERMS_URL = "https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/terms"
    }

    fun currentTermsAccepted(): Pair<Boolean?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return null to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call("/rest/v1/profiles?select=terms_version&user_id=eq.$userId", "GET", token)
        if (status !in 200..299) return null to message(body)
        val rows = JSONArray(body)
        if (rows.length() == 0) return false to null
        val accepted = rows.getJSONObject(0).optString("terms_version") == CURRENT_TERMS_VERSION
        return accepted to null
    }

    fun acceptCurrentTerms(): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val body = JSONObject()
            .put("terms_version", CURRENT_TERMS_VERSION)
            .put("terms_accepted_at", utcNow())
        val (status, response) = call("/rest/v1/profiles?user_id=eq.$userId", "PATCH", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun listOpenCampaigns(): Pair<List<Campaign>, String?> {
        val token = sessions.read()?.first ?: return emptyList<Campaign>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<Campaign>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/campaigns?select=id,app_name,description,category,play_opt_in_url,target_testers,status,minimum_android_version&status=eq.open&developer_id=neq.$userId&order=created_at.desc",
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
        }.filter { it.minimumAndroid == null || it.minimumAndroid <= (Build.VERSION.RELEASE.substringBefore(".").toIntOrNull() ?: Build.VERSION.SDK_INT) } to null
    }

    fun syncTesterDevice(): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val body = JSONObject()
            .put("user_id", userId)
            .put("device_model", Build.MANUFACTURER + " " + Build.MODEL)
            .put("android_version", Build.VERSION.RELEASE.substringBefore(".").toIntOrNull() ?: Build.VERSION.SDK_INT)
            .put("available_for_testing", true)
            .put("updated_at", utcNow())
        val existing = call("/rest/v1/tester_profiles?select=user_id&user_id=eq.$userId", "GET", token)
        if (existing.first !in 200..299) return message(existing.second)
        val exists = JSONArray(existing.second).length() > 0
        val path = if (exists) "/rest/v1/tester_profiles?user_id=eq.$userId" else "/rest/v1/tester_profiles"
        val (status, response) = call(path, if (exists) "PATCH" else "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun createCampaign(appName: String, optInUrl: String, description: String, category: String, target: Int, minAndroid: Int?): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject()
            .put("p_app_name", appName.trim())
            .put("p_play_opt_in_url", optInUrl.trim())
            .put("p_description", description.trim())
            .put("p_category", category)
            .put("p_target_testers", target)
        if (minAndroid != null) body.put("p_minimum_android_version", minAndroid)
        val (status, response) = call("/rest/v1/rpc/betalink_create_campaign", "POST", token, body)
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






    fun listGroups(): Pair<List<TesterGroup>, String?> {
        val token = sessions.read()?.first ?: return emptyList<TesterGroup>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<TesterGroup>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val groupsResponse = call("/rest/v1/tester_groups?select=id,name,description,is_private,created_by&order=created_at.desc", "GET", token)
        if (groupsResponse.first !in 200..299) return emptyList<TesterGroup>() to message(groupsResponse.second)
        val memberResponse = call("/rest/v1/group_members?select=group_id,status&user_id=eq.$userId", "GET", token)
        if (memberResponse.first !in 200..299) return emptyList<TesterGroup>() to message(memberResponse.second)
        val statuses = mutableMapOf<String, String>()
        val memberArray = JSONArray(memberResponse.second)
        for (i in 0 until memberArray.length()) {
            val row = memberArray.getJSONObject(i)
            statuses[row.optString("group_id")] = row.optString("status")
        }
        val array = JSONArray(groupsResponse.second)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            val id = row.getString("id")
            TesterGroup(id, row.getString("name"), row.optString("description"), false, statuses[id], row.optString("created_by") == userId)
        } to null
    }

    fun createGroup(name: String, description: String, privateGroup: Boolean): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject().put("p_name", name.trim()).put("p_description", description.trim()).put("p_is_private", false)
        val (status, response) = call("/rest/v1/rpc/betalink_create_group", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun joinGroup(group: TesterGroup): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (status, response) = call("/rest/v1/rpc/betalink_join_group", "POST", token, JSONObject().put("p_group_id", group.id))
        return if (status in 200..299) null else message(response)
    }

    fun groupMembers(groupId: String): Pair<List<GroupMember>, String?> {
        val token = sessions.read()?.first ?: return emptyList<GroupMember>() to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/group_members?select=id,user_id,status&group_id=eq.$groupId&order=joined_at.asc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<GroupMember>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            GroupMember(row.getString("id"), row.getString("user_id"), row.optString("status"))
        } to null
    }

    fun reviewGroupMember(memberId: String, approved: Boolean): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject().put("status", if (approved) "approved" else "removed")
        val (status, response) = call("/rest/v1/group_members?id=eq.$memberId", "PATCH", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun removeGroupMember(groupId: String, memberId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (status, response) = call("/rest/v1/rpc/betalink_group_remove_member", "POST", token,
            JSONObject().put("p_group_id", groupId).put("p_member_id", memberId))
        return if (status in 200..299) null else message(response)
    }

    fun testerReliability(): Pair<TesterReliability?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return null to "Oturum doğrulanamadı."
        val id = JSONObject(user.second).optString("id")
        val (status, body) = call("/rest/v1/betalink_tester_reliability?select=tests_joined,tests_completed,tests_dropped,daily_checkins,missed_days,reputation_points&user_id=eq.$id", "GET", token)
        if (status !in 200..299) return null to message(body)
        val rows = JSONArray(body)
        if (rows.length() == 0) return TesterReliability(0,0,0,0,0,0) to null
        val row = rows.getJSONObject(0)
        return TesterReliability(row.optInt("tests_joined"),row.optInt("tests_completed"),row.optInt("tests_dropped"),row.optInt("daily_checkins"),row.optInt("missed_days"),row.optInt("reputation_points")) to null
    }

    fun testerReliabilityForCampaign(campaignId: String, testerId: String): Pair<TesterReliability?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("/rest/v1/rpc/betalink_tester_reliability_for_campaign", "POST", token,
            JSONObject().put("p_campaign_id",campaignId).put("p_tester_id",testerId))
        if (status !in 200..299) return null to message(body)
        val row = JSONObject(body)
        return TesterReliability(row.optInt("tests_joined"),row.optInt("tests_completed"),row.optInt("tests_dropped"),row.optInt("daily_checkins"),row.optInt("missed_days"),row.optInt("reputation_points")) to null
    }

    fun campaignReport(campaignId: String): Pair<CampaignReport?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("/rest/v1/betalink_campaign_report?select=active_testers,backup_testers,dropped_testers,total_checkins,feedback_count,average_rating,bug_reports,suggestions,accepted_testers&campaign_id=eq.$campaignId", "GET", token)
        if (status !in 200..299) return null to message(body)
        val rows = JSONArray(body)
        if (rows.length() == 0) return null to "Rapor bulunamadı."
        val row = rows.getJSONObject(0)
        return CampaignReport(row.optInt("active_testers"),row.optInt("backup_testers"),row.optInt("dropped_testers"),row.optInt("total_checkins"),row.optInt("feedback_count"),if(row.isNull("average_rating")) null else row.optDouble("average_rating"),row.optInt("bug_reports"),row.optInt("suggestions"),row.optInt("accepted_testers")) to null
    }

    fun campaignFeedbackSummary(campaignId: String): Pair<FeedbackSummary?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("/rest/v1/rpc/betalink_campaign_feedback_summary", "POST", token, JSONObject().put("p_campaign_id",campaignId))
        if (status !in 200..299) return null to message(body)
        val row = JSONObject(body)
        return FeedbackSummary(row.optInt("feedback_count"),if(row.isNull("average_rating")) null else row.optDouble("average_rating"),row.optInt("bugs"),row.optInt("experiences"),row.optInt("suggestions"),row.optInt("resolved")) to null
    }

    fun processCampaignAttendance(campaignId: String): Pair<String?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("/rest/v1/rpc/betalink_process_campaign_attendance", "POST", token, JSONObject().put("p_campaign_id",campaignId))
        if (status !in 200..299) return null to message(body)
        return body to null
    }

    fun setGroupEmailConsent(groupId: String, consent: Boolean, playTestEmail: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (status, response) = call(
            "/rest/v1/rpc/betalink_set_group_email_consent", "POST", token,
            JSONObject().put("p_group_id", groupId).put("p_consent", consent).put("p_play_test_email", playTestEmail)
        )
        return if (status in 200..299) null else message(response)
    }

    fun groupSnapshot(groupId: String): Pair<GroupSnapshot?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/rpc/betalink_group_snapshot", "POST", token,
            JSONObject().put("p_group_id", groupId)
        )
        if (status !in 200..299) return null to message(body)
        val row = try { JSONObject(body) } catch (_: Exception) { null }
            ?: return null to "Grup test durumu okunamadı."
        val emailArray = row.optJSONArray("test_emails") ?: JSONArray()
        val emails = (0 until emailArray.length()).map { i ->
            val item = emailArray.getJSONObject(i)
            GroupTestEmail(item.optString("display_name"), item.optString("email"))
        }
        return GroupSnapshot(
            row.optString("tracking_status"),
            row.optInt("approved_members"),
            row.optInt("required_members", 15),
            row.optInt("elapsed_days"),
            row.optInt("required_days", 14),
            row.optString("started_at").takeIf { it.isNotBlank() && it != "null" },
            row.optBoolean("my_email_shared"),
            row.optString("my_play_test_email"),
            emails
        ) to null
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
            row.optInt("required_testers", 12),
            row.optString("started_at").takeIf { it.isNotBlank() && it != "null" }
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
        val existing = call("/rest/v1/task_completions?select=id&task_id=eq.$taskId&tester_id=eq.$userId", "GET", token)
        if (existing.first !in 200..299) return message(existing.second)
        val rows = JSONArray(existing.second)
        val body = JSONObject().put("status", "submitted").put("notes", note)
            .put("submitted_at", utcNow()).put("reviewed_at", JSONObject.NULL)
        val (status, response) = if (rows.length() > 0) {
            val id = rows.getJSONObject(0).getString("id")
            call("/rest/v1/task_completions?id=eq.$id", "PATCH", token, body)
        } else {
            body.put("task_id", taskId).put("tester_id", userId)
            call("/rest/v1/task_completions", "POST", token, body)
        }
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





    fun reportUser(targetUserId: String, campaignId: String, reason: String, details: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val reporterId = JSONObject(user.second).optString("id")
        val body = JSONObject().put("reporter_id", reporterId)
            .put("target_user_id", targetUserId).put("campaign_id", campaignId)
            .put("reason", reason).put("details", details)
        val (status, response) = call("/rest/v1/reports", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun blockUser(targetUserId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val blockerId = JSONObject(user.second).optString("id")
        val body = JSONObject().put("blocker_id", blockerId).put("blocked_id", targetUserId)
        val (status, response) = call("/rest/v1/blocked_users", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun unblockUser(targetUserId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val blockerId = JSONObject(user.second).optString("id")
        val (status, response) = call("/rest/v1/blocked_users?blocker_id=eq.$blockerId&blocked_id=eq.$targetUserId", "DELETE", token)
        return if (status in 200..299) null else message(response)
    }

    fun myReputationScore(): Pair<Int, String?> {
        val token = sessions.read()?.first ?: return 0 to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return 0 to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call("/rest/v1/reputation_events?select=points&user_id=eq.$userId", "GET", token)
        if (status !in 200..299) return 0 to message(body)
        val array = JSONArray(body)
        var total = 0
        for (i in 0 until array.length()) total += array.getJSONObject(i).optInt("points")
        return total to null
    }


    fun walletSnapshot(): Pair<BetaWallet?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("/rest/v1/rpc/betalink_wallet_snapshot", "POST", token, JSONObject())
        if (status !in 200..299) return null to message(body)
        val row = try { JSONArray(body).optJSONObject(0) } catch (_: Exception) { null }
            ?: return null to "BetaPuan bakiyesi okunamadı."
        return BetaWallet(row.optInt("balance"), row.optInt("lifetime_earned"), row.optInt("completed_support_tests")) to null
    }

    fun listNotifications(): Pair<List<BetalinkNotification>, String?> {
        val token = sessions.read()?.first ?: return emptyList<BetalinkNotification>() to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/notifications?select=id,title,body,read_at,created_at&order=created_at.desc&limit=50",
            "GET", token
        )
        if (status !in 200..299) return emptyList<BetalinkNotification>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            BetalinkNotification(row.getString("id"), row.optString("title"), row.optString("body"), !row.isNull("read_at"), row.optString("created_at"))
        } to null
    }

    fun markNotificationRead(id: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject().put("read_at", utcNow())
        val (status, response) = call("/rest/v1/notifications?id=eq.$id", "PATCH", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun taskSubmissions(campaignId: String): Pair<List<TaskSubmission>, String?> {
        val token = sessions.read()?.first ?: return emptyList<TaskSubmission>() to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/task_completions?select=id,tester_id,status,notes,campaign_tasks!inner(title,campaign_id)&campaign_tasks.campaign_id=eq.$campaignId&order=submitted_at.desc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<TaskSubmission>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            val task = row.optJSONObject("campaign_tasks") ?: JSONObject()
            TaskSubmission(row.getString("id"), task.optString("title"), row.getString("tester_id"), row.optString("status"), row.optString("notes"))
        } to null
    }

    fun reviewTaskSubmission(id: String, approved: Boolean): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject().put("status", if (approved) "approved" else "needs_changes")
            .put("reviewed_at", utcNow())
        val (status, response) = call("/rest/v1/task_completions?id=eq.$id", "PATCH", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun feedbackForCampaign(campaignId: String): Pair<List<FeedbackItem>, String?> {
        val token = sessions.read()?.first ?: return emptyList<FeedbackItem>() to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/feedback?select=id,tester_id,rating,body,status,developer_reply&campaign_id=eq.$campaignId&order=created_at.desc",
            "GET", token
        )
        if (status !in 200..299) return emptyList<FeedbackItem>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            FeedbackItem(row.getString("id"), row.getString("tester_id"), if (row.isNull("rating")) null else row.optInt("rating"), row.optString("body"), row.optString("status"), row.optString("developer_reply").takeIf { it.isNotBlank() })
        } to null
    }

    fun replyToFeedback(id: String, reply: String, resolved: Boolean): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val body = JSONObject().put("developer_reply", reply.trim()).put("status", if (resolved) "resolved" else "open")
            .put("updated_at", utcNow())
        val (status, response) = call("/rest/v1/feedback?id=eq.$id", "PATCH", token, body)
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
        val body = JSONObject().put("status", statusValue).put("is_backup", statusValue == "waitlisted")
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

    fun campaignCheckinDates(campaignId: String): Pair<Set<String>, String?> {
        val token = sessions.read()?.first ?: return emptySet<String>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptySet<String>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call("/rest/v1/campaign_daily_checkins?select=checkin_date&campaign_id=eq.$campaignId&tester_id=eq.$userId&order=checkin_date.desc", "GET", token)
        if (status !in 200..299) return emptySet<String>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { array.getJSONObject(it).optString("checkin_date") }.toSet() to null
    }

    fun checkInToday(campaignId: String): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val today = utcToday()
        val existing = call("/rest/v1/campaign_daily_checkins?select=id&campaign_id=eq.$campaignId&tester_id=eq.$userId&checkin_date=eq.$today", "GET", token)
        if (existing.first !in 200..299) return message(existing.second)
        if (JSONArray(existing.second).length() > 0) return "Bugünkü test bildirimin zaten kayıtlı."
        val body = JSONObject().put("campaign_id", campaignId).put("tester_id", userId)
            .put("note", "Uygulama içi günlük test onayı")
        val (status, response) = call("/rest/v1/campaign_daily_checkins", "POST", token, body)
        return if (status in 200..299) null else message(response)
    }

    fun groupCheckinDates(groupId: String): Pair<Set<String>, String?> {
        val token = sessions.read()?.first ?: return emptySet<String>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptySet<String>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call("/rest/v1/group_daily_checkins?select=checkin_date&group_id=eq.$groupId&user_id=eq.$userId&order=checkin_date.desc", "GET", token)
        if (status !in 200..299) return emptySet<String>() to message(body)
        val array = JSONArray(body)
        return (0 until array.length()).map { array.getJSONObject(it).optString("checkin_date") }.toSet() to null
    }

    fun checkInGroupToday(groupId: String): Pair<Boolean?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call("/rest/v1/rpc/betalink_group_daily_check_in", "POST", token,
            JSONObject().put("p_group_id", groupId).put("p_note", "Uygulama içi günlük grup testi bildirimi"))
        if (status !in 200..299) return null to message(body)
        return body.trim().equals("true", ignoreCase = true) to null
    }

    private fun utcToday(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

    fun myCampaigns(): Pair<List<Campaign>, String?> {
        val token = sessions.read()?.first ?: return emptyList<Campaign>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<Campaign>() to "Oturum doğrulanamadı."
        val id = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/campaign_members?select=status,joined_at,campaigns(id,app_name,description,category,play_opt_in_url,target_testers,status,minimum_android_version)&tester_id=eq.$id&order=joined_at.desc",
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
                array.getJSONObject(i).optString("status"),
                array.getJSONObject(i).optString("joined_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        return result to null
    }

    fun listPeople(): Pair<List<DirectoryPerson>, String?> {
        val token = sessions.read()?.first ?: return emptyList<DirectoryPerson>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<DirectoryPerson>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/profiles?select=user_id,display_name,allow_group_invites&user_id=neq.$userId&order=created_at.desc&limit=500",
            "GET", token
        )
        if (status !in 200..299) return emptyList<DirectoryPerson>() to message(body)
        val rows = JSONArray(body)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            DirectoryPerson(row.getString("user_id"), row.optString("display_name").ifBlank { "Betalink kullanıcısı" }, row.optBoolean("allow_group_invites", true))
        } to null
    }

    fun listIncomingGroupInvitations(): Pair<List<GroupInvitation>, String?> {
        val token = sessions.read()?.first ?: return emptyList<GroupInvitation>() to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return emptyList<GroupInvitation>() to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val path = "/rest/v1/group_invitations?select=id,group_id,inviter_id,custom_message,invite_token,created_at,tester_groups(name,description)&invitee_id=eq.$userId&status=eq.pending&order=created_at.desc&limit=100"
        val (status, body) = call(path, "GET", token)
        if (status !in 200..299) return emptyList<GroupInvitation>() to message(body)
        val rows = JSONArray(body)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val group = row.optJSONObject("tester_groups") ?: JSONObject()
            GroupInvitation(
                row.getString("id"), row.getString("group_id"), group.optString("name", "Tester grubu"),
                group.optString("description"), row.optString("inviter_id"), row.optString("custom_message"),
                row.optString("invite_token"), row.optString("created_at")
            )
        } to null
    }

    fun sendGroupInvitation(groupId: String, inviteeId: String, messageText: String): Pair<String?, String?> {
        val token = sessions.read()?.first ?: return null to "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/rpc/betalink_send_group_invitation", "POST", token,
            JSONObject().put("p_group_id", groupId).put("p_invitee_id", inviteeId).put("p_message", messageText.trim())
        )
        if (status !in 200..299) return null to message(body)
        return try { JSONObject(body).optString("invite_token").takeIf { it.isNotBlank() } to null }
        catch (_: Exception) { null to "Davet oluşturuldu ancak bağlantı bilgisi okunamadı." }
    }

    fun respondGroupInvitation(invitationId: String, accept: Boolean): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val (status, body) = call(
            "/rest/v1/rpc/betalink_respond_group_invitation", "POST", token,
            JSONObject().put("p_invitation_id", invitationId).put("p_accept", accept)
        )
        return if (status in 200..299) null else message(body)
    }

    fun groupInvitePreference(): Pair<Boolean, String?> {
        val token = sessions.read()?.first ?: return true to "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return true to "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call("/rest/v1/profiles?select=allow_group_invites&user_id=eq.$userId", "GET", token)
        if (status !in 200..299) return true to message(body)
        val rows = JSONArray(body)
        return (if (rows.length() > 0) rows.getJSONObject(0).optBoolean("allow_group_invites", true) else true) to null
    }

    fun setGroupInvitePreference(enabled: Boolean): String? {
        val token = sessions.read()?.first ?: return "Oturum bulunamadı."
        val user = call("/auth/v1/user", "GET", token)
        if (user.first !in 200..299) return "Oturum doğrulanamadı."
        val userId = JSONObject(user.second).optString("id")
        val (status, body) = call(
            "/rest/v1/profiles?user_id=eq.$userId", "PATCH", token,
            JSONObject().put("allow_group_invites", enabled)
        )
        return if (status in 200..299) null else message(body)
    }

    private fun utcNow(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

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
