package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.LessonCardUi
import com.zuno.ai.data.LessonStageKind
import com.zuno.ai.data.LessonStageUi
import com.zuno.ai.data.PronunciationEvalUi
import com.zuno.ai.data.RescuePayloadUi
import com.zuno.ai.ui.components.VoiceOrbState
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoCompactTitleTopBar
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoLessonRescueBar
import com.zuno.ai.ui.components.ZunoListeningIndicator
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoOverflowMenu
import com.zuno.ai.ui.components.ZunoPronunciationFeedback
import com.zuno.ai.ui.components.ZunoRecallPrompt
import com.zuno.ai.ui.components.ZunoRoleplayPill
import com.zuno.ai.ui.components.ZunoScaffoldCard
import com.zuno.ai.ui.components.ZunoSpokenAssembly
import com.zuno.ai.ui.components.ZunoTeachCard
import com.zuno.ai.ui.components.ZunoVoiceOrb
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
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
    isMuted: Boolean,
    menu: List<ZunoMenuAction>,
    onEnd: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    isLessonMode: Boolean = false,
    lessonPosition: Int = 1,
    learnerLevel: String = "B1",
    lessonStage: LessonStageUi? = null,
    pronunciationEval: PronunciationEvalUi? = null,
    rescuePayload: RescuePayloadUi? = null,
    onLessonAction: (action: String, payload: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val defaultCaptions = isLessonMode && learnerLevel in listOf("A1", "A2", "B1")
    var captionsOn by rememberSaveable(isLessonMode) { mutableStateOf(defaultCaptions) }
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
    val topBarTitle = if (isLessonMode) elapsed else stringResource(R.string.voice_live_title, elapsed)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
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
            LessonVoiceBody(
                status = status,
                level = level,
                tutorLine = tutorLine,
                userLine = userLine,
                captionsOn = captionsOn,
                isMuted = isMuted,
                lessonStage = lessonStage,
                pronunciationEval = pronunciationEval,
                rescuePayload = rescuePayload,
                pulseFirstWord = pulseFirstWord,
                onInterrupt = onInterrupt,
                onToggleMute = onToggleMute,
                onEnd = handleExitRequest,
                onToggleCaptions = { captionsOn = !captionsOn },
                onLessonAction = onLessonAction,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = maxHeight * OrbTopFraction),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(OrbSphereSize)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onInterrupt)
                            .semantics { contentDescription = "Interrupt the tutor" },
                        contentAlignment = Alignment.Center,
                    ) {
                        ZunoVoiceOrb(
                            state = status.orb,
                            level = level,
                            size = OrbCanvasSize,
                            modifier = Modifier.requiredSize(OrbCanvasSize),
                        )
                    }
                    Spacer(Modifier.height(ZunoSpace.s40))
                    val line = tutorLine?.takeIf { captionsOn && it.isNotBlank() }
                    AnimatedContent(
                        targetState = line,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        contentKey = { it == null },
                        label = "voiceCaption",
                    ) { caption ->
                        if (caption == null) StatusText(status) else Captions(caption)
                    }
                }
                CallControls(
                    isMuted = isMuted,
                    captionsOn = captionsOn,
                    onToggleMute = onToggleMute,
                    onEnd = handleExitRequest,
                    onToggleCaptions = { captionsOn = !captionsOn },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
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
private fun LessonVoiceBody(
    status: VoiceCallStatus,
    level: () -> Float,
    tutorLine: String?,
    userLine: String?,
    captionsOn: Boolean,
    isMuted: Boolean,
    lessonStage: LessonStageUi?,
    pronunciationEval: PronunciationEvalUi?,
    rescuePayload: RescuePayloadUi?,
    pulseFirstWord: Boolean,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onToggleCaptions: () -> Unit,
    onLessonAction: (action: String, payload: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stageKind = lessonStage?.stage
    val isCompactOrb = stageKind in listOf(
        LessonStageKind.TeachModel,
        LessonStageKind.ShadowDrill,
        LessonStageKind.BuildingBlocks,
    )
    val sphereSize by animateDpAsState(if (isCompactOrb) CompactOrbSphereSize else OrbSphereSize, label = "orbSphere")
    val canvasSize by animateDpAsState(if (isCompactOrb) CompactOrbCanvasSize else OrbCanvasSize, label = "orbCanvas")
    val orbTopSpace by animateDpAsState(if (isCompactOrb) ZunoSpace.s20 else 56.dp, label = "orbTopSpace")
    val belowOrbSpace by animateDpAsState(if (isCompactOrb) ZunoSpace.s24 else ZunoSpace.s32, label = "belowOrbSpace")

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 112.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(ZunoSpace.s8))
            if (lessonStage != null) {
                ZunoRoleplayPill(
                    pill = lessonStage.pill,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s24),
                )
            } else {
                Spacer(Modifier.height(30.dp))
            }

            Spacer(Modifier.height(orbTopSpace))

            Box(
                modifier = Modifier
                    .size(sphereSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onInterrupt)
                    .semantics { contentDescription = "Interrupt the tutor" },
                contentAlignment = Alignment.Center,
            ) {
                ZunoVoiceOrb(
                    state = status.orb,
                    level = level,
                    size = canvasSize,
                    modifier = Modifier.requiredSize(canvasSize),
                )
            }

            Spacer(Modifier.height(belowOrbSpace))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s24),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (stageKind) {
                    null -> StatusText(status)

                    LessonStageKind.TeachModel -> {
                        val card = lessonStage.card as? LessonCardUi.TeachCard
                        if (card != null) {
                            if (card.setupPrompt.isNotBlank()) {
                                Text(
                                    text = card.setupPrompt,
                                    style = ZunoType.BodyMedium,
                                    color = ZunoColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = ZunoSpace.s14),
                                )
                            }
                            ZunoTeachCard(
                                card = card,
                                highlightFocusWord = false,
                                onListenNormal = { onLessonAction("listen_normal", card.phrase) },
                                onListenSlow = { onLessonAction("listen_slow", card.phrase) },
                            )
                        }
                    }

                    LessonStageKind.ShadowDrill -> {
                        val card = lessonStage.card as? LessonCardUi.TeachCard
                        if (card != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12)) {
                                ZunoTeachCard(
                                    card = card,
                                    highlightFocusWord = true,
                                    onListenNormal = { onLessonAction("listen_normal", card.phrase) },
                                    onListenSlow = { onLessonAction("listen_slow", card.phrase) },
                                )
                                if (pronunciationEval != null) {
                                    ZunoPronunciationFeedback(
                                        eval = pronunciationEval,
                                        chunks = card.chunks,
                                        onPlayChunk = { chunk -> onLessonAction("play_chunk", chunk) },
                                    )
                                } else {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        ZunoListeningIndicator(text = "Repeat the phrase aloud")
                                    }
                                }
                            }
                        }
                    }

                    LessonStageKind.BuildingBlocks -> {
                        val card = lessonStage.card as? LessonCardUi.ScaffoldCard
                        if (card != null) {
                            val revealed = rescuePayload?.takeIf { it.action == "reveal" }?.text
                            val hintText = rescuePayload?.takeIf { it.action == "hint" }?.text
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
                            ) {
                                ZunoScaffoldCard(card = card, revealedAnswer = revealed)
                                ZunoSpokenAssembly(liveTranscript = userLine, hintBanner = hintText)
                            }
                        }
                    }

                    LessonStageKind.ActiveRecall -> {
                        // Crucial 03d rule: suppress target captions until user reveals or finishes
                        val prompt = lessonStage.recallPrompt
                        if (prompt != null) {
                            ZunoRecallPrompt(
                                prompt = prompt,
                                rescueBanner = rescuePayload?.text,
                                liveUserText = userLine,
                            )
                        }
                    }

                    LessonStageKind.Roleplay -> {
                        RoleplayTurnCaptions(
                            status = status,
                            captionsOn = captionsOn,
                            tutorLine = tutorLine,
                            userLine = userLine,
                            rescueOverlayText = rescuePayload?.text,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (lessonStage != null && lessonStage.rescueChips.isNotEmpty()) {
                ZunoLessonRescueBar(
                    chips = lessonStage.rescueChips,
                    pulseFirstWord = pulseFirstWord,
                    onAction = { chip ->
                        val wireAction = if (chip == "slow") "listen_slow" else chip
                        onLessonAction(wireAction, null)
                    },
                    modifier = Modifier.padding(bottom = ZunoSpace.s12),
                )
            }
            CallControls(
                isMuted = isMuted,
                captionsOn = captionsOn,
                onToggleMute = onToggleMute,
                onEnd = onEnd,
                onToggleCaptions = onToggleCaptions,
            )
        }
    }
}

