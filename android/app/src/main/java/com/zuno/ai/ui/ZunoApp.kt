package com.zuno.ai.ui

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
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zuno.ai.BuildConfig
import com.zuno.ai.R
import com.zuno.ai.auth.AuthState
import com.zuno.ai.auth.AuthViewModel
import com.zuno.ai.avatar.catalog.LocalActiveTutor
import com.zuno.ai.avatar.catalog.TutorAvatarCatalog
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.Attachment
import com.zuno.ai.data.AttachmentFiles
import com.zuno.ai.data.AttachmentKind
import com.zuno.ai.data.ChatsFilter
import com.zuno.ai.data.Conversation
import com.zuno.ai.data.ConversationKind
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Message
import com.zuno.ai.data.RecentKind
import com.zuno.ai.data.Schedule
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.rememberCartesiaAudioPlayer
import com.zuno.ai.model.AgentState
import com.zuno.ai.model.Speaker
import com.zuno.ai.push.CallNotification
import com.zuno.ai.push.DeepLink
import com.zuno.ai.push.OngoingCallAction
import com.zuno.ai.push.Push
import com.zuno.ai.reminders.Reminder
import com.zuno.ai.reminders.Reminders
import com.zuno.ai.ui.components.ContinueSessionUi
import com.zuno.ai.ui.components.HomeNavTab
import com.zuno.ai.ui.components.ReminderOption
import com.zuno.ai.ui.components.ZunoAttachSheet
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoImageViewer
import com.zuno.ai.ui.components.ZunoInputDialog
import com.zuno.ai.ui.components.ZunoMenuAction
import com.zuno.ai.ui.components.ZunoPasteLinkSheet
import com.zuno.ai.ui.components.ZunoReminderSheet
import com.zuno.ai.ui.components.ZunoReportSheet
import com.zuno.ai.ui.components.ZunoShareSheet
import com.zuno.ai.ui.components.ZunoSheetAction
import com.zuno.ai.ui.components.ZunoSpinner
import com.zuno.ai.ui.components.ZunoToastHost
import com.zuno.ai.ui.components.rememberToastController
import com.zuno.ai.ui.screens.ActiveLiveRoomScreen
import com.zuno.ai.ui.screens.AuthMode
import com.zuno.ai.ui.screens.AuthScreen
import com.zuno.ai.ui.screens.ChatScreen
import com.zuno.ai.ui.screens.ChatsScreen
import com.zuno.ai.ui.screens.CommunityRoleplayPreviewScreen
import com.zuno.ai.ui.screens.CommunityScreen
import com.zuno.ai.ui.screens.CommunityTopicPreviewScreen
import com.zuno.ai.ui.screens.CreateLiveRoomScreen
import com.zuno.ai.ui.screens.ExploreRoleplaysScreen
import com.zuno.ai.ui.screens.ExploreTopicsScreen
import com.zuno.ai.ui.screens.HomeCopy
import com.zuno.ai.ui.screens.HomeScreen
import com.zuno.ai.ui.screens.LessonEvaluationScreen
import com.zuno.ai.ui.screens.LiveRoomLobbyScreen
import com.zuno.ai.ui.screens.LiveRoomsScreen
import com.zuno.ai.ui.screens.PaywallScreen
import com.zuno.ai.ui.screens.PeerCallScreen
import com.zuno.ai.ui.screens.PeerChatScreen
import com.zuno.ai.ui.screens.PostCallSavePhrasesSheet
import com.zuno.ai.ui.screens.ProgressScreen
import com.zuno.ai.ui.screens.RoleplaySettingsScreen
import com.zuno.ai.ui.screens.SprintEvaluationScreen
import com.zuno.ai.ui.screens.SprintReviewResult
import com.zuno.ai.ui.screens.SystemRoomDetailScreen
import com.zuno.ai.ui.screens.VocabularyScreen
import com.zuno.ai.ui.screens.VocabularySprintScreen
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.screens.VoiceModeScreen
import com.zuno.ai.ui.screens.WelcomeToProScreen
import com.zuno.ai.ui.screens.NewRoleplayScreen
import com.zuno.ai.ui.screens.NewScheduleScreen
import com.zuno.ai.ui.screens.NewTopicScreen
import com.zuno.ai.ui.screens.OnboardingFlowScreen
import com.zuno.ai.ui.screens.OnboardingScreen
import com.zuno.ai.ui.screens.RoleplaysScreen
import com.zuno.ai.ui.screens.ScheduleActionsSheet
import com.zuno.ai.ui.screens.ScheduleFormat
import com.zuno.ai.ui.screens.ScheduledScreen
import com.zuno.ai.ui.screens.SettingsAccountScreen
import com.zuno.ai.ui.screens.SettingsAppLanguageScreen
import com.zuno.ai.ui.screens.SettingsLearningLanguageScreen
import com.zuno.ai.ui.screens.SettingsLearningScreen
import com.zuno.ai.ui.screens.SettingsNotificationsScreen
import com.zuno.ai.ui.screens.SettingsOverviewScreen
import com.zuno.ai.ui.screens.SettingsPreferencesScreen
import com.zuno.ai.ui.screens.SettingsPrivacyScreen
import com.zuno.ai.ui.screens.SettingsTutorScreen
import com.zuno.ai.ui.screens.SplashScreen
import com.zuno.ai.ui.screens.TopicScreen
import com.zuno.ai.ui.screens.TopicSettingsScreen
import com.zuno.ai.ui.screens.TopicsScreen
import com.zuno.ai.ui.screens.displayName
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.viewmodel.HOME_DRAFT_KEY
import com.zuno.ai.viewmodel.MAX_TOPIC_MATERIAL
import com.zuno.ai.viewmodel.OnboardingViewModel
import com.zuno.ai.viewmodel.ProfileSyncState
import com.zuno.ai.viewmodel.ROLEPLAY_DRAFT_KEY
import com.zuno.ai.viewmodel.TOPIC_DRAFT_KEY
import com.zuno.ai.viewmodel.ToastEvent
import com.zuno.ai.viewmodel.ToastIcon
import com.zuno.ai.viewmodel.VoiceSessionViewModel
import com.zuno.ai.viewmodel.ZunoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    /** Post-auth guided onboarding (`5a`–`5f`: Target language, Voice/Chat Q&A, Path generation). */
    data object OnboardingSetup : ZunoRoute
    data class Auth(val mode: AuthMode) : ZunoRoute
    data object Home : ZunoRoute
    data class Chats(val initialFilter: ChatsFilter = ChatsFilter.All) : ZunoRoute
    data class PeerChat(val peerConversationId: String, val partnerId: String) : ZunoRoute
    data class PeerCall(val callId: String, val peerConversationId: String, val partnerId: String) : ZunoRoute
    data object Scheduled : ZunoRoute
    /** New schedule, or editing [editId], or pre-filling a target Topic/Roleplay. */
    data class NewSchedule(
        val editId: String? = null,
        val prefillTargetId: String? = null,
        val prefillIsRoleplay: Boolean = false,
    ) : ZunoRoute
    data object Topics : ZunoRoute
    data class ExploreTopics(val initialQuery: String = "") : ZunoRoute
    data class CommunityTopicPreview(val idOrToken: String) : ZunoRoute
    data object NewTopic : ZunoRoute
    data class TopicSettings(val id: String) : ZunoRoute
    data class Topic(val id: String) : ZunoRoute
    data object Roleplays : ZunoRoute
    data class ExploreRoleplays(val initialQuery: String = "") : ZunoRoute
    data class CommunityRoleplayPreview(val idOrToken: String) : ZunoRoute
    data object NewRoleplay : ZunoRoute
    data class RoleplaySettings(val id: String) : ZunoRoute
    data class Chat(val conversationId: String) : ZunoRoute
    /** Live voice call for a conversation (voice mode). */
    data class Voice(val conversationId: String) : ZunoRoute
    /** 03a–03f interactive 5-stage voice lesson. */
    data class VoiceLesson(
        val topicId: String,
        val lessonId: String,
        val conversationId: String,
        val position: Int,
        val totalLessons: Int,
    ) : ZunoRoute
    /** 04 · Lesson complete — Evaluation. */
    data class LessonEvaluation(val topicId: String, val lessonId: String) : ZunoRoute
    data object Settings : ZunoRoute
    data object SettingsPreferences : ZunoRoute
    data object SettingsTutor : ZunoRoute
    data object SettingsAppLanguage : ZunoRoute
    data object SettingsLearning : ZunoRoute
    data object SettingsLearningLanguage : ZunoRoute
    data object SettingsAccount : ZunoRoute
    data object SettingsPrivacy : ZunoRoute
    data object SettingsNotifications : ZunoRoute
    data object Paywall : ZunoRoute
    data object WelcomeToPro : ZunoRoute
    data object Vocabulary : ZunoRoute
    data class VocabularySprint(val dueOnly: Boolean = true, val phraseIds: List<String> = emptyList()) : ZunoRoute
    data object VocabularySprintEval : ZunoRoute
    data object Progress : ZunoRoute
    data object Community : ZunoRoute
    data object LiveRooms : ZunoRoute
    data class CreateLiveRoom(val isPrivate: Boolean = false) : ZunoRoute
    data class LiveRoomLobby(val roomId: String) : ZunoRoute
    data class SystemRoomDetail(val roomId: String) : ZunoRoute
    data class ActiveLiveRoom(val roomId: String) : ZunoRoute

    /** Routes inside the signed-in shell. */
    val isSignedInShell: Boolean get() = this !is Splash && this !is Onboarding && this !is OnboardingSetup && this !is Auth
}

private fun ZunoRoute.encode(): String = when (this) {
    ZunoRoute.Splash -> "splash"
    ZunoRoute.Onboarding -> "onboarding"
    ZunoRoute.OnboardingSetup -> "onboarding-setup"
    is ZunoRoute.Auth -> "auth:${mode.name}"
    ZunoRoute.Home -> "home"
    is ZunoRoute.Chats -> "chats:${initialFilter.name}"
    is ZunoRoute.PeerChat -> "peer-chat:$peerConversationId:$partnerId"
    is ZunoRoute.PeerCall -> "peer-call:$callId:$peerConversationId:$partnerId"
    ZunoRoute.Scheduled -> "scheduled"
    is ZunoRoute.NewSchedule -> "new-schedule:${editId.orEmpty()}:${prefillTargetId.orEmpty()}:$prefillIsRoleplay"
    ZunoRoute.Topics -> "topics"
    is ZunoRoute.ExploreTopics -> "explore-topics:$initialQuery"
    is ZunoRoute.CommunityTopicPreview -> "topic-preview:$idOrToken"
    ZunoRoute.NewTopic -> "new-topic"
    is ZunoRoute.TopicSettings -> "topic-settings:$id"
    is ZunoRoute.Topic -> "topic:$id"
    ZunoRoute.Roleplays -> "roleplays"
    is ZunoRoute.ExploreRoleplays -> "explore-roleplays:$initialQuery"
    is ZunoRoute.CommunityRoleplayPreview -> "roleplay-preview:$idOrToken"
    ZunoRoute.NewRoleplay -> "new-roleplay"
    is ZunoRoute.RoleplaySettings -> "roleplay-settings:$id"
    is ZunoRoute.Chat -> "chat:$conversationId"
    is ZunoRoute.Voice -> "voice:$conversationId"
    is ZunoRoute.VoiceLesson -> "voice-lesson:$topicId:$lessonId:$conversationId:$position:$totalLessons"
    is ZunoRoute.LessonEvaluation -> "lesson-eval:$topicId:$lessonId"
    ZunoRoute.Settings -> "settings"
    ZunoRoute.SettingsPreferences -> "settings:preferences"
    ZunoRoute.SettingsTutor -> "settings:tutor"
    ZunoRoute.SettingsAppLanguage -> "settings:app-language"
    ZunoRoute.SettingsLearning -> "settings:learning"
    ZunoRoute.SettingsLearningLanguage -> "settings:learning-language"
    ZunoRoute.SettingsAccount -> "settings:account"
    ZunoRoute.SettingsPrivacy -> "settings:privacy"
    ZunoRoute.SettingsNotifications -> "settings:notifications"
    ZunoRoute.Paywall -> "paywall"
    ZunoRoute.WelcomeToPro -> "welcome-to-pro"
    ZunoRoute.Vocabulary -> "vocabulary"
    is ZunoRoute.VocabularySprint -> "vocab-sprint:$dueOnly:${phraseIds.joinToString(",")}"
    ZunoRoute.VocabularySprintEval -> "vocab-sprint-eval"
    ZunoRoute.Progress -> "progress"
    ZunoRoute.Community -> "community"
    ZunoRoute.LiveRooms -> "live-rooms"
    is ZunoRoute.CreateLiveRoom -> "create-live-room:$isPrivate"
    is ZunoRoute.LiveRoomLobby -> "live-room-lobby:$roomId"
    is ZunoRoute.SystemRoomDetail -> "system-room-detail:$roomId"
    is ZunoRoute.ActiveLiveRoom -> "active-live-room:$roomId"
}

