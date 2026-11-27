package com.zuno.ai.ui.screens

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.RecentSession
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoMenuItem
import com.zuno.ai.ui.components.ZunoProfileCard
import com.zuno.ai.ui.components.ZunoSessionRow
import com.zuno.ai.ui.components.ZunoUpgradeBanner
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoGlassOverlay
import com.zuno.ai.ui.theme.zunoGlassSubtle

enum class DrawerDestination(@param:StringRes val labelRes: Int, val label: String, val icon: Int) {
    FreeTalk(R.string.drawer_free_talk, "Free Talk", R.drawable.zuno_ic_t_audio_lines),
    Topics(R.string.drawer_topics, "Topics", R.drawable.zuno_ic_t_book_open),
    Roleplays(R.string.drawer_roleplays, "Roleplays", R.drawable.zuno_ic_t_sparkles),
    Vocabulary(R.string.drawer_vocabulary, "Vocabulary", R.drawable.zuno_ic_bookmark),
    Scheduled(R.string.drawer_scheduled, "Scheduled", R.drawable.zuno_ic_t_calendar),
    Chats(R.string.chats_title, "Chats", R.drawable.zuno_ic_messages_square),
    LiveRooms(R.string.home_explore_live_rooms, "Live rooms", R.drawable.zuno_ic_radio),
    Community(R.string.home_explore_community, "Community & Partners", R.drawable.zuno_ic_t_users),
    Progress(R.string.drawer_progress, "Progress", R.drawable.zuno_ic_trending_up),
}

private val DrawerShape = RoundedCornerShape(topEnd = ZunoRadius.xxxxxl, bottomEnd = ZunoRadius.xxxxxl)

/**
 * `Home · 5 · Sidebar Drawer` (`3077:14447`):
 * Glass overlay panel with Profile + Bell header, Practice / Live & social / Recent sessions groups,
 * Pro upgrade banner, and Settings / + New chat footer.
 */
