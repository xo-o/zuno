package com.talkie.ai.ui.zuno.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.talkie.ai.R
import com.talkie.ai.ui.zuno.ChatTurn
import com.talkie.ai.ui.zuno.components.CallControlState
import com.talkie.ai.ui.zuno.components.ZunoAssistantBubble
import com.talkie.ai.ui.zuno.components.ZunoCallControl
import com.talkie.ai.ui.zuno.components.ZunoComposer
import com.talkie.ai.ui.zuno.components.ZunoFeedbackPill
import com.talkie.ai.ui.zuno.components.ZunoLiveIndicator
import com.talkie.ai.ui.zuno.components.ZunoTitleTopBar
import com.talkie.ai.ui.zuno.components.ZunoUserBubble
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType

/**
 * 7 · Free Talk — Chat and 7e · Voice Mode. Same conversation; the footer swaps between
 * the text composer and the live voice controls.
 */
@Composable
fun ChatScreen(
    title: String,
    turns: List<ChatTurn>,
    voiceMode: Boolean,
    isMuted: Boolean,
    liveCaption: String?,
    onOpenDrawer: () -> Unit,
    onMore: () -> Unit,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onStartVoice: () -> Unit,
    onSwitchToText: () -> Unit,
    onToggleMute: () -> Unit,
    onEndVoice: () -> Unit,
    onListen: (String) -> Unit,
    onExplain: (ChatTurn.Assistant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var focusComposer by remember { mutableStateOf(false) }
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoTitleTopBar(title = title, onMenu = onOpenDrawer, onMore = onMore)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            itemsIndexed(turns) { _, turn ->
                when (turn) {
                    is ChatTurn.User -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    ) {
                        ZunoUserBubble(message = turn.message, showSender = turn.showSender)
                        turn.feedback?.let { ZunoFeedbackPill(it) }
                    }
                    is ChatTurn.Assistant -> ZunoAssistantBubble(
                        message = turn.message,
                        translation = turn.translation,
                        glossary = turn.glossary,
                        onListen = { onListen(turn.message) },
                        onExplain = { onExplain(turn) },
                    )
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
                    isMuted = isMuted,
                    liveCaption = liveCaption,
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
                    onSend = onSend,
                    onAttach = onAttach,
                    onVoice = {
                        focusComposer = false
                        onStartVoice()
                    },
                )
            }
        }
    }
}

@Composable
private fun ComposerFooter(
    autoFocus: Boolean,
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    Box(Modifier.padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = ZunoSpace.s8)) {
        ZunoComposer(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    onSend(text)
                    draft = ""
                }
            },
            onAttach = onAttach,
            onVoice = onVoice,
            placeholder = "Ask or pick a topic…",
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun VoiceFooter(
    isMuted: Boolean,
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
        AnimatedVisibility(visible = captionsOn && !liveCaption.isNullOrBlank()) {
            Text(
                text = liveCaption.orEmpty(),
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
            ZunoCallControl(
                icon = R.drawable.zuno_ic_keyboard,
                onClick = onType,
                contentDescription = "Type instead",
            )
            ZunoCallControl(
                icon = R.drawable.zuno_ic_captions,
                onClick = { captionsOn = !captionsOn },
                contentDescription = if (captionsOn) "Hide captions" else "Show captions",
                state = if (captionsOn) CallControlState.Active else CallControlState.Default,
            )
            ZunoLiveIndicator(Modifier.weight(1f))
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
