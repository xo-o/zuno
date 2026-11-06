package com.talkie.ai.ui.zuno

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkie.ai.auth.AuthState
import com.talkie.ai.auth.AuthViewModel
import com.talkie.ai.model.AgentState
import com.talkie.ai.model.Speaker
import com.talkie.ai.ui.zuno.screens.AppDrawer
import com.talkie.ai.ui.zuno.screens.AuthMode
import com.talkie.ai.ui.zuno.screens.AuthScreen
import com.talkie.ai.ui.zuno.screens.ChatScreen
import com.talkie.ai.ui.zuno.screens.HomeScreen
import com.talkie.ai.ui.zuno.screens.OnboardingScreen
import com.talkie.ai.ui.zuno.screens.SplashScreen
import com.talkie.ai.ui.zuno.screens.TopicScreen
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.viewmodel.TalkieViewModel
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface ZunoRoute {
    data object Splash : ZunoRoute
    data object Onboarding : ZunoRoute
    data class Auth(val mode: AuthMode) : ZunoRoute
    data object Home : ZunoRoute
    data object Topic : ZunoRoute
    data class Chat(val title: String) : ZunoRoute

    /** Routes that sit under the side drawer. */
    val hasDrawer: Boolean get() = this is Home || this is Topic || this is Chat
}

private fun ZunoRoute.encode(): String = when (this) {
    ZunoRoute.Splash -> "splash"
    ZunoRoute.Onboarding -> "onboarding"
    is ZunoRoute.Auth -> "auth:${mode.name}"
    ZunoRoute.Home -> "home"
    ZunoRoute.Topic -> "topic"
    is ZunoRoute.Chat -> "chat:$title"
}

private fun decodeRoute(value: String): ZunoRoute = when {
    value == "onboarding" -> ZunoRoute.Onboarding
    value.startsWith("auth:") -> ZunoRoute.Auth(AuthMode.valueOf(value.removePrefix("auth:")))
    value == "home" -> ZunoRoute.Home
    value == "topic" -> ZunoRoute.Topic
    value.startsWith("chat:") -> ZunoRoute.Chat(value.removePrefix("chat:"))
    else -> ZunoRoute.Splash
}

private val BackStackSaver = listSaver<SnapshotStateList<ZunoRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<ZunoRoute>().apply { addAll(saved.map(::decodeRoute)) } },
)

private const val FREE_TALK_TITLE = "Free Talk"
private const val ROLEPLAY_TITLE = "Ordering at a café"
private const val NEW_SESSION_TITLE = "New session"

/**
 * Root of the Zuno UI: navigation between the polished screens, the side drawer,
 * and the bridge to the LiveKit voice session in [TalkieViewModel].
 */
