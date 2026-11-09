package com.talkie.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.talkie.ai.R
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

private val TopBarHeight = 56.dp

/** Top Bar — Type=Back (auth flow). Outline back button. */
@Composable
fun ZunoBackTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(start = ZunoSpace.s20, end = ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(
            icon = R.drawable.zuno_ic_chevron_left,
            onClick = onBack,
            style = ZunoIconButtonStyle.Outline,
            size = ZunoIconButtonSize.Large,
            contentDescription = "Back",
        )
    }
}

/** Top Bar — Type=Back + Compact Title. Chat & detail screens: back, centred title, trailing overflow. */
@Composable
fun ZunoCompactTitleTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(R.drawable.zuno_ic_arrow_left, onBack, size = ZunoIconButtonSize.Large, contentDescription = "Back")
        EllipsizedText(
            text = title,
            style = ZunoType.Headline.copy(textAlign = TextAlign.Center),
            color = ZunoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(ZunoIconButtonSize.Large.button), contentAlignment = Alignment.Center) { trailing() }
    }
}

/** Top Bar — Type=Menu + Action. Menu and a single action at opposite edges. */
@Composable
fun ZunoActionTopBar(
    onMenu: () -> Unit,
    onAction: () -> Unit,
    @DrawableRes actionIcon: Int,
    actionDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(R.drawable.zuno_ic_menu, onMenu, size = ZunoIconButtonSize.Large, contentDescription = "Menu")
        ZunoIconButton(actionIcon, onAction, size = ZunoIconButtonSize.Large, contentDescription = actionDescription)
    }
}

/** Top Bar — Type=Menu + Selector. Home: menu plus the "Spanish · B1" pill that opens the learning sheet. */
@Composable
fun ZunoSelectorTopBar(
    selector: String,
    onMenu: () -> Unit,
    onSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(R.drawable.zuno_ic_menu, onMenu, size = ZunoIconButtonSize.Large, contentDescription = "Menu")
        // The 36dp pill sits inside a 44dp-tall tap area.
        Box(
            modifier = Modifier
                .height(ZunoIconButtonSize.Large.button)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Change language and level", onClick = onSelector),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceSubtle)
                    .border(1.dp, ZunoColors.BorderDefault, CircleShape)
                    .padding(horizontal = ZunoSpace.s14),
                contentAlignment = Alignment.Center,
            ) {
                Text(selector, style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary, maxLines = 1)
            }
        }
    }
}

/** List section label with optional trailing icon action. */
@Composable
fun ZunoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes actionIcon: Int? = null,
    onAction: () -> Unit = {},
    actionDescription: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ZunoType.LabelSmall, color = ZunoColors.TextTertiary)
        actionIcon?.let {
            ZunoIconButton(it, onAction, size = ZunoIconButtonSize.Small, contentDescription = actionDescription)
        }
    }
}

/** Primary drawer navigation row. */
@Composable
fun ZunoMenuItem(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) ZunoColors.SurfaceDefault else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 18.dp, tint = if (active) ZunoColors.IconPrimary else ZunoColors.IconSecondary)
        Text(
            label,
            style = if (active) ZunoType.BodyMediumStrong else ZunoType.BodyMedium,
            color = if (active) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
        )
    }
}

/** Recent session row. Title truncates; selected marks the open session. */
@Composable
fun ZunoSessionRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** Pinned rows (drawer "Pinned") lead with a pin and use the stronger title style. */
    pinned: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(ZunoRadius.md))
            .background(if (selected) ZunoColors.SurfaceDefault else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) ZunoIcon(R.drawable.zuno_ic_t_pin, 14.dp, tint = ZunoColors.IconTertiary)
        EllipsizedText(
            text = title,
            style = if (selected || pinned) ZunoType.BodyMediumStrong else ZunoType.BodyMedium,
            color = if (selected || pinned) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(meta, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, maxLines = 1)
    }
}

enum class LessonStatus { Done, Current, Upcoming }

/** Lesson in a topic path; sits inside a [ZunoListGroup]. */
@Composable
fun ZunoLessonRow(
    number: Int,
    title: String,
    status: LessonStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (status == LessonStatus.Current) ZunoColors.SurfaceStrong else ZunoColors.SurfaceDefault)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s16, end = ZunoSpace.s16),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val indicatorModifier = Modifier.size(28.dp).clip(CircleShape)
        Box(
            modifier = when (status) {
                LessonStatus.Done -> indicatorModifier.background(ZunoColors.SurfaceInverse)
                LessonStatus.Current -> indicatorModifier.border(1.5.dp, ZunoColors.TextPrimary, CircleShape)
                LessonStatus.Upcoming -> indicatorModifier.border(1.5.dp, ZunoColors.BorderStrong, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (status == LessonStatus.Done) {
                ZunoIcon(R.drawable.zuno_ic_check_inverse, 16.dp)
            } else {
                Text(
                    number.toString(),
                    style = ZunoType.FootnoteStrong,
                    color = if (status == LessonStatus.Current) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                )
            }
        }
        EllipsizedText(
            text = title,
            style = ZunoType.BodyMediumStrong,
            color = if (status == LessonStatus.Upcoming) ZunoColors.TextSecondary else ZunoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        meta?.let {
            Text(
                it,
                style = ZunoType.FootnoteRegular,
                color = if (status == LessonStatus.Current) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
            )
        }
    }
}

/** Avatar for the signed-in user: their photo, or a monogram (Large = 44dp). */
@Composable
fun ZunoAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = 44.dp,
    textStyle: TextStyle = ZunoType.Headline,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(ZunoColors.SurfaceInverse),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = textStyle, color = ZunoColors.TextOnInverse)
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    // Signed URLs change on every load; cache by the stable part of the address.
                    .apply { imageUrl.substringBefore('?').let { memoryCacheKey(it).diskCacheKey(it) } }
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Signed-in user summary at the top of the drawer. */
@Composable
fun ZunoProfileCard(
    name: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(CircleShape)
            .background(ZunoColors.SurfaceDefault)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s10, end = ZunoSpace.s16),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoAvatar(name.take(1).uppercase(), imageUrl = imageUrl)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            EllipsizedText(name, ZunoType.BodyMediumStrong, ZunoColors.TextPrimary)
            EllipsizedText(detail, ZunoType.FootnoteRegular, ZunoColors.TextTertiary)
        }
    }
}

/** Subscription upsell row in the drawer. */
@Composable
fun ZunoUpgradeBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Get more with Zuno Pro",
    action: String = "Upgrade",
) {
    val shape = RoundedCornerShape(ZunoRadius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderDefault, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s16, end = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EllipsizedText(text, ZunoType.BodySmall, ZunoColors.TextSecondary, Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(ZunoSpace.s8))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(action, style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary)
            ZunoIcon(R.drawable.zuno_ic_chevron_right, 16.dp)
        }
    }
}

/** 6dp rounded progress track; [progress] in 0..1. */
@Composable
fun ZunoProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(ZunoColors.SurfaceStrong),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(ZunoColors.SurfaceInverse)
        )
    }
}
