package com.zuno.ai.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.NotificationFilter
import com.zuno.ai.data.SystemNotificationItem
import com.zuno.ai.data.SystemNotificationKind
import com.zuno.ai.ui.components.ZunoAvatar
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoEmptyState
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoSectionHeader
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassMediaPill
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout

/**
 * `Home · 5a · Notifications` (`7424:14044`) & `Home · 5b · Notifications — Empty` (`7424:14139`).
 * Bell icon destination from the sidebar drawer showing app updates, user messages, live room invites, and milestones.
 */
@Composable
fun NotificationsScreen(
    items: List<SystemNotificationItem>,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onItemClick: (SystemNotificationItem) -> Unit,
    modifier: Modifier = Modifier,
    initialFilter: NotificationFilter = NotificationFilter.All,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(initialFilter) }

    val filtered = items.filter {
        selectedFilter == NotificationFilter.All || it.filter == selectedFilter
    }
    val todayItems = filtered.filter { it.isToday }
    val earlierItems = filtered.filterNot { it.isToday }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = onBack,
            trailingIcon = R.drawable.zuno_ic_t_settings,
            trailingDescription = stringResource(R.string.notifications_settings_desc),
            onTrailing = onOpenNotificationSettings,
        )

        // Filter Tag Pills row (`All`, `Messages`, `Updates`, `Announcements`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s6),
            horizontalArrangement = Arrangement.spacedBy(zunoLayout.chipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotificationFilter.entries.forEach { filter ->
                val label = stringResource(
                    when (filter) {
                        NotificationFilter.All -> R.string.notifications_filter_all
                        NotificationFilter.Messages -> R.string.notifications_filter_messages
                        NotificationFilter.Updates -> R.string.notifications_filter_updates
                        NotificationFilter.Announcements -> R.string.notifications_filter_announcements
                    },
                )
                NotificationFilterPill(
                    label = label,
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = zunoLayout.screenPaddingH),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                ) {
                    val emptyBody = if (selectedFilter == NotificationFilter.All) {
                        stringResource(R.string.notifications_empty_body)
                    } else {
                        val filterLabel = stringResource(
                            when (selectedFilter) {
                                NotificationFilter.All -> R.string.notifications_filter_all
                                NotificationFilter.Messages -> R.string.notifications_filter_messages
                                NotificationFilter.Updates -> R.string.notifications_filter_updates
                                NotificationFilter.Announcements -> R.string.notifications_filter_announcements
                            },
                        )
                        stringResource(R.string.notifications_empty_filter_body, filterLabel.lowercase())
                    }
                    ZunoEmptyState(
                        icon = R.drawable.zuno_ic_t_bell,
                        title = stringResource(R.string.notifications_empty_title),
                        description = emptyBody,
                    )
                    ZunoButton(
                        label = stringResource(R.string.notifications_settings_cta),
                        onClick = onOpenNotificationSettings,
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Medium,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = zunoLayout.screenPaddingH,
                        end = zunoLayout.screenPaddingH,
                        top = ZunoSpace.s6,
                        bottom = ZunoSpace.s48,
                    ),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
            ) {
                if (todayItems.isNotEmpty()) {
                    NotificationSectionBlock(
                        title = stringResource(R.string.notifications_today),
                        items = todayItems,
                        onItemClick = onItemClick,
                    )
                }
                if (earlierItems.isNotEmpty()) {
                    NotificationSectionBlock(
                        title = stringResource(R.string.notifications_earlier),
                        items = earlierItems,
                        onItemClick = onItemClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pillModifier = if (selected) {
        Modifier.zunoGlassElevated(
            shape = CircleShape,
            fillColor = ZunoColors.SurfaceControlActive,
            borderBrush = SolidColor(ZunoColors.BorderControlActive),
        )
    } else {
        Modifier.zunoGlassSubtle(
            shape = CircleShape,
            fillColor = ZunoColors.SurfaceControlTrack,
        )
    }
    Box(
        modifier = pillModifier
            .height(34.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = zunoLayout.chipPaddingH),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ZunoType.LabelSmall,
            color = if (selected) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun NotificationSectionBlock(
    title: String,
    items: List<SystemNotificationItem>,
    onItemClick: (SystemNotificationItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
        ZunoSectionHeader(title = title)
        ZunoListGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) ZunoGlassHairline()
                if (item.kind == SystemNotificationKind.AppUpdate) {
                    AppUpdateHeroNotificationCard(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                } else {
                    CompactNotificationRow(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUpdateHeroNotificationCard(
    item: SystemNotificationItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s10, end = ZunoSpace.s10, top = ZunoSpace.s10, bottom = ZunoSpace.s12),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        // Featured Zuno gradient banner
        val bannerShape = RoundedCornerShape(ZunoRadius.lg)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(bannerShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ZunoColors.NotificationsBannerStart,
                            ZunoColors.NotificationsBannerMid,
                            ZunoColors.NotificationsBannerEnd,
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-32).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ZunoColors.NotificationsBannerGlow,
                                Color.Transparent,
                            ),
                            center = Offset.Unspecified,
                        ),
                    ),
            )
            ZunoIcon(
                icon = R.drawable.zuno_logo_mark,
                size = 20.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(ZunoSpace.s12),
            )
            item.badgeText?.let { badge ->
                Text(
                    text = badge,
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ZunoSpace.s12),
                )
            }
            item.pillText?.let { pill ->
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zunoGlassMediaPill(CircleShape)
                        .padding(horizontal = ZunoSpace.s14, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_t_sparkles, 15.dp, tint = ZunoColors.IconPrimary)
                    Text(
                        text = pill,
                        style = ZunoType.LabelSmall,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s4),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
        ) {
            Text(
                text = item.title,
                style = ZunoType.Headline,
                color = ZunoColors.TextPrimary,
            )
            Text(
                text = item.preview,
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(ZunoSpace.s2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.unread) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ZunoColors.StatusSpeaking),
                        )
                    }
                    Text(
                        text = item.meta,
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.actionLabel ?: stringResource(R.string.notifications_update_cta),
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextPrimary,
                    )
                    ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 14.dp, tint = ZunoColors.IconPrimary)
                }
            }
        }
    }
}

@Composable
private fun CompactNotificationRow(
    item: SystemNotificationItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.unread) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ZunoColors.StatusSpeaking),
                    )
                }
                Text(
                    text = item.title,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.preview,
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.meta,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
            )
        }

        when {
            item.kind == SystemNotificationKind.Announcement && !item.badgeText.isNullOrBlank() -> {
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 52.dp)
                        .clip(RoundedCornerShape(ZunoRadius.sm))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ZunoColors.NotificationsThumbStart, ZunoColors.NotificationsThumbEnd),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.badgeText,
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
            item.avatarInitial != null || item.avatarUrl != null -> {
                ZunoAvatar(
                    initial = item.avatarInitial ?: "Z",
                    imageUrl = item.avatarUrl,
                    size = 42.dp,
                    textStyle = ZunoType.LabelMedium,
                )
            }
            else -> {
                @DrawableRes val iconRes = when (item.kind) {
                    SystemNotificationKind.StreakMilestone -> R.drawable.zuno_ic_flame
                    SystemNotificationKind.ScheduledSession -> R.drawable.zuno_ic_calendar
                    else -> R.drawable.zuno_ic_t_sparkles
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(ZunoRadius.md))
                        .background(ZunoColors.SurfaceStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(iconRes, 20.dp, tint = ZunoColors.IconPrimary)
                }
            }
        }
    }
}
