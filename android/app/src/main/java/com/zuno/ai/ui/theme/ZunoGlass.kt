package com.zuno.ai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Zuno surface & elevation tokens aligned with Figma "Foundations" (`5:2`) and "Components" (`5:3`).
 */
object ZunoGlassTokens {
    val CanvasTop = ZunoColors.BgCanvas
    val CanvasMid = ZunoColors.BgCanvas
    val CanvasBottom = ZunoColors.BgCanvas

    val SubtleFill = ZunoColors.SurfaceSubtle
    val ElevatedFill = ZunoColors.SurfaceDefault
    val OverlayDarkBase = ZunoColors.BgElevated
    val MediaPillDarkBase = ZunoColors.SurfaceOverlayPill

    val Hairline = ZunoColors.DividerSubtle
}

val ZunoGlassBorderSubtleBrush: Brush = SolidColor(ZunoColors.BorderSubtle)

val ZunoGlassBorderElevatedBrush: Brush = SolidColor(ZunoColors.BorderDefault)

val ZunoGlassHairlineBrush: Brush = SolidColor(ZunoColors.DividerSubtle)

val ZunoGlassVerticalHairlineBrush: Brush = SolidColor(ZunoColors.DividerSubtle)

/**
 * Full-screen `bg/canvas` (`#0B0B0D`).
 */
fun Modifier.zunoCanvasBackground(): Modifier = this.background(ZunoColors.BgCanvas)

/**
 * Subtle surface card/pill (`surface/subtle` 2.5% carbon-alpha + `border/subtle` 8% carbon-alpha).
 */
fun Modifier.zunoGlassSubtle(
    shape: Shape,
    borderWidth: Dp = 1.dp,
    borderBrush: Brush? = ZunoGlassBorderSubtleBrush,
    fillColor: Color = ZunoColors.SurfaceSubtle,
): Modifier = this
    .clip(shape)
    .background(fillColor)
    .then(
        if (borderBrush != null) Modifier.border(borderWidth, borderBrush, shape) else Modifier,
    )

/**
 * Default/elevated control surface (`surface/default` 4.3% carbon-alpha + `border/default` 14% carbon-alpha).
 */
fun Modifier.zunoGlassElevated(
    shape: Shape,
    borderWidth: Dp = 1.dp,
    borderBrush: Brush? = ZunoGlassBorderElevatedBrush,
    fillColor: Color = ZunoColors.SurfaceDefault,
): Modifier = this
    .clip(shape)
    .background(fillColor)
    .then(
        if (borderBrush != null) Modifier.border(borderWidth, borderBrush, shape) else Modifier,
    )

/**
 * Elevated overlay surface (`bg/elevated` `#16161B` + `border/subtle` + elevation shadow).
 * Used for Drawer, Bottom Sheets, Dialogs, Dropdown Menus, and Floating Banners.
 */
fun Modifier.zunoGlassOverlay(
    shape: Shape,
    shadowElevation: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    drawBorder: Boolean = true,
): Modifier = composed {
    val base = if (shadowElevation > 0.dp) {
        this.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color.Black,
            spotColor = Color.Black,
        )
    } else {
        this
    }
    base
        .clip(shape)
        .background(ZunoColors.BgElevated)
        .then(
            if (drawBorder) Modifier.border(borderWidth, ZunoColors.BorderSubtle, shape) else Modifier,
        )
}

/**
 * Media overlay pill for video call overlays (AI Tutor badge, mic/waveform indicators).
 */
fun Modifier.zunoGlassMediaPill(
    shape: Shape,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(ZunoGlassTokens.MediaPillDarkBase)
    .border(borderWidth, ZunoColors.BorderSubtle, shape)

/**
 * Progress bar track (`surface/strong` 9% carbon-alpha).
 */
fun Modifier.zunoRecessedTrack(shape: Shape): Modifier = this
    .clip(shape)
    .background(ZunoColors.SurfaceStrong)

/** Horizontal hairline divider (`divider/subtle` 8% carbon-alpha). */
@Composable
fun ZunoGlassHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ZunoGlassHairlineBrush),
    )
}

/** Vertical hairline divider for split rows (e.g. Scheduled time | details). */
@Composable
fun ZunoGlassVerticalHairline(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height)
            .background(ZunoGlassVerticalHairlineBrush),
    )
}
