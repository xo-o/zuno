package com.zuno.ai.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuno.ai.BuildConfig
import com.zuno.ai.R
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.AppUpdateInfo
import com.zuno.ai.data.CartesiaAudioPlayer
import com.zuno.ai.data.ChatsFilter
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Profile
import com.zuno.ai.data.SystemNotificationItem
import com.zuno.ai.data.SystemNotificationKind
import com.zuno.ai.model.AgentState
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.push.CallNotification
import com.zuno.ai.ui.components.ToastController
import com.zuno.ai.ui.components.ZunoSpinner
import com.zuno.ai.ui.screens.ActiveLiveRoomScreen
import com.zuno.ai.ui.screens.CreateLiveRoomScreen
import com.zuno.ai.ui.screens.LiveRoomLobbyScreen
import com.zuno.ai.ui.screens.LiveRoomsScreen
import com.zuno.ai.ui.screens.NotificationsScreen
import com.zuno.ai.ui.screens.PaywallScreen
import com.zuno.ai.ui.screens.SettingsAboutScreen
import com.zuno.ai.ui.screens.SettingsAccountScreen
import com.zuno.ai.ui.screens.SettingsAppLanguageScreen
import com.zuno.ai.ui.screens.SettingsLearningLanguageScreen
import com.zuno.ai.ui.screens.SettingsLearningScreen
import com.zuno.ai.ui.screens.SettingsNotificationsScreen
import com.zuno.ai.ui.screens.SettingsOverviewScreen
import com.zuno.ai.ui.screens.SettingsPreferencesScreen
import com.zuno.ai.ui.screens.SettingsPrivacyScreen
import com.zuno.ai.ui.screens.SettingsTutorScreen
import com.zuno.ai.ui.screens.SettingsWhatsNewScreen
import com.zuno.ai.ui.screens.SprintEvaluationScreen
import com.zuno.ai.ui.screens.SprintReviewResult
import com.zuno.ai.ui.screens.SystemRoomDetailScreen
import com.zuno.ai.ui.screens.VocabularyScreen
import com.zuno.ai.ui.screens.VocabularySprintScreen
import com.zuno.ai.ui.screens.VoiceCallStatus
import com.zuno.ai.ui.screens.WelcomeToProScreen
import com.zuno.ai.ui.screens.displayName
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.viewmodel.ToastEvent
import com.zuno.ai.viewmodel.ToastIcon
import com.zuno.ai.viewmodel.VoiceSessionViewModel
import com.zuno.ai.viewmodel.ZunoViewModel
import kotlinx.coroutines.delay

