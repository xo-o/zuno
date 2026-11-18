package com.zuno.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import kotlin.math.roundToInt
import kotlin.math.sin

data class ContinueSessionUi(
    val conversationId: String,
    val title: String,
    val subtitle: String,
)

enum class HomeNavTab { Home, Learn, Zuno, Chats, More }

/** Top Bar · Home (`2644:3247`): Title/Large greeting on left, streak pill + 28dp user avatar on right. */
@Composable
fun ZunoHomeTopBar(
    greeting: String,
    streakDays: Int,
    userInitial: String,
    userImage: String?,
    onStreakClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s8),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = greeting,
            style = ZunoType.TitleLarge,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = ZunoSpace.s12),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceSubtle)
                    .border(1.dp, ZunoColors.BorderSubtle, CircleShape)
                    .clickable(role = Role.Button, onClick = onStreakClick)
                    .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoIcon(R.drawable.zuno_ic_flame, 16.dp, tint = ZunoColors.IconPrimary)
                Text(
                    text = streakDays.toString(),
                    style = ZunoType.LabelMedium,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                )
            }
            ZunoAvatar(
                initial = userInitial,
                imageUrl = userImage,
                size = 28.dp,
                textStyle = ZunoType.FootnoteStrong,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAvatarClick)
                    .semantics { contentDescription = "Account and settings" },
            )
        }
    }
}

/** Top Bar · Explore (`2653:3340` + `2831:14674`): Title/Large "Explore" on left, 32dp settings icon button on right. */
@Composable
fun ZunoExploreTopBar(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = ZunoSpace.s24),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_explore_screen_title),
            style = ZunoType.TitleLarge,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ZunoIconButton(
            icon = R.drawable.zuno_ic_settings,
            onClick = onSettingsClick,
            style = ZunoIconButtonStyle.Subtle,
            size = ZunoIconButtonSize.Small,
            contentDescription = stringResource(R.string.home_explore_settings),
        )
    }
}

/** Daily Target Row (`2826:13992`): compact icon badge, "Daily target · X / Y min", 56dp progress bar + %. */
@Composable
fun ZunoDailyTargetRow(
    minutesDone: Int,
    goalMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeGoal = goalMinutes.coerceAtLeast(1)
    val progress = (minutesDone.toFloat() / safeGoal).coerceIn(0f, 1f)
    val percent = (progress * 100f).roundToInt().coerceIn(0, 100)
    val shape = RoundedCornerShape(ZunoRadius.xxl)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = ZunoSpace.s12),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(ZunoRadius.md))
                    .background(ZunoColors.SurfaceStrong),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_zap, 16.dp, tint = ZunoColors.IconPrimary)
            }
            Text(
                text = stringResource(R.string.home_daily_target_summary, minutesDone, safeGoal),
                style = ZunoType.LabelSmall,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceStrong),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceInverse),
                )
            }
            Text(
                text = stringResource(R.string.home_percent, percent),
                style = ZunoType.Micro,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

/** Pick up where you left off (`2826:14001`): header + Continue card (`2826:14004`) with Resume pill CTA. */
@Composable
fun ZunoPickUpSection(
    session: ContinueSessionUi?,
    languageName: String,
    onResume: (String) -> Unit,
    onStartNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(ZunoRadius.xxxl)
    val title = session?.title ?: stringResource(R.string.home_free_talk_title)
    val subtitle = session?.subtitle ?: stringResource(R.string.home_free_talk_sub, languageName)
    val ctaLabel = stringResource(if (session != null) R.string.home_resume else R.string.home_start)
    val action = { if (session != null) onResume(session.conversationId) else onStartNew() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        Text(
            text = stringResource(if (session != null) R.string.home_pick_up_title else R.string.home_start_practicing),
            style = ZunoType.LabelSmall,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(ZunoColors.SurfaceDefault)
                .border(1.dp, ZunoColors.BorderSubtle, cardShape)
                .clickable(role = Role.Button, onClick = action)
                .padding(ZunoSpace.s16),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(ZunoRadius.md))
                    .background(ZunoColors.SurfaceInverse),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_sparkles, 16.dp, tint = ZunoColors.IconOnInverse)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
            ) {
                Text(
                    text = title,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ZunoButton(
                label = ctaLabel,
                onClick = action,
                size = ZunoButtonSize.Small,
            )
        }
    }
}

