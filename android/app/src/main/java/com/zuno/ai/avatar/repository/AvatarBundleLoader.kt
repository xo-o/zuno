package com.zuno.ai.avatar.repository

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import com.zuno.ai.avatar.model.AvatarBundleManifest
import com.zuno.ai.avatar.model.AvatarBundleManifestParser
import com.zuno.ai.avatar.model.AvatarBundleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

sealed interface IdleVideoSource : Closeable {
    fun applyTo(player: MediaPlayer)

    data class AssetFd(val afd: AssetFileDescriptor) : IdleVideoSource {
        override fun applyTo(player: MediaPlayer) {
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }

        override fun close() {
            runCatching { afd.close() }
        }
    }

    data class LocalFile(val file: File) : IdleVideoSource {
        override fun applyTo(player: MediaPlayer) {
            player.setDataSource(file.absolutePath)
        }

        override fun close() = Unit
    }
}

class LoadedAvatarBundle(
    val manifest: AvatarBundleManifest,
    val maskBitmap: Bitmap,
    val stripBitmap: Bitmap,
    val idleVideo: IdleVideoSource,
) : Closeable {
    fun recycleBitmaps() {
        if (!maskBitmap.isRecycled) maskBitmap.recycle()
        if (!stripBitmap.isRecycled) stripBitmap.recycle()
    }

    override fun close() {
        idleVideo.close()
        recycleBitmaps()
    }
}

/**
 * Loads compiled avatar bundles from APK `assets/` or downloaded local cache directories.
 * Caches parsed [AvatarBundleManifest] metadata in memory to keep reopen latency near-zero.
 */
object AvatarBundleLoader {
    private val manifestCache = ConcurrentHashMap<String, AvatarBundleManifest>()

    suspend fun load(context: Context, source: AvatarBundleSource): LoadedAvatarBundle =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val manifest = manifestCache[source.cacheKey] ?: loadManifest(appContext, source).also {
                manifestCache[source.cacheKey] = it
            }

            val maskBitmap = openFileStream(appContext, source, "mask.png").use(::decodeUnscaledBitmap)
            val stripBitmap = try {
                openFileStream(appContext, source, "mouth_strip.webp").use(::decodeUnscaledBitmap)
            } catch (t: Throwable) {
                maskBitmap.recycle()
                throw t
            }
            val idleVideo = try {
                openVideoSource(appContext, source, "idle.mp4")
            } catch (t: Throwable) {
                maskBitmap.recycle()
                stripBitmap.recycle()
                throw t
            }

            LoadedAvatarBundle(
                manifest = manifest,
                maskBitmap = maskBitmap,
                stripBitmap = stripBitmap,
                idleVideo = idleVideo,
            )
        }

    private fun loadManifest(context: Context, source: AvatarBundleSource): AvatarBundleManifest {
        val jsonText = openFileStream(context, source, "meta.json").bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
        return AvatarBundleManifestParser.parse(jsonText)
    }

    private fun openFileStream(
        context: Context,
        source: AvatarBundleSource,
        fileName: String,
    ): InputStream = when (source) {
        is AvatarBundleSource.BundledAsset -> {
            val path = "${source.assetDir.trimEnd('/')}/$fileName"
            context.assets.open(path)
        }
        is AvatarBundleSource.LocalDirectory -> {
            File(source.directory, fileName).inputStream()
        }
    }

    private fun openVideoSource(
        context: Context,
        source: AvatarBundleSource,
        fileName: String,
    ): IdleVideoSource = when (source) {
        is AvatarBundleSource.BundledAsset -> {
            val path = "${source.assetDir.trimEnd('/')}/$fileName"
            IdleVideoSource.AssetFd(context.assets.openFd(path))
        }
        is AvatarBundleSource.LocalDirectory -> {
            IdleVideoSource.LocalFile(File(source.directory, fileName))
        }
    }

    private fun decodeUnscaledBitmap(stream: InputStream): Bitmap {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeStream(stream, null, opts)
            ?: error("Failed to decode avatar bundle bitmap")
    }
}
