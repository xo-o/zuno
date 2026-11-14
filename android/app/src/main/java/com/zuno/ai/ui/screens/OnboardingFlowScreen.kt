package com.zuno.ai.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuno.ai.R
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.CartesiaAudioPlayer
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.OnboardingCompleteResult
import com.zuno.ai.data.OnboardingTurnResult
import com.zuno.ai.data.RecommendedTopic
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.model.AgentState
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.components.VoiceOrbState
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoOnboardingTopBar
import com.zuno.ai.ui.components.ZunoSpinner
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.theme.Lexend
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.viewmodel.OnboardingChatItem
import com.zuno.ai.viewmodel.OnboardingChatRole
import com.zuno.ai.viewmodel.OnboardingInputMode
import com.zuno.ai.viewmodel.OnboardingStep
import com.zuno.ai.viewmodel.OnboardingUiState
import com.zuno.ai.viewmodel.OnboardingViewModel
import com.zuno.ai.viewmodel.VoiceSessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StarterCardTitleStyle = TextStyle(
    fontFamily = Lexend,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp,
)

/**
 * 01 · Onboarding (`5a` Target Language -> `5b` Name -> `5c` Level -> `5d` Goals -> `5e` Preparing -> `5f` Ready).
 * Supports seamless switching between real-time Voice Orb mode and Chat mode.
 */