@Composable
internal fun VocabularyRouteHost(
    screen: ZunoRoute,
    zunoViewModel: ZunoViewModel,
    language: LearningLanguage,
    appLanguage: AppLanguage,
    profile: Profile?,
    activeTutor: TutorAvatarProfile,
    tts: CartesiaAudioPlayer,
    toasts: ToastController,
    lastSprintResults: List<SprintReviewResult>,
    onUpdateSprintResults: (List<SprintReviewResult>) -> Unit,
    str: (Int, Array<Any>) -> String,
    navigate: (ZunoRoute) -> Unit,
    replace: (ZunoRoute) -> Unit,
    openSection: (ZunoRoute) -> Unit,
    startFreeTalk: (String?) -> Unit,
    pop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vocabLibrary by zunoViewModel.vocabLibrary.collectAsStateWithLifecycle()
    val savedVocabPhrases by zunoViewModel.savedVocabPhrases.collectAsStateWithLifecycle()

    when (screen) {
        ZunoRoute.Vocabulary -> {
            LaunchedEffect(Unit) { zunoViewModel.loadVocabulary() }
            VocabularyScreen(
                languageName = language.displayName(appLanguage),
                level = profile?.level ?: "B1",
                onBack = pop,
                onStartVoiceSprint = { navigate(ZunoRoute.VocabularySprint(dueOnly = true)) },
                onSpeakPhrase = { phrase ->
                    tts.speak(
                        text = phrase,
                        language = language.code,
                        nativeLanguage = appLanguage.code,
                        voiceKey = activeTutor.id,
                    )
                },
                onSlowSpeakPhrase = { phrase ->
                    tts.speak(
                        text = phrase,
                        language = language.code,
                        nativeLanguage = appLanguage.code,
                        voiceKey = activeTutor.id,
                        slow = true,
                    )
                },
                extraSavedPhrases = savedVocabPhrases,
                library = vocabLibrary,
                onPreviewPhrase = zunoViewModel::previewVocabPhrase,
                onAddPhrase = { phrase, translation, contextNote ->
                    zunoViewModel.addVocabPhrase(phrase, translation, "Added manually", contextNote) {
                        toasts.show(ToastEvent(str(R.string.vocab_added_toast, arrayOf(phrase)), ToastIcon.Check))
                    }
                },
                onUpdatePhrase = { id, phrase, translation, contextNote ->
                    zunoViewModel.updateVocabPhrase(id, phrase, translation, contextNote) {
                        toasts.show(ToastEvent(str(R.string.vocab_updated_toast, emptyArray()), ToastIcon.Check))
                    }
                },
                onDeletePhrase = { id ->
                    zunoViewModel.deleteVocabPhrase(id) {
                        toasts.show(ToastEvent(str(R.string.vocab_removed_toast, emptyArray()), ToastIcon.Trash))
                    }
                },
                onPracticePhrase = { item ->
                    navigate(ZunoRoute.VocabularySprint(dueOnly = false, phraseIds = listOf(item.id)))
                },
                onStartChatWithZuno = { startFreeTalk(null) },
                onOpenTopics = { openSection(ZunoRoute.Topics) },
                modifier = modifier,
            )
        }

        is ZunoRoute.VocabularySprint -> {
            val all = vocabLibrary.items?.phrases.orEmpty()
            val deck = remember(all, screen) {
                when {
                    screen.phraseIds.isNotEmpty() -> all.filter { it.id in screen.phraseIds }
                    screen.dueOnly -> all.filter { it.dueNow }.ifEmpty { all }
                    else -> all
                }
            }
            val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()
            VocabularySprintScreen(
                phrases = deck,
                languageName = language.displayName(appLanguage),
                tutor = activeTutor,
                isSpeaking = isSpeaking,
                onSpeakPhrase = { phrase ->
                    tts.speak(
                        text = phrase,
                        language = language.code,
                        nativeLanguage = appLanguage.code,
                        voiceKey = activeTutor.id,
                    )
                },
                onSlowSpeakPhrase = { phrase ->
                    tts.speak(
                        text = phrase,
                        language = language.code,
                        nativeLanguage = appLanguage.code,
                        voiceKey = activeTutor.id,
                        slow = true,
                    )
                },
                onReviewPhrase = { phraseId, passed ->
                    if (vocabLibrary.items != null) {
                        zunoViewModel.reviewVocabPhrase(phraseId, passed)
                    }
                },
                onFinishSprint = { sprintResults ->
                    onUpdateSprintResults(sprintResults)
                    replace(ZunoRoute.VocabularySprintEval)
                },
                onBack = pop,
                modifier = modifier,
            )
        }

        ZunoRoute.VocabularySprintEval -> SprintEvaluationScreen(
            results = lastSprintResults,
            onSpeakPhrase = { phrase ->
                tts.speak(
                    text = phrase,
                    language = language.code,
                    nativeLanguage = appLanguage.code,
                    voiceKey = activeTutor.id,
                )
            },
            onPracticeMissed = {
                val missedIds = lastSprintResults.filterNot { it.passed }.map { it.phrase.id }
                replace(ZunoRoute.VocabularySprint(dueOnly = false, phraseIds = missedIds))
            },
            onBackToVocabulary = pop,
            modifier = modifier,
        )

        else -> Unit
    }
}

