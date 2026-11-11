package com.zuno.ai.model

/**
 * Cartesia Sonic-3 multilingual tutor voice presets.
 * Each voice speaks both the learner's native language and target language with the same vocal tone.
 */
data class VoiceOption(
    val id: String,
    val preset: String,
    val name: String,
    val tag: String,
)

object VoiceCatalog {
    val VOICES = listOf(
        VoiceOption(
            id = "ec1e269e-9ca0-402f-8a18-58e0e022355a",
            preset = "warm",
            name = "Ariana",
            tag = "Cartesia · Warm & friendly",
        ),
        VoiceOption(
            id = "ae823354-f9be-4aef-8543-f569644136b4",
            preset = "calm",
            name = "Mariana",
            tag = "Cartesia · Calm & clear",
        ),
        VoiceOption(
            id = "4df027cb-2920-4a1f-8c34-f21529d5c3fe",
            preset = "energetic",
            name = "Carson",
            tag = "Cartesia · Energetic",
        ),
    )

    val DEFAULT_VOICE: VoiceOption = VOICES.first()

    fun forPreset(preset: String?): VoiceOption =
        VOICES.firstOrNull { it.preset == preset } ?: DEFAULT_VOICE
}
