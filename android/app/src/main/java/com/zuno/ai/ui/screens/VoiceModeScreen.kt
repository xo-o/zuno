package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.avatar.audio.AvatarSpeechSink
import com.zuno.ai.avatar.catalog.TutorAvatarCatalog
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.ui.TutorStageAvatar
import com.zuno.ai.data.CoachPromptItem
import com.zuno.ai.data.CoachPrompts
import com.zuno.ai.data.LessonCardUi
import com.zuno.ai.data.LessonStageKind
import com.zuno.ai.data.LessonStageUi
import com.zuno.ai.data.LessonTurnUi
import com.zuno.ai.data.PronunciationEvalUi
import com.zuno.ai.data.RescuePayloadUi
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.ui.components.LiveKitVideoFeed
import com.zuno.ai.ui.components.VoiceOrbState
import com.zuno.ai.ui.components.ZunoAssistantBubble
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoCallControlsBar
import com.zuno.ai.ui.components.ZunoChip
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoFeedbackPill
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoLessonCoachCard
import com.zuno.ai.ui.components.ZunoLessonRescueBar
import com.zuno.ai.ui.components.ZunoListeningIndicator
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoPronunciationFeedback
import com.zuno.ai.ui.components.ZunoRecallPrompt
import com.zuno.ai.ui.components.ZunoRoleplayPill
import com.zuno.ai.ui.components.ZunoScaffoldCard
import com.zuno.ai.ui.components.ZunoSpokenAssembly
import com.zuno.ai.ui.components.ZunoTabbedCoachCard
import com.zuno.ai.ui.components.ZunoTeachCard
import com.zuno.ai.ui.components.ZunoUserBubble
import com.zuno.ai.ui.components.ZunoVoiceOrb
import com.zuno.ai.ui.components.ZunoVoiceWaveform
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassMediaPill
import com.zuno.ai.ui.theme.zunoGlassSubtle
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay

/** What the live call is doing, as shown under the orb. */
enum class VoiceCallStatus(val orb: VoiceOrbState) {
    Connecting(VoiceOrbState.Thinking),
    Listening(VoiceOrbState.Listening),
    Muted(VoiceOrbState.Idle),
    Thinking(VoiceOrbState.Thinking),
    Speaking(VoiceOrbState.Speaking),
}

/** Where the orb sits on Home and in a standard call, as a share of the space under the top bar. */
internal const val OrbTopFraction = 0.21f

private val CompactOrbSphereSize = 140.dp
private val CompactOrbCanvasSize = 218.dp
internal val OrbSphereSize = 190.dp
internal val OrbCanvasSize = 296.dp

/**
 * Voice mode — handles both standard live tutor calls (Free Talk / Roleplay) and the
 * 5-stage `03g` interactive voice lesson flow (`03a` Teach & Model → `03b` Shadow → `03c` Build → `03d` Recall → `03e/03f` Roleplay).
 */
