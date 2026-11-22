package com.zuno.ai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuno.ai.avatar.audio.AvatarSpeechSink
import com.zuno.ai.avatar.audio.LiveKitAvatarAudioTap
import com.zuno.ai.data.LessonEvaluation
import com.zuno.ai.data.LessonSessionTicket
import com.zuno.ai.data.LessonStageUi
import com.zuno.ai.data.LessonTurnUi
import com.zuno.ai.data.LivekitTicket
import com.zuno.ai.data.OnboardingVoiceTurnEvent
import com.zuno.ai.data.PronunciationEvalUi
import com.zuno.ai.data.RescuePayloadUi
import com.zuno.ai.data.toLessonEvaluation
import com.zuno.ai.data.toLessonStageUi
import com.zuno.ai.data.toLessonTurnUi
import com.zuno.ai.data.toOnboardingVoiceTurnEvent
import com.zuno.ai.data.toPronunciationEvalUi
import com.zuno.ai.data.toRescuePayloadUi
import com.zuno.ai.model.AgentState
import com.zuno.ai.model.ChatMessage
import com.zuno.ai.model.Speaker
import com.zuno.ai.service.SessionExpiredException
import com.zuno.ai.service.TokenService
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.sqrt
import kotlin.random.Random

class VoiceSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _agentState = MutableStateFlow(AgentState.DISCONNECTED)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _activeSpeaker = MutableStateFlow<Speaker?>(null)
    val activeSpeaker: StateFlow<Speaker?> = _activeSpeaker.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _roomName = MutableStateFlow("")
    val roomName: StateFlow<String> = _roomName.asStateFlow()

    private val _latencyMs = MutableStateFlow<Int?>(null)
    val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

    // ── 03g Interactive Lesson Data-Channel State ───────────────────────────
    private val _lessonStage = MutableStateFlow<LessonStageUi?>(null)
    val lessonStage: StateFlow<LessonStageUi?> = _lessonStage.asStateFlow()

    private val _pronunciationEval = MutableStateFlow<PronunciationEvalUi?>(null)
    val pronunciationEval: StateFlow<PronunciationEvalUi?> = _pronunciationEval.asStateFlow()

    private val _rescuePayload = MutableStateFlow<RescuePayloadUi?>(null)
    val rescuePayload: StateFlow<RescuePayloadUi?> = _rescuePayload.asStateFlow()

    private val _completedLessonEvaluation = MutableStateFlow<LessonEvaluation?>(null)
    val completedLessonEvaluation: StateFlow<LessonEvaluation?> = _completedLessonEvaluation.asStateFlow()

    private val _lessonTurns = MutableStateFlow<List<LessonTurnUi>>(emptyList())
    val lessonTurns: StateFlow<List<LessonTurnUi>> = _lessonTurns.asStateFlow()

    private val _onboardingEvents = MutableSharedFlow<OnboardingVoiceTurnEvent>(extraBufferCapacity = 8)
    val onboardingEvents: SharedFlow<OnboardingVoiceTurnEvent> = _onboardingEvents.asSharedFlow()

    /** Normalized (0..1) loudness of the user's mic and the agent's voice. Poll `.value`; updated ~20×/s. */
    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel.asStateFlow()
    private val _agentAudioLevel = MutableStateFlow(0f)
    val agentAudioLevel: StateFlow<Float> = _agentAudioLevel.asStateFlow()

    // ── Video & 1:1 Peer Call Room State ────────────────────────────────────
    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _partnerJoined = MutableStateFlow(false)
    val partnerJoined: StateFlow<Boolean> = _partnerJoined.asStateFlow()

    val activeRoom: Room? get() = currentRoom

    /** Real-time PCM audio sink driving dynamic client-side tutor avatar lip-sync. */
    val avatarSpeechSink = AvatarSpeechSink()
    private val avatarAudioTap = LiveKitAvatarAudioTap(avatarSpeechSink)

    private var currentRoom: Room? = null
    private var eventJob: Job? = null
    private var connectJob: Job? = null
    private var levelJob: Job? = null

    /**
     * Joins a LiveKit room for Free Talk or Roleplay using the user's Cartesia multilingual tutor voice.
     */
    fun connect(
        sessionToken: String? = null,
        onSessionExpired: () -> Unit = {},
        conversationId: String? = null,
        language: String? = null,
        speakLastReply: Boolean = false,
    ) {
        if (_agentState.value != AgentState.DISCONNECTED) return
        if (sessionToken == null) {
            _errorMessage.value = "Please sign in to start a voice session."
            return
        }
        resetLessonState()
        _errorMessage.value = null
        _agentState.value = AgentState.CONNECTING

        connectJob = viewModelScope.launch {
            try {
                val tokenResult = TokenService.fetchToken(
                    sessionToken = sessionToken,
                    language = language,
                    conversationId = conversationId,
                    speakLastReply = speakLastReply,
                )
                joinRoom(tokenResult.url, tokenResult.token, tokenResult.room)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                _errorMessage.value = e.message
                disconnectInternal()
                onSessionExpired()
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _errorMessage.value = e.message ?: "Connection failed. Please check network."
                disconnectInternal()
            }
        }
    }

    /**
     * Joins a LiveKit room in `mode = "onboarding"` (`5b`–`5d` guided onboarding).
     */
    fun connectOnboarding(
        sessionToken: String?,
        step: String,
        targetLanguage: String,
        nativeLanguage: String,
        accent: String? = null,
        name: String? = null,
        level: String? = null,
        onSessionExpired: () -> Unit = {},
    ) {
        if (_agentState.value != AgentState.DISCONNECTED) {
            disconnectInternal()
        }
        if (sessionToken == null) {
            _errorMessage.value = "Please sign in to start onboarding."
            return
        }
        resetLessonState()
        _messages.value = emptyList()
        _errorMessage.value = null
        _agentState.value = AgentState.CONNECTING

        connectJob = viewModelScope.launch {
            try {
                val tokenResult = TokenService.fetchOnboardingToken(
                    sessionToken = sessionToken,
                    step = step,
                    targetLanguage = targetLanguage,
                    nativeLanguage = nativeLanguage,
                    accent = accent,
                    name = name,
                    level = level,
                )
                joinRoom(tokenResult.url, tokenResult.token, tokenResult.room)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                _errorMessage.value = e.message
                disconnectInternal()
                onSessionExpired()
            } catch (e: Exception) {
                Log.e(TAG, "Onboarding voice connection failed", e)
                _errorMessage.value = e.message ?: "Connection failed. Please check network."
                disconnectInternal()
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun sendOnboardingAction(action: String, extras: Map<String, Any?> = emptyMap()) {
        val room = currentRoom ?: return
        viewModelScope.launch {
            try {
                val packet = JSONObject().apply {
                    put("type", "onboarding_action")
                    put("action", action)
                    extras.forEach { (k, v) -> if (v != null) put(k, v) }
                }.toString().toByteArray(Charsets.UTF_8)
                room.localParticipant.publishData(packet, DataPublishReliability.RELIABLE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send onboarding_action ($action)", e)
            }
        }
    }
    fun connectLesson(ticket: LessonSessionTicket) {
        if (_agentState.value != AgentState.DISCONNECTED) {
            disconnectInternal()
        }
        resetLessonState()
        _errorMessage.value = null
        _agentState.value = AgentState.CONNECTING

        connectJob = viewModelScope.launch {
            try {
                joinRoom(ticket.url, ticket.token, ticket.room)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lesson connection failed", e)
                _errorMessage.value = e.message ?: "Connection failed. Please check network."
                disconnectInternal()
            }
        }
    }

    fun connectPeerCall(ticket: LivekitTicket, enableCamera: Boolean = false) {
        if (_agentState.value != AgentState.DISCONNECTED) {
            disconnectInternal()
        }
        resetLessonState()
        _messages.value = emptyList()
        _partnerJoined.value = false
        _errorMessage.value = null
        _agentState.value = AgentState.CONNECTING

        connectJob = viewModelScope.launch {
            try {
                joinRoom(ticket.url, ticket.token, ticket.room, enableCamera = enableCamera, isPeerRoom = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Peer call connection failed", e)
                _errorMessage.value = e.message ?: "Connection failed. Please check network."
                disconnectInternal()
            }
        }
    }

    private suspend fun joinRoom(
        url: String,
        token: String,
        roomName: String,
        enableCamera: Boolean = false,
        isPeerRoom: Boolean = false,
    ) {
        _roomName.value = roomName
        val room = LiveKit.create(getApplication())
        currentRoom = room

        eventJob = viewModelScope.launch {
            room.events.events.collect { event ->
                handleRoomEvent(event, isPeerRoom)
            }
        }

        room.connect(url, token)
        room.localParticipant.setMicrophoneEnabled(true)
        if (enableCamera) {
            runCatching { room.localParticipant.setCameraEnabled(true) }
                .onSuccess {
                    _isCameraEnabled.value = true
                    refreshVideoTracks(room)
                }
        } else {
            _isCameraEnabled.value = false
        }
        _isSpeakerOn.value = true
        room.setSpeakerMute(false)
        levelJob = viewModelScope.launch { pollAudioLevels(room) }
        _isMuted.value = false
        _agentState.value = AgentState.IDLE
        if (isPeerRoom && room.remoteParticipants.values.any { !it.identity?.value.orEmpty().startsWith("agent-") }) {
            _partnerJoined.value = true
        }
    }

    fun clearCompletedLessonEvaluation() {
        _completedLessonEvaluation.value = null
    }

    private fun resetLessonState() {
        _lessonStage.value = null
        _pronunciationEval.value = null
        _rescuePayload.value = null
        _completedLessonEvaluation.value = null
        _lessonTurns.value = emptyList()
    }

    /** Hangs up synchronously, so a new call can be started right away. */
    fun disconnect() = disconnectInternal()

    private fun disconnectInternal() {
        connectJob?.cancel()
        connectJob = null
        eventJob?.cancel()
        eventJob = null
        levelJob?.cancel()
        levelJob = null
        _userAudioLevel.value = 0f
        _agentAudioLevel.value = 0f
        _isCameraEnabled.value = false
        _isSpeakerOn.value = true
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _partnerJoined.value = false
        avatarAudioTap.detachAll()
        currentRoom?.let { room ->
            try {
                room.disconnect()
                room.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting room", e)
            }
        }
        currentRoom = null
        _agentState.value = AgentState.DISCONNECTED
        _activeSpeaker.value = null
        _latencyMs.value = null
        _lessonStage.value = null
        _pronunciationEval.value = null
        _rescuePayload.value = null
        _lessonTurns.value = emptyList()
    }

    fun toggleMute() {
        val room = currentRoom ?: return
        viewModelScope.launch {
            try {
                val nextMuted = !_isMuted.value
                room.localParticipant.setMicrophoneEnabled(!nextMuted)
                _isMuted.value = nextMuted
            } catch (e: Exception) {
                Log.w(TAG, "Failed to toggle microphone", e)
            }
        }
    }

    fun toggleCamera(enable: Boolean? = null) {
        val room = currentRoom ?: return
        viewModelScope.launch {
            try {
                val next = enable ?: !_isCameraEnabled.value
                room.localParticipant.setCameraEnabled(next)
                _isCameraEnabled.value = next
                refreshVideoTracks(room)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to toggle camera", e)
            }
        }
    }

    fun toggleSpeaker() {
        val room = currentRoom ?: return
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        runCatching { room.setSpeakerMute(!next) }
    }

    fun sendPingPartner() {
        val room = currentRoom ?: return
        viewModelScope.launch {
            try {
                val packet = JSONObject().apply { put("type", "ping_partner") }.toString().toByteArray(Charsets.UTF_8)
                room.localParticipant.publishData(packet, DataPublishReliability.RELIABLE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send ping_partner", e)
            }
        }
    }

    private fun refreshVideoTracks(room: Room) {
        val local = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        _localVideoTrack.value = if (_isCameraEnabled.value) local else null
        val remote = room.remoteParticipants.values
            .firstOrNull()
            ?.getTrackPublication(Track.Source.CAMERA)
            ?.track as? VideoTrack
        _remoteVideoTrack.value = remote
        syncRemoteAudioSinks(room)
    }

    private fun syncRemoteAudioSinks(room: Room) {
        val tracks = buildList {
            for (participant in room.remoteParticipants.values) {
                for (pub in participant.trackPublications.values) {
                    val audioTrack = pub.track as? RemoteAudioTrack
                    if (audioTrack != null) add(audioTrack)
                }
            }
        }
        avatarAudioTap.syncRemoteTracks(tracks)
    }

    fun interrupt() {
        val room = currentRoom ?: return
        viewModelScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "interrupt")
                }.toString().toByteArray(Charsets.UTF_8)
                room.localParticipant.publishData(payload, DataPublishReliability.RELIABLE)
                if (_agentState.value == AgentState.SPEAKING) {
                    _agentState.value = AgentState.LISTENING
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send interrupt signal", e)
            }
        }
    }

    fun sendLessonAction(action: String, payloadText: String? = null) {
        val room = currentRoom ?: return
        viewModelScope.launch {
            try {
                val packet = JSONObject().apply {
                    put("type", "lesson_action")
                    put("action", action)
                    payloadText?.let { put("payload", it) }
                }.toString().toByteArray(Charsets.UTF_8)
                room.localParticipant.publishData(packet, DataPublishReliability.RELIABLE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send lesson_action ($action)", e)
            }
        }
    }

    private fun handleRoomEvent(event: RoomEvent, isPeerRoom: Boolean = false) {
        val room = currentRoom
        when (event) {
            is RoomEvent.Connected -> {
                _agentState.value = AgentState.IDLE
                if (room != null) refreshVideoTracks(room)
            }
            is RoomEvent.ParticipantConnected -> {
                if (isPeerRoom && !event.participant.identity?.value.orEmpty().startsWith("agent-")) {
                    _partnerJoined.value = true
                }
            }
            is RoomEvent.ParticipantDisconnected -> {
                if (room != null) refreshVideoTracks(room)
            }
            is RoomEvent.TrackPublished,
            is RoomEvent.TrackUnpublished,
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed -> {
                if (room != null) refreshVideoTracks(room)
            }
            is RoomEvent.ActiveSpeakersChanged -> {
                val localIdentity = currentRoom?.localParticipant?.identity?.value
                val userSpeaking = event.speakers.any { it.identity?.value == localIdentity }
                val agentSpeaking = event.speakers.any { it.identity?.value != null && it.identity?.value != localIdentity }

                when {
                    userSpeaking -> {
                        _activeSpeaker.value = Speaker.USER
                        _agentState.value = AgentState.LISTENING
                    }
                    agentSpeaking -> {
                        _activeSpeaker.value = Speaker.AGENT
                        _agentState.value = AgentState.SPEAKING
                    }
                    else -> {
                        _activeSpeaker.value = null
                        if (_agentState.value == AgentState.LISTENING || _agentState.value == AgentState.SPEAKING) {
                            _agentState.value = AgentState.IDLE
                        }
                    }
                }
            }
            is RoomEvent.DataReceived -> {
                try {
                    val text = String(event.data, Charsets.UTF_8)
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "agent_state" -> {
                            val stateStr = json.optString("state").lowercase()
                            when {
                                stateStr.contains("listening") -> _agentState.value = AgentState.LISTENING
                                stateStr.contains("thinking") -> _agentState.value = AgentState.THINKING
                                stateStr.contains("speaking") -> _agentState.value = AgentState.SPEAKING
                                else -> _agentState.value = AgentState.IDLE
                            }
                        }
                        "transcript" -> {
                            val speakerStr = json.optString("speaker")
                            val speaker = if (speakerStr == "user") Speaker.USER else Speaker.AGENT
                            val rawText = json.optString("text")
                            val messageText = if (speaker == Speaker.AGENT) stripVoiceTags(rawText) else rawText
                            val isFinal = json.optBoolean("is_final", true)
                            addOrUpdateMessage(speaker, messageText, isFinal)
                        }
                        "metrics" -> {
                            val total = json.optInt("total_ms", -1)
                            if (total > 0) {
                                _latencyMs.value = total
                            }
                        }
                        "lesson_stage" -> {
                            _lessonStage.value = json.toLessonStageUi()
                            _pronunciationEval.value = null
                            _rescuePayload.value = null
                        }
                        "lesson_turn" -> {
                            val turn = json.toLessonTurnUi()
                            if (turn.text.isNotBlank()) {
                                _lessonTurns.update { current ->
                                    if (current.any { it.id == turn.id }) current else (current + turn).takeLast(12)
                                }
                            }
                        }
                        "lesson_turn_update" -> {
                            val targetId = json.optString("id")
                            val newLabel = json.optString("senderLabel").takeIf { it.isNotBlank() }
                            val newTranslation = json.optString("translation").takeIf { it.isNotBlank() }
                            if (targetId.isNotBlank()) {
                                _lessonTurns.update { current ->
                                    current.map { t ->
                                        if (t.id == targetId) {
                                            t.copy(
                                                senderLabel = newLabel ?: t.senderLabel,
                                                translation = newTranslation ?: t.translation,
                                            )
                                        } else {
                                            t
                                        }
                                    }
                                }
                            }
                        }
                        "pronunciation_eval" -> {
                            _pronunciationEval.value = json.toPronunciationEvalUi()
                        }
                        "rescue_payload" -> {
                            _rescuePayload.value = json.toRescuePayloadUi()
                        }
                        "lesson_complete" -> {
                            val lessonId = json.optString("lessonId")
                            val evalObj = json.optJSONObject("evaluation") ?: JSONObject()
                            _completedLessonEvaluation.value = evalObj.toLessonEvaluation(lessonId)
                        }
                        "onboarding_turn" -> {
                            _onboardingEvents.tryEmit(json.toOnboardingVoiceTurnEvent())
                        }
                        "partner_joined" -> {
                            _partnerJoined.value = true
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Ignored non-json or unparsed data channel packet", e)
                }
            }
            is RoomEvent.Disconnected -> {
                _agentState.value = AgentState.DISCONNECTED
                _activeSpeaker.value = null
                _latencyMs.value = null
            }
            else -> Unit
        }
    }

    private suspend fun pollAudioLevels(room: Room) {
        while (true) {
            val user = if (_isMuted.value) 0f else room.localParticipant.audioLevel
            val agent = room.remoteParticipants.values.maxOfOrNull { it.audioLevel } ?: 0f
            _userAudioLevel.value = normalizeLevel(user)
            _agentAudioLevel.value = normalizeLevel(agent)
            delay(LEVEL_POLL_MS)
        }
    }

    /** LiveKit levels sit mostly in 0..0.3 for speech; lift them into a usable 0..1 range. */
    private fun normalizeLevel(raw: Float): Float = (sqrt(raw.coerceAtLeast(0f)) * 1.6f).coerceIn(0f, 1f)

    private fun addOrUpdateMessage(speaker: Speaker, text: String, isFinal: Boolean) {
        if (text.isBlank()) return
        _messages.update { currentList ->
            val last = currentList.lastOrNull()
            if (last != null && last.speaker == speaker && !last.isFinal) {
                val updated = currentList.toMutableList()
                updated[updated.lastIndex] = last.copy(
                    text = text,
                    isFinal = isFinal,
                    timestamp = System.currentTimeMillis(),
                )
                updated
            } else {
                currentList + ChatMessage(
                    id = "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}",
                    speaker = speaker,
                    text = text,
                    isFinal = isFinal,
                    timestamp = System.currentTimeMillis(),
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectInternal()
    }

    companion object {
        private const val TAG = "VoiceSessionViewModel"
        private const val LEVEL_POLL_MS = 50L

        private fun stripVoiceTags(text: String): String {
            return text
                .replace(Regex("<lang=\"?[a-zA-Z_-]+\"?>([\\s\\S]*?)</lang>"), "$1")
                .replace(Regex("\\[[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?\\]([\\s\\S]*?)\\[/[^\\]]+\\]"), "$1")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }
    }
}
