package com.talkie.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.ui.components.PlasmaOrb
import com.talkie.ai.ui.components.PlasmaOrbState
import com.talkie.ai.ui.theme.InterFontFamily
import com.talkie.ai.ui.theme.TalkieTheme
import kotlinx.coroutines.delay
import java.util.Locale

private val VoiceBg = Color(0xFF171717)

private val NoFontPaddingStyle = TextStyle(
    fontFamily = InterFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

// Exact composited Liquid Glass fill colors over #010206 from Figma node 125:718
private val HeaderBtnGlassFill = Color(0xFF1C1D20)
private val CoachingPillGlassFill = Color(0xFF121318)
private val SideActionBtnGlassFill = Color(0xFF131419)
private val MicOuterRingGlassFill = Color(0xFF101116)
private val MicInnerSurfaceFill = Color(0xFF3C3D43)
private val MicMutedSurfaceFill = Color(0xFF5A232B)

@Composable
private fun LiquidGlassContainer(
    fillColor: Color,
    cornerRadiusDp: Dp,
    strokeWeightPx: Float,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadiusDp)
    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Color.White.copy(alpha = 0.2f)),
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val rPx = cornerRadiusDp.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
                val cornerRadius = CornerRadius(rPx, rPx)

                val borderBrush = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.40f),
                    0.36835f to Color.White.copy(alpha = 0.0f),
                    0.57437f to Color.White.copy(alpha = 0.0f),
                    1.0f to Color.White.copy(alpha = 0.10f),
                    start = Offset(size.width * 0.46f, 0f),
                    end = Offset(size.width * 0.54f, size.height)
                )

                val halfStroke = strokeWeightPx / 2f
                val strokeTopLeft = Offset(halfStroke, halfStroke)
                val strokeSize = Size(
                    width = (size.width - strokeWeightPx).coerceAtLeast(0f),
                    height = (size.height - strokeWeightPx).coerceAtLeast(0f)
                )
                val strokeCorner = CornerRadius(
                    (rPx - halfStroke).coerceAtLeast(0f),
                    (rPx - halfStroke).coerceAtLeast(0f)
                )

                onDrawBehind {
                    drawRoundRect(
                        color = fillColor,
                        cornerRadius = cornerRadius
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = strokeTopLeft,
                        size = strokeSize,
                        cornerRadius = strokeCorner,
                        style = Stroke(width = strokeWeightPx)
                    )
                }
            }
            .then(clickableModifier),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
