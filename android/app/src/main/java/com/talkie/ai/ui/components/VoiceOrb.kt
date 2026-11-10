package com.talkie.ai.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkie.ai.ui.theme.ZunoColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** What the tutor is doing; the orb melts from one state into the next. */
enum class VoiceOrbState(internal val spin: Float) {
    Idle(0.16f),
    Listening(0.3f),
    /** Collapses into a spinning ring (also used while a call connects). */
    Thinking(1.9f),
    Speaking(0.42f),
}

/**
 * Voice orb — a sphere of dots (port of `orb/src/components/VoiceOrb.tsx`).
 * Idle breathes, Listening bulges with the mic level, Speaking ripples with the tutor's voice and
 * Thinking folds into a spinning ring. State changes are eased, so the shape morphs between them.
 *
 * The sphere fills 64% of the orb's box; the rest leaves room for bulges and the glow.
 * [level] is loudness 0..1; without it the orb makes up a natural-looking level.
 */
@Composable
fun ZunoVoiceOrb(
    state: VoiceOrbState,
    modifier: Modifier = Modifier,
    size: Dp = 296.dp,
    level: (() -> Float)? = null,
    dots: Int = 520,
    ink: Color = ZunoColors.TextPrimary,
    accent: Color = Color.White,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val sim = remember(dots) { OrbSimulation(max(60, dots), state) }
    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(level)
    val reducedMotion = rememberReducedMotion()
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        while (true) withFrameNanos { frameNanos = it }
    }

    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        // Reading the frame clock here redraws every frame without recomposing.
        val now = if (reducedMotion) System.nanoTime() else frameNanos
        sim.step(now, currentState, currentLevel?.invoke(), reducedMotion)
        sim.draw(this, ink, accent)
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

private class OrbSimulation(private val count: Int, initial: VoiceOrbState) {
    // Fibonacci sphere
    private val points = FloatArray(count * 3).also { p ->
        val golden = (Math.PI * (3 - sqrt(5.0))).toFloat()
        for (i in 0 until count) {
            val y = 1f - (i / (count - 1f)) * 2f
            val r = sqrt(1f - y * y)
            p[i * 3] = cos(golden * i) * r
            p[i * 3 + 1] = y
            p[i * 3 + 2] = sin(golden * i) * r
        }
    }
    private val states = VoiceOrbState.entries
    private val weights = FloatArray(states.size).also { it[initial.ordinal] = 1f }
    private var angle = 0f
    private var smoothLevel = 0f
    private var startNanos = 0L
    private var lastNanos = 0L
    private var t = 0f

