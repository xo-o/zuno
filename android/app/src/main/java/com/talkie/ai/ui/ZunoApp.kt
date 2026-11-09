package com.talkie.ai.ui

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkie.ai.BuildConfig
import com.talkie.ai.R
import com.talkie.ai.auth.AuthState
import com.talkie.ai.auth.AuthViewModel
import com.talkie.ai.data.AppLanguage
import com.talkie.ai.data.Attachment
import com.talkie.ai.data.AttachmentFiles
import com.talkie.ai.data.AttachmentKind
import com.talkie.ai.data.Conversation
import com.talkie.ai.data.ConversationKind
import com.talkie.ai.data.LearningLanguage
import com.talkie.ai.data.LearningLanguages
import com.talkie.ai.data.Message
import com.talkie.ai.data.RecentKind
import com.talkie.ai.data.Schedule
import com.talkie.ai.model.AgentState
import com.talkie.ai.model.Speaker
import com.talkie.ai.model.VoiceCatalog
import com.talkie.ai.push.DeepLink
import com.talkie.ai.push.Push
import com.talkie.ai.reminders.Reminder
import com.talkie.ai.reminders.Reminders
import com.talkie.ai.ui.components.AgentGlowState
import com.talkie.ai.ui.components.ReminderOption
import com.talkie.ai.ui.components.ZunoAttachSheet
import com.talkie.ai.ui.components.ZunoDialog
import com.talkie.ai.ui.components.ZunoImageViewer
import com.talkie.ai.ui.components.ZunoInputDialog
import com.talkie.ai.ui.components.ZunoMenuAction
import com.talkie.ai.ui.components.ZunoPasteLinkSheet
import com.talkie.ai.ui.components.ZunoReminderSheet
import com.talkie.ai.ui.components.ZunoReportSheet
import com.talkie.ai.ui.components.ZunoShareSheet
import com.talkie.ai.ui.components.ZunoSheetAction
import com.talkie.ai.ui.components.ZunoToastHost
import com.talkie.ai.ui.components.rememberToastController
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
import com.talkie.ai.ui.screens.NewScheduleScreen
import com.talkie.ai.ui.screens.NewTopicScreen
import com.talkie.ai.ui.screens.OnboardingScreen
import com.talkie.ai.ui.screens.RoleplaysScreen
import com.talkie.ai.ui.screens.ScheduleActionsSheet
import com.talkie.ai.ui.screens.ScheduleFormat
import com.talkie.ai.ui.screens.ScheduledScreen
import com.talkie.ai.ui.screens.SettingsAccountScreen
import com.talkie.ai.ui.screens.SettingsAppLanguageScreen
import com.talkie.ai.ui.screens.SettingsLearningLanguageScreen
import com.talkie.ai.ui.screens.SettingsLearningScreen
import com.talkie.ai.ui.screens.SettingsNotificationsScreen
import com.talkie.ai.ui.screens.SettingsOverviewScreen
import com.talkie.ai.ui.screens.SettingsPreferencesScreen
import com.talkie.ai.ui.screens.SplashScreen
import com.talkie.ai.ui.screens.TopicScreen
import com.talkie.ai.ui.screens.TopicsScreen
import com.talkie.ai.ui.screens.displayName
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import com.talkie.ai.viewmodel.HOME_DRAFT_KEY
import com.talkie.ai.viewmodel.MAX_TOPIC_MATERIAL
import com.talkie.ai.viewmodel.TOPIC_DRAFT_KEY
import com.talkie.ai.viewmodel.ToastEvent
import com.talkie.ai.viewmodel.ToastIcon
import com.talkie.ai.viewmodel.VoiceSessionViewModel
import com.talkie.ai.viewmodel.ZunoViewModel
import com.talkie.ai.voice.rememberSpeechCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface ZunoRoute {
    data object Splash : ZunoRoute
    data object Onboarding : ZunoRoute
    data class Auth(val mode: AuthMode) : ZunoRoute
    data object Home : ZunoRoute
    data object Scheduled : ZunoRoute
    /** New schedule, or editing [editId]. */
    data class NewSchedule(val editId: String? = null) : ZunoRoute
    data object Topics : ZunoRoute
    data object NewTopic : ZunoRoute
    data class Topic(val id: String) : ZunoRoute
    data object Roleplays : ZunoRoute
    data object NewRoleplay : ZunoRoute
    data class Chat(val conversationId: String) : ZunoRoute
    data object Settings : ZunoRoute
    data object SettingsPreferences : ZunoRoute
    data object SettingsAppLanguage : ZunoRoute
    data object SettingsLearning : ZunoRoute
    data object SettingsLearningLanguage : ZunoRoute
    data object SettingsAccount : ZunoRoute
    data object SettingsNotifications : ZunoRoute

    /** Routes that sit under the side drawer. */
    val hasDrawer: Boolean get() = this !is Splash && this !is Onboarding && this !is Auth
}

