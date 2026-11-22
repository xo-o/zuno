package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.DiscoverRoleplay
import com.zuno.ai.data.DiscoverRoleplaysResult
import com.zuno.ai.data.DiscoverTopic
import com.zuno.ai.data.DiscoverTopicsResult
import com.zuno.ai.data.RecommendedTopic
import com.zuno.ai.data.Roleplay
import com.zuno.ai.data.TopicStatus
import com.zuno.ai.data.TopicSummary
import com.zuno.ai.ui.components.HomeNavTab
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoCollectionCard
import com.zuno.ai.ui.components.ZunoEmptyState
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoIconButtonStyle
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoSectionHeader
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassBorderSubtleBrush
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.viewmodel.Loadable
import kotlinx.coroutines.delay

fun TopicSummary.subtitle(): String = when (status) {
    TopicStatus.Completed -> "Completed"
    else -> "$completedLessons of $totalLessons lessons"
}

@Composable
fun TopicSummary.localizedSubtitle(): String {
    val base = subtitle()
    val publicSuffix = if (isPublic) " · ${stringResource(R.string.topics_public_badge)}" else ""
    val pinPrefix = if (pinned) "📌 " else ""
    return "$pinPrefix$base$publicSuffix"
}

@Composable
fun Roleplay.localizedSubtitle(): String {
    val base = "$userRole / $aiRole"
    val publicSuffix = if (isPublic) " · ${stringResource(R.string.topics_public_badge)}" else ""
    val pinPrefix = if (pinned) "📌 " else ""
    return "$pinPrefix$base$publicSuffix"
}

@Composable
fun topicCategoryLabel(category: String): String = when (category.lowercase()) {
    "travel" -> stringResource(R.string.topics_cat_travel)
    "food" -> stringResource(R.string.topics_cat_food)
    "work" -> stringResource(R.string.topics_cat_work)
    "daily_life" -> stringResource(R.string.topics_cat_daily_life)
    "grammar" -> stringResource(R.string.topics_cat_grammar)
    "culture" -> stringResource(R.string.topics_cat_culture)
    else -> stringResource(R.string.topics_cat_daily_life)
}

@Composable
fun roleplayCategoryLabel(category: String): String = when (category.lowercase()) {
    "travel" -> stringResource(R.string.topics_cat_travel)
    "food" -> stringResource(R.string.topics_cat_food)
    "work" -> stringResource(R.string.topics_cat_work)
    "housing" -> stringResource(R.string.roleplays_cat_housing)
    "health" -> stringResource(R.string.roleplays_cat_health)
    "social" -> stringResource(R.string.roleplays_cat_social)
    else -> stringResource(R.string.roleplays_cat_social)
}

/** Search Field (`2998:13121`): 42dp glass input with leading search icon and clear button. */
@Composable
internal fun ExploreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    val shape = RoundedCornerShape(ZunoRadius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .zunoGlassSubtle(
                shape = shape,
                borderBrush = if (focused) SolidColor(ZunoColors.BorderFocus) else ZunoGlassBorderSubtleBrush,
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_t_search, 16.dp, tint = ZunoColors.IconSecondary)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            interactionSource = interactionSource,
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
                    .clickable(role = Role.Button, onClick = { onQueryChange("") }),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_x, 16.dp, tint = ZunoColors.IconSecondary)
            }
        }
    }
}

/** Collection Card with a trailing `>` chevron (used in Explore & Empty-State Previews). */
@Composable
internal fun ExplorePreviewCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) ZunoGlassHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s12, top = ZunoSpace.s12, bottom = ZunoSpace.s12),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.md)),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(icon, 18.dp, tint = ZunoColors.IconPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
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
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 16.dp, tint = ZunoColors.IconTertiary)
        }
    }
}

