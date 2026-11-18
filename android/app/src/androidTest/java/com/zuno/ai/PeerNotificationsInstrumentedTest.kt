package com.zuno.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zuno.ai.push.DeepLink
import com.zuno.ai.push.Push
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerNotificationsInstrumentedTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
        Push.setAppForeground(false)
        Push.setActivePeerConversation(null)
        Push.clearPeerNotifications(context, "conv-test-1")
        Push.clearPeerNotifications(context, "conv-test-2")
        Push.ensureChannels(context)
    }

    @After
    fun tearDown() {
        Push.setAppForeground(false)
        Push.setActivePeerConversation(null)
        Push.clearPeerNotifications(context, "conv-test-1")
        Push.clearPeerNotifications(context, "conv-test-2")
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Test
    fun createsMessagesAndCallInvitesChannels() {
        val messagesChannel = notificationManager.getNotificationChannel(Push.Channel.Messages.id)
        val invitesChannel = notificationManager.getNotificationChannel(Push.Channel.CallInvites.id)
        assertNotNull(messagesChannel)
        assertNotNull(invitesChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, messagesChannel.importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, invitesChannel.importance)
    }

    @Test
    fun activeChatGatingTruthTable() {
        // App killed / backgrounded -> never in chat
        Push.setAppForeground(false)
        Push.setActivePeerConversation(null)
        assertFalse(Push.isInPeerChat("conv-1"))
        Push.setActivePeerConversation("conv-1")
        assertFalse(Push.isInPeerChat("conv-1"))

        // App foreground, not in a peer chat (Home, Chats, Settings, AI chat) -> not in chat
        Push.setAppForeground(true)
        Push.setActivePeerConversation(null)
        assertFalse(Push.isInPeerChat("conv-1"))

        // App foreground, in a different peer chat -> not in that chat
        Push.setActivePeerConversation("conv-2")
        assertFalse(Push.isInPeerChat("conv-1"))

        // App foreground, in that exact peer chat -> suppressed
        Push.setActivePeerConversation("conv-1")
        assertTrue(Push.isInPeerChat("conv-1"))
    }

    @Test
    fun peerMessageNotifiesOnlyWhenNotInThatChat() {
        assumeTrue("POST_NOTIFICATIONS permission not granted on device", hasNotificationPermission())

        val payload = mapOf(
            "type" to "peer_message",
            "title" to "Lucía M.",
            "body" to "¿Tienes 5 minutos?",
            "peerConversationId" to "conv-test-1",
            "partnerId" to "seed:lucia",
            "messageId" to "msg-1",
        )

        // 1. User is in foreground on Home/Chats (activePeerConversationId = null) -> MUST notify
        Push.setAppForeground(true)
        Push.setActivePeerConversation(null)
        Push.show(context, payload)
        awaitCondition("Expected system notification when user is not in any peer chat") {
            activePeerNotifications("conv-test-1").isNotEmpty()
        }

        // 2. User enters that exact chat in foreground -> MUST suppress notification
        Push.clearPeerNotifications(context, "conv-test-1")
        awaitCondition("Expected clearPeerNotifications to remove existing notification") {
            activePeerNotifications("conv-test-1").isEmpty()
        }
        Push.setAppForeground(true)
        Push.setActivePeerConversation("conv-test-1")
        assertTrue(Push.isInPeerChat("conv-test-1"))
        Push.show(context, payload)
        Thread.sleep(150)
        assertTrue(
            "Expected system notification to be suppressed while viewing that chat",
            activePeerNotifications("conv-test-1").isEmpty(),
        )

        // 3. User is in a DIFFERENT peer chat in foreground -> MUST notify
        Push.setActivePeerConversation("conv-other")
        assertFalse(Push.isInPeerChat("conv-test-1"))
        Push.show(context, payload)
        awaitCondition("Expected system notification when viewing a different peer chat") {
            activePeerNotifications("conv-test-1").isNotEmpty()
        }

        // 4. User backgrounds the app while still on that chat -> MUST notify
        Push.clearPeerNotifications(context, "conv-test-1")
        awaitCondition("Expected clearPeerNotifications before background test") {
            activePeerNotifications("conv-test-1").isEmpty()
        }
        Push.setActivePeerConversation("conv-test-1")
        Push.setAppForeground(false)
        assertFalse(Push.isInPeerChat("conv-test-1"))
        Push.show(context, payload)
        awaitCondition("Expected system notification when app is backgrounded") {
            activePeerNotifications("conv-test-1").isNotEmpty()
        }
        val posted = activePeerNotifications("conv-test-1")
        assertEquals(Push.Channel.Messages.id, posted.first().notification.channelId)
        assertEquals(Notification.CATEGORY_MESSAGE, posted.first().notification.category)
    }

    @Test
    fun callInviteNotifiesWithActionsSuppressesInChatAndClearsOnEntry() {
        assumeTrue("POST_NOTIFICATIONS permission not granted on device", hasNotificationPermission())

        val msgPayload = mapOf(
            "type" to "peer_message",
            "title" to "Lucía M.",
            "body" to "Hola!",
            "peerConversationId" to "conv-test-2",
            "partnerId" to "seed:lucia",
            "messageId" to "msg-2",
        )
        val invitePayload = mapOf(
            "type" to "peer_invite",
            "title" to "Lucía M. invited you to talk",
            "body" to "1:1 Spanish B1 · Tap to join call room",
            "peerConversationId" to "conv-test-2",
            "partnerId" to "seed:lucia",
            "callId" to "call-123",
        )

        // Suppressed while actively viewing conv-test-2 in foreground
        Push.setAppForeground(true)
        Push.setActivePeerConversation("conv-test-2")
        Push.show(context, invitePayload)
        Thread.sleep(150)
        assertTrue(activePeerNotifications("conv-test-2").isEmpty())

        // Posted when user leaves conv-test-2
        Push.setActivePeerConversation(null)
        Push.show(context, msgPayload)
        Thread.sleep(150)
        Push.show(context, invitePayload)

        awaitCondition("Expected both message and call invite notifications without overwriting each other") {
            activePeerNotifications("conv-test-2").size == 2
        }
        val notifications = activePeerNotifications("conv-test-2")
        val invite = notifications.first { it.notification.channelId == Push.Channel.CallInvites.id }.notification
        assertEquals(Notification.CATEGORY_CALL, invite.category)
        assertEquals(2, invite.actions?.size ?: 0)
        assertEquals(context.getString(R.string.push_action_decline), invite.actions[0].title)
        assertEquals(context.getString(R.string.push_action_join), invite.actions[1].title)

        // Clearing notifications when opening that chat removes both
        Push.clearPeerNotifications(context, "conv-test-2")
        awaitCondition("Expected clearPeerNotifications to clear both notifications") {
            activePeerNotifications("conv-test-2").isEmpty()
        }
    }

    private fun awaitCondition(message: String, timeoutMs: Long = 2_000L, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(50)
        }
        assertTrue(message, check())
    }

    @Test
    fun deepLinkParsesPeerMessageAndJoinCallExtras() {
        val msgIntent = Intent().apply {
            putExtra(Push.EXTRA_PEER_CONVERSATION_ID, "conv-1")
            putExtra(Push.EXTRA_PARTNER_ID, "partner-1")
        }
        val parsedMsg = DeepLink.from(msgIntent) as DeepLink.PeerChat
        assertEquals("conv-1", parsedMsg.peerConversationId)
        assertEquals("partner-1", parsedMsg.partnerId)
        assertEquals(null, parsedMsg.callId)
        assertFalse(parsedMsg.joinCall)

        val joinIntent = Intent().apply {
            putExtra(Push.EXTRA_PEER_CONVERSATION_ID, "conv-1")
            putExtra(Push.EXTRA_PARTNER_ID, "partner-1")
            putExtra(Push.EXTRA_CALL_ID, "call-99")
            putExtra(Push.EXTRA_JOIN_CALL, true)
        }
        val parsedJoin = DeepLink.from(joinIntent) as DeepLink.PeerChat
        assertEquals("conv-1", parsedJoin.peerConversationId)
        assertEquals("partner-1", parsedJoin.partnerId)
        assertEquals("call-99", parsedJoin.callId)
        assertTrue(parsedJoin.joinCall)
    }

    private fun activePeerNotifications(peerConversationId: String) =
        notificationManager.activeNotifications.filter { it.tag == "peer:$peerConversationId" }
}
