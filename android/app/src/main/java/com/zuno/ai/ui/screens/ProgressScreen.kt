package com.zuno.ai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoIcon
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
    onBack: () -> Unit,
    onStartDrill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val levelIndex = CefrOrder.indexOf(level).coerceAtLeast(0)
    val nextLevel = CefrOrder.getOrNull(levelIndex + 1) ?: "C2"

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
                    Text("$languageName · $level", style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                    Text("68% to $nextLevel", style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
                }

                ZunoProgressBar(progress = 0.68f, height = 5.dp)

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
                    ProgressMetricCell("${streakDays}d", "Streak", Modifier.weight(1f))
                    ZunoGlassVerticalHairline(28.dp)
                    ProgressMetricCell("186m", "Spoken", Modifier.weight(1f))
                    ZunoGlassVerticalHairline(28.dp)
                    ProgressMetricCell("248", "Words", Modifier.weight(1f))
                }
            }

            // Daily goals
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                ProgressSectionHeader("Daily goals", "Resets in 6h")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                        .padding(ZunoSpace.s16),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
                ) {
                    DailyGoalTrack("Speak for 10 minutes", "10 / 10m", 1.0f)
                    DailyGoalTrack("Use 3 due words in chat", "2 / 3", 0.67f)
                    DailyGoalTrack("Complete 2 shadow drills", "1 / 2", 0.5f)
                }
            }

            // Skill balance + Hexagonal radar chart
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                ProgressSectionHeader("Skill balance", "Last 30 days")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                        .padding(ZunoSpace.s16),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    SkillLabel("Pronunciation", "91%", highlight = false)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            SkillLabel("Word stress", "72%", highlight = true)
                            SkillLabel("Grammar", "76%", highlight = false)
                        }
                        SkillRadarChart(
                            values = listOf(0.91f, 0.90f, 0.88f, 0.84f, 0.76f, 0.72f),
                            modifier = Modifier.size(144.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
                            horizontalAlignment = Alignment.End,
                        ) {
                            SkillLabel("Listening", "90%", highlight = false, alignEnd = true)
                            SkillLabel("Vocabulary", "88%", highlight = false, alignEnd = true)
                        }
                    }
                    SkillLabel("Fluency", "84%", highlight = false)
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
                    Text("Word stress · 72% · 3 min", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    Text("Accented syllables drill", style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                }
                ZunoButton(
                    label = "Drill",
                    onClick = onStartDrill,
                    size = ZunoButtonSize.Small,
                )
            }

            // Speaking & consistency
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                ProgressSectionHeader("Speaking & consistency", "58 active days")
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
                            Text("1,240 XP", style = ZunoType.TitleMedium, color = ZunoColors.TextPrimary)
                        }
                        Text("Avg 26m / day", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }

                    WeeklyBarChart(
                        bars = listOf("M" to 0.38f, "T" to 0.56f, "W" to 0.30f, "T" to 0.64f, "F" to 0.44f, "S" to 1.0f, "S" to 0.62f),
                        highlightIndex = 5,
                    )

                    ZunoGlassHairline()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Last 12 weeks", style = ZunoType.FootnoteStrong, color = ZunoColors.TextSecondary)
                        Text("58 active days", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }

                    ConsistencyHeatmap()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Sep · Oct · Nov", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Less", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
                            listOf(0.08f, 0.20f, 0.45f, 0.92f).forEach { alpha ->
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

        // Concentric hexagonal rings
        listOf(0.33f, 0.66f, 1.0f).forEach { ring ->
            val path = Path()
            for (i in 0 until sides) {
                val p = pointAt(i, ring)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color = Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.dp.toPx()))
        }

        // Spokes
        for (i in 0 until sides) {
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(cx, cy),
                end = pointAt(i, 1f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Filled data polygon
        val dataPath = Path()
        values.forEachIndexed { idx, v ->
            val p = pointAt(idx, v.coerceIn(0.15f, 1f))
            if (idx == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, color = Color.White.copy(alpha = 0.14f))
        drawPath(dataPath, color = Color.White.copy(alpha = 0.88f), style = Stroke(width = 1.75.dp.toPx()))

        // Data vertices
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height((64 * fraction.coerceIn(0.15f, 1f)).dp)
                            .clip(RoundedCornerShape(ZunoRadius.sm))
                            .background(if (idx == highlightIndex) ZunoColors.SurfaceInverse else ZunoColors.SurfaceStrong),
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
private fun ConsistencyHeatmap() {
    val rows = 6
    val cols = 12
    val levels = listOf(0.07f, 0.16f, 0.36f, 0.92f)
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
                    val hash = (r * 7 + c * 13 + (c % 3) * 5) % 11
                    val levelIdx = when {
                        c >= 9 && (r + c) % 2 == 0 -> 3
                        hash >= 9 -> 3
                        hash >= 6 -> 2
                        hash >= 3 -> 1
                        else -> 0
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = levels[levelIdx])),
                    )
                }
            }
        }
    }
}
