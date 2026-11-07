package com.talkie.ai.ui

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkie.ai.auth.AuthState
import com.talkie.ai.auth.AuthViewModel
import com.talkie.ai.data.Attachment
import com.talkie.ai.data.AttachmentFiles
import com.talkie.ai.data.AttachmentKind
import com.talkie.ai.data.Conversation
import com.talkie.ai.data.ConversationKind
import com.talkie.ai.data.Message
import com.talkie.ai.data.RecentKind
import com.talkie.ai.data.TargetLanguage
import com.talkie.ai.model.AgentState
import com.talkie.ai.model.Speaker
import com.talkie.ai.model.VoiceCatalog
import com.talkie.ai.ui.components.AgentGlowState
import com.talkie.ai.R
import com.talkie.ai.reminders.Reminder
import com.talkie.ai.reminders.Reminders
import com.talkie.ai.ui.components.ReminderOption
import com.talkie.ai.ui.components.ZunoAttachSheet
import com.talkie.ai.ui.components.ZunoImageViewer
import com.talkie.ai.ui.components.ZunoMenuAction
import com.talkie.ai.ui.components.ZunoPasteLinkSheet
import com.talkie.ai.ui.components.ZunoReminderSheet
import com.talkie.ai.ui.components.ZunoToast
import com.talkie.ai.ui.screens.AccountSheet
import com.talkie.ai.ui.screens.AppDrawer
import com.talkie.ai.ui.screens.AuthMode
import com.talkie.ai.ui.screens.AuthScreen
import com.talkie.ai.ui.screens.ChatScreen
import com.talkie.ai.ui.screens.DrawerDestination
import com.talkie.ai.ui.screens.HomeCopy
import com.talkie.ai.ui.screens.HomeScreen
import com.talkie.ai.ui.screens.HomeVoiceState
import com.talkie.ai.ui.screens.LiveLine
import com.talkie.ai.ui.screens.NewRoleplayScreen
import com.talkie.ai.ui.screens.NewTopicScreen
import com.talkie.ai.ui.screens.OnboardingScreen
import com.talkie.ai.ui.screens.RoleplaysScreen
import com.talkie.ai.ui.screens.SplashScreen
import com.talkie.ai.ui.screens.TopicScreen
import com.talkie.ai.ui.screens.TopicsScreen
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import com.talkie.ai.viewmodel.HOME_DRAFT_KEY
import com.talkie.ai.viewmodel.MAX_TOPIC_MATERIAL
import com.talkie.ai.viewmodel.TOPIC_DRAFT_KEY
import com.talkie.ai.viewmodel.VoiceSessionViewModel
import com.talkie.ai.viewmodel.ZunoViewModel
import com.talkie.ai.voice.rememberSpeechCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface ZunoRoute {
    data object Splash : ZunoRoute
    data object Onboarding : ZunoRoute
    data class Auth(val mode: AuthMode) : ZunoRoute
    data object Home : ZunoRoute
    data object Topics : ZunoRoute
    data object NewTopic : ZunoRoute
    data class Topic(val id: String) : ZunoRoute
    data object Roleplays : ZunoRoute
    data object NewRoleplay : ZunoRoute
    data class Chat(val conversationId: String) : ZunoRoute

    /** Routes that sit under the side drawer. */
    val hasDrawer: Boolean get() = this !is Splash && this !is Onboarding && this !is Auth
}

private fun ZunoRoute.encode(): String = when (this) {
    ZunoRoute.Splash -> "splash"
    ZunoRoute.Onboarding -> "onboarding"
    is ZunoRoute.Auth -> "auth:${mode.name}"
    ZunoRoute.Home -> "home"
    ZunoRoute.Topics -> "topics"
    ZunoRoute.NewTopic -> "new-topic"
    is ZunoRoute.Topic -> "topic:$id"
    ZunoRoute.Roleplays -> "roleplays"
    ZunoRoute.NewRoleplay -> "new-roleplay"
    is ZunoRoute.Chat -> "chat:$conversationId"
}

private fun decodeRoute(value: String): ZunoRoute = when {
    value == "onboarding" -> ZunoRoute.Onboarding
    value.startsWith("auth:") -> ZunoRoute.Auth(AuthMode.valueOf(value.removePrefix("auth:")))
    value == "home" -> ZunoRoute.Home
    value == "topics" -> ZunoRoute.Topics
    value == "new-topic" -> ZunoRoute.NewTopic
    value.startsWith("topic:") -> ZunoRoute.Topic(value.removePrefix("topic:"))
    value == "roleplays" -> ZunoRoute.Roleplays
    value == "new-roleplay" -> ZunoRoute.NewRoleplay
    value.startsWith("chat:") -> ZunoRoute.Chat(value.removePrefix("chat:"))
    else -> ZunoRoute.Splash
}

