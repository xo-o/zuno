package com.zuno.ai.push

import android.content.Intent
import com.zuno.ai.reminders.Reminders

/** Where a notification tap should take the learner. */
sealed interface DeepLink {
    data class Chat(val conversationId: String) : DeepLink
    /** A scheduled session: open (and start) its lesson or roleplay. */
    data class Schedule(val scheduleId: String) : DeepLink
    data class Topic(val topicId: String) : DeepLink
    data class PeerChat(
        val peerConversationId: String,
        val partnerId: String,
        val callId: String? = null,
        val joinCall: Boolean = false,
    ) : DeepLink

    companion object {
        fun from(intent: Intent?): DeepLink? {
            intent ?: return null
            intent.getStringExtra(Push.EXTRA_PEER_CONVERSATION_ID)?.let { convId ->
                val partnerId = intent.getStringExtra(Push.EXTRA_PARTNER_ID).orEmpty()
                val callId = intent.getStringExtra(Push.EXTRA_CALL_ID)
                val joinCall = intent.getBooleanExtra(Push.EXTRA_JOIN_CALL, false)
                return PeerChat(convId, partnerId, callId, joinCall)
            }
            intent.getStringExtra(Push.EXTRA_SCHEDULE_ID)?.let { return Schedule(it) }
            intent.getStringExtra(Reminders.EXTRA_CONVERSATION_ID)?.let { return Chat(it) }
            intent.getStringExtra(Push.EXTRA_TOPIC_ID)?.let { return Topic(it) }
            return null
        }
    }
}