@Composable
fun VoiceModeScreen(
    status: VoiceCallStatus,
    level: () -> Float,
    startedAtMillis: Long?,
    tutorLine: String?,
    userLine: String? = null,
    turns: List<ChatMessage> = emptyList(),
    isMuted: Boolean,
    menu: List<ZunoMenuAction>,
    onEnd: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    isLessonMode: Boolean = false,
    lessonPosition: Int = 1,
    learnerLevel: String = "B1",
    lessonStage: LessonStageUi? = null,
    lessonTurns: List<LessonTurnUi> = emptyList(),
    pronunciationEval: PronunciationEvalUi? = null,
    rescuePayload: RescuePayloadUi? = null,
    onLessonAction: (action: String, payload: String?) -> Unit = { _, _ -> },
    sessionKindLabel: String = "Free talk",
    languageLevelLabel: String = "Spanish $learnerLevel",
    initialCaptionsOn: Boolean? = null,
    userName: String = "You",
    userInitial: String = "Y",
    isCameraOn: Boolean = false,
    isSpeakerOn: Boolean = true,
    room: Room? = null,
    localVideoTrack: VideoTrack? = null,
    tutor: TutorAvatarProfile = TutorAvatarCatalog.DEFAULT_TUTOR,
    avatarSpeechSink: AvatarSpeechSink? = null,
    coachPrompts: CoachPrompts = CoachPrompts(),
    onToggleCamera: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onSpeakCoachPhrase: (String) -> Unit = {},
    onTranslate: ((String) -> Unit)? = null,
    onSlowReplay: ((String) -> Unit)? = null,
    onSwitchTopic: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val defaultCaptions = initialCaptionsOn ?: true
    var captionsOn by rememberSaveable(isLessonMode, initialCaptionsOn) { mutableStateOf(defaultCaptions) }
    var confirmExitLesson by rememberSaveable { mutableStateOf(false) }

    val handleExitRequest = {
        if (isLessonMode) {
            confirmExitLesson = true
        } else {
            onEnd()
        }
    }
    BackHandler(onBack = handleExitRequest)

    // 6.0-second silence timer in 03d Active Recall to subtly pulse the `First word` chip
    var pulseFirstWord by remember { mutableStateOf(false) }
    val inActiveRecall = lessonStage?.stage == LessonStageKind.ActiveRecall
    LaunchedEffect(inActiveRecall, status, userLine, rescuePayload) {
        pulseFirstWord = false
        if (inActiveRecall && status == VoiceCallStatus.Listening && userLine.isNullOrBlank() && rescuePayload == null) {
            delay(6_000)
            pulseFirstWord = true
        }
    }

    val elapsed = rememberElapsed(startedAtMillis)
    val topBarTitle = when {
        isCameraOn -> stringResource(R.string.call_video_title, elapsed)
        isLessonMode -> stringResource(R.string.call_waiting_title, elapsed)
        else -> stringResource(R.string.call_live_title, elapsed)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoCompactTitleTopBar(
            title = topBarTitle,
            onBack = handleExitRequest,
            trailing = {
                if (menu.isNotEmpty()) {
                    ZunoOverflowMenu(menu, size = ZunoIconButtonSize.Large)
                } else {
                    Spacer(Modifier.size(44.dp))
                }
            },
        )

        if (isLessonMode) {
            LessonAvatarVoiceBody(
                status = status,
                level = level,
                tutorLine = tutorLine,
                userLine = userLine,
                captionsOn = captionsOn,
                isMuted = isMuted,
                isCameraOn = isCameraOn,
                isSpeakerOn = isSpeakerOn,
                learnerLevel = learnerLevel,
                userName = userName,
                userInitial = userInitial,
                room = room,
                localVideoTrack = localVideoTrack,
                tutor = tutor,
                avatarSpeechSink = avatarSpeechSink,
                lessonStage = lessonStage,
                lessonTurns = lessonTurns,
                pronunciationEval = pronunciationEval,
                rescuePayload = rescuePayload,
                pulseFirstWord = pulseFirstWord,
                onInterrupt = onInterrupt,
                onToggleMute = onToggleMute,
                onToggleCamera = onToggleCamera,
                onToggleSpeaker = onToggleSpeaker,
                onEnd = handleExitRequest,
                onToggleCaptions = { captionsOn = !captionsOn },
                onLessonAction = onLessonAction,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            PortraitVoiceCallBody(
                status = status,
                level = level,
                tutorLine = tutorLine,
                userLine = userLine,
                turns = turns,
                captionsOn = captionsOn,
                isMuted = isMuted,
                isCameraOn = isCameraOn,
                isSpeakerOn = isSpeakerOn,
                sessionKindLabel = sessionKindLabel,
                learnerLevel = learnerLevel,
                languageLevelLabel = languageLevelLabel,
                userName = userName,
                userInitial = userInitial,
                room = room,
                localVideoTrack = localVideoTrack,
                tutor = tutor,
                avatarSpeechSink = avatarSpeechSink,
                coachPrompts = coachPrompts,
                onInterrupt = onInterrupt,
                onToggleMute = onToggleMute,
                onToggleCamera = onToggleCamera,
                onToggleSpeaker = onToggleSpeaker,
                onEnd = handleExitRequest,
                onToggleCaptions = { captionsOn = !captionsOn },
                onSpeakCoachPhrase = onSpeakCoachPhrase,
                onTranslate = onTranslate,
                onSlowReplay = onSlowReplay,
                onSwitchTopic = onSwitchTopic,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    if (confirmExitLesson) {
        ZunoDialog(
            title = "End lesson early?",
            body = "Your progress in Lesson $lessonPosition won't be marked complete.",
            confirmLabel = "End call",
            dismissLabel = "Continue lesson",
            destructive = true,
            onConfirm = {
                confirmExitLesson = false
                onEnd()
            },
            onDismiss = { confirmExitLesson = false },
        )
    }
}

@Composable
private fun PortraitVoiceCallBody(
    status: VoiceCallStatus,
    level: () -> Float,
    tutorLine: String?,
    userLine: String?,
    turns: List<ChatMessage>,
    captionsOn: Boolean,
    isMuted: Boolean,
    isCameraOn: Boolean,
    isSpeakerOn: Boolean,
    sessionKindLabel: String,
    learnerLevel: String,
    languageLevelLabel: String,
    userName: String,
    userInitial: String,
    room: Room?,
    localVideoTrack: VideoTrack?,
    tutor: TutorAvatarProfile,
    avatarSpeechSink: AvatarSpeechSink?,
    coachPrompts: CoachPrompts,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
    onToggleCaptions: () -> Unit,
    onSpeakCoachPhrase: (String) -> Unit,
    onTranslate: ((String) -> Unit)?,
    onSlowReplay: ((String) -> Unit)?,
    onSwitchTopic: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tutorSpeaking = status == VoiceCallStatus.Speaking
    val displayTurns = remember(turns, tutorLine, userLine) {
        if (turns.isNotEmpty()) {
            turns.filter { it.text.isNotBlank() }
        } else {
            buildList {
                tutorLine?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(ChatMessage(id = "tutor-preview", speaker = Speaker.AGENT, text = it, isFinal = true))
                }
                userLine?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(ChatMessage(id = "user-preview", speaker = Speaker.USER, text = it, isFinal = false))
                }
            }
        }
    }
    val hasActiveUserSpeech = displayTurns.any { it.speaker == Speaker.USER && !it.isFinal }
    val showListeningPlaceholder = !hasActiveUserSpeech &&
        (status == VoiceCallStatus.Listening || status == VoiceCallStatus.Connecting || status == VoiceCallStatus.Speaking)
    val listState = rememberLazyListState()
    val lastTurnText = displayTurns.lastOrNull()?.text.orEmpty()

    LaunchedEffect(captionsOn, displayTurns.size, lastTurnText, showListeningPlaceholder) {
        if (!captionsOn) return@LaunchedEffect
        val total = displayTurns.size + if (showListeningPlaceholder) 1 else 0
        if (total > 0) {
            delay(50)
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(modifier = modifier) {
        AnimatedContent(
            targetState = isCameraOn,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "voiceCallCameraMode",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = ZunoSpace.s4),
        ) { cameraActive ->
            if (cameraActive) {
                // `Chats · 11 · AI Tutor — Video Call (Both Cameras)` (`2847:15551`)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = ZunoSpace.s20, vertical = ZunoSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    AiVideoParticipantsRow(
                        tutor = tutor,
                        avatarSpeechSink = avatarSpeechSink,
                        tutorSpeaking = tutorSpeaking,
                        isMuted = isMuted,
                        audioLevel = level,
                        sessionKindLabel = sessionKindLabel,
                        learnerLevel = learnerLevel,
                        userName = userName,
                        userInitial = userInitial,
                        room = room,
                        localVideoTrack = localVideoTrack,
                        onInterrupt = onInterrupt,
                    )
                    if (captionsOn) {
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
                                    Speaker.AGENT -> {
                                        ZunoAssistantBubble(
                                            message = turn.text,
                                            senderLabel = stringResource(R.string.onboarding_sender_sofia),
                                            onListen = { onTranslate?.invoke(turn.text) },
                                            listenLabel = stringResource(R.string.voice_chip_translate),
                                            listenIcon = R.drawable.zuno_ic_languages,
                                            onExplain = { onSlowReplay?.invoke(turn.text) },
                                            explainLabel = stringResource(R.string.voice_chip_speed_slow),
                                            explainIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                        )
                                    }
                                    Speaker.USER -> {
                                        ZunoUserBubble(
                                            message = turn.text,
                                            senderLabel = if (turn.isFinal) "You" else stringResource(R.string.voice_you_speaking),
                                        )
                                    }
                                }
                            }

                            if (showListeningPlaceholder) {
                                item(key = "listening_placeholder") {
                                    ListeningTurnPlaceholderBubble()
                                }
                            }
                        }

                        ZunoTabbedCoachCard(
                            prompts = coachPrompts,
                            onSpeakPhrase = onSpeakCoachPhrase,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                // Keep PortraitStageCard at a stable slot so toggling captions resizes without recreating GL/MediaPlayer
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val portraitHeight = (maxHeight * 0.44f).coerceIn(190.dp, 296.dp)
                    val bottomScrimHeight by animateDpAsState(
                        if (captionsOn) 52.dp else 64.dp,
                        label = "portraitScrimHeight",
                    )
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = if (captionsOn) Arrangement.spacedBy(ZunoSpace.s8) else Arrangement.Top,
                    ) {
                        PortraitStageCard(
                            tutor = tutor,
                            avatarSpeechSink = avatarSpeechSink,
                            tutorSpeaking = tutorSpeaking,
                            isCameraOn = false,
                            room = room,
                            localVideoTrack = localVideoTrack,
                            bottomScrimHeight = bottomScrimHeight,
                            pipWidth = if (captionsOn) 76.dp else 96.dp,
                            pipHeight = if (captionsOn) 104.dp else 132.dp,
                            onInterrupt = onInterrupt,
                            modifier = if (captionsOn) {
                                Modifier
                                    .fillMaxWidth()
                                    .height(portraitHeight)
                            } else {
                                Modifier.fillMaxSize()
                            },
                        )

                        if (captionsOn) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = ZunoSpace.s20),
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                            ) {
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
                                            Speaker.AGENT -> {
                                                ZunoAssistantBubble(
                                                    message = turn.text,
                                                    senderLabel = "${stringResource(R.string.onboarding_sender_sofia)} · $sessionKindLabel",
                                                    onListen = { onTranslate?.invoke(turn.text) },
                                                    listenLabel = stringResource(R.string.voice_chip_translate),
                                                    listenIcon = R.drawable.zuno_ic_languages,
                                                    onExplain = { onSlowReplay?.invoke(turn.text) },
                                                    explainLabel = stringResource(R.string.voice_chip_speed_slow),
                                                    explainIcon = R.drawable.zuno_ic_rotate_ccw_secondary,
                                                )
                                            }
                                            Speaker.USER -> {
                                                ZunoUserBubble(
                                                    message = turn.text,
                                                    senderLabel = if (turn.isFinal) "You" else stringResource(R.string.voice_you_speaking),
                                                )
                                            }
                                        }
                                    }

                                    if (showListeningPlaceholder) {
                                        item(key = "listening_placeholder") {
                                            ListeningTurnPlaceholderBubble()
                                        }
                                    }
                                }

                                FreeTalkCoachCard(
                                    sessionKindLabel = sessionKindLabel,
                                    languageLevelLabel = languageLevelLabel,
                                    status = status,
                                    coachPrompts = coachPrompts,
                                    onSpeakCoachPhrase = onSpeakCoachPhrase,
                                    onSwitchTopic = onSwitchTopic,
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
            onToggleMute = onToggleMute,
            onToggleCamera = onToggleCamera,
            onEndCall = onEnd,
            onToggleSpeaker = onToggleSpeaker,
            onToggleCaptions = onToggleCaptions,
        )
    }
}

