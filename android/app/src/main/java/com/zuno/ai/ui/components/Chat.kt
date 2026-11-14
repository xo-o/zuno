package com.zuno.ai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoType

/** User message — right-aligned, tail bottom-right. */
@Composable
fun ZunoUserBubble(
    message: String,
    modifier: Modifier = Modifier,
    showSender: Boolean = true,
) {
    val shape = RoundedCornerShape(
        topStart = ZunoRadius.xl,
        topEnd = ZunoRadius.xl,
        bottomEnd = ZunoRadius.xs,
        bottomStart = ZunoRadius.xl,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
        if (showSender) {
            Text("You", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
        }
        Box(
            modifier = Modifier
                .clip(shape)
                .background(ZunoColors.SurfaceStrong)
                .border(1.dp, ZunoColors.BorderSubtle, shape)
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s12),
        ) {
            Text(
                message,
                style = ZunoType.BodyMedium,
                color = ZunoColors.TextPrimary,
                modifier = Modifier.widthIn(max = 264.dp),
            )
        }
    }
}

/** Assistant message — full-width card with optional translation, glossary and quick actions. */
@Composable
fun ZunoAssistantBubble(
    message: String,
    modifier: Modifier = Modifier,
    translation: String? = null,
    glossary: List<String> = emptyList(),
    showSender: Boolean = true,
    senderLabel: String = "ZunoAI",
    onListen: (() -> Unit)? = null,
    onExplain: (() -> Unit)? = null,
    contextNote: String? = null,
    onContextClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(
        topStart = ZunoRadius.xs,
        topEnd = ZunoRadius.xl,
        bottomEnd = ZunoRadius.xl,
        bottomStart = ZunoRadius.xl,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
    ) {
        if (showSender) {
            Text(senderLabel, style = ZunoType.Micro, color = ZunoColors.TextTertiary)
        }
        if (!contextNote.isNullOrBlank()) {
            ZunoContextStatus(contextNote, onClick = onContextClick)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(ZunoColors.SurfaceSubtle)
                .border(1.dp, ZunoColors.BorderDefault, shape)
                .padding(ZunoSpace.s16),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        ) {
            Text(message, style = ZunoType.BodyMedium, color = ZunoColors.TextPrimary)
            if (!translation.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ZunoColors.BorderSubtle))
                Text(translation, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary)
            }
            if (glossary.isNotEmpty()) {
                val glossaryShape = RoundedCornerShape(ZunoRadius.md)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(glossaryShape)
                        .background(ZunoColors.SurfaceSubtle)
                        .border(1.dp, ZunoColors.BorderSubtle, glossaryShape)
                        .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s10),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
                ) {
                    glossary.forEach { entry ->
                        Text(entry, style = ZunoType.FootnoteStrong, color = ZunoColors.TextSecondary)
                    }
                }
            }
        }
        if (onListen != null || onExplain != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                onListen?.let { ZunoChip("Listen", it, leadingIcon = R.drawable.zuno_ic_volume) }
                onExplain?.let { ZunoChip("Explain grammar", it, leadingIcon = R.drawable.zuno_ic_sparkles) }
            }
        }
    }
}

/** Inline AI feedback under a user message (grammar, pronunciation). */
@Composable
fun ZunoFeedbackPill(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ZunoRadius.md)
    Row(
        modifier = modifier
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .padding(start = ZunoSpace.s10, end = ZunoSpace.s12, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.Top,
    ) {
        ZunoIcon(R.drawable.zuno_ic_check_feedback, 14.dp, Modifier.padding(top = 1.dp))
        Text(
            text,
            style = ZunoType.FootnoteStrong,
            color = ZunoColors.TextSecondary,
            modifier = Modifier.widthIn(max = 306.dp),
        )
    }
}

/**
 * Bottom input card: optional pending [attachments], a multi-line text area, then a toolbar with
 * attach (+) and voice mode — or send once there is text or an attachment ([sendEnabled] dims it).
 */
@Composable
fun ZunoComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask me anything",
    focusRequester: FocusRequester? = null,
    showAttach: Boolean = true,
    enabled: Boolean = true,
    attachments: (@Composable RowScope.() -> Unit)? = null,
    sendEnabled: Boolean = true,
) {
    val shape = RoundedCornerShape(ZunoRadius.xxl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceStrong)
            .padding(
                start = ZunoSpace.s8,
                end = ZunoSpace.s8,
                top = if (attachments != null) ZunoSpace.s12 else ZunoSpace.s20,
                bottom = ZunoSpace.s8,
            ),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
    ) {
        if (attachments != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ZunoSpace.s4),
                horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                content = attachments,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            maxLines = 6,
            textStyle = ZunoType.BodyLarge.copy(color = ZunoColors.TextPrimary),
            cursorBrush = SolidColor(ZunoColors.TextPrimary),
            keyboardOptions = LearnerKeyboard,
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZunoSpace.s12, vertical = ZunoSpace.s4)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        EllipsizedText(placeholder, ZunoType.BodyLarge, ZunoColors.TextPlaceholder)
                    }
                    inner()
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAttach) {
                ZunoIconButton(
                    icon = R.drawable.zuno_ic_plus,
                    onClick = onAttach,
                    iconSize = 20.dp,
                    contentDescription = "Attach",
                )
            }
            Spacer(Modifier.weight(1f))
            if (value.isNotBlank() || attachments != null) {
                val canSend = enabled && sendEnabled
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .alpha(if (canSend) 1f else 0.4f)
                        .clip(CircleShape)
                        .background(ZunoColors.SurfaceStronger)
                        .border(1.dp, ZunoColors.BorderDefault, CircleShape)
                        .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    ZunoIcon(R.drawable.zuno_ic_arrow_up, 20.dp, contentDescription = "Send")
                }
            } else {
                ZunoIconButton(
                    icon = R.drawable.zuno_ic_mic,
                    onClick = onVoice,
                    contentDescription = "Voice mode",
                )
            }
        }
    }
}

/** Assistant placeholder while the tutor is composing a reply. */
@Composable
fun ZunoTypingBubble(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(
        topStart = ZunoRadius.xs,
        topEnd = ZunoRadius.xl,
        bottomEnd = ZunoRadius.xl,
        bottomStart = ZunoRadius.xl,
    )
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "typingAlpha",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s6)) {
        Text("ZunoAI", style = ZunoType.Micro, color = ZunoColors.TextTertiary)
        Row(
            modifier = Modifier
                .clip(shape)
                .background(ZunoColors.SurfaceSubtle)
                .border(1.dp, ZunoColors.BorderDefault, shape)
                .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s14)
                .alpha(alpha),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4),
        ) {
            repeat(3) { Box(Modifier.size(6.dp).clip(CircleShape).background(ZunoColors.TextSecondary)) }
        }
    }
}

/** "New Free Talk · Just now" divider at the top of a conversation. */
@Composable
fun ZunoSessionStart(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(ZunoColors.BorderDefault))
        Text(label, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
        Box(Modifier.weight(1f).height(1.dp).background(ZunoColors.BorderDefault))
    }
}