/** 2-option modal bottom drawer (`07 · Add Topic Sheet`, `Roleplays · 4 · Add Roleplay Sheet`). */
@Composable
private fun AddChoiceSheet(
    title: String,
    createTitle: String,
    createSubtitle: String,
    findTitle: String,
    findSubtitle: String,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onExplore: () -> Unit,
) {
    ZunoSheet(onDismiss = onDismiss, title = title, spacing = ZunoSpace.s8) {
        Column(
            modifier = Modifier.padding(horizontal = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
        ) {
            AddChoiceRow(
                icon = R.drawable.zuno_ic_t_sparkles,
                title = createTitle,
                subtitle = createSubtitle,
                onClick = {
                    onDismiss()
                    onCreate()
                },
            )
            AddChoiceRow(
                icon = R.drawable.zuno_ic_t_globe,
                title = findTitle,
                subtitle = findSubtitle,
                onClick = {
                    onDismiss()
                    onExplore()
                },
            )
        }
    }
}

@Composable
private fun AddChoiceRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ZunoRadius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s12, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 18.dp, tint = ZunoColors.IconPrimary)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Text(title, style = ZunoType.BodyMedium, color = ZunoColors.TextPrimary)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
        ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 18.dp, tint = ZunoColors.IconTertiary)
    }
}

// ── Empty State Viewport (Centers Hero above optional anchored discovery) ───

@Composable
private fun EmptyStateViewport(
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = 92.dp + navBottom
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val topPadPx = with(density) { ZunoSpace.s12.roundToPx() }
        val bottomPadPx = with(density) { bottomPadding.roundToPx() }
        val gapPx = with(density) { ZunoSpace.s20.roundToPx() }
        val minInnerHeightPx = (with(density) { maxHeight.roundToPx() } - topPadPx - bottomPadPx).coerceAtLeast(0)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = bottomPadding),
        ) {
            Layout(
                content = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { hero() }
                    if (footer != null) {
                        Box(modifier = Modifier.fillMaxWidth()) { footer() }
                    }
                },
            ) { measurables, constraints ->
                val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                val heroPlaceable = measurables[0].measure(loose)
                val footerPlaceable = measurables.getOrNull(1)?.measure(loose)

                val neededHeight = if (footerPlaceable != null) {
                    heroPlaceable.height + gapPx + footerPlaceable.height
                } else {
                    heroPlaceable.height
                }
                val totalHeight = maxOf(minInnerHeightPx, neededHeight)

                val heroY = if (footerPlaceable != null) {
                    val stageHeight = (totalHeight - gapPx - footerPlaceable.height).coerceAtLeast(heroPlaceable.height)
                    (stageHeight - heroPlaceable.height) / 2
                } else {
                    (totalHeight - heroPlaceable.height) / 2
                }
                val footerY = totalHeight - (footerPlaceable?.height ?: 0)

                layout(constraints.maxWidth, totalHeight) {
                    heroPlaceable.placeRelative(0, heroY)
                    footerPlaceable?.placeRelative(0, footerY)
                }
            }
        }
    }
}

// ── 01 · Topics — List & 06 · Topics — Empty ────────────────────────────────

