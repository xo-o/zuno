package com.zuno.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace

/** Zuno interlocking mark. */
@Composable
fun ZunoLogoMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.zuno_logo_mark),
        contentDescription = "Zuno",
        modifier = modifier.size(size),
    )
}

/**
 * Talk Button — glossy orb. Small = 48dp (in the Talk Dock), Large = 72dp.
 * Listening widens the glow.
 */
@Composable
fun ZunoTalkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    listening: Boolean = false,
) {
    val size = if (large) 72.dp else 48.dp
    val glowRadius by animateFloatAsState(if (listening) 24f else 12f, label = "talkGlow")
    val glowAlpha by animateFloatAsState(if (listening) 0.30f else 0.18f, label = "talkGlowAlpha")

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val blur = glowRadius.dp.toPx()
                val radius = this.size.minDimension / 2f + blur
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            (this.size.minDimension / 2f / radius) * 0.85f to Color.White.copy(alpha = glowAlpha),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            }
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(ZunoColors.GlowOrbTop, ZunoColors.GlowOrbBottom)))
            .drawWithContent {
                drawContent()
                // Inner shadow: 0,-4 offset, 8 blur, black 18%.
                drawRect(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.18f),
                    )
                )
            }
            .border(1.5.dp, ZunoColors.GlowOrbRim, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Talk" },
        contentAlignment = Alignment.Center,
    ) {
        if (large) {
            ZunoIcon(R.drawable.zuno_ic_talk_large_icon, 28.dp)
        } else {
            ZunoIcon(R.drawable.zuno_ic_talk_small_icon, 20.dp)
        }
    }
}

/** Home bottom dock: Lessons · Talk Button (Small) · Roleplay, separated by hairline rules. */
@Composable
fun ZunoTalkDock(
    onLessons: () -> Unit,
    onTalk: () -> Unit,
    onRoleplay: () -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderDefault, CircleShape)
            .padding(horizontal = ZunoSpace.s8, vertical = ZunoSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(R.drawable.zuno_ic_book_open, onLessons, contentDescription = "Lessons")
        DockRule()
        ZunoTalkButton(onClick = onTalk, listening = listening)
        DockRule()
        ZunoIconButton(R.drawable.zuno_ic_users, onRoleplay, contentDescription = "Roleplay")
    }
}

@Composable
private fun DockRule() {
    Box(Modifier.width(1.dp).height(24.dp).background(ZunoColors.Divider))
}

enum class CallControlState { Default, Active, End }

/** Round 56dp control for the live voice dock. */
@Composable
fun ZunoCallControl(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    state: CallControlState = CallControlState.Default,
) {
    val base = modifier.size(56.dp).clip(CircleShape)
    val styled = when (state) {
        CallControlState.Default -> base
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderDefault, CircleShape)
        CallControlState.Active -> base
            .background(ZunoColors.SurfaceStronger)
            .border(1.dp, ZunoColors.BorderStrong, CircleShape)
        CallControlState.End -> base.background(ZunoColors.SurfaceInverse)
    }
    Box(
        modifier = styled
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, 22.dp)
    }
}
