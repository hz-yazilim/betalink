package com.hzyazilim.betalink.core.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hzyazilim.betalink.MainActivity
import org.json.JSONObject

object TestReminderScheduler {
    private const val PREFS = "betalink_test_reminders"
    private const val ACTION_REMINDER = "com.hzyazilim.betalink.TEST_REMINDER"

    fun schedule(context: Context, campaignId: String, appName: String, triggerAtMillis: Long): String? {
        if (campaignId.isBlank() || triggerAtMillis <= System.currentTimeMillis()) {
            return "Hatırlatıcı için gelecekte bir tarih ve saat seç."
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(
            campaignId,
            JSONObject().put("at", triggerAtMillis).put("name", appName).toString()
        ).apply()
        scheduleAlarm(context, campaignId, appName, triggerAtMillis)
        return null
    }

    internal fun scheduleStored(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.forEach { (campaignId, raw) ->
            val value = raw as? String ?: return@forEach
            try {
                val item = JSONObject(value)
                val at = item.optLong("at")
                if (at > System.currentTimeMillis()) {
                    scheduleAlarm(context, campaignId, item.optString("name"), at)
                } else {
                    prefs.edit().remove(campaignId).apply()
                }
            } catch (_: Exception) {
                prefs.edit().remove(campaignId).apply()
            }
        }
    }

    internal fun consume(context: Context, campaignId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(campaignId).apply()
    }

    private fun scheduleAlarm(context: Context, campaignId: String, appName: String, at: Long) {
        val intent = Intent(context, TestReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("betalink://reminder/$campaignId")
            putExtra("campaign_id", campaignId)
            putExtra("app_name", appName)
        }
        val pending = PendingIntent.getBroadcast(
            context, campaignId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    internal fun isReminderAction(intent: Intent?): Boolean = intent?.action == ACTION_REMINDER
}

class TestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            TestReminderScheduler.scheduleStored(context)
            return
        }
        if (!TestReminderScheduler.isReminderAction(intent)) return
        val campaignId = intent?.getStringExtra("campaign_id").orEmpty()
        val appName = intent?.getStringExtra("app_name").orEmpty().ifBlank { "test kampanyan" }
        TestReminderScheduler.consume(context, campaignId)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("betalink_updates", "Betalink bildirimleri", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, 7001, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "betalink_updates")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Betalink · Test hatırlatıcısı")
            .setContentText("$appName testini kontrol et.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$appName testine ayırdığın zamanı kontrol et; görevlerini ve günlük test bildirimini unutma."))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(campaignId.hashCode(), notification)
    }
}
