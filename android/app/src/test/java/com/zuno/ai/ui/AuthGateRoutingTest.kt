package com.zuno.ai.ui

import com.zuno.ai.auth.AuthState
import com.zuno.ai.auth.AuthUser
import com.zuno.ai.data.Profile
import com.zuno.ai.ui.screens.AuthMode
import com.zuno.ai.viewmodel.ProfileSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthGateRoutingTest {

    private val signedIn = AuthState.SignedIn(
        AuthUser(id = "u1", email = "noah@example.com", name = "", image = null),
    )

    private fun profile(onboardingCompleted: Boolean) = Profile(
        name = "",
        email = "noah@example.com",
        targetLanguage = "es",
        level = "B1",
        onboardingCompleted = onboardingCompleted,
    )

    @Test
    fun signedOut_neverEmitsGateRoute() {
        assertNull(
            resolveAuthGateRoute(
                currentRoute = ZunoRoute.Auth(AuthMode.SignUp),
                splashDone = true,
                authState = AuthState.SignedOut,
                profileSync = ProfileSyncState.Idle,
            ),
        )
    }

    @Test
    fun authScreen_whileProfileIdleOrLoading_neverRoutesToHome() {
        val route = ZunoRoute.Auth(AuthMode.SignUp)
        assertNull(resolveAuthGateRoute(route, splashDone = true, authState = signedIn, profileSync = ProfileSyncState.Idle))
        assertNull(resolveAuthGateRoute(route, splashDone = true, authState = signedIn, profileSync = ProfileSyncState.Loading))
        assertNull(resolveAuthGateRoute(route, splashDone = true, authState = signedIn, profileSync = ProfileSyncState.Failed("timeout")))
    }

    @Test
    fun authScreen_whenOnboardingIncomplete_routesDirectlyToOnboardingSetup() {
        val route = ZunoRoute.Auth(AuthMode.SignUp)
        val result = resolveAuthGateRoute(
            currentRoute = route,
            splashDone = true,
            authState = signedIn,
            profileSync = ProfileSyncState.Ready(profile(onboardingCompleted = false)),
        )
        assertEquals(ZunoRoute.OnboardingSetup, result)
    }

    @Test
    fun authScreen_whenOnboardingCompleted_routesToHome() {
        val route = ZunoRoute.Auth(AuthMode.SignIn)
        val result = resolveAuthGateRoute(
            currentRoute = route,
            splashDone = true,
            authState = signedIn,
            profileSync = ProfileSyncState.Ready(profile(onboardingCompleted = true)),
        )
        assertEquals(ZunoRoute.Home, result)
    }

    @Test
    fun splashScreen_waitsForSplashDoneAndProfileReady_thenRoutesToOnboardingSetup() {
        val incomplete = ProfileSyncState.Ready(profile(onboardingCompleted = false))
        assertNull(resolveAuthGateRoute(ZunoRoute.Splash, splashDone = false, authState = signedIn, profileSync = incomplete))
        assertNull(resolveAuthGateRoute(ZunoRoute.Splash, splashDone = true, authState = signedIn, profileSync = ProfileSyncState.Loading))
        assertEquals(
            ZunoRoute.OnboardingSetup,
            resolveAuthGateRoute(ZunoRoute.Splash, splashDone = true, authState = signedIn, profileSync = incomplete),
        )
    }

    @Test
    fun staleHomeRoute_whenOnboardingIncomplete_redirectsBackToOnboardingSetup() {
        val incomplete = ProfileSyncState.Ready(profile(onboardingCompleted = false))
        assertEquals(
            ZunoRoute.OnboardingSetup,
            resolveAuthGateRoute(ZunoRoute.Home, splashDone = true, authState = signedIn, profileSync = incomplete),
        )
    }

    @Test
    fun alreadyOnOnboardingSetup_returnsNull() {
        val incomplete = ProfileSyncState.Ready(profile(onboardingCompleted = false))
        assertNull(
            resolveAuthGateRoute(ZunoRoute.OnboardingSetup, splashDone = true, authState = signedIn, profileSync = incomplete),
        )
    }
}
