package com.talkie.ai.model

data class VoiceOption(
    val id: String,
    val name: String,
    val provider: String,
    val language: String,
    val flag: String,
    val tag: String
) {
    val engineBadge: String
        get() = when (provider.lowercase()) {
            "breeze", "breezeblue" -> "🌬️ Breeze"
            "cartesia" -> "⚡ Cartesia"
            "gcp", "google" -> "🌐 Google"
            else -> "⚡ Flux"
        }
}

data class LanguageFilter(
    val code: String,
    val label: String,
    val flag: String
)

object VoiceCatalog {

    val LANGUAGES = listOf(
        LanguageFilter("all", "All", "🌐"),
        LanguageFilter("en", "English", "🇺🇸"),
        LanguageFilter("es", "Spanish", "🇪🇸"),
        LanguageFilter("fr", "French", "🇫🇷"),
        LanguageFilter("de", "German", "🇩🇪"),
        LanguageFilter("it", "Italian", "🇮🇹"),
        LanguageFilter("pt", "Portuguese", "🇧🇷"),
        LanguageFilter("ja", "Japanese", "🇯🇵"),
        LanguageFilter("ko", "Korean", "🇰🇷"),
        LanguageFilter("zh", "Chinese", "🇨🇳"),
        LanguageFilter("hi", "Hindi", "🇮🇳"),
        LanguageFilter("ar", "Arabic", "🇸🇦"),
        LanguageFilter("ru", "Russian", "🇷🇺"),
        LanguageFilter("nl", "Dutch", "🇳🇱"),
        LanguageFilter("sv", "Swedish", "🇸🇪"),
        LanguageFilter("tr", "Turkish", "🇹🇷"),
    )

