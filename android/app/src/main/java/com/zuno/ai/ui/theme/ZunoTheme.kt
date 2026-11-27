package com.zuno.ai.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuno.ai.R

/** Zuno foundations — Smoked Carbon tokens from Figma "Foundations" (`5:2`). */
object ZunoColors {
    // Primitives — Gray (Smoked Carbon cool-zinc)
    val Gray1 = Color(0xFF050506)
    val Gray2 = Color(0xFF08080A)
    // Matches Figma gray/3 (`bg/canvas`) & gray/3-5 (`bg/elevated`). Keep res/values/colors.xml `background` and the inverse zuno_ic_* drawables in sync.
    val Gray3 = Color(0xFF0B0B0D)
    val Gray3_5 = Color(0xFF16161B)
    val Gray4 = Color(0xFF1A1A20)
    val Gray5 = Color(0xFF24242C)
    val Gray6 = Color(0xFF2E2E37)
    val Gray7 = Color(0xFF3B3B46)
    val Gray8 = Color(0xFF52525B)
    val Gray9 = Color(0xFF6E6E78)
    val Gray10 = Color(0xFFA1A1AA)
    val Gray11 = Color(0xFFE4E4E7)
    val Gray12 = Color(0xFFF4F4F5)

    // Primitives — Carbon Alpha (#DDDDFB)
    val CarbonAlpha2_5 = Color(0x06DDDDFB) // ~2.5%
    val CarbonAlpha4_5 = Color(0x0BDDDDFB) // ~4.5%
    val CarbonAlpha8 = Color(0x14DDDDFB)   // 8%
    val CarbonAlpha9 = Color(0x17DDDDFB)   // 9%
    val CarbonAlpha14 = Color(0x24DDDDFB)  // 14%
    val CarbonAlpha20 = Color(0x33DDDDFB)  // 20%
    val CarbonAlpha44 = Color(0x70DDDDFB)  // 44%
    val CarbonAlpha56 = Color(0x8FDDDDFB)  // 56%

    // Primitives — Emerald
    val Emerald400 = Color(0xFF4ADE80)
    val EmeraldAlpha14 = Color(0x244ADE80) // 14%
    val EmeraldAlpha34 = Color(0x574ADE80) // 34%

    // Primitives — Red & Warm Accents
    val Red300 = Color(0xFFFF8B8B)
    val Red500 = Color(0xFFD15555)
    val Coral400 = Color(0xFFF87171)
    val CoralAlpha12 = Color(0x1FF87171) // 12%
    val Amber400 = Color(0xFFFBBF24)
    val Teal400 = Color(0xFF14C8B4)

    // Primitives — Pure Black / White
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    // Background
    val BgCanvas = Gray3
    val BgElevated = Gray3_5
    val BgOverlay = Gray3_5
    val BgScrim = Color(0xB8000000) // black-alpha/72
    val ScrimVideoMid = Color(0x910B0B0D)    // bg/canvas @57%
    val ScrimVideoBottom = Color(0xE00B0B0D) // bg/canvas @88%

    // Surface
    val SurfaceSubtle = CarbonAlpha2_5
    val SurfaceDefault = CarbonAlpha4_5
    val SurfaceStrong = CarbonAlpha9
    val SurfaceStronger = CarbonAlpha14
    val SurfaceInverse = Gray11
    val SurfaceInverseMuted = CarbonAlpha44
    val SurfaceOverlayPill = Color(0xE018181E) // #18181E @88%
    val SurfaceControlTrack = SurfaceSubtle
    val SurfaceControlActive = CarbonAlpha8
    val SurfaceSuccessSubtle = EmeraldAlpha14

    // Text
    val TextPrimary = Gray12
    val TextSecondary = Gray10
    val TextTertiary = Gray9
    val TextPlaceholder = Gray8
    val TextOnInverse = Gray3
    val TextOnInverseSecondary = Gray8
    val TextSuccess = Emerald400

    // Border
    val BorderSubtle = CarbonAlpha8
    val BorderDefault = CarbonAlpha14
    val BorderStrong = CarbonAlpha20
    val BorderFocus = CarbonAlpha44
    val BorderInverse = Gray3
    val BorderControlActive = BorderStrong
    val BorderSuccessSubtle = EmeraldAlpha34

    // Icon
    val IconPrimary = Gray12
    val IconSecondary = CarbonAlpha56
    val IconTertiary = Gray8
    val IconOnInverse = Gray3
    val IconBrand = Gray12
    val IconDanger = Red300
    val IconSuccess = Emerald400

    // Status
    val StatusListening = Color(0xFF60A5FA)
    val StatusSpeaking = Emerald400
    val StatusConnecting = Amber400

    // Notification hero gradients (`Home · 5a · Notifications`)
    val NotificationsBannerStart = Color(0xFF0F1738)
    val NotificationsBannerMid = Color(0xFF29459E)
    val NotificationsBannerEnd = Color(0xFF6170D9)
    val NotificationsBannerGlow = Color(0x8C8CB8FF)
    val NotificationsThumbStart = Color(0xFF1A265C)
    val NotificationsThumbEnd = Color(0xFF476BD9)

    val Divider = CarbonAlpha9
    val DividerSubtle = CarbonAlpha8