@Composable
fun OnboardingFlowScreen(
    onboardingViewModel: OnboardingViewModel,
    voiceViewModel: VoiceSessionViewModel,
    languages: List<LearningLanguage>,
    appLanguage: AppLanguage,
    busy: Boolean,
    micGranted: () -> Boolean,
    requestVoicePermission: (onGranted: () -> Unit, onDeclined: () -> Unit) -> Unit,
    ttsPlayer: CartesiaAudioPlayer,
    sessionTokenProvider: () -> String?,
    onSessionExpired: () -> Unit,
    onParseTurn: suspend (step: String, answer: String, chips: List<String>, targetLanguage: String, nativeLanguage: String) -> OnboardingTurnResult,
    onCompleteOnboarding: suspend (
        name: String?,
        targetLanguage: String,
        nativeLanguage: String,
        accent: String?,
        level: String,
        focus: String,
        interests: List<String>,
        goalNote: String?,
    ) -> OnboardingCompleteResult,
    onStartFirstLesson: (TopicDetail) -> Unit,
    onSelectRecommendedTopic: (RecommendedTopic) -> Unit,
    onGoHome: () -> Unit,
    onExitOnboarding: () -> Unit,
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val agentState by voiceViewModel.agentState.collectAsStateWithLifecycle()
    val voiceMessages by voiceViewModel.messages.collectAsStateWithLifecycle()
    val ttsSpeaking by ttsPlayer.isSpeaking.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedLanguage = remember(state.selectedLanguageCode, languages) {
        languages.firstOrNull { it.code == state.selectedLanguageCode }
            ?: LearningLanguages.find(state.selectedLanguageCode)
    }
    val targetDisplayName = selectedLanguage.displayName(appLanguage)

    val voiceNamePrompt = stringResource(R.string.onboarding_prompt_name_voice)
    val namePrompt = stringResource(R.string.onboarding_prompt_name)
    val levelPromptTemplate = stringResource(R.string.onboarding_prompt_level, "%1\$s", targetDisplayName)
    val goalsPromptTemplate = stringResource(R.string.onboarding_prompt_goals, targetDisplayName, "%1\$s")

    fun buildLevelPrompt(firstName: String): String =
        String.format(levelPromptTemplate, firstName.ifBlank { "there" })

    fun buildGoalsPrompt(firstName: String): String =
        String.format(goalsPromptTemplate, firstName.ifBlank { "there" })

    val currentPrompt = when (state.step) {
        OnboardingStep.Name -> state.chatHistory.lastOrNull { it.role == OnboardingChatRole.Assistant }?.text ?: namePrompt
        OnboardingStep.Level -> state.chatHistory.lastOrNull { it.role == OnboardingChatRole.Assistant }?.text ?: buildLevelPrompt(state.firstName)
        OnboardingStep.Goals -> state.chatHistory.lastOrNull { it.role == OnboardingChatRole.Assistant }?.text ?: buildGoalsPrompt(state.firstName)
        else -> ""
    }
    val voicePrompt = if (state.step == OnboardingStep.Name && currentPrompt == namePrompt) {
        voiceNamePrompt
    } else {
        currentPrompt
    }

    // ── Local Fallback Voice Driver (activates automatically if LiveKit worker isn't active) ──
    var useLocalVoiceFallback by remember { mutableStateOf(false) }
    var localListening by remember { mutableStateOf(false) }
    var localTranscript by remember { mutableStateOf("") }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsPlayer.stop()
            voiceViewModel.disconnect()
            runCatching { speechRecognizer?.destroy() }
        }
    }

    suspend fun submitTurnViaApi(
        step: OnboardingStep,
        displayText: String,
        rawAnswer: String = displayText,
        chips: List<String> = emptyList(),
    ) {
        if (state.submittingTurn) return
        onboardingViewModel.setSubmittingTurn(true)
        ttsPlayer.stop()
        if (agentState != AgentState.DISCONNECTED) {
            voiceViewModel.interrupt()
        }
        try {
            val parsed = onParseTurn(
                step.wireName,
                rawAnswer,
                chips,
                state.selectedLanguageCode,
                appLanguage.code,
            )
            if (!parsed.valid) {
                val retry = parsed.retryPrompt?.takeIf { it.isNotBlank() } ?: currentPrompt
                onboardingViewModel.appendUserAndRetry(step, displayText, retry)
            } else {
                voiceViewModel.clearMessages()
                localTranscript = ""
                onboardingViewModel.applyParsedTurn(
                    step = step,
                    userDisplayText = displayText,
                    parsed = parsed,
                    levelPromptBuilder = ::buildLevelPrompt,
                    goalsPromptBuilder = ::buildGoalsPrompt,
                )
                val updated = onboardingViewModel.state.value
                if (updated.step.isConversational && agentState != AgentState.DISCONNECTED && !useLocalVoiceFallback) {
                    voiceViewModel.sendOnboardingAction(
                        action = "sync_step",
                        extras = mapOf(
                            "step" to updated.step.wireName,
                            "name" to updated.name,
                            "level" to updated.level,
                            "targetLanguage" to updated.selectedLanguageCode,
                            "paused" to (updated.inputMode != OnboardingInputMode.Voice),
                            "speakPrompt" to (updated.inputMode == OnboardingInputMode.Voice),
                        ),
                    )
                } else if (!updated.step.isConversational) {
                    voiceViewModel.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.w("OnboardingFlow", "submitTurnViaApi failed on step=${step.wireName}", e)
            onboardingViewModel.setSubmittingTurn(false)
            onError(e.message ?: "Something went wrong. Please try again.")
        }
    }

    val latestSubmitTurn by rememberUpdatedState<(OnboardingStep, String) -> Unit> { step, text ->
        scope.launch { submitTurnViaApi(step, text) }
    }

    fun startLocalListening() {
        val recognizer = speechRecognizer ?: return
        if (!micGranted() || state.submittingTurn || !state.step.isConversational || state.inputMode != OnboardingInputMode.Voice) return
        val activeStep = state.step
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { localListening = true }
            override fun onBeginningOfSpeech() { localListening = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                localListening = false
            }
            override fun onError(error: Int) {
                localListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) localTranscript = partial
            }
            override fun onResults(results: Bundle?) {
                localListening = false
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (spoken.isNotBlank()) {
                    localTranscript = spoken
                    latestSubmitTurn(activeStep, spoken)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, appLanguage.bcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { recognizer.startListening(intent) }
    }

    // Listen for LiveKit voice-agent `onboarding_turn` packets
    LaunchedEffect(voiceViewModel) {
        voiceViewModel.onboardingEvents.collect { event ->
            voiceViewModel.clearMessages()
            onboardingViewModel.applyVoiceTurnEvent(
                event = event,
                levelPromptBuilder = ::buildLevelPrompt,
                goalsPromptBuilder = ::buildGoalsPrompt,
            )
            if (onboardingViewModel.state.value.step == OnboardingStep.Preparing) {
                voiceViewModel.disconnect()
            }
        }
    }

    // Manage Voice session lifecycle across `5b`–`5d`
    LaunchedEffect(state.step, state.inputMode) {
        localTranscript = ""
        runCatching { speechRecognizer?.cancel() }
        localListening = false

        if (!state.step.isConversational) {
            ttsPlayer.stop()
            if (agentState != AgentState.DISCONNECTED) voiceViewModel.disconnect()
            return@LaunchedEffect
        }

        if (state.inputMode == OnboardingInputMode.Chat) {
            ttsPlayer.stop()
            if (agentState != AgentState.DISCONNECTED) {
                voiceViewModel.sendOnboardingAction("pause")
            }
            return@LaunchedEffect
        }

        // Voice Mode active on `5b`, `5c`, or `5d`
        if (!micGranted()) return@LaunchedEffect

        if (!useLocalVoiceFallback) {
            if (agentState == AgentState.DISCONNECTED) {
                voiceViewModel.connectOnboarding(
                    sessionToken = sessionTokenProvider(),
                    step = state.step.wireName,
                    targetLanguage = state.selectedLanguageCode,
                    nativeLanguage = appLanguage.code,
                    accent = state.selectedAccentId,
                    name = state.name,
                    level = state.level,
                    onSessionExpired = onSessionExpired,
                )
                // Wait up to 3.5s for LiveKit agent to speak/listen; otherwise smoothly engage direct Cartesia+STT fallback
                delay(3_500)
                val currentAgent = voiceViewModel.agentState.value
                val hasAgentActivity = currentAgent == AgentState.SPEAKING ||
                    currentAgent == AgentState.LISTENING ||
                    voiceViewModel.messages.value.isNotEmpty()
                if (!hasAgentActivity && onboardingViewModel.state.value.inputMode == OnboardingInputMode.Voice) {
                    voiceViewModel.disconnect()
                    useLocalVoiceFallback = true
                }
            } else {
                voiceViewModel.sendOnboardingAction(
                    action = "sync_step",
                    extras = mapOf(
                        "step" to state.step.wireName,
                        "name" to state.name,
                        "level" to state.level,
                        "targetLanguage" to state.selectedLanguageCode,
                        "paused" to false,
                        "speakPrompt" to true,
                    ),
                )
            }
        }
    }

    // Drive direct Cartesia TTS + platform SpeechRecognizer when fallback is active
    LaunchedEffect(useLocalVoiceFallback, state.step, state.inputMode, currentPrompt) {
        if (!useLocalVoiceFallback || !state.step.isConversational || state.inputMode != OnboardingInputMode.Voice) {
            return@LaunchedEffect
        }
        ttsPlayer.speak(
            text = currentPrompt,
            language = appLanguage.code,
            nativeLanguage = appLanguage.code,
            onFinished = {
                startLocalListening()
            },
        )
    }

    // Execute `POST /api/me/onboarding` during `5e · Preparing Path`
    LaunchedEffect(state.step, state.pathError) {
        if (state.step == OnboardingStep.Preparing && state.pathError == null) {
            val startedAt = System.currentTimeMillis()
            try {
                val res = onCompleteOnboarding(
                    state.name.takeIf { it.isNotBlank() },
                    state.selectedLanguageCode,
                    appLanguage.code,
                    state.selectedAccentId,
                    state.level,
                    state.focus,
                    state.interests,
                    state.goalNote.takeIf { it.isNotBlank() },
                )
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < 900L) delay(900L - elapsed)
                onboardingViewModel.onPathBuilt(res)
            } catch (e: Exception) {
                onboardingViewModel.onPathFailed(e.message ?: "Couldn't build your path. Please try again.")
            }
        }
    }

    fun handleBack() {
        ttsPlayer.stop()
        runCatching { speechRecognizer?.cancel() }
        voiceViewModel.clearMessages()
        localTranscript = ""
        val handled = onboardingViewModel.stepBack(
            namePrompt = namePrompt,
            levelPrompt = ::buildLevelPrompt,
        )
        if (!handled) {
            voiceViewModel.disconnect()
            onExitOnboarding()
        } else {
            val updated = onboardingViewModel.state.value
            if (!updated.step.isConversational) {
                voiceViewModel.disconnect()
            } else if (agentState != AgentState.DISCONNECTED && !useLocalVoiceFallback) {
                voiceViewModel.sendOnboardingAction(
                    action = "sync_step",
                    extras = mapOf(
                        "step" to updated.step.wireName,
                        "name" to updated.name,
                        "level" to updated.level,
                        "targetLanguage" to updated.selectedLanguageCode,
                        "paused" to (updated.inputMode != OnboardingInputMode.Voice),
                        "speakPrompt" to (updated.inputMode == OnboardingInputMode.Voice),
                    ),
                )
            }
        }
    }

    BackHandler(enabled = state.step != OnboardingStep.Preparing && state.step != OnboardingStep.Ready) {
        handleBack()
    }

    // Compute live orb state & level for `5b`–`5d` Voice Mode
    val orbState: VoiceOrbState = when {
        state.submittingTurn -> VoiceOrbState.Thinking
        useLocalVoiceFallback -> when {
            ttsSpeaking -> VoiceOrbState.Speaking
            localListening -> VoiceOrbState.Listening
            else -> VoiceOrbState.Idle
        }
        else -> when (agentState) {
            AgentState.CONNECTING, AgentState.THINKING -> VoiceOrbState.Thinking
            AgentState.SPEAKING -> VoiceOrbState.Speaking
            AgentState.LISTENING -> VoiceOrbState.Listening
            AgentState.IDLE, AgentState.DISCONNECTED -> if (micGranted()) VoiceOrbState.Listening else VoiceOrbState.Idle
        }
    }

    val liveSpokenTranscript = if (useLocalVoiceFallback) {
        localTranscript
    } else {
        voiceMessages.lastOrNull { it.speaker == Speaker.USER }?.text.orEmpty()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas),
    ) {
        Crossfade(targetState = state.step, label = "onboardingStep") { step ->
            when (step) {
                OnboardingStep.Language -> TargetLanguageStep(
                    languages = languages,
                    selectedLanguage = selectedLanguage,
                    selectedAccentId = state.selectedAccentId ?: selectedLanguage.defaultAccent,
                    appLanguage = appLanguage,
                    onSelectLanguage = onboardingViewModel::selectLanguage,
                    onSelectAccent = onboardingViewModel::selectAccent,
                    onBack = ::handleBack,
                    onContinue = {
                        if (micGranted()) {
                            onboardingViewModel.continueFromLanguage(namePrompt, startInVoice = true)
                        } else {
                            requestVoicePermission(
                                { onboardingViewModel.continueFromLanguage(namePrompt, startInVoice = true) },
                                { onboardingViewModel.continueFromLanguage(namePrompt, startInVoice = false) },
                            )
                        }
                    },
                )

                OnboardingStep.Name, OnboardingStep.Level, OnboardingStep.Goals -> ConversationalOnboardingStep(
                    state = state,
                    step = step,
                    appLanguage = appLanguage,
                    targetLanguageName = targetDisplayName,
                    prompt = if (state.inputMode == OnboardingInputMode.Voice) voicePrompt else currentPrompt,
                    liveTranscript = liveSpokenTranscript,
                    orbState = orbState,
                    onBack = ::handleBack,
                    onMicTap = {
                        if (!micGranted()) {
                            requestVoicePermission(
                                { onboardingViewModel.setInputMode(OnboardingInputMode.Voice) },
                                { onboardingViewModel.setInputMode(OnboardingInputMode.Chat) },
                            )
                        } else if (orbState == VoiceOrbState.Speaking) {
                            if (useLocalVoiceFallback) {
                                ttsPlayer.stop()
                                startLocalListening()
                            } else {
                                voiceViewModel.interrupt()
                            }
                        } else if (useLocalVoiceFallback && !localListening) {
                            startLocalListening()
                        }
                    },
                    onNextInVoice = {
                        val spoken = liveSpokenTranscript.trim()
                        if (spoken.isNotEmpty()) {
                            scope.launch {
                                submitTurnViaApi(step = step, displayText = spoken)
                            }
                        }
                    },
                    onSwitchToChat = {
                        ttsPlayer.stop()
                        runCatching { speechRecognizer?.cancel() }
                        onboardingViewModel.setInputMode(OnboardingInputMode.Chat)
                    },
                    onSwitchToVoice = {
                        if (micGranted()) {
                            onboardingViewModel.setInputMode(OnboardingInputMode.Voice)
                        } else {
                            requestVoicePermission(
                                { onboardingViewModel.setInputMode(OnboardingInputMode.Voice) },
                                {},
                            )
                        }
                    },
                    onDraftChange = onboardingViewModel::updateDraft,
                    onSelectLevelChip = { label, rawKey ->
                        scope.launch {
                            submitTurnViaApi(OnboardingStep.Level, displayText = label, rawAnswer = rawKey)
                        }
                    },
                    onTapGoalChipInVoice = { label ->
                        scope.launch {
                            submitTurnViaApi(OnboardingStep.Goals, displayText = label, rawAnswer = label, chips = listOf(label))
                        }
                    },
                    onToggleGoalChipInChat = onboardingViewModel::toggleGoalChip,
                    onSubmitChat = {
                        val draft = state.draftText.trim()
                        val chips = if (step == OnboardingStep.Goals) state.selectedGoalChips.toList() else emptyList()
                        if (draft.isBlank() && chips.isEmpty()) return@ConversationalOnboardingStep
                        val display = when {
                            draft.isNotBlank() -> draft
                            else -> chips.joinToString(", ")
                        }
                        scope.launch {
                            submitTurnViaApi(step = step, displayText = display, rawAnswer = draft, chips = chips)
                        }
                    },
                )

                OnboardingStep.Preparing -> PreparingPathStep(
                    goalSummary = state.interests.take(2).joinToString(" & ").ifBlank {
                        state.goalNote.trim().take(48)
                    }.ifBlank { null },
                    errorMessage = state.pathError,
                    onRetry = onboardingViewModel::retryBuildPath,
                )

                OnboardingStep.Ready -> {
                    val result = state.result
                    if (result != null) {
                        StartingPathReadyStep(
                            firstName = state.firstName,
                            result = result,
                            busy = busy,
                            onStartFirstLesson = { onStartFirstLesson(result.starterTopic) },
                            onSelectRecommended = onSelectRecommendedTopic,
                            onGoHome = onGoHome,
                        )
                    } else {
                        PreparingPathStep(goalSummary = null, errorMessage = null, onRetry = {})
                    }
                }
            }
        }
    }
}

