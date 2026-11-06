package com.talkie.ai.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.talkie.ai.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/**
 * Timeline boundaries for the `onboarding-motion` composition (Scenes 1–6).
 * - Scenes 1–5 (Intro): 0.00s .. 6.016667s (frames 0..361 @ 60fps)
 * - Scene 6 (Onboarding Screen Reveal): starts at 5.88s, settles by 8.50s
 */
const val ONBOARDING_INTRO_END_SECONDS = 6.016667f
const val ONBOARDING_SCENE6_START_SECONDS = 5.88f
const val ONBOARDING_TOTAL_DURATION_SECONDS = 8.50f

private val PrimaryStrokeColor = Color.White

/**
 * Exact 8-iteration Newton-Raphson cubic-bezier evaluator matching the GSAP `bezier(x1, y1, x2, y2)`
 * helper in `onboarding-motion/compositions/scene-1.html` through `scene-5.html`.
 */
fun evaluateCubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var t = x
    for (i in 0 until 8) {
        val invT = 1f - t
        val cx = 3f * x1 * t * invT * invT + 3f * x2 * t * t * invT + t * t * t - x
        if (abs(cx) < 1e-6f) break
        val dx = 3f * invT * invT * x1 + 6f * invT * t * (x2 - x1) + 3f * t * t * (1f - x2)
        if (abs(dx) < 1e-6f) break
        t -= cx / dx
    }
    val invT = 1f - t
    return 3f * y1 * t * invT * invT + 3f * y2 * t * t * invT + t * t * t
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

fun easePower2Out(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    val inv = 1f - t
    return 1f - inv * inv
}

fun easePower2InOut(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    return if (t < 0.5f) {
        2f * t * t
    } else {
        1f - (-2f * t + 2f).pow(2) / 2f
    }
}

fun easePower3Out(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    val inv = 1f - t
    return 1f - inv * inv * inv
}

fun easeSineInOut(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    return (-(cos(PI * t) - 1.0) / 2.0).toFloat()
}

fun easeBackOut(p: Float, overshoot: Float): Float {
    val t = p.coerceIn(0f, 1f)
    val c1 = overshoot
    val c3 = c1 + 1f
    val tm1 = t - 1f
    return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1
}

/**
 * Snapshot of Scene 6 entrance animations at global time [tSeconds] (`scene-6.html`).
 */
@Immutable
data class Scene6RevealState(
    val isVisible: Boolean,
    val ringScale: Float,
    val ringAlpha: Float,
    val orbScale: Float,
    val orbTranslationYDp: Float,
    val orbAlpha: Float,
    val orbShimmerRotationDeg: Float,
    val orbShimmerScale: Float,
    val buttonTranslationYDp: Float,
    val buttonScale: Float,
    val buttonAlpha: Float,
    val chevronTranslationXDp: Float,
    val chevronAlpha: Float,
    val accountTranslationYDp: Float,
    val accountAlpha: Float
)

