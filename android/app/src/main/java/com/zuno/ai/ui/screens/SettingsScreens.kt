package com.zuno.ai.ui.screens

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.zuno.ai.R
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.DailyGoals
import com.zuno.ai.data.Focuses
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.Levels
import com.zuno.ai.data.NotificationPrefs
import com.zuno.ai.data.Preferences
import com.zuno.ai.data.Profile
import com.zuno.ai.data.TeachingStyles
import com.zuno.ai.data.TutorVoices
import com.zuno.ai.ui.components.ZunoAvatar
import com.zuno.ai.ui.components.ZunoBackTitleTopBar
import com.zuno.ai.ui.components.ZunoDialog
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoIconButton
import com.zuno.ai.ui.components.ZunoIconButtonSize
import com.zuno.ai.ui.components.ZunoInputDialog
import com.zuno.ai.ui.components.ZunoSheet
import com.zuno.ai.ui.components.ZunoSpinner
import com.zuno.ai.ui.components.ZunoUpgradeBanner
import com.zuno.ai.ui.theme.Lexend
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import kotlin.math.roundToInt

// ── Shared Settings Components (Form Row, Card Group, Switch) ────────────────

/**
 * Rounded 20dp card group (`bg-surface-default`, `border-subtle`) with an optional section label above it.
 */
@Composable
fun ZunoSettingsGroup(
    modifier: Modifier = Modifier,
    sectionLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        if (sectionLabel != null) {
            Text(
                text = sectionLabel,
                style = ZunoType.LabelMedium,
                color = ZunoColors.TextSecondary,
                modifier = Modifier.padding(start = ZunoSpace.s4, top = ZunoSpace.s8),
            )
        }
        val shape = RoundedCornerShape(ZunoRadius.xl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(ZunoColors.SurfaceDefault)
                .border(1.dp, ZunoColors.BorderSubtle, shape),
            content = content,
        )
    }
}

/**
 * Figma `Form Row` (node 2120:1859): 52dp min height, 16dp horizontal / 10dp vertical padding,
 * optional 18dp leading icon, Body/Medium title + optional 12sp subtitle, and trailing value/chevron/switch/check.
 */
@Composable
fun ZunoSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    checked: Boolean? = null,
    selected: Boolean = false,
    muted: Boolean = false,
    showDivider: Boolean = false,
    /** Custom leading content instead of [icon] (e.g. a tinted glyph). */
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ZunoColors.BorderSubtle)
            )
        }
        val rowModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = if (checked != null) Role.Switch else Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s10)

        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
            } else if (icon != null) {
                ZunoIcon(icon = icon, size = 18.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
            ) {
                Text(
                    text = title,
                    style = ZunoType.BodyMedium,
                    color = if (muted) ZunoColors.TextTertiary else ZunoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    maxLines = 1,
                )
            }
            if (trailing != null) {
                trailing()
            } else if (checked != null) {
                ZunoSwitch(checked = checked, onCheckedChange = { if (enabled) onClick?.invoke() })
            } else if (selected) {
                ZunoIcon(icon = R.drawable.zuno_ic_check_20, size = 18.dp)
            } else if (showChevron) {
                ZunoIcon(icon = R.drawable.zuno_ic_chevron_right_muted, size = 18.dp)
            }
        }
    }
}

/**
 * Figma `Switch` (node 2102:1652): 48×28dp pill track with 20dp circular knob.
 * On: `SurfaceInverse` (`rgba(255,255,255,0.92)`) track, `#121212` knob at x=34.
 * Off: `rgba(255,255,255,0.14)` track, `rgba(255,255,255,0.44)` knob at x=14.
 */
