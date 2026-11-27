package com.zuno.ai.data

import com.zuno.ai.BuildConfig
import org.json.JSONObject

// ── App Version, Release Notes & Update Catalog ──────────────────────────────

enum class ReleaseHighlightCategory { New, Improved, Fixed }

data class AppReleaseHighlight(
    val category: ReleaseHighlightCategory,
    val title: String,
    val description: String,
)

data class AppReleaseNote(
    val version: String,
    val versionCode: Int,
    val dateLabel: String,
    val summary: String,
    val highlights: List<AppReleaseHighlight>,
)

data class AppUpdateInfo(
    val latestVersion: String,
    val latestVersionCode: Int,
    val required: Boolean,
    val summary: String,
    val releaseNotes: AppReleaseNote,
    val storeUrl: String = PLAY_STORE_HTTPS_URL,
) {
    val hasUpdate: Boolean
        get() = AppReleaseCatalog.hasNewerVersion(
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersion,
        )

    companion object {
        const val PLAY_STORE_HTTPS_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        const val PLAY_STORE_MARKET_URL = "market://details?id=${BuildConfig.APPLICATION_ID}"
    }
}

object AppReleaseCatalog {
    /** Bundled release notes for installed and recent versions. */
    fun bundledReleases(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): List<AppReleaseNote> = listOf(
        AppReleaseNote(
            version = currentVersionName,
            versionCode = currentVersionCode,
            dateLabel = "Nov 26, 2026",
            summary = "System notifications inbox, streamlined About & update status, and consistent Settings icons.",
            highlights = listOf(
                AppReleaseHighlight(
                    category = ReleaseHighlightCategory.New,
                    title = "System notifications inbox",
                    description = "Catch user messages, live room invites, and app updates from the sidebar bell.",
                ),
                AppReleaseHighlight(
                    category = ReleaseHighlightCategory.Improved,
                    title = "Streamlined About & update check",
                    description = "One-tap update status strip, release notes timeline, and live version check.",
                ),
                AppReleaseHighlight(
                    category = ReleaseHighlightCategory.Fixed,
                    title = "Consistent Settings icons",
                    description = "Unified icon colors and contrast across all Settings screens.",
                ),
            ),
        ),
        AppReleaseNote(
            version = "1.0.7",
            versionCode = (currentVersionCode - 1).coerceAtLeast(1),
            dateLabel = "Nov 25, 2026",
            summary = "Smarter onboarding parsing, synced app locale, and faster live voice replies.",
            highlights = listOf(
                AppReleaseHighlight(
                    category = ReleaseHighlightCategory.Improved,
                    title = "Faster live voice replies",
                    description = "Smoother turn-taking and barge-in during Free Talk and Roleplays.",
                ),
                AppReleaseHighlight(
                    category = ReleaseHighlightCategory.Fixed,
                    title = "Audio focus after phone calls",
                    description = "Calls automatically resume your session mic without reconnecting.",
                ),
            ),
        ),
    )

