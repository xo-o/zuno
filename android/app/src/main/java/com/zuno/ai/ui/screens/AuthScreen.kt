package com.zuno.ai.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.ui.components.ZunoBackTopBar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoDivider
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoTextField
import kotlinx.coroutines.delay
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType

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
                    helper = if (isBusy) "We’ll send a magic link to this address." else null,
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
                label = if (isBusy) "Sending link…" else mode.primaryLabel,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit(email.trim())
                },
                enabled = email.isNotBlank(),
                loading = isBusy,
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
                modifier = Modifier.alpha(if (isBusy) 0.4f else 1f),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mode.footerPrompt, style = ZunoType.CaptionRegular, color = ZunoColors.TextTertiary)
                Text(
                    mode.footerAction,
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextPrimary,
                    modifier = Modifier.clickable(enabled = !isBusy, role = Role.Button, onClick = onSwitchMode),
                )
            }
        }
    }
}

private const val RESEND_COOLDOWN_SECONDS = 30

/** 4c · Check Your Email — the link can be opened on this phone or any browser; this screen polls. */
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
    val context = LocalContext.current
    var cooldown by rememberSaveable(email) { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }
    LaunchedEffect(email, cooldown > 0) {
        while (cooldown > 0) {
            delay(1_000)
            cooldown--
        }
    }

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
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s32),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, ZunoColors.BorderDefault, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.ic_mail, 32.dp)
            }
            Spacer(Modifier.height(ZunoSpace.s24))
            Text("Check your email", style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(ZunoSpace.s8))
            Text("We sent a magic link to", style = ZunoType.BodySmall, color = ZunoColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(ZunoSpace.s8))
            Text(email, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(ZunoSpace.s12))
            Text(
                "Tap the link to sign in. It expires in 15 min.",
                style = ZunoType.CaptionRegular,
                color = ZunoColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
            errorMessage?.let {
                Spacer(Modifier.height(ZunoSpace.s12))
                AuthError(it)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoButton(
                label = "Open email app",
                onClick = { openEmailApp(context) },
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoButton(
                label = if (cooldown > 0) "Resend link in ${cooldown}s" else "Resend link",
                onClick = {
                    onResend()
                    cooldown = RESEND_COOLDOWN_SECONDS
                },
                variant = ZunoButtonVariant.Ghost,
                enabled = cooldown == 0 && !isBusy,
                loading = isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wrong email?", style = ZunoType.CaptionRegular, color = ZunoColors.TextTertiary)
                Text(
                    "Use a different one",
                    style = ZunoType.LabelSmall,
                    color = ZunoColors.TextPrimary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onUseDifferentEmail),
                )
            }
        }
    }
}

private fun openEmailApp(context: Context) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found on this device.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AuthError(message: String) {
    Text(message, style = ZunoType.CaptionRegular, color = ZunoColors.TextSecondary)
}