@Composable
fun ZunoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "zunoSwitchThumb",
    )
    val trackColor = if (checked) ZunoColors.SurfaceInverse else Color(0x24FFFFFF)
    val thumbColor = if (checked) ZunoColors.IconOnInverse else ZunoColors.TextTertiary

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(role = Role.Switch, onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

// ── 1 & 2 · Settings Overview + Smooth Scrolled Profile Header ──────────────

private val HeaderHeight = 64.dp
private val HeroProfileHeight = 200.dp
private val CollapseRange = 196.dp

/**
 * Figma `Settings · 1 · Overview` (2148:4711) & `Settings · 2 · Scrolled` (2148:4940).
 * Scrolling smoothly animates the centered 80dp Avatar, Name, and Plan pill up into the 64dp Top Bar
 * next to the Back button as a compact 36dp Avatar + two-line Name / Free plan header.
 */
@Composable
fun SettingsOverviewScreen(
    profile: Profile?,
    appLanguage: AppLanguage,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onAccount: () -> Unit,
    onLearning: () -> Unit,
    onAppLanguage: () -> Unit,
    onPreferences: () -> Unit,
    onNotifications: () -> Unit,
    onAbout: () -> Unit,
    onContactSupport: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val collapseRangePx = with(density) { CollapseRange.toPx() }

    val rawProgress by remember(collapseRangePx) {
        derivedStateOf { (scrollState.value / collapseRangePx).coerceIn(0f, 1f) }
    }
    val progress = FastOutSlowInEasing.transform(rawProgress)

    // Snap smoothly to either expanded (0) or collapsed (collapseRangePx) when the user releases mid-transition.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            val current = scrollState.value
            if (current > 0 && current < collapseRangePx.roundToInt()) {
                // Clamp to the real scroll range: short content can't reach the collapsed offset, and
                // snapping past maxValue would restart this effect forever (swallowing taps).
                val target = (if (rawProgress < 0.5f) 0 else collapseRangePx.roundToInt()).coerceAtMost(scrollState.maxValue)
                if (target != current) scrollState.animateScrollTo(target)
            }
        }
    }

    // Blank while the profile loads, rather than a placeholder name.
    val displayName = profile?.displayName.orEmpty()
    val email = profile?.email.orEmpty()
    val initial = displayName.take(1).uppercase()
    val learningSummary = profile?.detail.orEmpty()
    val freePlanLabel = if (profile == null) "" else stringResource(if (profile.isPro) R.string.settings_pro_plan else R.string.settings_free_plan)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val containerWidth = maxWidth

        // Scrollable Settings content below the 64dp Top Bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = HeaderHeight)
                .verticalScroll(scrollState)
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            // Reserves the 200dp Hero Profile space in the scroll container
            Spacer(Modifier.fillMaxWidth().height(HeroProfileHeight))

            if (profile?.isPro != true) {
                ZunoUpgradeBanner(
                    onClick = onUpgrade,
                    text = stringResource(R.string.drawer_upgrade_banner),
                    action = stringResource(R.string.drawer_upgrade_action),
                )
            }

            // Account · Learning
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_account),
                    icon = R.drawable.zuno_ic_user_circle,
                    showChevron = true,
                    onClick = onAccount,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_learning),
                    icon = R.drawable.zuno_ic_trending_up,
                    value = learningSummary,
                    showChevron = true,
                    showDivider = true,
                    onClick = onLearning,
                )
            }

            // App language · Preferences · Notifications
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_app_language),
                    icon = R.drawable.zuno_ic_languages,
                    value = appLanguage.nativeLabel,
                    showChevron = true,
                    onClick = onAppLanguage,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_preferences),
                    icon = R.drawable.zuno_ic_sliders_menu,
                    showChevron = true,
                    showDivider = true,
                    onClick = onPreferences,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_notifications),
                    icon = R.drawable.zuno_ic_bell_secondary,
                    showChevron = true,
                    showDivider = true,
                    onClick = onNotifications,
                )
            }

            // About · Contact support
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_about),
                    icon = R.drawable.zuno_ic_info,
                    showChevron = true,
                    onClick = onAbout,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_contact_support),
                    icon = R.drawable.zuno_ic_mail_secondary,
                    showChevron = true,
                    showDivider = true,
                    onClick = onContactSupport,
                )
            }

            // Sign out · Delete account
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_sign_out),
                    icon = R.drawable.zuno_ic_log_out,
                    showChevron = true,
                    onClick = onSignOut,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_delete_account),
                    icon = R.drawable.zuno_ic_trash_muted,
                    muted = true,
                    showChevron = true,
                    showDivider = true,
                    onClick = onDeleteAccount,
                )
            }
        }

        // Opaque 64dp Top Bar background so scrolled cards slide cleanly underneath the header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
                .background(ZunoColors.BgCanvas),
        )

        // Pinned Back button (x=12dp, y=10dp) + fading "Settings" large title (x=64dp)
        val titleAlpha = (1f - rawProgress * 2.5f).coerceIn(0f, 1f)
        val titleOffsetY = lerp(0.dp, (-10).dp, progress)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
                .padding(horizontal = ZunoSpace.s12),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZunoIconButton(
                icon = R.drawable.zuno_ic_arrow_left,
                onClick = onBack,
                size = ZunoIconButtonSize.Large,
                contentDescription = stringResource(R.string.common_back),
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = ZunoType.TitleLarge,
                color = ZunoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .offset(y = titleOffsetY)
                    .alpha(titleAlpha),
            )
        }

        // ── Morphing Profile Elements (Avatar, Name, Email, Plan) ─────────────
        // 1. Avatar: 80dp centered at (y = 80dp) -> 36dp in Top Bar at (x = 64dp, y = 14dp)
        val avatarSize = lerp(80.dp, 36.dp, progress)
        val avatarStartX = (containerWidth - 80.dp) / 2f
        val avatarX = lerp(avatarStartX, 64.dp, progress)
        val avatarY = lerp(80.dp, 14.dp, progress)
        val avatarFontSize = lerp(30.91.sp, 15.sp, progress)
        val avatarLineHeight = lerp(43.6.sp, 20.sp, progress)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = avatarX.roundToPx(),
                        y = avatarY.roundToPx(),
                    )
                }
                .size(avatarSize)
                .clip(CircleShape)
                .background(ZunoColors.SurfaceInverse),
            contentAlignment = Alignment.Center,
        ) {
            ZunoAvatar(
                initial = initial,
                imageUrl = profile?.imageUrl,
                size = avatarSize,
                textStyle = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = avatarFontSize, lineHeight = avatarLineHeight),
            )
        }

        // 2. Name: centered at (y = 172dp, 17sp) -> Top Bar at (x = 112dp, y = 13dp, 15sp)
        var nameWidthPx by remember { mutableIntStateOf(0) }
        val nameWidthDp: Dp = with(density) { nameWidthPx.toDp() }
        val nameStartX = ((containerWidth - nameWidthDp) / 2f).coerceAtLeast(16.dp)
        val nameX = lerp(nameStartX, 112.dp, progress)
        val nameY = lerp(172.dp, 13.dp, progress)
        val nameFontSize = lerp(17.sp, 15.sp, progress)
        val nameLineHeight = lerp(24.sp, 22.sp, progress)

        Text(
            text = displayName,
            style = ZunoType.Headline.copy(
                fontSize = nameFontSize,
                lineHeight = nameLineHeight,
            ),
            color = ZunoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .onSizeChanged { nameWidthPx = it.width }
                .offset {
                    IntOffset(
                        x = nameX.roundToPx(),
                        y = nameY.roundToPx(),
                    )
                },
        )

        // 3. Email: centered at (y = 200dp), fades out smoothly on scroll
        val emailAlpha = (1f - rawProgress * 2.5f).coerceIn(0f, 1f)
        if (email.isNotEmpty() && emailAlpha > 0f) {
            var emailWidthPx by remember { mutableIntStateOf(0) }
            val emailWidthDp = with(density) { emailWidthPx.toDp() }
            val emailX = ((containerWidth - emailWidthDp) / 2f).coerceAtLeast(16.dp)
            val emailY = lerp(200.dp, 148.dp, progress)

            Text(
                text = email,
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .onSizeChanged { emailWidthPx = it.width }
                    .offset {
                        IntOffset(
                            x = emailX.roundToPx(),
                            y = emailY.roundToPx(),
                        )
                    }
                    .alpha(emailAlpha),
            )
        }

        // 4. Plan: centered 32dp pill at (y = 232dp) -> Top Bar subtitle at (x = 112dp, y = 35dp)
        // Because the 32dp pill has 12dp horizontal padding and vertically centers the 16dp text (8dp top inset),
        // interpolating the pill box to (x = 112dp - 12dp = 100dp, y = 35dp - 8dp = 27dp) lands the text
        // at exact pixel (x = 112dp, y = 35dp) directly beneath the Name!
        var pillWidthPx by remember { mutableIntStateOf(0) }
        val pillWidthDp = with(density) { pillWidthPx.toDp() }
        val pillStartX = ((containerWidth - pillWidthDp) / 2f).coerceAtLeast(16.dp)
        val pillX = lerp(pillStartX, 100.dp, progress)
        val pillY = lerp(232.dp, 27.dp, progress)
        val pillBgAlpha = (1f - rawProgress * 2.2f).coerceIn(0f, 1f)
        val planTextColor = lerp(ZunoColors.TextPrimary, ZunoColors.TextSecondary, progress)

        Box(
            modifier = Modifier
                .onSizeChanged { pillWidthPx = it.width }
                .offset {
                    IntOffset(
                        x = pillX.roundToPx(),
                        y = pillY.roundToPx(),
                    )
                }
                .height(32.dp)
                .clip(CircleShape)
                .background(ZunoColors.SurfaceStrong.copy(alpha = ZunoColors.SurfaceStrong.alpha * pillBgAlpha))
                .border(
                    width = 1.dp,
                    color = ZunoColors.BorderDefault.copy(alpha = ZunoColors.BorderDefault.alpha * pillBgAlpha),
                    shape = CircleShape,
                )
                .padding(horizontal = ZunoSpace.s12),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = freePlanLabel,
                style = if (progress < 0.5f) ZunoType.FootnoteStrong else ZunoType.FootnoteRegular,
                color = planTextColor,
                maxLines = 1,
            )
        }
    }
}