@Composable
fun ZunoApp(
    viewModel: TalkieViewModel,
    authViewModel: AuthViewModel,
    runWithVoicePermissions: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<ZunoRoute>(ZunoRoute.Splash) }
    val route = backStack.last()

    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val authForm by authViewModel.form.collectAsStateWithLifecycle()

    fun navigate(to: ZunoRoute, clearStack: Boolean = false) {
        if (clearStack) backStack.clear()
        if (backStack.lastOrNull() != to) backStack.add(to)
    }

    fun replace(to: ZunoRoute) {
        backStack.removeAt(backStack.lastIndex)
        backStack.add(to)
    }

    fun comingSoon(feature: String) {
        Toast.makeText(context, "$feature — coming soon", Toast.LENGTH_SHORT).show()
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    // Conversations keyed by session title.
    val conversations = remember { mutableStateMapOf<String, List<ChatTurn>>() }
    fun turnsFor(title: String): List<ChatTurn> = conversations[title] ?: when (title) {
        FREE_TALK_TITLE, NEW_SESSION_TITLE -> emptyList()
        else -> ZunoSampleData.cafeConversation
    }

    // Voice session state.
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var voiceChatTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceMessageStart by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (voiceChatTitle != null && !message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun startVoice(title: String) {
        runWithVoicePermissions {
            if (agentState == AgentState.DISCONNECTED) {
                viewModel.connect(authViewModel.sessionToken, onSessionExpired = authViewModel::signOut)
            }
            voiceMessageStart = viewModel.messages.value.size
            voiceChatTitle = title
            navigate(ZunoRoute.Chat(title))
        }
    }

    fun endVoice(commit: Boolean = true) {
        val title = voiceChatTitle ?: return
        if (commit) {
            val spoken = viewModel.messages.value.drop(voiceMessageStart).filter { it.isFinal }.map { it.toTurn() }
            if (spoken.isNotEmpty()) conversations[title] = turnsFor(title) + spoken
        }
        viewModel.disconnect()
        voiceChatTitle = null
    }

    val tts = rememberSpanishTts()

    // Signing in (magic link, Google) jumps to Home; losing the session returns to Onboarding.
    LaunchedEffect(authState) {
        val current = backStack.last()
        when (authState) {
            is AuthState.SignedIn ->
                if (current is ZunoRoute.Auth || current == ZunoRoute.Onboarding) navigate(ZunoRoute.Home, clearStack = true)
            AuthState.SignedOut ->
                if (current.hasDrawer) {
                    if (voiceChatTitle != null) endVoice(commit = false)
                    navigate(ZunoRoute.Onboarding, clearStack = true)
                }
        }
    }

    // A magic link can fail while the user isn't on the auth screen (e.g. an expired link opened from email).
    LaunchedEffect(authForm.error) {
        val error = authForm.error
        if (error != null && backStack.last() !is ZunoRoute.Auth) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            authViewModel.clearError()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && backStack.size > 1) {
        if (route is ZunoRoute.Chat && voiceChatTitle == route.title) endVoice()
        backStack.removeAt(backStack.lastIndex)
    }

    val selectedSessionTitle = when (route) {
        ZunoRoute.Topic -> ZunoSampleData.busStopTopic.title
        is ZunoRoute.Chat -> route.title
        else -> null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = route.hasDrawer,
        scrimColor = ZunoColors.BgScrim,
        modifier = modifier,
        drawerContent = {
            AppDrawer(
                userName = ZunoSampleData.userName,
                userDetail = ZunoSampleData.learningDetail,
                sessions = ZunoSampleData.recentSessions,
                selectedSessionTitle = selectedSessionTitle,
                onProfile = { comingSoon("Profile") },
                onNotifications = { comingSoon("Notifications") },
                onDestination = { comingSoon(it.label) },
                onSearchSessions = { comingSoon("Search") },
                onSession = { session ->
                    closeDrawerThen {
                        if (voiceChatTitle != null) endVoice()
                        when (session.kind) {
                            SessionKind.Topic -> navigate(ZunoRoute.Topic)
                            else -> navigate(ZunoRoute.Chat(session.title))
                        }
                    }
                },
                onUpgrade = { comingSoon("Zuno Pro") },
                onSettings = { comingSoon("Settings") },
                onNewSession = {
                    closeDrawerThen {
                        if (voiceChatTitle != null) endVoice()
                        conversations.remove(NEW_SESSION_TITLE)
                        navigate(ZunoRoute.Chat(NEW_SESSION_TITLE))
                    }
                },
            )
        },
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it.encode() },
            label = "zunoRoute",
        ) { screen ->
            val screenModifier = Modifier.fillMaxSize()
            when (screen) {
                ZunoRoute.Splash -> SplashScreen(
                    onFinished = {
                        replace(if (authViewModel.authState.value is AuthState.SignedIn) ZunoRoute.Home else ZunoRoute.Onboarding)
                    },
                    modifier = screenModifier,
                )

                ZunoRoute.Onboarding -> OnboardingScreen(
                    onGetStarted = { navigate(ZunoRoute.Auth(AuthMode.SignIn)) },
                    onOpenTerms = { comingSoon("Terms") },
                    onOpenPrivacy = { comingSoon("Privacy Policy") },
                    modifier = screenModifier,
                )

                is ZunoRoute.Auth -> AuthScreen(
                    mode = screen.mode,
                    onBack = {
                        authViewModel.clearError()
                        backStack.removeAt(backStack.lastIndex)
                    },
                    onSubmit = { email -> authViewModel.sendMagicLink(email) },
                    onContinueWithGoogle = { authViewModel.signInWithGoogle(context) },
                    onSwitchMode = {
                        authViewModel.clearError()
                        val other = if (screen.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn
                        replace(ZunoRoute.Auth(other))
                    },
                    isBusy = authForm.isBusy,
                    linkSentTo = authForm.linkSentTo,
                    errorMessage = authForm.error,
                    onUseDifferentEmail = { authViewModel.clearMagicLinkSent() },
                    modifier = screenModifier,
                )

                ZunoRoute.Home -> HomeScreen(
                    firstName = ZunoSampleData.userFirstName,
                    languageLabel = "Spanish B1",
                    onOpenDrawer = { openDrawer() },
                    onPersonalize = { comingSoon("Personalize") },
                    onTalk = { startVoice(FREE_TALK_TITLE) },
                    onLessons = { navigate(ZunoRoute.Topic) },
                    onRoleplay = { navigate(ZunoRoute.Chat(ROLEPLAY_TITLE)) },
                    modifier = screenModifier,
                )

                ZunoRoute.Topic -> {
                    val topic = ZunoSampleData.busStopTopic
                    TopicScreen(
                        topic = topic,
                        onOpenDrawer = { openDrawer() },
                        onMore = { comingSoon("Topic options") },
                        onContinue = {
                            topic.currentLesson?.let { navigate(ZunoRoute.Chat(it.title)) }
                        },
                        onLesson = { navigate(ZunoRoute.Chat(it.title)) },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.Chat -> {
                    val inVoice = voiceChatTitle == screen.title
                    val baseTurns = turnsFor(screen.title)
                    val liveMessages = if (inVoice) messages.drop(voiceMessageStart) else emptyList()
                    ChatScreen(
                        title = screen.title,
                        turns = baseTurns + liveMessages.map { it.toTurn() },
                        voiceMode = inVoice,
                        isMuted = isMuted,
                        liveCaption = liveMessages.lastOrNull { it.speaker == Speaker.AGENT }?.text,
                        onOpenDrawer = { openDrawer() },
                        onMore = { comingSoon("Conversation options") },
                        onSend = { text -> conversations[screen.title] = baseTurns + ChatTurn.User(text) },
                        onAttach = { comingSoon("Attachments") },
                        onStartVoice = { startVoice(screen.title) },
                        onSwitchToText = { endVoice() },
                        onToggleMute = { viewModel.toggleMute() },
                        onEndVoice = { endVoice() },
                        onListen = { text -> tts.speak(text) },
                        onExplain = { comingSoon("Grammar explanations") },
                        modifier = screenModifier,
                    )
                }
            }
        }
    }
}

private fun com.talkie.ai.model.ChatMessage.toTurn(): ChatTurn = when (speaker) {
    Speaker.USER -> ChatTurn.User(message = text)
    Speaker.AGENT -> ChatTurn.Assistant(message = text)
}

/** Reads tutor messages aloud for the "Listen" chip. */
private class SpanishTts(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) engine.language = Locale("es", "ES")
    }

    fun speak(text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zuno-listen")
    }

    fun shutdown() = engine.shutdown()
}

@Composable
private fun rememberSpanishTts(): SpanishTts {
    val context = LocalContext.current
    val holder = remember { SpanishTts(context) }
    DisposableEffect(holder) {
        onDispose { holder.shutdown() }
    }
    return holder
}
