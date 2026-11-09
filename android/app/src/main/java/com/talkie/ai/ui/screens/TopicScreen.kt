package com.talkie.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.data.LessonState
import com.talkie.ai.data.TopicDetail
import com.talkie.ai.data.TopicLesson
import com.talkie.ai.data.TopicStatus
import com.talkie.ai.ui.components.LessonStatus
import com.talkie.ai.ui.components.ZunoBackTitleTopBar
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoButtonSize
import com.talkie.ai.ui.components.ZunoButtonVariant
import com.talkie.ai.ui.components.ZunoIcon
import com.talkie.ai.ui.components.ZunoLessonRow
import com.talkie.ai.ui.components.ZunoListGroup
import com.talkie.ai.ui.components.ZunoLoadState
import com.talkie.ai.ui.components.ZunoMenuAction
import com.talkie.ai.ui.components.ZunoOverflowMenu
import com.talkie.ai.ui.components.ZunoSectionHeader
import com.talkie.ai.ui.components.ZunoSegmentedProgress
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

/** Topic — Not started / In progress / Completed. */
@Composable
fun TopicScreen(
    topic: TopicDetail?,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLesson: (TopicLesson) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        ZunoMenuAction("Delete topic", icon = R.drawable.zuno_ic_t_trash, destructive = true, dividerBefore = true, onClick = onDelete),
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
            TopicHeader(topic)
            ProgressCard(topic, busy, onOpenLesson)
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                Box(Modifier.padding(horizontal = ZunoSpace.s16)) { ZunoSectionHeader(title = "Lessons") }
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
                            onClick = { if (!busy) onOpenLesson(lesson) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicHeader(topic: TopicDetail) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s4, vertical = ZunoSpace.s8),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(ZunoRadius.lg)).background(ZunoColors.SurfaceStrong),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(
                if (topic.summary.status == TopicStatus.Completed) R.drawable.zuno_ic_check else R.drawable.zuno_ic_book_open,
                28.dp,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(topic.summary.title, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
            Text(topic.summary.translation, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
        }
    }
}

@Composable
private fun ProgressCard(topic: TopicDetail, busy: Boolean, onOpenLesson: (TopicLesson) -> Unit) {
    val summary = topic.summary
    val next = topic.nextLesson
    val (eyebrow, headline) = when {
        next == null -> "TOPIC COMPLETE" to "All ${summary.totalLessons} lessons done"
        summary.status == TopicStatus.NotStarted -> "START WITH · LESSON ${next.position}" to next.title
        else -> "UP NEXT · LESSON ${next.position}" to next.title
    }
    val statusLabel = when (summary.status) {
        TopicStatus.NotStarted -> "Not started"
        TopicStatus.InProgress -> "${summary.progress}%"
        TopicStatus.Completed -> "Completed"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ZunoRadius.xxl))
            .background(ZunoColors.SurfaceDefault)
            .padding(ZunoSpace.s20),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${summary.completedLessons} of ${summary.totalLessons} lessons",
                style = ZunoType.CaptionRegular,
                color = ZunoColors.TextSecondary,
            )
            Text(statusLabel, style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary)
        }
        ZunoSegmentedProgress(total = summary.totalLessons, completed = summary.completedLessons)
        Box(Modifier.fillMaxWidth().height(1.dp).background(ZunoColors.BorderDefault))
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(eyebrow, style = ZunoType.Micro, color = ZunoColors.TextTertiary)
            Text(headline, style = ZunoType.Headline, color = ZunoColors.TextPrimary)
        }
        ZunoButton(
            label = when (summary.status) {
                TopicStatus.NotStarted -> "Start topic"
                TopicStatus.InProgress -> "Continue"
                TopicStatus.Completed -> "Practice again"
            },
            onClick = { (next ?: topic.lessons.firstOrNull())?.let(onOpenLesson) },
            variant = if (summary.status == TopicStatus.Completed) ZunoButtonVariant.Secondary else ZunoButtonVariant.Primary,
            size = ZunoButtonSize.Medium,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
