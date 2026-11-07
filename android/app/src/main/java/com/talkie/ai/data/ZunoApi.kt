package com.talkie.ai.data

import com.talkie.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

    suspend fun updateProfile(targetLanguage: TargetLanguage? = null, level: String? = null, name: String? = null): Profile {
        val body = JSONObject()
        targetLanguage?.let { body.put("targetLanguage", it.code) }
        level?.let { body.put("level", it) }
        name?.let { body.put("name", it) }
        return send("PATCH", "/me", body).toProfile()
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

    suspend fun startLesson(topicId: String, lessonId: String): ConversationThread =
        send("POST", "/topics/$topicId/lessons/$lessonId/start").toThread()

    suspend fun completeLesson(topicId: String, lessonId: String): TopicDetail =
        send("POST", "/topics/$topicId/lessons/$lessonId/complete").getJSONObject("topic").toTopicDetail()

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

    suspend fun createFreeTalk(): ConversationThread = send("POST", "/conversations", JSONObject()).toThread()

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
