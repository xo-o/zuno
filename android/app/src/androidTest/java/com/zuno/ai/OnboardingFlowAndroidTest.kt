package com.zuno.ai

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OnboardingFlowAndroidTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun advanceUntilText(text: String, substring: Boolean = false, timeoutMs: Long = 45_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(100)
            Thread.sleep(60)
            val matches = composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()
            if (matches.isNotEmpty()) return
        }
        throw AssertionError("Timed out waiting for text: '$text'")
    }

    private fun saveScreenshot(name: String) {
        runCatching {
            composeRule.mainClock.advanceTimeBy(450)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = File(context.externalCacheDir ?: context.cacheDir, "onboarding_screenshots").apply { mkdirs() }
            val bmp = composeRule.onRoot().captureToImage().asAndroidBitmap()
            FileOutputStream(File(dir, "$name.png")).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
        }
    }

    @Test
    fun completeFullOnboardingAndVerifyAppIntegration() {
        // ZunoVoiceOrb runs a 60fps withFrameNanos loop; manual clock advancement avoids infinite-animation waits.
        composeRule.mainClock.autoAdvance = false

        // 1. Wait past Splash into `5a · Target Language`
        advanceUntilText("Choose a language", timeoutMs = 15_000L)
        composeRule.onNodeWithText("Español").assertIsDisplayed()
        composeRule.onNodeWithText("Latin America").assertIsDisplayed()
        composeRule.onNodeWithText("Spain").assertIsDisplayed()
        saveScreenshot("01_5a_target_language")

        // 2. Tap Continue -> enters `5b` (either Mic Dialog -> Chat/Voice or directly Voice if granted)
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.mainClock.advanceTimeBy(400)
        Thread.sleep(200)

        // If mic permission dialog appears on fresh device state, test "Not now" -> seamless Chat fallback,
        // or if already in Voice mode, verify Voice HUD mode and switch via "Type instead".
        val notNowNodes = composeRule.onAllNodesWithText("Not now").fetchSemanticsNodes()
        if (notNowNodes.isNotEmpty()) {
            composeRule.onNodeWithText("Not now").performClick()
            composeRule.mainClock.advanceTimeBy(400)
        } else {
            advanceUntilText("Onboarding · Step 1 of 3", timeoutMs = 10_000L)
            composeRule.onNodeWithText("Your name").assertIsDisplayed()
            composeRule.onNodeWithText("Sofía · Your Spanish tutor").assertIsDisplayed()
            saveScreenshot("02_5b_voice_name")
            composeRule.onNodeWithText("Type instead").performClick()
            composeRule.mainClock.advanceTimeBy(400)
        }

        // 3. `5b (Chat) · Step 1 — Name`
        advanceUntilText("Hi, I’m Sofía. What should I call you?")
        saveScreenshot("03_5b_chat_name")
        composeRule.onNode(hasSetTextAction()).performTextInput("Call me Noah.")
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithContentDescription("Send").performClick()

        // 4. `5c · Step 2 — Level`
        advanceUntilText("Nice to meet you, Noah. How much Spanish do you know?")
        composeRule.onNodeWithText("Beginner").assertIsDisplayed()
        composeRule.onNodeWithText("Intermediate").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced").assertIsDisplayed()
        saveScreenshot("04_5c_chat_level")

        // Tap quick option chip `Intermediate`
        composeRule.onNodeWithText("Intermediate").performClick()

        // 5. `5d · Step 3 — Goals`
        advanceUntilText("Why are you learning Spanish, Noah?")
        composeRule.onNodeWithText("Work").performClick()
        composeRule.onNodeWithText("Travel").performClick()
        composeRule.onNodeWithText("Interviews").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNode(hasSetTextAction()).performTextInput("Backend interviews in Mexico, plus coffee and travel.")
        composeRule.mainClock.advanceTimeBy(200)
        saveScreenshot("05_5d_chat_goals")
        composeRule.onNodeWithContentDescription("Send").performClick()

        // 6. `5e · Preparing Path` (`Spinner` + `Web/Title` + Sofía tailoring subtitle)
        advanceUntilText("Building your path", timeoutMs = 10_000L)
        composeRule.onNodeWithText("just a few seconds", substring = true).assertIsDisplayed()
        saveScreenshot("06_5e_preparing_path")

        // 7. `5f · Starting Path Ready` (real Gemini Starter 5-lesson Topic + Recommended Next)
        advanceUntilText("Your path is ready, Noah", timeoutMs = 60_000L)
        composeRule.onNodeWithText("Starter · 5 lessons").assertIsDisplayed()
        composeRule.onNodeWithText("Start first lesson").assertIsDisplayed()
        composeRule.onNodeWithText("Recommended next").assertIsDisplayed()
        composeRule.onNodeWithText("Go to Home").assertIsDisplayed()
        saveScreenshot("07_5f_path_ready")

        // 8. Verify integration with Home (`Hola Noah` + `Spanish · B1`) and Topics (`Starter` + `Recommended next`)
        composeRule.onNodeWithText("Go to Home").performClick()
        advanceUntilText("Hola Noah", substring = true, timeoutMs = 10_000L)
        composeRule.onAllNodesWithText("Spanish · B1")[0].assertIsDisplayed()
        saveScreenshot("08_home_integrated")

        // Open drawer -> Topics -> verify Starter Topic & Recommended Next appear in TopicsScreen!
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.mainClock.advanceTimeBy(600)
        Thread.sleep(250)
        composeRule.onAllNodesWithText("Topics")[0].performClick()
        advanceUntilText("Recommended next", timeoutMs = 10_000L)
        composeRule.onNodeWithText("0 of 5 lessons", substring = true).assertIsDisplayed()
        saveScreenshot("09_topics_integrated")

        // Open the generated Starter Topic -> verify 5-lesson Topic detail screen!
        composeRule.onNodeWithText("0 of 5 lessons", substring = true).performClick()
        advanceUntilText("Start topic", timeoutMs = 10_000L)
        saveScreenshot("10_starter_topic_detail")
    }
}
