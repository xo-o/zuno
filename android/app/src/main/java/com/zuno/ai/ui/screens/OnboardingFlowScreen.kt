package com.zuno.ai.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuno.ai.R
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.LessonState
import com.zuno.ai.data.OnboardingCompleteResult
import com.zuno.ai.data.OnboardingTurnResult
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicLesson
import com.zuno.ai.ui.components.LessonStatus
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoLessonRow
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoOnboardingTopBar
import com.zuno.ai.ui.components.ZunoSectionHeader
import com.zuno.ai.ui.components.ZunoSpinner
import com.zuno.ai.ui.components.ZunoTopicUpNextCard
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassBorderElevatedBrush
import com.zuno.ai.ui.theme.ZunoGlassBorderSubtleBrush
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.viewmodel.OnboardingChatItem
import com.zuno.ai.viewmodel.OnboardingChatRole
import com.zuno.ai.viewmodel.OnboardingStep
import com.zuno.ai.viewmodel.OnboardingUiState
import com.zuno.ai.viewmodel.OnboardingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 01 · Onboarding (`5a` Your Language -> `5b` Target Language -> `5c` Name -> `5d` Level -> `5e` Goals -> `5f` Preparing -> `5g` Starting Path Ready).
 * Chat-only conversational onboarding with quick-option chips and synchronized localization.
 */
