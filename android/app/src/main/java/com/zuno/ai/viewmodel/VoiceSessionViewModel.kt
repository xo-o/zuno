package com.zuno.ai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuno.ai.data.LessonEvaluation
import com.zuno.ai.data.LessonSessionTicket
import com.zuno.ai.data.LessonStageUi
import com.zuno.ai.data.PronunciationEvalUi
import com.zuno.ai.data.RescuePayloadUi
import com.zuno.ai.data.toLessonEvaluation
import com.zuno.ai.data.toLessonStageUi
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** Normalized (0..1) loudness of the user's mic and the agent's voice. Poll `.value`; updated ~20×/s. */
    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel.asStateFlow()
    private val _agentAudioLevel = MutableStateFlow(0f)
    val agentAudioLevel: StateFlow<Float> = _agentAudioLevel.asStateFlow()

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
     * Joins an interactive 03g Voice Lesson room using a server-minted [LessonSessionTicket].
     */
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

    private suspend fun joinRoom(url: String, token: String, roomName: String) {
        _roomName.value = roomName
        val room = LiveKit.create(getApplication())
        currentRoom = room

        eventJob = viewModelScope.launch {
            room.events.events.collect { event ->
                handleRoomEvent(event)
            }
        }

        room.connect(url, token)
        room.localParticipant.setMicrophoneEnabled(true)
        levelJob = viewModelScope.launch { pollAudioLevels(room) }
        _isMuted.value = false
        _agentState.value = AgentState.IDLE
    }

    fun clearCompletedLessonEvaluation() {
        _completedLessonEvaluation.value = null
    }

    private fun resetLessonState() {
        _lessonStage.value = null
        _pronunciationEval.value = null
        _rescuePayload.value = null
        _completedLessonEvaluation.value = null
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

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Connected -> {
                _agentState.value = AgentState.IDLE
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
