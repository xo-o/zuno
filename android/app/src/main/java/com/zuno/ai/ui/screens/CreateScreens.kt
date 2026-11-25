package com.zuno.ai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zuno.ai.R
import com.zuno.ai.avatar.catalog.LocalActiveTutor
import com.zuno.ai.data.Roleplay
import com.zuno.ai.data.RoleplayDraft
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicDraft
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoChoiceGroup
import com.zuno.ai.ui.components.ZunoChoiceRow
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoComposer
import com.zuno.ai.ui.components.ZunoDraftAttachment
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoIconButtonStyle
import com.zuno.ai.ui.components.ZunoInputDialog
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.components.ZunoTypingBubble
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.theme.Lexend
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.viewmodel.DraftAttachment
import com.zuno.ai.viewmodel.DraftUploadState
import com.zuno.ai.viewmodel.RoleplayBuilderState
import com.zuno.ai.viewmodel.TopicBuilderState

// ── 10a → 10b · Interactive Topic Chat Builder (`3007:13787`, `3005:13754`) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTopicScreen(
    builder: TopicBuilderState,
    firstName: String?,
    busy: Boolean,
    attachments: List<DraftAttachment>,
    onBack: () -> Unit,
    onSendTurn: (String) -> Unit,
    onAcceptDraft: (TopicDraft) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onRetryAttachment: (DraftAttachment) -> Unit,
    nativeLanguageBcp47: String = "en-US",
    onRequestMicPermission: ((onGranted: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var dictating by remember { mutableStateOf(false) }
    var showDraftDetails by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val composerFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    fun startDictation() {
        val recognizer = speechRecognizer ?: run {
            composerFocus.requestFocus()
            return
        }
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            onRequestMicPermission?.invoke { startDictation() } ?: composerFocus.requestFocus()
            return
        }
        if (dictating) {
            dictating = false
            runCatching { recognizer.stopListening() }
            return
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { dictating = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { dictating = false }
            override fun onError(error: Int) { dictating = false }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!partial.isNullOrBlank()) input = partial.take(500)
            }
            override fun onResults(results: Bundle?) {
                dictating = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!spoken.isNullOrBlank()) {
                    input = spoken.take(500)
                    composerFocus.requestFocus()
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, nativeLanguageBcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { recognizer.startListening(intent) }.onFailure {
            dictating = false
            composerFocus.requestFocus()
        }
    }

    val attachmentsReady = attachments.all { it.state == DraftUploadState.Ready }
    val canSend = !builder.sending && !busy && attachmentsReady && (input.isNotBlank() || attachments.isNotEmpty())

    fun sendCurrent() {
        if (!canSend) return
        val toSend = input.trim()
        input = ""
        onSendTurn(toSend)
    }

    val totalItems = 1 + builder.turns.size + (if (builder.sending) 1 else 0) + (if (builder.draft != null) 1 else 0)
    LaunchedEffect(totalItems, builder.draft) {
        if (totalItems > 1) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val greeting = if (!firstName.isNullOrBlank()) {
        stringResource(R.string.topics_builder_greeting_named, firstName)
    } else {
        stringResource(R.string.topics_builder_greeting)
    }
    val senderLabel = stringResource(R.string.topics_builder_sender)
    val starterChips = listOf(
        stringResource(R.string.topics_chip_trip),
        stringResource(R.string.topics_chip_interview),
        stringResource(R.string.topics_chip_cafe),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = stringResource(R.string.topics_builder_title),
            onBack = onBack,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            item(key = "greeting") {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                    ZunoAssistantBubble(
                        message = greeting,
                        senderLabel = senderLabel,
                    )
                    if (builder.turns.isEmpty() && !builder.sending) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        ) {
                            starterChips.forEach { chip ->
                                ZunoChip(
                                    label = chip,
                                    onClick = { onSendTurn(chip) },
                                )
                            }
                        }
                    }
                }
            }

            items(builder.turns, key = { it.id }) { turn ->
                if (turn.isUser) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        turn.attachmentNames.forEach { fileName ->
                            BuilderSentAttachmentPill(fileName)
                        }
                        ZunoUserBubble(
                            message = turn.text,
                            showSender = turn.attachmentNames.isEmpty(),
                        )
                    }
                } else {
                    ZunoAssistantBubble(
                        message = turn.text,
                        senderLabel = senderLabel,
                        contextNote = turn.contextNote,
                    )
                }
            }

            if (builder.sending) {
                item(key = "typing") {
                    ZunoTypingBubble()
                }
            }

            val draft = builder.draft
            if (draft != null && !builder.sending) {
                item(key = "draft-card") {
                    DraftTopicCard(
                        draft = draft,
                        busy = busy,
                        onDetails = { showDraftDetails = true },
                        onCreate = {
                            focusManager.clearFocus()
                            onAcceptDraft(draft)
                        },
                    )
                }
            }
        }

        val placeholder = when {
            dictating -> stringResource(R.string.voice_listening)
            builder.draft != null -> stringResource(R.string.topics_builder_refine_placeholder)
            else -> stringResource(R.string.topics_builder_placeholder)
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8)) {
            ZunoComposer(
                value = input,
                onValueChange = { input = it.take(500) },
                onSend = ::sendCurrent,
                onAttach = onAttach,
                onVoice = ::startDictation,
                placeholder = placeholder,
                focusRequester = composerFocus,
                enabled = !builder.sending && !busy,
                sendEnabled = canSend,
                attachments = if (attachments.isNotEmpty()) {
                    {
                        attachments.forEach { item ->
                            key(item.localId) {
                                ZunoDraftAttachment(
                                    draft = item,
                                    onRemove = { onRemoveAttachment(item) },
                                    onRetry = { onRetryAttachment(item) },
                                )
                            }
                        }
                    }
                } else {
                    null
                },
            )
        }
    }

    val activeDraft = builder.draft
    if (showDraftDetails && activeDraft != null) {
        TopicDetailsSheet(
            draft = activeDraft,
            busy = busy,
            onDismiss = { showDraftDetails = false },
            onCreate = {
                showDraftDetails = false
                focusManager.clearFocus()
                onAcceptDraft(activeDraft)
            },
        )
    }
}

