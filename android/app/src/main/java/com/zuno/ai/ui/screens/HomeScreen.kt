package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.ui.components.ContinueSessionUi
import com.zuno.ai.ui.components.HomeNavTab
import com.zuno.ai.ui.components.ZunoDailyTargetRow
import com.zuno.ai.ui.components.ZunoExploreTopBar
import com.zuno.ai.ui.components.ZunoFloatingBottomNav
import com.zuno.ai.ui.components.ZunoHomeTopBar
import com.zuno.ai.ui.components.ZunoIcon
import com.zuno.ai.ui.components.ZunoLiveNowSection
import com.zuno.ai.ui.components.ZunoPickUpSection
import com.zuno.ai.ui.components.ZunoProfileCard
import com.zuno.ai.ui.components.ZunoUpgradeBanner
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoTheme

/** Greeting copy for Home top bar and composer placeholder. */
data class HomeCopy(
    val shortGreeting: String,
    val languageName: String,
    val placeholder: String,
) {
    companion object {
        fun forLanguage(language: LearningLanguage, firstName: String): HomeCopy = HomeCopy(
            shortGreeting = language.shortGreeting(firstName),
            languageName = language.englishName,
            placeholder = language.placeholder,
        )
    }
}

/**
 * 02 · Home — Dashboard (`Home · 1` Active — Continue with Zuno) and
 * Explore tab (`Home · 5 · More — Explore Zuno`).
 */
@Composable
fun HomeScreen(
    copy: HomeCopy,
    userName: String,
    userInitial: String,
    userImage: String?,
    streakDays: Int,
    dailyGoalMinutesDone: Int,
    dailyGoalTargetMinutes: Int,
    level: String,
    showUpgrade: Boolean,
    continueSession: ContinueSessionUi?,
    vocabDueCount: Int = 14,
    activeLiveRoomsCount: Int = 2,
    partnersOnlineCount: Int = 12,
    scheduledTimeLabel: String? = null,
    leagueRank: Int = 5,
    onStreakClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onDailyGoalContinue: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenPartners: () -> Unit = onOpenChats,
    onOpenLiveRooms: () -> Unit = onOpenChats,
    onOpenRoleplays: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartHomeVoice: () -> Unit,
    onStartFreeTalk: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onUpgrade: () -> Unit,
    onComingSoon: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialExplore: Boolean = false,
) {
    var showExplore by rememberSaveable(initialExplore) { mutableStateOf(initialExplore) }

    BackHandler(enabled = showExplore) { showExplore = false }

    val liveRoomsLabel = stringResource(R.string.home_explore_live_rooms)
    val partnersLabel = stringResource(R.string.home_explore_partners)
    val vocabularyLabel = stringResource(R.string.home_explore_vocabulary)
    val communityLabel = stringResource(R.string.home_explore_community)
    val leaderboardLabel = stringResource(R.string.home_explore_leaderboard)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas),
    ) {
        Crossfade(
            targetState = showExplore,
            label = "homeRootTab",
            modifier = Modifier.fillMaxSize(),
        ) { isExplore ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                if (isExplore) {
                    ZunoExploreTopBar(onSettingsClick = onOpenSettings)
                    HomeExploreBody(
                        userName = userName.ifBlank { userInitial },
                        userImage = userImage,
                        languageName = copy.languageName,
                        level = level,
                        streakDays = streakDays,
                        showUpgrade = showUpgrade,
                        vocabDueCount = vocabDueCount,
                        activeLiveRoomsCount = activeLiveRoomsCount,
                        partnersOnlineCount = partnersOnlineCount,
                        scheduledTimeLabel = scheduledTimeLabel,
                        leagueRank = leagueRank,
                        onProfileClick = onOpenSettings,
                        onUpgrade = onUpgrade,
                        onRoleplayClick = onOpenRoleplays,
                        onLiveRoomsClick = onOpenLiveRooms,
                        onVocabularyClick = { onComingSoon(vocabularyLabel) },
                        onScheduledClick = onOpenScheduled,
                        onCommunityClick = { onComingSoon(communityLabel) },
                        onPartnersClick = onOpenPartners,
                        onLeaderboardClick = { onComingSoon(leaderboardLabel) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ZunoHomeTopBar(
                        greeting = copy.shortGreeting,
                        streakDays = streakDays,
                        userInitial = userInitial,
                        userImage = userImage,
                        onStreakClick = onStreakClick,
                        onAvatarClick = onAvatarClick,
                    )
                    HomeActiveBody(
                        languageName = copy.languageName,
                        dailyGoalMinutesDone = dailyGoalMinutesDone,
                        dailyGoalTargetMinutes = dailyGoalTargetMinutes,
                        continueSession = continueSession,
                        onDailyGoalContinue = onDailyGoalContinue,
                        onResumeSession = onOpenConversation,
                        onStartFreeTalk = onStartFreeTalk,
                        onLiveRoomClick = onOpenLiveRooms,
                        onPracticePartnerClick = onOpenPartners,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to ZunoColors.BgCanvas.copy(alpha = 0.85f),
                        1f to ZunoColors.BgCanvas,
                    ),
                ),
        )

        ZunoFloatingBottomNav(
            activeTab = if (showExplore) HomeNavTab.More else HomeNavTab.Home,
            onTabSelected = { tab ->
                when (tab) {
                    HomeNavTab.Home -> showExplore = false
                    HomeNavTab.Learn -> {
                        showExplore = false
                        onOpenTopics()
                    }
                    HomeNavTab.Zuno -> onStartHomeVoice()
                    HomeNavTab.Chats -> onOpenChats()
                    HomeNavTab.More -> showExplore = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = ZunoSpace.s12),
        )
    }
}

@Composable
private fun HomeActiveBody(
    languageName: String,
    dailyGoalMinutesDone: Int,
    dailyGoalTargetMinutes: Int,
    continueSession: ContinueSessionUi?,
    onDailyGoalContinue: () -> Unit,
    onResumeSession: (String) -> Unit,
    onStartFreeTalk: () -> Unit,
    onLiveRoomClick: () -> Unit,
    onPracticePartnerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s20, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s24),
    ) {
        ZunoDailyTargetRow(
            minutesDone = dailyGoalMinutesDone,
            goalMinutes = dailyGoalTargetMinutes,
            onClick = onDailyGoalContinue,
        )

        ZunoPickUpSection(
            session = continueSession,
            languageName = languageName,
            onResume = onResumeSession,
            onStartNew = onStartFreeTalk,
        )

        ZunoLiveNowSection(
            onLiveRoomClick = onLiveRoomClick,
            onPracticePartnerClick = onPracticePartnerClick,
        )
    }
}

