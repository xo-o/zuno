package com.talkie.ai.ui.zuno.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.tooling.preview.Preview
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoRadius
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * What the agent glow is expressing.
 * - [Idle]: resting, slow breathing.
 * - [Listening]: the mic is open; the glow rises with the user's voice level.
 * - [Thinking]: waiting for the tutor's reply; faster upward sweeps and a soft pulse.
 * - [Speaking]: the tutor is talking; the glow swells with the tutor's voice level.
 * - [Cancelling]: the user is sliding away to cancel; the glow sinks and dims.
 * - [Disabled]: mic blocked or muted; a still, dim baseline.
 */
enum class AgentGlowState(val reactsToVoice: Boolean) {
    Idle(false),
    Listening(true),
    Thinking(false),
    Speaking(true),
    Cancelling(false),
    Disabled(false),
}

private class GlowTune(
    val rise: Float,
    val levelRise: Float,
    val breath: Float,
    val brightness: Float,
    val bandSpeed: Float,
    val bandAlpha: Float,
    val pulse: Float,
)

private fun tuneFor(state: AgentGlowState): GlowTune = when (state) {
    AgentGlowState.Idle -> GlowTune(0.40f, 0.00f, 0.05f, 0.80f, 0.10f, 0.10f, 0f)
    AgentGlowState.Listening -> GlowTune(0.50f, 0.42f, 0.03f, 0.92f, 0.32f, 0.20f, 0f)
    AgentGlowState.Thinking -> GlowTune(0.48f, 0.00f, 0.04f, 0.85f, 0.55f, 0.24f, 1f)
    AgentGlowState.Speaking -> GlowTune(0.52f, 0.32f, 0.03f, 0.96f, 0.40f, 0.22f, 0f)
    AgentGlowState.Cancelling -> GlowTune(0.22f, 0.05f, 0.01f, 0.40f, 0.06f, 0.04f, 0f)
    AgentGlowState.Disabled -> GlowTune(0.22f, 0.00f, 0.00f, 0.35f, 0.00f, 0.00f, 0f)
}

private const val BAND_COUNT = 3

/**
 * Agent Horizon glow — a procedural gradient that rises from the bottom of its bounds and travels
 * upward. Everything is mirrored around the vertical center line, so the card stays symmetric.
 *
 * [audioLevel] is polled every frame (0..1); it should be cheap and must not trigger recomposition.
 * [intensity] scales overall brightness (e.g. lower behind chat text).
 */
@Composable
fun ZunoAgentGlow(
    state: AgentGlowState,
    modifier: Modifier = Modifier,
    audioLevel: () -> Float = { 0f },
    intensity: Float = 1f,
    shape: Shape = RoundedCornerShape(ZunoRadius.xxxl),
) {
    val tune = tuneFor(state)
    val spec = tween<Float>(durationMillis = 650)
    val rise = animateFloatAsState(tune.rise, spec, label = "glowRise")
    val levelRise = animateFloatAsState(tune.levelRise, spec, label = "glowLevelRise")
    val breath = animateFloatAsState(tune.breath, spec, label = "glowBreath")
    val brightness = animateFloatAsState(tune.brightness * intensity, spec, label = "glowBrightness")
    val bandSpeed = animateFloatAsState(tune.bandSpeed, spec, label = "glowBandSpeed")
    val bandAlpha = animateFloatAsState(tune.bandAlpha, spec, label = "glowBandAlpha")
    val pulse = animateFloatAsState(tune.pulse, spec, label = "glowPulse")

    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(audioLevel)
    val clock = remember { GlowClock() }

    LaunchedEffect(clock) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                clock.advance(dt, bandSpeed.value, targetLevel(currentState, currentLevel, clock.time))
            }
        }
    }

    Canvas(modifier.clip(shape)) {
        drawGlow(
            time = clock.time,
            bandPhase = clock.bandPhase,
            level = clock.level,
            rise = rise.value,
            levelRise = levelRise.value,
            breath = breath.value,
            brightness = brightness.value,
            bandAlpha = bandAlpha.value,
            pulse = pulse.value,
        )
    }
}

/** Frame-driven values; read only in the draw phase so animation never recomposes. */
private class GlowClock {
    var time by mutableFloatStateOf(0f)
        private set
    var bandPhase by mutableFloatStateOf(0f)
        private set
    var level by mutableFloatStateOf(0f)
        private set

