package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Levels
import com.zuno.ai.data.LiveRoom
import com.zuno.ai.data.LiveRoomMember
import com.zuno.ai.data.LiveRoomsBrowse
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.components.CallAvatarItem
import com.zuno.ai.ui.components.LiveKitVideoFeed
import com.zuno.ai.ui.components.TurnPillState
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoAvatar
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoCallControlsBar
import com.zuno.ai.ui.components.ZunoCallTopBar
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoDivider
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSegmentedTabs
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.components.ZunoTextField
import com.zuno.ai.ui.components.ZunoTurnPillRow
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.components.ZunoVoiceWaveform
import com.zuno.ai.ui.components.rememberTurnPillState
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
import com.zuno.ai.viewmodel.Loadable
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay

/**
 * `15 · Live Rooms` (`3091:14069` Browse & `2704:12642` Private Code Sheet).
 */
@Composable
fun LiveRoomsScreen(
    languageName: String,
    level: String,
    onBack: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    browse: Loadable<LiveRoomsBrowse> = Loadable(),
    onRetry: () -> Unit = {},
    busy: Boolean = false,
    onOpenRoom: (LiveRoom) -> Unit = { onJoinRoom(it.title) },
    onJoinByCode: (String) -> Unit = {},
    onCreatePrivateRoom: () -> Unit = onCreateRoom,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCodeSheet by rememberSaveable { mutableStateOf(false) }

    val browseData = browse.items
    val featured = browseData?.featured
    val allMoreRooms = browseData?.rooms.orEmpty()

    val q = query.trim().lowercase()
    val featuredToShow = featured?.takeIf { selectedTab != 2 && selectedTab != 3 && (q.isEmpty() || it.title.lowercase().contains(q)) }
    val filteredRooms = allMoreRooms.filter { room ->
        val matchesTab = when (selectedTab) {
            1 -> room.isSystem
            2 -> !room.isSystem && room.isPublic
            else -> true
        }
        val matchesQuery = q.isEmpty() || room.title.lowercase().contains(q) || room.hostName.lowercase().contains(q)
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
            title = stringResource(R.string.live_rooms_title),
            onBack = onBack,
            showSearch = true,
            searchActive = searchOpen,
            onSearchToggle = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
            showAdd = true,
            onAdd = onCreateRoom,
            addDescription = stringResource(R.string.live_rooms_create_action),
        )

        Box(Modifier.padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s6)) {
            ZunoSegmentedTabs(
                labels = listOf(
                    stringResource(R.string.chats_filter_all),
                    stringResource(R.string.live_rooms_tab_zuno),
                    stringResource(R.string.live_rooms_tab_public),
                    stringResource(R.string.live_rooms_tab_code),
                ),
                selectedIndex = selectedTab,
                onSelect = { idx ->
                    selectedTab = idx
                    if (idx == 3) showCodeSheet = true
                },
            )
        }

        if (browseData == null && selectedTab != 3) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = browse.error, onRetry = onRetry)
            }
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s12, bottom = ZunoSpace.s32),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            if (searchOpen) {
                item(key = "search") {
                    ExploreSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.live_rooms_search_placeholder),
                        autoFocus = true,
                    )
                }
            }

            if (featuredToShow != null) {
                val featuredRoom = featuredToShow
                item(key = "featured") {
                    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                        Text(stringResource(R.string.live_rooms_featured_label), style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(
                                    RoundedCornerShape(ZunoRadius.xxl),
                                    fillColor = ZunoColors.SurfaceDefault,
                                    borderBrush = SolidColor(ZunoColors.BorderStrong),
                                )
                                .clickable(role = Role.Button, onClick = { onOpenRoom(featuredRoom) })
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
                                            .background(if (featuredRoom.members.isNotEmpty()) ZunoColors.StatusSpeaking else ZunoColors.TextTertiary),
                                    )
                                    Text(
                                        text = if (featuredRoom.members.isNotEmpty()) {
                                            stringResource(R.string.live_rooms_seats_live, featuredRoom.members.size, featuredRoom.maxMembers)
                                        } else {
                                            "${featuredRoom.members.size}/${featuredRoom.maxMembers} · Open"
                                        },
                                        style = ZunoType.FootnoteStrong,
                                        color = ZunoColors.TextPrimary,
                                    )
                                }
                                Text("$languageName · ${featuredRoom.level}", style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                                Text(
                                    text = featuredRoom.title,
                                    style = ZunoType.TitleMedium,
                                    color = ZunoColors.TextPrimary,
                                )
                                Text(
                                    text = stringResource(R.string.live_rooms_guided_by_zuno),
                                    style = ZunoType.CaptionRegular,
                                    color = ZunoColors.TextSecondary,
                                )
                            }

                            if (featuredRoom.members.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val stack = featuredRoom.members.take(3)
                                    Box(modifier = Modifier.width((28 + (stack.size - 1).coerceAtLeast(0) * 18).dp)) {
                                        stack.asReversed().forEachIndexed { revIdx, m ->
                                            val offsetDp = ((stack.size - 1 - revIdx) * 18).dp
                                            Box(modifier = Modifier.offset(x = offsetDp)) {
                                                ZunoAvatar(
                                                    initial = m.initial,
                                                    imageUrl = m.imageUrl,
                                                    size = 28.dp,
                                                    textStyle = ZunoType.Micro,
                                                )
                                            }
                                        }
                                    }
                                    val names = stack.joinToString(", ") { it.name.substringBefore(" ") }
                                    Text(
                                        text = stringResource(R.string.live_rooms_speakers_line, names),
                                        style = ZunoType.FootnoteRegular,
                                        color = ZunoColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Text(
                                    text = "Room is open — be the first to start speaking",
                                    style = ZunoType.FootnoteRegular,
                                    color = ZunoColors.TextTertiary,
                                )
                            }

                            ZunoButton(
                                label = stringResource(R.string.live_rooms_join_cta),
                                onClick = { onOpenRoom(featuredRoom) },
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
                        text = stringResource(if (selectedTab == 3) R.string.live_rooms_private_section else R.string.live_rooms_more_section),
                        style = ZunoType.LabelSmall,
                        color = ZunoColors.TextSecondary,
                    )
                    if (selectedTab == 3) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                                .padding(ZunoSpace.s16),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                        ) {
                            Text(
                                text = stringResource(R.string.live_rooms_private_desc),
                                style = ZunoType.BodySmall,
                                color = ZunoColors.TextSecondary,
                            )
                            ZunoButton(
                                label = stringResource(R.string.live_rooms_enter_code_cta),
                                onClick = { showCodeSheet = true },
                                size = ZunoButtonSize.Medium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (filteredRooms.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                                .padding(ZunoSpace.s16),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                        ) {
                            Text(
                                text = if (selectedTab == 2) "No public rooms open yet" else "No matching rooms",
                                style = ZunoType.BodyMediumStrong,
                                color = ZunoColors.TextPrimary,
                            )
                            Text(
                                text = "Start a room and invite partners or let other $languageName learners join.",
                                style = ZunoType.FootnoteRegular,
                                color = ZunoColors.TextTertiary,
                            )
                            ZunoButton(
                                label = stringResource(R.string.live_rooms_create_action),
                                onClick = onCreateRoom,
                                variant = ZunoButtonVariant.Secondary,
                                size = ZunoButtonSize.Small,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                        ) {
                            filteredRooms.forEachIndexed { idx, room ->
                                if (idx > 0) ZunoGlassHairline()
                                val sub = when {
                                    room.isLive && room.members.isNotEmpty() ->
                                        "${room.hostName} · ${room.level} · ${room.members.size}/${room.maxMembers} live"
                                    room.isLive ->
                                        "${room.hostName} · ${room.level} · ${room.members.size}/${room.maxMembers} open"
                                    else ->
                                        "${room.hostName} · ${room.level} · Waiting ${room.members.size}/${room.maxMembers}"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button, onClick = { onOpenRoom(room) })
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
                                            if (room.hasActiveParticipants) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(ZunoColors.StatusSpeaking),
                                                )
                                            } else if (room.isLive) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(ZunoColors.TextTertiary),
                                                )
                                            }
                                            Text(
                                                text = sub,
                                                style = ZunoType.FootnoteRegular,
                                                color = ZunoColors.TextTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    ZunoButton(
                                        label = stringResource(R.string.push_action_join),
                                        onClick = { onOpenRoom(room) },
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
    }

    if (showCodeSheet) {
        var code by rememberSaveable { mutableStateOf("") }
        ZunoSheet(
            onDismiss = {
                showCodeSheet = false
                if (selectedTab == 3) selectedTab = 0
            },
            title = stringResource(R.string.live_rooms_join_private_title),
            description = stringResource(R.string.live_rooms_join_private_desc),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
            ) {
                ZunoTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = stringResource(R.string.live_rooms_code_label),
                    placeholder = "ZUNO-482",
                )
                ZunoButton(
                    label = stringResource(R.string.live_rooms_join_room_btn),
                    onClick = {
                        showCodeSheet = false
                        onJoinByCode(code.trim())
                    },
                    enabled = code.trim().length >= 3 && !busy,
                    loading = busy,
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
                ZunoDivider(label = "or")
                ZunoButton(
                    label = stringResource(R.string.live_rooms_create_private_btn),
                    onClick = {
                        showCodeSheet = false
                        onCreatePrivateRoom()
                    },
                    variant = ZunoButtonVariant.Secondary,
                    size = ZunoButtonSize.Large,
                    leadingIcon = R.drawable.zuno_ic_plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * `Live Rooms · 2 · Create` (`2703:12485`).
 */
@Composable
fun CreateLiveRoomScreen(
    languages: List<LearningLanguage>,
    defaultLanguage: LearningLanguage,
    defaultLevel: String,
    initialPrivate: Boolean = false,
    busy: Boolean = false,
    onBack: () -> Unit,
    onCreate: (title: String, languageCode: String, level: String, maxMembers: Int, visibility: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var topic by rememberSaveable { mutableStateOf("") }
    var selectedLangCode by rememberSaveable { mutableStateOf(defaultLanguage.code) }
    var selectedLevel by rememberSaveable { mutableStateOf(defaultLevel) }
    var maxMembers by rememberSaveable { mutableIntStateOf(4) }
    var visibility by rememberSaveable { mutableStateOf(if (initialPrivate) "private" else "public") }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedLang = LearningLanguages.find(selectedLangCode, languages)

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.live_rooms_new_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Text(
                text = stringResource(R.string.live_rooms_new_subtitle),
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
            )

            ZunoTextField(
                value = topic,
                onValueChange = { topic = it },
                label = stringResource(R.string.live_rooms_topic_label),
                placeholder = stringResource(R.string.live_rooms_topic_placeholder),
            )

            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_learn_language),
                    icon = R.drawable.zuno_ic_t_globe,
                    value = selectedLang.englishName,
                    showChevron = true,
                    onClick = { picker = "language" },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.live_rooms_language_level),
                    icon = R.drawable.zuno_ic_trending_up,
                    value = selectedLevel,
                    showChevron = true,
                    showDivider = true,
                    onClick = { picker = "level" },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.live_rooms_max_members),
                    subtitle = stringResource(R.string.live_rooms_max_members_sub),
                    icon = R.drawable.zuno_ic_t_users,
                    value = stringResource(R.string.live_rooms_members_value, maxMembers),
                    showChevron = true,
                    showDivider = true,
                    onClick = { picker = "members" },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(stringResource(R.string.settings_visibility_section), style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                ZunoSettingsGroup {
                    VisibilityChoiceRow(
                        title = stringResource(R.string.topics_public_badge),
                        subtitle = stringResource(R.string.live_rooms_visibility_public_sub, selectedLang.englishName),
                        icon = R.drawable.zuno_ic_t_globe,
                        selected = visibility == "public",
                        onClick = { visibility = "public" },
                    )
                    ZunoGlassHairline()
                    VisibilityChoiceRow(
                        title = stringResource(R.string.topics_private_badge),
                        subtitle = stringResource(R.string.live_rooms_visibility_private_sub),
                        icon = R.drawable.zuno_ic_t_lock,
                        selected = visibility == "private",
                        onClick = { visibility = "private" },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s16),
        ) {
            ZunoButton(
                label = stringResource(R.string.live_rooms_create_btn),
                onClick = { onCreate(topic.trim(), selectedLang.code, selectedLevel, maxMembers, visibility) },
                enabled = topic.trim().length >= 2 && !busy,
                loading = busy,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (picker) {
        "language" -> ZunoOptionSheet(
            title = stringResource(R.string.settings_learn_language),
            options = languages.take(12).map { it.code },
            selected = selectedLangCode,
            label = { code -> LearningLanguages.find(code, languages).englishName },
            onSelect = { selectedLangCode = it; picker = null },
            onDismiss = { picker = null },
        )
        "level" -> ZunoOptionSheet(
            title = stringResource(R.string.live_rooms_language_level),
            options = Levels,
            selected = selectedLevel,
            label = { it },
            onSelect = { selectedLevel = it; picker = null },
            onDismiss = { picker = null },
        )
        "members" -> ZunoOptionSheet(
            title = stringResource(R.string.live_rooms_max_members),
            options = listOf(2, 3, 4),
            selected = maxMembers,
            label = { count -> stringResource(R.string.live_rooms_members_value, count) },
            onSelect = { maxMembers = it; picker = null },
            onDismiss = { picker = null },
        )
    }
}

/**
 * `Live Rooms · 3 · Creator Lobby` (`2703:12625`) & `Live Rooms · 4 · Participant Lobby` (`2703:12741`).
 */
@Composable
fun LiveRoomLobbyScreen(
    room: LiveRoom,
    languageName: String,
    busy: Boolean = false,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onStartRoom: () -> Unit,
    onLeaveLobby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = stringResource(R.string.live_rooms_lobby_title, room.members.size, room.maxMembers),
            onBack = onBack,
            trailing = { Spacer(Modifier.size(44.dp)) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                    .padding(ZunoSpace.s16),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                ) {
                    LobbyStatusPill(
                        text = stringResource(if (room.isHost) R.string.live_rooms_waiting_start else R.string.live_rooms_waiting_host),
                        dotColor = ZunoColors.StatusListening,
                    )
                    LobbyStatusPill(
                        text = stringResource(if (room.isPublic) R.string.topics_public_badge else R.string.topics_private_badge),
                        icon = if (room.isPublic) R.drawable.zuno_ic_t_globe else R.drawable.zuno_ic_t_lock,
                    )
                    LobbyStatusPill(text = "$languageName · ${room.level}")
                }

                Text(room.title, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)

                if (room.isHost) {
                    Text(
                        text = stringResource(R.string.live_rooms_creator_hint, room.maxMembers),
                        style = ZunoType.BodySmall,
                        color = ZunoColors.TextSecondary,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZunoAvatar(initial = room.hostName.take(1).uppercase(), size = 24.dp, textStyle = ZunoType.Micro)
                        Text(
                            text = stringResource(R.string.live_rooms_hosted_by, room.hostName),
                            style = ZunoType.BodySmall,
                            color = ZunoColors.TextSecondary,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.live_rooms_members_header, room.members.size, room.maxMembers),
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextPrimary,
                    )
                    if (room.isHost) {
                        ZunoChip(
                            label = stringResource(R.string.live_rooms_invite_btn),
                            leadingIcon = R.drawable.zuno_ic_user_plus,
                            onClick = onInvite,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    room.members.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                                .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ZunoAvatar(
                                initial = member.initial,
                                imageUrl = member.imageUrl,
                                size = 38.dp,
                                textStyle = ZunoType.LabelMedium,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                                Text(
                                    text = if (member.isYou) stringResource(R.string.call_you_name, member.name) else member.name,
                                    style = ZunoType.BodyMediumStrong,
                                    color = ZunoColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${room.level} · ${if (member.isHost) stringResource(R.string.live_rooms_role_creator) else stringResource(R.string.live_rooms_role_ready)}",
                                    style = ZunoType.FootnoteRegular,
                                    color = ZunoColors.TextTertiary,
                                )
                            }
                            if (member.isHost || member.ready) {
                                LobbyStatusPill(
                                    text = stringResource(if (member.isHost) R.string.live_rooms_badge_host else R.string.live_rooms_badge_ready),
                                    dotColor = ZunoColors.StatusSpeaking,
                                )
                            }
                        }
                    }

                    val openSeats = (room.maxMembers - room.members.size).coerceAtLeast(0)
                    if (openSeats > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl), fillColor = ZunoColors.SurfaceSubtle)
                                .clickable(role = Role.Button, onClick = onInvite)
                                .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s14),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ZunoIcon(
                                icon = if (room.isHost) R.drawable.zuno_ic_user_plus else R.drawable.zuno_ic_t_clock,
                                size = 18.dp,
                                tint = ZunoColors.IconSecondary,
                            )
                            Text(
                                text = if (room.isHost) {
                                    stringResource(R.string.live_rooms_open_seats_host, openSeats)
                                } else {
                                    stringResource(R.string.live_rooms_open_seats_guest, room.hostName.substringBefore(" "), openSeats)
                                },
                                style = ZunoType.BodySmall,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        ) {
            if (room.isHost) {
                ZunoButton(
                    label = stringResource(R.string.live_rooms_start_now),
                    onClick = onStartRoom,
                    loading = busy,
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
                ZunoButton(
                    label = stringResource(R.string.live_rooms_wait_copy_link),
                    onClick = onInvite,
                    variant = ZunoButtonVariant.Secondary,
                    size = ZunoButtonSize.Large,
                    leadingIcon = R.drawable.zuno_ic_t_share,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ZunoButton(
                    label = stringResource(R.string.live_rooms_leave_lobby),
                    onClick = onLeaveLobby,
                    variant = ZunoButtonVariant.Secondary,
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * `Live Rooms · 6 · System Room Detail` (`2704:12733`).
 */
@Composable
fun SystemRoomDetailScreen(
    room: LiveRoom,
    languageName: String,
    busy: Boolean = false,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = stringResource(R.string.live_rooms_detail_title),
            onBack = onBack,
            trailing = {
                ZunoOverflowMenu(
                    actions = listOf(
                        ZunoMenuAction(stringResource(R.string.share_action), icon = R.drawable.zuno_ic_t_share, onClick = onShare),
                    ),
                    size = ZunoIconButtonSize.Large,
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                    .padding(ZunoSpace.s16),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceStrong)
                        .padding(horizontal = ZunoSpace.s10, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_t_sparkles, 12.dp, tint = ZunoColors.IconPrimary)
                    Text(
                        text = "SYSTEM PROGRAMMED · ${languageName.uppercase()} · ${room.level}",
                        style = ZunoType.Micro,
                        color = ZunoColors.TextPrimary,
                    )
                }
                Text(room.title, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
                Text(
                    text = room.description.ifBlank { stringResource(R.string.live_rooms_system_default_desc) },
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                RoomMetricCard("10 min", stringResource(R.string.live_rooms_stat_duration), Modifier.weight(1f))
                RoomMetricCard("${room.maxMembers} max", stringResource(R.string.live_rooms_stat_members), Modifier.weight(1f))
                RoomMetricCard(
                    stringResource(if (room.isPublic) R.string.topics_public_badge else R.string.topics_private_badge),
                    stringResource(R.string.live_rooms_stat_access),
                    Modifier.weight(1f),
                )
            }

            val prompts = room.discussionPrompts.ifEmpty {
                listOf(
                    "Your go-to morning coffee order and routine",
                    "Favorite neighborhood cafés in your city",
                    "Comparing café culture across cities",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(stringResource(R.string.live_rooms_discuss_header), style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                        .padding(ZunoSpace.s16),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                ) {
                    prompts.forEach { prompt ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ZunoIcon(R.drawable.zuno_ic_t_check, 16.dp, tint = ZunoColors.IconSecondary)
                            Text(prompt, style = ZunoType.BodySmall, color = ZunoColors.TextPrimary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                    .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                    room.members.take(3).forEach { m ->
                        ZunoAvatar(initial = m.initial, imageUrl = m.imageUrl, size = 28.dp, textStyle = ZunoType.Micro)
                    }
                }
                Text(
                    text = stringResource(R.string.live_rooms_members_joined, room.members.size, room.maxMembers),
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s16),
        ) {
            ZunoButton(
                label = stringResource(R.string.live_rooms_join_cta),
                onClick = onJoin,
                loading = busy,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * `Live Rooms · 7 · Active Room` (`2704:12814`), `8 · Host Controls` (`2704:12995`),
 * and `9 · Active Room — No Captions (Avatars Only)` (`7353:15741`).
 */
@Composable
fun ActiveLiveRoomScreen(
    roomData: LiveRoom,
    userName: String,
    userInitial: String,
    startedAtMillis: Long?,
    turns: List<ChatMessage>,
    isMuted: Boolean,
    isCameraOn: Boolean,
    isSpeakerOn: Boolean,
    livekitRoom: Room? = null,
    localVideoTrack: VideoTrack? = null,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onNextQuestion: () -> Unit,
    onSwitchVisibility: () -> Unit,
    onInviteMember: () -> Unit,
    onSpeakStarter: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSlowReplay: (String) -> Unit,
    onEndRoom: () -> Unit,
    status: VoiceCallStatus = if (isMuted) VoiceCallStatus.Muted else VoiceCallStatus.Listening,
    audioLevel: () -> Float = { 0f },
    userAudioLevel: () -> Float = audioLevel,
    activeSpeaker: Speaker? = null,
    activeSpeakerName: String? = null,
    hasError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var captionsOn by rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSec = startedAtMillis?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0
    val elapsed = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    BackHandler(onBack = onEndRoom)

    val displayMembers = remember(roomData.members, userName, userInitial) {
        roomData.members.ifEmpty {
            listOf(
                LiveRoomMember("you", userName, userInitial, isHost = roomData.isHost, isYou = true),
            )
        }
    }

    val topAvatars = remember(displayMembers) {
        displayMembers.take(4).map { CallAvatarItem(initial = it.initial, imageUrl = it.imageUrl) }
    }

    val hostMenu = buildList {
        add(
            ZunoMenuAction(
                label = stringResource(R.string.live_rooms_menu_invite, displayMembers.size, roomData.maxMembers),
                icon = R.drawable.zuno_ic_user_plus,
                onClick = onInviteMember,
            ),
        )
        add(
            ZunoMenuAction(
                label = stringResource(R.string.live_rooms_menu_next_question),
                icon = R.drawable.zuno_ic_t_sparkles,
                onClick = onNextQuestion,
            ),
        )
        if (roomData.isHost) {
            add(
                ZunoMenuAction(
                    label = stringResource(if (roomData.isPublic) R.string.live_rooms_menu_switch_private else R.string.live_rooms_menu_switch_public),
                    icon = if (roomData.isPublic) R.drawable.zuno_ic_t_lock else R.drawable.zuno_ic_t_globe,
                    onClick = onSwitchVisibility,
                ),
            )
            add(
                ZunoMenuAction(
                    label = stringResource(R.string.live_rooms_menu_end_all),
                    icon = R.drawable.zuno_ic_log_out,
                    destructive = true,
                    dividerBefore = true,
                    onClick = onEndRoom,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCallTopBar(
            sessionLabel = stringResource(R.string.live_rooms_call_label),
            elapsed = elapsed,
            showAvatars = captionsOn,
            avatars = topAvatars,
            onBack = onEndRoom,
            trailing = { ZunoOverflowMenu(hostMenu, size = ZunoIconButtonSize.Large) },
        )

        if (!captionsOn) {
            // `Live Rooms · 9 · Active Room — No Captions (Avatars Only)` (`7353:15741`)
            val rows = displayMembers.take(4).chunked(2)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                    ) {
                        rowItems.forEach { member ->
                            LiveRoomGridTile(
                                member = member,
                                level = roomData.level,
                                isMuted = member.isYou && isMuted,
                                videoTrack = if (member.isYou) localVideoTrack else null,
                                room = livekitRoom,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            // `Live Rooms · 7 · Active Room` (`2704:12814`)
            val promptText = roomData.discussionPrompts.getOrNull((roomData.currentRound - 1).coerceAtLeast(0))
                ?: roomData.title
            val listState = rememberLazyListState()
            val displayTurns = remember(turns, promptText) {
                if (turns.isNotEmpty()) turns
                else listOf(
                    ChatMessage(id = "sys-q", speaker = Speaker.AGENT, text = "“$promptText”", isFinal = true),
                )
            }
            val lastTurnText = displayTurns.lastOrNull()?.text.orEmpty()
            LaunchedEffect(displayTurns.size, lastTurnText) {
                if (displayTurns.isNotEmpty()) {
                    delay(50)
                    listState.animateScrollToItem(displayTurns.size - 1)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s20, vertical = ZunoSpace.s4),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = ZunoSpace.s4),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    itemsIndexed(displayTurns, key = { _, turn -> turn.id }) { index, turn ->
                        val turnMod = if (index < displayTurns.lastIndex) Modifier.alpha(0.78f) else Modifier
                        if (turn.speaker == Speaker.USER) {
                            val youName = stringResource(R.string.call_you_name, userName)
                            val userLabel = if (!turn.isFinal) {
                                "$youName · ${stringResource(R.string.voice_status_dot_speaking)}"
                            } else {
                                youName
                            }
                            ZunoUserBubble(
                                message = "“${turn.text.trim('“', '”')}”",
                                senderLabel = userLabel,
                                modifier = turnMod,
                            )
                        } else {
                            val baseSender = turn.senderName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.live_rooms_zuno_prompt_sender)
                            val otherLabel = if (!turn.isFinal) {
                                "$baseSender · ${stringResource(R.string.voice_status_dot_speaking)}"
                            } else {
                                baseSender
                            }
                            ZunoAssistantBubble(
                                message = turn.text,
                                senderLabel = otherLabel,
                                onListen = { onTranslate(turn.text) },
                                listenLabel = stringResource(R.string.voice_chip_translate),
                                listenIcon = R.drawable.zuno_ic_languages,
                                onExplain = { onSlowReplay(turn.text) },
                                explainLabel = stringResource(R.string.voice_chip_speed_slow),
                                explainIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                modifier = turnMod,
                            )
                        }
                    }
                }

                val hasActiveUserSpeech = turns.any { it.speaker == Speaker.USER && !it.isFinal }
                val turnPillState = rememberTurnPillState(
                    status = status,
                    userAudioLevel = userAudioLevel,
                    activeSpeaker = activeSpeaker,
                    hasActiveUserSpeech = hasActiveUserSpeech,
                    hasError = hasError,
                )
                val remoteSpeaker = activeSpeakerName?.takeIf { it.isNotBlank() }
                    ?: turns.lastOrNull { it.speaker == Speaker.AGENT && !it.senderName.isNullOrBlank() }?.senderName
                    ?: displayMembers.firstOrNull { !it.isYou }?.name?.substringBefore(" ")
                    ?: "Zuno"
                ZunoTurnPillRow(
                    state = turnPillState,
                    speakerName = remoteSpeaker,
                    audioLevel = audioLevel,
                    userAudioLevel = userAudioLevel,
                    onClick = if (turnPillState == TurnPillState.Muted) onToggleMute else null,
                )

                // Round Coach Card (`2704:12814`)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(
                            RoundedCornerShape(ZunoRadius.xxxl),
                            fillColor = ZunoColors.SurfaceDefault,
                            borderBrush = SolidColor(ZunoColors.BorderDefault),
                        )
                        .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Round ${roomData.currentRound} · ${roomData.title}",
                            style = ZunoType.LabelSmall,
                            color = ZunoColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = ZunoSpace.s8),
                        )
                        Text(
                            text = "${displayMembers.size}/${roomData.maxMembers} ${roomData.visibility} · Live",
                            style = ZunoType.FootnoteRegular,
                            color = ZunoColors.TextTertiary,
                            maxLines = 1,
                        )
                    }
                    val starters = roomData.starterPhrases.take(2)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        starters.forEachIndexed { idx, starter ->
                            val cleanSpeak = starter.trim('“', '”', '…', ' ')
                            ZunoChip(
                                label = "“$starter”",
                                selected = idx == 0,
                                onClick = { onSpeakStarter(cleanSpeak) },
                            )
                        }
                        ZunoChip(
                            label = stringResource(R.string.lesson_bubble_hint),
                            leadingIcon = R.drawable.zuno_ic_sparkles,
                            onClick = onNextQuestion,
                        )
                    }
                }
            }
        }

        ZunoCallControlsBar(
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            isSpeakerOn = isSpeakerOn,
            isCaptionsOn = captionsOn,
            endCallLabel = stringResource(R.string.live_rooms_end_btn),
            onToggleMute = onToggleMute,
            onToggleCamera = onToggleCamera,
            onEndCall = onEndRoom,
            onToggleSpeaker = onToggleSpeaker,
            onToggleCaptions = { captionsOn = !captionsOn },
        )
    }
}

@Composable
private fun LiveRoomGridTile(
    member: LiveRoomMember,
    level: String,
    isMuted: Boolean,
    videoTrack: VideoTrack?,
    room: Room?,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(ZunoRadius.xxl)
    val scrim = Brush.verticalGradient(0f to Color.Transparent, 0.55f to ZunoColors.ScrimVideoMid, 1f to ZunoColors.ScrimVideoBottom)
    Box(
        modifier = modifier
            .clip(tileShape)
            .background(ZunoColors.SurfaceDefault)
            .border(
                width = if (member.isYou && !isMuted) 1.5.dp else 1.dp,
                color = if (member.isYou && !isMuted) ZunoColors.BorderStrong else ZunoColors.BorderDefault,
                shape = tileShape,
            ),
    ) {
        if (videoTrack != null) {
            LiveKitVideoFeed(room = room, videoTrack = videoTrack, mirror = true, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(90.dp).background(scrim))
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(ZunoSpace.s12),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (member.isHost) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (member.isHost) {
                    Box(
                        modifier = Modifier
                            .zunoGlassMediaPill(CircleShape)
                            .padding(horizontal = ZunoSpace.s8, vertical = 3.dp),
                    ) {
                        Text("Host", style = ZunoType.Micro, color = ZunoColors.TextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .zunoGlassMediaPill(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(
                        icon = if (isMuted) R.drawable.zuno_ic_mic_off else R.drawable.zuno_ic_t_mic,
                        size = 13.dp,
                        tint = if (isMuted) ZunoColors.TextDanger else ZunoColors.IconPrimary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                Text(
                    text = if (member.isYou) stringResource(R.string.call_you_name, member.name) else member.name,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isMuted) "Muted · $level" else if (member.isYou) "Speaking · $level" else level,
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LobbyStatusPill(
    text: String,
    dotColor: Color? = null,
    icon: Int? = null,
) {
    Row(
        modifier = Modifier
            .zunoGlassSubtle(CircleShape, fillColor = ZunoColors.SurfaceStrong)
            .padding(horizontal = ZunoSpace.s10, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        }
        if (icon != null) {
            ZunoIcon(icon, 12.dp, tint = ZunoColors.IconSecondary)
        }
        Text(text, style = ZunoType.FootnoteStrong, color = ZunoColors.TextPrimary, maxLines = 1)
    }
}

@Composable
internal fun RoomMetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
            .padding(ZunoSpace.s12),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
    ) {
        Text(value, style = ZunoType.Headline, color = ZunoColors.TextPrimary, maxLines = 1)
        Text(label, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, maxLines = 1)
    }
}

@Composable
private fun VisibilityChoiceRow(
    title: String,
    subtitle: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).zunoGlassSubtle(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 16.dp, tint = ZunoColors.IconPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
        if (selected) {
            ZunoIcon(R.drawable.zuno_ic_t_check, 18.dp, tint = ZunoColors.IconPrimary)
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
