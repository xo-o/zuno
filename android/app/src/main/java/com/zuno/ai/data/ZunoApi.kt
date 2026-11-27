package com.zuno.ai.data

import com.zuno.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** User-facing API failure. [unauthorized] means the session is gone and the user must sign in again. */
class ApiException(message: String, val unauthorized: Boolean = false) : Exception(message)

/** Client for the Zuno app API (`/api/...`), authenticated with the Better Auth bearer token. */
class ZunoApi(
    private val tokenProvider: () -> String?,
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
) {
    private val apiUrl = "${baseUrl.trimEnd('/')}/api"

    suspend fun profile(): Profile = get("/me").toProfile()

    suspend fun progress(): ProgressOverview = get("/me/progress").toProgressOverview()

    suspend fun heartbeatPresence(status: String = "online") {
        send("POST", "/me/presence", JSONObject().put("status", status))
    }

    suspend fun communityLeague(): LeagueOverview = get("/community/league").toLeagueOverview()

    /** PATCH /me with any of: name, targetLanguage, nativeLanguage, level, accent and preference fields. */
    suspend fun updateProfile(fields: Map<String, Any>): Profile {
        val body = JSONObject()
        fields.forEach { (key, value) -> body.put(key, value) }
        return send("PATCH", "/me", body).toProfile()
    }

    suspend fun parseOnboardingTurn(
        step: String,
        answer: String,
        chips: List<String> = emptyList(),
        targetLanguage: String? = null,
        nativeLanguage: String? = null,
    ): OnboardingTurnResult {
        val body = JSONObject()
            .put("step", step)
            .put("answer", answer)
            .put("chips", JSONArray(chips))
        targetLanguage?.takeIf { it.isNotBlank() }?.let { body.put("targetLanguage", it) }
        nativeLanguage?.takeIf { it.isNotBlank() }?.let { body.put("nativeLanguage", it) }
        return send("POST", "/me/onboarding/turn", body).toOnboardingTurnResult()
    }

    suspend fun completeOnboarding(
        name: String?,
        targetLanguage: String,
        nativeLanguage: String,
        accent: String?,
        level: String,
        focus: String,
        interests: List<String>,
        goalNote: String?,
    ): OnboardingCompleteResult {
        val body = JSONObject()
            .put("targetLanguage", targetLanguage)
            .put("nativeLanguage", nativeLanguage)
            .put("level", level)
            .put("focus", focus)
            .put("interests", JSONArray(interests))
        name?.trim()?.takeIf { it.isNotEmpty() }?.let { body.put("name", it) }
        accent?.takeIf { it.isNotBlank() }?.let { body.put("accent", it) }
        goalNote?.trim()?.takeIf { it.isNotEmpty() }?.let { body.put("goalNote", it) }
        return send("POST", "/me/onboarding", body).toOnboardingCompleteResult()
    }

    suspend fun languages(): List<LearningLanguage> =
        execute(Request.Builder().url("$apiUrl/languages").get().build()).listOf("languages").map { it.toLearningLanguage() }

    suspend fun notificationPrefs(): NotificationPrefs = get("/me/notifications").getJSONObject("notifications").toNotificationPrefs()

    suspend fun updateNotificationPrefs(fields: Map<String, Any>): NotificationPrefs {
        val body = JSONObject()
        fields.forEach { (key, value) -> body.put(key, value) }
        return send("PATCH", "/me/notifications", body).getJSONObject("notifications").toNotificationPrefs()
    }

    /** Change photo: presigned upload, PUT the bytes, then confirm. Returns the new photo URL. */
    suspend fun uploadAvatar(file: java.io.File, mimeType: String): String? {
        val ticket = send("POST", "/me/avatar", JSONObject().put("mimeType", mimeType).put("sizeBytes", file.length()))
        val key = ticket.getString("key")
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(ticket.getString("uploadUrl")).put(file.asRequestBody(mimeType.toMediaType())).build()
            val response = try {
                uploadClient.newCall(request).execute()
            } catch (_: IOException) {
                throw ApiException("Upload failed. Check your connection.")
            }
            response.use { if (!it.isSuccessful) throw ApiException("Upload failed (${it.code}).") }
        }
        val user = send("POST", "/me/avatar/complete", JSONObject().put("key", key)).getJSONObject("user")
        return if (user.isNull("image")) null else user.optString("image")
    }

    suspend fun deleteAccount() {
        send("DELETE", "/me")
    }

    // Push devices

    suspend fun registerDevice(pushToken: String, timezone: String, appLanguage: String) {
        send(
            "POST",
            "/devices",
            JSONObject().put("pushToken", pushToken).put("platform", "android").put("timezone", timezone).put("appLanguage", appLanguage),
        )
    }

    suspend fun unregisterDevice(pushToken: String, token: String?) {
        val request = Request.Builder()
            .url("$apiUrl/devices/${java.net.URLEncoder.encode(pushToken, "UTF-8")}")
            .header("Authorization", "Bearer ${token ?: throw ApiException("Not signed in", unauthorized = true)}")
            .delete()
            .build()
        execute(request)
    }

    suspend fun recentSessions(): List<RecentSession> =
        get("/conversations/recent").listOf("sessions").map { it.toRecentSession() }

    // Topics

    suspend fun topics(): List<TopicSummary> = get("/topics").listOf("topics").map { it.toTopicSummary() }

    suspend fun createTopic(
        prompt: String,
        attachmentIds: List<String> = emptyList(),
        parts: List<String> = emptyList(),
        recommendationId: String? = null,
        draft: TopicDraft? = null,
    ): TopicDetail {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("attachmentIds", JSONArray(attachmentIds))
            .put("parts", JSONArray(parts))
        recommendationId?.takeIf { it.isNotBlank() }?.let { body.put("recommendationId", it) }
        draft?.let { body.put("draft", it.toJson()) }
        return send("POST", "/topics", body).getJSONObject("topic").toTopicDetail()
    }

    suspend fun discoverTopics(category: String = "all", query: String = "", level: String = "all"): DiscoverTopicsResult {
        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
        val encodedCat = java.net.URLEncoder.encode(category, "UTF-8")
        val encodedLevel = java.net.URLEncoder.encode(level, "UTF-8")
        val json = get("/topics/discover?category=$encodedCat&q=$encodedQ&level=$encodedLevel")
        return DiscoverTopicsResult(
            official = json.listOf("official").map { it.toDiscoverTopic() },
            community = json.listOf("community").map { it.toDiscoverTopic() },
        )
    }

    suspend fun discoverTopic(idOrToken: String): DiscoverTopic {
        val encoded = java.net.URLEncoder.encode(idOrToken, "UTF-8")
        return get("/topics/discover/$encoded").getJSONObject("topic").toDiscoverTopic()
    }

    suspend fun topicBuilderTurn(
        turns: List<Pair<String, String>>,
        attachmentIds: List<String> = emptyList(),
    ): TopicBuilderTurnResult {
        val turnsArray = JSONArray().apply {
            turns.forEach { (role, text) ->
                put(JSONObject().put("role", role).put("text", text))
            }
        }
        val body = JSONObject()
            .put("turns", turnsArray)
            .put("attachmentIds", JSONArray(attachmentIds))
        return send("POST", "/topics/builder/turn", body).toTopicBuilderTurnResult()
    }

    suspend fun saveTopic(id: String): TopicDetail =
        send("POST", "/topics/$id/save").getJSONObject("topic").toTopicDetail()

    suspend fun setTopicPinned(id: String, pinned: Boolean): TopicDetail =
        send("PATCH", "/topics/$id", JSONObject().put("pinned", pinned)).getJSONObject("topic").toTopicDetail()

    suspend fun updateTopic(id: String, title: String? = null, category: String? = null): TopicDetail {
        val body = JSONObject()
        title?.takeIf { it.isNotBlank() }?.let { body.put("title", it.trim()) }
        category?.takeIf { it.isNotBlank() }?.let { body.put("category", it) }
        return send("PATCH", "/topics/$id", body).getJSONObject("topic").toTopicDetail()
    }

    suspend fun setTopicShare(id: String, public: Boolean): TopicShareResult =
        send("PUT", "/topics/$id/share", JSONObject().put("visibility", if (public) "public" else "private")).let {
            TopicShareResult(
                public = it.optString("visibility") == "public",
                shareUrl = if (it.isNull("shareUrl")) null else it.optString("shareUrl"),
                learnersCount = it.optInt("learnersCount", 0),
                topic = it.getJSONObject("topic").toTopicDetail(),
            )
        }

    suspend fun reportTopic(id: String, reason: ReportReason, details: String) {
        val body = JSONObject().put("reason", reason.code)
        if (details.isNotBlank()) body.put("details", details.trim())
        send("POST", "/topics/$id/report", body)
    }

    /** Sub-topics found in learning material, for the learner to include or skip. */
    suspend fun analyzeMaterial(prompt: String, attachmentIds: List<String>): List<MaterialPart> =
        send(
            "POST",
            "/topics/analyze",
            JSONObject().put("prompt", prompt).put("attachmentIds", JSONArray(attachmentIds)),
        ).listOf("parts").map { MaterialPart(it.optString("label"), it.optBoolean("suggested")) }

    suspend fun topic(id: String): TopicDetail = get("/topics/$id").getJSONObject("topic").toTopicDetail()

    suspend fun deleteTopic(id: String) {
        send("DELETE", "/topics/$id")
    }

    suspend fun resetTopic(id: String): TopicDetail =
        send("POST", "/topics/$id/reset").getJSONObject("topic").toTopicDetail()

    suspend fun startLessonSession(lessonId: String): LessonSessionTicket =
        send("POST", "/lessons/$lessonId/session").toLessonSessionTicket()

    suspend fun completeLessonEvaluation(
        lessonId: String,
        conversationId: String?,
        evaluation: LessonEvaluation,
    ): CompleteLessonResult {
        val json = send("POST", "/lessons/$lessonId/complete", evaluation.toRequestBody(conversationId))
        val nextId = if (json.isNull("nextLessonId")) null else json.optString("nextLessonId").takeIf { it.isNotBlank() }
        val savedEval = json.getJSONObject("evaluation").toLessonEvaluation(lessonId)
        val topicDetail = json.optJSONObject("topic")?.toTopicDetail()
        return CompleteLessonResult(savedEval, topicDetail, nextId)
    }

    suspend fun lessonEvaluation(lessonId: String): LessonEvaluation {
        val json = get("/lessons/$lessonId/evaluation")
        return json.getJSONObject("evaluation").toLessonEvaluation(lessonId)
    }

    suspend fun ttsPreview(
        text: String,
        language: String? = null,
        nativeLanguage: String? = null,
        defaultLanguage: String? = null,
        voiceKey: String? = null,
        slow: Boolean = false,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("text", text).put("slow", slow)
            language?.takeIf { it.isNotBlank() }?.let { body.put("language", it) }
            nativeLanguage?.takeIf { it.isNotBlank() }?.let { body.put("nativeLanguage", it) }
            defaultLanguage?.takeIf { it.isNotBlank() }?.let { body.put("defaultLanguage", it) }
            voiceKey?.takeIf { it.isNotBlank() }?.let { vk ->
                if (vk in TutorIds) body.put("tutorId", vk) else body.put("tutorVoice", vk)
            }
            val req = request("/tts/preview").post(body.toString().toRequestBody(JSON)).build()
            val response = try {
                client.newCall(req).execute()
            } catch (_: IOException) {
                throw ApiException("Can't reach Zuno. Check your connection and try again.")
            }
            response.use {
                when {
                    it.code == 401 -> throw ApiException("Your session has expired. Please sign in again.", unauthorized = true)
                    !it.isSuccessful -> throw ApiException("Voice preview unavailable (${it.code}).")
                    else -> it.body?.bytes() ?: ByteArray(0)
                }
            }
        }

    // Roleplays

    suspend fun roleplays(): List<Roleplay> = get("/roleplays").listOf("roleplays").map { it.toRoleplay() }

    suspend fun roleplay(id: String): Roleplay = get("/roleplays/$id").getJSONObject("roleplay").toRoleplay()

    suspend fun discoverRoleplays(category: String = "all", query: String = "", level: String = "all"): DiscoverRoleplaysResult {
        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
        val encodedCat = java.net.URLEncoder.encode(category, "UTF-8")
        val encodedLevel = java.net.URLEncoder.encode(level, "UTF-8")
        val json = get("/roleplays/discover?category=$encodedCat&q=$encodedQ&level=$encodedLevel")
        return DiscoverRoleplaysResult(
            official = json.listOf("official").map { it.toDiscoverRoleplay() },
            community = json.listOf("community").map { it.toDiscoverRoleplay() },
        )
    }

    suspend fun discoverRoleplay(idOrToken: String): DiscoverRoleplay {
        val encoded = java.net.URLEncoder.encode(idOrToken, "UTF-8")
        return get("/roleplays/discover/$encoded").getJSONObject("roleplay").toDiscoverRoleplay()
    }

    suspend fun roleplayBuilderTurn(
        turns: List<Pair<String, String>>,
        attachmentIds: List<String> = emptyList(),
    ): RoleplayBuilderTurnResult {
        val turnsArray = JSONArray().apply {
            turns.forEach { (role, text) ->
                put(JSONObject().put("role", role).put("text", text))
            }
        }
        val body = JSONObject()
            .put("turns", turnsArray)
            .put("attachmentIds", JSONArray(attachmentIds))
        return send("POST", "/roleplays/builder/turn", body).toRoleplayBuilderTurnResult()
    }

    suspend fun createRoleplay(scenario: String, userRole: String, aiRole: String): ConversationThread {
        val body = JSONObject().put("scenario", scenario)
        if (userRole.isNotBlank()) body.put("userRole", userRole)
        if (aiRole.isNotBlank()) body.put("aiRole", aiRole)
        return send("POST", "/roleplays", body).toThread()
    }

    suspend fun createRoleplayDraft(
        scenario: String,
        draft: RoleplayDraft? = null,
        attachmentIds: List<String> = emptyList(),
    ): Roleplay {
        val body = JSONObject()
            .put("scenario", scenario)
            .put("attachmentIds", JSONArray(attachmentIds))
            .put("skipInitialRun", true)
        draft?.let { body.put("draft", it.toJson()) }
        return send("POST", "/roleplays", body).getJSONObject("roleplay").toRoleplay()
    }

    suspend fun saveRoleplay(id: String): Roleplay =
        send("POST", "/roleplays/$id/save").getJSONObject("roleplay").toRoleplay()

    suspend fun setRoleplayPinned(id: String, pinned: Boolean): Roleplay =
        send("PATCH", "/roleplays/$id", JSONObject().put("pinned", pinned)).getJSONObject("roleplay").toRoleplay()

    suspend fun updateRoleplay(
        id: String,
        title: String? = null,
        category: String? = null,
        userRole: String? = null,
        aiRole: String? = null,
    ): Roleplay {
        val body = JSONObject()
        title?.takeIf { it.isNotBlank() }?.let { body.put("title", it.trim()) }
        category?.takeIf { it.isNotBlank() }?.let { body.put("category", it) }
        userRole?.takeIf { it.isNotBlank() }?.let { body.put("userRole", it.trim()) }
        aiRole?.takeIf { it.isNotBlank() }?.let { body.put("aiRole", it.trim()) }
        return send("PATCH", "/roleplays/$id", body).getJSONObject("roleplay").toRoleplay()
    }

    suspend fun setRoleplayShare(id: String, public: Boolean): RoleplayShareResult =
        send("PUT", "/roleplays/$id/share", JSONObject().put("visibility", if (public) "public" else "private")).let {
            RoleplayShareResult(
                public = it.optString("visibility") == "public",
                shareUrl = if (it.isNull("shareUrl")) null else it.optString("shareUrl"),
                practicedCount = it.optInt("practicedCount", 0),
                roleplay = it.getJSONObject("roleplay").toRoleplay(),
            )
        }

    suspend fun reportRoleplay(id: String, reason: ReportReason, details: String) {
        val body = JSONObject().put("reason", reason.code)
        if (details.isNotBlank()) body.put("details", details.trim())
        send("POST", "/roleplays/$id/report", body)
    }

    suspend fun openRoleplay(id: String): ConversationThread = send("POST", "/roleplays/$id/open").toThread()

    suspend fun restartRoleplay(id: String): ConversationThread = send("POST", "/roleplays/$id/restart").toThread()

    suspend fun startRoleplaySession(id: String, restart: Boolean = false): RoleplaySessionTicket =
        send("POST", "/roleplays/$id/session", JSONObject().put("restart", restart)).toRoleplaySessionTicket()

    suspend fun deleteRoleplay(id: String) {
        send("DELETE", "/roleplays/$id")
    }

    // Conversations

    suspend fun createFreeTalk(levelCheck: Boolean = false): ConversationThread =
        send("POST", "/conversations", JSONObject().put("levelCheck", levelCheck)).toThread()

    suspend fun renameConversation(id: String, title: String): Conversation =
        send("PATCH", "/conversations/$id", JSONObject().put("title", title)).getJSONObject("conversation").toConversation()

    suspend fun setPinned(id: String, pinned: Boolean): Conversation =
        send("PATCH", "/conversations/$id", JSONObject().put("pinned", pinned)).getJSONObject("conversation").toConversation()

    suspend fun setShare(id: String, public: Boolean): ShareResult =
        send("PUT", "/conversations/$id/share", JSONObject().put("visibility", if (public) "public" else "private")).let {
            ShareResult(it.optString("visibility") == "public", if (it.isNull("shareUrl")) null else it.optString("shareUrl"))
        }

    suspend fun reportConversation(id: String, reason: ReportReason, details: String) {
        val body = JSONObject().put("reason", reason.code)
        if (details.isNotBlank()) body.put("details", details.trim())
        send("POST", "/conversations/$id/report", body)
    }

    /** "Remind me to continue" on the server (push). Returns the reminder id. */
    suspend fun createReminder(conversationId: String, atMillis: Long): String =
        send("POST", "/conversations/$conversationId/reminders", JSONObject().put("remindAt", java.time.Instant.ofEpochMilli(atMillis).toString()))
            .getString("id")

    suspend fun cancelReminder(id: String) {
        send("DELETE", "/reminders/$id")
    }

    // Schedules

    suspend fun schedules(): List<Schedule> = get("/schedules").listOf("schedules").map { it.toSchedule() }

    suspend fun scheduleTargets(): ScheduleTargets = get("/schedules/targets").toScheduleTargets()

    suspend fun createSchedule(draft: ScheduleDraft): Schedule =
        send("POST", "/schedules", draft.toJson(includeTarget = true)).getJSONObject("schedule").toSchedule()

    suspend fun updateSchedule(id: String, draft: ScheduleDraft): Schedule =
        send("PATCH", "/schedules/$id", draft.toJson(includeTarget = false)).getJSONObject("schedule").toSchedule()

    suspend fun deleteSchedule(id: String) {
        send("DELETE", "/schedules/$id")
    }

    suspend fun skipNextSchedule(id: String): Schedule =
        send("POST", "/schedules/$id/skip-next").getJSONObject("schedule").toSchedule()

    suspend fun snoozeSchedule(id: String, minutes: Int = 10) {
        send("POST", "/schedules/$id/snooze", JSONObject().put("minutes", minutes))
    }

    suspend fun startSchedule(id: String): ConversationThread = send("POST", "/schedules/$id/start").toThread()

    private fun ScheduleDraft.toJson(includeTarget: Boolean): JSONObject {
        val body = JSONObject()
        if (includeTarget) {
            body.put("targetKind", if (isRoleplay) "roleplay" else "topic")
            body.put(if (isRoleplay) "roleplayId" else "topicId", targetId)
        }
        body.put("repeat", repeat.code)
            .put("timeOfDay", timeOfDay)
            .put("timezone", timezone)
            .put("durationMin", durationMin)
            .put("active", active)
            .put("remindBeforeMin", remindBeforeMin ?: JSONObject.NULL)
            .put("notifyEmail", notifyEmail)
        when (repeat) {
            ScheduleRepeat.Once -> body.put("date", date)
            ScheduleRepeat.Weekly -> body.put("weekdays", JSONArray(weekdays))
            ScheduleRepeat.Monthly -> body.put("monthDay", monthDay)
            else -> Unit
        }
        return body
    }

    suspend fun conversation(id: String): ConversationThread = get("/conversations/$id").toThread()

    suspend fun deleteConversation(id: String) {
        send("DELETE", "/conversations/$id")
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        viaVoice: Boolean = false,
        attachmentIds: List<String> = emptyList(),
    ): SendResult =
        send(
            "POST",
            "/conversations/$conversationId/messages",
            JSONObject().put("text", text).put("viaVoice", viaVoice).put("attachmentIds", JSONArray(attachmentIds)),
        ).toSendResult()

    // Attachments: create → PUT bytes to R2 → complete → poll until ready

    suspend fun createUpload(file: PreparedFile): UploadTicket {
        val body = JSONObject()
            .put("fileName", file.name)
            .put("mimeType", file.mimeType)
            .put("sizeBytes", file.file.length())
        file.width?.let { body.put("width", it) }
        file.height?.let { body.put("height", it) }
        return send("POST", "/attachments", body).toUploadTicket()
    }

    /** Streams [file] straight to the presigned R2 URL, reporting progress in 0..1. */
    suspend fun uploadToStorage(ticket: UploadTicket, file: PreparedFile, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(ticket.uploadUrl)
                .put(ProgressRequestBody(file.file, file.mimeType.toMediaType(), onProgress))
                .build()
            val response = try {
                uploadClient.newCall(request).execute()
            } catch (e: IOException) {
                if (e is java.io.InterruptedIOException && !currentCoroutineContext().isActive) throw e
                throw ApiException("Upload failed. Check your connection.")
            }
            response.use { if (!it.isSuccessful) throw ApiException("Upload failed (${it.code}).") }
        }

    /** Shares an article or YouTube link; the server reads it in the background like a file. */
    suspend fun createLink(url: String): Attachment =
        send("POST", "/attachments/link", JSONObject().put("url", url)).getJSONObject("attachment").toAttachment()

    suspend fun completeUpload(id: String): Attachment =
        send("POST", "/attachments/$id/complete").getJSONObject("attachment").toAttachment()

    suspend fun attachment(id: String): Attachment = get("/attachments/$id").getJSONObject("attachment").toAttachment()

    suspend fun deleteAttachment(id: String) {
        send("DELETE", "/attachments/$id")
    }

    suspend fun saveTranscript(conversationId: String, turns: List<Pair<Boolean, String>>): List<Message> {
        val array = JSONArray()
        turns.forEach { (isUser, text) ->
            array.put(JSONObject().put("role", if (isUser) "user" else "assistant").put("text", text))
        }
        return send("POST", "/conversations/$conversationId/transcript", JSONObject().put("turns", array))
            .listOf("messages").map { it.toMessage() }
    }

    suspend fun saveCallSummary(
        conversationId: String,
        durationSec: Int,
        isVideo: Boolean,
        tipsCount: Int = 2,
    ): Message =
        send(
            "POST",
            "/conversations/$conversationId/call-summary",
            JSONObject()
                .put("durationSec", durationSec)
                .put("isVideo", isVideo)
                .put("tipsCount", tipsCount),
        ).getJSONObject("message").toMessage()

    // Unified Chats, Peer Messaging & 1:1 Call Rooms

    suspend fun chatsOverview(): ChatsOverview = get("/chats/overview").toChatsOverview()

    suspend fun setOpenToSpeak(openToSpeak: Boolean): Boolean =
        send("PATCH", "/chats/open-to-speak", JSONObject().put("openToSpeak", openToSpeak)).optBoolean("openToSpeak", openToSpeak)

    suspend fun addPartner(partnerId: String) {
        send("POST", "/chats/partners/$partnerId")
    }

    suspend fun removePartner(partnerId: String) {
        send("DELETE", "/chats/partners/$partnerId")
    }

    suspend fun openPeerChat(partnerId: String): PeerConversationThread =
        send("POST", "/chats/people/$partnerId/open").toPeerConversationThread()

    suspend fun peerChat(conversationId: String): PeerConversationThread =
        get("/chats/people/$conversationId").toPeerConversationThread()

    suspend fun markPeerChatRead(conversationId: String) {
        send("POST", "/chats/people/$conversationId/read")
    }

    suspend fun sendPeerMessage(conversationId: String, text: String): PeerMessage =
        send("POST", "/chats/people/$conversationId/messages", JSONObject().put("text", text))
            .getJSONObject("message")
            .toPeerMessage()

    suspend fun mutePeerChat(conversationId: String, muted: Boolean): Boolean =
        send("PATCH", "/chats/people/$conversationId", JSONObject().put("muted", muted)).optBoolean("muted", muted)

    suspend fun reportPeerUser(conversationId: String, reason: String, details: String) {
        val body = JSONObject().put("reason", reason)
        if (details.isNotBlank()) body.put("details", details.trim())
        send("POST", "/chats/people/$conversationId/report", body)
    }

    suspend fun deletePeerChat(conversationId: String) {
        send("DELETE", "/chats/people/$conversationId")
    }

    suspend fun startPeerCall(conversationId: String, cameraEnabled: Boolean = false): StartPeerCallResult =
        send("POST", "/chats/people/$conversationId/call", JSONObject().put("cameraEnabled", cameraEnabled))
            .toStartPeerCallResult()

    suspend fun joinPeerCall(callId: String): JoinPeerCallResult =
        send("POST", "/chats/calls/$callId/join").toJoinPeerCallResult()

    suspend fun declinePeerCall(callId: String): PeerCallRoom =
        send("POST", "/chats/calls/$callId/decline").getJSONObject("call").toPeerCallRoom()

    suspend fun pingPeerCall(callId: String) {
        send("POST", "/chats/calls/$callId/ping")
    }

    suspend fun endPeerCall(callId: String, durationSec: Int, isVideo: Boolean): PeerMessage =
        send(
            "POST",
            "/chats/calls/$callId/end",
            JSONObject().put("durationSec", durationSec).put("isVideo", isVideo),
        ).getJSONObject("summaryMessage").toPeerMessage()

    suspend fun coachPrompts(
        mode: String? = null,
        targetLanguage: String? = null,
        contextLabel: String? = null,
        partnerName: String? = null,
        lastLine: String? = null,
    ): CoachPrompts {
        val body = JSONObject()
        mode?.takeIf { it.isNotBlank() }?.let { body.put("mode", it) }
        targetLanguage?.takeIf { it.isNotBlank() }?.let { body.put("targetLanguage", it) }
        contextLabel?.takeIf { it.isNotBlank() }?.let { body.put("contextLabel", it) }
        partnerName?.takeIf { it.isNotBlank() }?.let { body.put("partnerName", it) }
        lastLine?.takeIf { it.isNotBlank() }?.let { body.put("lastLine", it) }
        return send("POST", "/chats/coach-prompts", body).toCoachPrompts()
    }

    suspend fun explain(messageId: String): String =
        send("POST", "/messages/$messageId/explain").optString("explanation")

    suspend fun typingHint(text: String): String? =
        send("POST", "/tutor/hint", JSONObject().put("text", text)).let {
            if (it.isNull("hint")) null else it.optString("hint").takeIf(String::isNotBlank)
        }

    // Account Privacy, Avatar Removal & Data Export

    suspend fun deleteAvatar() {
        send("DELETE", "/me/avatar")
    }

    suspend fun privacySettings(): PrivacySettings =
        get("/me/privacy").getJSONObject("privacy").toPrivacySettings()

    suspend fun updatePrivacySettings(
        openToSpeak: Boolean? = null,
        allowCallInvites: Boolean? = null,
        whoCanMessage: String? = null,
    ): PrivacySettings {
        val body = JSONObject()
        openToSpeak?.let { body.put("openToSpeak", it) }
        allowCallInvites?.let { body.put("allowCallInvites", it) }
        whoCanMessage?.let { body.put("whoCanMessage", it) }
        return send("PATCH", "/me/privacy", body).getJSONObject("privacy").toPrivacySettings()
    }

    suspend fun unblockLearner(userId: String) {
        send("DELETE", "/me/blocked/$userId")
    }

    suspend fun requestDataExport(): String =
        send("POST", "/me/export").optString("email")

    // 12 · Vocabulary SRS Library

    suspend fun vocabulary(): VocabLibrary = get("/vocabulary").toVocabLibrary()

    suspend fun previewVocabPhrase(phrase: String, contextNote: String? = null): VocabPreview {
        val body = JSONObject().put("phrase", phrase.trim())
        contextNote?.takeIf { it.isNotBlank() }?.let { body.put("contextNote", it.trim()) }
        return send("POST", "/vocabulary/preview", body).toVocabPreview()
    }

    suspend fun addVocabPhrase(
        phrase: String,
        translation: String? = null,
        source: String? = null,
        contextNote: String? = null,
    ): VocabPhrase {
        val body = JSONObject().put("phrase", phrase.trim())
        translation?.takeIf { it.isNotBlank() }?.let { body.put("translation", it.trim()) }
        source?.takeIf { it.isNotBlank() }?.let { body.put("source", it.trim()) }
        contextNote?.takeIf { it.isNotBlank() }?.let { body.put("contextNote", it.trim()) }
        return send("POST", "/vocabulary", body).getJSONObject("phrase").toVocabPhrase()
    }

    suspend fun updateVocabPhrase(
        id: String,
        phrase: String? = null,
        translation: String? = null,
        contextNote: String? = null,
    ): VocabPhrase {
        val body = JSONObject()
        phrase?.takeIf { it.isNotBlank() }?.let { body.put("phrase", it.trim()) }
        translation?.takeIf { it.isNotBlank() }?.let { body.put("translation", it.trim()) }
        if (contextNote != null) body.put("contextNote", contextNote.trim().ifBlank { JSONObject.NULL })
        return send("PATCH", "/vocabulary/$id", body).getJSONObject("phrase").toVocabPhrase()
    }

    suspend fun reviewVocabPhrase(id: String, passed: Boolean): VocabPhrase =
        send("POST", "/vocabulary/$id/review", JSONObject().put("passed", passed))
            .getJSONObject("phrase")
            .toVocabPhrase()

    suspend fun captureVocabPhrases(phrases: List<Triple<String, String?, String?>>): List<VocabPhrase> {
        val array = JSONArray().apply {
            phrases.forEach { (phrase, translation, source) ->
                put(
                    JSONObject().apply {
                        put("phrase", phrase.trim())
                        translation?.takeIf { it.isNotBlank() }?.let { put("translation", it.trim()) }
                        source?.takeIf { it.isNotBlank() }?.let { put("source", it.trim()) }
                    },
                )
            }
        }
        return send("POST", "/vocabulary/capture", JSONObject().put("phrases", array))
            .listOf("phrases")
            .map { it.toVocabPhrase() }
    }

    suspend fun deleteVocabPhrase(id: String) {
        send("DELETE", "/vocabulary/$id")
    }

    // 15 · Live Rooms

    suspend fun liveRooms(): LiveRoomsBrowse = get("/live-rooms").toLiveRoomsBrowse()

    suspend fun createLiveRoom(
        title: String,
        language: String,
        level: String,
        maxMembers: Int,
        visibility: String,
    ): LiveRoom = send(
        "POST",
        "/live-rooms",
        JSONObject()
            .put("title", title.trim())
            .put("language", language)
            .put("level", level)
            .put("maxMembers", maxMembers.coerceIn(2, 4))
            .put("visibility", visibility),
    ).getJSONObject("room").toLiveRoom()

    suspend fun liveRoom(id: String): LiveRoom = get("/live-rooms/$id").getJSONObject("room").toLiveRoom()

    suspend fun joinLiveRoom(id: String): JoinLiveRoomResult =
        send("POST", "/live-rooms/$id/join").toJoinLiveRoomResult()

    suspend fun joinLiveRoomByCode(code: String): JoinLiveRoomResult =
        send("POST", "/live-rooms/join-code", JSONObject().put("code", code.trim())).toJoinLiveRoomResult()

    suspend fun startLiveRoom(id: String): JoinLiveRoomResult =
        send("POST", "/live-rooms/$id/start").toJoinLiveRoomResult()

    suspend fun updateLiveRoom(id: String, visibility: String? = null, nextQuestion: Boolean = false): LiveRoom {
        val body = JSONObject()
        visibility?.let { body.put("visibility", it) }
        if (nextQuestion) body.put("nextQuestion", true)
        return send("PATCH", "/live-rooms/$id", body).getJSONObject("room").toLiveRoom()
    }

    suspend fun pingLiveRoom(id: String) {
        send("POST", "/live-rooms/$id/ping")
    }

    suspend fun leaveLiveRoom(id: String, durationSec: Int = 0) {
        send("POST", "/live-rooms/$id/leave", JSONObject().put("durationSec", durationSec.coerceAtLeast(0)))
    }

    suspend fun endLiveRoom(id: String, durationSec: Int = 0) {
        send("POST", "/live-rooms/$id/end", JSONObject().put("durationSec", durationSec.coerceAtLeast(0)))
    }

    // App version & updates

    suspend fun appUpdateStatus(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): AppUpdateInfo? = try {
        val req = Request.Builder()
            .url("$apiUrl/app/version?platform=android&versionCode=$currentVersionCode&versionName=$currentVersionName")
            .get()
            .build()
        execute(req).toAppUpdateInfoOrNull(currentVersionCode, currentVersionName)
    } catch (_: ApiException) {
        null
    }

    // Transport

    private suspend fun get(path: String): JSONObject = execute(request(path).get().build())

    private suspend fun send(method: String, path: String, body: JSONObject? = null): JSONObject =
        execute(request(path).method(method, (body ?: JSONObject()).toString().toRequestBody(JSON)).build())

    private fun request(path: String): Request.Builder {
        val token = tokenProvider() ?: throw ApiException("Please sign in again.", unauthorized = true)
        return Request.Builder()
            .url("$apiUrl$path")
            .header("Authorization", "Bearer $token")
            .header("X-Timezone", java.util.TimeZone.getDefault().id)
    }

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            throw ApiException("Can't reach Zuno. Check your connection and try again.")
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            when {
                it.code == 401 -> throw ApiException("Your session has expired. Please sign in again.", unauthorized = true)
                !it.isSuccessful -> {
                    val message = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                    throw ApiException(message?.takeIf(String::isNotBlank) ?: "Something went wrong (${it.code}).")
                }
                raw.isBlank() -> JSONObject()
                else -> JSONObject(raw)
            }
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()

        // Tutor replies come from Gemini (and may read attached files), so allow generous read timeouts.
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        // Uploads of up to 20 MB on mobile networks.
        val uploadClient: OkHttpClient = client.newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