    fun advance(dt: Float, bandSpeed: Float, target: Float) {
        time += dt
        bandPhase = (bandPhase + dt * bandSpeed) % 1f
        // Fast attack, slow release, so loud syllables punch up and silence settles gently.
        val rate = if (target > level) 16f else 3.5f
        level += (target - level) * (1f - exp(-rate * dt))
    }
}

private fun targetLevel(state: AgentGlowState, level: () -> Float, time: Float): Float {
    if (!state.reactsToVoice) return 0f
    val measured = level().coerceIn(0f, 1f)
    return if (state == AgentGlowState.Speaking) {
        // Keep a gentle cadence even when the remote level is sparse.
        val cadence = 0.18f + 0.12f * wave(time * 6.3f) + 0.08f * wave(time * 11.7f + 1.3f)
        (measured * 0.85f + cadence * 0.45f).coerceIn(0f, 1f)
    } else {
        measured
    }
}

private fun wave(x: Float) = 0.5f + 0.5f * sin(x)

private fun DrawScope.drawGlow(
    time: Float,
    bandPhase: Float,
    level: Float,
    rise: Float,
    levelRise: Float,
    breath: Float,
    brightness: Float,
    bandAlpha: Float,
    pulse: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val cx = w / 2f

    val breathing = wave(time * (2f * PI.toFloat() / 3.6f))
    val pulsing = 1f + pulse * 0.28f * sin(time * 5.2f)
    val riseFraction = (rise + breath * breathing + level * levelRise).coerceIn(0.08f, 0.96f)
    val riseHeight = h * riseFraction
    val glowTop = h - riseHeight
    val b = brightness.coerceIn(0f, 1.2f)

    // 1. Base: dark → deep → base, rising from the bottom.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.35f to ZunoColors.GlowDeep.copy(alpha = 0.85f),
            1f to ZunoColors.GlowBase,
            startY = glowTop - h * 0.18f,
            endY = h,
        ),
        alpha = b.coerceAtMost(1f),
    )

    // 2. Mirrored accent blobs at both lower corners.
    val sway = w * 0.025f * sin(time * 0.7f)
    val sideOffset = w * 0.38f + sway
    val sideY = h - riseHeight * 0.32f
    val sideRadius = w * (0.42f + 0.10f * level)
    val sideAlpha = 0.50f * b
    glowEllipse(Offset(cx - sideOffset, sideY), sideRadius, sideRadius * 0.85f, ZunoColors.GlowAccent, sideAlpha)
    glowEllipse(Offset(cx + sideOffset, sideY), sideRadius, sideRadius * 0.85f, ZunoColors.GlowAccent, sideAlpha)

    // 3. Bands of light travelling from the bottom to the top of the glow.
    if (bandAlpha > 0.001f) {
        for (i in 0 until BAND_COUNT) {
            val phase = (bandPhase + i.toFloat() / BAND_COUNT) % 1f
            val y = h - phase * riseHeight * 1.1f
            val fade = sin(phase * PI.toFloat())
            glowEllipse(
                center = Offset(cx, y),
                rx = w * 0.78f,
                ry = h * 0.11f,
                color = ZunoColors.GlowHighlight,
                alpha = bandAlpha * fade * b * (0.7f + 0.6f * level),
            )
        }
    }

    // 4. Central highlight pool, swelling with the voice.
    glowEllipse(
        center = Offset(cx, h + h * 0.02f),
        rx = w * (0.44f + 0.20f * level + 0.03f * breathing),
        ry = riseHeight * (0.36f + 0.28f * level),
        color = ZunoColors.GlowHighlight,
        alpha = ((0.62f + 0.38f * level) * b * pulsing).coerceIn(0f, 1f),
    )
}

private fun DrawScope.glowEllipse(center: Offset, rx: Float, ry: Float, color: Color, alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0.001f || rx <= 0f || ry <= 0f) return
    scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = a),
                0.45f to color.copy(alpha = a * 0.55f),
                1f to Color.Transparent,
                center = center,
                radius = rx,
            ),
            radius = rx,
            center = center,
        )
    }
}

@Preview(name = "Agent Glow · Listening", widthDp = 386, heightDp = 600)
@Composable
private fun ZunoAgentGlowPreview() {
    ZunoAgentGlow(
        state = AgentGlowState.Listening,
        audioLevel = { 0.5f },
        modifier = Modifier.background(ZunoColors.BgCanvas).fillMaxSize(),
    )
}
