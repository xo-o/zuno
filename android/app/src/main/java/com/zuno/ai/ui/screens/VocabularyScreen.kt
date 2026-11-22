package com.zuno.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.components.ZunoTextField
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle

private enum class VocabFilter { Due, Learning, Mastered }

private data class VocabPhraseItem(
    val id: String,
    val phrase: String,
    val translation: String,
    val source: String,
    val dueNow: Boolean,
    val intervalLabel: String,
    val bucket: VocabFilter,
)

private val DefaultVocabPhrases = listOf(
    VocabPhraseItem("v1", "pedir la cuenta", "to ask for the bill", "Café roleplay", true, "Due now", VocabFilter.Due),
    VocabPhraseItem("v2", "hacer escala", "to have a layover", "Airport check-in", true, "Due now", VocabFilter.Due),
    VocabPhraseItem("v3", "tomo la línea cinco", "I take line five", "Lesson 3", true, "Due now", VocabFilter.Due),
    VocabPhraseItem("v4", "para llevar", "to go / takeaway", "Pedir un café", false, "7d", VocabFilter.Learning),
    VocabPhraseItem("v5", "¿pasa por la estación?", "does it stop by the station?", "Lesson 3", false, "14d", VocabFilter.Learning),
    VocabPhraseItem("v6", "encantado de conocerte", "lovely to meet you", "Free Talk", false, "30d", VocabFilter.Mastered),
)

/**
 * `12 · Vocabulary` (`2462:10079` 11.1 · Vocabulary — SRS Library).
 */
@Composable
fun VocabularyScreen(
    languageName: String,
    level: String,
    onBack: () -> Unit,
    onStartVoiceSprint: () -> Unit,
    onSpeakPhrase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<VocabFilter?>(null) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val items = remember { mutableStateListOf<VocabPhraseItem>().apply { addAll(DefaultVocabPhrases) } }

    val q = query.trim().lowercase()
    val visible = items.filter { item ->
        val matchesBucket = filter == null || item.bucket == filter
        val matchesQuery = q.isEmpty() || item.phrase.lowercase().contains(q) || item.translation.lowercase().contains(q)
        matchesBucket && matchesQuery
    }
    val dueCount = items.count { it.dueNow }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoSectionTopBar(
            title = "Vocabulary",
            onBack = onBack,
            showSearch = true,
            searchActive = searchOpen,
            onSearchToggle = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
            showAdd = true,
            onAdd = { showAddSheet = true },
            addDescription = "Add phrase",
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s32),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            if (searchOpen) {
                item(key = "search") {
                    ExploreSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search saved phrases…",
                        autoFocus = true,
                    )
                }
            }

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
                            text = "${142 + items.size} saved",
                            style = ZunoType.FootnoteStrong,
                            color = ZunoColors.TextSecondary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                        Text(
                            text = "${dueCount.coerceAtLeast(1)} phrases ready for active recall",
                            style = ZunoType.Headline,
                            color = ZunoColors.TextPrimary,
                        )
                        Text(
                            text = "Hear cues in context and speak back with Sofía.",
                            style = ZunoType.BodySmall,
                            color = ZunoColors.TextSecondary,
                        )
                    }
                    ZunoButton(
                        label = "Start 2-min Voice Sprint",
                        onClick = onStartVoiceSprint,
                        leadingIcon = R.drawable.zuno_ic_t_video,
                        leadingIconTint = ZunoColors.IconOnInverse,
                        size = ZunoButtonSize.Large,
                        modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s4),
                    )
                }
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    ZunoChip(
                        label = "Due now · ${dueCount + 2}",
                        selected = filter == VocabFilter.Due,
                        onClick = { filter = if (filter == VocabFilter.Due) null else VocabFilter.Due },
                    )
                    ZunoChip(
                        label = "Learning · 34",
                        selected = filter == VocabFilter.Learning,
                        onClick = { filter = if (filter == VocabFilter.Learning) null else VocabFilter.Learning },
                    )
                    ZunoChip(
                        label = "Mastered · 109",
                        selected = filter == VocabFilter.Mastered,
                        onClick = { filter = if (filter == VocabFilter.Mastered) null else VocabFilter.Mastered },
                    )
                }
            }

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
                                .clickable(role = Role.Button, onClick = { onSpeakPhrase(item.phrase) })
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

                            if (item.dueNow) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ZunoColors.SurfaceInverse)
                                        .padding(horizontal = ZunoSpace.s10, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = item.intervalLabel,
                                        style = ZunoType.FootnoteStrong,
                                        color = ZunoColors.TextOnInverse,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .zunoGlassElevated(CircleShape)
                                        .padding(horizontal = ZunoSpace.s10, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = item.intervalLabel,
                                        style = ZunoType.FootnoteStrong,
                                        color = ZunoColors.TextSecondary,
                                    )
                                }
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

    if (showAddSheet) {
        var newPhrase by rememberSaveable { mutableStateOf("") }
        var newMeaning by rememberSaveable { mutableStateOf("") }
        ZunoSheet(onDismiss = { showAddSheet = false }, title = "Add phrase") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s12),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                ZunoTextField(
                    value = newPhrase,
                    onValueChange = { newPhrase = it },
                    label = "Phrase ($languageName)",
                    placeholder = "e.g. ¿Me puedes ayudar?",
                )
                ZunoTextField(
                    value = newMeaning,
                    onValueChange = { newMeaning = it },
                    label = "Meaning",
                    placeholder = "e.g. Can you help me?",
                )
                ZunoButton(
                    label = "Save to Vocabulary",
                    onClick = {
                        val p = newPhrase.trim()
                        if (p.isNotEmpty()) {
                            items.add(
                                0,
                                VocabPhraseItem(
                                    id = "local-${System.currentTimeMillis()}",
                                    phrase = p,
                                    translation = newMeaning.trim().ifBlank { "Saved phrase" },
                                    source = "Added manually",
                                    dueNow = true,
                                    intervalLabel = "Due now",
                                    bucket = VocabFilter.Due,
                                ),
                            )
                            showAddSheet = false
                        }
                    },
                    enabled = newPhrase.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
