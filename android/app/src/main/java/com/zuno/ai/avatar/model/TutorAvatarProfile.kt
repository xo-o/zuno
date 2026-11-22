package com.zuno.ai.avatar.model

import androidx.annotation.DrawableRes
import java.io.File

/**
 * Source for a tutor's static image (used in chat list thumbnails and instant stage placeholder).
 */
sealed interface TutorPosterSource {
    data class DrawableRes(@param:androidx.annotation.DrawableRes val resId: Int) : TutorPosterSource
    data class RemoteUrl(val url: String) : TutorPosterSource
}

/**
 * Location of the compiled dynamic avatar bundle (`meta.json`, `idle.mp4`, `mouth_strip.webp`, `mask.png`).
 * Supports both APK-bundled default tutors and future downloaded/cached tutors.
 */
sealed interface AvatarBundleSource {
    val cacheKey: String

    data class BundledAsset(val assetDir: String) : AvatarBundleSource {
        override val cacheKey: String = "asset:$assetDir"
    }

    data class LocalDirectory(val directory: File) : AvatarBundleSource {
        override val cacheKey: String = "dir:${directory.absolutePath}"
    }
}

/**
 * Domain descriptor for an AI tutor avatar.
 * Screens depend on [TutorAvatarProfile] rather than hardcoding drawable/asset paths,
 * allowing new tutors to be added from bundled assets or remote catalogs.
 */
data class TutorAvatarProfile(
    val id: String,
    val displayName: String,
    val roleLabel: String,
    val voicePreset: String,
    val thumbPoster: TutorPosterSource,
    val stagePoster: TutorPosterSource,
    val bundleSource: AvatarBundleSource?,
)
