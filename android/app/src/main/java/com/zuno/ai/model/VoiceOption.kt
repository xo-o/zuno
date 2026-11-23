package com.zuno.ai.model

/**
 * Cartesia Sonic-3.6 multilingual tutor voice presets (strict 1:1 with TutorAvatarCatalog).
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
            id = "ec1e269e-9ca0-402f-8a18-58e0e022355a",
            tutorId = "sofia",
            preset = "warm",
            name = "Sofía",
            tag = "Cartesia · Warm & friendly",
        ),
        VoiceOption(
            id = "ae823354-f9be-4aef-8543-f569644136b4",
            tutorId = "valeria",
            preset = "calm",
            name = "Valeria",
            tag = "Cartesia · Calm & patient",
        ),
        VoiceOption(
            id = "f786b574-daa5-4673-aa0c-cbe3e8534c02",
            tutorId = "claire",
            preset = "crisp",
            name = "Claire",
            tag = "Cartesia · Crisp & clear",
        ),
        VoiceOption(
            id = "4df027cb-2920-4a1f-8c34-f21529d5c3fe",
            tutorId = "mateo",
            preset = "energetic",
            name = "Mateo",
            tag = "Cartesia · Energetic",
        ),
        VoiceOption(
            id = "8c254787-4eb4-4577-bd3d-fb3c273baea2",
            tutorId = "lucas",
            preset = "steady",
            name = "Lucas",
            tag = "Cartesia · Steady & clear",
        ),
        VoiceOption(
            id = "30894953-bcce-41fe-892c-15ce19c843ff",
            tutorId = "ethan",
            preset = "casual",
            name = "Ethan",
            tag = "Cartesia · Casual & relaxed",
        ),
    )

    val DEFAULT_VOICE: VoiceOption = VOICES.first()

    fun forPreset(preset: String?): VoiceOption =
        VOICES.firstOrNull { it.preset == preset || it.tutorId == preset } ?: DEFAULT_VOICE
}
