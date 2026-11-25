package com.zuno.ai.auth

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Patterns
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuno.ai.push.Push
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Extracts the single-use `token` query parameter from `/auth/verify` magic-link URIs. */
fun extractMagicLinkToken(uri: Uri?): String? =
    uri?.takeIf { it.path?.endsWith("/verify") == true }
        ?.getQueryParameter("token")
        ?.takeIf { it.isNotBlank() }

sealed interface AuthState {
    data object SignedOut : AuthState

    /** [user] is null until the stored session has been refreshed from the server. */
    data class SignedIn(val user: AuthUser?) : AuthState
}

data class AuthFormState(
    val isBusy: Boolean = false,
    /** True while the busy action is Google sign-in rather than sending a magic link. */
    val isGoogleBusy: Boolean = false,
    /** True once credentials succeeded and the app is loading `/api/me` to route post-auth. */
    val isCompletingSignIn: Boolean = false,
    val linkSentTo: String? = null,
    val error: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AuthApi()
    private val tokenStore = SessionTokenStore(application)
    private val flowPrefs = application.applicationContext.getSharedPreferences(FLOW_PREFS_NAME, Context.MODE_PRIVATE)
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var activeFlowId: String? = null

    private val _authState = MutableStateFlow<AuthState>(
        if (tokenStore.read() != null) AuthState.SignedIn(user = null) else AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    /** Bearer token for authenticated calls to the backend, or null when signed out. */
    val sessionToken: String? get() = tokenStore.read()

    init {
        if (tokenStore.read() != null) {
            clearSavedPendingFlow()
            refreshSession()
        } else {
            restorePendingFlowIfValid()
        }
    }

    fun sendMagicLink(rawEmail: String) {
        val email = rawEmail.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _form.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        val flowId = java.util.UUID.randomUUID().toString()
        activeFlowId = flowId
        runFormAction {
            api.sendMagicLink(email, flowId)
            savePendingFlow(flowId, email)
            _form.update { it.copy(linkSentTo = email) }
            startMagicLinkPolling(flowId)
        }
    }

    private fun startMagicLinkPolling(flowId: String, startedAtMillis: Long = System.currentTimeMillis()) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && (System.currentTimeMillis() - startedAtMillis) < FLOW_POLL_TIMEOUT_MS) {
                delay(2000L)
                try {
                    when (val result = api.pollMagicLink(flowId)) {
                        is MagicLinkPollResult.Verified -> {
                            completeSignIn(result.session)
                            break
                        }
                        is MagicLinkPollResult.Expired -> {
                            clearSavedPendingFlow()
                            break
                        }
                        is MagicLinkPollResult.Pending -> { /* keep waiting */ }
                    }
                } catch (_: Exception) {
                    // Ignore transient network errors, keep polling
                }
            }
        }
    }

    /** Handles https://<host>/auth/verify?token=… and zuno://auth/verify?token=… links. */
    fun handleMagicLink(uri: Uri?): Boolean {
        val token = extractMagicLinkToken(uri) ?: return false
        // Only the flow this app instance started is trusted, never one supplied by a link.
        val flowId = activeFlowId
        pollingJob?.cancel()
        pollingJob = null
        runFormAction(allowWhileBusy = true) {
            val session = try {
                api.verifyMagicLink(token)
            } catch (e: AuthException) {
                // The link may already have been confirmed in a browser for the sign-in this
                // app is waiting on; pick up that session instead of failing.
                flowId?.let { awaitVerifiedFlow(it) } ?: throw e
            }
            completeSignIn(session)
        }
        return true
    }

    private suspend fun awaitVerifiedFlow(flowId: String): AuthSession? {
        repeat(5) { attempt ->
            if (attempt > 0) delay(1000L)
            when (val result = api.pollMagicLink(flowId)) {
                is MagicLinkPollResult.Verified -> return result.session
                is MagicLinkPollResult.Expired -> return null
                is MagicLinkPollResult.Pending -> Unit
            }
        }
        return null
    }

    fun signInWithGoogle(activityContext: Context) {
        runFormAction(google = true) {
            val idToken = try {
                requestGoogleIdToken(activityContext)
            } catch (_: GoogleSignInCancelledException) {
                return@runFormAction
            }
            completeSignIn(api.signInWithGoogle(idToken))
        }
    }

    fun clearMagicLinkSent() {
        pollingJob?.cancel()
        pollingJob = null
        activeFlowId = null
        clearSavedPendingFlow()
        _form.update { it.copy(linkSentTo = null, isCompletingSignIn = false, error = null) }
    }

    fun clearError() = _form.update { it.copy(error = null) }

    /** Clears transient sign-in form state once post-auth routing has left the Auth screen. */
    fun onAuthFlowCompleted() {
        if (_form.value != AuthFormState()) {
            _form.value = AuthFormState()
        }
    }

    fun signOut() {
        pollingJob?.cancel()
        pollingJob = null
        activeFlowId = null
        clearSavedPendingFlow()
        val token = tokenStore.read()
        tokenStore.clear()
        _authState.value = AuthState.SignedOut
        _form.value = AuthFormState()
        if (token != null) {
            viewModelScope.launch {
                // Stop this device's pushes for the account before the session is revoked.
                runCatching { Push.unregister(getApplication(), token) }
                runCatching { api.signOut(token) }
            }
        }
    }

    /** The account was deleted on the server: forget the session without calling sign-out. */
    fun forgetSession() {
        pollingJob?.cancel()
        pollingJob = null
        activeFlowId = null
        clearSavedPendingFlow()
        tokenStore.clear()
        _authState.value = AuthState.SignedOut
        _form.value = AuthFormState()
    }

    private fun refreshSession() {
        val token = tokenStore.read() ?: return
        viewModelScope.launch {
            try {
                val user = api.getSession(token)
                if (user == null) {
                    tokenStore.clear()
                    _authState.value = AuthState.SignedOut
                } else if (tokenStore.read() == token) {
                    _authState.value = AuthState.SignedIn(user)
                }
            } catch (_: AuthException) {
                // Offline or server error: keep the stored session and retry on next launch.
            }
        }
    }

    private fun completeSignIn(session: AuthSession) {
        tokenStore.write(session.token)
        activeFlowId = null
        clearSavedPendingFlow()
        // Keep `linkSentTo` + `isCompletingSignIn` active while `/api/me` resolves so `4c` never flashes back to an empty form.
        _form.update { current ->
            current.copy(
                isCompletingSignIn = true,
                error = null,
            )
        }
        _authState.value = AuthState.SignedIn(session.user)
    }

    private fun savePendingFlow(flowId: String, email: String) {
        flowPrefs.edit {
            putString(KEY_PENDING_FLOW_ID, flowId)
            putString(KEY_PENDING_EMAIL, email)
            putLong(KEY_PENDING_SENT_AT, System.currentTimeMillis())
        }
    }

    private fun clearSavedPendingFlow() {
        flowPrefs.edit { clear() }
    }

    private fun restorePendingFlowIfValid() {
        val savedFlowId = flowPrefs.getString(KEY_PENDING_FLOW_ID, null)
        val savedEmail = flowPrefs.getString(KEY_PENDING_EMAIL, null)
        val sentAt = flowPrefs.getLong(KEY_PENDING_SENT_AT, 0L)
        val ageMs = System.currentTimeMillis() - sentAt
        if (savedFlowId.isNullOrBlank() || savedEmail.isNullOrBlank() || sentAt <= 0L || ageMs >= MAGIC_LINK_TTL_MS) {
            clearSavedPendingFlow()
            return
        }
        activeFlowId = savedFlowId
        _form.value = AuthFormState(linkSentTo = savedEmail)
        if (ageMs < FLOW_POLL_TIMEOUT_MS) {
            startMagicLinkPolling(savedFlowId, startedAtMillis = sentAt)
        }
    }

    private fun runFormAction(allowWhileBusy: Boolean = false, google: Boolean = false, action: suspend () -> Unit) {
        if (_form.value.isBusy && !allowWhileBusy) return
        _form.update { it.copy(isBusy = true, isGoogleBusy = google, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: AuthException) {
                _form.update { it.copy(error = e.message, isCompletingSignIn = false) }
            } finally {
                _form.update { it.copy(isBusy = false, isGoogleBusy = false) }
            }
        }
    }

    private companion object {
        const val FLOW_PREFS_NAME = "zuno_magic_link_flow"
        const val KEY_PENDING_FLOW_ID = "pending_flow_id"
        const val KEY_PENDING_EMAIL = "pending_email"
        const val KEY_PENDING_SENT_AT = "pending_sent_at"
        const val FLOW_POLL_TIMEOUT_MS = 5 * 60 * 1000L
        const val MAGIC_LINK_TTL_MS = 15 * 60 * 1000L
    }
}
