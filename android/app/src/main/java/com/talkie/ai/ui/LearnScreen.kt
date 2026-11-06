package com.talkie.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.R
import com.talkie.ai.ui.theme.ActiveTabBg
import com.talkie.ai.ui.theme.BorderDivider
import com.talkie.ai.ui.theme.BorderHairline
import com.talkie.ai.ui.theme.BorderHighlight
import com.talkie.ai.ui.theme.BorderSubtle
import com.talkie.ai.ui.theme.ButtonPrimaryBg
import com.talkie.ai.ui.theme.ButtonPrimaryText
import com.talkie.ai.ui.theme.CanvasBg
import com.talkie.ai.ui.theme.CardBg
import com.talkie.ai.ui.theme.ControlActiveBg
import com.talkie.ai.ui.theme.ControlBg
import com.talkie.ai.ui.theme.EmeraldActive
import com.talkie.ai.ui.theme.IconMuted
import com.talkie.ai.ui.theme.InterFontFamily
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextBadge
import com.talkie.ai.ui.theme.TextOnPrimary
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.TextSecondary

enum class MainNavTab {
    LEARN,
    FREE_TALK
}

private val NoFontPaddingStyle = TextStyle(
    fontFamily = InterFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * Shared Top Bar Navigation matching updated Figma nodes:
 * - `310:9520` (`FreeTalk`)
 * - `310:9652` (`FreeTalk-Chat Messages`)
 * - `310:9756` (`Learn`)
 *
 * Layout specs (@3x):
 * - Container: `left: 56px, top: 180px, width: 1096px, height: 128px` -> `padding(horizontal = 18.67.dp), height(42.67.dp)`
 * - Left: Profile Button (`88x88` @3x = `29.33.dp` user icon with notification dot in `#E5E5E5`)
 * - Center: Pill Switcher (`720x128` @3x = `240.dp x 42.67.dp`, rounded `68px` = `21.33.dp`, `#222222` fill, `1.5px` = `0.5.dp` `#2E2E2E` border, `12px` = `4.dp` padding, `12px` = `4.dp` gap):
 *   - `Tab · Learn` & `Tab · Free Talk`: `342x104` @3x = `114.dp x 34.67.dp`, rounded `56px` = `17.33.dp`, font size `44px` @3x = `14.67.sp`
 * - Right: Curriculum Button (`88x88` @3x = `29.33.dp` container with `56x56` @3x = `18.67.dp` Units folder+ icon in `#E5E5E5`)
 */
@Composable
fun TopBarNavigation(
    selectedTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit,
    onProfileClick: () -> Unit = {},
    onCurriculumClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLearnActive = selectedTab == MainNavTab.LEARN
    val isFreeTalkActive = selectedTab == MainNavTab.FREE_TALK

    val indicatorOffsetX by animateDpAsState(
        targetValue = if (isLearnActive) 0.dp else 118.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "pillIndicatorOffset"
    )
    val learnTextColor by animateColorAsState(
        targetValue = if (isLearnActive) TextPrimary else TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "learnTextColor"
    )
    val freeTalkTextColor by animateColorAsState(
        targetValue = if (isFreeTalkActive) TextPrimary else TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "freeTalkTextColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.67.dp)
            .height(42.67.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Profile Button (36 x 36 dp, translucent smooth circle)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ControlBg, CircleShape)
                .border(width = 0.67.dp, color = BorderHairline, shape = CircleShape)
                .semantics {
                    contentDescription = "Profile"
                    role = Role.Button
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp, color = Color.White.copy(alpha = 0.2f)),
                    onClick = onProfileClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_profile_button),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }

        // 2. Pill Switcher (230.dp x 42.dp, rounded 100.dp)
        Box(
            modifier = Modifier
                .width(230.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(ControlBg, CircleShape)
                .border(width = 0.67.dp, color = BorderHairline, shape = CircleShape)
                .padding(3.dp)
        ) {
            // Sliding Active Pill Indicator
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffsetX)
                    .width(110.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(ActiveTabBg, CircleShape)
                    .border(width = 0.67.dp, color = Color.White.copy(alpha = 0.08f), shape = CircleShape)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectTab(MainNavTab.LEARN) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Learn",
                        color = learnTextColor,
                        fontSize = 14.sp,
                        fontWeight = if (isLearnActive) FontWeight.SemiBold else FontWeight.Medium,
                        style = NoFontPaddingStyle
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectTab(MainNavTab.FREE_TALK) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Free Talk",
                        color = freeTalkTextColor,
                        fontSize = 14.sp,
                        fontWeight = if (isFreeTalkActive) FontWeight.SemiBold else FontWeight.Medium,
                        style = NoFontPaddingStyle
                    )
                }
            }
        }

        // 3. Curriculum Button (36 x 36 dp, translucent smooth circle)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ControlBg, CircleShape)
                .border(width = 0.67.dp, color = BorderHairline, shape = CircleShape)
                .semantics {
                    contentDescription = "Curriculum Units"
                    role = Role.Button
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp, color = Color.White.copy(alpha = 0.2f)),
                    onClick = onCurriculumClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_units_folder),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class LessonObjectiveSpec(
    val stepNumber: String,
    val objectiveLabel: String,
    val title: String,
    val heroTitleMultiline: String
)

