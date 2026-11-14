package com.zuno.ai.service

import com.zuno.ai.BuildConfig
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
    val url: String,
)

class SessionExpiredException : Exception("Your session has expired. Please sign in again.")

/** Fetches LiveKit access tokens from the backend for the signed-in user (Cartesia multilingual TTS). */
object TokenService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tokenUrl = "${BuildConfig.AUTH_BASE_URL.trimEnd('/')}/api/livekit/token"

    suspend fun fetchToken(
        sessionToken: String,
        language: String? = null,
        conversationId: String? = null,
        speakLastReply: Boolean = false,
    ): TokenResult = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            language?.let { put("language", it) }
            conversationId?.let { put("conversationId", it) }
            if (speakLastReply) put("speakLastReply", true)
        }
        postTokenRequest(sessionToken, bodyJson)
    }

    suspend fun fetchOnboardingToken(
        sessionToken: String,
        step: String,
        targetLanguage: String,
        nativeLanguage: String,
        accent: String? = null,
        name: String? = null,
        level: String? = null,
    ): TokenResult = withContext(Dispatchers.IO) {
        val onboardingObj = JSONObject().apply {
            put("step", step)
            put("targetLanguage", targetLanguage)
            put("nativeLanguage", nativeLanguage)
            accent?.takeIf { it.isNotBlank() }?.let { put("accent", it) }
            name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
            level?.takeIf { it.isNotBlank() }?.let { put("level", it) }
        }
        val bodyJson = JSONObject().apply {
            put("mode", "onboarding")
            put("language", nativeLanguage)
            put("onboarding", onboardingObj)
        }
        postTokenRequest(sessionToken, bodyJson)
    }

    private fun postTokenRequest(sessionToken: String, bodyJson: JSONObject): TokenResult {
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
            return TokenResult(
                token = json.getString("token"),
                room = json.getString("room"),
                url = json.getString("url"),
            )
        }
    }
}