@Composable
private fun DraftTopicCard(
    draft: TopicDraft,
    busy: Boolean,
    onDetails: () -> Unit,
    onCreate: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    val previewNames = draft.lessons.take(3).joinToString(" · ") { it.title }
    val overflow = (draft.lessons.size - 3).coerceAtLeast(0)
    val summaryLine = if (overflow > 0) "$previewNames (+$overflow)" else previewNames

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.topics_draft_eyebrow, draft.lessons.size),
                style = ZunoType.Micro,
                color = ZunoColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            ZunoChip(
                label = topicCategoryLabel(draft.category),
                onClick = {},
            )
        }
        Text(
            text = draft.title,
            style = ZunoType.BodyMediumStrong,
            color = ZunoColors.TextPrimary,
        )
        Text(
            text = summaryLine,
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextSecondary,
        )
        Spacer(Modifier.height(ZunoSpace.s2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            ZunoButton(
                label = stringResource(R.string.draft_details_action),
                onClick = onDetails,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_t_info,
                leadingIconTint = ZunoColors.IconPrimary,
                enabled = !busy,
            )
            ZunoButton(
                label = stringResource(R.string.topics_draft_create),
                onClick = onCreate,
                size = ZunoButtonSize.Medium,
                loading = busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TopicDetailsSheet(
    draft: TopicDraft,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    ZunoSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.topic_details_title),
        spacing = ZunoSpace.s14,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = draft.title,
                        style = ZunoType.Headline.copy(
                            fontFamily = Lexend,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                        ),
                        color = ZunoColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoChip(
                        label = topicCategoryLabel(draft.category),
                        onClick = {},
                    )
                }
                Text(
                    text = draft.translation.ifBlank { draft.prompt },
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(
                    text = stringResource(R.string.topic_details_contents, draft.lessons.size),
                    style = ZunoType.Micro,
                    color = ZunoColors.TextTertiary,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl), fillColor = ZunoColors.SurfaceDefault)
                        .verticalScroll(rememberScrollState()),
                ) {
                    draft.lessons.forEachIndexed { index, lesson ->
                        if (index > 0) ZunoGlassHairline()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ZunoSpace.s14, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = ZunoType.FootnoteStrong,
                                color = ZunoColors.TextTertiary,
                            )
                            Text(
                                text = lesson.title,
                                style = ZunoType.BodySmall,
                                color = ZunoColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.topics_lesson_est_min),
                                style = ZunoType.FootnoteRegular,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                    }
                }
            }

            ZunoButton(
                label = stringResource(R.string.topics_draft_create),
                onClick = onCreate,
                size = ZunoButtonSize.Large,
                loading = busy,
                modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s4),
            )
        }
    }
}

