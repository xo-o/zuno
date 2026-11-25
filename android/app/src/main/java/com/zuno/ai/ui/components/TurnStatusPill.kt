package com.zuno.ai.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * All live voice turn states for `ZunoTurnStatusPill` (`V5B · All 6 States`, `7406:13028`),
 * plus `Muted` when the learner mutes their microphone.
 */
enum class TurnPillState {
    Speaking,
    Listening,
    Talking,
    Thinking,
    Loading,
    Error,
    Muted,
}

private enum class TurnPillGlyph {
    Waveform,
    Thinking,
    Loading,
    Error,
    Muted,
}

private fun TurnPillState.glyph(): TurnPillGlyph = when (this) {
    TurnPillState.Speaking, TurnPillState.Listening, TurnPillState.Talking -> TurnPillGlyph.Waveform
    TurnPillState.Thinking -> TurnPillGlyph.Thinking
    TurnPillState.Loading -> TurnPillGlyph.Loading
    TurnPillState.Error -> TurnPillGlyph.Error
    TurnPillState.Muted -> TurnPillGlyph.Muted
}

/**
 * Derives the live [TurnPillState] with a smooth 650ms holdover so [TurnPillState.Talking]
 * ("Hearing you…") does not flicker between words or syllables.
 */
@Composable
fun rememberTurnPillState(
    status: VoiceCallStatus,
    userAudioLevel: () -> Float,
    activeSpeaker: Speaker?,
    hasActiveUserSpeech: Boolean,
    hasError: Boolean = false,
): TurnPillState {
    val currentLevel by rememberUpdatedState(userAudioLevel)
    var lastTalkAtMs by remember { mutableLongStateOf(0L) }
    var talkingHoldActive by remember { mutableStateOf(false) }

    LaunchedEffect(status, activeSpeaker, hasActiveUserSpeech) {
        if (status != VoiceCallStatus.Listening) {
            talkingHoldActive = false
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            val speakingNow = activeSpeaker == Speaker.USER || hasActiveUserSpeech || currentLevel() > 0.14f
            if (speakingNow) {
                lastTalkAtMs = now
                talkingHoldActive = true
            } else if (talkingHoldActive && now - lastTalkAtMs > 650L) {
                talkingHoldActive = false
            }
            delay(80L)
        }
    }

    return when {
        hasError -> TurnPillState.Error
        status == VoiceCallStatus.Connecting -> TurnPillState.Loading
        status == VoiceCallStatus.Muted -> TurnPillState.Muted
        status == VoiceCallStatus.Thinking -> TurnPillState.Thinking
        status == VoiceCallStatus.Speaking -> TurnPillState.Speaking
        talkingHoldActive || activeSpeaker == Speaker.USER || hasActiveUserSpeech -> TurnPillState.Talking
        else -> TurnPillState.Listening
    }
}

/**
 * Centered row wrapper for [ZunoTurnStatusPill] (`7409:13167`, `7411:13098`).
 */
@Composable
fun ZunoTurnPillRow(
    state: TurnPillState,
    speakerName: String,
    audioLevel: () -> Float,
    modifier: Modifier = Modifier,
    userAudioLevel: () -> Float = audioLevel,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ZunoTurnStatusPill(
            state = state,
            speakerName = speakerName,
            audioLevel = audioLevel,
            userAudioLevel = userAudioLevel,
            onClick = onClick,
        )
    }
}

/**
 * Live Waveform Pill (`7406:13028` · V5B · All 6 States):
 * - **Speaking**: 5-bar green (`#6EE3A6`) animated waveform + `"{Name} speaking · Speak to interrupt"`
 * - **Listening**: 5-bar blue (`#85BDFF`) subtle breathing waveform + `"Listening · Speak anytime"`
 * - **Talking**: 5-bar blue (`#85BDFF`) high-energy waveform + `border/strong` + `"Hearing you…"`
 * - **Thinking**: 3-dot animated thinking wave + `"Thinking…"`
 * - **Loading**: rotating sweep-gradient spinner + `"Loading voice…"`
 * - **Error**: red tinted surface/border + alert icon + `"Connection lost"`
 * - **Muted**: mic-off icon + `"Muted · Tap mic to unmute"`
 *
 * Smoothly interpolates bar heights, waveform colors, pill border/background, and width across states.
 */
