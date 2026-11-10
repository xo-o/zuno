package com.zuno.ai.data

import java.text.Normalizer
import java.util.Locale

data class Accent(val id: String, val label: String)

/**
 * A language the learner can study (Settings · Learning language). The server's list (`GET /api/languages`)
 * is authoritative; [LearningLanguages.bundled] mirrors it so the app works before it loads.
 */
data class LearningLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val accents: List<Accent>,
    val rtl: Boolean = false,
) {
    val defaultAccent: String get() = accents.firstOrNull()?.id.orEmpty()

    fun accentLabel(id: String?): String = (accents.firstOrNull { it.id == id } ?: accents.firstOrNull())?.label.orEmpty()

    /** BCP-47 tag for on-device speech recognition and read-aloud, refined by accent. */
    fun speechTag(accent: String?): String = SPEECH_TAGS[code to accent] ?: SPEECH_TAGS[code to null] ?: code

    fun locale(accent: String?): Locale = Locale.forLanguageTag(speechTag(accent))

    /** Home prompt in the language being learned, e.g. "Hola Noah, ¿qué practicamos hoy?". */
    fun greeting(firstName: String): String {
        val copy = HOME_COPY[code] ?: HOME_COPY.getValue("en")
        return if (firstName.isBlank()) copy.plain else copy.named.replace("{name}", firstName)
    }

    /** Composer placeholder, e.g. "Responde en español…". */
    val placeholder: String get() = (HOME_COPY[code] ?: HOME_COPY.getValue("en")).placeholder

    /** Matches the English or native name, ignoring case and diacritics. */
    fun matches(query: String): Boolean {
        val q = fold(query.trim())
        return q.isEmpty() || fold(englishName).contains(q) || fold(nativeName).contains(q) || code.equals(q, ignoreCase = true)
    }

    private class HomeCopyText(val named: String, val plain: String, val placeholder: String)

    private companion object {
        fun fold(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

        val SPEECH_TAGS: Map<Pair<String, String?>, String> = mapOf(
            ("ar" to null) to "ar-SA", ("ar" to "egyptian") to "ar-EG", ("ar" to "levantine") to "ar-JO", ("ar" to "gulf") to "ar-AE",
            ("bn" to null) to "bn-BD", ("bn" to "india") to "bn-IN",
            ("zh" to null) to "zh-CN", ("zh" to "taiwan") to "zh-TW",
            ("cs" to null) to "cs-CZ",
            ("da" to null) to "da-DK",
            ("nl" to null) to "nl-NL", ("nl" to "belgium") to "nl-BE",
            ("en" to null) to "en-US", ("en" to "uk") to "en-GB", ("en" to "australia") to "en-AU",
            ("fi" to null) to "fi-FI",
            ("fr" to null) to "fr-FR", ("fr" to "canada") to "fr-CA",
            ("de" to null) to "de-DE", ("de" to "austria") to "de-AT", ("de" to "switzerland") to "de-CH",
            ("el" to null) to "el-GR",
            ("he" to null) to "he-IL",
            ("hi" to null) to "hi-IN",
            ("hu" to null) to "hu-HU",
            ("id" to null) to "id-ID",
            ("it" to null) to "it-IT",
            ("ja" to null) to "ja-JP",
            ("ko" to null) to "ko-KR",
            ("ms" to null) to "ms-MY",
            ("no" to null) to "nb-NO",
            ("fa" to null) to "fa-IR",
            ("pl" to null) to "pl-PL",
            ("pt" to null) to "pt-BR", ("pt" to "portugal") to "pt-PT",
            ("ro" to null) to "ro-RO",
            ("ru" to null) to "ru-RU",
            ("es" to null) to "es-419", ("es" to "spain") to "es-ES",
            ("sv" to null) to "sv-SE",
            ("tl" to null) to "fil-PH",
            ("th" to null) to "th-TH",
            ("tr" to null) to "tr-TR",
            ("uk" to null) to "uk-UA",
            ("ur" to null) to "ur-PK",
            ("vi" to null) to "vi-VN",
        )

        val HOME_COPY: Map<String, HomeCopyText> = mapOf(
            "ar" to HomeCopyText("مرحبًا {name}، ماذا نتدرّب اليوم؟", "مرحبًا، ماذا نتدرّب اليوم؟", "ردّ بالعربية…"),
            "bn" to HomeCopyText("হ্যালো {name}, আজ আমরা কী অনুশীলন করব?", "হ্যালো, আজ আমরা কী অনুশীলন করব?", "বাংলায় উত্তর দাও…"),
            "zh" to HomeCopyText("{name}你好，今天我们练习什么？", "你好，今天我们练习什么？", "用中文回答…"),
            "cs" to HomeCopyText("Ahoj {name}, co si dnes procvičíme?", "Ahoj, co si dnes procvičíme?", "Odpověz česky…"),
            "da" to HomeCopyText("Hej {name}, hvad skal vi øve i dag?", "Hej, hvad skal vi øve i dag?", "Svar på dansk…"),
            "nl" to HomeCopyText("Hoi {name}, wat gaan we vandaag oefenen?", "Hoi, wat gaan we vandaag oefenen?", "Antwoord in het Nederlands…"),
            "en" to HomeCopyText("Hi {name}, what shall we practice today?", "Hi, what shall we practice today?", "Reply in English…"),
            "fi" to HomeCopyText("Hei {name}, mitä harjoittelemme tänään?", "Hei, mitä harjoittelemme tänään?", "Vastaa suomeksi…"),
            "fr" to HomeCopyText("Salut {name}, on pratique quoi aujourd’hui ?", "Salut, on pratique quoi aujourd’hui ?", "Réponds en français…"),
            "de" to HomeCopyText("Hallo {name}, was üben wir heute?", "Hallo, was üben wir heute?", "Antworte auf Deutsch…"),
            "el" to HomeCopyText("Γεια σου {name}, τι θα εξασκήσουμε σήμερα;", "Γεια σου, τι θα εξασκήσουμε σήμερα;", "Απάντησε στα ελληνικά…"),
            "he" to HomeCopyText("היי {name}, מה נתרגל היום?", "היי, מה נתרגל היום?", "ענה בעברית…"),
            "hi" to HomeCopyText("नमस्ते {name}, आज हम क्या अभ्यास करें?", "नमस्ते, आज हम क्या अभ्यास करें?", "हिन्दी में जवाब दो…"),
            "hu" to HomeCopyText("Szia {name}, mit gyakorolunk ma?", "Szia, mit gyakorolunk ma?", "Válaszolj magyarul…"),
            "id" to HomeCopyText("Hai {name}, hari ini kita latihan apa?", "Hai, hari ini kita latihan apa?", "Balas dalam bahasa Indonesia…"),
            "it" to HomeCopyText("Ciao {name}, cosa pratichiamo oggi?", "Ciao, cosa pratichiamo oggi?", "Rispondi in italiano…"),
            "ja" to HomeCopyText("{name}さん、今日は何を練習しましょうか？", "こんにちは、今日は何を練習しましょうか？", "日本語で返信…"),
            "ko" to HomeCopyText("{name}님, 오늘은 무엇을 연습할까요?", "안녕하세요, 오늘은 무엇을 연습할까요?", "한국어로 답하세요…"),
            "ms" to HomeCopyText("Hai {name}, apa yang kita nak latih hari ini?", "Hai, apa yang kita nak latih hari ini?", "Balas dalam bahasa Melayu…"),
            "no" to HomeCopyText("Hei {name}, hva skal vi øve på i dag?", "Hei, hva skal vi øve på i dag?", "Svar på norsk…"),
            "fa" to HomeCopyText("سلام {name}، امروز چه چیزی تمرین کنیم؟", "سلام، امروز چه چیزی تمرین کنیم؟", "به فارسی جواب بده…"),
            "pl" to HomeCopyText("Cześć {name}, co dziś ćwiczymy?", "Cześć, co dziś ćwiczymy?", "Odpowiedz po polsku…"),
            "pt" to HomeCopyText("Oi {name}, o que vamos praticar hoje?", "Oi, o que vamos praticar hoje?", "Responda em português…"),
            "ro" to HomeCopyText("Salut {name}, ce exersăm azi?", "Salut, ce exersăm azi?", "Răspunde în română…"),
            "ru" to HomeCopyText("Привет, {name}! Что потренируем сегодня?", "Привет! Что потренируем сегодня?", "Ответь по-русски…"),
            "es" to HomeCopyText("Hola {name}, ¿qué practicamos hoy?", "Hola, ¿qué practicamos hoy?", "Responde en español…"),
            "sv" to HomeCopyText("Hej {name}, vad ska vi öva på idag?", "Hej, vad ska vi öva på idag?", "Svara på svenska…"),
            "tl" to HomeCopyText("Kumusta {name}, ano ang pagsasanayan natin ngayon?", "Kumusta, ano ang pagsasanayan natin ngayon?", "Sumagot sa Tagalog…"),
            "th" to HomeCopyText("สวัสดี {name} วันนี้เราจะฝึกอะไรกันดี?", "สวัสดี วันนี้เราจะฝึกอะไรกันดี?", "ตอบเป็นภาษาไทย…"),
            "tr" to HomeCopyText("Merhaba {name}, bugün ne çalışalım?", "Merhaba, bugün ne çalışalım?", "Türkçe cevap ver…"),
            "uk" to HomeCopyText("Привіт, {name}! Що потренуємо сьогодні?", "Привіт! Що потренуємо сьогодні?", "Відповідай українською…"),
            "ur" to HomeCopyText("ہیلو {name}، آج ہم کیا مشق کریں؟", "ہیلو، آج ہم کیا مشق کریں؟", "اردو میں جواب دیں…"),
            "vi" to HomeCopyText("Chào {name}, hôm nay mình luyện gì nhé?", "Chào bạn, hôm nay mình luyện gì nhé?", "Trả lời bằng tiếng Việt…"),
        )
    }
}

object LearningLanguages {
    private val STANDARD = listOf(Accent("standard", "Standard"))

    /** Mirrors the backend's list (backend/src/lib/languages.ts), A→Z by English name. */
    val bundled: List<LearningLanguage> = listOf(
        LearningLanguage("ar", "Arabic", "العربية", listOf(Accent("msa", "Modern Standard"), Accent("egyptian", "Egyptian"), Accent("levantine", "Levantine"), Accent("gulf", "Gulf")), rtl = true),
        LearningLanguage("bn", "Bengali", "বাংলা", listOf(Accent("bangladesh", "Bangladesh"), Accent("india", "India (West Bengal)"))),
        LearningLanguage("zh", "Chinese (Mandarin)", "中文（普通话）", listOf(Accent("mainland", "Mainland"), Accent("taiwan", "Taiwan"))),
        LearningLanguage("cs", "Czech", "Čeština", STANDARD),
        LearningLanguage("da", "Danish", "Dansk", STANDARD),
        LearningLanguage("nl", "Dutch", "Nederlands", listOf(Accent("netherlands", "Netherlands"), Accent("belgium", "Belgium (Flemish)"))),
        LearningLanguage("en", "English", "English", listOf(Accent("us", "US"), Accent("uk", "UK"), Accent("australia", "Australia"))),
        LearningLanguage("fi", "Finnish", "Suomi", STANDARD),
        LearningLanguage("fr", "French", "Français", listOf(Accent("france", "France"), Accent("canada", "Canada"))),
        LearningLanguage("de", "German", "Deutsch", listOf(Accent("germany", "Germany"), Accent("austria", "Austria"), Accent("switzerland", "Switzerland"))),
        LearningLanguage("el", "Greek", "Ελληνικά", STANDARD),
        LearningLanguage("he", "Hebrew", "עברית", STANDARD, rtl = true),
        LearningLanguage("hi", "Hindi", "हिन्दी", STANDARD),
        LearningLanguage("hu", "Hungarian", "Magyar", STANDARD),
        LearningLanguage("id", "Indonesian", "Bahasa Indonesia", STANDARD),
        LearningLanguage("it", "Italian", "Italiano", STANDARD),
        LearningLanguage("ja", "Japanese", "日本語", listOf(Accent("tokyo", "Standard (Tokyo)"))),
        LearningLanguage("ko", "Korean", "한국어", listOf(Accent("seoul", "Standard (Seoul)"))),
        LearningLanguage("ms", "Malay", "Bahasa Melayu", listOf(Accent("malaysia", "Malaysia"))),
        LearningLanguage("no", "Norwegian", "Norsk", listOf(Accent("bokmal", "Bokmål"))),
        LearningLanguage("fa", "Persian", "فارسی", listOf(Accent("iran", "Iran")), rtl = true),
        LearningLanguage("pl", "Polish", "Polski", STANDARD),
        LearningLanguage("pt", "Portuguese", "Português", listOf(Accent("brazil", "Brazil"), Accent("portugal", "Portugal"))),
        LearningLanguage("ro", "Romanian", "Română", STANDARD),
        LearningLanguage("ru", "Russian", "Русский", STANDARD),
        LearningLanguage("es", "Spanish", "Español", listOf(Accent("latam", "Latin America"), Accent("spain", "Spain"))),
        LearningLanguage("sv", "Swedish", "Svenska", STANDARD),
        LearningLanguage("tl", "Tagalog", "Tagalog", listOf(Accent("philippines", "Philippines"))),
        LearningLanguage("th", "Thai", "ไทย", STANDARD),
        LearningLanguage("tr", "Turkish", "Türkçe", STANDARD),
        LearningLanguage("uk", "Ukrainian", "Українська", STANDARD),
        LearningLanguage("ur", "Urdu", "اردو", listOf(Accent("pakistan", "Pakistan")), rtl = true),
        LearningLanguage("vi", "Vietnamese", "Tiếng Việt", listOf(Accent("north", "Northern (Hanoi)"), Accent("south", "Southern (Saigon)"))),
    )

    val Spanish: LearningLanguage = bundled.first { it.code == "es" }

    fun find(code: String?, list: List<LearningLanguage> = bundled): LearningLanguage =
        list.firstOrNull { it.code == code } ?: bundled.firstOrNull { it.code == code } ?: Spanish
}
