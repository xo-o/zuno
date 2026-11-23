package com.zuno.ai.avatar.catalog

import androidx.compose.runtime.compositionLocalOf
import com.zuno.ai.R
import com.zuno.ai.avatar.model.AvatarBundleSource
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.model.TutorPosterSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry of Zuno's 6 curated AI tutors.
 * Every tutor maps 1:1 to a dark-studio Crisp-Clips v4 lip-sync bundle and a Cartesia Sonic-3.6 multilingual voice.
 */
object TutorAvatarCatalog {
    val SOFIA = TutorAvatarProfile(
        id = "sofia",
        displayName = "Sofía",
        roleLabel = "AI Tutor",
        voicePreset = "warm",
        voiceId = "ec1e269e-9ca0-402f-8a18-58e0e022355a",
        taglineRes = R.string.settings_pref_voice_warm,
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_sofia_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_sofia_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/sofia"),
    )

    val VALERIA = TutorAvatarProfile(
        id = "valeria",
        displayName = "Valeria",
        roleLabel = "AI Tutor",
        voicePreset = "calm",
        voiceId = "ae823354-f9be-4aef-8543-f569644136b4",
        taglineRes = R.string.settings_pref_voice_calm,
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_valeria_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_valeria_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/valeria"),
    )

    val CLAIRE = TutorAvatarProfile(
        id = "claire",
        displayName = "Claire",
        roleLabel = "AI Tutor",
        voicePreset = "crisp",
        voiceId = "f786b574-daa5-4673-aa0c-cbe3e8534c02",
        taglineRes = R.string.settings_pref_voice_crisp,
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_claire_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_claire_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/claire"),
    )

    val MATEO = TutorAvatarProfile(
        id = "mateo",
        displayName = "Mateo",
        roleLabel = "AI Tutor",
        voicePreset = "energetic",
        voiceId = "4df027cb-2920-4a1f-8c34-f21529d5c3fe",
        taglineRes = R.string.settings_pref_voice_energetic,
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_mateo_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_mateo_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/mateo"),
    )

    val LUCAS = TutorAvatarProfile(
        id = "lucas",
        displayName = "Lucas",
        roleLabel = "AI Tutor",
        voicePreset = "steady",
        voiceId = "8c254787-4eb4-4577-bd3d-fb3c273baea2",
        taglineRes = R.string.settings_pref_voice_steady,
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_lucas_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_lucas_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/lucas"),
    )

    val ETHAN = TutorAvatarProfile(
        id = "ethan",
        displayName = "Ethan",
        roleLabel = "AI Tutor",
        voicePreset = "casual",
        voiceId = "30894953-bcce-41fe-892c-15ce19c843ff",
        taglineRes = R.string.settings_pref_voice_casual,
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