fun computeScene6RevealState(tSeconds: Float): Scene6RevealState {
    if (tSeconds < ONBOARDING_SCENE6_START_SECONDS) {
        return Scene6RevealState(
            isVisible = false,
            ringScale = 0.42f,
            ringAlpha = 0f,
            orbScale = 0.08f,
            orbTranslationYDp = 12f,
            orbAlpha = 0f,
            orbShimmerRotationDeg = 0f,
            orbShimmerScale = 0.96f,
            buttonTranslationYDp = 24f,
            buttonScale = 0.82f,
            buttonAlpha = 0f,
            chevronTranslationXDp = -5.33f,
            chevronAlpha = 0f,
            accountTranslationYDp = 9.33f,
            accountAlpha = 0f
        )
    }

    val s6T = (tSeconds - ONBOARDING_SCENE6_START_SECONDS).coerceAtLeast(0f)

    // 1. Concentric reveal ring:
    // 0.02s -> 0.47s (dur 0.45s, power2.out): scale 0.42 -> 1.0, opacity 0 -> 0.72
    // 0.47s -> 1.37s (dur 0.90s, sine.inOut): scale 1.0 -> 1.04, opacity 0.72 -> 0.25
    val ringScale: Float
    val ringAlpha: Float
    when {
        s6T < 0.02f -> {
            ringScale = 0.42f
            ringAlpha = 0f
        }
        s6T <= 0.47f -> {
            val e = easePower2Out((s6T - 0.02f) / 0.45f)
            ringScale = lerp(0.42f, 1.0f, e)
            ringAlpha = lerp(0f, 0.72f, e)
        }
        else -> {
            val e = easeSineInOut(((s6T - 0.47f) / 0.90f).coerceIn(0f, 1f))
            ringScale = lerp(1.0f, 1.04f, e)
            ringAlpha = lerp(0.72f, 0.25f, e)
        }
    }

    // 2. Hero PlasmaOrb:
    // 0.00s -> 0.64s (dur 0.64s, back.out(1.22)): scale 0.08 -> 1.0, y: 36px (@3x = 12dp) -> 0, opacity: 0 -> 1
    // 0.64s -> 2.54s (dur 1.90s, sine.inOut): scale 1.0 -> 1.03
    val orbScale: Float
    val orbTranslationYDp: Float
    val orbAlpha: Float
    if (s6T <= 0.64f) {
        val p = (s6T / 0.64f).coerceIn(0f, 1f)
        val eBack = easeBackOut(p, 1.22f)
        val eFade = easePower2Out(p)
        orbScale = lerp(0.08f, 1.0f, eBack)
        orbTranslationYDp = lerp(12f, 0f, eBack)
        orbAlpha = eFade
    } else {
        val eBreath = easeSineInOut(((s6T - 0.64f) / 1.90f).coerceIn(0f, 1f))
        orbScale = lerp(1.0f, 1.03f, eBreath)
        orbTranslationYDp = 0f
        orbAlpha = 1f
    }

    val eShimmer = easeSineInOut((s6T / 2.60f).coerceIn(0f, 1f))
    val orbShimmerRotationDeg = lerp(0f, 38f, eShimmer)
    val orbShimmerScale = lerp(0.96f, 1.06f, eShimmer)

    // 3. Circular Liquid Glass "Get Started" Button (`#s6-get-started-btn`):
    // 0.20s -> 0.86s (dur 0.66s, back.out(1.45)): y: 72px (@3x = 24dp) -> 0, scale: 0.82 -> 1.0, opacity: 0 -> 1
    val buttonTranslationYDp: Float
    val buttonScale: Float
    val buttonAlpha: Float
    if (s6T < 0.20f) {
        buttonTranslationYDp = 24f
        buttonScale = 0.82f
        buttonAlpha = 0f
    } else {
        val pBtn = ((s6T - 0.20f) / 0.66f).coerceIn(0f, 1f)
        val eBtnBack = easeBackOut(pBtn, 1.45f)
        val eBtnFade = easePower3Out(pBtn)
        buttonTranslationYDp = lerp(24f, 0f, eBtnBack)
        buttonScale = lerp(0.82f, 1.0f, eBtnBack)
        buttonAlpha = eBtnFade
    }

    // 4. Chevron icon inside button (`#s6-chevron-wrap`):
    // 0.32s -> 0.84s (dur 0.52s, power3.out): x: -16px (@3x = -5.33dp) -> 0, opacity: 0 -> 1
    // 1.25s -> 1.70s (dur 0.45s, sine.inOut): x: 0 -> 2dp (+6px @3x)
    // 1.70s -> 2.15s (dur 0.45s, sine.inOut): x: 2dp -> 0
    val chevronTranslationXDp: Float
    val chevronAlpha: Float
    when {
        s6T < 0.32f -> {
            chevronTranslationXDp = -5.33f
            chevronAlpha = 0f
        }
        s6T <= 0.84f -> {
            val eChev = easePower3Out(((s6T - 0.32f) / 0.52f).coerceIn(0f, 1f))
            chevronTranslationXDp = lerp(-5.33f, 0f, eChev)
            chevronAlpha = eChev
        }
        s6T < 1.25f -> {
            chevronTranslationXDp = 0f
            chevronAlpha = 1f
        }
        s6T <= 1.70f -> {
            val ePulseOut = easeSineInOut(((s6T - 1.25f) / 0.45f).coerceIn(0f, 1f))
            chevronTranslationXDp = lerp(0f, 2f, ePulseOut)
            chevronAlpha = 1f
        }
        s6T <= 2.15f -> {
            val ePulseBack = easeSineInOut(((s6T - 1.70f) / 0.45f).coerceIn(0f, 1f))
            chevronTranslationXDp = lerp(2f, 0f, ePulseBack)
            chevronAlpha = 1f
        }
        else -> {
            chevronTranslationXDp = 0f
            chevronAlpha = 1f
        }
    }

    // 5. "Already have an account?" footer (`#s6-account-wrap`):
    // 0.36s -> 0.88s (dur 0.52s, power3.out): y: 28px (@3x = 9.33dp) -> 0, opacity: 0 -> 1
    val accountTranslationYDp: Float
    val accountAlpha: Float
    if (s6T < 0.36f) {
        accountTranslationYDp = 9.33f
        accountAlpha = 0f
    } else {
        val eAcc = easePower3Out(((s6T - 0.36f) / 0.52f).coerceIn(0f, 1f))
        accountTranslationYDp = lerp(9.33f, 0f, eAcc)
        accountAlpha = eAcc
    }

    return Scene6RevealState(
        isVisible = true,
        ringScale = ringScale,
        ringAlpha = ringAlpha,
        orbScale = orbScale,
        orbTranslationYDp = orbTranslationYDp,
        orbAlpha = orbAlpha,
        orbShimmerRotationDeg = orbShimmerRotationDeg,
        orbShimmerScale = orbShimmerScale,
        buttonTranslationYDp = buttonTranslationYDp,
        buttonScale = buttonScale,
        buttonAlpha = buttonAlpha,
        chevronTranslationXDp = chevronTranslationXDp,
        chevronAlpha = chevronAlpha,
        accountTranslationYDp = accountTranslationYDp,
        accountAlpha = accountAlpha
    )
}

