package com.zuno.ai.viewmodel

import androidx.lifecycle.ViewModel
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.OnboardingCompleteResult
import com.zuno.ai.data.OnboardingTurnResult
import com.zuno.ai.data.OnboardingVoiceTurnEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OnboardingStep(val wireName: String) {
    Language("language"),
    Name("name"),
    Level("level"),
    Goals("goals"),
    Preparing("preparing"),
    Ready("ready");

    val isConversational: Boolean
        get() = this == Name || this == Level || this == Goals
}

enum class OnboardingInputMode {
    Voice,
    Chat,
}

enum class OnboardingChatRole {
    Assistant,
    User,
}

data class OnboardingChatItem(
    val id: String,
    val role: OnboardingChatRole,
    val text: String,
    val step: OnboardingStep,
)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Language,
    val inputMode: OnboardingInputMode = OnboardingInputMode.Voice,
    val selectedLanguageCode: String = "es",
    val selectedAccentId: String? = LearningLanguages.Spanish.defaultAccent,
    val name: String = "",
    val level: String = "B1",
    val focus: String = "conversation",
    val interests: List<String> = emptyList(),
    val goalNote: String = "",
    val selectedGoalChips: Set<String> = emptySet(),
    val chatHistory: List<OnboardingChatItem> = emptyList(),
    val draftText: String = "",
    val submittingTurn: Boolean = false,
    val pathError: String? = null,
    val result: OnboardingCompleteResult? = null,
) {
    val firstName: String
        get() = name.trim().substringBefore(' ').ifBlank { name.trim() }
}