private val DEFAULT_LESSON_OBJECTIVES = listOf(
    LessonObjectiveSpec(
        stepNumber = "01",
        objectiveLabel = "Objetivo 1",
        title = "Encuentra la parada de autobús",
        heroTitleMultiline = "Encuentra la parada\nde autobús"
    ),
    LessonObjectiveSpec(
        stepNumber = "02",
        objectiveLabel = "Objetivo 2",
        title = "Pedir ayuda a locales",
        heroTitleMultiline = "Pedir ayuda\na locales"
    ),
    LessonObjectiveSpec(
        stepNumber = "03",
        objectiveLabel = "Objetivo 3",
        title = "Elige el autobús adecuado",
        heroTitleMultiline = "Elige el autobús\nadecuado"
    ),
    LessonObjectiveSpec(
        stepNumber = "04",
        objectiveLabel = "Objetivo 4",
        title = "Nombra tu destino exacto",
        heroTitleMultiline = "Nombra tu destino\nexacto"
    ),
    LessonObjectiveSpec(
        stepNumber = "05",
        objectiveLabel = "Objetivo 5",
        title = "Saber dónde bajarse",
        heroTitleMultiline = "Saber dónde\nbajarse"
    )
)

/**
 * Learn tab content matching Figma node 310:9747 (`Learn`).
 */
@Composable
fun LearnContent(
    selectedLessonIndex: Int = 0,
    onSelectLesson: (Int, LessonObjectiveSpec) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val lessons = remember { DEFAULT_LESSON_OBJECTIVES }
    val activeIdx = selectedLessonIndex.coerceIn(0, lessons.lastIndex)
    val activeLesson = lessons[activeIdx]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.67.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gap from TopBar bottom to Centered Hero
        Spacer(modifier = Modifier.height(28.dp))

        // 1. Centered Hero
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress Capsule (smooth translucent pill + emerald dot)
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CardBg, CircleShape)
                    .border(width = 0.67.dp, color = BorderHairline, shape = CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.67.dp)
            ) {
                // Emerald active dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(EmeraldActive, CircleShape)
                )
                Text(
                    text = "En progreso · Etapa ${activeIdx + 1} de ${lessons.size}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    style = NoFontPaddingStyle
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Title
            Text(
                text = activeLesson.heroTitleMultiline,
                color = TextPrimary,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = NoFontPaddingStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // 2. Lessons Vertical List (smooth translucent cards stack)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lessons.forEachIndexed { index, lesson ->
                val isSelected = index == activeIdx
                val cardShape = RoundedCornerShape(18.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .clip(cardShape)
                        .background(if (isSelected) ControlActiveBg else CardBg, cardShape)
                        .border(
                            width = 0.67.dp,
                            color = if (isSelected) BorderHighlight else BorderSubtle,
                            shape = cardShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White.copy(alpha = 0.14f)),
                            onClick = { onSelectLesson(index, lesson) }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Step Badge (32 x 32 dp, circle)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ButtonPrimaryBg else ControlBg, CircleShape)
                            .then(
                                if (!isSelected) {
                                    Modifier.border(width = 0.67.dp, color = BorderHairline, shape = CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lesson.stepNumber,
                            color = if (isSelected) ButtonPrimaryText else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = NoFontPaddingStyle
                        )
                    }

                    // Copy column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = lesson.objectiveLabel,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = NoFontPaddingStyle
                        )
                        Text(
                            text = lesson.title,
                            color = if (isSelected) TextPrimary else TextBadge,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = NoFontPaddingStyle
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Standalone Learn Screen matching Figma node 310:9747 (`Learn`).
 */
@Composable
fun LearnScreen(
    onSelectFreeTalk: () -> Unit = {},
    onOpenLessonConversation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedLessonIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TopBarNavigation(
            selectedTab = MainNavTab.LEARN,
            onSelectTab = { tab ->
                if (tab == MainNavTab.FREE_TALK) {
                    onSelectFreeTalk()
                }
            },
            onCurriculumClick = onSelectFreeTalk
        )

        LearnContent(
            selectedLessonIndex = selectedLessonIndex,
            onSelectLesson = { idx, lesson ->
                if (selectedLessonIndex == idx) {
                    onOpenLessonConversation(lesson.title)
                } else {
                    selectedLessonIndex = idx
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(
    name = "Learn Screen · Zuno Clean Balance",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun LearnScreenPreview() {
    TalkieTheme {
        LearnScreen()
    }
}
