package com.zuno.ai

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zuno.ai.data.CoachNote
import com.zuno.ai.data.LessonCardUi
import com.zuno.ai.data.LessonEvaluation
import com.zuno.ai.data.LessonPillUi
import com.zuno.ai.data.LessonStageKind
import com.zuno.ai.data.LessonStageUi
import com.zuno.ai.data.LessonTurnUi
import com.zuno.ai.data.PhraseResult
import com.zuno.ai.data.PronunciationEvalUi
import com.zuno.ai.data.RecallPromptUi
import com.zuno.ai.data.RoleplayGoalUi
import com.zuno.ai.ui.screens.LessonEvaluationScreen
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.screens.VoiceModeScreen
import com.zuno.ai.ui.theme.ZunoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TopicVoiceLessonUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun screenshotDir(): File =
        File(instrumentation.targetContext.filesDir, "topics_lesson_verify")

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(400)
        val dir = screenshotDir().apply { mkdirs() }
        val file = File(dir, "$name.png")
        val bmp = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Failed to capture $name")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()
    }

    private fun waitForText(expected: String, substring: Boolean = false, timeoutMs: Long = 10_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(expected, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private data class LessonTestSnapshot(
        val status: VoiceCallStatus,
        val elapsedMs: Long,
        val tutorLine: String,
        val stage: LessonStageUi,
        val turns: List<LessonTurnUi>,
        val eval: PronunciationEvalUi?,
    )

    @Test
    fun verifyTopicVoiceLessonStagesAndEvaluation() {
        var step by mutableStateOf("03a_teach")
        var savedPhrases by mutableStateOf(setOf("¿Este autobús va al centro?"))

        val teachCard = LessonCardUi.TeachCard(
            setupPrompt = "Polite way to ask if this bus goes downtown",
            phrase = "Disculpe, ¿este autobús va al centro?",
            translation = "Excuse me, does this bus go downtown?",
            focusWord = "autobús",
            syllables = "au · to · BÚS",
            cue = "Stress “-BÚS”",
            chunks = listOf("va al centro", "este autobús"),
            goalLabel = "Ask route",
        )

        val evaluation = LessonEvaluation(
            lessonId = "les-3",
            topicId = "t-bus",
            lessonPosition = 3,
            totalLessons = 5,
            lessonTitle = "Elige el autobús adecuado",
            durationSec = 300,
            pronunciationScore = 91,
            goalsCompleted = 5,
            goalsTotal = 5,
            fluencyLabel = "Natural",
            coachNotes = listOf(
                CoachNote(
                    category = "Roleplay recast · Natural phrasing",
                    correction = "“Voy en el cinco” → “Tomo la línea cinco”",
                    ttsSampleText = "Tomo la línea cinco",
                    kind = "grammar",
                ),
                CoachNote(
                    category = "Pronunciation · Stress",
                    correction = "au·to·BÚS — stress the final syllable",
                    ttsSampleText = "autobús",
                    kind = "pronunciation",
                ),
            ),
            phraseResults = listOf(
                PhraseResult("¿Este autobús va al centro?", "mastered"),
                PhraseResult("¿Cuántas paradas son?", "mastered"),
                PhraseResult("¿Pasa por la estación?", "practice"),
            ),
            nextLessonId = "les-4",
        )

        composeRule.setContent {
            ZunoTheme {
                if (step == "04_evaluation") {
                    LessonEvaluationScreen(
                        evaluation = evaluation,
                        lessonTitleFallback = evaluation.lessonTitle,
                        lessonPositionFallback = 3,
                        totalLessonsFallback = 5,
                        busy = false,
                        savedPhrases = savedPhrases,
                        onToggleSavePhrase = { phrase ->
                            savedPhrases = if (phrase in savedPhrases) savedPhrases - phrase else savedPhrases + phrase
                        },
                        onSaveAllPhrases = { phrases ->
                            savedPhrases = savedPhrases + phrases
                        },
                        onPlaySample = {},
                        onNextLesson = {},
                        onPracticeAgain = {},
                        onBackToTopic = {},
                        onRetryLoad = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val (status, elapsedMs, tutorLine, stageObj, turnsList, evalObj) = when (step) {
                        "03a_teach" -> LessonTestSnapshot(
                            status = VoiceCallStatus.Speaking,
                            elapsedMs = 18_000L,
                            tutorLine = teachCard.phrase,
                            stage = LessonStageUi(
                                stage = LessonStageKind.TeachModel,
                                pill = LessonPillUi("book-open", "Learn", "Phrase 1 of 3", "Ask route"),
                                card = teachCard,
                                recallPrompt = null,
                                rescueChips = emptyList(),
                            ),
                            turns = listOf(
                                LessonTurnUi("t1", isUser = false, senderLabel = "Sofía · Speaking", text = teachCard.phrase, translation = teachCard.translation, actions = listOf("listen_normal", "listen_slow")),
                            ),
                            eval = null,
                        )
                        "03b_shadow" -> LessonTestSnapshot(
                            status = VoiceCallStatus.Listening,
                            elapsedMs = 34_000L,
                            tutorLine = teachCard.phrase,
                            stage = LessonStageUi(
                                stage = LessonStageKind.ShadowDrill,
                                pill = LessonPillUi("waveform", "Shadow", "Pronunciation drill", "Stress “-BÚS”"),
                                card = teachCard,
                                recallPrompt = null,
                                rescueChips = emptyList(),
                            ),
                            turns = listOf(
                                LessonTurnUi("t1", isUser = false, senderLabel = "Sofía", text = teachCard.phrase, translation = teachCard.translation, actions = listOf("listen_normal", "listen_slow")),
                                LessonTurnUi("u1", isUser = true, senderLabel = "You · 88% match", text = "“Disculpe, ¿este autobús va al centro?”"),
                            ),
                            eval = PronunciationEvalUi(88, false, "autobús", "Stress “-BÚS”", true),
                        )
                        "03c_build" -> LessonTestSnapshot(
                            status = VoiceCallStatus.Listening,
                            elapsedMs = 85_000L,
                            tutorLine = "Now ask if this bus goes to the train station.",
                            stage = LessonStageUi(
                                stage = LessonStageKind.BuildingBlocks,
                                pill = LessonPillUi("sparkles", "Build", "Combine pattern", "Pattern drill"),
                                card = LessonCardUi.ScaffoldCard("Now ask if this bus goes to the train station.", "la estación", "train station", "¿Este autobús…?"),
                                recallPrompt = null,
                                rescueChips = listOf("hint", "reveal", "slow"),
                            ),
                            turns = listOf(
                                LessonTurnUi("u0", isUser = true, senderLabel = "You · Shadow", text = "Disculpe, ¿este autobús va al centro?"),
                                LessonTurnUi("t2", isUser = false, senderLabel = "Sofía", text = "Now ask if this bus goes to the train station.", actions = listOf("hint", "reveal")),
                                LessonTurnUi("u2", isUser = true, senderLabel = "You · Speaking", text = "“¿Este autobús va a la estación?”"),
                            ),
                            eval = null,
                        )
                        "03d_recall" -> LessonTestSnapshot(
                            status = VoiceCallStatus.Listening,
                            elapsedMs = 150_000L,
                            tutorLine = "The doors open. Ask the driver which bus goes downtown.",
                            stage = LessonStageUi(
                                stage = LessonStageKind.ActiveRecall,
                                pill = LessonPillUi("rotate-ccw", "Recall", "Speak from memory", "Target hidden"),
                                card = null,
                                recallPrompt = RecallPromptUi("Say in Spanish", "The doors open. Ask the driver which bus goes downtown.", listOf(5, 4, 6)),
                                rescueChips = listOf("first_word", "reveal", "slow"),
                            ),
                            turns = listOf(
                                LessonTurnUi("t3", isUser = false, senderLabel = "Sofía", text = "The doors open. Ask the driver which bus goes downtown.", actions = listOf("first_word", "reveal")),
                            ),
                            eval = null,
                        )
                        "03e_roleplay_tutor" -> LessonTestSnapshot(
                            status = VoiceCallStatus.Speaking,
                            elapsedMs = 220_000L,
                            tutorLine = "“No, este va hacia el norte. Toma la línea cinco enfrente.”",
                            stage = LessonStageUi(
                                stage = LessonStageKind.Roleplay,
                                pill = LessonPillUi("users", "Bus driver", "Goal 1 of 2 · Ask route"),
                                card = null,
                                recallPrompt = null,
                                rescueChips = listOf("hint", "translate", "slow"),
                                roleplayGoals = listOf(
                                    RoleplayGoalUi("goal_1", 1, "Ask route", "Ask if this bus goes downtown", "¿Disculpe, este autobús va al centro?", completed = false, current = true),
                                    RoleplayGoalUi("goal_2", 2, "Ask stops", "Ask how many stops to downtown", "¿Cuántas paradas son hasta el centro?", completed = false, current = false),
                                ),
                            ),
                            turns = listOf(
                                LessonTurnUi("rp1", isUser = false, senderLabel = "Bus driver · Sofía · Speaking", text = "“No, este va hacia el norte. Toma la línea cinco enfrente.”", actions = listOf("translate", "listen_slow")),
                            ),
                            eval = null,
                        )
                        else -> LessonTestSnapshot(
                            status = VoiceCallStatus.Listening,
                            elapsedMs = 252_000L,
                            tutorLine = "“No, este va hacia el norte. Toma la línea cinco enfrente.”",
                            stage = LessonStageUi(
                                stage = LessonStageKind.Roleplay,
                                pill = LessonPillUi("users", "Bus driver", "Goal 2 of 2 · Ask stops"),
                                card = null,
                                recallPrompt = null,
                                rescueChips = listOf("hint", "translate", "slow"),
                                roleplayGoals = listOf(
                                    RoleplayGoalUi("goal_1", 1, "Route", "Ask if this bus goes downtown", "¿Disculpe, este autobús va al centro?", completed = true, current = false),
                                    RoleplayGoalUi("goal_2", 2, "Ask stops", "Ask how many stops to downtown", "¿Cuántas paradas son hasta el centro?", completed = false, current = true),
                                ),
                            ),
                            turns = listOf(
                                LessonTurnUi("rp1", isUser = false, senderLabel = "Bus driver · Sofía", text = "“No, este va hacia el norte. Toma la línea cinco enfrente.”", actions = listOf("translate", "hint")),
                                LessonTurnUi("rp2", isUser = true, senderLabel = "You · Speaking (Goal 2 of 2)", text = "“Gracias, ¿cuántas paradas son?”"),
                            ),
                            eval = null,
                        )
                    }
                    VoiceModeScreen(
                        status = status,
                        level = { 0.35f },
                        startedAtMillis = System.currentTimeMillis() - elapsedMs,
                        tutorLine = tutorLine,
                        isMuted = false,
                        menu = emptyList(),
                        isLessonMode = true,
                        lessonPosition = 3,
                        lessonStage = stageObj,
                        lessonTurns = turnsList,
                        pronunciationEval = evalObj,
                        onEnd = {},
                        onInterrupt = {},
                        onToggleMute = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        waitForText("Learn · Phrase 1 of 3")
        waitForText("Disculpe, ¿este autobús va al centro?")
        saveScreenshot("03a_teach")

        composeRule.runOnUiThread { step = "03b_shadow" }
        waitForText("Shadow · Pronunciation drill")
        waitForText("va al centro")
        saveScreenshot("03b_shadow")

        composeRule.runOnUiThread { step = "03c_build" }
        waitForText("Build · Combine pattern")
        waitForText("la estación")
        saveScreenshot("03c_build")

        composeRule.runOnUiThread { step = "03d_recall" }
        waitForText("Recall · Speak from memory")
        waitForText("Target hidden")
        saveScreenshot("03d_recall")

        composeRule.runOnUiThread { step = "03e_roleplay_tutor" }
        waitForText("● 1. Ask route")
        waitForText("Answers")
        saveScreenshot("03e_roleplay_tutor")

        composeRule.runOnUiThread { step = "03f_roleplay_user" }
        waitForText("✓ 1. Route")
        waitForText("● 2. Ask stops")
        saveScreenshot("03f_roleplay_user")

        composeRule.runOnUiThread { step = "04_evaluation" }
        waitForText("Lesson 3 of 5")
        waitForText("Practiced phrases")
        saveScreenshot("04_evaluation")
    }
}
