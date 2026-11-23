package com.zuno.ai.ui.screens

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.ChatsFilter
import com.zuno.ai.data.ChatsOverview
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.data.UnifiedChatItem
import com.zuno.ai.data.UnifiedChatKind
import com.zuno.ai.ui.components.HomeNavTab
import com.zuno.ai.avatar.catalog.LocalActiveTutor
import com.zuno.ai.ui.components.ZunoAiTutorAvatar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoEmptyState
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoIconButtonStyle
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoOpenToSpeakAvatar
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSegmentedTabs
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassBorderSubtleBrush
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.viewmodel.Loadable

@Composable
fun ChatsScreen(
    overview: Loadable<ChatsOverview>,
    userInitial: String,
    userImageUrl: String?,
    initialFilter: ChatsFilter,
    onRetry: () -> Unit,
    onToggleOpenToSpeak: (Boolean) -> Unit,
    onOpenAiConversation: (String) -> Unit,
    onStartAiChat: () -> Unit,
    onStartAiVoiceCall: () -> Unit,
    onOpenRoleplays: () -> Unit,
    onOpenPeerChat: (PeerLearner) -> Unit,
    onInvitePeerToTalk: (PeerLearner) -> Unit,
    onTogglePartner: (PeerLearner, Boolean) -> Unit,
    onNavigateTab: (HomeNavTab) -> Unit = {},
    onBack: () -> Unit = { onNavigateTab(HomeNavTab.Home) },
    modifier: Modifier = Modifier,
) {
    var selectedFilter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showNewChatSheet by rememberSaveable { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<PeerLearner?>(null) }

    val data = overview.items
    // Keep selectedMember in sync with latest overview data (e.g. after toggling partner)
    val activeMember = selectedMember?.let { current ->
        data?.openSpeakers?.firstOrNull { it.id == current.id }
            ?: data?.partners?.firstOrNull { it.id == current.id }
            ?: current
    }

    val query = searchQuery.trim().lowercase()
    val filteredConversations = remember(data, selectedFilter, query) {
        data?.conversations.orEmpty().filter { item ->
            val matchesTab = when (selectedFilter) {
                ChatsFilter.All -> true
                ChatsFilter.AiTutor -> item.kind == UnifiedChatKind.Ai
                ChatsFilter.People -> item.kind == UnifiedChatKind.Peer
                ChatsFilter.Open -> false
            }
            val matchesQuery = query.isEmpty() ||
                item.title.lowercase().contains(query) ||
                item.snippet.lowercase().contains(query)
            matchesTab && matchesQuery
        }
    }
    val filteredOpenSpeakers = remember(data, query) {
        data?.openSpeakers.orEmpty().filter { learner ->
            query.isEmpty() ||
                learner.name.lowercase().contains(query) ||
                learner.interests.any { it.lowercase().contains(query) }
        }
    }
    val filteredPartners = remember(data, query) {
        data?.partners.orEmpty().filter { learner ->
            query.isEmpty() || learner.name.lowercase().contains(query)
        }
    }

    val showSearch = (data != null && (
        data.conversations.isNotEmpty() ||
            (selectedFilter == ChatsFilter.Open && data.openSpeakers.isNotEmpty()) ||
            (selectedFilter == ChatsFilter.People && data.partners.isNotEmpty())
        )) || searchVisible

    Box(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ZunoSectionTopBar(
                title = stringResource(R.string.chats_title),
                onBack = onBack,
                showSearch = showSearch,
                searchActive = searchVisible,
                onSearchToggle = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                },
                searchDescription = stringResource(R.string.chats_search),
                showAdd = true,
                onAdd = { showNewChatSheet = true },
                addDescription = stringResource(R.string.chats_new_chat),
            )

            if (data == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ZunoLoadState(error = overview.error, onRetry = onRetry)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s6, bottom = ZunoSpace.s32),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
                ) {
                    item(key = "open-toggle") {
                        OpenToSpeakToggleCard(
                            userInitial = userInitial,
                            userImageUrl = userImageUrl,
                            openToSpeak = data.openToSpeak,
                            languageName = data.languageName,
                            level = data.level,
                            onToggle = onToggleOpenToSpeak,
                        )
                    }

                    if (searchVisible) {
                        item(key = "search-field") {
                            ChatsSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = stringResource(R.string.chats_search_placeholder),
                                autoFocus = true,
                            )
                        }
                    }

                    item(key = "filters") {
                        ChatsFilterChipsRow(
                            selected = selectedFilter,
                            onSelect = { selectedFilter = it },
                        )
                    }

                    // Search with zero results across the current filter
                    val noSearchResults = query.isNotEmpty() && when (selectedFilter) {
                        ChatsFilter.All -> filteredConversations.isEmpty() && filteredOpenSpeakers.isEmpty()
                        ChatsFilter.AiTutor -> filteredConversations.isEmpty()
                        ChatsFilter.People -> filteredConversations.isEmpty() && filteredPartners.isEmpty()
                        ChatsFilter.Open -> filteredOpenSpeakers.isEmpty()
                    }

                    if (noSearchResults) {
                        item(key = "no-results") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s24),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                            ) {
                                ZunoEmptyState(
                                    icon = R.drawable.zuno_ic_search,
                                    title = stringResource(R.string.chats_empty_search_title, searchQuery.trim()),
                                    description = stringResource(R.string.chats_empty_search_body),
                                )
                                ZunoButton(
                                    label = stringResource(R.string.chats_empty_search_cta),
                                    onClick = { searchQuery = "" },
                                    variant = ZunoButtonVariant.Secondary,
                                    size = ZunoButtonSize.Medium,
                                )
                            }
                        }
                    } else {
                        when (selectedFilter) {
                            ChatsFilter.All -> {
                                if (filteredConversations.isEmpty()) {
                                    item(key = "empty-all") {
                                        ChatsEmptySection(
                                            icon = R.drawable.zuno_ic_messages_square,
                                            title = stringResource(R.string.chats_empty_all_title),
                                            description = stringResource(R.string.chats_empty_all_body),
                                            quickStartsTitle = stringResource(R.string.chats_ways_to_start),
                                            quickStarts = {
                                                QuickStartAiRow(onClick = onStartAiChat)
                                                if (data.openSpeakers.isNotEmpty()) {
                                                    QuickStartOpenSpeakersRow(
                                                        speakers = data.openSpeakers,
                                                        onClick = { selectedFilter = ChatsFilter.Open },
                                                    )
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    val openUnifiedChat = { item: UnifiedChatItem ->
                                        if (item.kind == UnifiedChatKind.Ai && item.conversationId != null) {
                                            onOpenAiConversation(item.conversationId)
                                        } else if (item.kind == UnifiedChatKind.Peer && item.partnerId != null) {
                                            val partner = data.openSpeakers.firstOrNull { it.id == item.partnerId }
                                                ?: data.partners.firstOrNull { it.id == item.partnerId }
                                                ?: PeerLearner(
                                                    id = item.partnerId,
                                                    name = item.title,
                                                    shortName = item.title.substringBefore(" "),
                                                    initial = item.avatarInitial,
                                                    imageUrl = item.avatarUrl,
                                                    language = data.language,
                                                    languageName = data.languageName,
                                                    level = data.level,
                                                    nativeLanguage = "English",
                                                    streakDays = 7,
                                                    sessionsCount = 12,
                                                    interests = emptyList(),
                                                    openToSpeak = item.openToSpeak,
                                                    isPartner = false,
                                                    activeLabel = if (item.openToSpeak) "Open to speak" else "Online",
                                                )
                                            onOpenPeerChat(partner)
                                        }
                                    }
                                    val pinnedItems = filteredConversations.filter { it.pinned }
                                    val recentItems = filteredConversations.filterNot { it.pinned }
                                    if (pinnedItems.isNotEmpty()) {
                                        item(key = "pinned-convs") {
                                            ConversationsSection(
                                                header = stringResource(R.string.drawer_pinned),
                                                items = pinnedItems,
                                                onClickItem = openUnifiedChat,
                                            )
                                        }
                                        if (recentItems.isNotEmpty()) {
                                            item(key = "recent-convs") {
                                                ConversationsSection(
                                                    header = stringResource(R.string.chats_recent_header),
                                                    items = recentItems,
                                                    onClickItem = openUnifiedChat,
                                                )
                                            }
                                        }
                                    } else {
                                        item(key = "all-convs") {
                                            ConversationsSection(
                                                header = null,
                                                items = recentItems,
                                                onClickItem = openUnifiedChat,
                                            )
                                        }
                                    }
                                }
                            }

                            ChatsFilter.AiTutor -> {
                                if (filteredConversations.isEmpty()) {
                                    item(key = "empty-ai") {
                                        ChatsEmptyHero(
                                            icon = R.drawable.zuno_ic_t_sparkles,
                                            title = stringResource(R.string.chats_empty_ai_title),
                                            description = stringResource(R.string.chats_empty_ai_body),
                                            ctaLabel = stringResource(R.string.chats_start_ai_title),
                                            onCta = onStartAiChat,
                                            modifier = Modifier.fillParentMaxHeight(0.75f),
                                        )
                                    }
                                } else {
                                    item(key = "start-ai-card") {
                                        StartAiChatCard(onChat = onStartAiChat)
                                    }
                                    item(key = "ai-convs") {
                                        ConversationsSection(
                                            header = stringResource(R.string.chats_ai_conversations_count, filteredConversations.size),
                                            items = filteredConversations,
                                            onClickItem = { item ->
                                                item.conversationId?.let(onOpenAiConversation)
                                            },
                                        )
                                    }
                                }
                            }

                            ChatsFilter.People -> {
                                if (filteredConversations.isEmpty() && filteredPartners.isEmpty()) {
                                    item(key = "empty-people") {
                                        ChatsEmptyHero(
                                            icon = R.drawable.zuno_ic_users,
                                            title = stringResource(R.string.chats_empty_people_title),
                                            description = stringResource(R.string.chats_empty_people_body),
                                            ctaLabel = stringResource(R.string.chats_empty_people_cta),
                                            onCta = { selectedFilter = ChatsFilter.Open },
                                            modifier = Modifier.fillParentMaxHeight(0.75f),
                                        )
                                    }
                                } else {
                                    if (filteredConversations.isNotEmpty()) {
                                        item(key = "people-convs") {
                                            ConversationsSection(
                                                header = null,
                                                items = filteredConversations,
                                                onClickItem = { item ->
                                                    if (item.partnerId != null) {
                                                        val partner = data.openSpeakers.firstOrNull { it.id == item.partnerId }
                                                            ?: data.partners.firstOrNull { it.id == item.partnerId }
                                                            ?: PeerLearner(
                                                                id = item.partnerId,
                                                                name = item.title,
                                                                shortName = item.title.substringBefore(" "),
                                                                initial = item.avatarInitial,
                                                                imageUrl = item.avatarUrl,
                                                                language = data.language,
                                                                languageName = data.languageName,
                                                                level = data.level,
                                                                nativeLanguage = "English",
                                                                streakDays = 7,
                                                                sessionsCount = 12,
                                                                interests = emptyList(),
                                                                openToSpeak = item.openToSpeak,
                                                                isPartner = false,
                                                                activeLabel = if (item.openToSpeak) "Open to speak" else "Online",
                                                            )
                                                        onOpenPeerChat(partner)
                                                    }
                                                },
                                            )
                                        }
                                    } else if (filteredPartners.isNotEmpty()) {
                                        item(key = "added-partners") {
                                            AddedPartnersSection(
                                                partners = filteredPartners,
                                                actionLabel = stringResource(R.string.chats_action_message),
                                                onAction = onOpenPeerChat,
                                                onRowClick = { selectedMember = it },
                                            )
                                        }
                                    }
                                }
                            }

                            ChatsFilter.Open -> {
                                if (filteredOpenSpeakers.isEmpty()) {
                                    item(key = "empty-open") {
                                        ChatsEmptyHero(
                                            icon = R.drawable.zuno_ic_t_mic,
                                            title = stringResource(R.string.chats_empty_open_title),
                                            description = stringResource(R.string.chats_empty_open_body),
                                            ctaLabel = stringResource(R.string.chats_start_ai_title),
                                            onCta = onStartAiChat,
                                            modifier = Modifier.fillParentMaxHeight(0.75f),
                                        )
                                    }
                                } else {
                                    item(key = "open-full-list") {
                                        OpenSpeakersFullListSection(
                                            speakers = filteredOpenSpeakers,
                                            onView = { selectedMember = it },
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

    if (showNewChatSheet && data != null) {
        NewChatSheet(
            openSpeakers = data.openSpeakers,
            partners = data.partners,
            onDismiss = { showNewChatSheet = false },
            onChatAi = {
                showNewChatSheet = false
                onStartAiChat()
            },
            onCallAi = {
                showNewChatSheet = false
                onStartAiVoiceCall()
            },
            onTalkSpeaker = { learner ->
                showNewChatSheet = false
                onInvitePeerToTalk(learner)
            },
            onMessagePartner = { learner ->
                showNewChatSheet = false
                onOpenPeerChat(learner)
            },
            onSelectMember = { learner ->
                showNewChatSheet = false
                selectedMember = learner
            },
        )
    }

    if (activeMember != null) {
        MemberProfileSheet(
            member = activeMember,
            onDismiss = { selectedMember = null },
            onInviteToTalk = {
                val target = activeMember
                selectedMember = null
                onInvitePeerToTalk(target)
            },
            onSendMessage = {
                val target = activeMember
                selectedMember = null
                onOpenPeerChat(target)
            },
            onTogglePartner = { add ->
                onTogglePartner(activeMember, add)
            },
        )
    }
}

@Composable
private fun OpenToSpeakToggleCard(
    userInitial: String,
    userImageUrl: String?,
    openToSpeak: Boolean,
    languageName: String,
    level: String,
    onToggle: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .clickable(role = Role.Switch, onClick = { onToggle(!openToSpeak) })
            .padding(start = ZunoSpace.s12, end = ZunoSpace.s14, top = ZunoSpace.s10, bottom = ZunoSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoOpenToSpeakAvatar(
            initial = userInitial,
            imageUrl = userImageUrl,
            size = 38.dp,
            openToSpeak = openToSpeak,
            inverseInner = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = stringResource(R.string.chats_open_to_speak),
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = if (openToSpeak) {
                    stringResource(R.string.chats_open_to_speak_on_sub, languageName, level)
                } else {
                    stringResource(R.string.chats_open_to_speak_off_sub)
                },
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZunoSwitch(
            checked = openToSpeak,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
fun ChatsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    val shape = RoundedCornerShape(ZunoRadius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .zunoGlassSubtle(
                shape = shape,
                borderBrush = if (query.isNotEmpty()) SolidColor(ZunoColors.BorderFocus) else ZunoGlassBorderSubtleBrush,
            )
            .padding(horizontal = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_t_search, 16.dp, tint = ZunoColors.IconSecondary)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = ZunoType.BodySmall.copy(color = ZunoColors.TextPrimary),
            cursorBrush = SolidColor(ZunoColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = ZunoType.BodySmall,
                            color = ZunoColors.TextPlaceholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = { onQueryChange("") }),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_x, 16.dp, tint = ZunoColors.IconSecondary, contentDescription = stringResource(R.string.common_clear))
            }
        }
    }
}

@Composable
private fun ChatsFilterChipsRow(
    selected: ChatsFilter,
    onSelect: (ChatsFilter) -> Unit,
) {
    val tabs = listOf(
        ChatsFilter.All to stringResource(R.string.chats_filter_all),
        ChatsFilter.AiTutor to stringResource(R.string.chats_filter_ai_tutor),
        ChatsFilter.People to stringResource(R.string.chats_filter_people),
        ChatsFilter.Open to stringResource(R.string.chats_filter_open),
    )
    ZunoSegmentedTabs(
        labels = tabs.map { it.second },
        selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onSelect = { idx -> onSelect(tabs[idx].first) },
    )
}

@Composable
private fun StartAiChatCard(onChat: () -> Unit) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .clickable(role = Role.Button, onClick = onChat)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoAiTutorAvatar(size = 40.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Text(
                text = stringResource(R.string.chats_start_ai_title),
                style = ZunoType.BodyMediumStrong,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.chats_start_ai_sub),
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZunoButton(
            label = stringResource(R.string.chats_action_chat),
            onClick = onChat,
            size = ZunoButtonSize.Small,
        )
    }
}

@Composable
private fun ConversationsSection(
    header: String?,
    items: List<UnifiedChatItem>,
    onClickItem: (UnifiedChatItem) -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextSecondary,
                modifier = Modifier.padding(start = ZunoSpace.s4),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    ZunoGlassHairline()
                }
                UnifiedConversationRow(item = item, onClick = { onClickItem(item) })
            }
        }
    }
}

@Composable
private fun UnifiedConversationRow(
    item: UnifiedChatItem,
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
        if (item.kind == UnifiedChatKind.Ai) {
            ZunoAiTutorAvatar(size = 44.dp)
        } else {
            ZunoOpenToSpeakAvatar(
                initial = item.avatarInitial,
                imageUrl = item.avatarUrl,
                size = 44.dp,
                openToSpeak = item.openToSpeak,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = ZunoType.BodyMediumStrong,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.badge?.let { badgeLabel ->
                        val badgeIcon = when {
                            item.kind == UnifiedChatKind.Ai && item.isVoice -> R.drawable.zuno_ic_t_audio_lines
                            item.kind == UnifiedChatKind.Ai && badgeLabel == "Text" -> R.drawable.zuno_ic_messages_square
                            item.kind == UnifiedChatKind.Ai -> R.drawable.zuno_ic_t_sparkles
                            else -> null
                        }
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (item.kind == UnifiedChatKind.Ai) ZunoColors.SurfaceStrong else ZunoColors.SurfaceDefault)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (badgeIcon != null) {
                                ZunoIcon(badgeIcon, 10.dp, tint = ZunoColors.IconSecondary)
                            }
                            Text(
                                text = badgeLabel,
                                style = ZunoType.Micro,
                                color = if (item.kind == UnifiedChatKind.Ai) ZunoColors.TextSecondary else ZunoColors.TextPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.pinned) {
                        ZunoIcon(
                            icon = R.drawable.zuno_ic_t_pin,
                            size = 12.dp,
                            tint = ZunoColors.IconSecondary,
                        )
                    }
                    Text(
                        text = formatCompactTime(item.updatedAtMillis, capitalizeNow = item.pinned),
                        style = ZunoType.FootnoteRegular,
                        color = if (item.unread) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.snippet,
                    style = ZunoType.FootnoteRegular,
                    color = if (item.unread) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.unread) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ZunoColors.SurfaceInverse),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddedPartnersSection(
    partners: List<PeerLearner>,
    actionLabel: String,
    onAction: (PeerLearner) -> Unit,
    onRowClick: (PeerLearner) -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        Text(
            text = stringResource(R.string.chats_added_partners),
            style = ZunoType.LabelMedium,
            color = ZunoColors.TextSecondary,
            modifier = Modifier.padding(start = ZunoSpace.s4),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape),
        ) {
            partners.forEachIndexed { index, partner ->
                if (index > 0) {
                    ZunoGlassHairline()
                }
                LearnerActionRow(
                    learner = partner,
                    subtitle = "${partner.languageName} ${partner.level} · ${partner.activeLabel}",
                    actionLabel = actionLabel,
                    onRowClick = { onRowClick(partner) },
                    onAction = { onAction(partner) },
                )
            }
        }
    }
}

@Composable
private fun OpenSpeakersFullListSection(
    speakers: List<PeerLearner>,
    onView: (PeerLearner) -> Unit,
    header: String? = null,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextSecondary,
                modifier = Modifier.padding(start = ZunoSpace.s4),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape),
        ) {
            speakers.forEachIndexed { index, speaker ->
                if (index > 0) {
                    ZunoGlassHairline()
                }
                val interests = speaker.interests.take(2).joinToString(", ")
                val subtitle = if (interests.isNotBlank()) {
                    "${speaker.languageName} ${speaker.level} · $interests"
                } else {
                    "${speaker.languageName} ${speaker.level} · ${speaker.activeLabel}"
                }
                LearnerActionRow(
                    learner = speaker,
                    subtitle = subtitle,
                    actionLabel = stringResource(R.string.chats_action_view),
                    onRowClick = { onView(speaker) },
                    onAction = { onView(speaker) },
                )
            }
        }
    }
}

@Composable
private fun LearnerActionRow(
    learner: PeerLearner,
    subtitle: String,
    actionLabel: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onRowClick)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoOpenToSpeakAvatar(
            initial = learner.initial,
            imageUrl = learner.imageUrl,
            size = 40.dp,
            openToSpeak = learner.openToSpeak,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Text(
                text = learner.name,
                style = ZunoType.BodyMediumStrong,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZunoButton(
            label = actionLabel,
            onClick = onAction,
            variant = ZunoButtonVariant.Secondary,
            size = ZunoButtonSize.Small,
        )
    }
}

@Composable
private fun ChatsEmptyHero(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    ctaLabel: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16, Alignment.CenterVertically),
    ) {
        ZunoEmptyState(icon = icon, title = title, description = description)
        ZunoButton(
            label = ctaLabel,
            onClick = onCta,
            variant = ZunoButtonVariant.Primary,
            size = ZunoButtonSize.Medium,
        )
    }
}

