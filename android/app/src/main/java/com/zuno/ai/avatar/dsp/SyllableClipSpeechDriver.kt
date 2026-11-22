package com.zuno.ai.avatar.dsp

import com.zuno.ai.avatar.model.CrispClipMeta
import com.zuno.ai.avatar.model.SpeechClipBucket
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zero-blur discrete syllable clip scheduler (`mode: "crisp-clips"`), ported 1:1 from
 * `syllableClipDriver.ts`.
 *
 * - Runs pure scalar 10 ms band-split DSP (< 0.02 ms/frame) with pre-allocated ring buffers.
 * - Zero heap allocations in [pushPcm16], [pushPcm16Array], and [step].
 * - Outputs an exact integer tile index so lip edges remain razor-sharp without crossfade blur,
 *   plus smooth prosody-driven eyebrow, cheek, and lower-eyelid envelopes.
 */
class SyllableClipSpeechDriver(
    private val allClips: List<CrispClipMeta>,
    fps: Float = 25f,
) : AvatarSpeechDriver {

    private val fps = max(10f, fps)
    private val softClips = allClips.filter { it.bucket == SpeechClipBucket.SOFT }
    private val mediumClips = allClips.filter { it.bucket == SpeechClipBucket.MEDIUM }
    private val wideClips = allClips.filter { it.bucket == SpeechClipBucket.WIDE }
    private val roundClips = allClips.filter { it.bucket == SpeechClipBucket.ROUND }

    private val pickCursor = IntArray(4)
    private var lastPickedId = -1

    // 10 ms audio energy ring buffers (640 ms history)
    private val ringLock = Any()
    private val rmsRing = FloatArray(RING_CAP)
    private val lowRing = FloatArray(RING_CAP)
    private val highRing = FloatArray(RING_CAP)
    private var writeCount = 0

    private var accSq = 0f
    private var accLowSq = 0f
    private var accHighSq = 0f
    private var accCount = 0
    private var lpState = 0f

    // Active discrete clip playback state (owned by GL render thread)
    private var activeClip: CrispClipMeta? = null
    private var clipCursor = 0f
    private var exitOffset: Int? = null
    private var lastClosedTile = -1
    private var lastVoiceMs = -10_000L
    private var lastLoud = 0f
    private var lastRound = 0f
    private var lastBucketRead = -1

    // Smooth prosody-driven facial muscle states (0..1)
    private var presenceEnv = 0f
    private var baselineLoud = 0f
    private var browRaiseEnv = 0f
    private var cheekLiftEnv = 0f
    private var eyeEngageEnv = 0f
    private var speakTimeSec = 0f

    override fun reset() {
        synchronized(ringLock) {
            rmsRing.fill(0f)
            lowRing.fill(0f)
            highRing.fill(0f)
            writeCount = 0
            accSq = 0f
            accLowSq = 0f
            accHighSq = 0f
            accCount = 0
            lpState = 0f
        }
        activeClip = null
        clipCursor = 0f
        exitOffset = null
        lastClosedTile = -1
        lastVoiceMs = -10_000L
        lastLoud = 0f
        lastRound = 0f
        lastBucketRead = -1
        presenceEnv = 0f
        baselineLoud = 0f
        browRaiseEnv = 0f
        cheekLiftEnv = 0f
        eyeEngageEnv = 0f
        speakTimeSec = 0f
    }

    override fun pushPcm16(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        channels: Int,
        frameCount: Int,
    ) {
        if (bitsPerSample != 16 || frameCount <= 0 || sampleRate <= 0 || channels <= 0) return
        val basePos = audioData.position()
        val requiredBytes = frameCount * channels * 2
        if (basePos + requiredBytes > audioData.limit()) return

        val bucketSamples = max(1, ((sampleRate * BUCKET_MS) / 1000f).roundToInt())
        val rc = 1f / (2f * PI.toFloat() * 950f)
        val dt = 1f / sampleRate.toFloat()
        val alphaLp = dt / (rc + dt)
        val invChannels = 1f / (32768f * channels)

        synchronized(ringLock) {
            var lp = lpState
            var sq = accSq
            var lowSq = accLowSq
            var highSq = accHighSq
            var cnt = accCount

            var byteOffset = basePos
            val stride = channels * 2
            for (f in 0 until frameCount) {
                var sum = 0
                var chOffset = byteOffset
                for (c in 0 until channels) {
                    val lo = audioData.get(chOffset).toInt() and 0xFF
                    val hi = audioData.get(chOffset + 1).toInt()
                    sum += (hi shl 8) or lo
                    chOffset += 2
                }
                byteOffset += stride

                val x = sum * invChannels
                lp += alphaLp * (x - lp)
                val hp = x - lp
                sq += x * x
                lowSq += lp * lp
                highSq += hp * hp
                cnt++

                if (cnt >= bucketSamples) {
                    val inv = 1f / cnt
                    val slot = writeCount % RING_CAP
                    rmsRing[slot] = sqrt(sq * inv)
                    lowRing[slot] = sqrt(lowSq * inv)
                    highRing[slot] = sqrt(highSq * inv)
                    writeCount++
                    sq = 0f
                    lowSq = 0f
                    highSq = 0f
                    cnt = 0
                }
            }

            lpState = lp
            accSq = sq
            accLowSq = lowSq
            accHighSq = highSq
            accCount = cnt
        }
    }

    override fun pushPcm16Array(
        samples: ShortArray,
        offsetShorts: Int,
        frameCount: Int,
        sampleRate: Int,
        channels: Int,
    ) {
        if (frameCount <= 0 || sampleRate <= 0 || channels <= 0) return
        val needed = offsetShorts + frameCount * channels
        if (offsetShorts < 0 || needed > samples.size) return

        val bucketSamples = max(1, ((sampleRate * BUCKET_MS) / 1000f).roundToInt())
        val rc = 1f / (2f * PI.toFloat() * 950f)
        val dt = 1f / sampleRate.toFloat()
        val alphaLp = dt / (rc + dt)
        val invChannels = 1f / (32768f * channels)

        synchronized(ringLock) {
            var lp = lpState
            var sq = accSq
            var lowSq = accLowSq
            var highSq = accHighSq
            var cnt = accCount

            var idx = offsetShorts
            for (f in 0 until frameCount) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += samples[idx++].toInt()
                }
                val x = sum * invChannels
                lp += alphaLp * (x - lp)
                val hp = x - lp
                sq += x * x
                lowSq += lp * lp
                highSq += hp * hp
                cnt++

                if (cnt >= bucketSamples) {
                    val inv = 1f / cnt
                    val slot = writeCount % RING_CAP
                    rmsRing[slot] = sqrt(sq * inv)
                    lowRing[slot] = sqrt(lowSq * inv)
                    highRing[slot] = sqrt(highSq * inv)
                    writeCount++
                    sq = 0f
                    lowSq = 0f
                    highSq = 0f
                    cnt = 0
                }
            }

            lpState = lp
            accSq = sq
            accLowSq = lowSq
            accHighSq = highSq
            accCount = cnt
        }
    }

    private fun pickClip(loud: Float, roundness: Float): CrispClipMeta? {
        if (allClips.isEmpty()) return null

        val bucketIdx: Int
        val pool: List<CrispClipMeta>
        when {
            roundness > 0.56f && roundClips.isNotEmpty() -> {
                bucketIdx = 3
                pool = roundClips
            }
            loud < 0.38f && softClips.isNotEmpty() -> {
                bucketIdx = 0
                pool = softClips
            }
            loud > 0.68f && wideClips.isNotEmpty() -> {
                bucketIdx = 2
                pool = wideClips
            }
            mediumClips.isNotEmpty() -> {
                bucketIdx = 1
                pool = mediumClips
            }
            wideClips.isNotEmpty() -> {
                bucketIdx = 2
                pool = wideClips
            }
            softClips.isNotEmpty() -> {
                bucketIdx = 0
                pool = softClips
            }
            roundClips.isNotEmpty() -> {
                bucketIdx = 3
                pool = roundClips
            }
            else -> {
                bucketIdx = 1
                pool = allClips
            }
        }

        var idx = pickCursor[bucketIdx] % pool.size
        var chosen = pool[idx]
        if (pool.size > 1 && chosen.id == lastPickedId) {
            idx = (idx + 1) % pool.size
            chosen = pool[idx]
        }
        pickCursor[bucketIdx] = idx + 1
        lastPickedId = chosen.id
        return chosen
    }

    override fun step(
        dtSec: Float,
        targetDelayMs: Float,
        nowMs: Long,
        out: AvatarFrameExpression,
    ) {
        val dt = dtSec.coerceIn(0.001f, 0.05f)

        // 1. Sample 10 ms audio ring buffer aligned to expected speaker delay
        var sampledValid = false
        var rms = 0f
        var lowE = 0f
        var highE = 0f
        var bucketIdx = -1

        synchronized(ringLock) {
            if (writeCount > 0) {
                val delayBuckets = max(0, (targetDelayMs / BUCKET_MS).roundToInt())
                val idx = writeCount - 1 - delayBuckets
                bucketIdx = idx
                if (idx >= 0 && idx != lastBucketRead) {
                    sampledValid = true
                    val s0 = ((idx % RING_CAP) + RING_CAP) % RING_CAP
                    val s1 = (((idx - 1) % RING_CAP) + RING_CAP) % RING_CAP
                    if (idx > 0) {
                        rms = 0.65f * rmsRing[s0] + 0.35f * rmsRing[s1]
                        lowE = 0.65f * lowRing[s0] + 0.35f * lowRing[s1]
                        highE = 0.65f * highRing[s0] + 0.35f * highRing[s1]
                    } else {
                        rms = rmsRing[s0]
                        lowE = lowRing[s0]
                        highE = highRing[s0]
                    }
                }
            }
        }

        if (sampledValid) {
            lastBucketRead = bucketIdx
            val db = 20f * log10(max(rms, 1e-5f))
            lastLoud = ((db + 42f) / 26f).coerceIn(0f, 1f)
            val ratio = lowE / (lowE + highE + 1e-6f)
            lastRound = ((ratio - 0.54f) / 0.28f).coerceIn(0f, 1f)

            if (lastLoud >= LOUD_ON_THRESH) {
                lastVoiceMs = nowMs
                exitOffset = null
            }
        } else if (bucketIdx < 0) {
            lastLoud = 0f
        }

        val voiceActive = nowMs - lastVoiceMs <= SILENCE_STOP_MS

        // 2. Select discrete tile index (exact integer -> zero lip crossfade blur)
        var tileIndex = -1
        var speaking = false
        var outLoud = 0f

        val clip = activeClip
        if (clip == null) {
            if (voiceActive && lastLoud >= LOUD_ON_THRESH) {
                val next = pickClip(lastLoud, lastRound)
                if (next != null) {
                    activeClip = next
                    clipCursor = 0f
                    exitOffset = null
                    lastClosedTile = next.startTile
                    tileIndex = next.startTile
                    speaking = true
                    outLoud = lastLoud
                }
            }
        } else {
            val speed = if (voiceActive) 0.92f + 0.22f * lastLoud else 1.18f
            clipCursor += dt * fps * speed
            val curOff = floor(clipCursor).toInt()
            val clipLen = clip.length

            if (!voiceActive && exitOffset == null) {
                var bestClose: Int? = null
                for (co in clip.closeOffsets) {
                    if (co in curOff..(curOff + 5)) {
                        bestClose = co
                        break
                    }
                }
                exitOffset = bestClose ?: (clipLen - 1)
            }

            val scheduledExit = exitOffset
            if (scheduledExit != null && curOff >= scheduledExit) {
                val tile = clip.startTile + min(clipLen - 1, scheduledExit)
                lastClosedTile = tile
                activeClip = null
                clipCursor = 0f
                exitOffset = null
                tileIndex = tile
                speaking = false
                outLoud = 0f
            } else if (curOff >= clipLen) {
                if (voiceActive) {
                    val next = pickClip(lastLoud, lastRound)
                    if (next != null) {
                        val frac = (clipCursor - clipLen).coerceIn(0f, 0.99f)
                        activeClip = next
                        clipCursor = (if (next.length > 2) 1f else 0f) + frac
                        exitOffset = null
                        tileIndex = next.startTile + min(next.length - 1, floor(clipCursor).toInt())
                        speaking = true
                        outLoud = lastLoud
                    }
                } else {
                    lastClosedTile = clip.startTile + clipLen - 1
                    activeClip = null
                    clipCursor = 0f
                    exitOffset = null
                    tileIndex = -1
                    speaking = false
                    outLoud = 0f
                }
            } else {
                tileIndex = clip.startTile + max(0, curOff)
                speaking = true
                outLoud = lastLoud
            }
        }

        // 3. Smooth closed-mouth presence ramp (~50 ms onset, ~75 ms closed exit dissolve)
        val targetPresence = if (speaking) 1f else 0f
        val presTau = if (speaking) 0.028f else 0.042f
        val presAlpha = 1f - exp(-dt / presTau)
        presenceEnv += (targetPresence - presenceEnv) * presAlpha
        if (speaking && presenceEnv > 0.96f) {
            presenceEnv = 1f
        } else if (!speaking && presenceEnv < 0.01f) {
            presenceEnv = 0f
            lastClosedTile = -1
        }

        if (tileIndex < 0 && !speaking && presenceEnv > 0.01f && lastClosedTile > 0) {
            tileIndex = lastClosedTile
        }

        // 4. Prosody-driven upper-face muscle envelopes (brows, mid-cheeks, lower lids)
        if (speaking) {
            speakTimeSec += dt
        } else if (presenceEnv <= 0.01f) {
            speakTimeSec = 0f
        }

        val baseTau = if (speaking) 0.55f else 0.25f
        baselineLoud += (outLoud - baselineLoud) * (1f - exp(-dt / baseTau))

        val stress = max(0f, outLoud - baselineLoud * 0.72f)
        val organicWave = if (speaking) {
            0.5f + 0.5f * sin(speakTimeSec * 2.1f) * cos(speakTimeSec * 0.85f)
        } else {
            0f
        }

        val targetBrow = if (speaking) {
            min(1f, 0.14f * organicWave + max(0f, outLoud - 0.34f) * 0.85f + stress * 1.45f)
        } else {
            0f
        }
        val targetCheek = if (speaking) {
            min(1f, 0.22f + outLoud * 0.78f)
        } else {
            0f
        }
        val targetEye = if (speaking) {
            min(1f, 0.12f + max(0f, outLoud - 0.22f) * 0.75f + stress * 0.65f)
        } else {
            0f
        }

        val browTau = if (targetBrow > browRaiseEnv) 0.11f else 0.30f
        val cheekTau = if (targetCheek > cheekLiftEnv) 0.085f else 0.22f
        val eyeTau = if (targetEye > eyeEngageEnv) 0.12f else 0.28f

        browRaiseEnv += (targetBrow - browRaiseEnv) * (1f - exp(-dt / browTau))
        cheekLiftEnv += (targetCheek - cheekLiftEnv) * (1f - exp(-dt / cheekTau))
        eyeEngageEnv += (targetEye - eyeEngageEnv) * (1f - exp(-dt / eyeTau))

        out.tileIndex = tileIndex
        out.speaking = speaking
        out.loudness = outLoud
        out.presence = presenceEnv
        out.browRaise = browRaiseEnv
        out.cheekLift = cheekLiftEnv
        out.eyeEngage = eyeEngageEnv
    }

    companion object {
        private const val BUCKET_MS = 10
        private const val RING_CAP = 64
        private const val LOUD_ON_THRESH = 0.11f
        private const val SILENCE_STOP_MS = 135L
    }
}