@Composable
private fun AiVideoParticipantsRow(
    tutor: TutorAvatarProfile,
    avatarSpeechSink: AvatarSpeechSink?,
    tutorSpeaking: Boolean,
    isMuted: Boolean,
    audioLevel: () -> Float,
    sessionKindLabel: String,
    learnerLevel: String,
    userName: String,
    userInitial: String,
    room: Room?,
    localVideoTrack: VideoTrack?,
    onInterrupt: () -> Unit,
) {
    val tileShape = RoundedCornerShape(ZunoRadius.xxl)
    val overlayPill = Color(0xD6242429)
    val scrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.55f to Color(0x910D0D0F),
        1f to Color(0xE00D0D0F),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        // Left tile: Sofía (AI Tutor)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(204.dp)
                .clip(tileShape)
                .background(ZunoColors.SurfaceDefault)
                .border(
                    width = if (tutorSpeaking) 1.5.dp else 1.dp,
                    color = if (tutorSpeaking) ZunoColors.StatusSpeaking else ZunoColors.BorderDefault,
                    shape = tileShape,
                )
                .clickable(
                    enabled = tutorSpeaking,
                    role = Role.Button,
                    onClick = onInterrupt,
                )
                .semantics { contentDescription = "Sofía AI tutor — tap to interrupt" },
        ) {
            TutorStageAvatar(
                tutor = tutor,
                speechSink = avatarSpeechSink,
                contentDescription = stringResource(R.string.onboarding_sender_sofia),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(98.dp)
                    .background(scrim),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .zunoGlassMediaPill(CircleShape)
                            .padding(horizontal = ZunoSpace.s8, vertical = ZunoSpace.s4),
                        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZunoIcon(
                            icon = R.drawable.zuno_ic_t_sparkles,
                            size = 11.dp,
                            tint = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.chats_filter_ai_tutor),
                            style = ZunoType.Micro,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (tutorSpeaking) {
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .zunoGlassMediaPill(CircleShape)
                                .padding(horizontal = ZunoSpace.s8),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZunoVoiceWaveform(
                                active = true,
                                audioLevel = audioLevel,
                                modifier = Modifier.width(23.dp),
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                    Text(
                        text = stringResource(R.string.onboarding_sender_sofia),
                        style = ZunoType.BodyMediumStrong,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (tutorSpeaking) {
                            "${stringResource(R.string.voice_status_dot_speaking)} · $sessionKindLabel"
                        } else {
                            sessionKindLabel
                        },
                        style = ZunoType.FootnoteRegular,
                        color = if (tutorSpeaking) ZunoColors.StatusSpeaking else ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Right tile: You (Camera feed or avatar fallback)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(204.dp)
                .clip(tileShape)
                .background(ZunoColors.SurfaceDefault)
                .border(1.dp, ZunoColors.BorderDefault, tileShape),
        ) {
            if (localVideoTrack != null) {
                LiveKitVideoFeed(
                    room = room,
                    videoTrack = localVideoTrack,
                    mirror = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceStrong)
                        .border(1.dp, ZunoColors.BorderDefault, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = userInitial.take(1).uppercase(),
                        style = ZunoType.TitleLarge,
                        color = ZunoColors.TextPrimary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(98.dp)
                    .background(scrim),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ZunoSpace.s12, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .zunoGlassMediaPill(CircleShape)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoIcon(
                            icon = if (isMuted) R.drawable.zuno_ic_mic_off else R.drawable.zuno_ic_t_mic,
                            size = 12.dp,
                            tint = if (isMuted) ZunoColors.TextDanger else Color.White,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                    Text(
                        text = stringResource(R.string.call_you_name, userName.ifBlank { "You" }),
                        style = ZunoType.BodyMediumStrong,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${if (isMuted) stringResource(R.string.voice_status_dot_muted) else stringResource(R.string.voice_status_dot_listening)} · $learnerLevel",
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitStageCard(
    tutor: TutorAvatarProfile,
    avatarSpeechSink: AvatarSpeechSink?,
    tutorSpeaking: Boolean,
    isCameraOn: Boolean,
    room: Room?,
    localVideoTrack: VideoTrack?,
    bottomScrimHeight: androidx.compose.ui.unit.Dp,
    pipWidth: androidx.compose.ui.unit.Dp,
    pipHeight: androidx.compose.ui.unit.Dp,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(
                enabled = tutorSpeaking,
                role = Role.Button,
                onClick = onInterrupt,
            )
            .semantics { contentDescription = "Sofía AI tutor — tap to interrupt" },
    ) {
        TutorStageAvatar(
            tutor = tutor,
            speechSink = avatarSpeechSink,
            contentDescription = stringResource(R.string.onboarding_sender_sofia),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        0f to ZunoColors.BgCanvas.copy(alpha = 0.70f),
                        0.5f to ZunoColors.BgCanvas.copy(alpha = 0.25f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomScrimHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to ZunoColors.BgCanvas.copy(alpha = 0.22f),
                        0.80f to ZunoColors.BgCanvas.copy(alpha = 0.68f),
                        1f to ZunoColors.BgCanvas,
                    ),
                ),
        )
        if (isCameraOn && localVideoTrack != null) {
            val pipShape = RoundedCornerShape(ZunoRadius.xl)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = ZunoSpace.s16, bottom = ZunoSpace.s12)
                    .size(width = pipWidth, height = pipHeight)
                    .clip(pipShape)
                    .background(ZunoColors.BgElevated)
                    .border(1.dp, ZunoColors.BorderDefault, pipShape),
            ) {
                LiveKitVideoFeed(
                    room = room,
                    videoTrack = localVideoTrack,
                    mirror = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ListeningTurnPlaceholderBubble(
    senderLabel: String = stringResource(R.string.voice_you_listening),
    placeholderText: String = stringResource(R.string.voice_listening_turn_placeholder),
) {
    val shape = RoundedCornerShape(
        topStart = ZunoRadius.xl,
        topEnd = ZunoRadius.xl,
        bottomEnd = ZunoRadius.xs,
        bottomStart = ZunoRadius.xl,
    )
    val dashColor = ZunoColors.BorderSubtle
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
        Text(
            text = senderLabel,
            style = ZunoType.Micro,
            color = ZunoColors.TextTertiary,
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(ZunoColors.SurfaceSubtle)
                .drawBehind {
                    val radiusPx = ZunoRadius.xl.toPx()
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                        ),
                    )
                }
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s10),
        ) {
            Text(
                text = placeholderText,
                style = ZunoType.BodyMedium,
                color = ZunoColors.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FreeTalkCoachCard(
    sessionKindLabel: String,
    languageLevelLabel: String,
    status: VoiceCallStatus,
    coachPrompts: CoachPrompts,
    onSpeakCoachPhrase: (String) -> Unit,
    onSwitchTopic: (() -> Unit)?,
) {
    var expandedTab by rememberSaveable { mutableStateOf<Int?>(null) }
    val cardShape = RoundedCornerShape(ZunoRadius.xxxl)
    val statusColor = when (status) {
        VoiceCallStatus.Speaking -> ZunoColors.StatusSpeaking
        VoiceCallStatus.Listening -> ZunoColors.StatusListening
        VoiceCallStatus.Muted -> ZunoColors.IconTertiary
        VoiceCallStatus.Thinking -> ZunoColors.TextSecondary
        VoiceCallStatus.Connecting -> ZunoColors.TextTertiary
    }
    val statusLabel = stringResource(
        when (status) {
            VoiceCallStatus.Speaking -> R.string.voice_status_dot_speaking
            VoiceCallStatus.Listening -> R.string.voice_status_dot_listening
            VoiceCallStatus.Thinking -> R.string.voice_status_dot_thinking
            VoiceCallStatus.Muted -> R.string.voice_status_dot_muted
            VoiceCallStatus.Connecting -> R.string.voice_status_dot_connecting
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zunoGlassSubtle(cardShape)
            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$sessionKindLabel · $languageLevelLabel",
                style = ZunoType.LabelSmall,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = ZunoSpace.s8),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = statusLabel,
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextTertiary,
                    maxLines = 1,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoChip(
                label = stringResource(R.string.call_coach_answers),
                leadingIcon = R.drawable.zuno_ic_t_message_circle,
                leadingIconTint = ZunoColors.IconSecondary,
                selected = expandedTab == 0,
                onClick = { expandedTab = if (expandedTab == 0) null else 0 },
            )
            ZunoChip(
                label = stringResource(R.string.call_coach_switch_topic),
                leadingIcon = R.drawable.zuno_ic_sparkles,
                onClick = { onSwitchTopic?.invoke() },
            )
            ZunoChip(
                label = stringResource(R.string.call_coach_questions),
                leadingIcon = R.drawable.zuno_ic_plus,
                leadingIconTint = ZunoColors.IconSecondary,
                selected = expandedTab == 2,
                onClick = { expandedTab = if (expandedTab == 2) null else 2 },
            )
        }

        val activeItems: List<CoachPromptItem> = when (expandedTab) {
            0 -> coachPrompts.answers
            2 -> coachPrompts.questions
            else -> emptyList()
        }
        if (activeItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
                activeItems.take(2).forEach { item ->
                    val rowShape = RoundedCornerShape(ZunoRadius.lg)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zunoGlassElevated(rowShape)
                            .clickable(role = Role.Button, onClick = { onSpeakCoachPhrase(item.phrase) })
                            .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s8),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "“${item.phrase}”",
                                style = ZunoType.FootnoteStrong,
                                color = ZunoColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            ZunoIcon(R.drawable.zuno_ic_t_volume, 14.dp, tint = ZunoColors.IconSecondary)
                        }
                        Text(
                            text = item.translation,
                            style = ZunoType.FootnoteRegular,
                            color = ZunoColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonAvatarVoiceBody(
    status: VoiceCallStatus,
    level: () -> Float,
    tutorLine: String?,
    userLine: String?,
    captionsOn: Boolean,
    isMuted: Boolean,
    isCameraOn: Boolean,
    isSpeakerOn: Boolean,
    learnerLevel: String,
    userName: String,
    userInitial: String,
    room: Room?,
    localVideoTrack: VideoTrack?,
    tutor: TutorAvatarProfile,
    avatarSpeechSink: AvatarSpeechSink?,
    lessonStage: LessonStageUi?,
    lessonTurns: List<LessonTurnUi>,
    pronunciationEval: PronunciationEvalUi?,
    rescuePayload: RescuePayloadUi?,
    pulseFirstWord: Boolean,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
    onToggleCaptions: () -> Unit,
    onLessonAction: (action: String, payload: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tutorSpeaking = status == VoiceCallStatus.Speaking
    val stageKind = lessonStage?.stage

    val displayTurns = remember(lessonTurns, lessonStage, tutorLine) {
        if (lessonTurns.isNotEmpty()) {
            lessonTurns
        } else {
            buildList {
                when (val card = lessonStage?.card) {
                    is LessonCardUi.TeachCard -> add(
                        LessonTurnUi(
                            id = "stage-teach",
                            isUser = false,
                            senderLabel = if (tutorSpeaking) "Sofía · Speaking" else "Sofía",
                            text = card.phrase,
                            translation = card.translation.takeIf { it.isNotBlank() },
                            actions = listOf("listen_normal", "listen_slow"),
                        ),
                    )
                    is LessonCardUi.ScaffoldCard -> add(
                        LessonTurnUi(
                            id = "stage-build",
                            isUser = false,
                            senderLabel = "Sofía",
                            text = card.prompt,
                            actions = listOf("hint", "reveal"),
                        ),
                    )
                    null -> {
                        val recall = lessonStage?.recallPrompt
                        if (stageKind == LessonStageKind.ActiveRecall && recall != null) {
                            add(
                                LessonTurnUi(
                                    id = "stage-recall",
                                    isUser = false,
                                    senderLabel = "Sofía",
                                    text = recall.cue,
                                    actions = listOf("first_word", "reveal"),
                                ),
                            )
                        } else if (!tutorLine.isNullOrBlank()) {
                            val roleLabel = lessonStage?.pill?.role?.takeIf { it.isNotBlank() }?.let { "$it · Sofía" } ?: "Sofía"
                            add(
                                LessonTurnUi(
                                    id = "stage-tutor",
                                    isUser = false,
                                    senderLabel = if (tutorSpeaking) "$roleLabel · Speaking" else roleLabel,
                                    text = tutorLine,
                                    actions = if (stageKind == LessonStageKind.Roleplay) {
                                        listOf("translate", "listen_slow")
                                    } else {
                                        listOf("listen_normal", "listen_slow")
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    val liveUserText = userLine?.trim()?.takeIf { it.isNotEmpty() }
    val lastUserBubble = displayTurns.lastOrNull { it.isUser }?.text?.trim('“', '”', '"', ' ')
    val showLiveUserBubble = liveUserText != null && liveUserText != lastUserBubble
    val awaitingUserTurn = displayTurns.lastOrNull()?.isUser != true
    val showPlaceholder = !showLiveUserBubble && awaitingUserTurn && (
        status == VoiceCallStatus.Listening ||
            status == VoiceCallStatus.Connecting ||
            status == VoiceCallStatus.Speaking
        )
    val listState = rememberLazyListState()
    val lastTurnText = displayTurns.lastOrNull()?.text.orEmpty()

    LaunchedEffect(captionsOn, displayTurns.size, lastTurnText, showLiveUserBubble, showPlaceholder, rescuePayload) {
        if (!captionsOn) return@LaunchedEffect
        val total = displayTurns.size + (if (showLiveUserBubble || showPlaceholder) 1 else 0) + (if (rescuePayload != null) 1 else 0)
        if (total > 0) {
            delay(50)
            listState.animateScrollToItem(total - 1)
        }
    }

    val sessionKindLabel = lessonStage?.pill?.role?.takeIf { it.isNotBlank() } ?: stringResource(R.string.session_kind_lesson)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = ZunoSpace.s4),
        ) {
            if (isCameraOn) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = ZunoSpace.s20, vertical = ZunoSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                ) {
                    AiVideoParticipantsRow(
                        tutor = tutor,
                        avatarSpeechSink = avatarSpeechSink,
                        tutorSpeaking = tutorSpeaking,
                        isMuted = isMuted,
                        audioLevel = level,
                        sessionKindLabel = sessionKindLabel,
                        learnerLevel = learnerLevel,
                        userName = userName,
                        userInitial = userInitial,
                        room = room,
                        localVideoTrack = localVideoTrack,
                        onInterrupt = onInterrupt,
                    )
                    if (captionsOn) {
                        LessonConversationAndCoach(
                            displayTurns = displayTurns,
                            listState = listState,
                            stageKind = stageKind,
                            tutorSpeaking = tutorSpeaking,
                            liveUserText = liveUserText,
                            showLiveUserBubble = showLiveUserBubble,
                            showPlaceholder = showPlaceholder,
                            lessonStage = lessonStage,
                            pronunciationEval = pronunciationEval,
                            rescuePayload = rescuePayload,
                            pulseFirstWord = pulseFirstWord,
                            onLessonAction = onLessonAction,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val portraitHeight = (maxHeight * 0.44f).coerceIn(190.dp, 296.dp)
                    val bottomScrimHeight by animateDpAsState(
                        if (captionsOn) 52.dp else 64.dp,
                        label = "lessonPortraitScrim",
                    )
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = if (captionsOn) Arrangement.spacedBy(ZunoSpace.s8) else Arrangement.Top,
                    ) {
                        PortraitStageCard(
                            tutor = tutor,
                            avatarSpeechSink = avatarSpeechSink,
                            tutorSpeaking = tutorSpeaking,
                            isCameraOn = false,
                            room = room,
                            localVideoTrack = localVideoTrack,
                            bottomScrimHeight = bottomScrimHeight,
                            pipWidth = if (captionsOn) 76.dp else 96.dp,
                            pipHeight = if (captionsOn) 104.dp else 132.dp,
                            onInterrupt = onInterrupt,
                            modifier = if (captionsOn) {
                                Modifier
                                    .fillMaxWidth()
                                    .height(portraitHeight)
                            } else {
                                Modifier.fillMaxSize()
                            },
                        )

                        if (captionsOn) {
                            LessonConversationAndCoach(
                                displayTurns = displayTurns,
                                listState = listState,
                                stageKind = stageKind,
                                tutorSpeaking = tutorSpeaking,
                                liveUserText = liveUserText,
                                showLiveUserBubble = showLiveUserBubble,
                                showPlaceholder = showPlaceholder,
                                lessonStage = lessonStage,
                                pronunciationEval = pronunciationEval,
                                rescuePayload = rescuePayload,
                                pulseFirstWord = pulseFirstWord,
                                onLessonAction = onLessonAction,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = ZunoSpace.s20),
                            )
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
            onToggleMute = onToggleMute,
            onToggleCamera = onToggleCamera,
            onEndCall = onEnd,
            onToggleSpeaker = onToggleSpeaker,
            onToggleCaptions = onToggleCaptions,
        )
    }
}

@Composable
private fun LessonConversationAndCoach(
    displayTurns: List<LessonTurnUi>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    stageKind: LessonStageKind?,
    tutorSpeaking: Boolean,
    liveUserText: String?,
    showLiveUserBubble: Boolean,
    showPlaceholder: Boolean,
    lessonStage: LessonStageUi?,
    pronunciationEval: PronunciationEvalUi?,
    rescuePayload: RescuePayloadUi?,
    pulseFirstWord: Boolean,
    onLessonAction: (action: String, payload: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = ZunoSpace.s4),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            items(displayTurns, key = { it.id }) { turn ->
                if (!turn.isUser) {
                    val a1 = turn.actions.getOrNull(0)
                    val a2 = turn.actions.getOrNull(1)
                    val (l1, i1, cb1) = mapTurnAction(a1, turn.text, onLessonAction)
                    val (l2, i2, cb2) = mapTurnAction(a2, turn.text, onLessonAction)
                    ZunoAssistantBubble(
                        message = turn.text,
                        translation = if (stageKind == LessonStageKind.ActiveRecall) null else turn.translation,
                        senderLabel = turn.senderLabel,
                        onListen = cb1,
                        listenLabel = l1,
                        listenIcon = i1,
                        onExplain = cb2,
                        explainLabel = l2,
                        explainIcon = i2,
                    )
                } else {
                    ZunoUserBubble(
                        message = turn.text,
                        senderLabel = turn.senderLabel,
                    )
                }
            }

            if (rescuePayload != null && rescuePayload.text.isNotBlank() && rescuePayload.action != "translate") {
                item(key = "rescue_pill") {
                    ZunoFeedbackPill(text = rescuePayload.text)
                }
            }

            if (showLiveUserBubble && liveUserText != null) {
                item(key = "live_user_speech") {
                    val label = if (stageKind == LessonStageKind.Roleplay) {
                        val currentGoal = lessonStage?.roleplayGoals?.firstOrNull { it.current }
                        val total = lessonStage?.roleplayGoals?.size ?: 2
                        if (currentGoal != null) "You · Speaking (Goal ${currentGoal.index} of $total)" else stringResource(R.string.voice_you_speaking)
                    } else {
                        stringResource(R.string.voice_you_speaking)
                    }
                    ZunoUserBubble(
                        message = "“$liveUserText”",
                        senderLabel = label,
                    )
                }
            } else if (showPlaceholder) {
                item(key = "lesson_placeholder") {
                    val (senderLabel, placeholderText) = when {
                        stageKind == LessonStageKind.ActiveRecall ->
                            stringResource(R.string.lesson_you_spoken_recall) to stringResource(R.string.lesson_placeholder_recall)
                        stageKind == LessonStageKind.Roleplay && tutorSpeaking ->
                            "You · Next" to stringResource(R.string.voice_listening_turn_placeholder)
                        else ->
                            stringResource(R.string.voice_you_listening) to stringResource(R.string.lesson_placeholder_speak_or_ask)
                    }
                    ListeningTurnPlaceholderBubble(
                        senderLabel = senderLabel,
                        placeholderText = placeholderText,
                    )
                }
            }
        }

        if (lessonStage != null) {
            ZunoLessonCoachCard(
                lessonStage = lessonStage,
                pronunciationEval = pronunciationEval,
                rescuePayload = rescuePayload,
                pulseFirstWord = pulseFirstWord,
                onLessonAction = onLessonAction,
            )
        }
    }
}

@Composable
private fun mapTurnAction(
    action: String?,
    turnText: String,
    onLessonAction: (action: String, payload: String?) -> Unit,
): Triple<String, Int, (() -> Unit)?> = when (action) {
    "listen_normal" -> Triple(
        "Listen",
        R.drawable.zuno_ic_volume,
        { onLessonAction("listen_normal", turnText) },
    )
    "listen_slow", "slow" -> Triple(
        stringResource(R.string.voice_chip_speed_slow),
        R.drawable.zuno_ic_rotate_ccw_secondary,
        { onLessonAction("listen_slow", turnText) },
    )
    "hint" -> Triple(
        stringResource(R.string.lesson_bubble_hint),
        R.drawable.zuno_ic_sparkles,
        { onLessonAction("hint", null) },
    )
    "first_word" -> Triple(
        stringResource(R.string.lesson_bubble_first_word),
        R.drawable.zuno_ic_sparkles,
        { onLessonAction("first_word", null) },
    )
    "reveal" -> Triple(
        stringResource(R.string.lesson_bubble_reveal),
        R.drawable.zuno_ic_t_book_open,
        { onLessonAction("reveal", null) },
    )
    "translate" -> Triple(
        stringResource(R.string.voice_chip_translate),
        R.drawable.zuno_ic_languages,
        { onLessonAction("translate", null) },
    )
    else -> Triple("", R.drawable.zuno_ic_volume, null)
}

@Composable
private fun rememberElapsed(startedAtMillis: Long?): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1000 - (now - startedAtMillis) % 1000)
        }
    }
    val seconds = startedAtMillis?.let { ((now - it) / 1000).coerceAtLeast(0) } ?: 0
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun StatusText(status: VoiceCallStatus) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
        Text("ZunoAI", style = ZunoType.Headline, color = ZunoColors.TextPrimary)
        Text(
            stringResource(
                when (status) {
                    VoiceCallStatus.Connecting -> R.string.voice_connecting
                    VoiceCallStatus.Listening -> R.string.voice_listening
                    VoiceCallStatus.Muted -> R.string.voice_muted
                    VoiceCallStatus.Thinking -> R.string.voice_thinking
                    VoiceCallStatus.Speaking -> R.string.voice_tap_to_interrupt
                },
            ),
            style = ZunoType.CaptionRegular,
            color = ZunoColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The tutor's line: the sentence before dimmed, the current one large. */
@Composable
private fun Captions(line: String) {
    val sentences = line.trim().split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
    val current = sentences.lastOrNull().orEmpty()
    val previous = sentences.dropLast(1).lastOrNull()
    Column(
        modifier = Modifier.width(338.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        previous?.let {
            Text(it, style = ZunoType.BodyMedium, color = ZunoColors.TextTertiary, textAlign = TextAlign.Center, maxLines = 2)
        }
        Text(current, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 4)
    }
}

@Composable
private fun CallControls(
    isMuted: Boolean,
    captionsOn: Boolean,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onToggleCaptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallButton(
            icon = if (isMuted) R.drawable.zuno_ic_mic_off else R.drawable.zuno_ic_mic_secondary,
            active = isMuted,
            onClick = onToggleMute,
            contentDescription = stringResource(if (isMuted) R.string.voice_unmute else R.string.voice_mute),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ZunoButton(
                stringResource(R.string.voice_end_call),
                onEnd,
                variant = ZunoButtonVariant.Secondary,
                size = ZunoButtonSize.Medium,
            )
        }
        CallButton(
            icon = R.drawable.zuno_ic_captions,
            active = captionsOn,
            activeSubtle = true,
            onClick = onToggleCaptions,
            contentDescription = stringResource(if (captionsOn) R.string.voice_hide_captions else R.string.voice_show_captions),
        )
    }
}

/** 44dp round control; Active turns white for mute or strong surface for captions (`Home · 7`). */
@Composable
private fun CallButton(
    @DrawableRes icon: Int,
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    activeSubtle: Boolean = false,
) {
    val bg = when {
        active && activeSubtle -> ZunoColors.SurfaceStronger
        active -> ZunoColors.SurfaceInverse
        else -> ZunoColors.SurfaceDefault
    }
    val tint = if (active && !activeSubtle) ZunoColors.IconOnInverse else ZunoColors.IconPrimary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (active && activeSubtle) Modifier.border(1.dp, ZunoColors.BorderDefault, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, 24.dp, tint = tint)
    }
}

@Preview(backgroundColor = 0xFF171717, showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun VoiceModeScreenNoCaptionsPreview() {
    ZunoTheme {
        VoiceModeScreen(
            status = VoiceCallStatus.Speaking,
            level = { 0.5f },
            startedAtMillis = System.currentTimeMillis() - 42_000,
            tutorLine = "Imagina que estás en un café en Madrid. ¿Qué vas a pedir? ¿Con leche o solo?",
            isMuted = false,
            menu = emptyList(),
            initialCaptionsOn = false,
            onEnd = {},
            onInterrupt = {},
            onToggleMute = {},
        )
    }
}

@Preview(backgroundColor = 0xFF171717, showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun VoiceModeScreenCaptionsPreview() {
    ZunoTheme {
        VoiceModeScreen(
            status = VoiceCallStatus.Speaking,
            level = { 0.5f },
            startedAtMillis = System.currentTimeMillis() - 42_000,
            tutorLine = "Imagina que estás en un café en Madrid. ¿Qué vas a pedir? ¿Con leche o solo?",
            isMuted = false,
            menu = emptyList(),
            initialCaptionsOn = true,
            onEnd = {},
            onInterrupt = {},
            onToggleMute = {},
        )
    }
}
