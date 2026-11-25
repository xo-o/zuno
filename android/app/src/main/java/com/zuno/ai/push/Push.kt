package com.zuno.ai.push

import android.Manifest
import android.app.Notification
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
import com.zuno.ai.BuildConfig
import com.zuno.ai.MainActivity
import com.zuno.ai.R
import com.zuno.ai.auth.SessionTokenStore
import com.zuno.ai.data.ZunoApi
import com.zuno.ai.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.TimeZone

sealed interface PeerPushEvent {
    val peerConversationId: String

    data class Message(override val peerConversationId: String, val messageId: String?) : PeerPushEvent
    data class CallInvite(override val peerConversationId: String, val callId: String?) : PeerPushEvent
}

/** Firebase Cloud Messaging: token registration and the notification channels pushes are shown on. */
object Push {
    const val EXTRA_SCHEDULE_ID = "com.zuno.ai.extra.SCHEDULE_ID"
    const val EXTRA_TOPIC_ID = "com.zuno.ai.extra.TOPIC_ID"
    const val EXTRA_ROLEPLAY_ID = "com.zuno.ai.extra.ROLEPLAY_ID"
    const val EXTRA_START = "com.zuno.ai.extra.START"
    const val EXTRA_PEER_CONVERSATION_ID = "com.zuno.ai.extra.PEER_CONVERSATION_ID"
    const val EXTRA_PARTNER_ID = "com.zuno.ai.extra.PARTNER_ID"
    const val EXTRA_CALL_ID = "com.zuno.ai.extra.CALL_ID"
    const val EXTRA_JOIN_CALL = "com.zuno.ai.extra.JOIN_CALL"

    private const val TAG = "ZunoPush"
    private const val PREFS = "zuno_push"
    private const val KEY_TOKEN = "token"

    /** data.type → channel. "reminder" shares the local reminders channel. */
    enum class Channel(val id: String, val importance: Int) {
        Reminder("reminders", NotificationManager.IMPORTANCE_HIGH),
        Schedule("schedule", NotificationManager.IMPORTANCE_HIGH),
        Messages("messages", NotificationManager.IMPORTANCE_HIGH),
        CallInvites("call_invites", NotificationManager.IMPORTANCE_HIGH),
        Progress("progress", NotificationManager.IMPORTANCE_DEFAULT),
        News("news_v2", NotificationManager.IMPORTANCE_DEFAULT),
    }

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isAppInForeground: Boolean = false

    @Volatile
    private var activePeerConversationId: String? = null

    private val _peerEvents = MutableSharedFlow<PeerPushEvent>(extraBufferCapacity = 32)
    val peerEvents: SharedFlow<PeerPushEvent> = _peerEvents.asSharedFlow()

    fun setAppForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun setActivePeerConversation(peerConversationId: String?) {
        activePeerConversationId = peerConversationId
    }

    /** True only when the app is visible in the foreground AND the user is viewing [peerConversationId]. */
    fun isInPeerChat(peerConversationId: String?): Boolean =
        !peerConversationId.isNullOrBlank() && isAppInForeground && activePeerConversationId == peerConversationId

    internal fun emitPeerEvent(event: PeerPushEvent) {
        _peerEvents.tryEmit(event)
    }

    fun clearPeerNotifications(context: Context, peerConversationId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val expectedTag = peerTag(peerConversationId)
        manager.activeNotifications
            .filter { it.tag == expectedTag }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun peerTag(peerConversationId: String): String = "peer:$peerConversationId"

    internal fun effectiveType(rawType: String?, peerConversationId: String?, callId: String?): String = when {
        rawType == "peer_message" -> "peer_message"
        rawType == "peer_invite" -> "peer_invite"
        rawType == "reminder" && !peerConversationId.isNullOrBlank() -> "peer_message"
        rawType == "schedule" && !peerConversationId.isNullOrBlank() && !callId.isNullOrBlank() -> "peer_invite"
        else -> rawType ?: "news"
    }

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
        runCatching { manager.deleteNotificationChannel("news") }
        Channel.entries.forEach { channel ->
            if (manager.getNotificationChannel(channel.id) != null) return@forEach
            val (name, description) = when (channel) {
                Channel.Reminder -> context.getString(R.string.push_channel_reminders) to context.getString(R.string.push_channel_reminders_desc)
                Channel.Schedule -> context.getString(R.string.push_channel_schedule) to context.getString(R.string.push_channel_schedule_desc)
                Channel.Messages -> context.getString(R.string.push_channel_messages) to context.getString(R.string.push_channel_messages_desc)
                Channel.CallInvites -> context.getString(R.string.push_channel_call_invites) to context.getString(R.string.push_channel_call_invites_desc)
                Channel.Progress -> context.getString(R.string.push_channel_progress) to context.getString(R.string.push_channel_progress_desc)
                Channel.News -> context.getString(R.string.push_channel_news) to context.getString(R.string.push_channel_news_desc)
            }
            manager.createNotificationChannel(
                NotificationChannel(channel.id, name, channel.importance).apply {
                    this.description = description
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    if (channel == Channel.News) {
                        setSound(null, null)
                        enableVibration(false)
                    }
                },
            )
        }
    }

