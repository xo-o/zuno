package com.talkie.ai.ui.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.talkie.ai.model.AgentState
import com.talkie.ai.ui.theme.TalkieTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * All states supported by [PlasmaOrb] (`OrbState` in `voiceorbs/src/registry/lib/orb-state.ts`).
 */
enum class PlasmaOrbState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
    DISABLED
}

/**
 * Maps the app's [AgentState] to [PlasmaOrbState].
 */
fun AgentState.toPlasmaOrbState(): PlasmaOrbState = when (this) {
    AgentState.DISCONNECTED -> PlasmaOrbState.IDLE
    AgentState.CONNECTING -> PlasmaOrbState.CONNECTING
    AgentState.IDLE -> PlasmaOrbState.IDLE
    AgentState.LISTENING -> PlasmaOrbState.LISTENING
    AgentState.THINKING -> PlasmaOrbState.THINKING
    AgentState.SPEAKING -> PlasmaOrbState.SPEAKING
}

// Default palette from `plasma-orb.tsx` (`colorFrom = '#7c3aed'`, `colorTo = '#06b6d4'`)
val DefaultPlasmaColorFrom = Color(0xFF7C3AED)
val DefaultPlasmaColorTo = Color(0xFF06B6D4)

// Error palette from `orb-state.ts` (`ERROR_COLOR_FROM = '#fb7185'`, `ERROR_COLOR_TO = '#f43f5e'`)
private val ErrorColorFrom = Color(0xFFFB7185)
private val ErrorColorTo = Color(0xFFF43F5E)

private const val MAX_FRAME_DT = 0.1f
private const val STATIC_PHASE = 0.9f
private const val BASE_TIME_SECONDS = 8.0f // BASE_FRAME = 8000 in `@paper-design/shaders`
private const val FIRST_FRAME_OFFSET = 41.5f
private const val MESH_SCALE = 1.15f
private const val GRAIN_OVERLAY = 0.05f
private const val TAU = (PI * 2.0).toFloat()

private const val ENTER_RATE = 14f
private const val SETTLE_RATE = 5f
private const val ERROR_RATE = 10f
private const val LEVEL_ATTACK_RATE = 14f
private const val LEVEL_RELEASE_RATE = 4f

internal data class PlasmaTune(
    val flow: Float,
    val distortion: Float,
    val swirl: Float,
    val grain: Float,
    val outer: Float,
    val inner: Float,
    val beat: Float,
    val beatRate: Float,
    val error: Float
)

// Exact `TUNE` table from `voiceorbs/src/registry/orbe/plasma-orb/plasma-orb.tsx`
internal val IDLE_PLASMA_TUNE = PlasmaTune(
    flow = 0.30f,
    distortion = 0.42f,
    swirl = 0.26f,
    grain = 0.06f,
    outer = 0.20f,
    inner = 0.20f,
    beat = 0.00f,
    beatRate = 0.30f,
    error = 0.00f
)

private fun tuneForState(state: PlasmaOrbState): PlasmaTune = when (state) {
    PlasmaOrbState.IDLE -> IDLE_PLASMA_TUNE
    PlasmaOrbState.CONNECTING -> PlasmaTune(0.40f, 0.40f, 0.40f, 0.08f, 0.10f, 0.10f, 0.60f, 0.28f, 0.00f)
    PlasmaOrbState.LISTENING -> PlasmaTune(0.60f, 0.44f, 0.22f, 0.12f, 1.00f, 0.15f, 0.00f, 0.30f, 0.00f)
    PlasmaOrbState.THINKING -> PlasmaTune(0.70f, 0.35f, 0.78f, 0.10f, 0.10f, 0.10f, 1.00f, 0.50f, 0.00f)
    PlasmaOrbState.SPEAKING -> PlasmaTune(1.20f, 0.55f, 0.34f, 0.18f, 0.30f, 1.00f, 0.00f, 0.30f, 0.00f)
    PlasmaOrbState.ERROR -> PlasmaTune(1.70f, 0.85f, 0.55f, 0.20f, 0.20f, 0.20f, 0.00f, 0.30f, 1.00f)
    PlasmaOrbState.DISABLED -> PlasmaTune(0.00f, 0.40f, 0.20f, 0.04f, 0.00f, 0.00f, 0.00f, 0.30f, 0.00f)
}

