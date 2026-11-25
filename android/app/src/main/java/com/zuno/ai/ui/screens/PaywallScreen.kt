package com.zuno.ai.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoLogoMark
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle

/**
 * `11 · Paywall` (`2365:8836` Paywall · 1 · Plans).
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    onStartTrial: (yearly: Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onRestorePurchases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var yearlySelected by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = ZunoSpace.s12),
            contentAlignment = Alignment.CenterStart,
        ) {
            ZunoIconButton(
                icon = R.drawable.zuno_ic_t_x,
                onClick = onClose,
                size = ZunoIconButtonSize.Large,
                tint = ZunoColors.IconPrimary,
                contentDescription = "Close",
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10)) {
                ZunoLogoMark(52.dp)
                Spacer(Modifier.height(ZunoSpace.s2))
                Text(
                    text = "Zuno Pro",
                    style = ZunoType.Display,
                    color = ZunoColors.TextPrimary,
                )
                Text(
                    text = "Unlimited voice practice and every feature, in all 30+ languages.",
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14)) {
                PaywallFeatureRow(
                    icon = R.drawable.zuno_ic_t_audio_lines,
                    title = "Unlimited AI tutor video calls",
                    subtitle = "Free plan: 10 minutes a day",
                )
                PaywallFeatureRow(
                    icon = R.drawable.zuno_ic_book_open,
                    title = "Unlimited Topics & Roleplays",
                    subtitle = "Build as many lessons as you like",
                )
                PaywallFeatureRow(
                    icon = R.drawable.zuno_ic_camera,
                    title = "Learn from photos, PDFs & links",
                    subtitle = "Turn real material into practice",
                )
                PaywallFeatureRow(
                    icon = R.drawable.zuno_ic_trending_up,
                    title = "Level checks & detailed feedback",
                    subtitle = "See exactly how you’re improving",
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ZunoSpace.s8),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl)),
                ) {
                    PaywallPlanRow(
                        icon = R.drawable.zuno_ic_t_calendar,
                        title = "Yearly · $59.88",
                        subtitle = "$4.99/month · 7-day free trial",
                        selected = yearlySelected,
                        onClick = { yearlySelected = true },
                    )
                    ZunoGlassHairline()
                    PaywallPlanRow(
                        icon = R.drawable.zuno_ic_t_repeat,
                        title = "Monthly · $9.99",
                        subtitle = "Billed every month · cancel anytime",
                        selected = !yearlySelected,
                        onClick = { yearlySelected = false },
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14).dp, y = (-11).dp)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceInverse)
                        .padding(horizontal = ZunoSpace.s10, vertical = 4.dp),
                ) {
                    Text(
                        text = "Save 50%",
                        style = ZunoType.FootnoteStrong,
                        color = ZunoColors.TextOnInverse,
                    )
                }
            }
            Spacer(Modifier.height(ZunoSpace.s4))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s10, bottom = ZunoSpace.s16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        ) {
            ZunoButton(
                label = if (yearlySelected) "Start 7-day free trial" else "Continue with Monthly",
                onClick = { onStartTrial(yearlySelected) },
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (yearlySelected) {
                    "Free for 7 days, then $59.88/year. Renews automatically until cancelled. Cancel anytime in Google Play at least 24 hours before renewal."
                } else {
                    "$9.99/month. Renews automatically until cancelled. Cancel anytime in Google Play at least 24 hours before renewal."
                },
                style = ZunoType.FootnoteRegular,
                color = ZunoColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Terms",
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenTerms),
                )
                Text(
                    text = "Privacy Policy",
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenPrivacy),
                )
                Text(
                    text = "Restore purchases",
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onRestorePurchases),
                )
            }
        }
    }
}

@Composable
private fun PaywallFeatureRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.md)),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 18.dp, tint = ZunoColors.IconPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
    }
}

@Composable
private fun PaywallPlanRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(if (selected) Modifier.zunoGlassElevated(CircleShape) else Modifier.zunoGlassSubtle(CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 16.dp, tint = ZunoColors.IconSecondary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        ) {
            Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
        if (selected) {
            ZunoIcon(R.drawable.zuno_ic_t_check, 18.dp, tint = ZunoColors.IconPrimary)
        }
    }
}

/**
 * `11 · Paywall · 2 · Welcome to Pro` (`2365:8964`).
 */
@Composable
fun WelcomeToProScreen(
    onStartPractising: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = ZunoSpace.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Box(
                modifier = Modifier.size(60.dp).zunoGlassSubtle(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_sparkles, 24.dp, tint = ZunoColors.IconPrimary)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                Text(
                    text = stringResource(R.string.paywall_welcome_title),
                    style = ZunoType.TitleLarge,
                    color = ZunoColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.paywall_welcome_body),
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                    .padding(ZunoSpace.s16),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
            ) {
                WelcomePerkRow(
                    title = stringResource(R.string.paywall_welcome_perk_1_title),
                    subtitle = stringResource(R.string.paywall_welcome_perk_1_sub),
                )
                WelcomePerkRow(
                    title = stringResource(R.string.paywall_welcome_perk_2_title),
                    subtitle = stringResource(R.string.paywall_welcome_perk_2_sub),
                )
                WelcomePerkRow(
                    title = stringResource(R.string.paywall_welcome_perk_3_title),
                    subtitle = stringResource(R.string.paywall_welcome_perk_3_sub),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            ZunoButton(
                label = stringResource(R.string.paywall_welcome_start_btn),
                onClick = onStartPractising,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
            ZunoButton(
                label = stringResource(R.string.paywall_welcome_manage_btn),
                onClick = onManageSubscription,
                variant = ZunoButtonVariant.Ghost,
                size = ZunoButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WelcomePerkRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).zunoGlassSubtle(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(R.drawable.zuno_ic_t_check, 15.dp, tint = ZunoColors.IconPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        }
    }
}
