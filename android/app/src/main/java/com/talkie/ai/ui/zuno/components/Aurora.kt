package com.talkie.ai.ui.zuno.components

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class AuroraDirection { Top, Bottom }

private const val MAX_STOPS = 5
private const val MAX_LAYERS = 3
private const val INTRO_SECONDS = 2.4f

/**
 * Aurora — a flowing band of light, ported from the React Bits `Aurora` WebGL component to AGSL.
 *
 * [direction] = [AuroraDirection.Bottom] makes the light rise from the bottom edge. [energy] is polled
 * every frame (0..1, e.g. a voice level) and pushes the band higher ([energyHeight]) and faster
 * ([energySpeed]) without recomposing. Requires API 33 for the shader; older devices get a soft
 * gradient approximation.
 */
@Composable
fun ZunoAurora(
    colorStops: List<Color>,
    modifier: Modifier = Modifier,
    amplitude: Float = 1f,
    blend: Float = 0.5f,
    height: Float = 0.45f,
    speed: Float = 1f,
    scale: Float = 1f,
    glow: Float = 0.6f,
    brightness: Float = 1f,
    layers: Int = 1,
    direction: AuroraDirection = AuroraDirection.Top,
    opacity: Float = 1f,
    intro: Boolean = true,
    energy: () -> Float = { 0f },
    energyHeight: Float = 0.3f,
    energySpeed: Float = 2f,
) {
    val inspection = LocalInspectionMode.current
    val stops = remember(colorStops) { normalizeStops(colorStops) }
    val palette = remember(stops) { oklchPalette(stops) }

    val currentSpeed by rememberUpdatedState(speed.coerceAtLeast(0f))
    val currentEnergy by rememberUpdatedState(energy)
    val currentEnergySpeed by rememberUpdatedState(energySpeed)
    val motion = remember { AuroraMotion(revealed = !intro || inspection) }

    LaunchedEffect(motion, inspection) {
        if (inspection) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0.0005f, 0.05f)
                last = now
                motion.step(dt, currentSpeed, currentEnergy().coerceIn(0f, 1f), currentEnergySpeed)
            }
        }
    }

    val shader = remember(inspection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !inspection) {
            runCatching { RuntimeShader(AURORA_AGSL) }
                .onFailure { Log.e("ZunoAurora", "Aurora shader failed to compile; using fallback", it) }
                .getOrNull()
        } else {
            null
        }
    }

    Canvas(modifier) {
        motion.frame // Redraw on every animation frame.
        val liveHeight = (height + motion.energy * energyHeight).coerceIn(0f, 1f)
        val reveal = 1f - (1f - motion.reveal).pow(3)
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.setAuroraUniforms(
                width = size.width,
                height = size.height,
                time = motion.clock,
                palette = palette,
                stopCount = stops.size,
                amplitude = amplitude.coerceAtLeast(0f),
                blend = blend.coerceIn(0f, 1f),
                bandHeight = liveHeight,
                scale = scale.coerceAtLeast(0.1f),
                glow = glow.coerceAtLeast(0f),
                brightness = brightness.coerceAtLeast(0f),
                layers = layers.coerceIn(1, MAX_LAYERS),
                flip = direction == AuroraDirection.Bottom,
                reveal = reveal,
                opacity = opacity.coerceIn(0f, 1f),
            )
            drawRect(ShaderBrush(shader))
        } else {
            drawAuroraFallback(stops, liveHeight, brightness, opacity * reveal, direction, motion.clock)
        }
    }
}

/** Frame-driven values; only read during draw so animation never recomposes. */
private class AuroraMotion(revealed: Boolean) {
    var clock = 0f
        private set
    var energy = 0f
        private set
    var reveal = if (revealed) 1f else 0f
        private set
    var frame by mutableIntStateOf(0)
        private set

    fun step(dt: Float, speed: Float, targetEnergy: Float, energySpeed: Float) {
        // Fast attack, slow release, so loud syllables punch up and silence settles gently.
        val rate = if (targetEnergy > energy) 16f else 3.5f
        energy += (targetEnergy - energy) * (1f - exp(-rate * dt))
        clock += dt * speed * (1f + energy * energySpeed)
        if (reveal < 1f) reveal = (reveal + dt / INTRO_SECONDS).coerceAtMost(1f)
        frame++
    }
}

