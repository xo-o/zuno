package com.zuno.ai.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuno.ai.R

/** Zuno foundations — tokens from the Figma "Foundations" page. */
object ZunoColors {
    // Primitives — Gray
    val Gray1 = Color(0xFF050505)
    // Matches Figma gray/2. Keep res/values/colors.xml `background` and the inverse zuno_ic_* drawables in sync.
    val Gray2 = Color(0xFF121212)
    val Gray3 = Color(0xFF171717)
    val Gray4 = Color(0xFF242424)
    val Gray5 = Color(0xFF333333)
    val Gray6 = Color(0xFF454545)
    val Gray7 = Color(0xFF595959)
    val Gray8 = Color(0xFF707070)
    val Gray9 = Color(0xFF8A8A8A)
    val Gray10 = Color(0xFFA8A8A8)
    val Gray11 = Color(0xFFCCCCCC)
    val Gray12 = Color(0xFFE6E6E6)

    // Background
    val BgCanvas = Gray2
    val BgElevated = Gray3
    val BgScrim = Color(0xA3000000)

    // Surface
    val SurfaceSubtle = Color(0x0AFFFFFF)
    val SurfaceDefault = Color(0x0FFFFFFF)
    val SurfaceStrong = Color(0x1AFFFFFF)
    val SurfaceStronger = Color(0x24FFFFFF)
    val SurfaceInverse = Color(0xEBFFFFFF)
    val SurfaceInverseMuted = Color(0x70FFFFFF)

    // Text
    val TextPrimary = Color(0xEBFFFFFF)
    val TextSecondary = Color(0xA3FFFFFF)
    val TextTertiary = Color(0x70FFFFFF)
    val TextPlaceholder = Color(0x52FFFFFF)
    val TextOnInverse = Gray2
    val TextOnInverseSecondary = Color(0xFF595959)

    // Border
    val BorderSubtle = Color(0x0FFFFFFF)
    val BorderDefault = Color(0x14FFFFFF)
    val BorderStrong = Color(0x24FFFFFF)
    val BorderFocus = Color(0x70FFFFFF)
    val BorderInverse = Gray2

    // Icon
    val IconPrimary = Color(0xEBFFFFFF)
    val IconSecondary = Color(0x8FFFFFFF)
    val IconTertiary = Color(0x52FFFFFF)
    val IconOnInverse = Gray2

    val Divider = Color(0x24FFFFFF)

    // Danger (destructive confirm, "Delete" menu item)
    val SurfaceDanger = Color(0xFFD15555)
    val TextDanger = Color(0xFFFF8B8B)

    // Glow
    val GlowDeep = Gray4
    val GlowBase = Gray7
    val GlowAccent = Gray9
    val GlowHighlight = Gray11
    val GlowOrbTop = Color(0xFFFFFFFF)
    val GlowOrbBottom = Color(0xFFB8B8BD)
    val GlowOrbRim = Color(0x8FFFFFFF)
}

object ZunoSpace {
    val s0 = 0.dp
    val s2 = 2.dp
    val s4 = 4.dp
    val s6 = 6.dp
    val s8 = 8.dp
    val s10 = 10.dp
    val s12 = 12.dp
    val s14 = 14.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
}

object ZunoRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 40.dp
}

@OptIn(ExperimentalTextApi::class)
private fun lexend(weight: FontWeight) = Font(
    resId = R.font.lexend,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val Lexend = FontFamily(
    lexend(FontWeight.Normal),
    lexend(FontWeight.Medium),
    lexend(FontWeight.SemiBold),
)

/** The 14 Lexend text styles. */
object ZunoType {
    private fun style(weight: FontWeight, size: Int, lineHeight: Int, tracking: Float = 0f) = TextStyle(
        fontFamily = Lexend,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    val Display = style(FontWeight.SemiBold, 28, 34, -1f)
    val TitleLarge = style(FontWeight.SemiBold, 24, 30, -0.8f)
    val Headline = style(FontWeight.Medium, 17, 24, -0.2f)
    val BodyLarge = style(FontWeight.Normal, 17, 24)
    val BodyMedium = style(FontWeight.Normal, 15, 22)
    val BodyMediumStrong = style(FontWeight.Medium, 15, 22, -0.1f)
    val BodySmall = style(FontWeight.Normal, 14, 20)
    val LabelLarge = style(FontWeight.SemiBold, 15, 20, -0.2f)
    val LabelMedium = style(FontWeight.Medium, 14, 18)
    val LabelSmall = style(FontWeight.Medium, 13, 16)
    val CaptionRegular = style(FontWeight.Normal, 13, 18)
    val FootnoteRegular = style(FontWeight.Normal, 12, 16)
    val FootnoteStrong = style(FontWeight.Medium, 12, 16)
    val Micro = style(FontWeight.Medium, 11, 14, 0.2f)
}

private val ZunoColorScheme = darkColorScheme(
    primary = ZunoColors.SurfaceInverse,
    onPrimary = ZunoColors.TextOnInverse,
    background = ZunoColors.BgCanvas,
    onBackground = ZunoColors.TextPrimary,
    surface = ZunoColors.BgElevated,
    onSurface = ZunoColors.TextPrimary,
    surfaceVariant = ZunoColors.SurfaceDefault,
    onSurfaceVariant = ZunoColors.TextSecondary,
    surfaceTint = Color.Transparent,
    outline = ZunoColors.BorderDefault,
    scrim = ZunoColors.BgScrim,
)

@Composable
fun ZunoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ZunoColorScheme) {
        CompositionLocalProvider(
            LocalContentColor provides ZunoColors.TextPrimary,
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = ZunoColors.TextPrimary,
                backgroundColor = ZunoColors.SurfaceStronger,
            ),
        ) {
            ProvideTextStyle(ZunoType.BodyMedium.copy(color = ZunoColors.TextPrimary), content)
        }
    }
}