// ── 5a · Target Language (`2544:20712`) ──────────────────────────────────────

private val PopularLanguageOrder = listOf(
    "es", "fr", "de", "ja", "zh", "ko", "it", "ar", "pt", "en",
)

@Composable
private fun TargetLanguageStep(
    languages: List<LearningLanguage>,
    selectedLanguage: LearningLanguage,
    selectedAccentId: String?,
    appLanguage: AppLanguage,
    onSelectLanguage: (LearningLanguage) -> Unit,
    onSelectAccent: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val filtered = remember(query, languages) {
        if (query.isBlank()) {
            languages.sortedWith(
                compareBy<LearningLanguage> { lang ->
                    val idx = PopularLanguageOrder.indexOf(lang.code)
                    if (idx >= 0) idx else 100
                }.thenBy { it.englishName },
            )
        } else {
            languages.filter { it.matches(query) }
        }
    }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoOnboardingTopBar(
            title = stringResource(R.string.onboarding_top_title),
            onBack = onBack,
        )
        Text(
            text = stringResource(R.string.onboarding_choose_language),
            style = ZunoType.TitleLarge,
            color = ZunoColors.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8),
        )
        Spacer(Modifier.height(ZunoSpace.s16))

        OnboardingLanguageSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.onboarding_search_languages, languages.size),
            modifier = Modifier.padding(horizontal = ZunoSpace.s16),
        )

        Spacer(Modifier.height(ZunoSpace.s16))

        val listShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s16)
                .clip(listShape)
                .background(ZunoColors.SurfaceSubtle)
                .border(1.dp, ZunoColors.BorderSubtle, listShape),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(filtered, key = { _, item -> item.code }) { index, lang ->
                    val isSelected = lang.code == selectedLanguage.code
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) ZunoColors.SurfaceDefault else Color.Transparent)
                            .clickable(role = Role.RadioButton) {
                                focusManager.clearFocus()
                                onSelectLanguage(lang)
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                            ) {
                                Text(
                                    text = lang.nativeName,
                                    style = ZunoType.BodyMediumStrong,
                                    color = ZunoColors.TextPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    text = lang.displayName(appLanguage),
                                    style = ZunoType.CaptionRegular,
                                    color = ZunoColors.TextTertiary,
                                    maxLines = 1,
                                )
                            }
                            if (isSelected) {
                                ZunoIcon(
                                    icon = R.drawable.zuno_ic_check,
                                    size = 18.dp,
                                    tint = ZunoColors.TextPrimary,
                                )
                            }
                        }
                        if (index < filtered.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(ZunoColors.BorderSubtle),
                            )
                        }
                    }
                }
            }

            if (listState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ZunoColors.BgCanvas.copy(alpha = 0.96f),
                                ),
                            ),
                        ),
                )
            }
        }

        AnimatedVisibility(visible = selectedLanguage.accents.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = ZunoSpace.s24, end = ZunoSpace.s24)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                selectedLanguage.accents.forEach { accent ->
                    val chosen = accent.id == selectedAccentId
                    ZunoChip(
                        label = accent.label,
                        selected = chosen,
                        leadingIcon = if (chosen) R.drawable.zuno_ic_check_small else null,
                        onClick = { onSelectAccent(accent.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(ZunoSpace.s20))
        ZunoButton(
            label = stringResource(R.string.onboarding_continue),
            onClick = {
                focusManager.clearFocus()
                onContinue()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
        )
    }
}

@Composable
private fun OnboardingLanguageSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = ZunoType.BodyMedium.copy(color = ZunoColors.TextPrimary),
        cursorBrush = SolidColor(ZunoColors.TextPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, if (focused) ZunoColors.BorderFocus else ZunoColors.BorderSubtle, CircleShape)
                    .padding(start = ZunoSpace.s16, end = ZunoSpace.s8),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_search, 18.dp, tint = ZunoColors.IconSecondary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = ZunoType.BodyMedium, color = ZunoColors.TextPlaceholder, maxLines = 1)
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    ZunoIconButton(
                        icon = R.drawable.zuno_ic_t_x,
                        onClick = { onQueryChange("") },
                        size = ZunoIconButtonSize.Small,
                        contentDescription = stringResource(R.string.common_clear),
                    )
                }
            }
        },
    )
}

