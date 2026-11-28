package com.zuno.ai.avatar.catalog

import androidx.compose.runtime.compositionLocalOf
import com.zuno.ai.R
import com.zuno.ai.avatar.model.AvatarBundleSource
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.model.TutorPosterSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry of Zuno's 6 curated AI tutors.
 * Every tutor maps 1:1 to a dark-studio Crisp-Clips v4 lip-sync bundle and an ElevenLabs Eleven v4 Turbo multilingual voice.
 */
object TutorAvatarCatalog {
    val SOFIA = TutorAvatarProfile(
        id = "sofia",
        displayName = "Sofía",
        roleLabel = "AI Tutor",
        voicePreset = "warm",
        voiceId = "DODLEQrClDo8wCz460ld",
        taglineRes = R.string.settings_pref_voice_warm,
        traitRes = R.string.ai_friend_sofia_trait,
        chatSubtitleRes = R.string.ai_friend_sofia_sub,
        bioRes = R.string.ai_friend_sofia_bio,
        topicChipRes = listOf(R.string.ai_friend_sofia_chip_1, R.string.ai_friend_sofia_chip_2, R.string.ai_friend_sofia_chip_3),
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_sofia_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_sofia_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/sofia"),
    )

    val VALERIA = TutorAvatarProfile(
        id = "valeria",
        displayName = "Valeria",
        roleLabel = "AI Tutor",
        voicePreset = "calm",
        voiceId = "EXAVITQu4vr4xnSDxMaL",
        taglineRes = R.string.settings_pref_voice_calm,
        traitRes = R.string.ai_friend_valeria_trait,
        chatSubtitleRes = R.string.ai_friend_valeria_sub,
        bioRes = R.string.ai_friend_valeria_bio,
        topicChipRes = listOf(R.string.ai_friend_valeria_chip_1, R.string.ai_friend_valeria_chip_2, R.string.ai_friend_valeria_chip_3),
        isSlowPace = true,
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_valeria_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_valeria_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/valeria"),
    )

    val CLAIRE = TutorAvatarProfile(
        id = "claire",
        displayName = "Claire",
        roleLabel = "AI Tutor",
        voicePreset = "crisp",
        voiceId = "Xb7hH8MSUJpSbSDYk0k2",
        taglineRes = R.string.settings_pref_voice_crisp,
        traitRes = R.string.ai_friend_claire_trait,
        chatSubtitleRes = R.string.ai_friend_claire_sub,
        bioRes = R.string.ai_friend_claire_bio,
        topicChipRes = listOf(R.string.ai_friend_claire_chip_1, R.string.ai_friend_claire_chip_2, R.string.ai_friend_claire_chip_3),
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_claire_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_claire_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/claire"),
    )

    val MATEO = TutorAvatarProfile(
        id = "mateo",
        displayName = "Mateo",
        roleLabel = "AI Tutor",
        voicePreset = "energetic",
        voiceId = "TX3LPaxmHKxFdv7VOQHJ",
        taglineRes = R.string.settings_pref_voice_energetic,
        traitRes = R.string.ai_friend_mateo_trait,
        chatSubtitleRes = R.string.ai_friend_mateo_sub,
        bioRes = R.string.ai_friend_mateo_bio,
        topicChipRes = listOf(R.string.ai_friend_mateo_chip_1, R.string.ai_friend_mateo_chip_2, R.string.ai_friend_mateo_chip_3),
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_mateo_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_mateo_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/mateo"),
    )

    val LUCAS = TutorAvatarProfile(
        id = "lucas",
        displayName = "Lucas",
        roleLabel = "AI Tutor",
        voicePreset = "steady",
        voiceId = "cjVigY5qzO86Huf0OWal",
        taglineRes = R.string.settings_pref_voice_steady,
        traitRes = R.string.ai_friend_lucas_trait,
        chatSubtitleRes = R.string.ai_friend_lucas_sub,
        bioRes = R.string.ai_friend_lucas_bio,
        topicChipRes = listOf(R.string.ai_friend_lucas_chip_1, R.string.ai_friend_lucas_chip_2, R.string.ai_friend_lucas_chip_3),
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_lucas_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_lucas_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/lucas"),
    )

    val ETHAN = TutorAvatarProfile(
        id = "ethan",
        displayName = "Ethan",
        roleLabel = "AI Tutor",
        voicePreset = "casual",
        voiceId = "bIHbv24MWmeRgasZH58o",
        taglineRes = R.string.settings_pref_voice_casual,
        traitRes = R.string.ai_friend_ethan_trait,
        chatSubtitleRes = R.string.ai_friend_ethan_sub,
        bioRes = R.string.ai_friend_ethan_bio,
        topicChipRes = listOf(R.string.ai_friend_ethan_chip_1, R.string.ai_friend_ethan_chip_2, R.string.ai_friend_ethan_chip_3),
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_ethan_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_ethan_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/ethan"),
    )

    val DEFAULT_TUTOR: TutorAvatarProfile = SOFIA

    val ORDERED_TUTORS: List<TutorAvatarProfile> = listOf(
        SOFIA,
        VALERIA,
        CLAIRE,
        MATEO,
        LUCAS,
        ETHAN,
    )

    /** Display order for `Chats → AI Friends` & `New chat` sheet (Figma `2842:15221`). */
    val CHATS_FRIENDS_ORDER: List<TutorAvatarProfile> = listOf(
        SOFIA,
        MATEO,
        VALERIA,
        CLAIRE,
        LUCAS,
        ETHAN,
    )

    private val registry = ConcurrentHashMap<String, TutorAvatarProfile>().apply {
        ORDERED_TUTORS.forEach { put(it.id, it) }
    }

    fun register(profile: TutorAvatarProfile) {
        registry[profile.id] = profile
    }

    fun all(): List<TutorAvatarProfile> = ORDERED_TUTORS

    fun resolve(tutorId: String? = null, voicePreset: String? = null): TutorAvatarProfile {
        if (!tutorId.isNullOrBlank()) {
            val norm = tutorId.trim().lowercase()
            if (norm == "elena") return SOFIA
            registry[norm]?.let { return it }
        }
        if (!voicePreset.isNullOrBlank()) {
            ORDERED_TUTORS.firstOrNull { it.voicePreset.equals(voicePreset, ignoreCase = true) }
                ?.let { return it }
        }
        return DEFAULT_TUTOR
    }
}

val LocalActiveTutor = compositionLocalOf { TutorAvatarCatalog.DEFAULT_TUTOR }