class OnboardingViewModel : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var messageSeq = 0
    private fun nextId(): String = "ob_${++messageSeq}"

    fun reset(initialLanguageCode: String = "es") {
        val lang = LearningLanguages.find(initialLanguageCode)
        _state.value = OnboardingUiState(
            selectedLanguageCode = lang.code,
            selectedAccentId = lang.defaultAccent,
        )
    }

    fun selectLanguage(language: LearningLanguage) {
        _state.update {
            it.copy(
                selectedLanguageCode = language.code,
                selectedAccentId = language.defaultAccent,
            )
        }
    }

    fun selectAccent(accentId: String) {
        _state.update { it.copy(selectedAccentId = accentId) }
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draftText = text) }
    }

    fun toggleGoalChip(chip: String) {
        _state.update { current ->
            val next = if (chip in current.selectedGoalChips) {
                current.selectedGoalChips - chip
            } else {
                current.selectedGoalChips + chip
            }
            current.copy(selectedGoalChips = next)
        }
    }

    fun setInputMode(mode: OnboardingInputMode) {
        _state.update { it.copy(inputMode = mode) }
    }

    fun continueFromLanguage(namePrompt: String, startInVoice: Boolean) {
        _state.update { current ->
            current.copy(
                step = OnboardingStep.Name,
                inputMode = if (startInVoice) OnboardingInputMode.Voice else OnboardingInputMode.Chat,
                draftText = "",
                chatHistory = listOf(
                    OnboardingChatItem(
                        id = nextId(),
                        role = OnboardingChatRole.Assistant,
                        text = namePrompt,
                        step = OnboardingStep.Name,
                    )
                ),
            )
        }
    }

    /** Returns true if handled internally, or false if already at the first step (`5a`). */
    fun stepBack(
        namePrompt: String,
        levelPrompt: (firstName: String) -> String,
    ): Boolean {
        val current = _state.value
        return when (current.step) {
            OnboardingStep.Language -> false
            OnboardingStep.Name -> {
                _state.update { it.copy(step = OnboardingStep.Language, draftText = "") }
                true
            }
            OnboardingStep.Level -> {
                _state.update {
                    it.copy(
                        step = OnboardingStep.Name,
                        draftText = "",
                        chatHistory = listOf(
                            OnboardingChatItem(
                                id = nextId(),
                                role = OnboardingChatRole.Assistant,
                                text = namePrompt,
                                step = OnboardingStep.Name,
                            )
                        ),
                    )
                }
                true
            }
            OnboardingStep.Goals -> {
                val kept = current.chatHistory.filter { it.step == OnboardingStep.Name }
                val prompt = levelPrompt(current.firstName)
                _state.update {
                    it.copy(
                        step = OnboardingStep.Level,
                        draftText = "",
                        selectedGoalChips = emptySet(),
                        chatHistory = kept + OnboardingChatItem(
                            id = nextId(),
                            role = OnboardingChatRole.Assistant,
                            text = prompt,
                            step = OnboardingStep.Level,
                        ),
                    )
                }
                true
            }
            OnboardingStep.Preparing, OnboardingStep.Ready -> true
        }
    }

    fun setSubmittingTurn(submitting: Boolean) {
        _state.update { it.copy(submittingTurn = submitting) }
    }

    fun appendUserAndRetry(step: OnboardingStep, userText: String, retryPrompt: String) {
        _state.update { current ->
            val items = buildList {
                addAll(current.chatHistory)
                if (userText.isNotBlank()) {
                    add(OnboardingChatItem(nextId(), OnboardingChatRole.User, userText, step))
                }
                add(OnboardingChatItem(nextId(), OnboardingChatRole.Assistant, retryPrompt, step))
            }
            current.copy(chatHistory = items, draftText = "", submittingTurn = false)
        }
    }

    fun applyParsedTurn(
        step: OnboardingStep,
        userDisplayText: String,
        parsed: OnboardingTurnResult,
        levelPromptBuilder: (firstName: String) -> String,
        goalsPromptBuilder: (firstName: String) -> String,
    ) {
        _state.update { current ->
            val updatedHistory = current.chatHistory.toMutableList().apply {
                if (userDisplayText.isNotBlank()) {
                    add(OnboardingChatItem(nextId(), OnboardingChatRole.User, userDisplayText, step))
                }
            }
            when (step) {
                OnboardingStep.Name -> {
                    val cleanName = parsed.name.trim().ifBlank { userDisplayText.trim() }
                    val firstName = cleanName.substringBefore(' ').ifBlank { cleanName }
                    val nextPrompt = levelPromptBuilder(firstName)
                    updatedHistory.add(
                        OnboardingChatItem(nextId(), OnboardingChatRole.Assistant, nextPrompt, OnboardingStep.Level)
                    )
                    current.copy(
                        name = cleanName,
                        step = OnboardingStep.Level,
                        chatHistory = updatedHistory,
                        draftText = "",
                        submittingTurn = false,
                    )
                }
                OnboardingStep.Level -> {
                    val nextPrompt = goalsPromptBuilder(current.firstName)
                    updatedHistory.add(
                        OnboardingChatItem(nextId(), OnboardingChatRole.Assistant, nextPrompt, OnboardingStep.Goals)
                    )
                    current.copy(
                        level = parsed.level.ifBlank { "B1" },
                        step = OnboardingStep.Goals,
                        chatHistory = updatedHistory,
                        draftText = "",
                        submittingTurn = false,
                    )
                }
                OnboardingStep.Goals -> {
                    current.copy(
                        focus = parsed.focus.ifBlank { "conversation" },
                        interests = parsed.interests,
                        goalNote = parsed.goalNote.ifBlank { userDisplayText },
                        step = OnboardingStep.Preparing,
                        chatHistory = updatedHistory,
                        draftText = "",
                        submittingTurn = false,
                        pathError = null,
                    )
                }
                else -> current.copy(submittingTurn = false)
            }
        }
    }

    fun applyVoiceTurnEvent(
        event: OnboardingVoiceTurnEvent,
        levelPromptBuilder: (firstName: String) -> String,
        goalsPromptBuilder: (firstName: String) -> String,
    ) {
        val step = when (event.step) {
            "name" -> OnboardingStep.Name
            "level" -> OnboardingStep.Level
            "goals" -> OnboardingStep.Goals
            else -> return
        }
        val current = _state.value
        if (current.step != step) return

        val synthetic = OnboardingTurnResult(
            step = event.step,
            valid = true,
            name = event.name,
            level = event.level.ifBlank { "B1" },
            focus = event.focus.ifBlank { "conversation" },
            interests = event.interests,
            goalNote = event.goalNote,
        )
        applyParsedTurn(
            step = step,
            userDisplayText = event.transcript,
            parsed = synthetic,
            levelPromptBuilder = { fn -> event.nextPrompt.ifBlank { levelPromptBuilder(fn) } },
            goalsPromptBuilder = { fn -> event.nextPrompt.ifBlank { goalsPromptBuilder(fn) } },
        )
    }

    fun onPathBuilt(result: OnboardingCompleteResult) {
        _state.update {
            it.copy(
                result = result,
                pathError = null,
                step = OnboardingStep.Ready,
            )
        }
    }

    fun onPathFailed(message: String) {
        _state.update { it.copy(pathError = message) }
    }

    fun retryBuildPath() {
        _state.update { it.copy(pathError = null, step = OnboardingStep.Preparing) }
    }
}