// ── Option sheet (single choice) ──────────────────────────────────────────────

/** Single-select bottom sheet used for voice, teaching style, accent, daily goal and focus. */
@Composable
fun <T> ZunoOptionSheet(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: (@Composable (T) -> String?)? = null,
) {
    ZunoSheet(onDismiss = onDismiss, title = title) {
        ZunoSettingsGroup(modifier = Modifier.padding(horizontal = ZunoSpace.s12)) {
            options.forEachIndexed { index, option ->
                ZunoSettingsRow(
                    title = label(option),
                    subtitle = subtitle?.invoke(option),
                    selected = option == selected,
                    showDivider = index > 0,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun voiceLabel(id: String) = stringResource(
    when (id) {
        "calm" -> R.string.settings_pref_voice_calm
        "energetic" -> R.string.settings_pref_voice_energetic
        else -> R.string.settings_pref_voice_warm
    },
)

@Composable
private fun styleLabel(id: String) = stringResource(
    when (id) {
        "gentle" -> R.string.settings_pref_style_gentle
        "immersive" -> R.string.settings_pref_style_immersive
        else -> R.string.settings_pref_style_balanced
    },
)

@Composable
private fun focusLabel(id: String) = stringResource(
    when (id) {
        "travel" -> R.string.settings_learn_focus_travel
        "work" -> R.string.settings_learn_focus_work
        "exams" -> R.string.settings_learn_focus_exams
        "culture" -> R.string.settings_learn_focus_culture
        else -> R.string.settings_learn_focus_conversation
    },
)

// ── 3 · Settings · Preferences (2151:5353) ──────────────────────────────────

@Composable
fun SettingsPreferencesScreen(
    preferences: Preferences,
    targetLanguage: LearningLanguage,
    appLanguage: AppLanguage,
    onChange: ((Preferences) -> Preferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val targetLangName = targetLanguage.displayName(appLanguage)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.settings_preferences), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_voice),
                    icon = R.drawable.zuno_ic_volume_secondary,
                    value = voiceLabel(preferences.tutorVoice),
                    showChevron = true,
                    onClick = { picker = "voice" },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_teaching_style),
                    icon = R.drawable.zuno_ic_book_open_secondary,
                    value = styleLabel(preferences.teachingStyle),
                    showChevron = true,
                    showDivider = true,
                    onClick = { picker = "style" },
                )
            }

            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_strict_mode),
                    subtitle = stringResource(R.string.settings_pref_strict_mode_sub),
                    icon = R.drawable.zuno_ic_alert_circle_secondary,
                    checked = preferences.strictMode,
                    onClick = { onChange { it.copy(strictMode = !it.strictMode) } },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_hands_free),
                    subtitle = stringResource(R.string.settings_pref_hands_free_sub),
                    icon = R.drawable.zuno_ic_mic_secondary,
                    checked = preferences.handsFree,
                    showDivider = true,
                    onClick = { onChange { it.copy(handsFree = !it.handsFree) } },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_suggest),
                    subtitle = stringResource(R.string.settings_pref_suggest_sub),
                    icon = R.drawable.zuno_ic_sparkles_secondary,
                    checked = preferences.suggestReplies,
                    showDivider = true,
                    onClick = { onChange { it.copy(suggestReplies = !it.suggestReplies) } },
                )
            }

            ZunoSettingsGroup(sectionLabel = targetLangName) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_show_translations),
                    icon = R.drawable.zuno_ic_captions_secondary,
                    checked = preferences.showTranslations,
                    onClick = { onChange { it.copy(showTranslations = !it.showTranslations) } },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_pref_teach_in_lang, targetLangName),
                    subtitle = stringResource(R.string.settings_pref_teach_in_lang_sub, targetLangName, stringResource(appLanguage.subtitleRes)),
                    icon = R.drawable.zuno_ic_languages,
                    checked = preferences.teachInTarget,
                    showDivider = true,
                    onClick = { onChange { it.copy(teachInTarget = !it.teachInTarget) } },
                )
            }
        }
    }

    when (picker) {
        "voice" -> ZunoOptionSheet(
            title = stringResource(R.string.settings_pref_voice),
            options = TutorVoices,
            selected = preferences.tutorVoice,
            label = { voiceLabel(it) },
            onSelect = { voice -> picker = null; onChange { it.copy(tutorVoice = voice) } },
            onDismiss = { picker = null },
        )
        "style" -> ZunoOptionSheet(
            title = stringResource(R.string.settings_pref_teaching_style),
            options = TeachingStyles,
            selected = preferences.teachingStyle,
            label = { styleLabel(it) },
            subtitle = {
                stringResource(
                    when (it) {
                        "gentle" -> R.string.settings_pref_style_gentle_sub
                        "immersive" -> R.string.settings_pref_style_immersive_sub
                        else -> R.string.settings_pref_style_balanced_sub
                    },
                )
            },
            onSelect = { style -> picker = null; onChange { it.copy(teachingStyle = style) } },
            onDismiss = { picker = null },
        )
    }
}

