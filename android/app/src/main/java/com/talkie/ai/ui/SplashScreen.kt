package com.talkie.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkie.ai.ui.theme.CanvasBg
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private const val ZUNO_LOGO_PATH =
    "M41.3371 6.55097C48.3802 1.2848 57.2363 -0.942236 65.93 0.366545C80.2575 2.46007 91.6871 14.096 93.5077 28.4774C102.38 29.8428 110.102 34.0308 115.473 41.3571C120.752 48.4392 122.964 57.3484 121.612 66.0797C120.597 72.6473 116.757 80.7356 111.544 84.9541C107.666 89.4483 99.2266 92.7733 93.379 93.5678C93.1093 99.3287 89.6451 106.183 85.9484 110.512C80.338 117.114 72.3297 121.207 63.6961 121.884C54.9325 122.624 46.2401 119.799 39.5828 114.047C33.8394 109.097 29.2305 101.188 28.6639 93.5345C28.2996 93.4917 27.9359 93.4444 27.5727 93.3931C20.0079 92.3174 11.8383 87.5892 7.24852 81.4622C5.32474 79.3395 3.63216 76.1164 2.51075 73.4888C-5.78281 54.0574 7.39198 30.937 28.4906 28.4968C29.7797 19.6378 34.1332 11.9367 41.3371 6.55097ZM65.2519 65.9691C64.8695 70.2457 61.7014 78.0968 58.7179 81.1518C57.9899 82.2191 56.5017 83.8025 55.5667 84.7015C50.3102 89.7545 44.8268 92.1297 37.7947 93.5081C38.1012 94.4249 38.3524 95.4572 38.6605 96.4068C40.6557 102.473 44.9974 107.485 50.714 110.323C56.1946 113.052 62.5332 113.493 68.3384 111.55C74.2856 109.556 79.2067 105.294 82.0337 99.6907C85.2883 93.1546 85.0503 87.0537 82.8035 80.3142C78.809 72.1158 74.0692 68.3106 65.2519 65.9691ZM28.3845 37.9067C21.6483 39.6099 16.4252 42.8497 12.7874 48.9277C9.5826 54.2142 8.6032 60.5572 10.0638 66.5654C11.5849 72.6927 15.4762 77.9646 20.881 81.2203C25.7534 84.1242 31.0573 84.9887 36.6225 84.1586C47.4167 82.2301 53.2311 75.5492 56.0363 65.2886C48.3901 64.4786 40.2118 59.3224 35.4007 53.3759C33.198 50.6532 31.4525 47.5899 30.2329 44.306C29.9754 43.6133 28.4933 37.9984 28.3845 37.9067ZM102.127 41.4388C98.2274 38.85 91.6749 36.8003 87.0735 37.5956C75.9948 38.7615 68.6866 46.1948 66.0129 56.7453C75.6018 58.5152 82.4955 62.7737 88.2501 70.8522C90.4059 73.8787 92.7697 79.475 93.2836 83.1758L93.4198 83.9807L93.6383 84.1316C99.7438 82.8998 105.122 79.3167 108.613 74.1549C112.113 68.9351 113.399 62.5377 112.187 56.3695C110.92 50.265 107.307 44.9028 102.127 41.4388ZM78.3935 17.0963C73.8194 11.9664 66.0873 8.61975 59.1731 9.44337C52.8145 10.0907 47.033 12.8857 42.955 17.8874C38.9382 22.7685 37.0474 29.0589 37.7062 35.348C38.3926 41.5327 41.4813 47.1996 46.3059 51.1251C49.3986 53.621 52.8373 55.0247 56.6447 55.9874C57.0486 54.1827 57.3882 52.6821 57.9525 50.9089C62.0653 39.1874 71.6313 30.2924 84.1559 28.5584C83.0382 23.9888 81.581 20.6711 78.3935 17.0963Z"

/**
 * Reusable Zuno · Interlocking symbol mark from Figma nodes 354:861 / 354:871 & 354:872 / 354:890
 * (`viewBox="0 0 122 122"`, fill `#FFFFFF` / `#F9F9F9`, fillType `EvenOdd`).
 */
@Composable
fun ZunoLogo(
    size: Dp,
    tint: Color = TextPrimary,
    contentDesc: String = "Zuno",
    modifier: Modifier = Modifier
) {
    val basePath = remember {
        PathParser().parsePathString(ZUNO_LOGO_PATH).toPath().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDesc }
    ) {
        val scaleX = this.size.width / 122f
        val scaleY = this.size.height / 122f
        val matrix = Matrix().apply {
            scale(scaleX, scaleY)
        }
        val scaledPath = Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addPath(basePath)
            transform(matrix)
        }
        drawPath(
            path = scaledPath,
            color = tint
        )
    }
}

/**
 * Backward-compatible alias for ZunoLogo.
 */
@Composable
fun ZunoCleanBalanceLogo(
    size: Dp,
    tint: Color = TextPrimary,
    contentDesc: String = "Zuno",
    modifier: Modifier = Modifier
) = ZunoLogo(size = size, tint = tint, contentDesc = contentDesc, modifier = modifier)

/**
 * Splash Screen matching Figma node 354:861 (`Splash Screen`):
 * - Background: `#171717` (`var(--color-bg-canvas, #171717)`)
 * - Centered Zuno interlocking symbol (`354:871`, `189.76x189.60` @3x = `63.25.dp` in `#F9F9F9`)
 */
@Composable
fun SplashScreen(
    onSplashFinished: (() -> Unit)? = null,
    durationMs: Long = 700L,
    modifier: Modifier = Modifier
) {
    val isInspection = LocalInspectionMode.current

    if (onSplashFinished != null && !isInspection && durationMs > 0L) {
        LaunchedEffect(onSplashFinished, durationMs) {
            delay(durationMs)
            onSplashFinished()
        }
    }

    val clickableModifier = if (onSplashFinished != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onSplashFinished
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        // Figma node 354:871: 189.76x189.60px in 1208x2770 frame @3x = 63.25.dp centered
        ZunoLogo(
            size = 63.25.dp,
            tint = TextPrimary
        )
    }
}

@Preview(
    name = "Splash Screen · Zuno",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun SplashScreenPreview() {
    TalkieTheme {
        SplashScreen()
    }
}
