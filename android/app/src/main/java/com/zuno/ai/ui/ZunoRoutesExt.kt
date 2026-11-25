package com.zuno.ai.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuno.ai.R
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.CartesiaAudioPlayer
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Profile
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.push.CallNotification
import com.zuno.ai.ui.components.ToastController
import com.zuno.ai.ui.components.ZunoSpinner
import com.zuno.ai.ui.screens.ActiveLiveRoomScreen
import com.zuno.ai.ui.screens.CreateLiveRoomScreen
import com.zuno.ai.ui.screens.LiveRoomLobbyScreen
import com.zuno.ai.ui.screens.LiveRoomsScreen
import com.zuno.ai.ui.screens.SprintEvaluationScreen
import com.zuno.ai.ui.screens.SprintReviewResult
import com.zuno.ai.ui.screens.SystemRoomDetailScreen
import com.zuno.ai.ui.screens.VocabularyScreen
import com.zuno.ai.ui.screens.VocabularySprintScreen
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
            VocabularySprintScreen(
                phrases = deck,
                languageName = language.displayName(appLanguage),
                tutor = activeTutor,
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

    when (screen) {
        ZunoRoute.LiveRooms -> {
            LaunchedEffect(Unit) { zunoViewModel.loadLiveRooms() }
            LiveRoomsScreen(
                languageName = language.displayName(appLanguage),
                level = profile?.level ?: "B1",
                browse = liveRooms,
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
                while (true) {
                    zunoViewModel.refreshLiveRoom(screen.roomId)
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
                        voiceViewModel.disconnect()
                        onSetActiveRoom(null, null)
                        CallNotification.cancel(context)
                        zunoViewModel.endLiveRoom(roomObj.id) { pop() }
                    },
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
