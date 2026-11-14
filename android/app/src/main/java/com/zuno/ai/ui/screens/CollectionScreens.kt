package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.RecommendedTopic
import com.zuno.ai.data.Roleplay
import com.zuno.ai.data.TopicStatus
import com.zuno.ai.data.TopicSummary
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoCollectionCard
import com.zuno.ai.ui.components.ZunoEmptyState
import com.zuno.ai.ui.components.ZunoFab
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoSearchTopBar
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType

/** One card in a collection list. */
data class CollectionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val menu: List<ZunoMenuAction>,
)

/** Shared layout for "Topics" and "Roleplays": list or empty state, search and a FAB. */
@Composable
private fun CollectionScreen(
    title: String,
    @DrawableRes icon: Int,
    items: List<CollectionItem>?,
    loading: Boolean,
    error: String?,
    emptyTitle: String,
    emptyDescription: String,
    fabLabel: String,
    searchPlaceholder: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    footerContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }
    fun closeSearch() {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)

    val visible = items?.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }

    Box(modifier.fillMaxSize().background(ZunoColors.BgCanvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (searching) {
                ZunoSearchTopBar(query, { query = it }, ::closeSearch, searchPlaceholder, focusRequester)
            } else {
                ZunoBackTitleTopBar(
                    title = title,
                    onBack = onBack,
                    trailingIcon = if (!items.isNullOrEmpty()) R.drawable.zuno_ic_search else null,
                    trailingDescription = "Search",
                    onTrailing = { searching = true },
                )
            }
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading || error != null) ZunoLoadState(error, onRetry, Modifier.padding(ZunoSpace.s24))
                }
                items.isEmpty() && footerContent == null -> Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
                    ZunoEmptyState(icon, emptyTitle, emptyDescription)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 120.dp),
                ) {
                    if (items.isNotEmpty()) {
                        item {
                            if (visible.isNullOrEmpty()) {
                                Text(
                                    "No matches for “${query.trim()}”",
                                    style = ZunoType.BodySmall,
                                    color = ZunoColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s24),
                                )
                            } else {
                                ZunoListGroup {
                                    visible.forEach { entry ->
                                        ZunoCollectionCard(
                                            icon = icon,
                                            title = entry.title,
                                            subtitle = entry.subtitle,
                                            onClick = { onOpen(entry.id) },
                                            menu = entry.menu,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!searching && footerContent != null) {
                        item {
                            if (items.isNotEmpty()) Spacer(Modifier.height(ZunoSpace.s20))
                            footerContent()
                        }
                    }
                }
            }
        }
        if (!searching) {
            ZunoFab(
                label = fabLabel,
                onClick = onCreate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = ZunoSpace.s16, bottom = ZunoSpace.s16),
            )
        }
    }
}

fun TopicSummary.subtitle(): String = when (status) {
    TopicStatus.Completed -> "Completed"
    else -> "$completedLessons of $totalLessons lessons"
}

/** Topics — List / Empty + Personalized Recommendations from Onboarding. */
@Composable
fun TopicsScreen(
    topics: List<TopicSummary>?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (TopicSummary) -> Unit,
    recommendedTopics: List<RecommendedTopic> = emptyList(),
    busy: Boolean = false,
    onSelectRecommended: (RecommendedTopic) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CollectionScreen(
        title = "Topics",
        icon = R.drawable.zuno_ic_book_open,
        items = topics?.map { topic ->
            CollectionItem(topic.id, topic.title, topic.subtitle(), listOf(ZunoMenuAction("Delete topic", icon = R.drawable.zuno_ic_t_trash, destructive = true) { onDelete(topic) }))
        },
        loading = loading,
        error = error,
        emptyTitle = "No topics yet",
        emptyDescription = "Guided lessons, step by step. Pick one up or create your own.",
        fabLabel = "New topic",
        searchPlaceholder = "Search topics",
        onBack = onBack,
        onRetry = onRetry,
        onOpen = onOpen,
        onCreate = onCreate,
        footerContent = if (recommendedTopics.isNotEmpty()) {
            {
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
                            .clip(shape)
                            .background(ZunoColors.SurfaceSubtle)
                            .border(1.dp, ZunoColors.BorderSubtle, shape),
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
                                        .padding(start = 18.dp, end = ZunoSpace.s16, top = ZunoSpace.s14, bottom = ZunoSpace.s14),
                                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                                    ) {
                                        Text(
                                            text = rec.title,
                                            style = ZunoType.BodyMediumStrong,
                                            color = ZunoColors.TextPrimary,
                                        )
                                        Text(
                                            text = "$categoryTag · $lessonsLabel",
                                            style = ZunoType.CaptionRegular,
                                            color = ZunoColors.TextTertiary,
                                        )
                                    }
                                    ZunoIcon(R.drawable.zuno_ic_chevron_right_muted, 16.dp)
                                }
                                if (index < recommendedTopics.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(ZunoColors.BorderSubtle),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else null,
        modifier = modifier,
    )
}

/** Roleplays — List / Empty. */
@Composable
fun RoleplaysScreen(
    roleplays: List<Roleplay>?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onRestart: (Roleplay) -> Unit,
    onDelete: (Roleplay) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionScreen(
        title = "Roleplays",
        icon = R.drawable.zuno_ic_users,
        items = roleplays?.map { roleplay ->
            CollectionItem(
                id = roleplay.id,
                title = roleplay.title,
                subtitle = "You: ${roleplay.userRole} · Zuno: ${roleplay.aiRole}",
                menu = listOf(
                    ZunoMenuAction("Start over", icon = R.drawable.zuno_ic_t_rotate_ccw) { onRestart(roleplay) },
                    ZunoMenuAction("Delete roleplay", icon = R.drawable.zuno_ic_t_trash, destructive = true, dividerBefore = true) { onDelete(roleplay) },
                ),
            )
        },
        loading = loading,
        error = error,
        emptyTitle = "No roleplays yet",
        emptyDescription = "Practice real situations. Zuno plays the other person.",
        fabLabel = "New roleplay",
        searchPlaceholder = "Search roleplays",
        onBack = onBack,
        onRetry = onRetry,
        onOpen = onOpen,
        onCreate = onCreate,
        modifier = modifier,
    )
}
