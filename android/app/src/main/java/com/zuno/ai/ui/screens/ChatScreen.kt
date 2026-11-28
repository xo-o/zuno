package com.zuno.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.Attachment
import com.zuno.ai.data.Message
import com.zuno.ai.avatar.catalog.LocalActiveTutor
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.ui.components.ZunoAiTutorAvatar
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoComposer
import com.zuno.ai.ui.components.ZunoDraftAttachment
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoMessageAttachments
import com.zuno.ai.ui.components.ZunoPendingAttachments
import com.zuno.ai.ui.components.ZunoFeedbackPill
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoSessionStart
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoIconButtonStyle
import com.zuno.ai.ui.components.ZunoTutorHint
import com.zuno.ai.ui.components.ZunoTypingBubble
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout
import com.zuno.ai.viewmodel.DraftAttachment
import com.zuno.ai.viewmodel.DraftUploadState

/** Free Talk / roleplay / lesson conversation. The mic opens voice mode ([VoiceModeScreen]) for it. */
@Composable
fun ChatScreen(
    title: String,
    sessionLabel: String,
    messages: List<Message>?,
    pendingText: String?,
    pendingAttachments: List<DraftAttachment>,
    drafts: List<DraftAttachment>,
    error: String?,
    placeholder: String,
    typingHint: String?,
    menu: List<ZunoMenuAction>,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onSend: (String) -> Unit,
    onDraftChanged: (String) -> Unit,
    /** Saved composer text, restored when the chat reopens. */
    savedDraft: () -> String,
    onSaveDraft: (String) -> Unit,
    onStartVoice: () -> Unit,
    onListen: (String) -> Unit,
    onExplain: (Message) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onRetryAttachment: (DraftAttachment) -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
    /** Preferences · Show translations. */
    showTranslations: Boolean = true,
    levelLabel: String = "B1",
    /** AI persona for this thread (defaults to user's active tutor; overridden for Chats AI Friends). */
    tutor: TutorAvatarProfile = LocalActiveTutor.current,
) {
    val listState = rememberLazyListState()
    // Re-scroll whenever content at the bottom changes — including the typing bubble being replaced by the reply.
    LaunchedEffect(messages?.size, pendingText, error) {
        if (messages == null) return@LaunchedEffect
        val lastIndex = messages.size +
            (if (pendingText != null) 2 else 0) +
            (if (error != null && pendingText == null) 1 else 0)
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
    }

    Box(modifier.fillMaxSize().zunoCanvasBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AiTutorTopBar(
                tutor = tutor,
                subtitle = if (title.isNotBlank()) "$title · $levelLabel" else levelLabel,
                onBack = onBack,
                onStartCall = onStartVoice,
                menu = menu,
            )
            if (messages == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ZunoLoadState(error = error, onRetry = onRetryLoad)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
                ) {
                    item(key = "start") { ZunoSessionStart(sessionLabel) }
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        if (message.kind == "call_summary") {
                            ReturnedCallSummaryCard(
                                message = message,
                                onNotesClick = { onExplain(message) },
                            )
                        } else if (message.isUser) {
                            UserTurn(message.text, message.feedback) {
                                if (message.attachments.isNotEmpty()) ZunoMessageAttachments(message.attachments, onOpenAttachment)
                            }
                        } else {
                            // The note describes files on the learner turn just above; tapping it opens the first one.
                            val source = messages.getOrNull(index - 1)?.attachments?.firstOrNull()
                            ZunoAssistantBubble(
                                message = message.text,
                                translation = message.translation.takeIf { showTranslations },
                                glossary = message.glossary,
                                senderLabel = tutor.displayName,
                                onListen = { onListen(message.text) },
                                onExplain = { onExplain(message) },
                                contextNote = message.contextNote,
                                onContextClick = source?.let { { onOpenAttachment(it) } },
                            )
                        }
                    }
                    if (pendingText != null) {
                        item(key = "pending-user") {
                            UserTurn(pendingText, null) {
                                if (pendingAttachments.isNotEmpty()) ZunoPendingAttachments(pendingAttachments)
                            }
                        }
                        item(key = "pending-reply") { ZunoTypingBubble() }
                    }
                    if (error != null && pendingText == null) {
                        item(key = "error") {
                            Text(
                                error,
                                style = ZunoType.CaptionRegular,
                                color = ZunoColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            ComposerFooter(
                enabled = messages != null && pendingText == null,
                placeholder = placeholder,
                typingHint = typingHint,
                drafts = drafts,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment,
                onRetryAttachment = onRetryAttachment,
                onDraftChanged = onDraftChanged,
                savedDraft = savedDraft,
                onSaveDraft = onSaveDraft,
                onSend = onSend,
                onVoice = onStartVoice,
            )
        }
    }
}

@Composable
private fun AiTutorTopBar(
    tutor: TutorAvatarProfile,
    subtitle: String,
    onBack: () -> Unit,
    onStartCall: () -> Unit,
    menu: List<ZunoMenuAction>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(
            icon = R.drawable.zuno_ic_arrow_left,
            onClick = onBack,
            style = ZunoIconButtonStyle.Ghost,
            size = ZunoIconButtonSize.Large,
            contentDescription = stringResource(R.string.common_back),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoAiTutorAvatar(size = 36.dp, tutor = tutor)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = tutor.displayName,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoIconButton(
                icon = R.drawable.zuno_ic_t_phone,
                onClick = onStartCall,
                style = ZunoIconButtonStyle.Ghost,
                size = ZunoIconButtonSize.Large,
                tint = ZunoColors.IconPrimary,
                contentDescription = stringResource(R.string.chats_action_talk),
            )
            ZunoOverflowMenu(menu, size = ZunoIconButtonSize.Large)
        }
    }
}

@Composable
private fun ReturnedCallSummaryCard(
    message: Message,
    onNotesClick: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .clickable(role = Role.Button, onClick = onNotesClick)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .zunoGlassSubtle(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(
                icon = if (message.callIsVideo) R.drawable.zuno_ic_t_video else R.drawable.zuno_ic_t_phone,
                size = 16.dp,
                tint = ZunoColors.IconPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(
                text = message.text,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.call_returned_summary_sub, message.callTipsCount.coerceAtLeast(1)),
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
            )
        }
        ZunoChip(
            label = stringResource(R.string.call_returned_notes),
            onClick = onNotesClick,
        )
    }
}