private data class WordLayoutSpec(
    val text: String,
    val scale: Float,
    val fallbackWidth: Float
)

private data class PositionedWord(
    val text: String,
    val x: Float,
    val baselineY: Float,
    val scale: Float
)

private fun layoutRow(
    items: List<WordLayoutSpec>,
    baselineY: Float,
    gap: Float,
    textPaint: Paint
): List<PositionedWord> {
    val widths = items.map { item ->
        val measured = textPaint.measureText(item.text)
        val rawW = if (measured > 0f) measured else item.fallbackWidth
        rawW * item.scale
    }
    val total = widths.sum() + gap * (items.size - 1)
    var x = 256f - total * 0.5f
    return items.mapIndexed { idx, item ->
        val placed = PositionedWord(
            text = item.text,
            x = x,
            baselineY = baselineY,
            scale = item.scale
        )
        x += widths[idx] + gap
        placed
    }
}

private data class Scene2RingSpec(
    val dx: Float,
    val dy: Float,
    val rotationDeg: Float,
    val alpha: Float
)

private val SCENE2_RINGS = listOf(
    Scene2RingSpec(dx = 8.660254f, dy = 5f, rotationDeg = 120f, alpha = 0.53f),
    Scene2RingSpec(dx = 0f, dy = 10f, rotationDeg = 180f, alpha = 0.53f),
    Scene2RingSpec(dx = -8.660254f, dy = 5f, rotationDeg = 240f, alpha = 0.53f),
    Scene2RingSpec(dx = -8.660254f, dy = -5f, rotationDeg = 300f, alpha = 1.0f),
    Scene2RingSpec(dx = 0f, dy = -10f, rotationDeg = 360f, alpha = 0.53f),
    Scene2RingSpec(dx = 8.660254f, dy = -5f, rotationDeg = 420f, alpha = 0.53f)
)

private data class Scene3CircleSpec(
    val scale: Float,
    val rotationDeg: Float,
    val x0: Float,
    val y0: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val ix: Float,
    val iy: Float
)

