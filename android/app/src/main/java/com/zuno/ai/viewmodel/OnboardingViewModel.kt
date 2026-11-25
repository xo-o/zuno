package com.zuno.ai.viewmodel

import androidx.lifecycle.ViewModel
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.OnboardingCompleteResult
import com.zuno.ai.data.OnboardingTurnResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OnboardingStep(val wireName: String) {
    UserLanguage("user_language"),
    Language("language"),
    Name("name"),
    Level("level"),
    Goals("goals"),
    Preparing("preparing"),
    Ready("ready");

    val isConversational: Boolean
        get() = this == Name || this == Level || this == Goals
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
    val step: OnboardingStep = OnboardingStep.UserLanguage,
    val selectedAppLanguage: AppLanguage = AppLanguage.English,
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

    fun reset(
        initialLanguageCode: String = "es",
        initialAppLanguage: AppLanguage = AppLanguage.English,
        initialAccentId: String? = null,
    ) {
        val lang = LearningLanguages.find(initialLanguageCode)
        _state.value = OnboardingUiState(
            selectedAppLanguage = initialAppLanguage,
            selectedLanguageCode = lang.code,
            selectedAccentId = initialAccentId ?: lang.defaultAccent,
        )
    }

    fun selectAppLanguage(appLanguage: AppLanguage) {
        _state.update { it.copy(selectedAppLanguage = appLanguage) }
    }

    fun continueFromUserLanguage() {
        _state.update { it.copy(step = OnboardingStep.Language) }
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
        _state.update { current ->
            val keptChips = current.selectedGoalChips.filterTo(mutableSetOf()) { chip ->
                text.contains(chip, ignoreCase = true)
            }
            current.copy(
                draftText = text,
                selectedGoalChips = keptChips,
            )
        }
    }

    fun selectLevelChip(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            val nextDraft = if (current.draftText.trim().equals(trimmed, ignoreCase = true)) {
                ""
            } else {
                trimmed
            }
            current.copy(draftText = nextDraft)
        }
    }

    fun toggleGoalChip(chip: String) {
        val trimmedChip = chip.trim()
        if (trimmedChip.isEmpty()) return
        _state.update { current ->
            val isSelected = current.selectedGoalChips.any { it.equals(trimmedChip, ignoreCase = true) } ||
                splitCommaSegments(current.draftText).any { it.equals(trimmedChip, ignoreCase = true) }
            if (isSelected) {
                val nextDraft = removeChipFromDraft(current.draftText, trimmedChip)
                val nextChips = current.selectedGoalChips
                    .filterNot { it.equals(trimmedChip, ignoreCase = true) }
                    .filterTo(mutableSetOf()) { nextDraft.contains(it, ignoreCase = true) }
                current.copy(
                    draftText = nextDraft,
                    selectedGoalChips = nextChips,
                )
            } else {
                val nextDraft = appendChipToDraft(current.draftText, trimmedChip)
                current.copy(
                    draftText = nextDraft,
                    selectedGoalChips = current.selectedGoalChips + trimmedChip,
                )
            }
        }
    }

    fun continueFromLanguage(namePrompt: String) {
        _state.update { current ->
            current.copy(
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
    }

    /** Returns true if handled internally, or false if already at the first step (`UserLanguage`). */
    fun stepBack(
        namePrompt: String,
        levelPrompt: (firstName: String) -> String,
    ): Boolean {
        val current = _state.value
        return when (current.step) {
            OnboardingStep.UserLanguage -> false
            OnboardingStep.Language -> {
                _state.update { it.copy(step = OnboardingStep.UserLanguage, draftText = "") }
                true
            }
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
                        selectedGoalChips = emptySet(),
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
                        selectedGoalChips = emptySet(),
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
                        selectedGoalChips = emptySet(),
                        submittingTurn = false,
                        pathError = null,
                    )
                }
                else -> current.copy(submittingTurn = false)
            }
        }
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

private fun splitCommaSegments(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun appendChipToDraft(draft: String, chip: String): String {
    val trimmedEnd = draft.trimEnd()
    return when {
        trimmedEnd.isEmpty() -> chip
        trimmedEnd.endsWith(",") -> "$trimmedEnd $chip"
        else -> "$trimmedEnd, $chip"
    }
}

private fun removeChipFromDraft(draft: String, chip: String): String {
    val parts = draft.split(',')
    val nonEmptyCount = parts.count { it.trim().isNotEmpty() }
    val filtered = parts
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(chip, ignoreCase = true) }
    return if (filtered.size < nonEmptyCount) {
        filtered.joinToString(", ")
    } else {
        draft
    }
}