@Composable
fun ZunoTurnStatusPill(
    state: TurnPillState,
    speakerName: String,
    audioLevel: () -> Float,
    modifier: Modifier = Modifier,
    userAudioLevel: () -> Float = audioLevel,
    onClick: (() -> Unit)? = null,
) {
    val bgColor by animateColorAsState(
        targetValue = if (state == TurnPillState.Error) ZunoColors.DangerSurface else ZunoColors.SurfaceDefault,
        animationSpec = tween(240),
        label = "turnPillBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            TurnPillState.Error -> ZunoColors.DangerBorder
            TurnPillState.Talking -> ZunoColors.BorderStrong
            else -> ZunoColors.BorderDefault
        },
        animationSpec = tween(240),
        label = "turnPillBorder",
    )

    val a11yLabel = when (state) {
        TurnPillState.Speaking -> "${stringResource(R.string.voice_pill_speaking, speakerName)} ${stringResource(R.string.voice_pill_speak_interrupt)}"
        TurnPillState.Listening -> "${stringResource(R.string.voice_pill_listening)} ${stringResource(R.string.voice_pill_speak_anytime)}"
        TurnPillState.Talking -> stringResource(R.string.voice_pill_hearing_you)
        TurnPillState.Thinking -> stringResource(R.string.voice_pill_thinking)
        TurnPillState.Loading -> stringResource(R.string.voice_pill_loading)
        TurnPillState.Error -> stringResource(R.string.voice_pill_error)
        TurnPillState.Muted -> "${stringResource(R.string.voice_pill_muted)} ${stringResource(R.string.voice_pill_tap_unmute)}"
    }

    Row(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s6)
            .semantics { contentDescription = a11yLabel },
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = state.glyph(),
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.84f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.84f, animationSpec = tween(140)))
            },
            label = "turnPillGlyph",
        ) { glyph ->
            when (glyph) {
                TurnPillGlyph.Waveform -> PillWaveformGlyph(
                    state = state,
                    audioLevel = if (state == TurnPillState.Speaking) audioLevel else userAudioLevel,
                )
                TurnPillGlyph.Thinking -> PillThinkingDotsGlyph()
                TurnPillGlyph.Loading -> PillLoadingSpinnerGlyph()
                TurnPillGlyph.Error -> ZunoIcon(
                    icon = R.drawable.zuno_ic_alert_circle,
                    size = 14.dp,
                    tint = ZunoColors.TextDanger,
                )
                TurnPillGlyph.Muted -> ZunoIcon(
                    icon = R.drawable.zuno_ic_mic_off,
                    size = 14.dp,
                    tint = ZunoColors.IconTertiary,
                )
            }
        }

        AnimatedContent(
            targetState = state to speakerName,
            transitionSpec = {
                fadeIn(tween(180)).togetherWith(fadeOut(tween(130)))
            },
            label = "turnPillLabel",
        ) { (animState, animSpeaker) ->
            val animPrimaryText = when (animState) {
                TurnPillState.Speaking -> stringResource(R.string.voice_pill_speaking, animSpeaker)
                TurnPillState.Listening -> stringResource(R.string.voice_pill_listening)
                TurnPillState.Talking -> stringResource(R.string.voice_pill_hearing_you)
                TurnPillState.Thinking -> stringResource(R.string.voice_pill_thinking)
                TurnPillState.Loading -> stringResource(R.string.voice_pill_loading)
                TurnPillState.Error -> stringResource(R.string.voice_pill_error)
                TurnPillState.Muted -> stringResource(R.string.voice_pill_muted)
            }
            val animSecondaryText = when (animState) {
                TurnPillState.Speaking -> stringResource(R.string.voice_pill_speak_interrupt)
                TurnPillState.Listening -> stringResource(R.string.voice_pill_speak_anytime)
                TurnPillState.Muted -> stringResource(R.string.voice_pill_tap_unmute)
                else -> null
            }
            val animPrimaryColor = when (animState) {
                TurnPillState.Error -> ZunoColors.TextDanger
                TurnPillState.Muted -> ZunoColors.TextSecondary
                else -> ZunoColors.TextPrimary
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = animPrimaryText,
                    style = ZunoType.FootnoteStrong,
                    color = animPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (animSecondaryText != null) {
                    Text(
                        text = animSecondaryText,
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private class PillWaveMotion {
    var phase by mutableFloatStateOf(0f)
    var colorBlend by mutableFloatStateOf(0f) // 0f = Listening/Talking blue, 1f = Speaking green
    var smoothedLevel by mutableFloatStateOf(0.2f)
    val bars = FloatArray(5) { 0.25f }
}

@Composable
private fun PillWaveformGlyph(
    state: TurnPillState,
    audioLevel: () -> Float,
) {
    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(audioLevel)
    val motion = remember { PillWaveMotion() }
    val reducedMotion = rememberPillReducedMotion()

    LaunchedEffect(motion, reducedMotion) {
        if (reducedMotion) {
            val targetBlend = if (currentState == TurnPillState.Speaking) 1f else 0f
            motion.colorBlend = targetBlend
            val staticHeights = when (currentState) {
                TurnPillState.Speaking -> floatArrayOf(0.36f, 0.79f, 0.50f, 0.93f, 0.43f)
                TurnPillState.Talking -> floatArrayOf(0.50f, 0.93f, 0.64f, 1.00f, 0.57f)
                else -> floatArrayOf(0.22f, 0.22f, 0.29f, 0.22f, 0.22f)
            }
            for (i in 0 until 5) motion.bars[i] = staticHeights[i]
            return@LaunchedEffect
        }
        var last = 0L
        val speakingEnv = floatArrayOf(0.42f, 0.84f, 0.58f, 0.96f, 0.48f)
        val talkingEnv = floatArrayOf(0.54f, 0.92f, 0.68f, 1.00f, 0.58f)
        val listeningEnv = floatArrayOf(0.21f, 0.23f, 0.29f, 0.23f, 0.21f)

        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.08f)
                last = now

                val st = currentState
                val isSpeaking = st == TurnPillState.Speaking
                val isTalking = st == TurnPillState.Talking

                val targetBlend = if (isSpeaking) 1f else 0f
                motion.colorBlend += (targetBlend - motion.colorBlend) * (1f - exp(-10f * dt))

                val rawLevel = currentLevel().coerceIn(0f, 1f)
                val targetLevel = when {
                    isSpeaking -> maxOf(rawLevel, 0.40f)
                    isTalking -> maxOf(rawLevel, 0.48f)
                    else -> 0.14f + rawLevel * 0.22f
                }
                val levelRate = if (targetLevel > motion.smoothedLevel) 18f else 7f
                motion.smoothedLevel += (targetLevel - motion.smoothedLevel) * (1f - exp(-levelRate * dt))

                val speed = when {
                    isTalking -> 9.2f
                    isSpeaking -> 7.6f
                    else -> 2.5f
                }
                motion.phase += dt * speed

                val env = when {
                    isSpeaking -> speakingEnv
                    isTalking -> talkingEnv
                    else -> listeningEnv
                }
                val p = motion.phase
                val amp = motion.smoothedLevel

                for (i in 0 until 5) {
                    val osc = when {
                        isSpeaking || isTalking -> 0.58f + 0.42f * sin(p + i * 1.25f)
                        else -> 0.85f + 0.15f * sin(p + i * 0.85f)
                    }
                    val targetBar = if (isSpeaking || isTalking) {
                        (env[i] * (0.42f + 0.72f * amp) * osc).coerceIn(0.22f, 1f)
                    } else {
                        (env[i] * osc).coerceIn(0.18f, 0.36f)
                    }
                    motion.bars[i] += (targetBar - motion.bars[i]) * (1f - exp(-14f * dt))
                }
            }
        }
    }

    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val barWidth = 2.dp.toPx()
        val gap = 2.dp.toPx()
        val radius = barWidth / 2f
        val barColor = lerp(ZunoColors.StatusListening, ZunoColors.StatusSpeaking, motion.colorBlend)
        for (i in 0 until 5) {
            val h = (size.height * motion.bars[i]).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, top),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

@Composable
private fun PillThinkingDotsGlyph() {
    val reducedMotion = rememberPillReducedMotion()
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.08f)
                last = now
                phase = (phase + dt * 5.2f) % (2f * PI.toFloat())
            }
        }
    }

    val dotColor = ZunoColors.TextPrimary
    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val r = 2.dp.toPx()
        val spacing = 6.dp.toPx()
        val startX = (size.width - spacing * 2f) / 2f
        val cy = size.height / 2f
        for (i in 0 until 3) {
            val wave = if (reducedMotion) 0.5f else (0.5f + 0.5f * sin(phase - i * 1.05f))
            val alpha = 0.34f + 0.60f * wave
            val lift = if (reducedMotion) 0f else -1.2.dp.toPx() * wave
            drawCircle(
                color = dotColor.copy(alpha = alpha),
                radius = r * (0.90f + 0.14f * wave),
                center = Offset(startX + i * spacing, cy + lift),
            )
        }
    }
}

@Composable
private fun PillLoadingSpinnerGlyph() {
    val reducedMotion = rememberPillReducedMotion()
    var angle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.08f)
                last = now
                angle = (angle + dt * 360f) % 360f
            }
        }
    }

    val trackColor = ZunoColors.BorderDefault
    val sweepBrush = remember {
        Brush.sweepGradient(
            0f to Color.Transparent,
            0.25f to ZunoColors.TextTertiary,
            1f to ZunoColors.TextPrimary,
        )
    }
    Canvas(modifier = Modifier.size(13.dp)) {
        val strokeWidth = 1.75.dp.toPx()
        drawCircle(
            color = trackColor,
            style = Stroke(width = strokeWidth),
        )
        rotate(degrees = angle) {
            drawArc(
                brush = sweepBrush,
                startAngle = 0f,
                sweepAngle = 290f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun rememberPillReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
