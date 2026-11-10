package com.talkie.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.data.LearningLanguage
import com.talkie.ai.ui.components.VoiceOrbState
import com.talkie.ai.ui.components.ZunoComposer
import com.talkie.ai.ui.components.ZunoDraftAttachment
import com.talkie.ai.ui.components.ZunoIcon
import com.talkie.ai.ui.components.ZunoSelectorTopBar
import com.talkie.ai.ui.components.ZunoTutorHint
import com.talkie.ai.ui.components.ZunoVoiceOrb
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

/**
 * Home — voice orb. Tap the orb to start a live call in a new Free Talk, or type instead.
 */
@Composable
fun HomeScreen(
    copy: HomeCopy,
    busy: Boolean,
    typingHint: String?,
    /** "Spanish · B1" — active language and level. */
    selector: String,
    onOpenDrawer: () -> Unit,
    onSelector: () -> Unit,
    onOrbTap: () -> Unit,
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
                OrbMode(
                    copy = copy,
                    busy = busy,
                    onOrbTap = onOrbTap,
                    onTypeInstead = { setTyping(true) },
                )
            }
        }
    }
}

/** Visible sphere of the Home / call orb (Figma 190dp); the canvas around it leaves room to bulge. */
internal val OrbSphereSize = 190.dp
internal val OrbCanvasSize = 296.dp

@Composable
private fun OrbMode(
    copy: HomeCopy,
    busy: Boolean,
    onOrbTap: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = maxHeight * OrbTopFraction),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(OrbSphereSize)
                    .clip(CircleShape)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onOrbTap)
                    .semantics { contentDescription = "Start a voice call" },
                contentAlignment = Alignment.Center,
            ) {
                ZunoVoiceOrb(
                    state = if (busy) VoiceOrbState.Thinking else VoiceOrbState.Idle,
                    size = OrbCanvasSize,
                    modifier = Modifier.requiredSize(OrbCanvasSize),
                )
            }
            Spacer(Modifier.height(ZunoSpace.s40))
            Column(
                modifier = Modifier.width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
            ) {
                Prompt(copy.prompt)
                Text(
                    stringResource(if (busy) R.string.voice_connecting else R.string.home_tap_to_talk),
                    style = ZunoType.CaptionRegular,
                    color = ZunoColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ZunoSpace.s16)
                .clip(RoundedCornerShape(ZunoRadius.sm))
                .clickable(role = Role.Button, onClick = onTypeInstead)
                .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoIcon(R.drawable.zuno_ic_keyboard, 16.dp, tint = ZunoColors.IconPrimary)
            Text(stringResource(R.string.home_type_instead), style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary)
        }
    }
}

@Composable
private fun Prompt(text: String) {
    Text(text, style = ZunoType.Display, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        GreetingArea(Modifier.weight(1f)) {
            Column(
                modifier = Modifier.width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    ZunoVoiceOrb(
                        state = if (busy) VoiceOrbState.Thinking else VoiceOrbState.Idle,
                        size = 150.dp,
                        dots = 360,
                        modifier = Modifier.requiredSize(150.dp),
                    )
                }
                Spacer(Modifier.height(ZunoSpace.s20))
                Prompt(copy.prompt)
                if (copy.translation.isNotBlank()) {
                    Text(copy.translation, style = ZunoType.BodySmall, color = ZunoColors.TextTertiary, textAlign = TextAlign.Center)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, bottom = ZunoSpace.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
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

/**
 * Space above the composer: the greeting sits 120dp down and rises as the keyboard and a growing
 * composer take room, so it never runs under the input. When it no longer fits, its top (the orb)
 * slides out of view first, keeping the text readable.
 */
@Composable
private fun GreetingArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.fillMaxWidth().clipToBounds()) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        val top = (constraints.maxHeight - placeable.height - 24.dp.roundToPx())
            .coerceAtMost(120.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - placeable.width) / 2, top)
        }
    }
}