private fun decodeRoute(value: String): ZunoRoute = when {
    value == "onboarding" -> ZunoRoute.Onboarding
    value == "onboarding-setup" -> ZunoRoute.OnboardingSetup
    value.startsWith("auth:") -> ZunoRoute.Auth(AuthMode.valueOf(value.removePrefix("auth:")))
    value == "home" -> ZunoRoute.Home
    value == "chats" -> ZunoRoute.Chats()
    value.startsWith("chats:") -> ZunoRoute.Chats(
        runCatching { ChatsFilter.valueOf(value.removePrefix("chats:")) }.getOrDefault(ChatsFilter.All),
    )
    value.startsWith("peer-chat:") -> {
        val parts = value.removePrefix("peer-chat:").split(':')
        if (parts.size >= 2) ZunoRoute.PeerChat(parts[0], parts[1]) else ZunoRoute.Chats()
    }
    value.startsWith("peer-call:") -> {
        val parts = value.removePrefix("peer-call:").split(':')
        if (parts.size >= 3) ZunoRoute.PeerCall(parts[0], parts[1], parts[2]) else ZunoRoute.Chats()
    }
    value == "scheduled" -> ZunoRoute.Scheduled
    value.startsWith("new-schedule:") -> {
        val parts = value.removePrefix("new-schedule:").split(':')
        ZunoRoute.NewSchedule(
            editId = parts.getOrNull(0)?.ifBlank { null },
            prefillTargetId = parts.getOrNull(1)?.ifBlank { null },
            prefillIsRoleplay = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false,
        )
    }
    value == "topics" -> ZunoRoute.Topics
    value.startsWith("explore-topics:") -> ZunoRoute.ExploreTopics(value.removePrefix("explore-topics:"))
    value.startsWith("topic-preview:") -> ZunoRoute.CommunityTopicPreview(value.removePrefix("topic-preview:"))
    value == "new-topic" -> ZunoRoute.NewTopic
    value.startsWith("topic-settings:") -> ZunoRoute.TopicSettings(value.removePrefix("topic-settings:"))
    value.startsWith("topic:") -> ZunoRoute.Topic(value.removePrefix("topic:"))
    value == "roleplays" -> ZunoRoute.Roleplays
    value.startsWith("explore-roleplays:") -> ZunoRoute.ExploreRoleplays(value.removePrefix("explore-roleplays:"))
    value.startsWith("roleplay-preview:") -> ZunoRoute.CommunityRoleplayPreview(value.removePrefix("roleplay-preview:"))
    value == "new-roleplay" -> ZunoRoute.NewRoleplay
    value.startsWith("roleplay-settings:") -> ZunoRoute.RoleplaySettings(value.removePrefix("roleplay-settings:"))
    value.startsWith("chat:") -> ZunoRoute.Chat(value.removePrefix("chat:"))
    value.startsWith("voice-lesson:") -> {
        val parts = value.removePrefix("voice-lesson:").split(':')
        if (parts.size >= 5) {
            ZunoRoute.VoiceLesson(
                topicId = parts[0],
                lessonId = parts[1],
                conversationId = parts[2],
                position = parts[3].toIntOrNull() ?: 1,
                totalLessons = parts[4].toIntOrNull() ?: 5,
            )
        } else {
            ZunoRoute.Topics
        }
    }
    value.startsWith("lesson-eval:") -> {
        val parts = value.removePrefix("lesson-eval:").split(':')
        if (parts.size >= 2) ZunoRoute.LessonEvaluation(parts[0], parts[1]) else ZunoRoute.Topics
    }
    value.startsWith("voice:") -> ZunoRoute.Voice(value.removePrefix("voice:"))
    value == "settings" -> ZunoRoute.Settings
    value == "settings:preferences" -> ZunoRoute.SettingsPreferences
    value == "settings:tutor" -> ZunoRoute.SettingsTutor
    value == "settings:app-language" -> ZunoRoute.SettingsAppLanguage
    value == "settings:learning" -> ZunoRoute.SettingsLearning
    value == "settings:learning-language" -> ZunoRoute.SettingsLearningLanguage
    value == "settings:account" -> ZunoRoute.SettingsAccount
    value == "settings:privacy" -> ZunoRoute.SettingsPrivacy
    value == "settings:notifications" -> ZunoRoute.SettingsNotifications
    value == "paywall" -> ZunoRoute.Paywall
    value == "welcome-to-pro" -> ZunoRoute.WelcomeToPro
    value == "vocabulary" -> ZunoRoute.Vocabulary
    value.startsWith("vocab-sprint:") -> {
        val parts = value.removePrefix("vocab-sprint:").split(':')
        ZunoRoute.VocabularySprint(
            dueOnly = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: true,
            phraseIds = parts.getOrNull(1)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        )
    }
    value == "vocab-sprint-eval" -> ZunoRoute.VocabularySprintEval
    value == "progress" -> ZunoRoute.Progress
    value == "community" -> ZunoRoute.Community
    value == "live-rooms" -> ZunoRoute.LiveRooms
    value.startsWith("create-live-room:") -> ZunoRoute.CreateLiveRoom(value.removePrefix("create-live-room:").toBooleanStrictOrNull() ?: false)
    value.startsWith("live-room-lobby:") -> ZunoRoute.LiveRoomLobby(value.removePrefix("live-room-lobby:"))
    value.startsWith("system-room-detail:") -> ZunoRoute.SystemRoomDetail(value.removePrefix("system-room-detail:"))
    value.startsWith("active-live-room:") -> ZunoRoute.ActiveLiveRoom(value.removePrefix("active-live-room:"))
    else -> ZunoRoute.Splash
}

private val BackStackSaver = listSaver<SnapshotStateList<ZunoRoute>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> mutableStateListOf<ZunoRoute>().apply { addAll(saved.map(::decodeRoute)) } },
)

private const val PREFS = "zuno_prefs"
private const val KEY_MIC_ASKED = "mic_permission_asked"
private const val KEY_CHATS_NOTIF_ASKED = "chats_notif_permission_asked"
private const val KEY_CALL_NOTIF_ASKED = "call_notif_permission_asked"

private const val TERMS_URL = "https://heyzuno.app/terms"
private const val PRIVACY_URL = "https://heyzuno.app/privacy"
private const val SUPPORT_EMAIL = "support@heyzuno.app"

/** Why the app is signing out, so the Sign in screen can say so. */
private enum class SignOutReason { User, Expired, Deleted }

/**
 * Pure route-gate resolution for signed-in users.
 *
 * Guarantees [ZunoRoute.Home] is never emitted while [profileSync] is still loading, idle,
 * or failed, or when `profile.onboardingCompleted == false`.
 */
