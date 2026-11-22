package com.zuno.ai.ui.components

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
import androidx.compose.ui.graphics.SolidColor
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
import com.zuno.ai.R
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoRecessedTrack

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

/** Top Bar · Onboarding (`2524:3247`): optional Ghost `arrow-left` back button + optional centered title. */
@Composable
fun ZunoOnboardingTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ZunoIconButton(
                icon = R.drawable.zuno_ic_arrow_left,
                onClick = onBack,
                size = ZunoIconButtonSize.Large,
                contentDescription = "Back",
            )
        } else {
            Spacer(Modifier.size(ZunoIconButtonSize.Large.button))
        }
        if (title != null) {
            EllipsizedText(
                text = title,
                style = ZunoType.Headline.copy(textAlign = TextAlign.Center),
                color = ZunoColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.size(ZunoIconButtonSize.Large.button))
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

/**
 * Top Bar · Section (`2653:3340`): 64dp header with leading arrow-left back button,
 * Title/Large title, optional search ghost button, and optional circular glass `+` button.
 */
@Composable
fun ZunoSectionTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showSearch: Boolean = false,
    searchActive: Boolean = false,
    onSearchToggle: () -> Unit = {},
    searchDescription: String = "Search",
    showAdd: Boolean = false,
    onAdd: () -> Unit = {},
    addDescription: String = "Add",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = ZunoSpace.s12, end = ZunoSpace.s24),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(
            icon = R.drawable.zuno_ic_arrow_left,
            onClick = onBack,
            size = ZunoIconButtonSize.Large,
            contentDescription = "Back",
        )
        EllipsizedText(
            text = title,
            style = ZunoType.TitleLarge,
            color = ZunoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (showSearch || showAdd) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSearch) {
                    ZunoIconButton(
                        icon = if (searchActive) R.drawable.zuno_ic_t_x else R.drawable.zuno_ic_t_search,
                        onClick = onSearchToggle,
                        style = ZunoIconButtonStyle.Ghost,
                        size = ZunoIconButtonSize.Medium,
                        tint = ZunoColors.IconPrimary,
                        contentDescription = searchDescription,
                    )
                }
                if (showAdd) {
                    ZunoIconButton(
                        icon = R.drawable.zuno_ic_plus,
                        onClick = onAdd,
                        style = ZunoIconButtonStyle.Strong,
                        size = ZunoIconButtonSize.Medium,
                        contentDescription = addDescription,
                    )
                }
            }
        }
    }
}

/**
 * Segmented Tabs (`3091:3712`): 40dp glass pill track with elevated glass active pill.
 */
@Composable
fun ZunoSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .zunoGlassSubtle(CircleShape, fillColor = ZunoColors.SurfaceControlTrack)
            .padding(ZunoSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (selected) {
                            Modifier.zunoGlassElevated(
                                CircleShape,
                                fillColor = ZunoColors.SurfaceControlActive,
                                borderBrush = SolidColor(ZunoColors.BorderDefault),
                            )
                        } else {
                            Modifier.clip(CircleShape)
                        },
                    )
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                EllipsizedText(
                    text = label,
                    style = ZunoType.LabelSmall,
                    color = if (selected) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                )
            }
        }
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
        // The 34dp glass pill sits inside a 44dp-tall tap area.
        Box(
            modifier = Modifier
                .height(ZunoIconButtonSize.Large.button)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Change language and level", onClick = onSelector),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .zunoGlassSubtle(CircleShape)
                    .padding(horizontal = ZunoSpace.s12),
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

/** Primary drawer navigation row (`3077:14461`..`14500`). */
@Composable
fun ZunoMenuItem(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(ZunoRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(
                if (active) {
                    Modifier.zunoGlassElevated(
                        shape,
                        fillColor = ZunoColors.SurfaceControlActive,
                        borderBrush = SolidColor(ZunoColors.BorderSubtle),
                    )
                } else {
                    Modifier.clip(shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s12, end = ZunoSpace.s10),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoIcon(icon, 17.dp, tint = if (active) ZunoColors.IconPrimary else ZunoColors.IconSecondary)
            EllipsizedText(
                text = label,
                style = if (active) ZunoType.LabelMedium else ZunoType.BodySmall,
                color = if (active) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
            )
        }
        trailing?.invoke()
    }
}

/** Recent session row (`3077:14509`..`14521`). Title truncates; selected marks the open session. */
@Composable
fun ZunoSessionRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** Pinned rows lead with a pin and use the stronger title style. */
    pinned: Boolean = false,
    unread: Boolean = false,
) {
    val shape = RoundedCornerShape(ZunoRadius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .then(if (selected) Modifier.clip(shape).background(ZunoColors.SurfaceDefault) else Modifier.clip(shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s12, end = ZunoSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) ZunoIcon(R.drawable.zuno_ic_t_pin, 13.dp, tint = ZunoColors.IconTertiary)
        EllipsizedText(
            text = title,
            style = if (selected || pinned) ZunoType.LabelMedium else ZunoType.BodySmall,
            color = if (selected || pinned) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meta, style = ZunoType.Micro, color = ZunoColors.TextTertiary, maxLines = 1)
            if (unread) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceInverse),
                )
            }
        }
    }
}

enum class LessonStatus { Done, Current, Upcoming }

/** Lesson in a topic path; sits inside a glass [ZunoListGroup]. */
@Composable
fun ZunoLessonRow(
    number: Int,
    title: String,
    status: LessonStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) ZunoGlassHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .then(
                    if (status == LessonStatus.Current) {
                        Modifier.background(ZunoColors.SurfaceStrong)
                    } else {
                        Modifier
                    },
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ZunoSpace.s16),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val indicatorModifier = Modifier.size(32.dp).clip(CircleShape)
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

/** Signed-in user summary glass pill at the top of the drawer (`3077:14450`). */
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
            .height(50.dp)
            .zunoGlassSubtle(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s6, end = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoAvatar(
            initial = name.take(1).uppercase(),
            imageUrl = imageUrl,
            size = 36.dp,
            textStyle = ZunoType.LabelMedium,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            EllipsizedText(name, ZunoType.LabelMedium, ZunoColors.TextPrimary)
            EllipsizedText(detail, ZunoType.Micro, ZunoColors.TextTertiary)
        }
    }
}

/** Subscription upsell glass row in the drawer & settings (`3077:14528`). */
@Composable
fun ZunoUpgradeBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Get more with Zuno Pro",
    action: String = "Upgrade",
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .zunoGlassSubtle(shape, borderBrush = SolidColor(ZunoColors.BorderDefault))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s14, end = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EllipsizedText(text, ZunoType.CaptionRegular, ZunoColors.TextSecondary, Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(ZunoSpace.s8))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(action, style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary)
            ZunoIcon(R.drawable.zuno_ic_chevron_right, 14.dp)
        }
    }
}

/** 6dp rounded recessed glass progress track; [progress] in 0..1. */
@Composable
fun ZunoProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .zunoRecessedTrack(CircleShape),
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
