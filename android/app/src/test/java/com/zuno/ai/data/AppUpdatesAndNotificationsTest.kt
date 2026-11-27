package com.zuno.ai.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesAndNotificationsTest {

    @Test
    fun compareVersionNames_ordersSemverCorrectly() {
        assertTrue(AppReleaseCatalog.compareVersionNames("1.0.8", "1.0.7") > 0)
        assertTrue(AppReleaseCatalog.compareVersionNames("2.5.0", "1.0.7") > 0)
        assertEquals(0, AppReleaseCatalog.compareVersionNames("v1.0.7", "1.0.7"))
        assertTrue(AppReleaseCatalog.compareVersionNames("1.0.6", "1.0.7") < 0)
    }

    @Test
    fun parseAppUpdateInfo_detectsOptionalAndRequiredUpdates() {
        val json = JSONObject(
            """
            {
              "latestVersion": "2.5.0",
              "latestVersionCode": 15,
              "minSupportedVersionCode": 10,
              "summary": "Faster voice replies and 4 new Spanish roleplays.",
              "releasedAtLabel": "Nov 25, 2026",
              "highlights": [
                { "category": "new", "title": "4 Spanish roleplays", "description": "Café & travel scenes." },
                { "category": "improved", "title": "40% faster turns", "description": "Lower voice latency." }
              ]
            }
            """.trimIndent(),
        )
        val requiredInfo = json.toAppUpdateInfoOrNull(currentVersionCode = 9, currentVersionName = "1.0.7")
        assertNotNull(requiredInfo)
        assertTrue(requiredInfo!!.required)
        assertEquals("2.5.0", requiredInfo.latestVersion)
        assertEquals(2, requiredInfo.releaseNotes.highlights.size)
        assertEquals(ReleaseHighlightCategory.New, requiredInfo.releaseNotes.highlights.first().category)

        val optionalInfo = json.toAppUpdateInfoOrNull(currentVersionCode = 11, currentVersionName = "1.1.0")
        assertNotNull(optionalInfo)
        assertFalse(optionalInfo!!.required)
    }

    @Test
    fun buildSystemNotificationItems_buildsUpdateMessagesRoomsAndRespectsReadIds() {
        val now = 1_700_000_000_000L
        val update = AppUpdateInfo(
            latestVersion = "2.5.0",
            latestVersionCode = 999,
            required = false,
            summary = "Faster voice replies.",
            releaseNotes = AppReleaseNote(
                version = "2.5.0",
                versionCode = 999,
                dateLabel = "Today",
                summary = "Faster voice replies.",
                highlights = emptyList(),
            ),
        )
        val chatsOverview = ChatsOverview(
            openToSpeak = true,
            language = "es",
            languageName = "Spanish",
            level = "B1",
            unreadCount = 1,
            onlineSpeakersCount = 1,
            openSpeakers = emptyList(),
            partners = emptyList(),
            conversations = listOf(
                UnifiedChatItem(
                    id = "peer-1",
                    kind = UnifiedChatKind.Peer,
                    conversationId = null,
                    peerConversationId = "peer-conv-1",
                    partnerId = "u-elena",
                    title = "Elena",
                    badge = null,
                    isVoice = false,
                    openToSpeak = true,
                    snippet = "¿Practicamos español hoy a las 7?",
                    unread = true,
                    pinned = false,
                    updatedAtMillis = now - 14 * 60_000L,
                    avatarInitial = "E",
                    avatarUrl = null,
                ),
            ),
        )
        val liveRooms = LiveRoomsBrowse(
            featured = LiveRoom(
                id = "room-1",
                title = "Spanish B1 · Airport check-in",
                status = "live",
                hostName = "Marco",
                members = listOf(LiveRoomMember(id = "u-marco", name = "Marco", initial = "M")),
            ),
            rooms = emptyList(),
            activeRoomsCount = 1,
        )
        val profile = Profile(
            name = "Noah Carter",
            email = "noah@example.com",
            targetLanguage = "es",
            level = "B1",
            stats = UserStatsSummary(streakDays = 22),
        )

        val unreadItems = buildSystemNotificationItems(
            profile = profile,
            chatsOverview = chatsOverview,
            liveRooms = liveRooms,
            schedules = emptyList(),
            appUpdateInfo = update,
            pushNotifications = emptyList(),
            readIds = emptySet(),
            nowMillis = now,
        )

        assertTrue(unreadItems.any { it.kind == SystemNotificationKind.AppUpdate && it.unread })
        assertTrue(unreadItems.any { it.kind == SystemNotificationKind.UserMessage && it.unread && it.title.contains("Elena") })
        assertTrue(unreadItems.any { it.kind == SystemNotificationKind.LiveRoomInvite && it.unread })
        assertTrue(unreadItems.any { it.kind == SystemNotificationKind.Announcement })
        assertTrue(unreadItems.any { it.kind == SystemNotificationKind.StreakMilestone && it.title.contains("22-day") })

        val msgId = unreadItems.first { it.kind == SystemNotificationKind.UserMessage }.id
        val afterRead = buildSystemNotificationItems(
            profile = profile,
            chatsOverview = chatsOverview,
            liveRooms = liveRooms,
            schedules = emptyList(),
            appUpdateInfo = update,
            pushNotifications = emptyList(),
            readIds = setOf(msgId),
            nowMillis = now,
        )
        assertFalse(afterRead.first { it.id == msgId }.unread)
    }
}