@Composable
private fun RoleplayTurnCaptions(
    status: VoiceCallStatus,
    captionsOn: Boolean,
    tutorLine: String?,
    userLine: String?,
    rescueOverlayText: String?,
) {
    val isLearnerTurn = status == VoiceCallStatus.Listening
    Column(
        modifier = Modifier.width(338.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
    ) {
        if (captionsOn) {
            if (isLearnerTurn && (!userLine.isNullOrBlank() || !tutorLine.isNullOrBlank())) {
                // 03f · Learner Turn: dimmed last tutor sentence -> bold live user speech
                val lastTutorSentence = tutorLine
                    ?.trim()
                    ?.split(Regex("(?<=[.!?…])\\s+"))
                    ?.lastOrNull { it.isNotBlank() }
                if (!lastTutorSentence.isNullOrBlank()) {
                    Text(
                        text = lastTutorSentence,
                        style = ZunoType.BodyMedium,
                        color = ZunoColors.TextTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                if (!userLine.isNullOrBlank()) {
                    Text(
                        text = userLine,
                        style = ZunoType.TitleLarge,
                        color = ZunoColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                }
            } else if (!tutorLine.isNullOrBlank()) {
                // 03e · Tutor Turn: 2-line rolling tutor captions
                Captions(tutorLine)
            }
        } else if (!isLearnerTurn) {
            StatusText(status)
        }

        if (!rescueOverlayText.isNullOrBlank()) {
            Text(
                text = rescueOverlayText,
                style = ZunoType.BodyMediumStrong,
                color = ZunoColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        if (isLearnerTurn) {
            ZunoListeningIndicator(text = "Listening · Take your time")
        }
    }
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
            onClick = onToggleCaptions,
            contentDescription = stringResource(if (captionsOn) R.string.voice_hide_captions else R.string.voice_show_captions),
        )
    }
}

/** 44dp round control; Active turns it white (captions on, muted). */
@Composable
private fun CallButton(@DrawableRes icon: Int, active: Boolean, onClick: () -> Unit, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) ZunoColors.SurfaceInverse else ZunoColors.SurfaceDefault)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, 24.dp, tint = if (active) ZunoColors.IconOnInverse else ZunoColors.IconPrimary)
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun VoiceModeScreenPreview() {
    VoiceModeScreen(
        status = VoiceCallStatus.Speaking,
        level = { 0.5f },
        startedAtMillis = System.currentTimeMillis() - 42_000,
        tutorLine = "¡Perfecto! Imagina que estás en un café en Madrid. ¿Qué vas a pedir?",
        isMuted = false,
        menu = emptyList(),
        onEnd = {},
        onInterrupt = {},
        onToggleMute = {},
    )
}
