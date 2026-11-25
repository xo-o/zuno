package com.zuno.ai

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zuno.ai.data.ChatsFilter
import com.zuno.ai.data.ChatsOverview
import com.zuno.ai.data.CoachPromptItem
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.data.Message
import com.zuno.ai.data.PeerCallRoom
import com.zuno.ai.data.PeerConversationMeta
import com.zuno.ai.data.PeerConversationThread
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.data.PeerMessage
import com.zuno.ai.data.ReportReason
import com.zuno.ai.data.UnifiedChatItem
import com.zuno.ai.data.UnifiedChatKind
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoInputDialog
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoReportSheet
import com.zuno.ai.ui.components.ZunoShareSheet
import com.zuno.ai.ui.screens.ChatScreen
import com.zuno.ai.ui.screens.ChatsScreen
import com.zuno.ai.ui.screens.PeerCallScreen
import com.zuno.ai.ui.screens.PeerChatScreen
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.screens.VoiceModeScreen
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.viewmodel.Loadable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ChatsScreensUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun screenshotDir(): File =
        File(instrumentation.targetContext.filesDir, "chats_verify")

    private fun saveScreenshot(scenario: ActivityScenario<ComponentActivity>, name: String) {
        Thread.sleep(400)
        val dir = screenshotDir().apply { mkdirs() }
        val file = File(dir, "$name.png")
        val bmp = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Failed to capture $name")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), out)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, expected: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == expected || node.contentDescription?.toString() == expected) return node
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i), expected)?.let { return it }
        }
        return null
    }

    private fun waitForText(expected: String, substring: Boolean = false, timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val texts = mutableListOf<String>()
            collectTexts(instrumentation.uiAutomation.rootInActiveWindow, texts)
            val matched = if (substring) texts.any { it.contains(expected) } else texts.any { it == expected }
            if (matched) return
            Thread.sleep(100)
        }
        val texts = mutableListOf<String>()
        collectTexts(instrumentation.uiAutomation.rootInActiveWindow, texts)
        throw AssertionError("Timed out waiting for '$expected'. Visible: $texts")
    }

    private fun tapText(label: String) {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: throw AssertionError("No active window")
        val target = findNodeByText(root, label) ?: throw AssertionError("No node found for '$label'")
        val rect = Rect()
        target.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        instrumentation.uiAutomation.injectInputEvent(down, true)
        instrumentation.uiAutomation.injectInputEvent(up, true)
        down.recycle()
        up.recycle()
        Thread.sleep(250)
    }

    @Test
    fun verifyFullChatsFlowScreens() {
        val lucia = PeerLearner(
            id = "seed:lucia",
            name = "Lucía M.",
            shortName = "Lucía",
            initial = "L",
            imageUrl = null,
            language = "es",
            languageName = "Spanish",
            level = "B1",
            nativeLanguage = "English",
            streakDays = 18,
            sessionsCount = 34,
            interests = listOf("Travel", "Café", "Culture"),
            openToSpeak = true,
            isPartner = false,
            activeLabel = "Open to speak",
        )
        val carlos = PeerLearner(
            id = "seed:carlos",
            name = "Carlos Vega",
            shortName = "Carlos",
            initial = "C",
            imageUrl = null,
            language = "es",
            languageName = "Spanish",
            level = "B2",
            nativeLanguage = "Portuguese",
            streakDays = 14,
            sessionsCount = 42,
            interests = listOf("Travel", "Daily life", "Tech"),
            openToSpeak = true,
            isPartner = false,
            activeLabel = "Open to speak",
        )
        val priya = PeerLearner(
            id = "seed:priya",
            name = "Priya S.",
            shortName = "Priya",
            initial = "P",
            imageUrl = null,
            language = "es",
            languageName = "Spanish",
            level = "B2",
            nativeLanguage = "English",
            streakDays = 21,
            sessionsCount = 48,
            interests = listOf("Travel", "Food"),
            openToSpeak = true,
            isPartner = true,
            activeLabel = "Open to speak",
        )

        val now = System.currentTimeMillis()
        val sampleOverview = ChatsOverview(
            openToSpeak = true,
            language = "es",
            languageName = "Spanish",
            level = "B1",
            openSpeakers = listOf(lucia, priya, carlos),
            partners = listOf(priya),
            conversations = listOf(
                UnifiedChatItem(
                    id = "ai-1",
                    kind = UnifiedChatKind.Ai,
                    conversationId = "ai-1",
                    peerConversationId = null,
                    partnerId = null,
                    title = "Pedir un café",
                    badge = "Voice",
                    isVoice = true,
                    openToSpeak = false,
                    snippet = "Sofía · ¡Claro! Son tres euros. ¿Algo más?",
                    unread = true,
                    pinned = false,
                    updatedAtMillis = now - 2 * 60_000L,
                    avatarInitial = "S",
                    avatarUrl = null,
                ),
                UnifiedChatItem(
                    id = "peer:p1",
                    kind = UnifiedChatKind.Peer,
                    conversationId = null,
                    peerConversationId = "p1",
                    partnerId = lucia.id,
                    title = "Lucía M.",
                    badge = "Open to speak",
                    isVoice = false,
                    openToSpeak = true,
                    snippet = "¿Practicamos 5 min antes de la clase?",
                    unread = true,
                    pinned = false,
                    updatedAtMillis = now - 14 * 60_000L,
                    avatarInitial = "L",
                    avatarUrl = null,
                ),
            ),
        )

        val samplePrompts = CoachPrompts(
            answers = listOf(
                CoachPromptItem("Normalmente tomo un café por la mañana.", "I usually have a coffee in the morning."),
                CoachPromptItem("Hoy prefiero practicar conversación cotidiana.", "Today I prefer everyday conversation."),
            ),
            followUps = listOf(
                CoachPromptItem("¡Hola Lucía! ¿Qué tal tu día?", "Hi Lucía! How is your day going?"),
                CoachPromptItem("¿Sobre qué tema practicamos hoy?", "What topic should we practice today?"),
            ),
            questions = listOf(
                CoachPromptItem("¿Cómo se dice esto de forma más natural?", "How do you say this more naturally?"),
                CoachPromptItem("¿Puedes repetir eso un poco más despacio?", "Could you say that a bit slower?"),
            ),
        )

        var stage by mutableStateOf("chats")
        var openToSpeak by mutableStateOf(true)
        var partnerJoined by mutableStateOf(false)
        var peerInviteJoined by mutableStateOf(false)
        var hasPinned by mutableStateOf(false)
        var activeActionModal by mutableStateOf<String?>(null)
        var sharePublic by mutableStateOf(false)
        var aiCameraOn by mutableStateOf(false)

        val pinnedOverview = sampleOverview.copy(
            conversations = listOf(
                UnifiedChatItem(
                    id = "ai-pinned",
                    kind = UnifiedChatKind.Ai,
                    conversationId = "ai-pinned",
                    peerConversationId = null,
                    partnerId = null,
                    title = "Café en Madrid",
                    badge = "AI Tutor",
                    isVoice = false,
                    openToSpeak = false,
                    snippet = "Sofía · ¡Claro! Son tres euros. ¿Algo más?",
                    unread = true,
                    pinned = true,
                    updatedAtMillis = now - 10_000L,
                    avatarInitial = "S",
                    avatarUrl = null,
                ),
            ) + sampleOverview.conversations.drop(1),
        )
        val emptyOverview = ChatsOverview(
            openToSpeak = false,
            language = "es",
            languageName = "Spanish",
            level = "B1",
            openSpeakers = listOf(lucia, priya, carlos),
            partners = emptyList(),
            conversations = emptyList(),
        )

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                )
                activity.window.setBackgroundDrawableResource(R.color.background)
                activity.setContent {
                    ZunoTheme {
                        when (stage) {
                            "chats" -> ChatsScreen(
                                overview = Loadable((if (hasPinned) pinnedOverview else sampleOverview).copy(openToSpeak = openToSpeak)),
                                userInitial = "N",
                                userImageUrl = null,
                                initialFilter = ChatsFilter.All,
                                onRetry = {},
                                onToggleOpenToSpeak = { openToSpeak = it },
                                onOpenAiConversation = { stage = "ai_chat" },
                                onStartAiChat = { stage = "ai_chat" },
                                onStartAiVoiceCall = { stage = "ai_call" },
                                onOpenRoleplays = {},
                                onOpenPeerChat = { stage = "peer_chat" },
                                onInvitePeerToTalk = { stage = "peer_call" },
                                onTogglePartner = { _, _ -> },
                                onNavigateTab = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "chats_empty" -> ChatsScreen(
                                overview = Loadable(emptyOverview),
                                userInitial = "N",
                                userImageUrl = null,
                                initialFilter = ChatsFilter.All,
                                onRetry = {},
                                onToggleOpenToSpeak = {},
                                onOpenAiConversation = {},
                                onStartAiChat = {},
                                onStartAiVoiceCall = {},
                                onOpenRoleplays = {},
                                onOpenPeerChat = {},
                                onInvitePeerToTalk = {},
                                onTogglePartner = { _, _ -> },
                                onNavigateTab = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "ai_chat" -> {
                                val chatMenu = listOf(
                                    ZunoMenuAction("Rename", icon = R.drawable.zuno_ic_t_pencil) { activeActionModal = "rename" },
                                    ZunoMenuAction("Pin", icon = R.drawable.zuno_ic_t_pin) {},
                                    ZunoMenuAction("Share", icon = R.drawable.zuno_ic_t_share) { activeActionModal = "share" },
                                    ZunoMenuAction("Report", icon = R.drawable.zuno_ic_t_flag) { activeActionModal = "report" },
                                    ZunoMenuAction("Delete", icon = R.drawable.zuno_ic_t_trash, destructive = true, dividerBefore = true) { activeActionModal = "delete" },
                                )
                                ChatScreen(
                                    title = "Pedir un café",
                                    sessionLabel = "Pedir un café · Today",
                                    messages = listOf(
                                        Message("m1", isUser = true, text = "Un café con leche para llevar, por favor.", translation = null, feedback = "Natural Spanish · Native tip: “¿Me pones un café…?”", glossary = emptyList()),
                                        Message("m2", isUser = false, text = "¡Claro! Son tres euros. ¿Algo más?", translation = "Sure! That's three euros. Anything else?", glossary = emptyList(), feedback = null),
                                        Message("m3", isUser = false, text = "Video call · 03:42 · Ended", translation = null, feedback = null, glossary = emptyList(), kind = "call_summary", callDurationSec = 222, callIsVideo = true, callTipsCount = 2),
                                    ),
                                    pendingText = null,
                                    pendingAttachments = emptyList(),
                                    drafts = emptyList(),
                                    error = null,
                                    placeholder = "Responde en español…",
                                    typingHint = null,
                                    menu = chatMenu,
                                    onBack = { stage = "chats" },
                                    onRetryLoad = {},
                                    onSend = {},
                                    onDraftChanged = {},
                                    savedDraft = { "" },
                                    onSaveDraft = {},
                                    onStartVoice = { stage = "ai_call" },
                                    onListen = {},
                                    onExplain = {},
                                    onAttach = {},
                                    onRemoveAttachment = {},
                                    onRetryAttachment = {},
                                    onOpenAttachment = {},
                                    levelLabel = "B1",
                                    modifier = Modifier.fillMaxSize(),
                                )
                                when (activeActionModal) {
                                    "rename" -> ZunoInputDialog(
                                        title = "Rename chat",
                                        initialValue = "Café en Madrid",
                                        confirmLabel = "Save",
                                        onConfirm = { activeActionModal = null },
                                        onDismiss = { activeActionModal = null },
                                    )
                                    "share" -> ZunoShareSheet(
                                        isPublic = sharePublic,
                                        shareUrl = if (sharePublic) "https://heyzuno.app/s/demo" else null,
                                        busy = false,
                                        onSelectPublic = { sharePublic = it },
                                        onShare = {},
                                        onCopyLink = {},
                                        onDismiss = { activeActionModal = null },
                                    )
                                    "report" -> ZunoReportSheet(
                                        onSubmit = { _, _ -> activeActionModal = null },
                                        onDismiss = { activeActionModal = null },
                                    )
                                    "delete" -> ZunoDialog(
                                        title = "Delete chat?",
                                        body = "“Café en Madrid” and its corrections will be deleted. Words saved to Vocabulary stay. This can’t be undone.",
                                        confirmLabel = "Delete",
                                        destructive = true,
                                        onConfirm = { activeActionModal = null },
                                        onDismiss = { activeActionModal = null },
                                    )
                                }
                            }

                            "ai_call" -> VoiceModeScreen(
                                status = VoiceCallStatus.Speaking,
                                level = { 0.5f },
                                startedAtMillis = now - 134_000L,
                                tutorLine = "¿Qué vas a pedir? ¿Con leche o solo?",
                                isMuted = false,
                                isCameraOn = aiCameraOn,
                                menu = emptyList(),
                                sessionKindLabel = "Free talk",
                                languageLevelLabel = "Spanish B1",
                                learnerLevel = "B1",
                                initialCaptionsOn = true,
                                userName = "Noah",
                                userInitial = "N",
                                coachPrompts = samplePrompts,
                                onEnd = { stage = "ai_chat" },
                                onInterrupt = {},
                                onToggleMute = {},
                                onToggleCamera = { aiCameraOn = !aiCameraOn },
                                modifier = Modifier.fillMaxSize(),
                            )

                            "peer_chat" -> PeerChatScreen(
                                thread = PeerConversationThread(
                                    conversation = PeerConversationMeta("p1", "es", muted = false, partnerId = lucia.id),
                                    partner = lucia,
                                    messages = listOf(
                                        PeerMessage("pm1", "p1", lucia.id, isMine = false, kind = "text", text = "¡Hola Noah! ¿Tienes 5 minutos para practicar antes de la clase?", callRoomId = null, cameraEnabled = false, durationSec = null, createdAtMillis = now - 16 * 60_000L),
                                        PeerMessage("pm2", "p1", "me", isMine = true, kind = "text", text = "¡Sí! Estoy libre ahora. ¿Hablamos por voz?", callRoomId = null, cameraEnabled = false, durationSec = null, createdAtMillis = now - 15 * 60_000L),
                                        PeerMessage("pm3", "p1", lucia.id, isMine = false, kind = "missed_invite", text = "Missed invite to talk", callRoomId = "c0", cameraEnabled = false, durationSec = null, createdAtMillis = now - 14 * 60_000L),
                                        PeerMessage("pm4", "p1", lucia.id, isMine = false, kind = "call_invite", text = "Lucía is inviting you to talk", callRoomId = "c1", cameraEnabled = false, durationSec = null, createdAtMillis = now - 60_000L),
                                    ),
                                    activeCall = PeerCallRoom("c1", "p1", lucia.id, "me", "peer-c1", "waiting", false, null, 0),
                                ),
                                onRefresh = {},
                                onBack = { stage = "chats" },
                                onSendMessage = {},
                                onStartCall = { stage = "peer_call" },
                                onJoinCall = {
                                    peerInviteJoined = true
                                    stage = "peer_call"
                                },
                                onDeclineCall = {},
                                onTogglePartner = { _, _ -> },
                                onToggleMute = {},
                                onReportUser = { _, _ -> },
                                onDeleteChat = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "peer_call" -> PeerCallScreen(
                                partner = lucia,
                                userName = "Noah",
                                userInitial = "N",
                                languageName = "Spanish",
                                userLevel = "B1",
                                partnerJoined = partnerJoined,
                                status = if (partnerJoined) VoiceCallStatus.Speaking else VoiceCallStatus.Listening,
                                audioLevel = { 0.6f },
                                startedAtMillis = now - 6_000L,
                                partnerLine = "¿Qué bien! ¿Y a qué hora te despiertas normalmente?",
                                turns = if (partnerJoined) {
                                    listOf(
                                        ChatMessage("u1", Speaker.USER, "Normalmente tomo un café y leo un poco antes de trabajar.", isFinal = true),
                                        ChatMessage("a1", Speaker.AGENT, "¡Qué bien! ¿Y a qué hora te despiertas normalmente?", isFinal = true),
                                    )
                                } else {
                                    emptyList()
                                },
                                isMuted = false,
                                isCameraOn = false,
                                isSpeakerOn = true,
                                room = null,
                                localVideoTrack = null,
                                remoteVideoTrack = null,
                                coachPrompts = samplePrompts,
                                menu = emptyList(),
                                onToggleMute = {},
                                onToggleCamera = {},
                                onToggleSpeaker = {},
                                onEndCall = { stage = "peer_chat" },
                                onPingPartner = { partnerJoined = true },
                                onTranslate = {},
                                onSlowReplay = {},
                                onSpeakCoachPhrase = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // 1. Unified Chats screen (`Chats · 1 · All Conversations`)
            waitForText("Chats")
            waitForText("Open to speak")
            waitForText("Pedir un café")
            waitForText("Lucía M.")
            saveScreenshot(scenario, "01_chats_all")

            // 1b. Pinned + Recent (`Chat actions · 5 · Chats · Pinned`)
            hasPinned = true
            waitForText("Pinned")
            waitForText("Café en Madrid")
            waitForText("Recent")
            saveScreenshot(scenario, "02_chats_pinned")
            hasPinned = false
            waitForText("Pedir un café")

            // 2. Switch to AI Tutor filter (`Chats · 2`)
            tapText("AI Tutor")
            waitForText("Chat with Sofía")
            waitForText("Sofía · AI Tutor (1)")
            saveScreenshot(scenario, "03_chats_ai_filter")

            // 3. Switch to People filter (`Chats · 3`)
            tapText("People")
            waitForText("Lucía M.")
            saveScreenshot(scenario, "04_chats_people_filter")

            // 4. Switch to Open filter (`Chats · 4`) & open Member Profile Sheet (`Chats · 16`)
            tapText("Open")
            waitForText("Carlos Vega")
            saveScreenshot(scenario, "05_chats_open_filter")
            tapText("View")
            waitForText("Invite to talk")
            waitForText("Send message")
            saveScreenshot(scenario, "06_member_profile_sheet")

            // 5. Tap Send message -> Peer 1:1 Chat (`Chats · 12`)
            tapText("Send message")
            waitForText("Lucía M.")
            waitForText("Decline")
            waitForText("Join")
            saveScreenshot(scenario, "07_peer_chat")

            // 6. Tap Join -> Peer Call Waiting Room (`Chats · 13`) -> Ping -> Active Member Call (`Chats · 14`)
            tapText("Join")
            assertTrue("Expected Join callback", peerInviteJoined)
            waitForText("Waiting for Lucía M. to join…")
            saveScreenshot(scenario, "08_peer_call_waiting")

            tapText("Ping Lucía")
            waitForText("Lucía M. · Speaking")
            waitForText("Replay")
            saveScreenshot(scenario, "09_peer_call_active")

            // 7. Empty All (`Chats · 6 · Empty — All`)
            stage = "chats_empty"
            waitForText("Start your first conversation")
            waitForText("Start chatting")
            waitForText("Meet a partner")
            waitForText("Your availability")
            saveScreenshot(scenario, "10_chats_empty_all")

            // 8. AI Tutor Chat + Returned Call Summary + Chat Actions (`06 · Chat actions`)
            stage = "ai_chat"
            waitForText("Sofía")
            waitForText("Video call · 03:42 · Ended")
            waitForText("Notes")
            saveScreenshot(scenario, "11_ai_tutor_chat")

            tapText("More options")
            waitForText("Rename")
            waitForText("Pin")
            waitForText("Share")
            waitForText("Report")
            waitForText("Delete")
            saveScreenshot(scenario, "12_chat_actions_menu")

            tapText("Rename")
            waitForText("Rename chat")
            saveScreenshot(scenario, "13_chat_actions_rename")
            tapText("Cancel")

            activeActionModal = "share"
            waitForText("Only me")
            waitForText("Public access")
            saveScreenshot(scenario, "14_chat_actions_share")
            activeActionModal = null

            activeActionModal = "report"
            waitForText("Report chat")
            saveScreenshot(scenario, "15_chat_actions_report")
            activeActionModal = null

            activeActionModal = "delete"
            waitForText("Delete chat?")
            saveScreenshot(scenario, "16_chat_actions_delete")
            activeActionModal = null

            // 9. AI Tutor Call (`Home · 7` Captions + `Chats · 11` video call + `Chats · 19` No Captions)
            stage = "ai_call"
            waitForText("Free talk")
            waitForText("Sofía · Free talk")
            waitForText("Answers")
            waitForText("Switch topic")
            waitForText("Questions")
            saveScreenshot(scenario, "17_ai_tutor_call")

            aiCameraOn = true
            waitForText("Video Call")
            waitForText("Follow-ups")
            saveScreenshot(scenario, "18_ai_tutor_video_call")

            tapText("Hide captions")
            waitForText("AI Tutor")
            waitForText("Noah (You)")
            saveScreenshot(scenario, "19_ai_tutor_video_no_captions")

            // End call so VoiceModeScreen / TutorStageAvatar / ZunoVoiceWaveform dispose cleanly before ActivityScenario.close()
            tapText("End call")
            waitForText("Video call · 03:42 · Ended")
        }
    }
}
