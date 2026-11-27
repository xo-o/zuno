package com.zuno.ai.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.AppReleaseHighlight
import com.zuno.ai.data.AppReleaseNote
import com.zuno.ai.data.AppUpdateInfo
import com.zuno.ai.data.NotificationFilter
import com.zuno.ai.data.Profile
import com.zuno.ai.data.ReleaseHighlightCategory
import com.zuno.ai.data.SystemNotificationItem
import com.zuno.ai.data.SystemNotificationKind
import com.zuno.ai.ui.screens.AppUpdateSheet
import com.zuno.ai.ui.screens.NotificationsScreen
import com.zuno.ai.ui.screens.RequiredUpdateDialog
import com.zuno.ai.ui.screens.SettingsAboutScreen
import com.zuno.ai.ui.screens.SettingsOverviewScreen
import com.zuno.ai.ui.screens.SettingsWhatsNewScreen
import com.zuno.ai.ui.theme.ZunoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdatesAndNotificationsUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val sampleUpdate = AppUpdateInfo(
        latestVersion = "2.5.0",
        latestVersionCode = 999,
        required = false,
        summary = "Faster live voice replies with Sofía and 4 new Spanish roleplays.",
        releaseNotes = AppReleaseNote(
            version = "2.5.0",
            versionCode = 999,
            dateLabel = "Nov 25, 2026",
            summary = "Faster live voice replies with Sofía and 4 new Spanish roleplays.",
            highlights = listOf(
                AppReleaseHighlight(
                    ReleaseHighlightCategory.New,
                    "4 Spanish café & travel roleplays",
                    "Practice airport check-in and hotel check-out.",
                ),
                AppReleaseHighlight(
                    ReleaseHighlightCategory.Improved,
                    "40% faster live voice replies",
                    "Smoother barge-in during Free Talk.",
                ),
            ),
        ),
    )

    @Test
    fun settingsOverview_showsAppVersionAndUpdateBadge() {
        val profile = Profile(name = "Noah Carter", email = "noah@example.com", targetLanguage = "es", level = "B1")
        rule.setContent {
            ZunoTheme {
                SettingsOverviewScreen(
                    profile = profile,
                    appLanguage = AppLanguage.English,
                    onBack = {},
                    onUpgrade = {},
                    onAccount = {},
                    onLearning = {},
                    onAppLanguage = {},
                    onPreferences = {},
                    onNotifications = {},
                    onAbout = {},
                    onContactSupport = {},
                    onSignOut = {},
                    onDeleteAccount = {},
                    appVersionName = "1.0.7",
                    appVersionCode = 9,
                    availableUpdateVersion = "2.5.0",
                )
            }
        }
        rule.onNodeWithText("About & updates").assertExists()
        rule.onNodeWithText("v2.5.0 available").assertExists()
        rule.onNodeWithText("ZunoAI v1.0.7 (9)").assertExists()
    }

    @Test
    fun settingsAbout_showsSlimStripForUpdateAndUpToDateStates() {
        var whatsNewClicked = false
        var updateClicked = false
        rule.setContent {
            ZunoTheme {
                SettingsAboutScreen(
                    availableUpdate = sampleUpdate,
                    onWhatsNew = { whatsNewClicked = true },
                    onUpdateNow = { updateClicked = true },
                    onRateApp = {},
                    onTerms = {},
                    onPrivacy = {},
                    onBack = {},
                    appVersionName = "1.0.7",
                    appVersionCode = 9,
                )
            }
        }
        rule.onNodeWithText("Version 1.0.7 · Build 9").assertIsDisplayed()
        rule.onNodeWithText("v2.5.0 available").assertIsDisplayed()
        rule.onNodeWithText("Update").performClick()
        assertTrue(updateClicked)
        rule.onNodeWithText("What’s new").performClick()
        assertTrue(whatsNewClicked)
    }

    @Test
    fun settingsAbout_upToDateStateTriggersRefreshCheck() {
        var refreshClicked = false
        rule.setContent {
            ZunoTheme {
                SettingsAboutScreen(
                    availableUpdate = null,
                    onWhatsNew = {},
                    onUpdateNow = {},
                    onRateApp = {},
                    onTerms = {},
                    onPrivacy = {},
                    onCheckForUpdates = { refreshClicked = true },
                    onBack = {},
                    appVersionName = "1.0.7",
                    appVersionCode = 9,
                )
            }
        }
        rule.onNodeWithText("Version 1.0.7 · Build 9").assertIsDisplayed()
        rule.onNodeWithText("You’re up to date").assertIsDisplayed()
        rule.onNodeWithContentDescription("Check for updates").performClick()
        assertTrue(refreshClicked)
    }

    @Test
    fun settingsWhatsNew_rendersLatestAndPreviousReleases() {
        rule.setContent {
            ZunoTheme {
                SettingsWhatsNewScreen(
                    availableUpdate = sampleUpdate,
                    onUpdateNow = {},
                    onBack = {},
                    appVersionName = "1.0.7",
                    appVersionCode = 9,
                )
            }
        }
        rule.onNodeWithText("Version 2.5.0").assertIsDisplayed()
        rule.onNodeWithText("Available").assertIsDisplayed()
        rule.onNodeWithText("Update to v2.5.0").assertIsDisplayed()
        rule.onNodeWithText("Version 1.0.7").assertIsDisplayed()
    }

    @Test
    fun updateSheetAndRequiredDialog_renderActions() {
        var dismissed = false
        rule.setContent {
            ZunoTheme {
                AppUpdateSheet(
                    update = sampleUpdate,
                    onUpdateNow = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.onNodeWithText("ZunoAI 2.5.0 is ready").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun requiredUpdateDialog_displaysBlockedPrompt() {
        var updated = false
        rule.setContent {
            ZunoTheme {
                RequiredUpdateDialog(
                    currentVersion = "1.0.5",
                    latestVersion = "2.5.0",
                    onWhatsNew = {},
                    onUpdateNow = { updated = true },
                )
            }
        }
        rule.onNodeWithText("Update required").assertIsDisplayed()
        rule.onNodeWithText("Update now").performClick()
        assertTrue(updated)
    }

    @Test
    fun notificationsScreen_filtersAndEmptyStateWork() {
        val items = listOf(
            SystemNotificationItem(
                id = "upd-1",
                kind = SystemNotificationKind.AppUpdate,
                filter = NotificationFilter.Updates,
                title = "ZunoAI 2.5.0 is ready to update.",
                preview = "Faster voice replies & 4 new roleplays.",
                meta = "App update · v2.5.0 · Today",
                isToday = true,
                unread = true,
                badgeText = "ZunoAI 2.5.0",
                pillText = "What's new in v2.5.0",
                actionLabel = "Update",
            ),
            SystemNotificationItem(
                id = "msg-1",
                kind = SystemNotificationKind.UserMessage,
                filter = NotificationFilter.Messages,
                title = "Elena sent you a message",
                preview = "¿Practicamos español hoy a las 7?",
                meta = "Message · 14m ago",
                isToday = true,
                unread = true,
                avatarInitial = "E",
            ),
        )
        var clickedId: String? = null
        rule.setContent {
            ZunoTheme {
                NotificationsScreen(
                    items = items,
                    onBack = {},
                    onOpenNotificationSettings = {},
                    onItemClick = { clickedId = it.id },
                )
            }
        }
        rule.onNodeWithText("ZunoAI 2.5.0 is ready to update.").assertIsDisplayed()
        rule.onNodeWithText("Elena sent you a message").assertIsDisplayed()
        rule.onNodeWithText("Messages").performClick()
        rule.onNodeWithText("Elena sent you a message").performClick()
        assertEquals("msg-1", clickedId)
        rule.onNodeWithText("Announcements").performClick()
        rule.onNodeWithText("You’re all caught up").assertIsDisplayed()
        rule.onNodeWithContentDescription("Notification settings").assertIsDisplayed()
    }
}
