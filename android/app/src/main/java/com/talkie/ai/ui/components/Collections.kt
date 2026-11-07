package com.talkie.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

/** Top Bar — Type=Back + Title. 64dp, Title/Large, optional trailing action. */
@Composable
fun ZunoBackTitleTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
    trailingDescription: String? = null,
    onTrailing: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(R.drawable.zuno_ic_arrow_left, onBack, size = ZunoIconButtonSize.Large, contentDescription = "Back")
        EllipsizedText(title, ZunoType.TitleLarge, ZunoColors.TextPrimary, Modifier.weight(1f))
        trailingIcon?.let {
            ZunoIconButton(it, onTrailing, size = ZunoIconButtonSize.Large, contentDescription = trailingDescription)
        }
    }
}

/** Inline search field that replaces the title while searching a list. */
@Composable
fun ZunoSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIconButton(R.drawable.zuno_ic_arrow_left, onClose, size = ZunoIconButtonSize.Large, contentDescription = "Close search")
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = ZunoType.BodyLarge.copy(color = ZunoColors.TextPrimary),
            cursorBrush = SolidColor(ZunoColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) EllipsizedText(placeholder, ZunoType.BodyLarge, ZunoColors.TextPlaceholder)
                    inner()
                }
            },
        )
    }
}

/** Rounded container whose rows are separated by 2dp gaps (List Group). */
@Composable
fun ZunoListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(ZunoRadius.xxl)),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2),
        content = content,
    )
}

data class ZunoMenuAction(val label: String, val onClick: () -> Unit)

/** Collection Card — topic / roleplay row with icon badge, two lines and an overflow menu. */
@Composable
fun ZunoCollectionCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    menu: List<ZunoMenuAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZunoColors.SurfaceDefault)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s16, end = ZunoSpace.s12, top = ZunoSpace.s16, bottom = ZunoSpace.s16),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(ZunoRadius.lg)).background(ZunoColors.SurfaceStrong),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
            EllipsizedText(title, ZunoType.Headline, ZunoColors.TextPrimary)
            EllipsizedText(subtitle, ZunoType.BodySmall, ZunoColors.TextSecondary)
        }
        ZunoOverflowMenu(menu, size = ZunoIconButtonSize.Small)
    }
}

/** Three-dots button that opens a dropdown of [actions]. */
@Composable
fun ZunoOverflowMenu(
    actions: List<ZunoMenuAction>,
    modifier: Modifier = Modifier,
    size: ZunoIconButtonSize = ZunoIconButtonSize.Medium,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ZunoIconButton(
            icon = R.drawable.zuno_ic_more_vertical,
            onClick = { open = true },
            size = size,
            contentDescription = "More options",
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = ZunoColors.BgElevated,
            shape = RoundedCornerShape(ZunoRadius.lg),
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, style = ZunoType.BodyMedium, color = ZunoColors.TextPrimary) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** Extended FAB — inverse pill with a plus icon. */
@Composable
fun ZunoFab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(56.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(ZunoColors.SurfaceInverse)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ZunoSpace.s20, end = ZunoSpace.s24),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_plus_fab, 24.dp)
        Text(label, style = ZunoType.Headline, color = ZunoColors.TextOnInverse, maxLines = 1)
    }
}

/** Empty State — round icon badge, title and description. */
@Composable
fun ZunoEmptyState(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(ZunoColors.SurfaceStrong),
            contentAlignment = Alignment.Center,
        ) {
            ZunoIcon(icon, 22.dp)
        }
        Box(Modifier.height(ZunoSpace.s16))
        Text(title, style = ZunoType.Headline, color = ZunoColors.TextPrimary, textAlign = TextAlign.Center)
        Box(Modifier.height(ZunoSpace.s8))
        Text(description, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/** Five-segment lesson progress (6dp tall, 4dp gaps). */
@Composable
fun ZunoSegmentedProgress(total: Int, completed: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s4)) {
        repeat(total.coerceAtLeast(1)) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (index < completed) ZunoColors.SurfaceInverse else ZunoColors.SurfaceStrong)
            )
        }
    }
}

/** Live typing tip above the composer. */
@Composable
fun ZunoTutorHint(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ZunoRadius.md)
    Row(
        modifier = modifier
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderSubtle, shape)
            .padding(start = ZunoSpace.s10, end = ZunoSpace.s12, top = ZunoSpace.s8, bottom = ZunoSpace.s8),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(R.drawable.zuno_ic_sparkles, 14.dp)
        Text(text, style = ZunoType.FootnoteStrong, color = ZunoColors.TextSecondary, modifier = Modifier.widthIn(max = 306.dp))
    }
}

/** Suggestion chips that wrap onto multiple lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZunoChipGroup(labels: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        labels.forEach { label -> ZunoChip(label, onClick = { onSelect(label) }) }
    }
}

@Composable
fun ZunoSpinner(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = ZunoColors.TextPrimary) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeWidth = 2.dp,
    )
}

/** Centered spinner or error-with-retry for list screens. */
@Composable
fun ZunoLoadState(error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZunoSpace.s16)) {
        if (error == null) {
            ZunoSpinner(size = 24.dp)
        } else {
            Text(error, style = ZunoType.BodySmall, color = ZunoColors.TextSecondary, textAlign = TextAlign.Center)
            ZunoButton("Try again", onRetry, variant = ZunoButtonVariant.Secondary, size = ZunoButtonSize.Medium)
        }
    }
}

/** Keyboard options for target-language input: no autocorrect, so mistakes stay visible to the tutor. */
val LearnerKeyboard = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send)
