package com.talkie.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.R
import com.talkie.ai.ui.theme.BorderDivider
import com.talkie.ai.ui.theme.BorderHairline
import com.talkie.ai.ui.theme.ButtonPrimaryBg
import com.talkie.ai.ui.theme.ButtonPrimaryText
import com.talkie.ai.ui.theme.CanvasBg
import com.talkie.ai.ui.theme.ControlBg
import com.talkie.ai.ui.theme.InterFontFamily
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextBadge
import com.talkie.ai.ui.theme.TextMuted
import com.talkie.ai.ui.theme.TextOnPrimary
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.TextSecondary

private val InputBorderColor = BorderHairline
private val ButtonBorderColor = BorderHairline
private val DividerLineColor = BorderDivider
private val TextMutedColor = TextMuted
private val TextPlaceholderColor = TextMuted

private val NoFontPaddingStyle = TextStyle(
    fontFamily = InterFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * Sign In Screen matching Figma node 335:11599 (`SignIn`).
 */
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onSignIn: (email: String) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onContinueWithGoogle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var email by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.67.dp))

            // 1. Top Bar: Back Button + Zuno Brand Mark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (38 x 38 dp)
                val backInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .semantics {
                            this.contentDescription = "Back"
                            this.role = Role.Button
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(ControlBg, RoundedCornerShape(12.dp))
                        .border(width = 0.67.dp, color = ButtonBorderColor, shape = RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = backInteractionSource,
                            indication = ripple(color = Color.White.copy(alpha = 0.12f)),
                            onClick = onBack
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Brand Mark (26.67 x 26.67 dp)
                ZunoCleanBalanceLogo(
                    size = 26.67.dp,
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 2. Header: Title + Subtitle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Sign In",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.45).sp,
                    lineHeight = 30.sp,
                    style = NoFontPaddingStyle
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in to continue your quiet language journey.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.07).sp,
                    lineHeight = 20.sp,
                    style = NoFontPaddingStyle
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 3. Form: Email Address Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Email address",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = NoFontPaddingStyle
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Input Box (52.dp height, corner radius 16.dp)
                val inputShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(inputShape)
                        .background(ControlBg, inputShape)
                        .border(width = 0.67.dp, color = InputBorderColor, shape = inputShape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (email.isEmpty()) {
                        Text(
                            text = "name@example.com",
                            color = TextPlaceholderColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            style = NoFontPaddingStyle
                        )
                    }
                    BasicTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = NoFontPaddingStyle.copy(
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onSignIn(email)
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "We'll email you a sign-in link — no password needed.",
                    color = TextMutedColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    style = NoFontPaddingStyle
                )
            }

            // Pushes actions to the bottom
            Spacer(modifier = Modifier.weight(1f))

            // 4. Actions: Sign in button + Divider + Google button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Sign in Button (52.dp height, full pill)
                val signInInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics {
                            this.contentDescription = "Sign in"
                            this.role = Role.Button
                        }
                        .clip(CircleShape)
                        .background(ButtonPrimaryBg, CircleShape)
                        .clickable(
                            interactionSource = signInInteractionSource,
                            indication = ripple(color = Color.Black.copy(alpha = 0.12f)),
                            onClick = { onSignIn(email) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign in",
                        color = ButtonPrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.15).sp,
                        style = NoFontPaddingStyle
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Divider ("or")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 0.67.dp,
                        color = DividerLineColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "or",
                        color = TextMutedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        style = NoFontPaddingStyle
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 0.67.dp,
                        color = DividerLineColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Secondary Continue with Google Button
                val googleInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics {
                            this.contentDescription = "Continue with Google"
                            this.role = Role.Button
                        }
                        .clip(CircleShape)
                        .background(ControlBg, CircleShape)
                        .border(width = 0.67.dp, color = ButtonBorderColor, shape = CircleShape)
                        .clickable(
                            interactionSource = googleInteractionSource,
                            indication = ripple(color = Color.White.copy(alpha = 0.12f)),
                            onClick = onContinueWithGoogle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google_mono),
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.14).sp,
                            style = NoFontPaddingStyle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 5. Footer Switch: Don't have an account? Sign up
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    color = TextMutedColor,
                    fontSize = 10.67.sp,
                    fontWeight = FontWeight.Normal,
                    style = NoFontPaddingStyle
                )
                Text(
                    text = "Sign up",
                    color = TextBadge,
                    fontSize = 10.67.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = NoFontPaddingStyle,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNavigateToSignUp
                        )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Preview(
    name = "Sign In Screen",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun SignInScreenPreview() {
    TalkieTheme {
        SignInScreen(
            onBack = {},
            onSignIn = {},
            onNavigateToSignUp = {},
            onContinueWithGoogle = {}
        )
    }
}
