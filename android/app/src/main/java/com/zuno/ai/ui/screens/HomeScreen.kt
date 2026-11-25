package com.zuno.ai.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.RecentSession
import com.zuno.ai.data.UiSoundEffects
import kotlinx.coroutines.delay
import com.zuno.ai.ui.components.AgentGlowState
import com.zuno.ai.ui.components.ContinueSessionUi
import com.zuno.ai.ui.components.GlowTheme
import com.zuno.ai.ui.components.ZunoAgentGlow
import com.zuno.ai.ui.components.ZunoSidebarHomeTopBar
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.ui.theme.ZunoType
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Greeting copy for Home header, Horizon prompt, and composer placeholder. */
data class HomeCopy(
    val shortGreeting: String,
    val languageName: String,
    val placeholder: String,
    val fullGreeting: String = shortGreeting,
    val speechTag: String = "es-419",
) {
    companion object {
        fun forLanguage(language: LearningLanguage, firstName: String, accent: String? = null): HomeCopy = HomeCopy(
            shortGreeting = language.shortGreeting(firstName),
            languageName = language.englishName,
            placeholder = language.placeholder,
            fullGreeting = language.greeting(firstName),
            speechTag = language.speechTag(accent),
        )
    }
}

/**
 * `02 · Home` (`7365:20293` Home · 1 · Active — Continue with Zuno & `3077:14390` Home · 5 · Sidebar Drawer).
 */