/** "Spanish" in the app language (falls back to the English name). */
@Composable
fun LearningLanguage.displayName(appLanguage: AppLanguage): String {
    val locale = java.util.Locale.forLanguageTag(appLanguage.bcp47)
    return java.util.Locale.forLanguageTag(code).getDisplayLanguage(locale)
        .takeIf { it.isNotBlank() && it != code }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        ?: englishName
}

// ── 4 · Settings · App language (2153:5078) ─────────────────────────────────

@Composable
fun SettingsAppLanguageScreen(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(
            title = stringResource(R.string.settings_app_language),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            Text(
                text = stringResource(R.string.settings_lang_intro),
                style = ZunoType.BodySmall,
                color = ZunoColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZunoSpace.s4),
            )

            ZunoSettingsGroup {
                AppLanguage.entries.forEachIndexed { index, language ->
                    ZunoSettingsRow(
                        title = language.nativeLabel,
                        subtitle = stringResource(language.subtitleRes),
                        selected = language == selectedLanguage,
                        showDivider = index > 0,
                        onClick = { onSelectLanguage(language) },
                    )
                }
            }
        }
    }
}

// ── 5 · Settings · Learning (2153:5222, 2177:9075) ──────────────────────────

@Composable
fun SettingsLearningScreen(
    profile: Profile?,
    appLanguage: AppLanguage,
    onOpenLanguages: () -> Unit,
    onAccentChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onFindLevel: () -> Unit,
    onPreferencesChange: ((Preferences) -> Preferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = profile?.language ?: LearningLanguages.Spanish
    val level = profile?.level ?: "B1"
    val preferences = profile?.preferences ?: Preferences()
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val languageName = language.displayName(appLanguage)

    val (levelTitle, levelDescription) = when (level) {
        "A1" -> stringResource(R.string.settings_learn_level_a1_title) to stringResource(R.string.settings_learn_level_a1_desc)
        "A2" -> stringResource(R.string.settings_learn_level_a2_title) to stringResource(R.string.settings_learn_level_a2_desc)
        "B2" -> stringResource(R.string.settings_learn_level_b2_title) to stringResource(R.string.settings_learn_level_b2_desc)
        "C1" -> stringResource(R.string.settings_learn_level_c1_title) to stringResource(R.string.settings_learn_level_c1_desc)
        "C2" -> stringResource(R.string.settings_learn_level_c2_title) to stringResource(R.string.settings_learn_level_c2_desc)
        else -> stringResource(R.string.settings_learn_level_b1_title) to stringResource(R.string.settings_learn_level_b1_desc)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.settings_learning), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_learn_studying)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_learn_language),
                    icon = R.drawable.zuno_ic_languages,
                    value = languageName,
                    showChevron = true,
                    onClick = onOpenLanguages,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_learn_accent),
                    icon = R.drawable.zuno_ic_waveform,
                    value = language.accentLabel(profile?.accent),
                    showChevron = language.accents.size > 1,
                    showDivider = true,
                    onClick = if (language.accents.size > 1) ({ picker = "accent" }) else null,
                )
            }

            if (profile != null && !profile.levelChecked) {
                // A new language starts with "Find my level" instead of a level.
                ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_learn_your_level)) {
                    ZunoSettingsRow(
                        title = stringResource(R.string.settings_learn_find_level),
                        subtitle = stringResource(R.string.settings_learn_find_level_sub, languageName),
                        icon = R.drawable.zuno_ic_check_circle_secondary,
                        showChevron = true,
                        onClick = onFindLevel,
                    )
                }
            } else {
                ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_learn_your_level)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(ZunoSpace.s16),
                        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Levels.forEach { item ->
                                val isSelected = item == level
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) ZunoColors.SurfaceInverse else ZunoColors.SurfaceDefault)
                                        .then(if (isSelected) Modifier else Modifier.border(1.dp, ZunoColors.BorderDefault, CircleShape))
                                        .clickable(role = Role.RadioButton, onClick = { onLevelChange(item) }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = item,
                                        style = if (isSelected) ZunoType.LabelLarge else ZunoType.LabelMedium,
                                        color = if (isSelected) ZunoColors.TextOnInverse else ZunoColors.TextSecondary,
                                    )
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                            Text(levelTitle, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                            Text(levelDescription, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
                        }
                    }
                    ZunoSettingsRow(
                        title = stringResource(R.string.settings_learn_retake_check),
                        subtitle = stringResource(R.string.settings_learn_retake_check_sub),
                        icon = R.drawable.zuno_ic_check_circle_secondary,
                        showChevron = true,
                        showDivider = true,
                        onClick = onFindLevel,
                    )
                }
            }

            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_learn_goals)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_learn_daily_goal),
                    icon = R.drawable.zuno_ic_clock_secondary,
                    value = stringResource(R.string.settings_learn_daily_goal_min, preferences.dailyGoalMin),
                    showChevron = true,
                    onClick = { picker = "goal" },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_learn_focus),
                    icon = R.drawable.zuno_ic_users_secondary,
                    value = focusLabel(preferences.focus),
                    showChevron = true,
                    showDivider = true,
                    onClick = { picker = "focus" },
                )
            }
        }
    }

    when (picker) {
        "accent" -> ZunoOptionSheet(
            title = stringResource(R.string.settings_learn_accent),
            options = language.accents.map { it.id },
            selected = profile?.accent ?: language.defaultAccent,
            label = { id -> language.accentLabel(id) },
            onSelect = { picker = null; onAccentChange(it) },
            onDismiss = { picker = null },
        )
        "goal" -> ZunoOptionSheet(
            title = stringResource(R.string.settings_learn_daily_goal),
            options = DailyGoals,
            selected = preferences.dailyGoalMin,
            label = { stringResource(R.string.settings_learn_daily_goal_min, it) },
            onSelect = { goal -> picker = null; onPreferencesChange { it.copy(dailyGoalMin = goal) } },
            onDismiss = { picker = null },
        )
        "focus" -> ZunoOptionSheet(
            title = stringResource(R.string.settings_learn_focus),
            options = Focuses,
            selected = preferences.focus,
            label = { focusLabel(it) },
            onSelect = { focus -> picker = null; onPreferencesChange { it.copy(focus = focus) } },
            onDismiss = { picker = null },
        )
    }
}

