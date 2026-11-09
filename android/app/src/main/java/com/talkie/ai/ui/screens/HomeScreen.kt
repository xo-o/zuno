package com.talkie.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.talkie.ai.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.talkie.ai.data.LearningLanguage
import com.talkie.ai.ui.components.AgentGlowState
import com.talkie.ai.ui.components.ZunoSelectorTopBar
import com.talkie.ai.ui.components.ZunoAgentGlow
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoButtonSize
import com.talkie.ai.ui.components.ZunoComposer
import com.talkie.ai.ui.components.ZunoDraftAttachment
import com.talkie.ai.ui.components.ZunoSpinner
import com.talkie.ai.ui.components.ZunoTutorHint
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import com.talkie.ai.viewmodel.DraftAttachment
import com.talkie.ai.viewmodel.DraftUploadState

/** Greeting copy: the prompt in the language being learned, its translation in the app language. */
data class HomeCopy(val prompt: String, val translation: String, val placeholder: String) {
    companion object {
        /** [translation] is the app-language greeting; it's hidden when it matches the prompt. */
        fun forLanguage(language: LearningLanguage, firstName: String, translation: String): HomeCopy {
            val prompt = language.greeting(firstName)
            return HomeCopy(prompt, translation.takeIf { it != prompt }.orEmpty(), language.placeholder)
        }
    }
}

/** Voice states for the glow; drives caption, prompt and footer hints. */
enum class HomeVoiceState { Idle, Listening, Cancelling, Sending }

/**
 * Home — V3 Glow. Hold the glow to talk (release sends, slide down cancels) or type instead.
 * Both paths open a new Free Talk session.
 */
