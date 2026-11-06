package com.talkie.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.model.VoiceCatalog
import com.talkie.ai.model.VoiceOption
import com.talkie.ai.ui.theme.BorderDark
import com.talkie.ai.ui.theme.EmeraldListening
import com.talkie.ai.ui.theme.IndigoBubble
import com.talkie.ai.ui.theme.IndigoPrimary
import com.talkie.ai.ui.theme.SurfaceDark
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextMuted
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.TextSecondary

@Composable
fun VoiceSelector(
    selectedVoice: VoiceOption,
    onVoiceSelected: (VoiceOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeLangCode by remember { mutableStateOf("all") }

    val filteredVoices = remember(activeLangCode) {
        if (activeLangCode == "all") {
            VoiceCatalog.VOICES
        } else {
            VoiceCatalog.VOICES.filter { it.language == activeLangCode }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Row 1: Language filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(VoiceCatalog.LANGUAGES, key = { it.code }) { lang ->
                val isLangSelected = lang.code == activeLangCode

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLangSelected) IndigoPrimary.copy(alpha = 0.25f) else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (isLangSelected) IndigoPrimary else BorderDark,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { activeLangCode = lang.code }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = lang.flag, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = lang.label,
                            color = if (isLangSelected) TextPrimary else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Row 2: Filtered voice cards
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(filteredVoices, key = { it.id + it.language }) { voice ->
                val isSelected = voice.id == selectedVoice.id && voice.language == selectedVoice.language

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) IndigoBubble else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) IndigoPrimary else BorderDark,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onVoiceSelected(voice) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = voice.flag, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = voice.name,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(EmeraldListening, CircleShape)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = voice.engineBadge,
                                    color = if (isSelected) IndigoPrimary else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = " · ${voice.tag.split("·").last().trim()}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Voice Selector Preview", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun VoiceSelectorPreview() {
    TalkieTheme {
        VoiceSelector(
            selectedVoice = VoiceCatalog.DEFAULT_VOICE,
            onVoiceSelected = {}
        )
    }
}