private fun stateRate(state: PlasmaOrbState): Float = when (state) {
    PlasmaOrbState.IDLE, PlasmaOrbState.DISABLED -> SETTLE_RATE
    PlasmaOrbState.ERROR -> ERROR_RATE
    else -> ENTER_RATE
}

private fun wave(x: Float): Float = 0.5f - 0.5f * cos(x)

/**
 * Exact port of `stateEnergy(state, t)` from `orb-state.ts`.
 */
private fun stateEnergy(state: PlasmaOrbState, t: Float): Float = when (state) {
    PlasmaOrbState.LISTENING -> 0.4f + 0.32f * wave(t * 17f) + 0.18f * wave(t * 8.2f + 3f)
    PlasmaOrbState.SPEAKING -> 0.3f + 0.24f * wave(t * 12.4f) + 0.16f * wave(t * 6f + 1.2f)
    PlasmaOrbState.THINKING -> 0.24f + 0.2f * wave(t * 4.8f)
    PlasmaOrbState.CONNECTING -> 0.12f + 0.1f * wave(t * 3.2f)
    PlasmaOrbState.ERROR -> 0.2f
    PlasmaOrbState.IDLE, PlasmaOrbState.DISABLED -> 0f
}

private fun approach(current: Float, target: Float, rate: Float, dt: Float): Float =
    current + (target - current) * (1f - exp(-rate * dt))

private fun smoothLevel(current: Float, target: Float, dt: Float): Float =
    approach(current, target, if (target > current) LEVEL_ATTACK_RATE else LEVEL_RELEASE_RATE, dt)

// ============================================================================
// Linear-RGB color mixing ported 1:1 from `voiceorbs/src/registry/lib/orb-color.ts`
// ============================================================================

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(v: Float): Float {
    val c = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
    return c.coerceIn(0f, 1f)
}

internal fun mixSrgbColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    if (clamped <= 0f) return a
    if (clamped >= 1f) return b
    val r = linearToSrgb(srgbToLinear(a.red) + (srgbToLinear(b.red) - srgbToLinear(a.red)) * clamped)
    val g = linearToSrgb(srgbToLinear(a.green) + (srgbToLinear(b.green) - srgbToLinear(a.green)) * clamped)
    val bl = linearToSrgb(srgbToLinear(a.blue) + (srgbToLinear(b.blue) - srgbToLinear(a.blue)) * clamped)
    return Color(r, g, bl, 1f)
}

private fun shadeColor(color: Color, t: Float): Color = mixSrgbColor(color, Color.Black, t)
private fun tintColor(color: Color, t: Float): Color = mixSrgbColor(color, Color.White, t)

/**
 * Fills [outRgb15] (5 stops × 3 RGB channels) with `livePalette(colorFrom, colorTo, errorMix)`.
 */
internal fun fillPlasmaPalette(
    colorFrom: Color,
    colorTo: Color,
    errorMix: Float,
    outRgb15: FloatArray
) {
    val b0 = shadeColor(colorFrom, 0.35f)
    val b1 = colorFrom
    val b2 = mixSrgbColor(colorFrom, colorTo, 0.5f)
    val b3 = colorTo
    val b4 = tintColor(colorTo, 0.35f)

    val stops = if (errorMix <= 0.0001f) {
        arrayOf(b0, b1, b2, b3, b4)
    } else {
        val e0 = shadeColor(ErrorColorFrom, 0.35f)
        val e1 = ErrorColorFrom
        val e2 = mixSrgbColor(ErrorColorFrom, ErrorColorTo, 0.5f)
        val e3 = ErrorColorTo
        val e4 = tintColor(ErrorColorTo, 0.35f)
        arrayOf(
            mixSrgbColor(b0, e0, errorMix),
            mixSrgbColor(b1, e1, errorMix),
            mixSrgbColor(b2, e2, errorMix),
            mixSrgbColor(b3, e3, errorMix),
            mixSrgbColor(b4, e4, errorMix)
        )
    }

    for (i in 0 until 5) {
        val c = stops[i]
        outRgb15[i * 3] = c.red
        outRgb15[i * 3 + 1] = c.green
        outRgb15[i * 3 + 2] = c.blue
    }
}

/**
 * Exact AGSL port of `@paper-design/shaders` `meshGradientFragmentShader` +
 * `plasma-orb.tsx` radial specular & inner shadow overlays.
 */
