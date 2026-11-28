package com.zuno.ai.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.PeerCallRoom
import com.zuno.ai.data.PeerConversationThread
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.data.PeerMessage
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoComposer
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoIconButtonStyle
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoLogoMark
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOpenToSpeakAvatar
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoRadioRow
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.components.ZunoTextField
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassOverlay
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun PeerChatScreen(
    thread: PeerConversationThread?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStartCall: () -> Unit,
    onJoinCall: (String) -> Unit,
    onDeclineCall: (String) -> Unit,
    onTogglePartner: (PeerLearner, Boolean) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onReportUser: (String, String) -> Unit,
    onDeleteChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var bannerDismissedCallId by rememberSaveable { mutableStateOf<String?>(null) }

    // Poll every 2 seconds while on screen for real-time replies & incoming call invites
    LaunchedEffect(thread?.conversation?.id) {
        while (true) {
            delay(2_000)
            onRefresh()
        }
    }

    val partner = thread?.partner
    val messages = thread?.messages
    val activeCall = thread?.activeCall
    val listState = rememberLazyListState()

    LaunchedEffect(messages?.size) {
        if (!messages.isNullOrEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    val incomingWaitingCall = activeCall?.takeIf {
        it.status == "waiting" &&
            it.hostUserId == partner?.id &&
            bannerDismissedCallId != it.id
    }

    val menuActions = if (thread == null || partner == null) {
        emptyList()
    } else {
        listOf(
            ZunoMenuAction(
                label = stringResource(R.string.member_menu_view_profile),
                icon = R.drawable.zuno_ic_user_circle,
                onClick = { showProfileSheet = true },
            ),
            ZunoMenuAction(
                label = stringResource(
                    if (partner.isPartner) R.string.member_menu_partner_added else R.string.member_menu_add_partner,
                ),
                icon = if (partner.isPartner) R.drawable.zuno_ic_t_check_circle else R.drawable.zuno_ic_user_plus,
                onClick = { onTogglePartner(partner, !partner.isPartner) },
            ),
            ZunoMenuAction(
                label = stringResource(
                    if (thread.conversation.muted) R.string.member_menu_unmute_chat else R.string.member_menu_mute_chat,
                ),
                icon = R.drawable.zuno_ic_t_bell_off,
                onClick = { onToggleMute(!thread.conversation.muted) },
            ),
            ZunoMenuAction(
                label = stringResource(R.string.member_menu_report_user),
                icon = R.drawable.zuno_ic_t_flag,
                destructive = true,
                dividerBefore = true,
                onClick = { showReportSheet = true },
            ),
            ZunoMenuAction(
                label = stringResource(R.string.member_menu_delete_chat),
                icon = R.drawable.zuno_ic_t_trash,
                destructive = true,
                onClick = { confirmDelete = true },
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            MemberTopBar(
                partner = partner,
                menu = menuActions,
                onBack = onBack,
                onProfileClick = { if (partner != null) showProfileSheet = true },
                onStartCall = onStartCall,
            )

            if (messages == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ZunoLoadState(error = null, onRetry = onRefresh)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = zunoLayout.screenPaddingH,
                        vertical = ZunoSpace.s16,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
                ) {
                    if (messages.isNotEmpty()) {
                        item(key = "time-header") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = formatDayTime(messages.first().createdAtMillis),
                                    style = ZunoType.FootnoteRegular,
                                    color = ZunoColors.TextTertiary,
                                )
                            }
                        }
                    }

                    items(messages, key = { it.id }) { msg ->
                        when (msg.kind) {
                            "call_invite" -> CallRoomInviteCard(
                                message = msg,
                                partnerName = partner?.name ?: "Partner",
                                activeCall = activeCall,
                                onDecline = { msg.callRoomId?.let(onDeclineCall) },
                                onJoin = { msg.callRoomId?.let(onJoinCall) },
                            )
                            "missed_invite" -> MissedInviteCard(
                                message = msg,
                                partnerShortName = partner?.shortName ?: "Partner",
                                onTapToCall = onStartCall,
                            )
                            "call_summary" -> PeerCallSummaryCard(
                                message = msg,
                                onTapToCall = onStartCall,
                            )
                            else -> {
                                if (msg.isMine) {
                                    ZunoUserBubble(message = msg.text)
                                } else {
                                    IncomingMemberBubble(
                                        senderName = partner?.name ?: "Partner",
                                        text = msg.text,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s8),
            ) {
                ZunoComposer(
                    value = draft,
                    onValueChange = { draft = it },
                    onSend = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed)
                            draft = ""
                        }
                    },
                    onAttach = { },
                    onVoice = onStartCall,
                    showAttach = true,
                    placeholder = stringResource(
                        R.string.member_message_placeholder,
                        partner?.shortName ?: "Partner",
                    ),
                    enabled = thread != null,
                )
            }
        }

        // In-app Call Invite Banner (`Chats · 17`)
        AnimatedVisibility(
            visible = incomingWaitingCall != null && partner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
        ) {
            if (incomingWaitingCall != null && partner != null) {
                IncomingInviteNotificationBanner(
                    partnerName = partner.name,
                    languageName = partner.languageName,
                    level = partner.level,
                    onJoin = {
                        bannerDismissedCallId = incomingWaitingCall.id
                        onJoinCall(incomingWaitingCall.id)
                    },
                    onDismiss = { bannerDismissedCallId = incomingWaitingCall.id },
                )
            }
        }
    }

    if (showProfileSheet && partner != null) {
        MemberProfileSheet(
            member = partner,
            onDismiss = { showProfileSheet = false },
            onInviteToTalk = {
                showProfileSheet = false
                onStartCall()
            },
            onSendMessage = { showProfileSheet = false },
            onTogglePartner = { add -> onTogglePartner(partner, add) },
        )
    }

    if (showReportSheet && partner != null) {
        ReportPeerSheet(
            partnerName = partner.name,
            onDismiss = { showReportSheet = false },
            onSubmit = { reason, details ->
                showReportSheet = false
                onReportUser(reason, details)
            },
        )
    }

    if (confirmDelete && partner != null) {
        ZunoDialog(
            title = stringResource(R.string.member_menu_delete_chat),
            body = "Your 1:1 messages with ${partner.name} will be removed from your inbox.",
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteChat()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun MemberTopBar(
    partner: PeerLearner?,
    menu: List<ZunoMenuAction>,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onStartCall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = zunoLayout.topBarPaddingH),
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
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(ZunoRadius.md))
                .clickable(enabled = partner != null, role = Role.Button, onClick = onProfileClick)
                .padding(vertical = ZunoSpace.s4, horizontal = ZunoSpace.s2),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoOpenToSpeakAvatar(
                initial = partner?.initial ?: "L",
                imageUrl = partner?.imageUrl,
                size = 36.dp,
                openToSpeak = false,
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = partner?.name.orEmpty(),
                        style = ZunoType.LabelMedium,
                        color = ZunoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (partner?.openToSpeak == true) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(ZunoColors.StatusSpeaking),
                        )
                    }
                }
                if (partner != null) {
                    Text(
                        text = if (partner.openToSpeak) {
                            stringResource(R.string.member_open_status, partner.languageName, partner.level)
                        } else {
                            "${partner.activeLabel} · ${partner.languageName} ${partner.level}"
                        },
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                contentDescription = stringResource(R.string.member_invite_to_talk),
            )
            if (menu.isNotEmpty()) {
                ZunoOverflowMenu(menu, size = ZunoIconButtonSize.Large)
            }
        }
    }
}

