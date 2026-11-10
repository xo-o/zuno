package com.zuno.ai.auth

import com.zuno.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    val image: String?,
)

/** A signed Better Auth session token plus the user it belongs to. */
data class AuthSession(val token: String, val user: AuthUser)

sealed class MagicLinkPollResult {
    data class Verified(val session: AuthSession) : MagicLinkPollResult()
    data object Pending : MagicLinkPollResult()
    data object Expired : MagicLinkPollResult()
}

class AuthException(message: String) : Exception(message)

/**
 * Talks to the Better Auth endpoints mounted at `/api/auth` on the Next.js backend.
 * Sessions come back in the `set-auth-token` header (bearer plugin) and are sent
 * back as `Authorization: Bearer <token>`.
 */
class AuthApi(
    baseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val client: OkHttpClient = defaultClient,
) {
    private val authUrl = "${baseUrl.trimEnd('/')}/api/auth"

    /** Emails a magic link to [email]. Unknown emails get an account on first sign-in. */
    suspend fun sendMagicLink(email: String, flowId: String? = null) {
        val body = JSONObject().put("email", email)
        if (flowId != null) {
            body.put("callbackURL", "/auth/verify?flowId=$flowId")
        }
        execute(post("/sign-in/magic-link", body)) { }
    }

    /** Polls to check if the magic link was verified on another device or browser. */
    suspend fun pollMagicLink(flowId: String): MagicLinkPollResult = withContext(Dispatchers.IO) {
        val url = "$authUrl/magic-link/poll".toHttpUrl().newBuilder()
            .addQueryParameter("flowId", flowId)
            .build()
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext MagicLinkPollResult.Expired
                if (!response.isSuccessful) return@withContext MagicLinkPollResult.Pending
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw)
                when (json.optString("status")) {
                    "verified" -> {
                        val token = json.optString("token")
                        val userJson = json.optJSONObject("user")
                        val user = if (userJson != null) {
                            AuthUser(
                                id = userJson.optString("id"),
                                email = userJson.optString("email"),
                                name = userJson.optString("name"),
                                image = userJson.optString("image").takeIf { it.isNotEmpty() }
                            )
                        } else {
                            AuthUser("", "", "", null)
                        }
                        if (token.isNotEmpty()) {
                            MagicLinkPollResult.Verified(AuthSession(token, user))
                        } else {
                            MagicLinkPollResult.Pending
                        }
                    }
                    "expired" -> MagicLinkPollResult.Expired
                    else -> MagicLinkPollResult.Pending
                }
            }
        } catch (_: Exception) {
            MagicLinkPollResult.Pending
        }
    }

    /** Redeems the single-use token from a magic link. */
    suspend fun verifyMagicLink(token: String): AuthSession {
        val url = "$authUrl/magic-link/verify".toHttpUrl().newBuilder()
            .addQueryParameter("token", token)
            .build()
        return execute(Request.Builder().url(url).get().build(), ::readSession)
    }

    /** Exchanges a Google ID token from Credential Manager for a session. */
    suspend fun signInWithGoogle(idToken: String): AuthSession = signInWithIdToken("google", idToken)

    /** Exchanges a Sign in with Apple identity token for a session. */
    suspend fun signInWithApple(idToken: String): AuthSession = signInWithIdToken("apple", idToken)

    /** Returns the current user, or null if the token is no longer valid. */
    suspend fun getSession(token: String): AuthUser? {
        val request = Request.Builder().url("$authUrl/get-session").bearer(token).get().build()
        return execute(request) { response ->
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank() || raw == "null") null else JSONObject(raw).optJSONObject("user")?.toUser()
        }
    }

    suspend fun signOut(token: String) {
        execute(post("/sign-out", JSONObject()).newBuilder().bearer(token).build()) { }
    }

    private suspend fun signInWithIdToken(provider: String, idToken: String): AuthSession {
        val body = JSONObject()
            .put("provider", provider)
            .put("idToken", JSONObject().put("token", idToken))
        return execute(post("/sign-in/social", body), ::readSession)
    }

    private fun readSession(response: Response): AuthSession {
        val token = response.header(SET_AUTH_TOKEN_HEADER)
            ?: throw AuthException("Sign-in succeeded but no session token was returned.")
        val user = JSONObject(response.body?.string().orEmpty()).optJSONObject("user")?.toUser()
            ?: throw AuthException("Sign-in succeeded but no user was returned.")
        return AuthSession(token, user)
    }

    private fun post(path: String, body: JSONObject): Request =
        Request.Builder()
            .url("$authUrl$path")
            .post(body.toString().toRequestBody(JSON))
            .build()

    private suspend fun <T> execute(request: Request, onSuccess: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw AuthException("Can't reach the server. Check your connection and try again.")
            }
            response.use {
                when {
                    it.isSuccessful -> onSuccess(it)
                    // Better Auth redirects with ?error=… when a magic link is invalid, expired or already used.
                    it.isRedirect -> throw AuthException(errorForRedirect(it.header("Location")))
                    else -> throw AuthException(errorMessage(it))
                }
            }
        }

    private fun errorForRedirect(location: String?): String {
        val code = location?.let { runCatching { it.toHttpUrl().queryParameter("error") }.getOrNull() }
        return when (code) {
            "INVALID_TOKEN", "EXPIRED_TOKEN" -> "This sign-in link has expired or was already used. Request a new one."
            else -> "We couldn't sign you in with that link. Request a new one."
        }
    }

    private fun errorMessage(response: Response): String {
        if (response.code == 429) return "Too many attempts. Please wait a minute and try again."
        val message = runCatching { JSONObject(response.body?.string().orEmpty()).optString("message") }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: "Something went wrong (${response.code}). Please try again."
    }

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    private fun JSONObject.toUser() = AuthUser(
        id = getString("id"),
        email = optString("email"),
        name = optString("name"),
        image = optString("image").takeIf { it.isNotBlank() && it != "null" },
    )

    private companion object {
        const val SET_AUTH_TOKEN_HEADER = "set-auth-token"
        val JSON = "application/json".toMediaType()

        // Redirects are Better Auth's error signal for magic links, so they must not be followed.
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
