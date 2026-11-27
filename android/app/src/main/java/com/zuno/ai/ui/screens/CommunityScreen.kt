package com.zuno.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.LeagueOverview
import com.zuno.ai.data.LeagueStanding
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.ui.components.ZunoAvatar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
import com.zuno.ai.ui.components.ZunoEmptyState
import com.zuno.ai.ui.components.ZunoLoadState
import com.zuno.ai.ui.components.ZunoSectionTopBar
import com.zuno.ai.ui.components.ZunoSegmentedTabs
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoGlassHairline
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType
import com.zuno.ai.ui.theme.zunoCanvasBackground
import com.zuno.ai.ui.theme.zunoGlassElevated
import com.zuno.ai.ui.theme.zunoGlassSubtle
import com.zuno.ai.ui.theme.zunoLayout
import com.zuno.ai.viewmodel.Loadable

/**
 * `14 · Community & Partners` (`3131:13712` Partners & `3131:13835` League).
 */
@Composable
fun CommunityScreen(
    userName: String,
    userInitial: String,
    userImageUrl: String?,
    languageName: String,
    level: String,
    openToSpeak: Boolean,
    partners: List<PeerLearner>,
    onlineLearners: List<PeerLearner>,
    league: Loadable<LeagueOverview> = Loadable(),
    onRetryLeague: () -> Unit = {},
    onToggleOpenToSpeak: (Boolean) -> Unit,
    onTalkToPartner: (PeerLearner) -> Unit,
    onInviteClick: () -> Unit,
    onOpenChats: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zunoCanvasBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoSectionTopBar(
            title = "Community",
            onBack = onBack,
            showSearch = false,
            showAdd = selectedTab == 0,
            onAdd = onInviteClick,
            addDescription = "Invite partner",
        )

        Box(Modifier.padding(horizontal = zunoLayout.screenPaddingH, vertical = ZunoSpace.s6)) {
            ZunoSegmentedTabs(
                labels = listOf("Partners", "League"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
        }

        if (selectedTab == 0) {
            PartnersTabBody(
                userInitial = userInitial,
                userImageUrl = userImageUrl,
                languageName = languageName,
                level = level,
                openToSpeak = openToSpeak,
                partners = partners,
                onlineLearners = onlineLearners,
                onToggleOpenToSpeak = onToggleOpenToSpeak,
                onTalkToPartner = onTalkToPartner,
                onInviteClick = onInviteClick,
                modifier = Modifier.weight(1f),
            )
        } else {
            LeagueTabBody(
                league = league,
                onRetry = onRetryLeague,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PartnersTabBody(
    userInitial: String,
    userImageUrl: String?,
    languageName: String,
    level: String,
    openToSpeak: Boolean,
    partners: List<PeerLearner>,
    onlineLearners: List<PeerLearner>,
    onToggleOpenToSpeak: (Boolean) -> Unit,
    onTalkToPartner: (PeerLearner) -> Unit,
    onInviteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s12, bottom = ZunoSpace.s32),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
    ) {
        item(key = "open-toggle") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl))
                    .clickable(role = Role.Switch, onClick = { onToggleOpenToSpeak(!openToSpeak) })
                    .padding(horizontal = ZunoSpace.s14, vertical = ZunoSpace.s12),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZunoAvatar(
                    initial = userInitial,
                    imageUrl = userImageUrl,
                    size = 38.dp,
                    textStyle = ZunoType.LabelMedium,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                    Text("Open to speak", style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                    Text(
                        text = if (openToSpeak) {
                            "Visible to $languageName $level learners online"
                        } else {
                            "Turn on so $languageName learners can invite you"
                        },
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ZunoSwitch(checked = openToSpeak, onCheckedChange = onToggleOpenToSpeak)
            }
        }

        item(key = "your-partners") {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Your partners", style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                    Text(
                        text = "Invite",
                        style = ZunoType.LabelSmall,
                        color = ZunoColors.TextSecondary,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onInviteClick),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                ) {
                    if (partners.isNotEmpty()) {
                        partners.forEachIndexed { idx, p ->
                            if (idx > 0) ZunoGlassHairline()
                            PartnerCardRow(
                                initial = p.initial,
                                imageUrl = p.imageUrl,
                                name = p.name,
                                subtitle = buildString {
                                    append("${p.languageName} ${p.level}")
                                    if (p.activeLabel.isNotBlank()) append(" · ${p.activeLabel}")
                                },
                                onlineDot = p.online,
                                onTalk = { onTalkToPartner(p) },
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s20),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                        ) {
                            Text(
                                text = "No practice partners yet",
                                style = ZunoType.BodyMediumStrong,
                                color = ZunoColors.TextPrimary,
                            )
                            Text(
                                text = "Add learners you meet in Chats or Live Rooms to practice together again.",
                                style = ZunoType.FootnoteRegular,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                    }
                }
            }
        }

        item(key = "online-now") {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Online now", style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                    Text(
                        text = "${onlineLearners.size} online",
                        style = ZunoType.FootnoteRegular,
                        color = ZunoColors.TextTertiary,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                ) {
                    if (onlineLearners.isNotEmpty()) {
                        onlineLearners.forEachIndexed { idx, learner ->
                            if (idx > 0) ZunoGlassHairline()
                            val tags = learner.interests.take(2).joinToString(", ")
                            PartnerCardRow(
                                initial = learner.initial,
                                imageUrl = learner.imageUrl,
                                name = learner.name,
                                subtitle = if (tags.isNotBlank()) {
                                    "${learner.languageName} ${learner.level} · $tags"
                                } else {
                                    "${learner.languageName} ${learner.level} · ${learner.activeLabel}"
                                },
                                onlineDot = learner.online,
                                onTalk = { onTalkToPartner(learner) },
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s20),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
                        ) {
                            Text(
                                text = "No $languageName learners online right now",
                                style = ZunoType.BodyMediumStrong,
                                color = ZunoColors.TextPrimary,
                            )
                            Text(
                                text = "Leave “Open to speak” on — real learners studying $languageName will appear here as soon as they connect.",
                                style = ZunoType.FootnoteRegular,
                                color = ZunoColors.TextTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerCardRow(
    initial: String,
    imageUrl: String?,
    name: String,
    subtitle: String,
    onlineDot: Boolean,
    onTalk: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onTalk)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            ZunoAvatar(
                initial = initial,
                imageUrl = imageUrl,
                size = 40.dp,
                textStyle = ZunoType.LabelMedium,
            )
            if (onlineDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ZunoColors.StatusSpeaking)
                        .border(2.dp, ZunoColors.BgCanvas, CircleShape),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            Text(name, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ZunoButton(
            label = "Talk",
            onClick = onTalk,
            variant = ZunoButtonVariant.Secondary,
            size = ZunoButtonSize.Small,
        )
    }
}

private fun formatCountdown(resetsAtMillis: Long?): String {
    if (resetsAtMillis == null) return ""
    val remainingMs = (resetsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val totalHours = remainingMs / 3_600_000L
    val days = totalHours / 24L
    val hours = totalHours % 24L
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h left"
        else -> "<1h left"
    }
}

@Composable
private fun LeagueTabBody(
    league: Loadable<LeagueOverview>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = league.items
    if (data == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ZunoLoadState(error = league.error, onRetry = onRetry)
        }
        return
    }

    if (data.standings.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(horizontal = zunoLayout.screenPaddingH),
            contentAlignment = Alignment.Center,
        ) {
            ZunoEmptyState(
                icon = R.drawable.zuno_ic_trending_up,
                title = "This week's league is open",
                description = "Complete a voice call, chat, or lesson to earn XP and appear on the leaderboard.",
            )
        }
        return
    }

    val top3 = data.standings.take(3)
    val restStandings = if (data.standings.size > 3) data.standings.drop(3) else data.standings
    val countdown = formatCountdown(data.resetsAtMillis)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = zunoLayout.screenPaddingH, end = zunoLayout.screenPaddingH, top = ZunoSpace.s12, bottom = ZunoSpace.s32),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
    ) {
        item(key = "podium") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xxl))
                    .padding(ZunoSpace.s16),
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s14),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                        Text("Weekly speaking", style = ZunoType.FootnoteRegular, color = ZunoColors.TextSecondary)
                        Text(
                            text = data.myRank?.let { "Your rank · #$it" } ?: "Practice to rank",
                            style = ZunoType.TitleMedium,
                            color = ZunoColors.TextPrimary,
                        )
                    }
                    if (countdown.isNotBlank()) {
                        Text(countdown, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                    }
                }

                ZunoGlassHairline()

                val podiumOrdered: List<LeagueStanding> = when (top3.size) {
                    3 -> listOf(top3[1], top3[0], top3[2])
                    2 -> listOf(top3[1], top3[0])
                    else -> top3
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZunoSpace.s4),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    podiumOrdered.forEach { entry ->
                        PodiumColumn(
                            initial = entry.initial,
                            imageUrl = entry.imageUrl,
                            label = "${entry.rank} · ${entry.name.substringBefore(" ")}",
                            xp = "%,d XP".format(entry.xp),
                            isFirst = entry.rank == 1,
                        )
                    }
                }
            }
        }

        item(key = "standings") {
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Standings", style = ZunoType.LabelSmall, color = ZunoColors.TextSecondary)
                    Text("${data.totalParticipants} active", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                ) {
                    restStandings.forEachIndexed { idx, row ->
                        if (idx > 0) ZunoGlassHairline()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14),
                            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.rank.toString(),
                                style = ZunoType.LabelMedium,
                                color = if (row.isYou) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
                                modifier = Modifier.size(20.dp),
                            )
                            ZunoAvatar(
                                initial = row.initial,
                                imageUrl = row.imageUrl,
                                size = 34.dp,
                                textStyle = ZunoType.LabelSmall,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                                Text(
                                    text = if (row.isYou) "${row.name} (You)" else row.name,
                                    style = ZunoType.BodyMediumStrong,
                                    color = ZunoColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("${row.minutesSpoken} min spoken", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                            }
                            Text(
                                text = "%,d XP".format(row.xp),
                                style = if (row.isYou) ZunoType.BodyMediumStrong else ZunoType.BodySmall,
                                color = if (row.isYou) ZunoColors.TextPrimary else ZunoColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    initial: String,
    imageUrl: String?,
    label: String,
    xp: String,
    isFirst: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
        if (imageUrl != null) {
            ZunoAvatar(
                initial = initial,
                imageUrl = imageUrl,
                size = if (isFirst) 42.dp else 36.dp,
                textStyle = ZunoType.LabelMedium,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(if (isFirst) 42.dp else 36.dp)
                    .then(
                        if (isFirst) Modifier.clip(CircleShape).background(ZunoColors.SurfaceInverse)
                        else Modifier.zunoGlassElevated(CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = ZunoType.LabelMedium,
                    color = if (isFirst) ZunoColors.TextOnInverse else ZunoColors.TextPrimary,
                )
            }
        }
        Text(label, style = ZunoType.FootnoteStrong, color = ZunoColors.TextPrimary)
        Text(xp, style = ZunoType.Micro, color = ZunoColors.TextTertiary)
    }
}