// ── 5b / 5c / 5d · Conversational Onboarding (Voice Portrait HUD & Chat) ─────

@Composable
private fun ConversationalOnboardingStep(
    state: OnboardingUiState,
    step: OnboardingStep,
    appLanguage: AppLanguage,
    targetLanguageName: String,
    prompt: String,
    liveTranscript: String,
    orbState: VoiceOrbState,
    onBack: () -> Unit,
    onMicTap: () -> Unit,
    onNextInVoice: () -> Unit,
    onSwitchToChat: () -> Unit,
    onSwitchToVoice: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSelectLevelChip: (label: String, rawKey: String) -> Unit,
    onTapGoalChipInVoice: (label: String) -> Unit,
    onToggleGoalChipInChat: (label: String) -> Unit,
    onSubmitChat: () -> Unit,
) {
    val beginnerLabel = stringResource(R.string.onboarding_level_beginner)
    val intermediateLabel = stringResource(R.string.onboarding_level_intermediate)
    val advancedLabel = stringResource(R.string.onboarding_level_advanced)

    val goalChips = listOf(
        stringResource(R.string.onboarding_goal_work),
        stringResource(R.string.onboarding_goal_travel),
        stringResource(R.string.onboarding_goal_interviews),
        stringResource(R.string.onboarding_goal_daily_chat),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoOnboardingTopBar(
            title = stringResource(R.string.onboarding_top_title),
            onBack = onBack,
        )

        Crossfade(
            targetState = state.inputMode,
            label = "onboardingInputMode",
            modifier = Modifier.weight(1f),
        ) { mode ->
            when (mode) {
                OnboardingInputMode.Voice -> VoiceStepBody(
                    step = step,
                    appLanguage = appLanguage,
                    targetLanguageName = targetLanguageName,
                    prompt = prompt,
                    liveTranscript = liveTranscript,
                    orbState = orbState,
                    submitting = state.submittingTurn,
                    levelChips = listOf(
                        beginnerLabel to "beginner",
                        intermediateLabel to "intermediate",
                        advancedLabel to "advanced",
                    ),
                    goalChips = goalChips,
                    onMicTap = onMicTap,
                    onNext = onNextInVoice,
                    onSelectLevelChip = onSelectLevelChip,
                    onTapGoalChip = onTapGoalChipInVoice,
                    onTypeInstead = onSwitchToChat,
                )

                OnboardingInputMode.Chat -> ChatStepBody(
                    step = step,
                    history = state.chatHistory,
                    draftText = state.draftText,
                    submitting = state.submittingTurn,
                    selectedGoalChips = state.selectedGoalChips,
                    levelChips = listOf(
                        beginnerLabel to "beginner",
                        intermediateLabel to "intermediate",
                        advancedLabel to "advanced",
                    ),
                    goalChips = goalChips,
                    onDraftChange = onDraftChange,
                    onSelectLevelChip = onSelectLevelChip,
                    onToggleGoalChip = onToggleGoalChipInChat,
                    onSpeakInstead = onSwitchToVoice,
                    onSend = onSubmitChat,
                )
            }
        }
    }
}