@Composable
fun AppDrawer(
    userName: String,
    userDetail: String,
    userImage: String?,
    showUpgrade: Boolean,
    activeDestination: DrawerDestination?,
    sessions: List<RecentSession>,
    selectedSessionId: String?,
    onProfile: () -> Unit,
    onNotifications: () -> Unit,
    onDestination: (DrawerDestination) -> Unit,
    onSearchSessions: () -> Unit,
    onSession: (RecentSession) -> Unit,
    onUpgrade: () -> Unit,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    vocabDueCount: Int = 0,
    scheduledTimeLabel: String? = null,
    unreadChatsCount: Int = 0,
    unreadNotificationsCount: Int = 0,
    activeLiveRoomsCount: Int = 0,
    partnersOnlineCount: Int = 0,
    leagueRank: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(340.dp)
            .zunoGlassOverlay(DrawerShape, shadowElevation = 14.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Scrollable header + navigation sections + recent sessions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s4),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            // Header: Profile pill + Bell circle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ZunoSpace.s2, top = ZunoSpace.s4, bottom = ZunoSpace.s10),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoProfileCard(
                    name = userName.ifBlank { "Zuno Learner" },
                    detail = userDetail,
                    imageUrl = userImage,
                    onClick = onProfile,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .zunoGlassSubtle(CircleShape, borderBrush = null)
                            .clickable(role = Role.Button, onClick = onNotifications)
                            .semantics { contentDescription = "Notifications" },
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoIcon(R.drawable.zuno_ic_bell, 18.dp, tint = ZunoColors.IconPrimary)
                    }
                    if (unreadNotificationsCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 1.dp, y = (-1).dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(ZunoColors.StatusSpeaking)
                                .border(2.dp, ZunoColors.BgElevated, CircleShape),
                        )
                    }
                }
            }

            DrawerSectionEyebrow("Practice")
            ZunoMenuItem(
                label = stringResource(DrawerDestination.FreeTalk.labelRes),
                icon = DrawerDestination.FreeTalk.icon,
                active = activeDestination == DrawerDestination.FreeTalk,
                onClick = { onDestination(DrawerDestination.FreeTalk) },
            )
            ZunoMenuItem(
                label = stringResource(DrawerDestination.Topics.labelRes),
                icon = DrawerDestination.Topics.icon,
                active = activeDestination == DrawerDestination.Topics,
                onClick = { onDestination(DrawerDestination.Topics) },
            )
            ZunoMenuItem(
                label = stringResource(DrawerDestination.Roleplays.labelRes),
                icon = DrawerDestination.Roleplays.icon,
                active = activeDestination == DrawerDestination.Roleplays,
                onClick = { onDestination(DrawerDestination.Roleplays) },
            )
            ZunoMenuItem(
                label = stringResource(DrawerDestination.Vocabulary.labelRes),
                icon = DrawerDestination.Vocabulary.icon,
                active = activeDestination == DrawerDestination.Vocabulary,
                trailing = if (vocabDueCount > 0) {
                    {
                        Text(
                            text = "$vocabDueCount due",
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            maxLines = 1,
                        )
                    }
                } else {
                    null
                },
                onClick = { onDestination(DrawerDestination.Vocabulary) },
            )
            ZunoMenuItem(
                label = stringResource(DrawerDestination.Scheduled.labelRes),
                icon = DrawerDestination.Scheduled.icon,
                active = activeDestination == DrawerDestination.Scheduled,
                trailing = scheduledTimeLabel?.let { time ->
                    {
                        Text(
                            text = time,
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            maxLines = 1,
                        )
                    }
                },
                onClick = { onDestination(DrawerDestination.Scheduled) },
            )

            DrawerSectionEyebrow("Live & social")
            ZunoMenuItem(
                label = stringResource(DrawerDestination.Chats.labelRes),
                icon = DrawerDestination.Chats.icon,
                active = activeDestination == DrawerDestination.Chats,
                trailing = if (unreadChatsCount > 0) {
                    {
                        Text(
                            text = stringResource(R.string.drawer_unread_count, unreadChatsCount),
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            maxLines = 1,
                        )
                    }
                } else {
                    null
                },
                onClick = { onDestination(DrawerDestination.Chats) },
            )
            ZunoMenuItem(
                label = stringResource(DrawerDestination.LiveRooms.labelRes),
                icon = DrawerDestination.LiveRooms.icon,
                active = activeDestination == DrawerDestination.LiveRooms,
                trailing = if (activeLiveRoomsCount > 0) {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ZunoColors.StatusSpeaking),
                            )
                            Text(
                                text = "$activeLiveRoomsCount active",
                                style = ZunoType.Micro,
                                color = ZunoColors.TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                } else {
                    null
                },
                onClick = { onDestination(DrawerDestination.LiveRooms) },
            )
            ZunoMenuItem(
                label = "Community & Partners",
                icon = DrawerDestination.Community.icon,
                active = activeDestination == DrawerDestination.Community,
                trailing = if (partnersOnlineCount > 0) {
                    {
                        Text(
                            text = "$partnersOnlineCount online",
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            maxLines = 1,
                        )
                    }
                } else {
                    null
                },
                onClick = { onDestination(DrawerDestination.Community) },
            )
            ZunoMenuItem(
                label = stringResource(DrawerDestination.Progress.labelRes),
                icon = DrawerDestination.Progress.icon,
                active = activeDestination == DrawerDestination.Progress,
                trailing = leagueRank?.let { rank ->
                    {
                        Text(
                            text = "#$rank",
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            maxLines = 1,
                        )
                    }
                },
                onClick = { onDestination(DrawerDestination.Progress) },
            )

            DrawerSectionEyebrow(
                title = stringResource(R.string.drawer_recent_sessions),
                trailingAction = {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onSearchSessions)
                            .semantics { contentDescription = "Search chats and sessions" },
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoIcon(R.drawable.zuno_ic_t_search, 15.dp, tint = ZunoColors.IconTertiary)
                    }
                },
            )

            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.drawer_recent_empty),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                )
            } else {
                val sorted = sessions.sortedByDescending { it.pinned }
                sorted.take(8).forEachIndexed { index, session ->
                    val isSelected = session.id == selectedSessionId || (selectedSessionId == null && index == 0 && session.pinned)
                    ZunoSessionRow(
                        title = session.title,
                        meta = session.meta,
                        selected = isSelected,
                        pinned = session.pinned,
                        unread = session.unread,
                        onClick = { onSession(session) },
                    )
                }
            }
            Spacer(Modifier.height(ZunoSpace.s8))
        }

        // Upgrade banner
        if (showUpgrade) {
            Box(Modifier.padding(start = ZunoSpace.s14, end = ZunoSpace.s14, bottom = ZunoSpace.s10)) {
                ZunoUpgradeBanner(
                    onClick = onUpgrade,
                    text = stringResource(R.string.drawer_upgrade_banner),
                    action = stringResource(R.string.drawer_upgrade_action),
                )
            }
        }

        ZunoGlassHairline()

        // Footer: Settings + New chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s14, end = ZunoSpace.s14, top = ZunoSpace.s10, bottom = ZunoSpace.s8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoButton(
                label = stringResource(R.string.drawer_settings),
                onClick = onSettings,
                variant = ZunoButtonVariant.Ghost,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_settings,
            )
            ZunoButton(
                label = stringResource(R.string.drawer_new_chat),
                onClick = onNewChat,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_plus_inverse,
            )
        }
    }
}

@Composable
private fun DrawerSectionEyebrow(
    title: String,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ZunoSpace.s12, end = ZunoSpace.s4, top = ZunoSpace.s10, bottom = ZunoSpace.s4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ZunoType.FootnoteStrong,
            color = ZunoColors.TextTertiary,
        )
        trailingAction?.invoke()
    }
}
