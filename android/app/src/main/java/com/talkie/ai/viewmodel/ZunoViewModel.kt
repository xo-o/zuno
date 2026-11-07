package com.talkie.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.talkie.ai.auth.SessionTokenStore
import com.talkie.ai.data.ApiException
import com.talkie.ai.data.Conversation
import com.talkie.ai.data.ConversationThread
import com.talkie.ai.data.Message
import com.talkie.ai.data.Profile
import com.talkie.ai.data.RecentSession
import com.talkie.ai.data.Roleplay
import com.talkie.ai.data.TargetLanguage
import com.talkie.ai.data.TopicDetail
import com.talkie.ai.data.TopicSummary
import com.talkie.ai.data.ZunoApi
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

/** A list loaded from the API. [items] is null until the first successful load. */
data class Loadable<T>(val items: T? = null, val loading: Boolean = false, val error: String? = null)

data class ChatState(
    val conversation: Conversation,
    val messages: List<Message>,
    /** Learner text sent but not yet answered — rendered as an optimistic bubble. */
    val pendingText: String? = null,
    val error: String? = null,
)

class ZunoViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SessionTokenStore(application)
    private val api = ZunoApi(tokenProvider = tokenStore::read)

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentSession>>(emptyList())
    val recent: StateFlow<List<RecentSession>> = _recent.asStateFlow()

    private val _topics = MutableStateFlow(Loadable<List<TopicSummary>>())
    val topics: StateFlow<Loadable<List<TopicSummary>>> = _topics.asStateFlow()

    private val _topicDetails = MutableStateFlow<Map<String, TopicDetail>>(emptyMap())
    val topicDetails: StateFlow<Map<String, TopicDetail>> = _topicDetails.asStateFlow()

    private val _roleplays = MutableStateFlow(Loadable<List<Roleplay>>())
    val roleplays: StateFlow<Loadable<List<Roleplay>>> = _roleplays.asStateFlow()

    private val _chats = MutableStateFlow<Map<String, ChatState>>(emptyMap())
    val chats: StateFlow<Map<String, ChatState>> = _chats.asStateFlow()

    /** True while a create/open request that leads to navigation is running. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _typingHint = MutableStateFlow<String?>(null)
    val typingHint: StateFlow<String?> = _typingHint.asStateFlow()
    private var hintJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off user-facing messages (errors, confirmations). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /** Loads everything the signed-in shell needs (drawer + home). */
    fun refreshAccount() {
        launchSafely { _profile.value = api.profile() }
        refreshRecent()
    }

    fun refreshRecent() = launchSafely(silent = true) { _recent.value = api.recentSessions() }

    fun clear() {
        _profile.value = null
        _recent.value = emptyList()
        _topics.value = Loadable()
        _topicDetails.value = emptyMap()
        _roleplays.value = Loadable()
        _chats.value = emptyMap()
        _typingHint.value = null
    }

    fun updateProfile(targetLanguage: TargetLanguage? = null, level: String? = null, name: String? = null) =
        launchSafely {
            _profile.value = api.updateProfile(targetLanguage, level, name)
            if (targetLanguage != null) _typingHint.value = null
        }

    // ── Topics ──────────────────────────────────────────────────────────────

    fun loadTopics() {
        _topics.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _topics.value = Loadable(api.topics())
            } catch (e: ApiException) {
                handleAuth(e)
                _topics.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun createTopic(prompt: String, onCreated: (TopicDetail) -> Unit) = runBusy {
        val topic = api.createTopic(prompt)
        cacheTopic(topic)
        _topics.update { state -> state.copy(items = listOf(topic.summary) + state.items.orEmpty()) }
        refreshRecent()
        onCreated(topic)
    }

    fun loadTopic(id: String) = launchSafely { cacheTopic(api.topic(id)) }

    fun deleteTopic(id: String) = launchSafely {
        api.deleteTopic(id)
        _topics.update { state -> state.copy(items = state.items?.filterNot { it.id == id }) }
        _topicDetails.update { it - id }
        refreshRecent()
    }

    fun resetTopic(id: String) = launchSafely {
        cacheTopic(api.resetTopic(id))
        _chats.update { chats -> chats.filterValues { it.conversation.topicId != id } }
        refreshRecent()
    }

    fun startLesson(topicId: String, lessonId: String, onOpened: (Conversation) -> Unit) = runBusy {
        val thread = api.startLesson(topicId, lessonId)
        cacheThread(thread)
        onOpened(thread.conversation)
    }

    fun completeLesson(topicId: String, lessonId: String) = launchSafely {
        cacheTopic(api.completeLesson(topicId, lessonId))
        refreshRecent()
        _messages.tryEmit("Lesson complete")
    }

    private fun cacheTopic(topic: TopicDetail) {
        _topicDetails.update { it + (topic.summary.id to topic) }
        _topics.update { state ->
            state.copy(items = state.items?.map { if (it.id == topic.summary.id) topic.summary else it })
        }
    }

    // ── Roleplays ───────────────────────────────────────────────────────────

    fun loadRoleplays() {
        _roleplays.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _roleplays.value = Loadable(api.roleplays())
            } catch (e: ApiException) {
                handleAuth(e)
                _roleplays.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun createRoleplay(scenario: String, userRole: String, aiRole: String, onOpened: (Conversation) -> Unit) = runBusy {
        val thread = api.createRoleplay(scenario, userRole, aiRole)
        cacheThread(thread)
        loadRoleplays()
        refreshRecent()
        onOpened(thread.conversation)
    }

    fun openRoleplay(id: String, restart: Boolean = false, onOpened: (Conversation) -> Unit) = runBusy {
        val thread = if (restart) api.restartRoleplay(id) else api.openRoleplay(id)
        cacheThread(thread)
        refreshRecent()
        onOpened(thread.conversation)
    }

    fun deleteRoleplay(id: String) = launchSafely {
        api.deleteRoleplay(id)
        _roleplays.update { state -> state.copy(items = state.items?.filterNot { it.id == id }) }
        refreshRecent()
    }

    // ── Conversations ───────────────────────────────────────────────────────

    /**
     * Creates a Free Talk thread; when [firstMessage] is given it is sent right away and
     * [onReplied] reports whether the tutor answered it.
     */
    fun startFreeTalk(
        firstMessage: String? = null,
        viaVoice: Boolean = false,
        onReplied: ((Boolean) -> Unit)? = null,
        onOpened: (Conversation) -> Unit,
    ) = runBusy {
        val thread = api.createFreeTalk()
        cacheThread(thread)
        onOpened(thread.conversation)
        if (!firstMessage.isNullOrBlank()) send(thread.conversation.id, firstMessage, viaVoice, onReplied)
        refreshRecent()
    }

    fun loadConversation(id: String) = launchSafely { cacheThread(api.conversation(id)) }

    fun deleteConversation(id: String, onDeleted: () -> Unit) = launchSafely {
        api.deleteConversation(id)
        _chats.update { it - id }
        refreshRecent()
        onDeleted()
    }

    fun send(
        conversationId: String,
        text: String,
        viaVoice: Boolean = false,
        onReplied: ((Boolean) -> Unit)? = null,
    ) {
        val current = _chats.value[conversationId]
        if (current == null || current.pendingText != null) {
            onReplied?.invoke(false)
            return
        }
        updateChat(conversationId) { it.copy(pendingText = text, error = null) }
        _typingHint.value = null
        viewModelScope.launch {
            try {
                val result = api.sendMessage(conversationId, text, viaVoice)
                updateChat(conversationId) {
                    it.copy(
                        conversation = result.conversation,
                        messages = it.messages + result.userMessage + result.reply,
                        pendingText = null,
                    )
                }
                onReplied?.invoke(true)
                if (result.lessonCompleted) {
                    result.conversation.topicId?.let { loadTopic(it) }
                    _messages.tryEmit("Lesson complete — nice work!")
                }
                refreshRecent()
            } catch (e: ApiException) {
                handleAuth(e)
                updateChat(conversationId) { it.copy(pendingText = null, error = e.message) }
                onReplied?.invoke(false)
            }
        }
    }

    /** Persists turns spoken in a live voice session so they survive in history. */
    fun saveVoiceTurns(conversationId: String, turns: List<Pair<Boolean, String>>) {
        if (turns.isEmpty()) return
        launchSafely(silent = true) {
            val saved = api.saveTranscript(conversationId, turns)
            updateChat(conversationId) { it.copy(messages = it.messages + saved) }
            refreshRecent()
        }
    }

    suspend fun explain(messageId: String): Result<String> = try {
        Result.success(api.explain(messageId))
    } catch (e: ApiException) {
        handleAuth(e)
        Result.failure(e)
    }

    /** Debounced live tip for the composer; clears when the draft is too short. */
    fun onDraftChanged(draft: String) {
        hintJob?.cancel()
        val text = draft.trim()
        if (text.length < 4) {
            _typingHint.value = null
            return
        }
        hintJob = viewModelScope.launch {
            delay(HINT_DEBOUNCE_MS)
            try {
                _typingHint.value = api.typingHint(text)
            } catch (e: ApiException) {
                handleAuth(e)
            }
        }
    }

    fun clearHint() {
        hintJob?.cancel()
        _typingHint.value = null
    }

    private fun cacheThread(thread: ConversationThread) {
        _chats.update { it + (thread.conversation.id to ChatState(thread.conversation, thread.messages)) }
    }

    private fun updateChat(id: String, transform: (ChatState) -> ChatState) {
        _chats.update { chats -> chats[id]?.let { chats + (id to transform(it)) } ?: chats }
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    private fun handleAuth(e: ApiException) {
        if (e.unauthorized) _sessionExpired.tryEmit(Unit)
    }

    private fun launchSafely(silent: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                handleAuth(e)
                if (!silent && !e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                handleAuth(e)
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val HINT_DEBOUNCE_MS = 700L
    }
}
