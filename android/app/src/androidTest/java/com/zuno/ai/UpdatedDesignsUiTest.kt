package com.zuno.ai

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import com.zuno.ai.data.BlockedLearner
import com.zuno.ai.data.CoachPromptItem
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.LiveRoom
import com.zuno.ai.data.LiveRoomMember
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.data.PrivacySettings
import com.zuno.ai.data.Profile
import com.zuno.ai.data.VocabBucket
import com.zuno.ai.data.VocabCounts
import com.zuno.ai.data.VocabLibrary
import com.zuno.ai.data.VocabPhrase
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.components.ZunoOngoingCallBanner
import com.zuno.ai.ui.screens.ActiveLiveRoomScreen
import com.zuno.ai.ui.screens.CreateLiveRoomScreen
import com.zuno.ai.ui.screens.HomeCopy
import com.zuno.ai.ui.screens.HomeScreen
import com.zuno.ai.ui.screens.LiveRoomLobbyScreen
import com.zuno.ai.ui.screens.LiveRoomsScreen
import com.zuno.ai.ui.screens.PeerCallScreen
import com.zuno.ai.ui.screens.PostCallSavePhrasesSheet
import com.zuno.ai.ui.screens.SettingsAccountScreen
import com.zuno.ai.ui.screens.SettingsPrivacyScreen
import com.zuno.ai.ui.screens.SprintEvaluationScreen
import com.zuno.ai.ui.screens.SprintReviewResult
import com.zuno.ai.ui.screens.SystemRoomDetailScreen
import com.zuno.ai.ui.screens.VocabularyScreen
import com.zuno.ai.ui.screens.VocabularySprintScreen
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.screens.WelcomeToProScreen
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.viewmodel.Loadable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class UpdatedDesignsUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun screenshotDir(): File =
        File(instrumentation.targetContext.filesDir, "updated_designs_verify")

    private fun saveScreenshot(name: String) {
        Thread.sleep(450)
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

    private fun visibleTexts(): List<String> {
        val list = mutableListOf<String>()
        collectTexts(instrumentation.uiAutomation.rootInActiveWindow, list)
        return list
    }

    private fun waitForText(expected: String, substring: Boolean = false, timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val texts = visibleTexts()
            val matched = if (substring) texts.any { it.contains(expected) } else texts.any { it == expected }
            if (matched) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for '$expected'. Visible: ${visibleTexts()}")
    }

    private fun tapText(label: String, timeoutMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var root: AccessibilityNodeInfo? = null
        var target: AccessibilityNodeInfo? = null
        while (System.currentTimeMillis() < deadline) {
            root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                target = findNodeByText(root, label)
                if (target != null) break
            }
            Thread.sleep(100)
        }
        if (root == null) throw AssertionError("No active window")
        val found = target ?: throw AssertionError("No node found for '$label'. Visible: ${visibleTexts()}")
        val rect = Rect()
        found.getBoundsInScreen(rect)
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
    fun verifyAllUpdatedDesignsInGenymotion() {
        var stage by mutableStateOf("01_home_banner")
        var muteBanner by mutableStateOf(false)
        var speakerBanner by mutableStateOf(true)
        var privacy by mutableStateOf(
            PrivacySettings(
                openToSpeak = true,
                allowCallInvites = true,
                whoCanMessage = "partners",
                blockedLearners = listOf(BlockedLearner("b1", "Alex P.")),
            ),
        )

        val now = System.currentTimeMillis()
        val sampleRoom = LiveRoom(
            id = "room-1",
            title = "Weekend travel stories",
            description = "Share your best weekend trip and practice past tense.",
            language = "es",
            level = "B1",
            maxMembers = 4,
            visibility = "public",
            code = "ZUNO-482",
            sourceKind = "user",
            status = "lobby",
            hostName = "Noah Carter",
            isHost = true,
            currentRound = 1,
            discussionPrompts = listOf("¿Adónde fuiste en tu último viaje de fin de semana?"),
            members = listOf(
                LiveRoomMember("u1", "Noah Carter", "N", isHost = true, isYou = true, ready = true),
                LiveRoomMember("u2", "Lucía M.", "L", ready = true),
                LiveRoomMember("u3", "Mateo R.", "M", ready = true),
                LiveRoomMember("u4", "Yuki T.", "Y", ready = true),
            ),
        )
        val systemRoom = LiveRoom(
            id = "sys-cafe",
            title = "Café culture & morning routines",
            description = "System-guided topic room. Zuno prompts the group with live questions and helpful phrases.",
            language = "es",
            level = "B1",
            maxMembers = 4,
            visibility = "public",
            sourceKind = "system",
            status = "live",
            hostName = "Zuno",
            discussionPrompts = listOf(
                "Your go-to morning coffee order and routine",
                "Favorite neighborhood cafés in your city",
                "Comparing café culture across Spanish-speaking cities",
            ),
            members = listOf(
                LiveRoomMember("z", "Zuno", "Z", isHost = true),
                LiveRoomMember("m", "Mateo R.", "M"),
                LiveRoomMember("s", "Sofía", "S"),
            ),
        )
        val caughtUpLib = VocabLibrary(
            counts = VocabCounts(due = 0, learning = 2, mastered = 1, total = 3),
            phrases = listOf(
                VocabPhrase("v4", "es", "para llevar", "to go / takeaway", "Pedir un café", intervalDays = 7, intervalLabel = "7d", bucket = VocabBucket.Learning, dueNow = false),
                VocabPhrase("v5", "es", "¿pasa por la estación?", "does it stop by the station?", "Lesson 3", intervalDays = 14, intervalLabel = "14d", bucket = VocabBucket.Learning, dueNow = false),
                VocabPhrase("v6", "es", "encantado de conocerte", "lovely to meet you", "Free Talk", intervalDays = 30, intervalLabel = "30d", bucket = VocabBucket.Mastered, dueNow = false),
            ),
        )
        val emptyLib = VocabLibrary(phrases = emptyList(), counts = VocabCounts())
        val sprintPhrases = listOf(
            VocabPhrase("v1", "es", "pedir la cuenta", "to ask for the bill", "Café roleplay", syllableStress = "pe·DIR la CUEN·ta", recallCue = "Could you bring me the bill, please?", bucket = VocabBucket.Due, dueNow = true, intervalLabel = "Due now"),
            VocabPhrase("v2", "es", "hacer escala", "to have a layover", "Airport check-in", syllableStress = "ha·CER es·CA·la", recallCue = "To stop over between flights", bucket = VocabBucket.Due, dueNow = true, intervalLabel = "Due now"),
        )
        val sprintResults = listOf(
            SprintReviewResult(sprintPhrases[0], passed = true, nextIntervalLabel = "1d"),
            SprintReviewResult(sprintPhrases[1], passed = false, nextIntervalLabel = "Tomorrow"),
        )
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
            interests = listOf("Travel", "Café"),
            openToSpeak = true,
            isPartner = true,
            activeLabel = "Open to speak",
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
                            "01_home_banner" -> HomeScreen(
                                copy = HomeCopy.forLanguage(LearningLanguages.Spanish, "Noah"),
                                userName = "Noah Carter",
                                userInitial = "N",
                                userImage = null,
                                streakDays = 22,
                                dailyGoalMinutesDone = 8,
                                dailyGoalTargetMinutes = 15,
                                level = "B1",
                                showUpgrade = true,
                                continueSession = null,
                                onStreakClick = {},
                                onAvatarClick = {},
                                onDailyGoalContinue = {},
                                onOpenTopics = {},
                                onOpenChats = {},
                                onOpenRoleplays = {},
                                onOpenScheduled = {},
                                onOpenSettings = {},
                                onStartHomeVoice = {},
                                onStartFreeTalk = {},
                                onOpenConversation = {},
                                onUpgrade = {},
                                ongoingCallBanner = {
                                    ZunoOngoingCallBanner(
                                        callerTitle = "Sofía · AI Tutor",
                                        sessionSubtitle = "Free talk · Spanish B1 · Tap to return",
                                        statusLabel = "Speaking · 02:14",
                                        statusColor = ZunoColors.StatusSpeaking,
                                        isMuted = muteBanner,
                                        isSpeakerOn = speakerBanner,
                                        onReturnToCall = {},
                                        onToggleMute = { muteBanner = !muteBanner },
                                        onToggleSpeaker = { speakerBanner = !speakerBanner },
                                        onEndCall = {},
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )

                            "02_live_rooms_browse" -> LiveRoomsScreen(
                                languageName = "Spanish",
                                level = "B1",
                                onBack = {},
                                onJoinRoom = {},
                                onCreateRoom = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "03_live_rooms_create" -> CreateLiveRoomScreen(
                                languages = LearningLanguages.bundled,
                                defaultLanguage = LearningLanguages.Spanish,
                                defaultLevel = "B1",
                                onBack = {},
                                onCreate = { _, _, _, _, _ -> },
                                modifier = Modifier.fillMaxSize(),
                            )

                            "04_live_rooms_lobby" -> LiveRoomLobbyScreen(
                                room = sampleRoom.copy(members = sampleRoom.members.take(2)),
                                languageName = "Spanish",
                                onBack = {},
                                onInvite = {},
                                onStartRoom = {},
                                onLeaveLobby = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "05_live_rooms_system_detail" -> SystemRoomDetailScreen(
                                room = systemRoom,
                                languageName = "Spanish",
                                onBack = {},
                                onShare = {},
                                onJoin = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "06_live_rooms_active" -> ActiveLiveRoomScreen(
                                roomData = sampleRoom.copy(status = "live"),
                                userName = "Noah",
                                userInitial = "N",
                                startedAtMillis = now - 74_000L,
                                turns = listOf(
                                    ChatMessage("1", Speaker.AGENT, "“¿Cuál es tu comida callejera favorita y por qué?”", isFinal = true),
                                    ChatMessage("2", Speaker.USER, "Siempre pido tacos al pastor en la esquina.", isFinal = true),
                                ),
                                isMuted = false,
                                isCameraOn = false,
                                isSpeakerOn = true,
                                onToggleMute = {},
                                onToggleCamera = {},
                                onToggleSpeaker = {},
                                onNextQuestion = {},
                                onSwitchVisibility = {},
                                onInviteMember = {},
                                onSpeakStarter = {},
                                onTranslate = {},
                                onSlowReplay = {},
                                onEndRoom = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "08_vocab_empty" -> VocabularyScreen(
                                languageName = "Spanish",
                                level = "B1",
                                library = Loadable(emptyLib),
                                onBack = {},
                                onStartVoiceSprint = {},
                                onSpeakPhrase = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "09_vocab_caught_up" -> VocabularyScreen(
                                languageName = "Spanish",
                                level = "B1",
                                library = Loadable(caughtUpLib),
                                onBack = {},
                                onStartVoiceSprint = {},
                                onSpeakPhrase = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "10_vocab_default" -> VocabularyScreen(
                                languageName = "Spanish",
                                level = "B1",
                                onBack = {},
                                onStartVoiceSprint = {},
                                onSpeakPhrase = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "12_vocab_sprint" -> VocabularySprintScreen(
                                phrases = sprintPhrases,
                                languageName = "Spanish",
                                onSpeakPhrase = {},
                                onSlowSpeakPhrase = {},
                                onReviewPhrase = { _, _ -> },
                                onFinishSprint = {},
                                onBack = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "14_vocab_eval" -> SprintEvaluationScreen(
                                results = sprintResults,
                                onSpeakPhrase = {},
                                onPracticeMissed = {},
                                onBackToVocabulary = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "15_post_call_sheet" -> PostCallSavePhrasesSheet(
                                durationLabel = "03:42",
                                candidates = listOf(
                                    Triple("para llevar", "to go / takeaway", "Free talk"),
                                    Triple("¿me pones un café?", "can you get me a coffee?", "Native tip"),
                                ),
                                onSpeakPhrase = {},
                                onSaveSelected = {},
                                onDismiss = {},
                            )

                            "16_peer_call" -> PeerCallScreen(
                                partner = lucia,
                                userName = "Noah",
                                userInitial = "N",
                                languageName = "Spanish",
                                userLevel = "B1",
                                partnerJoined = true,
                                status = VoiceCallStatus.Speaking,
                                audioLevel = { 0.45f },
                                startedAtMillis = now - 25_000L,
                                partnerLine = "¿Nos vemos mañana?",
                                turns = listOf(ChatMessage("1", Speaker.AGENT, "¿Nos vemos mañana?", isFinal = true)),
                                isMuted = false,
                                isCameraOn = false,
                                isSpeakerOn = true,
                                room = null,
                                localVideoTrack = null,
                                remoteVideoTrack = null,
                                coachPrompts = CoachPrompts(answers = listOf(CoachPromptItem("¡Claro!", "Of course!"))),
                                menu = emptyList(),
                                onToggleMute = {},
                                onToggleCamera = {},
                                onToggleSpeaker = {},
                                onEndCall = {},
                                onPingPartner = {},
                                onTranslate = {},
                                onSlowReplay = {},
                                onSpeakCoachPhrase = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "17_account" -> SettingsAccountScreen(
                                profile = Profile("Noah Carter", "noah@example.com", "es", "B1"),
                                uploadingPhoto = false,
                                onUpdateName = {},
                                onChangePhoto = {},
                                onPlanClick = {},
                                onDownloadData = {},
                                onDeleteAccount = {},
                                onBack = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "18_privacy" -> SettingsPrivacyScreen(
                                privacy = privacy,
                                languageName = "Spanish",
                                level = "B1",
                                onUpdate = { open, invites, who ->
                                    privacy = privacy.copy(
                                        openToSpeak = open ?: privacy.openToSpeak,
                                        allowCallInvites = invites ?: privacy.allowCallInvites,
                                        whoCanMessage = who ?: privacy.whoCanMessage,
                                    )
                                },
                                onUnblock = {},
                                onBack = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "19_welcome_pro" -> WelcomeToProScreen(
                                onStartPractising = {},
                                onManageSubscription = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // 1. Ongoing Call Banner on Home
            waitForText("Sofía · AI Tutor")
            waitForText("Speaking · 02:14")
            waitForText("Mute")
            saveScreenshot("01_home_ongoing_call_banner")

            // 2. Live Rooms Browse + Code Sheet
            stage = "02_live_rooms_browse"
            waitForText("Live rooms")
            tapText("Code")
            waitForText("Join private room")
            waitForText("Room code")
            saveScreenshot("02_live_rooms_code_sheet")

            // 3. Create Live Room
            stage = "03_live_rooms_create"
            waitForText("New live room")
            waitForText("Topic")
            waitForText("Max members")
            saveScreenshot("03_live_rooms_create")

            // 4. Creator Lobby
            stage = "04_live_rooms_lobby"
            waitForText("Lobby · 2/4")
            waitForText("Start room now")
            saveScreenshot("04_live_rooms_lobby")

            // 5. System Room Detail
            stage = "05_live_rooms_system_detail"
            waitForText("Room details")
            waitForText("What you", substring = true)
            saveScreenshot("05_live_rooms_system_detail")

            // 6. Active Live Room (Captions ON + Host Controls) & 7. 2x2 Grid (No Captions)
            stage = "06_live_rooms_active"
            waitForText("Live room")
            waitForText("Round 1 · Weekend travel stories")
            saveScreenshot("06_live_rooms_active_captions")

            tapText("More options")
            waitForText("Switch to Private")
            waitForText("End room for all")
            saveScreenshot("06b_live_rooms_host_controls")
            // Dismiss overflow menu by tapping Next system question
            tapText("Next system question")
            Thread.sleep(200)
            tapText("Hide captions")
            waitForText("Yuki T.")
            waitForText("Noah Carter (You)")
            saveScreenshot("07_live_rooms_active_grid")

            // 8. Vocabulary Empty Library
            stage = "08_vocab_empty"
            waitForText("No saved phrases yet")
            waitForText("Ways to build your deck")
            saveScreenshot("08_vocab_empty")

            // 9. Vocabulary All Caught Up
            stage = "09_vocab_caught_up"
            waitForText("All caught up for today")
            waitForText("Run bonus sprint")
            saveScreenshot("09_vocab_caught_up")

            // 10. Vocabulary Detail & Edit Sheet
            stage = "10_vocab_default"
            waitForText("pedir la cuenta")
            tapText("pedir la cuenta")
            waitForText("Phrase details")
            waitForText("Practice with Zuno")
            saveScreenshot("10_vocab_detail_sheet")
            tapText("Edit")
            waitForText("Edit phrase")
            saveScreenshot("10b_vocab_edit_sheet")

            stage = "09_vocab_caught_up"
            waitForText("All caught up for today")
            tapText("Add phrase")
            waitForText("Meaning (auto-filled by Zuno)")
            saveScreenshot("11_vocab_add_sheet")

            // 12 & 13. Vocabulary Sprint (Captions ON & No Captions Avatar Only)
            stage = "12_vocab_sprint"
            waitForText("Recall · Phrase 1 of 2")
            tapText("Reveal")
            waitForText("pedir la cuenta")
            saveScreenshot("12_vocab_sprint_captions")
            tapText("Hide captions")
            waitForText("Show captions")
            saveScreenshot("13_vocab_sprint_no_captions")

            // 14. Sprint Evaluation
            stage = "14_vocab_eval"
            waitForText("Sprint · 2 of 2")
            waitForText("2 phrases reviewed")
            waitForText("Practice 1 due phrase(s)")
            saveScreenshot("14_vocab_sprint_eval")

            // 15. Post-Call Save Phrases Sheet
            stage = "15_post_call_sheet"
            waitForText("Save from this call")
            waitForText("Save 2 phrases to Vocabulary")
            saveScreenshot("15_post_call_save_phrases")

            // 16. Peer Call No Captions (2 stacked full-height tiles)
            stage = "16_peer_call"
            waitForText("Lucía M.")
            waitForText("Lucía speaking")
            tapText("Hide captions")
            waitForText("Show captions")
            saveScreenshot("16_peer_call_no_captions")

            // 17. Account Profile Photo Sheet
            stage = "17_account"
            waitForText("Change photo")
            tapText("Change photo")
            waitForText("Profile photo")
            waitForText("Take photo")
            waitForText("Choose from library")
            saveScreenshot("17_account_photo_sheet")

            // 18. Settings Privacy Screen
            stage = "18_privacy"
            waitForText("Privacy & visibility")
            waitForText("Allow 1:1 call invites")
            waitForText("Who can message me")
            waitForText("Blocked learners")
            saveScreenshot("18_settings_privacy")

            // 19. Welcome to Pro
            stage = "19_welcome_pro"
            waitForText("on Zuno Pro", substring = true)
            waitForText("Start practising")
            saveScreenshot("19_welcome_to_pro")
        }

        val captured = screenshotDir().listFiles()?.map { it.name }.orEmpty()
        assertTrue("Expected at least 19 verification screenshots, got: $captured", captured.size >= 19)
    }
}