// Ordered back-to-front matching DOM order in `scene-3.html` (#s3-c6 .. #s3-c2)
private val SCENE3_CIRCLES = listOf(
    Scene3CircleSpec(
        scale = 0.90f,
        rotationDeg = 575.8565f,
        x0 = 332.8187f,
        y0 = 16.65f,
        y1 = 222.5765f,
        x2 = 266.9059f,
        y2 = 285.03857f,
        ix = 1f,
        iy = 0.50055147f
    ),
    Scene3CircleSpec(
        scale = 0.835f,
        rotationDeg = 602.3519f,
        x0 = 282.8552f,
        y0 = -192.48f,
        y1 = 81.445435f,
        x2 = 266.90686f,
        y2 = 285.04022f,
        ix = 1f,
        iy = 0.39837185f
    ),
    Scene3CircleSpec(
        scale = 0.77f,
        rotationDeg = 708.0139f,
        x0 = 201.29446f,
        y0 = 283.68f,
        y1 = 490.61066f,
        x2 = 266.9023f,
        y2 = 285.03766f,
        ix = 1f,
        iy = 0.5323398f
    ),
    Scene3CircleSpec(
        scale = 0.705f,
        rotationDeg = 382.8426f,
        x0 = 406.63654f,
        y0 = 136.48f,
        y1 = 407.40552f,
        x2 = 266.90564f,
        y2 = 285.03967f,
        ix = 1f,
        iy = 0.43079044f
    ),
    Scene3CircleSpec(
        scale = 0.64f,
        rotationDeg = 556.838f,
        x0 = 125.381355f,
        y0 = 42.24f,
        y1 = 261.16333f,
        x2 = 266.9045f,
        y2 = 285.03934f,
        ix = 0.98990285f,
        iy = 0.4790835f
    )
)

/**
 * Renders the 5-scene intro animation (`onboarding-motion/compositions/scene-1.html` .. `scene-5.html`).
 * Reads [timeSeconds] inside the [Canvas] draw scope to avoid recomposition on frame ticks.
 */
