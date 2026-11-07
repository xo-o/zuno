package com.talkie.ai.ui.zuno.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoRadius
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType

/** Draws a design asset with the colors baked into the Figma export. */
@Composable
fun ZunoIcon(@DrawableRes icon: Int, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

enum class ZunoButtonVariant { Primary, Secondary, Ghost }

enum class ZunoButtonSize(val height: Dp, val horizontalPadding: Dp, val gap: Dp, val iconSize: Dp) {
    Large(52.dp, ZunoSpace.s24, ZunoSpace.s8, 20.dp),
    Medium(44.dp, ZunoSpace.s20, ZunoSpace.s8, 18.dp),
    Small(36.dp, ZunoSpace.s16, ZunoSpace.s6, 16.dp),
}

/** Pill button. Primary = main CTA, Secondary = alternative action, Ghost = low emphasis. */
@Composable
fun ZunoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ZunoButtonVariant = ZunoButtonVariant.Primary,
    size: ZunoButtonSize = ZunoButtonSize.Large,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    loading: Boolean = false,
) {
    val shape = CircleShape
    val container = when (variant) {
        ZunoButtonVariant.Primary -> ZunoColors.SurfaceInverse
        ZunoButtonVariant.Secondary -> ZunoColors.SurfaceSubtle
        ZunoButtonVariant.Ghost -> Color.Transparent
    }
    val contentColor = if (variant == ZunoButtonVariant.Primary) ZunoColors.TextOnInverse else ZunoColors.TextPrimary
    val textStyle = if (size == ZunoButtonSize.Small) ZunoType.LabelSmall else ZunoType.LabelLarge

    Row(
        modifier = modifier
            .alpha(if (enabled || loading) 1f else 0.4f)
            .height(size.height)
            .clip(shape)
            .background(container)
            .then(
                if (variant == ZunoButtonVariant.Secondary) Modifier.border(1.dp, ZunoColors.BorderDefault, shape) else Modifier
            )
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            ZunoSpinner(size = size.iconSize, color = contentColor)
        } else {
            leadingIcon?.let { ZunoIcon(it, size.iconSize) }
        }
        Text(text = label, style = textStyle, color = contentColor, maxLines = 1)
        trailingIcon?.let { ZunoIcon(it, size.iconSize) }
    }
}

enum class ZunoIconButtonStyle { Ghost, Subtle, Outline, Strong }

/** Circular icon-only button. 40dp (Medium) or 32dp (Small). */
@Composable
fun ZunoIconButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ZunoIconButtonStyle = ZunoIconButtonStyle.Ghost,
    small: Boolean = false,
    iconSize: Dp = if (small) 16.dp else 20.dp,
    contentDescription: String? = null,
) {
    val buttonSize = if (small) 32.dp else 40.dp
    val background = when (style) {
        ZunoIconButtonStyle.Ghost -> Color.Transparent
        ZunoIconButtonStyle.Subtle -> ZunoColors.SurfaceDefault
        ZunoIconButtonStyle.Outline -> ZunoColors.SurfaceSubtle
        ZunoIconButtonStyle.Strong -> ZunoColors.SurfaceStronger
    }
    val bordered = style == ZunoIconButtonStyle.Outline || style == ZunoIconButtonStyle.Strong
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, ZunoColors.BorderDefault, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ZunoIcon(icon, iconSize, contentDescription = contentDescription)
    }
}

/** Compact pill for secondary inline actions (Listen, Explain grammar). */
@Composable
fun ZunoChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(ZunoColors.SurfaceSubtle)
            .border(1.dp, ZunoColors.BorderDefault, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZunoSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { ZunoIcon(it, 14.dp) }
        Text(label, style = ZunoType.FootnoteStrong, color = ZunoColors.TextPrimary, maxLines = 1)
    }
}

/** Labelled single-line input: 52dp field, Body/Large text. */
@Composable
fun ZunoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    helper: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(ZunoRadius.lg)

    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
    ) {
        label?.let { Text(it, style = ZunoType.LabelMedium, color = ZunoColors.TextPrimary) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = ZunoType.BodyLarge.copy(color = ZunoColors.TextPrimary),
            cursorBrush = SolidColor(ZunoColors.TextPrimary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(shape)
                        .background(if (focused) ZunoColors.SurfaceDefault else ZunoColors.SurfaceSubtle)
                        .border(1.dp, if (focused) ZunoColors.BorderFocus else ZunoColors.BorderDefault, shape)
                        .padding(horizontal = ZunoSpace.s16),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = ZunoType.BodyLarge, color = ZunoColors.TextPlaceholder, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        helper?.let { Text(it, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary) }
    }
}

/** Hairline separator; pass a label for the auth "or" split. */
@Composable
fun ZunoDivider(modifier: Modifier = Modifier, label: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(ZunoColors.BorderDefault))
        if (label != null) {
            Text(label, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
            Box(Modifier.weight(1f).height(1.dp).background(ZunoColors.BorderDefault))
        }
    }
}

/** Single-line text with ellipsis — shared by list rows. */
@Composable
internal fun EllipsizedText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