@Composable
fun OnboardingFlowScreen(
    onboardingViewModel: OnboardingViewModel,
    languages: List<LearningLanguage>,
    appLanguage: AppLanguage,
    busy: Boolean,
    onSelectAppLanguage: (AppLanguage) -> Unit,
    onConfirmAppLanguage: (AppLanguage) -> Unit,
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
    onStartLesson: (TopicDetail, TopicLesson?) -> Unit,
    onGoHome: () -> Unit,
    onExitOnboarding: () -> Unit,
    onError: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by onboardingViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val selectedLanguage = remember(state.selectedLanguageCode, languages) {
        languages.firstOrNull { it.code == state.selectedLanguageCode }
            ?: LearningLanguages.find(state.selectedLanguageCode)
    }
    val targetDisplayName = selectedLanguage.displayName(appLanguage)

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

    suspend fun submitTurnViaApi(
        step: OnboardingStep,
        displayText: String,
        rawAnswer: String = displayText,
        chips: List<String> = emptyList(),
    ) {
        if (state.submittingTurn) return
        onboardingViewModel.setSubmittingTurn(true)
        try {
            val parsed = onParseTurn(
                step.wireName,
                rawAnswer,
                chips,
                state.selectedLanguageCode,
                state.selectedAppLanguage.code,
            )
            if (!parsed.valid) {
                val retry = parsed.retryPrompt?.takeIf { it.isNotBlank() } ?: currentPrompt
                onboardingViewModel.appendUserAndRetry(step, displayText, retry)
            } else {
                onboardingViewModel.applyParsedTurn(
                    step = step,
                    userDisplayText = displayText,
                    parsed = parsed,
                    levelPromptBuilder = ::buildLevelPrompt,
                    goalsPromptBuilder = ::buildGoalsPrompt,
                )
            }
        } catch (e: Exception) {
            Log.w("OnboardingFlow", "submitTurnViaApi failed on step=${step.wireName}", e)
            onboardingViewModel.setSubmittingTurn(false)
            onError(e.message ?: "Something went wrong. Please try again.")
        }
    }

    // Execute `POST /api/me/onboarding` during `5e · Preparing Path`
    LaunchedEffect(state.step, state.pathError) {
        if (state.step == OnboardingStep.Preparing && state.pathError == null) {
            val startedAt = System.currentTimeMillis()
            try {
                val res = onCompleteOnboarding(
                    state.name.takeIf { it.isNotBlank() },
                    state.selectedLanguageCode,
                    state.selectedAppLanguage.code,
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
        val handled = onboardingViewModel.stepBack(
            namePrompt = namePrompt,
            levelPrompt = ::buildLevelPrompt,
        )
        if (!handled) {
            onExitOnboarding()
        }
    }

    BackHandler(enabled = state.step != OnboardingStep.Preparing && state.step != OnboardingStep.Ready) {
        handleBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground(),
    ) {
        Crossfade(targetState = state.step, label = "onboardingStep") { step ->
            when (step) {
                OnboardingStep.UserLanguage -> UserLanguageStep(
                    selectedAppLanguage = state.selectedAppLanguage,
                    onSelectAppLanguage = { lang ->
                        onboardingViewModel.selectAppLanguage(lang)
                        onSelectAppLanguage(lang)
                    },
                    onBack = ::handleBack,
                    onContinue = {
                        onConfirmAppLanguage(state.selectedAppLanguage)
                        onboardingViewModel.continueFromUserLanguage()
                    },
                )

                OnboardingStep.Language -> TargetLanguageStep(
                    languages = languages,
                    selectedLanguage = selectedLanguage,
                    selectedAccentId = state.selectedAccentId ?: selectedLanguage.defaultAccent,
                    appLanguage = appLanguage,
                    onSelectLanguage = onboardingViewModel::selectLanguage,
                    onSelectAccent = onboardingViewModel::selectAccent,
                    onBack = ::handleBack,
                    onContinue = {
                        onboardingViewModel.continueFromLanguage(namePrompt)
                    },
                )

                OnboardingStep.Name, OnboardingStep.Level, OnboardingStep.Goals -> ChatOnboardingStep(
                    state = state,
                    step = step,
                    onBack = ::handleBack,
                    onDraftChange = onboardingViewModel::updateDraft,
                    onSelectLevelChip = onboardingViewModel::selectLevelChip,
                    onToggleGoalChip = onboardingViewModel::toggleGoalChip,
                    onSubmitChat = {
                        val draft = state.draftText.trim()
                        if (draft.isBlank()) return@ChatOnboardingStep
                        val chips = if (step == OnboardingStep.Goals) state.selectedGoalChips.toList() else emptyList()
                        scope.launch {
                            submitTurnViaApi(step = step, displayText = draft, rawAnswer = draft, chips = chips)
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
                            selectedLanguage = selectedLanguage,
                            selectedAccentId = state.selectedAccentId ?: selectedLanguage.defaultAccent,
                            appLanguage = appLanguage,
                            result = result,
                            busy = busy,
                            onStartLesson = { lesson -> onStartLesson(result.starterTopic, lesson) },
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

// ── User Language Step (Native / App language) ───────────────────────────────

@Composable
private fun UserLanguageStep(
    selectedAppLanguage: AppLanguage,
    onSelectAppLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val listState = rememberLazyListState()
    val listShape = RoundedCornerShape(ZunoRadius.xxxxl)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoOnboardingTopBar(
            title = stringResource(R.string.onboarding_top_title),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        ) {
            Text(
                text = stringResource(R.string.onboarding_user_language_title),
                style = ZunoType.TitleLarge,
                color = ZunoColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.onboarding_user_language_subtitle),
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(ZunoSpace.s20))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s24),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(listShape),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(AppLanguage.entries, key = { _, item -> item.code }) { index, language ->
                        val isSelected = language == selectedAppLanguage
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) ZunoColors.SurfaceDefault else Color.Transparent)
                                .clickable(role = Role.RadioButton) {
                                    onSelectAppLanguage(language)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 62.dp)
                                    .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s10),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                                ) {
                                    Text(
                                        text = language.nativeLabel,
                                        style = ZunoType.BodyMediumStrong,
                                        color = ZunoColors.TextPrimary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = stringResource(language.subtitleRes),
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
                            if (index < AppLanguage.entries.lastIndex) {
                                ZunoGlassHairline()
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
        }

        Spacer(Modifier.height(ZunoSpace.s20))
        ZunoButton(
            label = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
        )
    }
}

// ── 5b · Target Language (`2544:20712`) ──────────────────────────────────────

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        ) {
            Text(
                text = stringResource(R.string.onboarding_choose_language),
                style = ZunoType.TitleLarge,
                color = ZunoColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.onboarding_target_language_subtitle),
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(ZunoSpace.s14))

        OnboardingLanguageSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.onboarding_search_languages, languages.size),
            modifier = Modifier.padding(horizontal = ZunoSpace.s24),
        )

        Spacer(Modifier.height(ZunoSpace.s16))

        val listShape = RoundedCornerShape(ZunoRadius.xxxxl)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s24)
                .zunoGlassSubtle(listShape),
        ) {
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ZunoSpace.s24),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_no_languages_match, query.trim()),
                        style = ZunoType.BodySmall,
                        color = ZunoColors.TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
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
                                    .heightIn(min = 62.dp)
                                    .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s10),
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
                                ZunoGlassHairline()
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
        }

        AnimatedVisibility(visible = selectedLanguage.accents.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ZunoSpace.s16, start = ZunoSpace.s24, end = ZunoSpace.s24)
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
                    .then(
                        if (focused) {
                            Modifier.zunoGlassElevated(CircleShape, borderBrush = SolidColor(ZunoColors.BorderFocus))
                        } else {
                            Modifier.zunoGlassSubtle(
                                CircleShape,
                                borderBrush = ZunoGlassBorderSubtleBrush,
                                fillColor = ZunoColors.SurfaceDefault,
                            )
                        },
                    )
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