private fun normalizeStops(colors: List<Color>): List<Color> {
    val stops = colors.take(MAX_STOPS)
    return when {
        stops.size >= 2 -> stops
        stops.size == 1 -> listOf(stops[0], stops[0])
        else -> listOf(Color(0xFF5227FF), Color(0xFF7CFF67))
    }
}

/** sRGB colors → OKLCH triples (L, C, h) with hues unwrapped so the ramp takes the short way round. */
private fun oklchPalette(stops: List<Color>): FloatArray {
    val lch = stops.map { toOklch(it.convert(ColorSpaces.Srgb)) }
    val anchor = lch.firstOrNull { it[1] > 0.02f }?.get(2) ?: 0f
    var previous = anchor
    val out = FloatArray(MAX_STOPS * 3)
    lch.forEachIndexed { index, stop ->
        var hue = if (stop[1] > 0.02f) stop[2] else previous
        while (hue - previous > PI) hue -= (2 * PI).toFloat()
        while (hue - previous < -PI) hue += (2 * PI).toFloat()
        previous = hue
        out[index * 3] = stop[0]
        out[index * 3 + 1] = stop[1]
        out[index * 3 + 2] = hue
    }
    return out
}

private fun toOklch(color: Color): FloatArray {
    fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val r = linear(color.red)
    val g = linear(color.green)
    val b = linear(color.blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    val labL = 0.2104542553f * l + 0.793617785f * m - 0.0040720468f * s
    val labA = 1.9779984951f * l - 2.428592205f * m + 0.4505937099f * s
    val labB = 0.0259040371f * l + 0.7827717662f * m - 0.808675766f * s
    return floatArrayOf(labL, hypot(labA, labB), atan2(labB, labA))
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setAuroraUniforms(
    width: Float,
    height: Float,
    time: Float,
    palette: FloatArray,
    stopCount: Int,
    amplitude: Float,
    blend: Float,
    bandHeight: Float,
    scale: Float,
    glow: Float,
    brightness: Float,
    layers: Int,
    flip: Boolean,
    reveal: Float,
    opacity: Float,
) {
    setFloatUniform("uResolution", width, height)
    setFloatUniform("uTime", time)
    for (i in 0 until MAX_STOPS) {
        setFloatUniform("uStop$i", palette[i * 3], palette[i * 3 + 1], palette[i * 3 + 2])
    }
    setFloatUniform("uStopCount", stopCount.toFloat())
    setFloatUniform("uAmplitude", amplitude)
    setFloatUniform("uBlend", blend)
    setFloatUniform("uHeight", bandHeight)
    setFloatUniform("uScale", scale)
    setFloatUniform("uGlow", glow)
    setFloatUniform("uBrightness", brightness)
    setFloatUniform("uLayers", layers.toFloat())
    setFloatUniform("uFlip", if (flip) 1f else 0f)
    setFloatUniform("uReveal", reveal)
    setFloatUniform("uOpacity", opacity)
}

/** Pre-API 33 approximation: soft glows in the stop colors rising from the edge. */
private fun DrawScope.drawAuroraFallback(
    stops: List<Color>,
    height: Float,
    brightness: Float,
    opacity: Float,
    direction: AuroraDirection,
    time: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val edgeY = if (direction == AuroraDirection.Bottom) h else 0f
    val alpha = (brightness.coerceAtMost(1f) * opacity).coerceIn(0f, 1f)
    val count = stops.size
    stops.forEachIndexed { index, color ->
        val u = if (count == 1) 0.5f else index / (count - 1f)
        val wobble = 0.08f * sin(time * 0.6f + index * 2.3f)
        val rx = w * (0.55f + wobble)
        val ry = h * (height * 1.6f + wobble)
        val center = Offset(w * u, edgeY)
        if (rx <= 0f || ry <= 0f) return@forEachIndexed
        scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha * 0.85f),
                    0.5f to color.copy(alpha = alpha * 0.35f),
                    1f to Color.Transparent,
                    center = center,
                    radius = rx,
                ),
                radius = rx,
                center = center,
            )
        }
    }
}

private const val AURORA_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float3 uStop0;
uniform float3 uStop1;
uniform float3 uStop2;
uniform float3 uStop3;
uniform float3 uStop4;
uniform float uStopCount;
uniform float uAmplitude;
uniform float uBlend;
uniform float uHeight;
uniform float uScale;
uniform float uGlow;
uniform float uBrightness;
uniform float uLayers;
uniform float uFlip;
uniform float uReveal;
uniform float uOpacity;

