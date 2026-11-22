package com.zuno.ai.avatar.renderer

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import com.zuno.ai.avatar.audio.AvatarSpeechSink
import com.zuno.ai.avatar.dsp.AvatarFrameExpression
import com.zuno.ai.avatar.dsp.AvatarSpeechDriver
import com.zuno.ai.avatar.dsp.SyllableClipSpeechDriver
import com.zuno.ai.avatar.model.AvatarBundleManifest
import com.zuno.ai.avatar.repository.LoadedAvatarBundle
import kotlin.math.floor
import kotlin.math.max

/**
 * Owns the dedicated OpenGL ES 3.0 thread, hardware `MediaPlayer` idle loop,
 * adaptive 25 FPS (idle) / 60 FPS (speaking) frame pacing, and clean EGL lifecycle.
 */
internal class AvatarRenderController(
    private val speechSink: AvatarSpeechSink?,
    private val onFirstFrameRendered: () -> Unit,
    private val onFatalError: (Throwable) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val glThread = HandlerThread("ZunoAvatarGL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    @Volatile
    private var released = false

    @Volatile
    private var paused = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var outputSurfaceTexture: SurfaceTexture? = null
    private var viewWidth = 0
    private var viewHeight = 0

    private var loadedBundle: LoadedAvatarBundle? = null
    private var compositor: AvatarGlCompositor? = null
    private var speechDriver: AvatarSpeechDriver? = null

    private var videoSurfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerPrepared = false
    private var videoFrameAvailable = false
    private var firstFrameDispatched = false

    private val stMatrix = FloatArray(16).apply {
        android.opengl.Matrix.setIdentityM(this, 0)
    }
    private val interpolatedCoords = FloatArray(4)
    private val expression = AvatarFrameExpression()
    private var lastStepNanos = 0L
    private var choreographer: Choreographer? = null
    private var vsyncActive = false

    private val vsyncCallback = Choreographer.FrameCallback {
        vsyncActive = false
        if (released || paused) return@FrameCallback
        renderStep()
    }

    fun attachOutputSurface(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        glHandler.post {
            if (released) return@post
            outputSurfaceTexture = surfaceTexture
            viewWidth = width
            viewHeight = height
            try {
                ensureEglContextAndSurface(surfaceTexture)
                tryCompletePipelineInit()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach output EGL surface", t)
                notifyFatalError(t)
            }
        }
    }

    fun onOutputSurfaceSizeChanged(width: Int, height: Int) {
        glHandler.post {
            viewWidth = width
            viewHeight = height
            if (!released && compositor != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                renderStep()
            }
        }
    }

    fun detachOutputSurface() {
        glHandler.post {
            stopVsync()
            outputSurfaceTexture = null
            destroyWindowSurface()
        }
    }

    fun setBundle(bundle: LoadedAvatarBundle) {
        glHandler.post {
            if (released) {
                bundle.close()
                return@post
            }
            if (loadedBundle != null) {
                bundle.close()
                return@post
            }
            loadedBundle = bundle
            try {
                tryCompletePipelineInit()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize avatar GL pipeline", t)
                notifyFatalError(t)
            }
        }
    }

    fun setPaused(shouldPause: Boolean) {
        glHandler.post {
            if (released || paused == shouldPause) return@post
            paused = shouldPause
            if (shouldPause) {
                stopVsync()
                runCatching {
                    if (mediaPlayerPrepared && mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.pause()
                    }
                }
            } else {
                lastStepNanos = 0L
                runCatching {
                    if (mediaPlayerPrepared && mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                    }
                }
                scheduleSingleDraw()
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        glHandler.post {
            stopVsync()
            speechDriver?.let { speechSink?.detachDriver(it) }
            speechDriver = null

            runCatching {
                mediaPlayer?.apply {
                    setOnPreparedListener(null)
                    setOnErrorListener(null)
                    if (isPlaying) stop()
                    release()
                }
            }
            mediaPlayer = null
            mediaPlayerPrepared = false

            videoSurface?.release()
            videoSurface = null
            videoSurfaceTexture?.setOnFrameAvailableListener(null)
            videoSurfaceTexture?.release()
            videoSurfaceTexture = null

            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                } else {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext)
                }
            }
            if (EGL14.eglGetCurrentContext() != EGL14.EGL_NO_CONTEXT) {
                compositor?.release()
            }
            compositor = null

            loadedBundle?.close()
            loadedBundle = null

            destroyEglCompletely()
            glThread.quitSafely()
        }
    }

    private fun tryCompletePipelineInit() {
        val bundle = loadedBundle ?: return
        if (eglContext == EGL14.EGL_NO_CONTEXT || compositor != null) return

        if (choreographer == null) {
            choreographer = Choreographer.getInstance()
        }

        val comp = AvatarGlCompositor(
            manifest = bundle.manifest,
            maskBitmap = bundle.maskBitmap,
            stripBitmap = bundle.stripBitmap,
        )
        // Uploaded to GPU textures -> free CPU bitmap memory immediately
        bundle.recycleBitmaps()
        compositor = comp

        val driver = SyllableClipSpeechDriver(
            allClips = bundle.manifest.clips,
            fps = bundle.manifest.fps,
        )
        speechDriver = driver
        speechSink?.attachDriver(driver)

        val vst = SurfaceTexture(comp.videoTextureId).apply {
            setDefaultBufferSize(bundle.manifest.width, bundle.manifest.height)
            setOnFrameAvailableListener({
                if (!released) {
                    videoFrameAvailable = true
                    if (!vsyncActive && !paused) {
                        renderStep()
                    }
                }
            }, glHandler)
        }
        videoSurfaceTexture = vst
        val surface = Surface(vst)
        videoSurface = surface

        val player = MediaPlayer().apply {
            setSurface(surface)
            setVolume(0f, 0f)
            isLooping = true
            setOnPreparedListener { mp ->
                if (released) return@setOnPreparedListener
                mediaPlayerPrepared = true
                if (!paused) {
                    runCatching { mp.start() }
                }
            }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Idle video MediaPlayer error what=$what extra=$extra")
                false
            }
            bundle.idleVideo.applyTo(this)
            prepareAsync()
        }
        mediaPlayer = player
    }

    private fun renderStep() {
        val comp = compositor ?: return
        val bundle = loadedBundle ?: return
        val vst = videoSurfaceTexture ?: return
        if (eglSurface == EGL14.EGL_NO_SURFACE || viewWidth <= 0 || viewHeight <= 0) return

        var updatedVideo = false
        if (videoFrameAvailable) {
            videoFrameAvailable = false
            runCatching {
                vst.updateTexImage()
                vst.getTransformMatrix(stMatrix)
                updatedVideo = true
            }
        }
        if (!firstFrameDispatched && !updatedVideo) {
            return
        }

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val dtSec = if (lastStepNanos > 0L) {
            ((nowNanos - lastStepNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        } else {
            1f / 60f
        }
        lastStepNanos = nowNanos

        val nowMs = nowNanos / 1_000_000L
        speechDriver?.step(
            dtSec = dtSec,
            targetDelayMs = TARGET_AUDIO_DELAY_MS,
            nowMs = nowMs,
            out = expression,
        ) ?: expression.clear()

        val mediaPosMs = if (mediaPlayerPrepared) {
            runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)
        } else {
            0
        }
        sampleTrackedCoords(bundle.manifest, mediaPosMs / 1000f, interpolatedCoords)
        val manifest = bundle.manifest
        val faceShiftX = (interpolatedCoords[0] - manifest.baseRect[0]) / max(1, manifest.width).toFloat()
        val faceShiftY = (interpolatedCoords[1] - manifest.baseRect[1]) / max(1, manifest.height).toFloat()

        comp.render(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            stMatrix = stMatrix,
            coords = interpolatedCoords,
            faceShiftX = faceShiftX,
            faceShiftY = faceShiftY,
            expression = expression,
        )

        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            val err = EGL14.eglGetError()
            if (!released && outputSurfaceTexture != null && err != EGL14.EGL_BAD_SURFACE) {
                Log.w(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(err)}")
            }
        } else if (!firstFrameDispatched) {
            firstFrameDispatched = true
            mainHandler.post(onFirstFrameRendered)
        }

        // Adaptive FPS pacing:
        // - Active speech / feathering -> 60 FPS Choreographer vsync for smooth lip & prosody motion
        // - Silent / idle -> 0 Choreographer callbacks (driven strictly by 25 FPS video frame arrivals)
        if (!paused && (expression.speaking || expression.presence > 0.002f)) {
            scheduleVsync()
        }
    }

    private fun scheduleSingleDraw() {
        glHandler.post {
            if (!released && !paused) {
                renderStep()
            }
        }
    }

    private fun scheduleVsync() {
        if (!vsyncActive && !released && !paused) {
            vsyncActive = true
            choreographer?.postFrameCallback(vsyncCallback)
        }
    }

    private fun stopVsync() {
        if (vsyncActive) {
            vsyncActive = false
            choreographer?.removeFrameCallback(vsyncCallback)
        }
    }

    private fun sampleTrackedCoords(
        manifest: AvatarBundleManifest,
        mediaTimeSec: Float,
        out: FloatArray,
    ) {
        val coords = manifest.coords
        val n = coords.size
        if (n == 0) {
            for (i in 0 until 4) out[i] = manifest.baseRect[i]
            return
        }
        val fPos = (((mediaTimeSec * manifest.fps) % n) + n) % n
        val i0 = floor(fPos).toInt() % n
        val i1 = (i0 + 1) % n
        val frac = fPos - floor(fPos)
        val a = coords[i0]
        val b = coords[i1]
        for (i in 0 until 4) {
            out[i] = a[i] + (b[i] - a[i]) * frac
        }
    }

    private fun ensureEglContextAndSurface(surfaceTexture: SurfaceTexture) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
            eglDisplay = display

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
                "eglChooseConfig for ES 3.0 failed"
            }
            val config = configs[0]!!
            eglConfig = config

            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE,
            )
            val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            check(ctx != EGL14.EGL_NO_CONTEXT) { "eglCreateContext ES 3.0 failed" }
            eglContext = ctx
        }

        destroyWindowSurface()
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val winSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfaceAttribs, 0)
        check(winSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        eglSurface = winSurface
        check(EGL14.eglMakeCurrent(eglDisplay, winSurface, winSurface, eglContext)) { "eglMakeCurrent failed" }
    }

    private fun destroyWindowSurface() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private fun destroyEglCompletely() {
        destroyWindowSurface()
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    private fun notifyFatalError(t: Throwable) {
        mainHandler.post { onFatalError(t) }
    }

    companion object {
        private const val TAG = "AvatarRenderController"
        private const val EGL_OPENGL_ES3_BIT = 0x0040
        private const val TARGET_AUDIO_DELAY_MS = 28f
    }
}
