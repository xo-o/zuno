package com.zuno.ai.auth

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Patterns
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

sealed interface AuthState {
    data object SignedOut : AuthState

    /** [user] is null until the stored session has been refreshed from the server. */
    data class SignedIn(val user: AuthUser?) : AuthState
}

data class AuthFormState(
    val isBusy: Boolean = false,
    val linkSentTo: String? = null,
    val error: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AuthApi()
    private val tokenStore = SessionTokenStore(application)
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
        refreshSession()
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
            _form.update { it.copy(linkSentTo = email) }
            startMagicLinkPolling(flowId)
        }
    }

    private fun startMagicLinkPolling(flowId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val timeoutMs = 5 * 60 * 1000L // 5 minutes timeout
            while (isActive && (System.currentTimeMillis() - startTime) < timeoutMs) {
                delay(2000L)
                try {
                    when (val result = api.pollMagicLink(flowId)) {
                        is MagicLinkPollResult.Verified -> {
                            completeSignIn(result.session)
                            break
                        }
                        is MagicLinkPollResult.Expired -> break
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
        val token = uri?.takeIf { it.path?.endsWith("/verify") == true }?.getQueryParameter("token")
        if (token.isNullOrBlank()) return false
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
        runFormAction {
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
        _form.update { it.copy(linkSentTo = null, error = null) }
    }

    fun clearError() = _form.update { it.copy(error = null) }

    fun signOut() {
        pollingJob?.cancel()
        pollingJob = null
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
        _form.value = AuthFormState()
        _authState.value = AuthState.SignedIn(session.user)
    }

    private fun runFormAction(allowWhileBusy: Boolean = false, action: suspend () -> Unit) {
        if (_form.value.isBusy && !allowWhileBusy) return
        _form.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: AuthException) {
                _form.update { it.copy(error = e.message) }
            } finally {
                _form.update { it.copy(isBusy = false) }
            }
        }
    }
}
