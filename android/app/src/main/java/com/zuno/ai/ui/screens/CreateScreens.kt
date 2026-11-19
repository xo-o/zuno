package com.zuno.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.Roleplay
import com.zuno.ai.data.RoleplayDraft
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicDraft
import com.zuno.ai.data.TopicStatus
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
import com.zuno.ai.ui.components.ZunoTypingBubble
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
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
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
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
        stringResource(R.string.topics_builder_greeting_named, firstName)
    } else {
        stringResource(R.string.topics_builder_greeting)
    }
    val senderLabel = stringResource(R.string.topics_builder_sender)
    val starterChips = listOf(
        stringResource(R.string.topics_chip_trip),
        stringResource(R.string.topics_chip_interview),
        stringResource(R.string.topics_chip_cafe),
        stringResource(R.string.topics_chip_small_talk),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
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
                        onRefine = { composerFocus.requestFocus() },
                        onCreate = {
                            focusManager.clearFocus()
                            onAcceptDraft(draft)
                        },
                    )
                }
            }
        }

        val placeholder = if (builder.draft != null) {
            stringResource(R.string.topics_builder_refine_placeholder)
        } else {
            stringResource(R.string.topics_builder_placeholder)
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
}

@Composable
private fun DraftTopicCard(
    draft: TopicDraft,
    busy: Boolean,
    onRefine: () -> Unit,
    onCreate: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    val previewNames = draft.lessons.take(3).joinToString(" · ") { it.title }
    val overflow = (draft.lessons.size - 3).coerceAtLeast(0)
    val summaryLine = if (overflow > 0) "$previewNames (+$overflow)" else previewNames

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderDefault, shape)
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
                label = stringResource(R.string.topics_draft_refine),
                onClick = onRefine,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_t_sparkles,
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

// ── 10c · Topic Settings (`3007:13865`) ─────────────────────────────────────

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
    onPrimaryAction: (TopicDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val cardShape = RoundedCornerShape(ZunoRadius.xxl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, ZunoColors.BorderSubtle, cardShape)
                    .padding(horizontal = ZunoSpace.s16, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            ) {
                Text(
                    text = stringResource(R.string.topics_settings_created_eyebrow, summary.totalLessons),
                    style = ZunoType.Micro,
                    color = ZunoColors.TextTertiary,
                )
                Text(
                    text = summary.title,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = "$languageName $level · ${topicCategoryLabel(summary.category)}",
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(
                    text = stringResource(R.string.topics_settings_who_label),
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

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(
                    text = if (isPublic && summary.learnersCount > 0) {
                        "$displayUrl · ${stringResource(R.string.topics_learners_count, summary.learnersCount)}"
                    } else {
                        displayUrl
                    },
                    style = ZunoType.BodySmall,
                    color = if (isPublic) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val ready = isPublic && shareUrl != null && !busy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ZunoButton(
                        label = stringResource(R.string.share_action),
                        onClick = { shareUrl?.let(onShare) },
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Medium,
                        leadingIcon = R.drawable.zuno_ic_t_share,
                        leadingIconTint = ZunoColors.IconPrimary,
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoButton(
                        label = stringResource(R.string.share_copy_link),
                        onClick = { shareUrl?.let(onCopyLink) },
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Medium,
                        leadingIcon = R.drawable.zuno_ic_t_copy,
                        leadingIconTint = ZunoColors.IconPrimary,
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s16),
        ) {
            val primaryLabel = if (summary.status == TopicStatus.NotStarted) {
                stringResource(R.string.topics_settings_start_lesson1)
            } else {
                stringResource(R.string.topics_settings_open_topic)
            }
            ZunoButton(
                label = primaryLabel,
                onClick = { onPrimaryAction(topic) },
                size = ZunoButtonSize.Large,
                leadingIcon = R.drawable.zuno_ic_t_arrow_right,
                leadingIconTint = ZunoColors.IconOnInverse,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            .background(ZunoColors.BgCanvas)
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
                        onRefine = { composerFocus.requestFocus() },
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
}

@Composable
private fun DraftRoleplayCard(
    draft: RoleplayDraft,
    busy: Boolean,
    onRefine: () -> Unit,
    onCreate: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderDefault, shape)
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
                label = stringResource(R.string.topics_draft_refine),
                onClick = onRefine,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Medium,
                leadingIcon = R.drawable.zuno_ic_t_sparkles,
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

// ── Roleplays · 3c · Roleplay Settings (`3008:13950`) ───────────────────────

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
    onStartLive: (Roleplay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
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
                .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s12),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val cardShape = RoundedCornerShape(ZunoRadius.xxl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, ZunoColors.BorderSubtle, cardShape)
                    .padding(horizontal = ZunoSpace.s16, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            ) {
                Text(
                    text = stringResource(R.string.roleplays_settings_created_eyebrow),
                    style = ZunoType.Micro,
                    color = ZunoColors.TextTertiary,
                )
                Text(
                    text = roleplay.title,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.roleplays_draft_roles_line, roleplay.userRole, roleplay.aiRole) +
                        " · $languageName $level · ${roleplayCategoryLabel(roleplay.category)}",
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(
                    text = stringResource(R.string.topics_settings_who_label),
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

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(
                    text = if (isPublic && roleplay.practicedCount > 0) {
                        "$displayUrl · ${stringResource(R.string.roleplays_practiced_count, roleplay.practicedCount)}"
                    } else {
                        displayUrl
                    },
                    style = ZunoType.BodySmall,
                    color = if (isPublic) ZunoColors.TextSecondary else ZunoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val ready = isPublic && shareUrl != null && !busy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ZunoButton(
                        label = stringResource(R.string.share_action),
                        onClick = { shareUrl?.let(onShare) },
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Medium,
                        leadingIcon = R.drawable.zuno_ic_t_share,
                        leadingIconTint = ZunoColors.IconPrimary,
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    )
                    ZunoButton(
                        label = stringResource(R.string.share_copy_link),
                        onClick = { shareUrl?.let(onCopyLink) },
                        variant = ZunoButtonVariant.Secondary,
                        size = ZunoButtonSize.Medium,
                        leadingIcon = R.drawable.zuno_ic_t_copy,
                        leadingIconTint = ZunoColors.IconPrimary,
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8, bottom = ZunoSpace.s16),
        ) {
            ZunoButton(
                label = stringResource(R.string.roleplays_settings_start_cta),
                onClick = { onStartLive(roleplay) },
                size = ZunoButtonSize.Large,
                leadingIcon = R.drawable.zuno_ic_t_audio_lines,
                leadingIconTint = ZunoColors.IconOnInverse,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            .clip(shape)
            .background(ZunoColors.SurfaceStrong)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
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
