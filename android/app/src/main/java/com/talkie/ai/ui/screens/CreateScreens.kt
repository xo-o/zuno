package com.talkie.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.ui.components.ZunoBackTitleTopBar
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoChip
import com.talkie.ai.ui.components.ZunoChipGroup
import com.talkie.ai.ui.components.ZunoDraftAttachment
import com.talkie.ai.ui.components.ZunoSectionHeader
import com.talkie.ai.ui.components.ZunoSpinner
import com.talkie.ai.ui.components.ZunoTextField
import com.talkie.ai.ui.components.ZunoUploadArea
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import com.talkie.ai.viewmodel.DraftAttachment
import com.talkie.ai.viewmodel.DraftUploadState
import com.talkie.ai.viewmodel.MAX_TOPIC_MATERIAL
import com.talkie.ai.viewmodel.MaterialAnalysis

private val TopicSuggestions = listOf("Ordering food", "Small talk", "Past tense", "Travel phrases", "At the doctor")

private data class ScenarioPreset(val label: String, val userRole: String, val aiRole: String)

private val RoleplayPresets = listOf(
    ScenarioPreset("Job interview", "Candidate", "Interviewer"),
    ScenarioPreset("Doctor’s visit", "Patient", "Doctor"),
    ScenarioPreset("First date", "Date", "Date"),
    ScenarioPreset("Returning an item", "Customer", "Shop assistant"),
)

@Composable
private fun CreateLayout(
    title: String,
    description: String,
    actionLabel: String,
    busyLabel: String,
    canSubmit: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoBackTitleTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
        ) {
            Text(description, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
            content()
        }
        ZunoButton(
            label = if (busy) busyLabel else actionLabel,
            onClick = onSubmit,
            enabled = canSubmit,
            loading = busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s12, bottom = ZunoSpace.s16),
        )
    }
}

