package com.talkie.ai.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.data.Schedule
import com.talkie.ai.data.ScheduleDraft
import com.talkie.ai.data.ScheduleRepeat
import com.talkie.ai.data.ScheduleTargets
import com.talkie.ai.ui.components.ZunoBackTitleTopBar
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoCheckBadge
import com.talkie.ai.ui.components.ZunoDropdownMenu
import com.talkie.ai.ui.components.ZunoEmptyState
import com.talkie.ai.ui.components.ZunoFab
import com.talkie.ai.ui.components.ZunoIcon
import com.talkie.ai.ui.components.ZunoLoadState
import com.talkie.ai.ui.components.ZunoMenuAction
import com.talkie.ai.ui.components.ZunoSheet
import com.talkie.ai.ui.components.ZunoSheetAction
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import com.talkie.ai.viewmodel.Loadable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone

// ── Formatting ───────────────────────────────────────────────────────────────

/** Labels for schedules: times follow the device's 12/24-hour setting, names follow the app language. */
object ScheduleFormat {
    private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    fun time(context: Context, timeOfDay: String): String {
        val time = parseTime(timeOfDay)
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, locale(context)))
    }

    fun time(context: Context, millis: Long): String =
        DateFormat.getTimeFormat(context).format(java.util.Date(millis))

    fun parseTime(value: String): LocalTime = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))

    fun weekdayShort(context: Context, index: Int): String =
        DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, locale(context))

    /** Single-letter chips M T W T F S S. */
    fun weekdayLetter(context: Context, index: Int): String =
        DayOfWeek.of(index + 1).getDisplayName(TextStyle.NARROW_STANDALONE, locale(context))

    fun date(context: Context, date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(context), "EEEMMMd"), locale(context)))

    fun repeatLabel(context: Context, repeat: ScheduleRepeat): String = context.getString(
        when (repeat) {
            ScheduleRepeat.Once -> R.string.schedule_repeat_once
            ScheduleRepeat.Daily -> R.string.schedule_repeat_daily
            ScheduleRepeat.Weekdays -> R.string.schedule_repeat_weekdays
            ScheduleRepeat.Weekly -> R.string.schedule_repeat_weekly
            ScheduleRepeat.Monthly -> R.string.schedule_repeat_monthly
        },
    )

    private fun days(context: Context, weekdays: List<Int>) = weekdays.sorted().joinToString(", ") { weekdayShort(context, it) }

    /** Step 2 headline: "Daily · 9:00 AM", "Weekly · Mon, Wed, Fri · 9:00 AM", "Once · Sun, Nov 8, 9:00 AM". */
    fun summary(context: Context, repeat: ScheduleRepeat, timeOfDay: String, date: LocalDate?, weekdays: List<Int>, monthDay: Int?): String {
        val time = time(context, timeOfDay)
        val label = repeatLabel(context, repeat)
        return when (repeat) {
            ScheduleRepeat.Once -> "$label · ${date?.let { date(context, it) } ?: "—"}, $time"
            ScheduleRepeat.Weekly -> listOf(label, days(context, weekdays).ifBlank { "—" }, time).joinToString(" · ")
            ScheduleRepeat.Monthly -> "$label · ${context.getString(R.string.schedule_day_n, monthDay ?: 1)} · $time"
            else -> "$label · $time"
        }
    }

    /** Toast / sheet phrase: "Daily at 9:00 AM", "Weekly on Mon, Wed at 9:00 AM", "Once on Sun, Nov 8 at 9:00 AM". */
    fun phrase(context: Context, schedule: Schedule): String {
        val time = time(context, schedule.timeOfDay)
        return when (schedule.repeat) {
            ScheduleRepeat.Once -> context.getString(
                R.string.schedule_phrase_once,
                schedule.date?.let { runCatching { date(context, LocalDate.parse(it)) }.getOrNull() } ?: "—",
                time,
            )
            ScheduleRepeat.Daily -> context.getString(R.string.schedule_phrase_daily, time)
            ScheduleRepeat.Weekdays -> context.getString(R.string.schedule_phrase_weekdays, time)
            ScheduleRepeat.Weekly -> context.getString(R.string.schedule_phrase_weekly, days(context, schedule.weekdays), time)
            ScheduleRepeat.Monthly -> context.getString(R.string.schedule_phrase_monthly, schedule.monthDay ?: 1, time)
        }
    }

    fun minutes(context: Context, minutes: Int) = context.getString(R.string.schedule_minutes, minutes)

    /** "Roleplay · Daily at 9:00 AM · 15 min" */
    fun subtitle(context: Context, schedule: Schedule): String = listOf(
        context.getString(if (schedule.isRoleplay) R.string.schedule_kind_roleplay else R.string.schedule_kind_topic),
        phrase(context, schedule),
        minutes(context, schedule.durationMin),
    ).joinToString(" · ")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
}

