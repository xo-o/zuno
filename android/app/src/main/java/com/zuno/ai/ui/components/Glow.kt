package com.zuno.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What the agent glow is expressing.
 * - [Idle]: resting, slow drift and breathing.
 * - [Listening]: the user is talking; the glow grows with the user's voice level.
 * - [Thinking]: waiting for the tutor's reply; upward sweeps and a soft pulse.
 * - [Speaking]: the tutor is talking; the glow grows with the tutor's voice level.
 * - [Cancelling]: the user is sliding away to cancel; the glow sinks and dims.
 * - [Disabled]: mic blocked or muted; a slow, dim baseline.
 */
enum class AgentGlowState(val reactsToVoice: Boolean) {
    Idle(false),
    Listening(true),
    Thinking(false),
    Speaking(true),
    Cancelling(false),
    Disabled(false),
}

/**
 * Per-state motion. [speed] scales the drift clock (voice adds up to +75% on top), [breath] and [lift]
 * are vertical offsets as a fraction of the glow height.
 */
private class GlowTune(
    val speed: Float,
    val breath: Float,
    val brightness: Float,
    val lift: Float,
    val bandSpeed: Float,
    val bandAlpha: Float,
    val pulse: Float,
)

private fun tuneFor(state: AgentGlowState): GlowTune = when (state) {
    AgentGlowState.Idle -> GlowTune(1.0f, 0.012f, 1.00f, 0.00f, 0.10f, 0.00f, 0f)
    AgentGlowState.Listening -> GlowTune(2.0f, 0.008f, 1.00f, 0.02f, 0.32f, 0.00f, 0f)
    AgentGlowState.Thinking -> GlowTune(1.6f, 0.012f, 1.00f, 0.03f, 0.55f, 0.18f, 1f)
    AgentGlowState.Speaking -> GlowTune(2.0f, 0.008f, 1.00f, 0.02f, 0.40f, 0.00f, 0f)
    AgentGlowState.Cancelling -> GlowTune(0.6f, 0.000f, 0.50f, -0.10f, 0.06f, 0.00f, 0f)
    AgentGlowState.Disabled -> GlowTune(0.4f, 0.000f, 0.45f, -0.10f, 0.00f, 0.00f, 0f)
}

/** Animated (650ms) view of a [GlowTune], so state changes ease instead of jumping. */
private class AnimatedTune(
    val speed: State<Float>,
    val breath: State<Float>,
    val brightness: State<Float>,
    val lift: State<Float>,
    val bandSpeed: State<Float>,
    val bandAlpha: State<Float>,
    val pulse: State<Float>,
)

@Composable
private fun animatedTune(state: AgentGlowState, intensity: Float = 1f): AnimatedTune {
    val tune = tuneFor(state)
    val spec = tween<Float>(durationMillis = 650)
    return AnimatedTune(
        speed = animateFloatAsState(tune.speed, spec, label = "glowSpeed"),
        breath = animateFloatAsState(tune.breath, spec, label = "glowBreath"),
        brightness = animateFloatAsState(tune.brightness * intensity, spec, label = "glowBrightness"),
        lift = animateFloatAsState(tune.lift, spec, label = "glowLift"),
        bandSpeed = animateFloatAsState(tune.bandSpeed, spec, label = "glowBandSpeed"),
        bandAlpha = animateFloatAsState(tune.bandAlpha, spec, label = "glowBandAlpha"),
        pulse = animateFloatAsState(tune.pulse, spec, label = "glowPulse"),
    )
}

/** Frame-driven values; read only in the draw phase so animation never recomposes. */
private class GlowMotion {
    /** Drift clock in seconds of idle motion; runs faster with the state speed and the voice. */
    var time by mutableFloatStateOf(0f)
        private set
    var bandPhase by mutableFloatStateOf(0f)
        private set
    /** Smoothed voice level (0..1). */
    var energy by mutableFloatStateOf(0f)
        private set

    fun advance(dt: Float, speed: Float, bandSpeed: Float, target: Float) {
        // ~55ms attack, ~285ms release: each word swells the glow, pauses let it settle.
        val rate = if (target > energy) 18f else 3.5f
        energy += (target - energy) * (1f - exp(-rate * dt))
        time += dt * speed * (1f + 0.75f * energy)
        bandPhase = (bandPhase + dt * bandSpeed) % 1f
    }
}