// ── 5a–5c · Settings · Learning language (2177:6626, 2177:7335, 2177:8045, 2177:8316) ──

@Composable
fun SettingsLearningLanguageScreen(
    languages: List<LearningLanguage>,
    profile: Profile?,
    appLanguage: AppLanguage,
    busy: Boolean,
    onSwitch: (LearningLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var switching by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // The dialog stays open with a spinner until the switch is saved.
    LaunchedEffect(busy) { if (!busy && switching) { switching = false; confirming = null } }
    fun ask(code: String) {
        keyboard?.hide()
        focusManager.clearFocus()
        confirming = code
    }
    val current = profile?.language
    val results = remember(query, languages) { languages.filter { it.matches(query) } }
    val searching = query.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.learning_language_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            if (!searching) {
                Text(
                    stringResource(R.string.learning_language_intro),
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s4),
                )
            }
            ZunoSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.learning_language_search),
            )
            if (searching) {
                Text(
                    pluralStringResource(R.plurals.learning_language_results, results.size, results.size),
                    style = ZunoType.LabelMedium,
                    color = ZunoColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s4),
                )
                if (results.isNotEmpty()) {
                    ZunoSettingsGroup {
                        results.forEachIndexed { index, language ->
                            LanguageRow(language, selected = language.code == current?.code, showDivider = index > 0) {
                                if (language.code != current?.code) ask(language.code)
                            }
                        }
                    }
                }
            } else {
                if (current != null && profile != null) {
                    ZunoSettingsGroup(sectionLabel = stringResource(R.string.learning_language_now)) {
                        ZunoSettingsRow(
                            title = current.nativeName,
                            subtitle = "${current.displayName(appLanguage)} · ${profile.level}",
                            selected = true,
                        )
                    }
                }
                ZunoSettingsGroup(sectionLabel = stringResource(R.string.learning_language_all)) {
                    languages.forEachIndexed { index, language ->
                        LanguageRow(language, selected = language.code == current?.code, showDivider = index > 0) {
                            if (language.code != current?.code) ask(language.code)
                        }
                    }
                }
            }
        }
    }

    val target = confirming?.let { code -> languages.firstOrNull { it.code == code } }
    if (target != null && current != null) {
        val targetName = target.displayName(appLanguage)
        val learnedBefore = profile?.languages?.any { it.language == target.code && it.levelChecked } == true
        ZunoDialog(
            title = stringResource(R.string.learning_language_switch_title, targetName),
            body = stringResource(R.string.learning_language_switch_body, current.displayName(appLanguage)) +
                if (learnedBefore) "" else " " + stringResource(R.string.learning_language_switch_level, targetName),
            confirmLabel = stringResource(R.string.learning_language_switch),
            confirmLoading = switching,
            onConfirm = {
                if (!switching) {
                    switching = true
                    onSwitch(target)
                }
            },
            onDismiss = { if (!switching) confirming = null },
        )
    }
}