// ── 10c · Topic Settings (`3007:13865`) ─────────────────────────────────────

private val TopicCategories = listOf("travel", "food", "work", "daily_life", "grammar", "culture")

@Composable
fun TopicSettingsScreen(
    topic: TopicDetail?,
    languageName: String,
    level: String,
    busy: Boolean,
    onBack: () -> Unit,
    onSelectPublic: (Boolean) -> Unit,
    onShare: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onPrimaryAction: (TopicDetail) -> Unit = {},
    onUpdateTitle: (String) -> Unit = {},
    onUpdateCategory: (String) -> Unit = {},
    onOpenLearning: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editingTitle by rememberSaveable { mutableStateOf(false) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(R.string.topics_settings_title),
            onBack = onBack,
        )

        if (topic == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.zuno.ai.ui.components.ZunoLoadState(error = null, onRetry = {})
            }
            return@Column
        }

        val summary = topic.summary
        val isPublic = summary.isPublic
        val shareUrl = summary.shareUrl
        val displayUrl = shareUrl?.removePrefix("https://")?.removePrefix("http://")
            ?: "heyzuno.app/t/${summary.id.take(8)}"
        val estMinutes = (summary.totalLessons * 5).coerceAtLeast(5)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s32),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            // Header Card (`7251:12836`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl), fillColor = ZunoColors.SurfaceDefault)
                    .padding(ZunoSpace.s14),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .zunoGlassElevated(
                            shape = RoundedCornerShape(ZunoRadius.lg),
                            fillColor = ZunoColors.SurfaceStrong,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_t_book_open, 20.dp, tint = ZunoColors.IconPrimary)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.topics_settings_created_eyebrow, summary.totalLessons),
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        VisibilityStatusTag(isPublic = isPublic)
                    }
                    Text(
                        text = summary.title,
                        style = ZunoType.BodyMediumStrong,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$languageName $level · ${topicCategoryLabel(summary.category)} · ~$estMinutes min",
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Details Section (`7251:12848`)
            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_details_section)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_details_title_label),
                    icon = R.drawable.zuno_ic_t_pencil,
                    value = summary.title,
                    showChevron = true,
                    onClick = { if (!busy) editingTitle = true },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_details_category_label),
                    icon = R.drawable.zuno_ic_layout_grid,
                    value = topicCategoryLabel(summary.category),
                    showChevron = true,
                    showDivider = true,
                    onClick = { if (!busy) pickingCategory = true },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_details_level_label),
                    icon = R.drawable.zuno_ic_languages,
                    value = "$languageName · $level",
                    showChevron = true,
                    showDivider = true,
                    onClick = onOpenLearning,
                )
            }

            // Visibility Section (`7251:12881`)
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                Text(
                    text = stringResource(R.string.settings_visibility_section),
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextSecondary,
                )
                ZunoChoiceGroup {
                    ZunoChoiceRow(
                        icon = R.drawable.zuno_ic_t_lock,
                        title = stringResource(R.string.share_only_me),
                        description = stringResource(R.string.topics_settings_only_me_desc),
                        selected = !isPublic,
                        onClick = { if (isPublic && !busy) onSelectPublic(false) },
                    )
                    ZunoChoiceRow(
                        icon = R.drawable.zuno_ic_t_globe,
                        title = stringResource(R.string.topics_settings_public_title),
                        description = stringResource(R.string.topics_settings_public_desc),
                        selected = isPublic,
                        onClick = { if (!isPublic && !busy) onSelectPublic(true) },
                        showDivider = true,
                    )
                }
            }

            // Sharing Section (`7251:12906`)
            val shareReady = isPublic && shareUrl != null && !busy
            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_sharing_section)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(start = ZunoSpace.s16, end = ZunoSpace.s12),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_t_link, 18.dp, tint = ZunoColors.IconSecondary)
                    Text(
                        text = displayUrl,
                        style = ZunoType.BodySmall,
                        color = if (isPublic) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoIconButton(
                        icon = R.drawable.zuno_ic_t_copy,
                        onClick = { if (shareReady) shareUrl?.let(onCopyLink) },
                        style = ZunoIconButtonStyle.Subtle,
                        size = ZunoIconButtonSize.Small,
                        tint = if (shareReady) ZunoColors.IconPrimary else ZunoColors.IconTertiary,
                        contentDescription = stringResource(R.string.share_copy_link),
                    )
                }
                ZunoSettingsRow(
                    title = stringResource(R.string.topics_settings_share_row),
                    icon = R.drawable.zuno_ic_t_share,
                    showChevron = true,
                    showDivider = true,
                    enabled = shareReady,
                    onClick = { shareUrl?.let(onShare) },
                )
            }

            // Delete Topic (`7251:12927`)
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.topics_settings_delete_row),
                    icon = R.drawable.zuno_ic_t_trash,
                    destructive = true,
                    showChevron = true,
                    enabled = !busy,
                    onClick = onDelete,
                )
            }
        }
    }

    if (editingTitle && topic != null) {
        ZunoInputDialog(
            title = stringResource(R.string.settings_details_title_label),
            initialValue = topic.summary.title,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { newTitle ->
                editingTitle = false
                if (newTitle.isNotBlank() && newTitle != topic.summary.title) {
                    onUpdateTitle(newTitle)
                }
            },
            onDismiss = { editingTitle = false },
        )
    }

    if (pickingCategory && topic != null) {
        ZunoOptionSheet(
            title = stringResource(R.string.settings_details_category_label),
            options = TopicCategories,
            selected = topic.summary.category,
            label = { topicCategoryLabel(it) },
            onSelect = { chosen ->
                pickingCategory = false
                if (chosen != topic.summary.category) onUpdateCategory(chosen)
            },
            onDismiss = { pickingCategory = false },
        )
    }
}

