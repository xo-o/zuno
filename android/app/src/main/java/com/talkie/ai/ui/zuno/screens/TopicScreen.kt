package com.talkie.ai.ui.zuno.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.ui.zuno.Lesson
import com.talkie.ai.ui.zuno.Topic
import com.talkie.ai.ui.zuno.components.ZunoButton
import com.talkie.ai.ui.zuno.components.ZunoLessonRow
import com.talkie.ai.ui.zuno.components.ZunoProgressBar
import com.talkie.ai.ui.zuno.components.ZunoSectionHeader
import com.talkie.ai.ui.zuno.components.ZunoTitleTopBar
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoRadius
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType
import kotlin.math.roundToInt

/** 6 · Topic — hero card with progress and the lesson path. */
@Composable
fun TopicScreen(
    topic: Topic,
    onOpenDrawer: () -> Unit,
    onMore: () -> Unit,
    onContinue: () -> Unit,
    onLesson: (Lesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding(),
    ) {
        ZunoTitleTopBar(title = "", onMenu = onOpenDrawer, onMore = onMore)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Box(Modifier.padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8)) {
                TopicHero(topic = topic, onContinue = onContinue)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ZunoSpace.s12, end = ZunoSpace.s12, top = ZunoSpace.s24, bottom = ZunoSpace.s24),
            ) {
                Box(Modifier.padding(start = ZunoSpace.s12, end = ZunoSpace.s12, bottom = ZunoSpace.s4)) {
                    ZunoSectionHeader(title = "Lessons")
                }
                topic.lessons.forEach { lesson ->
                    ZunoLessonRow(
                        number = lesson.number,
                        title = lesson.title,
                        status = lesson.status,
                        meta = lesson.meta,
                        onClick = { onLesson(lesson) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicHero(topic: Topic, onContinue: () -> Unit) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderDefault, shape)
            .padding(ZunoSpace.s24),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
            Text("Topic", style = ZunoType.FootnoteStrong, color = ZunoColors.TextTertiary)
            Text(topic.title, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
            Text(topic.translation, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${topic.completed} of ${topic.lessons.size} lessons",
                    style = ZunoType.CaptionRegular,
                    color = ZunoColors.TextSecondary,
                )
                Text(
                    "${(topic.progress * 100).roundToInt()}%",
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextPrimary,
                )
            }
            ZunoProgressBar(progress = topic.progress)
        }
        ZunoButton(
            label = "Continue",
            onClick = onContinue,
            trailingIcon = R.drawable.zuno_ic_arrow_right,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