/**
 * Topics — New. Gemini turns the request into a 5-lesson path, optionally built from learning
 * material (files, photos, links) whose sub-topics the learner can include or skip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTopicScreen(
    busy: Boolean,
    initialPrompt: String,
    material: List<DraftAttachment>,
    analysis: MaterialAnalysis,
    onBack: () -> Unit,
    onCreate: (prompt: String) -> Unit,
    onAnalyze: (prompt: String) -> Unit,
    onTogglePart: (String) -> Unit,
    onFiles: () -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onAddMaterial: () -> Unit,
    onRemoveMaterial: (DraftAttachment) -> Unit,
    onRetryMaterial: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var prompt by rememberSaveable { mutableStateOf(initialPrompt) }
    val focusManager = LocalFocusManager.current
    val materialReady = material.all { it.state == DraftUploadState.Ready }
    val canSubmit = prompt.trim().length >= 2 && !busy && materialReady
    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onCreate(prompt.trim())
    }

    val readyIds = material.filter { it.state == DraftUploadState.Ready }.mapNotNull { it.serverId }
    LaunchedEffect(readyIds) { onAnalyze(prompt.trim()) }

    CreateLayout(
        title = "New topic",
        description = "Tell Zuno what you want to learn and it will build a short lesson path for you.",
        actionLabel = if (material.isEmpty()) "Create topic" else "Create topic · $LessonsPerTopic lessons",
        busyLabel = "Building your lessons…",
        canSubmit = canSubmit,
        busy = busy,
        onBack = onBack,
        onSubmit = ::submit,
        modifier = modifier,
    ) {
        ZunoTextField(
            value = prompt,
            onValueChange = { prompt = it.take(200) },
            label = "What do you want to learn?",
            placeholder = "e.g. Asking for directions",
            helper = if (material.isEmpty()) "Be specific — a situation or a grammar point works best."
            else "We’ll combine your goal with the material below.",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            if (material.isEmpty()) {
                ZunoSectionHeader(title = "Learning material · optional")
                ZunoUploadArea(onFiles = onFiles, onPhotos = onPhotos, onCamera = onCamera)
            } else {
                ZunoSectionHeader(
                    title = "Learning material · ${material.size}",
                    actionIcon = if (material.size < MAX_TOPIC_MATERIAL) R.drawable.zuno_ic_plus else null,
                    onAction = onAddMaterial,
                    actionDescription = "Add material",
                )
                val (photos, cards) = material.partition { it.isImage && !it.isLink }
                if (photos.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    ) {
                        photos.forEach { item ->
                            key(item.localId) {
                                ZunoDraftAttachment(item, onRemove = { onRemoveMaterial(item) }, onRetry = { onRetryMaterial(item) })
                            }
                        }
                    }
                }
                cards.forEach { item ->
                    key(item.localId) {
                        ZunoDraftAttachment(
                            item,
                            onRemove = { onRemoveMaterial(item) },
                            onRetry = { onRetryMaterial(item) },
                            wide = true,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            val parts = analysis.parts
            when {
                material.isEmpty() -> {
                    ZunoSectionHeader(title = "Suggestions")
                    ZunoChipGroup(TopicSuggestions, onSelect = { if (!busy) prompt = it })
                }
                parts != null -> {
                    ZunoSectionHeader(title = "Found in your material")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    ) {
                        parts.forEach { part ->
                            val selected = part.label in analysis.selected
                            ZunoChip(
                                label = part.label,
                                onClick = { if (!busy) onTogglePart(part.label) },
                                leadingIcon = if (selected) R.drawable.zuno_ic_check_small else null,
                                selected = selected,
                            )
                        }
                    }
                    Text(
                        "Tap to include or skip. Zuno builds $LessonsPerTopic short lessons from the selected parts.",
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                    )
                }
                analysis.loading || !materialReady -> {
                    ZunoSectionHeader(title = "Found in your material")
                    Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8), verticalAlignment = Alignment.CenterVertically) {
                        ZunoSpinner(size = 16.dp)
                        Text("Reading your material…", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }
                }
            }
        }
    }
}

private const val LessonsPerTopic = 5

/** Roleplays — New. Scenario plus optional roles; Gemini fills in the rest. */
@Composable
fun NewRoleplayScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onCreate: (scenario: String, userRole: String, aiRole: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scenario by rememberSaveable { mutableStateOf("") }
    var userRole by rememberSaveable { mutableStateOf("") }
    var aiRole by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val canSubmit = scenario.trim().length >= 2 && !busy
    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onCreate(scenario.trim(), userRole.trim(), aiRole.trim())
    }
    val fieldOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)

    CreateLayout(
        title = "New roleplay",
        description = "Set the scene and Zuno will play along.",
        actionLabel = "Create roleplay",
        busyLabel = "Setting the scene…",
        canSubmit = canSubmit,
        busy = busy,
        onBack = onBack,
        onSubmit = ::submit,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16)) {
            ZunoTextField(
                value = scenario,
                onValueChange = { scenario = it.take(120) },
                label = "Scenario",
                placeholder = "Checking into a hotel",
                enabled = !busy,
                keyboardOptions = fieldOptions,
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoTextField(
                value = userRole,
                onValueChange = { userRole = it.take(40) },
                label = "Your role",
                placeholder = "Guest",
                enabled = !busy,
                keyboardOptions = fieldOptions,
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoTextField(
                value = aiRole,
                onValueChange = { aiRole = it.take(40) },
                label = "Zuno’s role",
                placeholder = "Receptionist",
                enabled = !busy,
                keyboardOptions = fieldOptions.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            ZunoSectionHeader(title = "Or try one of these")
            ZunoChipGroup(RoleplayPresets.map { it.label }, onSelect = { label ->
                if (busy) return@ZunoChipGroup
                RoleplayPresets.first { it.label == label }.let {
                    scenario = it.label
                    userRole = it.userRole
                    aiRole = it.aiRole
                }
            })
        }
    }
}