    val VOICES = listOf(
        // Breeze Blue (Top Open-Weight Realtime TTS)
        VoiceOption("voc_9f5gtax4x5ac", "Miles", "breeze", "en", "🇺🇸", "Breeze · Conversational"),
        VoiceOption("voc_mxz4hwdzagst", "Annabel", "breeze", "en", "🇺🇸", "Breeze · Assistant"),
        VoiceOption("voc_bd9xj6jwrsr5", "Asher", "breeze", "en", "🇺🇸", "Breeze · Warm Calm"),
        VoiceOption("voc_8a4jbpgm3a3e", "Chloe", "breeze", "en", "🇺🇸", "Breeze · Energetic"),

        // English (Deepgram Flux, Cartesia, Google Cloud)
        VoiceOption("flux-alexis-en", "Alexis", "deepgram", "en", "🇺🇸", "Flux · Crisp"),
        VoiceOption("flux-heather-en", "Heather", "deepgram", "en", "🇺🇸", "Flux · Warm"),
        VoiceOption("flux-cole-en", "Cole", "deepgram", "en", "🇺🇸", "Flux · Confident"),
        VoiceOption("flux-colin-en", "Colin", "deepgram", "en", "🇬🇧", "Flux · Narrator"),
        VoiceOption("flux-sharon-en", "Sharon", "deepgram", "en", "🇦🇺", "Flux · Calm"),
        VoiceOption("6c756032-11c3-4be8-a120-74f9673fb700", "Harlow", "cartesia", "en", "🇺🇸", "Cartesia · Warm"),
        VoiceOption("3a01612e-07da-4a85-bba8-ce10481df477", "Gareth", "cartesia", "en", "🇺🇸", "Cartesia · Clear"),
        VoiceOption("Aoede", "Aoede", "gcp", "en", "🇺🇸", "Google · Warm Neural"),
        VoiceOption("Charon", "Charon", "gcp", "en", "🇺🇸", "Google · Deep Voice"),
        VoiceOption("Puck", "Puck", "gcp", "en", "🇺🇸", "Google · Dynamic"),

        // Spanish
        VoiceOption("80ae4708-744d-459d-bd80-58964af17905", "Maritza", "cartesia", "es", "🇪🇸", "Cartesia · Spanish"),
        VoiceOption("3c1c7da2-8f3f-4966-a76d-1f5e484865b5", "Gonzalo", "cartesia", "es", "🇪🇸", "Cartesia · Spanish"),
        VoiceOption("Aoede", "Aoede (ES)", "gcp", "es", "🇪🇸", "Google · Spanish"),
        VoiceOption("Charon", "Charon (ES)", "gcp", "es", "🇪🇸", "Google · Spanish"),

        // French
        VoiceOption("6acedd3e-bac4-41bc-8c7c-ef560d27e778", "Clémence", "cartesia", "fr", "🇫🇷", "Cartesia · French"),
        VoiceOption("Aoede", "Aoede (FR)", "gcp", "fr", "🇫🇷", "Google · French"),

        // German
        VoiceOption("57a3a9e0-a91c-4c94-a2bb-e6cbab3ae649", "Clemens", "cartesia", "de", "🇩🇪", "Cartesia · German"),
        VoiceOption("Aoede", "Aoede (DE)", "gcp", "de", "🇩🇪", "Google · German"),

        // Italian
        VoiceOption("c8403b5c-6465-4396-9065-a440d376528a", "Alessio", "cartesia", "it", "🇮🇹", "Cartesia · Italian"),
        VoiceOption("Aoede", "Aoede (IT)", "gcp", "it", "🇮🇹", "Google · Italian"),

        // Portuguese
        VoiceOption("2c5dc34c-50e9-4e93-9a87-08e81ad8b4bd", "Clara", "cartesia", "pt", "🇧🇷", "Cartesia · Portuguese"),
        VoiceOption("Aoede", "Aoede (PT)", "gcp", "pt", "🇧🇷", "Google · Portuguese"),

        // Japanese
        VoiceOption("7f822e9a-d00c-45fe-84e4-d59a3ac664d4", "Hina", "cartesia", "ja", "🇯🇵", "Cartesia · Japanese"),
        VoiceOption("Aoede", "Aoede (JA)", "gcp", "ja", "🇯🇵", "Google · Japanese"),

        // Korean
        VoiceOption("d6d687b4-5d1e-4eb7-8c5a-1eb608532772", "Junho", "cartesia", "ko", "🇰🇷", "Cartesia · Korean"),
        VoiceOption("Aoede", "Aoede (KO)", "gcp", "ko", "🇰🇷", "Google · Korean"),

        // Chinese
        VoiceOption("0f5ac064-30c7-4cf6-8748-d275f682a0d5", "Yu-Hsin", "cartesia", "zh", "🇨🇳", "Cartesia · Mandarin"),
        VoiceOption("Aoede", "Aoede (ZH)", "gcp", "zh", "🇨🇳", "Google · Mandarin"),

        // Hindi
        VoiceOption("ce45ddca-5f4b-4047-ada3-3fce2bc78acd", "Kavya", "cartesia", "hi", "🇮🇳", "Cartesia · Hindi"),
        VoiceOption("Aoede", "Aoede (HI)", "gcp", "hi", "🇮🇳", "Google · Hindi"),

        // Arabic
        VoiceOption("1118c504-54fa-4cea-b2da-a3745a0862eb", "Mona", "cartesia", "ar", "🇸🇦", "Cartesia · Arabic"),
        VoiceOption("Aoede", "Aoede (AR)", "gcp", "ar", "🇸🇦", "Google · Arabic"),

        // Russian
        VoiceOption("7a62541e-5492-410e-95ff-3abd096fce87", "Natalia", "cartesia", "ru", "🇷🇺", "Cartesia · Russian"),
        VoiceOption("Aoede", "Aoede (RU)", "gcp", "ru", "🇷🇺", "Google · Russian"),

        // Dutch
        VoiceOption("4fbae271-89c4-494d-8181-6eddca393453", "Femke", "cartesia", "nl", "🇳🇱", "Cartesia · Dutch"),
        VoiceOption("Aoede", "Aoede (NL)", "gcp", "nl", "🇳🇱", "Google · Dutch"),

        // Swedish
        VoiceOption("3528e5c3-02d3-4a43-ac10-e1b3f1f6277d", "Arvid", "cartesia", "sv", "🇸🇪", "Cartesia · Swedish"),
        VoiceOption("Aoede", "Aoede (SV)", "gcp", "sv", "🇸🇪", "Google · Swedish"),

        // Turkish
        VoiceOption("8036098f-cff4-401e-bfba-f0a6a6e5e49b", "Elif", "cartesia", "tr", "🇹🇷", "Cartesia · Turkish"),
        VoiceOption("Aoede", "Aoede (TR)", "gcp", "tr", "🇹🇷", "Google · Turkish"),
    )

    val DEFAULT_VOICE = VOICES[0]
}