// ── Roleplays · 3a → 3b · Interactive Roleplay Chat Builder ─────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewRoleplayScreen(
    builder: RoleplayBuilderState,
    firstName: String?,
    busy: Boolean,
    attachments: List<DraftAttachment>,
    onBack: () -> Unit,
    onSendTurn: (String) -> Unit,
    onAcceptDraft: (RoleplayDraft) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onRetryAttachment: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showDraftDetails by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val composerFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val attachmentsReady = attachments.all { it.state == DraftUploadState.Ready }
    val canSend = !builder.sending && !busy && attachmentsReady && (input.isNotBlank() || attachments.isNotEmpty())

    fun sendCurrent() {
        if (!canSend) return
        val toSend = input.trim()
        input = ""
        onSendTurn(toSend)
    }

    val totalItems = 1 + builder.turns.size + (if (builder.sending) 1 else 0) + (if (builder.draft != null) 1 else 0)
    LaunchedEffect(totalItems, builder.draft) {
        if (totalItems > 1) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val greeting = if (!firstName.isNullOrBlank()) {
        stringResource(R.string.roleplays_builder_greeting_named, firstName)
    } else {
        stringResource(R.string.roleplays_builder_greeting)
    }
    val senderLabel = stringResource(R.string.roleplays_builder_sender)
    val starterChips = listOf(
        stringResource(R.string.roleplays_chip_interview),
        stringResource(R.string.roleplays_chip_flat),
        stringResource(R.string.roleplays_chip_doctor),
        stringResource(R.string.roleplays_chip_cafe),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = stringResource(R.string.roleplays_builder_title),
            onBack = onBack,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            item(key = "greeting") {
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                    ZunoAssistantBubble(
                        message = greeting,
                        senderLabel = senderLabel,
                    )
                    if (builder.turns.isEmpty() && !builder.sending) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        ) {
                            starterChips.forEach { chip ->
                                ZunoChip(
                                    label = chip,
                                    onClick = { onSendTurn(chip) },
                                )
                            }
                        }
                    }
                }
            }

            items(builder.turns, key = { it.id }) { turn ->
                if (turn.isUser) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    ) {
                        turn.attachmentNames.forEach { fileName ->
                            BuilderSentAttachmentPill(fileName)
                        }
                        ZunoUserBubble(
                            message = turn.text,
                            showSender = turn.attachmentNames.isEmpty(),
                        )
                    }
                } else {
                    ZunoAssistantBubble(
                        message = turn.text,
                        senderLabel = senderLabel,
                        contextNote = turn.contextNote,
                    )
                }
            }

            if (builder.sending) {
                item(key = "typing") {
                    ZunoTypingBubble()
                }
            }

            val draft = builder.draft
            if (draft != null && !builder.sending) {
                item(key = "draft-card") {
                    DraftRoleplayCard(
                        draft = draft,
                        busy = busy,
                        onDetails = { showDraftDetails = true },
                        onCreate = {
                            focusManager.clearFocus()
                            onAcceptDraft(draft)
                        },
                    )
                }
            }
        }

        val placeholder = if (builder.draft != null) {
            stringResource(R.string.roleplays_builder_refine_placeholder)
        } else {
            stringResource(R.string.roleplays_builder_placeholder)
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8)) {
            ZunoComposer(
                value = input,
                onValueChange = { input = it.take(500) },
                onSend = ::sendCurrent,
                onAttach = onAttach,
                onVoice = { composerFocus.requestFocus() },
                placeholder = placeholder,
                focusRequester = composerFocus,
                enabled = !builder.sending && !busy,
                sendEnabled = canSend,
                attachments = if (attachments.isNotEmpty()) {
                    {
                        attachments.forEach { item ->
                            key(item.localId) {
                                ZunoDraftAttachment(
                                    draft = item,
                                    onRemove = { onRemoveAttachment(item) },
                                    onRetry = { onRetryAttachment(item) },
                                )
                            }
                        }
                    }
                } else {
                    null
                },
            )
        }
    }

    val activeDraft = builder.draft
    if (showDraftDetails && activeDraft != null) {
        RoleplayDetailsSheet(
            draft = activeDraft,
            busy = busy,
            onDismiss = { showDraftDetails = false },
            onCreate = {
                showDraftDetails = false
                focusManager.clearFocus()
                onAcceptDraft(activeDraft)
            },
        )
    }
}

