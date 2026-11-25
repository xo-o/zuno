package com.zuno.ai.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.zuno.ai.R

/** Lightweight one-shot UI sound effects (e.g., Horizon hold -> Free Talk sweep). */
object UiSoundEffects {
    private const val TAG = "UiSoundEffects"

    private var soundPool: SoundPool? = null
    private var sweepSoundId: Int = 0
    @Volatile
    private var sweepLoaded: Boolean = false
    @Volatile
    private var pendingPlay: Boolean = false
    @Volatile
    private var activeFallbackPlayer: MediaPlayer? = null

    @Synchronized
    fun preload(context: Context) {
        val appContext = context.applicationContext
        if (soundPool != null && sweepSoundId != 0) return
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build()
            pool.setOnLoadCompleteListener { sp, sampleId, status ->
                if (sampleId == sweepSoundId && status == 0) {
                    sweepLoaded = true
                    if (pendingPlay) {
                        pendingPlay = false
                        val streamId = sp.play(sampleId, 1f, 1f, 1, 0, 1f)
                        Log.d(TAG, "Played deferred SoundPool sweep streamId=$streamId")
                    }
                } else if (status != 0) {
                    Log.w(TAG, "SoundPool load failed status=$status")
                }
            }
            sweepSoundId = pool.load(appContext, R.raw.sweep1, 1)
            soundPool = pool
        }.onFailure { err ->
            Log.w(TAG, "Failed to preload SoundPool", err)
        }
    }

    fun playHorizonSweep(context: Context) {
        val appContext = context.applicationContext
        val pool = soundPool
        val sid = sweepSoundId
        if (pool != null && sid != 0 && sweepLoaded) {
            val streamId = pool.play(sid, 1f, 1f, 1, 0, 1f)
            Log.d(TAG, "Played SoundPool sweep streamId=$streamId")
            if (streamId != 0) return
        }
        // Fallback if SoundPool isn't ready yet (or first tap before preload finished).
        if (pool == null || sid == 0) preload(appContext)
        playWithMediaPlayer(appContext)
    }

    private fun playWithMediaPlayer(appContext: Context) {
        runCatching {
            activeFallbackPlayer?.let { prev ->
                runCatching {
                    if (prev.isPlaying) prev.stop()
                    prev.release()
                }
                activeFallbackPlayer = null
            }
            val afd = appContext.resources.openRawResourceFd(R.raw.sweep1) ?: return
            val player = MediaPlayer()
            activeFallbackPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setVolume(1f, 1f)
            player.setOnCompletionListener { mp ->
                if (activeFallbackPlayer === mp) activeFallbackPlayer = null
                runCatching { mp.release() }
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "MediaPlayer sweep error what=$what extra=$extra")
                if (activeFallbackPlayer === mp) activeFallbackPlayer = null
                runCatching { mp.release() }
                true
            }
            player.prepare()
            player.start()
            Log.d(TAG, "Played MediaPlayer sweep fallback")
        }.onFailure { err ->
            Log.w(TAG, "MediaPlayer sweep failed", err)
        }
    }
}
