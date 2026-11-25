package com.zuno.ai.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.HeatmapDay
import com.zuno.ai.data.ProgressOverview
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoProgressBar
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoGlassVerticalHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.viewmodel.Loadable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CefrOrder = listOf("A1", "A2", "B1", "B2", "C1", "C2")

/**
 * `13 · Progress` (`3127:14332` 13.1 · Progress).
 */
@Composable
fun ProgressScreen(
    languageName: String,
    level: String,
    streakDays: Int,
    progress: Loadable<ProgressOverview> = Loadable(),
    onRetry: () -> Unit = {},
    onBack: () -> Unit,
    onStartDrill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = progress.items
    val effectiveLevel = data?.level ?: level
    val effectiveStreak = data?.streakDays ?: streakDays
    val levelIndex = CefrOrder.indexOf(effectiveLevel).coerceAtLeast(0)
    val nextLevel = CefrOrder.getOrNull(levelIndex + 1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoSectionTopBar(
            title = "Progress",
            onBack = onBack,
            showSearch = false,
            showAdd = false,
        )

        if (data == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ZunoLoadState(error = progress.error, onRetry = onRetry)
            }
            return
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s32),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            // CEFR level + 3 summary metrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                    .padding(ZunoSpace.s16),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$languageName · $effectiveLevel", style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                    Text(
                        text = if (nextLevel != null) "${data.cefrProgressPercent}% to $nextLevel" else "Top level",
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextSecondary,
                    )
                }

                ZunoProgressBar(progress = (data.cefrProgressPercent / 100f).coerceIn(0f, 1f), height = 5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CefrOrder.forEachIndexed { idx, code ->
                        Text(
                            text = code,
                            style = ZunoType.Micro,
                            color = if (idx <= levelIndex) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                        )
                    }
                }

                ZunoGlassHairline()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgressMetricCell("${effectiveStreak}d", "Streak", Modifier.weight(1f))
                    ZunoGlassVerticalHairline(28.dp)
                    ProgressMetricCell("${data.totalSpokenMinutes}m", "Spoken", Modifier.weight(1f))
                    ZunoGlassVerticalHairline(28.dp)
                    ProgressMetricCell(data.wordsCount.toString(), "Words", Modifier.weight(1f))
                }
            }

            // Daily goals
            if (data.dailyGoals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                    ProgressSectionHeader("Daily goals", "Resets in ${data.hoursUntilReset}h")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                            .padding(ZunoSpace.s16),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
                    ) {
                        data.dailyGoals.forEach { goal ->
                            DailyGoalTrack(
                                title = goal.title,
                                value = goal.valueLabel,
                                progress = goal.progress,
                            )
                        }
                    }
                }
            }

            // Skill balance + Hexagonal radar chart
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                ProgressSectionHeader("Skill balance", "Last 30 days")
                if (data.skills.hasData) {
                    val s = data.skills
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                            .padding(ZunoSpace.s16),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    ) {
                        SkillLabel("Pronunciation", "${s.pronunciation}%", highlight = s.focusArea == "Pronunciation")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                SkillLabel("Word stress", "${s.wordStress}%", highlight = s.focusArea == "Word stress")
                                SkillLabel("Grammar", "${s.grammar}%", highlight = s.focusArea == "Grammar")
                            }
                            SkillRadarChart(
                                values = listOf(
                                    s.pronunciation / 100f,
                                    s.listening / 100f,
                                    s.vocabulary / 100f,
                                    s.fluency / 100f,
                                    s.grammar / 100f,
                                    s.wordStress / 100f,
                                ),
                                modifier = Modifier.size(144.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                                horizontalAlignment = Alignment.End,
                            ) {
                                SkillLabel("Listening", "${s.listening}%", highlight = s.focusArea == "Listening", alignEnd = true)
                                SkillLabel("Vocabulary", "${s.vocabulary}%", highlight = s.focusArea == "Vocabulary", alignEnd = true)
                            }
                        }
                        SkillLabel("Fluency", "${s.fluency}%", highlight = s.focusArea == "Fluency")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                            .padding(ZunoSpace.s16),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        Text("No skill breakdown yet", style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                        Text(
                            text = "Complete your first voice lesson to unlock pronunciation, fluency, and grammar insights.",
                            style = ZunoType.FootnoteRegular,
                            color = ZunoColors.TextTertiary,
                        )
                    }
                }
            }

            // Recommended Drill card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                    .padding(ZunoSpace.s14),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.md)),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_t_volume, 16.dp, tint = ZunoColors.IconPrimary)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                ) {
                    val focusLabel = if (data.skills.hasData && data.skills.focusArea != null && data.skills.focusScore != null) {
                        "${data.skills.focusArea} · ${data.skills.focusScore}% · 3 min"
                    } else {
                        "Quick voice warmup · 3 min"
                    }
                    val drillTitle = when (data.skills.focusArea) {
                        "Word stress" -> "Accented syllables drill"
                        "Grammar" -> "Natural phrasing practice"
                        "Fluency" -> "Timed speaking warmup"
                        "Vocabulary" -> "Active recall conversation"
                        "Listening" -> "Conversational listening drill"
                        else -> "Start a quick speaking session"
                    }
                    Text(focusLabel, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    Text(drillTitle, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                }
                ZunoButton(
                    label = "Practice",
                    onClick = onStartDrill,
                    size = ZunoButtonSize.Small,
                )
            }

            // Speaking & consistency
            val activeDaysLabel = if (data.activeDaysLast12Weeks == 1) "1 active day" else "${data.activeDaysLast12Weeks} active days"
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                ProgressSectionHeader("Speaking & consistency", activeDaysLabel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                        .padding(ZunoSpace.s16),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                            Text("Speaking this week", style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
                            Text("%,d XP".format(data.weeklyXp), style = ZunoType.TitleMedium, color = ZunoColors.TextPrimary)
                        }
                        Text("Avg ${data.avgMinutesPerDay}m / day", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }

                    val maxActivity = (data.weeklyBars.maxOfOrNull { maxOf(it.minutes, if (it.xp > 0) 1 else 0) } ?: 0).coerceAtLeast(1)
                    val highlightIndex = data.weeklyBars.indexOfFirst { it.isToday }.coerceAtLeast(0)
                    WeeklyBarChart(
                        bars = data.weeklyBars.map { day ->
                            val value = if (day.minutes > 0) day.minutes.toFloat() else if (day.xp > 0) 0.5f else 0f
                            day.dayLabel to (value / maxActivity.toFloat()).coerceIn(0f, 1f)
                        },
                        highlightIndex = highlightIndex,
                    )

                    ZunoGlassHairline()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Last 12 weeks", style = ZunoType.FootnoteStrong, color = ZunoColors.TextSecondary)
                        Text(activeDaysLabel, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }

                    ConsistencyHeatmap(days = data.heatmapDays)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = data.heatmapMonths.joinToString(" · "),
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Less", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
                            listOf(0.08f, 0.22f, 0.48f, 0.92f).forEach { alpha ->
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = alpha)),
                                )
                            }
                            Text("More", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
        Text(trailing, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
    }
}