    fun step(now: Long, state: VoiceOrbState, level: Float?, reduced: Boolean) {
        if (startNanos == 0L) {
            startNanos = now
            lastNanos = now
        }
        val dt = min(0.05f, (now - lastNanos) / 1e9f).coerceAtLeast(0f)
        lastNanos = now
        t = (now - startNanos) / 1e9f

        var speed = 0f
        for (s in states) {
            val target = if (s == state) 1f else 0f
            weights[s.ordinal] = if (reduced) target else weights[s.ordinal] + (target - weights[s.ordinal]) * min(1f, dt * 5f)
            speed += weights[s.ordinal] * s.spin
        }
        angle += speed * dt

        val fake = if (state == VoiceOrbState.Listening) {
            clamp01(0.35f + 0.35f * sin(t * 2.1f) * sin(t * 5.3f) + 0.3f * sin(t * 9.7f + 1f) * sin(t * 0.9f))
        } else {
            clamp01(0.3f + 0.7f * abs(sin(t * 4.1f)) * (0.5f + 0.5f * sin(t * 1.3f + 2f)))
        }
        val target = level?.let(::clamp01) ?: fake
        smoothLevel = if (reduced) target else smoothLevel + (target - smoothLevel) * min(1f, dt * 12f)
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, ink: Color, accent: Color) = with(scope) {
        val size = min(this.size.width, this.size.height)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val radius = size * 0.32f
        val wIdle = weights[VoiceOrbState.Idle.ordinal]
        val wListen = weights[VoiceOrbState.Listening.ordinal]
        val wThink = weights[VoiceOrbState.Thinking.ordinal]
        val wSpeak = weights[VoiceOrbState.Speaking.ordinal]
        val tilt = 0.32f + wThink * 0.55f
        val sinTilt = sin(tilt)
        val cosTilt = cos(tilt)
        val sinA = sin(angle)
        val cosA = cos(angle)
        val baseDot = max(0.9f * density, size / 190f)

        val glow = (wListen + wSpeak) * smoothLevel * 0.16f + wThink * 0.05f
        if (glow > 0.004f) {
            val outer = radius * 1.55f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to accent.copy(alpha = glow),
                    (0.2f / 1.55f) to accent.copy(alpha = glow),
                    1f to accent.copy(alpha = 0f),
                    center = Offset(cx, cy),
                    radius = outer,
                ),
                radius = outer,
                center = Offset(cx, cy),
            )
        }

        for (i in 0 until count) {
            val px = points[i * 3]
            val py = points[i * 3 + 1]
            val pz = points[i * 3 + 2]

            val noise = (sin(px * 3.1f + t * 2.3f) + sin(py * 4.3f - t * 1.7f) + sin(pz * 5.2f + t * 2.9f)) / 3f
            val idleWave = wIdle * 0.022f * sin(t * 1.3f + py * 2f)
            val listenBump = wListen * smoothLevel * 0.3f * max(0f, noise).pow(1.4f)
            val speakWave = wSpeak * smoothLevel * 0.13f * sin(py * 7.5f - t * 7f)
            val disp = idleWave + listenBump + speakWave

            var x = px * (1 + disp)
            var y = py * (1 + disp)
            var z = pz * (1 + disp)
            var highlight = 0f
            var fade = 1f

            // Thinking: collapse into a spinning ring
            if (wThink > 0.001f) {
                val flat = hypot(px, pz)
                val len = if (flat == 0f) 1f else flat
                val wobble = 1f + 0.035f * sin(t * 3f + i * 0.7f)
                x += ((px / len) * wobble - x) * wThink
                y += (py * 0.2f - y) * wThink
                z += ((pz / len) * wobble - z) * wThink
                fade = 1f - wThink * (1f - clamp01((flat - 0.25f) * 2.2f))
                val a = atan2(pz, px) + angle
                highlight = wThink * max(0f, cos(a - t * 2.2f)).pow(6)
            }

            val rx = x * cosA + z * sinA
            val rz = -x * sinA + z * cosA
            val ry = y * cosTilt - rz * sinTilt
            val depth = y * sinTilt + rz * cosTilt
            val persp = 3.4f / (3.4f - depth)
            val sx = cx + rx * radius * persp
            val sy = cy + ry * radius * persp

            val near = (depth + 1.3f) / 2.6f
            val energy = clamp01(abs(listenBump + speakWave) * 9f + highlight)
            val r = baseDot * (0.55f + 0.75f * near) * persp * (1f + energy * 0.5f)
            val alpha = clamp01(0.12f + 0.88f * near.coerceAtLeast(0f).pow(1.6f) + energy * 0.3f) * fade
            if (alpha < 0.01f) continue

            val color = Color(
                red = ink.red + (accent.red - ink.red) * energy,
                green = ink.green + (accent.green - ink.green) * energy,
                blue = ink.blue + (accent.blue - ink.blue) * energy,
                alpha = alpha * (ink.alpha + (accent.alpha - ink.alpha) * energy),
            )
            drawCircle(color = color, radius = r, center = Offset(sx, sy))
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, widthDp = 620, heightDp = 170)
@Composable
private fun ZunoVoiceOrbPreview() {
    Row(Modifier.background(ZunoColors.BgCanvas), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        VoiceOrbState.entries.forEach { ZunoVoiceOrb(it, size = 150.dp) }
    }
}