    // Danger (destructive confirm, "Delete" menu item, Error status pill)
    val SurfaceDanger = Red500
    val TextDanger = Red300
    val DangerSurface = Color(0x24D15555) // rgba(209,85,85,0.14)
    val DangerBorder = Color(0x52FF8B8B) // rgba(255,139,139,0.32)

    // Glow
    val GlowDeep = Gray4
    val GlowBase = Gray7
    val GlowAccent = Gray9
    val GlowHighlight = Gray11
    val GlowOrbTop = Color(0xFFFFFFFF)
    val GlowOrbBottom = Color(0xFFB4B4BE)
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
    val s48 = 48.dp
}

/**
 * Responsive horizontal spacing tokens keyed off [android.content.res.Configuration.screenWidthDp].
 * Standard phones (< 420dp, e.g. Xiaomi 14T at 360-406dp) use 16dp outer gutters and tighter
 * internal row padding so labels, tabs, and pills retain breathing room; wider phones/tablets step up.
 */
@Immutable
data class ZunoLayout(
    val screenPaddingH: Dp,
    val topBarPaddingH: Dp,
    val topBarPillEndPadding: Dp,
    val cardPaddingH: Dp,
    val cardTrailingMenuEndPadding: Dp,
    val rowGap: Dp,
    val chipPaddingH: Dp,
    val chipGap: Dp,
    val tabTrackPadding: Dp,
    val modalPaddingH: Dp,
    val isCompact: Boolean,
)

fun zunoLayoutForWidth(screenWidthDp: Int): ZunoLayout = when {
    screenWidthDp < 420 -> ZunoLayout(
        screenPaddingH = 16.dp,
        topBarPaddingH = 6.dp, // 6dp bar padding + 10dp icon inset inside 44dp ZunoIconButton = 16dp edge
        topBarPillEndPadding = 16.dp,
        cardPaddingH = 12.dp,
        cardTrailingMenuEndPadding = 6.dp,
        rowGap = 10.dp,
        chipPaddingH = 12.dp,
        chipGap = 6.dp,
        tabTrackPadding = 3.dp,
        modalPaddingH = 16.dp,
        isCompact = true,
    )
    screenWidthDp < 600 -> ZunoLayout(
        screenPaddingH = 20.dp,
        topBarPaddingH = 10.dp,
        topBarPillEndPadding = 20.dp,
        cardPaddingH = 14.dp,
        cardTrailingMenuEndPadding = 8.dp,
        rowGap = 12.dp,
        chipPaddingH = 14.dp,
        chipGap = 8.dp,
        tabTrackPadding = 4.dp,
        modalPaddingH = 20.dp,
        isCompact = false,
    )
    else -> ZunoLayout(
        screenPaddingH = 24.dp,
        topBarPaddingH = 12.dp,
        topBarPillEndPadding = 20.dp,
        cardPaddingH = 16.dp,
        cardTrailingMenuEndPadding = 8.dp,
        rowGap = 12.dp,
        chipPaddingH = 14.dp,
        chipGap = 8.dp,
        tabTrackPadding = 4.dp,
        modalPaddingH = 24.dp,
        isCompact = false,
    )
}

val LocalZunoLayout = staticCompositionLocalOf { zunoLayoutForWidth(360) }

val zunoLayout: ZunoLayout
    @Composable
    @ReadOnlyComposable
    get() = LocalZunoLayout.current

object ZunoRadius {
    val xs = 6.dp
    val sm = 8.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 14.dp
    val xxl = 16.dp
    val xxxl = 18.dp
    val xxxxl = 20.dp
    val xxxxxl = 24.dp
    val full = 999.dp
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

val Outfit = FontFamily(
    Font(resId = R.font.outfit, weight = FontWeight.Medium),
)

/** The 14 Lexend text styles + Web/Title & brand Wordmark. */
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
    val TitleMedium = style(FontWeight.SemiBold, 20, 26, -0.5f)
    val HeaderTitle = style(FontWeight.Normal, 20, 26, -0.3f)
    val WebTitle = style(FontWeight.SemiBold, 22, 28, -0.4f)
    val Wordmark = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val Headline = style(FontWeight.Medium, 17, 24, -0.2f)
    val BodyLarge = style(FontWeight.Normal, 17, 24)
    val BodyMedium = style(FontWeight.Normal, 15, 22)
    val BodyMediumStrong = style(FontWeight.Medium, 15, 22, -0.1f)
    val BodySmall = style(FontWeight.Normal, 14, 20)
    val LabelLarge = style(FontWeight.SemiBold, 15, 20, -0.2f)
    val LabelMedium = style(FontWeight.Medium, 14, 18)
    val SectionLabel = style(FontWeight.Medium, 13, 18)
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
    val layout = zunoLayoutForWidth(LocalConfiguration.current.screenWidthDp)
    MaterialTheme(colorScheme = ZunoColorScheme) {
        CompositionLocalProvider(
            LocalContentColor provides ZunoColors.TextPrimary,
            LocalZunoLayout provides layout,
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = ZunoColors.TextPrimary,
                backgroundColor = ZunoColors.SurfaceStronger,
            ),
        ) {
            ProvideTextStyle(ZunoType.BodyMedium.copy(color = ZunoColors.TextPrimary), content)
        }
    }
}
