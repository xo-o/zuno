package com.talkie.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.ui.components.VoiceOrbState
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoButtonSize
import com.talkie.ai.ui.components.ZunoButtonVariant
import com.talkie.ai.ui.components.ZunoCompactTitleTopBar
import com.talkie.ai.ui.components.ZunoIcon
import com.talkie.ai.ui.components.ZunoIconButtonSize
import com.talkie.ai.ui.components.ZunoMenuAction
import com.talkie.ai.ui.components.ZunoOverflowMenu
import com.talkie.ai.ui.components.ZunoVoiceOrb
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import kotlinx.coroutines.delay

/** What the live call is doing, as shown under the orb. */
enum class VoiceCallStatus(val orb: VoiceOrbState) {
    Connecting(VoiceOrbState.Thinking),
    Listening(VoiceOrbState.Listening),
    Muted(VoiceOrbState.Idle),
    Thinking(VoiceOrbState.Thinking),
    Speaking(VoiceOrbState.Speaking),
}

/** Where the orb sits on Home and in a call, as a share of the space under the top bar (Figma 156 / 740). */
internal const val OrbTopFraction = 0.21f

/**
 * Voice mode — the live call with the tutor (Figma "Home - Speaking"). The orb stays where it was
 * on Home; tap it to interrupt the tutor. Captions show the tutor's latest line.
 */
@Composable
fun VoiceModeScreen(
    status: VoiceCallStatus,
    level: () -> Float,
    /** When the call connected (epoch millis); the title counts up from it. */
    startedAtMillis: Long?,
    tutorLine: String?,
    isMuted: Boolean,
    menu: List<ZunoMenuAction>,
    onEnd: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var captionsOn by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onEnd)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = stringResource(R.string.voice_live_title, rememberElapsed(startedAtMillis)),
            onBack = onEnd,
            trailing = { ZunoOverflowMenu(menu, size = ZunoIconButtonSize.Large) },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
                        .clickable(role = Role.Button, onClick = onInterrupt)
                        .semantics { contentDescription = "Interrupt the tutor" },
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoVoiceOrb(
                        state = status.orb,
                        level = level,
                        size = OrbCanvasSize,
                        modifier = Modifier.requiredSize(OrbCanvasSize),
                    )
                }
                Spacer(Modifier.height(ZunoSpace.s40))
                val line = tutorLine?.takeIf { captionsOn && it.isNotBlank() }
                AnimatedContent(
                    targetState = line,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it == null },
                    label = "voiceCaption",
                ) { caption ->
                    if (caption == null) StatusText(status) else Captions(caption)
                }
            }
            CallControls(
                isMuted = isMuted,
                captionsOn = captionsOn,
                onToggleMute = onToggleMute,
                onEnd = onEnd,
                onToggleCaptions = { captionsOn = !captionsOn },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun rememberElapsed(startedAtMillis: Long?): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1000 - (now - startedAtMillis) % 1000)
        }
    }
    val seconds = startedAtMillis?.let { ((now - it) / 1000).coerceAtLeast(0) } ?: 0
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun StatusText(status: VoiceCallStatus) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
        Text("ZunoAI", style = ZunoType.Headline, color = ZunoColors.TextPrimary)
        Text(
            stringResource(
                when (status) {
                    VoiceCallStatus.Connecting -> R.string.voice_connecting
                    VoiceCallStatus.Listening -> R.string.voice_listening
                    VoiceCallStatus.Muted -> R.string.voice_muted
                    VoiceCallStatus.Thinking -> R.string.voice_thinking
                    VoiceCallStatus.Speaking -> R.string.voice_tap_to_interrupt
                },
            ),
            style = ZunoType.CaptionRegular,
            color = ZunoColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The tutor's line: the sentence before dimmed, the current one large. */
@Composable
private fun Captions(line: String) {
    val sentences = line.trim().split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
    val current = sentences.lastOrNull().orEmpty()
    val previous = sentences.dropLast(1).lastOrNull()
    Column(
        modifier = Modifier.width(310.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        previous?.let {
            Text(it, style = ZunoType.BodyMedium, color = ZunoColors.TextTertiary, textAlign = TextAlign.Center, maxLines = 2)
        }
        Text(current, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 4)
    }
}

@Composable
private fun CallControls(
    isMuted: Boolean,
    captionsOn: Boolean,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onToggleCaptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallButton(
            icon = if (isMuted) R.drawable.zuno_ic_mic_off else R.drawable.zuno_ic_mic_secondary,
            active = isMuted,
            onClick = onToggleMute,
            contentDescription = stringResource(if (isMuted) R.string.voice_unmute else R.string.voice_mute),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ZunoButton(
                stringResource(R.string.voice_end_call),
                onEnd,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Medium,
            )
        }
        CallButton(
            icon = R.drawable.zuno_ic_captions,
            active = captionsOn,
            onClick = onToggleCaptions,
            contentDescription = stringResource(if (captionsOn) R.string.voice_hide_captions else R.string.voice_show_captions),
        )
    }
}

/** 44dp round control; Active turns it white (captions on, muted). */
@Composable
private fun CallButton(@DrawableRes icon: Int, active: Boolean, onClick: () -> Unit, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) ZunoColors.SurfaceInverse else ZunoColors.SurfaceDefault)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, 24.dp, tint = if (active) ZunoColors.IconOnInverse else ZunoColors.IconPrimary)
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun VoiceModeScreenPreview() {
    VoiceModeScreen(
        status = VoiceCallStatus.Speaking,
        level = { 0.5f },
        startedAtMillis = System.currentTimeMillis() - 42_000,
        tutorLine = "¡Perfecto! Imagina que estás en un café en Madrid. ¿Qué vas a pedir?",
        isMuted = false,
        menu = emptyList(),
        onEnd = {},
        onInterrupt = {},
        onToggleMute = {},
    )
}
