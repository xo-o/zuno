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
import com.zuno.ai.data.CoachPromptItem
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Message
import com.zuno.ai.ui.components.ContinueSessionUi
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.screens.ChatScreen
import com.zuno.ai.ui.screens.HomeCopy
import com.zuno.ai.ui.screens.HomeScreen
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.screens.VoiceModeScreen
import com.zuno.ai.ui.theme.ZunoTheme
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
        var slowReplayCalledWith by mutableStateOf<String?>(null)

        val activeSession = ContinueSessionUi(
            conversationId = "conv-1",
            title = "At the café",
            subtitle = "Spanish B1 · 2h ago",
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
                                    onStreakClick = {},
                                    onAvatarClick = {},
                                    onDailyGoalContinue = {},
                                    onOpenTopics = {},
                                    onOpenChats = {},
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
                                    initialCaptionsOn = false,
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

            // 1. Home · 1 · Active — Continue with Zuno
            waitForText("Hola, Noah")
            waitForText("Daily target · 8 / 15 min")
            waitForText("53%")
            waitForText("Pick up where you left off")
            waitForText("At the café")
            waitForText("Spanish B1 · 2h ago")
            waitForText("Resume")
            waitForText("Live now")
            waitForText("Café small talk circle")
            waitForText("Practice with a partner")
            saveScreenshot(scenario, "01_home_active")

            tapText("Resume")
            assertTrue("Expected Resume to open conversation", resumedConversationId == "conv-1")

            // 2. Home · 5 · More — Explore Zuno (Pure App Directory)
            tapText("More")
            waitForText("Explore")
            waitForText("Noah Carter")
            waitForText("Spanish · B1 · 22-day streak")
            waitForText("Unlock unlimited roleplays")
            waitForText("Practice & Learn")
            waitForText("Roleplay")
            waitForText("Live rooms")
            waitForText("2 active")
            waitForText("Vocabulary")
            waitForText("14 due")
            waitForText("Scheduled")
            waitForText("7:00 PM")
            waitForText("Social & Milestones")
            waitForText("Community")
            waitForText("Partners")
            waitForText("12 online")
            waitForText("Leaderboard")
            waitForText("#5")
            saveScreenshot(scenario, "02_home_more_explore")
            tapText("Home")
            waitForText("Hola, Noah")

            // 3. Home · 8 · Mic Blocked → Dialog
            tapText("Start voice call with Zuno")
            waitForText("Allow microphone access?")
            saveScreenshot(scenario, "03_home_mic_dialog", useFullScreen = true)
            tapText("Not now")
            Thread.sleep(200)

            // 4. Home empty state (new user with no prior session)
            stage = "home_empty"
            waitForText("Start practicing")
            waitForText("Free talk")
            waitForText("Start")
            saveScreenshot(scenario, "04_home_empty")

            // 5. Home · 6 · Speaking — No captions
            stage = "voice"
            waitForText("Live · 00:", substring = true)
            waitForText("End call")
            waitForText("Show captions")
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
        }
    }
}