@Composable
private fun DraftRoleplayCard(
    draft: RoleplayDraft,
    busy: Boolean,
    onDetails: () -> Unit,
    onCreate: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.roleplays_draft_eyebrow),
                style = ZunoType.Micro,
                color = ZunoColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            ZunoChip(
                label = roleplayCategoryLabel(draft.category),
                onClick = {},
            )
        }
        Text(
            text = draft.title,
            style = ZunoType.BodyMediumStrong,
            color = ZunoColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.roleplays_draft_roles_line, draft.userRole, draft.aiRole),
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextSecondary,
        )
        Spacer(Modifier.height(ZunoSpace.s2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            ZunoButton(
                label = stringResource(R.string.draft_details_action),
                onClick = onDetails,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_t_info,
                leadingIconTint = ZunoColors.IconPrimary,
                enabled = !busy,
            )
            ZunoButton(
                label = stringResource(R.string.roleplays_draft_create),
                onClick = onCreate,
                size = ZunoButtonSize.Medium,
                loading = busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoleplayDetailsSheet(
    draft: RoleplayDraft,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val tutorName = LocalActiveTutor.current.displayName
    ZunoSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.roleplay_details_title),
        spacing = ZunoSpace.s14,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = draft.title,
                    style = ZunoType.Headline.copy(
                        fontFamily = Lexend,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                    color = ZunoColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                ZunoChip(
                    label = roleplayCategoryLabel(draft.category),
                    onClick = {},
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl), fillColor = ZunoColors.SurfaceDefault),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.roleplays_preview_your_role),
                        style = ZunoType.BodySmall,
                        color = ZunoColors.TextSecondary,
                    )
                    Text(
                        text = draft.userRole,
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextPrimary,
                    )
                }
                ZunoGlassHairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ZunoAI’s role",
                        style = ZunoType.BodySmall,
                        color = ZunoColors.TextSecondary,
                    )
                    Text(
                        text = "${draft.aiRole} ($tutorName)",
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl), fillColor = ZunoColors.SurfaceSubtle)
                    .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            ) {
                Text(
                    text = stringResource(R.string.roleplay_details_context),
                    style = ZunoType.Micro,
                    color = ZunoColors.TextTertiary,
                )
                Text(
                    text = draft.setting.ifBlank { draft.scenario },
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextPrimary,
                )
            }

            ZunoButton(
                label = stringResource(R.string.roleplays_draft_create),
                onClick = onCreate,
                size = ZunoButtonSize.Large,
                loading = busy,
                modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s4),
            )
        }
    }
}