    /** Builds and posts the notification for an FCM data message. */
    fun show(context: Context, data: Map<String, String>) {
        val peerConversationId = data["peerConversationId"]
        val callId = data["callId"]
        val type = effectiveType(data["type"], peerConversationId, callId)
        val isPeerPush = type == "peer_message" || type == "peer_invite"
        if (isPeerPush && isInPeerChat(peerConversationId)) {
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) return
        ensureChannels(context)

        val channel = when (type) {
            "reminder" -> Channel.Reminder
            "schedule" -> Channel.Schedule
            "peer_message" -> Channel.Messages
            "peer_invite" -> Channel.CallInvites
            "progress" -> Channel.Progress
            else -> Channel.News
        }
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        val conversationId = data["conversationId"]
        val partnerId = data["partnerId"]
        val scheduleId = data["scheduleId"]
        val topicId = data["topicId"]
        val key = when (type) {
            "peer_message" -> "peer_msg:${peerConversationId ?: type}"
            "peer_invite" -> "peer_call:${callId ?: peerConversationId ?: type}"
            else -> data["reminderId"] ?: callId ?: peerConversationId ?: scheduleId ?: conversationId ?: topicId ?: type
        }
        val notificationId = key.hashCode()
        val notificationTag = if (isPeerPush && !peerConversationId.isNullOrBlank()) peerTag(peerConversationId) else null

        val open = Intent(context, MainActivity::class.java)
            .setAction("com.zuno.ai.PUSH.$type.$key.${System.currentTimeMillis()}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        peerConversationId?.let { open.putExtra(EXTRA_PEER_CONVERSATION_ID, it) }
        partnerId?.let { open.putExtra(EXTRA_PARTNER_ID, it) }
        callId?.let { open.putExtra(EXTRA_CALL_ID, it) }
        conversationId?.let { open.putExtra(Reminders.EXTRA_CONVERSATION_ID, it) }
        scheduleId?.let {
            open.putExtra(EXTRA_SCHEDULE_ID, it)
            open.putExtra(EXTRA_START, true)
        }
        if (scheduleId == null) topicId?.let { open.putExtra(EXTRA_TOPIC_ID, it) }

        val category = when (channel) {
            Channel.Messages -> NotificationCompat.CATEGORY_MESSAGE
            Channel.CallInvites -> NotificationCompat.CATEGORY_CALL
            Channel.News -> NotificationCompat.CATEGORY_RECOMMENDATION
            else -> NotificationCompat.CATEGORY_REMINDER
        }

        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.zuno_logo_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(if (channel.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(category)
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

        if (type == "peer_invite" && !callId.isNullOrBlank() && !peerConversationId.isNullOrBlank()) {
            val decline = Intent(context, PeerCallActionReceiver::class.java)
                .setAction(PeerCallActionReceiver.ACTION_DECLINE)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(PeerCallActionReceiver.EXTRA_NOTIFICATION_TAG, notificationTag)
                .putExtra(PeerCallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            builder.addAction(
                0,
                context.getString(R.string.push_action_decline),
                PendingIntent.getBroadcast(context, notificationId * 31 + 1, decline, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )

            val join = Intent(context, MainActivity::class.java)
                .setAction("com.zuno.ai.PUSH.JOIN_CALL.$callId.${System.currentTimeMillis()}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PEER_CONVERSATION_ID, peerConversationId)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_JOIN_CALL, true)
            partnerId?.let { join.putExtra(EXTRA_PARTNER_ID, it) }
            builder.addAction(
                0,
                context.getString(R.string.push_action_join),
                PendingIntent.getActivity(context, notificationId * 31 + 2, join, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationTag, notificationId, builder.build())
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
        val peerConversationId = data["peerConversationId"]
        when (Push.effectiveType(data["type"], peerConversationId, data["callId"])) {
            "peer_message" -> if (!peerConversationId.isNullOrBlank()) {
                Push.emitPeerEvent(PeerPushEvent.Message(peerConversationId, data["messageId"]))
            }
            "peer_invite" -> if (!peerConversationId.isNullOrBlank()) {
                Push.emitPeerEvent(PeerPushEvent.CallInvite(peerConversationId, data["callId"]))
            }
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
        const val ACTION_SNOOZE = "com.zuno.ai.SCHEDULE_SNOOZE"
        const val EXTRA_NOTIFICATION_ID = "com.zuno.ai.extra.NOTIFICATION_ID"
    }
}

/** "Decline" on an incoming 1:1 call invite notification. */
class PeerCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE) return
        val callId = intent.getStringExtra(Push.EXTRA_CALL_ID) ?: return
        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, "peer_call:$callId".hashCode())
        NotificationManagerCompat.from(context).cancel(tag, notificationId)
        val pending = goAsync()
        Push.scope.launch {
            try {
                ZunoApi(tokenProvider = SessionTokenStore(context.applicationContext)::read).declinePeerCall(callId)
            } catch (e: Exception) {
                Log.w("ZunoPush", "Decline peer call failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.zuno.ai.PEER_CALL_DECLINE"
        const val EXTRA_NOTIFICATION_TAG = "com.zuno.ai.extra.NOTIFICATION_TAG"
        const val EXTRA_NOTIFICATION_ID = "com.zuno.ai.extra.NOTIFICATION_ID"
    }
}
