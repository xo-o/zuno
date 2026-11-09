package com.talkie.ai.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.talkie.ai.BuildConfig
import com.talkie.ai.MainActivity
import com.talkie.ai.R
import com.talkie.ai.auth.SessionTokenStore
import com.talkie.ai.data.ZunoApi
import com.talkie.ai.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.TimeZone

/** Firebase Cloud Messaging: token registration and the notification channels pushes are shown on. */
object Push {
    const val EXTRA_SCHEDULE_ID = "com.talkie.ai.extra.SCHEDULE_ID"
    const val EXTRA_TOPIC_ID = "com.talkie.ai.extra.TOPIC_ID"
    const val EXTRA_ROLEPLAY_ID = "com.talkie.ai.extra.ROLEPLAY_ID"
    const val EXTRA_START = "com.talkie.ai.extra.START"

    private const val TAG = "ZunoPush"
    private const val PREFS = "zuno_push"
    private const val KEY_TOKEN = "token"

    /** data.type → channel. "reminder" shares the local reminders channel. */
    enum class Channel(val id: String, val importance: Int) {
        Reminder("reminders", NotificationManager.IMPORTANCE_HIGH),
        Schedule("schedule", NotificationManager.IMPORTANCE_HIGH),
        Progress("progress", NotificationManager.IMPORTANCE_DEFAULT),
        News("news", NotificationManager.IMPORTANCE_LOW),
    }

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isAvailable: Boolean
        get() = BuildConfig.PUSH_ENABLED

    fun canNotify(context: Context): Boolean = Reminders.canNotify(context)

    private fun firebaseReady(context: Context): Boolean {
        if (!isAvailable) return false
        return FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }

    /** The current FCM token, or null when push isn't configured or Play services are missing. */
    suspend fun token(context: Context): String? {
        if (!firebaseReady(context)) return null
        return try {
            FirebaseMessaging.getInstance().token.await().also { remember(context, it) }
        } catch (e: Exception) {
            Log.w(TAG, "No FCM token: ${e.message}")
            null
        }
    }

    internal fun remember(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    private fun lastToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    /** Sends this device's token to the backend for the signed-in account. */
    suspend fun register(context: Context, appLanguage: String = Locale.getDefault().language) {
        val token = token(context) ?: return
        val api = ZunoApi(tokenProvider = SessionTokenStore(context)::read)
        runCatching { api.registerDevice(token, TimeZone.getDefault().id, appLanguage) }
            .onFailure { Log.w(TAG, "Device registration failed: ${it.message}") }
    }

    /** Sign-out: stop pushes for [sessionToken]'s account on this device (call before the session is revoked). */
    suspend fun unregister(context: Context, sessionToken: String) {
        val token = lastToken(context) ?: (if (firebaseReady(context)) runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() else null)
            ?: return
        runCatching { ZunoApi(tokenProvider = { sessionToken }).unregisterDevice(token, sessionToken) }
            .onFailure { Log.w(TAG, "Device unregister failed: ${it.message}") }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        Channel.entries.forEach { channel ->
            if (manager.getNotificationChannel(channel.id) != null) return@forEach
            val (name, description) = when (channel) {
                Channel.Reminder -> context.getString(R.string.push_channel_reminders) to context.getString(R.string.push_channel_reminders_desc)
                Channel.Schedule -> context.getString(R.string.push_channel_schedule) to context.getString(R.string.push_channel_schedule_desc)
                Channel.Progress -> context.getString(R.string.push_channel_progress) to context.getString(R.string.push_channel_progress_desc)
                Channel.News -> context.getString(R.string.push_channel_news) to context.getString(R.string.push_channel_news_desc)
            }
            manager.createNotificationChannel(NotificationChannel(channel.id, name, channel.importance).apply { this.description = description })
        }
    }

    /** Builds and posts the notification for an FCM data message. */
    fun show(context: Context, data: Map<String, String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) return
        ensureChannels(context)
        val type = data["type"] ?: "news"
        val channel = when (type) {
            "reminder" -> Channel.Reminder
            "schedule" -> Channel.Schedule
            "progress" -> Channel.Progress
            else -> Channel.News
        }
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        val conversationId = data["conversationId"]
        val scheduleId = data["scheduleId"]
        val topicId = data["topicId"]
        val key = data["reminderId"] ?: scheduleId ?: conversationId ?: topicId ?: type
        val notificationId = key.hashCode()

        val open = Intent(context, MainActivity::class.java)
            .setAction("com.talkie.ai.PUSH.$type.$key.${System.currentTimeMillis()}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        conversationId?.let { open.putExtra(Reminders.EXTRA_CONVERSATION_ID, it) }
        scheduleId?.let {
            open.putExtra(EXTRA_SCHEDULE_ID, it)
            open.putExtra(EXTRA_START, true)
        }
        if (scheduleId == null) topicId?.let { open.putExtra(EXTRA_TOPIC_ID, it) }

        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.zuno_logo_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(if (channel.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (channel == Channel.News) NotificationCompat.CATEGORY_RECOMMENDATION else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(PendingIntent.getActivity(context, notificationId, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        if (scheduleId != null) {
            val snooze = Intent(context, ScheduleActionReceiver::class.java)
                .setAction(ScheduleActionReceiver.ACTION_SNOOZE)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .putExtra(ScheduleActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            builder.addAction(
                0,
                context.getString(R.string.push_action_snooze),
                PendingIntent.getBroadcast(context, notificationId, snooze, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and posting.
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ZunoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Push.remember(applicationContext, token)
        if (SessionTokenStore(applicationContext).read() != null) {
            Push.scope.launch { Push.register(applicationContext) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data.toMutableMap()
        // Fall back to the notification payload if a console message was sent.
        message.notification?.let { n ->
            n.title?.let { data.putIfAbsent("title", it) }
            n.body?.let { data.putIfAbsent("body", it) }
        }
        if (data["title"].isNullOrBlank() && data["body"].isNullOrBlank()) return
        Push.show(applicationContext, data)
    }
}

/** "Snooze" on a scheduled-session notification: remind again in 10 minutes. */
class ScheduleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return
        val scheduleId = intent.getStringExtra(Push.EXTRA_SCHEDULE_ID) ?: return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, scheduleId.hashCode()))
        val pending = goAsync()
        Push.scope.launch {
            try {
                ZunoApi(tokenProvider = SessionTokenStore(context.applicationContext)::read).snoozeSchedule(scheduleId, 10)
            } catch (e: Exception) {
                Log.w("ZunoPush", "Snooze failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.talkie.ai.SCHEDULE_SNOOZE"
        const val EXTRA_NOTIFICATION_ID = "com.talkie.ai.extra.NOTIFICATION_ID"
    }
}