// ── Roleplays · 17 · Roleplay Settings (`7249:12833`) ───────────────────────

@Composable
fun RoleplaySettingsScreen(
    roleplay: Roleplay?,
    languageName: String,
    level: String,
    busy: Boolean,
    onBack: () -> Unit,
    onSelectPublic: (Boolean) -> Unit,
    onShare: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onStartLive: (Roleplay) -> Unit = {},
    onUpdateTitle: (String) -> Unit = {},
    onUpdateUserRole: (String) -> Unit = {},
    onUpdateAiRole: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tutorName = LocalActiveTutor.current.displayName
    var editField by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(R.string.roleplays_settings_title),
            onBack = onBack,
        )

        if (roleplay == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.zuno.ai.ui.components.ZunoLoadState(error = null, onRetry = {})
            }
            return@Column
        }

        val isPublic = roleplay.isPublic
        val shareUrl = roleplay.shareUrl
        val displayUrl = shareUrl?.removePrefix("https://")?.removePrefix("http://")
            ?: "heyzuno.app/r/${roleplay.id.take(8)}"

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s32),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
        ) {
            // Header Card (`7249:12852`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl), fillColor = ZunoColors.SurfaceDefault)
                    .padding(ZunoSpace.s14),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .zunoGlassElevated(
                            shape = RoundedCornerShape(ZunoRadius.lg),
                            fillColor = ZunoColors.SurfaceStrong,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_messages_square, 20.dp, tint = ZunoColors.IconPrimary)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ROLEPLAY · ${roleplayCategoryLabel(roleplay.category).uppercase()}",
                            style = ZunoType.Micro,
                            color = ZunoColors.TextTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        VisibilityStatusTag(isPublic = isPublic)
                    }
                    Text(
                        text = roleplay.title,
                        style = ZunoType.BodyMediumStrong,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "You: ${roleplay.userRole} · $tutorName: ${roleplay.aiRole}",
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Details Section (`7249:12863`)
            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_details_section)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_details_title_label),
                    icon = R.drawable.zuno_ic_t_pencil,
                    value = roleplay.title,
                    showChevron = true,
                    onClick = { if (!busy) editField = "title" },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_details_your_role_label),
                    icon = R.drawable.zuno_ic_user_circle,
                    value = roleplay.userRole,
                    showChevron = true,
                    showDivider = true,
                    onClick = { if (!busy) editField = "userRole" },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_details_tutor_role_label, tutorName),
                    icon = R.drawable.zuno_ic_t_sparkles,
                    value = roleplay.aiRole,
                    showChevron = true,
                    showDivider = true,
                    onClick = { if (!busy) editField = "aiRole" },
                )
            }

            // Visibility Section (`7249:12896`)
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                Text(
                    text = stringResource(R.string.settings_visibility_section),
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextSecondary,
                )
                ZunoChoiceGroup {
                    ZunoChoiceRow(
                        icon = R.drawable.zuno_ic_t_lock,
                        title = stringResource(R.string.share_only_me),
                        description = stringResource(R.string.topics_settings_only_me_desc),
                        selected = !isPublic,
                        onClick = { if (isPublic && !busy) onSelectPublic(false) },
                    )
                    ZunoChoiceRow(
                        icon = R.drawable.zuno_ic_t_globe,
                        title = stringResource(R.string.topics_settings_public_title),
                        description = stringResource(R.string.topics_settings_public_desc),
                        selected = isPublic,
                        onClick = { if (!isPublic && !busy) onSelectPublic(true) },
                        showDivider = true,
                    )
                }
            }

            // Sharing Section (`7249:12920`)
            val shareReady = isPublic && shareUrl != null && !busy
            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_sharing_section)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(start = ZunoSpace.s16, end = ZunoSpace.s12),
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_t_link, 18.dp, tint = ZunoColors.IconSecondary)
                    Text(
                        text = displayUrl,
                        style = ZunoType.BodySmall,
                        color = if (isPublic) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoIconButton(
                        icon = R.drawable.zuno_ic_t_copy,
                        onClick = { if (shareReady) shareUrl?.let(onCopyLink) },
                        style = ZunoIconButtonStyle.Subtle,
                        size = ZunoIconButtonSize.Small,
                        tint = if (shareReady) ZunoColors.IconPrimary else ZunoColors.IconTertiary,
                        contentDescription = stringResource(R.string.share_copy_link),
                    )
                }
                ZunoSettingsRow(
                    title = stringResource(R.string.roleplays_settings_share_row),
                    icon = R.drawable.zuno_ic_t_share,
                    showChevron = true,
                    showDivider = true,
                    enabled = shareReady,
                    onClick = { shareUrl?.let(onShare) },
                )
            }

            // Delete Roleplay (`7249:12941`)
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.roleplays_settings_delete_row),
                    icon = R.drawable.zuno_ic_t_trash,
                    destructive = true,
                    showChevron = true,
                    enabled = !busy,
                    onClick = onDelete,
                )
            }
        }
    }

    if (editField != null && roleplay != null) {
        val (dialogTitle, initialVal) = when (editField) {
            "userRole" -> stringResource(R.string.settings_details_your_role_label) to roleplay.userRole
            "aiRole" -> stringResource(R.string.settings_details_tutor_role_label, tutorName) to roleplay.aiRole
            else -> stringResource(R.string.settings_details_title_label) to roleplay.title
        }
        ZunoInputDialog(
            title = dialogTitle,
            initialValue = initialVal,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { value ->
                val target = editField
                editField = null
                if (value.isNotBlank() && value != initialVal) {
                    when (target) {
                        "userRole" -> onUpdateUserRole(value)
                        "aiRole" -> onUpdateAiRole(value)
                        "title" -> onUpdateTitle(value)
                    }
                }
            },
            onDismiss = { editField = null },
        )
    }
}

@Composable
private fun VisibilityStatusTag(isPublic: Boolean) {
    Row(
        modifier = Modifier
            .zunoGlassSubtle(CircleShape, fillColor = ZunoColors.SurfaceSubtle)
            .padding(start = ZunoSpace.s8, end = 9.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isPublic) ZunoColors.StatusSpeaking else ZunoColors.IconTertiary),
        )
        Text(
            text = stringResource(if (isPublic) R.string.topics_public_badge else R.string.topics_private_badge),
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BuilderSentAttachmentPill(fileName: String) {
    val shape = RoundedCornerShape(
        topStart = ZunoRadius.xl,
        topEnd = ZunoRadius.xl,
        bottomStart = ZunoRadius.xl,
        bottomEnd = ZunoRadius.xs,
    )
    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .zunoGlassElevated(shape)
            .padding(horizontal = ZunoSpace.s12, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_file, 18.dp)
        Text(
            text = fileName,
            style = ZunoType.FootnoteStrong,
            color = ZunoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
