package com.zuno.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.LearningLanguage
import com.zuno.ai.data.RecentSession
import com.zuno.ai.ui.components.ContinueSessionUi
import com.zuno.ai.ui.components.ZunoHomeGreetingSection
import com.zuno.ai.ui.components.ZunoNextTasksSection
import com.zuno.ai.ui.components.ZunoPickUpSection
import com.zuno.ai.ui.components.ZunoSidebarHomeTopBar
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.ui.theme.zunoCanvasBackground
import kotlinx.coroutines.launch

/** Greeting copy for Home header and composer placeholder. */
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
 * `02 · Home` (`3077:14335` Home · 1 · Active — Continue with Zuno & `3077:14390` Home · 5 · Sidebar Drawer).
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
    recentSessions: List<RecentSession> = emptyList(),
    vocabDueCount: Int = 12,
    nextRoleplayTitle: String? = null,
    activeLiveRoomsCount: Int = 2,
    partnersOnlineCount: Int = 12,
    scheduledTimeLabel: String? = null,
    leagueRank: Int = 5,
    onStreakClick: () -> Unit,
    onLanguageClick: () -> Unit = {},
    onAvatarClick: () -> Unit,
    onNotificationsClick: () -> Unit = onAvatarClick,
    onDailyGoalContinue: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenPartners: () -> Unit = onOpenChats,
    onOpenLiveRooms: () -> Unit = onOpenChats,
    onOpenVocabulary: () -> Unit = {},
    onOpenProgress: () -> Unit = onStreakClick,
    onOpenRoleplays: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartHomeVoice: () -> Unit,
    onStartFreeTalk: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenRecentSession: (RecentSession) -> Unit = { onOpenConversation(it.conversationId ?: it.id) },
    onUpgrade: () -> Unit,
    onComingSoon: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    initialDrawerOpen: Boolean = false,
) {
    val drawerState = rememberDrawerState(if (initialDrawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val closeAndRun: (() -> Unit) -> Unit = { action ->
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = ZunoColors.BgScrim,
        drawerContent = {
            AppDrawer(
                userName = userName.ifBlank { userInitial },
                userDetail = "${copy.languageName} · $level · ${streakDays}d",
                userImage = userImage,
                showUpgrade = showUpgrade,
                activeDestination = DrawerDestination.FreeTalk,
                sessions = recentSessions,
                selectedSessionId = continueSession?.conversationId,
                vocabDueCount = vocabDueCount,
                scheduledTimeLabel = scheduledTimeLabel ?: "7:00 PM",
                activeLiveRoomsCount = activeLiveRoomsCount,
                partnersOnlineCount = partnersOnlineCount,
                leagueRank = leagueRank,
                onProfile = { closeAndRun(onAvatarClick) },
                onNotifications = { closeAndRun(onNotificationsClick) },
                onDestination = { destination ->
                    closeAndRun {
                        when (destination) {
                            DrawerDestination.FreeTalk -> onOpenChats()
                            DrawerDestination.Topics -> onOpenTopics()
                            DrawerDestination.Roleplays -> onOpenRoleplays()
                            DrawerDestination.Vocabulary -> onOpenVocabulary()
                            DrawerDestination.Scheduled -> onOpenScheduled()
                            DrawerDestination.LiveRooms -> onOpenLiveRooms()
                            DrawerDestination.Community -> onOpenPartners()
                            DrawerDestination.Progress -> onOpenProgress()
                        }
                    }
                },
                onSearchSessions = { closeAndRun(onOpenChats) },
                onSession = { session -> closeAndRun { onOpenRecentSession(session) } },
                onUpgrade = { closeAndRun(onUpgrade) },
                onSettings = { closeAndRun(onOpenSettings) },
                onNewChat = { closeAndRun(onStartFreeTalk) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zunoCanvasBackground(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                ZunoSidebarHomeTopBar(
                    streakDays = streakDays,
                    languageLabel = "${copy.languageName} · $level",
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onStreakClick = onStreakClick,
                    onLanguageClick = onLanguageClick,
                )

                val resolvedRoleplayTitle = nextRoleplayTitle?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.home_next_roleplay_default)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = 28.dp, bottom = ZunoSpace.s32),
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    ZunoHomeGreetingSection(
                        greeting = copy.shortGreeting,
                        minutesDone = dailyGoalMinutesDone,
                        goalMinutes = dailyGoalTargetMinutes,
                        onProgressClick = onOpenProgress,
                    )

                    ZunoPickUpSection(
                        session = continueSession,
                        languageName = copy.languageName,
                        onResume = onOpenConversation,
                        onStartNew = onStartFreeTalk,
                    )

                    ZunoNextTasksSection(
                        vocabDueCount = vocabDueCount,
                        roleplayTitle = resolvedRoleplayTitle,
                        onPracticeVocabulary = onOpenVocabulary,
                        onOpenRoleplay = onOpenRoleplays,
                        onFreeTalk = onStartHomeVoice,
                    )
                }
            }
        }
    }
}

@Preview(backgroundColor = 0xFF171717, showBackground = true, widthDp = 402, heightDp = 874)
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
