package com.talkie.ai.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.data.Message
import com.talkie.ai.ui.components.AgentGlowState
import com.talkie.ai.ui.components.CallControlState
import com.talkie.ai.ui.components.ZunoAssistantBubble
import com.talkie.ai.ui.components.ZunoCallControl
import com.talkie.ai.ui.components.ZunoComposer
import com.talkie.ai.ui.components.ZunoFeedbackPill
import com.talkie.ai.ui.components.ZunoLiveIndicator
import com.talkie.ai.ui.components.ZunoLoadState
import com.talkie.ai.ui.components.ZunoMenuAction
import com.talkie.ai.ui.components.ZunoOverflowMenu
import com.talkie.ai.ui.components.ZunoSessionStart
import com.talkie.ai.ui.components.ZunoTitleTopBar
import com.talkie.ai.ui.components.ZunoTutorHint
import com.talkie.ai.ui.components.ZunoTypingBubble
import com.talkie.ai.ui.components.ZunoUserBubble
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

/** A line spoken in the current live voice session (not yet saved to history). */
data class LiveLine(val isUser: Boolean, val text: String)

/** Free Talk / roleplay / lesson conversation — text chat with an optional live voice footer. */
@Composable
fun ChatScreen(
    title: String,
    sessionLabel: String,
    messages: List<Message>?,
    pendingText: String?,
    error: String?,
    liveLines: List<LiveLine>,
    voiceMode: Boolean,
    voiceConnecting: Boolean,
    glowState: AgentGlowState,
    glowLevel: () -> Float,
    isMuted: Boolean,
    placeholder: String,
    typingHint: String?,
    menu: List<ZunoMenuAction>,
    onOpenDrawer: () -> Unit,
    onRetryLoad: () -> Unit,
    onSend: (String) -> Unit,
    onDraftChanged: (String) -> Unit,
    onStartVoice: () -> Unit,
    onSwitchToText: () -> Unit,
    onToggleMute: () -> Unit,
    onEndVoice: () -> Unit,
    onListen: (String) -> Unit,
    onExplain: (Message) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var focusComposer by remember { mutableStateOf(false) }
    // Re-scroll whenever content at the bottom changes — including the typing bubble being replaced by the reply.
    LaunchedEffect(messages?.size, pendingText, error, liveLines.size, liveLines.lastOrNull()?.text) {
        if (messages == null) return@LaunchedEffect
        val lastIndex = messages.size + liveLines.size +
            (if (pendingText != null) 2 else 0) +
            (if (error != null && pendingText == null) 1 else 0)
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
    }

    Box(modifier.fillMaxSize().background(ZunoColors.BgCanvas)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ZunoTitleTopBar(
                title = title,
                onMenu = onOpenDrawer,
                onMore = {},
                trailing = { ZunoOverflowMenu(menu) },
            )
            if (messages == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ZunoLoadState(error = error, onRetry = onRetryLoad)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
                ) {
                    item(key = "start") { ZunoSessionStart(sessionLabel) }
                    items(messages, key = { it.id }) { message ->
                        if (message.isUser) {
                            UserTurn(message.text, message.feedback)
                        } else {
                            ZunoAssistantBubble(
                                message = message.text,
                                translation = message.translation,
                                glossary = message.glossary,
                                onListen = { onListen(message.text) },
                                onExplain = { onExplain(message) },
                            )
                        }
                    }
                    items(liveLines.size, key = { "live-$it" }) { index ->
                        val line = liveLines[index]
                        if (line.isUser) UserTurn(line.text, null) else ZunoAssistantBubble(message = line.text)
                    }
                    if (pendingText != null) {
                        item(key = "pending-user") { UserTurn(pendingText, null) }
                        item(key = "pending-reply") { ZunoTypingBubble() }
                    }
                    if (error != null && pendingText == null) {
                        item(key = "error") {
                            Text(
                                error,
                                style = ZunoType.CaptionRegular,
                                color = ZunoColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = voiceMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "chatFooter",
            ) { voice ->
                if (voice) {
                    VoiceFooter(
                        glowState = glowState,
                        glowLevel = glowLevel,
                        isMuted = isMuted,
                        connecting = voiceConnecting,
                        liveCaption = liveLines.lastOrNull { !it.isUser }?.text,
                        onType = {
                            focusComposer = true
                            onSwitchToText()
                        },
                        onToggleMute = onToggleMute,
                        onEnd = onEndVoice,
                    )
                } else {
                    ComposerFooter(
                        autoFocus = focusComposer,
                        enabled = messages != null && pendingText == null,
                        placeholder = placeholder,
                        typingHint = typingHint,
                        onDraftChanged = onDraftChanged,
                        onSend = onSend,
                        onVoice = {
                            focusComposer = false
                            onStartVoice()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserTurn(text: String, feedback: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        ZunoUserBubble(message = text)
        feedback?.let { ZunoFeedbackPill(it) }
    }
}

@Composable
private fun ComposerFooter(
    autoFocus: Boolean,
    enabled: Boolean,
    placeholder: String,
    typingHint: String?,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    Column(
        modifier = Modifier.padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = ZunoSpace.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        AnimatedVisibility(visible = typingHint != null && draft.isNotBlank()) {
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
                if (text.isNotEmpty() && enabled) {
                    onSend(text)
                    draft = ""
                }
            },
            onAttach = {},
            onVoice = onVoice,
            placeholder = placeholder,
            focusRequester = focusRequester,
            showAttach = false,
        )
    }
}

@Composable
private fun VoiceFooter(
    glowState: AgentGlowState,
    glowLevel: () -> Float,
    isMuted: Boolean,
    connecting: Boolean,
    liveCaption: String?,
    onType: () -> Unit,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
) {
    var captionsOn by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = ZunoSpace.s8),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        AnimatedVisibility(visible = connecting || (captionsOn && !liveCaption.isNullOrBlank())) {
            Text(
                text = if (connecting) "Connecting…" else liveCaption.orEmpty(),
                style = ZunoType.BodyMedium,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoCallControl(icon = R.drawable.zuno_ic_keyboard, onClick = onType, contentDescription = "Type instead")
            ZunoCallControl(
                icon = R.drawable.zuno_ic_captions,
                onClick = { captionsOn = !captionsOn },
                contentDescription = if (captionsOn) "Hide captions" else "Show captions",
                state = if (captionsOn) CallControlState.Active else CallControlState.Default,
            )
            ZunoLiveIndicator(state = glowState, audioLevel = glowLevel, modifier = Modifier.weight(1f))
            ZunoCallControl(
                icon = R.drawable.zuno_ic_mic_off,
                onClick = onToggleMute,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                state = if (isMuted) CallControlState.Active else CallControlState.Default,
            )
            ZunoCallControl(
                icon = R.drawable.zuno_ic_x_inverse,
                onClick = onEnd,
                contentDescription = "End voice session",
                state = CallControlState.End,
            )
        }
    }
}