private enum class ScheduleGroup(val label: Int) {
    Today(R.string.schedule_group_today),
    Tomorrow(R.string.schedule_group_tomorrow),
    ThisWeek(R.string.schedule_group_this_week),
    Later(R.string.schedule_group_later),
    Paused(R.string.schedule_group_paused),
}

private fun groupOf(schedule: Schedule, today: LocalDate): ScheduleGroup {
    val next = schedule.nextRunAtMillis ?: return ScheduleGroup.Paused
    if (!schedule.active) return ScheduleGroup.Paused
    val day = ScheduleFormat.localDate(next)
    return when {
        day <= today -> ScheduleGroup.Today
        day == today.plusDays(1) -> ScheduleGroup.Tomorrow
        day < today.plusDays(7) -> ScheduleGroup.ThisWeek
        else -> ScheduleGroup.Later
    }
}

// ── 07 · Scheduled (list / empty) ────────────────────────────────────────────

@Composable
fun ScheduledScreen(
    schedules: Loadable<List<Schedule>>,
    highlightId: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Schedule) -> Unit,
    onStart: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = schedules.items
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            ZunoBackTitleTopBar(title = stringResource(R.string.schedule_title), onBack = onBack)
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ZunoLoadState(error = schedules.error, onRetry = onRetry)
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.Center) {
                    ZunoEmptyState(
                        icon = R.drawable.zuno_ic_calendar,
                        title = stringResource(R.string.schedule_empty_title),
                        description = stringResource(R.string.schedule_empty_body),
                    )
                }
                else -> {
                    val today = LocalDate.now(ScheduleFormat.zone())
                    val grouped = items.groupBy { groupOf(it, today) }
                    // The soonest session today gets a "start" button.
                    val startable = grouped[ScheduleGroup.Today]?.firstOrNull()?.id
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                    ) {
                        ScheduleGroup.entries.forEach { group ->
                            val rows = grouped[group] ?: return@forEach
                            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                                Text(
                                    stringResource(group.label),
                                    style = ZunoType.LabelMedium,
                                    color = ZunoColors.TextTertiary,
                                    modifier = Modifier.padding(top = ZunoSpace.s4),
                                )
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(ZunoRadius.xl)),
                                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                                ) {
                                    rows.forEach { schedule ->
                                        ScheduleRow(
                                            schedule = schedule,
                                            group = group,
                                            startable = schedule.id == startable,
                                            highlighted = schedule.id == highlightId,
                                            onClick = { onOpen(schedule) },
                                            onStart = { onStart(schedule) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ZunoFab(
            label = stringResource(R.string.schedule_fab),
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = ZunoSpace.s16, bottom = ZunoSpace.s16),
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    group: ScheduleGroup,
    startable: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    var flash by remember(schedule.id) { mutableStateOf(highlighted) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            kotlinx.coroutines.delay(1600)
            flash = false
        }
    }
    val background by animateColorAsState(
        targetValue = if (flash || startable) ZunoColors.SurfaceStrong else ZunoColors.SurfaceDefault,
        animationSpec = tween(600),
        label = "scheduleRow",
    )
    val next = schedule.nextRunAtMillis
    val (primary, secondary) = when {
        next == null -> ScheduleFormat.time(context, schedule.timeOfDay) to ScheduleFormat.minutes(context, schedule.durationMin)
        group == ScheduleGroup.Today || group == ScheduleGroup.Tomorrow ->
            ScheduleFormat.time(context, next) to ScheduleFormat.minutes(context, schedule.durationMin)
        group == ScheduleGroup.ThisWeek ->
            ScheduleFormat.localDate(next).dayOfWeek.getDisplayName(TextStyle.SHORT, context.resources.configuration.locales[0]) to
                ScheduleFormat.time(context, next)
        else -> ScheduleFormat.date(context, ScheduleFormat.localDate(next)) to ScheduleFormat.time(context, next)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s16, bottom = ZunoSpace.s16),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(primary, style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary, maxLines = 1)
            Text(secondary, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, maxLines = 1)
        }
        Box(Modifier.width(1.dp).height(36.dp).background(ZunoColors.BorderDefault))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
            Text(
                schedule.title,
                style = ZunoType.BodyMediumStrong,
                color = if (schedule.active) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6), verticalAlignment = Alignment.CenterVertically) {
                ZunoIcon(
                    if (schedule.isRoleplay) R.drawable.zuno_ic_t_users else R.drawable.zuno_ic_t_book_open,
                    14.dp,
                    tint = ZunoColors.IconSecondary,
                )
                Text(
                    targetMeta(context, schedule),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (schedule.repeat != ScheduleRepeat.Once) {
                    ZunoIcon(R.drawable.zuno_ic_t_repeat, 14.dp, tint = ZunoColors.IconSecondary)
                    Text(
                        ScheduleFormat.repeatLabel(context, schedule.repeat),
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (startable) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceInverse)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.schedule_start_now), onClick = onStart),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_arrow_right, 20.dp, tint = ZunoColors.IconOnInverse)
            }
        } else {
            ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 20.dp, tint = ZunoColors.IconTertiary)
        }
    }
}

