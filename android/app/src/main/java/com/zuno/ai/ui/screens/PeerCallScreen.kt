package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.components.LiveKitVideoFeed
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoCallControlsBar
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoTabbedCoachCard
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.components.ZunoVoiceWaveform
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay

@Composable
fun PeerCallScreen(
    partner: PeerLearner?,
    userName: String,
    userInitial: String,
    languageName: String,
    userLevel: String,
    partnerJoined: Boolean,
    status: VoiceCallStatus,
    audioLevel: () -> Float,
    startedAtMillis: Long?,
    partnerLine: String?,
    turns: List<ChatMessage> = emptyList(),
    isMuted: Boolean,
    isCameraOn: Boolean,
    isSpeakerOn: Boolean,
    room: Room?,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    coachPrompts: CoachPrompts,
    menu: List<ZunoMenuAction>,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: (durationSec: Int) -> Unit,
    onPingPartner: () -> Unit,
    onTranslate: (String) -> Unit,
    onSlowReplay: (String) -> Unit,
    onSpeakCoachPhrase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var captionsOn by rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSec = startedAtMillis?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0
    val elapsedText = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    val handleEnd = { onEndCall(elapsedSec) }
    BackHandler(onBack = handleEnd)

    val partnerName = partner?.name ?: "Partner"
    val partnerShort = partner?.shortName ?: "Partner"
    val partnerInitial = partner?.initial ?: "L"
    val partnerLevel = partner?.level ?: userLevel

    val topTitle = stringResource(
        if (isCameraOn || remoteVideoTrack != null) R.string.call_video_title else R.string.call_waiting_title,
        elapsedText,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = topTitle,
            onBack = handleEnd,
            trailing = {
                if (menu.isNotEmpty()) {
                    ZunoOverflowMenu(menu, size = ZunoIconButtonSize.Large)
                } else {
                    Spacer(Modifier.size(44.dp))
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s20, vertical = ZunoSpace.s6),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
        ) {
            // 2-up Participants Row (`Chats · 13` waiting and `Chats · 14` active)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                if (!partnerJoined) {
                    // `Chats · 13`: Left tile = Partner (Waiting), Right tile = You (In room)
                    ParticipantTile(
                        name = partnerName,
                        initial = partnerInitial,
                        subtitle = stringResource(R.string.call_waiting_to_join),
                        badgeText = null,
                        highlightColor = null,
                        isSpeaking = false,
                        isMuted = false,
                        audioLevel = { 0f },
                        room = room,
                        videoTrack = null,
                        dimmed = true,
                        modifier = Modifier.weight(1f),
                    )
                    ParticipantTile(
                        name = stringResource(R.string.call_you_name, userName.ifBlank { "You" }),
                        initial = userInitial,
                        subtitle = if (isCameraOn) stringResource(R.string.call_camera_on) else stringResource(R.string.call_camera_off_ready),
                        badgeText = stringResource(R.string.call_in_room),
                        highlightColor = ZunoColors.StatusListening,
                        isSpeaking = false,
                        isMuted = isMuted,
                        audioLevel = audioLevel,
                        room = room,
                        videoTrack = localVideoTrack,
                        mirrorVideo = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // `Chats · 14`: Left tile = Partner, Right tile = You
                    val partnerSpeaking = status == VoiceCallStatus.Speaking
                    val userSpeaking = status == VoiceCallStatus.Listening
                    ParticipantTile(
                        name = partnerName,
                        initial = partnerInitial,
                        subtitle = "Open to speak · $partnerLevel",
                        badgeText = if (partnerSpeaking) stringResource(R.string.voice_status_dot_speaking) else null,
                        highlightColor = if (partnerSpeaking) ZunoColors.StatusSpeaking else null,
                        isSpeaking = partnerSpeaking,
                        isMuted = false,
                        audioLevel = audioLevel,
                        room = room,
                        videoTrack = remoteVideoTrack,
                        modifier = Modifier.weight(1f),
                    )
                    ParticipantTile(
                        name = stringResource(R.string.call_you_name, userName.ifBlank { "You" }),
                        initial = userInitial,
                        subtitle = "${if (isMuted) stringResource(R.string.voice_status_dot_muted) else stringResource(R.string.voice_status_dot_listening)} · $languageName $userLevel",
                        badgeText = null,
                        highlightColor = if (userSpeaking && !isMuted) ZunoColors.StatusListening else null,
                        isSpeaking = false,
                        isMuted = isMuted,
                        audioLevel = audioLevel,
                        room = room,
                        videoTrack = localVideoTrack,
                        mirrorVideo = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!partnerJoined) {
                // `Chats · 13`: Call room invite card + Tabbed Coach Card
                val hudShape = RoundedCornerShape(ZunoRadius.xxxl)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(hudShape)
                        .background(ZunoColors.SurfaceSubtle)
                        .border(1.dp, ZunoColors.BorderSubtle, hudShape)
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    Text(
                        text = stringResource(R.string.call_invite_sent),
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.call_waiting_for_join, partnerName),
                        style = ZunoType.BodyMediumStrong,
                        color = ZunoColors.TextPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                        ZunoChip(
                            label = stringResource(R.string.call_ping_partner, partnerShort),
                            onClick = onPingPartner,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            } else {
                // `Chats · 14`: Top-aligned scrollable Conversation (`You` + `Partner · Speaking`)
                val displayTurns = remember(turns, partnerLine) {
                    if (turns.isNotEmpty()) {
                        turns.filter { it.text.isNotBlank() }
                    } else {
                        buildList {
                            partnerLine?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                add(ChatMessage(id = "partner-preview", speaker = Speaker.AGENT, text = it, isFinal = true))
                            }
                        }
                    }
                }
                val listState = rememberLazyListState()
                val lastText = displayTurns.lastOrNull()?.text.orEmpty()
                LaunchedEffect(displayTurns.size, lastText) {
                    if (displayTurns.isNotEmpty()) {
                        delay(50)
                        listState.animateScrollToItem(displayTurns.size - 1)
                    }
                }
                val partnerLabel = if (status == VoiceCallStatus.Speaking) {
                    "$partnerName · ${stringResource(R.string.voice_status_dot_speaking)}"
                } else {
                    "$partnerName · ${stringResource(R.string.voice_status_dot_listening)}"
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = ZunoSpace.s4),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    items(displayTurns, key = { it.id }) { turn ->
                        when (turn.speaker) {
                            Speaker.USER -> {
                                ZunoUserBubble(
                                    message = turn.text,
                                    senderLabel = if (turn.isFinal) "You" else stringResource(R.string.voice_you_speaking),
                                )
                            }
                            Speaker.AGENT -> {
                                ZunoAssistantBubble(
                                    message = turn.text,
                                    senderLabel = partnerLabel,
                                    onListen = { onTranslate(turn.text) },
                                    listenLabel = stringResource(R.string.voice_chip_translate),
                                    listenIcon = R.drawable.zuno_ic_languages,
                                    onExplain = { onSlowReplay(turn.text) },
                                    explainLabel = stringResource(R.string.voice_chip_replay),
                                    explainIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                )
                            }
                        }
                    }
                }
            }

            if (captionsOn) {
                ZunoTabbedCoachCard(
                    prompts = coachPrompts,
                    onSpeakPhrase = onSpeakCoachPhrase,
                )
            }
        }

        ZunoCallControlsBar(
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            isSpeakerOn = isSpeakerOn,
            isCaptionsOn = captionsOn,
            onToggleMute = onToggleMute,
            onToggleCamera = onToggleCamera,
            onEndCall = handleEnd,
            onToggleSpeaker = onToggleSpeaker,
            onToggleCaptions = { captionsOn = !captionsOn },
        )
    }
}