internal const val PLASMA_ORB_AGSL = """
uniform vec2 uCenter;
uniform float uRadius;
uniform float uTime;
uniform float uDistortion;
uniform float uSwirl;
uniform float uGrainMixer;
uniform float uGrainOverlay;
uniform vec3 uColor0;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
uniform vec3 uColor4;

float smoothstepSafe(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

vec2 rotate2D(vec2 uv, float th) {
    float c = cos(th);
    float s = sin(th);
    return mat2(c, s, -s, c) * uv;
}

float hash21(vec2 p) {
    p = fract(p * vec2(0.3183099, 0.3678794)) + 0.1;
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float valueNoise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    float x1 = mix(a, b, u.x);
    float x2 = mix(c, d, u.x);
    return mix(x1, x2, u.y);
}

vec2 getPosition(int idx, float t) {
    float fi = float(idx);
    float a = fi * 0.37;
    float b = 0.6 + fract(fi / 3.0) * 0.9;
    float c = 0.8 + fract(float(idx + 1) / 4.0);
    float x = sin(t * b + a);
    float y = cos(t * c + a * 1.5);
    return 0.5 + 0.5 * vec2(x, y);
}

vec3 getStopColor(int idx) {
    if (idx == 0) return uColor0;
    if (idx == 1) return uColor1;
    if (idx == 2) return uColor2;
    if (idx == 3) return uColor3;
    return uColor4;
}

vec4 main(vec2 fragCoord) {
    vec2 norm = (fragCoord - uCenter) / max(1e-4, uRadius);
    float r = length(norm);
    float edgeMask = 1.0 - smoothstepSafe(0.985, 1.0, r);
    if (edgeMask <= 0.001) {
        return vec4(0.0);
    }

    // Object UV with scale = 1.15 (WebGL Y-up convention for MeshGradient)
    vec2 vObjectUV = vec2(norm.x, -norm.y) * (0.5 / 1.15);
    vec2 uv = vObjectUV + 0.5;
    vec2 grainUV = uv * 1000.0;

    float grain = valueNoise(grainUV);
    float mixerGrain = 0.4 * uGrainMixer * (grain - 0.5);

    const float firstFrameOffset = 41.5;
    float t = 0.5 * (uTime + firstFrameOffset);

    float radius = smoothstepSafe(0.0, 1.0, length(uv - 0.5));
    float center = 1.0 - radius;
    for (int iter = 1; iter <= 2; iter++) {
        float fi = float(iter);
        float sy = smoothstepSafe(0.0, 1.0, uv.y);
        float sx = smoothstepSafe(0.0, 1.0, uv.x);
        uv.x += uDistortion * center / fi * sin(t + fi * 0.4 * sy) * cos(0.2 * t + fi * 2.4 * sy);
        uv.y += uDistortion * center / fi * cos(t + fi * 2.0 * sx);
    }

    vec2 uvRotated = rotate2D(uv - vec2(0.5), -3.0 * uSwirl * radius) + vec2(0.5);

    vec3 color = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < 5; i++) {
        vec2 pos = getPosition(i, t) + mixerGrain;
        float dist = max(1e-4, length(uvRotated - pos));
        float d2 = dist * dist;
        float d35 = d2 * dist * sqrt(dist);
        float weight = 1.0 / (d35 + 1e-3);
        color += getStopColor(i) * weight;
        totalWeight += weight;
    }

    color /= max(1e-4, totalWeight);

    float g1 = valueNoise(rotate2D(grainUV, 1.0) + vec2(3.0));
    float g2 = valueNoise(rotate2D(grainUV, 2.0) + vec2(-1.0));
    float grainOverlay = pow(max(1e-5, mix(g1, g2, 0.5)), 1.3);
    float grainOverlayV = grainOverlay * 2.0 - 1.0;
    vec3 grainOverlayColor = vec3(step(0.0, grainOverlayV));
    float grainOverlayStrength = pow(max(1e-5, uGrainOverlay * abs(grainOverlayV)), 0.8);
    color = mix(color, grainOverlayColor, 0.35 * grainOverlayStrength);

    // Top-down CSS coordinates [0..1] for the 3 radial specular/shadow overlays in `plasma-orb.tsx`
    vec2 cssUV = norm * 0.5 + 0.5;

    // 1. radial-gradient(circle at 31% 22%, rgba(255,255,255,0.55), transparent 14%)
    float dSpec1 = length(cssUV - vec2(0.31, 0.22));
    float aSpec1 = 0.55 * clamp(1.0 - dSpec1 / 0.1458, 0.0, 1.0);
    color = mix(color, vec3(1.0), aSpec1);

    // 2. radial-gradient(circle at 30% 26%, rgba(255,255,255,0.28), transparent 48%)
    float dSpec2 = length(cssUV - vec2(0.30, 0.26));
    float aSpec2 = 0.28 * clamp(1.0 - dSpec2 / 0.4889, 0.0, 1.0);
    color = mix(color, vec3(1.0), aSpec2);

    // 3. radial-gradient(circle at 68% 76%, rgba(10,14,24,0.42), transparent 60%)
    float dShade = length(cssUV - vec2(0.68, 0.76));
    float aShade = 0.42 * clamp(1.0 - dShade / 0.6119, 0.0, 1.0);
    color = mix(color, vec3(0.0392, 0.0549, 0.0941), aShade);

    color = clamp(color, 0.0, 1.0);
    return vec4(color * edgeMask, edgeMask);
}
"""

