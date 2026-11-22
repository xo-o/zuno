package com.zuno.ai.avatar.audio

import com.zuno.ai.avatar.dsp.AvatarSpeechDriver
import java.nio.ByteBuffer

/**
 * Thread-safe audio fan-out hub shared between audio sources (LiveKit remote track, local WAV TTS)
 * and one or more mounted [AvatarSpeechDriver] instances.
 *
 * Uses a copy-on-write snapshot array so hot-path audio callbacks perform zero allocations.
 */
class AvatarSpeechSink {
    private val lock = Any()
    private val attached = ArrayList<AvatarSpeechDriver>(2)

    @Volatile
    private var snapshot: Array<AvatarSpeechDriver> = emptyArray()

    fun attachDriver(driver: AvatarSpeechDriver) {
        synchronized(lock) {
            if (!attached.contains(driver)) {
                attached.add(driver)
                snapshot = attached.toTypedArray()
            }
        }
    }

    fun detachDriver(driver: AvatarSpeechDriver) {
        synchronized(lock) {
            if (attached.remove(driver)) {
                snapshot = attached.toTypedArray()
            }
        }
    }

    fun pushPcm16(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        channels: Int,
        frameCount: Int,
    ) {
        val drivers = snapshot
        for (i in drivers.indices) {
            drivers[i].pushPcm16(audioData, bitsPerSample, sampleRate, channels, frameCount)
        }
    }

    fun pushPcm16Array(
        samples: ShortArray,
        offsetShorts: Int,
        frameCount: Int,
        sampleRate: Int,
        channels: Int,
    ) {
        val drivers = snapshot
        for (i in drivers.indices) {
            drivers[i].pushPcm16Array(samples, offsetShorts, frameCount, sampleRate, channels)
        }
    }

    fun reset() {
        val drivers = snapshot
        for (i in drivers.indices) {
            drivers[i].reset()
        }
    }
}
