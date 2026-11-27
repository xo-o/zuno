package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.avatar.catalog.LocalActiveTutor
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.ui.TutorStageAvatar
import com.zuno.ai.data.VocabBucket
import com.zuno.ai.data.VocabCounts
import com.zuno.ai.data.VocabLibrary
import com.zuno.ai.data.VocabPhrase
import com.zuno.ai.data.VocabPreview
import com.zuno.ai.ui.components.CallAvatarItem
import com.zuno.ai.ui.components.CoachStageHeader
import com.zuno.ai.ui.components.StatusPillTone
import com.zuno.ai.ui.components.TurnPillState
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoBlankCueTile
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoCallControlsBar
import com.zuno.ai.ui.components.ZunoCallTopBar
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoCoachTilePill
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoFeedbackPill
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.components.ZunoStatusPill
import com.zuno.ai.ui.components.ZunoTextField
import com.zuno.ai.ui.components.ZunoTurnPillRow
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.components.parseSyllables
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout
import com.zuno.ai.viewmodel.Loadable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DefaultVocabPhrases = listOf(
    VocabPhrase("v1", "es", "pedir la cuenta", "to ask for the bill", "Café roleplay", exampleSentence = "¿Nos podría traer la cuenta cuando pueda?", exampleTranslation = "Could you bring us the bill when you can?", syllableStress = "pe·DIR la CUEN·ta", recallCue = "Could you bring me the bill, please?", bucket = VocabBucket.Due, dueNow = true, intervalLabel = "Due now"),
    VocabPhrase("v2", "es", "hacer escala", "to have a layover", "Airport check-in", exampleSentence = "Necesito hacer escala en Madrid.", exampleTranslation = "I need to have a layover in Madrid.", syllableStress = "ha·CER es·CA·la", recallCue = "To stop over between flights", bucket = VocabBucket.Due, dueNow = true, intervalLabel = "Due now"),
    VocabPhrase("v3", "es", "tomo la línea cinco", "I take line five", "Lesson 3", exampleSentence = "Normalmente tomo la línea cinco al centro.", exampleTranslation = "I usually take line five downtown.", syllableStress = "TO·mo la LÍ·ne·a CIN·co", recallCue = "Explain which metro line you take", bucket = VocabBucket.Due, dueNow = true, intervalLabel = "Due now"),
    VocabPhrase("v4", "es", "para llevar", "to go / takeaway", "Pedir un café", exampleSentence = "Un café con leche para llevar, por favor.", exampleTranslation = "A coffee with milk to go, please.", intervalDays = 7, intervalLabel = "7d", bucket = VocabBucket.Learning, dueNow = false),
    VocabPhrase("v5", "es", "¿pasa por la estación?", "does it stop by the station?", "Lesson 3", exampleSentence = "Disculpe, ¿pasa por la estación?", exampleTranslation = "Excuse me, does it stop by the station?", intervalDays = 14, intervalLabel = "14d", bucket = VocabBucket.Learning, dueNow = false),
    VocabPhrase("v6", "es", "encantado de conocerte", "lovely to meet you", "Free Talk", exampleSentence = "¡Hola! Encantado de conocerte.", exampleTranslation = "Hi! Lovely to meet you.", intervalDays = 30, intervalLabel = "30d", bucket = VocabBucket.Mastered, dueNow = false),
)

data class SprintReviewResult(
    val phrase: VocabPhrase,
    val passed: Boolean,
    val nextIntervalLabel: String,
)

/**
 * `12 · Vocabulary` (`2462:10079` SRS Library, `2746:13274` All Caught Up,
 * `2985:12530` Empty Library, `2985:12634` Search & Quick Add, `2985:12721` Add Sheet, `2985:12783` Detail Sheet).
 */