@Composable
private fun LanguageRow(language: LearningLanguage, selected: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    ZunoSettingsRow(
        title = language.nativeName,
        subtitle = language.englishName,
        selected = selected,
        showDivider = showDivider,
        onClick = onClick,
    )
}

/** Search field with leading glyph and clear button (Settings · 5b). */
@Composable
fun ZunoSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = ZunoType.BodyMedium.copy(color = ZunoColors.TextPrimary),
        cursorBrush = SolidColor(ZunoColors.TextPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.SurfaceDefault)
                    .border(1.dp, if (focused) ZunoColors.BorderFocus else ZunoColors.BorderDefault, CircleShape)
                    .padding(start = ZunoSpace.s16, end = ZunoSpace.s8),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoIcon(R.drawable.zuno_ic_t_search, 18.dp, tint = ZunoColors.IconSecondary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, style = ZunoType.BodyMedium, color = ZunoColors.TextPlaceholder, maxLines = 1)
                    inner()
                }
                if (query.isNotEmpty()) {
                    ZunoIconButton(
                        icon = R.drawable.zuno_ic_t_x,
                        onClick = { onQueryChange("") },
                        size = ZunoIconButtonSize.Small,
                        contentDescription = stringResource(R.string.common_clear),
                    )
                }
            }
        },
    )
}