@Composable
private fun rememberGlowMotion(state: AgentGlowState, tune: AnimatedTune, audioLevel: () -> Float): GlowMotion {
    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(audioLevel)
    val motion = remember { GlowMotion() }
    LaunchedEffect(motion) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                val target = if (currentState.reactsToVoice) currentLevel().coerceIn(0f, 1f) else 0f
                motion.advance(dt, tune.speed.value, tune.bandSpeed.value, target)
            }
        }
    }
    return motion
}

private const val TWO_PI = (2.0 * PI).toFloat()
private const val BREATH_PERIOD = 7f
private const val BAND_COUNT = 3

// Figma Horizon frame is 386 x 600; blob geometry below is in those units.
private const val DESIGN_WIDTH = 386f
private val TopFadeTint = Color(0xFF0D0D0D)
private val TopFadeStops = floatArrayOf(0f, 0.18f, 0.32f, 0.46f, 0.58f, 0.70f, 0.82f)
private val TopFadeAlphas = floatArrayOf(1f, 0.97f, 0.85f, 0.62f, 0.36f, 0.14f, 0f)

/**
 * Agent Horizon glow — mirrors the Figma "Horizon" component: a dark→gray base gradient, three
 * blurred blobs (left/right/center) along the bottom edge, a top fade into the canvas and a soft
 * white sheen. At rest it is exactly the Figma frame, drifting slowly; while someone talks the
 * gradient grows upward and lightens in proportion to the voice level.
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
    shape: Shape = RoundedCornerShape(40.dp),
) {
    val tune = animatedTune(state, intensity)
    val motion = rememberGlowMotion(state, tune, audioLevel)

    Spacer(
        modifier
            .clip(shape)
            .drawWithCache {
                val u = size.width / DESIGN_WIDTH
                // Figma layer blur radius r renders as a Gaussian with sigma = r / 2.
                val left = BlurredOval.create(280f * u, 220f * u, 35f * u)
                val right = BlurredOval.create(270f * u, 230f * u, 35f * u)
                val center = BlurredOval.create(220f * u, 110f * u, 23f * u)
                onDrawBehind {
                    drawHorizon(u, left, right, center, motion, tune)
                }
            }
    )
}

private fun DrawScope.drawHorizon(
    u: Float,
    leftBlob: BlurredOval,
    rightBlob: BlurredOval,
    centerBlob: BlurredOval,
    motion: GlowMotion,
    tune: AnimatedTune,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val cx = w / 2f
    val t = motion.time
    val e = motion.energy
    val b = tune.brightness.value.coerceIn(0f, 1f)
    val pulse = tune.pulse.value
    val bandAlpha = tune.bandAlpha.value

    val breathing = sin(t * TWO_PI / BREATH_PERIOD)
    val pulsing = 1f + pulse * 0.28f * sin(t * 3.2f)
    val lift = h * (tune.lift.value + tune.breath.value * breathing + 0.10f * e)
    // Blobs grow upward from the bottom edge (their centers rise by most of the added half-height).
    val grow = 1f + 0.6f * e
    val sideRise = 0.8f * 110f * u * (grow - 1f)

    // 1. Base gradient: glow/deep until 55%, then glow/base at the bottom. The voice pulls the gray higher.
    drawRect(
        brush = Brush.verticalGradient(
            0f to ZunoColors.GlowDeep,
            lerpF(0.55f, 0.30f, e) to ZunoColors.GlowDeep,
            1f to lerp(ZunoColors.GlowBase, ZunoColors.GlowAccent, 0.5f * e),
        ),
        alpha = b,
    )

    // 2. Side blobs, anchored like the Figma frame (left/right edges, bottom), each on its own slow drift.
    val leftSway = 7f * u * sin(t * 0.55f)
    val rightSway = 7f * u * sin(t * 0.43f + 1.7f)
    drawBlob(
        leftBlob, 50f * u + leftSway, h - 60f * u - lift - sideRise + 4f * u * sin(t * 0.31f), grow,
        lerp(ZunoColors.GlowBase, ZunoColors.GlowAccent, e), 0.90f * b,
    )
    drawBlob(
        rightBlob, w - 41f * u - rightSway, h - 75f * u - lift - sideRise + 4f * u * sin(t * 0.37f + 0.9f), grow,
        lerp(ZunoColors.GlowAccent, ZunoColors.GlowHighlight, e), 0.95f * b,
    )

    // 3. Bands of light travelling upward (thinking only).
    if (bandAlpha > 0.001f) {
        val travel = h * 0.45f + lift
        for (i in 0 until BAND_COUNT) {
            val phase = (motion.bandPhase + i.toFloat() / BAND_COUNT) % 1f
            glowEllipse(
                center = Offset(cx, h - phase * travel),
                rx = w * 0.78f,
                ry = h * 0.11f,
                color = ZunoColors.GlowHighlight,
                alpha = bandAlpha * sin(phase * PI.toFloat()) * b,
            )
        }
    }

    // 4. Central highlight pool, growing up from the bottom edge.
    val centerGrow = 1f + 0.5f * e
    drawBlob(
        centerBlob,
        cx + 10f * u * sin(t * 0.37f + 0.4f),
        h - 15f * u - lift * 0.6f - 0.8f * 55f * u * (centerGrow - 1f),
        centerGrow * pulsing,
        lerp(ZunoColors.GlowHighlight, Color.White, 0.7f * e),
        0.95f * b,
    )

    // 5. Top fade into the canvas; its clear point moves from 82% up to ~55% so the prompt stays readable.
    val reach = lerpF(1f, 0.55f / 0.82f, e)
    drawRect(
        Brush.verticalGradient(
            *Array(TopFadeStops.size) { i ->
                val color = if (i == 0) ZunoColors.BgCanvas else TopFadeTint.copy(alpha = TopFadeAlphas[i])
                TopFadeStops[i] * reach to color
            }
        )
    )

    // 6. Bottom sheen.
    drawRect(
        Brush.verticalGradient(
            0.70f to Color.White.copy(alpha = 0f),
            1f to Color.White.copy(alpha = 0.08f + 0.14f * e),
        )
    )
}

// Tutor palette for the live indicator: green → teal → blue.
private val TutorGreen = Color(0xFF1EFFA0)
private val TutorTeal = Color(0xFF14C8B4)
private val TutorBlue = Color(0xFF3A7BFF)

private fun liveLabel(state: AgentGlowState): String = when (state) {
    AgentGlowState.Speaking -> "Zuno is speaking"
    AgentGlowState.Listening -> "Listening"
    AgentGlowState.Thinking -> "Thinking"
    AgentGlowState.Idle, AgentGlowState.Cancelling, AgentGlowState.Disabled -> "Microphone off"
}

/**
 * Center of the live voice dock — Figma "Live Indicator": a dark pill with two blurred blobs glowing
 * up from the bottom. Gray while the user talks, tutor green/teal/blue while Zuno speaks; in both
 * cases the glow grows and lightens with [audioLevel] (0..1, polled per frame) of whoever is talking.
 */