@Composable
private fun ChatsEmptySection(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    quickStartsTitle: String,
    quickStarts: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s48),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s40),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ZunoEmptyState(icon = icon, title = title, description = description)
        }

        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            Text(
                text = quickStartsTitle,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextSecondary,
                modifier = Modifier.padding(start = ZunoSpace.s4),
            )
            val cardShape = RoundedCornerShape(ZunoRadius.xl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(cardShape),
            ) {
                quickStarts()
            }
        }
    }
}

@Composable
private fun QuickStartAiRow(onClick: () -> Unit) {
    QuickStartRow(
        leading = { ZunoAiTutorAvatar(size = 44.dp) },
        title = stringResource(R.string.chats_start_ai_title),
        badge = stringResource(R.string.chats_filter_ai_tutor),
        trailing = "Anytime",
        subtitle = stringResource(R.string.chats_sofia_call_or_chat),
        onClick = onClick,
    )
}

@Composable
private fun QuickStartOpenSpeakersRow(speakers: List<PeerLearner>, onClick: () -> Unit) {
    ZunoGlassHairline()
    val first = speakers.firstOrNull()
    QuickStartRow(
        leading = {
            ZunoOpenToSpeakAvatar(
                initial = first?.initial ?: "L",
                size = 44.dp,
                openToSpeak = true,
            )
        },
        title = "${speakers.size} learners online",
        badge = stringResource(R.string.chats_open_to_speak_badge),
        trailing = "Live",
        subtitle = "${first?.languageName ?: "Spanish"} · Ready for 1:1 practice",
        onClick = onClick,
    )
}