@Composable
fun HomeScreen(
    copy: HomeCopy,
    voiceState: HomeVoiceState,
    liveTranscript: String,
    audioLevel: () -> Float,
    micBlocked: Boolean,
    busy: Boolean,
    typingHint: String?,
    /** "Spanish · B1" — active language and level. */
    selector: String,
    onOpenDrawer: () -> Unit,
    onSelector: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldMove: (cancelling: Boolean) -> Unit,
    onHoldEnd: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendText: (String) -> Unit,
    onTypeModeChanged: (Boolean) -> Unit,
    drafts: List<DraftAttachment>,
    onAttach: () -> Unit,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onRetryAttachment: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    fun setTyping(value: Boolean) {
        typing = value
        onTypeModeChanged(value)
    }
    BackHandler(enabled = typing) { setTyping(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoSelectorTopBar(selector = selector, onMenu = onOpenDrawer, onSelector = onSelector)
        Crossfade(targetState = typing, label = "homeMode", modifier = Modifier.weight(1f)) { isTyping ->
            if (isTyping) {
                TypeMode(
                    copy = copy,
                    busy = busy,
                    typingHint = typingHint,
                    onDraftChanged = onDraftChanged,
                    onSend = onSendText,
                    onVoice = { setTyping(false) },
                    drafts = drafts,
                    onAttach = onAttach,
                    onRemoveAttachment = onRemoveAttachment,
                    onRetryAttachment = onRetryAttachment,
                )
            } else {
                GlowMode(
                    copy = copy,
                    voiceState = voiceState,
                    liveTranscript = liveTranscript,
                    audioLevel = audioLevel,
                    micBlocked = micBlocked,
                    busy = busy,
                    onHoldStart = onHoldStart,
                    onHoldMove = onHoldMove,
                    onHoldEnd = onHoldEnd,
                    onAllowMicrophone = onAllowMicrophone,
                    onTypeInstead = { setTyping(true) },
                )
            }
        }
    }
}

@Composable
private fun GlowMode(
    copy: HomeCopy,
    voiceState: HomeVoiceState,
    liveTranscript: String,
    audioLevel: () -> Float,
    micBlocked: Boolean,
    busy: Boolean,
    onHoldStart: () -> Unit,
    onHoldMove: (Boolean) -> Unit,
    onHoldEnd: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    val holding = voiceState == HomeVoiceState.Listening || voiceState == HomeVoiceState.Cancelling
    val cancelThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val currentStart by rememberUpdatedState(onHoldStart)
    val currentMove by rememberUpdatedState(onHoldMove)
    val currentEnd by rememberUpdatedState(onHoldEnd)
    val interactive = !micBlocked && !busy && voiceState != HomeVoiceState.Sending

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s8),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val horizonHeight = min(600.dp, maxHeight)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(horizonHeight)
                    .clip(RoundedCornerShape(ZunoRadius.xxxl))
                    .semantics { contentDescription = "Hold to talk" }
                    .then(
                        if (interactive) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    currentStart()
                                    var cancelling = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val shouldCancel = change.position.y - down.position.y > cancelThresholdPx
                                        if (shouldCancel != cancelling) {
                                            cancelling = shouldCancel
                                            currentMove(cancelling)
                                        }
                                        change.consume()
                                    }
                                    currentEnd()
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                ZunoAgentGlow(
                    state = when {
                        micBlocked -> AgentGlowState.Disabled
                        voiceState == HomeVoiceState.Sending || busy -> AgentGlowState.Thinking
                        voiceState == HomeVoiceState.Cancelling -> AgentGlowState.Cancelling
                        voiceState == HomeVoiceState.Listening -> AgentGlowState.Listening
                        else -> AgentGlowState.Idle
                    },
                    audioLevel = audioLevel,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = horizonHeight * 0.36f)
                        .width(300.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    when {
                        micBlocked -> MicBlockedContent(onAllowMicrophone)
                        voiceState == HomeVoiceState.Sending || busy -> {
                            Caption(stringResource(R.string.home_sending))
                            Prompt(liveTranscript.ifBlank { copy.prompt })
                            ZunoSpinner(Modifier.padding(top = ZunoSpace.s8))
                        }
                        holding -> {
                            Caption(
                                if (voiceState == HomeVoiceState.Cancelling) {
                                    stringResource(R.string.home_release_to_cancel)
                                } else {
                                    stringResource(R.string.home_listening)
                                }
                            )
                            ListeningPrompt(liveTranscript)
                        }
                        else -> {
                            Caption(stringResource(R.string.home_hold_to_talk))
                            Prompt(copy.prompt)
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (holding) {
                Text(stringResource(R.string.home_release_to_send), style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary)
                Spacer(Modifier.height(ZunoSpace.s4))
                Text(stringResource(R.string.home_slide_to_cancel), style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
            } else {
                Text(
                    stringResource(R.string.home_type_instead),
                    style = ZunoType.LabelSmall,
                    color = if (micBlocked) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(ZunoRadius.sm))
                        .clickable(role = Role.Button, onClick = onTypeInstead)
                        .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                )
            }
        }
    }
}

@Composable
private fun MicBlockedContent(onAllowMicrophone: () -> Unit) {
    Caption(stringResource(R.string.home_mic_off))
    Prompt(stringResource(R.string.home_mic_prompt))
    Spacer(Modifier.height(ZunoSpace.s8))
    ZunoButton(stringResource(R.string.home_allow_mic), onAllowMicrophone, size = ZunoButtonSize.Medium)
    Text(
        stringResource(R.string.home_mic_settings_hint),
        style = ZunoType.FootnoteRegular,
        color = ZunoColors.TextTertiary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = ZunoType.FootnoteStrong, color = ZunoColors.TextSecondary, textAlign = TextAlign.Center)
}

@Composable
private fun Prompt(text: String) {
    Text(text, style = ZunoType.Display, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** Live transcript: settled words bright, the trailing words dimmed while speech is still arriving. */
@Composable
private fun ListeningPrompt(transcript: String) {
    if (transcript.isBlank()) {
        Text("…", style = ZunoType.Display, color = ZunoColors.TextTertiary, textAlign = TextAlign.Center)
        return
    }
    val words = transcript.trim().split(Regex("\\s+"))
    val settled = words.dropLast(2).joinToString(" ")
    val trailing = words.takeLast(2).joinToString(" ")
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = ZunoColors.TextPrimary)) { append(settled) }
            if (settled.isNotEmpty()) append(" ")
            withStyle(SpanStyle(color = ZunoColors.TextTertiary)) { append("$trailing…") }
        },
        style = ZunoType.Display,
        textAlign = TextAlign.Center,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeMode(
    copy: HomeCopy,
    busy: Boolean,
    typingHint: String?,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
    drafts: List<DraftAttachment>,
    onAttach: () -> Unit,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onRetryAttachment: (DraftAttachment) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val attachmentsReady = drafts.all { it.state == DraftUploadState.Ready }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = ZunoSpace.s8),
    ) {
        ZunoAgentGlow(
            state = if (busy) AgentGlowState.Thinking else AgentGlowState.Idle,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = ZunoSpace.s4),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 116.dp)
                .width(300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        ) {
            Caption(stringResource(R.string.home_ai_tutor))
            Prompt(copy.prompt)
            Text(copy.translation, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary, textAlign = TextAlign.Center)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, bottom = ZunoSpace.s12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            AnimatedVisibility(visible = typingHint != null && draft.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                ZunoTutorHint(typingHint.orEmpty())
            }
            ZunoComposer(
                value = draft,
                onValueChange = {
                    draft = it
                    onDraftChanged(it)
                },
                onSend = {
                    val text = draft.trim()
                    if ((text.isNotEmpty() || drafts.isNotEmpty()) && attachmentsReady && !busy) onSend(text)
                },
                onAttach = onAttach,
                onVoice = onVoice,
                placeholder = copy.placeholder,
                focusRequester = focusRequester,
                enabled = !busy,
                elevated = true,
                attachments = if (drafts.isEmpty()) null else {
                    {
                        drafts.forEach { attachment ->
                            key(attachment.localId) {
                                ZunoDraftAttachment(
                                    draft = attachment,
                                    onRemove = { onRemoveAttachment(attachment) },
                                    onRetry = { onRetryAttachment(attachment) },
                                )
                            }
                        }
                    }
                },
                sendEnabled = attachmentsReady,
            )
        }
    }
}
