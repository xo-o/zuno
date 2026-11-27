package com.zuno.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuno.ai.R
import com.zuno.ai.data.LessonCardUi
import com.zuno.ai.data.LessonPillUi
import com.zuno.ai.data.LessonStageKind
import com.zuno.ai.data.LessonStageUi
import com.zuno.ai.data.PronunciationEvalUi
import com.zuno.ai.data.RecallPromptUi
import com.zuno.ai.data.RescuePayloadUi
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout

internal val LessonSentenceStyle: TextStyle
    get() = ZunoType.TitleMedium

internal val LessonQuoteStyle: TextStyle
    get() = ZunoType.TitleMedium.copy(
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp,
    )

@DrawableRes
private fun stagePillIcon(iconKey: String): Int = when (iconKey) {
    "waveform" -> R.drawable.zuno_ic_waveform
    "sparkles" -> R.drawable.zuno_ic_sparkles
    "rotate-ccw" -> R.drawable.zuno_ic_rotate_ccw
    "users" -> R.drawable.zuno_ic_users
    else -> R.drawable.zuno_ic_book_open
}

/**
 * Roleplay Pill (`2439:706`) — compact stage header capsule indicating active lesson phase
 * or roleplay persona + goal progress.
 */
@Composable
fun ZunoRoleplayPill(
    pill: LessonPillUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .zunoGlassSubtle(CircleShape)
            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s6),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(stagePillIcon(pill.icon), 13.dp, tint = ZunoColors.IconSecondary)
        Text(
            text = pill.role,
            style = ZunoType.FootnoteStrong,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
        )
        if (pill.goal.isNotBlank()) {
            Text(
                text = "·",
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
            )
            Text(
                text = pill.goal,
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Teach Card (`2439:658`) — used in `03a · Teach & Model` and `03b · Shadow & Pronunciation`.
 * When [highlightFocusWord] is true (`03b`), non-focus words dim to `TextSecondary` while
 * `focusWord` stands out in `TextPrimary`.
 */
@Composable
fun ZunoTeachCard(
    card: LessonCardUi.TeachCard,
    highlightFocusWord: Boolean,
    onListenNormal: () -> Unit,
    onListenSlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .padding(ZunoSpace.s20),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
            if (highlightFocusWord && card.focusWord.isNotBlank()) {
                val annotated = buildAnnotatedString {
                    val idx = card.phrase.indexOf(card.focusWord, ignoreCase = true)
                    if (idx >= 0) {
                        val end = idx + card.focusWord.length
                        withStyle(SpanStyle(color = ZunoColors.TextSecondary)) {
                            append(card.phrase.substring(0, idx))
                        }
                        withStyle(SpanStyle(color = ZunoColors.TextPrimary)) {
                            append(card.phrase.substring(idx, end))
                        }
                        withStyle(SpanStyle(color = ZunoColors.TextSecondary)) {
                            append(card.phrase.substring(end))
                        }
                    } else {
                        withStyle(SpanStyle(color = ZunoColors.TextPrimary)) {
                            append(card.phrase)
                        }
                    }
                }
                Text(text = annotated, style = LessonSentenceStyle)
            } else {
                Text(
                    text = card.phrase,
                    style = LessonSentenceStyle,
                    color = ZunoColors.TextPrimary,
                )
            }
            if (card.translation.isNotBlank()) {
                Text(
                    text = card.translation,
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextTertiary,
                )
            }
        }

        ZunoDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).padding(end = ZunoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
            ) {
                if (card.syllables.isNotBlank()) {
                    Text(
                        text = card.syllables,
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.cue.isNotBlank()) {
                    Text(
                        text = card.cue,
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                ZunoChip(
                    label = "Listen",
                    leadingIcon = R.drawable.zuno_ic_volume,
                    onClick = onListenNormal,
                )
                ZunoChip(
                    label = "0.75×",
                    leadingIcon = R.drawable.zuno_ic_rotate_ccw,
                    onClick = onListenSlow,
                )
            }
        }
    }
}

/**
 * Feedback Pill + Back-chaining chunk chips (`03b · Shadow & Pronunciation`, `2440:9875` & `2440:9887`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZunoPronunciationFeedback(
    eval: PronunciationEvalUi,
    chunks: List<String>,
    onPlayChunk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(ZunoRadius.md)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zunoGlassSubtle(pillShape)
                .padding(start = ZunoSpace.s10, end = ZunoSpace.s12, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoIcon(
                if (eval.passed) R.drawable.zuno_ic_check_feedback else R.drawable.zuno_ic_waveform,
                14.dp,
            )
            Text(
                text = eval.feedbackText,
                style = ZunoType.FootnoteStrong,
                color = ZunoColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (eval.showChunks && chunks.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                chunks.forEach { chunk ->
                    ZunoChip(
                        label = chunk,
                        leadingIcon = R.drawable.zuno_ic_volume,
                        onClick = { onPlayChunk(chunk) },
                    )
                }
            }
        }
    }
}

/**
 * Scaffold Card (`2439:694`) — used in `03c · Building Blocks`.
 */
@Composable
fun ZunoScaffoldCard(
    card: LessonCardUi.ScaffoldCard,
    revealedAnswer: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    val vocabShape = RoundedCornerShape(ZunoRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .padding(ZunoSpace.s20),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
    ) {
        Text(
            text = card.prompt,
            style = ZunoType.BodyMediumStrong,
            color = ZunoColors.TextPrimary,
        )
        if (!revealedAnswer.isNullOrBlank()) {
            Text(
                text = revealedAnswer,
                style = LessonSentenceStyle,
                color = ZunoColors.TextPrimary,
            )
        }
        ZunoDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .zunoGlassElevated(vocabShape)
                    .padding(horizontal = ZunoSpace.s10, vertical = ZunoSpace.s6),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoIcon(R.drawable.zuno_ic_sparkles, 13.dp)
                Text(
                    text = card.newWord,
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextPrimary,
                )
                if (card.meaning.isNotBlank()) {
                    Text(
                        text = card.meaning,
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                    )
                }
            }
            if (card.stemHint.isNotBlank()) {
                Text(
                    text = card.stemHint,
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ZunoSpace.s8),
                )
            }
        }
    }
}