@Composable
fun ZunoLiveIndicator(
    state: AgentGlowState,
    modifier: Modifier = Modifier,
    audioLevel: () -> Float = { 0f },
) {
    val tune = animatedTune(state, intensity = if (state == AgentGlowState.Thinking) 0.85f else 1f)
    val motion = rememberGlowMotion(state, tune, audioLevel)
    val colorful = animateFloatAsState(
        if (state == AgentGlowState.Speaking) 1f else 0f,
        tween(durationMillis = 600),
        label = "liveColorful",
    )

    Spacer(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(ZunoColors.BgCanvas)
            .drawWithCache {
                val d = density
                val w = size.width
                // Figma: outer 90% x 50 (blur 14), inner 60% x 34 (blur 10); tutor blobs split the outer one.
                val outer = BlurredOval.create(w * 0.90f, 50f * d, 7f * d)
                val inner = BlurredOval.create(w * 0.60f, 34f * d, 5f * d)
                val tinted = BlurredOval.create(w * 0.55f, 50f * d, 7f * d)
                onDrawBehind {
                    drawLiveGlow(d, outer, inner, tinted, motion, tune, colorful.value)
                }
            }
            .border(1.dp, ZunoColors.BorderDefault, CircleShape)
            .semantics { contentDescription = liveLabel(state) }
    )
}

