package com.zuno.ai.push

import android.content.Intent
import com.zuno.ai.reminders.Reminders

/** Where a notification tap or App Link should take the learner. */
sealed interface DeepLink {
    data class Chat(val conversationId: String) : DeepLink
    /** A scheduled session: open (and start) its lesson or roleplay. */
    data class Schedule(val scheduleId: String) : DeepLink
    data class Topic(val topicId: String) : DeepLink
    data class TopicPreview(val idOrToken: String) : DeepLink
    data class RoleplayPreview(val idOrToken: String) : DeepLink
    data class PeerChat(
        val peerConversationId: String,
        val partnerId: String,
        val callId: String? = null,
        val joinCall: Boolean = false,
    ) : DeepLink

    companion object {
        fun from(intent: Intent?): DeepLink? {
            intent ?: return null
            intent.data?.let { uri ->
                val scheme = uri.scheme?.lowercase()
                val host = uri.host?.lowercase()
                val segments = uri.pathSegments.orEmpty()
                if (scheme == "zuno" && host == "topic-preview" && segments.isNotEmpty()) {
                    return TopicPreview(segments.first())
                }
                if (scheme == "zuno" && host == "roleplay-preview" && segments.isNotEmpty()) {
                    return RoleplayPreview(segments.first())
                }
                if ((scheme == "https" || scheme == "http") && segments.size >= 2) {
                    if (segments[0] == "t" && segments[1].isNotBlank()) return TopicPreview(segments[1])
                    if (segments[0] == "r" && segments[1].isNotBlank()) return RoleplayPreview(segments[1])
                }
            }
            intent.getStringExtra(Push.EXTRA_PEER_CONVERSATION_ID)?.let { convId ->
                val partnerId = intent.getStringExtra(Push.EXTRA_PARTNER_ID).orEmpty()
                val callId = intent.getStringExtra(Push.EXTRA_CALL_ID)
                val joinCall = intent.getBooleanExtra(Push.EXTRA_JOIN_CALL, false)
                return PeerChat(convId, partnerId, callId, joinCall)
            }
            intent.getStringExtra(Push.EXTRA_SCHEDULE_ID)?.let { return Schedule(it) }
            intent.getStringExtra(Push.EXTRA_TOPIC_ID)?.let { return Topic(it) }
            val conversationId = intent.getStringExtra(Reminders.EXTRA_CONVERSATION_ID)
            return conversationId?.let(::Chat)
        }
    }
}
