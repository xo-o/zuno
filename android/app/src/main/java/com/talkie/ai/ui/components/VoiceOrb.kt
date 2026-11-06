package com.talkie.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.model.AgentState
import com.talkie.ai.ui.theme.AmberConnecting
import com.talkie.ai.ui.theme.EmeraldListening
import com.talkie.ai.ui.theme.IndigoPrimary
import com.talkie.ai.ui.theme.PurpleThinking
import com.talkie.ai.ui.theme.SurfaceDark
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.ZincIdle

@Composable
fun VoiceOrb(
    state: AgentState,
    modifier: Modifier = Modifier
) {
    val primaryColor = when (state) {
        AgentState.SPEAKING -> IndigoPrimary
        AgentState.LISTENING -> EmeraldListening
        AgentState.THINKING -> PurpleThinking
        AgentState.CONNECTING -> AmberConnecting
        AgentState.IDLE, AgentState.DISCONNECTED -> ZincIdle
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PlasmaOrb(
            state = state.toPlasmaOrbState(),
            size = 160.dp
        )

        // Status badge pill
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .background(SurfaceDark, RoundedCornerShape(20.dp))
                .border(1.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(primaryColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.label,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(name = "Voice Orb - Speaking", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun VoiceOrbSpeakingPreview() {
    TalkieTheme {
        VoiceOrb(state = AgentState.SPEAKING)
    }
}

@Preview(name = "Voice Orb - Listening", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun VoiceOrbListeningPreview() {
    TalkieTheme {
        VoiceOrb(state = AgentState.LISTENING)
    }
}

@Preview(name = "Voice Orb - Thinking", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun VoiceOrbThinkingPreview() {
    TalkieTheme {
        VoiceOrb(state = AgentState.THINKING)
    }
}

@Preview(name = "Voice Orb - Idle", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun VoiceOrbIdlePreview() {
    TalkieTheme {
        VoiceOrb(state = AgentState.IDLE)
    }
}
