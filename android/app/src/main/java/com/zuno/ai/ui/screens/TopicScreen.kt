package com.zuno.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.DiscoverRoleplay
import com.zuno.ai.data.DiscoverTopic
import com.zuno.ai.data.LessonState
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicLesson
import com.zuno.ai.data.TopicStatus
import com.zuno.ai.ui.components.LessonStatus
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoLessonRow
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoSectionHeader
import com.zuno.ai.ui.components.ZunoTopicUpNextCard
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout
import com.zuno.ai.viewmodel.Loadable

/**
 * 02 · Topic — In progress & 05 · Topic — Updated (Figma `2677:27375` & `2677:27421`).
 * Displays the flat Topic Hero (`Title`, `Translation`, `{done} of {total} lessons · {min} min left [· Public]`,
 * primary `Continue` CTA) and the 5-lesson learning path.
 */
@Composable
fun TopicScreen(
    topic: TopicDetail?,
    busy: Boolean,
    menu: List<ZunoMenuAction>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartLesson: (TopicLesson) -> Unit,
    onOpenEvaluation: (TopicLesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    var jumpAheadLesson by remember { mutableStateOf<TopicLesson?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = "Topic",
            onBack = onBack,
            trailing = if (topic != null && menu.isNotEmpty()) {
                { ZunoOverflowMenu(actions = menu, size = ZunoIconButtonSize.Large) }
            } else {
                null
            },
        )
        if (topic == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = null, onRetry = onRetry)
            }
            return@Column
        }

        val upNextLesson = topic.nextLesson ?: topic.lessons.firstOrNull()
        val summary = topic.summary
        val remainingLessons = if (summary.status != TopicStatus.Completed && upNextLesson != null) {
            topic.lessons.filterNot { it.id == upNextLesson.id }
        } else {
            topic.lessons
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s8, bottom = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
        ) {
            TopicHero(topic = topic)

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16)) {
                if (upNextLesson != null) {
                    val eyebrow = when (summary.status) {
                        TopicStatus.Completed -> stringResource(R.string.topics_review_eyebrow)
                        TopicStatus.NotStarted -> stringResource(R.string.topics_start_eyebrow, upNextLesson.position)
                        TopicStatus.InProgress -> stringResource(R.string.topics_up_next_eyebrow, upNextLesson.position)
                    }
                    val actionLabel = when (summary.status) {
                        TopicStatus.Completed -> "Review"
                        TopicStatus.NotStarted -> "Start"
                        TopicStatus.InProgress -> "Continue"
                    }
                    ZunoTopicUpNextCard(
                        eyebrow = eyebrow,
                        title = upNextLesson.title,
                        actionLabel = actionLabel,
                        busy = busy,
                        onAction = { if (!busy) onStartLesson(upNextLesson) },
                    )
                }

                if (remainingLessons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                        Text(
                            text = stringResource(R.string.topics_all_lessons_header),
                            style = ZunoType.LabelSmall,
                            color = ZunoColors.TextTertiary,
                        )
                        ZunoListGroup {
                            remainingLessons.forEachIndexed { index, lesson ->
                                ZunoLessonRow(
                                    number = lesson.position,
                                    title = lesson.title,
                                    status = when (lesson.state) {
                                        LessonState.Done -> LessonStatus.Done
                                        LessonState.Current -> LessonStatus.Current
                                        LessonState.Upcoming -> LessonStatus.Upcoming
                                    },
                                    meta = when (lesson.state) {
                                        LessonState.Done -> stringResource(R.string.topics_lesson_done_meta)
                                        LessonState.Current -> stringResource(R.string.topics_lesson_est_min)
                                        LessonState.Upcoming -> stringResource(R.string.topics_lesson_est_min)
                                    },
                                    showDivider = index > 0,
                                    onClick = {
                                        if (!busy) {
                                            when (lesson.state) {
                                                LessonState.Current -> onStartLesson(lesson)
                                                LessonState.Done -> onOpenEvaluation(lesson)
                                                LessonState.Upcoming -> jumpAheadLesson = lesson
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    jumpAheadLesson?.let { target ->
        ZunoDialog(
            title = "Jump ahead to Lesson ${target.position}?",
            body = "“${target.title}” builds on earlier lessons. You can practice it now or finish the current lesson first.",
            confirmLabel = "Start lesson",
            onConfirm = {
                jumpAheadLesson = null
                onStartLesson(target)
            },
            onDismiss = { jumpAheadLesson = null },
        )
    }
}

@Composable
private fun TopicHero(topic: TopicDetail) {
    val summary = topic.summary
    val baseMeta = when (summary.status) {
        TopicStatus.Completed -> "Completed · ${summary.totalLessons} lessons"
        else -> "${summary.completedLessons} of ${summary.totalLessons} lessons · ${summary.estimatedMinutesLeft} min left"
    }
    val metaText = if (summary.isPublic) {
        "$baseMeta · ${stringResource(R.string.topics_public_badge)}"
    } else {
        baseMeta
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZunoSpace.s8),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        Text(
            text = summary.title,
            style = ZunoType.Display,
            color = ZunoColors.TextPrimary,
        )
        Text(
            text = summary.translation,
            style = ZunoType.BodyMedium,
            color = ZunoColors.TextSecondary,
        )
        Text(
            text = metaText,
            style = ZunoType.BodySmall,
            color = ZunoColors.TextTertiary,
        )
    }
}

// ── 09 · Topics — Community Preview (`2998:13198`, `7210:11885`, `7210:11928`) ──

@Composable
fun CommunityTopicPreviewScreen(
    preview: Loadable<DiscoverTopic>,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddOrOpen: (DiscoverTopic) -> Unit,
    onShare: (DiscoverTopic) -> Unit,
    onReport: (DiscoverTopic) -> Unit,
    onCopyLink: (DiscoverTopic) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val item = preview.items
    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(R.string.topics_preview_title),
            onBack = onBack,
            trailing = if (item != null) {
                {
                    ZunoOverflowMenu(
                        actions = buildList {
                            add(
                                ZunoMenuAction(
                                    label = stringResource(
                                        if (item.alreadySaved) R.string.topics_preview_open_cta else R.string.topics_preview_menu_add,
                                    ),
                                    icon = if (item.alreadySaved) R.drawable.zuno_ic_t_play_circle else R.drawable.zuno_ic_plus,
                                    onClick = { if (!busy) onAddOrOpen(item) },
                                ),
                            )
                            add(
                                ZunoMenuAction(
                                    label = stringResource(R.string.share_action),
                                    icon = R.drawable.zuno_ic_t_share,
                                    onClick = { onShare(item) },
                                ),
                            )
                            add(
                                ZunoMenuAction(
                                    label = stringResource(R.string.share_copy_link),
                                    icon = R.drawable.zuno_ic_t_link,
                                    onClick = { onCopyLink(item) },
                                ),
                            )
                            if (!item.isOfficial) {
                                add(
                                    ZunoMenuAction(
                                        label = stringResource(R.string.topics_preview_menu_report),
                                        icon = R.drawable.zuno_ic_t_flag,
                                        destructive = true,
                                        dividerBefore = true,
                                        onClick = { onReport(item) },
                                    ),
                                )
                            }
                        },
                        size = ZunoIconButtonSize.Large,
                    )
                }
            } else {
                null
            },
        )

        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = preview.error, onRetry = onRetry)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s8, bottom = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = ZunoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                Text(item.title, style = ZunoType.Display, color = ZunoColors.TextPrimary)
                Text(item.translation, style = ZunoType.BodyMedium, color = ZunoColors.TextSecondary)
                val attribution = if (item.isOfficial) {
                    "${stringResource(R.string.topics_official_badge)} · ${stringResource(R.string.onboarding_lessons_count, item.totalLessons)} · ${item.level}"
                } else {
                    stringResource(
                        R.string.topics_preview_by_author,
                        item.authorHandle ?: "@learner",
                        item.totalLessons,
                        item.learnersCount,
                    )
                }
                Text(attribution, style = ZunoType.BodySmall, color = ZunoColors.TextTertiary)
            }

            ZunoTopicUpNextCard(
                eyebrow = if (item.alreadySaved) {
                    stringResource(R.string.topics_preview_saved_eyebrow, 1)
                } else {
                    stringResource(R.string.topics_preview_card_eyebrow)
                },
                title = if (item.alreadySaved) {
                    item.lessons.firstOrNull()?.title ?: item.title
                } else {
                    stringResource(R.string.topics_preview_card_title)
                },
                actionLabel = stringResource(
                    if (item.alreadySaved) R.string.topics_preview_start_button else R.string.topics_preview_add_button,
                ),
                busy = busy,
                onAction = { onAddOrOpen(item) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Box(Modifier.padding(bottom = ZunoSpace.s4)) {
                    ZunoSectionHeader(title = stringResource(R.string.topics_preview_syllabus, item.lessons.size))
                }
                ZunoListGroup {
                    val previewBadge = stringResource(R.string.topics_preview_row_badge)
                    item.lessons.forEachIndexed { index, lesson ->
                        ZunoLessonRow(
                            number = lesson.position,
                            title = lesson.title,
                            status = LessonStatus.Upcoming,
                            meta = previewBadge,
                            showDivider = index > 0,
                            onClick = { if (!busy) onAddOrOpen(item) },
                        )
                    }
                }
            }
        }
    }
}

// ── Roleplays · 6, 12, 13 · Community Preview (`2999:13467`, `7210:12308`, `7210:12358`) ──

@Composable
fun CommunityRoleplayPreviewScreen(
    preview: Loadable<DiscoverRoleplay>,
    languageName: String,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartLive: (DiscoverRoleplay) -> Unit,
    onSave: (DiscoverRoleplay) -> Unit,
    onShare: (DiscoverRoleplay) -> Unit,
    onReport: (DiscoverRoleplay) -> Unit,
    onCopyLink: (DiscoverRoleplay) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val item = preview.items
    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(R.string.roleplays_preview_title),
            onBack = onBack,
            trailing = if (item != null) {
                {
                    ZunoOverflowMenu(
                        actions = buildList {
                            add(
                                ZunoMenuAction(
                                    label = stringResource(
                                        if (item.alreadySaved) R.string.roleplays_preview_start_cta else R.string.roleplays_preview_menu_add,
                                    ),
                                    icon = if (item.alreadySaved) R.drawable.zuno_ic_t_audio_lines else R.drawable.zuno_ic_plus,
                                    onClick = {
                                        if (!busy) {
                                            if (item.alreadySaved) onStartLive(item) else onSave(item)
                                        }
                                    },
                                ),
                            )
                            add(
                                ZunoMenuAction(
                                    label = stringResource(R.string.share_action),
                                    icon = R.drawable.zuno_ic_t_share,
                                    onClick = { onShare(item) },
                                ),
                            )
                            add(
                                ZunoMenuAction(
                                    label = stringResource(R.string.share_copy_link),
                                    icon = R.drawable.zuno_ic_t_link,
                                    onClick = { onCopyLink(item) },
                                ),
                            )
                            if (!item.isOfficial) {
                                add(
                                    ZunoMenuAction(
                                        label = stringResource(R.string.roleplays_preview_menu_report),
                                        icon = R.drawable.zuno_ic_t_flag,
                                        destructive = true,
                                        dividerBefore = true,
                                        onClick = { onReport(item) },
                                    ),
                                )
                            }
                        },
                        size = ZunoIconButtonSize.Large,
                    )
                }
            } else {
                null
            },
        )

        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = preview.error, onRetry = onRetry)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s12, bottom = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                    ZunoChip(
                        label = "${stringResource(R.string.topics_public_badge)} · ${item.level}",
                        selected = true,
                        onClick = {},
                    )
                    ZunoChip(
                        label = stringResource(R.string.roleplays_practiced_count, item.practicedCount),
                        onClick = {},
                    )
                }
                Spacer(Modifier.height(ZunoSpace.s2))
                Text(item.title, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
                val creatorLine = if (item.isOfficial) {
                    stringResource(R.string.roleplays_preview_created_by_zuno, languageName)
                } else {
                    stringResource(R.string.roleplays_preview_created_by, item.authorHandle ?: "@learner", languageName)
                }
                Text(creatorLine, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
            }

            val cardShape = RoundedCornerShape(ZunoRadius.xxl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(cardShape, fillColor = ZunoColors.SurfaceDefault)
                    .padding(horizontal = ZunoSpace.s16, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            ) {
                Text(
                    text = stringResource(R.string.roleplays_preview_scenario_label),
                    style = ZunoType.Micro,
                    color = ZunoColors.TextTertiary,
                )
                Text(
                    text = item.setting.ifBlank { item.scenario },
                    style = ZunoType.BodyMedium,
                    color = ZunoColors.TextPrimary,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(cardShape)
                    .padding(horizontal = ZunoSpace.s16, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.roleplays_preview_your_role), style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
                    Text(item.userRole, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.roleplays_preview_sofia_role), style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
                    Text(item.aiRole, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                }
            }

            ZunoTopicUpNextCard(
                eyebrow = if (item.alreadySaved) {
                    stringResource(R.string.roleplays_preview_saved_eyebrow)
                } else {
                    stringResource(R.string.roleplays_preview_card_eyebrow)
                },
                title = if (item.alreadySaved) {
                    stringResource(R.string.roleplays_preview_saved_title)
                } else {
                    stringResource(R.string.topics_preview_card_title)
                },
                actionLabel = stringResource(
                    if (item.alreadySaved) R.string.topics_preview_start_button else R.string.roleplays_preview_add_button,
                ),
                busy = busy,
                onAction = {
                    if (item.alreadySaved) onStartLive(item) else onSave(item)
                },
            )
        }
    }
}