@Composable
private fun ParticipantTile(
    name: String,
    initial: String,
    subtitle: String,
    badgeText: String?,
    highlightColor: Color?,
    isSpeaking: Boolean,
    isMuted: Boolean,
    audioLevel: () -> Float,
    room: Room?,
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier,
    mirrorVideo: Boolean = false,
    dimmed: Boolean = false,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    val borderColor = highlightColor ?: ZunoColors.BorderDefault
    val borderWidth = if (highlightColor != null) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .height(204.dp)
            .alpha(if (dimmed) 0.72f else 1f)
            .clip(shape)
            .background(if (highlightColor != null) ZunoColors.SurfaceDefault else ZunoColors.SurfaceSubtle)
            .border(borderWidth, borderColor, shape),
    ) {
        if (videoTrack != null) {
            LiveKitVideoFeed(
                room = room,
                videoTrack = videoTrack,
                mirror = mirrorVideo,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(98.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color(0x910D0D0F),
                            1f to Color(0xE00D0D0F),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top badge / waveform / mic pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (badgeText != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (badgeText != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(highlightColor ?: ZunoColors.StatusSpeaking),
                        )
                        Text(
                            text = badgeText,
                            style = ZunoType.Micro,
                            color = highlightColor ?: ZunoColors.StatusSpeaking,
                            maxLines = 1,
                        )
                    }
                }
                if (isSpeaking) {
                    ZunoVoiceWaveform(
                        active = true,
                        audioLevel = audioLevel,
                        modifier = Modifier.width(42.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(23.dp)
                            .clip(CircleShape)
                            .background(ZunoColors.SurfaceStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoIcon(
                            icon = if (isMuted) R.drawable.zuno_ic_mic_off else R.drawable.zuno_ic_t_mic,
                            size = 13.dp,
                            tint = if (isMuted) ZunoColors.TextDanger else ZunoColors.IconPrimary,
                        )
                    }
                }
            }

            // Center avatar halo when camera is off
            if (videoTrack == null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .then(
                                if (highlightColor != null) {
                                    Modifier.border(1.5.dp, highlightColor, CircleShape)
                                } else Modifier,
                            )
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ZunoColors.SurfaceStrong)
                                .border(1.dp, highlightColor ?: ZunoColors.BorderDefault, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initial.take(1).uppercase(),
                                style = ZunoType.TitleLarge,
                                color = ZunoColors.TextPrimary,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = ZunoType.BodyMediumStrong,
                    color = ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = ZunoType.FootnoteRegular,
                    color = highlightColor ?: ZunoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