/** Live now (`2826:14012`): header + grouped 2-row card (`2826:14014`) for live rooms and practice partners. */
@Composable
fun ZunoLiveNowSection(
    onLiveRoomClick: () -> Unit,
    onPracticePartnerClick: () -> Unit,
    modifier: Modifier = Modifier,
    partnersOnlineCount: Int = 12,
) {
    val cardShape = RoundedCornerShape(ZunoRadius.xxxl)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        Text(
            text = stringResource(R.string.home_live_now),
            style = ZunoType.LabelSmall,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(ZunoColors.SurfaceSubtle)
                .border(1.dp, ZunoColors.BorderSubtle, cardShape)
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s4),
        ) {
            LiveNowRow(
                icon = R.drawable.zuno_ic_radio,
                title = stringResource(R.string.home_live_room_sample_title),
                subtitle = stringResource(R.string.home_live_room_sample_sub),
                onClick = onLiveRoomClick,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ZunoColors.Divider),
            )
            LiveNowRow(
                icon = R.drawable.zuno_ic_t_users,
                title = stringResource(R.string.home_partner_row_title),
                subtitle = stringResource(R.string.home_partner_row_sub, partnersOnlineCount),
                onClick = onPracticePartnerClick,
            )
        }
    }
}

@Composable
private fun LiveNowRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(ZunoRadius.md))
                .background(ZunoColors.SurfaceStrong),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 16.dp, tint = ZunoColors.IconPrimary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Text(
                text = title,
                style = ZunoType.BodyMediumStrong,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 18.dp, tint = ZunoColors.IconTertiary)
    }
}

/** Stat card in the 3-column Explore row (`2831:14678`). */
@Composable
fun ZunoExploreStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(
        modifier = modifier
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .padding(ZunoSpace.s12),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
    ) {
        Text(
            text = value,
            style = ZunoType.Headline,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Practice & Learn grouped row (`2102:1645` Sheet Row). */
@Composable
fun ZunoExploreSheetRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingValue: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ZunoRadius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 18.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Text(
                text = title,
                style = ZunoType.BodyMedium,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailingValue.isNullOrBlank()) {
            Text(
                text = trailingValue,
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
            )
        }
        ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 18.dp, tint = ZunoColors.IconTertiary)
    }
}