@Composable
private fun QuickStartRow(
    leading: @Composable () -> Unit,
    title: String,
    badge: String?,
    trailing: String,
    subtitle: String,
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
        leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = ZunoType.Micro,
                        color = ZunoColors.TextSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ZunoColors.SurfaceStrong)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = trailing,
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                )
            }
            Text(
                text = subtitle,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `Chats · 3 · New Chat Sheet`: AI Tutor + Open speakers right now + Added partners.
 */
@Composable
private fun NewChatSheet(
    openSpeakers: List<PeerLearner>,
    partners: List<PeerLearner>,
    onDismiss: () -> Unit,
    onChatAi: () -> Unit,
    onCallAi: () -> Unit,
    onTalkSpeaker: (PeerLearner) -> Unit,
    onMessagePartner: (PeerLearner) -> Unit,
    onSelectMember: (PeerLearner) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filteredSpeakers = openSpeakers.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    val filteredPartners = partners.filter { q.isEmpty() || it.name.lowercase().contains(q) }

    ZunoSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.chats_new_chat),
        spacing = ZunoSpace.s14,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            ChatsSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.chats_new_sheet_search_placeholder),
            )

            val aiCardShape = RoundedCornerShape(ZunoRadius.xl)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(aiCardShape)
                    .clickable(role = Role.Button, onClick = onChatAi)
                    .padding(ZunoSpace.s14),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val activeTutor = LocalActiveTutor.current
                ZunoAiTutorAvatar(size = 42.dp, tutor = activeTutor)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                    Text(
                        text = "${activeTutor.displayName} · ${stringResource(R.string.chats_filter_ai_tutor)}",
                        style = ZunoType.BodyMediumStrong,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.chats_sofia_call_or_chat),
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoButton(
                        label = stringResource(R.string.chats_action_chat),
                        onClick = onChatAi,
                        size = ZunoButtonSize.Small,
                    )
                    ZunoIconButton(
                        icon = R.drawable.zuno_ic_t_phone,
                        onClick = onCallAi,
                        style = ZunoIconButtonStyle.Outline,
                        size = ZunoIconButtonSize.Small,
                        tint = ZunoColors.IconPrimary,
                        contentDescription = stringResource(R.string.chats_action_talk),
                    )
                }
            }

            if (filteredSpeakers.isNotEmpty()) {
                val listShape = RoundedCornerShape(ZunoRadius.xl)
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    Text(
                        text = stringResource(R.string.chats_open_right_now_header),
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextSecondary,
                        modifier = Modifier.padding(start = ZunoSpace.s4),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(listShape),
                    ) {
                        filteredSpeakers.take(4).forEachIndexed { idx, speaker ->
                            if (idx > 0) ZunoGlassHairline()
                            LearnerActionRow(
                                learner = speaker,
                                subtitle = "${speaker.languageName} ${speaker.level} · ${speaker.activeLabel}",
                                actionLabel = stringResource(R.string.chats_action_talk),
                                onRowClick = { onSelectMember(speaker) },
                                onAction = { onTalkSpeaker(speaker) },
                            )
                        }
                    }
                }
            }

            if (filteredPartners.isNotEmpty()) {
                val listShape = RoundedCornerShape(ZunoRadius.xl)
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    Text(
                        text = stringResource(R.string.chats_added_partners),
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextSecondary,
                        modifier = Modifier.padding(start = ZunoSpace.s4),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(listShape),
                    ) {
                        filteredPartners.forEachIndexed { idx, partner ->
                            if (idx > 0) ZunoGlassHairline()
                            LearnerActionRow(
                                learner = partner,
                                subtitle = "${partner.languageName} ${partner.level} · ${partner.activeLabel}",
                                actionLabel = stringResource(R.string.chats_action_message),
                                onRowClick = { onSelectMember(partner) },
                                onAction = { onMessagePartner(partner) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * `Chats · 9 · Member Profile & Open to Speak Sheet`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberProfileSheet(
    member: PeerLearner,
    onDismiss: () -> Unit,
    onInviteToTalk: () -> Unit,
    onSendMessage: () -> Unit,
    onTogglePartner: (Boolean) -> Unit,
) {
    ZunoSheet(
        onDismiss = onDismiss,
        title = "",
        spacing = ZunoSpace.s16,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoOpenToSpeakAvatar(
                initial = member.initial,
                imageUrl = member.imageUrl,
                size = 72.dp,
                openToSpeak = member.openToSpeak,
            )

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, ZunoColors.BorderSubtle, CircleShape)
                    .padding(horizontal = ZunoSpace.s10, vertical = ZunoSpace.s4),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (member.openToSpeak) ZunoColors.StatusSpeaking else ZunoColors.IconTertiary),
                )
                Text(
                    text = "${if (member.openToSpeak) stringResource(R.string.chats_open_to_speak) else member.activeLabel} · ${member.languageName} ${member.level}",
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
            ) {
                Text(
                    text = member.name,
                    style = ZunoType.Headline,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.member_stats,
                        "Native ${member.nativeLanguage}",
                        member.streakDays,
                        member.sessionsCount,
                    ),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                )
            }

            if (member.interests.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    member.interests.forEach { interest ->
                        Text(
                            text = interest,
                            style = ZunoType.FootnoteStrong,
                            color = ZunoColors.TextSecondary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ZunoColors.SurfaceSubtle)
                                .border(1.dp, ZunoColors.BorderSubtle, CircleShape)
                                .padding(horizontal = ZunoSpace.s12, vertical = 5.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                ZunoButton(
                    label = stringResource(R.string.member_invite_to_talk),
                    onClick = onInviteToTalk,
                    leadingIcon = R.drawable.zuno_ic_t_phone,
                    leadingIconTint = ZunoColors.IconOnInverse,
                    variant = ZunoButtonVariant.Primary,
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoButton(
                        label = stringResource(R.string.member_send_message),
                        onClick = onSendMessage,
                        leadingIcon = R.drawable.zuno_ic_t_message_circle,
                        leadingIconTint = ZunoColors.IconPrimary,
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Large,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (member.isPartner) ZunoColors.SurfaceStrong else ZunoColors.SurfaceSubtle)
                            .border(1.dp, ZunoColors.BorderDefault, CircleShape)
                            .clickable(role = Role.Button, onClick = { onTogglePartner(!member.isPartner) })
                            .semantics {
                                contentDescription = if (member.isPartner) "Remove partner" else "Add partner"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoIcon(
                            icon = if (member.isPartner) R.drawable.zuno_ic_t_check else R.drawable.zuno_ic_user_plus,
                            size = 20.dp,
                            tint = ZunoColors.IconPrimary,
                        )
                    }
                }
            }
        }
    }
}

internal fun formatCompactTime(millis: Long, capitalizeNow: Boolean = false): String {
    val diffMs = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    return when {
        mins < 1 -> if (capitalizeNow) "Now" else "now"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        else -> "${mins / (60 * 24)}d"
    }
}