@Composable
fun VocabularyScreen(
    languageName: String,
    level: String,
    onBack: () -> Unit,
    onStartVoiceSprint: () -> Unit,
    onSpeakPhrase: (String) -> Unit,
    extraSavedPhrases: Set<String> = emptySet(),
    library: Loadable<VocabLibrary>? = null,
    onSlowSpeakPhrase: (String) -> Unit = onSpeakPhrase,
    onPreviewPhrase: suspend (String, String?) -> VocabPreview? = { _, _ -> null },
    onAddPhrase: (phrase: String, translation: String?, contextNote: String?) -> Unit = { _, _, _ -> },
    onUpdatePhrase: (id: String, phrase: String, translation: String, contextNote: String?) -> Unit = { _, _, _, _ -> },
    onDeletePhrase: (String) -> Unit = {},
    onPracticePhrase: (VocabPhrase) -> Unit = { onStartVoiceSprint() },
    onStartChatWithZuno: () -> Unit = onBack,
    onOpenTopics: () -> Unit = onBack,
    modifier: Modifier = Modifier,
) {
    val activeTutor = LocalActiveTutor.current
    val scope = rememberCoroutineScope()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<VocabBucket?>(null) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var addPrefill by rememberSaveable { mutableStateOf("") }
    var selectedPhrase by remember { mutableStateOf<VocabPhrase?>(null) }
    var editingPhrase by remember { mutableStateOf<VocabPhrase?>(null) }

    val fallbackItems = remember(extraSavedPhrases) {
        mutableStateListOf<VocabPhrase>().apply {
            extraSavedPhrases.forEachIndexed { idx, phrase ->
                if (DefaultVocabPhrases.none { it.phrase.equals(phrase, ignoreCase = true) }) {
                    add(
                        VocabPhrase(
                            id = "lesson-$idx-${phrase.hashCode()}",
                            phrase = phrase,
                            translation = "Saved from lesson",
                            source = "Lesson evaluation",
                            dueNow = true,
                            intervalLabel = "Due now",
                            bucket = VocabBucket.Due,
                        ),
                    )
                }
            }
            addAll(DefaultVocabPhrases)
        }
    }

    val liveLib = library?.items
    val allPhrases = liveLib?.phrases ?: fallbackItems
    val counts = liveLib?.counts ?: VocabCounts(
        due = allPhrases.count { it.dueNow },
        learning = allPhrases.count { !it.dueNow && it.bucket == VocabBucket.Learning },
        mastered = allPhrases.count { !it.dueNow && it.bucket == VocabBucket.Mastered },
        total = allPhrases.size,
    )

    val q = query.trim().lowercase()
    val visible = allPhrases.filter { item ->
        val matchesBucket = when (filter) {
            null -> true
            VocabBucket.Due -> item.dueNow
            VocabBucket.Learning -> !item.dueNow && item.bucket == VocabBucket.Learning
            VocabBucket.Mastered -> !item.dueNow && item.bucket == VocabBucket.Mastered
        }
        val matchesQuery = q.isEmpty() || item.phrase.lowercase().contains(q) || item.translation.lowercase().contains(q)
        matchesBucket && matchesQuery
    }

    val isEmptyLibrary = liveLib != null && allPhrases.isEmpty()
    val isAllCaughtUp = !isEmptyLibrary && counts.due == 0 && (filter == null || filter == VocabBucket.Due)

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoSectionTopBar(
            title = stringResource(R.string.vocab_title),
            onBack = onBack,
            showSearch = !isEmptyLibrary,
            searchActive = searchOpen,
            onSearchToggle = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
            showAdd = true,
            onAdd = {
                addPrefill = ""
                showAddSheet = true
            },
            addDescription = stringResource(R.string.vocab_add_phrase),
        )

        if (isEmptyLibrary) {
            // `11.11 · Vocabulary — Empty Library` (`2985:12530`)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s32),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
            ) {
                Spacer(Modifier.height(ZunoSpace.s16))
                Box(
                    modifier = Modifier.size(56.dp).zunoGlassSubtle(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_bookmark, 24.dp, tint = ZunoColors.IconPrimary)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    Text(stringResource(R.string.vocab_empty_title), style = ZunoType.TitleMedium, color = ZunoColors.TextPrimary)
                    Text(
                        text = stringResource(R.string.vocab_empty_body),
                        style = ZunoType.BodySmall,
                        color = ZunoColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    Text(stringResource(R.string.vocab_empty_ways_title), style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                    Column(
                        modifier = Modifier.fillMaxWidth().zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl)),
                    ) {
                        VocabWayRow(
                            icon = R.drawable.zuno_ic_t_sparkles,
                            title = stringResource(R.string.vocab_way_chat_title),
                            subtitle = stringResource(R.string.vocab_way_chat_sub),
                            onClick = onStartChatWithZuno,
                        )
                        ZunoGlassHairline()
                        VocabWayRow(
                            icon = R.drawable.zuno_ic_t_book_open,
                            title = stringResource(R.string.vocab_way_lesson_title),
                            subtitle = stringResource(R.string.vocab_way_lesson_sub),
                            onClick = onOpenTopics,
                        )
                        ZunoGlassHairline()
                        VocabWayRow(
                            icon = R.drawable.zuno_ic_plus,
                            title = stringResource(R.string.vocab_way_manual_title),
                            subtitle = stringResource(R.string.vocab_way_manual_sub),
                            onClick = {
                                addPrefill = ""
                                showAddSheet = true
                            },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s8, bottom = ZunoSpace.s32),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
            ) {
                if (searchOpen) {
                    item(key = "search") {
                        ExploreSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = stringResource(R.string.vocab_search_placeholder),
                            autoFocus = true,
                        )
                    }
                }

                if (q.isEmpty()) {
                    item(key = "srs-hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                                .padding(ZunoSpace.s16),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "SPACED REPETITION · ${languageName.uppercase()} $level",
                                    style = ZunoType.Micro,
                                    color = ZunoColors.TextTertiary,
                                )
                                Text(
                                    text = stringResource(R.string.vocab_saved_count, counts.total),
                                    style = ZunoType.FootnoteStrong,
                                    color = ZunoColors.TextSecondary,
                                )
                            }
                            if (counts.due > 0) {
                                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                                    Text(
                                        text = stringResource(R.string.vocab_ready_recall, counts.due),
                                        style = ZunoType.Headline,
                                        color = ZunoColors.TextPrimary,
                                    )
                                    Text(
                                        text = stringResource(R.string.vocab_hero_sub, activeTutor.displayName),
                                        style = ZunoType.BodySmall,
                                        color = ZunoColors.TextSecondary,
                                    )
                                }
                                ZunoButton(
                                    label = stringResource(R.string.vocab_start_sprint),
                                    onClick = onStartVoiceSprint,
                                    leadingIcon = R.drawable.zuno_ic_t_video,
                                    leadingIconTint = ZunoColors.IconOnInverse,
                                    size = ZunoButtonSize.Large,
                                    modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s4),
                                )
                            } else {
                                // `11.7 · Vocabulary — All Caught Up` (`2746:13274`)
                                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                                    Text(
                                        text = stringResource(R.string.vocab_caught_up_title),
                                        style = ZunoType.Headline,
                                        color = ZunoColors.TextPrimary,
                                    )
                                    Text(
                                        text = stringResource(R.string.vocab_caught_up_sub, activeTutor.displayName),
                                        style = ZunoType.BodySmall,
                                        color = ZunoColors.TextSecondary,
                                    )
                                }
                                ZunoButton(
                                    label = stringResource(R.string.vocab_run_bonus_sprint),
                                    onClick = onStartVoiceSprint,
                                    variant = ZunoButtonVariant.Secondary,
                                    leadingIcon = R.drawable.zuno_ic_t_video,
                                    leadingIconTint = ZunoColors.IconPrimary,
                                    size = ZunoButtonSize.Large,
                                    modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s4),
                                )
                            }
                        }
                    }
                }

                item(key = "filters") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    ) {
                        ZunoChip(
                            label = stringResource(R.string.vocab_filter_due, counts.due),
                            selected = filter == VocabBucket.Due,
                            onClick = { filter = if (filter == VocabBucket.Due) null else VocabBucket.Due },
                        )
                        ZunoChip(
                            label = stringResource(R.string.vocab_filter_learning, counts.learning),
                            selected = filter == VocabBucket.Learning,
                            onClick = { filter = if (filter == VocabBucket.Learning) null else VocabBucket.Learning },
                        )
                        ZunoChip(
                            label = stringResource(R.string.vocab_filter_mastered, counts.mastered),
                            selected = filter == VocabBucket.Mastered,
                            onClick = { filter = if (filter == VocabBucket.Mastered) null else VocabBucket.Mastered },
                        )
                    }
                }

                if (q.isNotEmpty() && visible.isEmpty()) {
                    // `11.12 · Vocabulary — Search & Quick Add` (`2985:12634`)
                    item(key = "search-empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = ZunoSpace.s32, start = ZunoSpace.s12, end = ZunoSpace.s12),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).zunoGlassSubtle(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                ZunoIcon(R.drawable.zuno_ic_t_search, 20.dp, tint = ZunoColors.IconPrimary)
                            }
                            Text(
                                text = stringResource(R.string.vocab_search_no_matches, query.trim()),
                                style = ZunoType.Headline,
                                color = ZunoColors.TextPrimary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.vocab_search_quick_add_body, languageName),
                                style = ZunoType.BodySmall,
                                color = ZunoColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                            ZunoButton(
                                label = stringResource(R.string.vocab_quick_add_btn, query.trim()),
                                onClick = {
                                    addPrefill = query.trim()
                                    showAddSheet = true
                                },
                                leadingIcon = R.drawable.zuno_ic_plus_inverse,
                                size = ZunoButtonSize.Medium,
                            )
                        }
                    }
                } else if (isAllCaughtUp && filter == VocabBucket.Due) {
                    item(key = "zero-due") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s32),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ZunoColors.SurfaceSuccessSubtle)
                                    .border(1.dp, ZunoColors.BorderSuccessSubtle, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                ZunoIcon(R.drawable.zuno_ic_t_check_circle, 20.dp, tint = ZunoColors.IconSuccess)
                            }
                            Text(stringResource(R.string.vocab_zero_due_title), style = ZunoType.Headline, color = ZunoColors.TextPrimary)
                            Text(
                                text = stringResource(R.string.vocab_zero_due_body, counts.total),
                                style = ZunoType.BodySmall,
                                color = ZunoColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    item(key = "phrases-list") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                        ) {
                            visible.forEachIndexed { index, item ->
                                if (index > 0) ZunoGlassHairline()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button, onClick = { selectedPhrase = item })
                                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                                    ) {
                                        Text(
                                            text = item.phrase,
                                            style = ZunoType.BodyMediumStrong,
                                            color = ZunoColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${item.translation} · ${item.source}",
                                            style = ZunoType.FootnoteRegular,
                                            color = ZunoColors.TextTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    if (item.dueNow || item.bucket == VocabBucket.Mastered) {
                                        ZunoStatusPill(
                                            label = item.intervalLabel,
                                            tone = StatusPillTone.Success,
                                        )
                                    } else {
                                        ZunoStatusPill(
                                            label = item.intervalLabel,
                                            tone = StatusPillTone.Neutral,
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable(role = Role.Button, onClick = { onSpeakPhrase(item.phrase) }),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ZunoIcon(R.drawable.zuno_ic_t_volume, 16.dp, tint = ZunoColors.IconSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // `11.13 · Vocabulary — Add Phrase Sheet` (`2985:12721`)
    if (showAddSheet) {
        var newPhrase by rememberSaveable(addPrefill) { mutableStateOf(addPrefill) }
        var newMeaning by rememberSaveable { mutableStateOf("") }
        var newContext by rememberSaveable { mutableStateOf("") }
        var autoFilled by remember { mutableStateOf(false) }

        LaunchedEffect(newPhrase) {
            val trimmed = newPhrase.trim()
            if (trimmed.length >= 3 && (newMeaning.isBlank() || autoFilled)) {
                delay(450)
                val preview = onPreviewPhrase(trimmed, newContext.trim().ifBlank { null })
                if (preview != null && preview.translation.isNotBlank()) {
                    newMeaning = preview.translation
                    autoFilled = true
                }
            }
        }

        ZunoSheet(onDismiss = { showAddSheet = false }, title = stringResource(R.string.vocab_add_phrase)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                ZunoTextField(
                    value = newPhrase,
                    onValueChange = { newPhrase = it },
                    label = stringResource(R.string.vocab_phrase_in_lang, languageName),
                    placeholder = "e.g. sobremesa",
                )
                ZunoTextField(
                    value = newMeaning,
                    onValueChange = {
                        newMeaning = it
                        autoFilled = false
                    },
                    label = stringResource(R.string.vocab_meaning_autofill),
                    placeholder = stringResource(R.string.vocab_meaning_placeholder),
                )
                Text(
                    text = stringResource(R.string.vocab_autofill_helper),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                )
                ZunoTextField(
                    value = newContext,
                    onValueChange = { newContext = it },
                    label = stringResource(R.string.vocab_context_optional),
                    placeholder = stringResource(R.string.vocab_context_placeholder),
                )
                ZunoButton(
                    label = stringResource(R.string.vocab_save_btn),
                    onClick = {
                        val p = newPhrase.trim()
                        if (p.isNotEmpty()) {
                            if (liveLib != null) {
                                onAddPhrase(p, newMeaning.trim().ifBlank { null }, newContext.trim().ifBlank { null })
                            } else {
                                fallbackItems.add(
                                    0,
                                    VocabPhrase(
                                        id = "local-${System.currentTimeMillis()}",
                                        phrase = p,
                                        translation = newMeaning.trim().ifBlank { "Saved phrase" },
                                        source = "Added manually",
                                        contextNote = newContext.trim().ifBlank { null },
                                        dueNow = true,
                                        intervalLabel = "Due now",
                                        bucket = VocabBucket.Due,
                                    ),
                                )
                            }
                            showAddSheet = false
                        }
                    },
                    enabled = newPhrase.isNotBlank(),
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // `11.14 · Vocabulary — Phrase Detail Sheet` (`2985:12783`)
    selectedPhrase?.let { item ->
        ZunoSheet(
            onDismiss = { selectedPhrase = null },
            title = stringResource(R.string.vocab_detail_title),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                    Text(item.phrase, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
                    Text(item.translation, style = ZunoType.BodyMedium, color = ZunoColors.TextSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    ZunoChip(
                        label = "${item.intervalLabel} · ${item.streak} streak",
                        selected = true,
                        onClick = {},
                    )
                    ZunoChip(
                        label = item.source,
                        onClick = {},
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    ZunoChip(
                        label = stringResource(R.string.voice_chip_listen),
                        leadingIcon = R.drawable.zuno_ic_t_volume,
                        onClick = { onSpeakPhrase(item.phrase) },
                    )
                    ZunoChip(
                        label = stringResource(R.string.voice_chip_speed_slow),
                        leadingIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                        onClick = { onSlowSpeakPhrase(item.phrase) },
                    )
                }

                val example = item.exampleSentence ?: item.contextNote
                if (!example.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                            .padding(ZunoSpace.s14),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                    ) {
                        Text(stringResource(R.string.vocab_example_eyebrow), style = ZunoType.Micro, color = ZunoColors.TextTertiary)
                        Text(example, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                        item.exampleTranslation?.takeIf { it.isNotBlank() }?.let { tr ->
                            Text(tr, style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
                        }
                    }
                }

                ZunoButton(
                    label = stringResource(R.string.vocab_practice_with_zuno),
                    onClick = {
                        selectedPhrase = null
                        onPracticePhrase(item)
                    },
                    leadingIcon = R.drawable.zuno_ic_t_video,
                    leadingIconTint = ZunoColors.IconOnInverse,
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    ZunoButton(
                        label = stringResource(R.string.vocab_edit_btn),
                        onClick = {
                            selectedPhrase = null
                            editingPhrase = item
                        },
                        variant = ZunoButtonVariant.Secondary,
                        leadingIcon = R.drawable.zuno_ic_t_pencil,
                        size = ZunoButtonSize.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoButton(
                        label = stringResource(R.string.topics_menu_remove),
                        onClick = {
                            selectedPhrase = null
                            if (liveLib != null) {
                                onDeletePhrase(item.id)
                            } else {
                                fallbackItems.removeAll { it.id == item.id }
                            }
                        },
                        variant = ZunoButtonVariant.Secondary,
                        leadingIcon = R.drawable.zuno_ic_t_trash,
                        leadingIconTint = ZunoColors.IconDanger,
                        size = ZunoButtonSize.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    editingPhrase?.let { item ->
        var editText by rememberSaveable(item.id) { mutableStateOf(item.phrase) }
        var editMeaning by rememberSaveable(item.id) { mutableStateOf(item.translation) }
        var editContext by rememberSaveable(item.id) { mutableStateOf(item.contextNote.orEmpty()) }
        ZunoSheet(onDismiss = { editingPhrase = null }, title = stringResource(R.string.vocab_edit_title)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                ZunoTextField(value = editText, onValueChange = { editText = it }, label = stringResource(R.string.vocab_phrase_in_lang, languageName))
                ZunoTextField(value = editMeaning, onValueChange = { editMeaning = it }, label = stringResource(R.string.vocab_meaning_label))
                ZunoTextField(value = editContext, onValueChange = { editContext = it }, label = stringResource(R.string.vocab_context_optional))
                ZunoButton(
                    label = stringResource(R.string.common_save),
                    onClick = {
                        if (editText.isNotBlank() && editMeaning.isNotBlank()) {
                            if (liveLib != null) {
                                onUpdatePhrase(item.id, editText.trim(), editMeaning.trim(), editContext.trim().ifBlank { null })
                            } else {
                                val idx = fallbackItems.indexOfFirst { it.id == item.id }
                                if (idx >= 0) {
                                    fallbackItems[idx] = item.copy(
                                        phrase = editText.trim(),
                                        translation = editMeaning.trim(),
                                        contextNote = editContext.trim().ifBlank { null },
                                    )
                                }
                            }
                            editingPhrase = null
                        }
                    },
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * `11.2–11.4` & `11.15 · Sprint — No Captions (Avatar Only)` (`7352:15637`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularySprintScreen(
    phrases: List<VocabPhrase>,
    languageName: String,
    tutor: TutorAvatarProfile = LocalActiveTutor.current,
    isSpeaking: Boolean = false,
    onSpeakPhrase: (String) -> Unit,
    onSlowSpeakPhrase: (String) -> Unit,
    onReviewPhrase: (phraseId: String, passed: Boolean) -> Unit,
    onFinishSprint: (List<SprintReviewResult>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck = remember(phrases) { phrases.ifEmpty { DefaultVocabPhrases }.take(5) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var showHint by rememberSaveable(currentIndex) { mutableStateOf(false) }
    var revealed by rememberSaveable(currentIndex) { mutableStateOf(false) }
    var matchedInterval by rememberSaveable(currentIndex) { mutableStateOf<String?>(null) }
    var captionsOn by rememberSaveable { mutableStateOf(true) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var isCameraOn by rememberSaveable { mutableStateOf(false) }
    var isSpeakerOn by rememberSaveable { mutableStateOf(true) }
    val results = remember { mutableStateListOf<SprintReviewResult>() }

    var startMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(startMs) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSec = ((now - startMs) / 1000L).toInt().coerceAtLeast(0)
    val elapsed = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    BackHandler(onBack = onBack)

    val current = deck.getOrNull(currentIndex) ?: deck.first()

    fun nextIntervalFor(passed: Boolean): String = if (!passed) "Tomorrow" else when (current.streak) {
        0 -> "1d"
        1 -> "3d"
        2 -> "7d"
        3 -> "14d"
        else -> "30d"
    }

    fun advanceOrFinish(passed: Boolean) {
        onReviewPhrase(current.id, passed)
        results.add(SprintReviewResult(current, passed, nextIntervalFor(passed)))
        if (currentIndex + 1 < deck.size) {
            currentIndex += 1
        } else {
            onFinishSprint(results.toList())
        }
    }

    fun markMasteredAndShowInterval() {
        matchedInterval = nextIntervalFor(true)
    }

    val sprintPillState = when {
        isSpeaking -> TurnPillState.Speaking
        isMuted -> TurnPillState.Muted
        else -> TurnPillState.Listening
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCallTopBar(
            sessionLabel = stringResource(R.string.call_voice_label),
            elapsed = elapsed,
            showAvatars = captionsOn,
            avatars = listOf(CallAvatarItem(tutor.displayName.take(1), tutor = tutor)),
            onBack = onBack,
            trailing = { Spacer(Modifier.size(44.dp)) },
        )

        if (!captionsOn) {
            // `11.15 · Sprint — No Captions (Avatar Only)` (`7352:15637`)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TutorStageAvatar(
                    tutor = tutor,
                    contentDescription = tutor.displayName,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s20, vertical = ZunoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                ) {
                    results.lastOrNull()?.let { prev ->
                        ZunoUserBubble(
                            message = "“${prev.phrase.phrase}”",
                            senderLabel = "You · Phrase ${results.size} · ${if (prev.passed) "+${prev.nextIntervalLabel}" else "Retry"}",
                            modifier = Modifier.alpha(0.78f),
                        )
                    }

                    val cueText = current.recallCue?.takeIf { it.isNotBlank() } ?: "“${current.translation}”"
                    if (!revealed && matchedInterval == null) {
                        ZunoAssistantBubble(
                            message = cueText,
                            senderLabel = "${tutor.displayName} · Recall cue",
                            onListen = { showHint = true },
                            listenLabel = stringResource(R.string.lesson_bubble_first_word),
                            listenIcon = R.drawable.zuno_ic_sparkles,
                            onExplain = {
                                revealed = true
                                onSpeakPhrase(current.phrase)
                            },
                            explainLabel = stringResource(R.string.lesson_bubble_reveal),
                            explainIcon = R.drawable.zuno_ic_t_book_open,
                        )
                    } else if (matchedInterval != null) {
                        ZunoAssistantBubble(
                            message = cueText,
                            senderLabel = "${tutor.displayName} · Recall cue",
                            onListen = { onSpeakPhrase(current.phrase) },
                            listenLabel = stringResource(R.string.voice_chip_listen),
                            listenIcon = R.drawable.zuno_ic_t_volume,
                            onExplain = { onSlowSpeakPhrase(current.phrase) },
                            explainLabel = stringResource(R.string.voice_chip_speed_slow),
                            explainIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                        )
                        ZunoUserBubble(
                            message = "“${current.phrase}”",
                            senderLabel = "You · 94% match · +$matchedInterval",
                        )
                        ZunoFeedbackPill(
                            text = stringResource(R.string.vocab_sprint_mastered_feedback, matchedInterval!!),
                            fillWidth = true,
                        )
                    } else {
                        ZunoAssistantBubble(
                            message = current.phrase,
                            translation = current.translation,
                            senderLabel = "${tutor.displayName} · Coach",
                            onListen = { onSpeakPhrase(current.phrase) },
                            listenLabel = stringResource(R.string.voice_chip_listen),
                            listenIcon = R.drawable.zuno_ic_t_volume,
                            onExplain = { onSlowSpeakPhrase(current.phrase) },
                            explainLabel = stringResource(R.string.voice_chip_speed_slow),
                            explainIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                        )
                        val stressLabel = current.syllableStress?.takeIf { it.isNotBlank() }
                        if (stressLabel != null) {
                            ZunoFeedbackPill(
                                text = stringResource(R.string.vocab_sprint_retry_feedback, stressLabel),
                                fillWidth = true,
                            )
                        }
                    }

                    if (showHint && !revealed && matchedInterval == null) {
                        val firstWord = current.phrase.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                        ZunoUserBubble(
                            message = "$firstWord …",
                            senderLabel = "First word hint",
                        )
                    }
                }

                ZunoTurnPillRow(
                    state = sprintPillState,
                    speakerName = tutor.displayName,
                    audioLevel = { if (isSpeaking) 0.55f else 0.12f },
                    userAudioLevel = { 0.12f },
                    onClick = if (sprintPillState == TurnPillState.Muted) {
                        { isMuted = !isMuted }
                    } else {
                        null
                    },
                )

                // Bottom Sprint Coach Card (`2733:12889`, `2733:12957`, `2733:13086`)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(
                            RoundedCornerShape(ZunoRadius.xxxl),
                            fillColor = ZunoColors.SurfaceDefault,
                            borderBrush = SolidColor(ZunoColors.BorderDefault),
                        )
                        .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    val headerLeft = if (revealed && matchedInterval == null) {
                        "Shadow · Phrase ${currentIndex + 1} of ${deck.size}"
                    } else {
                        "Recall · Phrase ${currentIndex + 1} of ${deck.size}"
                    }
                    val headerRight = current.syllableStress?.takeIf { revealed && matchedInterval == null && it.isNotBlank() }
                        ?: current.source
                    CoachStageHeader(left = headerLeft, right = headerRight)

                    when {
                        matchedInterval != null -> {
                            ZunoButton(
                                label = stringResource(R.string.vocab_sprint_next_phrase),
                                onClick = { advanceOrFinish(true) },
                                size = ZunoButtonSize.Small,
                                trailingIcon = R.drawable.zuno_ic_t_arrow_right,
                                trailingIconTint = ZunoColors.TextOnInverse,
                            )
                        }
                        revealed -> {
                            val syllables = parseSyllables(current.syllableStress.orEmpty())
                            val words = current.phrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            ) {
                                syllables.forEach { (part, stressed) ->
                                    ZunoCoachTilePill(
                                        text = part,
                                        stressed = stressed,
                                        onClick = { onSpeakPhrase(current.phrase) },
                                    )
                                }
                                if (words.size >= 2) {
                                    ZunoChip(
                                        label = words.take(2).joinToString(" "),
                                        leadingIcon = R.drawable.zuno_ic_t_volume,
                                        onClick = { onSpeakPhrase(words.take(2).joinToString(" ")) },
                                    )
                                }
                                ZunoChip(
                                    label = stringResource(R.string.vocab_sprint_got_it),
                                    leadingIcon = R.drawable.zuno_ic_t_check,
                                    selected = true,
                                    onClick = { advanceOrFinish(true) },
                                )
                                ZunoChip(
                                    label = stringResource(R.string.vocab_sprint_retry),
                                    leadingIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                    onClick = { advanceOrFinish(false) },
                                )
                            }
                        }
                        else -> {
                            val blankLengths = remember(current.id) {
                                current.phrase.trim()
                                    .split(Regex("\\s+"))
                                    .filter { it.isNotEmpty() }
                                    .take(4)
                                    .map { it.length.coerceIn(2, 7) }
                                    .ifEmpty { listOf(5, 4, 6) }
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            ) {
                                blankLengths.forEach { len ->
                                    ZunoBlankCueTile(wordLength = len)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                            ) {
                                ZunoChip(
                                    label = stringResource(R.string.vocab_sprint_got_it),
                                    leadingIcon = R.drawable.zuno_ic_t_check,
                                    selected = true,
                                    onClick = { markMasteredAndShowInterval() },
                                )
                                ZunoChip(
                                    label = stringResource(R.string.vocab_sprint_retry),
                                    leadingIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                    onClick = {
                                        revealed = true
                                        onSpeakPhrase(current.phrase)
                                    },
                                )
                                ZunoChip(
                                    label = stringResource(R.string.voice_chip_listen),
                                    leadingIcon = R.drawable.zuno_ic_t_volume,
                                    onClick = { onSpeakPhrase(current.phrase) },
                                )
                            }
                        }
                    }
                }
            }
        }

        ZunoCallControlsBar(
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            isSpeakerOn = isSpeakerOn,
            isCaptionsOn = captionsOn,
            onToggleMute = { isMuted = !isMuted },
            onToggleCamera = { isCameraOn = !isCameraOn },
            onEndCall = {
                if (results.isNotEmpty()) onFinishSprint(results.toList()) else onBack()
            },
            onToggleSpeaker = { isSpeakerOn = !isSpeakerOn },
            onToggleCaptions = { captionsOn = !captionsOn },
        )
    }
}

/**
 * `11.5 · Sprint complete — Evaluation` (`2733:13196`).
 */
@Composable
fun SprintEvaluationScreen(
    results: List<SprintReviewResult>,
    onSpeakPhrase: (String) -> Unit,
    onPracticeMissed: () -> Unit,
    onBackToVocabulary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = results.size.coerceAtLeast(1)
    val mastered = results.count { it.passed }
    val missed = (total - mastered).coerceAtLeast(0)
    val accuracy = (mastered * 100 / total).coerceIn(0, 100)
    val xp = mastered * 5 + total * 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = stringResource(R.string.vocab_eval_title, total, total),
            onBack = onBackToVocabulary,
            trailing = { Spacer(Modifier.size(44.dp)) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                Text(stringResource(R.string.vocab_eval_eyebrow), style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                Text(stringResource(R.string.vocab_eval_reviewed, total), style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                RoomMetricCard("$accuracy%", stringResource(R.string.vocab_eval_accuracy), Modifier.weight(1f))
                RoomMetricCard("$mastered of $total", stringResource(R.string.vocab_eval_mastered), Modifier.weight(1f))
                RoomMetricCard("+$xp XP", stringResource(R.string.vocab_eval_xp), Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(stringResource(R.string.vocab_eval_phrases_header), style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                Column(modifier = Modifier.fillMaxWidth().zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))) {
                    results.forEachIndexed { idx, res ->
                        if (idx > 0) ZunoGlassHairline()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onSpeakPhrase(res.phrase.phrase) })
                                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (res.passed) {
                                            Modifier
                                                .background(ZunoColors.SurfaceSuccessSubtle)
                                                .border(1.dp, ZunoColors.BorderSuccessSubtle, CircleShape)
                                        } else {
                                            Modifier.background(ZunoColors.SurfaceStrong)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                ZunoIcon(
                                    icon = if (res.passed) R.drawable.zuno_ic_t_check else R.drawable.zuno_ic_rotate_ccw_secondary,
                                    size = 14.dp,
                                    tint = if (res.passed) ZunoColors.IconSuccess else ZunoColors.IconPrimary,
                                )
                            }
                            Text(
                                text = res.phrase.phrase,
                                style = ZunoType.BodyMediumStrong,
                                color = ZunoColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (res.passed) "Mastered · ${res.nextIntervalLabel}" else "Tomorrow",
                                style = ZunoType.FootnoteStrong,
                                color = if (res.passed) ZunoColors.TextSuccess else ZunoColors.TextTertiary,
                            )
                            ZunoIcon(R.drawable.zuno_ic_t_volume, 16.dp, tint = ZunoColors.IconSecondary)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            if (missed > 0) {
                ZunoButton(
                    label = stringResource(R.string.vocab_eval_practice_missed, missed),
                    onClick = onPracticeMissed,
                    size = ZunoButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ZunoButton(
                label = stringResource(R.string.vocab_eval_back_btn),
                onClick = onBackToVocabulary,
                variant = if (missed > 0) ZunoButtonVariant.Ghost else ZunoButtonVariant.Primary,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * `11.9 · Capture — Post-Call / Meeting Phrases` (`2990:12861`).
 */
@Composable
fun PostCallSavePhrasesSheet(
    durationLabel: String,
    candidates: List<Triple<String, String, String>>,
    onSpeakPhrase: (String) -> Unit,
    onSaveSelected: (List<Triple<String, String, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(candidates) {
        mutableStateListOf<Boolean>().apply { repeat(candidates.size) { add(true) } }
    }
    val count = selected.count { it }

    ZunoSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.vocab_capture_title),
        description = stringResource(R.string.vocab_capture_desc, durationLabel, candidates.size),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        ) {
            candidates.forEachIndexed { idx, (phrase, meaning, tag) ->
                val isChecked = selected.getOrElse(idx) { false }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                        .clickable(role = Role.Checkbox) { selected[idx] = !isChecked }
                        .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .then(
                                if (isChecked) Modifier.background(ZunoColors.SurfaceInverse)
                                else Modifier.border(1.5.dp, ZunoColors.BorderStrong, CircleShape),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isChecked) {
                            ZunoIcon(R.drawable.zuno_ic_t_check, 14.dp, tint = ZunoColors.IconOnInverse)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                        Text(phrase, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                        Text("$meaning · $tag", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = { onSpeakPhrase(phrase) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoIcon(R.drawable.zuno_ic_t_volume, 16.dp, tint = ZunoColors.IconSecondary)
                    }
                }
            }

            Spacer(Modifier.height(ZunoSpace.s4))
            ZunoButton(
                label = stringResource(R.string.vocab_capture_save_btn, count),
                onClick = {
                    val picked = candidates.filterIndexed { idx, _ -> selected.getOrElse(idx) { false } }
                    onSaveSelected(picked)
                },
                enabled = count > 0,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoButton(
                label = stringResource(R.string.vocab_capture_skip_btn),
                onClick = onDismiss,
                variant = ZunoButtonVariant.Ghost,
                size = ZunoButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VocabWayRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).zunoGlassSubtle(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 16.dp, tint = ZunoColors.IconPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
        ZunoIcon(R.drawable.zuno_ic_chevron_right, 16.dp, tint = ZunoColors.IconTertiary)
    }
}
