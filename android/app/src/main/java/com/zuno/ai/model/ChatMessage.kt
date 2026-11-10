package com.zuno.ai.model

enum class Speaker {
    USER,
    AGENT
}

data class ChatMessage(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val isFinal: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
