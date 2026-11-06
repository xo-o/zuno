package com.talkie.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.ui.theme.ActiveTabBg
import com.talkie.ai.ui.theme.BorderDivider
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

private val SpeakerLabelColor = TextMuted
private val SubtleBorder07 = BorderHairline
private val ActionIconStrokeColor = TextPrimary

private val NoFontPaddingStyle = TextStyle(
    fontFamily = InterFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

private val UserBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 6.dp,
    bottomEnd = 18.dp,
    bottomStart = 18.dp
)

@Composable
private fun MatteSurface(
    fillColor: Color,
    cornerRadiusDp: Dp = 0.dp,
    shape: Shape = RoundedCornerShape(cornerRadiusDp),
    borderWidth: Dp = 0.67.dp,
    borderColor: Color = BorderHairline,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
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
private fun VectorPathIcon(
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

// Exact 34x34 viewBox paths from Figma node 308:8983 & 308:8991
private val PATHS_VOLUME_ACTION = listOf(
    "M15.5833 7.08333L8.5 12.75H2.83333V21.25H8.5L15.5833 26.9167V7.08333Z",
    "M22.015 11.985C23.343 13.3133 24.089 15.1147 24.089 16.9929C24.089 18.8711 23.343 20.6726 22.015 22.0008",
    "M27.0158 6.98417C29.6717 9.64081 31.1637 13.2435 31.1637 17C31.1637 20.7564 29.6717 24.3592 27.0158 27.0158"
)

private val PATHS_SPARKLES_ACTION = listOf(
    "M14.0774 21.9583C13.9509 21.4681 13.6954 21.0206 13.3374 20.6626C12.9794 20.3046 12.5319 20.0491 12.0417 19.9226L3.35042 17.6814C3.20214 17.6393 3.07163 17.55 2.9787 17.427C2.88577 17.3041 2.83549 17.1541 2.83549 17C2.83549 16.8459 2.88577 16.6959 2.9787 16.573C3.07163 16.45 3.20214 16.3607 3.35042 16.3186L12.0417 14.076C12.5318 13.9496 12.9791 13.6943 13.3371 13.3366C13.6951 12.9788 13.9507 12.5317 14.0774 12.0417L16.3186 3.35042C16.3602 3.20155 16.4495 3.0704 16.5726 2.97698C16.6958 2.88355 16.8461 2.83298 17.0007 2.83298C17.1553 2.83298 17.3056 2.88355 17.4288 2.97698C17.552 3.0704 17.6412 3.20155 17.6828 3.35042L19.9226 12.0417C20.0491 12.5319 20.3046 12.9794 20.6626 13.3374C21.0206 13.6954 21.4681 13.9509 21.9583 14.0774L30.6496 16.3172C30.799 16.3584 30.9308 16.4475 31.0248 16.5709C31.1187 16.6942 31.1696 16.845 31.1696 17C31.1696 17.155 31.1187 17.3058 31.0248 17.4291C30.9308 17.5525 30.799 17.6416 30.6496 17.6828L21.9583 19.9226C21.4681 20.0491 21.0206 20.3046 20.6626 20.6626C20.3046 21.0206 20.0491 21.4681 19.9226 21.9583L17.6814 30.6496C17.6398 30.7984 17.5505 30.9296 17.4274 31.023C17.3042 31.1164 17.1539 31.167 16.9993 31.167C16.8447 31.167 16.6944 31.1164 16.5712 31.023C16.448 30.9296 16.3588 30.7984 16.3172 30.6496L14.0774 21.9583Z"
)

// Exact 52x52 viewBox paths from Figma node 308:8998
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

private sealed interface ConversationTurn {
    val id: String

    data class UserTurn(
        override val id: String,
        val text: String,
        val feedback: String? = null
    ) : ConversationTurn

    data class AiTutorTurn(
        override val id: String,
        val spanishText: String,
        val englishTranslation: String,
        val vocabHighlight: String? = null,
        val showActions: Boolean = true
    ) : ConversationTurn
}

/**
 * FreeTalk Chat Messages screen matching Figma node 308:8917 (`FreeTalk-Chat Messages`).
 */
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    onStartVoice: () -> Unit,
    onSwitchToLearn: (() -> Unit)? = null,
    topicTitle: String = "Roleplay · Café",
    languageSubtitle: String = "Spanish · B1",
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var selectedTab by remember { mutableStateOf(MainNavTab.FREE_TALK) }
    var selectedLessonIndex by remember { mutableIntStateOf(0) }

    val turns = remember {
        mutableStateListOf<ConversationTurn>(
            ConversationTurn.UserTurn(
                id = "u1",
                text = "¡Hola! Quiero pedir un café con leche y un croissant, por favor.",
                feedback = "✓ Natural Spanish · Native tip: \"¿Me pones un café con leche?\""
            ),
            ConversationTurn.AiTutorTurn(
                id = "a1",
                spanishText = "¡Claro que sí! ¿Lo quieres para tomar aquí o para llevar? También tenemos croissants recién horneados.",
                englishTranslation = "Of course! Would you like it for here or to go? We also have freshly baked croissants.",
                vocabHighlight = "para llevar = to go   ·   recién horneado = freshly baked",
                showActions = true
            ),
            ConversationTurn.UserTurn(
                id = "u2",
                text = "Para llevar, por favor. ¿Cuánto cuesta en total?",
                feedback = "✓ 96% Pronunciation · Great rhythm on \"¿Cuánto cuesta?\""
            )
        )
    }

    var composerText by remember { mutableStateOf("") }

    fun sendUserMessage(messageText: String) {
        val trimmed = messageText.trim().removeSurrounding("\"")
        if (trimmed.isBlank()) return
        val nextId = "u_${turns.size + 1}"
        turns.add(
            ConversationTurn.UserTurn(
                id = nextId,
                text = trimmed,
                feedback = "✓ Clear phrasing · Natural conversational tone"
            )
        )
        val replySpanish = if (trimmed.contains("tarjeta", ignoreCase = true)) {
            "Son cuatro euros con cincuenta. Sí, claro, puedes pagar con tarjeta aquí mismo."
        } else {
            "¡Perfecto! Aquí tienes tu café con leche y tu croissant. ¡Que tengas un buen día!"
        }
        val replyEnglish = if (trimmed.contains("tarjeta", ignoreCase = true)) {
            "That's €4.50. Yes, of course, you can pay by card right here."
        } else {
            "Perfect! Here is your coffee with milk and your croissant. Have a great day!"
        }
        turns.add(
            ConversationTurn.AiTutorTurn(
                id = "a_${turns.size + 1}",
                spanishText = replySpanish,
                englishTranslation = replyEnglish,
                vocabHighlight = "con tarjeta = by card   ·   aquí mismo = right here",
                showActions = true
            )
        )
        composerText = ""
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBg)
            .systemBarsPadding()
            .imePadding()
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Shared Top Bar Navigation (310:9652: Profile Button + [ Learn | Free Talk ] Pill Switcher + Curriculum Button)
        TopBarNavigation(
            selectedTab = selectedTab,
            onSelectTab = { tab ->
                if (tab == MainNavTab.LEARN) {
                    if (onSwitchToLearn != null) {
                        onSwitchToLearn()
                    } else {
                        selectedTab = MainNavTab.LEARN
                    }
                } else {
                    if (selectedTab == MainNavTab.FREE_TALK) {
                        onBack()
                    } else {
                        selectedTab = MainNavTab.FREE_TALK
                    }
                }
            },
            onProfileClick = onBack,
            onCurriculumClick = {
                if (onSwitchToLearn != null) {
                    onSwitchToLearn()
                } else {
                    selectedTab = if (selectedTab == MainNavTab.LEARN) MainNavTab.FREE_TALK else MainNavTab.LEARN
                }
            }
        )

        if (selectedTab == MainNavTab.LEARN) {
            LearnContent(
                selectedLessonIndex = selectedLessonIndex,
                onSelectLesson = { idx, _ ->
                    if (selectedLessonIndex == idx) {
                        selectedTab = MainNavTab.FREE_TALK
                    } else {
                        selectedLessonIndex = idx
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            // 2. Scrollable Conversation List (308:8971 .. 308:8997)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 17.67.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(turns, key = { it.id }) { turn ->
                    when (turn) {
                        is ConversationTurn.UserTurn -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                // "You" label (308:8971 / 308:8993: 30px @3x = 10.sp, Medium, rgba(255,255,255,0.46))
                                Text(
                                    text = "You",
                                    color = SpeakerLabelColor,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.05).sp,
                                    style = NoFontPaddingStyle,
                                    modifier = Modifier.padding(end = 4.dp, bottom = 5.dp)
                                )

                                // User message bubble (custom asymmetric smooth shape)
                                MatteSurface(
                                    fillColor = ControlActiveBg,
                                    shape = UserBubbleShape,
                                    borderWidth = 0.67.dp,
                                    borderColor = BorderHairline,
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = turn.text,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.Normal,
                                        letterSpacing = 0.sp,
                                        style = NoFontPaddingStyle,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp
                                        )
                                    )
                                }

                                // Grammar / Pronunciation feedback pill
                                if (!turn.feedback.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    MatteSurface(
                                        fillColor = CardBg,
                                        cornerRadiusDp = 14.dp,
                                        borderWidth = 0.67.dp,
                                        borderColor = BorderSubtle,
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Text(
                                            text = turn.feedback,
                                            color = TextBadge,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = (-0.02).sp,
                                            style = NoFontPaddingStyle,
                                            modifier = Modifier.padding(
                                                horizontal = 14.dp,
                                                vertical = 8.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        is ConversationTurn.AiTutorTurn -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                // "AI Tutor" label
                                Text(
                                    text = "AI Tutor",
                                    color = SpeakerLabelColor,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.sp,
                                    style = NoFontPaddingStyle,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                                )

                                // Assistant message card
                                MatteSurface(
                                    fillColor = ControlBg,
                                    cornerRadiusDp = 20.dp,
                                    borderWidth = 0.67.dp,
                                    borderColor = BorderHairline,
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 18.dp,
                                            vertical = 16.dp
                                        )
                                    ) {
                                        // Spanish text
                                        Text(
                                            text = turn.spanishText,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = (-0.05).sp,
                                            style = NoFontPaddingStyle
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Translation divider hairline
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(0.67.dp)
                                                .background(BorderDivider)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // English translation
                                        Text(
                                            text = turn.englishTranslation,
                                            color = TextSecondary,
                                            fontSize = 13.5.sp,
                                            lineHeight = 20.sp,
                                            fontWeight = FontWeight.Normal,
                                            letterSpacing = 0.sp,
                                            style = NoFontPaddingStyle
                                        )

                                        // Vocab highlight
                                        if (!turn.vocabHighlight.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            MatteSurface(
                                                fillColor = CardBg,
                                                cornerRadiusDp = 10.dp,
                                                borderWidth = 0.67.dp,
                                                borderColor = BorderSubtle,
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = turn.vocabHighlight,
                                                    color = TextBadge,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    letterSpacing = 0.sp,
                                                    style = NoFontPaddingStyle,
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Listen & Explain grammar action chips
                                if (turn.showActions) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Listen action
                                        MatteSurface(
                                            fillColor = CardBg,
                                            shape = CircleShape,
                                            borderWidth = 0.67.dp,
                                            borderColor = BorderHairline,
                                            onClick = onStartVoice,
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                VectorPathIcon(
                                                    viewBoxSize = 34f,
                                                    strokeWidthViewBox = 2.83333f,
                                                    paths = PATHS_VOLUME_ACTION,
                                                    size = 14.dp,
                                                    tint = ActionIconStrokeColor
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Listen",
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = (-0.04).sp,
                                                    style = NoFontPaddingStyle
                                                )
                                            }
                                        }

                                        // Explain grammar action
                                        MatteSurface(
                                            fillColor = CardBg,
                                            shape = CircleShape,
                                            borderWidth = 0.67.dp,
                                            borderColor = BorderHairline,
                                            onClick = {
                                                sendUserMessage("¿Puedes explicarme \"para tomar aquí o para llevar\"?")
                                            },
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                VectorPathIcon(
                                                    viewBoxSize = 34f,
                                                    strokeWidthViewBox = 2.83333f,
                                                    paths = PATHS_SPARKLES_ACTION,
                                                    size = 14.dp,
                                                    tint = ActionIconStrokeColor
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Explain grammar",
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = (-0.04).sp,
                                                    style = NoFontPaddingStyle
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Question Composer (floating pill bar, height 54.dp, radius 100.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                MatteSurface(
                    fillColor = ControlBg,
                    cornerRadiusDp = 100.dp,
                    borderWidth = 0.67.dp,
                    borderColor = BorderHairline,
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
                        // Add attachment button
                        MatteSurface(
                            fillColor = CardBg,
                            shape = CircleShape,
                            borderWidth = 0.67.dp,
                            borderColor = BorderSubtle,
                            onClick = { sendUserMessage("¿Me pones un café con leche, por favor?") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            VectorPathIcon(
                                viewBoxSize = 52f,
                                strokeWidthViewBox = 3.17241f,
                                paths = PATHS_PLUS,
                                size = 16.dp,
                                tint = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // Text input ("Ask or pick a topic...")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (composerText.isEmpty()) {
                                Text(
                                    text = "Ask or pick a topic...",
                                    color = TextMuted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = (-0.08).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = NoFontPaddingStyle
                                )
                            }
                            BasicTextField(
                                value = composerText,
                                onValueChange = { composerText = it },
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
                                    onSend = { sendUserMessage(composerText) }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Voice input button
                        MatteSurface(
                            fillColor = ActiveTabBg,
                            shape = CircleShape,
                            borderWidth = 0.67.dp,
                            borderColor = BorderHairline,
                            onClick = {
                                if (composerText.isNotBlank()) {
                                    sendUserMessage(composerText)
                                } else {
                                    onStartVoice()
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            VectorPathIcon(
                                viewBoxSize = 52f,
                                strokeWidthViewBox = 3.17241f,
                                paths = PATHS_AUDIO_LINES,
                                size = 16.dp,
                                tint = TextPrimary,
                                modifier = Modifier.align(Alignment.Center)
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
    name = "FreeTalk Chat Messages · Zuno Clean Balance",
    showBackground = true,
    backgroundColor = 0xFF171717,
    widthDp = 402,
    heightDp = 923
)
@Composable
private fun ConversationScreenPreview() {
    TalkieTheme {
        ConversationScreen(
            onBack = {},
            onStartVoice = {}
        )
    }
}