@Composable
private fun UserTurn(text: String, feedback: String?, attachments: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        attachments()
        // A message can be just a file; the "You" label then sits under the attachment.
        if (text.isNotBlank()) ZunoUserBubble(message = text)
        else Text("You", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
        feedback?.let { ZunoFeedbackPill(it) }
    }
}

@Composable
private fun ComposerFooter(
    enabled: Boolean,
    placeholder: String,
    typingHint: String?,
    drafts: List<DraftAttachment>,
    onAttach: () -> Unit,
    onRemoveAttachment: (DraftAttachment) -> Unit,
    onRetryAttachment: (DraftAttachment) -> Unit,
    onDraftChanged: (String) -> Unit,
    savedDraft: () -> String,
    onSaveDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(savedDraft()) }
    val attachmentsReady = drafts.all { it.state == DraftUploadState.Ready }
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier.padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        AnimatedVisibility(visible = typingHint != null && draft.isNotBlank()) {
            ZunoTutorHint(typingHint.orEmpty())
        }
        ZunoComposer(
            value = draft,
            onValueChange = {
                draft = it
                onDraftChanged(it)
                onSaveDraft(it)
            },
            onSend = {
                val text = draft.trim()
                if ((text.isNotEmpty() || drafts.isNotEmpty()) && enabled && attachmentsReady) {
                    onSend(text)
                    draft = ""
                    onSaveDraft("")
                }
            },
            onAttach = onAttach,
            onVoice = onVoice,
            placeholder = placeholder,
            focusRequester = focusRequester,
            attachments = if (drafts.isEmpty()) null else {
                {
                    drafts.forEach { attachment ->
                        key(attachment.localId) {
                            ZunoDraftAttachment(
                                draft = attachment,
                                onRemove = { onRemoveAttachment(attachment) },
                                onRetry = { onRetryAttachment(attachment) },
                            )
                        }
                    }
                }
            },
            sendEnabled = attachmentsReady,
        )
    }
}