@Composable
private fun ProgressMetricCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
    ) {
        Text(value, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
        Text(label, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
    }
}

@Composable
private fun DailyGoalTrack(title: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = ZunoType.BodySmall, color = ZunoColors.TextPrimary)
            Text(value, style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
        }
        ZunoProgressBar(progress = progress, height = 4.dp)
    }
}

@Composable
private fun SkillLabel(title: String, value: String, highlight: Boolean, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = if (highlight) ZunoType.FootnoteStrong else ZunoType.FootnoteRegular,
            color = if (highlight) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Center,
        )
        Text(
            text = value,
            style = ZunoType.Micro,
            color = if (highlight) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
        )
    }
}

@Composable
private fun SkillRadarChart(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(cx, cy) * 0.88f
        val sides = values.size.coerceAtLeast(3)

        fun pointAt(index: Int, fraction: Float): Offset {
            val angle = (-PI / 2.0) + (2.0 * PI * index / sides)
            return Offset(
                x = cx + (maxR * fraction * cos(angle)).toFloat(),
                y = cy + (maxR * fraction * sin(angle)).toFloat(),
            )
        }

        listOf(0.33f, 0.66f, 1.0f).forEach { ring ->
            val path = Path()
            for (i in 0 until sides) {
                val p = pointAt(i, ring)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color = Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.dp.toPx()))
        }

        for (i in 0 until sides) {
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(cx, cy),
                end = pointAt(i, 1f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val dataPath = Path()
        values.forEachIndexed { idx, v ->
            val p = pointAt(idx, v.coerceIn(0.15f, 1f))
            if (idx == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, color = Color.White.copy(alpha = 0.14f))
        drawPath(dataPath, color = Color.White.copy(alpha = 0.88f), style = Stroke(width = 1.75.dp.toPx()))

        values.forEachIndexed { idx, v ->
            val p = pointAt(idx, v.coerceIn(0.15f, 1f))
            drawCircle(color = Color.White.copy(alpha = 0.92f), radius = 3.dp.toPx(), center = p)
        }
    }
}

@Composable
private fun WeeklyBarChart(bars: List<Pair<String, Float>>, highlightIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().height(92.dp),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { idx, (day, fraction) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val heightDp = if (fraction <= 0.01f) 6.dp else (64f * fraction.coerceIn(0.14f, 1f)).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(heightDp)
                            .clip(RoundedCornerShape(ZunoRadius.sm))
                            .background(
                                when {
                                    idx == highlightIndex && fraction > 0.01f -> ZunoColors.SurfaceInverse
                                    fraction > 0.01f -> ZunoColors.SurfaceStrong
                                    else -> Color.White.copy(alpha = 0.08f)
                                },
                            ),
                    )
                }
                Text(
                    text = day,
                    style = ZunoType.Micro,
                    color = if (idx == highlightIndex) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun ConsistencyHeatmap(days: List<HeatmapDay>) {
    // 12 columns (weeks) x 7 rows (Mon..Sun) or padded 84-day grid
    val cols = 12
    val rows = 7
    val levels = listOf(0.07f, 0.22f, 0.48f, 0.92f)
    val totalCells = cols * rows
    val padded = if (days.size >= totalCells) {
        days.takeLast(totalCells)
    } else {
        List(totalCells - days.size) { HeatmapDay("", 0, 0) } + days
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (c in 0 until cols) {
                    val cell = padded[c * rows + r]
                    val levelIdx = cell.level.coerceIn(0, levels.lastIndex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = levels[levelIdx])),
                    )
                }
            }
        }
    }
}
