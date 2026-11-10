package com.zuno.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zuno.ai.R
import com.zuno.ai.ui.theme.ZunoColors
import com.zuno.ai.ui.theme.ZunoRadius
import com.zuno.ai.ui.theme.ZunoSpace
import com.zuno.ai.ui.theme.ZunoTheme
import com.zuno.ai.ui.theme.ZunoType

private val DialogShape = RoundedCornerShape(ZunoRadius.xxl)

/**
 * Dialog — centered card over the scrim (Figma "Dialog"): title, optional body and [content] slot,
 * and right-aligned Cancel (secondary pill) + confirm (white pill, or red when [destructive]).
 */
@Composable
fun ZunoDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissLabel: String = stringResource(R.string.common_cancel),
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    /** False for informational dialogs that only have a confirm button. */
    showDismiss: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ZunoDialogCard(
            showDismiss = showDismiss,
            title = title,
            body = body,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = destructive,
            confirmEnabled = confirmEnabled,
            confirmLoading = confirmLoading,
            content = content,
            modifier = modifier.imePadding(),
        )
    }
}

@Composable
private fun ZunoDialogCard(
    title: String,
    body: String?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean,
    confirmEnabled: Boolean,
    confirmLoading: Boolean,
    content: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    showDismiss: Boolean = true,
) {
    Column(
        modifier = modifier
            .padding(horizontal = ZunoSpace.s24)
            .fillMaxWidth()
            .shadow(8.dp, DialogShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(DialogShape)
            .background(ZunoColors.BgElevated)
            .border(1.dp, ZunoColors.BorderSubtle, DialogShape)
            .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, top = ZunoSpace.s24, bottom = ZunoSpace.s20),
        verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
            Text(title, style = ZunoType.Headline, color = ZunoColors.TextPrimary)
            body?.let {
                Text(
                    it,
                    style = ZunoType.BodyMedium,
                    color = ZunoColors.TextSecondary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        content?.invoke()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8, Alignment.End),
        ) {
            if (showDismiss) ZunoButton(dismissLabel, onDismiss, variant = ZunoButtonVariant.Secondary, size = ZunoButtonSize.Medium)
            ZunoButton(
                confirmLabel,
                onConfirm,
                variant = if (destructive) ZunoButtonVariant.Danger else ZunoButtonVariant.Primary,
                size = ZunoButtonSize.Medium,
                enabled = confirmEnabled,
                loading = confirmLoading,
            )
        }
    }
}

/**
 * Dialog · Input — a single text field, prefilled, focused and fully selected (e.g. "Rename chat").
 * Save stays disabled while the text is blank or unchanged.
 */
@Composable
fun ZunoInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 80,
    placeholder: String = "",
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(0, initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val trimmed = value.text.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != initialValue.trim()
    ZunoDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = { if (canSave) onConfirm(trimmed) },
        onDismiss = onDismiss,
        confirmEnabled = canSave,
    ) {
        ZunoTextFieldValue(
            value = value,
            onValueChange = { if (it.text.length <= maxLength) value = it },
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(trimmed) }),
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun ZunoDialogPreview() {
    ZunoTheme {
        ZunoDialogCard(
            title = "Delete chat?",
            body = "“Café en Madrid” and its corrections will be deleted. Words saved to Vocabulary stay. This can’t be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
            confirmEnabled = true,
            confirmLoading = false,
            content = null,
        )
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun ZunoInputDialogPreview() {
    ZunoTheme {
        ZunoDialogCard(
            title = "Rename chat",
            body = null,
            confirmLabel = "Save",
            dismissLabel = "Cancel",
            onConfirm = {},
            onDismiss = {},
            destructive = false,
            confirmEnabled = true,
            confirmLoading = false,
            content = { ZunoTextFieldValue(TextFieldValue("Café en Madrid"), {}) },
        )
    }
}
