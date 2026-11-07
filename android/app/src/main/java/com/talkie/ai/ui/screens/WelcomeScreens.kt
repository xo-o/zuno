package com.talkie.ai.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoLogoMark
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType
import kotlinx.coroutines.delay

/** 1 · Splash — centered 64dp mark. */
@Composable
fun SplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(durationMillis = 500))
        delay(900)
        currentOnFinished()
    }
    Box(
        modifier = modifier.fillMaxSize().background(ZunoColors.BgCanvas),
        contentAlignment = Alignment.Center,
    ) {
        ZunoLogoMark(64.dp, Modifier.graphicsLayer { this.alpha = alpha.value })
    }
}

/** 2 · Onboarding — brand + Get started. */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s40),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ZunoLogoMark(56.dp)
            Spacer(Modifier.height(ZunoSpace.s16))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                Text("Zuno", style = ZunoType.TitleLarge, color = ZunoColors.TextPrimary)
                Text(
                    "Your quiet space to master any language.",
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoButton(label = "Get started", onClick = onGetStarted, modifier = Modifier.fillMaxWidth())
            LegalText(onOpenTerms, onOpenPrivacy)
        }
    }
}

@Composable
private fun LegalText(onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(fontWeight = FontWeight.Medium, color = ZunoColors.TextSecondary)
    )
    val text = buildAnnotatedString {
        append("By continuing, you agree to our ")
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpenTerms() }) { append("Terms") }
        append(" and ")
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpenPrivacy() }) { append("Privacy Policy") }
        append(".")
    }
    Text(
        text = text,
        style = ZunoType.FootnoteRegular,
        color = ZunoColors.TextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