@Composable
fun TopicsScreen(
    topics: List<TopicSummary>?,
    loading: Boolean,
    error: String?,
    discoverPreview: List<DiscoverTopic>,
    menuForTopic: (TopicSummary) -> List<ZunoMenuAction>,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onExplore: (String) -> Unit,
    onOpenPreview: (String) -> Unit,
    onNavTabSelected: (HomeNavTab) -> Unit = {},
    onBack: () -> Unit = { onNavTabSelected(HomeNavTab.Home) },
    recommendedTopics: List<RecommendedTopic> = emptyList(),
    busy: Boolean = false,
    onSelectRecommended: (RecommendedTopic) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    val filtered = topics?.filter {
        query.isBlank() ||
            it.title.contains(query.trim(), ignoreCase = true) ||
            it.translation.contains(query.trim(), ignoreCase = true)
    }

    Box(modifier.fillMaxSize().zunoCanvasBackground()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ZunoSectionTopBar(
                title = stringResource(R.string.topics_title),
                onBack = onBack,
                addDescription = stringResource(R.string.topics_add_sheet_title),
                showSearch = !topics.isNullOrEmpty(),
                searchActive = searching,
                onSearchToggle = {
                    searching = !searching
                    if (!searching) query = ""
                },
                searchDescription = stringResource(R.string.chats_search),
                showAdd = true,
                onAdd = { showAddSheet = true },
            )

            when {
                topics == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (loading || error != null) ZunoLoadState(error, onRetry, Modifier.padding(ZunoSpace.s24))
                }
                topics.isEmpty() && !searching -> {
                    EmptyStateViewport(
                        modifier = Modifier.weight(1f),
                        hero = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = ZunoSpace.s32),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                            ) {
                                ZunoEmptyState(
                                    icon = R.drawable.zuno_ic_book_open,
                                    title = stringResource(R.string.topics_empty_title),
                                    description = stringResource(R.string.topics_empty_body),
                                )
                                ZunoButton(
                                    label = stringResource(R.string.topics_explore_cta),
                                    onClick = { onExplore("") },
                                    variant = ZunoButtonVariant.Secondary,
                                    size = ZunoButtonSize.Medium,
                                    leadingIcon = R.drawable.zuno_ic_t_search,
                                    leadingIconTint = ZunoColors.IconPrimary,
                                    modifier = Modifier.widthIn(min = 168.dp),
                                )
                            }
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                    ) {
                        if (searching) {
                            item(key = "search-bar") {
                                ExploreSearchField(
                                    query = query,
                                    onQueryChange = { query = it },
                                    placeholder = stringResource(R.string.topics_search_placeholder),
                                    autoFocus = true,
                                )
                            }
                        }
                        if (filtered.isNullOrEmpty()) {
                            item(key = "no-match") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s24),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                                ) {
                                    val trimmed = query.trim()
                                    ZunoEmptyState(
                                        icon = R.drawable.zuno_ic_search,
                                        title = if (trimmed.isNotEmpty()) {
                                            stringResource(R.string.topics_browse_empty_title, trimmed)
                                        } else {
                                            stringResource(R.string.topics_empty_title)
                                        },
                                        description = stringResource(R.string.topics_empty_body),
                                    )
                                    ZunoButton(
                                        label = stringResource(R.string.topics_explore_title),
                                        onClick = { onExplore(trimmed) },
                                        variant = ZunoButtonVariant.Secondary,
                                        size = ZunoButtonSize.Medium,
                                        leadingIcon = R.drawable.zuno_ic_t_globe,
                                        leadingIconTint = ZunoColors.IconPrimary,
                                    )
                                }
                            }
                        } else {
                            item(key = "topics-list") {
                                ZunoListGroup {
                                    filtered.forEachIndexed { index, topic ->
                                        ZunoCollectionCard(
                                            icon = R.drawable.zuno_ic_book_open,
                                            title = topic.title,
                                            subtitle = topic.localizedSubtitle(),
                                            onClick = { onOpen(topic.id) },
                                            menu = menuForTopic(topic),
                                            showDivider = index > 0,
                                        )
                                    }
                                }
                            }
                        }

                        if (!searching && recommendedTopics.isNotEmpty()) {
                            item(key = "recommended-footer") {
                                RecommendedTopicsBlock(
                                    recommendedTopics = recommendedTopics,
                                    busy = busy,
                                    onSelectRecommended = onSelectRecommended,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddChoiceSheet(
            title = stringResource(R.string.topics_add_sheet_title),
            createTitle = stringResource(R.string.topics_add_create_title),
            createSubtitle = stringResource(R.string.topics_add_create_sub),
            findTitle = stringResource(R.string.topics_add_find_title),
            findSubtitle = stringResource(R.string.topics_add_find_sub),
            onDismiss = { showAddSheet = false },
            onCreate = onCreate,
            onExplore = { onExplore("") },
        )
    }
}

@Composable
private fun RecommendedTopicsBlock(
    recommendedTopics: List<RecommendedTopic>,
    busy: Boolean,
    onSelectRecommended: (RecommendedTopic) -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        Text(
            text = stringResource(R.string.onboarding_recommended_next),
            style = ZunoType.LabelSmall,
            color = ZunoColors.TextTertiary,
            modifier = Modifier.padding(start = ZunoSpace.s8),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(shape),
        ) {
            val lessonsLabel = stringResource(R.string.onboarding_lessons_count, 5)
            recommendedTopics.forEachIndexed { index, rec ->
                val categoryTag = rec.reason.ifBlank { rec.translation }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy, role = Role.Button) { onSelectRecommended(rec) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                        ) {
                            Text(rec.title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                            Text("$categoryTag · $lessonsLabel", style = ZunoType.CaptionRegular, color = ZunoColors.TextTertiary)
                        }
                        ZunoIcon(R.drawable.zuno_ic_chevron_right_muted, 16.dp)
                    }
                    if (index < recommendedTopics.lastIndex) {
                        ZunoGlassHairline()
                    }
                }
            }
        }
    }
}

