package com.fastgallery.app.data

import android.util.Log
import android.app.WallpaperManager
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.fastgallery.app.R
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Crop area as fractions (0..1) of the already-rotated image. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class ImageEdit(
    val rotationDegrees: Float = 0f,
    val crop: CropRect? = null,
    val filter: String = "Original",
)

object MediaOperations {
    private const val MAX_EDIT_PIXELS_NORMAL = 8_000_000L
    private const val MAX_EDIT_PIXELS_LOW_RAM = 4_000_000L

    fun rename(context: Context, item: MediaItem, name: String): Int {
        val entered = name.trim().takeIf { it.isNotEmpty() } ?: return 0
        val extension = item.name.substringAfterLast('.', "")
        val safeName = if (extension.isNotBlank() && '.' !in entered) "$entered.$extension" else entered
        return context.contentResolver.update(item.uri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
        }, null, null)
    }

    fun permanentlyDelete(context: Context, item: MediaItem): Int =
        context.contentResolver.delete(item.uri, null, null)

    fun permanentlyDeleteUri(context: Context, uri: Uri): Int =
        context.contentResolver.delete(uri, null, null)

    fun copyToAlbum(context: Context, item: MediaItem, album: String): Uri? {
        val folder = album.trim().replace(Regex("[/\\\\]+"), "_").ifBlank { "FastGallery" }
        val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val extension = item.name.substringAfterLast('.', "")
        val baseName = item.name.substringBeforeLast('.', item.name)
        if (Build.VERSION.SDK_INT < 29) {
            val parent = Environment.getExternalStoragePublicDirectory(
                if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
            )
            val directory = File(parent, folder)
            if (!directory.exists() && !directory.mkdirs()) error("Could not create destination folder")
            val suffix = if (extension.isBlank()) "" else ".$extension"
            var destinationFile = File(directory, "$baseName (copy)$suffix")
            var copyNumber = 2
            while (destinationFile.exists()) {
                destinationFile = File(directory, "$baseName (copy $copyNumber)$suffix")
                copyNumber++
            }
            val source = context.contentResolver.openInputStream(item.uri) ?: error("Could not open source media")
            source.use { input -> destinationFile.outputStream().use { output -> input.copyTo(output) } }
            MediaScannerConnection.scanFile(context, arrayOf(destinationFile.absolutePath), arrayOf(item.mime), null)
            return Uri.fromFile(destinationFile)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName (copy)${if (extension.isBlank()) "" else ".$extension"}")
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (item.isVideo) "Movies/$folder/" else "Pictures/$folder/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val destination = resolver.insert(collection, values) ?: return null
        try {
            val source = resolver.openInputStream(item.uri) ?: error("Could not open source media")
            source.use { input ->
                val target = resolver.openOutputStream(destination, "w") ?: error("Could not create destination media")
                target.use { output -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(destination, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            }
            return destination
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }

    /** Saves a non-destructive edited copy. Original is never overwritten. */
    fun saveEditedCopy(context: Context, item: MediaItem, edit: ImageEdit): Uri? {
        require(!item.isVideo) { "Video editing is not supported by this tool" }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val maxPixels = if (activityManager?.isLowRamDevice == true) {
            MAX_EDIT_PIXELS_LOW_RAM
        } else {
            MAX_EDIT_PIXELS_NORMAL
        }
        val sourceBitmap = decodeBoundedBitmap(context, item.uri, maxPixels) ?: return null
        var bitmap = sourceBitmap
        var outputUri: Uri? = null
        try {
            val (exifRotation, exifFlipped) = readExifTransform(context, item.uri)
            val userRotation = ((edit.rotationDegrees % 360f) + 360f) % 360f
            val needsTransform = exifRotation != 0 || exifFlipped || userRotation != 0f
            if (needsTransform) {
                val matrix = Matrix().apply {
                    if (exifRotation != 0) postRotate(exifRotation.toFloat())
                    if (exifFlipped) postScale(-1f, 1f)
                    if (userRotation != 0f) postRotate(userRotation)
                }
                val oriented = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
                )
                if (oriented !== bitmap) {
                    bitmap.recycle()
                    bitmap = oriented
                }
            }

            edit.crop?.let { c ->
                val w = bitmap.width
                val h = bitmap.height
                val x = (c.left.coerceIn(0f, 1f) * w).roundToInt().coerceIn(0, w - 1)
                val y = (c.top.coerceIn(0f, 1f) * h).roundToInt().coerceIn(0, h - 1)
                val cw = ((c.right - c.left).coerceIn(0f, 1f) * w).roundToInt().coerceIn(1, w - x)
                val ch = ((c.bottom - c.top).coerceIn(0f, 1f) * h).roundToInt().coerceIn(1, h - y)
                if (x != 0 || y != 0 || cw != w || ch != h) {
                    val cropped = Bitmap.createBitmap(bitmap, x, y, cw, ch)
                    if (cropped !== bitmap) {
                        bitmap.recycle()
                        bitmap = cropped
                    }
                }
            }

            if (edit.filter != "Original") {
                val filtered = applyFilter(bitmap, edit.filter)
                if (filtered !== bitmap) {
                    bitmap.recycle()
                    bitmap = filtered
                }
            }

            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${item.name.substringBeforeLast('.', item.name)}_edited.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FastGallery/Edited/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val createdUri = resolver.insert(collection, values) ?: return null
            outputUri = createdUri
            val outputStream = resolver.openOutputStream(createdUri, "w")
                ?: error("Could not create edited image")
            outputStream.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                    "Could not encode edited image"
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(
                    createdUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return createdUri
        } catch (error: Throwable) {
            outputUri?.let { context.contentResolver.delete(it, null, null) }
            throw error
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decodeBoundedBitmap(context: Context, uri: Uri, maxPixels: Long): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = resolver.openInputStream(uri) ?: return null
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            (bounds.outWidth.toLong() / sampleSize) *
            (bounds.outHeight.toLong() / sampleSize) > maxPixels * 2L
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val decodedPixels = decoded.width.toLong() * decoded.height.toLong()
        if (decodedPixels <= maxPixels) return decoded

        val scale = sqrt(maxPixels.toDouble() / decodedPixels)
        return try {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } catch (error: Throwable) {
            if (!decoded.isRecycled) decoded.recycle()
            throw error
        }
    }

    private fun readExifTransform(context: Context, uri: Uri): Pair<Int, Boolean> =
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.rotationDegrees to exif.isFlipped
            } ?: (0 to false)
        } catch (error: Exception) {
            Log.w("MediaOperations", "EXIF transform read failed: $uri", error)
            0 to false
        }

    /** 4x5 colour matrix for a filter id, or null for "Original". Shared by the edit preview and the saved copy. */
    fun filterMatrix(filter: String): FloatArray? = when (filter) {
        "Mono" -> ColorMatrix().apply { setSaturation(0f) }.array
        "Warm" -> floatArrayOf(
            1.12f, 0f, 0f, 0f, 12f, 0f, 1.02f, 0f, 0f, 3f,
            0f, 0f, 0.88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
        )
        "Cool" -> floatArrayOf(
            0.92f, 0f, 0f, 0f, 0f, 0f, 1.02f, 0f, 0f, 0f,
            0f, 0f, 1.12f, 0f, 12f, 0f, 0f, 0f, 1f, 0f,
        )
        else -> null
    }

    /** Small, EXIF-oriented bitmap for the edit preview (about 1.5 MP). */
    fun loadEditPreview(context: Context, uri: Uri): Bitmap? {
        val decoded = decodeBoundedBitmap(context, uri, 1_500_000L) ?: return null
        val (exifRotation, exifFlipped) = readExifTransform(context, uri)
        if (exifRotation == 0 && !exifFlipped) return decoded
        val matrix = Matrix().apply {
            if (exifRotation != 0) postRotate(exifRotation.toFloat())
            if (exifFlipped) postScale(-1f, 1f)
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun applyFilter(source: Bitmap, filter: String): Bitmap {
        val matrix = filterMatrix(filter)?.let { ColorMatrix(it) } ?: ColorMatrix()
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            })
        }
    }

    fun exifDetails(context: Context, item: MediaItem): List<Pair<String, String>> {
        val details = mutableListOf(
            context.getString(R.string.exif_name) to item.name,
            context.getString(R.string.exif_type) to item.mime,
            context.getString(R.string.exif_album) to item.bucketName,
            context.getString(R.string.exif_size) to formatBytes(item.sizeBytes),
            context.getString(R.string.exif_dimensions) to
                if (item.width > 0 && item.height > 0) "${item.width} × ${item.height}" else context.getString(R.string.unknown),
            context.getString(R.string.exif_date_added) to
                if (item.dateAdded > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.dateAdded * 1000)) else "",
            context.getString(R.string.exif_duration) to if (item.isVideo) formatMediaDuration(item.durationMs) else "",
        )
        if (!item.isVideo) {
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val exif = ExifInterface(input)
                    listOf(
                        ExifInterface.TAG_DATETIME_ORIGINAL to context.getString(R.string.exif_captured),
                        ExifInterface.TAG_MAKE to context.getString(R.string.exif_camera_make),
                        ExifInterface.TAG_MODEL to context.getString(R.string.exif_camera_model),
                        ExifInterface.TAG_F_NUMBER to context.getString(R.string.exif_aperture),
                        ExifInterface.TAG_EXPOSURE_TIME to context.getString(R.string.exif_exposure),
                        ExifInterface.TAG_ISO_SPEED to context.getString(R.string.exif_iso),
                        ExifInterface.TAG_FOCAL_LENGTH to context.getString(R.string.exif_focal_length),
                        ExifInterface.TAG_LENS_MODEL to context.getString(R.string.exif_lens),
                        ExifInterface.TAG_GPS_LATITUDE to context.getString(R.string.exif_gps_latitude),
                        ExifInterface.TAG_GPS_LONGITUDE to context.getString(R.string.exif_gps_longitude),
                    ).forEach { (tag, label) -> exif.getAttribute(tag)?.takeIf(String::isNotBlank)?.let { details += label to it } }
                }
            } catch (error: Exception) {
                Log.w("MediaOperations", "EXIF details read failed", error)
            }
        }
        return details.filter { it.second.isNotBlank() }
    }

    fun setWallpaper(context: Context, item: MediaItem) {
        context.contentResolver.openInputStream(item.uri).use { input ->
            requireNotNull(input) { "Could not open media" }
            WallpaperManager.getInstance(context).setStream(input)
        }
    }

    private fun formatMediaDuration(ms: Long): String = formatDuration(ms)
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000L
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}