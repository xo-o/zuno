package com.zuno.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** A local copy of a picked file, ready to upload (images are already downscaled and EXIF-stripped). */
data class PreparedFile(
    val file: File,
    val name: String,
    val mimeType: String,
    val isImage: Boolean,
    val width: Int? = null,
    val height: Int? = null,
)

object AttachmentFiles {
    const val MAX_PER_MESSAGE = 4
    private const val MB = 1024L * 1024L
    private const val MAX_IMAGE_EDGE = 2048
    private const val JPEG_QUALITY = 85

    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** Files picker filter; must stay in sync with the backend's accepted types. */
    val documentMimeTypes = arrayOf("application/pdf", DOCX, "text/plain")

    private val documentLimits = mapOf("application/pdf" to 20 * MB, DOCX to 20 * MB, "text/plain" to 2 * MB)

    private fun dir(context: Context) = File(context.cacheDir, "attachments").apply { mkdirs() }

    /** A FileProvider target for the system camera to write into. */
    fun newCameraUri(context: Context): Uri {
        val file = File(dir(context), "camera-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Copies (and for images, compresses) [uri] into the cache. Throws [ApiException] with a user-facing reason. */
    suspend fun prepare(context: Context, uri: Uri): PreparedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryName(context, uri) ?: uri.lastPathSegment ?: "file"
        val mimeType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

        if (mimeType.startsWith("image/")) {
            prepareImage(context, uri, displayName)
        } else {
            val limit = documentLimits[mimeType]
                ?: throw ApiException("This file type isn't supported. Use a photo, PDF, DOCX or TXT file.")
            val target = File(dir(context), "${UUID.randomUUID()}-${displayName.safeFileName()}")
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw ApiException("Couldn't open this file.")
            if (target.length() > limit) {
                target.delete()
                throw ApiException("This file is too large. The limit is ${limit / MB} MB.")
            }
            if (target.length() == 0L) {
                target.delete()
                throw ApiException("This file is empty.")
            }
            PreparedFile(target, displayName, mimeType, isImage = false)
        }
    }

    private fun prepareImage(context: Context, uri: Uri, displayName: String): PreparedFile {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ApiException("Couldn't read this photo.")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw ApiException("Couldn't read this photo.")

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, MAX_IMAGE_EDGE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val bitmap = if (matrix.isIdentity) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }

        // Re-encoding drops EXIF (location, device) and normalises HEIC/PNG/WebP to JPEG.
        val baseName = displayName.substringBeforeLast('.').ifBlank { "photo" }
        val target = File(dir(context), "${UUID.randomUUID()}.jpg")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        val result = PreparedFile(target, "$baseName.jpg", "image/jpeg", isImage = true, bitmap.width, bitmap.height)
        bitmap.recycle()
        return result
    }

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun String.safeFileName() = replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)

    /** "PDF · 2.4 MB" */
    fun describe(mimeType: String, sizeBytes: Long): String {
        val type = when (mimeType) {
            "application/pdf" -> "PDF"
            DOCX -> "DOCX"
            "text/plain" -> "TXT"
            else -> if (mimeType.startsWith("image/")) "Photo" else "File"
        }
        val size = when {
            sizeBytes >= MB -> "${((sizeBytes / MB.toDouble()) * 10).roundToInt() / 10.0} MB"
            else -> "${max(1, (sizeBytes / 1024.0).roundToInt())} KB"
        }
        return "$type · $size"
    }
}

/** Uploads a file while reporting progress, so the composer can show "Uploading · 64%". */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = contentType
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1)
        var written = 0L
        file.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 64 * 1024L)
                if (read == -1L) break
                written += read
                sink.flush()
                onProgress(written.toFloat() / total)
            }
        }
    }
}
