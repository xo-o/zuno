package com.zuno.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.zuno.ai.R
import com.zuno.ai.data.ReportReason
import com.zuno.ai.ui.theme.ZunoSpace

/**
 * Share — "Only me" / "Public access" visibility and Share / Copy link actions, which stay disabled
 * until the chat is public and its link is known.
 */
@Composable
fun ZunoShareSheet(
    isPublic: Boolean,
    shareUrl: String?,
    busy: Boolean,
    onSelectPublic: (Boolean) -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.share_title),
    onlyMeDescription: String = stringResource(R.string.share_only_me_desc),
    publicTitle: String = stringResource(R.string.share_public),
    publicDescription: String = stringResource(R.string.share_public_desc),
    linkSubtitle: String? = null,
) {
    ZunoSheet(onDismiss = onDismiss, title = title) {
        ZunoChoiceGroup(Modifier.padding(horizontal = ZunoSpace.s12)) {
            ZunoChoiceRow(
                icon = R.drawable.zuno_ic_t_lock,
                title = stringResource(R.string.share_only_me),
                description = onlyMeDescription,
                selected = !isPublic,
                onClick = { if (isPublic && !busy) onSelectPublic(false) },
            )
            ZunoChoiceRow(
                icon = R.drawable.zuno_ic_t_globe,
                title = publicTitle,
                description = publicDescription,
                selected = isPublic,
                onClick = { if (!isPublic && !busy) onSelectPublic(true) },
                showDivider = true,
            )
        }
        if (isPublic && !linkSubtitle.isNullOrBlank()) {
            androidx.compose.material3.Text(
                text = linkSubtitle,
                style = com.zuno.ai.ui.theme.ZunoType.FootnoteRegular,
                color = com.zuno.ai.ui.theme.ZunoColors.TextSecondary,
                modifier = Modifier.padding(horizontal = ZunoSpace.s16),
            )
        }
        val ready = isPublic && shareUrl != null && !busy
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = ZunoSpace.s12, end = ZunoSpace.s12, top = ZunoSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
        ) {
            ZunoButton(
                label = stringResource(R.string.share_action),
                onClick = onShare,
                variant = ZunoButtonVariant.Secondary,
                leadingIcon = R.drawable.zuno_ic_t_share,
                leadingIconTint = com.zuno.ai.ui.theme.ZunoColors.IconPrimary,
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            ZunoButton(
                label = stringResource(R.string.share_copy_link),
                onClick = onCopyLink,
                leadingIcon = R.drawable.zuno_ic_t_copy,
                leadingIconTint = com.zuno.ai.ui.theme.ZunoColors.IconOnInverse,
                enabled = ready,
                loading = busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Report chat — one reason (required) and optional details. */
@Composable
fun ZunoReportSheet(onSubmit: (ReportReason, String) -> Unit, onDismiss: () -> Unit, busy: Boolean = false) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var details by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    ZunoSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.report_title),
        description = stringResource(R.string.report_description),
        spacing = ZunoSpace.s12,
    ) {
        Column(Modifier.padding(horizontal = ZunoSpace.s12)) {
            ReportReason.entries.forEach { item ->
                ZunoRadioRow(label = stringResource(item.label()), selected = item == reason, onClick = { reason = item })
            }
        }
        ZunoTextFieldValue(
            value = details,
            onValueChange = { if (it.text.length <= 1000) details = it },
            placeholder = stringResource(R.string.report_details_hint),
            singleLine = false,
            minHeight = 104.dp,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.padding(start = ZunoSpace.s12, end = ZunoSpace.s12, top = ZunoSpace.s4),
        )
        ZunoButton(
            label = stringResource(R.string.report_submit),
            onClick = { reason?.let { onSubmit(it, details.text) } },
            enabled = reason != null,
            loading = busy,
            modifier = Modifier.fillMaxWidth().padding(start = ZunoSpace.s12, end = ZunoSpace.s12, top = ZunoSpace.s4),
        )
    }
}

private fun ReportReason.label(): Int = when (this) {
    ReportReason.WrongCorrection -> R.string.report_reason_wrong_correction
    ReportReason.WrongLanguageLevel -> R.string.report_reason_wrong_level
    ReportReason.Offensive -> R.string.report_reason_offensive
    ReportReason.Unsafe -> R.string.report_reason_unsafe
    ReportReason.Other -> R.string.report_reason_other
}
