package com.fastgallery.app.data

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
import java.io.File
import kotlin.math.sqrt

data class ImageEdit(
    val rotationDegrees: Float = 0f,
    val cropRatio: Float? = null,
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

            edit.cropRatio?.let { ratio ->
                if (ratio > 0f && bitmap.width > 0 && bitmap.height > 0) {
                    val currentRatio = bitmap.width.toFloat() / bitmap.height
                    val width = if (currentRatio > ratio) (bitmap.height * ratio).toInt() else bitmap.width
                    val height = if (currentRatio > ratio) bitmap.height else (bitmap.width / ratio).toInt()
                    val x = (bitmap.width - width) / 2
                    val y = (bitmap.height - height) / 2
                    val cropped = Bitmap.createBitmap(
                        bitmap,
                        x,
                        y,
                        width.coerceIn(1, bitmap.width),
                        height.coerceIn(1, bitmap.height),
                    )
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
        } catch (_: Exception) {
            0 to false
        }

    private fun applyFilter(source: Bitmap, filter: String): Bitmap {
        val matrix = when (filter) {
            "Mono" -> ColorMatrix().apply { setSaturation(0f) }
            "Warm" -> ColorMatrix(floatArrayOf(
                1.12f, 0f, 0f, 0f, 12f, 0f, 1.02f, 0f, 0f, 3f,
                0f, 0f, 0.88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
            ))
            "Cool" -> ColorMatrix(floatArrayOf(
                0.92f, 0f, 0f, 0f, 0f, 0f, 1.02f, 0f, 0f, 0f,
                0f, 0f, 1.12f, 0f, 12f, 0f, 0f, 0f, 1f, 0f,
            ))
            else -> ColorMatrix()
        }
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            })
        }
    }

    fun exifDetails(context: Context, item: MediaItem): List<Pair<String, String>> {
        val details = mutableListOf(
            "Name" to item.name,
            "Type" to item.mime,
            "Album" to item.bucketName,
            "Size" to formatBytes(item.sizeBytes),
            "Dimensions" to if (item.width > 0 && item.height > 0) "${item.width} × ${item.height}" else "Unknown",
            "Date added" to java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.dateAdded * 1000)),
            "Duration" to if (item.isVideo) formatMediaDuration(item.durationMs) else "",
        )
        if (!item.isVideo) {
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val exif = ExifInterface(input)
                    listOf(
                        ExifInterface.TAG_DATETIME_ORIGINAL to "Captured",
                        ExifInterface.TAG_MAKE to "Camera make",
                        ExifInterface.TAG_MODEL to "Camera model",
                        ExifInterface.TAG_F_NUMBER to "Aperture",
                        ExifInterface.TAG_EXPOSURE_TIME to "Exposure",
                        ExifInterface.TAG_ISO_SPEED to "ISO",
                        ExifInterface.TAG_FOCAL_LENGTH to "Focal length",
                        ExifInterface.TAG_LENS_MODEL to "Lens",
                        ExifInterface.TAG_GPS_LATITUDE to "GPS latitude",
                        ExifInterface.TAG_GPS_LONGITUDE to "GPS longitude",
                    ).forEach { (tag, label) -> exif.getAttribute(tag)?.takeIf(String::isNotBlank)?.let { details += label to it } }
                }
            } catch (_: Exception) { }
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