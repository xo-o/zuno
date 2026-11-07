package com.talkie.ai.ui.zuno.screens

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
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.data.RecentSession
import com.talkie.ai.ui.zuno.components.ZunoButton
import com.talkie.ai.ui.zuno.components.ZunoButtonSize
import com.talkie.ai.ui.zuno.components.ZunoButtonVariant
import com.talkie.ai.ui.zuno.components.ZunoIconButton
import com.talkie.ai.ui.zuno.components.ZunoMenuItem
import com.talkie.ai.ui.zuno.components.ZunoProfileCard
import com.talkie.ai.ui.zuno.components.ZunoSectionHeader
import com.talkie.ai.ui.zuno.components.ZunoSessionRow
import com.talkie.ai.ui.zuno.components.ZunoUpgradeBanner
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoRadius
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType

enum class DrawerDestination(val label: String, val icon: Int) {
    FreeTalk("Free Talk", R.drawable.zuno_ic_composer_voice),
    Topics("Topics", R.drawable.zuno_ic_book_open),
    Roleplays("Roleplays", R.drawable.zuno_ic_users),
    Progress("Progress", R.drawable.zuno_ic_trending_up),
    Vocabulary("Vocabulary", R.drawable.zuno_ic_bookmark),
}

private val DrawerShape = RoundedCornerShape(topEnd = ZunoRadius.xxl, bottomEnd = ZunoRadius.xxl)

/** 5b · Drawer — profile, navigation, recent sessions, upsell and footer actions. */
@Composable
fun AppDrawer(
    userName: String,
    userDetail: String,
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
                onClick = onProfile,
                modifier = Modifier.weight(1f),
            )
            ZunoIconButton(R.drawable.zuno_ic_bell, onNotifications, contentDescription = "Notifications")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
        ) {
            DrawerDestination.entries.forEach { destination ->
                ZunoMenuItem(
                    label = destination.label,
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
            Box(Modifier.padding(start = ZunoSpace.s12)) {
                ZunoSectionHeader(
                    title = "Recent sessions",
                    actionIcon = if (sessions.isNotEmpty()) R.drawable.zuno_ic_search else null,
                    onAction = onSearchSessions,
                    actionDescription = "Search sessions",
                )
            }
            if (sessions.isEmpty()) {
                Text(
                    "Your conversations, topics and roleplays will show up here.",
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                )
            }
            sessions.forEach { session ->
                ZunoSessionRow(
                    title = session.title,
                    meta = session.meta,
                    selected = session.id == selectedSessionId,
                    onClick = { onSession(session) },
                )
            }
        }

        Box(Modifier.padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12)) {
            ZunoUpgradeBanner(onClick = onUpgrade)
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
                label = "Settings",
                onClick = onSettings,
                variant = ZunoButtonVariant.Ghost,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_settings,
            )
            ZunoButton(
                label = "New chat",
                onClick = onNewChat,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_plus_inverse,
            )
        }
    }
}
