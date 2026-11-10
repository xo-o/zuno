package com.zuno.ai.service

import com.zuno.ai.BuildConfig
import com.zuno.ai.model.VoiceCatalog
import com.zuno.ai.model.VoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TokenResult(
    val token: String,
    val room: String,
    val url: String
)

class SessionExpiredException : Exception("Your session has expired. Please sign in again.")

/** Fetches LiveKit access tokens from the backend for the signed-in user. */
object TokenService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tokenUrl = "${BuildConfig.AUTH_BASE_URL.trimEnd('/')}/api/livekit/token"

    suspend fun fetchToken(
        sessionToken: String,
        selectedVoice: VoiceOption = VoiceCatalog.DEFAULT_VOICE,
        conversationId: String? = null,
        speakLastReply: Boolean = false,
    ): TokenResult = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("provider", selectedVoice.provider)
            put("voice", selectedVoice.id)
            put("language", selectedVoice.language)
            conversationId?.let { put("conversationId", it) }
            if (speakLastReply) put("speakLastReply", true)
        }
        val request = Request.Builder()
            .url(tokenUrl)
            .header("Authorization", "Bearer $sessionToken")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Can't reach the server. Check your connection and try again.", e)
        }
        response.use {
            if (it.code == 401) throw SessionExpiredException()
            if (!it.isSuccessful) throw IOException("Couldn't start a voice session (${it.code}).")
            val json = JSONObject(it.body?.string().orEmpty())
            TokenResult(
                token = json.getString("token"),
                room = json.getString("room"),
                url = json.getString("url"),
            )
        }
    }
}
