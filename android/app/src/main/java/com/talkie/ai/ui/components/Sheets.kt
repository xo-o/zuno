package com.talkie.ai.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkie.ai.R
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoRadius
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

private val SheetShape = RoundedCornerShape(topStart = ZunoRadius.xxl, topEnd = ZunoRadius.xxl)

/**
 * Bottom Sheet — elevated surface with a drag handle, Headline title and optional description.
 * Content is laid out in a 12dp-inset column; rows add their own 12dp padding like the design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZunoSheet(
    onDismiss: () -> Unit,
    title: String,
    description: String? = null,
    spacing: Dp = ZunoSpace.s16,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ZunoColors.BgElevated,
        scrimColor = ZunoColors.BgScrim,
        shape = SheetShape,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = ZunoSpace.s8, bottom = ZunoSpace.s4)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(ZunoColors.BorderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = ZunoSpace.s12, end = ZunoSpace.s12, bottom = ZunoSpace.s40),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(
                title,
                style = ZunoType.Headline,
                color = ZunoColors.TextPrimary,
                modifier = Modifier.padding(horizontal = ZunoSpace.s12).heightIn(min = 32.dp).padding(top = ZunoSpace.s4),
            )
            description?.let {
                Text(
                    it,
                    style = ZunoType.BodySmall,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = ZunoSpace.s12),
                )
            }
            content()
        }
    }
}

/** Sheet action — 18dp icon and Body/Medium label (Start now, Edit time & repeat, Delete schedule…). */
@Composable
fun ZunoSheetAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chevron: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(ZunoRadius.md))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoIcon(icon, 18.dp, tint = if (enabled) ZunoColors.IconPrimary else ZunoColors.IconTertiary)
        Text(
            label,
            style = ZunoType.BodyMedium,
            color = if (enabled) ZunoColors.TextPrimary else ZunoColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        if (chevron) ZunoIcon(R.drawable.zuno_ic_t_chevron_right, 18.dp, tint = ZunoColors.IconTertiary)
    }
}

/** Rounded group of [ZunoChoiceRow]s separated by hairlines (Share · visibility). */
@Composable
fun ZunoChoiceGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(ZunoRadius.xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZunoColors.SurfaceDefault)
            .border(1.dp, ZunoColors.BorderSubtle, shape),
        content = content,
    )
}

/** Choice Row — 36dp icon badge, title + description, check when [selected]. */
@Composable
fun ZunoChoiceRow(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(ZunoColors.BorderSubtle))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = ZunoSpace.s16),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(ZunoColors.SurfaceDefault),
                contentAlignment = Alignment.Center,
            ) {
                ZunoIcon(icon, 18.dp, tint = ZunoColors.IconSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZunoSpace.s2)) {
                Text(title, style = ZunoType.BodyMediumStrong, color = ZunoColors.TextPrimary)
                Text(description, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
            }
            if (selected) ZunoIcon(R.drawable.zuno_ic_t_check, 18.dp, tint = ZunoColors.IconPrimary)
        }
    }
}

/** Radio Row — 20dp radio and Body/Medium label (Report reasons). */
@Composable
fun ZunoRadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(ZunoRadius.md))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ZunoSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZunoRadio(selected, size = 20.dp)
        Text(label, style = ZunoType.BodyMedium, color = ZunoColors.TextPrimary)
    }
}

@Composable
fun ZunoRadio(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) ZunoColors.TextPrimary else ZunoColors.BorderStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(size * 0.5f).clip(CircleShape).background(ZunoColors.TextPrimary))
    }
}

/** Filled check badge for a picked list item; an empty ring otherwise. */
@Composable
fun ZunoCheckBadge(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) ZunoColors.SurfaceInverse else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, ZunoColors.BorderStrong, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) ZunoIcon(R.drawable.zuno_ic_t_check, size * 0.6f, tint = ZunoColors.IconOnInverse)
    }
}
