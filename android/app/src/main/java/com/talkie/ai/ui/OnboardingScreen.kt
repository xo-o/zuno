package com.talkie.ai.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.ui.components.ONBOARDING_INTRO_END_SECONDS
import com.talkie.ai.ui.components.ONBOARDING_SCENE6_START_SECONDS
import com.talkie.ai.ui.components.ONBOARDING_TOTAL_DURATION_SECONDS
import com.talkie.ai.ui.components.OnboardingIntroMotionCanvas
import com.talkie.ai.ui.components.computeScene6RevealState
import com.talkie.ai.ui.theme.ButtonPrimaryBg
import com.talkie.ai.ui.theme.ButtonPrimaryText
import com.talkie.ai.ui.theme.CanvasBg
import com.talkie.ai.ui.theme.InterFontFamily
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextBadge
import com.talkie.ai.ui.theme.TextMuted
import com.talkie.ai.ui.theme.TextOnPrimary
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.TextSecondary
import kotlin.math.min

private val ButtonPillRadius = 100.dp // rounded-[var(--radius-pill,100px)] from Figma node 352:808
private val TextLegalMuted = Color(0xFF808080)

private val NoFontPaddingStyle = TextStyle(
    fontFamily = InterFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * Onboarding screen matching Figma node 354:872 (`Onboarding`).
 *
 * Design Structure:
 * - Canvas Background: `#171717` (`var(--color-bg-canvas, #171717)`)
 * - Hero Content (`354:882` + `354:890`):
 *   - Brand Mark (`354:890`): `Interlocking symbol — white vector` (122×122px @3x = `40.67.dp`)
 *   - Title (`354:883`): "Zuno", Inter SemiBold, 68px @3x = `22.67.sp`, `#F9F9F9`
 *   - Tagline (`354:884`): "Your quiet space to master any language.", Inter Regular, 40px @3x = `13.33.sp`, `#969696`
 * - Sign In Actions (`354:885`):
 *   - "Get started" button (`354:887`):
 *     Fill `#F9F9F9`, pill shape (`CircleShape` / `100.dp`), height 140px @3x = `46.67.dp`,
 *     text Inter SemiBold 44px @3x = `14.67.sp`, `#161616` (`TextOnPrimary`).
 *   - Terms and Privacy (`354:889`):
 *     "By continuing, you agree to our Terms and Privacy Policy."
 *     Inter Regular 30px @3x = `10.sp` `#808080`,
 *     "Terms" & "Privacy Policy" in Inter Medium `#BEBEBE`.
 */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit = {},
    onContinueWithGoogle: () -> Unit = onGetStarted,
    onContinueWithEmail: () -> Unit = onGetStarted,
    onExistingAccount: () -> Unit = onGetStarted,
    onTermsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    playIntroAnimation: Boolean = true,
    initialTimeSeconds: Float? = null,
    modifier: Modifier = Modifier
) {
    val isInspection = LocalInspectionMode.current
    val animatorsEnabled = remember(isInspection) {
        !isInspection && ValueAnimator.areAnimatorsEnabled()
    }

    var introCompleted by rememberSaveable { mutableStateOf(!playIntroAnimation) }
    var elapsedSeconds by remember(playIntroAnimation, initialTimeSeconds, isInspection, animatorsEnabled) {
        mutableFloatStateOf(
            when {
                initialTimeSeconds != null -> initialTimeSeconds
                !playIntroAnimation || introCompleted || !animatorsEnabled -> ONBOARDING_TOTAL_DURATION_SECONDS
                else -> 0f
            }
        )
    }

    LaunchedEffect(playIntroAnimation, introCompleted, animatorsEnabled, initialTimeSeconds) {
        if (!animatorsEnabled || initialTimeSeconds != null || !playIntroAnimation || introCompleted) {
            return@LaunchedEffect
        }
        var lastFrameNanos = -1L
        while (elapsedSeconds < ONBOARDING_TOTAL_DURATION_SECONDS) {
            withFrameNanos { nowNanos ->
                val dt = if (lastFrameNanos < 0L) {
                    0f
                } else {
                    min((nowNanos - lastFrameNanos) / 1_000_000_000f, 0.05f)
                }
                lastFrameNanos = nowNanos
                elapsedSeconds = min(elapsedSeconds + dt, ONBOARDING_TOTAL_DURATION_SECONDS)
            }
        }
        introCompleted = true
    }

    val showScene6 by remember {
        derivedStateOf { elapsedSeconds >= ONBOARDING_SCENE6_START_SECONDS }
    }
    val showIntroOverlay by remember {
        derivedStateOf { elapsedSeconds <= ONBOARDING_INTRO_END_SECONDS }
    }
    val buttonsInteractive by remember {
        derivedStateOf { elapsedSeconds >= ONBOARDING_SCENE6_START_SECONDS + 0.50f }
    }

    val buttonAction = remember(onGetStarted, onContinueWithGoogle) {
        if (onGetStarted != {}) onGetStarted else onContinueWithGoogle
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
    ) {
        // Landing Onboarding Screen (Figma node 352:793)
        if (showScene6) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top spacing matching Figma node 352:793 (`y: 1015` in 2770 frame ≈ 36.6%)
                Spacer(modifier = Modifier.weight(0.98f))

                // Hero Content (Figma nodes 352:803 + 352:811)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            val s6 = computeScene6RevealState(elapsedSeconds)
                            val scale = if (elapsedSeconds >= ONBOARDING_SCENE6_START_SECONDS + 0.64f) 1f else s6.orbScale
                            scaleX = scale
                            scaleY = scale
                            translationY = s6.orbTranslationYDp.dp.toPx()
                            alpha = s6.orbAlpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Brand Mark: Interlocking symbol (Figma node 354:890: 122×122 @3x = 40.67.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.67.dp)
                    ) {
                        ZunoLogo(
                            size = 40.67.dp,
                            tint = TextPrimary
                        )
                    }

                    // Gap between Brand Mark and Copy: 62px @3x = 20.67.dp
                    Spacer(modifier = Modifier.height(20.67.dp))

                    // Brand Title (Lexend SemiBold 24.sp, lineHeight 30.sp)
                    Text(
                        text = "Zuno",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.36).sp,
                        lineHeight = 30.sp,
                        style = NoFontPaddingStyle
                    )

                    // Gap between Title and Tagline
                    Spacer(modifier = Modifier.height(8.dp))

                    // Tagline (Lexend Regular 14.sp, lineHeight 20.sp)
                    Text(
                        text = "Your quiet space to master any language.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                        style = NoFontPaddingStyle
                    )
                }

                // Spacing between Hero and Actions (Figma node 352:806 top: 2360, hero bottom: 1328 -> 1032px @3x)
                Spacer(modifier = Modifier.weight(1.0f))

                // Sign in actions (Figma node 352:806)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 34.67.dp)
                        .graphicsLayer {
                            val s6 = computeScene6RevealState(elapsedSeconds)
                            translationY = s6.buttonTranslationYDp.dp.toPx()
                            alpha = s6.buttonAlpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // "Get started" Button (height 52.dp, fill ButtonPrimaryBg, radius-pill)
                    val getStartedInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .semantics {
                                this.contentDescription = "Get started"
                                this.role = Role.Button
                            }
                            .clip(CircleShape)
                            .background(ButtonPrimaryBg, CircleShape)
                            .clickable(
                                enabled = buttonsInteractive,
                                interactionSource = getStartedInteractionSource,
                                indication = ripple(color = Color.Black.copy(alpha = 0.12f)),
                                onClick = buttonAction
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Get started",
                            color = ButtonPrimaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.15).sp,
                            style = NoFontPaddingStyle
                        )
                    }

                    // Gap between button and Terms text
                    Spacer(modifier = Modifier.height(16.dp))

                    // Terms and Privacy Legal Text
                    val legalAnnotatedString = remember(onTermsClick, onPrivacyPolicyClick) {
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append("By continuing, you agree to our ")
                            }
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "terms",
                                    linkInteractionListener = { onTermsClick() }
                                )
                            ) {
                                withStyle(
                                    SpanStyle(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                ) {
                                    append("Terms")
                                }
                            }
                            withStyle(
                                SpanStyle(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(" and ")
                            }
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "privacy",
                                    linkInteractionListener = { onPrivacyPolicyClick() }
                                )
                            ) {
                                withStyle(
                                    SpanStyle(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                ) {
                                    append("Privacy Policy")
                                }
                            }
                            withStyle(
                                SpanStyle(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(".")
                            }
                        }
                    }

                    Text(
                        text = legalAnnotatedString,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        letterSpacing = (-0.05).sp,
                        textAlign = TextAlign.Center,
                        style = NoFontPaddingStyle,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )
                }

                // Bottom padding above system navigation bar
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // Scenes 1–5: Intro Motion Overlay (0.00s -> 6.0167s, overlapping Scene 6 from 5.88s -> 6.0167s)
        if (showIntroOverlay) {
            OnboardingIntroMotionCanvas(
                timeSeconds = { elapsedSeconds },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(
    name = "Onboarding Screen · Get Started",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun OnboardingScreenPreview() {
    TalkieTheme {
        var currentScreen by remember { mutableStateOf("onboarding") }
        when (currentScreen) {
            "onboarding" -> OnboardingScreen(
                onGetStarted = { currentScreen = "signin" },
                playIntroAnimation = false
            )
            "signin" -> SignInScreen(
                onBack = { currentScreen = "onboarding" },
                onSignIn = { currentScreen = "welcome" },
                onNavigateToSignUp = { currentScreen = "signup" },
                onContinueWithGoogle = { currentScreen = "welcome" }
            )
            "signup" -> SignUpScreen(
                onBack = { currentScreen = "signin" },
                onCreateAccount = { currentScreen = "welcome" },
                onNavigateToSignIn = { currentScreen = "signin" },
                onContinueWithGoogle = { currentScreen = "welcome" }
            )
            "welcome" -> WelcomeScreen(
                onStartVoice = { currentScreen = "voice" },
                onOpenConversation = { currentScreen = "conversation" }
            )
            "conversation" -> ConversationScreen(
                onBack = { currentScreen = "welcome" },
                onStartVoice = { currentScreen = "voice" }
            )
            "voice" -> VoiceScreen(
                onBack = { currentScreen = "welcome" },
                onClose = { currentScreen = "welcome" },
                onVoiceEffectsClick = { currentScreen = "conversation" }
            )
        }
    }
}

@Preview(
    name = "Intro Scene 2 · Rings & PlasmaOrb",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun OnboardingIntroScene2Preview() {
    TalkieTheme {
        OnboardingScreen(
            onGetStarted = {},
            initialTimeSeconds = 2.1f
        )
    }
}

@Preview(
    name = "Intro Scene 3 · Say anything",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun OnboardingIntroScene3Preview() {
    TalkieTheme {
        OnboardingScreen(
            onGetStarted = {},
            initialTimeSeconds = 3.1f
        )
    }
}