/** "Topic · Lesson 2" / "Roleplay" — the server meta for topics, a short kind for roleplays. */
private fun targetMeta(context: Context, schedule: Schedule): String =
    if (schedule.isRoleplay) context.getString(R.string.schedule_kind_roleplay)
    else schedule.meta.ifBlank { context.getString(R.string.schedule_kind_topic) }

// ── Item actions sheet ───────────────────────────────────────────────────────

@Composable
fun ScheduleActionsSheet(
    schedule: Schedule,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val skipLabel = remember(schedule.nextRunAtMillis) {
        val next = schedule.nextRunAtMillis?.let(ScheduleFormat::localDate)
        val today = LocalDate.now(ScheduleFormat.zone())
        when (next) {
            null -> context.getString(R.string.schedule_skip_next)
            today -> context.getString(R.string.schedule_skip_today)
            today.plusDays(1) -> context.getString(R.string.schedule_skip_tomorrow)
            else -> context.getString(R.string.schedule_skip_day, ScheduleFormat.date(context, next))
        }
    }
    ZunoSheet(
        onDismiss = onDismiss,
        title = schedule.title,
        description = ScheduleFormat.subtitle(context, schedule),
        spacing = ZunoSpace.s8,
    ) {
        Column {
            ZunoSheetAction(R.drawable.zuno_ic_t_arrow_right, stringResource(R.string.schedule_start_now), onStart)
            ZunoSheetAction(R.drawable.zuno_ic_t_clock, stringResource(R.string.schedule_edit), onEdit, chevron = true)
            ZunoSheetAction(
                R.drawable.zuno_ic_t_rotate_ccw,
                skipLabel,
                onSkip,
                enabled = schedule.active && schedule.nextRunAtMillis != null,
            )
            ZunoSheetAction(R.drawable.zuno_ic_t_x, stringResource(R.string.schedule_delete), onDelete)
        }
    }
}

// ── New schedule (2 steps) ───────────────────────────────────────────────────