// ── 6 · Settings · Account (2153:5368) ──────────────────────────────────────

@Composable
fun SettingsAccountScreen(
    profile: Profile?,
    uploadingPhoto: Boolean,
    onUpdateName: (String) -> Unit,
    onChangePhoto: () -> Unit,
    onPlanClick: () -> Unit,
    onDownloadData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = profile?.displayName.orEmpty()
    val email = profile?.email.orEmpty()
    val initial = displayName.take(1).uppercase()
    var editingName by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.settings_account), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = ZunoSpace.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ZunoAvatar(
                        initial = initial,
                        imageUrl = profile?.imageUrl,
                        size = 72.dp,
                        textStyle = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 27.82.sp, lineHeight = 39.27.sp),
                    )
                    if (uploadingPhoto) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(ZunoColors.BgScrim), contentAlignment = Alignment.Center) {
                            ZunoSpinner(size = 24.dp)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !uploadingPhoto, role = Role.Button, onClick = onChangePhoto)
                        .padding(horizontal = ZunoSpace.s16),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.settings_acc_change_photo), style = ZunoType.LabelSmall, color = ZunoColors.TextPrimary)
                }
            }

            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_acc_profile)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_acc_name),
                    subtitle = displayName,
                    icon = R.drawable.zuno_ic_user_circle,
                    showChevron = true,
                    onClick = { editingName = true },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_acc_email),
                    subtitle = email,
                    icon = R.drawable.zuno_ic_mail_secondary,
                    showDivider = true,
                )
                if (profile?.signedInWithGoogle == true) {
                    ZunoSettingsRow(
                        title = stringResource(R.string.settings_acc_signed_in_google),
                        icon = R.drawable.zuno_ic_google,
                        showDivider = true,
                    )
                }
            }

            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_acc_subscription)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_acc_plan),
                    icon = R.drawable.zuno_ic_sparkles_secondary,
                    value = stringResource(if (profile?.isPro == true) R.string.settings_acc_plan_pro else R.string.settings_acc_plan_free),
                    showChevron = profile?.isPro != true,
                    onClick = if (profile?.isPro == true) null else onPlanClick,
                )
                // Purchases can't be restored until in-app billing exists.
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_acc_restore_purchases),
                    icon = R.drawable.zuno_ic_rotate_ccw_secondary,
                    showDivider = true,
                    enabled = false,
                )
            }

            ZunoSettingsGroup {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_acc_download_data),
                    icon = R.drawable.zuno_ic_file_text_secondary,
                    showChevron = true,
                    onClick = onDownloadData,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_delete_account),
                    icon = R.drawable.zuno_ic_trash_muted,
                    muted = true,
                    showChevron = true,
                    showDivider = true,
                    onClick = onDeleteAccount,
                )
            }
        }
    }

    if (editingName) {
        ZunoInputDialog(
            title = stringResource(R.string.settings_acc_edit_name),
            initialValue = displayName,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = {
                editingName = false
                onUpdateName(it)
            },
            onDismiss = { editingName = false },
        )
    }
}