@Composable
private fun IncomingMemberBubble(
    senderName: String,
    text: String,
) {
    val shape = RoundedCornerShape(
        topStart = ZunoRadius.xs,
        topEnd = ZunoRadius.xxl,
        bottomEnd = ZunoRadius.xxl,
        bottomStart = ZunoRadius.xxl,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
    ) {
        Text(
            text = senderName,
            style = ZunoType.FootnoteRegular,
            color = ZunoColors.TextTertiary,
        )
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .zunoGlassSubtle(shape)
                .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        ) {
            Text(
                text = text,
                style = ZunoType.BodyMedium,
                color = ZunoColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun CallRoomInviteCard(
    message: PeerMessage,
    partnerName: String,
    activeCall: PeerCallRoom?,
    onDecline: () -> Unit,
    onJoin: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    val isLive = activeCall == null || activeCall.id != message.callRoomId || activeCall.status in listOf("waiting", "active")
    val short = partnerName.substringBefore(" ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .padding(ZunoSpace.s14),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isLive) ZunoColors.StatusSpeaking else ZunoColors.IconTertiary),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                Text(
                    text = if (message.isMine) "You invited $short to talk" else stringResource(R.string.member_call_invite_title, short),
                    style = ZunoType.LabelMedium,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.member_call_invite_sub),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            ZunoButton(
                label = stringResource(R.string.member_call_decline),
                onClick = onDecline,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Small,
                modifier = Modifier.weight(1f),
            )
            ZunoButton(
                label = stringResource(R.string.member_call_join),
                onClick = onJoin,
                leadingIcon = R.drawable.zuno_ic_t_phone,
                leadingIconTint = ZunoColors.IconOnInverse,
                variant = ZunoButtonVariant.Primary,
                size = ZunoButtonSize.Small,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MissedInviteCard(
    message: PeerMessage,
    partnerShortName: String,
    onTapToCall: () -> Unit,
) {
    val context = LocalContext.current
    val timeText = DateFormat.getTimeFormat(context).format(Date(message.createdAtMillis))
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .clickable(role = Role.Button, onClick = onTapToCall)
            .padding(ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ZunoColors.TextDanger),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(
                text = "Missed invite to talk · $timeText",
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.member_call_missed_sub, partnerShortName),
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun PeerCallSummaryCard(
    message: PeerMessage,
    onTapToCall: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(shape)
            .clickable(role = Role.Button, onClick = onTapToCall)
            .padding(ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .zunoGlassSubtle(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(R.drawable.zuno_ic_t_phone, 16.dp, tint = ZunoColors.IconPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(
                text = message.text,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextPrimary,
            )
            Text(
                text = "Tap to start another 1:1 practice",
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun IncomingInviteNotificationBanner(
    partnerName: String,
    languageName: String,
    level: String,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassOverlay(shape, shadowElevation = 14.dp)
            .clickable(role = Role.Button, onClick = onJoin)
            .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .zunoGlassElevated(
                    shape = RoundedCornerShape(ZunoRadius.sm),
                    fillColor = ZunoColors.BgCanvas,
                ),
            contentAlignment = Alignment.Center,
        ) {
            ZunoLogoMark(24.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$partnerName invited you to talk",
                    style = ZunoType.LabelMedium,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "now",
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                )
            }
            Text(
                text = stringResource(R.string.member_invite_banner_body, languageName, level),
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportPeerSheet(
    partnerName: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val reasons = listOf(
        "harassment" to "Harassment or inappropriate behaviour",
        "spam" to "Spam or off-topic",
        "no_show" to "Not responding or fake profile",
        "other" to "Something else",
    )
    var selected by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }

    ZunoSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.member_menu_report_user),
        description = "Report $partnerName to the Zuno safety team.",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            reasons.forEach { (code, label) ->
                ZunoRadioRow(
                    label = label,
                    selected = selected == code,
                    onClick = { selected = code },
                )
            }
            ZunoTextField(
                value = details,
                onValueChange = { details = it },
                placeholder = stringResource(R.string.report_details_hint),
            )
            Spacer(Modifier.height(ZunoSpace.s4))
            ZunoButton(
                label = stringResource(R.string.report_submit),
                onClick = { selected?.let { onSubmit(it, details) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatDayTime(millis: Long): String {
    val date = Date(millis)
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(date)
    return "Today · $time"
}