// ============================================================================
// Pure-Kotlin exact evaluator of `PlasmaOrb` for Compose @Preview
// and devices running Android < API 33.
// ============================================================================

private fun fract(x: Float): Float = x - floor(x)

private fun smoothstepSafe(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun hash21(px: Float, py: Float): Float {
    var x = fract(px * 0.3183099f) + 0.1f
    var y = fract(py * 0.3678794f) + 0.1f
    val d = x * (x + 19.19f) + y * (y + 19.19f)
    x += d
    y += d
    return fract(x * y)
}

private fun valueNoise(stX: Float, stY: Float): Float {
    val ix = floor(stX)
    val iy = floor(stY)
    val fx = stX - ix
    val fy = stY - iy
    val a = hash21(ix, iy)
    val b = hash21(ix + 1f, iy)
    val c = hash21(ix, iy + 1f)
    val d = hash21(ix + 1f, iy + 1f)
    val ux = fx * fx * (3f - 2f * fx)
    val uy = fy * fy * (3f - 2f * fy)
    return mix(mix(a, b, ux), mix(c, d, ux), uy)
}

internal fun renderPlasmaOrbBitmap(
    gridSize: Int,
    uTime: Float,
    uDistortion: Float,
    uSwirl: Float,
    uGrainMixer: Float,
    paletteRgb15: FloatArray
): ImageBitmap {
    val pixels = IntArray(gridSize * gridSize)
    val halfSize = gridSize * 0.5f
    val invHalfSize = 1f / halfSize
    val t = 0.5f * (uTime + FIRST_FRAME_OFFSET)

    val posX = FloatArray(5)
    val posY = FloatArray(5)
    for (i in 0 until 5) {
        val fi = i.toFloat()
        val a = fi * 0.37f
        val b = 0.6f + fract(fi / 3f) * 0.9f
        val c = 0.8f + fract((i + 1).toFloat() / 4f)
        posX[i] = 0.5f + 0.5f * sin(t * b + a)
        posY[i] = 0.5f + 0.5f * cos(t * c + a * 1.5f)
    }

    val cosT = cos(t)
    val sinT = sin(t)
    val cos02T = cos(0.2f * t)
    val sin02T = sin(0.2f * t)

    for (py in 0 until gridSize) {
        val normY = (py + 0.5f - halfSize) * invHalfSize
        val cssY = normY * 0.5f + 0.5f
        for (px in 0 until gridSize) {
            val normX = (px + 0.5f - halfSize) * invHalfSize
            val r = hypot(normX, normY)
            val edgeMask = 1f - smoothstepSafe(0.985f, 1.0f, r)
            if (edgeMask <= 0.001f) {
                pixels[py * gridSize + px] = 0
                continue
            }

            var uvX = normX * (0.5f / MESH_SCALE) + 0.5f
            var uvY = -normY * (0.5f / MESH_SCALE) + 0.5f

            val grain = valueNoise(uvX * 1000f, uvY * 1000f)
            val mixerGrain = 0.4f * uGrainMixer * (grain - 0.5f)

            val rad = smoothstepSafe(0f, 1f, hypot(uvX - 0.5f, uvY - 0.5f))
            val center = 1f - rad

            for (iter in 1..2) {
                val fi = iter.toFloat()
                val sy = smoothstepSafe(0f, 1f, uvY)
                val sx = smoothstepSafe(0f, 1f, uvX)
                val a1 = fi * 0.4f * sy
                val a2 = fi * 2.4f * sy
                val sinPart = sinT * cos(a1) + cosT * sin(a1)
                val cosPart = cos02T * cos(a2) - sin02T * sin(a2)
                uvX += uDistortion * center / fi * sinPart * cosPart
                uvY += uDistortion * center / fi * cos(t + fi * 2f * sx)
            }

            val angle = -3f * uSwirl * rad
            val ca = cos(angle)
            val sa = sin(angle)
            val dx0 = uvX - 0.5f
            val dy0 = uvY - 0.5f
            val rotX = ca * dx0 - sa * dy0 + 0.5f
            val rotY = sa * dx0 + ca * dy0 + 0.5f

            var colR = 0f
            var colG = 0f
            var colB = 0f
            var totalW = 0f

            for (i in 0 until 5) {
                val d = max(1e-4f, hypot(rotX - (posX[i] + mixerGrain), rotY - (posY[i] + mixerGrain)))
                val d35 = d * d * d * kotlin.math.sqrt(d)
                val w = 1f / (d35 + 1e-3f)
                colR += paletteRgb15[i * 3] * w
                colG += paletteRgb15[i * 3 + 1] * w
                colB += paletteRgb15[i * 3 + 2] * w
                totalW += w
            }

            val invW = 1f / max(1e-4f, totalW)
            colR *= invW
            colG *= invW
            colB *= invW

            val cssX = normX * 0.5f + 0.5f

            val dSpec1 = hypot(cssX - 0.31f, cssY - 0.22f)
            val aSpec1 = 0.55f * (1f - dSpec1 / 0.1458f).coerceIn(0f, 1f)
            colR = mix(colR, 1f, aSpec1)
            colG = mix(colG, 1f, aSpec1)
            colB = mix(colB, 1f, aSpec1)

            val dSpec2 = hypot(cssX - 0.30f, cssY - 0.26f)
            val aSpec2 = 0.28f * (1f - dSpec2 / 0.4889f).coerceIn(0f, 1f)
            colR = mix(colR, 1f, aSpec2)
            colG = mix(colG, 1f, aSpec2)
            colB = mix(colB, 1f, aSpec2)

            val dShade = hypot(cssX - 0.68f, cssY - 0.76f)
            val aShade = 0.42f * (1f - dShade / 0.6119f).coerceIn(0f, 1f)
            colR = mix(colR, 0.0392f, aShade).coerceIn(0f, 1f)
            colG = mix(colG, 0.0549f, aShade).coerceIn(0f, 1f)
            colB = mix(colB, 0.0941f, aShade).coerceIn(0f, 1f)

            val sr = (colR * 255f + 0.5f).toInt().coerceIn(0, 255)
            val sg = (colG * 255f + 0.5f).toInt().coerceIn(0, 255)
            val sb = (colB * 255f + 0.5f).toInt().coerceIn(0, 255)
            val saByte = (edgeMask * 255f + 0.5f).toInt().coerceIn(0, 255)

            pixels[py * gridSize + px] = (saByte shl 24) or (sr shl 16) or (sg shl 8) or sb
        }
    }

    return Bitmap.createBitmap(pixels, gridSize, gridSize, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Shared `DrawScope` primitive to draw a `PlasmaOrb` in `IDLE` status at [center] and [radius]
 * (used by `OnboardingIntroMotionCanvas` for Scene 2 and Scene 3 orbs).
 */
internal fun DrawScope.drawPlasmaIdleOrb(
    center: Offset,
    radius: Float,
    tSeconds: Float,
    runtimeShader: RuntimeShader?,
    fallbackBitmap: ImageBitmap?,
    paletteRgb15: FloatArray
) {
    if (radius <= 0f) return
    val uTime = BASE_TIME_SECONDS + tSeconds * IDLE_PLASMA_TUNE.flow

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
        runtimeShader.setFloatUniform("uCenter", center.x, center.y)
        runtimeShader.setFloatUniform("uRadius", radius)
        runtimeShader.setFloatUniform("uTime", uTime)
        runtimeShader.setFloatUniform("uDistortion", IDLE_PLASMA_TUNE.distortion)
        runtimeShader.setFloatUniform("uSwirl", IDLE_PLASMA_TUNE.swirl)
        runtimeShader.setFloatUniform("uGrainMixer", IDLE_PLASMA_TUNE.grain)
        runtimeShader.setFloatUniform("uGrainOverlay", GRAIN_OVERLAY)
        runtimeShader.setFloatUniform("uColor0", paletteRgb15[0], paletteRgb15[1], paletteRgb15[2])
        runtimeShader.setFloatUniform("uColor1", paletteRgb15[3], paletteRgb15[4], paletteRgb15[5])
        runtimeShader.setFloatUniform("uColor2", paletteRgb15[6], paletteRgb15[7], paletteRgb15[8])
        runtimeShader.setFloatUniform("uColor3", paletteRgb15[9], paletteRgb15[10], paletteRgb15[11])
        runtimeShader.setFloatUniform("uColor4", paletteRgb15[12], paletteRgb15[13], paletteRgb15[14])

        drawCircle(
            brush = ShaderBrush(runtimeShader),
            radius = radius,
            center = center
        )
    } else if (fallbackBitmap != null) {
        val diameter = (radius * 2f).roundToInt().coerceAtLeast(1)
        drawImage(
            image = fallbackBitmap,
            dstOffset = IntOffset(
                (center.x - radius).roundToInt(),
                (center.y - radius).roundToInt()
            ),
            dstSize = IntSize(diameter, diameter),
            filterQuality = FilterQuality.High
        )
    }
}

/**
 * Kotlin & Jetpack Compose implementation of `PlasmaOrb`
 * ported 1:1 from `voiceorbs/src/registry/orbe/plasma-orb/plasma-orb.tsx`.
 */
@Composable
fun PlasmaOrb(
    state: PlasmaOrbState = PlasmaOrbState.IDLE,
    size: Dp = 160.dp,
    speed: Float = 1f,
    colorFrom: Color = DefaultPlasmaColorFrom,
    colorTo: Color = DefaultPlasmaColorTo,
    audioLevel: (() -> Float)? = null,
    label: String = "Assistant orb",
    modifier: Modifier = Modifier
) {
    val isInspection = LocalInspectionMode.current
    val isReducedMotion = remember(isInspection) {
        isInspection || !ValueAnimator.areAnimatorsEnabled()
    }

    val currentState by rememberUpdatedState(state)
    val currentSpeed by rememberUpdatedState(speed)
    val currentColorFrom by rememberUpdatedState(colorFrom)
    val currentColorTo by rememberUpdatedState(colorTo)
    val currentAudioLevel by rememberUpdatedState(audioLevel)

    val allStates = remember { PlasmaOrbState.entries.toTypedArray() }
    val stateWeights = remember {
        FloatArray(allStates.size).apply {
            this[state.ordinal] = 1f
        }
    }
    val paletteRgb15 = remember(colorFrom, colorTo) {
        FloatArray(15).also { buf ->
            fillPlasmaPalette(
                colorFrom = colorFrom,
                colorTo = colorTo,
                errorMix = tuneForState(state).error,
                outRgb15 = buf
            )
        }
    }

    val initialTune = remember { tuneForState(state) }
    var shaderClock by remember {
        mutableFloatStateOf(if (isInspection) STATIC_PHASE * initialTune.flow else 0f)
    }
    var beatClock by remember { mutableFloatStateOf(0f) }
    var level by remember {
        mutableFloatStateOf(if (isInspection) stateEnergy(state, STATIC_PHASE) else 0f)
    }
    var outer by remember { mutableFloatStateOf(0f) }
    var liveDistortion by remember { mutableFloatStateOf(initialTune.distortion) }
    var liveSwirl by remember { mutableFloatStateOf(initialTune.swirl) }
    var liveGrain by remember { mutableFloatStateOf(initialTune.grain) }
    var liveErrorMix by remember { mutableFloatStateOf(initialTune.error) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isReducedMotion) {
        if (isReducedMotion) return@LaunchedEffect
        var lastNanos = -1L
        var phase = 0f

        while (true) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos < 0L) {
                    0f
                } else {
                    min((nowNanos - lastNanos) / 1_000_000_000f, MAX_FRAME_DT)
                }
                lastNanos = nowNanos

                val targetState = currentState
                val rate = stateRate(targetState)
                var totalWeight = 0f
                for (s in allStates) {
                    val idx = s.ordinal
                    val target = if (s == targetState) 1f else 0f
                    val next = approach(stateWeights[idx], target, rate, dt)
                    stateWeights[idx] = if (target == 0f && next < 0.001f) 0f else next
                    totalWeight += stateWeights[idx]
                }
                if (totalWeight > 0f) {
                    for (i in stateWeights.indices) {
                        stateWeights[i] /= totalWeight
                    }
                }

                val step = dt * max(0f, currentSpeed)
                phase += step

                val externalLevel = currentAudioLevel?.invoke()
                val targetLevel = if (externalLevel != null && externalLevel >= 0f) {
                    externalLevel.coerceIn(0f, 1f)
                } else {
                    var blendedEnergy = 0f
                    for (s in allStates) {
                        val w = stateWeights[s.ordinal]
                        if (w > 0f) {
                            blendedEnergy += w * stateEnergy(s, phase)
                        }
                    }
                    blendedEnergy
                }
                level = smoothLevel(level, targetLevel, dt)

                var bFlow = 0f
                var bDistortion = 0f
                var bSwirl = 0f
                var bGrain = 0f
                var bOuter = 0f
                var bInner = 0f
                var bBeat = 0f
                var bBeatRate = 0f
                var bError = 0f
                for (s in allStates) {
                    val w = stateWeights[s.ordinal]
                    if (w <= 0f) continue
                    val t = tuneForState(s)
                    bFlow += w * t.flow
                    bDistortion += w * t.distortion
                    bSwirl += w * t.swirl
                    bGrain += w * t.grain
                    bOuter += w * t.outer
                    bInner += w * t.inner
                    bBeat += w * t.beat
                    bBeatRate += w * t.beatRate
                    bError += w * t.error
                }

                beatClock += step * bBeatRate
                val beatWave = 0.5f - 0.5f * cos(beatClock * TAU)
                val innerVal = level * bInner + beatWave * bBeat * 0.4f
                val outerVal = (level * bOuter + beatWave * bBeat * 0.3f).coerceIn(0f, 1f)

                shaderClock += step * (bFlow + innerVal * 0.9f)
                outer = outerVal
                liveDistortion = (bDistortion + innerVal * 0.4f).coerceIn(0f, 1f)
                liveSwirl = (bSwirl + innerVal * 0.15f + beatWave * bBeat * 0.15f).coerceIn(0f, 1f)
                liveGrain = bGrain
                liveErrorMix = bError.coerceIn(0f, 1f)

                fillPlasmaPalette(
                    colorFrom = currentColorFrom,
                    colorTo = currentColorTo,
                    errorMix = liveErrorMix,
                    outRgb15 = paletteRgb15
                )

                frameTick++
            }
        }
    }

    val disabledAlpha by animateFloatAsState(
        targetValue = if (state == PlasmaOrbState.DISABLED) 0.5f else 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "PlasmaOrbDisabledAlpha"
    )
    val disabledGrayscale by animateFloatAsState(
        targetValue = if (state == PlasmaOrbState.DISABLED) 0.85f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "PlasmaOrbDisabledGrayscale"
    )

    val colorFilter = remember(disabledGrayscale) {
        if (disabledGrayscale > 0.001f) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(1f - disabledGrayscale) }
            )
        } else {
            null
        }
    }

    val useHardwareAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isInspection
    val runtimeShader = remember(useHardwareAgsl) {
        if (useHardwareAgsl) RuntimeShader(PLASMA_ORB_AGSL) else null
    }

    // Software bitmap is only computed on < API 33 or inside @Preview
    val softwareBitmap = remember(useHardwareAgsl, colorFrom, colorTo, state) {
        if (!useHardwareAgsl) {
            val tune = tuneForState(state)
            val previewPalette = FloatArray(15).also {
                fillPlasmaPalette(colorFrom, colorTo, tune.error, it)
            }
            renderPlasmaOrbBitmap(
                gridSize = 160,
                uTime = BASE_TIME_SECONDS + STATIC_PHASE * tune.flow,
                uDistortion = tune.distortion,
                uSwirl = tune.swirl,
                uGrainMixer = tune.grain,
                paletteRgb15 = previewPalette
            )
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = label
                this.role = Role.Image
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scaleBoost = 1f + outer * 0.07f
                    scaleX = scaleBoost
                    scaleY = scaleBoost
                    alpha = disabledAlpha
                }
        ) {
            @Suppress("UNUSED_EXPRESSION")
            frameTick

            val w = this.size.width
            val h = this.size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val sphereRadius = min(w, h) * 0.5f
            val center = Offset(w * 0.5f, h * 0.5f)
            val liveFrom = mixSrgbColor(colorFrom, ErrorColorFrom, liveErrorMix)
            val liveTo = mixSrgbColor(colorTo, ErrorColorTo, liveErrorMix)
            val glowAlpha = (0.35f + outer * 0.6f + level * 0.05f).coerceIn(0f, 1f)

            // Dual ambient outer glow matching `plasma-orb.tsx` box-shadow
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to liveFrom.copy(alpha = 0.28f * glowAlpha),
                    0.65f to liveFrom.copy(alpha = 0.08f * glowAlpha),
                    1.0f to Color.Transparent,
                    center = Offset(center.x, center.y - sphereRadius * 0.12f),
                    radius = sphereRadius * 1.15f
                ),
                radius = sphereRadius * 1.15f,
                center = Offset(center.x, center.y - sphereRadius * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to liveTo.copy(alpha = 0.28f * glowAlpha),
                    0.65f to liveTo.copy(alpha = 0.08f * glowAlpha),
                    1.0f to Color.Transparent,
                    center = Offset(center.x, center.y + sphereRadius * 0.12f),
                    radius = sphereRadius * 1.15f
                ),
                radius = sphereRadius * 1.15f,
                center = Offset(center.x, center.y + sphereRadius * 0.12f)
            )

            // MeshGradient sphere + specular/shadow overlays
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
                runtimeShader.setFloatUniform("uCenter", center.x, center.y)
                runtimeShader.setFloatUniform("uRadius", sphereRadius)
                runtimeShader.setFloatUniform("uTime", BASE_TIME_SECONDS + shaderClock)
                runtimeShader.setFloatUniform("uDistortion", liveDistortion)
                runtimeShader.setFloatUniform("uSwirl", liveSwirl)
                runtimeShader.setFloatUniform("uGrainMixer", liveGrain)
                runtimeShader.setFloatUniform("uGrainOverlay", GRAIN_OVERLAY)
                runtimeShader.setFloatUniform("uColor0", paletteRgb15[0], paletteRgb15[1], paletteRgb15[2])
                runtimeShader.setFloatUniform("uColor1", paletteRgb15[3], paletteRgb15[4], paletteRgb15[5])
                runtimeShader.setFloatUniform("uColor2", paletteRgb15[6], paletteRgb15[7], paletteRgb15[8])
                runtimeShader.setFloatUniform("uColor3", paletteRgb15[9], paletteRgb15[10], paletteRgb15[11])
                runtimeShader.setFloatUniform("uColor4", paletteRgb15[12], paletteRgb15[13], paletteRgb15[14])

                drawCircle(
                    brush = ShaderBrush(runtimeShader),
                    radius = sphereRadius,
                    center = center,
                    colorFilter = colorFilter
                )
            } else if (softwareBitmap != null) {
                drawImage(
                    image = softwareBitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    filterQuality = FilterQuality.High,
                    colorFilter = colorFilter
                )
            }

            // Subtle inset & outer rim matching `inset 0 0 0 1px color-mix(in oklab, liveFrom 45%, transparent), 0 0 0 1px rgba(255,255,255,0.08)`
            val strokePx = 1.dp.toPx()
            drawCircle(
                color = liveFrom.copy(alpha = 0.32f),
                radius = sphereRadius - strokePx * 0.5f,
                center = center,
                style = Stroke(width = strokePx)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = sphereRadius,
                center = center,
                style = Stroke(width = strokePx)
            )
        }
    }
}

@Preview(name = "PlasmaOrb · Idle", showBackground = true, backgroundColor = 0xFF010206, widthDp = 220, heightDp = 220)
@Composable
private fun PlasmaOrbIdlePreview() {
    TalkieTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlasmaOrb(state = PlasmaOrbState.IDLE, size = 160.dp)
        }
    }
}

@Preview(name = "PlasmaOrb · Speaking", showBackground = true, backgroundColor = 0xFF010206, widthDp = 220, heightDp = 220)
@Composable
private fun PlasmaOrbSpeakingPreview() {
    TalkieTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlasmaOrb(state = PlasmaOrbState.SPEAKING, size = 160.dp)
        }
    }
}