/**
 * Live spoken assembly indicator below Scaffold Card (`03c · Building Blocks`, `2440:10295`).
 */
@Composable
fun ZunoSpokenAssembly(
    liveTranscript: String?,
    hintBanner: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        if (!liveTranscript.isNullOrBlank()) {
            Text(
                text = liveTranscript,
                style = LessonSentenceStyle,
                color = ZunoColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
        } else if (!hintBanner.isNullOrBlank()) {
            Text(
                text = hintBanner,
                style = ZunoType.BodyMediumStrong,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        ZunoListeningIndicator(text = "Listening · Building sentence")
    }
}

/**
 * Active Recall prompt (`03d · Active Recall`, `2440:10713`).
 */
@Composable
fun ZunoRecallPrompt(
    prompt: RecallPromptUi,
    rescueBanner: String?,
    liveUserText: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        Text(
            text = prompt.eyebrow,
            style = ZunoType.FootnoteStrong,
            color = ZunoColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = prompt.cue,
            style = LessonQuoteStyle,
            color = ZunoColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (!rescueBanner.isNullOrBlank()) {
            Text(
                text = rescueBanner,
                style = ZunoType.BodyMediumStrong,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else if (!liveUserText.isNullOrBlank()) {
            Text(
                text = liveUserText,
                style = ZunoType.BodyLarge,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        ZunoListeningIndicator(text = "Target hidden · Speak when ready")
    }
}

@Composable
fun ZunoListeningIndicator(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_waveform, 13.dp, tint = ZunoColors.IconTertiary)
        Text(
            text = text,
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextTertiary,
        )
    }
}

/**
 * Bottom Rescue Actions bar (`03c`, `03d`, `03f`).
 * When [pulseFirstWord] is true (after 6s of learner silence in `03d`), subtly pulses the `First word` chip.
 */
@Composable
fun ZunoLessonRescueBar(
    chips: List<String>,
    pulseFirstWord: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    val pulseTransition = rememberInfiniteTransition(label = "rescuePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulseFirstWord) 0.55f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "firstWordAlpha",
    )

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = zunoLayout.screenPaddingH),
        horizontalArrangement = Arrangement.spacedBy(zunoLayout.chipGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val (label, icon) = when (chip) {
                "first_word" -> "First word" to R.drawable.zuno_ic_sparkles
                "hint" -> "Hint" to R.drawable.zuno_ic_sparkles
                "reveal" -> "Reveal" to R.drawable.zuno_ic_languages
                "translate" -> "Translate" to R.drawable.zuno_ic_languages
                "slow" -> "Slow" to R.drawable.zuno_ic_volume
                else -> chip.replaceFirstChar { it.uppercase() } to R.drawable.zuno_ic_sparkles
            }
            val isPulsing = chip == "first_word" && pulseFirstWord
            ZunoChip(
                label = label,
                leadingIcon = icon,
                selected = isPulsing,
                onClick = { onAction(chip) },
                modifier = if (isPulsing) Modifier.alpha(pulseAlpha) else Modifier,
            )
        }
    }
}

/**
 * Unified Lesson Coach Card (`7400:15220` .. `7400:15408`) rendered at the bottom of the
 * avatar voice lesson stage (`03a`–`03f`), adapting cleanly per pedagogical stage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZunoLessonCoachCard(
    lessonStage: LessonStageUi,
    pronunciationEval: PronunciationEvalUi?,
    rescuePayload: RescuePayloadUi?,
    pulseFirstWord: Boolean,
    onLessonAction: (action: String, payload: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(ZunoRadius.xxxl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zunoGlassSubtle(
                cardShape,
                fillColor = ZunoColors.SurfaceDefault,
                borderBrush = SolidColor(ZunoColors.BorderDefault),
            )
            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        when (lessonStage.stage) {
            LessonStageKind.TeachModel -> {
                val card = lessonStage.card as? LessonCardUi.TeachCard
                val headerLeft = "${lessonStage.pill.role} · ${lessonStage.pill.goal}".trim(' ', '·')
                val headerRight = card?.goalLabel?.ifBlank { null } ?: lessonStage.pill.contextNote
                CoachStageHeader(left = headerLeft, right = headerRight)

                if (card != null) {
                    val syllables = parseSyllables(card.syllables)
                    val noteText = card.setupPrompt.ifBlank { card.cue }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        syllables.forEach { (part, stressed) ->
                            ZunoCoachTilePill(
                                text = part,
                                stressed = stressed,
                                onClick = if (card.focusWord.isNotBlank()) {
                                    { onLessonAction("play_chunk", card.focusWord) }
                                } else {
                                    null
                                },
                            )
                        }
                        if (noteText.isNotBlank()) {
                            Text(
                                text = noteText,
                                style = ZunoType.FootnoteRegular,
                                color = ZunoColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = ZunoSpace.s2),
                            )
                        }
                    }
                }
            }

            LessonStageKind.ShadowDrill -> {
                val card = lessonStage.card as? LessonCardUi.TeachCard
                val headerLeft = "${lessonStage.pill.role} · ${lessonStage.pill.goal}".trim(' ', '·')
                val headerRight = pronunciationEval?.feedbackText
                    ?: lessonStage.pill.contextNote.ifBlank { card?.cue.orEmpty() }
                CoachStageHeader(left = headerLeft, right = headerRight)

                if (card != null) {
                    val syllables = parseSyllables(card.syllables)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        syllables.forEach { (part, stressed) ->
                            ZunoCoachTilePill(
                                text = part,
                                stressed = stressed,
                                onClick = if (card.focusWord.isNotBlank()) {
                                    { onLessonAction("play_chunk", card.focusWord) }
                                } else {
                                    null
                                },
                            )
                        }
                        card.chunks.forEach { chunk ->
                            ZunoChip(
                                label = chunk,
                                leadingIcon = R.drawable.zuno_ic_volume,
                                onClick = { onLessonAction("play_chunk", chunk) },
                            )
                        }
                    }
                }
            }

            LessonStageKind.BuildingBlocks -> {
                val card = lessonStage.card as? LessonCardUi.ScaffoldCard
                val headerLeft = "${lessonStage.pill.role} · ${lessonStage.pill.goal}".trim(' ', '·')
                val headerRight = lessonStage.pill.contextNote.ifBlank {
                    stringResource(R.string.lesson_stage_pattern_drill)
                }
                CoachStageHeader(left = headerLeft, right = headerRight)

                if (card != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        if (card.newWord.isNotBlank()) {
                            ZunoCoachTilePill(
                                text = card.newWord,
                                stressed = false,
                                compactHorizontalPadding = true,
                                onClick = { onLessonAction("play_chunk", card.newWord) },
                            )
                        }
                        if (card.meaning.isNotBlank()) {
                            ZunoCoachTilePill(
                                text = card.meaning,
                                stressed = false,
                                compactHorizontalPadding = true,
                            )
                        }
                        if (card.stemHint.isNotBlank()) {
                            ZunoCoachTilePill(
                                text = card.stemHint,
                                stressed = false,
                                compactHorizontalPadding = true,
                            )
                        }
                    }
                }
            }

            LessonStageKind.ActiveRecall -> {
                val prompt = lessonStage.recallPrompt
                val headerLeft = "${lessonStage.pill.role} · ${lessonStage.pill.goal}".trim(' ', '·')
                val headerRight = lessonStage.pill.contextNote.ifBlank {
                    stringResource(R.string.lesson_stage_target_hidden)
                }
                CoachStageHeader(left = headerLeft, right = headerRight)

                val revealed = rescuePayload?.takeIf { it.action == "reveal" }?.text
                val firstWord = rescuePayload?.takeIf { it.action == "first_word" }?.text
                when {
                    !revealed.isNullOrBlank() -> {
                        ZunoCoachTilePill(
                            text = revealed,
                            stressed = true,
                            onClick = { onLessonAction("play_chunk", revealed) },
                        )
                    }
                    !firstWord.isNullOrBlank() -> {
                        val pulseAlpha = if (pulseFirstWord) 0.72f else 1f
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            modifier = Modifier.alpha(pulseAlpha),
                        ) {
                            ZunoCoachTilePill(text = firstWord, stressed = true)
                            (prompt?.blankLengths ?: listOf(4, 5)).drop(1).forEach { len ->
                                ZunoBlankCueTile(wordLength = len)
                            }
                        }
                    }
                    else -> {
                        val lengths = prompt?.blankLengths?.takeIf { it.isNotEmpty() } ?: listOf(5, 4, 6)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                        ) {
                            lengths.forEach { len ->
                                ZunoBlankCueTile(wordLength = len)
                            }
                        }
                    }
                }
            }

            LessonStageKind.Roleplay -> {
                val goals = lessonStage.roleplayGoals
                val defaultGoalIdx = goals.indexOfFirst { it.current }.takeIf { it >= 0 } ?: 0
                var selectedTab by remember(goals) { mutableIntStateOf(defaultGoalIdx) }
                val rescueTabIdx = goals.size
                val anyCompleted = goals.any { it.completed }
                val rescueTabLabel = stringResource(
                    if (anyCompleted) R.string.lesson_roleplay_rescue else R.string.lesson_roleplay_answers,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    goals.forEachIndexed { idx, goal ->
                        val prefix = when {
                            goal.completed -> "✓ "
                            goal.current -> "● "
                            else -> ""
                        }
                        ZunoChip(
                            label = "$prefix${goal.index}. ${goal.label}",
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                        )
                    }
                    ZunoChip(
                        label = rescueTabLabel,
                        selected = selectedTab == rescueTabIdx,
                        onClick = { selectedTab = rescueTabIdx },
                    )
                }

                val promptRowShape = RoundedCornerShape(ZunoRadius.lg)
                if (selectedTab < goals.size) {
                    val activeGoal = goals[selectedTab]
                    val phraseText = activeGoal.samplePhrase.ifBlank { activeGoal.description }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(
                                promptRowShape,
                                fillColor = ZunoColors.SurfaceDefault,
                                borderBrush = SolidColor(ZunoColors.BorderDefault),
                            )
                            .clickable(
                                enabled = phraseText.isNotBlank(),
                                role = Role.Button,
                                onClick = { onLessonAction("play_chunk", phraseText) },
                            )
                            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (activeGoal.samplePhrase.isNotBlank()) "“${activeGoal.samplePhrase}”" else activeGoal.description,
                                style = ZunoType.FootnoteStrong,
                                color = ZunoColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            ZunoIcon(R.drawable.zuno_ic_t_volume, 14.dp, tint = ZunoColors.IconSecondary)
                        }
                        if (activeGoal.description.isNotBlank()) {
                            Text(
                                text = "Goal ${activeGoal.index} · ${activeGoal.description}",
                                style = ZunoType.FootnoteRegular,
                                color = ZunoColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(
                                promptRowShape,
                                fillColor = ZunoColors.SurfaceDefault,
                                borderBrush = SolidColor(ZunoColors.BorderDefault),
                            )
                            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        if (!rescuePayload?.text.isNullOrBlank()) {
                            Text(
                                text = rescuePayload!!.text,
                                style = ZunoType.FootnoteStrong,
                                color = ZunoColors.TextPrimary,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ZunoChip(
                                label = stringResource(R.string.lesson_bubble_hint),
                                leadingIcon = R.drawable.zuno_ic_sparkles,
                                onClick = { onLessonAction("hint", null) },
                            )
                            ZunoChip(
                                label = stringResource(R.string.voice_chip_translate),
                                leadingIcon = R.drawable.zuno_ic_languages,
                                onClick = { onLessonAction("translate", null) },
                            )
                            ZunoChip(
                                label = stringResource(R.string.voice_chip_speed_slow),
                                leadingIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                onClick = { onLessonAction("listen_slow", null) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoachStageHeader(left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = left,
            style = ZunoType.LabelSmall,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = ZunoSpace.s8),
        )
        if (right.isNotBlank()) {
            Text(
                text = right,
                style = ZunoType.LabelSmall,
                color = ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ZunoCoachTilePill(
    text: String,
    stressed: Boolean,
    onClick: (() -> Unit)? = null,
    compactHorizontalPadding: Boolean = false,
) {
    val shape = RoundedCornerShape(ZunoRadius.lg)
    val baseModifier = if (stressed) {
        Modifier.zunoGlassElevated(
            shape = shape,
            fillColor = ZunoColors.SurfaceStrong,
            borderBrush = SolidColor(ZunoColors.BorderStrong),
        )
    } else {
        Modifier.zunoGlassSubtle(
            shape = shape,
            fillColor = ZunoColors.SurfaceDefault,
            borderBrush = SolidColor(ZunoColors.BorderDefault),
        )
    }
    Box(
        modifier = baseModifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = if (compactHorizontalPadding) ZunoSpace.s10 else ZunoSpace.s12,
                vertical = ZunoSpace.s4,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (stressed) ZunoType.FootnoteStrong.copy(fontWeight = FontWeight.SemiBold) else ZunoType.FootnoteStrong,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ZunoBlankCueTile(wordLength: Int) {
    val shape = RoundedCornerShape(ZunoRadius.md)
    val dashes = List(wordLength.coerceIn(2, 7)) { "_" }.joinToString(" ")
    Box(
        modifier = Modifier
            .zunoGlassSubtle(
                shape = shape,
                fillColor = ZunoColors.SurfaceDefault,
                borderBrush = SolidColor(ZunoColors.BorderDefault),
            )
            .padding(horizontal = ZunoSpace.s10, vertical = ZunoSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dashes,
            style = ZunoType.FootnoteStrong,
            color = ZunoColors.TextTertiary,
            maxLines = 1,
        )
    }
}

internal fun parseSyllables(raw: String): List<Pair<String, Boolean>> {
    val parts = raw.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return emptyList()
    return parts.map { part ->
        val hasLetter = part.any { it.isLetter() }
        val isAllUpper = hasLetter && part.filter { it.isLetter() }.all { it.isUpperCase() }
        val stressed = isAllUpper && (parts.size > 1)
        part to stressed
    }
}