private fun ZunoRoute.encode(): String = when (this) {
    ZunoRoute.Splash -> "splash"
    ZunoRoute.Onboarding -> "onboarding"
    is ZunoRoute.Auth -> "auth:${mode.name}"
    ZunoRoute.Home -> "home"
    ZunoRoute.Scheduled -> "scheduled"
    is ZunoRoute.NewSchedule -> "new-schedule:${editId.orEmpty()}"
    ZunoRoute.Topics -> "topics"
    ZunoRoute.NewTopic -> "new-topic"
    is ZunoRoute.Topic -> "topic:$id"
    ZunoRoute.Roleplays -> "roleplays"
    ZunoRoute.NewRoleplay -> "new-roleplay"
    is ZunoRoute.Chat -> "chat:$conversationId"
    ZunoRoute.Settings -> "settings"
    ZunoRoute.SettingsPreferences -> "settings:preferences"
    ZunoRoute.SettingsAppLanguage -> "settings:app-language"
    ZunoRoute.SettingsLearning -> "settings:learning"
    ZunoRoute.SettingsLearningLanguage -> "settings:learning-language"
    ZunoRoute.SettingsAccount -> "settings:account"
    ZunoRoute.SettingsNotifications -> "settings:notifications"
}

private fun decodeRoute(value: String): ZunoRoute = when {
    value == "onboarding" -> ZunoRoute.Onboarding
    value.startsWith("auth:") -> ZunoRoute.Auth(AuthMode.valueOf(value.removePrefix("auth:")))
    value == "home" -> ZunoRoute.Home
    value == "scheduled" -> ZunoRoute.Scheduled
    value.startsWith("new-schedule:") -> ZunoRoute.NewSchedule(value.removePrefix("new-schedule:").ifBlank { null })
    value == "topics" -> ZunoRoute.Topics
    value == "new-topic" -> ZunoRoute.NewTopic
    value.startsWith("topic:") -> ZunoRoute.Topic(value.removePrefix("topic:"))
    value == "roleplays" -> ZunoRoute.Roleplays
    value == "new-roleplay" -> ZunoRoute.NewRoleplay
    value.startsWith("chat:") -> ZunoRoute.Chat(value.removePrefix("chat:"))
    value == "settings" -> ZunoRoute.Settings
    value == "settings:preferences" -> ZunoRoute.SettingsPreferences
    value == "settings:app-language" -> ZunoRoute.SettingsAppLanguage
    value == "settings:learning" -> ZunoRoute.SettingsLearning
    value == "settings:learning-language" -> ZunoRoute.SettingsLearningLanguage
    value == "settings:account" -> ZunoRoute.SettingsAccount
    value == "settings:notifications" -> ZunoRoute.SettingsNotifications
    else -> ZunoRoute.Splash
}

private val BackStackSaver = listSaver<SnapshotStateList<ZunoRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<ZunoRoute>().apply { addAll(saved.map(::decodeRoute)) } },
)

private const val PREFS = "zuno_prefs"
private const val KEY_MIC_ASKED = "mic_permission_asked"

private const val TERMS_URL = "https://usezuno.rest/terms"
private const val PRIVACY_URL = "https://usezuno.rest/privacy"
private const val SUPPORT_EMAIL = "support@designcombo.dev"

