package com.zuno.ai.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.zuno.ai.BuildConfig

/** Thrown when the user dismisses the Google account picker. */
class GoogleSignInCancelledException : Exception()

/**
 * Shows the "Sign in with Google" sheet and returns a Google ID token issued for
 * the backend's Web client ID, which Better Auth verifies server-side.
 *
 * [activityContext] must be an Activity context so the sheet can be displayed.
 */
suspend fun requestGoogleIdToken(
    activityContext: Context,
    serverClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
): String {
    if (serverClientId.isBlank()) {
        throw AuthException("Google sign-in isn't configured yet (missing zuno.googleWebClientId).")
    }

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()

    val credential = try {
        CredentialManager.create(activityContext).getCredential(activityContext, request).credential
    } catch (_: GetCredentialCancellationException) {
        throw GoogleSignInCancelledException()
    } catch (_: NoCredentialException) {
        throw AuthException("No Google account found on this device.")
    } catch (e: GetCredentialException) {
        throw AuthException("Google sign-in failed. Please try again.")
    }

    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw AuthException("Google sign-in returned an unexpected credential.")
    }
    return try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (_: GoogleIdTokenParsingException) {
        throw AuthException("Google sign-in returned an invalid token.")
    }
}
