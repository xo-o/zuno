package com.zuno.ai.model

enum class AgentState(val label: String) {
    DISCONNECTED("Ready to chat"),
    CONNECTING("Connecting to LiveKit..."),
    IDLE("Ready to chat"),
    LISTENING("Listening to you..."),
    THINKING("Thinking..."),
    SPEAKING("Zuno is speaking...")
}
