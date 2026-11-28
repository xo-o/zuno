package com.zuno.ai.ui.screens

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zuno.ai.R
import com.zuno.ai.avatar.catalog.LocalActiveTutor
import com.zuno.ai.avatar.catalog.TutorAvatarCatalog
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.data.ChatsFilter
import com.zuno.ai.data.ChatsOverview
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.data.UnifiedChatItem
import com.zuno.ai.data.UnifiedChatKind
import com.zuno.ai.ui.components.HomeNavTab
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
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout
import com.zuno.ai.viewmodel.Loadable

@Composable
fun ChatsScreen(
    overview: Loadable<ChatsOverview>,
    initialFilter: ChatsFilter,
    onRetry: () -> Unit,
    onOpenAiConversation: (String) -> Unit,
    onStartAiFriendChat: (String) -> Unit,
    onStartAiFriendCall: (String, Boolean) -> Unit,
    onOpenPeerChat: (PeerLearner) -> Unit,
    onInvitePeerToTalk: (PeerLearner) -> Unit,
    onTogglePartner: (PeerLearner, Boolean) -> Unit,
    onNavigateTab: (HomeNavTab) -> Unit = {},
    onBack: () -> Unit = { onNavigateTab(HomeNavTab.Home) },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showNewChatSheet by rememberSaveable { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<PeerLearner?>(null) }
    var selectedAiFriend by remember { mutableStateOf<TutorAvatarProfile?>(null) }

    val data = overview.items
    // Keep selectedMember in sync with latest overview data (e.g. after toggling partner)
    val activeMember = selectedMember?.let { current ->
        data?.openSpeakers?.firstOrNull { it.id == current.id }
            ?: data?.partners?.firstOrNull { it.id == current.id }
            ?: current
    }

    val allAiFriends = TutorAvatarCatalog.CHATS_FRIENDS_ORDER
    val query = searchQuery.trim().lowercase()

    val filteredConversations = remember(data, query) {
        data?.conversations.orEmpty().filter { item ->
            query.isEmpty() ||
                item.title.lowercase().contains(query) ||
                item.conversationTitle.orEmpty().lowercase().contains(query) ||
                item.snippet.lowercase().contains(query)
        }
    }

    val filteredAiFriends = remember(allAiFriends, query, context) {
        allAiFriends.filter { friend ->
            if (query.isEmpty()) {
                true
            } else {
                friend.displayName.lowercase().contains(query) ||
                    context.getString(friend.traitRes).lowercase().contains(query) ||
                    context.getString(friend.chatSubtitleRes).lowercase().contains(query) ||
                    friend.topicChipRes.any { context.getString(it).lowercase().contains(query) }
            }
        }
    }

    val partnerIds = remember(data) { data?.partners.orEmpty().map { it.id }.toSet() }
    val filteredPartners = remember(data, query) {
        data?.partners.orEmpty().filter { learner ->
            query.isEmpty() ||
                learner.name.lowercase().contains(query) ||
                learner.level.lowercase().contains(query) ||
                learner.nativeLanguage.lowercase().contains(query) ||
                learner.interests.any { it.lowercase().contains(query) }
        }
    }
    val filteredOpenSpeakers = remember(data, partnerIds, query) {
        data?.openSpeakers.orEmpty()
            .filterNot { it.id in partnerIds }
            .filter { learner ->
                query.isEmpty() ||
                    learner.name.lowercase().contains(query) ||
                    learner.level.lowercase().contains(query) ||
                    learner.nativeLanguage.lowercase().contains(query) ||
                    learner.interests.any { it.lowercase().contains(query) }
            }
    }

    val isConversationsEmpty = data != null &&
        selectedFilter == ChatsFilter.Conversations &&
        data.conversations.isEmpty() &&
        query.isEmpty()

    val showSearch = searchVisible || (data != null && !isConversationsEmpty)

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
                    contentPadding = PaddingValues(
                        start = zunoLayout.screenPaddingH,
                        end = zunoLayout.screenPaddingH,
                        top = ZunoSpace.s6,
                        bottom = ZunoSpace.s32,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                ) {
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

                    val noSearchResults = query.isNotEmpty() && when (selectedFilter) {
                        ChatsFilter.Conversations -> filteredConversations.isEmpty()
                        ChatsFilter.AiFriends -> filteredAiFriends.isEmpty()
                        ChatsFilter.People -> filteredPartners.isEmpty() && filteredOpenSpeakers.isEmpty()
                    }

                    if (noSearchResults) {
                        item(key = "no-results") {
                            Column(
                                modifier = Modifier
                                    .fillParentMaxHeight(0.72f)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16, Alignment.CenterVertically),
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
                            ChatsFilter.Conversations -> {
                                if (filteredConversations.isEmpty()) {
                                    item(key = "empty-conversations") {
                                        EmptyConversationsSection(
                                            openSpeakers = data.openSpeakers,
                                            onStartChatting = { selectedFilter = ChatsFilter.AiFriends },
                                            onBrowsePeople = { selectedFilter = ChatsFilter.People },
                                            modifier = Modifier.fillParentMaxHeight(0.76f),
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
                                                    streakDays = 0,
                                                    sessionsCount = 0,
                                                    interests = emptyList(),
                                                    openToSpeak = item.openToSpeak,
                                                    online = item.openToSpeak,
                                                    isPartner = false,
                                                    activeLabel = if (item.openToSpeak) "Open to speak" else "",
                                                )
                                            onOpenPeerChat(partner)
                                        }
                                    }

                                    if (query.isNotEmpty()) {
                                        item(key = "search-conv-matches") {
                                            ConversationsSection(
                                                header = stringResource(R.string.chats_matches_in_conversations),
                                                trailingMeta = filteredConversations.size.toString(),
                                                items = filteredConversations,
                                                onClickItem = openUnifiedChat,
                                            )
                                        }
                                    } else {
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
                            }

                            ChatsFilter.AiFriends -> {
                                item(key = "ai-friends-list") {
                                    AiFriendsSection(
                                        header = if (query.isNotEmpty()) {
                                            stringResource(R.string.chats_matches_in_ai_friends)
                                        } else {
                                            stringResource(R.string.chats_ai_friends_header)
                                        },
                                        trailingMeta = if (query.isNotEmpty()) filteredAiFriends.size.toString() else null,
                                        friends = filteredAiFriends,
                                        onSelectFriend = { selectedAiFriend = it },
                                        onChatFriend = { onStartAiFriendChat(it.id) },
                                        onCallFriend = { onStartAiFriendCall(it.id, false) },
                                    )
                                }
                            }

                            ChatsFilter.People -> {
                                if (filteredPartners.isEmpty() && filteredOpenSpeakers.isEmpty()) {
                                    item(key = "empty-people") {
                                        ChatsEmptyHero(
                                            icon = R.drawable.zuno_ic_users,
                                            title = stringResource(R.string.chats_empty_people_title),
                                            description = stringResource(R.string.chats_empty_people_body),
                                            ctaLabel = stringResource(R.string.chats_empty_people_cta),
                                            onCta = { selectedFilter = ChatsFilter.AiFriends },
                                            modifier = Modifier.fillParentMaxHeight(0.75f),
                                        )
                                    }
                                } else {
                                    if (filteredPartners.isNotEmpty()) {
                                        item(key = "people-partners") {
                                            PeopleListSection(
                                                header = stringResource(R.string.chats_partners_header),
                                                trailingMeta = stringResource(R.string.chats_partners_saved),
                                                learners = filteredPartners,
                                                isPartnersSection = true,
                                                onSelectMember = { selectedMember = it },
                                                onChatMember = onOpenPeerChat,
                                                onCallMember = onInvitePeerToTalk,
                                            )
                                        }
                                    }
                                    if (filteredOpenSpeakers.isNotEmpty()) {
                                        item(key = "people-open") {
                                            PeopleListSection(
                                                header = stringResource(R.string.chats_open_to_speak),
                                                trailingMeta = stringResource(R.string.chats_online_count, filteredOpenSpeakers.size),
                                                learners = filteredOpenSpeakers,
                                                isPartnersSection = false,
                                                onSelectMember = { selectedMember = it },
                                                onChatMember = onOpenPeerChat,
                                                onCallMember = onInvitePeerToTalk,
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
    }

    if (showNewChatSheet && data != null) {
        NewChatSheet(
            aiFriends = allAiFriends,
            partners = data.partners,
            openSpeakers = data.openSpeakers.filterNot { it.id in partnerIds },
            onDismiss = { showNewChatSheet = false },
            onSelectAiFriend = { friend ->
                showNewChatSheet = false
                onStartAiFriendChat(friend.id)
            },
            onChatPeer = { learner ->
                showNewChatSheet = false
                onOpenPeerChat(learner)
            },
            onCallPeer = { learner ->
                showNewChatSheet = false
                onInvitePeerToTalk(learner)
            },
            onSelectMember = { learner ->
                showNewChatSheet = false
                selectedMember = learner
            },
        )
    }

    if (selectedAiFriend != null && data != null) {
        val friend = selectedAiFriend!!
        AiFriendProfileSheet(
            friend = friend,
            languageName = data.languageName,
            level = data.level,
            onDismiss = { selectedAiFriend = null },
            onStartVoiceCall = {
                selectedAiFriend = null
                onStartAiFriendCall(friend.id, false)
            },
            onMessage = {
                selectedAiFriend = null
                onStartAiFriendChat(friend.id)
            },
            onStartVideoCall = {
                selectedAiFriend = null
                onStartAiFriendCall(friend.id, true)
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
                fillColor = ZunoColors.SurfaceControlTrack,
                borderBrush = SolidColor(if (query.isNotEmpty()) ZunoColors.BorderFocus else ZunoColors.BorderDefault),
            )
            .padding(horizontal = zunoLayout.cardPaddingH),
        horizontalArrangement = Arrangement.spacedBy(zunoLayout.rowGap),
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
                ZunoIcon(
                    R.drawable.zuno_ic_t_x,
                    16.dp,
                    tint = ZunoColors.IconSecondary,
                    contentDescription = stringResource(R.string.common_clear),
                )
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
        ChatsFilter.Conversations to stringResource(R.string.chats_filter_conversations),
        ChatsFilter.AiFriends to stringResource(R.string.chats_filter_ai_friends),
        ChatsFilter.People to stringResource(R.string.chats_filter_people),
    )
    ZunoSegmentedTabs(
        labels = tabs.map { it.second },
        selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        onSelect = { idx -> onSelect(tabs[idx].first) },
    )
}

@Composable
private fun SectionHeaderRow(
    title: String,
    trailingMeta: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ZunoType.LabelMedium,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!trailingMeta.isNullOrBlank()) {
            Text(
                text = trailingMeta,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TagBadgePill(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(ZunoColors.SurfaceStrong)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ZunoType.Micro,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConversationsSection(
    header: String?,
    items: List<UnifiedChatItem>,
    onClickItem: (UnifiedChatItem) -> Unit,
    trailingMeta: String? = null,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        if (!header.isNullOrBlank()) {
            SectionHeaderRow(title = header, trailingMeta = trailingMeta)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape, fillColor = ZunoColors.SurfaceDefault),
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
    val defaultTutor = LocalActiveTutor.current
    val aiTutor = remember(item.tutorId, defaultTutor) {
        item.tutorId?.let { TutorAvatarCatalog.resolve(it) } ?: defaultTutor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = zunoLayout.cardPaddingH, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(zunoLayout.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarSize = if (zunoLayout.isCompact) 40.dp else 44.dp
        if (item.kind == UnifiedChatKind.Ai) {
            ZunoAiTutorAvatar(size = avatarSize, tutor = aiTutor)
        } else {
            ZunoOpenToSpeakAvatar(
                initial = item.avatarInitial,
                imageUrl = item.avatarUrl,
                size = avatarSize,
                openToSpeak = false,
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
                    if (item.kind == UnifiedChatKind.Ai) {
                        TagBadgePill("AI")
                    } else if (item.openToSpeak) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ZunoColors.StatusSpeaking),
                        )
                    }
                }
                Text(
                    text = formatCompactTime(item.updatedAtMillis, capitalizeNow = item.pinned),
                    style = ZunoType.FootnoteRegular,
                    color = if (item.unread) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                    maxLines = 1,
                )
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

/**
 * `Chats · 2 · AI Friends` (`2842:15221`): 6 personalities in a clean list card with Chat + Call actions.
 */
@Composable
private fun AiFriendsSection(
    header: String,
    trailingMeta: String?,
    friends: List<TutorAvatarProfile>,
    onSelectFriend: (TutorAvatarProfile) -> Unit,
    onChatFriend: (TutorAvatarProfile) -> Unit,
    onCallFriend: (TutorAvatarProfile) -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        SectionHeaderRow(title = header, trailingMeta = trailingMeta)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape, fillColor = ZunoColors.SurfaceDefault),
        ) {
            friends.forEachIndexed { index, friend ->
                if (index > 0) {
                    ZunoGlassHairline()
                }
                AiFriendRow(
                    friend = friend,
                    onRowClick = { onSelectFriend(friend) },
                    onChat = { onChatFriend(friend) },
                    onCall = { onCallFriend(friend) },
                )
            }
        }
    }
}

@Composable
private fun AiFriendRow(
    friend: TutorAvatarProfile,
    onRowClick: () -> Unit,
    onChat: () -> Unit,
    onCall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onRowClick)
            .padding(horizontal = zunoLayout.cardPaddingH, vertical = ZunoSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(zunoLayout.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoAiTutorAvatar(size = 40.dp, tutor = friend)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = friend.displayName,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TagBadgePill(stringResource(friend.traitRes))
            }
            Text(
                text = stringResource(friend.chatSubtitleRes),
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoButton(
                label = stringResource(R.string.chats_action_chat),
                onClick = onChat,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Small,
            )
            ZunoIconButton(
                icon = R.drawable.zuno_ic_t_phone,
                onClick = onCall,
                style = ZunoIconButtonStyle.Subtle,
                size = ZunoIconButtonSize.Small,
                tint = ZunoColors.IconPrimary,
                contentDescription = stringResource(R.string.chats_action_talk),
            )
        }
    }
}

/**
 * `Chats · 3 · People` (`2875:16785`) & `Chats · 7 · People — No Partners Yet` (`2869:16035`).
 */
@Composable
private fun PeopleListSection(
    header: String,
    trailingMeta: String?,
    learners: List<PeerLearner>,
    isPartnersSection: Boolean,
    onSelectMember: (PeerLearner) -> Unit,
    onChatMember: (PeerLearner) -> Unit,
    onCallMember: (PeerLearner) -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
        SectionHeaderRow(title = header, trailingMeta = trailingMeta)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape, fillColor = ZunoColors.SurfaceDefault),
        ) {
            learners.forEachIndexed { index, learner ->
                if (index > 0) {
                    ZunoGlassHairline()
                }
                val isOpenOnline = learner.openToSpeak && learner.online
                val interests = learner.interests.take(2).joinToString(" & ")
                val subtitle = if (isPartnersSection) {
                    val statusText = if (isOpenOnline) {
                        stringResource(R.string.chats_open_to_speak)
                    } else {
                        learner.activeLabel.ifBlank { "${learner.languageName} ${learner.level}" }
                    }
                    if (interests.isNotBlank()) "$statusText · $interests" else statusText
                } else {
                    val origin = learner.nativeLanguage.ifBlank { learner.languageName }
                    if (interests.isNotBlank()) "$origin · $interests" else "$origin · ${learner.activeLabel}"
                }
                PersonActionRow(
                    learner = learner,
                    subtitle = subtitle,
                    onRowClick = { onSelectMember(learner) },
                    onChat = { onChatMember(learner) },
                    onCall = { onCallMember(learner) },
                )
            }
        }
    }
}

@Composable
private fun PersonActionRow(
    learner: PeerLearner,
    subtitle: String,
    onRowClick: () -> Unit,
    onChat: () -> Unit,
    onCall: () -> Unit,
) {
    val isOpenOnline = learner.openToSpeak && learner.online
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onRowClick)
            .padding(horizontal = zunoLayout.cardPaddingH, vertical = ZunoSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(zunoLayout.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoOpenToSpeakAvatar(
            initial = learner.initial,
            imageUrl = learner.imageUrl,
            size = 40.dp,
            openToSpeak = false,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = learner.name,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (learner.level.isNotBlank()) {
                    TagBadgePill(learner.level)
                }
                if (isOpenOnline) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ZunoColors.StatusSpeaking),
                    )
                }
            }
            Text(
                text = subtitle,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoButton(
                label = stringResource(R.string.chats_action_chat),
                onClick = onChat,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Small,
            )
            ZunoIconButton(
                icon = R.drawable.zuno_ic_t_phone,
                onClick = onCall,
                style = ZunoIconButtonStyle.Subtle,
                size = ZunoIconButtonSize.Small,
                tint = ZunoColors.IconPrimary,
                contentDescription = stringResource(R.string.chats_action_talk),
            )
        }
    }
}

/**
 * `Chats · 6 · Empty — Conversations` (`3091:14336`, `Empty · T1 · 5-Avatar Row`).
 */
@Composable
private fun EmptyConversationsSection(
    openSpeakers: List<PeerLearner>,
    onStartChatting: () -> Unit,
    onBrowsePeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarSlots = remember(openSpeakers) {
        val friends = TutorAvatarCatalog.CHATS_FRIENDS_ORDER
        val peers = openSpeakers.take(2)
        buildList<Any> {
            add(friends[0])
            add(friends[1])
            if (peers.isNotEmpty()) add(peers[0]) else add(friends[2])
            add(friends[3])
            if (peers.size > 1) add(peers[1]) else add(friends[4])
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14, Alignment.CenterVertically),
    ) {
        // Overlapping 5-avatar stack (40dp circles, -10dp overlap, 2dp BgCanvas border)
        val avatarSize = 40.dp
        val step = 30.dp
        val totalWidth = avatarSize + step * (avatarSlots.size - 1)
        Box(
            modifier = Modifier
                .width(totalWidth)
                .height(avatarSize),
        ) {
            avatarSlots.forEachIndexed { idx, slot ->
                Box(
                    modifier = Modifier
                        .offset(x = step * idx)
                        .zIndex(idx.toFloat())
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(ZunoColors.BgCanvas)
                        .border(2.dp, ZunoColors.BgCanvas, CircleShape)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (slot) {
                        is TutorAvatarProfile -> ZunoAiTutorAvatar(size = 36.dp, tutor = slot)
                        is PeerLearner -> ZunoOpenToSpeakAvatar(
                            initial = slot.initial,
                            imageUrl = slot.imageUrl,
                            size = 36.dp,
                            openToSpeak = false,
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
        ) {
            Text(
                text = stringResource(R.string.chats_empty_conversations_title),
                style = ZunoType.TitleMedium,
                color = ZunoColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.chats_empty_conversations_body),
                style = ZunoType.BodySmall,
                color = ZunoColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.padding(top = ZunoSpace.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        ) {
            ZunoButton(
                label = stringResource(R.string.chats_empty_conversations_cta),
                onClick = onStartChatting,
                variant = ZunoButtonVariant.Primary,
                size = ZunoButtonSize.Medium,
            )
            val browseLabel = if (openSpeakers.isNotEmpty()) {
                stringResource(R.string.chats_empty_conversations_browse_online, openSpeakers.size)
            } else {
                stringResource(R.string.chats_empty_conversations_browse_people)
            }
            Text(
                text = browseLabel,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBrowsePeople)
                    .padding(horizontal = ZunoSpace.s10, vertical = ZunoSpace.s4),
            )
        }
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

/**
 * `Chats · 9 · New Chat Sheet` (`2842:15430`): Search + horizontal AI Friends cards + Partners + Open to speak.
 */
@Composable
private fun NewChatSheet(
    aiFriends: List<TutorAvatarProfile>,
    partners: List<PeerLearner>,
    openSpeakers: List<PeerLearner>,
    onDismiss: () -> Unit,
    onSelectAiFriend: (TutorAvatarProfile) -> Unit,
    onChatPeer: (PeerLearner) -> Unit,
    onCallPeer: (PeerLearner) -> Unit,
    onSelectMember: (PeerLearner) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filteredFriends = aiFriends.filter { friend ->
        q.isEmpty() ||
            friend.displayName.lowercase().contains(q) ||
            context.getString(friend.traitRes).lowercase().contains(q) ||
            context.getString(friend.chatSubtitleRes).lowercase().contains(q)
    }
    val filteredPartners = partners.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    val filteredSpeakers = openSpeakers.filter {
        q.isEmpty() ||
            it.name.lowercase().contains(q) ||
            it.nativeLanguage.lowercase().contains(q) ||
            it.interests.any { tag -> tag.lowercase().contains(q) }
    }

    ZunoSheet(
        onDismiss = onDismiss,
        title = "",
        spacing = ZunoSpace.s14,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chats_new_chat),
                    style = ZunoType.Headline,
                    color = ZunoColors.TextPrimary,
                )
                ZunoIconButton(
                    icon = R.drawable.zuno_ic_t_x,
                    onClick = onDismiss,
                    style = ZunoIconButtonStyle.Ghost,
                    size = ZunoIconButtonSize.Small,
                    tint = ZunoColors.IconSecondary,
                    contentDescription = stringResource(R.string.common_clear),
                )
            }

            ChatsSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.chats_new_sheet_search_placeholder),
            )

            if (filteredFriends.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    SectionHeaderRow(title = stringResource(R.string.chats_filter_ai_friends))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    ) {
                        val cardShape = RoundedCornerShape(ZunoRadius.lg)
                        filteredFriends.forEach { friend ->
                            Column(
                                modifier = Modifier
                                    .width(84.dp)
                                    .zunoGlassSubtle(cardShape, fillColor = ZunoColors.SurfaceDefault)
                                    .clickable(role = Role.Button, onClick = { onSelectAiFriend(friend) })
                                    .padding(horizontal = ZunoSpace.s8, vertical = ZunoSpace.s12),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            ) {
                                ZunoAiTutorAvatar(size = 44.dp, tutor = friend)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = friend.displayName,
                                        style = ZunoType.LabelMedium,
                                        color = ZunoColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(friend.traitRes),
                                        style = ZunoType.Micro,
                                        color = ZunoColors.TextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filteredPartners.isNotEmpty()) {
                PeopleListSection(
                    header = stringResource(R.string.chats_partners_header),
                    trailingMeta = null,
                    learners = filteredPartners.take(4),
                    isPartnersSection = true,
                    onSelectMember = onSelectMember,
                    onChatMember = onChatPeer,
                    onCallMember = onCallPeer,
                )
            }

            if (filteredSpeakers.isNotEmpty()) {
                PeopleListSection(
                    header = stringResource(R.string.chats_open_to_speak),
                    trailingMeta = null,
                    learners = filteredSpeakers.take(4),
                    isPartnersSection = false,
                    onSelectMember = onSelectMember,
                    onChatMember = onChatPeer,
                    onCallMember = onCallPeer,
                )
            }
        }
    }
}

/**
 * `Chats · 10 · AI Friend Profile Sheet` (`2869:15727`).
 * Adapts pace & language/level display dynamically for any target language and CEFR level.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiFriendProfileSheet(
    friend: TutorAvatarProfile,
    languageName: String,
    level: String,
    onDismiss: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onMessage: () -> Unit,
    onStartVideoCall: () -> Unit,
) {
    val normLevel = level.trim().uppercase()
    val paceLabel = if (friend.isSlowPace || normLevel == "A1" || normLevel == "A2") {
        stringResource(R.string.ai_friend_pace_slow)
    } else {
        stringResource(R.string.ai_friend_pace_natural)
    }
    val languageSuffix = if (normLevel.isNotBlank()) "$languageName $normLevel" else languageName

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
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            ZunoAiTutorAvatar(size = 72.dp, tutor = friend)

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, ZunoColors.BorderSubtle, CircleShape)
                    .padding(horizontal = ZunoSpace.s12, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoIcon(
                    icon = R.drawable.zuno_ic_t_sparkles,
                    size = 14.dp,
                    tint = ZunoColors.IconPrimary,
                )
                Text(
                    text = stringResource(R.string.ai_friend_badge, stringResource(friend.taglineRes)),
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
                    text = friend.displayName,
                    style = ZunoType.TitleMedium,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = "$paceLabel · $languageSuffix",
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                )
            }

            Text(
                text = stringResource(friend.bioRes),
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            if (friend.topicChipRes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    friend.topicChipRes.forEach { chipRes ->
                        Text(
                            text = stringResource(chipRes),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ZunoSpace.s4),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                ZunoButton(
                    label = stringResource(R.string.ai_friend_start_voice_call),
                    onClick = onStartVoiceCall,
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
                        label = stringResource(R.string.ai_friend_message),
                        onClick = onMessage,
                        leadingIcon = R.drawable.zuno_ic_t_message_circle,
                        leadingIconTint = ZunoColors.IconPrimary,
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Large,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoButton(
                        label = stringResource(R.string.ai_friend_video_call),
                        onClick = onStartVideoCall,
                        leadingIcon = R.drawable.zuno_ic_t_video,
                        leadingIconTint = ZunoColors.IconPrimary,
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Large,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * `Chats · 11 · Member Profile & Open to Speak Sheet` (`2842:16082`).
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
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            ZunoOpenToSpeakAvatar(
                initial = member.initial,
                imageUrl = member.imageUrl,
                size = 72.dp,
                openToSpeak = false,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ZunoSpace.s4),
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
                        label = stringResource(R.string.chats_action_message),
                        onClick = onSendMessage,
                        leadingIcon = R.drawable.zuno_ic_t_message_circle,
                        leadingIconTint = ZunoColors.IconPrimary,
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Large,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoButton(
                        label = stringResource(
                            if (member.isPartner) R.string.member_menu_partner_added else R.string.member_menu_add_partner,
                        ),
                        onClick = { onTogglePartner(!member.isPartner) },
                        leadingIcon = if (member.isPartner) R.drawable.zuno_ic_t_check else R.drawable.zuno_ic_user_plus,
                        leadingIconTint = ZunoColors.IconPrimary,
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Large,
                        modifier = Modifier.weight(1f),
                    )
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
