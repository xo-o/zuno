package com.talkie.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkie.ai.model.AgentState
import com.talkie.ai.model.ChatMessage
import com.talkie.ai.model.Speaker
import com.talkie.ai.model.VoiceCatalog
import com.talkie.ai.model.VoiceOption
import com.talkie.ai.ui.components.ControlBar
import com.talkie.ai.ui.components.TranscriptList
import com.talkie.ai.ui.components.VoiceOrb
import com.talkie.ai.ui.components.VoiceSelector
import com.talkie.ai.ui.theme.BackgroundDark
import com.talkie.ai.ui.theme.BorderDark
import com.talkie.ai.ui.theme.EmeraldListening
import com.talkie.ai.ui.theme.RedErrorBg
import com.talkie.ai.ui.theme.RedErrorBorder
import com.talkie.ai.ui.theme.RedErrorText
import com.talkie.ai.ui.theme.SurfaceDark
import com.talkie.ai.ui.theme.TalkieTheme
import com.talkie.ai.ui.theme.TextMuted
import com.talkie.ai.ui.theme.TextPrimary
import com.talkie.ai.ui.theme.TextSecondary
import com.talkie.ai.viewmodel.TalkieViewModel

@Composable
fun TalkieScreen(
    viewModel: TalkieViewModel,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val roomName by viewModel.roomName.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()

    TalkieContent(
        agentState = agentState,
        messages = messages,
        isMuted = isMuted,
        errorMessage = errorMessage,
        roomName = roomName,
        selectedVoice = selectedVoice,
        latencyMs = latencyMs,
        onConnect = { viewModel.connect() },
        onDisconnect = { viewModel.disconnect() },
        onToggleMute = { viewModel.toggleMute() },
        onInterrupt = { viewModel.interrupt() },
        onSelectVoice = { viewModel.selectVoice(it) },
        modifier = modifier
    )
}

@Composable
fun TalkieContent(
    agentState: AgentState,
    messages: List<ChatMessage>,
    isMuted: Boolean,
    errorMessage: String?,
    roomName: String,
    selectedVoice: VoiceOption,
    latencyMs: Int?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onSelectVoice: (VoiceOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = agentState != AgentState.DISCONNECTED && agentState != AgentState.CONNECTING

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Talkie AI",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Real-time Voice Conversation",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Latency Badge Pill (if available)
                if (latencyMs != null && isConnected) {
                    Box(
                        modifier = Modifier
                            .background(SurfaceDark, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚡ ${latencyMs}ms",
                            color = EmeraldListening,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Connection Status Pill
                Box(
                    modifier = Modifier
                        .background(SurfaceDark, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isConnected) EmeraldListening else TextMuted,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val statusText = when {
                            isConnected && roomName.isNotBlank() -> "Room: ${roomName.take(10)}..."
                            isConnected -> "Live"
                            else -> "Offline"
                        }
                        Text(
                            text = statusText,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Voice Selector Row (Flux & Cartesia)
        VoiceSelector(
            selectedVoice = selectedVoice,
            onVoiceSelected = onSelectVoice
        )

        // Error message banner
        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .background(RedErrorBg, RoundedCornerShape(10.dp))
                    .border(1.dp, RedErrorBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = RedErrorText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Animated Voice Orb
        VoiceOrb(state = agentState)

        // Live Transcripts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            TranscriptList(messages = messages)
        }

        // Controls
        ControlBar(
            state = agentState,
            isMuted = isMuted,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onToggleMute = onToggleMute,
            onInterrupt = onInterrupt
        )
    }
}

@Preview(name = "Talkie Screen - Live Preview", showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun TalkieScreenPreview() {
    TalkieTheme {
        TalkieContent(
            agentState = AgentState.SPEAKING,
            messages = listOf(
                ChatMessage("1", Speaker.USER, "Hey Nova, how does real-time voice latency compare to React Native?", true),
                ChatMessage("2", Speaker.AGENT, "With Kotlin and Jetpack Compose, audio pipelines run directly on native Android threads with zero bridge overhead!", true)
            ),
            isMuted = false,
            errorMessage = null,
            roomName = "conversational-room",
            selectedVoice = VoiceCatalog.DEFAULT_VOICE,
            latencyMs = 280,
            onConnect = {},
            onDisconnect = {},
            onToggleMute = {},
            onInterrupt = {},
            onSelectVoice = {}
        )
    }
}