@Composable
private fun VoiceStepBody(
    step: OnboardingStep,
    appLanguage: AppLanguage,
    targetLanguageName: String,
    prompt: String,
    liveTranscript: String,
    orbState: VoiceOrbState,
    submitting: Boolean,
    levelChips: List<Pair<String, String>>,
    goalChips: List<String>,
    onMicTap: () -> Unit,
    onNext: () -> Unit,
    onSelectLevelChip: (label: String, rawKey: String) -> Unit,
    onTapGoalChip: (label: String) -> Unit,
    onTypeInstead: () -> Unit,
) {
    val stepIndex = when (step) {
        OnboardingStep.Name -> 1
        OnboardingStep.Level -> 2
        OnboardingStep.Goals -> 3
        else -> 1
    }
    val stepLabel = when (step) {
        OnboardingStep.Name -> stringResource(R.string.onboarding_hud_step_name)
        OnboardingStep.Level -> stringResource(R.string.onboarding_hud_step_level, targetLanguageName)
        OnboardingStep.Goals -> stringResource(R.string.onboarding_hud_step_goals)
        else -> ""
    }
    val tutorEyebrow = when (step) {
        OnboardingStep.Name -> stringResource(R.string.onboarding_tutor_eyebrow_name, targetLanguageName)
        OnboardingStep.Level -> stringResource(R.string.onboarding_tutor_eyebrow_level, targetLanguageName)
        OnboardingStep.Goals -> stringResource(R.string.onboarding_tutor_eyebrow_goals)
        else -> stringResource(R.string.onboarding_sender_sofia)
    }
    val youHeader = if (step == OnboardingStep.Name) {
        stringResource(R.string.onboarding_you_speaking_in, appLanguage.nativeLabel)
    } else {
        stringResource(R.string.onboarding_you_speaking)
    }
    val badgeLabel = when (orbState) {
        VoiceOrbState.Listening -> stringResource(R.string.onboarding_badge_listening)
        VoiceOrbState.Speaking -> stringResource(R.string.onboarding_badge_speaking)
        VoiceOrbState.Thinking -> stringResource(R.string.onboarding_badge_thinking)
        VoiceOrbState.Idle -> stringResource(R.string.onboarding_badge_idle)
    }
    val badgeDotColor = when (orbState) {
        VoiceOrbState.Listening -> ZunoColors.StatusListening
        VoiceOrbState.Speaking -> ZunoColors.TextPrimary
        VoiceOrbState.Thinking -> ZunoColors.TextSecondary
        VoiceOrbState.Idle -> ZunoColors.IconTertiary
    }
    val placeholderText = when (step) {
        OnboardingStep.Name -> stringResource(R.string.onboarding_voice_placeholder_name)
        OnboardingStep.Level -> stringResource(R.string.onboarding_voice_placeholder_level)
        OnboardingStep.Goals -> stringResource(R.string.onboarding_voice_placeholder_goals)
        else -> ""
    }
    val quotedPrompt = remember(prompt) {
        val trimmed = prompt.trim()
        if (trimmed.startsWith("“") && trimmed.endsWith("”")) trimmed else "“$trimmed”"
    }
    val canAdvance = !submitting && liveTranscript.isNotBlank()
    val isMicActive = orbState == VoiceOrbState.Listening || orbState == VoiceOrbState.Speaking

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portraitHeight = (maxHeight * 0.49f).coerceIn(220.dp, 380.dp)

        // Portrait Stage Card (`2805:14014` / `2805:14015` / `2805:14016`)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(portraitHeight),
        ) {
            Image(
                painter = painterResource(R.drawable.sofia_tutor_avatar),
                contentDescription = stringResource(R.string.onboarding_sender_sofia),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            // Top Scrim
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to ZunoColors.BgCanvas,
                            0.45f to ZunoColors.BgCanvas.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            // Bottom Scrim
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to ZunoColors.BgCanvas.copy(alpha = 0.62f),
                            0.85f to ZunoColors.BgCanvas.copy(alpha = 0.96f),
                            1f to ZunoColors.BgCanvas,
                        ),
                    ),
            )
        }

        // Tactile HUD · S5 + Call controls anchored at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s20),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                // HUD Status Pill
                val pillShape = RoundedCornerShape(ZunoRadius.md)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(pillShape)
                        .background(ZunoColors.SurfaceSubtle)
                        .border(1.dp, ZunoColors.BorderSubtle, pillShape)
                        .padding(horizontal = ZunoSpace.s14, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_hud_step, stepIndex),
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                    )
                    Text(
                        text = stepLabel,
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tutor Question Card
                val questionShape = RoundedCornerShape(ZunoRadius.xxxl)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(questionShape)
                        .background(ZunoColors.SurfaceDefault)
                        .border(1.dp, ZunoColors.BorderDefault, questionShape)
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    Text(
                        text = tutorEyebrow,
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = quotedPrompt,
                        style = ZunoType.Headline,
                        color = ZunoColors.TextPrimary,
                    )
                    when (step) {
                        OnboardingStep.Name -> {
                            Text(
                                text = stringResource(R.string.onboarding_name_helper),
                                style = ZunoType.BodySmall,
                                color = ZunoColors.TextSecondary,
                            )
                        }
                        OnboardingStep.Level -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                levelChips.forEach { (label, key) ->
                                    ZunoChip(
                                        label = label,
                                        onClick = { if (!submitting) onSelectLevelChip(label, key) },
                                    )
                                }
                            }
                        }
                        OnboardingStep.Goals -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                goalChips.forEach { chip ->
                                    ZunoChip(
                                        label = chip,
                                        onClick = { if (!submitting) onTapGoalChip(chip) },
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                // You & Feedback · HUD
                val hudShape = RoundedCornerShape(ZunoRadius.xl)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(hudShape)
                        .background(ZunoColors.SurfaceSubtle)
                        .border(1.dp, ZunoColors.BorderDefault, hudShape)
                        .clickable(role = Role.Button, onClick = onMicTap)
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = youHeader,
                            style = ZunoType.FootnoteStrong,
                            color = ZunoColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(end = ZunoSpace.s8),
                        )
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ZunoColors.SurfaceStrong)
                                .padding(horizontal = ZunoSpace.s8, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeDotColor),
                            )
                            Text(
                                text = badgeLabel,
                                style = ZunoType.Micro,
                                color = ZunoColors.TextPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = if (liveTranscript.isNotBlank()) "“${liveTranscript.trim()}”" else placeholderText,
                        style = ZunoType.BodyMedium,
                        color = if (liveTranscript.isNotBlank()) ZunoColors.TextPrimary else ZunoColors.TextPlaceholder,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Call controls (`2749:13290` / `2749:13363` / `2749:13434`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16)
                    .height(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isMicActive) ZunoColors.SurfaceStronger else ZunoColors.SurfaceDefault)
                        .border(
                            1.dp,
                            if (isMicActive) ZunoColors.BorderDefault else ZunoColors.BorderSubtle,
                            CircleShape,
                        )
                        .clickable(role = Role.Button, onClick = onMicTap),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(
                        icon = R.drawable.zuno_ic_mic,
                        size = 24.dp,
                        tint = ZunoColors.IconPrimary,
                        contentDescription = stringResource(
                            if (orbState == VoiceOrbState.Speaking) R.string.onboarding_status_speaking
                            else R.string.onboarding_badge_listening
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceSubtle)
                        .border(1.dp, ZunoColors.BorderDefault, CircleShape)
                        .clickable(role = Role.Button, onClick = onTypeInstead)
                        .padding(horizontal = ZunoSpace.s20),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_keyboard, 18.dp, tint = ZunoColors.IconPrimary)
                    Text(
                        text = stringResource(R.string.onboarding_type_instead),
                        style = ZunoType.LabelLarge,
                        color = ZunoColors.TextPrimary,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .alpha(if (canAdvance) 1f else 0.4f)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceDefault)
                        .clickable(enabled = canAdvance, role = Role.Button, onClick = onNext),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(
                        icon = R.drawable.zuno_ic_arrow_right,
                        size = 24.dp,
                        tint = ZunoColors.IconPrimary,
                        contentDescription = stringResource(R.string.onboarding_next_step),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatStepBody(
    step: OnboardingStep,
    history: List<OnboardingChatItem>,
    draftText: String,
    submitting: Boolean,
    selectedGoalChips: Set<String>,
    levelChips: List<Pair<String, String>>,
    goalChips: List<String>,
    onDraftChange: (String) -> Unit,
    onSelectLevelChip: (label: String, rawKey: String) -> Unit,
    onToggleGoalChip: (label: String) -> Unit,
    onSpeakInstead: () -> Unit,
    onSend: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val sofiaLabel = stringResource(R.string.onboarding_sender_sofia)
    var listHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(history.size, step) {
        if (history.isNotEmpty()) {
            delay(80)
            listState.animateScrollToItem(history.lastIndex)
        }
    }
    LaunchedEffect(listHeightPx) {
        if (listHeightPx > 0 && history.isNotEmpty()) {
            listState.scrollToItem(history.lastIndex)
        }
    }
    LaunchedEffect(step) {
        runCatching { focusRequester.requestFocus() }
    }

    val canSend = !submitting && (
        draftText.isNotBlank() || (step == OnboardingStep.Goals && selectedGoalChips.isNotEmpty())
    )

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { listHeightPx = it.height },
            contentPadding = PaddingValues(
                start = ZunoSpace.s24,
                end = ZunoSpace.s24,
                top = ZunoSpace.s12,
                bottom = ZunoSpace.s8,
            ),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            itemsIndexed(history, key = { _, item -> item.id }) { _, item ->
                when (item.role) {
                    OnboardingChatRole.Assistant -> ZunoAssistantBubble(
                        message = item.text,
                        senderLabel = sofiaLabel,
                    )
                    OnboardingChatRole.User -> ZunoUserBubble(
                        message = item.text,
                    )
                }
            }
        }

        when (step) {
            OnboardingStep.Level -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s6)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally),
                ) {
                    levelChips.forEach { (label, key) ->
                        ZunoChip(
                            label = label,
                            onClick = { if (!submitting) onSelectLevelChip(label, key) },
                        )
                    }
                }
            }
            OnboardingStep.Goals -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s6)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally),
                ) {
                    goalChips.forEach { chip ->
                        ZunoChip(
                            label = chip,
                            selected = chip in selectedGoalChips,
                            onClick = { if (!submitting) onToggleGoalChip(chip) },
                        )
                    }
                }
            }
            else -> Unit
        }

        OnboardingComposer(
            value = draftText,
            onValueChange = onDraftChange,
            placeholder = stringResource(R.string.onboarding_composer_placeholder),
            canSend = canSend,
            submitting = submitting,
            enabled = !submitting,
            focusRequester = focusRequester,
            onSpeakInstead = onSpeakInstead,
            onSend = onSend,
            modifier = Modifier.padding(
                start = ZunoSpace.s16,
                end = ZunoSpace.s16,
                top = ZunoSpace.s4,
                bottom = ZunoSpace.s12,
            ),
        )
    }
}

