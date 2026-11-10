package com.zuno.ai.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.zuno.ai.MainActivity
import com.zuno.ai.R
import org.json.JSONObject

/** "Remind me to continue": a local notification that reopens [conversationId] at [atMillis]. */
data class Reminder(
    val conversationId: String,
    val title: String,
    /** The tutor's last line, quoted in the notification. */
    val lastLine: String,
    val atMillis: Long,
)

/** Schedules on-device reminders with AlarmManager and posts them as notifications. */
object Reminders {
    const val EXTRA_CONVERSATION_ID = "com.zuno.ai.extra.CONVERSATION_ID"
    private const val CHANNEL_ID = "reminders"
    private const val PREFS = "zuno_reminders"
    private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

    fun schedule(context: Context, reminder: Reminder) {
        prefs(context).edit { putString(reminder.conversationId, reminder.toJson()) }
        arm(context, reminder)
    }

    fun cancel(context: Context, conversationId: String) {
        prefs(context).edit { remove(conversationId) }
        alarmManager(context).cancel(alarmIntent(context, conversationId))
    }

    fun clearAll(context: Context) {
        all(context).forEach { cancel(context, it.conversationId) }
    }

    /** Alarms don't survive a reboot or app update, so they are re-armed from the saved list. */
    fun rescheduleAll(context: Context) {
        all(context).forEach { arm(context, it) }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun fire(context: Context, conversationId: String) {
        val reminder = prefs(context).getString(conversationId, null)?.let(::fromJson) ?: return
        prefs(context).edit { remove(conversationId) }
        show(context, reminder)
    }

    private fun show(context: Context, reminder: Reminder) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val body = if (reminder.lastLine.isNotBlank()) {
            "You left off at: “${quote(reminder.lastLine)}” Tap to jump back in."
        } else {
            "Tap to jump back in."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.zuno_logo_mark)
            .setContentTitle("Continue “${reminder.title}”")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openChatIntent(context, reminder.conversationId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.conversationId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked after the check.
        }
    }

    /** The tutor's last line, shortened at a word boundary. */
    private fun quote(line: String, max: Int = 90): String {
        val text = line.trim()
        if (text.length <= max) return text
        val cut = text.take(max).substringBeforeLast(' ').ifBlank { text.take(max) }
        return cut.trimEnd(',', ';', ':', ' ') + "…"
    }

    private fun arm(context: Context, reminder: Reminder) {
        val alarms = alarmManager(context)
        val intent = alarmIntent(context, reminder.conversationId)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, intent)
        } else {
            // Without exact-alarm access, a short window keeps "Tonight · 9:00 PM" close to 9 PM
            // (inexact alarms can drift by most of an hour).
            alarms.setWindow(AlarmManager.RTC_WAKEUP, reminder.atMillis, INEXACT_WINDOW_MS, intent)
        }
    }

    private fun alarmIntent(context: Context, conversationId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        conversationId.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction("com.zuno.ai.REMIND.$conversationId")
            .putExtra(EXTRA_CONVERSATION_ID, conversationId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openChatIntent(context: Context, conversationId: String): PendingIntent = PendingIntent.getActivity(
        context,
        conversationId.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction("com.zuno.ai.OPEN_CHAT.$conversationId")
            .putExtra(EXTRA_CONVERSATION_ID, conversationId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Practice reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nudges to continue a conversation you asked to be reminded about"
            },
        )
    }

    private fun all(context: Context): List<Reminder> =
        prefs(context).all.values.mapNotNull { (it as? String)?.let(::fromJson) }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun Reminder.toJson() = JSONObject()
        .put("conversationId", conversationId)
        .put("title", title)
        .put("lastLine", lastLine)
        .put("atMillis", atMillis)
        .toString()

    private fun fromJson(raw: String): Reminder? = runCatching {
        val json = JSONObject(raw)
        Reminder(json.getString("conversationId"), json.optString("title"), json.optString("lastLine"), json.getLong("atMillis"))
    }.getOrNull()
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(Reminders.EXTRA_CONVERSATION_ID)?.let { Reminders.fire(context, it) }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminders.rescheduleAll(context)
        }
    }
}
