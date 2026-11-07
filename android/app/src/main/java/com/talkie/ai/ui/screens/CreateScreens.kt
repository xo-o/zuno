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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.talkie.ai.ui.components.ZunoBackTitleTopBar
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoChipGroup
import com.talkie.ai.ui.components.ZunoSectionHeader
import com.talkie.ai.ui.components.ZunoTextField
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

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

/** Topics — New. Gemini turns the request into a 5-lesson path. */
@Composable
fun NewTopicScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onCreate: (prompt: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val canSubmit = prompt.trim().length >= 2 && !busy
    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        onCreate(prompt.trim())
    }

    CreateLayout(
        title = "New topic",
        description = "Tell Zuno what you want to learn and it will build a short lesson path for you.",
        actionLabel = "Create topic",
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
            helper = "Be specific — a situation or a grammar point works best.",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            ZunoSectionHeader(title = "Suggestions")
            ZunoChipGroup(TopicSuggestions, onSelect = { if (!busy) prompt = it })
        }
    }
}

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
