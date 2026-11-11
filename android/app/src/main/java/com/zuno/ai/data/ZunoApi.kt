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

    /** PATCH /me with any of: name, targetLanguage, nativeLanguage, level, accent and preference fields. */
    suspend fun updateProfile(fields: Map<String, Any>): Profile {
        val body = JSONObject()
        fields.forEach { (key, value) -> body.put(key, value) }
        return send("PATCH", "/me", body).toProfile()
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
    ): TopicDetail {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("attachmentIds", JSONArray(attachmentIds))
            .put("parts", JSONArray(parts))
        return send("POST", "/topics", body).getJSONObject("topic").toTopicDetail()
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

    suspend fun ttsPreview(text: String, language: String? = null, slow: Boolean = false): ByteArray =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("text", text).put("slow", slow)
            language?.takeIf { it.isNotBlank() }?.let { body.put("language", it) }
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

    suspend fun createRoleplay(scenario: String, userRole: String, aiRole: String): ConversationThread {
        val body = JSONObject().put("scenario", scenario)
        if (userRole.isNotBlank()) body.put("userRole", userRole)
        if (aiRole.isNotBlank()) body.put("aiRole", aiRole)
        return send("POST", "/roleplays", body).toThread()
    }

    suspend fun openRoleplay(id: String): ConversationThread = send("POST", "/roleplays/$id/open").toThread()

    suspend fun restartRoleplay(id: String): ConversationThread = send("POST", "/roleplays/$id/restart").toThread()

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

    suspend fun explain(messageId: String): String =
        send("POST", "/messages/$messageId/explain").optString("explanation")

    suspend fun typingHint(text: String): String? =
        send("POST", "/tutor/hint", JSONObject().put("text", text)).let {
            if (it.isNull("hint")) null else it.optString("hint").takeIf(String::isNotBlank)
        }

    // Transport

    private suspend fun get(path: String): JSONObject = execute(request(path).get().build())

    private suspend fun send(method: String, path: String, body: JSONObject? = null): JSONObject =
        execute(request(path).method(method, (body ?: JSONObject()).toString().toRequestBody(JSON)).build())

    private fun request(path: String): Request.Builder {
        val token = tokenProvider() ?: throw ApiException("Please sign in again.", unauthorized = true)
        return Request.Builder().url("$apiUrl$path").header("Authorization", "Bearer $token")
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
