package com.zuno.ai.model

/**
 * ElevenLabs Eleven v4 Turbo multilingual tutor voice presets (strict 1:1 with TutorAvatarCatalog).
 */
data class VoiceOption(
    val id: String,
    val tutorId: String,
    val preset: String,
    val name: String,
    val tag: String,
)

object VoiceCatalog {
    val VOICES = listOf(
        VoiceOption(
            id = "DODLEQrClDo8wCz460ld",
            tutorId = "sofia",
            preset = "warm",
            name = "Sofía",
            tag = "ElevenLabs · Warm & friendly",
        ),
        VoiceOption(
            id = "EXAVITQu4vr4xnSDxMaL",
            tutorId = "valeria",
            preset = "calm",
            name = "Valeria",
            tag = "ElevenLabs · Calm & patient",
        ),
        VoiceOption(
            id = "Xb7hH8MSUJpSbSDYk0k2",
            tutorId = "claire",
            preset = "crisp",
            name = "Claire",
            tag = "ElevenLabs · Crisp & clear",
        ),
        VoiceOption(
            id = "TX3LPaxmHKxFdv7VOQHJ",
            tutorId = "mateo",
            preset = "energetic",
            name = "Mateo",
            tag = "ElevenLabs · Energetic",
        ),
        VoiceOption(
            id = "cjVigY5qzO86Huf0OWal",
            tutorId = "lucas",
            preset = "steady",
            name = "Lucas",
            tag = "ElevenLabs · Steady & clear",
        ),
        VoiceOption(
            id = "bIHbv24MWmeRgasZH58o",
            tutorId = "ethan",
            preset = "casual",
            name = "Ethan",
            tag = "ElevenLabs · Casual & relaxed",
        ),
    )

    val DEFAULT_VOICE: VoiceOption = VOICES.first()

    fun forPreset(preset: String?): VoiceOption =
        VOICES.firstOrNull { it.preset == preset || it.tutorId == preset } ?: DEFAULT_VOICE
}