/**
 * New schedule: step 1 picks a topic or roleplay, step 2 sets repeat, date/days, time and options.
 * Editing an existing schedule ([editing]) opens straight on step 2, prefilled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewScheduleScreen(
    targets: Loadable<ScheduleTargets>,
    editing: Schedule?,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (ScheduleDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(if (editing != null) 2 else 1) }
    var roleplayTab by rememberSaveable { mutableStateOf(editing?.isRoleplay ?: false) }
    var targetId by rememberSaveable { mutableStateOf(editing?.let { it.roleplayId ?: it.topicId }) }
    var targetIsRoleplay by rememberSaveable { mutableStateOf(editing?.isRoleplay ?: false) }

    var repeat by rememberSaveable { mutableStateOf(editing?.repeat ?: ScheduleRepeat.Daily) }
    var timeOfDay by rememberSaveable { mutableStateOf(editing?.timeOfDay ?: "09:00") }
    var date by rememberSaveable { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var weekdays by rememberSaveable {
        mutableStateOf(editing?.weekdays?.takeIf { it.isNotEmpty() } ?: listOf(LocalDate.now().dayOfWeek.value - 1))
    }
    var monthDay by rememberSaveable { mutableIntStateOf(editing?.monthDay ?: LocalDate.now().dayOfMonth) }
    var active by rememberSaveable { mutableStateOf(editing?.active ?: true) }
    var remindBefore by rememberSaveable { mutableStateOf(editing?.let { it.remindBeforeMin != null } ?: true) }
    var notifyEmail by rememberSaveable { mutableStateOf(editing?.notifyEmail ?: false) }

    val topics = targets.items?.topics.orEmpty()
    val roleplays = targets.items?.roleplays.orEmpty()
    val selectedTopic = topics.firstOrNull { !targetIsRoleplay && it.id == targetId }
    val selectedRoleplay = roleplays.firstOrNull { targetIsRoleplay && it.id == targetId }
    val duration = editing?.durationMin ?: selectedRoleplay?.durationMin ?: selectedTopic?.durationMin ?: 15

    fun back() {
        if (step == 2 && editing == null) step = 1 else onBack()
    }
    androidx.activity.compose.BackHandler(enabled = step == 2 && editing == null) { step = 1 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(if (editing != null) R.string.schedule_edit_title else R.string.schedule_new_title),
            onBack = ::back,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            if (step == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                    Text(stringResource(R.string.schedule_step, 1, 2), style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    Text(stringResource(R.string.schedule_step1_question), style = ZunoType.BodyMedium, color = ZunoColors.TextSecondary)
                }
                SegmentedTabs(
                    labels = listOf(stringResource(R.string.drawer_topics), stringResource(R.string.drawer_roleplays)),
                    selected = if (roleplayTab) 1 else 0,
                    onSelect = { roleplayTab = it == 1 },
                )
                if (targets.items == null) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        ZunoLoadState(error = targets.error, onRetry = onRetry)
                    }
                } else {
                    val emptyText = stringResource(if (roleplayTab) R.string.schedule_no_roleplays else R.string.schedule_no_topics)
                    val rows = if (roleplayTab) {
                        roleplays.map { rp ->
                            TargetRowData(
                                rp.id, true, rp.title,
                                listOf(
                                    context.getString(R.string.schedule_roles, rp.userRole, rp.aiRole),
                                    ScheduleFormat.minutes(context, rp.durationMin),
                                ).joinToString(" · "),
                            )
                        }
                    } else {
                        topics.map { topic ->
                            val progress = if (topic.completed || topic.nextLessonPosition == null) {
                                context.getString(R.string.schedule_topic_review)
                            } else {
                                context.getString(R.string.schedule_topic_next, topic.nextLessonPosition, topic.totalLessons)
                            }
                            TargetRowData(topic.id, false, topic.title, "$progress · ${ScheduleFormat.minutes(context, topic.durationMin)}")
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            emptyText,
                            style = ZunoType.BodySmall,
                            color = ZunoColors.TextTertiary,
                            modifier = Modifier.padding(horizontal = ZunoSpace.s4, vertical = ZunoSpace.s16),
                        )
                    } else {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(ZunoRadius.xl)),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                        ) {
                            rows.forEach { row ->
                                val selected = row.id == targetId && row.isRoleplay == targetIsRoleplay
                                TargetRow(row, selected) {
                                    targetId = row.id
                                    targetIsRoleplay = row.isRoleplay
                                }
                            }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.schedule_step, 2, 2), style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                val title = editing?.title ?: selectedRoleplay?.title ?: selectedTopic?.title.orEmpty()
                val meta = when {
                    editing != null -> listOf(
                        context.getString(if (editing.isRoleplay) R.string.schedule_kind_roleplay else R.string.schedule_kind_topic),
                        editing.meta.substringAfter("Topic · ").takeIf { !editing.isRoleplay } ?: editing.meta,
                    ).joinToString(" · ")
                    selectedRoleplay != null -> listOf(
                        context.getString(R.string.schedule_kind_roleplay),
                        context.getString(R.string.schedule_roles, selectedRoleplay.userRole, selectedRoleplay.aiRole),
                    ).joinToString(" · ")
                    selectedTopic != null -> listOf(
                        context.getString(R.string.schedule_kind_topic),
                        if (selectedTopic.completed || selectedTopic.nextLessonPosition == null) context.getString(R.string.schedule_topic_review)
                        else context.getString(R.string.schedule_topic_next, selectedTopic.nextLessonPosition, selectedTopic.totalLessons),
                    ).joinToString(" · ")
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ZunoRadius.xxl))
                        .background(ZunoColors.SurfaceDefault)
                        .padding(ZunoSpace.s16),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TargetBadge(if (targetIsRoleplay) R.drawable.zuno_ic_t_users else R.drawable.zuno_ic_t_book_open)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                        Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(meta, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ZunoCheckBadge(selected = true)
                }

                val localDate = runCatching { LocalDate.parse(date) }.getOrNull()
                FormSection(stringResource(R.string.schedule_section_schedule)) {
                    Text(
                        ScheduleFormat.summary(context, repeat, timeOfDay, localDate, weekdays, monthDay),
                        style = ZunoType.Headline,
                        color = ZunoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s20),
                    )
                    FormDivider()
                    var repeatOpen by remember { mutableStateOf(false) }
                    FormRow(R.drawable.zuno_ic_t_repeat, stringResource(R.string.schedule_repeat)) {
                        Box {
                            SelectPill(ScheduleFormat.repeatLabel(context, repeat), chevron = true) { repeatOpen = true }
                            val options = ScheduleRepeat.entries.map { option ->
                                ZunoMenuAction(ScheduleFormat.repeatLabel(context, option)) { repeat = option }
                            }
                            ZunoDropdownMenu(
                                expanded = repeatOpen,
                                onDismiss = { repeatOpen = false },
                                actions = options,
                                selected = options[ScheduleRepeat.entries.indexOf(repeat)],
                            )
                        }
                    }
                    when (repeat) {
                        ScheduleRepeat.Once -> {
                            FormDivider()
                            FormRow(R.drawable.zuno_ic_t_calendar, stringResource(R.string.schedule_date)) {
                                SelectPill(localDate?.let { ScheduleFormat.date(context, it) } ?: "—") {
                                    val initial = localDate ?: LocalDate.now()
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() },
                                        initial.year,
                                        initial.monthValue - 1,
                                        initial.dayOfMonth,
                                    ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                                }
                            }
                        }
                        ScheduleRepeat.Weekly -> {
                            FormDivider()
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s16),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            ) {
                                Text(stringResource(R.string.schedule_days_hint), style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    (0..6).forEach { day ->
                                        DayChip(ScheduleFormat.weekdayLetter(context, day), selected = day in weekdays) {
                                            weekdays = if (day in weekdays) weekdays - day else (weekdays + day).sorted()
                                        }
                                    }
                                }
                            }
                        }
                        ScheduleRepeat.Monthly -> {
                            FormDivider()
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s16),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            ) {
                                Text(stringResource(R.string.schedule_month_day), style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                                    maxItemsInEachRow = 7,
                                ) {
                                    (1..31).forEach { day ->
                                        DayChip(day.toString(), selected = day == monthDay, modifier = Modifier.weight(1f)) { monthDay = day }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                    FormDivider()
                    FormRow(R.drawable.zuno_ic_t_clock, stringResource(R.string.schedule_time)) {
                        SelectPill(ScheduleFormat.time(context, timeOfDay)) {
                            val current = ScheduleFormat.parseTime(timeOfDay)
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> timeOfDay = "%02d:%02d".format(Locale.ROOT, hour, minute) },
                                current.hour,
                                current.minute,
                                DateFormat.is24HourFormat(context),
                            ).show()
                        }
                    }
                }

                FormSection(stringResource(R.string.schedule_section_more)) {
                    ZunoSettingsRow(
                        title = stringResource(R.string.schedule_active),
                        subtitle = stringResource(R.string.schedule_active_sub),
                        icon = null,
                        leading = { ZunoIcon(R.drawable.zuno_ic_t_play_circle, 22.dp, tint = ZunoColors.IconSecondary) },
                        checked = active,
                        onClick = { active = !active },
                    )
                    ZunoSettingsRow(
                        title = stringResource(R.string.schedule_remind_before),
                        subtitle = stringResource(R.string.schedule_remind_before_sub),
                        icon = null,
                        leading = { ZunoIcon(R.drawable.zuno_ic_t_bell, 22.dp, tint = ZunoColors.IconSecondary) },
                        checked = remindBefore,
                        showDivider = true,
                        onClick = { remindBefore = !remindBefore },
                    )
                    ZunoSettingsRow(
                        title = stringResource(R.string.schedule_notify_email),
                        icon = null,
                        leading = { ZunoIcon(R.drawable.zuno_ic_t_mail, 22.dp, tint = ZunoColors.IconSecondary) },
                        checked = notifyEmail,
                        showDivider = true,
                        onClick = { notifyEmail = !notifyEmail },
                    )
                }
            }
        }
        val canContinue = targetId != null && (targetIsRoleplay && selectedRoleplay != null || !targetIsRoleplay && selectedTopic != null)
        val canSave = (editing != null || canContinue) && when (repeat) {
            ScheduleRepeat.Weekly -> weekdays.isNotEmpty()
            ScheduleRepeat.Once -> runCatching { LocalDate.parse(date) }.isSuccess
            else -> true
        }
        ZunoButton(
            label = stringResource(
                when {
                    step == 1 -> R.string.schedule_continue
                    editing != null -> R.string.common_save
                    else -> R.string.schedule_fab
                },
            ),
            onClick = {
                if (step == 1) {
                    step = 2
                } else {
                    onSave(
                        ScheduleDraft(
                            isRoleplay = targetIsRoleplay,
                            targetId = targetId.orEmpty(),
                            repeat = repeat,
                            timeOfDay = timeOfDay,
                            date = date,
                            weekdays = weekdays,
                            monthDay = monthDay,
                            timezone = TimeZone.getDefault().id,
                            durationMin = duration,
                            active = active,
                            remindBeforeMin = if (remindBefore) 10 else null,
                            notifyEmail = notifyEmail,
                        ),
                    )
                }
            },
            enabled = if (step == 1) canContinue else canSave,
            loading = busy,
            modifier = Modifier.fillMaxWidth().padding(start = ZunoSpace.s16, end = ZunoSpace.s16, bottom = ZunoSpace.s16),
        )
    }
}

private data class TargetRowData(val id: String, val isRoleplay: Boolean, val title: String, val meta: String)

@Composable
private fun TargetRow(row: TargetRowData, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) ZunoColors.SurfaceStrong else ZunoColors.SurfaceDefault)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(ZunoSpace.s16),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TargetBadge(if (row.isRoleplay) R.drawable.zuno_ic_t_users else R.drawable.zuno_ic_t_book_open)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(row.title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.meta, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ZunoCheckBadge(selected)
    }
}

@Composable
private fun TargetBadge(@DrawableRes icon: Int) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(ZunoRadius.md)).background(ZunoColors.SurfaceStrong),
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, 20.dp, tint = ZunoColors.IconPrimary)
    }
}

/** Two-segment control (Topics / Roleplays). */
@Composable
private fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderDefault, shape)
            .padding(ZunoSpace.s4),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (index == selected) ZunoColors.SurfaceStrong else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = ZunoType.LabelMedium,
                    color = if (index == selected) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun FormSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    ZunoSettingsGroup(sectionLabel = label, content = content)
}

@Composable
private fun FormDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ZunoColors.BorderSubtle))
}

@Composable
private fun FormRow(@DrawableRes icon: Int, label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 18.dp, tint = ZunoColors.IconSecondary)
        Text(label, style = ZunoType.BodyMedium, color = ZunoColors.TextPrimary, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SelectPill(label: String, chevron: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(ZunoRadius.md))
            .background(ZunoColors.SurfaceStrong)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s14, end = if (chevron) ZunoSpace.s10 else ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ZunoType.BodyMedium, color = ZunoColors.TextPrimary, maxLines = 1)
        if (chevron) ZunoIcon(R.drawable.zuno_ic_t_chevron_down, 16.dp, tint = ZunoColors.IconTertiary)
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) ZunoColors.SurfaceInverse else ZunoColors.SurfaceDefault)
            .then(if (selected) Modifier else Modifier.border(1.dp, ZunoColors.BorderDefault, CircleShape))
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (selected) ZunoType.LabelLarge else ZunoType.LabelMedium,
            color = if (selected) ZunoColors.TextOnInverse else ZunoColors.TextSecondary,
        )
    }
}
