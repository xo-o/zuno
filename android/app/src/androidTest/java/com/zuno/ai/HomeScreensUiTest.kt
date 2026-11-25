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
import androidx.compose.ui.res.stringResource
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.ChatsFilter
import com.zuno.ai.data.ChatsOverview
import com.zuno.ai.data.CoachPromptItem
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Message
import com.zuno.ai.data.Profile
import com.zuno.ai.data.RecentKind
import com.zuno.ai.data.RecentSession
import com.zuno.ai.data.Schedule
import com.zuno.ai.data.ScheduleRepeat
import com.zuno.ai.data.UnifiedChatItem
import com.zuno.ai.data.UnifiedChatKind
import com.zuno.ai.ui.components.ContinueSessionUi
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.screens.ChatScreen
import com.zuno.ai.ui.screens.ChatsScreen
import com.zuno.ai.ui.screens.CommunityScreen
import com.zuno.ai.ui.screens.HomeCopy
import com.zuno.ai.ui.screens.HomeScreen
import com.zuno.ai.ui.screens.LiveRoomsScreen
import com.zuno.ai.ui.screens.PaywallScreen
import com.zuno.ai.ui.screens.ProgressScreen
import com.zuno.ai.ui.screens.ScheduledScreen
import com.zuno.ai.ui.screens.SettingsAccountScreen
import com.zuno.ai.ui.screens.SettingsLearningScreen
import com.zuno.ai.ui.screens.SettingsOverviewScreen
import com.zuno.ai.ui.screens.VocabularyScreen
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
class HomeScreensUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun screenshotDir(): File =
        File(instrumentation.targetContext.filesDir, "home_verify")

    private fun saveScreenshot(
        scenario: ActivityScenario<ComponentActivity>,
        name: String,
        useFullScreen: Boolean = true,
    ) {
        Thread.sleep(450)
        val dir = screenshotDir().apply { mkdirs() }
        val file = File(dir, "$name.png")
        var captured: Bitmap? = null
        if (!useFullScreen) {
            val latch = CountDownLatch(1)
            scenario.onActivity { activity ->
                val view = activity.window.decorView
                if (view.width > 0 && view.height > 0) {
                    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(
                        activity.window,
                        bmp,
                        { result ->
                            if (result == PixelCopy.SUCCESS) {
                                captured = bmp
                            } else {
                                bmp.recycle()
                            }
                            latch.countDown()
                        },
                        Handler(Looper.getMainLooper()),
                    )
                } else {
                    latch.countDown()
                }
            }
            latch.await(5, TimeUnit.SECONDS)
        }
        val bmp = captured ?: instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Failed to capture screenshot $name")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bmp.recycle()
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out)
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, expected: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == expected || node.contentDescription?.toString() == expected) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findNodeByText(node.getChild(i), expected)
            if (found != null) return found
        }
        return null
    }

    private fun waitForText(expected: String, substring: Boolean = false, timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val texts = mutableListOf<String>()
            collectTexts(root, texts)
            val matched = if (substring) {
                texts.any { it.contains(expected) }
            } else {
                texts.any { it == expected }
            }
            if (matched) return
            Thread.sleep(100)
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        throw AssertionError("Timed out waiting for '$expected'. Visible texts: $texts")
    }

    private fun tapText(label: String) {
        val root = instrumentation.uiAutomation.rootInActiveWindow
            ?: throw AssertionError("No active window")
        val target = findNodeByText(root, label)
            ?: throw AssertionError("No node found for '$label'")
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
    fun verifyAllHomeAndVoiceScreens() {
        var stage by mutableStateOf("home_active")
        var showMicDialog by mutableStateOf(false)
        var resumedConversationId by mutableStateOf<String?>(null)
        var chatsOpenedCount by mutableStateOf(0)
        var slowReplayCalledWith by mutableStateOf<String?>(null)

        val activeSession = ContinueSessionUi(
            conversationId = "conv-1",
            title = "At the café",
            subtitle = "Spanish B1 · 2h ago",
        )
        val recentSessions = listOf(
            RecentSession("conv-1", RecentKind.FreeTalk, "Café en Madrid", "AI · Now", pinned = true, conversationId = "conv-1"),
            RecentSession("rp-1", RecentKind.Roleplay, "Ordering at a café", "Roleplay", pinned = false, conversationId = "conv-2"),
            RecentSession("tp-1", RecentKind.Topic, "Job interview basics", "Topic · 60%", pinned = false, conversationId = null),
        )
        val sampleProfile = Profile(
            name = "Noah Carter",
            email = "noah.carter@gmail.com",
            targetLanguage = "es",
            level = "B1",
            appLanguage = AppLanguage.English,
            accent = "es-ES",
            levelChecked = true,
            onboardingCompleted = true,
            plan = "free",
            imageUrl = null,
            providers = listOf("google"),
        )
        val now = System.currentTimeMillis()
        val sampleSchedules = listOf(
            Schedule("s1", false, "t1", null, "Encuentra la parada de autobús", "Topic · Lesson 2", ScheduleRepeat.Once, "18:00", null, emptyList(), null, "UTC", 10, true, 10, false, now + 3_600_000L),
            Schedule("s2", false, "t2", null, "Job interview basics", "Topic · Lesson 4", ScheduleRepeat.Daily, "08:00", null, emptyList(), null, "UTC", 15, true, 10, false, now + 86_400_000L),
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
                            "home_active", "home_empty" -> {
                                HomeScreen(
                                    copy = HomeCopy.forLanguage(LearningLanguages.Spanish, "Noah"),
                                    userName = "Noah Carter",
                                    userInitial = "N",
                                    userImage = null,
                                    streakDays = 22,
                                    dailyGoalMinutesDone = 8,
                                    dailyGoalTargetMinutes = 15,
                                    level = "B1",
                                    showUpgrade = true,
                                    continueSession = if (stage == "home_active") activeSession else null,
                                    recentSessions = recentSessions,
                                    onStreakClick = {},
                                    onAvatarClick = {},
                                    onDailyGoalContinue = {},
                                    onOpenTopics = {},
                                    onOpenChats = { chatsOpenedCount += 1 },
                                    onOpenRoleplays = {},
                                    onOpenScheduled = {},
                                    onOpenSettings = {},
                                    onStartHomeVoice = { showMicDialog = true },
                                    onStartFreeTalk = {},
                                    onOpenConversation = { resumedConversationId = it },
                                    onUpgrade = {},
                                    onComingSoon = {},
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (showMicDialog) {
                                    ZunoDialog(
                                        title = stringResource(R.string.mic_dialog_title),
                                        body = stringResource(R.string.mic_dialog_body),
                                        confirmLabel = stringResource(R.string.mic_dialog_allow),
                                        dismissLabel = stringResource(R.string.common_not_now),
                                        onConfirm = { showMicDialog = false },
                                        onDismiss = { showMicDialog = false },
                                    )
                                }
                            }

                            "paywall" -> PaywallScreen(
                                onClose = {},
                                onStartTrial = {},
                                onOpenTerms = {},
                                onOpenPrivacy = {},
                                onRestorePurchases = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "vocabulary" -> VocabularyScreen(
                                languageName = "Spanish",
                                level = "B1",
                                onBack = {},
                                onStartVoiceSprint = {},
                                onSpeakPhrase = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "progress" -> ProgressScreen(
                                languageName = "Spanish",
                                level = "B1",
                                streakDays = 22,
                                onBack = {},
                                onStartDrill = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "community" -> CommunityScreen(
                                userName = "Noah Carter",
                                userInitial = "N",
                                userImageUrl = null,
                                languageName = "Spanish",
                                level = "B1",
                                openToSpeak = true,
                                partners = emptyList(),
                                onlineLearners = emptyList(),
                                onToggleOpenToSpeak = {},
                                onTalkToPartner = {},
                                onInviteClick = {},
                                onOpenChats = {},
                                onBack = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "live_rooms" -> LiveRoomsScreen(
                                languageName = "Spanish",
                                level = "B1",
                                onBack = {},
                                onJoinRoom = {},
                                onCreateRoom = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "settings" -> SettingsOverviewScreen(
                                profile = sampleProfile,
                                appLanguage = AppLanguage.English,
                                onBack = {},
                                onUpgrade = {},
                                onAccount = {},
                                onLearning = {},
                                onAppLanguage = {},
                                onPreferences = {},
                                onNotifications = {},
                                onAbout = {},
                                onContactSupport = {},
                                onSignOut = {},
                                onDeleteAccount = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "account" -> SettingsAccountScreen(
                                profile = sampleProfile,
                                uploadingPhoto = false,
                                onUpdateName = {},
                                onChangePhoto = {},
                                onPlanClick = {},
                                onDownloadData = {},
                                onDeleteAccount = {},
                                onBack = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "learning" -> SettingsLearningScreen(
                                profile = sampleProfile.copy(interests = listOf("Software", "Interviews")),
                                appLanguage = AppLanguage.English,
                                onOpenLanguages = {},
                                onAccentChange = {},
                                onLevelChange = {},
                                onFindLevel = {},
                                onPreferencesChange = {},
                                onBack = {},
                                onUpdateInterests = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "scheduled" -> ScheduledScreen(
                                schedules = Loadable(sampleSchedules),
                                highlightId = null,
                                onBack = {},
                                onRetry = {},
                                onCreate = {},
                                onOpen = {},
                                onStart = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "voice" -> {
                                VoiceModeScreen(
                                    status = VoiceCallStatus.Speaking,
                                    level = { 0.55f },
                                    startedAtMillis = System.currentTimeMillis() - 42_000L,
                                    tutorLine = "Imagina que estás en un café en Madrid. ¿Qué vas a pedir? ¿Con leche o solo?",
                                    userLine = null,
                                    isMuted = false,
                                    menu = emptyList(),
                                    sessionKindLabel = "Free talk",
                                    languageLevelLabel = "Spanish B1",
                                    coachPrompts = CoachPrompts(
                                        answers = listOf(
                                            CoachPromptItem("Un café con leche, por favor.", "A coffee with milk, please."),
                                            CoachPromptItem("Solo un espresso, gracias.", "Just an espresso, thanks."),
                                        ),
                                        questions = listOf(
                                            CoachPromptItem("¿Tienen leche de avena?", "Do you have oat milk?"),
                                            CoachPromptItem("¿Cuánto cuesta?", "How much does it cost?"),
                                        ),
                                    ),
                                    onTranslate = {},
                                    onSlowReplay = { slowReplayCalledWith = it },
                                    onSwitchTopic = {},
                                    onEnd = {},
                                    onInterrupt = {},
                                    onToggleMute = {},
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            "free_talk" -> {
                                ChatScreen(
                                    title = "Pedir un café",
                                    sessionLabel = "New Free Talk · Just now",
                                    messages = listOf(
                                        Message(
                                            id = "m1",
                                            isUser = true,
                                            text = "Un café con leche para llevar, por favor.",
                                            translation = null,
                                            feedback = "Natural Spanish · Native tip: “¿Me pones un café…?”",
                                            glossary = emptyList(),
                                        ),
                                        Message(
                                            id = "m2",
                                            isUser = false,
                                            text = "¡Claro! Son tres euros. ¿Algo más?",
                                            translation = "Sure! That’s three euros. Anything else?",
                                            feedback = null,
                                            glossary = emptyList(),
                                        ),
                                    ),
                                    pendingText = null,
                                    pendingAttachments = emptyList(),
                                    drafts = emptyList(),
                                    error = null,
                                    placeholder = "Responde en español…",
                                    typingHint = null,
                                    menu = emptyList(),
                                    onBack = {},
                                    onRetryLoad = {},
                                    onSend = {},
                                    onDraftChanged = {},
                                    savedDraft = { "" },
                                    onSaveDraft = {},
                                    onStartVoice = {},
                                    onListen = {},
                                    onExplain = {},
                                    onAttach = {},
                                    onRemoveAttachment = {},
                                    onRetryAttachment = {},
                                    onOpenAttachment = {},
                                    levelLabel = "B1",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            // 1. Home · 1 · Active — Continue with Zuno (`7365:20293` Agent Horizon)
            waitForText("22")
            waitForText("Spanish · B1")
            waitForText("Hold the glow to talk")
            waitForText("Hola Noah, ¿qué practicamos hoy?")
            waitForText("Type instead")
            saveScreenshot(scenario, "01_home_active")

            // 2. Home · 5 · Sidebar Drawer (BgElevated #1E1E1E overlay)
            tapText("Open navigation menu")
            waitForText("Noah Carter")
            waitForText("Practice")
            waitForText("Free Talk")
            waitForText("Topics")
            waitForText("Roleplays")
            waitForText("Vocabulary")
            waitForText("12 due")
            waitForText("Live & social")
            waitForText("Chats")
            waitForText("2 unread")
            waitForText("Live rooms")
            waitForText("Community & Partners")
            waitForText("Progress")
            waitForText("Recent sessions")
            waitForText("Café en Madrid")
            waitForText("Settings")
            waitForText("New chat")
            saveScreenshot(scenario, "02_home_sidebar_drawer")
            tapText("Chats")
            Thread.sleep(350)
            assertTrue("Expected Chats drawer item to navigate to Chats", chatsOpenedCount == 1)
            waitForText("Hola Noah, ¿qué practicamos hoy?")

            // 3. Home · 8 · Mic Blocked → Dialog
            showMicDialog = true
            waitForText("Allow microphone access?")
            saveScreenshot(scenario, "03_home_mic_dialog", useFullScreen = true)
            tapText("Not now")
            Thread.sleep(200)

            // 4. Home empty state
            stage = "home_empty"
            waitForText("Hold the glow to talk")
            waitForText("Type instead")
            saveScreenshot(scenario, "04_home_empty")

            // 5. Home · 6 · Speaking — No captions (`7383:13309` Agent Horizon)
            stage = "voice"
            waitForText("Free talk · 00:", substring = true)
            waitForText("ZunoAI")
            waitForText("Speak anytime to interrupt")
            waitForText("End call")
            waitForText("Show captions")
            Thread.sleep(950) // allow smooth Mono -> Cobalt transition to complete
            saveScreenshot(scenario, "05_voice_no_captions")

            // 6. Home · 7 · Speaking — Captions
            tapText("Show captions")
            waitForText("Sofía · Free talk")
            waitForText("¿Qué vas a pedir?", substring = true)
            waitForText("Translate")
            waitForText("0.75×")
            waitForText("You · Listening")
            waitForText("Listen · Your turn next…")
            waitForText("Free talk · Spanish B1")
            waitForText("Answers")
            waitForText("Switch topic")
            waitForText("Questions")
            saveScreenshot(scenario, "06_voice_captions")

            tapText("0.75×")
            assertTrue("Expected 0.75x chip callback to fire", slowReplayCalledWith != null)

            // 7. Home · 4 · Send → Free Talk
            stage = "free_talk"
            waitForText("Sofía")
            waitForText("Pedir un café · B1")
            waitForText("New Free Talk · Just now")
            waitForText("Un café con leche para llevar, por favor.")
            waitForText("¡Claro! Son tres euros. ¿Algo más?")
            waitForText("Responde en español…")
            saveScreenshot(scenario, "07_free_talk_chat")

            // 8. Paywall · 1 · Plans
            stage = "paywall"
            waitForText("Zuno Pro")
            waitForText("Save 50%")
            waitForText("Start 7-day free trial")
            saveScreenshot(scenario, "08_paywall")

            // 9. Vocabulary — SRS Library
            stage = "vocabulary"
            waitForText("Vocabulary")
            waitForText("Start 2-min Voice Sprint")
            waitForText("pedir la cuenta")
            saveScreenshot(scenario, "09_vocabulary")

            // 10. Progress
            stage = "progress"
            waitForText("Progress")
            waitForText("Skill balance")
            waitForText("Daily goals")
            saveScreenshot(scenario, "10_progress")

            // 11. Community — Partners & League
            stage = "community"
            waitForText("Community")
            waitForText("Your partners")
            saveScreenshot(scenario, "11_community_partners")
            tapText("League")
            waitForText("Your rank · #4")
            waitForText("Standings")
            saveScreenshot(scenario, "12_community_league")

            // 12. Live Rooms
            stage = "live_rooms"
            waitForText("Live rooms")
            waitForText("Café culture & morning routines")
            waitForText("Join live room")
            saveScreenshot(scenario, "13_live_rooms")

            // 13. Settings Overview & Account & Scheduled
            stage = "settings"
            waitForText("Settings")
            waitForText("Noah Carter")
            waitForText("Zuno Pro")
            saveScreenshot(scenario, "14_settings_overview")

            stage = "account"
            waitForText("Account")
            waitForText("Privacy & visibility")
            saveScreenshot(scenario, "15_account")

            stage = "learning"
            waitForText("Learning")
            waitForText("Interests")
            waitForText("Software, Interviews")
            saveScreenshot(scenario, "15b_learning")

            stage = "scheduled"
            waitForText("Scheduled")
            waitForText("Encuentra la parada de autobús")
            saveScreenshot(scenario, "16_scheduled")
        }
    }

    @Test
    fun drawerChatsNavigationWorks() {
        var destination by mutableStateOf("home")
        val now = System.currentTimeMillis()
        val overview = ChatsOverview(
            openToSpeak = true,
            language = "es",
            languageName = "Spanish",
            level = "B1",
            openSpeakers = emptyList(),
            partners = emptyList(),
            conversations = listOf(
                UnifiedChatItem(
                    id = "c1",
                    kind = UnifiedChatKind.Ai,
                    conversationId = "c1",
                    peerConversationId = null,
                    partnerId = null,
                    title = "Pedir un café",
                    badge = "AI",
                    isVoice = false,
                    openToSpeak = false,
                    snippet = "Sofía · ¡Claro! Son tres euros.",
                    unread = true,
                    pinned = false,
                    updatedAtMillis = now - 120_000L,
                    avatarInitial = "S",
                    avatarUrl = null,
                ),
                UnifiedChatItem(
                    id = "p1",
                    kind = UnifiedChatKind.Peer,
                    conversationId = null,
                    peerConversationId = "pc1",
                    partnerId = "u2",
                    title = "Lucía M.",
                    badge = null,
                    isVoice = false,
                    openToSpeak = true,
                    snippet = "¿Practicamos 5 min antes de la clase?",
                    unread = true,
                    pinned = false,
                    updatedAtMillis = now - 840_000L,
                    avatarInitial = "L",
                    avatarUrl = null,
                ),
            ),
        )
        val unreadCount = overview.conversations.count { it.unread }
        val recentSessions = listOf(
            RecentSession("conv-1", RecentKind.FreeTalk, "Café en Madrid", "AI · Now", pinned = true, conversationId = "conv-1"),
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
                        when (destination) {
                            "chats" -> ChatsScreen(
                                overview = Loadable(overview),
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
                                onBack = { destination = "home" },
                                modifier = Modifier.fillMaxSize(),
                            )

                            "voice_new", "voice_resume" -> VoiceModeScreen(
                                status = VoiceCallStatus.Listening,
                                level = { 0.25f },
                                startedAtMillis = now - 5_000L,
                                tutorLine = "¡Hola! ¿De qué quieres hablar hoy?",
                                isMuted = false,
                                menu = emptyList(),
                                sessionKindLabel = "Free talk",
                                languageLevelLabel = "Spanish B1",
                                initialCaptionsOn = true,
                                onEnd = { destination = "home" },
                                onInterrupt = {},
                                onToggleMute = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            else -> HomeScreen(
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
                                recentSessions = recentSessions,
                                unreadChatsCount = unreadCount,
                                onStreakClick = {},
                                onAvatarClick = {},
                                onDailyGoalContinue = {},
                                onOpenTopics = {},
                                onOpenChats = { destination = "chats" },
                                onOpenRoleplays = {},
                                onOpenScheduled = {},
                                onOpenSettings = {},
                                onStartHomeVoice = { destination = "voice_new" },
                                onStartFreeTalk = { destination = "voice_new" },
                                onNewChat = { destination = "voice_new" },
                                onOpenConversation = { destination = "voice_resume" },
                                onOpenRecentSession = { destination = "voice_resume" },
                                onUpgrade = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // 1. Open drawer from Home, verify Chats + 2 unread, verify Free Talk stays on Home
            waitForText("Hola Noah, ¿qué practicamos hoy?")
            tapText("Open navigation menu")
            waitForText("Live & social")
            waitForText("Chats")
            waitForText("2 unread")

            tapText("Free Talk")
            Thread.sleep(400)
            assertTrue("Expected Free Talk to stay on Home", destination == "home")
            waitForText("Hola Noah, ¿qué practicamos hoy?")

            // 2. Re-open drawer, tap Chats -> navigates to ChatsScreen -> Back to Home
            tapText("Open navigation menu")
            waitForText("Chats")
            tapText("Chats")
            waitForText("Open to speak")
            waitForText("Pedir un café")
            waitForText("Lucía M.")
            assertTrue("Expected navigation to ChatsScreen", destination == "chats")

            tapText("Back")
            waitForText("Hola Noah, ¿qué practicamos hoy?")
            assertTrue("Expected Back to return to Home", destination == "home")

            // 3. Re-open drawer, tap + New chat -> opens directly in VoiceModeScreen -> End back to Home
            tapText("Open navigation menu")
            waitForText("New chat")
            tapText("New chat")
            waitForText("End call")
            assertTrue("Expected New chat from drawer to open voice/video mode", destination == "voice_new")
            tapText("End call")
            waitForText("Hola Noah, ¿qué practicamos hoy?")

            // 4. Re-open drawer, tap recent session -> resumes directly in VoiceModeScreen
            tapText("Open navigation menu")
            waitForText("Café en Madrid")
            tapText("Café en Madrid")
            waitForText("End call")
            assertTrue("Expected Resume from sidebar to open voice/video mode", destination == "voice_resume")
            tapText("End call")
            waitForText("Hola Noah, ¿qué practicamos hoy?")
        }
    }
}
