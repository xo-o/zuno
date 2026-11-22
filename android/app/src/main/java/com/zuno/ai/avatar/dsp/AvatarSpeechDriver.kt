package com.zuno.ai.avatar.dsp

import java.nio.ByteBuffer

/**
 * Mutable per-frame facial expression & mouth-tile output populated by [AvatarSpeechDriver.step]
 * to avoid per-frame object allocations on the GL render thread.
 */
class AvatarFrameExpression {
    var tileIndex: Int = -1
    var speaking: Boolean = false
    var loudness: Float = 0f
    var presence: Float = 0f
    var browRaise: Float = 0f
    var cheekLift: Float = 0f
    var eyeEngage: Float = 0f

    fun clear() {
        tileIndex = -1
        speaking = false
        loudness = 0f
        presence = 0f
        browRaise = 0f
        cheekLift = 0f
        eyeEngage = 0f
    }
}

/**
 * Audio-to-expression driver abstraction.
 * Allows plugging in crisp-clips, phoneme-rig MLP, or future neural/viseme drivers
 * without changing the OpenGL compositor or Compose UI.
 */
interface AvatarSpeechDriver {
    fun pushPcm16(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        channels: Int,
        frameCount: Int,
    )

    fun pushPcm16Array(
        samples: ShortArray,
        offsetShorts: Int,
        frameCount: Int,
        sampleRate: Int,
        channels: Int,
    )

    fun step(
        dtSec: Float,
        targetDelayMs: Float,
        nowMs: Long,
        out: AvatarFrameExpression,
    )

    fun reset()
}