@Composable
private fun HomeExploreBody(
    userName: String,
    userImage: String?,
    languageName: String,
    level: String,
    streakDays: Int,
    showUpgrade: Boolean,
    vocabDueCount: Int,
    activeLiveRoomsCount: Int,
    partnersOnlineCount: Int,
    scheduledTimeLabel: String?,
    leagueRank: Int,
    onProfileClick: () -> Unit,
    onUpgrade: () -> Unit,
    onRoleplayClick: () -> Unit,
    onLiveRoomsClick: () -> Unit,
    onVocabularyClick: () -> Unit,
    onScheduledClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onPartnersClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s6, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
    ) {
        ZunoProfileCard(
            name = userName,
            detail = stringResource(R.string.home_explore_streak_detail, languageName, level, streakDays),
            imageUrl = userImage,
            onClick = onProfileClick,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showUpgrade) {
            ZunoUpgradeBanner(
                onClick = onUpgrade,
                text = stringResource(R.string.home_explore_upgrade_text),
                action = stringResource(R.string.home_explore_upgrade_action),
            )
        }

        ZunoSettingsGroup(
            sectionLabel = stringResource(R.string.home_explore_practice_learn),
        ) {
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_roleplay),
                leading = { ZunoIcon(R.drawable.zuno_ic_play_circle, 18.dp, tint = ZunoColors.IconSecondary) },
                showChevron = true,
                onClick = onRoleplayClick,
            )
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_live_rooms),
                leading = { ZunoIcon(R.drawable.zuno_ic_radio, 18.dp, tint = ZunoColors.IconSecondary) },
                value = stringResource(R.string.home_explore_live_active_count, activeLiveRoomsCount),
                showChevron = true,
                showDivider = true,
                onClick = onLiveRoomsClick,
            )
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_vocabulary),
                leading = { ZunoIcon(R.drawable.zuno_ic_bookmark, 18.dp, tint = ZunoColors.IconSecondary) },
                value = stringResource(R.string.home_explore_vocab_due_count, vocabDueCount),
                showChevron = true,
                showDivider = true,
                onClick = onVocabularyClick,
            )
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_scheduled),
                leading = { ZunoIcon(R.drawable.zuno_ic_calendar, 18.dp, tint = ZunoColors.IconSecondary) },
                value = scheduledTimeLabel ?: stringResource(R.string.home_explore_scheduled_default),
                showChevron = true,
                showDivider = true,
                onClick = onScheduledClick,
            )
        }

        ZunoSettingsGroup(
            sectionLabel = stringResource(R.string.home_explore_social_milestones),
        ) {
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_community),
                leading = { ZunoIcon(R.drawable.zuno_ic_users, 18.dp, tint = ZunoColors.IconSecondary) },
                showChevron = true,
                onClick = onCommunityClick,
            )
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_partners),
                leading = { ZunoIcon(R.drawable.zuno_ic_user_plus, 18.dp, tint = ZunoColors.IconSecondary) },
                value = stringResource(R.string.home_partners_count, partnersOnlineCount),
                showChevron = true,
                showDivider = true,
                onClick = onPartnersClick,
            )
            ZunoSettingsRow(
                title = stringResource(R.string.home_explore_leaderboard),
                leading = { ZunoIcon(R.drawable.zuno_ic_award, 18.dp, tint = ZunoColors.IconSecondary) },
                value = "#$leagueRank",
                showChevron = true,
                showDivider = true,
                onClick = onLeaderboardClick,
            )
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun HomeScreenActivePreview() {
    ZunoTheme {
        HomeScreen(
            copy = HomeCopy("Hola, Noah", "Spanish", "Responde en español…"),
            userName = "Noah Carter",
            userInitial = "N",
            userImage = null,
            streakDays = 22,
            dailyGoalMinutesDone = 8,
            dailyGoalTargetMinutes = 15,
            level = "B1",
            showUpgrade = true,
            continueSession = ContinueSessionUi(
                conversationId = "conv-1",
                title = "At the café",
                subtitle = "Spanish B1 · 2h ago",
            ),
            onStreakClick = {},
            onAvatarClick = {},
            onDailyGoalContinue = {},
            onOpenTopics = {},
            onOpenChats = {},
            onOpenRoleplays = {},
            onOpenScheduled = {},
            onOpenSettings = {},
            onStartHomeVoice = {},
            onStartFreeTalk = {},
            onOpenConversation = {},
            onUpgrade = {},
            onComingSoon = {},
        )
    }
}