// ── 7 · Settings · Notifications (2153:5545) ────────────────────────────────

@Composable
fun SettingsNotificationsScreen(
    prefs: NotificationPrefs?,
    onChange: ((NotificationPrefs) -> NotificationPrefs) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formattedReminderTime = prefs?.let { ScheduleFormat.time(context, it.reminderTime) }.orEmpty()

    fun pickReminderTime() {
        val current = ScheduleFormat.parseTime(prefs?.reminderTime ?: "19:00")
        TimePickerDialog(
            context,
            { _, hour, minute -> onChange { it.copy(reminderTime = "%02d:%02d".format(java.util.Locale.ROOT, hour, minute)) } },
            current.hour,
            current.minute,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoBackTitleTopBar(title = stringResource(R.string.settings_notifications), onBack = onBack)
        if (prefs == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ZunoSpinner(size = 24.dp) }
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ZunoSpace.s16, end = ZunoSpace.s16, top = ZunoSpace.s8, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        ) {
            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_notif_practice)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_notif_daily_reminder),
                    subtitle = stringResource(R.string.settings_notif_daily_reminder_sub),
                    icon = R.drawable.zuno_ic_bell_secondary,
                    checked = prefs.dailyReminder,
                    onClick = { onChange { it.copy(dailyReminder = !it.dailyReminder) } },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_notif_reminder_time),
                    icon = R.drawable.zuno_ic_clock_secondary,
                    value = formattedReminderTime,
                    showChevron = true,
                    showDivider = true,
                    enabled = prefs.dailyReminder,
                    onClick = ::pickReminderTime,
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_notif_scheduled),
                    subtitle = stringResource(R.string.settings_notif_scheduled_sub),
                    icon = R.drawable.zuno_ic_calendar_secondary,
                    checked = prefs.scheduledSessions,
                    showDivider = true,
                    onClick = { onChange { it.copy(scheduledSessions = !it.scheduledSessions) } },
                )
            }

            ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_notif_updates)) {
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_notif_progress),
                    icon = R.drawable.zuno_ic_trending_up,
                    checked = prefs.progressMilestones,
                    onClick = { onChange { it.copy(progressMilestones = !it.progressMilestones) } },
                )
                ZunoSettingsRow(
                    title = stringResource(R.string.settings_notif_new_topics),
                    icon = R.drawable.zuno_ic_book_open_secondary,
                    checked = prefs.newContent,
                    showDivider = true,
                    onClick = { onChange { it.copy(newContent = !it.newContent) } },
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                ZunoSettingsGroup(sectionLabel = stringResource(R.string.settings_notif_email)) {
                    ZunoSettingsRow(
                        title = stringResource(R.string.settings_notif_weekly_summary),
                        subtitle = stringResource(R.string.settings_notif_weekly_summary_sub),
                        icon = R.drawable.zuno_ic_mail_secondary,
                        checked = prefs.weeklySummaryEmail,
                        onClick = { onChange { it.copy(weeklySummaryEmail = !it.weeklySummaryEmail) } },
                    )
                    ZunoSettingsRow(
                        title = stringResource(R.string.settings_notif_product_news),
                        icon = R.drawable.zuno_ic_sparkles_secondary,
                        checked = prefs.productNewsEmail,
                        showDivider = true,
                        onClick = { onChange { it.copy(productNewsEmail = !it.productNewsEmail) } },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_notif_footnote),
                    style = ZunoType.FootnoteRegular,
                    color = ZunoColors.TextTertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ZunoRadius.xs))
                        .clickable(role = Role.Button, onClick = onOpenSystemSettings)
                        .padding(horizontal = ZunoSpace.s4, vertical = ZunoSpace.s4),
                )
            }
        }
    }
}