private fun DrawScope.drawLiveGlow(
    d: Float,
    outer: BlurredOval,
    inner: BlurredOval,
    tinted: BlurredOval,
    motion: GlowMotion,
    tune: AnimatedTune,
    colorful: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val cx = w / 2f
    val t = motion.time
    val e = motion.energy
    val b = tune.brightness.value.coerceIn(0f, 1f)
    val pulsing = 1f + tune.pulse.value * 0.22f * sin(t * 3.2f)
    val breathing = sin(t * TWO_PI / BREATH_PERIOD)
    // Rest: blob centers sit 4-5dp below the bottom edge (Figma). The voice raises them up to ~10dp.
    val lift = d * (tune.lift.value * 40f + tune.breath.value * 80f * breathing + 10f * e)
    val grow = 1f + 0.3f * e

    val mono = 1f - colorful
    if (mono > 0.001f) {
        drawBlob(
            outer, cx + 3f * d * sin(t * 0.5f), h + 4f * d - lift, grow,
            lerp(ZunoColors.GlowAccent, ZunoColors.Gray10, e), mono * b,
        )
    }
    if (colorful > 0.001f) {
        val drift = w * 0.06f
        val y = h + 4f * d - lift
        drawBlob(tinted, w * 0.28f + drift * sin(t * 0.45f), y, grow, TutorGreen, colorful * b)
        drawBlob(tinted, w * 0.72f + drift * sin(t * 0.38f + 2.1f), y, grow, TutorBlue, colorful * b)
        drawBlob(tinted, cx + drift * sin(t * 0.52f + 4.2f), y + 2f * d, grow * 0.9f, TutorTeal, colorful * b)
    }

    val innerMono = lerp(ZunoColors.GlowHighlight, Color.White, 0.6f * e)
    val innerTutor = lerp(lerp(TutorTeal, Color.White, 0.45f), Color.White, 0.3f * e)
    drawBlob(
        inner,
        cx + 4f * d * sin(t * 0.41f + 1.3f),
        h + 5f * d - lift * 0.8f,
        (1f + 0.3f * e) * pulsing,
        lerp(innerMono, innerTutor, colorful),
        b,
    )
}

/**
 * A pre-blurred white oval (Figma layer blur), rendered once in software so it works on every API
 * level; tinted at draw time.
 */
private class BlurredOval(val image: ImageBitmap, val width: Float, val height: Float, val pad: Float) {
    companion object {
        fun create(width: Float, height: Float, sigma: Float): BlurredOval {
            // Blurred content survives heavy downsampling; keep ~6px of sigma in the bitmap.
            val scale = (6f / sigma.coerceAtLeast(0.01f)).coerceAtMost(1f)
            val s = sigma * scale
            val pad = ceil(s * 3f)
            val bitmap = Bitmap.createBitmap(
                ceil(width * scale + pad * 2f).toInt().coerceAtLeast(1),
                ceil(height * scale + pad * 2f).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                // Skia: sigma = 0.57735 * radius + 0.5
                maskFilter = BlurMaskFilter(((s - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            android.graphics.Canvas(bitmap).drawOval(pad, pad, pad + width * scale, pad + height * scale, paint)
            return BlurredOval(bitmap.asImageBitmap(), width, height, pad / scale)
        }
    }
}

private fun DrawScope.drawBlob(
    blob: BlurredOval,
    centerX: Float,
    centerY: Float,
    scale: Float,
    color: Color,
    alpha: Float,
) {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0.001f) return
    val w = (blob.width + blob.pad * 2f) * scale
    val h = (blob.height + blob.pad * 2f) * scale
    drawImage(
        image = blob.image,
        dstOffset = IntOffset((centerX - w / 2f).roundToInt(), (centerY - h / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = a,
        colorFilter = ColorFilter.tint(color),
        filterQuality = FilterQuality.Low,
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

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

@Preview(name = "Agent Glow · Listening", widthDp = 386, heightDp = 600)
@Composable
private fun ZunoAgentGlowPreview() {
    ZunoAgentGlow(
        state = AgentGlowState.Listening,
        audioLevel = { 0.5f },
        modifier = Modifier.background(ZunoColors.BgCanvas).fillMaxSize(),
    )
}

@Preview(name = "Live Indicator · Speaking", widthDp = 118, heightDp = 56)
@Composable
private fun ZunoLiveIndicatorPreview() {
    ZunoLiveIndicator(
        state = AgentGlowState.Speaking,
        audioLevel = { 0.5f },
        modifier = Modifier.width(118.dp),
    )
}
