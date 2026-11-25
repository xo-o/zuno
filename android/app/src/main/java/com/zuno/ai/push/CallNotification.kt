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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zuno.ai.MainActivity
import com.zuno.ai.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class OngoingCallAction { ToggleMute, ToggleSpeaker, End }

/**
 * `16 · Ongoing Call & System` (`7173:11859`..`7175:12347`):
 * Posts/updates a persistent ongoing-call system notification (visible on both the lock screen
 * and notification shade) while a LiveKit voice, 1:1 peer, or live-room call is active.
 */
object CallNotification {
    private const val LEGACY_CHANNEL_ID = "ongoing_call"
    private const val CHANNEL_ID = "ongoing_call_v2"
    private const val NOTIFICATION_ID = 7173

    private val _actions = MutableSharedFlow<OngoingCallAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<OngoingCallAction> = _actions.asSharedFlow()

    internal fun emitAction(action: OngoingCallAction) {
        _actions.tryEmit(action)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Remove legacy IMPORTANCE_LOW channel so upgraded installs show on the lock screen.
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null &&
            existing.importance >= NotificationManager.IMPORTANCE_DEFAULT &&
            existing.lockscreenVisibility == Notification.VISIBILITY_PUBLIC
        ) return
        val name = context.getString(R.string.push_channel_ongoing_call)
        val desc = context.getString(R.string.push_channel_ongoing_call_desc)
        manager.createNotificationChannel(
            // IMPORTANCE_DEFAULT (with no sound/vibration) ensures Android & OEM lock screens
            // do not hide the ongoing-call controls as a silent/ambient notification.
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = desc
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun show(
        context: Context,
        title: String,
        subtitle: String,
        statusLabel: String? = null,
        isMuted: Boolean,
        isSpeakerOn: Boolean = true,
        startedAtMillis: Long?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val openIntent = Intent(appContext, MainActivity::class.java)
            .setAction("com.zuno.ai.RETURN_TO_CALL")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val muteIntent = Intent(appContext, OngoingCallActionReceiver::class.java)
            .setAction(OngoingCallActionReceiver.ACTION_TOGGLE_MUTE)
        val mutePending = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_ID + 1,
            muteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val speakerIntent = Intent(appContext, OngoingCallActionReceiver::class.java)
            .setAction(OngoingCallActionReceiver.ACTION_TOGGLE_SPEAKER)
        val speakerPending = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_ID + 3,
            speakerIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val endIntent = Intent(appContext, OngoingCallActionReceiver::class.java)
            .setAction(OngoingCallActionReceiver.ACTION_END)
        val endPending = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_ID + 2,
            endIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.zuno_logo_mark)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .addAction(
                0,
                context.getString(if (isMuted) R.string.voice_unmute else R.string.voice_mute),
                mutePending,
            )
            .addAction(
                0,
                context.getString(if (isSpeakerOn) R.string.ongoing_call_speaker else R.string.ongoing_call_earpiece),
                speakerPending,
            )
            .addAction(
                0,
                context.getString(R.string.ongoing_call_end),
                endPending,
            )

        if (!statusLabel.isNullOrBlank()) {
            builder.setSubText(statusLabel)
        }
        if (startedAtMillis != null) {
            builder.setWhen(startedAtMillis).setUsesChronometer(true).setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

class OngoingCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_MUTE -> CallNotification.emitAction(OngoingCallAction.ToggleMute)
            ACTION_TOGGLE_SPEAKER -> CallNotification.emitAction(OngoingCallAction.ToggleSpeaker)
            ACTION_END -> {
                CallNotification.cancel(context)
                CallNotification.emitAction(OngoingCallAction.End)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_MUTE = "com.zuno.ai.CALL_TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.zuno.ai.CALL_TOGGLE_SPEAKER"
        const val ACTION_END = "com.zuno.ai.CALL_END"
    }
}