@Composable
internal fun LiveRoomsRouteHost(
    screen: ZunoRoute,
    context: Context,
    zunoViewModel: ZunoViewModel,
    voiceViewModel: VoiceSessionViewModel,
    languages: List<LearningLanguage>,
    language: LearningLanguage,
    appLanguage: AppLanguage,
    profile: Profile?,
    activeTutor: TutorAvatarProfile,
    tts: CartesiaAudioPlayer,
    toasts: ToastController,
    busy: Boolean,
    callStartedAt: Long?,
    voiceMessages: List<ChatMessage>,
    voiceMessageStart: Int,
    isMuted: Boolean,
    isCameraOn: Boolean,
    isSpeakerOn: Boolean,
    onSetActiveRoom: (roomId: String?, startedAt: Long?) -> Unit,
    withMicrophone: (() -> Unit) -> Unit,
    runWithVoicePermissions: (() -> Unit) -> Unit,
    withCameraPermission: (() -> Unit) -> Unit,
    startHomeCall: () -> Unit,
    shareLink: (String) -> Unit,
    navigate: (ZunoRoute) -> Unit,
    replace: (ZunoRoute) -> Unit,
    pop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveRooms by zunoViewModel.liveRooms.collectAsStateWithLifecycle()
    val activeLiveRoom by zunoViewModel.activeLiveRoom.collectAsStateWithLifecycle()
    val localVideoTrack by voiceViewModel.localVideoTrack.collectAsStateWithLifecycle()
    val agentState by voiceViewModel.agentState.collectAsStateWithLifecycle()
    val activeSpeaker by voiceViewModel.activeSpeaker.collectAsStateWithLifecycle()
    val voiceError by voiceViewModel.errorMessage.collectAsStateWithLifecycle()

    when (screen) {
        ZunoRoute.LiveRooms -> {
            LaunchedEffect(Unit) { zunoViewModel.loadLiveRooms() }
            LiveRoomsScreen(
                languageName = language.displayName(appLanguage),
                level = profile?.level ?: "B1",
                browse = liveRooms,
                onRetry = { zunoViewModel.loadLiveRooms() },
                busy = busy,
                onBack = pop,
                onJoinRoom = { roomTitle ->
                    toasts.show(ToastEvent("Joining $roomTitle…", ToastIcon.Check))
                    withMicrophone(startHomeCall)
                },
                onCreateRoom = { navigate(ZunoRoute.CreateLiveRoom(isPrivate = false)) },
                onOpenRoom = { room ->
                    if (room.isSystem && !room.isHost) {
                        navigate(ZunoRoute.SystemRoomDetail(room.id))
                    } else {
                        zunoViewModel.joinLiveRoom(room.id) { joined ->
                            if (joined.room.isLive && joined.livekit != null) {
                                withMicrophone {
                                    runWithVoicePermissions {
                                        onSetActiveRoom(joined.room.id, System.currentTimeMillis())
                                        voiceViewModel.connectPeerCall(joined.livekit, enableCamera = false)
                                        navigate(ZunoRoute.ActiveLiveRoom(joined.room.id))
                                    }
                                }
                            } else {
                                navigate(ZunoRoute.LiveRoomLobby(joined.room.id))
                            }
                        }
                    }
                },
                onJoinByCode = { code ->
                    zunoViewModel.joinLiveRoomByCode(code) { joined ->
                        if (joined.room.isLive && joined.livekit != null) {
                            withMicrophone {
                                runWithVoicePermissions {
                                    onSetActiveRoom(joined.room.id, System.currentTimeMillis())
                                    voiceViewModel.connectPeerCall(joined.livekit, enableCamera = false)
                                    navigate(ZunoRoute.ActiveLiveRoom(joined.room.id))
                                }
                            }
                        } else {
                            navigate(ZunoRoute.LiveRoomLobby(joined.room.id))
                        }
                    }
                },
                onCreatePrivateRoom = { navigate(ZunoRoute.CreateLiveRoom(isPrivate = true)) },
                modifier = modifier,
            )
        }

        is ZunoRoute.CreateLiveRoom -> CreateLiveRoomScreen(
            languages = languages,
            defaultLanguage = language,
            defaultLevel = profile?.level ?: "B1",
            initialPrivate = screen.isPrivate,
            busy = busy,
            onBack = pop,
            onCreate = { title, langCode, lvl, maxMembers, vis ->
                zunoViewModel.createLiveRoom(title, langCode, lvl, maxMembers, vis) { created ->
                    replace(ZunoRoute.LiveRoomLobby(created.id))
                }
            },
            modifier = modifier,
        )

        is ZunoRoute.LiveRoomLobby -> {
            LaunchedEffect(screen.roomId) {
                var ticks = 0
                while (true) {
                    if (ticks % 5 == 0) zunoViewModel.pingLiveRoom(screen.roomId)
                    zunoViewModel.refreshLiveRoom(screen.roomId)
                    ticks++
                    delay(3_000)
                }
            }
            val roomObj = activeLiveRoom?.takeIf { it.id == screen.roomId }
                ?: liveRooms.items?.rooms?.firstOrNull { it.id == screen.roomId }
                ?: liveRooms.items?.featured?.takeIf { it.id == screen.roomId }
            if (roomObj != null) {
                LiveRoomLobbyScreen(
                    room = roomObj,
                    languageName = LearningLanguages.find(roomObj.language, languages).displayName(appLanguage),
                    busy = busy,
                    onBack = pop,
                    onInvite = {
                        val inviteText = roomObj.code?.let { "Join my Zuno Live Room with code: $it" }
                            ?: "Join my Zuno Live Room: ${roomObj.title}"
                        shareLink(inviteText)
                    },
                    onStartRoom = {
                        withMicrophone {
                            runWithVoicePermissions {
                                zunoViewModel.startLiveRoom(roomObj.id) { started ->
                                    onSetActiveRoom(started.room.id, System.currentTimeMillis())
                                    started.livekit?.let { voiceViewModel.connectPeerCall(it, enableCamera = false) }
                                    replace(ZunoRoute.ActiveLiveRoom(started.room.id))
                                }
                            }
                        }
                    },
                    onLeaveLobby = {
                        zunoViewModel.leaveLiveRoom(roomObj.id) { pop() }
                    },
                    modifier = modifier,
                )
            } else {
                Box(Modifier.fillMaxSize().zunoCanvasBackground(), contentAlignment = Alignment.Center) {
                    ZunoSpinner(size = 24.dp)
                }
            }
        }

        is ZunoRoute.SystemRoomDetail -> {
            val roomObj = activeLiveRoom?.takeIf { it.id == screen.roomId }
                ?: liveRooms.items?.featured?.takeIf { it.id == screen.roomId }
                ?: liveRooms.items?.rooms?.firstOrNull { it.id == screen.roomId }
            LaunchedEffect(screen.roomId) { zunoViewModel.refreshLiveRoom(screen.roomId) }
            if (roomObj != null) {
                SystemRoomDetailScreen(
                    room = roomObj,
                    languageName = LearningLanguages.find(roomObj.language, languages).displayName(appLanguage),
                    busy = busy,
                    onBack = pop,
                    onShare = { shareLink("Join Zuno Live Room: ${roomObj.title}") },
                    onJoin = {
                        withMicrophone {
                            runWithVoicePermissions {
                                zunoViewModel.joinLiveRoom(roomObj.id) { joined ->
                                    onSetActiveRoom(joined.room.id, System.currentTimeMillis())
                                    joined.livekit?.let { voiceViewModel.connectPeerCall(it, enableCamera = false) }
                                    replace(ZunoRoute.ActiveLiveRoom(joined.room.id))
                                }
                            }
                        }
                    },
                    modifier = modifier,
                )
            } else {
                Box(Modifier.fillMaxSize().zunoCanvasBackground(), contentAlignment = Alignment.Center) {
                    ZunoSpinner(size = 24.dp)
                }
            }
        }

        is ZunoRoute.ActiveLiveRoom -> {
            LaunchedEffect(screen.roomId) {
                while (true) {
                    zunoViewModel.pingLiveRoom(screen.roomId)
                    zunoViewModel.refreshLiveRoom(screen.roomId)
                    delay(15_000)
                }
            }
            val roomObj = activeLiveRoom?.takeIf { it.id == screen.roomId }
                ?: liveRooms.items?.featured?.takeIf { it.id == screen.roomId }
                ?: liveRooms.items?.rooms?.firstOrNull { it.id == screen.roomId }
            if (roomObj != null) {
                val userName = profile?.firstName?.ifBlank { null } ?: profile?.displayName.orEmpty().ifBlank { "You" }
                val userInitial = userName.take(1).uppercase()
                ActiveLiveRoomScreen(
                    roomData = roomObj,
                    userName = userName,
                    userInitial = userInitial,
                    startedAtMillis = callStartedAt,
                    turns = voiceMessages.drop(voiceMessageStart),
                    isMuted = isMuted,
                    isCameraOn = isCameraOn,
                    isSpeakerOn = isSpeakerOn,
                    livekitRoom = voiceViewModel.activeRoom,
                    localVideoTrack = localVideoTrack,
                    onToggleMute = { voiceViewModel.toggleMute() },
                    onToggleCamera = {
                        if (isCameraOn) voiceViewModel.toggleCamera(false)
                        else withCameraPermission { voiceViewModel.toggleCamera(true) }
                    },
                    onToggleSpeaker = { voiceViewModel.toggleSpeaker() },
                    onNextQuestion = { zunoViewModel.updateLiveRoom(roomObj.id, nextQuestion = true) },
                    onSwitchVisibility = {
                        val nextVis = if (roomObj.isPublic) "private" else "public"
                        zunoViewModel.updateLiveRoom(roomObj.id, visibility = nextVis) {
                            toasts.show(ToastEvent("Room switched to $nextVis", ToastIcon.Check))
                        }
                    },
                    onInviteMember = {
                        val inviteText = roomObj.code?.let { "Join my Zuno Live Room with code: $it" }
                            ?: "Join my Zuno Live Room: ${roomObj.title}"
                        shareLink(inviteText)
                    },
                    onSpeakStarter = { starter ->
                        tts.speak(
                            text = starter,
                            language = language.code,
                            nativeLanguage = appLanguage.code,
                            voiceKey = activeTutor.id,
                        )
                    },
                    onTranslate = { line ->
                        tts.speak(
                            text = line,
                            language = language.code,
                            nativeLanguage = appLanguage.code,
                            voiceKey = activeTutor.id,
                        )
                    },
                    onSlowReplay = { line ->
                        tts.speak(
                            text = line,
                            language = language.code,
                            nativeLanguage = appLanguage.code,
                            voiceKey = activeTutor.id,
                            slow = true,
                        )
                    },
                    onEndRoom = {
                        val elapsedSec = callStartedAt?.let { ((System.currentTimeMillis() - it) / 1000L).toInt().coerceAtLeast(0) } ?: 0
                        voiceViewModel.disconnect()
                        onSetActiveRoom(null, null)
                        CallNotification.cancel(context)
                        zunoViewModel.endLiveRoom(roomObj.id, durationSec = elapsedSec) { pop() }
                    },
                    status = when (agentState) {
                        AgentState.DISCONNECTED, AgentState.CONNECTING -> VoiceCallStatus.Connecting
                        AgentState.THINKING -> VoiceCallStatus.Thinking
                        AgentState.SPEAKING -> VoiceCallStatus.Speaking
                        AgentState.IDLE, AgentState.LISTENING -> if (isMuted) VoiceCallStatus.Muted else VoiceCallStatus.Listening
                    },
                    audioLevel = {
                        when (agentState) {
                            AgentState.SPEAKING -> voiceViewModel.agentAudioLevel.value
                            AgentState.IDLE, AgentState.LISTENING -> maxOf(voiceViewModel.userAudioLevel.value, 0.12f)
                            else -> 0f
                        }
                    },
                    userAudioLevel = { voiceViewModel.userAudioLevel.value },
                    activeSpeaker = activeSpeaker,
                    hasError = !voiceError.isNullOrBlank(),
                    modifier = modifier,
                )
            } else {
                Box(Modifier.fillMaxSize().zunoCanvasBackground(), contentAlignment = Alignment.Center) {
                    ZunoSpinner(size = 24.dp)
                }
            }
        }

        else -> Unit
    }
}