@Composable
fun HomeScreen(
    copy: HomeCopy,
    userName: String,
    userInitial: String,
    userImage: String?,
    streakDays: Int,
    dailyGoalMinutesDone: Int,
    dailyGoalTargetMinutes: Int,
    level: String,
    showUpgrade: Boolean,
    continueSession: ContinueSessionUi?,
    recentSessions: List<RecentSession> = emptyList(),
    vocabDueCount: Int = 0,
    nextRoleplayTitle: String? = null,
    unreadChatsCount: Int = 0,
    activeLiveRoomsCount: Int = 0,
    partnersOnlineCount: Int = 0,
    scheduledTimeLabel: String? = null,
    leagueRank: Int? = null,
    micGranted: () -> Boolean = { true },
    onRequestMicForTalk: (onGranted: () -> Unit) -> Unit = { it() },
    onStreakClick: () -> Unit,
    onLanguageClick: () -> Unit = {},
    onAvatarClick: () -> Unit,
    onNotificationsClick: () -> Unit = onAvatarClick,
    onDailyGoalContinue: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenPartners: () -> Unit = onOpenChats,
    onOpenLiveRooms: () -> Unit = onOpenChats,
    onOpenVocabulary: () -> Unit = {},
    onOpenProgress: () -> Unit = onStreakClick,
    onOpenRoleplays: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartHomeVoice: () -> Unit,
    onStartFreeTalk: () -> Unit,
    onStartFreeTalkWithText: (String) -> Unit = { onStartFreeTalk() },
    onNewChat: () -> Unit = onStartFreeTalk,
    onOpenConversation: (String) -> Unit,
    onOpenRecentSession: (RecentSession) -> Unit = { onOpenConversation(it.conversationId ?: it.id) },
    onUpgrade: () -> Unit,
    onComingSoon: (String) -> Unit = {},
    ongoingCallBanner: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialDrawerOpen: Boolean = false,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(if (initialDrawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val closeAndRun: (() -> Unit) -> Unit = { action ->
        scope.launch {
            drawerState.close()
            action()
        }
    }

    // Hold-the-glow speech capture + tap-to-talk state
    var holdState by remember { mutableStateOf(AgentGlowState.Idle) }
    var voiceLevel by remember { mutableFloatStateOf(0f) }
    var liveTranscript by remember { mutableStateOf("") }
    var recognizerActive by remember { mutableStateOf(false) }

    val latestCopy by rememberUpdatedState(copy)
    val latestOnStartVoice by rememberUpdatedState(onStartHomeVoice)
    val latestOnStartFreeTalkWithText by rememberUpdatedState(onStartFreeTalkWithText)
    val latestMicGranted by rememberUpdatedState(micGranted)
    val latestRequestMic by rememberUpdatedState(onRequestMicForTalk)

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    LaunchedEffect(Unit) {
        UiSoundEffects.preload(context)
    }

    LaunchedEffect(holdState) {
        if (holdState == AgentGlowState.Thinking) {
            delay(2500L)
            holdState = AgentGlowState.Idle
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    fun startSpeechCapture() {
        val sr = speechRecognizer ?: if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull().also { speechRecognizer = it }
        } else {
            null
        } ?: return
        liveTranscript = ""
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                voiceLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { voiceLevel = 0f }
            override fun onError(error: Int) {
                recognizerActive = false
                voiceLevel = 0f
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!spoken.isNullOrBlank()) liveTranscript = spoken
                recognizerActive = false
                voiceLevel = 0f
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!partial.isNullOrBlank()) liveTranscript = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, latestCopy.speechTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching {
            sr.startListening(intent)
            recognizerActive = true
        }
    }

    fun finishSpeechCapture(cancelled: Boolean, durationMs: Long) {
        val sr = speechRecognizer
        if (cancelled) {
            runCatching { sr?.cancel() }
            recognizerActive = false
            liveTranscript = ""
            voiceLevel = 0f
            holdState = AgentGlowState.Idle
            return
        }
        if (recognizerActive) {
            runCatching { sr?.stopListening() }
            recognizerActive = false
        }
        voiceLevel = 0f
        holdState = AgentGlowState.Thinking
        UiSoundEffects.playHorizonSweep(context)
        val captured = liveTranscript.trim()
        liveTranscript = ""
        if (durationMs >= 500L && captured.isNotEmpty()) {
            latestOnStartFreeTalkWithText(captured)
        } else {
            latestOnStartVoice()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = ZunoColors.BgScrim,
        drawerContent = {
            AppDrawer(
                userName = userName.ifBlank { userInitial },
                userDetail = "${copy.languageName} · $level · ${streakDays}d",
                userImage = userImage,
                showUpgrade = showUpgrade,
                activeDestination = DrawerDestination.FreeTalk,
                sessions = recentSessions,
                selectedSessionId = continueSession?.conversationId,
                vocabDueCount = vocabDueCount,
                scheduledTimeLabel = scheduledTimeLabel,
                unreadChatsCount = unreadChatsCount,
                activeLiveRoomsCount = activeLiveRoomsCount,
                partnersOnlineCount = partnersOnlineCount,
                leagueRank = leagueRank,
                onProfile = { closeAndRun(onAvatarClick) },
                onNotifications = { closeAndRun(onNotificationsClick) },
                onDestination = { destination ->
                    closeAndRun {
                        when (destination) {
                            DrawerDestination.FreeTalk -> Unit
                            DrawerDestination.Topics -> onOpenTopics()
                            DrawerDestination.Roleplays -> onOpenRoleplays()
                            DrawerDestination.Vocabulary -> onOpenVocabulary()
                            DrawerDestination.Scheduled -> onOpenScheduled()
                            DrawerDestination.Chats -> onOpenChats()
                            DrawerDestination.LiveRooms -> onOpenLiveRooms()
                            DrawerDestination.Community -> onOpenPartners()
                            DrawerDestination.Progress -> onOpenProgress()
                        }
                    }
                },
                onSearchSessions = { closeAndRun(onOpenChats) },
                onSession = { session -> closeAndRun { onOpenRecentSession(session) } },
                onUpgrade = { closeAndRun(onUpgrade) },
                onSettings = { closeAndRun(onOpenSettings) },
                onNewChat = { closeAndRun(onNewChat) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ZunoColors.Gray2)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ZunoSidebarHomeTopBar(
                streakDays = streakDays,
                languageLabel = "${copy.languageName} · $level",
                onMenuClick = { scope.launch { drawerState.open() } },
                onStreakClick = onStreakClick,
                onLanguageClick = onLanguageClick,
            )

            ongoingCallBanner?.let { banner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s6),
                ) {
                    banner()
                }
            }

            Spacer(Modifier.height(if (ongoingCallBanner != null) ZunoSpace.s12 else 70.dp))

            // Agent Horizon (`7297:11494` in `7365:20293`)
            val holdLabel = stringResource(R.string.home_hold_glow_to_talk)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s8)
                    .semantics { contentDescription = holdLabel }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!latestMicGranted()) {
                                //Wait for release, then show mic permission dialog (`2044:2943`)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.all { !it.pressed }) break
                                }
                                latestRequestMic {
                                    holdState = AgentGlowState.Thinking
                                    UiSoundEffects.playHorizonSweep(context)
                                    latestOnStartVoice()
                                }
                                return@awaitEachGesture
                            }

                            val startTime = System.currentTimeMillis()
                            holdState = AgentGlowState.Listening
                            startSpeechCapture()
                            var cancelled = false
                            val cancelThresholdPx = 110.dp.toPx()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) break
                                val dist = hypot(
                                    (change.position.x - down.position.x).toDouble(),
                                    (change.position.y - down.position.y).toDouble(),
                                ).toFloat()
                                val shouldCancel = dist > cancelThresholdPx && change.position.y < down.position.y
                                if (shouldCancel != cancelled) {
                                    cancelled = shouldCancel
                                    holdState = if (cancelled) AgentGlowState.Cancelling else AgentGlowState.Listening
                                }
                            }
                            finishSpeechCapture(
                                cancelled = cancelled,
                                durationMs = System.currentTimeMillis() - startTime,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                ZunoAgentGlow(
                    state = holdState,
                    audioLevel = { voiceLevel },
                    theme = GlowTheme.Mono,
                    canvasColor = ZunoColors.Gray2,
                    shape = RoundedCornerShape(40.dp),
                    modifier = Modifier.fillMaxSize(),
                )

                val captionText = when (holdState) {
                    AgentGlowState.Listening -> liveTranscript.ifBlank { stringResource(R.string.home_listening_release) }
                    AgentGlowState.Cancelling -> stringResource(R.string.home_release_to_cancel)
                    else -> holdLabel
                }
                val captionColor by animateColorAsState(
                    targetValue = when (holdState) {
                        AgentGlowState.Listening -> ZunoColors.TextPrimary
                        AgentGlowState.Cancelling -> ZunoColors.TextTertiary
                        else -> ZunoColors.TextSecondary
                    },
                    animationSpec = tween(durationMillis = 360),
                    label = "homePromptCaptionColor",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZunoSpace.s24)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedContent(
                        targetState = captionText,
                        transitionSpec = {
                            fadeIn(tween(260)) togetherWith fadeOut(tween(220))
                        },
                        label = "homePromptCaption",
                    ) { labelText ->
                        Text(
                            text = labelText,
                            style = ZunoType.FootnoteStrong,
                            color = captionColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                    Text(
                        text = copy.fullGreeting,
                        style = ZunoType.Display,
                        color = ZunoColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = ZunoSpace.s12, bottom = ZunoSpace.s16)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onStartFreeTalk)
                    .padding(horizontal = ZunoSpace.s20, vertical = ZunoSpace.s12),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_type_instead),
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextSecondary,
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun HomeScreenActivePreview() {
    ZunoTheme {
        HomeScreen(
            copy = HomeCopy.forLanguage(LearningLanguages.Spanish, "Noah"),
            userName = "Noah Carter",
            userInitial = "N",
            userImage = null,
            streakDays = 22,
            dailyGoalMinutesDone = 8,
            dailyGoalTargetMinutes = 15,
            level = "B1",
            showUpgrade = true,
            continueSession = ContinueSessionUi(
                conversationId = "conv-1",
                title = "At the café",
                subtitle = "Spanish B1 · 2h ago",
            ),
            onStreakClick = {},
            onAvatarClick = {},
            onDailyGoalContinue = {},
            onOpenTopics = {},
            onOpenChats = {},
            onOpenRoleplays = {},
            onOpenScheduled = {},
            onOpenSettings = {},
            onStartHomeVoice = {},
            onStartFreeTalk = {},
            onOpenConversation = {},
            onUpgrade = {},
            onComingSoon = {},
        )
    }
}