@Composable
fun OnboardingIntroMotionCanvas(
    timeSeconds: () -> Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calSansTypeface = remember(context) {
        try {
            ResourcesCompat.getFont(context, R.font.cal_sans_regular) ?: Typeface.DEFAULT_BOLD
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }
    }

    val textPaint = remember(calSansTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = calSansTypeface
            textSize = 50f
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
    }

    val s1Layout = remember(textPaint) {
        layoutRow(
            items = listOf(
                WordLayoutSpec("Ready", 0.78499525f, 144.7f),
                WordLayoutSpec("to", 0.78499525f, 46.1f),
                WordLayoutSpec("chat?", 0.78499525f, 127.65f)
            ),
            baselineY = 278f,
            gap = 11.5f,
            textPaint = textPaint
        )
    }

    val s3Layout = remember(textPaint) {
        layoutRow(
            items = listOf(
                WordLayoutSpec("Say", 0.91739075f, 85.55f),
                WordLayoutSpec("anything", 0.91739075f, 199.85f)
            ),
            baselineY = 272f,
            gap = 13.5f,
            textPaint = textPaint
        )
    }

    val s4Layout = remember(textPaint) {
        layoutRow(
            items = listOf(
                WordLayoutSpec("Make", 0.78499525f, 131.7f),
                WordLayoutSpec("mistakes.", 0.78499525f, 214.4f)
            ),
            baselineY = 269.5f,
            gap = 11.5f,
            textPaint = textPaint
        )
    }

    val s5Layout = remember(textPaint) {
        layoutRow(
            items = listOf(
                WordLayoutSpec("Grow", 0.78499525f, 123.95f),
                WordLayoutSpec("fluent.", 0.78499525f, 139.2f)
            ),
            baselineY = 265.5f,
            gap = 11.5f,
            textPaint = textPaint
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tSeconds = timeSeconds()
        if (tSeconds > ONBOARDING_INTRO_END_SECONDS) return@Canvas

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Fit 512x512 stage to viewport width and center vertically on screen,
        // matching `left: 0; top: 420px; transform: scale(2.109375)` in 1080x1920.
        val stageScale = w / 512f
        val stageTop = (h - 512f * stageScale) * 0.5f

        withTransform({
            translate(left = 0f, top = stageTop)
            scale(scaleX = stageScale, scaleY = stageScale, pivot = Offset.Zero)
        }) {
            val s1Start = 2f / 60f
            val s2Start = 88f / 60f
            val s3Start = 155f / 60f
            val s4Start = 221f / 60f
            val s5Start = 272f / 60f
            val s5End = 361f / 60f

            when {
                tSeconds >= s1Start && tSeconds < s2Start -> {
                    drawScene1(
                        localT = tSeconds - s1Start,
                        words = s1Layout,
                        textPaint = textPaint
                    )
                }
                tSeconds >= s2Start && tSeconds < s3Start -> {
                    drawScene2(
                        localT = tSeconds - s2Start
                    )
                }
                tSeconds >= s3Start && tSeconds < s4Start -> {
                    drawScene3(
                        localT = tSeconds - s3Start,
                        words = s3Layout,
                        textPaint = textPaint
                    )
                }
                tSeconds >= s4Start && tSeconds < s5Start -> {
                    drawScene4(
                        localT = tSeconds - s4Start,
                        words = s4Layout,
                        textPaint = textPaint
                    )
                }
                tSeconds >= s5Start && tSeconds <= s5End -> {
                    drawScene5(
                        localT = tSeconds - s5Start,
                        words = s5Layout,
                        textPaint = textPaint
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawStageWord(
    word: PositionedWord,
    layerOffsetY: Float,
    alpha: Float,
    textPaint: Paint
) {
    val clampedAlpha = (alpha * 255f).toInt().coerceIn(0, 255)
    if (clampedAlpha <= 0) return

    withTransform({
        translate(left = word.x, top = word.baselineY + layerOffsetY)
        scale(scaleX = word.scale, scaleY = word.scale, pivot = Offset.Zero)
    }) {
        textPaint.alpha = clampedAlpha
        drawContext.canvas.nativeCanvas.drawText(word.text, 0f, 0f, textPaint)
    }
}

/**
 * Scene 1 (`0.033333s -> 1.466667s`, frames 2..88): "Ready to chat?"
 */
private fun DrawScope.drawScene1(
    localT: Float,
    words: List<PositionedWord>,
    textPaint: Paint
) {
    val sAtFrame2 = 1f - (1f - 0.87f) * evaluateCubicBezier(0f, 0.42320772f, 0.64192489f, 0.61702994f, 2f / 63f)
    val tSplit = 61f / 60f
    val sceneScale = if (localT <= tSplit) {
        val p = (localT / tSplit).coerceIn(0f, 1f)
        val e = evaluateCubicBezier(0f, 0.42320772f, 0.64192489f, 0.61702994f, p)
        lerp(sAtFrame2, 0.87f, e)
    } else {
        val p = ((localT - tSplit) / (24f / 60f)).coerceIn(0f, 1f)
        val e = evaluateCubicBezier(0.65f, 0f, 1f, 0.32008272f, p)
        lerp(0.87f, 0.42f, e)
    }

    withTransform({
        scale(scaleX = sceneScale, scaleY = sceneScale, pivot = Offset(256f, 256f))
    }) {
        // Layer 1: "Ready" (data-start="0")
        val p1 = (localT / 1.0f).coerceIn(0f, 1f)
        val y1 = lerp(56f, 0f, evaluateCubicBezier(0f, 0.64765625f, 0.36f, 1f, p1))
        drawStageWord(words[0], y1, 1f, textPaint)

        // Layer 2: "to" (data-start="0.25")
        if (localT >= 0.25f) {
            val p2 = ((localT - 0.25f) / 1.0f).coerceIn(0f, 1f)
            val y2 = lerp(56f, 0f, evaluateCubicBezier(0f, 0.5928834f, 0.36f, 1f, p2))
            drawStageWord(words[1], y2, 1f, textPaint)
        }

        // Layer 3: "chat?" (data-start="0.50")
        if (localT >= 0.50f) {
            val p3 = ((localT - 0.50f) / 1.0f).coerceIn(0f, 1f)
            val y3 = lerp(56f, 0f, evaluateCubicBezier(0f, 0.53151261f, 0.36f, 1f, p3))
            drawStageWord(words[2], y3, 1f, textPaint)
        }
    }
}

/**
 * Scene 2 (`1.466667s -> 2.583333s`, frames 88..155): 6 rotating rings + solid white circle
 */
private fun DrawScope.drawScene2(
    localT: Float
) {
    val localFrame = (localT * 60f).coerceIn(0f, 67f)
    val globalFrame = 88f + localFrame

    val uRot = ((globalFrame - 76f) / (181f - 76f)).coerceIn(0f, 1f)
    val wrapRotation = 67f * evaluateCubicBezier(0.65f, 0f, 0.36f, 1f, uRot)

    val wrapScale = if (localFrame <= 23f) {
        1f
    } else {
        val p = ((localFrame - 23f) / 39f).coerceIn(0f, 1f)
        val e = evaluateCubicBezier(0.30292805f, -0.00567883f, 0.21479123f, 1.00843822f, p)
        lerp(1f, 1.24f, e)
    }

    val wrapY = if (localFrame <= 39f) {
        0f
    } else {
        val p = ((localFrame - 39f) / 27f).coerceIn(0f, 1f)
        val e = evaluateCubicBezier(0.65f, 0f, 1f, 0.52361476f, p)
        lerp(0f, 218f, e)
    }

    val pCircle = (localFrame / 60f).coerceIn(0f, 1f)
    val eCircle = evaluateCubicBezier(0f, 0.57748162f, 0.36f, 1f, pCircle)
    val moveX = lerp(0.004758f, 0f, eCircle)
    val moveY = lerp(0.001604f, -129f, eCircle)
    val circleRadius = 20.823166f

    withTransform({
        translate(left = 256f, top = 256f + wrapY)
        rotate(degrees = wrapRotation, pivot = Offset.Zero)
        scale(scaleX = wrapScale, scaleY = wrapScale, pivot = Offset.Zero)
    }) {
        // 6 Instances of Nested Scene 5 rings
        for (ring in SCENE2_RINGS) {
            withTransform({
                translate(left = ring.dx, top = ring.dy)
                rotate(degrees = ring.rotationDeg, pivot = Offset.Zero)
                translate(left = moveX, top = moveY)
            }) {
                drawCircle(
                    color = PrimaryStrokeColor.copy(alpha = ring.alpha),
                    radius = circleRadius - 0.5f,
                    center = Offset.Zero,
                    style = Stroke(width = 1f)
                )
            }
        }

        // Layer 4: Solid white circle (data-start="0.15", localFrame >= 9)
        if (localT >= 0.15f) {
            val orbX = lerp(-8.656481f, -120.377526f, eCircle)
            val orbY = lerp(-5.00333f, -69.50001f, eCircle)
            val orbScale = if (localFrame <= 21f) {
                1f
            } else {
                val pScale = ((localFrame - 21f) / 23f).coerceIn(0f, 1f)
                val eScale = evaluateCubicBezier(0.15316439f, 0.00119485f, 0.25006565f, 1.00051864f, pScale)
                lerp(1f, 1.46f, eScale)
            }

            withTransform({
                translate(left = orbX, top = orbY)
                rotate(degrees = 300f, pivot = Offset.Zero)
                scale(scaleX = orbScale, scaleY = orbScale, pivot = Offset.Zero)
            }) {
                drawCircle(
                    color = PrimaryStrokeColor,
                    radius = circleRadius,
                    center = Offset.Zero
                )
            }
        }
    }
}

/**
 * Scene 3 (`2.583333s -> 3.683333s`, frames 155..221): "Say anything" + 5 converging circles + solid white circle
 */
private fun DrawScope.drawScene3(
    localT: Float,
    words: List<PositionedWord>,
    textPaint: Paint
) {
    val localFrame = (localT * 60f).coerceIn(0f, 66f)
    val fPre = 2f + localFrame

    val yStart = -49f * (1f - (155f - 146f) / (254f - 146f))
    val yEnd = -49f * (1f - (221f - 146f) / (254f - 146f))
    val wrapY = lerp(yStart, yEnd, (localFrame / 66f).coerceIn(0f, 1f))

    val wrapScale = if (localFrame <= 28f) {
        1f
    } else {
        val p = ((localFrame - 28f) / 38f).coerceIn(0f, 1f)
        val e = evaluateCubicBezier(0.85266544f, 0.20078782f, 1f, 0.42484244f, p)
        lerp(1f, 1.33f, e)
    }

    withTransform({
        translate(left = 0f, top = wrapY)
        scale(scaleX = wrapScale, scaleY = wrapScale, pivot = Offset(256f, 256f))
    }) {
        // Layer 8: "anything" & Layer 7: "Say" (f_pre 0 -> 43: y: -118 -> 0)
        val pWords = (fPre / 43f).coerceIn(0f, 1f)
        val ySay = lerp(-118f, 0f, evaluateCubicBezier(0f, 0.53677127f, 0.36f, 1f, pWords))
        val yAnything = lerp(-118f, 0f, evaluateCubicBezier(0f, 0.55835741f, 0.36f, 1f, pWords))

        drawStageWord(words[1], yAnything, 1f, textPaint)
        drawStageWord(words[0], ySay, 1f, textPaint)

        // Layers 6..2: 5 Stroked White Circles
        for (c in SCENE3_CIRCLES) {
            val cx: Float
            val cy: Float
            when {
                localFrame <= 25f -> {
                    cx = c.x0
                    val eDrop = evaluateCubicBezier(0f, 0.53673188f, 0.36f, 1f, (fPre / 27f).coerceIn(0f, 1f))
                    cy = lerp(c.y0, c.y1, eDrop)
                }
                localFrame <= 26f -> {
                    cx = c.x0
                    cy = c.y1
                }
                else -> {
                    val pConv = ((localFrame - 26f) / 38f).coerceIn(0f, 1f)
                    val eConv = evaluateCubicBezier(0.65f, 0f, c.ix, c.iy, pConv)
                    cx = lerp(c.x0, c.x2, eConv)
                    cy = lerp(c.y1, c.y2, eConv)
                }
            }

            withTransform({
                translate(left = cx, top = cy)
                rotate(degrees = c.rotationDeg, pivot = Offset.Zero)
                scale(scaleX = c.scale, scaleY = c.scale, pivot = Offset.Zero)
            }) {
                drawCircle(
                    color = PrimaryStrokeColor,
                    radius = 29.5f,
                    center = Offset.Zero,
                    style = Stroke(width = 1f)
                )
            }
        }

        // Layer 1 (Top): Solid white circle (#s3-c1)
        val orbY: Float
        val orbScale: Float
        when {
            localFrame <= 33f -> {
                val eDrop = evaluateCubicBezier(0f, 0.53673188f, 0.36f, 1f, (fPre / 35f).coerceIn(0f, 1f))
                orbY = lerp(70.1f, 368.02863f, eDrop)
                orbScale = 1f
            }
            localFrame <= 34f -> {
                orbY = 368.02863f
                orbScale = 1f
            }
            else -> {
                val pRise = ((localFrame - 34f) / 33f).coerceIn(0f, 1f)
                orbY = lerp(
                    368.02863f,
                    256f,
                    evaluateCubicBezier(0.65f, 0f, 1f, 0.44174107f, pRise)
                )
                orbScale = lerp(
                    1f,
                    2.62f,
                    evaluateCubicBezier(0.65f, 0f, 1f, 0.49157038f, pRise)
                )
            }
        }

        withTransform({
            translate(left = 256f, top = orbY)
            scale(scaleX = orbScale, scaleY = orbScale, pivot = Offset.Zero)
        }) {
            drawCircle(
                color = PrimaryStrokeColor,
                radius = 29.5f,
                center = Offset.Zero
            )
        }
    }
}

/**
 * Scene 4 (`3.683333s -> 4.533333s`, frames 221..272): "Make mistakes." + morphing white pill
 */
private fun DrawScope.drawScene4(
    localT: Float,
    words: List<PositionedWord>,
    textPaint: Paint
) {
    val localFrame = (localT * 60f).coerceIn(0f, 51f)
    val fPre = 4f + localFrame

    val yMake: Float
    val yMistakes: Float
    val pillY: Float

    if (localFrame <= 23f) {
        val uText = (fPre / 27f).coerceIn(0f, 1f)
        yMake = lerp(106f, 0f, evaluateCubicBezier(0f, 0.64649422f, 0.36f, 1f, uText))
        yMistakes = lerp(106f, 0f, evaluateCubicBezier(0f, 0.58329175f, 0.36f, 1f, uText))

        val uPill = ((fPre - 1f) / (27f - 1f)).coerceIn(0f, 1f)
        pillY = lerp(416.56f, 103.56f, evaluateCubicBezier(0f, 0.57893908f, 0.36f, 1f, uPill))
    } else {
        val pOut = ((localFrame - 23f) / 28f).coerceIn(0f, 1f)
        yMake = lerp(0f, 106f, evaluateCubicBezier(0.65f, 0f, 1f, 0.46767988f, pOut))
        yMistakes = lerp(0f, 106f, evaluateCubicBezier(0.65f, 0f, 1f, 0.41932773f, pOut))
        pillY = lerp(103.56f, 416.56f, evaluateCubicBezier(0.65f, 0f, 1f, 0.37162553f, pOut))
    }

    // Layer 4: "mistakes." & Layer 3: "Make"
    drawStageWord(words[1], yMistakes, 1f, textPaint)
    drawStageWord(words[0], yMake, 1f, textPaint)

    // Layer 1: Morphing Pill Rectangle (`#s4-pill-rect`, x=226.5, width=59, rx=29.5)
    val pillHeight = when {
        fPre <= 11f -> {
            val u = ((fPre - 3f) / (11f - 3f)).coerceIn(0f, 1f)
            lerp(110f, 59f, evaluateCubicBezier(0.65f, 0f, 1f, 0.38712579f, u))
        }
        fPre < 46f -> 59f
        fPre <= 52f -> {
            val u = ((fPre - 46f) / (52f - 46f)).coerceIn(0f, 1f)
            lerp(59f, 110f, evaluateCubicBezier(0.65f, 0f, 0.36f, 1f, u))
        }
        else -> 110f
    }

    drawRoundRect(
        color = PrimaryStrokeColor,
        topLeft = Offset(226.5f, pillY - pillHeight * 0.5f),
        size = Size(59f, pillHeight),
        cornerRadius = CornerRadius(29.5f, 29.5f)
    )
}

/**
 * Scene 5 (`4.533333s -> 6.016667s`, frames 272..361): "Grow fluent." + upward lift & dissolve
 */
private fun DrawScope.drawScene5(
    localT: Float,
    words: List<PositionedWord>,
    textPaint: Paint
) {
    val localFrame = (localT * 60f).coerceIn(0f, 89f)

    val wrapY: Float
    val wrapScale: Float
    val wrapAlpha: Float
    if (localFrame <= 70f) {
        wrapY = 0f
        wrapScale = 1f
        wrapAlpha = 1f
    } else {
        val pLift = ((localFrame - 70f) / 16f).coerceIn(0f, 1f)
        val eLift = evaluateCubicBezier(0.65f, 0f, 1f, 0.26101628f, pLift)
        wrapY = lerp(0f, -82f, eLift)
        wrapScale = lerp(1f, 0.76f, eLift)

        val pFade = ((localFrame - 70f) / 14f).coerceIn(0f, 1f)
        wrapAlpha = lerp(1f, 0f, easePower2InOut(pFade))
    }

    if (wrapAlpha <= 0.001f) return

    withTransform({
        translate(left = 0f, top = wrapY)
        scale(scaleX = wrapScale, scaleY = wrapScale, pivot = Offset(256f, 256f))
    }) {
        // Layer 2: "fluent." (data-start="0.05", localFrame >= 3)
        if (localFrame >= 3f) {
            val pFluent = ((localFrame - 3f) / 41f).coerceIn(0f, 1f)
            val yFluent = lerp(-69f, 0f, evaluateCubicBezier(0f, 0.50821297f, 0.36f, 1f, pFluent))
            drawStageWord(words[1], yFluent, wrapAlpha, textPaint)
        }

        // Layer 1: "Grow" (data-start="0")
        val pGrow = (localFrame / 41f).coerceIn(0f, 1f)
        val yGrow = lerp(-69f, 0f, evaluateCubicBezier(0f, 0.46129858f, 0.36f, 1f, pGrow))
        drawStageWord(words[0], yGrow, wrapAlpha, textPaint)
    }
}
