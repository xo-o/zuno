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
import com.zuno.ai.data.PeerLearner
import com.zuno.ai.ui.components.ZunoAvatar
import com.zuno.ai.ui.components.ZunoButton
import com.zuno.ai.ui.components.ZunoButtonSize
import com.zuno.ai.ui.components.ZunoButtonVariant
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

private data class LeagueRow(
    val rank: Int,
    val initial: String,
    val name: String,
    val minutesSpoken: Int,
    val xp: Int,
    val isYou: Boolean = false,
)

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

        Box(Modifier.padding(horizontal = ZunoSpace.s24, vertical = ZunoSpace.s6)) {
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
                onFallbackTalk = onOpenChats,
                modifier = Modifier.weight(1f),
            )
        } else {
            LeagueTabBody(
                userName = userName.ifBlank { "You" },
                userInitial = userInitial,
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
    onFallbackTalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = ZunoSpace.s32),
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
                        text = "Visible to $languageName $level–B2 learners",
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
                                name = p.name,
                                subtitle = "${p.languageName} ${p.level} · ${p.activeLabel}",
                                onlineDot = p.openToSpeak,
                                onTalk = { onTalkToPartner(p) },
                            )
                        }
                    } else {
                        PartnerCardRow("P", "Priya S.", "34 / 45 min goal · Spoke 2h ago", false, onFallbackTalk)
                        ZunoGlassHairline()
                        PartnerCardRow("A", "Arjun K.", "$languageName B2 · Spoke 2d ago", false, onFallbackTalk)
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
                        text = "${onlineLearners.size.takeIf { it > 0 } ?: 12} partners",
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
                                name = learner.name,
                                subtitle = if (tags.isNotBlank()) "${learner.languageName} ${learner.level} · $tags" else "${learner.languageName} ${learner.level} · ${learner.activeLabel}",
                                onlineDot = true,
                                onTalk = { onTalkToPartner(learner) },
                            )
                        }
                    } else {
                        PartnerCardRow("C", "Carlos Vega", "$languageName B2 · Travel, Tech", true, onFallbackTalk)
                        ZunoGlassHairline()
                        PartnerCardRow("S", "Sofía Morales", "Native $languageName · Daily life", true, onFallbackTalk)
                        ZunoGlassHairline()
                        PartnerCardRow("M", "Mateo Ruiz", "$languageName C1 · Work, Culture", true, onFallbackTalk)
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerCardRow(
    initial: String,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zunoGlassElevated(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary)
            }
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

@Composable
private fun LeagueTabBody(
    userName: String,
    userInitial: String,
    modifier: Modifier = Modifier,
) {
    val standings = listOf(
        LeagueRow(4, userInitial, "$userName (You)", 186, 1840, isYou = true),
        LeagueRow(5, "J", "Jamie S.", 174, 1790),
        LeagueRow(6, "Y", "Yuki T.", 162, 1655),
        LeagueRow(7, "E", "Elena B.", 149, 1520),
        LeagueRow(8, "R", "Rahul P.", 138, 1410),
    )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s12, bottom = ZunoSpace.s32),
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
                        Text("Your rank · #4", style = ZunoType.TitleMedium, color = ZunoColors.TextPrimary)
                    }
                    Text("3d 14h left", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                }

                ZunoGlassHairline()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZunoSpace.s4),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PodiumColumn("P", "2 · Priya", "2,140 XP", isFirst = false)
                    PodiumColumn("A", "1 · Arjun", "2,560 XP", isFirst = true)
                    PodiumColumn("L", "3 · Lucía", "1,980 XP", isFirst = false)
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
                    Text("Top 10 advance", style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zunoGlassSubtle(RoundedCornerShape(ZunoRadius.xl)),
                ) {
                    standings.forEachIndexed { idx, row ->
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
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .then(
                                        if (row.isYou) Modifier.clip(CircleShape).background(ZunoColors.SurfaceInverse)
                                        else Modifier.zunoGlassElevated(CircleShape),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = row.initial,
                                    style = ZunoType.LabelSmall,
                                    color = if (row.isYou) ZunoColors.TextOnInverse else ZunoColors.TextPrimary,
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                                Text(row.name, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun PodiumColumn(initial: String, label: String, xp: String, isFirst: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
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
        Text(label, style = ZunoType.FootnoteStrong, color = ZunoColors.TextPrimary)
        Text(xp, style = ZunoType.Micro, color = ZunoColors.TextTertiary)
    }
}