private val BackStackSaver = listSaver<SnapshotStateList<ZunoRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<ZunoRoute>().apply { addAll(saved.map(::decodeRoute)) } },
)

private const val PREFS = "zuno_prefs"
private const val KEY_MIC_ASKED = "mic_permission_asked"

/**
 * Root of the Zuno UI: navigation between screens, the side drawer, and the bridges to the
 * app API ([ZunoViewModel]) and the LiveKit voice session ([VoiceSessionViewModel]).
 */
@Composable
fun ZunoApp(
    viewModel: VoiceSessionViewModel,
    authViewModel: AuthViewModel,
    zunoViewModel: ZunoViewModel,
    runWithVoicePermissions: (() -> Unit) -> Unit,
    /** Conversation to open from a reminder notification; set back to null once opened. */
    openChatRequest: MutableStateFlow<String?>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<ZunoRoute>(ZunoRoute.Splash) }
    val route = backStack.last()

    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val authForm by authViewModel.form.collectAsStateWithLifecycle()

    val profile by zunoViewModel.profile.collectAsStateWithLifecycle()
    val recent by zunoViewModel.recent.collectAsStateWithLifecycle()
    val topics by zunoViewModel.topics.collectAsStateWithLifecycle()
    val topicDetails by zunoViewModel.topicDetails.collectAsStateWithLifecycle()
    val roleplays by zunoViewModel.roleplays.collectAsStateWithLifecycle()
    val chats by zunoViewModel.chats.collectAsStateWithLifecycle()
    val busy by zunoViewModel.busy.collectAsStateWithLifecycle()
    val typingHint by zunoViewModel.typingHint.collectAsStateWithLifecycle()
    val drafts by zunoViewModel.drafts.collectAsStateWithLifecycle()
    val materialAnalysis by zunoViewModel.materialAnalysis.collectAsStateWithLifecycle()
    val pendingChat by openChatRequest.collectAsStateWithLifecycle()

    val language = profile?.targetLanguage ?: TargetLanguage.Spanish
    val tts = rememberTutorTts(language)

    fun navigate(to: ZunoRoute, clearStack: Boolean = false) {
        if (clearStack) backStack.clear()
        if (backStack.lastOrNull() != to) backStack.add(to)
    }

    fun replace(to: ZunoRoute) {
        backStack.removeAt(backStack.lastIndex)
        backStack.add(to)
    }

    /** Top-level sections sit directly above Home, so Back returns there. */
    fun openSection(section: ZunoRoute) {
        backStack.clear()
        backStack.add(ZunoRoute.Home)
        backStack.add(section)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else navigate(ZunoRoute.Home, clearStack = true)
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun comingSoon(feature: String) = toast("$feature — coming soon")

    LaunchedEffect(Unit) { zunoViewModel.messages.collect { toast(it) } }
    LaunchedEffect(Unit) { zunoViewModel.sessionExpired.collect { authViewModel.signOut() } }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    // ── Live voice session (chat) ───────────────────────────────────────────
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val voiceMessages by viewModel.messages.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val voiceError by viewModel.errorMessage.collectAsStateWithLifecycle()
    var voiceConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceMessageStart by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(voiceError) {
        val message = voiceError
        if (voiceConversationId != null && !message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            voiceConversationId = null
        }
    }

    /** Starts a live LiveKit voice call with the tutor for [conversationId]. */
    fun startVoice(conversationId: String, speakLastReply: Boolean = false) {
        runWithVoicePermissions {
            tts.stop()
            voiceMessageStart = viewModel.messages.value.size
            voiceConversationId = conversationId
            if (viewModel.agentState.value == AgentState.DISCONNECTED) {
                viewModel.connect(
                    sessionToken = authViewModel.sessionToken,
                    onSessionExpired = authViewModel::signOut,
                    conversationId = conversationId,
                    voice = VoiceCatalog.tutorVoiceFor(language.code),
                    speakLastReply = speakLastReply,
                )
            }
        }
    }

    fun endVoice(commit: Boolean = true) {
        val conversationId = voiceConversationId ?: return
        if (commit) {
            val spoken = viewModel.messages.value.drop(voiceMessageStart)
                .filter { it.isFinal && it.text.isNotBlank() }
                .map { (it.speaker == Speaker.USER) to it.text }
            zunoViewModel.saveVoiceTurns(conversationId, spoken)
        }
        viewModel.disconnect()
        voiceConversationId = null
    }

    // A call that drops after connecting (network, agent left) ends the voice session.
    var callWasLive by remember { mutableStateOf(false) }
    LaunchedEffect(agentState) {
        when (agentState) {
            AgentState.CONNECTING -> Unit
            AgentState.DISCONNECTED -> {
                if (callWasLive && voiceConversationId != null) {
                    toast("Voice call ended")
                    endVoice()
                }
                callWasLive = false
            }
            else -> callWasLive = true
        }
    }

    // ── Auth transitions ────────────────────────────────────────────────────
    LaunchedEffect(authState) {
        val current = backStack.last()
        when (authState) {
            is AuthState.SignedIn -> {
                zunoViewModel.refreshAccount()
                if (current is ZunoRoute.Auth || current == ZunoRoute.Onboarding) navigate(ZunoRoute.Home, clearStack = true)
            }
            AuthState.SignedOut -> {
                if (voiceConversationId != null) endVoice(commit = false)
                zunoViewModel.clear()
                Reminders.clearAll(context)
                if (current.hasDrawer) navigate(ZunoRoute.Onboarding, clearStack = true)
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

    // A reminder notification reopens its chat on top of Home, once signed in and past the splash.
    LaunchedEffect(pendingChat, authState, route) {
        val conversationId = pendingChat ?: return@LaunchedEffect
        if (authState !is AuthState.SignedIn || route == ZunoRoute.Splash) return@LaunchedEffect
        if (voiceConversationId != null && voiceConversationId != conversationId) endVoice()
        backStack.clear()
        backStack.add(ZunoRoute.Home)
        backStack.add(ZunoRoute.Chat(conversationId))
        openChatRequest.value = null
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && backStack.size > 1) {
        if (route is ZunoRoute.Chat && voiceConversationId == route.conversationId) endVoice()
        backStack.removeAt(backStack.lastIndex)
    }

    // ── Home: hold-to-talk with on-device speech recognition ────────────────
    val speech = rememberSpeechCapture()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var micBlocked by remember { mutableStateOf(!micGranted() && prefs.getBoolean(KEY_MIC_ASKED, false)) }
    var homeVoiceState by remember { mutableStateOf(HomeVoiceState.Idle) }
    LaunchedEffect(busy) {
        if (!busy && homeVoiceState == HomeVoiceState.Sending) homeVoiceState = HomeVoiceState.Idle
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (micGranted()) micBlocked = false
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit { putBoolean(KEY_MIC_ASKED, true) }
        micBlocked = !granted
        if (granted) toast("Microphone on — hold the glow to talk")
    }

    fun requestMicrophone() {
        val activity = context.findActivity()
        val canAsk = !prefs.getBoolean(KEY_MIC_ASKED, false) ||
            (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO))
        if (canAsk) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openChat(conversationId: String) = navigate(ZunoRoute.Chat(conversationId))

    fun startFreeTalk(text: String?, viaVoice: Boolean = false) {
        if (voiceConversationId != null) endVoice()
        if (!viaVoice) {
            zunoViewModel.startFreeTalk(text) { openChat(it.id) }
            return
        }
        // Spoken from Home: the chat opens straight into the live call. The first turn goes through
        // the API (for feedback), then the call connects and the tutor speaks its reply.
        var conversationId: String? = null
        zunoViewModel.startFreeTalk(
            firstMessage = text,
            viaVoice = true,
            onReplied = { replied ->
                val id = conversationId
                if (id != null && voiceConversationId == id) {
                    if (replied) startVoice(id, speakLastReply = true) else voiceConversationId = null
                }
            },
        ) { conversation ->
            conversationId = conversation.id
            voiceMessageStart = viewModel.messages.value.size
            voiceConversationId = conversation.id
            openChat(conversation.id)
        }
    }

    // ── Attachments: + sheet, pickers, links, viewer ────────────────────────
    /** Draft key (conversation id, Home or topic material) the + sheet adds files to. */
    var attachTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var linkTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }

    fun roomFor(key: String) = (zunoViewModel.maxAttachments(key) - drafts[key].orEmpty().size).coerceAtLeast(0)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentFiles.MAX_PER_MESSAGE),
    ) { uris -> pickerTarget?.let { zunoViewModel.addAttachments(it, uris) } }
    val materialPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_TOPIC_MATERIAL),
    ) { uris -> pickerTarget?.let { zunoViewModel.addAttachments(it, uris) } }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pickerTarget?.let { zunoViewModel.addAttachments(it, uris) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) pickerTarget?.let { zunoViewModel.addAttachments(it, listOf(uri)) }
    }

    fun limitMessage(key: String) =
        if (key == TOPIC_DRAFT_KEY) "You can add up to $MAX_TOPIC_MATERIAL files"
        else "You can attach up to ${AttachmentFiles.MAX_PER_MESSAGE} files per message"

    fun openAttachmentSheet(key: String) {
        if (roomFor(key) == 0) toast(limitMessage(key)) else attachTarget = key
    }

    /** Runs a picker for [key] directly (the topic Upload Area has its own source buttons). */
    fun launchPicker(key: String, action: (String) -> Unit) {
        if (roomFor(key) == 0) {
            toast(limitMessage(key))
            return
        }
        pickerTarget = key
        try {
            action(key)
        } catch (_: android.content.ActivityNotFoundException) {
            toast("No app available for this on your device")
        }
    }

    fun pick(action: (String) -> Unit) {
        val key = attachTarget ?: return
        attachTarget = null
        launchPicker(key, action)
    }

    fun launchCamera(key: String) = launchPicker(key) {
        val uri = AttachmentFiles.newCameraUri(context)
        cameraUri = uri
        camera.launch(uri)
    }

    fun launchPhotos(key: String) = launchPicker(key) {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (key == TOPIC_DRAFT_KEY) materialPhotoPicker.launch(request) else photoPicker.launch(request)
    }

    fun launchFiles(key: String) = launchPicker(key) { documentPicker.launch(AttachmentFiles.documentMimeTypes) }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            toast("No app can open this link")
        }
    }

    fun openAttachment(attachment: Attachment) {
        when (attachment.kind) {
            AttachmentKind.Image -> viewing = attachment
            AttachmentKind.Link -> attachment.url?.let(::openUrl)
            AttachmentKind.Document -> scope.launch {
                val url = zunoViewModel.attachmentUrl(attachment.id) ?: return@launch
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: android.content.ActivityNotFoundException) {
                    toast("No app can open this file")
                }
            }
        }
    }

    // ── Toast & "Remind me to continue" ─────────────────────────────────────
    var toastState by remember { mutableStateOf<ToastState?>(null) }
    LaunchedEffect(toastState) {
        if (toastState != null) {
            delay(TOAST_DURATION_MS)
            toastState = null
        }
    }

    var reminderTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var customReminder by remember { mutableStateOf<ReminderOption?>(null) }
    var pendingReminder by remember { mutableStateOf<Reminder?>(null) }

    fun confirmReminder(reminder: Reminder, summary: String) {
        Reminders.schedule(context, reminder)
        if (!Reminders.canNotify(context)) toast("Turn on notifications for Zuno to get this reminder")
        toastState = ToastState("Reminder set · $summary", action = "Undo") {
            Reminders.cancel(context, reminder.conversationId)
            toastState = null
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingReminder?.let { reminder ->
            confirmReminder(reminder, reminderSummary(context, reminder.atMillis))
            pendingReminder = null
        }
    }

    fun setReminder(conversationId: String, option: ReminderOption) {
        val chat = chats[conversationId]
        val reminder = Reminder(
            conversationId = conversationId,
            title = chat?.conversation?.title?.ifBlank { null } ?: "your chat",
            lastLine = chat?.messages?.lastOrNull { !it.isUser }?.text.orEmpty(),
            atMillis = option.atMillis,
        )
        if (!Reminders.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingReminder = reminder
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            confirmReminder(reminder, option.summary)
        }
    }

    fun pickReminderTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute -> customReminder = customReminderOption(context, hour, minute) },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(context),
        ).show()
    }

    // ── New topic (optionally from a chat's material) ───────────────────────
    var topicPrefill by rememberSaveable { mutableStateOf("") }

    fun leaveNewTopic() {
        zunoViewModel.discardTopicDraft()
        pop()
    }

    // ── Account sheet & grammar explanations ────────────────────────────────
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<ExplanationState?>(null) }
    fun explain(message: Message) {
        explanation = ExplanationState(message.text, null)
        scope.launch {
            val result = zunoViewModel.explain(message.id)
            explanation = explanation?.takeIf { it.source == message.text }?.copy(
                text = result.getOrElse { it.message ?: "Couldn't explain this right now." },
                loaded = true,
            )
        }
    }

    val activeDestination = when (route) {
        ZunoRoute.Home -> DrawerDestination.FreeTalk
        ZunoRoute.Topics, ZunoRoute.NewTopic, is ZunoRoute.Topic -> DrawerDestination.Topics
        ZunoRoute.Roleplays, ZunoRoute.NewRoleplay -> DrawerDestination.Roleplays
        is ZunoRoute.Chat -> when (chats[route.conversationId]?.conversation?.kind) {
            ConversationKind.Lesson -> DrawerDestination.Topics
            ConversationKind.Roleplay -> DrawerDestination.Roleplays
            else -> DrawerDestination.FreeTalk
        }
        else -> null
    }
    val selectedSessionId = when (route) {
        is ZunoRoute.Topic -> route.id
        is ZunoRoute.Chat -> chats[route.conversationId]?.conversation?.let { it.topicId ?: it.roleplayId ?: it.id }
        else -> null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = route.hasDrawer,
        scrimColor = ZunoColors.BgScrim,
        modifier = modifier,
        drawerContent = {
            AppDrawer(
                userName = profile?.displayName.orEmpty(),
                userDetail = profile?.detail ?: "",
                activeDestination = activeDestination,
                sessions = recent,
                selectedSessionId = selectedSessionId,
                onProfile = { closeDrawerThen { showAccount = true } },
                onNotifications = { comingSoon("Notifications") },
                onDestination = { destination ->
                    closeDrawerThen {
                        if (voiceConversationId != null) endVoice()
                        when (destination) {
                            DrawerDestination.FreeTalk -> navigate(ZunoRoute.Home, clearStack = true)
                            DrawerDestination.Topics -> openSection(ZunoRoute.Topics)
                            DrawerDestination.Roleplays -> openSection(ZunoRoute.Roleplays)
                            else -> comingSoon(destination.label)
                        }
                    }
                },
                onSearchSessions = { comingSoon("Search") },
                onSession = { session ->
                    closeDrawerThen {
                        if (voiceConversationId != null) endVoice()
                        when (session.kind) {
                            RecentKind.Topic -> navigate(ZunoRoute.Topic(session.id))
                            RecentKind.Roleplay -> zunoViewModel.openRoleplay(session.id) { openChat(it.id) }
                            RecentKind.FreeTalk -> openChat(session.id)
                        }
                    }
                },
                onUpgrade = { comingSoon("Zuno Pro") },
                onSettings = { closeDrawerThen { showAccount = true } },
                onNewChat = { closeDrawerThen { startFreeTalk(null) } },
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
                        pop()
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
                    copy = HomeCopy.forLanguage(language, profile?.firstName.orEmpty()),
                    voiceState = homeVoiceState,
                    liveTranscript = speech.partial,
                    audioLevel = { speech.level },
                    micBlocked = micBlocked,
                    busy = busy,
                    typingHint = typingHint,
                    selector = profile?.detail ?: "${language.label} · …",
                    onOpenDrawer = { openDrawer() },
                    onSelector = { showAccount = true },
                    onHoldStart = {
                        when {
                            !micGranted() -> requestMicrophone()
                            !speech.isAvailable -> toast("Speech recognition isn't available on this device. Type instead.")
                            else -> {
                                tts.stop()
                                homeVoiceState = HomeVoiceState.Listening
                                speech.start(language.speechTag) { transcript ->
                                    if (transcript.isNullOrBlank()) {
                                        homeVoiceState = HomeVoiceState.Idle
                                        toast("Didn't catch that — try again")
                                    } else {
                                        homeVoiceState = HomeVoiceState.Sending
                                        startFreeTalk(transcript, viaVoice = true)
                                    }
                                }
                            }
                        }
                    },
                    onHoldMove = { cancelling ->
                        if (speech.listening) homeVoiceState = if (cancelling) HomeVoiceState.Cancelling else HomeVoiceState.Listening
                    },
                    onHoldEnd = {
                        when (homeVoiceState) {
                            HomeVoiceState.Cancelling -> {
                                speech.cancel()
                                homeVoiceState = HomeVoiceState.Idle
                            }
                            HomeVoiceState.Listening -> speech.stop()
                            else -> Unit
                        }
                    },
                    onAllowMicrophone = ::requestMicrophone,
                    onDraftChanged = zunoViewModel::onDraftChanged,
                    onSendText = { text -> startFreeTalk(text) },
                    onTypeModeChanged = { if (!it) zunoViewModel.clearHint() },
                    drafts = drafts[HOME_DRAFT_KEY].orEmpty(),
                    onAttach = { openAttachmentSheet(HOME_DRAFT_KEY) },
                    onRemoveAttachment = { zunoViewModel.removeAttachment(HOME_DRAFT_KEY, it.localId) },
                    onRetryAttachment = { zunoViewModel.retryAttachment(HOME_DRAFT_KEY, it.localId) },
                    modifier = screenModifier,
                )

                ZunoRoute.Topics -> {
                    LaunchedEffect(Unit) { zunoViewModel.loadTopics() }
                    TopicsScreen(
                        topics = topics.items,
                        loading = topics.loading,
                        error = topics.error,
                        onBack = ::pop,
                        onRetry = zunoViewModel::loadTopics,
                        onOpen = { navigate(ZunoRoute.Topic(it)) },
                        onCreate = {
                            topicPrefill = ""
                            navigate(ZunoRoute.NewTopic)
                        },
                        onDelete = { zunoViewModel.deleteTopic(it.id) },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.NewTopic -> {
                    BackHandler(enabled = !drawerState.isOpen) { leaveNewTopic() }
                    NewTopicScreen(
                        busy = busy,
                        initialPrompt = topicPrefill,
                        material = drafts[TOPIC_DRAFT_KEY].orEmpty(),
                        analysis = materialAnalysis,
                        onBack = ::leaveNewTopic,
                        onCreate = { prompt -> zunoViewModel.createTopic(prompt) { replace(ZunoRoute.Topic(it.summary.id)) } },
                        onAnalyze = zunoViewModel::analyzeMaterial,
                        onTogglePart = zunoViewModel::toggleMaterialPart,
                        onFiles = { launchFiles(TOPIC_DRAFT_KEY) },
                        onPhotos = { launchPhotos(TOPIC_DRAFT_KEY) },
                        onCamera = { launchCamera(TOPIC_DRAFT_KEY) },
                        onAddMaterial = { openAttachmentSheet(TOPIC_DRAFT_KEY) },
                        onRemoveMaterial = { zunoViewModel.removeAttachment(TOPIC_DRAFT_KEY, it.localId) },
                        onRetryMaterial = { zunoViewModel.retryAttachment(TOPIC_DRAFT_KEY, it.localId) },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.Topic -> {
                    LaunchedEffect(screen.id) { zunoViewModel.loadTopic(screen.id) }
                    TopicScreen(
                        topic = topicDetails[screen.id],
                        busy = busy,
                        onBack = ::pop,
                        onRetry = { zunoViewModel.loadTopic(screen.id) },
                        onOpenLesson = { lesson -> zunoViewModel.startLesson(screen.id, lesson.id) { openChat(it.id) } },
                        onReset = { zunoViewModel.resetTopic(screen.id) },
                        onDelete = {
                            zunoViewModel.deleteTopic(screen.id)
                            pop()
                        },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.Roleplays -> {
                    LaunchedEffect(Unit) { zunoViewModel.loadRoleplays() }
                    RoleplaysScreen(
                        roleplays = roleplays.items,
                        loading = roleplays.loading,
                        error = roleplays.error,
                        onBack = ::pop,
                        onRetry = zunoViewModel::loadRoleplays,
                        onOpen = { id -> zunoViewModel.openRoleplay(id) { openChat(it.id) } },
                        onCreate = { navigate(ZunoRoute.NewRoleplay) },
                        onRestart = { roleplay -> zunoViewModel.openRoleplay(roleplay.id, restart = true) { openChat(it.id) } },
                        onDelete = { zunoViewModel.deleteRoleplay(it.id) },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.NewRoleplay -> NewRoleplayScreen(
                    busy = busy,
                    onBack = ::pop,
                    onCreate = { scenario, userRole, aiRole ->
                        zunoViewModel.createRoleplay(scenario, userRole, aiRole) { replace(ZunoRoute.Chat(it.id)) }
                    },
                    modifier = screenModifier,
                )

                is ZunoRoute.Chat -> {
                    val chat = chats[screen.conversationId]
                    LaunchedEffect(screen.conversationId) {
                        if (chat == null) zunoViewModel.loadConversation(screen.conversationId)
                        zunoViewModel.restoreDraft(screen.conversationId)
                    }
                    val conversation = chat?.conversation
                    val inVoice = voiceConversationId == screen.conversationId
                    val liveLines = if (inVoice) {
                        voiceMessages.drop(voiceMessageStart).map { LiveLine(it.speaker == Speaker.USER, it.text) }
                    } else {
                        emptyList()
                    }
                    val menu = buildList {
                        if (conversation != null) {
                            add(ZunoMenuAction("Remind me to continue") {
                                customReminder = null
                                reminderTarget = screen.conversationId
                            })
                            add(ZunoMenuAction("Turn into a topic") {
                                if (inVoice) endVoice()
                                topicPrefill = zunoViewModel.prefillTopicFromChat(screen.conversationId)
                                navigate(ZunoRoute.NewTopic)
                            })
                        }
                        if (conversation?.kind == ConversationKind.Lesson && conversation.topicId != null && conversation.lessonId != null) {
                            add(ZunoMenuAction("Mark lesson complete") {
                                zunoViewModel.completeLesson(conversation.topicId, conversation.lessonId)
                            })
                            add(ZunoMenuAction("Back to topic") { navigate(ZunoRoute.Topic(conversation.topicId)) })
                        }
                        if (conversation?.kind == ConversationKind.Roleplay && conversation.roleplayId != null) {
                            add(ZunoMenuAction("Start over") {
                                if (inVoice) endVoice()
                                zunoViewModel.openRoleplay(conversation.roleplayId, restart = true) { replace(ZunoRoute.Chat(it.id)) }
                            })
                        }
                        add(ZunoMenuAction("Delete conversation") {
                            if (inVoice) endVoice(commit = false)
                            zunoViewModel.deleteConversation(screen.conversationId) { pop() }
                        })
                    }
                    ChatScreen(
                        title = conversation?.title.orEmpty(),
                        sessionLabel = conversation?.let(::sessionLabel).orEmpty(),
                        messages = chat?.messages,
                        pendingText = chat?.pendingText,
                        pendingAttachments = chat?.pendingAttachments.orEmpty(),
                        drafts = drafts[screen.conversationId].orEmpty(),
                        error = chat?.error,
                        liveLines = liveLines,
                        voiceMode = inVoice,
                        voiceConnecting = inVoice &&
                            (agentState == AgentState.CONNECTING || agentState == AgentState.DISCONNECTED),
                        glowState = voiceGlowState(agentState, isMuted),
                        glowLevel = {
                            if (agentState == AgentState.SPEAKING) viewModel.agentAudioLevel.value
                            else viewModel.userAudioLevel.value
                        },
                        isMuted = isMuted,
                        placeholder = HomeCopy.forLanguage(language, "").placeholder,
                        typingHint = typingHint,
                        menu = menu,
                        onBack = {
                            if (inVoice) endVoice()
                            pop()
                        },
                        onRetryLoad = { zunoViewModel.loadConversation(screen.conversationId) },
                        onSend = { text -> zunoViewModel.send(screen.conversationId, text) },
                        onDraftChanged = zunoViewModel::onDraftChanged,
                        savedDraft = { zunoViewModel.draftText(screen.conversationId) },
                        onSaveDraft = { zunoViewModel.saveDraftText(screen.conversationId, it) },
                        onStartVoice = {
                            if (chat?.pendingText == null) startVoice(screen.conversationId)
                            else toast("One moment — Zuno is still replying")
                        },
                        onSwitchToText = { endVoice() },
                        onToggleMute = { viewModel.toggleMute() },
                        onEndVoice = { endVoice() },
                        onListen = { text -> tts.speak(text) },
                        onExplain = ::explain,
                        onAttach = { openAttachmentSheet(screen.conversationId) },
                        onRemoveAttachment = { zunoViewModel.removeAttachment(screen.conversationId, it.localId) },
                        onRetryAttachment = { zunoViewModel.retryAttachment(screen.conversationId, it.localId) },
                        onOpenAttachment = ::openAttachment,
                        modifier = screenModifier,
                    )
                }
            }
        }
    }

    attachTarget?.let { key ->
        // Conversations get "Remind me to continue"; Home and topic material have no chat to come back to yet.
        val isConversation = key != HOME_DRAFT_KEY && key != TOPIC_DRAFT_KEY
        ZunoAttachSheet(
            title = if (key == TOPIC_DRAFT_KEY) "Add learning material" else "Add to this chat",
            onDismiss = { attachTarget = null },
            onCamera = { pick(::launchCamera) },
            onPhotos = { pick(::launchPhotos) },
            onFiles = { pick(::launchFiles) },
            onPasteLink = {
                attachTarget = null
                linkTarget = key
            },
            onRemind = if (isConversation) {
                {
                    attachTarget = null
                    customReminder = null
                    reminderTarget = key
                }
            } else {
                null
            },
        )
    }

    linkTarget?.let { key ->
        ZunoPasteLinkSheet(
            onDismiss = { linkTarget = null },
            onAdd = { url ->
                linkTarget = null
                zunoViewModel.addLink(key, url)
            },
        )
    }

    reminderTarget?.let { conversationId ->
        ZunoReminderSheet(
            options = remember(conversationId) { reminderOptions(context) },
            custom = customReminder,
            onPickTime = ::pickReminderTime,
            onDismiss = { reminderTarget = null },
            onSet = { option ->
                reminderTarget = null
                setReminder(conversationId, option)
            },
        )
    }

    toastState?.let { state ->
        Box(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 80.dp, start = ZunoSpace.s24, end = ZunoSpace.s24),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ZunoToast(state.message, action = state.action, onAction = state.onAction)
        }
    }

    viewing?.let { ZunoImageViewer(it, onDismiss = { viewing = null }) }

    val currentProfile = profile
    if (showAccount && currentProfile != null) {
        AccountSheet(
            profile = currentProfile,
            onDismiss = { showAccount = false },
            onLanguage = { zunoViewModel.updateProfile(targetLanguage = it) },
            onLevel = { zunoViewModel.updateProfile(level = it) },
            onSignOut = {
                showAccount = false
                authViewModel.signOut()
            },
        )
    }

    explanation?.let { state ->
        AlertDialog(
            onDismissRequest = { explanation = null },
            containerColor = ZunoColors.BgElevated,
            title = { Text("Grammar", style = ZunoType.Headline, color = ZunoColors.TextPrimary) },
            text = {
                Text(
                    if (state.loaded) state.text.orEmpty() else "Thinking…",
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { explanation = null }) {
                    Text("Got it", style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary)
                }
            },
        )
    }
}

/** "New Free Talk · Just now", "Roleplay · 3 hours ago", … */
/** Live call → glow. The mic stays open between turns, so an idle call still listens to the user. */
private fun voiceGlowState(agentState: AgentState, isMuted: Boolean): AgentGlowState = when (agentState) {
    AgentState.DISCONNECTED, AgentState.CONNECTING, AgentState.THINKING -> AgentGlowState.Thinking
    AgentState.SPEAKING -> AgentGlowState.Speaking
    AgentState.IDLE, AgentState.LISTENING -> if (isMuted) AgentGlowState.Disabled else AgentGlowState.Listening
}

private fun sessionLabel(conversation: Conversation): String {
    val age = System.currentTimeMillis() - conversation.createdAtMillis
    val kind = when (conversation.kind) {
        ConversationKind.FreeTalk -> "Free Talk"
        ConversationKind.Roleplay -> "Roleplay"
        ConversationKind.Lesson -> "Lesson"
    }
    if (age < 5 * DateUtils.MINUTE_IN_MILLIS) return "New $kind · Just now"
    val relative = DateUtils.getRelativeTimeSpanString(
        conversation.createdAtMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "$kind · $relative"
}

private data class ExplanationState(val source: String, val text: String?, val loaded: Boolean = false)

private data class ToastState(val message: String, val action: String? = null, val onAction: () -> Unit = {})

private const val TOAST_DURATION_MS = 4_000L

private fun timeLabel(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

private fun atTime(dayOffset: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, dayOffset)
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Quick reminder times: in an hour, tonight at 9 (while it's at least an hour away) and tomorrow at 8. */
private fun reminderOptions(context: Context): List<ReminderOption> {
    val now = System.currentTimeMillis()
    val tonight = atTime(0, 21)
    val tomorrow = atTime(1, 8)
    return buildList {
        add(ReminderOption("In 1 hour", "In 1 hour", now + DateUtils.HOUR_IN_MILLIS))
        if (tonight - now >= DateUtils.HOUR_IN_MILLIS) {
            val time = timeLabel(context, tonight)
            add(ReminderOption("Tonight · $time", "Tonight $time", tonight))
        }
        val time = timeLabel(context, tomorrow)
        add(ReminderOption("Tomorrow · $time", "Tomorrow $time", tomorrow))
    }
}

/** The next [hour]:[minute] — today if it's still ahead, otherwise tomorrow. */
private fun customReminderOption(context: Context, hour: Int, minute: Int): ReminderOption {
    val today = atTime(0, hour, minute)
    val (at, day) = if (today > System.currentTimeMillis()) today to "Today" else atTime(1, hour, minute) to "Tomorrow"
    val time = timeLabel(context, at)
    return ReminderOption("$day · $time", "$day $time", at, icon = R.drawable.zuno_ic_clock_small)
}

private fun reminderSummary(context: Context, millis: Long): String {
    val day = if (DateUtils.isToday(millis)) "Today" else "Tomorrow"
    return "$day ${timeLabel(context, millis)}"
}

private val TargetLanguage.speechTag: String
    get() = when (this) {
        TargetLanguage.Spanish -> "es-ES"
        TargetLanguage.English -> "en-US"
    }

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Reads tutor messages aloud in the language being learned. */
private class TutorTts(context: Context, private var locale: Locale) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) engine.language = locale
    }

    fun setLocale(value: Locale) {
        locale = value
        if (ready) engine.language = value
    }

    fun speak(text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zuno-listen")
    }

    fun stop() {
        engine.stop()
    }

    fun shutdown() = engine.shutdown()
}

@Composable
private fun rememberTutorTts(language: TargetLanguage): TutorTts {
    val context = LocalContext.current
    val locale = if (language == TargetLanguage.Spanish) Locale("es", "ES") else Locale.US
    val holder = remember { TutorTts(context, locale) }
    LaunchedEffect(locale) { holder.setLocale(locale) }
    DisposableEffect(holder) {
        onDispose { holder.shutdown() }
    }
    return holder
}