// ── 5c / 5d / 5e · Conversational Onboarding (Chat-only) ─────────────────────

@Composable
private fun ChatOnboardingStep(
    state: OnboardingUiState,
    step: OnboardingStep,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSelectLevelChip: (label: String) -> Unit,
    onToggleGoalChip: (label: String) -> Unit,
    onSubmitChat: () -> Unit,
) {
    val levelChips = listOf(
        stringResource(R.string.onboarding_level_beginner),
        stringResource(R.string.onboarding_level_intermediate),
        stringResource(R.string.onboarding_level_advanced),
    )

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

        ChatStepBody(
            step = step,
            history = state.chatHistory,
            draftText = state.draftText,
            submitting = state.submittingTurn,
            selectedGoalChips = state.selectedGoalChips,
            levelChips = levelChips,
            goalChips = goalChips,
            onDraftChange = onDraftChange,
            onSelectLevelChip = onSelectLevelChip,
            onToggleGoalChip = onToggleGoalChip,
            onSend = onSubmitChat,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChatStepBody(
    step: OnboardingStep,
    history: List<OnboardingChatItem>,
    draftText: String,
    submitting: Boolean,
    selectedGoalChips: Set<String>,
    levelChips: List<String>,
    goalChips: List<String>,
    onDraftChange: (String) -> Unit,
    onSelectLevelChip: (label: String) -> Unit,
    onToggleGoalChip: (label: String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val zunoLabel = stringResource(R.string.onboarding_sender_zuno)
    val youLabel = stringResource(R.string.onboarding_sender_you)
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
        if (step == OnboardingStep.Level) {
            focusManager.clearFocus()
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val canSend = !submitting && draftText.isNotBlank()

    Column(modifier = modifier.fillMaxSize()) {
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
                        senderLabel = zunoLabel,
                    )
                    OnboardingChatRole.User -> ZunoUserBubble(
                        message = item.text,
                        senderLabel = youLabel,
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
                    levelChips.forEach { label ->
                        val selected = draftText.trim().equals(label, ignoreCase = true)
                        ZunoChip(
                            label = label,
                            selected = selected,
                            onClick = {
                                if (!submitting) {
                                    onSelectLevelChip(label)
                                    runCatching { focusRequester.requestFocus() }
                                }
                            },
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
                        val selected = selectedGoalChips.any { it.equals(chip, ignoreCase = true) } ||
                            draftText.split(',').any { it.trim().equals(chip, ignoreCase = true) }
                        ZunoChip(
                            label = chip,
                            selected = selected,
                            onClick = {
                                if (!submitting) {
                                    onToggleGoalChip(chip)
                                    runCatching { focusRequester.requestFocus() }
                                }
                            },
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
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    if (textFieldValue.text != value) {
        textFieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zunoGlassElevated(
                shape = shape,
                borderBrush = ZunoGlassBorderSubtleBrush,
                fillColor = ZunoColors.SurfaceStrong,
            )
            .padding(
                start = ZunoSpace.s16,
                end = ZunoSpace.s10,
                top = ZunoSpace.s14,
                bottom = ZunoSpace.s10,
            ),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { updated ->
                textFieldValue = updated
                if (updated.text != value) {
                    onValueChange(updated.text)
                }
            },
            enabled = enabled,
            maxLines = 4,
            textStyle = ZunoType.BodyMedium.copy(color = ZunoColors.TextPrimary),
            cursorBrush = SolidColor(ZunoColors.TextPrimary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
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
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val active = canSend || submitting
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (active) 1f else 0.4f)
                    .zunoGlassElevated(
                        shape = CircleShape,
                        borderBrush = if (active) ZunoGlassBorderElevatedBrush else null,
                        fillColor = if (active) ZunoColors.SurfaceStronger else ZunoColors.SurfaceDefault,
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

// ── 5f · Preparing Path (`2544:21444`) ───────────────────────────────────────

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
                    style = ZunoType.TitleMedium,
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
                        label = stringResource(R.string.common_try_again),
                        onClick = onRetry,
                        size = ZunoButtonSize.Medium,
                    )
                }
            }
        }
    }
}

// ── 5g · Starting Path Ready (`2544:21856`) ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartingPathReadyStep(
    firstName: String,
    selectedLanguage: LearningLanguage,
    selectedAccentId: String?,
    appLanguage: AppLanguage,
    result: OnboardingCompleteResult,
    busy: Boolean,
    onStartLesson: (TopicLesson?) -> Unit,
    onGoHome: () -> Unit,
) {
    val starter = result.starterTopic
    val firstLesson = starter.nextLesson ?: starter.lessons.firstOrNull()
    val totalLessons = starter.summary.totalLessons.coerceAtLeast(starter.lessons.size).coerceAtLeast(1)

    val languageDisplay = selectedLanguage.displayName(appLanguage)
    val languageChipLabel = if (selectedLanguage.accents.size > 1) {
        val accent = selectedLanguage.accentLabel(selectedAccentId)
        if (accent.isNotBlank()) "$languageDisplay · $accent" else languageDisplay
    } else {
        languageDisplay
    }
    val levelName = when (result.profile.level.uppercase()) {
        "A1", "A2" -> stringResource(R.string.onboarding_level_beginner)
        "C1", "C2" -> stringResource(R.string.onboarding_level_advanced)
        else -> stringResource(R.string.onboarding_level_intermediate)
    }
    val levelChipLabel = "$levelName · ${result.profile.level}"

    val baseSubtitle = result.pathSubtitle
        .ifBlank { stringResource(R.string.onboarding_ready_subtitle_default) }
        .trimEnd('.', ' ')
    val subtitleText = "$baseSubtitle · ${stringResource(R.string.onboarding_starter_lessons_suffix, totalLessons)}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoOnboardingTopBar(
            title = stringResource(R.string.onboarding_top_title),
            onBack = if (!busy) onGoHome else null,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ZunoSpace.s8, bottom = ZunoSpace.s4),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    ZunoChip(
                        label = languageChipLabel,
                        leadingIcon = R.drawable.zuno_ic_t_sparkles,
                        leadingIconTint = ZunoColors.IconPrimary,
                        selected = true,
                        onClick = {},
                    )
                    ZunoChip(
                        label = levelChipLabel,
                        selected = false,
                        onClick = {},
                    )
                }

                Text(
                    text = if (firstName.isNotBlank()) {
                        stringResource(R.string.onboarding_ready_title_named, firstName)
                    } else {
                        stringResource(R.string.onboarding_ready_title)
                    },
                    style = ZunoType.Display,
                    color = ZunoColors.TextPrimary,
                )

                Text(
                    text = subtitleText,
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                )
            }

            ZunoTopicUpNextCard(
                eyebrow = stringResource(
                    R.string.onboarding_up_next_eyebrow,
                    firstLesson?.position ?: 1,
                    totalLessons,
                ),
                title = starter.summary.title,
                actionLabel = stringResource(R.string.onboarding_up_next_start),
                busy = busy,
                onAction = { if (!busy) onStartLesson(firstLesson) },
            )

            if (starter.lessons.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    ZunoSectionHeader(
                        title = stringResource(R.string.onboarding_syllabus_header, starter.lessons.size),
                    )
                    ZunoListGroup {
                        starter.lessons.forEachIndexed { index, lesson ->
                            val isCurrent = lesson.state == LessonState.Current ||
                                (starter.nextLesson == null && index == 0)
                            ZunoLessonRow(
                                number = lesson.position,
                                title = lesson.title,
                                status = when {
                                    lesson.state == LessonState.Done -> LessonStatus.Done
                                    isCurrent -> LessonStatus.Current
                                    else -> LessonStatus.Upcoming
                                },
                                meta = when {
                                    lesson.state == LessonState.Done -> stringResource(R.string.topics_lesson_done_meta)
                                    isCurrent -> stringResource(R.string.onboarding_lesson_up_next)
                                    else -> stringResource(R.string.topics_lesson_est_min)
                                },
                                showDivider = index > 0,
                                onClick = { if (!busy) onStartLesson(lesson) },
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            ZunoButton(
                label = stringResource(R.string.onboarding_start_first_lesson),
                onClick = { onStartLesson(firstLesson) },
                size = ZunoButtonSize.Large,
                enabled = !busy,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
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
