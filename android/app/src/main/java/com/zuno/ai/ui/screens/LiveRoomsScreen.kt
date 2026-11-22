package com.zuno.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSegmentedTabs
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle

private data class LiveRoomUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val isLive: Boolean,
    val kind: String, // "zuno", "public"
)

private val DefaultLiveRooms = listOf(
    LiveRoomUi("r1", "Remote work vs. office life", "Zuno · B2 · 2/4 live", true, "zuno"),
    LiveRoomUi("r2", "Favorite street food & markets", "Lucía M. · B1 · Waiting 2/4", false, "public"),
    LiveRoomUi("r3", "Weekend travel stories", "Diego R. · A2 · Waiting 1/6", false, "public"),
)

/**
 * `15 · Live Rooms` (`3091:14069` Live Rooms · 1 · Browse).
 */
@Composable
fun LiveRoomsScreen(
    languageName: String,
    level: String,
    onBack: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val q = query.trim().lowercase()
    val filteredRooms = DefaultLiveRooms.filter { room ->
        val matchesTab = when (selectedTab) {
            1 -> room.kind == "zuno"
            2 -> room.kind == "public"
            else -> true
        }
        val matchesQuery = q.isEmpty() || room.title.lowercase().contains(q) || room.subtitle.lowercase().contains(q)
        matchesTab && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoSectionTopBar(
            title = "Live rooms",
            onBack = onBack,
            showSearch = true,
            searchActive = searchOpen,
            onSearchToggle = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
            showAdd = true,
            onAdd = onCreateRoom,
            addDescription = "Create live room",
        )

        Box(Modifier.padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s6)) {
            ZunoSegmentedTabs(
                labels = listOf("All", "Zuno", "Public", "Code"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = ZunoSpace.s32),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            if (searchOpen) {
                item(key = "search") {
                    ExploreSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search live rooms…",
                        autoFocus = true,
                    )
                }
            }

            if (selectedTab != 3) {
                item(key = "featured") {
                    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                        Text("Live now · Featured", style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(
                                    RoundedCornerShape(ZunoRadius.xxl),
                                    fillColor = ZunoColors.SurfaceDefault,
                                    borderBrush = SolidColor(ZunoColors.BorderStrong),
                                )
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ZunoColors.SurfaceStrong)
                                        .padding(horizontal = ZunoSpace.s10, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(ZunoColors.StatusSpeaking),
                                    )
                                    Text("Live · 3/4 seats", style = ZunoType.FootnoteStrong, color = ZunoColors.TextPrimary)
                                }
                                Text("$languageName · $level", style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                                Text(
                                    text = "Café culture & morning routines",
                                    style = ZunoType.TitleMedium,
                                    color = ZunoColors.TextPrimary,
                                )
                                Text(
                                    text = "Guided by Zuno · Drop in anytime",
                                    style = ZunoType.CaptionRegular,
                                    color = ZunoColors.TextSecondary,
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.width(64.dp)) {
                                    SmallRoomAvatar("S", inverse = false, modifier = Modifier.offset(x = 36.dp))
                                    SmallRoomAvatar("M", inverse = false, modifier = Modifier.offset(x = 18.dp))
                                    SmallRoomAvatar("Z", inverse = true, modifier = Modifier.offset(x = 0.dp))
                                }
                                Text(
                                    text = "Zuno, Mateo & Sofía speaking",
                                    style = ZunoType.FootnoteRegular,
                                    color = ZunoColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            ZunoButton(
                                label = "Join live room",
                                onClick = { onJoinRoom("Café culture & morning routines") },
                                size = ZunoButtonSize.Large,
                                modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s2),
                            )
                        }
                    }
                }
            }

            item(key = "more-rooms") {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                    Text(
                        text = if (selectedTab == 3) "Private room code" else "More rooms",
                        style = ZunoType.LabelSmall,
                        color = ZunoColors.TextSecondary,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                    ) {
                        filteredRooms.forEachIndexed { idx, room ->
                            if (idx > 0) ZunoGlassHairline()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClick = { onJoinRoom(room.title) })
                                    .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                                ) {
                                    Text(
                                        text = room.title,
                                        style = ZunoType.BodyMediumStrong,
                                        color = ZunoColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (room.isLive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(ZunoColors.StatusSpeaking),
                                            )
                                        }
                                        Text(
                                            text = room.subtitle,
                                            style = ZunoType.FootnoteRegular,
                                            color = ZunoColors.TextTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                ZunoButton(
                                    label = "Join",
                                    onClick = { onJoinRoom(room.title) },
                                    variant = ZunoButtonVariant.Secondary,
                                    size = ZunoButtonSize.Small,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallRoomAvatar(initial: String, inverse: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .then(
                if (inverse) Modifier.clip(CircleShape).background(ZunoColors.SurfaceInverse)
                else Modifier.zunoGlassElevated(CircleShape),
            )
            .border(1.5.dp, ZunoColors.BgCanvas, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = ZunoType.Micro,
            color = if (inverse) ZunoColors.TextOnInverse else ZunoColors.TextPrimary,
        )
    }
}