/** Why the app is signing out, so the Sign in screen can say so. */
private enum class SignOutReason { User, Expired, Deleted }

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
    /** Where a notification tap should go; set back to null once handled. */
    deepLink: MutableStateFlow<DeepLink?>,
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
    val languages by zunoViewModel.languages.collectAsStateWithLifecycle()
    val notificationPrefs by zunoViewModel.notificationPrefs.collectAsStateWithLifecycle()
    val schedules by zunoViewModel.schedules.collectAsStateWithLifecycle()
    val scheduleTargets by zunoViewModel.scheduleTargets.collectAsStateWithLifecycle()
    val pendingLink by deepLink.collectAsStateWithLifecycle()

    val language: LearningLanguage = profile?.language ?: LearningLanguages.Spanish
    val accent = profile?.accent
    val tts = rememberTutorTts(language.locale(accent))
    val preferences = profile?.preferences

    var appLanguage by rememberSaveable {
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        mutableStateOf(
            if (currentTag.isNotBlank()) AppLanguage.fromCode(currentTag)
            else AppLanguage.fromCode(Locale.getDefault().language)
        )
    }

    LaunchedEffect(profile?.appLanguage) {
        val serverLang = profile?.appLanguage ?: return@LaunchedEffect
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            appLanguage = serverLang
        }
    }

    val localizedContext = remember(context, appLanguage) {
        val locale = Locale.forLanguageTag(appLanguage.bcp47)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        context.createConfigurationContext(config)
    }

    /** A string in the app language, for callbacks outside composition (toasts, intents). */
    fun str(@StringRes id: Int, vararg args: Any): String = localizedContext.getString(id, *args)

    fun selectAppLanguage(newLanguage: AppLanguage) {
        appLanguage = newLanguage
        zunoViewModel.updateProfile(appLanguage = newLanguage)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLanguage.bcp47))
    }

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

    // ── Toasts (one app-level host, so they survive navigation) ─────────────
    val toasts = rememberToastController()
    fun toast(message: String, icon: ToastIcon = ToastIcon.Alert) = toasts.show(ToastEvent(message, icon))
    fun comingSoon(feature: String) = toast(str(R.string.common_coming_soon, feature), ToastIcon.Alert)

    LaunchedEffect(Unit) { zunoViewModel.messages.collect { toast(it) } }

    var signOutReason by remember { mutableStateOf<SignOutReason?>(null) }
    LaunchedEffect(Unit) {
        zunoViewModel.sessionExpired.collect {
            if (authViewModel.authState.value is AuthState.SignedIn) {
                signOutReason = SignOutReason.Expired
                authViewModel.signOut()
            }
        }
    }
    LaunchedEffect(Unit) {
        zunoViewModel.levelAssessed.collect { level -> toasts.show(ToastEvent(str(R.string.level_check_done, level), ToastIcon.Check)) }
    }

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
            toast(message)
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
                    // The server picks the Breeze voice from Preferences · Zuno's voice; Cartesia voices
                    // remain for languages that have one.
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
                    toast(str(R.string.voice_call_ended), ToastIcon.Check)
                    endVoice()
                }
                callWasLive = false
            }
            else -> callWasLive = true
        }
    }

    // ── Notifications permission (Android 13+), asked at the first relevant action ──
    var afterNotificationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotificationPermission?.invoke()
        afterNotificationPermission = null
    }

    fun withNotificationPermission(action: () -> Unit) {
        if (!Reminders.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            afterNotificationPermission = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    // ── Auth transitions ────────────────────────────────────────────────────
    LaunchedEffect(authState) {
        val current = backStack.last()
        when (authState) {
            is AuthState.SignedIn -> {
                zunoViewModel.refreshAccount()
                scope.launch { Push.register(context.applicationContext, appLanguage.code) }
                if (current is ZunoRoute.Auth || current == ZunoRoute.Onboarding) navigate(ZunoRoute.Home, clearStack = true)
            }
            AuthState.SignedOut -> {
                if (voiceConversationId != null) endVoice(commit = false)
                zunoViewModel.clear()
                Reminders.clearAll(context)
                if (current.hasDrawer) {
                    navigate(ZunoRoute.Auth(AuthMode.SignIn), clearStack = true)
                    when (signOutReason) {
                        SignOutReason.User -> toasts.show(ToastEvent(str(R.string.signed_out_toast), ToastIcon.SignOut, atTop = true))
                        SignOutReason.Expired -> toasts.show(ToastEvent(str(R.string.session_expired_toast), ToastIcon.SignOut, atTop = true))
                        SignOutReason.Deleted -> toasts.show(ToastEvent(str(R.string.account_deleted_toast), ToastIcon.Trash, atTop = true))
                        null -> Unit
                    }
                }
                signOutReason = null
            }
        }
    }

    // A magic link can fail while the user isn't on the auth screen (e.g. an expired link opened from email).
    LaunchedEffect(authForm.error) {
        val error = authForm.error
        if (error != null && backStack.last() !is ZunoRoute.Auth) {
            toast(error)
            authViewModel.clearError()
        }
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
        if (granted) toast(str(R.string.home_mic_on), ToastIcon.Check)
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

    /** "Find my level": a short Free Talk where the tutor assesses the learner. */
    fun startLevelCheck() {
        if (voiceConversationId != null) endVoice()
        zunoViewModel.startLevelCheck(str(R.string.level_check_first_message)) { openChat(it.id) }
    }

    // ── Deep links from notifications ───────────────────────────────────────
    LaunchedEffect(pendingLink, authState, route) {
        val link = pendingLink ?: return@LaunchedEffect
        if (authState !is AuthState.SignedIn || route == ZunoRoute.Splash) return@LaunchedEffect
        deepLink.value = null
        when (link) {
            is DeepLink.Chat -> {
                if (voiceConversationId != null && voiceConversationId != link.conversationId) endVoice()
                backStack.clear()
                backStack.add(ZunoRoute.Home)
                backStack.add(ZunoRoute.Chat(link.conversationId))
            }
            is DeepLink.Schedule -> {
                if (voiceConversationId != null) endVoice()
                zunoViewModel.startSchedule(link.scheduleId) { conversation ->
                    backStack.clear()
                    backStack.add(ZunoRoute.Home)
                    backStack.add(ZunoRoute.Scheduled)
                    backStack.add(ZunoRoute.Chat(conversation.id))
                }
            }
            is DeepLink.Topic -> {
                backStack.clear()
                backStack.add(ZunoRoute.Home)
                backStack.add(ZunoRoute.Topic(link.topicId))
            }
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
        if (key == TOPIC_DRAFT_KEY) str(R.string.attach_limit_material, MAX_TOPIC_MATERIAL)
        else str(R.string.attach_limit_message, AttachmentFiles.MAX_PER_MESSAGE)

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
            toast(str(R.string.error_no_app))
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
            toast(str(R.string.error_no_app_link))
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
                    toast(str(R.string.error_no_app_file))
                }
            }
        }
    }

    // ── "Remind me to continue" ─────────────────────────────────────────────
    var reminderTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var customReminder by remember { mutableStateOf<ReminderOption?>(null) }

    fun confirmReminder(reminder: Reminder, summary: String) {
        scope.launch {
            // Server first (push, survives reinstall and other devices); the on-device alarm is the
            // offline fallback when push isn't available.
            val serverId = if (Push.isAvailable) zunoViewModel.createReminder(reminder.conversationId, reminder.atMillis) else null
            if (serverId == null) Reminders.schedule(context, reminder) else Reminders.cancel(context, reminder.conversationId)
            if (!Reminders.canNotify(context)) toast(str(R.string.reminder_enable_notifications))
            toasts.show(
                ToastEvent(
                    message = str(R.string.reminder_set_toast, summary),
                    icon = ToastIcon.Check,
                    actionLabel = str(R.string.common_undo),
                    onAction = {
                        if (serverId != null) zunoViewModel.cancelReminder(serverId)
                        Reminders.cancel(context, reminder.conversationId)
                    },
                ),
            )
        }
    }

    fun setReminder(conversationId: String, option: ReminderOption) {
        val chat = chats[conversationId]
        val reminder = Reminder(
            conversationId = conversationId,
            title = chat?.conversation?.title?.ifBlank { null } ?: str(R.string.reminder_your_chat),
            lastLine = chat?.messages?.lastOrNull { !it.isUser }?.text.orEmpty(),
            atMillis = option.atMillis,
        )
        withNotificationPermission { confirmReminder(reminder, option.summary) }
    }

    fun pickReminderTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute -> customReminder = customReminderOption(localizedContext, hour, minute) },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(context),
        ).show()
    }

    // ── Chat actions: rename, pin, share, report, delete ────────────────────
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var shareTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var shareBusy by remember { mutableStateOf(false) }
    var reportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    fun deleteChat(conversationId: String) {
        if (voiceConversationId == conversationId) endVoice(commit = false)
        zunoViewModel.deleteConversation(conversationId) {
            Reminders.cancel(context, conversationId)
            navigate(ZunoRoute.Home, clearStack = true)
            toasts.show(ToastEvent(str(R.string.chat_deleted_toast), ToastIcon.Trash))
        }
    }

    fun shareLink(url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        try {
            context.startActivity(Intent.createChooser(send, str(R.string.share_title)))
        } catch (_: android.content.ActivityNotFoundException) {
            toast(str(R.string.error_no_app))
        }
    }

    fun copyLink(url: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(str(R.string.share_title), url))
        toasts.show(ToastEvent(str(R.string.share_link_copied), ToastIcon.Link))
    }

    // ── New topic (optionally from a chat's material) ───────────────────────
    var topicPrefill by rememberSaveable { mutableStateOf("") }

    fun leaveNewTopic() {
        zunoViewModel.discardTopicDraft()
        pop()
    }

    // ── Scheduled sessions ──────────────────────────────────────────────────
    var scheduleSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightSchedule by remember { mutableStateOf<String?>(null) }

    fun startScheduled(schedule: Schedule) {
        if (voiceConversationId != null) endVoice()
        zunoViewModel.startSchedule(schedule.id) { openChat(it.id) }
    }

    // ── Settings: sign out, account ─────────────────────────────────────────
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadingPhoto = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { compressAvatar(context, uri) }
            if (file == null) {
                uploadingPhoto = false
                toast(str(R.string.account_photo_unreadable))
                return@launch
            }
            zunoViewModel.uploadAvatar(file) { ok ->
                uploadingPhoto = false
                if (ok) toasts.show(ToastEvent(str(R.string.account_photo_updated), ToastIcon.Check))
            }
        }
    }

    fun contactSupport() {
        val body = str(
            R.string.support_email_body,
            BuildConfig.VERSION_NAME,
            "${Build.MANUFACTURER} ${Build.MODEL}",
            Build.VERSION.RELEASE,
        )
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, str(R.string.support_email_subject))
            .putExtra(Intent.EXTRA_TEXT, body)
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            toast(str(R.string.support_no_email_app, SUPPORT_EMAIL))
        }
    }

    // ── Grammar explanations ────────────────────────────────────────────────
    var explanation by remember { mutableStateOf<ExplanationState?>(null) }
    fun explain(message: Message) {
        explanation = ExplanationState(message.text, null)
        scope.launch {
            val result = zunoViewModel.explain(message.id)
            explanation = explanation?.takeIf { it.source == message.text }?.copy(
                text = result.getOrElse { it.message ?: str(R.string.explain_failed) },
                loaded = true,
            )
        }
    }

    val activeDestination = when (route) {
        ZunoRoute.Home -> DrawerDestination.FreeTalk
        ZunoRoute.Scheduled, is ZunoRoute.NewSchedule -> DrawerDestination.Scheduled
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

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = route.hasDrawer,
        scrimColor = ZunoColors.BgScrim,
        modifier = modifier,
        drawerContent = {
            AppDrawer(
                userName = profile?.displayName.orEmpty(),
                userDetail = profile?.let { "${language.displayName(appLanguage)} · ${it.level}" }.orEmpty(),
                userImage = profile?.imageUrl,
                showUpgrade = profile?.isPro != true,
                activeDestination = activeDestination,
                sessions = recent,
                selectedSessionId = selectedSessionId,
                onProfile = { closeDrawerThen { navigate(ZunoRoute.Settings) } },
                onNotifications = { closeDrawerThen { navigate(ZunoRoute.SettingsNotifications) } },
                onDestination = { destination ->
                    closeDrawerThen {
                        if (voiceConversationId != null) endVoice()
                        when (destination) {
                            DrawerDestination.FreeTalk -> navigate(ZunoRoute.Home, clearStack = true)
                            DrawerDestination.Scheduled -> openSection(ZunoRoute.Scheduled)
                            DrawerDestination.Topics -> openSection(ZunoRoute.Topics)
                            DrawerDestination.Roleplays -> openSection(ZunoRoute.Roleplays)
                            else -> comingSoon(str(destination.labelRes))
                        }
                    }
                },
                onSearchSessions = { comingSoon(str(R.string.drawer_search_sessions)) },
                onSession = { session ->
                    closeDrawerThen {
                        if (voiceConversationId != null) endVoice()
                        val pinnedChat = session.conversationId.takeIf { session.pinned && session.kind != RecentKind.FreeTalk }
                        when {
                            pinnedChat != null -> openChat(pinnedChat)
                            session.kind == RecentKind.Topic -> navigate(ZunoRoute.Topic(session.id))
                            session.kind == RecentKind.Roleplay -> zunoViewModel.openRoleplay(session.id) { openChat(it.id) }
                            else -> openChat(session.id)
                        }
                    }
                },
                onUpgrade = { comingSoon("Zuno Pro") },
                onSettings = { closeDrawerThen { navigate(ZunoRoute.Settings) } },
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
                    onOpenTerms = { openUrl(TERMS_URL) },
                    onOpenPrivacy = { openUrl(PRIVACY_URL) },
                    modifier = screenModifier,
                )

                is ZunoRoute.Auth -> AuthScreen(
                    mode = screen.mode,
                    onBack = {
                        authViewModel.clearError()
                        if (backStack.size > 1) pop() else replace(ZunoRoute.Onboarding)
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
                    copy = HomeCopy.forLanguage(
                        language,
                        profile?.firstName.orEmpty(),
                        profile?.firstName?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_greeting_named, it) }
                            ?: stringResource(R.string.home_greeting),
                    ),
                    voiceState = homeVoiceState,
                    liveTranscript = speech.partial,
                    audioLevel = { speech.level },
                    micBlocked = micBlocked,
                    busy = busy,
                    typingHint = typingHint,
                    selector = profile?.let { "${language.displayName(appLanguage)} · ${it.level}" } ?: "${language.displayName(appLanguage)} · …",
                    onOpenDrawer = { openDrawer() },
                    onSelector = { navigate(ZunoRoute.SettingsLearning) },
                    onHoldStart = {
                        when {
                            !micGranted() -> requestMicrophone()
                            !speech.isAvailable -> toast(str(R.string.home_speech_unavailable))
                            else -> {
                                tts.stop()
                                homeVoiceState = HomeVoiceState.Listening
                                speech.start(language.speechTag(accent)) { transcript ->
                                    if (transcript.isNullOrBlank()) {
                                        homeVoiceState = HomeVoiceState.Idle
                                        toast(str(R.string.home_didnt_catch))
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
                    onDraftChanged = { if (preferences?.suggestReplies != false) zunoViewModel.onDraftChanged(it) },
                    onSendText = { text -> startFreeTalk(text) },
                    onTypeModeChanged = { if (!it) zunoViewModel.clearHint() },
                    drafts = drafts[HOME_DRAFT_KEY].orEmpty(),
                    onAttach = { openAttachmentSheet(HOME_DRAFT_KEY) },
                    onRemoveAttachment = { zunoViewModel.removeAttachment(HOME_DRAFT_KEY, it.localId) },
                    onRetryAttachment = { zunoViewModel.retryAttachment(HOME_DRAFT_KEY, it.localId) },
                    modifier = screenModifier,
                )

                ZunoRoute.Scheduled -> {
                    LaunchedEffect(Unit) { zunoViewModel.loadSchedules() }
                    ScheduledScreen(
                        schedules = schedules,
                        highlightId = highlightSchedule,
                        onBack = ::pop,
                        onRetry = zunoViewModel::loadSchedules,
                        onCreate = { navigate(ZunoRoute.NewSchedule()) },
                        onOpen = { scheduleSheet = it.id },
                        onStart = ::startScheduled,
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.NewSchedule -> {
                    LaunchedEffect(Unit) { zunoViewModel.loadScheduleTargets() }
                    val editing = screen.editId?.let { id -> schedules.items?.firstOrNull { it.id == id } }
                    NewScheduleScreen(
                        targets = scheduleTargets,
                        editing = editing,
                        busy = busy,
                        onBack = ::pop,
                        onRetry = zunoViewModel::loadScheduleTargets,
                        onSave = { draft ->
                            withNotificationPermission {
                                zunoViewModel.saveSchedule(screen.editId, draft) { saved ->
                                    highlightSchedule = saved.id
                                    if (backStack.lastOrNull() is ZunoRoute.NewSchedule) pop()
                                    if (backStack.lastOrNull() != ZunoRoute.Scheduled) navigate(ZunoRoute.Scheduled)
                                    val phrase = ScheduleFormat.phrase(localizedContext, saved)
                                    toasts.show(
                                        if (screen.editId == null) {
                                            ToastEvent(
                                                message = str(R.string.schedule_added_toast, phrase),
                                                icon = ToastIcon.Check,
                                                actionLabel = str(R.string.common_undo),
                                                onAction = { zunoViewModel.deleteSchedule(saved.id) },
                                            )
                                        } else {
                                            ToastEvent(str(R.string.schedule_updated_toast, phrase), ToastIcon.Check)
                                        },
                                    )
                                }
                            }
                        },
                        modifier = screenModifier,
                    )
                }

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
                            add(ZunoMenuAction(stringResource(R.string.chat_menu_rename), icon = R.drawable.zuno_ic_t_pencil) {
                                renameTarget = screen.conversationId
                            })
                            add(
                                ZunoMenuAction(
                                    stringResource(if (conversation.pinned) R.string.chat_menu_unpin else R.string.chat_menu_pin),
                                    icon = if (conversation.pinned) R.drawable.zuno_ic_t_pin_off else R.drawable.zuno_ic_t_pin,
                                ) {
                                    zunoViewModel.setPinned(screen.conversationId, !conversation.pinned) { pinned ->
                                        toasts.show(
                                            ToastEvent(str(if (pinned) R.string.chat_pinned_toast else R.string.chat_unpinned_toast), ToastIcon.Pin),
                                        )
                                    }
                                },
                            )
                            add(ZunoMenuAction(stringResource(R.string.chat_menu_share), icon = R.drawable.zuno_ic_t_share) {
                                shareTarget = screen.conversationId
                            })
                            add(ZunoMenuAction(stringResource(R.string.chat_menu_report), icon = R.drawable.zuno_ic_t_flag) {
                                reportTarget = screen.conversationId
                            })
                            add(
                                ZunoMenuAction(
                                    stringResource(R.string.chat_menu_turn_into_topic),
                                    icon = R.drawable.zuno_ic_t_book_open,
                                    dividerBefore = true,
                                ) {
                                    if (inVoice) endVoice()
                                    topicPrefill = zunoViewModel.prefillTopicFromChat(screen.conversationId)
                                    navigate(ZunoRoute.NewTopic)
                                },
                            )
                        }
                        if (conversation?.kind == ConversationKind.Lesson && conversation.topicId != null && conversation.lessonId != null) {
                            add(ZunoMenuAction(stringResource(R.string.chat_menu_complete_lesson), icon = R.drawable.zuno_ic_t_check_circle) {
                                zunoViewModel.completeLesson(conversation.topicId, conversation.lessonId)
                            })
                            add(ZunoMenuAction(stringResource(R.string.chat_menu_back_to_topic), icon = R.drawable.zuno_ic_t_arrow_right) {
                                navigate(ZunoRoute.Topic(conversation.topicId))
                            })
                        }
                        if (conversation?.kind == ConversationKind.Roleplay && conversation.roleplayId != null) {
                            add(ZunoMenuAction(stringResource(R.string.chat_menu_start_over), icon = R.drawable.zuno_ic_t_rotate_ccw) {
                                if (inVoice) endVoice()
                                zunoViewModel.openRoleplay(conversation.roleplayId, restart = true) { replace(ZunoRoute.Chat(it.id)) }
                            })
                        }
                        add(
                            ZunoMenuAction(
                                stringResource(R.string.chat_menu_delete),
                                icon = R.drawable.zuno_ic_t_trash,
                                destructive = true,
                                dividerBefore = true,
                            ) { deleteTarget = screen.conversationId },
                        )
                    }
                    ChatScreen(
                        title = conversation?.title.orEmpty(),
                        sessionLabel = conversation?.let { sessionLabel(localizedContext, it) }.orEmpty(),
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
                        placeholder = language.placeholder,
                        typingHint = typingHint,
                        menu = menu,
                        onBack = {
                            if (inVoice) endVoice()
                            pop()
                        },
                        onRetryLoad = { zunoViewModel.loadConversation(screen.conversationId) },
                        onSend = { text -> zunoViewModel.send(screen.conversationId, text) },
                        onDraftChanged = { if (preferences?.suggestReplies != false) zunoViewModel.onDraftChanged(it) },
                        savedDraft = { zunoViewModel.draftText(screen.conversationId) },
                        onSaveDraft = { zunoViewModel.saveDraftText(screen.conversationId, it) },
                        onStartVoice = {
                            if (chat?.pendingText == null) startVoice(screen.conversationId)
                            else toast(str(R.string.chat_still_replying))
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
                        showTranslations = preferences?.showTranslations != false,
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.Settings -> SettingsOverviewScreen(
                    profile = profile,
                    appLanguage = appLanguage,
                    onBack = ::pop,
                    onUpgrade = { comingSoon("Zuno Pro") },
                    onAccount = { navigate(ZunoRoute.SettingsAccount) },
                    onLearning = { navigate(ZunoRoute.SettingsLearning) },
                    onAppLanguage = { navigate(ZunoRoute.SettingsAppLanguage) },
                    onPreferences = { navigate(ZunoRoute.SettingsPreferences) },
                    onNotifications = { navigate(ZunoRoute.SettingsNotifications) },
                    onAbout = { showAbout = true },
                    onContactSupport = ::contactSupport,
                    onSignOut = { confirmSignOut = true },
                    onDeleteAccount = { confirmDeleteAccount = true },
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsPreferences -> SettingsPreferencesScreen(
                    preferences = preferences ?: com.talkie.ai.data.Preferences(),
                    targetLanguage = language,
                    appLanguage = appLanguage,
                    onChange = zunoViewModel::updatePreferences,
                    onBack = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsAppLanguage -> SettingsAppLanguageScreen(
                    selectedLanguage = appLanguage,
                    onSelectLanguage = ::selectAppLanguage,
                    onBack = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsLearning -> SettingsLearningScreen(
                    profile = profile,
                    appLanguage = appLanguage,
                    onOpenLanguages = { navigate(ZunoRoute.SettingsLearningLanguage) },
                    onAccentChange = { zunoViewModel.updateProfile(accent = it) },
                    onLevelChange = { zunoViewModel.updateProfile(level = it) },
                    onFindLevel = ::startLevelCheck,
                    onPreferencesChange = zunoViewModel::updatePreferences,
                    onBack = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsLearningLanguage -> SettingsLearningLanguageScreen(
                    languages = languages,
                    profile = profile,
                    appLanguage = appLanguage,
                    busy = busy,
                    onSwitch = { target ->
                        if (voiceConversationId != null) endVoice()
                        zunoViewModel.switchLearningLanguage(target.code) { updated ->
                            if (backStack.lastOrNull() == ZunoRoute.SettingsLearningLanguage) pop()
                            toasts.show(
                                ToastEvent(str(R.string.learning_language_switched, updated.language.displayName(localizedContext, appLanguage)), ToastIcon.Check),
                            )
                        }
                    },
                    onBack = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsAccount -> SettingsAccountScreen(
                    profile = profile,
                    uploadingPhoto = uploadingPhoto,
                    onUpdateName = { zunoViewModel.updateProfile(name = it) },
                    onChangePhoto = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPlanClick = { comingSoon("Zuno Pro") },
                    onDownloadData = { comingSoon(str(R.string.settings_acc_download_data)) },
                    onDeleteAccount = { confirmDeleteAccount = true },
                    onBack = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsNotifications -> {
                    LaunchedEffect(Unit) { zunoViewModel.loadNotificationPrefs() }
                    SettingsNotificationsScreen(
                        prefs = notificationPrefs,
                        onChange = { transform ->
                            val before = notificationPrefs
                            zunoViewModel.updateNotificationPrefs(transform)
                            val after = before?.let(transform)
                            val turnedOnPush = before != null && after != null &&
                                ((!before.dailyReminder && after.dailyReminder) || (!before.scheduledSessions && after.scheduledSessions) ||
                                    (!before.progressMilestones && after.progressMilestones))
                            if (turnedOnPush) withNotificationPermission {}
                        },
                        onOpenSystemSettings = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        onBack = ::pop,
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
            title = stringResource(if (key == TOPIC_DRAFT_KEY) R.string.attach_title_material else R.string.attach_title_chat),
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
            options = remember(conversationId) { reminderOptions(localizedContext) },
            custom = customReminder,
            onPickTime = ::pickReminderTime,
            onDismiss = { reminderTarget = null },
            onSet = { option ->
                reminderTarget = null
                setReminder(conversationId, option)
            },
        )
    }

    renameTarget?.let { id ->
        val current = chats[id]?.conversation
        if (current == null) {
            renameTarget = null
        } else {
            ZunoInputDialog(
                title = stringResource(R.string.chat_rename_title),
                initialValue = current.title,
                confirmLabel = stringResource(R.string.common_save),
                onConfirm = { title ->
                    renameTarget = null
                    zunoViewModel.renameConversation(id, title) {
                        toasts.show(ToastEvent(str(R.string.chat_renamed_toast), ToastIcon.Check))
                    }
                },
                onDismiss = { renameTarget = null },
            )
        }
    }

    shareTarget?.let { id ->
        val current = chats[id]?.conversation
        ZunoShareSheet(
            isPublic = current?.public == true,
            shareUrl = current?.shareUrl,
            busy = shareBusy,
            onSelectPublic = { public ->
                shareBusy = true
                scope.launch {
                    runCatching { zunoViewModel.setShare(id, public) }
                    shareBusy = false
                }
            },
            onShare = { current?.shareUrl?.let(::shareLink) },
            onCopyLink = { current?.shareUrl?.let(::copyLink) },
            onDismiss = { shareTarget = null },
        )
    }

    reportTarget?.let { id ->
        ZunoReportSheet(
            busy = busy,
            onSubmit = { reason, details ->
                zunoViewModel.reportConversation(id, reason, details) {
                    reportTarget = null
                    toasts.show(ToastEvent(str(R.string.report_sent_toast), ToastIcon.Check))
                }
            },
            onDismiss = { reportTarget = null },
        )
    }

    deleteTarget?.let { id ->
        val title = chats[id]?.conversation?.title.orEmpty()
        ZunoDialog(
            title = stringResource(R.string.chat_delete_title),
            body = stringResource(R.string.chat_delete_body, title),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                deleteTarget = null
                deleteChat(id)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    scheduleSheet?.let { id ->
        val schedule = schedules.items?.firstOrNull { it.id == id }
        if (schedule == null) {
            scheduleSheet = null
        } else {
            ScheduleActionsSheet(
                schedule = schedule,
                onDismiss = { scheduleSheet = null },
                onStart = {
                    scheduleSheet = null
                    startScheduled(schedule)
                },
                onEdit = {
                    scheduleSheet = null
                    navigate(ZunoRoute.NewSchedule(schedule.id))
                },
                onSkip = {
                    scheduleSheet = null
                    zunoViewModel.skipNextSchedule(schedule.id) { updated ->
                        val next = updated.nextRunAtMillis
                        toasts.show(
                            ToastEvent(
                                if (next != null) {
                                    str(R.string.schedule_skipped_toast, ScheduleFormat.date(localizedContext, ScheduleFormat.localDate(next)))
                                } else {
                                    str(R.string.schedule_skipped_done_toast)
                                },
                                ToastIcon.Calendar,
                            ),
                        )
                    }
                },
                onDelete = {
                    scheduleSheet = null
                    scheduleDelete = schedule.id
                },
            )
        }
    }

    scheduleDelete?.let { id ->
        val title = schedules.items?.firstOrNull { it.id == id }?.title.orEmpty()
        ZunoDialog(
            title = stringResource(R.string.schedule_delete_title),
            body = stringResource(R.string.schedule_delete_body, title),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = {
                scheduleDelete = null
                zunoViewModel.deleteSchedule(id) { toasts.show(ToastEvent(str(R.string.schedule_deleted_toast), ToastIcon.Trash)) }
            },
            onDismiss = { scheduleDelete = null },
        )
    }

    if (confirmSignOut) {
        ZunoDialog(
            title = stringResource(R.string.signout_title),
            body = stringResource(R.string.signout_body),
            confirmLabel = stringResource(R.string.settings_sign_out),
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                signOutReason = SignOutReason.User
                authViewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (confirmDeleteAccount) {
        ZunoDialog(
            title = stringResource(R.string.account_delete_title),
            body = stringResource(R.string.account_delete_body),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            confirmLoading = busy,
            onConfirm = {
                zunoViewModel.deleteAccount {
                    confirmDeleteAccount = false
                    signOutReason = SignOutReason.Deleted
                    authViewModel.forgetSession()
                }
            },
            onDismiss = { confirmDeleteAccount = false },
        )
    }

    if (showAbout) {
        ZunoDialog(
            title = stringResource(R.string.about_title),
            body = stringResource(R.string.about_body, BuildConfig.VERSION_NAME),
            confirmLabel = stringResource(R.string.common_done),
            showDismiss = false,
            onConfirm = { showAbout = false },
            onDismiss = { showAbout = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                ZunoSheetAction(R.drawable.zuno_ic_t_external_link, stringResource(R.string.about_terms), { openUrl(TERMS_URL) })
                ZunoSheetAction(R.drawable.zuno_ic_t_external_link, stringResource(R.string.about_privacy), { openUrl(PRIVACY_URL) })
            }
        }
    }

    viewing?.let { ZunoImageViewer(it, onDismiss = { viewing = null }) }

    explanation?.let { state ->
        ZunoDialog(
            title = stringResource(R.string.explain_title),
            confirmLabel = stringResource(R.string.common_got_it),
            onConfirm = { explanation = null },
            onDismiss = { explanation = null },
            showDismiss = false,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
                Text(
                    if (state.loaded) state.text.orEmpty() else stringResource(R.string.explain_thinking),
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                )
            }
        }
    }

    ZunoToastHost(toasts)
    }
}

/** Live call → glow. The mic stays open between turns, so an idle call still listens to the user. */
private fun voiceGlowState(agentState: AgentState, isMuted: Boolean): AgentGlowState = when (agentState) {
    AgentState.DISCONNECTED, AgentState.CONNECTING, AgentState.THINKING -> AgentGlowState.Thinking
    AgentState.SPEAKING -> AgentGlowState.Speaking
    AgentState.IDLE, AgentState.LISTENING -> if (isMuted) AgentGlowState.Disabled else AgentGlowState.Listening
}

/** "New Free Talk · Just now", "Roleplay · 3 hours ago", … */
private fun sessionLabel(context: Context, conversation: Conversation): String {
    val age = System.currentTimeMillis() - conversation.createdAtMillis
    val kind = context.getString(
        when {
            conversation.levelCheck -> R.string.session_kind_level_check
            conversation.kind == ConversationKind.Roleplay -> R.string.session_kind_roleplay
            conversation.kind == ConversationKind.Lesson -> R.string.session_kind_lesson
            else -> R.string.session_kind_free_talk
        },
    )
    if (age < 5 * DateUtils.MINUTE_IN_MILLIS) return context.getString(R.string.session_label_new, kind)
    val relative = DateUtils.getRelativeTimeSpanString(
        conversation.createdAtMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "$kind · $relative"
}

/** The learning language's name in the app language, outside composition. */
private fun LearningLanguage.displayName(context: Context, appLanguage: AppLanguage): String {
    val locale = Locale.forLanguageTag(appLanguage.bcp47)
    return Locale.forLanguageTag(code).getDisplayLanguage(locale)
        .takeIf { it.isNotBlank() && it != code }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        ?: englishName
}

private data class ExplanationState(val source: String, val text: String?, val loaded: Boolean = false)

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
        val inAnHour = context.getString(R.string.reminder_in_one_hour)
        add(ReminderOption(inAnHour, inAnHour, now + DateUtils.HOUR_IN_MILLIS))
        if (tonight - now >= DateUtils.HOUR_IN_MILLIS) {
            val time = timeLabel(context, tonight)
            add(ReminderOption(context.getString(R.string.reminder_tonight_chip, time), context.getString(R.string.reminder_tonight, time), tonight, isTonight = true))
        }
        val time = timeLabel(context, tomorrow)
        add(ReminderOption(context.getString(R.string.reminder_tomorrow_chip, time), context.getString(R.string.reminder_tomorrow, time), tomorrow))
    }
}

/** The next [hour]:[minute] — today if it's still ahead, otherwise tomorrow. */
private fun customReminderOption(context: Context, hour: Int, minute: Int): ReminderOption {
    val today = atTime(0, hour, minute)
    val isToday = today > System.currentTimeMillis()
    val at = if (isToday) today else atTime(1, hour, minute)
    val time = timeLabel(context, at)
    return if (isToday) {
        ReminderOption(context.getString(R.string.reminder_today_chip, time), context.getString(R.string.reminder_today, time), at, icon = R.drawable.zuno_ic_clock_small)
    } else {
        ReminderOption(context.getString(R.string.reminder_tomorrow_chip, time), context.getString(R.string.reminder_tomorrow, time), at, icon = R.drawable.zuno_ic_clock_small)
    }
}

/** Reads, downsizes (max 1024px) and re-encodes a picked photo as JPEG for the avatar upload. */
private fun compressAvatar(context: Context, uri: Uri): File? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val largest = maxOf(bounds.outWidth, bounds.outHeight).takeIf { it > 0 } ?: return null
    var sample = 1
    while (largest / (sample * 2) >= 1024) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val scale = 1024f / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else {
        decoded
    }
    // Centre-crop to a square so the round avatar isn't distorted.
    val side = minOf(bitmap.width, bitmap.height)
    val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    val file = File(context.cacheDir, "avatar-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    file
}.getOrNull()

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
private fun rememberTutorTts(locale: Locale): TutorTts {
    val context = LocalContext.current
    val holder = remember { TutorTts(context, locale) }
    LaunchedEffect(locale) { holder.setLocale(locale) }
    DisposableEffect(holder) {
        onDispose { holder.shutdown() }
    }
    return holder
}