private fun SvgIcon(
    viewBoxSize: Float,
    strokeWidthViewBox: Float,
    paths: List<String>,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val parsedPaths = remember(paths) {
        paths.map { PathParser().parsePathString(it).toPath() }
    }

    Canvas(modifier = modifier.size(size)) {
        val scaleX = this.size.width / viewBoxSize
        val scaleY = this.size.height / viewBoxSize
        val scale = minOf(scaleX, scaleY)

        val matrix = Matrix().apply {
            scale(scaleX, scaleY)
        }

        val stroke = Stroke(
            width = strokeWidthViewBox * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        for (basePath in parsedPaths) {
            val scaledPath = Path().apply {
                addPath(basePath)
                transform(matrix)
            }
            drawPath(
                path = scaledPath,
                color = Color.White,
                style = stroke
            )
        }
    }
}

private val PATHS_ARROW_LEFT = listOf(
    "M23.0002 9.5818L9.58203 23L23.0002 36.4182M9.58203 23H36.4184"
)

private val PATHS_ELLIPSIS = listOf(
    "M24.9162 23C24.9162 24.0594 24.0581 24.9182 22.9997 24.9182C21.9413 24.9182 21.0832 24.0594 21.0832 23C21.0832 21.9406 21.9413 21.0818 22.9997 21.0818C24.0581 21.0818 24.9162 21.9406 24.9162 23ZM38.3315 23C38.3315 24.0594 37.4735 24.9182 36.415 24.9182C35.3566 24.9182 34.4986 24.0594 34.4986 23C34.4986 21.9406 35.3566 21.0818 36.415 21.0818C37.4735 21.0818 38.3315 21.9406 38.3315 23ZM11.5009 23C11.5009 24.0594 10.6428 24.9182 9.58438 24.9182C8.52594 24.9182 7.6679 24.0594 7.6679 23C7.6679 21.9406 8.52594 21.0818 9.58438 21.0818C10.6428 21.0818 11.5009 21.9406 11.5009 23Z"
)

private val PATHS_WAND_SPARKLES = listOf(
    "M30.3342 15.1654L36.8337 21.6654M36.8337 21.6654L36.8341 21.6658M36.8337 21.6654L11.6141 46.8867C11.3718 47.1357 11.0821 47.3336 10.7621 47.4687C10.4421 47.6039 10.0982 47.6735 9.75082 47.6735C9.40343 47.6735 9.05957 47.6039 8.73953 47.4687C8.4195 47.3336 8.12979 47.1357 7.88752 46.8867L5.11423 44.1133C4.86792 43.8695 4.67239 43.5792 4.53894 43.2594C4.4055 42.9395 4.33679 42.5964 4.33679 42.2498C4.33679 41.9032 4.4055 41.5601 4.53894 41.2403C4.67239 40.9204 4.86792 40.6302 5.11423 40.3864L40.387 5.11112C40.6308 4.86479 40.921 4.66924 41.2408 4.53579C41.5606 4.40233 41.9037 4.33362 42.2503 4.33362C42.5968 4.33362 42.94 4.40233 43.2598 4.53579C43.5796 4.66924 43.8698 4.86479 44.1136 5.11112L46.8869 7.8846C47.1358 8.12689 47.3337 8.41662 47.4689 8.73668C47.604 9.05673 47.6736 9.40062 47.6736 9.74804C47.6736 10.0954 47.604 10.4393 47.4689 10.7594C47.3337 11.0795 47.1358 11.3692 46.8869 11.6115L36.8337 21.6654ZM10.8346 12.9986V17.3322M10.8346 17.3322V21.6658M10.8346 17.3322H15.1678M10.8346 17.3322H6.5013M41.1674 30.3329V34.6665M41.1674 34.6665V39.0001M41.1674 34.6665H45.5007M41.1674 34.6665H36.8341M21.6677 4.3315V6.49829M21.6677 6.49829V8.66507M21.6677 6.49829H23.8344M21.6677 6.49829H19.5011"
)

private val PATHS_X = listOf(
    "M39.0003 13L26.0003 26M26.0003 26L13.0003 39M26.0003 26L13.0003 13M26.0003 26L39.0003 39"
)

private val PATHS_MIC = listOf(
    "M124 145.002V154.002M124 145.002C129.57 145.002 134.912 142.789 138.851 138.85C142.79 134.912 145.002 129.57 145.002 124V117.999M124 145.002C118.43 145.002 113.088 142.789 109.149 138.85C105.21 134.912 102.998 129.57 102.998 124V117.999M124 93.9976C128.971 93.9976 133.001 98.0273 133.001 102.998V124C133.001 128.971 128.971 133.001 124 133.001C119.029 133.001 114.999 128.971 114.999 124V102.998C114.999 98.0273 119.029 93.9976 124 93.9976Z"
)

@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    onClose: () -> Unit = onBack,
    onMicrophoneClick: () -> Unit = {},
    onVoiceEffectsClick: () -> Unit = {},
    orbState: PlasmaOrbState = PlasmaOrbState.CONNECTING,
    isMuted: Boolean = false,
    languageLabel: String = "Spanish · B1",
    aiPromptText: String = "¿Lo quieres para tomar\naquí o para llevar?",
    aiTranslationText: String = "Would you like it for here or to go?",
    userTranscriptLine1: String = "\"Para llevar, por favor.",
    userTranscriptLine2: String = "¿Cuánto cuesta en total?\"",
    coachingStatusText: String? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isInspection = LocalInspectionMode.current
    val stroke1Px = with(density) { 1.dp.toPx() }

    // Live session timer starting at 84s (01:24) to match Figma preview, ticking in real time
    var elapsedSeconds by remember { mutableIntStateOf(84) }
    LaunchedEffect(isInspection) {
        if (isInspection) return@LaunchedEffect
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }
    val formattedTime = remember(elapsedSeconds) {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    val resolvedStatusText = coachingStatusText ?: when {
        isMuted -> "Microphone muted  ·  Tap mic to speak"
        orbState == PlasmaOrbState.SPEAKING -> "✓ 96% Pronunciation  ·  AI Speaking..."
        orbState == PlasmaOrbState.THINKING -> "✓ 96% Pronunciation  ·  Thinking..."
        orbState == PlasmaOrbState.ERROR -> "Connection issue  ·  Tap mic to retry"
        else -> "✓ 96% Pronunciation  ·  Listening..."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoiceBg)
            .systemBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Header Row: Back (125:721), Title + Subtitle (125:729, 125:757), More options (125:725)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassContainer(
                fillColor = HeaderBtnGlassFill,
                cornerRadiusDp = 24.dp,
                strokeWeightPx = stroke1Px,
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                SvgIcon(
                    viewBoxSize = 46f,
                    strokeWidthViewBox = 3.17241f,
                    paths = PATHS_ARROW_LEFT,
                    size = 18.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Speaking Practice",
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$languageLabel  ·  $formattedTime",
                    color = Color.White.copy(alpha = 0.64f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp,
                    textAlign = TextAlign.Center
                )
            }

            LiquidGlassContainer(
                fillColor = HeaderBtnGlassFill,
                cornerRadiusDp = 24.dp,
                strokeWeightPx = stroke1Px,
                onClick = onVoiceEffectsClick,
                modifier = Modifier.size(48.dp)
            ) {
                SvgIcon(
                    viewBoxSize = 46f,
                    strokeWidthViewBox = 3.17241f,
                    paths = PATHS_ELLIPSIS,
                    size = 18.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Spanish Prompt (125:738)
        Text(
            text = aiPromptText,
            color = Color.White.copy(alpha = 0.96f),
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. English Translation (125:739)
        Text(
            text = aiTranslationText,
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.1).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        // 4. Center Voice Energy Orb (125:740)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            PlasmaOrb(
                state = if (isMuted) PlasmaOrbState.DISABLED else orbState,
                size = 220.dp,
                label = "Voice energy orb",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMicrophoneClick
                )
            )
        }

        // 5. User Speech Transcript Lines (125:742, 125:743)
        Text(
            text = userTranscriptLine1,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (userTranscriptLine2.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = userTranscriptLine2,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.25).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6. Voice coaching status pill (125:760)
        LiquidGlassContainer(
            fillColor = CoachingPillGlassFill,
            cornerRadiusDp = 20.dp,
            strokeWeightPx = stroke1Px,
            contentAlignment = Alignment.Center,
            modifier = Modifier.heightIn(min = 38.dp)
        ) {
            Text(
                text = resolvedStatusText,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp,
                style = NoFontPaddingStyle,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 7. Bottom Controls Row: Voice effects (125:744), Microphone (125:748), Close (125:753)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassContainer(
                fillColor = SideActionBtnGlassFill,
                cornerRadiusDp = 28.dp,
                strokeWeightPx = stroke1Px,
                onClick = onVoiceEffectsClick,
                modifier = Modifier.size(56.dp)
            ) {
                SvgIcon(
                    viewBoxSize = 52f,
                    strokeWidthViewBox = 3.58621f,
                    paths = PATHS_WAND_SPARKLES,
                    size = 20.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            LiquidGlassContainer(
                fillColor = MicOuterRingGlassFill,
                cornerRadiusDp = 44.dp,
                strokeWeightPx = stroke1Px,
                onClick = onMicrophoneClick,
                modifier = Modifier.size(88.dp)
            ) {
                LiquidGlassContainer(
                    fillColor = if (isMuted) MicMutedSurfaceFill else MicInnerSurfaceFill,
                    cornerRadiusDp = 35.dp,
                    strokeWeightPx = stroke1Px,
                    modifier = Modifier
                        .size(70.dp)
                        .align(Alignment.Center)
                ) {
                    SvgIcon(
                        viewBoxSize = 248f,
                        strokeWidthViewBox = 3.42857f,
                        paths = PATHS_MIC,
                        size = 88.dp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            LiquidGlassContainer(
                fillColor = SideActionBtnGlassFill,
                cornerRadiusDp = 28.dp,
                strokeWeightPx = stroke1Px,
                onClick = onClose,
                modifier = Modifier.size(56.dp)
            ) {
                SvgIcon(
                    viewBoxSize = 52f,
                    strokeWidthViewBox = 3.58621f,
                    paths = PATHS_X,
                    size = 20.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(
    name = "Voice Screen · Sleek Liquid Glass",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun VoiceScreenPreview() {
    TalkieTheme {
        var currentScreen by remember { mutableStateOf("voice") }
        when (currentScreen) {
            "voice" -> VoiceScreen(
                onBack = { currentScreen = "welcome" },
                onClose = { currentScreen = "welcome" }
            )
            "welcome" -> WelcomeScreen(
                onStartVoice = { currentScreen = "voice" }
            )
        }
    }
}