// ── Roleplays · 1 · Empty & Roleplays · 2 · List ────────────────────────────

@Composable
fun RoleplaysScreen(
    roleplays: List<Roleplay>?,
    loading: Boolean,
    error: String?,
    discoverPreview: List<DiscoverRoleplay>,
    menuForRoleplay: (Roleplay) -> List<ZunoMenuAction>,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onExplore: (String) -> Unit,
    onOpenPreview: (String) -> Unit,
    onNavTabSelected: (HomeNavTab) -> Unit = {},
    onBack: () -> Unit = { onNavTabSelected(HomeNavTab.Home) },
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    val filtered = roleplays?.filter {
        query.isBlank() ||
            it.title.contains(query.trim(), ignoreCase = true) ||
            it.scenario.contains(query.trim(), ignoreCase = true) ||
            it.userRole.contains(query.trim(), ignoreCase = true) ||
            it.aiRole.contains(query.trim(), ignoreCase = true)
    }

    Box(modifier.fillMaxSize().zunoCanvasBackground()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ZunoSectionTopBar(
                title = stringResource(R.string.roleplays_title),
                onBack = onBack,
                addDescription = stringResource(R.string.roleplays_add_sheet_title),
                showSearch = !roleplays.isNullOrEmpty(),
                searchActive = searching,
                onSearchToggle = {
                    searching = !searching
                    if (!searching) query = ""
                },
                searchDescription = stringResource(R.string.chats_search),
                showAdd = true,
                onAdd = { showAddSheet = true },
            )

            when {
                roleplays == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (loading || error != null) ZunoLoadState(error, onRetry, Modifier.padding(ZunoSpace.s24))
                }
                roleplays.isEmpty() && !searching -> {
                    EmptyStateViewport(
                        modifier = Modifier.weight(1f),
                        hero = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = ZunoSpace.s32),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                            ) {
                                ZunoEmptyState(
                                    icon = R.drawable.zuno_ic_users,
                                    title = stringResource(R.string.roleplays_empty_title),
                                    description = stringResource(R.string.roleplays_empty_body),
                                )
                                ZunoButton(
                                    label = stringResource(R.string.topics_explore_cta),
                                    onClick = { onExplore("") },
                                    variant = ZunoButtonVariant.Secondary,
                                    size = ZunoButtonSize.Medium,
                                    leadingIcon = R.drawable.zuno_ic_t_search,
                                    leadingIconTint = ZunoColors.IconPrimary,
                                    modifier = Modifier.widthIn(min = 168.dp),
                                )
                            }
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                    ) {
                        if (searching) {
                            item(key = "search-bar") {
                                ExploreSearchField(
                                    query = query,
                                    onQueryChange = { query = it },
                                    placeholder = stringResource(R.string.roleplays_search_placeholder),
                                    autoFocus = true,
                                )
                            }
                        }
                        if (filtered.isNullOrEmpty()) {
                            item(key = "no-match") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s24),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                                ) {
                                    val trimmed = query.trim()
                                    ZunoEmptyState(
                                        icon = R.drawable.zuno_ic_search,
                                        title = if (trimmed.isNotEmpty()) {
                                            stringResource(R.string.roleplays_browse_empty_title, trimmed)
                                        } else {
                                            stringResource(R.string.roleplays_empty_title)
                                        },
                                        description = stringResource(R.string.roleplays_empty_body),
                                    )
                                    ZunoButton(
                                        label = stringResource(R.string.roleplays_explore_title),
                                        onClick = { onExplore(trimmed) },
                                        variant = ZunoButtonVariant.Secondary,
                                        size = ZunoButtonSize.Medium,
                                        leadingIcon = R.drawable.zuno_ic_t_globe,
                                        leadingIconTint = ZunoColors.IconPrimary,
                                    )
                                }
                            }
                        } else {
                            item(key = "roleplays-list") {
                                ZunoListGroup {
                                    filtered.forEachIndexed { index, roleplay ->
                                        ZunoCollectionCard(
                                            icon = R.drawable.zuno_ic_users,
                                            title = roleplay.title,
                                            subtitle = roleplay.localizedSubtitle(),
                                            onClick = { onOpen(roleplay.id) },
                                            menu = menuForRoleplay(roleplay),
                                            showDivider = index > 0,
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

    if (showAddSheet) {
        AddChoiceSheet(
            title = stringResource(R.string.roleplays_add_sheet_title),
            createTitle = stringResource(R.string.roleplays_add_create_title),
            createSubtitle = stringResource(R.string.roleplays_add_create_sub),
            findTitle = stringResource(R.string.roleplays_add_find_title),
            findSubtitle = stringResource(R.string.roleplays_add_find_sub),
            onDismiss = { showAddSheet = false },
            onCreate = onCreate,
            onExplore = { onExplore("") },
        )
    }
}

// ── 08 & 12 · Explore Topics (Dedicated Community & System Browse) ──────────

private val TopicExploreCategories = listOf("all", "travel", "food", "work", "daily_life")

@Composable
fun ExploreTopicsScreen(
    discover: Loadable<DiscoverTopicsResult>,
    initialQuery: String,
    languageName: String,
    onBack: () -> Unit,
    onFilterChanged: (category: String, query: String) -> Unit,
    onOpenPreview: (DiscoverTopic) -> Unit,
    onCreateFromQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(selectedCategory, query) {
        if (query.isNotEmpty()) delay(180)
        onFilterChanged(selectedCategory, query.trim())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.topics_explore_title), onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExploreSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.topics_explore_search_placeholder),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                TopicExploreCategories.forEach { cat ->
                    val label = when (cat) {
                        "all" -> stringResource(R.string.topics_cat_all)
                        else -> topicCategoryLabel(cat)
                    }
                    ZunoChip(
                        label = label,
                        onClick = { selectedCategory = cat },
                        selected = selectedCategory == cat,
                    )
                }
            }
        }

        val data = discover.items
        when {
            data == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = discover.error, onRetry = { onFilterChanged(selectedCategory, query.trim()) })
            }
            data.official.isEmpty() && data.community.isEmpty() -> {
                val trimmed = query.trim().ifEmpty { topicCategoryLabel(selectedCategory) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16, Alignment.CenterVertically),
                ) {
                    ZunoEmptyState(
                        icon = R.drawable.zuno_ic_book_open,
                        title = stringResource(R.string.topics_browse_empty_title, trimmed),
                        description = stringResource(R.string.topics_browse_empty_body),
                    )
                    ZunoButton(
                        label = stringResource(R.string.topics_create_query_cta, trimmed),
                        onClick = { onCreateFromQuery(trimmed) },
                        size = ZunoButtonSize.Medium,
                        leadingIcon = R.drawable.zuno_ic_plus_inverse,
                        leadingIconTint = ZunoColors.IconOnInverse,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = 14.dp, bottom = ZunoSpace.s32),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (data.official.isNotEmpty()) {
                        item(key = "official-header") {
                            Text(
                                text = stringResource(R.string.topics_official_header),
                                style = ZunoType.Micro,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                        item(key = "official-list") {
                            ZunoListGroup {
                                data.official.forEachIndexed { index, item ->
                                    ExplorePreviewCard(
                                        icon = R.drawable.zuno_ic_t_sparkles,
                                        title = item.title,
                                        subtitle = "${stringResource(R.string.topics_official_badge)} · ${stringResource(R.string.onboarding_lessons_count, item.totalLessons)} · $languageName ${item.level}",
                                        onClick = { onOpenPreview(item) },
                                        showDivider = index > 0,
                                    )
                                }
                            }
                        }
                    }

                    if (data.community.isNotEmpty()) {
                        item(key = "community-header") {
                            Text(
                                text = stringResource(R.string.topics_community_header),
                                style = ZunoType.Micro,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                        item(key = "community-list") {
                            ZunoListGroup {
                                data.community.forEachIndexed { index, item ->
                                    ExplorePreviewCard(
                                        icon = R.drawable.zuno_ic_t_globe,
                                        title = item.title,
                                        subtitle = "${item.authorHandle ?: "@learner"} · ${stringResource(R.string.topics_learners_count, item.learnersCount)} · ${stringResource(R.string.onboarding_lessons_count, item.totalLessons)}",
                                        onClick = { onOpenPreview(item) },
                                        showDivider = index > 0,
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

// ── Roleplays · 5 & 9 · Explore Roleplays ───────────────────────────────────

private val RoleplayExploreCategories = listOf("all", "travel", "food", "work", "housing")

@Composable
fun ExploreRoleplaysScreen(
    discover: Loadable<DiscoverRoleplaysResult>,
    initialQuery: String,
    onBack: () -> Unit,
    onFilterChanged: (category: String, query: String) -> Unit,
    onOpenPreview: (DiscoverRoleplay) -> Unit,
    onCreateFromQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(selectedCategory, query) {
        if (query.isNotEmpty()) delay(180)
        onFilterChanged(selectedCategory, query.trim())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.roleplays_explore_title), onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExploreSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.roleplays_explore_search_placeholder),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                RoleplayExploreCategories.forEach { cat ->
                    val label = when (cat) {
                        "all" -> stringResource(R.string.topics_cat_all)
                        else -> roleplayCategoryLabel(cat)
                    }
                    ZunoChip(
                        label = label,
                        onClick = { selectedCategory = cat },
                        selected = selectedCategory == cat,
                    )
                }
            }
        }

        val data = discover.items
        when {
            data == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = discover.error, onRetry = { onFilterChanged(selectedCategory, query.trim()) })
            }
            data.official.isEmpty() && data.community.isEmpty() -> {
                val trimmed = query.trim().ifEmpty { roleplayCategoryLabel(selectedCategory) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16, Alignment.CenterVertically),
                ) {
                    ZunoEmptyState(
                        icon = R.drawable.zuno_ic_users,
                        title = stringResource(R.string.roleplays_browse_empty_title, trimmed),
                        description = stringResource(R.string.roleplays_browse_empty_body),
                    )
                    ZunoButton(
                        label = stringResource(R.string.topics_create_query_cta, trimmed),
                        onClick = { onCreateFromQuery(trimmed) },
                        size = ZunoButtonSize.Medium,
                        leadingIcon = R.drawable.zuno_ic_plus_inverse,
                        leadingIconTint = ZunoColors.IconOnInverse,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = 14.dp, bottom = ZunoSpace.s32),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (data.official.isNotEmpty()) {
                        item(key = "official-header") {
                            Text(
                                text = stringResource(R.string.topics_official_header),
                                style = ZunoType.Micro,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                        item(key = "official-list") {
                            ZunoListGroup {
                                data.official.forEachIndexed { index, item ->
                                    ExplorePreviewCard(
                                        icon = R.drawable.zuno_ic_t_sparkles,
                                        title = item.title,
                                        subtitle = "${stringResource(R.string.topics_official_badge)} · ${item.userRole} / ${item.aiRole}",
                                        onClick = { onOpenPreview(item) },
                                        showDivider = index > 0,
                                    )
                                }
                            }
                        }
                    }

                    if (data.community.isNotEmpty()) {
                        item(key = "community-header") {
                            Text(
                                text = stringResource(R.string.roleplays_community_header),
                                style = ZunoType.Micro,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                        item(key = "community-list") {
                            ZunoListGroup {
                                data.community.forEachIndexed { index, item ->
                                    ExplorePreviewCard(
                                        icon = R.drawable.zuno_ic_users,
                                        title = item.title,
                                        subtitle = "${item.authorHandle ?: "@learner"} · ${stringResource(R.string.roleplays_practiced_count, item.practicedCount)}",
                                        onClick = { onOpenPreview(item) },
                                        showDivider = index > 0,
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