@Composable
internal fun SettingsAndNotificationsRouteHost(
    screen: ZunoRoute,
    context: Context,
    localizedContext: Context,
    zunoViewModel: ZunoViewModel,
    profile: Profile?,
    languages: List<LearningLanguage>,
    language: LearningLanguage,
    appLanguage: AppLanguage,
    activeTutor: TutorAvatarProfile,
    tts: CartesiaAudioPlayer,
    toasts: ToastController,
    busy: Boolean,
    uploadingPhoto: Boolean,
    systemNotifications: List<SystemNotificationItem>,
    appUpdateInfo: AppUpdateInfo?,
    hasLoadedSchedules: Boolean,
    hasActiveVoice: Boolean,
    canReturnBackAfterLangSwitch: Boolean,
    endVoice: () -> Unit,
    selectAppLanguage: (AppLanguage) -> Unit,
    startLevelCheck: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onContactSupport: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    withNotificationPermission: (() -> Unit) -> Unit,
    openPlayStoreForUpdate: (String) -> Unit,
    openTerms: () -> Unit,
    openPrivacy: () -> Unit,
    str: (Int, Array<Any>) -> String,
    navigate: (ZunoRoute) -> Unit,
    navigateHomeClear: () -> Unit,
    replace: (ZunoRoute) -> Unit,
    openSection: (ZunoRoute) -> Unit,
    pop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ttsSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val chatsOverview by zunoViewModel.chatsOverview.collectAsStateWithLifecycle()
    val privacySettings by zunoViewModel.privacySettings.collectAsStateWithLifecycle()
    val notificationPrefs by zunoViewModel.notificationPrefs.collectAsStateWithLifecycle()
    val preferences = profile?.preferences

    when (screen) {
        ZunoRoute.Notifications -> {
            LaunchedEffect(Unit) {
                zunoViewModel.loadChatsOverview(silent = true)
                zunoViewModel.loadLiveRooms(silent = true)
                if (!hasLoadedSchedules) zunoViewModel.loadSchedules()
            }
            NotificationsScreen(
                items = systemNotifications,
                onBack = pop,
                onOpenNotificationSettings = { navigate(ZunoRoute.SettingsNotifications) },
                onItemClick = { item ->
                    zunoViewModel.markNotificationRead(item.id)
                    when (item.kind) {
                        SystemNotificationKind.AppUpdate -> navigate(ZunoRoute.SettingsWhatsNew)
                        SystemNotificationKind.UserMessage -> {
                            val convId = item.peerConversationId
                            val pId = item.partnerId
                            if (!convId.isNullOrBlank() && !pId.isNullOrBlank()) {
                                zunoViewModel.markPeerChatRead(convId)
                                navigate(ZunoRoute.PeerChat(convId, pId))
                            } else {
                                openSection(ZunoRoute.Chats(ChatsFilter.People))
                            }
                        }
                        SystemNotificationKind.LiveRoomInvite -> {
                            val roomId = item.roomId
                            if (!roomId.isNullOrBlank()) navigate(ZunoRoute.LiveRoomLobby(roomId))
                            else openSection(ZunoRoute.LiveRooms)
                        }
                        SystemNotificationKind.Announcement -> navigate(ZunoRoute.SettingsTutor)
                        SystemNotificationKind.StreakMilestone -> openSection(ZunoRoute.Progress)
                        SystemNotificationKind.ScheduledSession -> openSection(ZunoRoute.Scheduled)
                    }
                },
                modifier = modifier,
            )
        }

        ZunoRoute.Settings -> SettingsOverviewScreen(
            profile = profile,
            appLanguage = appLanguage,
            onBack = pop,
            onUpgrade = { navigate(ZunoRoute.Paywall) },
            onAccount = { navigate(ZunoRoute.SettingsAccount) },
            onLearning = { navigate(ZunoRoute.SettingsLearning) },
            onTutor = { navigate(ZunoRoute.SettingsTutor) },
            onAppLanguage = { navigate(ZunoRoute.SettingsAppLanguage) },
            onPreferences = { navigate(ZunoRoute.SettingsPreferences) },
            onNotifications = { navigate(ZunoRoute.SettingsNotifications) },
            onAbout = { navigate(ZunoRoute.SettingsAbout) },
            onContactSupport = onContactSupport,
            onSignOut = onSignOut,
            onDeleteAccount = onDeleteAccount,
            availableUpdateVersion = appUpdateInfo?.takeIf { it.hasUpdate }?.latestVersion,
            modifier = modifier,
        )

        ZunoRoute.SettingsPreferences -> SettingsPreferencesScreen(
            preferences = preferences ?: com.zuno.ai.data.Preferences(),
            targetLanguage = language,
            appLanguage = appLanguage,
            onChange = zunoViewModel::updatePreferences,
            onOpenTutor = { navigate(ZunoRoute.SettingsTutor) },
            onBack = pop,
            modifier = modifier,
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
            onBack = pop,
            modifier = modifier,
        )

        ZunoRoute.SettingsAppLanguage -> SettingsAppLanguageScreen(
            selectedLanguage = appLanguage,
            onSelectLanguage = selectAppLanguage,
            onBack = pop,
            modifier = modifier,
        )

        ZunoRoute.SettingsLearning -> SettingsLearningScreen(
            profile = profile,
            appLanguage = appLanguage,
            onOpenLanguages = { navigate(ZunoRoute.SettingsLearningLanguage) },
            onAccentChange = { zunoViewModel.updateProfile(accent = it) },
            onLevelChange = { zunoViewModel.updateProfile(level = it) },
            onFindLevel = startLevelCheck,
            onPreferencesChange = zunoViewModel::updatePreferences,
            onBack = pop,
            onUpdateInterests = { interests -> zunoViewModel.updateProfile(interests = interests) },
            modifier = modifier,
        )

        ZunoRoute.SettingsLearningLanguage -> SettingsLearningLanguageScreen(
            languages = languages,
            profile = profile,
            appLanguage = appLanguage,
            busy = busy,
            onSwitch = { target ->
                if (hasActiveVoice) endVoice()
                zunoViewModel.switchLearningLanguage(target.code) { updated ->
                    if (canReturnBackAfterLangSwitch) pop()
                    toasts.show(
                        ToastEvent(
                            str(R.string.learning_language_switched, arrayOf(updated.language.displayName(localizedContext, appLanguage))),
                            ToastIcon.Check,
                        ),
                    )
                }
            },
            onBack = pop,
            modifier = modifier,
        )

        ZunoRoute.SettingsAccount -> SettingsAccountScreen(
            profile = profile,
            uploadingPhoto = uploadingPhoto,
            onUpdateName = { zunoViewModel.updateProfile(name = it) },
            onChangePhoto = onPickPhoto,
            onTakePhoto = onTakePhoto,
            onRemovePhoto = {
                zunoViewModel.removeAvatar { ok ->
                    if (ok) toasts.show(ToastEvent(str(R.string.account_photo_removed, emptyArray()), ToastIcon.Trash))
                }
            },
            onPlanClick = { navigate(ZunoRoute.Paywall) },
            onDownloadData = {
                zunoViewModel.requestDataExport { email ->
                    toasts.show(
                        ToastEvent(
                            str(R.string.account_export_sent_toast, arrayOf(email.ifBlank { profile?.email.orEmpty() })),
                            ToastIcon.Check,
                        ),
                    )
                }
            },
            onDeleteAccount = onDeleteAccount,
            onBack = pop,
            openToSpeak = chatsOverview.items?.openToSpeak ?: true,
            onToggleOpenToSpeak = { enabled -> zunoViewModel.toggleOpenToSpeak(enabled) },
            onOpenPrivacy = { navigate(ZunoRoute.SettingsPrivacy) },
            modifier = modifier,
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
                        toasts.show(ToastEvent(str(R.string.account_privacy_unblocked_toast, emptyArray()), ToastIcon.Check))
                    }
                },
                onBack = pop,
                modifier = modifier,
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
                onBack = pop,
                modifier = modifier,
            )
        }

        ZunoRoute.SettingsAbout -> {
            var checkingUpdates by remember { mutableStateOf(false) }
            SettingsAboutScreen(
                availableUpdate = appUpdateInfo,
                onWhatsNew = { navigate(ZunoRoute.SettingsWhatsNew) },
                onUpdateNow = { openPlayStoreForUpdate(appUpdateInfo?.storeUrl ?: AppUpdateInfo.PLAY_STORE_HTTPS_URL) },
                onRateApp = { openPlayStoreForUpdate(AppUpdateInfo.PLAY_STORE_HTTPS_URL) },
                onTerms = openTerms,
                onPrivacy = openPrivacy,
                onCheckForUpdates = {
                    if (!checkingUpdates) {
                        checkingUpdates = true
                        zunoViewModel.checkForAppUpdate { info ->
                            checkingUpdates = false
                            if (info?.hasUpdate != true) {
                                toasts.show(
                                    ToastEvent(
                                        context.getString(R.string.about_up_to_date_toast, BuildConfig.VERSION_NAME),
                                        ToastIcon.Check,
                                    ),
                                )
                            }
                        }
                    }
                },
                isCheckingUpdates = checkingUpdates,
                onBack = pop,
                modifier = modifier,
            )
        }

        ZunoRoute.SettingsWhatsNew -> SettingsWhatsNewScreen(
            availableUpdate = appUpdateInfo,
            onUpdateNow = { openPlayStoreForUpdate(appUpdateInfo?.storeUrl ?: AppUpdateInfo.PLAY_STORE_HTTPS_URL) },
            onBack = pop,
            modifier = modifier,
        )

        ZunoRoute.Paywall -> PaywallScreen(
            onClose = pop,
            onStartTrial = { yearly ->
                toasts.show(
                    ToastEvent(
                        if (yearly) "7-day Zuno Pro trial selected" else "Monthly Zuno Pro plan selected",
                        ToastIcon.Check,
                    ),
                )
                replace(ZunoRoute.WelcomeToPro)
            },
            onOpenTerms = openTerms,
            onOpenPrivacy = openPrivacy,
            onRestorePurchases = { toasts.show(ToastEvent("No previous purchases found", ToastIcon.Alert)) },
            modifier = modifier,
        )

        ZunoRoute.WelcomeToPro -> WelcomeToProScreen(
            onStartPractising = navigateHomeClear,
            onManageSubscription = { replace(ZunoRoute.SettingsAccount) },
            modifier = modifier,
        )

        else -> Unit
    }
}
