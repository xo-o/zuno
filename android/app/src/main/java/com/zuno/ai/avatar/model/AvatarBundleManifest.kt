package com.zuno.ai.avatar.model

import org.json.JSONArray
import org.json.JSONObject

enum class AvatarBundleMode(val wireValue: String) {
    CRISP_CLIPS("crisp-clips"),
    SMOOTH_MORPH("smooth-morph"),
    SMOOTH_MORPH_V2("smooth-morph-v2"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(raw: String?): AvatarBundleMode =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

data class FaceRigAnchors(
    val leftBrow: FloatArray,
    val rightBrow: FloatArray,
    val leftCheek: FloatArray,
    val rightCheek: FloatArray,
    val leftLowerLid: FloatArray,
    val rightLowerLid: FloatArray,
    val eyeTopY: Float,
    val eyeCenterY: Float,
    val noseTipY: Float,
)

enum class SpeechClipBucket(val wireValue: String) {
    SOFT("soft"),
    MEDIUM("medium"),
    WIDE("wide"),
    ROUND("round");

    companion object {
        fun fromWire(raw: String?): SpeechClipBucket =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

data class CrispClipMeta(
    val id: Int,
    val startTile: Int,
    val length: Int,
    val bucket: SpeechClipBucket,
    val peak: Float,
    val closeOffsets: IntArray,
)

data class AvatarBundleManifest(
    val version: Int,
    val mode: AvatarBundleMode,
    val avatarId: String,
    val width: Int,
    val height: Int,
    val fps: Float,
    val numFrames: Int,
    val gridCols: Int,
    val gridRows: Int,
    val numTiles: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val maskChannels: Int,
    val baseRect: FloatArray,
    val coords: Array<FloatArray>,
    val faceRig: FaceRigAnchors?,
    val clips: List<CrispClipMeta>,
)

object AvatarBundleManifestParser {
    fun parse(jsonText: String): AvatarBundleManifest {
        val root = JSONObject(jsonText)
        val width = root.getInt("width")
        val height = root.getInt("height")
        val gridCols = root.getInt("gridCols")
        val gridRows = root.getInt("gridRows")
        val tileSize = root.optInt("tileSize", 192)
        val tileWidth = root.optInt("tileWidth", tileSize)
        val tileHeight = root.optInt("tileHeight", tileSize)

        val baseRect = parseFloat4(root.getJSONArray("baseRect"))
        val coordsArr = root.getJSONArray("coords")
        val coords = Array(coordsArr.length()) { idx ->
            parseFloat4(coordsArr.getJSONArray(idx))
        }

        val faceRigObj = root.optJSONObject("faceRig")
        val faceRig = if (faceRigObj != null) {
            FaceRigAnchors(
                leftBrow = parseFloat4(faceRigObj.getJSONArray("leftBrow")),
                rightBrow = parseFloat4(faceRigObj.getJSONArray("rightBrow")),
                leftCheek = parseFloat4(faceRigObj.getJSONArray("leftCheek")),
                rightCheek = parseFloat4(faceRigObj.getJSONArray("rightCheek")),
                leftLowerLid = parseFloat4(faceRigObj.getJSONArray("leftLowerLid")),
                rightLowerLid = parseFloat4(faceRigObj.getJSONArray("rightLowerLid")),
                eyeTopY = faceRigObj.optDouble("eyeTopY", 0.25).toFloat(),
                eyeCenterY = faceRigObj.optDouble("eyeCenterY", 0.26).toFloat(),
                noseTipY = faceRigObj.optDouble("noseTipY", 0.35).toFloat(),
            )
        } else {
            null
        }

        val clipsArr = root.optJSONArray("clips")
        val clips = if (clipsArr != null) {
            buildList(clipsArr.length()) {
                for (i in 0 until clipsArr.length()) {
                    val obj = clipsArr.getJSONObject(i)
                    val closeArr = obj.optJSONArray("closeOffsets")
                    val closeOffsets = if (closeArr != null) {
                        IntArray(closeArr.length()) { j -> closeArr.getInt(j) }
                    } else {
                        IntArray(0)
                    }
                    add(
                        CrispClipMeta(
                            id = obj.getInt("id"),
                            startTile = obj.getInt("startTile"),
                            length = obj.getInt("length"),
                            bucket = SpeechClipBucket.fromWire(obj.optString("bucket", "medium")),
                            peak = obj.optDouble("peak", 0.5).toFloat(),
                            closeOffsets = closeOffsets,
                        ),
                    )
                }
            }
        } else {
            emptyList()
        }

        return AvatarBundleManifest(
            version = root.optInt("version", 1),
            mode = AvatarBundleMode.fromWire(root.optString("mode", "crisp-clips")),
            avatarId = root.optString("avatarId", "elena"),
            width = width,
            height = height,
            fps = root.optDouble("fps", 25.0).toFloat().coerceAtLeast(1f),
            numFrames = root.optInt("numFrames", coords.size.coerceAtLeast(1)),
            gridCols = gridCols,
            gridRows = gridRows,
            numTiles = root.optInt("numTiles", gridCols * gridRows),
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            maskChannels = root.optInt("maskChannels", 1),
            baseRect = baseRect,
            coords = coords,
            faceRig = faceRig,
            clips = clips,
        )
    }

    private fun parseFloat4(arr: JSONArray): FloatArray = floatArrayOf(
        arr.getDouble(0).toFloat(),
        arr.getDouble(1).toFloat(),
        arr.getDouble(2).toFloat(),
        arr.getDouble(3).toFloat(),
    )
}
