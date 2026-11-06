package com.talkie.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkie.ai.model.AgentState
import com.talkie.ai.ui.theme.BackgroundDark
import com.talkie.ai.ui.theme.BorderDark
import com.talkie.ai.ui.theme.IndigoButton
import com.talkie.ai.ui.theme.IndigoInterrupt
import com.talkie.ai.ui.theme.IndigoInterruptBorder
import com.talkie.ai.ui.theme.RedEndCall
import com.talkie.ai.ui.theme.RedMuted
import com.talkie.ai.ui.theme.RedMutedBorder
import com.talkie.ai.ui.theme.SurfaceDark
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextPrimary

@Composable
fun ControlBar(
    state: AgentState,
    isMuted: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = state != AgentState.DISCONNECTED && state != AgentState.CONNECTING
    val isConnecting = state == AgentState.CONNECTING
    val isAgentSpeaking = state == AgentState.SPEAKING

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Secondary Controls (Mute & Interrupt)
        if (isConnected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isMuted) RedMuted else SurfaceDark)
                        .border(1.dp, if (isMuted) RedMutedBorder else BorderDark, RoundedCornerShape(24.dp))
                        .clickable { onToggleMute() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (isMuted) "🔇 Unmute" else "🎙️ Mute",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Interrupt button
                if (isAgentSpeaking) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(IndigoInterrupt)
                            .border(1.dp, IndigoInterruptBorder, RoundedCornerShape(24.dp))
                            .clickable { onInterrupt() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "✋ Interrupt",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Primary Action Button (Connect / End Call)
        val buttonColor = when {
            isConnected -> RedEndCall
            else -> IndigoButton
        }
        val buttonText = when {
            isConnecting -> "Connecting..."
            isConnected -> "End Call"
            else -> "Start Voice Chat"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(if (isConnecting) 0.6f else 1.0f)
                .clip(RoundedCornerShape(28.dp))
                .background(buttonColor)
                .clickable(enabled = !isConnecting) {
                    if (isConnected) onDisconnect() else onConnect()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonText,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Preview(name = "Control Bar - Disconnected", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun ControlBarDisconnectedPreview() {
    TalkieTheme {
        ControlBar(
            state = AgentState.DISCONNECTED,
            isMuted = false,
            onConnect = {},
            onDisconnect = {},
            onToggleMute = {},
            onInterrupt = {}
        )
    }
}

@Preview(name = "Control Bar - Speaking", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun ControlBarSpeakingPreview() {
    TalkieTheme {
        ControlBar(
            state = AgentState.SPEAKING,
            isMuted = false,
            onConnect = {},
            onDisconnect = {},
            onToggleMute = {},
            onInterrupt = {}
        )
    }
}
