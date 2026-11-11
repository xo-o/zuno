package com.zuno.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zuno.ai.R
import com.zuno.ai.data.LessonState
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicLesson
import com.zuno.ai.data.TopicStatus
import com.zuno.ai.ui.components.LessonStatus
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoLessonRow
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoSectionHeader
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType

/**
 * 02 · Topic — In progress & 05 · Topic — Updated (Figma `2421:8472` & `2421:9440`).
 * Displays the flat Topic Hero (`Title`, `Translation`, `{done} of {total} lessons · {min} min left`,
 * primary `Continue` CTA) and the 5-lesson learning path.
 */
@Composable
fun TopicScreen(
    topic: TopicDetail?,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartLesson: (TopicLesson) -> Unit,
    onOpenEvaluation: (TopicLesson) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var jumpAheadLesson by remember { mutableStateOf<TopicLesson?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding(),
    ) {
        Box {
            ZunoBackTitleTopBar(title = "Topic", onBack = onBack)
            if (topic != null) {
                ZunoOverflowMenu(
                    actions = listOf(
                        ZunoMenuAction("Reset progress", icon = R.drawable.zuno_ic_t_rotate_ccw, onClick = onReset),
                        ZunoMenuAction(
                            "Delete topic",
                            icon = R.drawable.zuno_ic_t_trash,
                            destructive = true,
                            dividerBefore = true,
                            onClick = onDelete,
                        ),
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = ZunoSpace.s12),
                )
            }
        }
        if (topic == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = null, onRetry = onRetry)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
        ) {
            TopicHero(
                topic = topic,
                busy = busy,
                onPrimaryClick = {
                    val target = topic.nextLesson ?: topic.lessons.firstOrNull()
                    if (!busy && target != null) onStartLesson(target)
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Box(Modifier.padding(start = ZunoSpace.s16, end = ZunoSpace.s4, bottom = ZunoSpace.s4)) {
                    ZunoSectionHeader(title = "Lessons")
                }
                ZunoListGroup {
                    topic.lessons.forEach { lesson ->
                        ZunoLessonRow(
                            number = lesson.position,
                            title = lesson.title,
                            status = when (lesson.state) {
                                LessonState.Done -> LessonStatus.Done
                                LessonState.Current -> LessonStatus.Current
                                LessonState.Upcoming -> LessonStatus.Upcoming
                            },
                            meta = when (lesson.state) {
                                LessonState.Done -> "Done"
                                LessonState.Current ->
                                    if (topic.summary.status == TopicStatus.NotStarted) "Start here" else "Up next"
                                LessonState.Upcoming -> null
                            },
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
private fun TopicHero(
    topic: TopicDetail,
    busy: Boolean,
    onPrimaryClick: () -> Unit,
) {
    val summary = topic.summary
    val metaText = when (summary.status) {
        TopicStatus.Completed -> "Completed · ${summary.totalLessons} lessons"
        else -> "${summary.completedLessons} of ${summary.totalLessons} lessons · ${summary.estimatedMinutesLeft} min left"
    }
    val ctaLabel = when (summary.status) {
        TopicStatus.NotStarted -> "Start topic"
        TopicStatus.InProgress -> "Continue"
        TopicStatus.Completed -> "Practice again"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ZunoSpace.s8),
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
        Spacer(Modifier.height(ZunoSpace.s12))
        ZunoButton(
            label = ctaLabel,
            onClick = onPrimaryClick,
            variant = if (summary.status == TopicStatus.Completed) ZunoButtonVariant.Secondary else ZunoButtonVariant.Primary,
            size = ZunoButtonSize.Large,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