    fun compareVersionNames(a: String, b: String): Int {
        val partsA = a.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val partsB = b.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun hasNewerVersion(
        currentVersionCode: Int,
        currentVersionName: String,
        latestVersionCode: Int,
        latestVersionName: String,
    ): Boolean = latestVersionCode > currentVersionCode || compareVersionNames(latestVersionName, currentVersionName) > 0
}

internal fun JSONObject.toAppUpdateInfoOrNull(
    currentVersionCode: Int = BuildConfig.VERSION_CODE,
    currentVersionName: String = BuildConfig.VERSION_NAME,
): AppUpdateInfo? {
    val latestVersion = optString("latestVersion").ifBlank { return null }
    val latestCode = optInt("latestVersionCode", currentVersionCode)
    val required = optBoolean("required", false) || optInt("minSupportedVersionCode", 0) > currentVersionCode
    val summary = optString("summary").ifBlank {
        "Faster voice replies, new roleplays, and reliability improvements."
    }
    val dateLabel = optString("releasedAtLabel").ifBlank { "Today" }
    val highlightsArr = optJSONArray("highlights")
    val highlights = if (highlightsArr != null) {
        List(highlightsArr.length()) { idx ->
            val obj = highlightsArr.optJSONObject(idx)
            val cat = when (obj?.optString("category")?.lowercase()) {
                "improved" -> ReleaseHighlightCategory.Improved
                "fixed" -> ReleaseHighlightCategory.Fixed
                else -> ReleaseHighlightCategory.New
            }
            AppReleaseHighlight(
                category = cat,
                title = obj?.optString("title").orEmpty(),
                description = obj?.optString("description").orEmpty(),
            )
        }.filter { it.title.isNotBlank() }
    } else {
        emptyList()
    }
    val note = AppReleaseNote(
        version = latestVersion,
        versionCode = latestCode,
        dateLabel = dateLabel,
        summary = summary,
        highlights = highlights.ifEmpty {
            AppReleaseCatalog.bundledReleases(currentVersionName, currentVersionCode).first().highlights
        },
    )
    return AppUpdateInfo(
        latestVersion = latestVersion,
        latestVersionCode = latestCode,
        required = required,
        summary = summary,
        releaseNotes = note,
        storeUrl = optString("storeUrl").ifBlank { AppUpdateInfo.PLAY_STORE_HTTPS_URL },
    )
}

// ── System Notifications Center (`Home · 5a`, `Home · 5b`) ───────────────────

enum class NotificationFilter { All, Messages, Updates, Announcements }

enum class SystemNotificationKind {
    AppUpdate,
    UserMessage,
    LiveRoomInvite,
    Announcement,
    StreakMilestone,
    ScheduledSession,
}

data class SystemNotificationItem(
    val id: String,
    val kind: SystemNotificationKind,
    val filter: NotificationFilter,
    val title: String,
    val preview: String,
    val meta: String,
    val isToday: Boolean,
    val unread: Boolean,
    val timestampMillis: Long = 0L,
    val avatarInitial: String? = null,
    val avatarUrl: String? = null,
    val badgeText: String? = null,
    val pillText: String? = null,
    val actionLabel: String? = null,
    val peerConversationId: String? = null,
    val partnerId: String? = null,
    val roomId: String? = null,
    val scheduleId: String? = null,
    val releaseVersion: String? = null,
)

fun formatRelativeNotificationTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (timestampMillis <= 0L) return "Recently"
    val diffSec = ((nowMillis - timestampMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3600L}h ago"
        diffSec < 7 * 86_400L -> "${diffSec / 86_400L}d ago"
        else -> "Earlier"
    }
}

