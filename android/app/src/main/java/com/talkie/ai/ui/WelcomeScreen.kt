package com.talkie.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.R
import com.talkie.ai.ui.theme.ActiveTabBg
import com.talkie.ai.ui.theme.BorderHairline
import com.talkie.ai.ui.theme.BorderSubtle
import com.talkie.ai.ui.theme.CanvasBg
import com.talkie.ai.ui.theme.CardBg
import com.talkie.ai.ui.theme.ControlActiveBg
import com.talkie.ai.ui.theme.ControlBg
import com.talkie.ai.ui.theme.InterFontFamily
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextBadge
import com.talkie.ai.ui.theme.TextMuted
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.TextSecondary

private val SubtleBorder10 = BorderHairline
private val SubtleBorder08 = BorderSubtle
private val SubtleBorder07 = BorderHairline

private val NoFontPaddingStyle = TextStyle(
    fontFamily = InterFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * Reusable matte control container matching Figma node 308:8785.
 */
@Composable
private fun MatteControlBox(
    fillColor: Color,
    cornerRadiusDp: Dp,
    borderWidth: Dp,
    borderColor: Color,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadiusDp)
    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Color.White.copy(alpha = 0.18f)),
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fillColor, shape)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .then(clickableModifier),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
private fun SvgVectorIcon(
    viewBoxSize: Float,
    strokeWidthViewBox: Float,
    paths: List<String>,
    size: Dp,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val parsedPaths = remember(paths) {
        paths.map { PathParser().parsePathString(it).toPath() }
    }

    Canvas(modifier = modifier.size(size)) {
        val scaleX = this.size.width / viewBoxSize
        val scaleY = this.size.height / viewBoxSize
        val scale = minOf(scaleX, scaleY)

        val matrix = Matrix().apply {
            scale(scaleX, scaleY)
        }

        val stroke = Stroke(
            width = strokeWidthViewBox * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        for (basePath in parsedPaths) {
            val scaledPath = Path().apply {
                addPath(basePath)
                transform(matrix)
            }
            drawPath(
                path = scaledPath,
                color = tint,
                style = stroke
            )
        }
    }
}

// Exact 44x44 viewBox paths from Figma node 308:8890
private val PATHS_MESSAGES_SQUARE = listOf(
    "M25.6667 16.5C25.6667 17.4725 25.2804 18.4051 24.5927 19.0927C23.9051 19.7804 22.9725 20.1667 22 20.1667H11L3.66667 27.5V7.33333C3.66667 5.31667 5.31667 3.66667 7.33333 3.66667H22C22.9725 3.66667 23.9051 4.05298 24.5927 4.74061C25.2804 5.42824 25.6667 6.36087 25.6667 7.33333V16.5Z",
    "M33 16.5H36.6667C37.6391 16.5 38.5718 16.8863 39.2594 17.5739C39.947 18.2616 40.3333 19.1942 40.3333 20.1667V40.3333L33 33H22C21.0275 33 20.0949 32.6137 19.4073 31.9261C18.7196 31.2384 18.3333 30.3058 18.3333 29.3333V27.5"
)

private val PATHS_SPARKLES = listOf(
    "M18.2178 28.4167C18.0542 27.7822 17.7235 27.2032 17.2601 26.7399C16.7968 26.2765 16.2178 25.9458 15.5833 25.7822L4.33583 22.8818C4.14394 22.8274 3.97505 22.7118 3.85479 22.5526C3.73453 22.3935 3.66946 22.1995 3.66946 22C3.66946 21.8005 3.73453 21.6065 3.85479 21.4474C3.97505 21.2882 4.14394 21.1726 4.33583 21.1182L15.5833 18.216C16.2176 18.0525 16.7964 17.7221 17.2597 17.2591C17.723 16.7961 18.0539 16.2175 18.2178 15.5833L21.1182 4.33583C21.1721 4.14318 21.2875 3.97346 21.4469 3.85256C21.6063 3.73165 21.8009 3.66621 22.0009 3.66621C22.201 3.66621 22.3955 3.73165 22.5549 3.85256C22.7143 3.97346 22.8298 4.14318 22.8837 4.33583L25.7822 15.5833C25.9458 16.2178 26.2765 16.7968 26.7399 17.2601C27.2032 17.7235 27.7822 18.0542 28.4167 18.2178L39.6642 21.1163C39.8576 21.1697 40.0282 21.285 40.1497 21.4446C40.2713 21.6043 40.3371 21.7994 40.3371 22C40.3371 22.2006 40.2713 22.3957 40.1497 22.5554C40.0282 22.715 39.8576 22.8303 39.6642 22.8837L28.4167 25.7822C27.7822 25.9458 27.2032 26.2765 26.7399 26.7399C26.2765 27.2032 25.9458 27.7822 25.7822 28.4167L22.8818 39.6642C22.8279 39.8568 22.7125 40.0265 22.5531 40.1474C22.3937 40.2683 22.1991 40.3338 21.9991 40.3338C21.799 40.3338 21.6045 40.2683 21.4451 40.1474C21.2857 40.0265 21.1702 39.8568 21.1163 39.6642L18.2178 28.4167Z",
    "M36.6667 5.5V12.8333",
    "M40.3333 9.16667H33"
)

private val PATHS_VOLUME_2 = listOf(
    "M20.1667 8.62033C20.1663 8.36497 20.0903 8.11544 19.9482 7.90324C19.8062 7.69103 19.6045 7.52566 19.3685 7.42798C19.1326 7.33031 18.873 7.30472 18.6225 7.35443C18.3721 7.40415 18.1419 7.52695 17.9612 7.70733L11.7572 13.9095C11.5177 14.1504 11.2329 14.3413 10.9191 14.4713C10.6054 14.6012 10.2689 14.6677 9.92933 14.6667H5.5C5.01377 14.6667 4.54745 14.8598 4.20364 15.2036C3.85982 15.5475 3.66667 16.0138 3.66667 16.5V27.5C3.66667 27.9862 3.85982 28.4525 4.20364 28.7964C4.54745 29.1402 5.01377 29.3333 5.5 29.3333H9.92933C10.2689 29.3323 10.6054 29.3988 10.9191 29.5287C11.2329 29.6587 11.5177 29.8496 11.7572 30.0905L17.9593 36.2945C18.1401 36.4756 18.3706 36.599 18.6215 36.649C18.8725 36.6991 19.1326 36.6735 19.369 36.5755C19.6054 36.4775 19.8074 36.3116 19.9494 36.0987C20.0914 35.8858 20.167 35.6356 20.1667 35.3797V8.62033Z",
    "M29.3333 16.5C30.5234 18.0867 31.1667 20.0166 31.1667 22C31.1667 23.9834 30.5234 25.9133 29.3333 27.5",
    "M35.5007 33.6673C37.0329 32.1352 38.2483 30.3162 39.0775 28.3143C39.9067 26.3124 40.3335 24.1668 40.3335 22C40.3335 19.8332 39.9067 17.6876 39.0775 15.6857C38.2483 13.6838 37.0329 11.8648 35.5007 10.3327"
)

private val PATHS_LANGUAGES = listOf(
    "M9.16667 14.6667L20.1667 25.6667",
    "M7.33333 25.6667L18.3333 14.6667L22 9.16667",
    "M3.66667 9.16667H25.6667",
    "M12.8333 3.66667H14.6667",
    "M40.3333 40.3333L31.1667 22L22 40.3333",
    "M25.6667 33H36.6667"
)

// Exact 52x52 viewBox paths from Figma node 308:8812
private val PATHS_PLUS = listOf(
    "M10.8319 26H41.1687",
    "M26.0003 10.8316V41.1684"
)

private val PATHS_AUDIO_LINES = listOf(
    "M4.33127 21.6666V28.1666",
    "M12.9986 12.9999V36.8332",
    "M21.666 6.4999V45.4999",
    "M30.3334 17.3332V32.4999",
    "M39.0007 10.8332V38.9999",
    "M47.6681 21.6666V28.1666"
)

private data class QuickActionPillSpec(
    val label: String,
    val paths: List<String>
)

/**
 * Updated Free Talk & Learn Hub Screen matching Figma nodes:
 * - `308:8785` (`FreeTalk`) when Free Talk tab is active
 * - `308:9024` (`Learn`) when Learn tab is active
 */
@Composable
fun WelcomeScreen(
    onStartVoice: () -> Unit,
    onOpenConversation: ((String) -> Unit)? = null,
    initialTab: MainNavTab = MainNavTab.FREE_TALK,
    languageLabel: String = "Spanish · B1",
    onLanguageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var selectedLessonIndex by rememberSaveable { mutableIntStateOf(0) }
    var internalConversationTopic by rememberSaveable { mutableStateOf<String?>(null) }
    var topicInput by remember { mutableStateOf("") }

    fun openConversationTopic(topic: String) {
        val resolvedTopic = if (topic == "Roleplay") "Roleplay · Café" else topic
        if (onOpenConversation != null) {
            onOpenConversation(resolvedTopic)
        } else {
            internalConversationTopic = resolvedTopic
        }
    }

    val activeInternalTopic = internalConversationTopic
    if (activeInternalTopic != null && onOpenConversation == null) {
        ConversationScreen(
            onBack = { internalConversationTopic = null },
            onStartVoice = onStartVoice,
            onSwitchToLearn = {
                internalConversationTopic = null
                selectedTab = MainNavTab.LEARN
            },
            topicTitle = activeInternalTopic,
            languageSubtitle = languageLabel,
            modifier = modifier
        )
        return
    }

    val quickActionPills = remember {
        listOf(
            QuickActionPillSpec(
                label = "Free Talk",
                paths = PATHS_MESSAGES_SQUARE
            ),
            QuickActionPillSpec(
                label = "Roleplay",
                paths = PATHS_SPARKLES
            ),
            QuickActionPillSpec(
                label = "Pronunciation",
                paths = PATHS_VOLUME_2
            ),
            QuickActionPillSpec(
                label = "Grammar",
                paths = PATHS_LANGUAGES
            )
        )
    }

    fun submitTextChat() {
        val topic = topicInput.trim().ifBlank { "Free Talk" }
        topicInput = ""
        openConversationTopic(topic)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
            .systemBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Shared Top Bar Navigation: Profile Button + [ Learn | Free Talk ] Pill Switcher + Curriculum Button
        TopBarNavigation(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onProfileClick = onLanguageClick,
            onCurriculumClick = {
                selectedTab = if (selectedTab == MainNavTab.LEARN) MainNavTab.FREE_TALK else MainNavTab.LEARN
            }
        )

        if (selectedTab == MainNavTab.LEARN) {
            LearnContent(
                selectedLessonIndex = selectedLessonIndex,
                onSelectLesson = { idx, lesson ->
                    if (selectedLessonIndex == idx) {
                        openConversationTopic(lesson.title)
                    } else {
                        selectedLessonIndex = idx
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            // 2. Center Hero Section: Mascot (308:8823, 240x240 @3x = 80dp) + 2-Line Greeting (308:8786)
            Spacer(modifier = Modifier.weight(0.62f))

            // Mascot (308:8823: left 486, top 937, size 240px @3x -> 80.dp)
            Image(
                painter = painterResource(id = R.drawable.ic_agent_mascot),
                contentDescription = "AI Tutor Mascot",
                modifier = Modifier
                    .size(80.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStartVoice
                    )
            )

            // Gap from mascot bottom (937 + 240 = 1177px) to greeting top (1218px) = 41px @3x -> 13.67.dp
            Spacer(modifier = Modifier.height(13.67.dp))

            // Conversational 2-line greeting (308:8786): Inter Regular, 48px @3x = 16.sp, line-height 1.42, #F9F9F9
            Text(
                text = "Hola Noah, what would you\nlike to practice today?",
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 22.7.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.16).sp,
                textAlign = TextAlign.Center,
                style = NoFontPaddingStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 3. Quick Actions Row (smooth pill buttons with subtle translucent border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (pill in quickActionPills) {
                    MatteControlBox(
                        fillColor = CardBg,
                        cornerRadiusDp = 100.dp,
                        borderWidth = 0.67.dp,
                        borderColor = BorderSubtle,
                        onClick = { openConversationTopic(pill.label) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SvgVectorIcon(
                                viewBoxSize = 44f,
                                strokeWidthViewBox = 3.8f,
                                paths = pill.paths,
                                size = 14.dp,
                                tint = TextBadge
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pill.label,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.14).sp,
                                maxLines = 1,
                                style = NoFontPaddingStyle
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Question composer (floating pill bar, height 54.dp, radius 100.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                MatteControlBox(
                    fillColor = ControlBg,
                    cornerRadiusDp = 100.dp,
                    borderWidth = 0.67.dp,
                    borderColor = BorderHairline,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add attachment / start chat button
                        MatteControlBox(
                            fillColor = CardBg,
                            cornerRadiusDp = 100.dp,
                            borderWidth = 0.67.dp,
                            borderColor = BorderSubtle,
                            onClick = { submitTextChat() },
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(38.dp)
                        ) {
                            SvgVectorIcon(
                                viewBoxSize = 52f,
                                strokeWidthViewBox = 3.17241f,
                                paths = PATHS_PLUS,
                                size = 16.dp,
                                tint = TextSecondary
                            )
                        }

                        // Topic / Message input
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (topicInput.isEmpty()) {
                                Text(
                                    text = "Ask or pick a topic...",
                                    color = TextMuted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = (-0.07).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = NoFontPaddingStyle
                                )
                            }
                            BasicTextField(
                                value = topicInput,
                                onValueChange = { topicInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = InterFontFamily,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                cursorBrush = SolidColor(TextPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { submitTextChat() }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Voice input button: pressing triggers voice chat
                        MatteControlBox(
                            fillColor = ActiveTabBg,
                            cornerRadiusDp = 100.dp,
                            borderWidth = 0.67.dp,
                            borderColor = BorderHairline,
                            onClick = onStartVoice,
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(38.dp)
                        ) {
                            SvgVectorIcon(
                                viewBoxSize = 52f,
                                strokeWidthViewBox = 3.17241f,
                                paths = PATHS_AUDIO_LINES,
                                size = 16.dp,
                                tint = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Preview(
    name = "FreeTalk Screen · Zuno Clean Balance",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun WelcomeScreenPreview() {
    TalkieTheme {
        var currentScreen by remember { mutableStateOf("welcome") }
        when (currentScreen) {
            "welcome" -> WelcomeScreen(
                onStartVoice = { currentScreen = "voice" },
                onOpenConversation = { currentScreen = "conversation" }
            )
            "conversation" -> ConversationScreen(
                onBack = { currentScreen = "welcome" },
                onStartVoice = { currentScreen = "voice" }
            )
            "voice" -> VoiceScreen(
                onBack = { currentScreen = "welcome" },
                onClose = { currentScreen = "welcome" }
            )
        }
    }
}
