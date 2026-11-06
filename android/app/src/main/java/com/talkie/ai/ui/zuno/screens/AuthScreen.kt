package com.talkie.ai.ui.zuno.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.talkie.ai.R
import com.talkie.ai.ui.zuno.components.ZunoBackTopBar
import com.talkie.ai.ui.zuno.components.ZunoButton
import com.talkie.ai.ui.zuno.components.ZunoButtonVariant
import com.talkie.ai.ui.zuno.components.ZunoDivider
import com.talkie.ai.ui.zuno.components.ZunoTextField
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType

enum class AuthMode(
    val title: String,
    val subtitle: String,
    val primaryLabel: String,
    val footerPrompt: String,
    val footerAction: String,
) {
    SignIn(
        title = "Sign in",
        subtitle = "Welcome back. Pick up where you left off.",
        primaryLabel = "Sign in",
        footerPrompt = "Don’t have an account?",
        footerAction = "Sign up",
    ),
    SignUp(
        title = "Create your account",
        subtitle = "Start your quiet space to master any language.",
        primaryLabel = "Create account",
        footerPrompt = "Already have an account?",
        footerAction = "Sign in",
    ),
}

/** 3 · Sign In and 4 · Sign Up share one layout. */
@Composable
fun AuthScreen(
    mode: AuthMode,
    onBack: () -> Unit,
    onSubmit: (email: String) -> Unit,
    onContinueWithGoogle: () -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    linkSentTo: String? = null,
    errorMessage: String? = null,
    onUseDifferentEmail: () -> Unit = {},
) {
    if (linkSentTo != null) {
        MagicLinkSent(
            email = linkSentTo,
            isBusy = isBusy,
            errorMessage = errorMessage,
            onBack = onUseDifferentEmail,
            onResend = { onSubmit(linkSentTo) },
            onUseDifferentEmail = onUseDifferentEmail,
            modifier = modifier,
        )
        return
    }

    var email by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoBackTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s32),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Text(mode.title, style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
                Text(mode.subtitle, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                ZunoTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email address",
                    placeholder = "name@example.com",
                    helper = "We'll email you a sign-in link — no password needed.",
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onSubmit(email.trim())
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { AuthError(it) }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoButton(
                label = if (isBusy) "Please wait…" else mode.primaryLabel,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit(email.trim())
                },
                enabled = !isBusy && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoDivider(label = "or")
            ZunoButton(
                label = "Continue with Google",
                onClick = onContinueWithGoogle,
                variant = ZunoButtonVariant.Secondary,
                enabled = !isBusy,
                leadingIcon = R.drawable.zuno_ic_google,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(ZunoSpace.s4))
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mode.footerPrompt, style = ZunoType.CaptionRegular, color = ZunoColors.TextTertiary)
                Text(
                    mode.footerAction,
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextPrimary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSwitchMode),
                )
            }
        }
    }
}

/** Shown after a magic link was emailed; tapping the link on this phone finishes sign-in. */
@Composable
private fun MagicLinkSent(
    email: String,
    isBusy: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onUseDifferentEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            Text("Check your email", style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
            Text(
                "We sent a sign-in link to $email. Open it on this phone, your laptop, or any browser to continue. This screen will update automatically.",
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
            )
            errorMessage?.let { AuthError(it) }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        ) {
            ZunoButton(
                label = if (isBusy) "Please wait…" else "Resend link",
                onClick = onResend,
                variant = ZunoButtonVariant.Secondary,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoButton(
                label = "Use a different email",
                onClick = onUseDifferentEmail,
                variant = ZunoButtonVariant.Ghost,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuthError(message: String) {
    Text(message, style = ZunoType.CaptionRegular, color = ZunoColors.TextSecondary)
}