fun buildSystemNotificationItems(
    profile: Profile?,
    chatsOverview: ChatsOverview?,
    liveRooms: LiveRoomsBrowse?,
    schedules: List<Schedule>,
    appUpdateInfo: AppUpdateInfo?,
    pushNotifications: List<SystemNotificationItem>,
    readIds: Set<String>,
    nowMillis: Long = System.currentTimeMillis(),
): List<SystemNotificationItem> {
    val items = mutableListOf<SystemNotificationItem>()
    val seenIds = mutableSetOf<String>()

    fun addItem(item: SystemNotificationItem) {
        if (seenIds.add(item.id)) {
            items += item.copy(unread = item.unread && item.id !in readIds)
        }
    }

    // 1. App update hero item (when newer version is available)
    if (appUpdateInfo != null && appUpdateInfo.hasUpdate) {
        addItem(
            SystemNotificationItem(
                id = "app_update:${appUpdateInfo.latestVersionCode}",
                kind = SystemNotificationKind.AppUpdate,
                filter = NotificationFilter.Updates,
                title = "ZunoAI ${appUpdateInfo.latestVersion} is ready to update.",
                preview = appUpdateInfo.summary,
                meta = "App update · v${appUpdateInfo.latestVersion} · Today",
                isToday = true,
                unread = true,
                timestampMillis = nowMillis,
                badgeText = "ZunoAI ${appUpdateInfo.latestVersion}",
                pillText = "What's new in v${appUpdateInfo.latestVersion}",
                actionLabel = "Update",
                releaseVersion = appUpdateInfo.latestVersion,
            ),
        )
    }

    // 2. Live incoming push events recorded during session
    pushNotifications.forEach { addItem(it) }

    // 3. Peer messages from unified chats overview
    chatsOverview?.conversations
        ?.filter { it.kind == UnifiedChatKind.Peer }
        ?.sortedByDescending { it.updatedAtMillis }
        ?.take(6)
        ?.forEach { chat ->
            val rel = formatRelativeNotificationTime(chat.updatedAtMillis, nowMillis)
            val ageMs = (nowMillis - chat.updatedAtMillis).coerceAtLeast(0L)
            addItem(
                SystemNotificationItem(
                    id = "peer_msg:${chat.peerConversationId ?: chat.id}",
                    kind = SystemNotificationKind.UserMessage,
                    filter = NotificationFilter.Messages,
                    title = "${chat.title} sent you a message",
                    preview = chat.snippet.ifBlank { "Tap to reply in 1:1 chat" },
                    meta = "Message · $rel",
                    isToday = ageMs < 86_400_000L,
                    unread = chat.unread,
                    timestampMillis = chat.updatedAtMillis,
                    avatarInitial = chat.avatarInitial,
                    avatarUrl = chat.avatarUrl,
                    peerConversationId = chat.peerConversationId ?: chat.id,
                    partnerId = chat.partnerId,
                ),
            )
        }

    // 4. Active live rooms / invites
    val activeRooms = buildList {
        liveRooms?.featured?.takeIf { it.isLive && it.members.isNotEmpty() }?.let { add(it) }
        liveRooms?.rooms?.filter { it.isLive || it.members.isNotEmpty() }?.forEach { add(it) }
    }.distinctBy { it.id }.take(3)

    activeRooms.forEach { room ->
        val host = room.hostName.ifBlank { "Zuno" }
        val activeCount = room.members.size.coerceAtLeast(1)
        val firstMember = room.members.firstOrNull { !it.isYou } ?: room.members.firstOrNull()
        addItem(
            SystemNotificationItem(
                id = "live_room:${room.id}",
                kind = SystemNotificationKind.LiveRoomInvite,
                filter = NotificationFilter.Messages,
                title = "$host invited you to a room",
                preview = "${room.title} · $activeCount active",
                meta = "Live room · Active now",
                isToday = true,
                unread = true,
                timestampMillis = nowMillis - 15 * 60_000L,
                avatarInitial = firstMember?.initial ?: host.take(1).uppercase(),
                avatarUrl = firstMember?.imageUrl,
                roomId = room.id,
            ),
        )
    }

    // 5. Upcoming scheduled practice session
    schedules.filter { it.active }
        .minByOrNull { it.nextRunAtMillis ?: Long.MAX_VALUE }
        ?.let { next ->
            addItem(
                SystemNotificationItem(
                    id = "schedule:${next.id}",
                    kind = SystemNotificationKind.ScheduledSession,
                    filter = NotificationFilter.Updates,
                    title = "Scheduled · ${next.title}",
                    preview = next.meta.ifBlank { "Practice session reminder" },
                    meta = "Scheduled · ${next.timeOfDay}",
                    isToday = true,
                    unread = false,
                    timestampMillis = nowMillis - 2 * 3_600_000L,
                    scheduleId = next.id,
                ),
            )
        }

    // 6. Earlier product announcement (Sofía / Tutor voices)
    addItem(
        SystemNotificationItem(
            id = "announcement:sofia_2_0",
            kind = SystemNotificationKind.Announcement,
            filter = NotificationFilter.Announcements,
            title = "Meet Sofía 2.0 natural voice",
            preview = "Richer accents & real-time pronunciation feedback across 35 languages.",
            meta = "Announcement · v${BuildConfig.VERSION_NAME}",
            isToday = false,
            unread = false,
            badgeText = "Sofía 2.0",
        ),
    )

    // 7. Streak milestone in Earlier
    val streak = profile?.stats?.streakDays ?: 0
    if (streak > 0) {
        val langName = profile?.language?.englishName ?: "Spanish"
        val lvl = profile?.level ?: "B1"
        addItem(
            SystemNotificationItem(
                id = "milestone:streak_$streak",
                kind = SystemNotificationKind.StreakMilestone,
                filter = NotificationFilter.Updates,
                title = "$streak-day streak milestone",
                preview = "Keep your $langName $lvl streak going tonight.",
                meta = "Progress · Active streak",
                isToday = false,
                unread = false,
            ),
        )
    }

    return items
}
