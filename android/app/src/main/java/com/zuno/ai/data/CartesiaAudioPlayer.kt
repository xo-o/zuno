package com.zuno.ai.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Plays spoken phrase & coach-note samples synthesized by Cartesia Sonic-3 (`/api/tts/preview`),
 * caching WAV files locally so repeat taps play instantaneously without extra API calls.
 */
class CartesiaAudioPlayer(
    context: Context,
    private val fetchAudio: suspend (text: String, language: String?, nativeLanguage: String?, slow: Boolean) -> ByteArray,
) {
    private val cacheRoot = File(context.applicationContext.cacheDir, "cartesia_tts_v2").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var playJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun speak(
        text: String,
        language: String? = null,
        nativeLanguage: String? = null,
        voiceKey: String = "warm",
        slow: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onFinished?.invoke()
            return
        }
        stop()
        playJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val hash = sha256("$voiceKey|${language.orEmpty()}|${nativeLanguage.orEmpty()}|$slow|$trimmed")
                    val cached = File(cacheRoot, "$hash.wav")
                    if (!cached.exists() || cached.length() == 0L) {
                        val bytes = fetchAudio(trimmed, language, nativeLanguage, slow)
                        if (bytes.isEmpty()) {
                            cached.delete()
                            return@withContext null
                        }
                        cached.writeBytes(bytes)
                    }
                    cached
                } ?: run {
                    _isSpeaking.value = false
                    onFinished?.invoke()
                    return@launch
                }

                _isSpeaking.value = true
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        releaseSafely(it)
                        onFinished?.invoke()
                    }
                    setOnErrorListener { player, _, _ ->
                        releaseSafely(player)
                        onFinished?.invoke()
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                Log.w("CartesiaAudioPlayer", "TTS preview playback failed", e)
                _isSpeaking.value = false
                onFinished?.invoke()
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        mediaPlayer?.let(::releaseSafely)
        mediaPlayer = null
        _isSpeaking.value = false
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun releaseSafely(player: MediaPlayer) {
        runCatching {
            if (player.isPlaying) player.stop()
        }
        runCatching { player.release() }
        if (mediaPlayer === player) {
            mediaPlayer = null
            _isSpeaking.value = false
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun rememberCartesiaAudioPlayer(
    fetchAudio: suspend (text: String, language: String?, nativeLanguage: String?, slow: Boolean) -> ByteArray,
): CartesiaAudioPlayer {
    val context = LocalContext.current
    val player = remember(context) { CartesiaAudioPlayer(context, fetchAudio) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
