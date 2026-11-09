package com.talkie.ai.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.data.RecentSession
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoButtonSize
import com.talkie.ai.ui.components.ZunoButtonVariant
import com.talkie.ai.ui.components.ZunoIconButton
import com.talkie.ai.ui.components.ZunoMenuItem
import com.talkie.ai.ui.components.ZunoProfileCard
import com.talkie.ai.ui.components.ZunoSectionHeader
import com.talkie.ai.ui.components.ZunoSessionRow
import com.talkie.ai.ui.components.ZunoUpgradeBanner
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

enum class DrawerDestination(@param:StringRes val labelRes: Int, val label: String, val icon: Int) {
    FreeTalk(R.string.drawer_free_talk, "Free Talk", R.drawable.zuno_ic_composer_voice),
    Scheduled(R.string.drawer_scheduled, "Scheduled", R.drawable.zuno_ic_calendar),
    Topics(R.string.drawer_topics, "Topics", R.drawable.zuno_ic_book_open),
    Roleplays(R.string.drawer_roleplays, "Roleplays", R.drawable.zuno_ic_users),
    Progress(R.string.drawer_progress, "Progress", R.drawable.zuno_ic_trending_up),
    Vocabulary(R.string.drawer_vocabulary, "Vocabulary", R.drawable.zuno_ic_bookmark),
}

private val DrawerShape = RoundedCornerShape(topEnd = ZunoRadius.xxl, bottomEnd = ZunoRadius.xxl)

/** 5b · Drawer — profile, navigation, recent sessions, upsell and footer actions. */
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
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(340.dp)
            .clip(DrawerShape)
            .background(ZunoColors.BgElevated)
            .drawWithContent {
                drawContent()
                // Right-edge hairline (border-r) following the rounded corners.
                val inset = ZunoRadius.xxl.toPx() + 1.dp.toPx()
                clipRect(left = size.width - inset) {
                    drawOutline(
                        outline = DrawerShape.createOutline(size, layoutDirection, this),
                        color = ZunoColors.BorderSubtle,
                        style = Stroke(width = 1.dp.toPx() * 2),
                    )
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s12, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoProfileCard(
                name = userName,
                detail = userDetail,
                imageUrl = userImage,
                onClick = onProfile,
                modifier = Modifier.weight(1f),
            )
            ZunoIconButton(R.drawable.zuno_ic_bell, onNotifications, contentDescription = stringResource(R.string.settings_notifications))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
        ) {
            DrawerDestination.entries.forEach { destination ->
                ZunoMenuItem(
                    label = stringResource(destination.labelRes),
                    icon = destination.icon,
                    active = destination == activeDestination,
                    onClick = { onDestination(destination) },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            val pinned = sessions.filter { it.pinned }
            val recent = sessions.filterNot { it.pinned }
            if (pinned.isNotEmpty()) {
                Box(Modifier.padding(start = ZunoSpace.s12)) {
                    ZunoSectionHeader(title = stringResource(R.string.drawer_pinned))
                }
                pinned.forEach { session ->
                    ZunoSessionRow(
                        title = session.title,
                        meta = session.meta,
                        selected = session.id == selectedSessionId,
                        pinned = true,
                        onClick = { onSession(session) },
                    )
                }
                Box(Modifier.height(ZunoSpace.s8))
            }
            Box(Modifier.padding(start = ZunoSpace.s12)) {
                ZunoSectionHeader(
                    title = stringResource(R.string.drawer_recent_sessions),
                    actionIcon = if (recent.isNotEmpty()) R.drawable.zuno_ic_search else null,
                    onAction = onSearchSessions,
                    actionDescription = stringResource(R.string.drawer_search_sessions),
                )
            }
            if (recent.isEmpty()) {
                Text(
                    stringResource(R.string.drawer_recent_empty),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                )
            }
            recent.forEach { session ->
                ZunoSessionRow(
                    title = session.title,
                    meta = session.meta,
                    selected = session.id == selectedSessionId,
                    onClick = { onSession(session) },
                )
            }
        }

        if (showUpgrade) Box(Modifier.padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12)) {
            ZunoUpgradeBanner(
                onClick = onUpgrade,
                text = stringResource(R.string.drawer_upgrade_banner),
                action = stringResource(R.string.drawer_upgrade_action),
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(ZunoColors.BorderSubtle))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s8, end = ZunoSpace.s16, top = ZunoSpace.s12, bottom = ZunoSpace.s4),
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