float3 permute(float3 x) {
  return mod(((x * 34.0) + 1.0) * x, 289.0);
}

float snoise(float2 v) {
  const float4 C = float4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
  float2 i = floor(v + dot(v, C.yy));
  float2 x0 = v - i + dot(i, C.xx);
  float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
  float4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
  float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
  m = m * m;
  m = m * m;
  float3 x = 2.0 * fract(p * C.www) - 1.0;
  float3 h = abs(x) - 0.5;
  float3 ox = floor(x + 0.5);
  float3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
  float3 g = float3(0.0);
  g.x = a0.x * x0.x + h.x * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

float smootherstep(float edge0, float edge1, float x) {
  float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
  return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

float3 oklabToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l;
  m = m * m * m;
  s = s * s * s;
  return float3(
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
  );
}

float3 toSrgb(float3 c) {
  c = clamp(c, 0.0, 1.0);
  return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

float3 stopAt(float i) {
  if (i < 0.5) return uStop0;
  if (i < 1.5) return uStop1;
  if (i < 2.5) return uStop2;
  if (i < 3.5) return uStop3;
  return uStop4;
}

float3 ramp(float t) {
  float x = clamp(t, 0.0, 1.0) * (uStopCount - 1.0);
  float i = min(floor(x), uStopCount - 2.0);
  float f = x - i;
  f = f * f * (3.0 - 2.0 * f);
  float3 lch = mix(stopAt(i), stopAt(i + 1.0), f);
  return toSrgb(oklabToLinear(float3(lch.x, lch.y * cos(lch.z), lch.y * sin(lch.z))));
}

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

half4 main(float2 fragCoord) {
  // AGSL's origin is top-left; the WebGL original works bottom-up.
  float2 frag = float2(fragCoord.x, uResolution.y - fragCoord.y);
  float y = 1.0 - frag.y / uResolution.y;
  y = mix(y, 1.0 - y, uFlip);
  float u = frag.x / uResolution.x;
  float X = (frag.x - uResolution.x * 0.5) / uResolution.y;
  float t = uTime;

  float3 glow = float3(0.0);
  float cover = 0.0;
  for (int k = 0; k < 3; k++) {
    float layer = float(k);
    if (layer >= uLayers) break;
    float seed = layer * 13.7;
    float pace = t * (1.0 - layer * 0.18);
    float warp = snoise(float2(X * 0.45 * uScale + pace * 0.02 + seed, pace * 0.05 + seed));
    float wave = snoise(float2(X * 0.75 * uScale + warp * 0.35 - pace * 0.03 + seed, pace * 0.07 - seed)) * 0.72
      + snoise(float2(X * 1.6 * uScale + warp * 0.5 + pace * 0.04 - seed, pace * 0.1 + seed * 0.5)) * 0.28;
    float hem = uHeight + layer * 0.07 + wave * 0.15 * uAmplitude;
    hem = mix(-0.25, hem, uReveal);

    float soft = (0.04 + uBlend * 0.16) * (1.0 + layer * 0.9);
    float rise = hem - y;
    float low = -soft - uGlow * 0.14;
    float high = 0.26 + soft * 0.6;
    float body = pow(smootherstep(low, high, rise), 1.5 + uGlow * 0.6);
    float lobes = 0.86 + 0.14 * snoise(float2(X * 0.9 * uScale - pace * 0.025 + seed, y * 0.3 + pace * 0.04));
    float alpha = clamp(body * lobes * (layer < 0.5 ? 1.0 : 0.32 / layer), 0.0, 1.0);

    float3 tint = ramp(u + 0.05 * sin(pace * 0.08 + layer * 2.3) + layer * 0.1 + max(rise, 0.0) * 0.05);
    glow = 1.0 - (1.0 - glow) * (1.0 - tint * alpha);
    cover = 1.0 - (1.0 - cover) * (1.0 - alpha);
  }

  float fade = smootherstep(0.0, 0.7, uReveal) * uOpacity;
  float strength = clamp(uBrightness, 0.0, 2.0);
  glow *= fade * min(strength, 1.0);
  cover *= fade * min(strength, 1.0);
  glow = 1.0 - pow(1.0 - glow, float3(max(strength, 1.0)));
  float dither = (hash(frag) - 0.5) / 255.0;
  return half4(glow + dither, clamp(cover + dither, 0.0, 1.0));
}
"""
