package com.talkie.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.talkie.ai.data.Levels
import com.talkie.ai.data.Profile
import com.talkie.ai.data.TargetLanguage
import com.talkie.ai.ui.components.ZunoAvatar
import com.talkie.ai.ui.components.ZunoButton
import com.talkie.ai.ui.components.ZunoButtonVariant
import com.talkie.ai.ui.components.ZunoSectionHeader
import com.talkie.ai.ui.theme.ZunoColors
import com.talkie.ai.ui.theme.ZunoSpace
import com.talkie.ai.ui.theme.ZunoType

/** Account & learning preferences: language being learned, CEFR level, sign out. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountSheet(
    profile: Profile,
    onDismiss: () -> Unit,
    onLanguage: (TargetLanguage) -> Unit,
    onLevel: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ZunoColors.BgElevated,
        scrimColor = ZunoColors.BgScrim,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = ZunoSpace.s24, end = ZunoSpace.s24, bottom = ZunoSpace.s24),
            verticalArrangement = Arrangement.spacedBy(ZunoSpace.s20),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s12), verticalAlignment = Alignment.CenterVertically) {
                ZunoAvatar(profile.displayName.take(1).uppercase().ifBlank { "Z" })
                Column {
                    Text(profile.displayName, style = ZunoType.Headline, color = ZunoColors.TextPrimary)
                    Text(profile.email, style = ZunoType.FootnoteRegular, color = ZunoColors.TextTertiary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                ZunoSectionHeader(title = "I’m learning")
                Row(horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                    TargetLanguage.entries.forEach { language ->
                        Choice(language.label, selected = language == profile.targetLanguage) { onLanguage(language) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8)) {
                ZunoSectionHeader(title = "My level")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                    verticalArrangement = Arrangement.spacedBy(ZunoSpace.s8),
                ) {
                    Levels.forEach { level -> Choice(level, selected = level == profile.level) { onLevel(level) } }
                }
            }
            ZunoButton(
                label = "Sign out",
                onClick = onSignOut,
                variant = ZunoButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth().padding(top = ZunoSpace.s8),
            )
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = ZunoType.LabelMedium,
        color = if (selected) ZunoColors.TextOnInverse else ZunoColors.TextPrimary,
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) ZunoColors.SurfaceInverse else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else ZunoColors.BorderDefault, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ZunoSpace.s16, vertical = ZunoSpace.s8),
    )
}
