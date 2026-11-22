package com.zuno.ai.avatar.catalog

import com.zuno.ai.R
import com.zuno.ai.avatar.model.AvatarBundleSource
import com.zuno.ai.avatar.model.TutorAvatarProfile
import com.zuno.ai.avatar.model.TutorPosterSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry of AI tutor avatars.
 * Currently ships the pre-compiled Elena (`smooth` v4 crisp-clips) bundle as the default tutor
 * and supports registering additional bundled or downloaded tutors without modifying UI screens.
 */
object TutorAvatarCatalog {
    val ELENA = TutorAvatarProfile(
        id = "elena",
        displayName = "Sofía",
        roleLabel = "AI Tutor",
        voicePreset = "warm",
        thumbPoster = TutorPosterSource.DrawableRes(R.drawable.tutor_elena_thumb),
        stagePoster = TutorPosterSource.DrawableRes(R.drawable.tutor_elena_poster),
        bundleSource = AvatarBundleSource.BundledAsset("avatars/elena"),
    )

    val DEFAULT_TUTOR: TutorAvatarProfile = ELENA

    private val registry = ConcurrentHashMap<String, TutorAvatarProfile>().apply {
        put(ELENA.id, ELENA)
    }

    fun register(profile: TutorAvatarProfile) {
        registry[profile.id] = profile
    }

    fun all(): List<TutorAvatarProfile> = registry.values.toList()

    fun resolve(tutorId: String? = null, voicePreset: String? = null): TutorAvatarProfile {
        if (!tutorId.isNullOrBlank()) {
            registry[tutorId]?.let { return it }
        }
        if (!voicePreset.isNullOrBlank()) {
            registry.values.firstOrNull { it.voicePreset.equals(voicePreset, ignoreCase = true) }
                ?.let { return it }
        }
        return DEFAULT_TUTOR
    }
}
