package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.CoachNote
import com.zuno.ai.data.LessonEvaluation
import com.zuno.ai.data.PhraseResult
import com.zuno.ai.ui.components.LessonSentenceStyle
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoListGroup
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoSectionHeader
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 04 · Lesson complete — Evaluation (Figma `2421:8995`).
 * Displays speaking metrics, tappable Coach Note cards (recasts & stress cues),
 * Practiced Phrases (`Mastered` vs. `Practice`) with Cartesia audio playback, and Next/Back CTAs.
 */
@Composable
fun LessonEvaluationScreen(
    evaluation: LessonEvaluation?,
    lessonTitleFallback: String,
    lessonPositionFallback: Int,
    totalLessonsFallback: Int,
    busy: Boolean,
    onPlaySample: (String) -> Unit,
    onNextLesson: (String) -> Unit,
    onPracticeAgain: () -> Unit,
    onBackToTopic: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackToTopic)

    val position = evaluation?.lessonPosition?.takeIf { it > 0 } ?: lessonPositionFallback
    val total = evaluation?.totalLessons?.takeIf { it > 0 } ?: totalLessonsFallback

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = "Lesson $position of $total",
            onBack = onBackToTopic,
            trailing = {
                ZunoOverflowMenu(
                    actions = listOf(
                        ZunoMenuAction(
                            label = "Practice again",
                            icon = R.drawable.zuno_ic_t_rotate_ccw,
                            onClick = onPracticeAgain,
                        ),
                    ),
                    size = ZunoIconButtonSize.Large,
                )
            },
        )

        if (evaluation == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = null, onRetry = onRetryLoad)
            }
            return@Column
        }

        val minutes = max(1, (evaluation.durationSec / 60.0).roundToInt())
        val title = evaluation.lessonTitle.ifBlank { lessonTitleFallback }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s14, bottom = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            // Summary + 3x Eval Stat cards
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14)) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                    Text(
                        text = "Lesson complete · $minutes min",
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextTertiary,
                    )
                    Text(
                        text = title,
                        style = LessonSentenceStyle,
                        color = ZunoColors.TextPrimary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    EvalStatCard(
                        value = "${evaluation.pronunciationScore}%",
                        label = "Pronunciation",
                        modifier = Modifier.weight(1f),
                    )
                    EvalStatCard(
                        value = "${evaluation.goalsCompleted} of ${evaluation.goalsTotal}",
                        label = "Goals",
                        modifier = Modifier.weight(1f),
                    )
                    EvalStatCard(
                        value = evaluation.fluencyLabel,
                        label = "Fluency",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Coach feedback
            if (evaluation.coachNotes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    ZunoSectionHeader(title = "Coach feedback")
                    evaluation.coachNotes.forEach { note ->
                        CoachNoteCard(
                            note = note,
                            onClick = {
                                val sample = note.ttsSampleText.ifBlank { note.correction }
                                onPlaySample(sample)
                            },
                        )
                    }
                }
            }

            // Practiced phrases
            if (evaluation.phraseResults.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    ZunoSectionHeader(title = "Practiced phrases")
                    ZunoListGroup {
                        evaluation.phraseResults.forEach { item ->
                            PhraseRow(
                                item = item,
                                onListen = { onPlaySample(item.phrase) },
                            )
                        }
                    }
                }
            }
        }

        // Bottom Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            val nextId = evaluation.nextLessonId
            if (nextId != null) {
                ZunoButton(
                    label = "Next lesson",
                    onClick = { if (!busy) onNextLesson(nextId) },
                    variant = ZunoButtonVariant.Primary,
                    size = ZunoButtonSize.Large,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ZunoButton(
                    label = "Practice again",
                    onClick = { if (!busy) onPracticeAgain() },
                    variant = ZunoButtonVariant.Primary,
                    size = ZunoButtonSize.Large,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ZunoButton(
                label = "Back to topic",
                onClick = onBackToTopic,
                variant = ZunoButtonVariant.Ghost,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EvalStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(
        modifier = modifier
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
    ) {
        Text(
            text = value,
            style = ZunoType.Headline,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoachNoteCard(
    note: CoachNote,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    val categoryIcon = if (note.kind == "pronunciation") R.drawable.zuno_ic_waveform else R.drawable.zuno_ic_sparkles
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                ZunoIcon(categoryIcon, 14.dp, tint = ZunoColors.IconSecondary)
                Text(
                    text = note.category,
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                )
            }
            ZunoIcon(R.drawable.zuno_ic_volume_secondary, 14.dp)
        }
        Text(
            text = note.correction,
            style = ZunoType.LabelMedium,
            color = ZunoColors.TextPrimary,
        )
    }
}

@Composable
private fun PhraseRow(
    item: PhraseResult,
    onListen: () -> Unit,
) {
    val isMastered = item.isMastered
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZunoColors.SurfaceDefault)
            .clickable(role = Role.Button, onClick = onListen)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (isMastered) {
                        Modifier.background(ZunoColors.SurfaceInverse)
                    } else {
                        Modifier
                            .background(ZunoColors.SurfaceStrong)
                            .border(1.dp, ZunoColors.BorderDefault, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isMastered) {
                ZunoIcon(R.drawable.zuno_ic_check_inverse, 13.dp)
            } else {
                ZunoIcon(R.drawable.zuno_ic_t_repeat, 13.dp, tint = ZunoColors.IconSecondary)
            }
        }

        Text(
            text = item.phrase,
            style = ZunoType.LabelMedium,
            color = ZunoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = if (isMastered) "Mastered" else "Practice",
            style = if (isMastered) ZunoType.FootnoteRegular else ZunoType.FootnoteStrong,
            color = if (isMastered) ZunoColors.TextTertiary else ZunoColors.TextSecondary,
        )

        ZunoIcon(R.drawable.zuno_ic_volume_secondary, 16.dp)
    }
}