/** Social & Milestones 3-column tile (`2102:1620` Attach Tile). */
@Composable
fun ZunoExploreMilestoneTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(
        modifier = modifier
            .clip(shape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s8, end = ZunoSpace.s8, top = ZunoSpace.s20, bottom = ZunoSpace.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10, Alignment.CenterVertically),
    ) {
        ZunoIcon(icon, 24.dp)
        Text(
            text = label,
            style = ZunoType.LabelMedium,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Floating 5-tab dot-card navigation dock (`2644:3256`). */
@Composable
fun ZunoFloatingBottomNav(
    activeTab: HomeNavTab,
    onTabSelected: (HomeNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockShape = RoundedCornerShape(ZunoRadius.xxl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s24)
            .height(64.dp)
            .shadow(16.dp, dockShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(dockShape)
            .background(ZunoColors.BgElevated)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, dockShape)
            .padding(horizontal = ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavTabItem(
            icon = R.drawable.zuno_ic_home,
            label = stringResource(R.string.home_nav_home),
            selected = activeTab == HomeNavTab.Home,
            onClick = { onTabSelected(HomeNavTab.Home) },
            modifier = Modifier.weight(1f),
        )
        BottomNavTabItem(
            icon = R.drawable.zuno_ic_book_open,
            label = stringResource(R.string.home_nav_learn),
            selected = activeTab == HomeNavTab.Learn,
            onClick = { onTabSelected(HomeNavTab.Learn) },
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(role = Role.Tab, onClick = { onTabSelected(HomeNavTab.Zuno) })
                .semantics { contentDescription = "Start voice call with Zuno" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(ZunoRadius.xl))
                    .background(ZunoColors.SurfaceInverse),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_sparkles, 22.dp, tint = ZunoColors.IconOnInverse)
            }
        }
        BottomNavTabItem(
            icon = R.drawable.zuno_ic_messages_square,
            label = stringResource(R.string.home_nav_chats),
            selected = activeTab == HomeNavTab.Chats,
            onClick = { onTabSelected(HomeNavTab.Chats) },
            modifier = Modifier.weight(1f),
        )
        BottomNavTabItem(
            icon = R.drawable.zuno_ic_layout_grid,
            label = stringResource(R.string.home_nav_more),
            selected = activeTab == HomeNavTab.More,
            onClick = { onTabSelected(HomeNavTab.More) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomNavTabItem(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(ZunoRadius.md))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6, Alignment.CenterVertically),
    ) {
        ZunoIcon(
            icon = icon,
            size = 24.dp,
            tint = if (selected) ZunoColors.IconPrimary else ZunoColors.IconTertiary,
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (selected) ZunoColors.SurfaceInverse else Color.Transparent),
        )
    }
}

private class WaveformMotion {
    var phase by mutableFloatStateOf(0f)
    var smoothed by mutableFloatStateOf(0.15f)
}

/** Compact 20-bar animated voice waveform (`30:639`) for Tactile HUD · S5, drawn in Canvas with zero recomposition. */
@Composable
fun ZunoVoiceWaveform(
    active: Boolean,
    audioLevel: () -> Float,
    modifier: Modifier = Modifier,
) {
    val currentLevel by rememberUpdatedState(audioLevel)
    val currentActive by rememberUpdatedState(active)
    val motion = remember { WaveformMotion() }

    LaunchedEffect(motion) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                val target = if (currentActive) maxOf(currentLevel().coerceIn(0f, 1f), 0.25f) else 0.12f
                motion.smoothed += (target - motion.smoothed) * (dt * 12f).coerceIn(0f, 1f)
                motion.phase += dt * (if (currentActive) 7.5f else 2.0f)
            }
        }
    }

    // Normalized heights matching Figma Voice Waveform (`30:639`), trailing 5 fade out.
    val baseProfile = remember {
        floatArrayOf(
            0.18f, 0.34f, 0.50f, 0.75f, 0.92f, 0.66f, 0.42f, 0.58f, 0.84f, 1.00f,
            0.75f, 0.50f, 0.34f, 0.58f, 0.84f, 0.66f, 0.42f, 0.25f, 0.18f, 0.18f,
        )
    }
    val barColor = ZunoColors.IconPrimary

    Canvas(modifier = modifier.height(14.dp)) {
        val count = baseProfile.size
        val barWidth = 2.dp.toPx()
        val gap = if (count > 1) ((size.width - count * barWidth) / (count - 1)).coerceAtLeast(1.dp.toPx()) else 0f
        val radius = barWidth / 2f
        val p = motion.phase
        val s = motion.smoothed
        val isActive = currentActive
        for (i in 0 until count) {
            val wave = if (isActive) {
                0.55f + 0.45f * sin(p + i * 0.55f)
            } else {
                0.35f + 0.15f * sin(p + i * 0.4f)
            }
            val fraction = (baseProfile[i] * (0.35f + 0.85f * s) * wave).coerceIn(0.18f, 1f)
            val barHeight = size.height * fraction
            val x = i * (barWidth + gap)
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radius, radius),
                alpha = if (i >= 15) 0.4f else 1f,
            )
        }
    }
}