internal fun resolveAuthGateRoute(
    currentRoute: ZunoRoute?,
    splashDone: Boolean,
    authState: AuthState,
    profileSync: ProfileSyncState,
): ZunoRoute? {
    if (authState !is AuthState.SignedIn) return null
    if (currentRoute == ZunoRoute.Splash && !splashDone) return null
    val readyProfile = (profileSync as? ProfileSyncState.Ready)?.profile ?: return null
    return if (!readyProfile.onboardingCompleted) {
        if (currentRoute != ZunoRoute.OnboardingSetup) ZunoRoute.OnboardingSetup else null
    } else if (currentRoute == ZunoRoute.Splash || currentRoute == ZunoRoute.Onboarding || currentRoute is ZunoRoute.Auth) {
        ZunoRoute.Home
    } else {
        null
    }
}

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
    val profileSync by zunoViewModel.profileSyncState.collectAsStateWithLifecycle()
    val onboardingViewModel: OnboardingViewModel = viewModel()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val recent by zunoViewModel.recent.collectAsStateWithLifecycle()
    val topics by zunoViewModel.topics.collectAsStateWithLifecycle()
    val topicDetails by zunoViewModel.topicDetails.collectAsStateWithLifecycle()
    val lessonEvaluations by zunoViewModel.lessonEvaluations.collectAsStateWithLifecycle()
    val savedVocabPhrases by zunoViewModel.savedVocabPhrases.collectAsStateWithLifecycle()
    val roleplays by zunoViewModel.roleplays.collectAsStateWithLifecycle()
    val roleplayDetails by zunoViewModel.roleplayDetails.collectAsStateWithLifecycle()
    val discoverTopics by zunoViewModel.discoverTopics.collectAsStateWithLifecycle()
    val previewTopics by zunoViewModel.previewTopics.collectAsStateWithLifecycle()
    val discoverRoleplays by zunoViewModel.discoverRoleplays.collectAsStateWithLifecycle()
    val previewRoleplays by zunoViewModel.previewRoleplays.collectAsStateWithLifecycle()
    val topicBuilder by zunoViewModel.topicBuilder.collectAsStateWithLifecycle()
    val roleplayBuilder by zunoViewModel.roleplayBuilder.collectAsStateWithLifecycle()
    val chats by zunoViewModel.chats.collectAsStateWithLifecycle()
    val chatsOverview by zunoViewModel.chatsOverview.collectAsStateWithLifecycle()
    val peerChats by zunoViewModel.peerChats.collectAsStateWithLifecycle()
    val coachPrompts by zunoViewModel.coachPrompts.collectAsStateWithLifecycle()
    val vocabLibrary by zunoViewModel.vocabLibrary.collectAsStateWithLifecycle()
    val liveRooms by zunoViewModel.liveRooms.collectAsStateWithLifecycle()
    val activeLiveRoom by zunoViewModel.activeLiveRoom.collectAsStateWithLifecycle()
    val privacySettings by zunoViewModel.privacySettings.collectAsStateWithLifecycle()
    var lastSprintResults by remember { mutableStateOf<List<SprintReviewResult>>(emptyList()) }
    var postCallCapture by remember { mutableStateOf<Pair<String, List<Triple<String, String, String>>>?>(null) }
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
    val tts = rememberCartesiaAudioPlayer(zunoViewModel::fetchTtsPreview)
    val ttsSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val preferences = profile?.preferences
    val activeTutor = remember(preferences?.tutorId, preferences?.tutorVoice) {
        TutorAvatarCatalog.resolve(
            tutorId = preferences?.tutorId,
            voicePreset = preferences?.tutorVoice,
        )
    }
    DisposableEffect(tts, viewModel) {
        tts.setAvatarSpeechSink(viewModel.avatarSpeechSink)
        onDispose { tts.setAvatarSpeechSink(null) }
    }

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
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else if ((profileSync as? ProfileSyncState.Ready)?.profile?.onboardingCompleted == true) {
            navigate(ZunoRoute.Home, clearStack = true)
        }
    }

    var homeInitialExplore by rememberSaveable { mutableStateOf(false) }
    fun openHome(explore: Boolean = false) {
        homeInitialExplore = explore
        navigate(ZunoRoute.Home, clearStack = true)
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

    // ── Live voice & video session (chat, peer calls & 03g lessons) ─────────
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val voiceMessages by viewModel.messages.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isCameraOn by viewModel.isCameraEnabled.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val partnerJoined by viewModel.partnerJoined.collectAsStateWithLifecycle()
    val voiceError by viewModel.errorMessage.collectAsStateWithLifecycle()
    val lessonStage by viewModel.lessonStage.collectAsStateWithLifecycle()
    val lessonTurns by viewModel.lessonTurns.collectAsStateWithLifecycle()
    val pronunciationEval by viewModel.pronunciationEval.collectAsStateWithLifecycle()
    val rescuePayload by viewModel.rescuePayload.collectAsStateWithLifecycle()
    val completedLessonEval by viewModel.completedLessonEvaluation.collectAsStateWithLifecycle()
    var voiceConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var activePeerCallId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLiveRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceMessageStart by rememberSaveable { mutableIntStateOf(0) }
    /** The call was started from the Home orb in a brand-new conversation. */
    var voiceFromHome by rememberSaveable { mutableStateOf<String?>(null) }
    /** A Home call that ended before anyone spoke; its empty conversation is deleted. */
    var discardedVoice by remember { mutableStateOf<String?>(null) }
    var callStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var callUsedVideo by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCameraOn) {
        if (isCameraOn) callUsedVideo = true
    }

    LaunchedEffect(voiceError) {
        val message = voiceError
        if ((voiceConversationId != null || activePeerCallId != null) && !message.isNullOrBlank()) {
            toast(message)
            voiceConversationId = null
            activePeerCallId = null
        }
    }

    fun cameraGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Starts a live LiveKit voice/video call with the tutor for [conversationId] and opens voice mode.
     * The tutor gets the conversation so far, so a call from a chat picks up where it left off.
     */
    fun startVoice(
        conversationId: String,
        speakLastReply: Boolean = false,
        enableCamera: Boolean = false,
    ) {
        runWithVoicePermissions {
            tts.stop()
            if (voiceConversationId != null) {
                viewModel.disconnect()
                voiceConversationId = null
            }
            voiceMessageStart = viewModel.messages.value.size
            voiceConversationId = conversationId
            callStartedAt = null
            callUsedVideo = enableCamera
            zunoViewModel.loadCoachPrompts(
                mode = "freetalk",
                targetLanguage = language.code,
                contextLabel = chats[conversationId]?.conversation?.title,
            )
            navigate(ZunoRoute.Voice(conversationId))
            if (viewModel.agentState.value == AgentState.DISCONNECTED) {
                viewModel.connect(
                    sessionToken = authViewModel.sessionToken,
                    onSessionExpired = authViewModel::signOut,
                    conversationId = conversationId,
                    language = language.code,
                    speakLastReply = speakLastReply,
                    enableCamera = enableCamera,
                )
            }
        }
    }

    fun endVoice(commit: Boolean = true) {
        val conversationId = voiceConversationId ?: return
        val started = callStartedAt
        val durationSec = if (started != null) ((System.currentTimeMillis() - started) / 1000L).toInt().coerceAtLeast(1) else 0
        val wasVideo = callUsedVideo || isCameraOn
        val spokenTurns = viewModel.messages.value.drop(voiceMessageStart)
            .filter { it.isFinal && it.text.isNotBlank() }
        if (commit) {
            val spoken = spokenTurns.map { (it.speaker == Speaker.USER) to it.text }
            val hasMessages = !chats[conversationId]?.messages.isNullOrEmpty()
            val emptyHomeCall = spoken.isEmpty() && voiceFromHome == conversationId && !hasMessages
            if (emptyHomeCall) {
                discardedVoice = conversationId
                zunoViewModel.deleteConversation(conversationId) {}
            } else if (spoken.isEmpty() && !hasMessages) {
                discardedVoice = conversationId
            } else {
                if (spoken.isNotEmpty()) {
                    zunoViewModel.saveVoiceTurns(conversationId, spoken)
                }
                if (durationSec > 0) {
                    zunoViewModel.saveCallSummary(conversationId, durationSec, isVideo = wasVideo)
                }
                val candidates = spokenTurns
                    .map { it.text.trim('“', '”', ' ') }
                    .filter { it.length in 6..64 }
                    .distinct()
                    .take(4)
                    .map { phrase ->
                        val tag = if (spokenTurns.any { it.speaker == Speaker.USER && it.text.contains(phrase) }) {
                            "Your reply"
                        } else {
                            "Used by ${activeTutor.displayName}"
                        }
                        Triple(phrase, "${language.displayName(localizedContext, appLanguage)} phrase", tag)
                    }
                if (candidates.isNotEmpty() && durationSec >= 10) {
                    val durLabel = "%02d:%02d".format(durationSec / 60, durationSec % 60)
                    postCallCapture = durLabel to candidates
                }
            }
        }
        viewModel.disconnect()
        voiceConversationId = null
        voiceFromHome = null
        callStartedAt = null
        callUsedVideo = false
        CallNotification.cancel(context)
    }

    LaunchedEffect(Unit) {
        CallNotification.actions.collect { action ->
            when (action) {
                OngoingCallAction.ToggleMute -> viewModel.toggleMute()
                OngoingCallAction.ToggleSpeaker -> viewModel.toggleSpeaker()
                OngoingCallAction.End -> {
                    when {
                        voiceConversationId != null -> endVoice()
                        activeLiveRoomId != null -> {
                            val rid = activeLiveRoomId
                            viewModel.disconnect()
                            activeLiveRoomId = null
                            callStartedAt = null
                            if (rid != null) zunoViewModel.leaveLiveRoom(rid)
                            if (route is ZunoRoute.ActiveLiveRoom) pop()
                        }
                        activePeerCallId != null -> {
                            viewModel.disconnect()
                            activePeerCallId = null
                            callStartedAt = null
                            if (route is ZunoRoute.PeerCall) pop()
                        }
                    }
                }
            }
        }
    }

    /** Voice mode closes into the conversation's transcript (or Home, for a discarded empty call). */
    fun leaveVoice(conversationId: String) {
        val below = backStack.getOrNull(backStack.lastIndex - 1)
        when {
            discardedVoice == conversationId -> {
                discardedVoice = null
                pop()
            }
            below == ZunoRoute.Chat(conversationId) -> pop()
            else -> replace(ZunoRoute.Chat(conversationId))
        }
    }

    // A call that drops after connecting (network, agent left) ends the voice session.
    var callWasLive by remember { mutableStateOf(false) }
    LaunchedEffect(agentState) {
        when (agentState) {
            AgentState.CONNECTING -> Unit
            AgentState.DISCONNECTED -> {
                if (callWasLive && voiceConversationId != null) {
                    val inLesson = backStack.lastOrNull() is ZunoRoute.VoiceLesson
                    toast(str(R.string.voice_call_ended), ToastIcon.Check)
                    endVoice(commit = !inLesson)
                }
                callWasLive = false
            }
            else -> {
                callWasLive = true
                if (voiceConversationId != null && callStartedAt == null) callStartedAt = System.currentTimeMillis()
            }
        }
    }

    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

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

    // `16 · Ongoing Call & System`: sync persistent call notification (prompting once if needed)
    LaunchedEffect(voiceConversationId, activePeerCallId, activeLiveRoomId, isMuted, isSpeakerOn, agentState, appLanguage) {
        val hasCall = voiceConversationId != null || activePeerCallId != null || activeLiveRoomId != null
        if (!hasCall) {
            CallNotification.cancel(context)
            return@LaunchedEffect
        }
        val statusLabel = when {
            isMuted -> localizedContext.getString(R.string.ongoing_call_status_muted)
            agentState == AgentState.SPEAKING -> localizedContext.getString(R.string.ongoing_call_status_speaking)
            agentState == AgentState.THINKING -> localizedContext.getString(R.string.ongoing_call_status_thinking)
            agentState == AgentState.CONNECTING -> localizedContext.getString(R.string.ongoing_call_status_connecting)
            agentState == AgentState.LISTENING -> localizedContext.getString(R.string.ongoing_call_status_listening)
            else -> null
        }
        val baseTitle = when {
            activeLiveRoomId != null -> activeLiveRoom?.title ?: "Live Room"
            activePeerCallId != null -> "1:1 practice call"
            else -> "${activeTutor.displayName} · Zuno Tutor"
        }
        val baseSubtitle = when {
            isMuted -> localizedContext.getString(R.string.ongoing_call_muted_sub)
            activeLiveRoomId != null -> "Live Room · ${language.displayName(localizedContext, appLanguage)} ${profile?.level ?: "B1"}"
            activePeerCallId != null -> "Voice practice · ${language.displayName(localizedContext, appLanguage)}"
            else -> "${language.displayName(localizedContext, appLanguage)} ${profile?.level ?: "B1"}"
        }
        fun postCallNotification() {
            CallNotification.show(
                context = localizedContext,
                title = baseTitle,
                subtitle = baseSubtitle,
                statusLabel = statusLabel,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                startedAtMillis = callStartedAt,
            )
        }
        if (!Reminders.canNotify(context) && !prefs.getBoolean(KEY_CALL_NOTIF_ASKED, false)) {
            prefs.edit { putBoolean(KEY_CALL_NOTIF_ASKED, true) }
            withNotificationPermission { postCallNotification() }
        } else {
            postCallNotification()
        }
    }

    // ── Active peer-chat tracking & foreground push refresh ─────────────────
    LaunchedEffect(route) {
        val activePeerId = when (route) {
            is ZunoRoute.PeerChat -> route.peerConversationId
            is ZunoRoute.PeerCall -> route.peerConversationId
            else -> null
        }
        Push.setActivePeerConversation(activePeerId)
        if (activePeerId != null) {
            Push.clearPeerNotifications(context, activePeerId)
        }
    }

    LaunchedEffect(authState) {
        if (authState !is AuthState.SignedIn) return@LaunchedEffect
        Push.peerEvents.collect { event ->
            zunoViewModel.loadChatsOverview(silent = true)
            val current = backStack.lastOrNull()
            if (current is ZunoRoute.PeerChat && current.peerConversationId == event.peerConversationId) {
                zunoViewModel.loadPeerChat(event.peerConversationId, silent = true)
            }
        }
    }

    // ── Auth transitions & Onboarding gate ──────────────────────────────────
    LaunchedEffect(authState) {
        val current = backStack.last()
        when (authState) {
            is AuthState.SignedIn -> {
                zunoViewModel.refreshAccount()
                scope.launch { Push.register(context.applicationContext, appLanguage.code) }
            }
            AuthState.SignedOut -> {
                if (voiceConversationId != null) endVoice(commit = false)
                viewModel.disconnect()
                zunoViewModel.clear()
                onboardingViewModel.reset()
                Reminders.clearAll(context)
                if (current.isSignedInShell || current == ZunoRoute.OnboardingSetup) {
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

    LaunchedEffect(authState, profileSync, splashDone, route) {
        val destination = resolveAuthGateRoute(
            currentRoute = route,
            splashDone = splashDone,
            authState = authState,
            profileSync = profileSync,
        ) ?: return@LaunchedEffect
        when (destination) {
            ZunoRoute.OnboardingSetup -> {
                val readyProfile = (profileSync as ProfileSyncState.Ready).profile
                val initialAppLang = if (AppCompatDelegate.getApplicationLocales().isEmpty) readyProfile.appLanguage else appLanguage
                onboardingViewModel.reset(readyProfile.targetLanguage, initialAppLang, readyProfile.accent)
                navigate(ZunoRoute.OnboardingSetup, clearStack = true)
                authViewModel.onAuthFlowCompleted()
            }
            ZunoRoute.Home -> {
                navigate(ZunoRoute.Home, clearStack = true)
                authViewModel.onAuthFlowCompleted()
            }
            else -> navigate(destination, clearStack = true)
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

    BackHandler(enabled = backStack.size > 1) {
        if (route is ZunoRoute.Chat && voiceConversationId == route.conversationId) endVoice()
        backStack.removeAt(backStack.lastIndex)
    }

    // ── Camera permission for LiveKit video calls ───────────────────────────
    var afterCameraGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterCameraGranted
        afterCameraGranted = null
        if (granted) action?.invoke() else toast("Camera permission is needed for video")
    }
    fun withCameraPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            afterCameraGranted = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Microphone: in-app "Allow microphone access?" dialog before the system prompt ──
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var showMicDialog by rememberSaveable { mutableStateOf(false) }
    /** What to do once the mic is allowed (start the call that asked for it). */
    var afterMicGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    var afterMicDeclined by remember { mutableStateOf<(() -> Unit)?>(null) }
    var micSentToSettings by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (micSentToSettings) {
            micSentToSettings = false
            val action = afterMicGranted
            val declined = afterMicDeclined
            afterMicGranted = null
            afterMicDeclined = null
            if (micGranted()) action?.invoke() else declined?.invoke()
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit { putBoolean(KEY_MIC_ASKED, true) }
        val action = afterMicGranted
        val declined = afterMicDeclined
        afterMicGranted = null
        afterMicDeclined = null
        if (granted) action?.invoke() else declined?.invoke()
    }

    fun requestMicrophone() {
        val activity = context.findActivity()
        val canAsk = !prefs.getBoolean(KEY_MIC_ASKED, false) ||
            (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO))
        if (canAsk) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Denied for good: only Settings can turn the mic back on.
            micSentToSettings = true
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun withMicrophoneOrFallback(onDeclined: (() -> Unit)?, action: () -> Unit) {
        if (micGranted()) {
            action()
        } else {
            afterMicGranted = action
            afterMicDeclined = onDeclined
            showMicDialog = true
        }
    }

    /** Runs [action] now if the mic is allowed, otherwise after the user allows it from the dialog. */
    fun withMicrophone(action: () -> Unit) {
        withMicrophoneOrFallback(onDeclined = null, action = action)
    }

    /**
     * Starts a 03g interactive 5-stage voice lesson (`POST /api/lessons/:id/session`) and enters `03a`.
     */
    fun startLessonVoice(
        topicId: String,
        lessonId: String,
        replaceCurrent: Boolean = false,
        onMicDeclined: (() -> Unit)? = null,
    ) {
        withMicrophoneOrFallback(onDeclined = onMicDeclined) {
            runWithVoicePermissions {
                tts.stop()
                if (voiceConversationId != null) {
                    viewModel.disconnect()
                    voiceConversationId = null
                }
                zunoViewModel.startLessonVoiceSession(lessonId) { ticket ->
                    voiceMessageStart = viewModel.messages.value.size
                    voiceConversationId = ticket.conversationId
                    callStartedAt = null
                    val dest = ZunoRoute.VoiceLesson(
                        topicId = topicId,
                        lessonId = ticket.lessonId,
                        conversationId = ticket.conversationId,
                        position = ticket.position,
                        totalLessons = ticket.totalLessons,
                    )
                    if (replaceCurrent) replace(dest) else navigate(dest)
                    viewModel.connectLesson(ticket)
                }
            }
        }
    }

    fun openChat(conversationId: String) = navigate(ZunoRoute.Chat(conversationId))

    /**
     * Starts a live dynamic Roleplay voice session (`POST /api/roleplays/:id/session`) directly in
     * avatar voice mode with 3 practice goals + rescue actions, falling back to text chat if mic is declined.
     */
    fun startRoleplayVoice(
        roleplayId: String,
        restart: Boolean = false,
        replaceCurrent: Boolean = false,
    ) {
        withMicrophoneOrFallback(
            onDeclined = {
                zunoViewModel.openRoleplay(roleplayId, restart = restart) { conv ->
                    if (replaceCurrent) replace(ZunoRoute.Chat(conv.id)) else openChat(conv.id)
                }
            },
        ) {
            runWithVoicePermissions {
                tts.stop()
                if (voiceConversationId != null) {
                    viewModel.disconnect()
                    voiceConversationId = null
                }
                zunoViewModel.startRoleplayVoiceSession(roleplayId, restart = restart) { ticket ->
                    voiceMessageStart = viewModel.messages.value.size
                    voiceConversationId = ticket.conversationId
                    callStartedAt = null
                    callUsedVideo = false
                    val dest = ZunoRoute.Voice(ticket.conversationId)
                    if (replaceCurrent) replace(dest) else navigate(dest)
                    viewModel.connectRoleplay(ticket)
                }
            }
        }
    }

    fun startFreeTalk(text: String?) {
        if (voiceConversationId != null) endVoice()
        zunoViewModel.startFreeTalk(text) { openChat(it.id) }
    }

    /** Home orb / drawer New chat: a new Free Talk that opens straight into a live call; the tutor greets first. */
    fun startHomeCall(enableCamera: Boolean = false) {
        if (voiceConversationId != null) endVoice()
        zunoViewModel.startFreeTalk(viaVoice = true) { conversation ->
            voiceFromHome = conversation.id
            startVoice(conversation.id, enableCamera = enableCamera)
        }
    }

    /** Starts a new Free Talk directly in voice/video mode, falling back to text chat if mic is declined. */
    fun startFreeTalkVoiceOrChat() {
        withMicrophoneOrFallback(onDeclined = { startFreeTalk(null) }) {
            startHomeCall()
        }
    }

    fun shouldRestoreVideo(messages: List<Message>): Boolean =
        messages.lastOrNull { it.kind == "call_summary" }?.callIsVideo == true && cameraGranted()

    /** Resumes an existing conversation directly in voice/video mode (restoring video if the last call used it). */
    fun resumeConversationVoice(conversationId: String) {
        withMicrophoneOrFallback(onDeclined = { openChat(conversationId) }) {
            if (voiceConversationId != null) endVoice()
            val cached = chats[conversationId]
            if (cached != null) {
                startVoice(conversationId, enableCamera = shouldRestoreVideo(cached.messages))
            } else {
                zunoViewModel.openConversationThread(conversationId) { thread ->
                    startVoice(conversationId, enableCamera = shouldRestoreVideo(thread.messages))
                }
            }
        }
    }

    /** Resumes a topic from the sidebar directly into its next unfinished lesson in voice/video mode. */
    fun resumeTopicVoice(topicId: String) {
        fun launchNextOrDetail(detail: TopicDetail) {
            val next = detail.nextLesson
            if (next != null) {
                startLessonVoice(topicId, next.id, onMicDeclined = { navigate(ZunoRoute.Topic(topicId)) })
            } else {
                navigate(ZunoRoute.Topic(topicId))
            }
        }
        val cached = topicDetails[topicId]
        if (cached != null) {
            launchNextOrDetail(cached)
        } else {
            zunoViewModel.openTopicDetail(topicId, ::launchNextOrDetail)
        }
    }

    /** "Find my level": a short Free Talk where the tutor assesses the learner. */
    fun startLevelCheck() {
        if (voiceConversationId != null) endVoice()
        zunoViewModel.startLevelCheck(str(R.string.level_check_first_message)) { openChat(it.id) }
    }

    fun requestChatsNotificationsOnce() {
        if (!Reminders.canNotify(context) && !prefs.getBoolean(KEY_CHATS_NOTIF_ASKED, false)) {
            prefs.edit { putBoolean(KEY_CHATS_NOTIF_ASKED, true) }
            withNotificationPermission {}
        }
    }

    fun joinPeerCallRoom(peerConversationId: String, partnerId: String, callId: String) {
        withMicrophone {
            runWithVoicePermissions {
                tts.stop()
                zunoViewModel.joinPeerCall(callId) { joined ->
                    if (joined.call.status != "waiting" && joined.call.status != "active") {
                        zunoViewModel.loadPeerChat(peerConversationId, silent = true)
                        return@joinPeerCall
                    }
                    activePeerCallId = joined.call.id
                    callStartedAt = System.currentTimeMillis()
                    callUsedVideo = false
                    zunoViewModel.loadCoachPrompts(
                        mode = "peer",
                        targetLanguage = language.code,
                        partnerName = peerChats[peerConversationId]?.partner?.name,
                    )
                    viewModel.connectPeerCall(joined.livekit, enableCamera = false)
                    navigate(ZunoRoute.PeerCall(joined.call.id, peerConversationId, partnerId))
                }
            }
        }
    }

    // ── Deep links from notifications ───────────────────────────────────────
    LaunchedEffect(pendingLink, authState, profileSync, route) {
        val link = pendingLink ?: return@LaunchedEffect
        val readyProfile = (profileSync as? ProfileSyncState.Ready)?.profile ?: return@LaunchedEffect
        if (authState !is AuthState.SignedIn || !readyProfile.onboardingCompleted || route == ZunoRoute.Splash || route == ZunoRoute.OnboardingSetup) return@LaunchedEffect
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
                    if (conversation.kind == ConversationKind.Lesson && conversation.topicId != null && conversation.lessonId != null) {
                        backStack.add(ZunoRoute.Topic(conversation.topicId))
                        startLessonVoice(conversation.topicId, conversation.lessonId)
                    } else {
                        backStack.add(ZunoRoute.Chat(conversation.id))
                    }
                }
            }
            is DeepLink.Topic -> {
                backStack.clear()
                backStack.add(ZunoRoute.Home)
                backStack.add(ZunoRoute.Topic(link.topicId))
            }
            is DeepLink.TopicPreview -> {
                backStack.clear()
                backStack.add(ZunoRoute.Home)
                backStack.add(ZunoRoute.Topics)
                backStack.add(ZunoRoute.CommunityTopicPreview(link.idOrToken))
            }
            is DeepLink.RoleplayPreview -> {
                backStack.clear()
                backStack.add(ZunoRoute.Home)
                backStack.add(ZunoRoute.Roleplays)
                backStack.add(ZunoRoute.CommunityRoleplayPreview(link.idOrToken))
            }
            is DeepLink.PeerChat -> {
                Push.clearPeerNotifications(context, link.peerConversationId)
                if (voiceConversationId != null) endVoice()
                backStack.clear()
                backStack.add(ZunoRoute.Home)
                backStack.add(ZunoRoute.Chats())
                backStack.add(ZunoRoute.PeerChat(link.peerConversationId, link.partnerId))
                zunoViewModel.loadPeerChat(link.peerConversationId, silent = true)
                if (link.joinCall && !link.callId.isNullOrBlank()) {
                    joinPeerCallRoom(link.peerConversationId, link.partnerId, link.callId)
                }
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
        if (key == TOPIC_DRAFT_KEY || key == ROLEPLAY_DRAFT_KEY) str(R.string.attach_limit_material, MAX_TOPIC_MATERIAL)
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
        if (key == TOPIC_DRAFT_KEY || key == ROLEPLAY_DRAFT_KEY) materialPhotoPicker.launch(request) else photoPicker.launch(request)
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

    // ── New topic & New roleplay builders ───────────────────────────────────
    var topicShareTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var roleplayShareTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var topicReportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var roleplayReportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var topicDeleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var roleplayDeleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    fun openNewTopic(initialQuery: String = "") {
        zunoViewModel.resetTopicBuilder(initialQuery)
        navigate(ZunoRoute.NewTopic)
    }

    fun leaveNewTopic() {
        zunoViewModel.discardTopicDraft()
        pop()
    }

    fun openNewRoleplay(initialQuery: String = "") {
        zunoViewModel.resetRoleplayBuilder(initialQuery)
        navigate(ZunoRoute.NewRoleplay)
    }

    fun leaveNewRoleplay() {
        zunoViewModel.discardRoleplayDraft()
        pop()
    }

    fun handleSectionNavTab(tab: HomeNavTab) {
        when (tab) {
            HomeNavTab.Home -> openHome(explore = false)
            HomeNavTab.Learn -> if (route != ZunoRoute.Topics) openSection(ZunoRoute.Topics)
            HomeNavTab.Zuno -> withMicrophone(::startHomeCall)
            HomeNavTab.Chats -> openSection(ZunoRoute.Chats())
            HomeNavTab.More -> openHome(explore = true)
        }
    }

    @Composable
    fun topicMenu(summary: com.zuno.ai.data.TopicSummary, closeDetailOnDelete: Boolean = false): List<ZunoMenuAction> = buildList {
        add(
            ZunoMenuAction(stringResource(R.string.topics_menu_start_next), icon = R.drawable.zuno_ic_t_play_circle) {
                val detail = topicDetails[summary.id]
                if (detail != null) {
                    val target = detail.nextLesson ?: detail.lessons.firstOrNull()
                    if (target != null) startLessonVoice(summary.id, target.id)
                } else {
                    navigate(ZunoRoute.Topic(summary.id))
                }
            },
        )
        add(
            ZunoMenuAction(stringResource(R.string.topics_menu_schedule), icon = R.drawable.zuno_ic_t_calendar) {
                navigate(ZunoRoute.NewSchedule(prefillTargetId = summary.id, prefillIsRoleplay = false))
            },
        )
        add(
            ZunoMenuAction(
                stringResource(if (summary.pinned) R.string.chat_menu_unpin else R.string.chat_menu_pin),
                icon = if (summary.pinned) R.drawable.zuno_ic_t_pin_off else R.drawable.zuno_ic_t_pin,
            ) {
                zunoViewModel.setTopicPinned(summary.id, !summary.pinned) { pinned ->
                    toasts.show(
                        ToastEvent(str(if (pinned) R.string.chat_pinned_toast else R.string.chat_unpinned_toast), ToastIcon.Pin),
                    )
                }
            },
        )
        // Share is strictly gated by visibility ("public") or saved community/system items.
        if (summary.isPublic || !summary.isUserOwned) {
            add(
                ZunoMenuAction(stringResource(R.string.chat_menu_share), icon = R.drawable.zuno_ic_t_share) {
                    if (summary.isUserOwned) {
                        topicShareTarget = summary.id
                    } else {
                        summary.effectiveShareUrl?.let(::shareLink)
                    }
                },
            )
        }
        if (summary.isUserOwned) {
            add(
                ZunoMenuAction(stringResource(R.string.topics_menu_settings), icon = R.drawable.zuno_ic_t_settings) {
                    navigate(ZunoRoute.TopicSettings(summary.id))
                },
            )
        } else {
            add(
                ZunoMenuAction(stringResource(R.string.chat_menu_report), icon = R.drawable.zuno_ic_t_flag) {
                    topicReportTarget = summary.sourceTopicId ?: summary.id
                },
            )
        }
        add(
            ZunoMenuAction(
                stringResource(if (summary.isUserOwned) R.string.common_delete else R.string.topics_menu_remove),
                icon = R.drawable.zuno_ic_t_trash,
                destructive = true,
                dividerBefore = true,
            ) {
                if (closeDetailOnDelete) {
                    zunoViewModel.deleteTopic(summary.id)
                    pop()
                } else {
                    topicDeleteTarget = summary.id
                }
            },
        )
    }

    @Composable
    fun roleplayMenu(rp: com.zuno.ai.data.Roleplay): List<ZunoMenuAction> = buildList {
        add(
            ZunoMenuAction(stringResource(R.string.roleplays_menu_start_over), icon = R.drawable.zuno_ic_t_rotate_ccw) {
                startRoleplayVoice(rp.id, restart = true)
            },
        )
        add(
            ZunoMenuAction(stringResource(R.string.chats_action_chat), icon = R.drawable.zuno_ic_t_message_circle) {
                zunoViewModel.openRoleplay(rp.id) { openChat(it.id) }
            },
        )
        add(
            ZunoMenuAction(stringResource(R.string.topics_menu_schedule), icon = R.drawable.zuno_ic_t_clock) {
                navigate(ZunoRoute.NewSchedule(prefillTargetId = rp.id, prefillIsRoleplay = true))
            },
        )
        add(
            ZunoMenuAction(
                stringResource(if (rp.pinned) R.string.chat_menu_unpin else R.string.chat_menu_pin),
                icon = if (rp.pinned) R.drawable.zuno_ic_t_pin_off else R.drawable.zuno_ic_t_pin,
            ) {
                zunoViewModel.setRoleplayPinned(rp.id, !rp.pinned) { pinned ->
                    toasts.show(
                        ToastEvent(str(if (pinned) R.string.chat_pinned_toast else R.string.chat_unpinned_toast), ToastIcon.Pin),
                    )
                }
            },
        )
        if (rp.isPublic || !rp.isUserOwned) {
            add(
                ZunoMenuAction(stringResource(R.string.chat_menu_share), icon = R.drawable.zuno_ic_t_share) {
                    if (rp.isUserOwned) {
                        roleplayShareTarget = rp.id
                    } else {
                        rp.effectiveShareUrl?.let(::shareLink)
                    }
                },
            )
        }
        if (rp.isUserOwned) {
            add(
                ZunoMenuAction(stringResource(R.string.roleplays_menu_settings), icon = R.drawable.zuno_ic_t_settings) {
                    navigate(ZunoRoute.RoleplaySettings(rp.id))
                },
            )
        } else {
            add(
                ZunoMenuAction(stringResource(R.string.chat_menu_report), icon = R.drawable.zuno_ic_t_flag) {
                    roleplayReportTarget = rp.sourceRoleplayId ?: rp.id
                },
            )
        }
        add(
            ZunoMenuAction(
                stringResource(if (rp.isUserOwned) R.string.common_delete else R.string.topics_menu_remove),
                icon = R.drawable.zuno_ic_t_trash,
                destructive = true,
                dividerBefore = true,
            ) {
                roleplayDeleteTarget = rp.id
            },
        )
    }

    // ── Scheduled sessions ──────────────────────────────────────────────────
    var scheduleSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightSchedule by remember { mutableStateOf<String?>(null) }

    fun startScheduled(schedule: Schedule) {
        if (voiceConversationId != null) endVoice()
        zunoViewModel.startSchedule(schedule.id) { conversation ->
            when {
                conversation.kind == ConversationKind.Lesson && conversation.topicId != null && conversation.lessonId != null ->
                    startLessonVoice(conversation.topicId, conversation.lessonId)
                conversation.kind == ConversationKind.Roleplay && conversation.roleplayId != null ->
                    startRoleplayVoice(conversation.roleplayId)
                else ->
                    openChat(conversation.id)
            }
        }
    }

    // ── Settings: sign out, account ─────────────────────────────────────────
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var avatarCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun uploadAvatarUri(uri: Uri) {
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

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadAvatarUri(uri)
    }

    val avatarCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = avatarCameraUri
        if (saved && uri != null) uploadAvatarUri(uri)
    }

    fun launchAvatarCamera() {
        withCameraPermission {
            val uri = AttachmentFiles.newCameraUri(context)
            avatarCameraUri = uri
            runCatching { avatarCamera.launch(uri) }
                .onFailure { toast(str(R.string.error_no_app)) }
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

    /** The conversation's overflow menu, shared by the chat and its voice mode. */
    @Composable
    fun conversationMenu(conversationId: String): List<ZunoMenuAction> {
        val conversation = chats[conversationId]?.conversation
        val inVoice = voiceConversationId == conversationId
        return buildList {
            if (conversation != null) {
                add(ZunoMenuAction(stringResource(R.string.chat_menu_rename), icon = R.drawable.zuno_ic_t_pencil) {
                    renameTarget = conversationId
                })
                add(
                    ZunoMenuAction(
                        stringResource(if (conversation.pinned) R.string.chat_menu_unpin else R.string.chat_menu_pin),
                        icon = if (conversation.pinned) R.drawable.zuno_ic_t_pin_off else R.drawable.zuno_ic_t_pin,
                    ) {
                        zunoViewModel.setPinned(conversationId, !conversation.pinned) { pinned ->
                            toasts.show(
                                ToastEvent(str(if (pinned) R.string.chat_pinned_toast else R.string.chat_unpinned_toast), ToastIcon.Pin),
                            )
                        }
                    },
                )
                add(ZunoMenuAction(stringResource(R.string.chat_menu_share), icon = R.drawable.zuno_ic_t_share) {
                    shareTarget = conversationId
                })
                add(ZunoMenuAction(stringResource(R.string.chat_menu_report), icon = R.drawable.zuno_ic_t_flag) {
                    reportTarget = conversationId
                })
                add(
                    ZunoMenuAction(
                        stringResource(R.string.chat_menu_turn_into_topic),
                        icon = R.drawable.zuno_ic_t_book_open,
                        dividerBefore = true,
                    ) {
                        if (inVoice) endVoice()
                        val seed = zunoViewModel.prefillTopicFromChat(conversationId)
                        openNewTopic(seed)
                    },
                )
                add(
                    ZunoMenuAction(
                        stringResource(R.string.chat_menu_turn_into_roleplay),
                        icon = R.drawable.zuno_ic_t_sparkles,
                    ) {
                        if (inVoice) endVoice()
                        val seed = zunoViewModel.prefillRoleplayFromChat(conversationId)
                        openNewRoleplay(seed)
                    },
                )
            }
            if (conversation?.kind == ConversationKind.Lesson && conversation.topicId != null) {
                add(ZunoMenuAction(stringResource(R.string.chat_menu_back_to_topic), icon = R.drawable.zuno_ic_t_arrow_right) {
                    navigate(ZunoRoute.Topic(conversation.topicId))
                })
            }
            if (conversation?.kind == ConversationKind.Roleplay && conversation.roleplayId != null) {
                add(ZunoMenuAction(stringResource(R.string.chat_menu_start_over), icon = R.drawable.zuno_ic_t_rotate_ccw) {
                    if (inVoice) {
                        endVoice()
                        startRoleplayVoice(conversation.roleplayId, restart = true, replaceCurrent = true)
                    } else {
                        zunoViewModel.openRoleplay(conversation.roleplayId, restart = true) { replace(ZunoRoute.Chat(it.id)) }
                    }
                })
            }
            add(
                ZunoMenuAction(
                    stringResource(R.string.chat_menu_delete),
                    icon = R.drawable.zuno_ic_t_trash,
                    destructive = true,
                    dividerBefore = true,
                ) { deleteTarget = conversationId },
            )
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalActiveTutor provides activeTutor,
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if ((initialState is ZunoRoute.Home && targetState is ZunoRoute.Voice) ||
                    (initialState is ZunoRoute.Voice && targetState is ZunoRoute.Home)
                ) {
                    fadeIn(tween(durationMillis = 420)) togetherWith fadeOut(tween(durationMillis = 420))
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            contentKey = { it.encode() },
            label = "zunoRoute",
        ) { screen ->
            val screenModifier = Modifier.fillMaxSize()
            when (screen) {
                ZunoRoute.Splash -> {
                    val failed = (profileSync as? ProfileSyncState.Failed)
                    if (splashDone && authState is AuthState.SignedIn && failed != null) {
                        AccountGateHoldScreen(
                            failedMessage = failed.message,
                            onRetry = zunoViewModel::refreshAccount,
                            onSignOut = { confirmSignOut = true },
                            modifier = screenModifier,
                        )
                    } else {
                        SplashScreen(
                            onFinished = {
                                splashDone = true
                                if (authViewModel.authState.value !is AuthState.SignedIn) {
                                    replace(ZunoRoute.Onboarding)
                                }
                            },
                            modifier = screenModifier,
                        )
                    }
                }

                ZunoRoute.Onboarding -> OnboardingScreen(
                    onGetStarted = { navigate(ZunoRoute.Auth(AuthMode.SignUp)) },
                    onOpenTerms = { openUrl(TERMS_URL) },
                    onOpenPrivacy = { openUrl(PRIVACY_URL) },
                    modifier = screenModifier,
                )

                ZunoRoute.OnboardingSetup -> OnboardingFlowScreen(
                    onboardingViewModel = onboardingViewModel,
                    languages = languages,
                    appLanguage = appLanguage,
                    busy = busy,
                    onSelectAppLanguage = { newLang -> appLanguage = newLang },
                    onConfirmAppLanguage = { newLang ->
                        appLanguage = newLang
                        zunoViewModel.updateProfile(appLanguage = newLang)
                    },
                    onParseTurn = zunoViewModel::parseOnboardingTurn,
                    onCompleteOnboarding = zunoViewModel::completeOnboarding,
                    onStartLesson = { starterTopic, chosenLesson ->
                        val desiredTag = appLanguage.bcp47
                        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != desiredTag) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(desiredTag))
                        }
                        val targetLesson = chosenLesson ?: starterTopic.nextLesson ?: starterTopic.lessons.firstOrNull()
                        backStack.clear()
                        backStack.add(ZunoRoute.Home)
                        backStack.add(ZunoRoute.Topic(starterTopic.summary.id))
                        if (targetLesson != null) {
                            startLessonVoice(starterTopic.summary.id, targetLesson.id)
                        }
                    },
                    onGoHome = {
                        val desiredTag = appLanguage.bcp47
                        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != desiredTag) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(desiredTag))
                        }
                        navigate(ZunoRoute.Home, clearStack = true)
                    },
                    onExitOnboarding = {
                        confirmSignOut = true
                    },
                    onError = { message -> toast(message) },
                    modifier = screenModifier,
                )

                is ZunoRoute.Auth -> {
                    val failed = (profileSync as? ProfileSyncState.Failed)
                    if (authState is AuthState.SignedIn && failed != null) {
                        AccountGateHoldScreen(
                            failedMessage = failed.message,
                            onRetry = zunoViewModel::refreshAccount,
                            onSignOut = { confirmSignOut = true },
                            modifier = screenModifier,
                        )
                    } else {
                        AuthScreen(
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
                            isGoogleBusy = authForm.isGoogleBusy,
                            isCompletingSignIn = authForm.isCompletingSignIn || (authState is AuthState.SignedIn),
                            linkSentTo = authForm.linkSentTo,
                            errorMessage = authForm.error,
                            onUseDifferentEmail = { authViewModel.clearMagicLinkSent() },
                            modifier = screenModifier,
                        )
                    }
                }

                ZunoRoute.Home -> {
                    val readyProfile = (profileSync as? ProfileSyncState.Ready)?.profile
                    if (readyProfile == null || !readyProfile.onboardingCompleted) {
                        AccountGateHoldScreen(
                            failedMessage = (profileSync as? ProfileSyncState.Failed)?.message,
                            onRetry = zunoViewModel::refreshAccount,
                            onSignOut = { confirmSignOut = true },
                            modifier = screenModifier,
                        )
                    } else {
                    LaunchedEffect(Unit) {
                        if (chatsOverview.items == null) zunoViewModel.loadChatsOverview(silent = true)
                        if (vocabLibrary.items == null) zunoViewModel.loadVocabulary()
                        if (liveRooms.items == null) zunoViewModel.loadLiveRooms()
                    }
                    val latestChatSession = recent.firstOrNull {
                        it.kind == RecentKind.FreeTalk || it.conversationId != null
                    }
                    val latestConversationId = latestChatSession?.let { it.conversationId ?: it.id }
                    LaunchedEffect(latestConversationId) {
                        if (latestConversationId != null && chats[latestConversationId] == null) {
                            zunoViewModel.loadConversation(latestConversationId)
                        }
                    }
                    val latestThread = latestConversationId?.let { chats[it] }
                    val continueSession = if (latestChatSession != null && latestConversationId != null) {
                        val langLabel = language.displayName(appLanguage)
                        val lvl = readyProfile.level
                        val title = latestThread?.conversation?.title?.ifBlank { null } ?: latestChatSession.title
                        val rel = latestThread?.conversation?.createdAtMillis?.let { createdAt ->
                            DateUtils.getRelativeTimeSpanString(
                                createdAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString()
                        } ?: latestChatSession.meta
                        ContinueSessionUi(
                            conversationId = latestConversationId,
                            title = title,
                            subtitle = "$langLabel $lvl · $rel",
                        )
                    } else {
                        null
                    }
                    val streakDays = 22
                    val dailyMinutesDone = 8
                    val dailyTargetMin = preferences?.dailyGoalMin ?: 15
                    val currentLevel = readyProfile.level
                    val unreadChatsCount = chatsOverview.items?.conversations?.count { it.unread } ?: 0
                    val partnersOnline = chatsOverview.items?.openSpeakers?.size?.takeIf { it > 0 } ?: 12
                    val vocabDueCount = vocabLibrary.items?.counts?.due ?: 0
                    val activeLiveRoomsCount = liveRooms.items?.let { browse ->
                        (if (browse.featured?.isLive == true) 1 else 0) + browse.rooms.count { it.isLive }
                    } ?: 0
                    val nextSchedule = schedules.items?.filter { it.active }?.minByOrNull { it.nextRunAtMillis ?: Long.MAX_VALUE }
                    val scheduledTimeLabel = nextSchedule?.let { ScheduleFormat.time(localizedContext, it.timeOfDay) }

                    HomeScreen(
                        copy = HomeCopy.forLanguage(language, readyProfile.firstName, readyProfile.accent),
                        userName = readyProfile.displayName,
                        userInitial = readyProfile.displayName.take(1).uppercase().ifBlank { "Z" },
                        userImage = readyProfile.imageUrl,
                        streakDays = streakDays,
                        dailyGoalMinutesDone = dailyMinutesDone,
                        dailyGoalTargetMinutes = dailyTargetMin,
                        level = currentLevel,
                        showUpgrade = !readyProfile.isPro,
                        continueSession = continueSession,
                        recentSessions = recent,
                        vocabDueCount = vocabDueCount,
                        nextRoleplayTitle = roleplays.items?.firstOrNull()?.title,
                        unreadChatsCount = unreadChatsCount,
                        activeLiveRoomsCount = activeLiveRoomsCount,
                        partnersOnlineCount = partnersOnline,
                        scheduledTimeLabel = scheduledTimeLabel,
                        micGranted = ::micGranted,
                        onRequestMicForTalk = { onGranted -> withMicrophone(onGranted) },
                        onStreakClick = { openSection(ZunoRoute.Progress) },
                        onLanguageClick = { navigate(ZunoRoute.SettingsLearning) },
                        onAvatarClick = { navigate(ZunoRoute.Settings) },
                        onNotificationsClick = { navigate(ZunoRoute.SettingsNotifications) },
                        onDailyGoalContinue = {
                            val firstTopic = recent.firstOrNull { it.kind == RecentKind.Topic }
                            when {
                                firstTopic != null -> resumeTopicVoice(firstTopic.id)
                                latestConversationId != null -> resumeConversationVoice(latestConversationId)
                                else -> openSection(ZunoRoute.Topics)
                            }
                        },
                        onOpenTopics = { openSection(ZunoRoute.Topics) },
                        onOpenChats = { openSection(ZunoRoute.Chats()) },
                        onOpenPartners = { openSection(ZunoRoute.Community) },
                        onOpenLiveRooms = { openSection(ZunoRoute.LiveRooms) },
                        onOpenVocabulary = { openSection(ZunoRoute.Vocabulary) },
                        onOpenProgress = { openSection(ZunoRoute.Progress) },
                        onOpenRoleplays = { openSection(ZunoRoute.Roleplays) },
                        onOpenScheduled = { openSection(ZunoRoute.Scheduled) },
                        onOpenSettings = { navigate(ZunoRoute.Settings) },
                        onStartHomeVoice = { startFreeTalkVoiceOrChat() },
                        onStartFreeTalk = { startFreeTalk(null) },
                        onStartFreeTalkWithText = { spoken -> startFreeTalk(spoken) },
                        onNewChat = { startFreeTalkVoiceOrChat() },
                        onOpenConversation = { id ->
                            if (latestChatSession?.kind == RecentKind.Roleplay) {
                                startRoleplayVoice(latestChatSession.id)
                            } else {
                                resumeConversationVoice(id)
                            }
                        },
                        onOpenRecentSession = { session ->
                            when (session.kind) {
                                RecentKind.Topic -> resumeTopicVoice(session.id)
                                RecentKind.Roleplay -> startRoleplayVoice(session.id)
                                RecentKind.FreeTalk -> resumeConversationVoice(session.conversationId ?: session.id)
                            }
                        },
                        onUpgrade = { navigate(ZunoRoute.Paywall) },
                        onComingSoon = ::comingSoon,
                        modifier = screenModifier,
                    )
                    }
                }

                is ZunoRoute.Chats -> {
                    LaunchedEffect(Unit) {
                        zunoViewModel.loadChatsOverview()
                        requestChatsNotificationsOnce()
                        while (true) {
                            kotlinx.coroutines.delay(3_000)
                            zunoViewModel.loadChatsOverview(silent = true)
                        }
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        zunoViewModel.loadChatsOverview(silent = true)
                    }
                    val userInitial = profile?.displayName?.take(1)?.uppercase()?.ifBlank { "Z" } ?: "Z"
                    ChatsScreen(
                        overview = chatsOverview,
                        userInitial = userInitial,
                        userImageUrl = profile?.imageUrl,
                        initialFilter = screen.initialFilter,
                        onRetry = { zunoViewModel.loadChatsOverview() },
                        onToggleOpenToSpeak = { enabled ->
                            if (enabled) requestChatsNotificationsOnce()
                            zunoViewModel.toggleOpenToSpeak(enabled)
                        },
                        onOpenAiConversation = ::openChat,
                        onStartAiChat = { startFreeTalk(null) },
                        onStartAiVoiceCall = { withMicrophone(::startHomeCall) },
                        onOpenRoleplays = { openSection(ZunoRoute.Roleplays) },
                        onOpenPeerChat = { partner ->
                            zunoViewModel.openPeerChat(partner.id) { thread ->
                                navigate(ZunoRoute.PeerChat(thread.conversation.id, partner.id))
                            }
                        },
                        onInvitePeerToTalk = { partner ->
                            withMicrophone {
                                runWithVoicePermissions {
                                    tts.stop()
                                    zunoViewModel.openPeerChat(partner.id) { thread ->
                                        zunoViewModel.startPeerCall(thread.conversation.id) { started ->
                                            activePeerCallId = started.call.id
                                            callStartedAt = System.currentTimeMillis()
                                            callUsedVideo = false
                                            zunoViewModel.loadCoachPrompts(
                                                mode = "peer",
                                                targetLanguage = language.code,
                                                partnerName = partner.name,
                                            )
                                            viewModel.connectPeerCall(started.livekit, enableCamera = false)
                                            navigate(ZunoRoute.PeerCall(started.call.id, thread.conversation.id, partner.id))
                                        }
                                    }
                                }
                            }
                        },
                        onTogglePartner = { partner, add ->
                            zunoViewModel.togglePartner(partner.id, add) { added ->
                                toasts.show(
                                    ToastEvent(
                                        message = str(
                                            if (added) R.string.member_partner_added_toast else R.string.member_partner_removed_toast,
                                            partner.name,
                                        ),
                                        icon = ToastIcon.Check,
                                        actionLabel = str(R.string.common_undo),
                                        onAction = { zunoViewModel.togglePartner(partner.id, !added) },
                                    ),
                                )
                            }
                        },
                        onNavigateTab = { tab ->
                            when (tab) {
                                HomeNavTab.Home -> openHome(explore = false)
                                HomeNavTab.Learn -> openSection(ZunoRoute.Topics)
                                HomeNavTab.Zuno -> withMicrophone(::startHomeCall)
                                HomeNavTab.Chats -> Unit
                                HomeNavTab.More -> openHome(explore = true)
                            }
                        },
                        onBack = ::pop,
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.PeerChat -> {
                    val thread = peerChats[screen.peerConversationId]
                    LaunchedEffect(screen.peerConversationId) {
                        zunoViewModel.loadPeerChat(screen.peerConversationId)
                        requestChatsNotificationsOnce()
                    }
                    PeerChatScreen(
                        thread = thread,
                        onRefresh = { zunoViewModel.loadPeerChat(screen.peerConversationId, silent = true) },
                        onBack = ::pop,
                        onSendMessage = { text -> zunoViewModel.sendPeerMessage(screen.peerConversationId, text) },
                        onStartCall = {
                            withMicrophone {
                                runWithVoicePermissions {
                                    tts.stop()
                                    zunoViewModel.startPeerCall(screen.peerConversationId) { started ->
                                        activePeerCallId = started.call.id
                                        callStartedAt = System.currentTimeMillis()
                                        callUsedVideo = false
                                        zunoViewModel.loadCoachPrompts(
                                            mode = "peer",
                                            targetLanguage = language.code,
                                            partnerName = thread?.partner?.name,
                                        )
                                        viewModel.connectPeerCall(started.livekit, enableCamera = false)
                                        navigate(ZunoRoute.PeerCall(started.call.id, screen.peerConversationId, screen.partnerId))
                                    }
                                }
                            }
                        },
                        onJoinCall = { callId ->
                            joinPeerCallRoom(screen.peerConversationId, screen.partnerId, callId)
                        },
                        onDeclineCall = { callId ->
                            zunoViewModel.declinePeerCall(screen.peerConversationId, callId)
                        },
                        onTogglePartner = { partner, add ->
                            zunoViewModel.togglePartner(partner.id, add) { added ->
                                toasts.show(
                                    ToastEvent(
                                        message = str(
                                            if (added) R.string.member_partner_added_toast else R.string.member_partner_removed_toast,
                                            partner.name,
                                        ),
                                        icon = ToastIcon.Check,
                                        actionLabel = str(R.string.common_undo),
                                        onAction = { zunoViewModel.togglePartner(partner.id, !added) },
                                    ),
                                )
                            }
                        },
                        onToggleMute = { muted ->
                            zunoViewModel.mutePeerChat(screen.peerConversationId, muted) {
                                toast(str(if (it) R.string.member_chat_muted_toast else R.string.member_chat_unmuted_toast), ToastIcon.Check)
                            }
                        },
                        onReportUser = { reason, details ->
                            zunoViewModel.reportPeerUser(screen.peerConversationId, reason, details) {
                                toasts.show(ToastEvent(str(R.string.report_sent_toast), ToastIcon.Check))
                            }
                        },
                        onDeleteChat = {
                            zunoViewModel.deletePeerChat(screen.peerConversationId) {
                                pop()
                                toasts.show(ToastEvent(str(R.string.member_chat_deleted_toast), ToastIcon.Trash))
                            }
                        },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.PeerCall -> {
                    val thread = peerChats[screen.peerConversationId]
                    LaunchedEffect(screen.peerConversationId) {
                        if (thread == null) zunoViewModel.loadPeerChat(screen.peerConversationId)
                    }
                    val status = voiceCallStatus(agentState, isMuted)
                    val peerTurns = voiceMessages.drop(voiceMessageStart)
                    val partnerLine = peerTurns.lastOrNull { it.speaker == Speaker.AGENT }?.text
                    val userName = profile?.firstName?.ifBlank { null } ?: profile?.displayName.orEmpty().ifBlank { "You" }
                    val userInitial = userName.take(1).uppercase()
                    PeerCallScreen(
                        partner = thread?.partner,
                        userName = userName,
                        userInitial = userInitial,
                        languageName = language.displayName(appLanguage),
                        userLevel = profile?.level ?: "B1",
                        partnerJoined = partnerJoined,
                        status = status,
                        audioLevel = {
                            when (status) {
                                VoiceCallStatus.Speaking -> viewModel.agentAudioLevel.value
                                VoiceCallStatus.Listening -> maxOf(viewModel.userAudioLevel.value, LISTENING_LEVEL_FLOOR)
                                else -> 0f
                            }
                        },
                        startedAtMillis = callStartedAt,
                        partnerLine = partnerLine,
                        turns = peerTurns,
                        isMuted = isMuted,
                        isCameraOn = isCameraOn,
                        isSpeakerOn = isSpeakerOn,
                        room = viewModel.activeRoom,
                        localVideoTrack = localVideoTrack,
                        remoteVideoTrack = remoteVideoTrack,
                        coachPrompts = coachPrompts,
                        menu = emptyList(),
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleCamera = {
                            if (isCameraOn) {
                                viewModel.toggleCamera(false)
                            } else {
                                withCameraPermission { viewModel.toggleCamera(true) }
                            }
                        },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onEndCall = { durationSec ->
                            val wasVideo = callUsedVideo || isCameraOn
                            viewModel.disconnect()
                            activePeerCallId = null
                            callStartedAt = null
                            callUsedVideo = false
                            zunoViewModel.endPeerCall(screen.callId, screen.peerConversationId, durationSec, wasVideo) {
                                pop()
                            }
                        },
                        onPingPartner = {
                            viewModel.sendPingPartner()
                            zunoViewModel.pingPeerCall(screen.callId) {
                                toasts.show(ToastEvent(str(R.string.call_ping_sent_toast, thread?.partner?.shortName ?: "Partner"), ToastIcon.Check))
                            }
                        },
                        onTranslate = { comingSoon(str(R.string.voice_chip_translate)) },
                        onSlowReplay = { line ->
                            tts.speak(
                                text = line,
                                language = language.code,
                                nativeLanguage = appLanguage.code,
                                voiceKey = activeTutor.id,
                                slow = true,
                            )
                        },
                        onSpeakCoachPhrase = { phrase ->
                            tts.speak(
                                text = phrase,
                                language = language.code,
                                nativeLanguage = appLanguage.code,
                                voiceKey = activeTutor.id,
                            )
                        },
                        modifier = screenModifier,
                    )
                }

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
                        prefillTargetId = screen.prefillTargetId,
                        prefillIsRoleplay = screen.prefillIsRoleplay,
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
                    LaunchedEffect(Unit) {
                        zunoViewModel.loadTopics()
                        if (discoverTopics.items == null) zunoViewModel.loadDiscoverTopics()
                    }
                    val discoverItems = remember(discoverTopics.items) {
                        val res = discoverTopics.items
                        if (res == null) emptyList() else res.official + res.community
                    }
                    val itemMenus = topics.items.orEmpty().associate { summary ->
                        summary.id to topicMenu(summary)
                    }
                    TopicsScreen(
                        topics = topics.items,
                        loading = topics.loading,
                        error = topics.error,
                        discoverPreview = discoverItems,
                        menuForTopic = { itemMenus[it.id].orEmpty() },
                        onRetry = {
                            zunoViewModel.loadTopics()
                            zunoViewModel.loadDiscoverTopics()
                        },
                        onOpen = { navigate(ZunoRoute.Topic(it)) },
                        onCreate = { openNewTopic("") },
                        onExplore = { q -> navigate(ZunoRoute.ExploreTopics(q)) },
                        onOpenPreview = { id -> navigate(ZunoRoute.CommunityTopicPreview(id)) },
                        onNavTabSelected = ::handleSectionNavTab,
                        onBack = ::pop,
                        recommendedTopics = profile?.recommendedTopics.orEmpty(),
                        busy = busy,
                        onSelectRecommended = { rec ->
                            zunoViewModel.createTopicFromRecommendation(rec) { created ->
                                navigate(ZunoRoute.Topic(created.summary.id))
                            }
                        },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.ExploreTopics -> {
                    ExploreTopicsScreen(
                        discover = discoverTopics,
                        initialQuery = screen.initialQuery,
                        languageName = language.displayName(appLanguage),
                        onBack = ::pop,
                        onFilterChanged = { cat, q -> zunoViewModel.loadDiscoverTopics(cat, q) },
                        onFilterWithLevel = { cat, q, lvl -> zunoViewModel.loadDiscoverTopics(cat, q, lvl) },
                        onOpenPreview = { item -> navigate(ZunoRoute.CommunityTopicPreview(item.id)) },
                        onCreateFromQuery = { q -> openNewTopic(q) },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.CommunityTopicPreview -> {
                    LaunchedEffect(screen.idOrToken) { zunoViewModel.loadDiscoverTopic(screen.idOrToken) }
                    val previewState = previewTopics[screen.idOrToken] ?: com.zuno.ai.viewmodel.Loadable(loading = true)
                    CommunityTopicPreviewScreen(
                        preview = previewState,
                        busy = busy,
                        onBack = ::pop,
                        onRetry = { zunoViewModel.loadDiscoverTopic(screen.idOrToken) },
                        onAddOrOpen = { item ->
                            if (item.alreadySaved && item.savedTopicId != null) {
                                replace(ZunoRoute.Topic(item.savedTopicId))
                            } else {
                                zunoViewModel.saveDiscoverTopic(item.id) { saved ->
                                    toasts.show(ToastEvent(str(R.string.topic_saved_toast), ToastIcon.Check))
                                    replace(ZunoRoute.Topic(saved.summary.id))
                                }
                            }
                        },
                        onShare = { item -> item.shareUrl?.let(::shareLink) },
                        onReport = { item -> topicReportTarget = item.id },
                        onCopyLink = { item -> item.shareUrl?.let(::copyLink) },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.NewTopic -> {
                    BackHandler(enabled = true) { leaveNewTopic() }
                    NewTopicScreen(
                        builder = topicBuilder,
                        firstName = profile?.firstName?.ifBlank { null },
                        busy = busy,
                        attachments = drafts[TOPIC_DRAFT_KEY].orEmpty(),
                        onBack = ::leaveNewTopic,
                        onSendTurn = zunoViewModel::sendTopicBuilderTurn,
                        onAcceptDraft = { draft ->
                            zunoViewModel.acceptTopicDraft(draft) { created ->
                                replace(ZunoRoute.TopicSettings(created.summary.id))
                            }
                        },
                        onAttach = { openAttachmentSheet(TOPIC_DRAFT_KEY) },
                        onRemoveAttachment = { zunoViewModel.removeAttachment(TOPIC_DRAFT_KEY, it.localId) },
                        onRetryAttachment = { zunoViewModel.retryAttachment(TOPIC_DRAFT_KEY, it.localId) },
                        nativeLanguageBcp47 = appLanguage.bcp47,
                        onRequestMicPermission = { onGranted -> withMicrophone(onGranted) },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.TopicSettings -> {
                    val detail = topicDetails[screen.id]
                    LaunchedEffect(screen.id) {
                        if (detail == null) zunoViewModel.loadTopic(screen.id)
                    }
                    TopicSettingsScreen(
                        topic = detail,
                        languageName = language.displayName(appLanguage),
                        level = profile?.level ?: "B1",
                        busy = busy,
                        onBack = {
                            if (backStack.getOrNull(backStack.lastIndex - 1) == ZunoRoute.Topic(screen.id)) {
                                pop()
                            } else {
                                replace(ZunoRoute.Topic(screen.id))
                            }
                        },
                        onSelectPublic = { public -> zunoViewModel.setTopicShare(screen.id, public) },
                        onShare = ::shareLink,
                        onCopyLink = ::copyLink,
                        onPrimaryAction = { topic ->
                            if (topic.summary.status == com.zuno.ai.data.TopicStatus.NotStarted) {
                                val firstLesson = topic.lessons.firstOrNull()
                                replace(ZunoRoute.Topic(screen.id))
                                if (firstLesson != null) startLessonVoice(screen.id, firstLesson.id)
                            } else {
                                replace(ZunoRoute.Topic(screen.id))
                            }
                        },
                        onUpdateTitle = { title -> zunoViewModel.updateTopic(screen.id, title = title) },
                        onUpdateCategory = { cat -> zunoViewModel.updateTopic(screen.id, category = cat) },
                        onOpenLearning = { navigate(ZunoRoute.SettingsLearning) },
                        onDelete = { topicDeleteTarget = screen.id },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.Topic -> {
                    LaunchedEffect(screen.id) { zunoViewModel.loadTopic(screen.id) }
                    val detail = topicDetails[screen.id]
                    val menu = detail?.let { topicMenu(it.summary, closeDetailOnDelete = true) }.orEmpty()
                    TopicScreen(
                        topic = detail,
                        busy = busy,
                        menu = menu,
                        onBack = ::pop,
                        onRetry = { zunoViewModel.loadTopic(screen.id) },
                        onStartLesson = { lesson -> startLessonVoice(screen.id, lesson.id) },
                        onOpenEvaluation = { lesson -> navigate(ZunoRoute.LessonEvaluation(screen.id, lesson.id)) },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.Roleplays -> {
                    LaunchedEffect(Unit) {
                        zunoViewModel.loadRoleplays()
                        if (discoverRoleplays.items == null) zunoViewModel.loadDiscoverRoleplays()
                    }
                    val discoverItems = remember(discoverRoleplays.items) {
                        val res = discoverRoleplays.items
                        if (res == null) emptyList() else res.official + res.community
                    }
                    val itemMenus = roleplays.items.orEmpty().associate { rp ->
                        rp.id to roleplayMenu(rp)
                    }
                    RoleplaysScreen(
                        roleplays = roleplays.items,
                        loading = roleplays.loading,
                        error = roleplays.error,
                        discoverPreview = discoverItems,
                        menuForRoleplay = { itemMenus[it.id].orEmpty() },
                        onRetry = {
                            zunoViewModel.loadRoleplays()
                            zunoViewModel.loadDiscoverRoleplays()
                        },
                        onOpen = { id -> startRoleplayVoice(id) },
                        onCreate = { openNewRoleplay("") },
                        onExplore = { q -> navigate(ZunoRoute.ExploreRoleplays(q)) },
                        onOpenPreview = { id -> navigate(ZunoRoute.CommunityRoleplayPreview(id)) },
                        onNavTabSelected = ::handleSectionNavTab,
                        onBack = ::pop,
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.ExploreRoleplays -> {
                    ExploreRoleplaysScreen(
                        discover = discoverRoleplays,
                        initialQuery = screen.initialQuery,
                        onBack = ::pop,
                        onFilterChanged = { cat, q -> zunoViewModel.loadDiscoverRoleplays(cat, q) },
                        onFilterWithLevel = { cat, q, lvl -> zunoViewModel.loadDiscoverRoleplays(cat, q, lvl) },
                        onOpenPreview = { item -> navigate(ZunoRoute.CommunityRoleplayPreview(item.id)) },
                        onCreateFromQuery = { q -> openNewRoleplay(q) },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.CommunityRoleplayPreview -> {
                    LaunchedEffect(screen.idOrToken) { zunoViewModel.loadDiscoverRoleplay(screen.idOrToken) }
                    val previewState = previewRoleplays[screen.idOrToken] ?: com.zuno.ai.viewmodel.Loadable(loading = true)
                    CommunityRoleplayPreviewScreen(
                        preview = previewState,
                        languageName = language.displayName(appLanguage),
                        busy = busy,
                        onBack = ::pop,
                        onRetry = { zunoViewModel.loadDiscoverRoleplay(screen.idOrToken) },
                        onStartLive = { item ->
                            startRoleplayVoice(item.savedRoleplayId ?: item.id, replaceCurrent = true)
                        },
                        onSave = { item ->
                            zunoViewModel.saveDiscoverRoleplay(item.id) {
                                toasts.show(ToastEvent(str(R.string.roleplay_saved_toast), ToastIcon.Check))
                            }
                        },
                        onShare = { item -> item.shareUrl?.let(::shareLink) },
                        onReport = { item -> roleplayReportTarget = item.id },
                        onCopyLink = { item -> item.shareUrl?.let(::copyLink) },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.NewRoleplay -> {
                    BackHandler(enabled = true) { leaveNewRoleplay() }
                    NewRoleplayScreen(
                        builder = roleplayBuilder,
                        firstName = profile?.firstName?.ifBlank { null },
                        busy = busy,
                        attachments = drafts[ROLEPLAY_DRAFT_KEY].orEmpty(),
                        onBack = ::leaveNewRoleplay,
                        onSendTurn = zunoViewModel::sendRoleplayBuilderTurn,
                        onAcceptDraft = { draft ->
                            zunoViewModel.acceptRoleplayDraft(draft) { created ->
                                replace(ZunoRoute.RoleplaySettings(created.id))
                            }
                        },
                        onAttach = { openAttachmentSheet(ROLEPLAY_DRAFT_KEY) },
                        onRemoveAttachment = { zunoViewModel.removeAttachment(ROLEPLAY_DRAFT_KEY, it.localId) },
                        onRetryAttachment = { zunoViewModel.retryAttachment(ROLEPLAY_DRAFT_KEY, it.localId) },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.RoleplaySettings -> {
                    val rp = roleplayDetails[screen.id] ?: roleplays.items?.firstOrNull { it.id == screen.id }
                    LaunchedEffect(screen.id) {
                        if (rp == null) zunoViewModel.loadRoleplay(screen.id)
                    }
                    RoleplaySettingsScreen(
                        roleplay = rp,
                        languageName = language.displayName(appLanguage),
                        level = profile?.level ?: "B1",
                        busy = busy,
                        onBack = ::pop,
                        onSelectPublic = { public -> zunoViewModel.setRoleplayShare(screen.id, public) },
                        onShare = ::shareLink,
                        onCopyLink = ::copyLink,
                        onStartLive = { current ->
                            startRoleplayVoice(current.id, replaceCurrent = true)
                        },
                        onUpdateTitle = { title -> zunoViewModel.updateRoleplay(screen.id, title = title) },
                        onUpdateUserRole = { role -> zunoViewModel.updateRoleplay(screen.id, userRole = role) },
                        onUpdateAiRole = { role -> zunoViewModel.updateRoleplay(screen.id, aiRole = role) },
                        onDelete = { roleplayDeleteTarget = screen.id },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.Chat -> {
                    val chat = chats[screen.conversationId]
                    LaunchedEffect(screen.conversationId) {
                        if (chat == null) zunoViewModel.loadConversation(screen.conversationId)
                        zunoViewModel.restoreDraft(screen.conversationId)
                    }
                    val conversation = chat?.conversation
                    val isBilingualFreeTalk = conversation?.kind == ConversationKind.FreeTalk &&
                        !conversation.levelCheck &&
                        preferences?.teachInTarget != true
                    val composerPlaceholder = if (isBilingualFreeTalk) {
                        stringResource(R.string.free_talk_composer_placeholder, language.displayName(appLanguage))
                    } else {
                        language.placeholder
                    }
                    ChatScreen(
                        title = conversation?.title.orEmpty(),
                        sessionLabel = conversation?.let { sessionLabel(localizedContext, it) }.orEmpty(),
                        messages = chat?.messages,
                        pendingText = chat?.pendingText,
                        pendingAttachments = chat?.pendingAttachments.orEmpty(),
                        drafts = drafts[screen.conversationId].orEmpty(),
                        error = chat?.error,
                        placeholder = composerPlaceholder,
                        typingHint = typingHint,
                        menu = conversationMenu(screen.conversationId),
                        onBack = ::pop,
                        onRetryLoad = { zunoViewModel.loadConversation(screen.conversationId) },
                        onSend = { text -> zunoViewModel.send(screen.conversationId, text) },
                        onDraftChanged = { if (preferences?.suggestReplies != false) zunoViewModel.onDraftChanged(it) },
                        savedDraft = { zunoViewModel.draftText(screen.conversationId) },
                        onSaveDraft = { zunoViewModel.saveDraftText(screen.conversationId, it) },
                        onStartVoice = {
                            if (chat?.pendingText == null) withMicrophone { startVoice(screen.conversationId) }
                            else toast(str(R.string.chat_still_replying))
                        },
                        onListen = { text ->
                            tts.speak(
                                text = text,
                                language = language.code,
                                nativeLanguage = appLanguage.code,
                                defaultLanguage = appLanguage.code.takeIf { isBilingualFreeTalk },
                                voiceKey = activeTutor.id,
                            )
                        },
                        onExplain = ::explain,
                        onAttach = { openAttachmentSheet(screen.conversationId) },
                        onRemoveAttachment = { zunoViewModel.removeAttachment(screen.conversationId, it.localId) },
                        onRetryAttachment = { zunoViewModel.retryAttachment(screen.conversationId, it.localId) },
                        onOpenAttachment = ::openAttachment,
                        showTranslations = preferences?.showTranslations != false,
                        levelLabel = profile?.level ?: "B1",
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.Voice -> {
                    val inVoice = voiceConversationId == screen.conversationId
                    LaunchedEffect(Unit) {
                        // Restored after the process died: the call is gone, so is voice mode.
                        if (inVoice && viewModel.agentState.value == AgentState.DISCONNECTED) voiceConversationId = null
                    }
                    LaunchedEffect(inVoice) {
                        if (!inVoice && backStack.lastOrNull() == screen) leaveVoice(screen.conversationId)
                    }
                    val status = voiceCallStatus(agentState, isMuted)
                    val recentTurns = if (inVoice) voiceMessages.drop(voiceMessageStart) else emptyList()
                    val convKind = chats[screen.conversationId]?.conversation?.kind
                    val sessionKindLabel = stringResource(
                        when (convKind) {
                            ConversationKind.Roleplay -> R.string.session_kind_roleplay
                            ConversationKind.Lesson -> R.string.session_kind_lesson
                            else -> R.string.session_kind_free_talk
                        },
                    )
                    val langLevelLabel = "${language.displayName(appLanguage)} ${profile?.level ?: "B1"}"
                    val userName = profile?.firstName?.ifBlank { null } ?: profile?.displayName.orEmpty().ifBlank { "You" }
                    val userInitial = userName.take(1).uppercase()
                    VoiceModeScreen(
                        status = status,
                        level = {
                            when (status) {
                                VoiceCallStatus.Speaking -> viewModel.agentAudioLevel.value
                                VoiceCallStatus.Listening -> maxOf(viewModel.userAudioLevel.value, LISTENING_LEVEL_FLOOR)
                                else -> 0f
                            }
                        },
                        startedAtMillis = callStartedAt.takeIf { inVoice },
                        tutorLine = recentTurns.lastOrNull { it.speaker == Speaker.AGENT }?.text,
                        userLine = recentTurns.lastOrNull { it.speaker == Speaker.USER }?.text,
                        turns = recentTurns,
                        isMuted = isMuted,
                        menu = conversationMenu(screen.conversationId),
                        lessonStage = lessonStage,
                        lessonTurns = lessonTurns,
                        pronunciationEval = pronunciationEval,
                        rescuePayload = rescuePayload,
                        onLessonAction = { action, payload -> viewModel.sendLessonAction(action, payload) },
                        sessionKindLabel = sessionKindLabel,
                        languageLevelLabel = langLevelLabel,
                        learnerLevel = profile?.level ?: "B1",
                        initialCaptionsOn = false,
                        userName = userName,
                        userInitial = userInitial,
                        isCameraOn = isCameraOn,
                        isSpeakerOn = isSpeakerOn,
                        room = viewModel.activeRoom,
                        localVideoTrack = localVideoTrack,
                        tutor = activeTutor,
                        avatarSpeechSink = viewModel.avatarSpeechSink,
                        coachPrompts = coachPrompts,
                        onToggleCamera = {
                            if (isCameraOn) {
                                viewModel.toggleCamera(false)
                            } else {
                                withCameraPermission { viewModel.toggleCamera(true) }
                            }
                        },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onSpeakCoachPhrase = { phrase ->
                            tts.speak(
                                text = phrase,
                                language = language.code,
                                nativeLanguage = appLanguage.code,
                                voiceKey = activeTutor.id,
                            )
                        },
                        onTranslate = { line -> viewModel.sendLessonAction("translate", line) },
                        onSlowReplay = { line ->
                            if (inVoice) {
                                viewModel.sendLessonAction("listen_slow", line)
                            } else {
                                val isFreeTalkVoice = (convKind == null || convKind == ConversationKind.FreeTalk) &&
                                    preferences?.teachInTarget != true
                                tts.speak(
                                    text = line,
                                    language = language.code,
                                    nativeLanguage = appLanguage.code,
                                    defaultLanguage = appLanguage.code.takeIf { isFreeTalkVoice },
                                    voiceKey = activeTutor.id,
                                    slow = true,
                                )
                            }
                        },
                        onSwitchTopic = {
                            if (inVoice) endVoice()
                            openSection(ZunoRoute.Topics)
                        },
                        onEnd = {
                            if (inVoice) endVoice() else leaveVoice(screen.conversationId)
                        },
                        onInterrupt = { viewModel.interrupt() },
                        onToggleMute = { viewModel.toggleMute() },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.VoiceLesson -> {
                    val inVoice = voiceConversationId == screen.conversationId
                    val topicDetail = topicDetails[screen.topicId]
                    val currentLesson = topicDetail?.lessons?.firstOrNull { it.id == screen.lessonId }

                    LaunchedEffect(Unit) {
                        if (inVoice && viewModel.agentState.value == AgentState.DISCONNECTED) {
                            voiceConversationId = null
                        }
                    }
                    LaunchedEffect(inVoice) {
                        if (!inVoice && backStack.lastOrNull() == screen && completedLessonEval == null) {
                            pop()
                        }
                    }

                    // Navigate automatically to 04 · Evaluation when `lesson_complete` arrives over the Data Channel
                    LaunchedEffect(completedLessonEval) {
                        val rawEval = completedLessonEval ?: return@LaunchedEffect
                        val spoken = viewModel.messages.value.drop(voiceMessageStart)
                            .filter { it.isFinal && it.text.isNotBlank() }
                            .map { (it.speaker == Speaker.USER) to it.text }
                        if (spoken.isNotEmpty()) {
                            zunoViewModel.saveVoiceTurns(screen.conversationId, spoken)
                        }
                        val enriched = rawEval.copy(
                            lessonId = screen.lessonId,
                            topicId = screen.topicId,
                            lessonPosition = screen.position,
                            totalLessons = screen.totalLessons,
                            lessonTitle = rawEval.lessonTitle.ifBlank { currentLesson?.title.orEmpty() },
                            lessonTranslation = rawEval.lessonTranslation.ifBlank { currentLesson?.translation.orEmpty() },
                        )
                        zunoViewModel.saveLessonEvaluation(screen.lessonId, screen.conversationId, enriched)
                        viewModel.clearCompletedLessonEvaluation()
                        viewModel.disconnect()
                        voiceConversationId = null
                        callStartedAt = null
                        if (backStack.lastOrNull() == screen) {
                            replace(ZunoRoute.LessonEvaluation(screen.topicId, screen.lessonId))
                        }
                    }

                    val status = voiceCallStatus(agentState, isMuted)
                    val recentTurns = if (inVoice) voiceMessages.drop(voiceMessageStart) else emptyList()
                    val userName = profile?.firstName?.ifBlank { null } ?: profile?.displayName.orEmpty().ifBlank { "You" }
                    val userInitial = userName.take(1).uppercase()
                    VoiceModeScreen(
                        status = status,
                        level = {
                            when (status) {
                                VoiceCallStatus.Speaking -> viewModel.agentAudioLevel.value
                                VoiceCallStatus.Listening -> maxOf(viewModel.userAudioLevel.value, LISTENING_LEVEL_FLOOR)
                                else -> 0f
                            }
                        },
                        startedAtMillis = callStartedAt.takeIf { inVoice },
                        tutorLine = recentTurns.lastOrNull { it.speaker == Speaker.AGENT }?.text,
                        userLine = recentTurns.lastOrNull()?.takeIf { it.speaker == Speaker.USER }?.text,
                        turns = recentTurns,
                        isMuted = isMuted,
                        menu = emptyList(),
                        isLessonMode = true,
                        lessonPosition = screen.position,
                        learnerLevel = profile?.level ?: "B1",
                        lessonStage = lessonStage,
                        lessonTurns = lessonTurns,
                        pronunciationEval = pronunciationEval,
                        rescuePayload = rescuePayload,
                        userName = userName,
                        userInitial = userInitial,
                        isCameraOn = isCameraOn,
                        isSpeakerOn = isSpeakerOn,
                        room = viewModel.activeRoom,
                        localVideoTrack = localVideoTrack,
                        tutor = activeTutor,
                        avatarSpeechSink = viewModel.avatarSpeechSink,
                        onToggleCamera = {
                            if (isCameraOn) {
                                viewModel.toggleCamera(false)
                            } else {
                                withCameraPermission { viewModel.toggleCamera(true) }
                            }
                        },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onLessonAction = { action, payload -> viewModel.sendLessonAction(action, payload) },
                        onEnd = {
                            // Mid-lesson early exit: disconnect cleanly without marking lesson complete
                            endVoice(commit = false)
                            if (backStack.lastOrNull() == screen) pop()
                        },
                        onInterrupt = { viewModel.interrupt() },
                        onToggleMute = { viewModel.toggleMute() },
                        modifier = screenModifier,
                    )
                }

                is ZunoRoute.LessonEvaluation -> {
                    val topicDetail = topicDetails[screen.topicId]
                    val lesson = topicDetail?.lessons?.firstOrNull { it.id == screen.lessonId }
                    val evaluation = lessonEvaluations[screen.lessonId]
                    LaunchedEffect(screen.topicId, screen.lessonId) {
                        if (topicDetail == null) zunoViewModel.loadTopic(screen.topicId)
                        if (evaluation == null) zunoViewModel.loadLessonEvaluation(screen.lessonId)
                    }
                    LessonEvaluationScreen(
                        evaluation = evaluation,
                        lessonTitleFallback = lesson?.title.orEmpty(),
                        lessonPositionFallback = lesson?.position ?: 1,
                        totalLessonsFallback = topicDetail?.summary?.totalLessons ?: 5,
                        busy = busy,
                        savedPhrases = savedVocabPhrases,
                        onToggleSavePhrase = { phrase ->
                            val added = zunoViewModel.toggleSavedVocabularyPhrase(phrase)
                            if (added) {
                                toasts.show(
                                    ToastEvent(
                                        str(R.string.topics_eval_saved_toast, phrase),
                                        ToastIcon.Check,
                                        actionLabel = str(R.string.common_undo),
                                        onAction = { zunoViewModel.removeSavedVocabularyPhrases(listOf(phrase)) },
                                    ),
                                )
                            }
                        },
                        onSaveAllPhrases = { phrases ->
                            val addedCount = zunoViewModel.saveAllVocabularyPhrases(phrases)
                            if (addedCount > 0) {
                                toasts.show(
                                    ToastEvent(
                                        str(R.string.topics_eval_saved_count_toast, addedCount),
                                        ToastIcon.Check,
                                        actionLabel = str(R.string.common_undo),
                                        onAction = { zunoViewModel.removeSavedVocabularyPhrases(phrases) },
                                    ),
                                )
                            }
                        },
                        onPlaySample = { sample ->
                            tts.speak(
                                text = sample,
                                language = language.code,
                                nativeLanguage = appLanguage.code,
                                voiceKey = activeTutor.id,
                            )
                        },
                        onNextLesson = { nextLessonId ->
                            startLessonVoice(screen.topicId, nextLessonId, replaceCurrent = true)
                        },
                        onPracticeAgain = {
                            startLessonVoice(screen.topicId, screen.lessonId, replaceCurrent = true)
                        },
                        onBackToTopic = {
                            tts.stop()
                            zunoViewModel.loadTopic(screen.topicId)
                            val prev = backStack.getOrNull(backStack.lastIndex - 1)
                            if (prev == ZunoRoute.Topic(screen.topicId)) {
                                pop()
                            } else {
                                replace(ZunoRoute.Topic(screen.topicId))
                            }
                        },
                        onRetryLoad = { zunoViewModel.loadLessonEvaluation(screen.lessonId) },
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.Settings -> SettingsOverviewScreen(
                    profile = profile,
                    appLanguage = appLanguage,
                    onBack = ::pop,
                    onUpgrade = { navigate(ZunoRoute.Paywall) },
                    onAccount = { navigate(ZunoRoute.SettingsAccount) },
                    onLearning = { navigate(ZunoRoute.SettingsLearning) },
                    onTutor = { navigate(ZunoRoute.SettingsTutor) },
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
                    preferences = preferences ?: com.zuno.ai.data.Preferences(),
                    targetLanguage = language,
                    appLanguage = appLanguage,
                    onChange = zunoViewModel::updatePreferences,
                    onOpenTutor = { navigate(ZunoRoute.SettingsTutor) },
                    onBack = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsTutor -> SettingsTutorScreen(
                    selectedTutorId = activeTutor.id,
                    targetLanguage = language,
                    appLanguage = appLanguage,
                    isSpeaking = ttsSpeaking,
                    onSelectTutor = { chosen ->
                        zunoViewModel.updatePreferences {
                            it.copy(tutorId = chosen.id, tutorVoice = chosen.voicePreset)
                        }
                    },
                    onPreviewTutor = { chosen, sampleText ->
                        tts.speak(
                            text = sampleText,
                            language = language.code,
                            nativeLanguage = appLanguage.code,
                            defaultLanguage = appLanguage.code,
                            voiceKey = chosen.id,
                        )
                    },
                    onStopPreview = { tts.stop() },
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
                    onUpdateInterests = { interests -> zunoViewModel.updateProfile(interests = interests) },
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
                    onTakePhoto = ::launchAvatarCamera,
                    onRemovePhoto = {
                        zunoViewModel.removeAvatar { ok ->
                            if (ok) toasts.show(ToastEvent(str(R.string.account_photo_removed), ToastIcon.Trash))
                        }
                    },
                    onPlanClick = { navigate(ZunoRoute.Paywall) },
                    onDownloadData = {
                        zunoViewModel.requestDataExport { email ->
                            toasts.show(ToastEvent(str(R.string.account_export_sent_toast, email.ifBlank { profile?.email.orEmpty() }), ToastIcon.Check))
                        }
                    },
                    onDeleteAccount = { confirmDeleteAccount = true },
                    onBack = ::pop,
                    openToSpeak = chatsOverview.items?.openToSpeak ?: true,
                    onToggleOpenToSpeak = { enabled -> zunoViewModel.toggleOpenToSpeak(enabled) },
                    onOpenPrivacy = { navigate(ZunoRoute.SettingsPrivacy) },
                    modifier = screenModifier,
                )

                ZunoRoute.SettingsPrivacy -> {
                    LaunchedEffect(Unit) { zunoViewModel.loadPrivacySettings() }
                    SettingsPrivacyScreen(
                        privacy = privacySettings,
                        languageName = language.displayName(appLanguage),
                        level = profile?.level ?: "B1",
                        onUpdate = { openToSpeak, allowCallInvites, whoCanMessage ->
                            zunoViewModel.updatePrivacySettings(openToSpeak, allowCallInvites, whoCanMessage)
                        },
                        onUnblock = { uid ->
                            zunoViewModel.unblockLearner(uid) {
                                toasts.show(ToastEvent(str(R.string.account_privacy_unblocked_toast), ToastIcon.Check))
                            }
                        },
                        onBack = ::pop,
                        modifier = screenModifier,
                    )
                }

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

                ZunoRoute.Paywall -> PaywallScreen(
                    onClose = ::pop,
                    onStartTrial = { yearly ->
                        toasts.show(
                            ToastEvent(
                                if (yearly) "7-day Zuno Pro trial selected" else "Monthly Zuno Pro plan selected",
                                ToastIcon.Check,
                            ),
                        )
                        replace(ZunoRoute.WelcomeToPro)
                    },
                    onOpenTerms = { openUrl(TERMS_URL) },
                    onOpenPrivacy = { openUrl(PRIVACY_URL) },
                    onRestorePurchases = { toast("No previous purchases found") },
                    modifier = screenModifier,
                )

                ZunoRoute.WelcomeToPro -> WelcomeToProScreen(
                    onStartPractising = { navigate(ZunoRoute.Home, clearStack = true) },
                    onManageSubscription = { replace(ZunoRoute.SettingsAccount) },
                    modifier = screenModifier,
                )

                ZunoRoute.Vocabulary,
                is ZunoRoute.VocabularySprint,
                ZunoRoute.VocabularySprintEval,
                -> VocabularyRouteHost(
                    screen = screen,
                    zunoViewModel = zunoViewModel,
                    language = language,
                    appLanguage = appLanguage,
                    profile = profile,
                    activeTutor = activeTutor,
                    tts = tts,
                    toasts = toasts,
                    lastSprintResults = lastSprintResults,
                    onUpdateSprintResults = { lastSprintResults = it },
                    str = { resId, args -> str(resId, *args) },
                    navigate = ::navigate,
                    replace = ::replace,
                    openSection = ::openSection,
                    startFreeTalk = ::startFreeTalk,
                    pop = ::pop,
                    modifier = screenModifier,
                )

                ZunoRoute.Progress -> ProgressScreen(
                    languageName = language.displayName(appLanguage),
                    level = profile?.level ?: "B1",
                    streakDays = 22,
                    onBack = ::pop,
                    onStartDrill = { withMicrophone(::startHomeCall) },
                    modifier = screenModifier,
                )

                ZunoRoute.Community -> {
                    LaunchedEffect(Unit) {
                        if (chatsOverview.items == null) zunoViewModel.loadChatsOverview(silent = true)
                    }
                    val overview = chatsOverview.items
                    val userInitial = profile?.displayName?.take(1)?.uppercase()?.ifBlank { "Z" } ?: "Z"
                    CommunityScreen(
                        userName = profile?.displayName.orEmpty(),
                        userInitial = userInitial,
                        userImageUrl = profile?.imageUrl,
                        languageName = language.displayName(appLanguage),
                        level = profile?.level ?: "B1",
                        openToSpeak = overview?.openToSpeak ?: true,
                        partners = overview?.partners.orEmpty(),
                        onlineLearners = overview?.openSpeakers.orEmpty(),
                        onToggleOpenToSpeak = { enabled ->
                            if (enabled) requestChatsNotificationsOnce()
                            zunoViewModel.toggleOpenToSpeak(enabled)
                        },
                        onTalkToPartner = { partner ->
                            zunoViewModel.openPeerChat(partner.id) { thread ->
                                navigate(ZunoRoute.PeerChat(thread.conversation.id, partner.id))
                            }
                        },
                        onInviteClick = { openSection(ZunoRoute.Chats(ChatsFilter.Open)) },
                        onOpenChats = { openSection(ZunoRoute.Chats(ChatsFilter.People)) },
                        onBack = ::pop,
                        modifier = screenModifier,
                    )
                }

                ZunoRoute.LiveRooms,
                is ZunoRoute.CreateLiveRoom,
                is ZunoRoute.LiveRoomLobby,
                is ZunoRoute.SystemRoomDetail,
                is ZunoRoute.ActiveLiveRoom,
                -> LiveRoomsRouteHost(
                    screen = screen,
                    context = context,
                    zunoViewModel = zunoViewModel,
                    voiceViewModel = viewModel,
                    languages = languages,
                    language = language,
                    appLanguage = appLanguage,
                    profile = profile,
                    activeTutor = activeTutor,
                    tts = tts,
                    toasts = toasts,
                    busy = busy,
                    callStartedAt = callStartedAt,
                    voiceMessages = voiceMessages,
                    voiceMessageStart = voiceMessageStart,
                    isMuted = isMuted,
                    isCameraOn = isCameraOn,
                    isSpeakerOn = isSpeakerOn,
                    onSetActiveRoom = { roomId, startedAt ->
                        activeLiveRoomId = roomId
                        callStartedAt = startedAt
                    },
                    withMicrophone = ::withMicrophone,
                    runWithVoicePermissions = runWithVoicePermissions,
                    withCameraPermission = ::withCameraPermission,
                    startHomeCall = ::startHomeCall,
                    shareLink = ::shareLink,
                    navigate = ::navigate,
                    replace = ::replace,
                    pop = ::pop,
                    modifier = screenModifier,
                )
            }
        }

    attachTarget?.let { key ->
        // Conversations get "Remind me to continue"; Home and builder drafts have no chat to come back to yet.
        val isConversation = key != HOME_DRAFT_KEY && key != TOPIC_DRAFT_KEY && key != ROLEPLAY_DRAFT_KEY
        ZunoAttachSheet(
            title = stringResource(
                if (key == TOPIC_DRAFT_KEY || key == ROLEPLAY_DRAFT_KEY) R.string.attach_title_material
                else R.string.attach_title_chat,
            ),
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

    postCallCapture?.let { (durationLabel, candidates) ->
        PostCallSavePhrasesSheet(
            durationLabel = durationLabel,
            candidates = candidates,
            onSpeakPhrase = { phrase ->
                tts.speak(
                    text = phrase,
                    language = language.code,
                    nativeLanguage = appLanguage.code,
                    voiceKey = activeTutor.id,
                )
            },
            onSaveSelected = { picked ->
                postCallCapture = null
                zunoViewModel.captureVocabPhrases(picked.map { (p, m, tag) -> Triple(p, m, tag) }) { saved ->
                    toasts.show(ToastEvent(str(R.string.vocab_capture_saved_toast, saved.size), ToastIcon.Check))
                }
            },
            onDismiss = { postCallCapture = null },
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

    topicShareTarget?.let { id ->
        val summary = topicDetails[id]?.summary ?: topics.items?.firstOrNull { it.id == id }
        val displayUrl = summary?.shareUrl?.removePrefix("https://")?.removePrefix("http://")
        val linkSub = if (displayUrl != null) {
            if ((summary.learnersCount) > 0) {
                "$displayUrl · ${str(R.string.topics_learners_count, summary.learnersCount)}"
            } else {
                displayUrl
            }
        } else {
            null
        }
        ZunoShareSheet(
            title = stringResource(R.string.topics_share_sheet_title),
            onlyMeDescription = stringResource(R.string.topics_settings_only_me_desc),
            publicTitle = stringResource(R.string.topics_settings_public_title),
            publicDescription = stringResource(R.string.topics_settings_public_desc),
            linkSubtitle = linkSub,
            isPublic = summary?.isPublic == true,
            shareUrl = summary?.shareUrl,
            busy = busy,
            onSelectPublic = { public -> zunoViewModel.setTopicShare(id, public) },
            onShare = { summary?.shareUrl?.let(::shareLink) },
            onCopyLink = { summary?.shareUrl?.let(::copyLink) },
            onDismiss = { topicShareTarget = null },
        )
    }

    roleplayShareTarget?.let { id ->
        val rp = roleplayDetails[id] ?: roleplays.items?.firstOrNull { it.id == id }
        val displayUrl = rp?.shareUrl?.removePrefix("https://")?.removePrefix("http://")
        val linkSub = if (displayUrl != null) {
            if ((rp.practicedCount) > 0) {
                "$displayUrl · ${str(R.string.roleplays_practiced_count, rp.practicedCount)}"
            } else {
                displayUrl
            }
        } else {
            null
        }
        ZunoShareSheet(
            title = stringResource(R.string.roleplays_share_sheet_title),
            onlyMeDescription = stringResource(R.string.topics_settings_only_me_desc),
            publicTitle = stringResource(R.string.topics_settings_public_title),
            publicDescription = stringResource(R.string.topics_settings_public_desc),
            linkSubtitle = linkSub,
            isPublic = rp?.isPublic == true,
            shareUrl = rp?.shareUrl,
            busy = busy,
            onSelectPublic = { public -> zunoViewModel.setRoleplayShare(id, public) },
            onShare = { rp?.shareUrl?.let(::shareLink) },
            onCopyLink = { rp?.shareUrl?.let(::copyLink) },
            onDismiss = { roleplayShareTarget = null },
        )
    }

    topicReportTarget?.let { id ->
        ZunoReportSheet(
            busy = busy,
            onSubmit = { reason, details ->
                zunoViewModel.reportTopic(id, reason, details) {
                    topicReportTarget = null
                    toasts.show(ToastEvent(str(R.string.report_sent_toast), ToastIcon.Check))
                }
            },
            onDismiss = { topicReportTarget = null },
        )
    }

    roleplayReportTarget?.let { id ->
        ZunoReportSheet(
            busy = busy,
            onSubmit = { reason, details ->
                zunoViewModel.reportRoleplay(id, reason, details) {
                    roleplayReportTarget = null
                    toasts.show(ToastEvent(str(R.string.report_sent_toast), ToastIcon.Check))
                }
            },
            onDismiss = { roleplayReportTarget = null },
        )
    }

    topicDeleteTarget?.let { id ->
        val summary = topics.items?.firstOrNull { it.id == id } ?: topicDetails[id]?.summary
        val actionLabel = stringResource(if (summary?.isUserOwned != false) R.string.common_delete else R.string.topics_menu_remove)
        ZunoDialog(
            title = "$actionLabel “${summary?.title.orEmpty()}”?",
            body = stringResource(R.string.chat_delete_body, summary?.title.orEmpty()),
            confirmLabel = actionLabel,
            destructive = true,
            onConfirm = {
                topicDeleteTarget = null
                zunoViewModel.deleteTopic(id)
                if (route is ZunoRoute.TopicSettings || route is ZunoRoute.Topic) {
                    openSection(ZunoRoute.Topics)
                }
            },
            onDismiss = { topicDeleteTarget = null },
        )
    }

    roleplayDeleteTarget?.let { id ->
        val rp = roleplays.items?.firstOrNull { it.id == id } ?: roleplayDetails[id]
        val actionLabel = stringResource(if (rp?.isUserOwned != false) R.string.common_delete else R.string.topics_menu_remove)
        ZunoDialog(
            title = "$actionLabel “${rp?.title.orEmpty()}”?",
            body = stringResource(R.string.chat_delete_body, rp?.title.orEmpty()),
            confirmLabel = actionLabel,
            destructive = true,
            onConfirm = {
                roleplayDeleteTarget = null
                zunoViewModel.deleteRoleplay(id)
                if (route is ZunoRoute.RoleplaySettings) {
                    openSection(ZunoRoute.Roleplays)
                }
            },
            onDismiss = { roleplayDeleteTarget = null },
        )
    }

    if (showMicDialog) {
        ZunoDialog(
            title = stringResource(R.string.mic_dialog_title),
            body = stringResource(R.string.mic_dialog_body),
            confirmLabel = stringResource(R.string.mic_dialog_allow),
            dismissLabel = stringResource(R.string.common_not_now),
            onConfirm = {
                showMicDialog = false
                requestMicrophone()
            },
            onDismiss = {
                showMicDialog = false
                val declined = afterMicDeclined
                afterMicGranted = null
                afterMicDeclined = null
                declined?.invoke()
            },
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

/** Live call → voice mode status. The mic stays open between turns, so an idle call still listens to the user. */
private fun voiceCallStatus(agentState: AgentState, isMuted: Boolean): VoiceCallStatus = when (agentState) {
    AgentState.DISCONNECTED, AgentState.CONNECTING -> VoiceCallStatus.Connecting
    AgentState.THINKING -> VoiceCallStatus.Thinking
    AgentState.SPEAKING -> VoiceCallStatus.Speaking
    AgentState.IDLE, AgentState.LISTENING -> if (isMuted) VoiceCallStatus.Muted else VoiceCallStatus.Listening
}

/** Keeps the listening orb gently alive while the learner is silent. */
private const val LISTENING_LEVEL_FLOOR = 0.12f

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

/**
 * Calm dark-canvas hold/retry surface shown whenever the user is signed in but `/api/me` has not
 * resolved yet (or failed transiently), preventing any flash of [HomeScreen] before onboarding state is known.
 */
@Composable
private fun AccountGateHoldScreen(
    failedMessage: String?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = ZunoSpace.s32),
        contentAlignment = Alignment.Center,
    ) {
        if (failedMessage == null) {
            ZunoSpinner(size = 24.dp, color = ZunoColors.TextPrimary)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            ) {
                Text(
                    text = stringResource(R.string.auth_account_load_error_title),
                    style = ZunoType.TitleMedium,
                    color = ZunoColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = failedMessage.ifBlank { stringResource(R.string.auth_account_load_error_body) },
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(ZunoSpace.s8))
                ZunoButton(
                    label = stringResource(R.string.common_try_again),
                    onClick = onRetry,
                    size = ZunoButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                ZunoButton(
                    label = stringResource(R.string.settings_sign_out),
                    onClick = onSignOut,
                    variant = ZunoButtonVariant.Ghost,
                    size = ZunoButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
