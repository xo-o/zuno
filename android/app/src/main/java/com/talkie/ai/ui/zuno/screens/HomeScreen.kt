package com.talkie.ai.ui.zuno.screens

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.talkie.ai.data.TargetLanguage
import com.talkie.ai.ui.zuno.components.AgentGlowState
import com.talkie.ai.ui.zuno.components.ZunoActionTopBar
import com.talkie.ai.ui.zuno.components.ZunoAgentGlow
import com.talkie.ai.ui.zuno.components.ZunoButton
import com.talkie.ai.ui.zuno.components.ZunoButtonSize
import com.talkie.ai.ui.zuno.components.ZunoComposer
import com.talkie.ai.ui.zuno.components.ZunoSpinner
import com.talkie.ai.ui.zuno.components.ZunoTutorHint
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoRadius
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType

/** Greeting copy per target language: the prompt in the target language, translation in the native one. */
data class HomeCopy(val prompt: String, val translation: String, val placeholder: String) {
    companion object {
        fun forLanguage(language: TargetLanguage, firstName: String): HomeCopy {
            val name = firstName.ifBlank { null }
            val es = if (name != null) "Hola $name, ¿qué practicamos hoy?" else "Hola, ¿qué practicamos hoy?"
            val en = if (name != null) "Hi $name, what shall we practice today?" else "Hi, what shall we practice today?"
            return when (language) {
                TargetLanguage.Spanish -> HomeCopy(es, en, "Responde en español…")
                TargetLanguage.English -> HomeCopy(en, es, "Reply in English…")
            }
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
    onOpenDrawer: () -> Unit,
    onPersonalize: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldMove: (cancelling: Boolean) -> Unit,
    onHoldEnd: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendText: (String) -> Unit,
    onTypeModeChanged: (Boolean) -> Unit,
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
        ZunoActionTopBar(onMenu = onOpenDrawer, onAction = onPersonalize)
        Crossfade(targetState = typing, label = "homeMode", modifier = Modifier.weight(1f)) { isTyping ->
            if (isTyping) {
                TypeMode(
                    copy = copy,
                    busy = busy,
                    typingHint = typingHint,
                    onDraftChanged = onDraftChanged,
                    onSend = onSendText,
                    onVoice = { setTyping(false) },
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
                            Caption("Sending…")
                            Prompt(liveTranscript.ifBlank { copy.prompt })
                            ZunoSpinner(Modifier.padding(top = ZunoSpace.s8))
                        }
                        holding -> {
                            Caption(if (voiceState == HomeVoiceState.Cancelling) "Release to cancel" else "Listening…")
                            ListeningPrompt(liveTranscript)
                        }
                        else -> {
                            Caption("Hold the glow to talk")
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
                Text("Release to send", style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary)
                Spacer(Modifier.height(ZunoSpace.s4))
                Text("Slide down to cancel", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
            } else {
                Text(
                    "Type instead",
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
    Caption("Microphone is off")
    Prompt("Allow mic access to talk with your tutor")
    Spacer(Modifier.height(ZunoSpace.s8))
    ZunoButton("Allow microphone", onAllowMicrophone, size = ZunoButtonSize.Medium)
    Text(
        "You can change this anytime in Settings",
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
) {
    var draft by rememberSaveable { mutableStateOf("") }
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
            Caption("AI Tutor")
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
                    if (text.isNotEmpty() && !busy) onSend(text)
                },
                onAttach = {},
                onVoice = onVoice,
                placeholder = copy.placeholder,
                focusRequester = focusRequester,
                showAttach = false,
                enabled = !busy,
                elevated = true,
            )
        }
    }
}
