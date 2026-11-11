package com.zuno.ai.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuno.ai.auth.SessionTokenStore
import com.zuno.ai.data.ApiException
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.Attachment
import com.zuno.ai.data.AttachmentFiles
import com.zuno.ai.data.AttachmentKind
import com.zuno.ai.data.AttachmentStatus
import com.zuno.ai.data.DraftStore
import com.zuno.ai.data.MaterialPart
import com.zuno.ai.data.PreparedFile
import com.zuno.ai.data.SavedDraftAttachment
import com.zuno.ai.data.Conversation
import com.zuno.ai.data.ConversationThread
import com.zuno.ai.data.Message
import com.zuno.ai.data.Profile
import com.zuno.ai.data.RecentSession
import com.zuno.ai.data.Roleplay
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.LessonEvaluation
import com.zuno.ai.data.LessonSessionTicket
import com.zuno.ai.data.NotificationPrefs
import com.zuno.ai.data.Preferences
import com.zuno.ai.data.ReportReason
import com.zuno.ai.data.Schedule
import com.zuno.ai.data.ScheduleDraft
import com.zuno.ai.data.ScheduleTargets
import com.zuno.ai.data.TopicDetail
import com.zuno.ai.data.TopicSummary
import com.zuno.ai.data.ZunoApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    /** Attachments sent with [pendingText], shown from their local previews until the reply lands. */
    val pendingAttachments: List<DraftAttachment> = emptyList(),
    val error: String? = null,
)

sealed interface DraftUploadState {
    data object Preparing : DraftUploadState
    data class Uploading(val progress: Float) : DraftUploadState
    /** Uploaded; the server is reading the file. */
    data object Processing : DraftUploadState
    data object Ready : DraftUploadState
    data class Failed(val reason: String) : DraftUploadState
}

/** A file or link added in the composer (or as topic material), not yet sent. */
data class DraftAttachment(
    val localId: String,
    val source: Uri,
    val name: String,
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val isImage: Boolean = false,
    /** Thumbnail until the message is sent: the local file, or a signed URL for restored drafts. */
    val preview: Uri? = null,
    val state: DraftUploadState = DraftUploadState.Preparing,
    val serverId: String? = null,
    /** Pasted article or video link; [url] is its address. */
    val isLink: Boolean = false,
    val url: String? = null,
    val pageCount: Int? = null,
    val sectionCount: Int? = null,
    /** Already sent in a chat and reused as topic material — never deleted from the server. */
    val borrowed: Boolean = false,
    internal val file: PreparedFile? = null,
)

/** Draft key for the Home composer, which has no conversation yet. */
const val HOME_DRAFT_KEY = "home"

/** Draft key for learning material on the New topic screen. */
const val TOPIC_DRAFT_KEY = "topic-new"

/** "Found in your material" for the New topic screen. [parts] is null until analysed. */
data class MaterialAnalysis(
    val attachmentIds: List<String> = emptyList(),
    val parts: List<MaterialPart>? = null,
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
)

class ZunoViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SessionTokenStore(application)
    private val api = ZunoApi(tokenProvider = tokenStore::read)
    private val draftStore = DraftStore(application)

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentSession>>(emptyList())
    val recent: StateFlow<List<RecentSession>> = _recent.asStateFlow()

    private val _topics = MutableStateFlow(Loadable<List<TopicSummary>>())
    val topics: StateFlow<Loadable<List<TopicSummary>>> = _topics.asStateFlow()

    private val _topicDetails = MutableStateFlow<Map<String, TopicDetail>>(emptyMap())
    val topicDetails: StateFlow<Map<String, TopicDetail>> = _topicDetails.asStateFlow()

    private val _lessonEvaluations = MutableStateFlow<Map<String, LessonEvaluation>>(emptyMap())
    val lessonEvaluations: StateFlow<Map<String, LessonEvaluation>> = _lessonEvaluations.asStateFlow()

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

    /** Composer attachments per conversation id (or [HOME_DRAFT_KEY]). */
    private val _drafts = MutableStateFlow<Map<String, List<DraftAttachment>>>(emptyMap())
    val drafts: StateFlow<Map<String, List<DraftAttachment>>> = _drafts.asStateFlow()
    private val uploadJobs = mutableMapOf<String, Job>()
    private val restoredDrafts = mutableSetOf<String>()

    /** Learning languages from the server; starts with the bundled copy. */
    private val _languages = MutableStateFlow(LearningLanguages.bundled)
    val languages: StateFlow<List<LearningLanguage>> = _languages.asStateFlow()

    private val _notificationPrefs = MutableStateFlow<NotificationPrefs?>(null)
    val notificationPrefs: StateFlow<NotificationPrefs?> = _notificationPrefs.asStateFlow()

    private val _schedules = MutableStateFlow(Loadable<List<Schedule>>())
    val schedules: StateFlow<Loadable<List<Schedule>>> = _schedules.asStateFlow()

    private val _scheduleTargets = MutableStateFlow(Loadable<ScheduleTargets>())
    val scheduleTargets: StateFlow<Loadable<ScheduleTargets>> = _scheduleTargets.asStateFlow()

    private val _materialAnalysis = MutableStateFlow(MaterialAnalysis())
    val materialAnalysis: StateFlow<MaterialAnalysis> = _materialAnalysis.asStateFlow()
    private var analysisJob: Job? = null

    init {
        // Ready attachments are saved with the conversation's draft, so they survive the app being closed.
        viewModelScope.launch {
            var saved = emptyMap<String, List<SavedDraftAttachment>>()
            _drafts.collect { drafts ->
                val next = drafts
                    .filterKeys { it != TOPIC_DRAFT_KEY }
                    .mapValues { (_, list) -> list.mapNotNull { it.toSaved() } }
                (saved.keys - next.keys).forEach { draftStore.saveAttachments(it, emptyList()) }
                next.forEach { (key, list) -> if (saved[key] != list) draftStore.saveAttachments(key, list) }
                saved = next
            }
        }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off user-facing messages (errors, confirmations). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private val _levelAssessed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** A level check finished with this CEFR level. */
    val levelAssessed: SharedFlow<String> = _levelAssessed.asSharedFlow()

    // ── Notifications ───────────────────────────────────────────────────────

    fun loadNotificationPrefs() = launchSafely { _notificationPrefs.value = api.notificationPrefs() }

    /** Optimistic; rolls back on failure. Always reports the device's time zone along with the change. */
    fun updateNotificationPrefs(transform: (NotificationPrefs) -> NotificationPrefs) {
        val previous = _notificationPrefs.value ?: return
        val timezone = java.util.TimeZone.getDefault().id
        val next = transform(previous).copy(timezone = timezone)
        _notificationPrefs.value = next
        val fields = buildMap<String, Any> {
            put("timezone", timezone)
            if (next.dailyReminder != previous.dailyReminder) put("dailyReminder", next.dailyReminder)
            if (next.reminderTime != previous.reminderTime) put("reminderTime", next.reminderTime)
            if (next.scheduledSessions != previous.scheduledSessions) put("scheduledSessions", next.scheduledSessions)
            if (next.progressMilestones != previous.progressMilestones) put("progressMilestones", next.progressMilestones)
            if (next.newContent != previous.newContent) put("newContent", next.newContent)
            if (next.weeklySummaryEmail != previous.weeklySummaryEmail) put("weeklySummaryEmail", next.weeklySummaryEmail)
            if (next.productNewsEmail != previous.productNewsEmail) put("productNewsEmail", next.productNewsEmail)
        }
        viewModelScope.launch {
            try {
                _notificationPrefs.value = api.updateNotificationPrefs(fields)
            } catch (e: ApiException) {
                handleAuth(e)
                _notificationPrefs.value = previous
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    // ── Scheduled sessions ──────────────────────────────────────────────────

    fun loadSchedules() {
        _schedules.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _schedules.value = Loadable(api.schedules())
            } catch (e: ApiException) {
                handleAuth(e)
                _schedules.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun loadScheduleTargets() {
        _scheduleTargets.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _scheduleTargets.value = Loadable(api.scheduleTargets())
            } catch (e: ApiException) {
                handleAuth(e)
                _scheduleTargets.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Creates ([editingId] null) or updates a schedule, then reports the saved row. */
    fun saveSchedule(editingId: String?, draft: ScheduleDraft, onSaved: (Schedule) -> Unit) = runBusy {
        val saved = if (editingId == null) api.createSchedule(draft) else api.updateSchedule(editingId, draft)
        _schedules.update { state ->
            val others = state.items.orEmpty().filterNot { it.id == saved.id }
            state.copy(items = (others + saved).sortedWith(compareBy(nullsLast()) { it.nextRunAtMillis }))
        }
        onSaved(saved)
    }

    fun deleteSchedule(id: String, onDeleted: () -> Unit = {}) {
        val previous = _schedules.value
        _schedules.update { state -> state.copy(items = state.items?.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                api.deleteSchedule(id)
                onDeleted()
            } catch (e: ApiException) {
                handleAuth(e)
                _schedules.value = previous
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    fun skipNextSchedule(id: String, onSkipped: (Schedule) -> Unit) = launchSafely {
        val updated = api.skipNextSchedule(id)
        _schedules.update { state ->
            state.copy(items = state.items?.map { if (it.id == id) updated else it }?.sortedWith(compareBy(nullsLast()) { it.nextRunAtMillis }))
        }
        onSkipped(updated)
    }

    fun snoozeSchedule(id: String, minutes: Int = 10) = launchSafely(silent = true) { api.snoozeSchedule(id, minutes) }

    /** Start now: opens the schedule's lesson or roleplay. */
    fun startSchedule(id: String, onOpened: (Conversation) -> Unit) = runBusy {
        val thread = api.startSchedule(id)
        cacheThread(thread)
        refreshRecent()
        onOpened(thread.conversation)
    }

    // ── Account ─────────────────────────────────────────────────────────────

    /** Change photo: [file] is a compressed JPEG. */
    fun uploadAvatar(file: java.io.File, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        try {
            val url = api.uploadAvatar(file, "image/jpeg")
            _profile.update { it?.copy(imageUrl = url) }
            onDone(true)
        } catch (e: ApiException) {
            handleAuth(e)
            if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            onDone(false)
        } finally {
            file.delete()
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) = runBusy {
        api.deleteAccount()
        onDeleted()
    }

    /** Loads everything the signed-in shell needs (drawer + home). */
    fun refreshAccount() {
        launchSafely { _profile.value = api.profile() }
        refreshRecent()
        loadLanguages()
    }

    fun loadLanguages() = launchSafely(silent = true) {
        val list = api.languages()
        if (list.isNotEmpty()) _languages.value = list
    }

    fun refreshRecent() = launchSafely(silent = true) { _recent.value = api.recentSessions() }

    fun clear() {
        _profile.value = null
        _notificationPrefs.value = null
        _schedules.value = Loadable()
        _scheduleTargets.value = Loadable()
        _recent.value = emptyList()
        _topics.value = Loadable()
        _topicDetails.value = emptyMap()
        _lessonEvaluations.value = emptyMap()
        _roleplays.value = Loadable()
        _chats.value = emptyMap()
        _typingHint.value = null
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        _drafts.value.values.flatten().forEach { it.file?.file?.delete() }
        _drafts.value = emptyMap()
        restoredDrafts.clear()
        draftStore.clear()
        analysisJob?.cancel()
        _materialAnalysis.value = MaterialAnalysis()
    }

    fun updateProfile(
        level: String? = null,
        name: String? = null,
        appLanguage: AppLanguage? = null,
        accent: String? = null,
    ) {
        val fields = buildMap<String, Any> {
            level?.let { put("level", it) }
            name?.let { put("name", it) }
            appLanguage?.let { put("nativeLanguage", it.code) }
            accent?.let { put("accent", it) }
        }
        if (fields.isEmpty()) return
        // Optimistic: the settings screens reflect the change right away.
        val previous = _profile.value
        _profile.update { current ->
            current?.copy(
                level = level ?: current.level,
                name = name ?: current.name,
                appLanguage = appLanguage ?: current.appLanguage,
                accent = accent ?: current.accent,
                levelChecked = current.levelChecked || level != null,
            )
        }
        viewModelScope.launch {
            try {
                _profile.value = api.updateProfile(fields)
                if (appLanguage != null) _typingHint.value = null
            } catch (e: ApiException) {
                handleAuth(e)
                _profile.value = previous
                if (!e.unauthorized && appLanguage == null) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    /** Preferences & learning goals (server-backed, read by the tutor). Optimistic with rollback. */
    fun updatePreferences(transform: (Preferences) -> Preferences) {
        val previous = _profile.value ?: return
        val next = transform(previous.preferences)
        if (next == previous.preferences) return
        _profile.value = previous.copy(preferences = next)
        val fields = buildMap<String, Any> {
            val old = previous.preferences
            if (next.tutorVoice != old.tutorVoice) put("tutorVoice", next.tutorVoice)
            if (next.teachingStyle != old.teachingStyle) put("teachingStyle", next.teachingStyle)
            if (next.strictMode != old.strictMode) put("strictMode", next.strictMode)
            if (next.handsFree != old.handsFree) put("handsFree", next.handsFree)
            if (next.suggestReplies != old.suggestReplies) put("suggestReplies", next.suggestReplies)
            if (next.showTranslations != old.showTranslations) put("showTranslations", next.showTranslations)
            if (next.teachInTarget != old.teachInTarget) put("teachInTarget", next.teachInTarget)
            if (next.dailyGoalMin != old.dailyGoalMin) put("dailyGoalMin", next.dailyGoalMin)
            if (next.focus != old.focus) put("focus", next.focus)
        }
        if (!next.suggestReplies) clearHint()
        viewModelScope.launch {
            try {
                _profile.value = api.updateProfile(fields)
            } catch (e: ApiException) {
                handleAuth(e)
                _profile.update { it?.copy(preferences = previous.preferences) }
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    /**
     * Switches the learning language. The server saves the current language's level and accent and
     * restores (or starts) the new one's; chats, topics and roleplays are per language, so lists reload.
     */
    fun switchLearningLanguage(code: String, onSwitched: (com.zuno.ai.data.Profile) -> Unit) = runBusy {
        val profile = api.updateProfile(mapOf("targetLanguage" to code))
        _profile.value = profile
        _typingHint.value = null
        _topics.value = Loadable()
        _roleplays.value = Loadable()
        _schedules.value = Loadable()
        _scheduleTargets.value = Loadable()
        refreshRecent()
        onSwitched(profile)
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

    /** Creates a topic from the goal plus any learning material on the New topic screen. */
    fun createTopic(prompt: String, onCreated: (TopicDetail) -> Unit) {
        val material = _drafts.value[TOPIC_DRAFT_KEY].orEmpty()
        if (material.any { it.state != DraftUploadState.Ready }) {
            _messages.tryEmit("Wait for your material to finish uploading")
            return
        }
        val parts = _materialAnalysis.value.let { analysis ->
            analysis.parts?.map { it.label }?.filter { it in analysis.selected }.orEmpty()
        }
        runBusy {
            val topic = api.createTopic(prompt, material.mapNotNull { it.serverId }, parts)
            // The material now belongs to the topic on the server; only the local copies go.
            _drafts.update { it - TOPIC_DRAFT_KEY }
            material.forEach { it.file?.file?.delete() }
            analysisJob?.cancel()
            _materialAnalysis.value = MaterialAnalysis()
            cacheTopic(topic)
            _topics.update { state -> state.copy(items = listOf(topic.summary) + state.items.orEmpty()) }
            refreshRecent()
            onCreated(topic)
        }
    }

    /** Asks Zuno which sub-topics the (ready) learning material covers. No-op if already analysed. */
    fun analyzeMaterial(prompt: String) {
        val ids = _drafts.value[TOPIC_DRAFT_KEY].orEmpty()
            .filter { it.state == DraftUploadState.Ready }
            .mapNotNull { it.serverId }
        val current = _materialAnalysis.value
        if (ids.isEmpty()) {
            analysisJob?.cancel()
            _materialAnalysis.value = MaterialAnalysis()
            return
        }
        if (ids == current.attachmentIds) return
        analysisJob?.cancel()
        _materialAnalysis.value = current.copy(attachmentIds = ids, loading = true)
        analysisJob = viewModelScope.launch {
            try {
                val parts = api.analyzeMaterial(prompt, ids)
                // Keep the learner's choices for parts that are still there.
                val kept = current.selected.filter { label -> parts.any { it.label == label } }.toSet()
                _materialAnalysis.value = MaterialAnalysis(
                    attachmentIds = ids,
                    parts = parts,
                    selected = kept.ifEmpty { parts.filter { it.suggested }.map { it.label }.toSet() },
                )
            } catch (e: ApiException) {
                handleAuth(e)
                _materialAnalysis.value = MaterialAnalysis(attachmentIds = ids, parts = current.parts, selected = current.selected)
            }
        }
    }

    fun toggleMaterialPart(label: String) = _materialAnalysis.update { state ->
        state.copy(selected = if (label in state.selected) state.selected - label else state.selected + label)
    }

    /** Leaving New topic without creating it discards the material. */
    fun discardTopicDraft() {
        _drafts.value[TOPIC_DRAFT_KEY].orEmpty().forEach { removeAttachment(TOPIC_DRAFT_KEY, it.localId) }
        analysisJob?.cancel()
        _materialAnalysis.value = MaterialAnalysis()
    }

    /** "Turn into a topic": reuses the files and links shared in a chat as material. Returns a suggested goal. */
    fun prefillTopicFromChat(conversationId: String): String {
        discardTopicDraft()
        val chat = _chats.value[conversationId] ?: return ""
        val shared = chat.messages.flatMap { it.attachments }
            .filter { it.status == AttachmentStatus.Ready }
            .takeLast(MAX_TOPIC_MATERIAL)
        if (shared.isNotEmpty()) _drafts.update { it + (TOPIC_DRAFT_KEY to shared.map { a -> a.toDraft(borrowed = true) }) }
        return chat.conversation.title.takeUnless { it == "Free Talk" }.orEmpty()
    }

    fun loadTopic(id: String) = launchSafely { cacheTopic(api.topic(id)) }

    fun deleteTopic(id: String) = launchSafely {
        api.deleteTopic(id)
        _topics.update { state -> state.copy(items = state.items?.filterNot { it.id == id }) }
        _topicDetails.update { it - id }
        refreshRecent()
    }

    fun resetTopic(id: String) = launchSafely {
        val resetDetail = api.resetTopic(id)
        val lessonIds = resetDetail.lessons.map { it.id }.toSet()
        cacheTopic(resetDetail)
        _lessonEvaluations.update { evals -> evals.filterKeys { it !in lessonIds } }
        _chats.update { chats -> chats.filterValues { it.conversation.topicId != id } }
        refreshRecent()
    }

    fun startLessonVoiceSession(lessonId: String, onReady: (LessonSessionTicket) -> Unit) = runBusy {
        val ticket = api.startLessonSession(lessonId)
        onReady(ticket)
    }

    fun saveLessonEvaluation(
        lessonId: String,
        conversationId: String?,
        evaluation: LessonEvaluation,
        onSaved: (LessonEvaluation) -> Unit = {},
    ) {
        // Optimistically cache immediately so 04 · Evaluation renders with zero loading flicker
        _lessonEvaluations.update { it + (lessonId to evaluation) }
        viewModelScope.launch {
            try {
                val res = api.completeLessonEvaluation(lessonId, conversationId, evaluation)
                _lessonEvaluations.update { it + (lessonId to res.evaluation) }
                res.topic?.let(::cacheTopic)
                refreshRecent()
                onSaved(res.evaluation)
            } catch (e: ApiException) {
                handleAuth(e)
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    fun loadLessonEvaluation(lessonId: String) = launchSafely {
        val eval = api.lessonEvaluation(lessonId)
        _lessonEvaluations.update { it + (lessonId to eval) }
    }

    suspend fun fetchTtsPreview(text: String, language: String? = null, slow: Boolean = false): ByteArray =
        api.ttsPreview(text = text, language = language, slow = slow)

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
        val id = thread.conversation.id
        cacheThread(thread)
        // Attachments added on Home travel with the first message.
        val homeDrafts = if (viaVoice) emptyList() else _drafts.value[HOME_DRAFT_KEY].orEmpty()
        if (homeDrafts.isNotEmpty()) _drafts.update { it - HOME_DRAFT_KEY + (id to homeDrafts) }
        onOpened(thread.conversation)
        if (!firstMessage.isNullOrBlank() || homeDrafts.isNotEmpty()) send(id, firstMessage.orEmpty(), viaVoice, onReplied)
        refreshRecent()
    }

    fun loadConversation(id: String) = launchSafely { cacheThread(api.conversation(id)) }

    fun deleteConversation(id: String, onDeleted: () -> Unit) = launchSafely {
        api.deleteConversation(id)
        _drafts.value[id].orEmpty().forEach { removeAttachment(id, it.localId) }
        draftStore.remove(id)
        restoredDrafts.remove(id)
        _chats.update { it - id }
        _recent.update { list -> list.filterNot { it.id == id || it.conversationId == id } }
        refreshRecent()
        onDeleted()
    }

    /** Optimistic rename of the chat title and its drawer row; rolls back on failure. */
    fun renameConversation(id: String, title: String, onRenamed: () -> Unit) {
        val previous = _chats.value[id]?.conversation ?: return
        val recentBefore = _recent.value
        updateChat(id) { it.copy(conversation = it.conversation.copy(title = title)) }
        _recent.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
        viewModelScope.launch {
            try {
                val updated = api.renameConversation(id, title)
                updateChat(id) { it.copy(conversation = updated) }
                onRenamed()
            } catch (e: ApiException) {
                handleAuth(e)
                updateChat(id) { it.copy(conversation = previous) }
                _recent.value = recentBefore
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    /** Pins (or unpins) the chat to the top of the drawer. */
    fun setPinned(id: String, pinned: Boolean, onDone: (Boolean) -> Unit) {
        val previous = _chats.value[id]?.conversation ?: return
        updateChat(id) { it.copy(conversation = it.conversation.copy(pinned = pinned)) }
        viewModelScope.launch {
            try {
                val updated = api.setPinned(id, pinned)
                updateChat(id) { it.copy(conversation = updated) }
                refreshRecent()
                onDone(pinned)
            } catch (e: ApiException) {
                handleAuth(e)
                updateChat(id) { it.copy(conversation = previous) }
                if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
            }
        }
    }

    /** Only me / Public access. Returns the public link (or null) once the server confirms. */
    suspend fun setShare(id: String, public: Boolean): String? = try {
        val result = api.setShare(id, public)
        updateChat(id) { it.copy(conversation = it.conversation.copy(public = result.public, shareUrl = result.shareUrl)) }
        result.shareUrl
    } catch (e: ApiException) {
        handleAuth(e)
        if (!e.unauthorized) e.message?.let { _messages.tryEmit(it) }
        throw e
    }

    fun reportConversation(id: String, reason: ReportReason, details: String, onSent: () -> Unit) = launchSafely {
        api.reportConversation(id, reason, details)
        onSent()
    }

    /** "Remind me to continue" on the server; returns the reminder id, or null when it couldn't be saved. */
    suspend fun createReminder(conversationId: String, atMillis: Long): String? = try {
        api.createReminder(conversationId, atMillis)
    } catch (e: ApiException) {
        handleAuth(e)
        null
    }

    fun cancelReminder(id: String) = launchSafely(silent = true) { api.cancelReminder(id) }

    /** "Find my level": a Free Talk where the tutor assesses the learner and sets their level. */
    fun startLevelCheck(firstMessage: String, onOpened: (Conversation) -> Unit) = runBusy {
        val thread = api.createFreeTalk(levelCheck = true)
        cacheThread(thread)
        onOpened(thread.conversation)
        send(thread.conversation.id, firstMessage)
    }

    fun send(
        conversationId: String,
        text: String,
        viaVoice: Boolean = false,
        onReplied: ((Boolean) -> Unit)? = null,
    ) {
        val current = _chats.value[conversationId]
        val attachments = _drafts.value[conversationId].orEmpty()
        if (current == null || current.pendingText != null || (text.isBlank() && attachments.isEmpty())) {
            onReplied?.invoke(false)
            return
        }
        if (attachments.any { it.state != DraftUploadState.Ready }) {
            _messages.tryEmit("Wait for your files to finish uploading")
            onReplied?.invoke(false)
            return
        }
        _drafts.update { it - conversationId }
        updateChat(conversationId) { it.copy(pendingText = text, pendingAttachments = attachments, error = null) }
        _typingHint.value = null
        viewModelScope.launch {
            try {
                val result = api.sendMessage(conversationId, text, viaVoice, attachments.mapNotNull { it.serverId })
                updateChat(conversationId) {
                    it.copy(
                        conversation = result.conversation,
                        messages = it.messages + result.userMessage + result.reply,
                        pendingText = null,
                        pendingAttachments = emptyList(),
                    )
                }
                attachments.forEach { it.file?.file?.delete() }
                onReplied?.invoke(true)
                if (result.lessonCompleted) {
                    result.conversation.topicId?.let { loadTopic(it) }
                    _messages.tryEmit("Lesson complete — nice work!")
                }
                result.levelAssessed?.let { level ->
                    _profile.update { it?.copy(level = level, levelChecked = true) }
                    _levelAssessed.tryEmit(level)
                    launchSafely(silent = true) { _profile.value = api.profile() }
                }
                refreshRecent()
            } catch (e: ApiException) {
                handleAuth(e)
                updateChat(conversationId) { it.copy(pendingText = null, pendingAttachments = emptyList(), error = e.message) }
                // Unsent files stay valid on the server, so put them back in the composer.
                if (attachments.isNotEmpty()) {
                    _drafts.update { it + (conversationId to attachments + it[conversationId].orEmpty()) }
                }
                onReplied?.invoke(false)
            }
        }
    }

    // ── Attachments ─────────────────────────────────────────────────────────

    /** How many attachments the composer (or topic material) for [key] can hold. */
    fun maxAttachments(key: String) = if (key == TOPIC_DRAFT_KEY) MAX_TOPIC_MATERIAL else AttachmentFiles.MAX_PER_MESSAGE

    private fun limitMessage(key: String) =
        if (key == TOPIC_DRAFT_KEY) "You can add up to $MAX_TOPIC_MATERIAL files"
        else "You can attach up to ${AttachmentFiles.MAX_PER_MESSAGE} files per message"

    /** Adds picked files to the composer for [key] and uploads them right away. */
    fun addAttachments(key: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val room = maxAttachments(key) - _drafts.value[key].orEmpty().size
        if (room <= 0 || uris.size > room) _messages.tryEmit(limitMessage(key))
        uris.take(room.coerceAtLeast(0)).forEach { uri ->
            val draft = DraftAttachment(localId = java.util.UUID.randomUUID().toString(), source = uri, name = "")
            _drafts.update { it + (key to it[key].orEmpty() + draft) }
            startUpload(key, draft.localId)
        }
    }

    /** Adds a pasted article or video link; the server reads it before it can be sent. */
    fun addLink(key: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        if (_drafts.value[key].orEmpty().size >= maxAttachments(key)) {
            _messages.tryEmit(limitMessage(key))
            return
        }
        val host = runCatching { Uri.parse(if ("://" in trimmed) trimmed else "https://$trimmed").host }.getOrNull()
        val draft = DraftAttachment(
            localId = java.util.UUID.randomUUID().toString(),
            source = Uri.EMPTY,
            name = host?.removePrefix("www.") ?: trimmed,
            isLink = true,
            url = trimmed,
            state = DraftUploadState.Processing,
        )
        _drafts.update { it + (key to it[key].orEmpty() + draft) }
        startUpload(key, draft.localId)
    }

    /** Unsent composer text for [key], saved across app restarts. */
    fun draftText(key: String): String = draftStore.text(key)

    fun saveDraftText(key: String, text: String) = draftStore.saveText(key, text)

    /** Puts back the attachments saved with a conversation's draft (once per app session). */
    fun restoreDraft(key: String) {
        if (!restoredDrafts.add(key)) return
        val saved = draftStore.read(key)?.attachments.orEmpty()
        if (saved.isEmpty()) return
        viewModelScope.launch {
            val restored = saved.mapNotNull { item ->
                try {
                    // Re-fetch for a fresh signed URL and to drop files the server no longer has.
                    api.attachment(item.serverId).takeIf { it.status == AttachmentStatus.Ready }?.toDraft()
                } catch (e: ApiException) {
                    handleAuth(e)
                    null
                }
            }
            _drafts.update { drafts ->
                val current = drafts[key].orEmpty()
                val merged = (restored.filter { r -> current.none { it.serverId == r.serverId } } + current)
                    .take(maxAttachments(key))
                if (merged.isEmpty()) drafts - key else drafts + (key to merged)
            }
        }
    }

    fun retryAttachment(key: String, localId: String) {
        val draft = findDraft(key, localId) ?: return
        if (draft.state !is DraftUploadState.Failed) return
        draft.serverId?.let { id -> launchSafely(silent = true) { api.deleteAttachment(id) } }
        updateDraft(key, localId) {
            it.copy(serverId = null, state = if (it.isLink) DraftUploadState.Processing else DraftUploadState.Preparing)
        }
        startUpload(key, localId)
    }

    fun removeAttachment(key: String, localId: String) {
        val draft = findDraft(key, localId) ?: return
        uploadJobs.remove(localId)?.cancel()
        _drafts.update { drafts ->
            val remaining = drafts[key].orEmpty().filterNot { it.localId == localId }
            if (remaining.isEmpty()) drafts - key else drafts + (key to remaining)
        }
        if (!draft.borrowed) draft.serverId?.let { id -> launchSafely(silent = true) { api.deleteAttachment(id) } }
        draft.file?.file?.delete()
    }

    /** Signed links expire, so documents are opened with a freshly signed URL. */
    suspend fun attachmentUrl(id: String): String? = try {
        api.attachment(id).url
    } catch (e: ApiException) {
        handleAuth(e)
        _messages.tryEmit(e.message ?: "Couldn't open this file")
        null
    }

    private fun startUpload(key: String, localId: String) {
        uploadJobs.remove(localId)?.cancel()
        if (findDraft(key, localId)?.isLink == true) {
            uploadJobs[localId] = viewModelScope.launch { readLink(key, localId) }
            return
        }
        uploadJobs[localId] = viewModelScope.launch {
            val prepared = findDraft(key, localId)?.file ?: try {
                AttachmentFiles.prepare(getApplication(), findDraft(key, localId)?.source ?: return@launch)
            } catch (e: ApiException) {
                // Unsupported or unreadable files never make it into the composer.
                removeAttachment(key, localId)
                _messages.tryEmit(e.message ?: "Couldn't add this file")
                return@launch
            }
            updateDraft(key, localId) {
                it.copy(
                    file = prepared,
                    name = prepared.name,
                    mimeType = prepared.mimeType,
                    sizeBytes = prepared.file.length(),
                    isImage = prepared.isImage,
                    preview = if (prepared.isImage) Uri.fromFile(prepared.file) else null,
                    state = DraftUploadState.Uploading(0f),
                )
            }
            try {
                val ticket = api.createUpload(prepared)
                val id = ticket.attachment.id
                updateDraft(key, localId) { it.copy(serverId = id) }
                var lastShown = 0f
                api.uploadToStorage(ticket, prepared) { progress ->
                    if (progress - lastShown >= 0.02f || progress >= 1f) {
                        lastShown = progress
                        updateDraft(key, localId) { it.copy(state = DraftUploadState.Uploading(progress)) }
                    }
                }
                val attachment = api.completeUpload(id)
                updateDraft(key, localId) { it.copy(state = DraftUploadState.Processing) }
                finishProcessing(key, localId, attachment)
            } catch (e: ApiException) {
                handleAuth(e)
                updateDraft(key, localId) { it.copy(state = DraftUploadState.Failed(e.message ?: "Upload failed")) }
            } finally {
                if (uploadJobs[localId] === coroutineContext[Job]) uploadJobs.remove(localId)
            }
        }
    }

    private suspend fun readLink(key: String, localId: String) {
        val url = findDraft(key, localId)?.url ?: return
        try {
            val attachment = api.createLink(url)
            updateDraft(key, localId) { it.copy(serverId = attachment.id, url = attachment.url ?: url) }
            finishProcessing(key, localId, attachment)
        } catch (e: ApiException) {
            handleAuth(e)
            updateDraft(key, localId) { it.copy(state = DraftUploadState.Failed(e.message ?: "Couldn't add this link")) }
        } finally {
            if (uploadJobs[localId] === currentCoroutineContext()[Job]) uploadJobs.remove(localId)
        }
    }

    /** Polls while the server reads the file or link, then marks the draft ready (with its title) or failed. */
    private suspend fun finishProcessing(key: String, localId: String, initial: Attachment) {
        var attachment = initial
        val deadline = System.currentTimeMillis() + PROCESSING_TIMEOUT_MS
        while (attachment.status == AttachmentStatus.Processing && System.currentTimeMillis() < deadline) {
            delay(PROCESSING_POLL_MS)
            attachment = api.attachment(attachment.id)
        }
        val state = when (attachment.status) {
            AttachmentStatus.Ready -> DraftUploadState.Ready
            AttachmentStatus.Failed -> DraftUploadState.Failed(attachment.error ?: "Couldn't read this file")
            else -> DraftUploadState.Failed("Taking too long")
        }
        updateDraft(key, localId) { draft ->
            draft.copy(
                state = state,
                name = if (draft.isLink && attachment.status == AttachmentStatus.Ready) attachment.fileName else draft.name,
                pageCount = attachment.pageCount,
                sectionCount = attachment.sectionCount,
            )
        }
    }

    private fun findDraft(key: String, localId: String) = _drafts.value[key]?.firstOrNull { it.localId == localId }

    private fun updateDraft(key: String, localId: String, transform: (DraftAttachment) -> DraftAttachment) {
        _drafts.update { drafts ->
            val list = drafts[key] ?: return@update drafts
            drafts + (key to list.map { if (it.localId == localId) transform(it) else it })
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
        const val PROCESSING_POLL_MS = 1_000L
        const val PROCESSING_TIMEOUT_MS = 120_000L
    }
}

/** Learning material for a new topic can hold more files than a single message (matches the backend). */
const val MAX_TOPIC_MATERIAL = 8

/** A server attachment shown as a ready draft (restored drafts, or chat files reused as topic material). */
private fun Attachment.toDraft(borrowed: Boolean = false) = DraftAttachment(
    localId = java.util.UUID.randomUUID().toString(),
    source = Uri.EMPTY,
    name = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    isImage = kind == AttachmentKind.Image,
    preview = if (kind == AttachmentKind.Image) url?.let(Uri::parse) else null,
    state = DraftUploadState.Ready,
    serverId = id,
    isLink = kind == AttachmentKind.Link,
    url = if (kind == AttachmentKind.Link) url else null,
    pageCount = pageCount,
    sectionCount = sectionCount,
    borrowed = borrowed,
)

private fun DraftAttachment.toSaved(): SavedDraftAttachment? {
    val id = serverId ?: return null
    if (state != DraftUploadState.Ready || borrowed) return null
    return SavedDraftAttachment(
        serverId = id,
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        kind = when {
            isLink -> AttachmentKind.Link
            isImage -> AttachmentKind.Image
            else -> AttachmentKind.Document
        },
        url = url,
    )
}