@Composable
private fun OnboardingComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    canSend: Boolean,
    submitting: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onSpeakInstead: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceStrong)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .padding(
                start = ZunoSpace.s16,
                end = ZunoSpace.s10,
                top = ZunoSpace.s14,
                bottom = ZunoSpace.s10,
            ),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            maxLines = 4,
            textStyle = ZunoType.BodyMedium.copy(color = ZunoColors.TextPrimary),
            cursorBrush = SolidColor(ZunoColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = ZunoSpace.s6)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = ZunoType.BodyMedium, color = ZunoColors.TextPlaceholder)
                    }
                    inner()
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSpeakInstead)
                    .padding(start = ZunoSpace.s4, end = ZunoSpace.s12),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoIcon(R.drawable.zuno_ic_mic, 16.dp, tint = ZunoColors.TextSecondary)
                Text(
                    text = stringResource(R.string.onboarding_speak_instead),
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextSecondary,
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (canSend || submitting) 1f else 0.4f)
                    .clip(CircleShape)
                    .background(if (canSend || submitting) ZunoColors.SurfaceStronger else ZunoColors.SurfaceDefault)
                    .then(
                        if (canSend || submitting) Modifier.border(1.dp, ZunoColors.BorderDefault, CircleShape)
                        else Modifier
                    )
                    .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (submitting) {
                    ZunoSpinner(size = 18.dp, color = ZunoColors.TextPrimary)
                } else {
                    ZunoIcon(R.drawable.zuno_ic_arrow_up, 20.dp, contentDescription = "Send")
                }
            }
        }
    }
}

