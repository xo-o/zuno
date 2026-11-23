package com.zuno.ai.avatar

import com.zuno.ai.avatar.audio.AvatarSpeechSink
import com.zuno.ai.avatar.audio.WavPcmFeeder
import com.zuno.ai.avatar.catalog.TutorAvatarCatalog
import com.zuno.ai.avatar.dsp.AvatarFrameExpression
import com.zuno.ai.avatar.dsp.SyllableClipSpeechDriver
import com.zuno.ai.avatar.model.AvatarBundleManifestParser
import com.zuno.ai.avatar.model.AvatarBundleMode
import com.zuno.ai.avatar.model.CrispClipMeta
import com.zuno.ai.avatar.model.SpeechClipBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AvatarEngineTest {

    @Test
    fun parseManifest_readsCrispClipsFaceRigAndCoords() {
        val json = """
            {
              "version": 4,
              "mode": "crisp-clips",
              "avatarId": "elena",
              "width": 1280,
              "height": 720,
              "fps": 25,
              "numFrames": 2,
              "gridCols": 14,
              "gridRows": 18,
              "numTiles": 244,
              "tileWidth": 192,
              "tileHeight": 144,
              "maskChannels": 2,
              "baseRect": [548, 214, 740, 358],
              "coords": [[548.0, 214.0, 740.0, 358.0], [548.5, 214.2, 740.5, 358.2]],
              "faceRig": {
                "leftBrow": [0.46, 0.20, 0.02, 0.03],
                "rightBrow": [0.54, 0.20, 0.02, 0.03],
                "leftCheek": [0.46, 0.30, 0.02, 0.05],
                "rightCheek": [0.54, 0.30, 0.02, 0.05],
                "leftLowerLid": [0.47, 0.26, 0.01, 0.01],
                "rightLowerLid": [0.53, 0.26, 0.01, 0.01],
                "eyeTopY": 0.244,
                "eyeCenterY": 0.252,
                "noseTipY": 0.343
              },
              "clips": [
                {"id": 0, "startTile": 1, "length": 12, "bucket": "wide", "peak": 0.95, "closeOffsets": [2, 11]},
                {"id": 1, "startTile": 13, "length": 10, "bucket": "round", "peak": 0.80, "closeOffsets": [9]}
              ]
            }
        """.trimIndent()

        val manifest = AvatarBundleManifestParser.parse(json)
        assertEquals(AvatarBundleMode.CRISP_CLIPS, manifest.mode)
        assertEquals("elena", manifest.avatarId)
        assertEquals(1280, manifest.width)
        assertEquals(720, manifest.height)
        assertEquals(2, manifest.maskChannels)
        assertEquals(2, manifest.coords.size)
        assertNotNull(manifest.faceRig)
        assertEquals(0.244f, manifest.faceRig!!.eyeTopY, 1e-4f)
        assertEquals(2, manifest.clips.size)
        assertEquals(SpeechClipBucket.WIDE, manifest.clips[0].bucket)
        assertEquals(2, manifest.clips[0].closeOffsets[0])
    }

    @Test
    fun syllableClipDriver_transitionsFromIdleToSpeakingAndBackToIdle() {
        val clips = listOf(
            CrispClipMeta(0, startTile = 1, length = 12, bucket = SpeechClipBucket.SOFT, peak = 0.3f, closeOffsets = intArrayOf(6, 11)),
            CrispClipMeta(1, startTile = 13, length = 16, bucket = SpeechClipBucket.MEDIUM, peak = 0.6f, closeOffsets = intArrayOf(8, 15)),
            CrispClipMeta(2, startTile = 29, length = 16, bucket = SpeechClipBucket.WIDE, peak = 0.95f, closeOffsets = intArrayOf(7, 15)),
            CrispClipMeta(3, startTile = 45, length = 14, bucket = SpeechClipBucket.ROUND, peak = 0.85f, closeOffsets = intArrayOf(6, 13)),
        )
        val driver = SyllableClipSpeechDriver(clips, fps = 25f)
        val sink = AvatarSpeechSink().apply { attachDriver(driver) }
        val expr = AvatarFrameExpression()

        // 1. Silence stays idle
        val silence = ByteBuffer.allocateDirect(480 * 2).order(ByteOrder.LITTLE_ENDIAN)
        sink.pushPcm16(silence, bitsPerSample = 16, sampleRate = 24000, channels = 1, frameCount = 480)
        driver.step(dtSec = 0.016f, targetDelayMs = 0f, nowMs = 100L, out = expr)
        assertFalse(expr.speaking)
        assertEquals(-1, expr.tileIndex)
        assertEquals(0f, expr.presence, 1e-4f)

        // 2. Push 120 ms of loud speech-like sine audio (650 Hz @ 24 kHz)
        val totalFrames = 24000 * 120 / 1000
        val speechBuf = ByteBuffer.allocateDirect(totalFrames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalFrames) {
            val sample = (sin(2.0 * PI * 650.0 * i / 24000.0) * 16000.0).toInt().toShort()
            speechBuf.putShort(sample)
        }
        speechBuf.flip()
        sink.pushPcm16(speechBuf, bitsPerSample = 16, sampleRate = 24000, channels = 1, frameCount = totalFrames)

        var now = 200L
        repeat(6) {
            now += 16L
            driver.step(dtSec = 0.016f, targetDelayMs = 20f, nowMs = now, out = expr)
        }
        assertTrue(expr.speaking)
        assertTrue(expr.tileIndex >= 1)
        assertTrue(expr.presence > 0.5f)
        assertTrue(expr.cheekLift > 0.05f)

        // 3. Push silence & advance past SILENCE_STOP_MS + fade-out
        val silenceChunk = ByteBuffer.allocateDirect(480 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(10) {
            silenceChunk.position(0)
            sink.pushPcm16(silenceChunk, bitsPerSample = 16, sampleRate = 24000, channels = 1, frameCount = 480)
            now += 40L
            driver.step(dtSec = 0.040f, targetDelayMs = 20f, nowMs = now, out = expr)
        }
        repeat(15) {
            now += 40L
            driver.step(dtSec = 0.040f, targetDelayMs = 20f, nowMs = now, out = expr)
        }
        assertFalse(expr.speaking)
        assertEquals(-1, expr.tileIndex)
        assertEquals(0f, expr.presence, 1e-4f)
    }

    @Test
    fun wavPcmFeeder_parsesStandardPcm16Wav() {
        val sampleCount = 240
        val dataSize = sampleCount * 2
        val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray(Charsets.US_ASCII))
        wav.putInt(36 + dataSize)
        wav.put("WAVE".toByteArray(Charsets.US_ASCII))
        wav.put("fmt ".toByteArray(Charsets.US_ASCII))
        wav.putInt(16)
        wav.putShort(1) // PCM
        wav.putShort(1) // mono
        wav.putInt(24000)
        wav.putInt(24000 * 2)
        wav.putShort(2)
        wav.putShort(16)
        wav.put("data".toByteArray(Charsets.US_ASCII))
        wav.putInt(dataSize)
        for (i in 0 until sampleCount) {
            wav.putShort((i * 100).toShort())
        }

        val tmp = File.createTempFile("zuno_wav_test_", ".wav")
        try {
            tmp.writeBytes(wav.array())
            val parsed = WavPcmFeeder.parsePcm16Wav(tmp)
            assertNotNull(parsed)
            assertEquals(24000, parsed!!.sampleRate)
            assertEquals(1, parsed.channels)
            assertEquals(16, parsed.bitsPerSample)
            assertEquals(dataSize, parsed.pcmBytes.size)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun tutorCatalog_sixTutorsHaveUniqueVoicesAndValidBundledManifests() {
        val tutors = TutorAvatarCatalog.ORDERED_TUTORS
        assertEquals(6, tutors.size)
        assertEquals("sofia", TutorAvatarCatalog.DEFAULT_TUTOR.id)
        assertEquals(6, tutors.map { it.voiceId }.toSet().size)
        assertEquals(6, tutors.map { it.voicePreset }.toSet().size)

        val assetsRoot = listOf(
            File("src/main/assets/avatars"),
            File("app/src/main/assets/avatars"),
            File("android/app/src/main/assets/avatars"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Expected bundled avatars asset directory", assetsRoot)

        for (tutor in tutors) {
            val dir = File(assetsRoot!!, tutor.id)
            assertTrue("Missing bundle dir for ${tutor.id}", dir.isDirectory)
            assertTrue("Missing idle.mp4 for ${tutor.id}", File(dir, "idle.mp4").length() > 10_000L)
            assertTrue("Missing mouth_strip.webp for ${tutor.id}", File(dir, "mouth_strip.webp").length() > 10_000L)
            assertTrue("Missing mask.png for ${tutor.id}", File(dir, "mask.png").length() > 500L)
            val metaFile = File(dir, "meta.json")
            val manifest = AvatarBundleManifestParser.parse(metaFile.readText())
            assertEquals(tutor.id, manifest.avatarId)
            assertEquals(1280, manifest.width)
            assertEquals(720, manifest.height)
            assertTrue("Expected >= 10 speech clips for ${tutor.id}", manifest.clips.size >= 10)
        }
    }
}
