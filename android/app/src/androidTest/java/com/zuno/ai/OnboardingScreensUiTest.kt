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
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.LessonState
import com.zuno.ai.data.OnboardingCompleteResult
import com.zuno.ai.data.OnboardingTurnResult
import com.zuno.ai.data.Profile
import com.zuno.ai.data.RecommendedTopic
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicLesson
import com.zuno.ai.data.TopicStatus
import com.zuno.ai.data.TopicSummary
import com.zuno.ai.ui.screens.AuthMode
import com.zuno.ai.ui.screens.AuthScreen
import com.zuno.ai.ui.screens.OnboardingFlowScreen
import com.zuno.ai.ui.screens.OnboardingScreen
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.viewmodel.OnboardingStep
import com.zuno.ai.viewmodel.OnboardingViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OnboardingScreensUiTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun screenshotDir(): File =
        File(instrumentation.targetContext.filesDir, "onboarding_verify")

    private fun saveScreenshot(scenario: ActivityScenario<ComponentActivity>, name: String) {
        Thread.sleep(450)
        val dir = screenshotDir().apply { mkdirs() }
        val file = File(dir, "$name.png")
        val latch = CountDownLatch(1)
        var captured: Bitmap? = null
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

    private fun assertNoText(unexpected: String) {
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        assertTrue("Expected '$unexpected' to be absent, but found in $texts", texts.none { it == unexpected })
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
    fun verifyWelcomeAuthAndAllOnboardingFlowScreens() {
        val onboardingVm = OnboardingViewModel()
        var stage by mutableStateOf("welcome")
        var linkSentTo by mutableStateOf<String?>(null)
        var currentAppLanguage by mutableStateOf(AppLanguage.English)
        val allowPathComplete = CompletableDeferred<Unit>()

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
                            "welcome" -> OnboardingScreen(
                                onGetStarted = { stage = "auth" },
                                onOpenTerms = {},
                                onOpenPrivacy = {},
                                modifier = Modifier.fillMaxSize(),
                            )

                            "auth" -> AuthScreen(
                                mode = AuthMode.SignIn,
                                onBack = { stage = "welcome" },
                                onSubmit = { linkSentTo = it },
                                onContinueWithGoogle = {},
                                onSwitchMode = {},
                                linkSentTo = linkSentTo,
                                onUseDifferentEmail = { linkSentTo = null },
                                modifier = Modifier.fillMaxSize(),
                            )

                            "flow" -> OnboardingFlowScreen(
                                onboardingViewModel = onboardingVm,
                                languages = LearningLanguages.bundled,
                                appLanguage = currentAppLanguage,
                                busy = false,
                                onSelectAppLanguage = { currentAppLanguage = it },
                                onConfirmAppLanguage = { currentAppLanguage = it },
                                onParseTurn = { step, answer, chips, _, _ ->
                                    when (step) {
                                        "name" -> OnboardingTurnResult(step = "name", valid = true, name = "Noah")
                                        "level" -> OnboardingTurnResult(step = "level", valid = true, level = "B1")
                                        else -> OnboardingTurnResult(
                                            step = "goals",
                                            valid = true,
                                            focus = "work",
                                            interests = chips.ifEmpty { listOf("Mexico", "travel") },
                                            goalNote = answer.ifBlank { "Mexico & travel" },
                                        )
                                    }
                                },
                                onCompleteOnboarding = { _, _, _, _, _, _, _, _ ->
                                    allowPathComplete.await()
                                    OnboardingCompleteResult(
                                        profile = Profile(
                                            name = "Noah",
                                            email = "noah@example.com",
                                            targetLanguage = "es",
                                            level = "B1",
                                        ),
                                        starterTopic = TopicDetail(
                                            summary = TopicSummary(
                                                id = "t1",
                                                title = "Mi perfil y herramientas técnicas",
                                                translation = "My profile and technical tools",
                                                totalLessons = 5,
                                                completedLessons = 0,
                                                estimatedMinutesLeft = 25,
                                                progress = 0,
                                                status = TopicStatus.NotStarted,
                                            ),
                                            lessons = listOf(
                                                TopicLesson(
                                                    id = "l1",
                                                    position = 1,
                                                    title = "Mi perfil y herramientas técnicas",
                                                    translation = "My profile and technical tools",
                                                    objective = "Introduce your backend stack and role in Mexican Spanish.",
                                                    state = LessonState.Current,
                                                ),
                                                TopicLesson(
                                                    id = "l2",
                                                    position = 2,
                                                    title = "Explicar mi stack de backend",
                                                    translation = "Explain my backend stack",
                                                    objective = "Describe services, databases, and APIs.",
                                                    state = LessonState.Upcoming,
                                                ),
                                                TopicLesson(
                                                    id = "l3",
                                                    position = 3,
                                                    title = "Pedir café y comida en CDMX",
                                                    translation = "Order coffee and food in CDMX",
                                                    objective = "Order naturally at cafés and street stalls.",
                                                    state = LessonState.Upcoming,
                                                ),
                                                TopicLesson(
                                                    id = "l4",
                                                    position = 4,
                                                    title = "Moverse por la ciudad",
                                                    translation = "Getting around the city",
                                                    objective = "Ask for directions and take transit.",
                                                    state = LessonState.Upcoming,
                                                ),
                                                TopicLesson(
                                                    id = "l5",
                                                    position = 5,
                                                    title = "Conversaciones diarias en el trabajo",
                                                    translation = "Daily conversations at work",
                                                    objective = "Handle casual check-ins with teammates.",
                                                    state = LessonState.Upcoming,
                                                ),
                                            ),
                                        ),
                                        recommendedTopics = listOf(
                                            RecommendedTopic(
                                                id = "r1",
                                                title = "Entrevista técnica en startup",
                                                translation = "Technical interview at a startup",
                                                reason = "Technical interview",
                                                prompt = "",
                                            ),
                                            RecommendedTopic(
                                                id = "r2",
                                                title = "Pedir café de especialidad en México",
                                                translation = "Order specialty coffee in Mexico",
                                                reason = "Coffee & travel chat",
                                                prompt = "",
                                            ),
                                        ),
                                        pathSubtitle = "Built from your goals in Mexico & travel.",
                                    )
                                },
                                onStartLesson = { _, _ -> },
                                onGoHome = {},
                                onExitOnboarding = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // 2 · Onboarding
            waitForText("Your quiet space to master any language.")
            saveScreenshot(scenario, "02_onboarding")

            // 3 · Sign In & 4c · Check Your Email
            tapText("Get started")
            waitForText("Welcome back. Pick up where you left off.")
            saveScreenshot(scenario, "03_signin")
            instrumentation.runOnMainSync { linkSentTo = "noah@example.com" }
            waitForText("Check your email")
            saveScreenshot(scenario, "04c_check_email")

            // User Language Step
            instrumentation.runOnMainSync { stage = "flow" }
            waitForText("What’s your language?")
            waitForText("English")
            waitForText("Español")
            saveScreenshot(scenario, "05_user_language")

            // 5a · Target Language
            tapText("Continue")
            waitForText("Choose a language")
            waitForText("Español")
            waitForText("Latin America")
            saveScreenshot(scenario, "05a_target_language")

            // 5c (Chat) · Step 1 — Name (chat-only, no voice mode controls)
            tapText("Continue")
            waitForText("Hi, I’m Zuno. What should I call you?")
            assertNoText("Speak instead")
            assertNoText("Type instead")
            instrumentation.runOnMainSync { onboardingVm.updateDraft("Call me Noah.") }
            saveScreenshot(scenario, "05c_chat_name")

            // Advance to 5d Chat
            instrumentation.runOnMainSync {
                onboardingVm.applyParsedTurn(
                    step = OnboardingStep.Name,
                    userDisplayText = "Call me Noah.",
                    parsed = OnboardingTurnResult(step = "name", valid = true, name = "Noah"),
                    levelPromptBuilder = { fn -> "Nice to meet you, $fn. How much Spanish do you know?" },
                    goalsPromptBuilder = { fn -> "Why are you learning Spanish, $fn?" },
                )
            }
            waitForText("Nice to meet you, Noah. How much Spanish do you know?")
            waitForText("Beginner")
            waitForText("Intermediate")
            waitForText("Advanced")
            assertNoText("Speak instead")
            saveScreenshot(scenario, "05d_chat_level")

            // Tap Intermediate chip -> populates composer without auto-sending, then tap Send -> advances to 5e Chat Goals
            tapText("Intermediate")
            assertNoText("Why are you learning Spanish, Noah?")
            tapText("Send")
            waitForText("Why are you learning Spanish, Noah?")
            waitForText("Work")
            waitForText("Travel")
            waitForText("Interviews")
            waitForText("Daily chat")
            tapText("Work")
            tapText("Travel")
            tapText("Interviews")
            waitForText("Work, Travel, Interviews")
            instrumentation.runOnMainSync {
                onboardingVm.updateDraft("Backend interviews in Mexico, plus coffee and travel.")
            }
            saveScreenshot(scenario, "05e_chat_goals")

            // Submit -> 5f · Preparing Path -> 5g · Starting Path Ready
            tapText("Send")
            waitForText("Building your path")
            waitForText("Zuno is tailoring lessons", substring = true)
            saveScreenshot(scenario, "05f_preparing_path")
            allowPathComplete.complete(Unit)

            waitForText("Your path is ready, Noah")
            waitForText("Intermediate · B1")
            waitForText("Starter · Lesson 1 of 5")
            waitForText("Start")
            waitForText("Starter syllabus · 5 lessons")
            waitForText("Up next")
            waitForText("Start first lesson")
            waitForText("Go to Home")
            saveScreenshot(scenario, "05g_path_ready")

            assertTrue(File(screenshotDir(), "05g_path_ready.png").exists())
        }
    }
}
