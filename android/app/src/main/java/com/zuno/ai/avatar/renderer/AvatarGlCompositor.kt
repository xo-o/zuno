package com.zuno.ai.avatar.renderer

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import com.zuno.ai.avatar.dsp.AvatarFrameExpression
import com.zuno.ai.avatar.model.AvatarBundleManifest
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure OpenGL ES 3.0 compositor for a single avatar bundle.
 * Must be created, invoked, and destroyed on the dedicated GL thread.
 */
internal class AvatarGlCompositor(
    private val manifest: AvatarBundleManifest,
    maskBitmap: Bitmap,
    stripBitmap: Bitmap,
) {
    val videoTextureId: Int
    private val maskTextureId: Int
    private val stripTextureId: Int
    private val programId: Int
    private val vaoId: Int

    private val stripWidth: Int
    private val stripHeight: Int
    private val hasFaceRig: Boolean = manifest.faceRig != null

    private val uCropRect: Int
    private val uStMatrix: Int
    private val uRect: Int
    private val uGrid: Int
    private val uTileTexel: Int
    private val uFrameSize: Int
    private val uOpen: Int
    private val uRowWeights: Int
    private val uDiscreteCell: Int
    private val uDualMask: Int
    private val uPresence: Int
    private val uFaceExpr: Int
    private val uFaceShift: Int
    private val uLeftBrow: Int
    private val uRightBrow: Int
    private val uLeftCheek: Int
    private val uRightCheek: Int
    private val uLeftLid: Int
    private val uRightLid: Int
    private val uFaceLimits: Int

    init {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, AvatarGlShaders.VERTEX_SHADER)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, AvatarGlShaders.FRAGMENT_SHADER)
        val prog = GLES30.glCreateProgram()
        check(prog != 0) { "glCreateProgram failed" }
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog)
            GLES30.glDeleteProgram(prog)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            error("GL program link failed: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        programId = prog

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]

        val textures = IntArray(3)
        GLES30.glGenTextures(3, textures, 0)
        videoTextureId = textures[0]
        maskTextureId = textures[1]
        stripTextureId = textures[2]

        // External OES texture for hardware-decoded video
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Mask texture
        uploadTexture2D(maskTextureId, maskBitmap)

        // Strip texture (safely downscale only if GPU GL_MAX_TEXTURE_SIZE is unusually small)
        val maxTexSizeArr = IntArray(1)
        GLES30.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexSizeArr, 0)
        val maxTexSize = max(2048, maxTexSizeArr[0])
        val maxDim = max(stripBitmap.width, stripBitmap.height)
        if (maxDim > maxTexSize) {
            val scale = maxTexSize.toFloat() / maxDim.toFloat()
            val scaledW = max(1, (stripBitmap.width * scale).toInt())
            val scaledH = max(1, (stripBitmap.height * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(stripBitmap, scaledW, scaledH, true)
            stripWidth = scaled.width
            stripHeight = scaled.height
            uploadTexture2D(stripTextureId, scaled)
            scaled.recycle()
        } else {
            stripWidth = stripBitmap.width
            stripHeight = stripBitmap.height
            uploadTexture2D(stripTextureId, stripBitmap)
        }

        GLES30.glUseProgram(programId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uVideoTex"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uMaskTex"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uStripTex"), 2)

        uCropRect = GLES30.glGetUniformLocation(programId, "uCropRect")
        uStMatrix = GLES30.glGetUniformLocation(programId, "uStMatrix")
        uRect = GLES30.glGetUniformLocation(programId, "uRect")
        uGrid = GLES30.glGetUniformLocation(programId, "uGrid")
        uTileTexel = GLES30.glGetUniformLocation(programId, "uTileTexel")
        uFrameSize = GLES30.glGetUniformLocation(programId, "uFrameSize")
        uOpen = GLES30.glGetUniformLocation(programId, "uOpen")
        uRowWeights = GLES30.glGetUniformLocation(programId, "uRowWeights")
        uDiscreteCell = GLES30.glGetUniformLocation(programId, "uDiscreteCell")
        uDualMask = GLES30.glGetUniformLocation(programId, "uDualMask")
        uPresence = GLES30.glGetUniformLocation(programId, "uPresence")
        uFaceExpr = GLES30.glGetUniformLocation(programId, "uFaceExpr")
        uFaceShift = GLES30.glGetUniformLocation(programId, "uFaceShift")
        uLeftBrow = GLES30.glGetUniformLocation(programId, "uLeftBrow")
        uRightBrow = GLES30.glGetUniformLocation(programId, "uRightBrow")
        uLeftCheek = GLES30.glGetUniformLocation(programId, "uLeftCheek")
        uRightCheek = GLES30.glGetUniformLocation(programId, "uRightCheek")
        uLeftLid = GLES30.glGetUniformLocation(programId, "uLeftLid")
        uRightLid = GLES30.glGetUniformLocation(programId, "uRightLid")
        uFaceLimits = GLES30.glGetUniformLocation(programId, "uFaceLimits")

        GLES30.glUniform2f(uFrameSize, manifest.width.toFloat(), manifest.height.toFloat())
        GLES30.glUniform2f(uGrid, manifest.gridCols.toFloat(), manifest.gridRows.toFloat())
        GLES30.glUniform2f(
            uTileTexel,
            1f / max(1, stripWidth).toFloat(),
            1f / max(1, stripHeight).toFloat(),
        )
        GLES30.glUniform1f(uDualMask, if (manifest.maskChannels >= 2) 1f else 0f)

        val rig = manifest.faceRig
        if (rig != null) {
            GLES30.glUniform4fv(uLeftBrow, 1, rig.leftBrow, 0)
            GLES30.glUniform4fv(uRightBrow, 1, rig.rightBrow, 0)
            GLES30.glUniform4fv(uLeftCheek, 1, rig.leftCheek, 0)
            GLES30.glUniform4fv(uRightCheek, 1, rig.rightCheek, 0)
            GLES30.glUniform4fv(uLeftLid, 1, rig.leftLowerLid, 0)
            GLES30.glUniform4fv(uRightLid, 1, rig.rightLowerLid, 0)
            GLES30.glUniform3f(uFaceLimits, rig.eyeTopY, rig.eyeCenterY, rig.noseTipY)
        }
    }

    fun render(
        viewWidth: Int,
        viewHeight: Int,
        stMatrix: FloatArray,
        coords: FloatArray,
        faceShiftX: Float,
        faceShiftY: Float,
        expression: AvatarFrameExpression,
    ) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        GLES30.glViewport(0, 0, viewWidth, viewHeight)
        GLES30.glUseProgram(programId)
        GLES30.glBindVertexArray(vaoId)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stripTextureId)

        // ContentScale.Crop + Alignment.TopCenter framing inside the 16:9 video
        val srcAspect = manifest.width.toFloat() / max(1, manifest.height).toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        if (viewAspect < srcAspect) {
            val cropW = viewAspect / srcAspect
            GLES30.glUniform4f(uCropRect, (1f - cropW) * 0.5f, 0f, cropW, 1f)
        } else {
            val cropH = srcAspect / viewAspect
            GLES30.glUniform4f(uCropRect, 0f, 0f, 1f, cropH)
        }

        GLES30.glUniformMatrix4fv(uStMatrix, 1, false, stMatrix, 0)

        val w = max(1f, manifest.width.toFloat())
        val h = max(1f, manifest.height.toFloat())
        val x1 = coords[0]
        val y1 = coords[1]
        val x2 = coords[2]
        val y2 = coords[3]
        GLES30.glUniform4f(uRect, x1 / w, y1 / h, (x2 - x1) / w, (y2 - y1) / h)

        GLES30.glUniform1f(uOpen, expression.loudness)
        GLES30.glUniform4f(uRowWeights, 1f, 0f, 0f, 0f)
        GLES30.glUniform1f(uPresence, expression.presence)
        GLES30.glUniform4f(
            uFaceExpr,
            expression.browRaise,
            expression.cheekLift,
            expression.eyeEngage,
            if (hasFaceRig) 1f else 0f,
        )
        GLES30.glUniform2f(uFaceShift, faceShiftX, faceShiftY)

        if (expression.tileIndex >= 0) {
            val maxTile = max(0, manifest.gridCols * manifest.gridRows - 1)
            val safeTile = expression.tileIndex.coerceIn(0, maxTile)
            val col = safeTile % manifest.gridCols
            val row = floor(safeTile.toFloat() / manifest.gridCols.toFloat()).toInt()
            GLES30.glUniform2f(uDiscreteCell, col.toFloat(), row.toFloat())
        } else {
            GLES30.glUniform2f(uDiscreteCell, -1f, -1f)
        }

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        GLES30.glDeleteTextures(3, intArrayOf(videoTextureId, maskTextureId, stripTextureId), 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
        GLES30.glDeleteProgram(programId)
    }

    private fun uploadTexture2D(textureId: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        check(shader != 0) { "glCreateShader failed for type=$type" }
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Shader compile failed ($type): $log")
        }
        return shader
    }
}