// ── 5e · Preparing Path (`2544:21444`) ───────────────────────────────────────

@Composable
private fun PreparingPathStep(
    goalSummary: String?,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    val subtitle = errorMessage ?: if (!goalSummary.isNullOrBlank()) {
        stringResource(R.string.onboarding_building_subtitle_named, goalSummary)
    } else {
        stringResource(R.string.onboarding_building_subtitle)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoOnboardingTopBar(title = stringResource(R.string.onboarding_top_title))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s40),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                ZunoSpinner(size = 20.dp, color = ZunoColors.TextPrimary)
                Text(
                    text = stringResource(R.string.onboarding_building_title),
                    style = ZunoType.WebTitle,
                    color = ZunoColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(ZunoSpace.s8))
                    ZunoButton(
                        label = stringResource(R.string.onboarding_continue),
                        onClick = onRetry,
                        size = ZunoButtonSize.Medium,
                    )
                }
            }
        }
    }
}

// ── 5f · Starting Path Ready (`2544:21856`) ──────────────────────────────────

@Composable
private fun StartingPathReadyStep(
    firstName: String,
    result: OnboardingCompleteResult,
    busy: Boolean,
    onStartFirstLesson: () -> Unit,
    onSelectRecommended: (RecommendedTopic) -> Unit,
    onGoHome: () -> Unit,
) {
    val starter = result.starterTopic
    val starterObjective = starter.lessons.firstOrNull()?.objective?.takeIf { it.isNotBlank() }
        ?: starter.summary.translation
    val starterCardShape = RoundedCornerShape(20.dp)
    val recGroupShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoOnboardingTopBar(title = stringResource(R.string.onboarding_top_title))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = ZunoSpace.s20),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            ) {
                Text(
                    text = if (firstName.isNotBlank()) {
                        stringResource(R.string.onboarding_ready_title_named, firstName)
                    } else {
                        stringResource(R.string.onboarding_ready_title)
                    },
                    style = ZunoType.TitleLarge,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = result.pathSubtitle.ifBlank { stringResource(R.string.onboarding_ready_subtitle_default) },
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextTertiary,
                )
            }

            // Starter Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(starterCardShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, ZunoColors.BorderSubtle, starterCardShape)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_starter_eyebrow, starter.summary.totalLessons),
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextTertiary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                    Text(
                        text = starter.summary.title,
                        style = StarterCardTitleStyle,
                        color = ZunoColors.TextPrimary,
                    )
                    Text(
                        text = starterObjective,
                        style = ZunoType.BodySmall,
                        color = ZunoColors.TextSecondary,
                    )
                }
                ZunoButton(
                    label = stringResource(R.string.onboarding_start_first_lesson),
                    onClick = onStartFirstLesson,
                    size = ZunoButtonSize.Medium,
                    enabled = !busy,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Recommended next
            if (result.recommendedTopics.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_recommended_next),
                        style = ZunoType.LabelSmall,
                        color = ZunoColors.TextTertiary,
                        modifier = Modifier.padding(start = ZunoSpace.s8),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(recGroupShape)
                            .background(ZunoColors.SurfaceSubtle)
                            .border(1.dp, ZunoColors.BorderSubtle, recGroupShape),
                    ) {
                        val visibleRecommendations = result.recommendedTopics.take(2)
                        visibleRecommendations.forEachIndexed { index, rec ->
                            val categoryTag = rec.reason.ifBlank { rec.translation }
                            val lessonsLabel = stringResource(R.string.onboarding_lessons_count, 5)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy, role = Role.Button) { onSelectRecommended(rec) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 18.dp, end = ZunoSpace.s16, top = ZunoSpace.s14, bottom = ZunoSpace.s14),
                                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                                    ) {
                                        Text(
                                            text = rec.title,
                                            style = ZunoType.BodyMediumStrong,
                                            color = ZunoColors.TextPrimary,
                                        )
                                        Text(
                                            text = "$categoryTag · $lessonsLabel",
                                            style = ZunoType.CaptionRegular,
                                            color = ZunoColors.TextTertiary,
                                        )
                                    }
                                    ZunoIcon(
                                        icon = R.drawable.zuno_ic_chevron_right_muted,
                                        size = 16.dp,
                                    )
                                }
                                if (index < visibleRecommendations.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(ZunoColors.BorderSubtle),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
            contentAlignment = Alignment.Center,
        ) {
            ZunoButton(
                label = stringResource(R.string.onboarding_go_home),
                onClick = onGoHome,
                variant = ZunoButtonVariant.Ghost,
                size = ZunoButtonSize.Medium,
                enabled = !busy,
            )
        }
    }
}
