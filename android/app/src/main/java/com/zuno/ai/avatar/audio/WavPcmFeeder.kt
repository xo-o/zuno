package com.zuno.ai.avatar.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Parses cached RIFF/WAVE PCM16 files played by [CartesiaAudioPlayer] and feeds chunks
 * paced to [MediaPlayer.getCurrentPosition] into [AvatarSpeechSink] so Coach card previews,
 * slow replays, and onboarding fallback TTS lip-sync cleanly.
 */
object WavPcmFeeder {
    data class ParsedWav(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val pcmBytes: ByteArray,
    )

    fun parsePcm16Wav(file: File): ParsedWav? = runCatching {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Check "RIFF" ... "WAVE"
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return null

        var pos = 12
        var channels = 1
        var sampleRate = 24000
        var bitsPerSample = 16
        var audioFormat = 1
        var dataStart = -1
        var dataSize = 0

        while (pos + 8 <= bytes.size) {
            val id0 = bytes[pos].toInt().toChar()
            val id1 = bytes[pos + 1].toInt().toChar()
            val id2 = bytes[pos + 2].toInt().toChar()
            val id3 = bytes[pos + 3].toInt().toChar()
            val chunkSize = buf.getInt(pos + 4)
            val chunkDataStart = pos + 8
            if (chunkSize < 0 || chunkDataStart > bytes.size) break

            if (id0 == 'f' && id1 == 'm' && id2 == 't' && id3 == ' ' && chunkSize >= 16) {
                audioFormat = buf.getShort(chunkDataStart).toInt() and 0xFFFF
                channels = buf.getShort(chunkDataStart + 2).toInt() and 0xFFFF
                sampleRate = buf.getInt(chunkDataStart + 4)
                bitsPerSample = buf.getShort(chunkDataStart + 14).toInt() and 0xFFFF
            } else if (id0 == 'd' && id1 == 'a' && id2 == 't' && id3 == 'a') {
                dataStart = chunkDataStart
                dataSize = min(chunkSize, bytes.size - chunkDataStart)
                break
            }

            // RIFF chunks are word-aligned
            val paddedSize = chunkSize + (chunkSize and 1)
            pos = chunkDataStart + paddedSize
        }

        if (audioFormat != 1 || bitsPerSample != 16 || dataStart < 0 || dataSize <= 0 || channels <= 0 || sampleRate <= 0) {
            return null
        }

        ParsedWav(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            pcmBytes = bytes.copyOfRange(dataStart, dataStart + dataSize),
        )
    }.getOrNull()

    suspend fun streamWhilePlaying(
        file: File,
        player: MediaPlayer,
        sink: AvatarSpeechSink,
    ) {
        val wav = withContext(Dispatchers.IO) { parsePcm16Wav(file) } ?: return
        val bytesPerFrame = wav.channels * 2
        val totalFrames = wav.pcmBytes.size / bytesPerFrame
        if (totalFrames <= 0) return

        val wrapper = ByteBuffer.wrap(wav.pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        var sentFrames = 0
        // Push ~40ms ahead of MediaPlayer position to match crisp-clips lookahead
        val lookaheadMs = 40

        try {
            while (coroutineContext.isActive) {
                val playing = runCatching { player.isPlaying }.getOrDefault(false)
                val posMs = runCatching { player.currentPosition }.getOrDefault(0)
                if (!playing && sentFrames > 0) break

                val targetFrame = (((posMs + lookaheadMs).toLong() * wav.sampleRate) / 1000L)
                    .toInt()
                    .coerceIn(0, totalFrames)

                if (targetFrame > sentFrames) {
                    val chunkFrames = targetFrame - sentFrames
                    val byteOffset = sentFrames * bytesPerFrame
                    wrapper.limit(byteOffset + chunkFrames * bytesPerFrame)
                    wrapper.position(byteOffset)
                    sink.pushPcm16(
                        audioData = wrapper,
                        bitsPerSample = 16,
                        sampleRate = wav.sampleRate,
                        channels = wav.channels,
                        frameCount = chunkFrames,
                    )
                    sentFrames = targetFrame
                }

                if (sentFrames >= totalFrames && !playing) break
                delay(16L)
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            sink.reset()
        }
    }
}
