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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Crop area as fractions (0..1) of the already-rotated image. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Editor ka poora result. Order (preview aur saved copy dono me same):
 * EXIF orientation -> rotate (90s) -> flip -> straighten -> crop -> colour (brightness/contrast/saturation) -> filter.
 * [flipHorizontal]/[flipVertical] rotate ke BAAD ki (dikhne wali) image par lagte hain.
 * [brightness], [contrast], [saturation]: -100..100 (0 = koi badlav nahi). [straightenDegrees]: -45..45.
 */
data class ImageEdit(
    val rotationDegrees: Float = 0f,
    val crop: CropRect? = null,
    val filter: String = "Original",
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val straightenDegrees: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
)

object MediaOperations {
    private const val MAX_EDIT_PIXELS_NORMAL = 8_000_000L
    private const val MAX_EDIT_PIXELS_LOW_RAM = 4_000_000L
    private const val MIN_STRAIGHTEN_DEGREES = 0.05f
    /** Brightness slider ke +/-100 ka matlab colour offset +/-80 (0..255 scale). */
    private const val BRIGHTNESS_OFFSET_RANGE = 80f

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

    /** Copy beech me user ne cancel kiya: aadhi bani file hata di jaati hai. */
    class CopyCancelledException : RuntimeException("Copy cancelled")

    /** input -> output, har chunk ke baad progress (0..1; size pata na ho to -1f) aur cancel check. */
    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: ((Float) -> Unit)?,
        isCancelled: (() -> Boolean)?,
    ) {
        val buffer = ByteArray(128 * 1024)
        var copied = 0L
        var lastPercent = -1
        while (true) {
            if (isCancelled?.invoke() == true) throw CopyCancelledException()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            if (onProgress != null) {
                if (totalBytes > 0L) {
                    val percent = (copied * 100 / totalBytes).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) { lastPercent = percent; onProgress(percent / 100f) }
                } else if (lastPercent != -1) {
                    lastPercent = -1; onProgress(-1f)
                }
            }
        }
    }

    fun copyToAlbum(
        context: Context,
        item: MediaItem,
        album: String,
        destRelativePath: String? = null,
        onProgress: ((Float) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
    ): Uri? {
        val folder = album.trim().replace(Regex("[/\\\\]+"), "_").ifBlank { "FastGallery" }
        // Existing album chuna ho to uska asli folder (jaise DCIM/Camera); warna naam se Pictures|Movies/<naam>.
        val existingPath = destRelativePath?.let { safeRelativePath(it, item.isVideo) }
        val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val extension = item.name.substringAfterLast('.', "")
        val baseName = item.name.substringBeforeLast('.', item.name)
        if (Build.VERSION.SDK_INT < 29) {
            val parent = Environment.getExternalStoragePublicDirectory(
                if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
            )
            val directory = if (existingPath != null) File(Environment.getExternalStorageDirectory(), existingPath) else File(parent, folder)
            if (!directory.exists() && !directory.mkdirs()) error("Could not create destination folder")
            val suffix = if (extension.isBlank()) "" else ".$extension"
            var destinationFile = File(directory, "$baseName (copy)$suffix")
            var copyNumber = 2
            while (destinationFile.exists()) {
                destinationFile = File(directory, "$baseName (copy $copyNumber)$suffix")
                copyNumber++
            }
            val source = context.contentResolver.openInputStream(item.uri) ?: error("Could not open source media")
            try {
                source.use { input ->
                    destinationFile.outputStream().use { output ->
                        copyWithProgress(input, output, item.sizeBytes, onProgress, isCancelled)
                    }
                }
            } catch (error: Exception) {
                destinationFile.delete()
                throw error
            }
            MediaScannerConnection.scanFile(context, arrayOf(destinationFile.absolutePath), arrayOf(item.mime), null)
            return Uri.fromFile(destinationFile)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName (copy)${if (extension.isBlank()) "" else ".$extension"}")
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, existingPath ?: if (item.isVideo) "Movies/$folder/" else "Pictures/$folder/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val destination = resolver.insert(collection, values) ?: return null
        try {
            val source = resolver.openInputStream(item.uri) ?: error("Could not open source media")
            source.use { input ->
                val target = resolver.openOutputStream(destination, "w") ?: error("Could not create destination media")
                target.use { output -> copyWithProgress(input, output, item.sizeBytes, onProgress, isCancelled) }
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

    /**
     * MediaStore RELATIVE_PATH sirf kuch top-level folders allow karta hai (images: DCIM/Pictures, videos: DCIM/Movies).
     * Baaki ya galat path (".." wagairah) par null: caller naam-based folder par fall back karta hai.
     */
    private fun safeRelativePath(path: String, isVideo: Boolean): String? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.any { it == ".." || it == "." }) return null
        val allowed = if (isVideo) setOf("DCIM", "Movies") else setOf("DCIM", "Pictures")
        if (parts.first() !in allowed) return null
        return parts.joinToString("/", postfix = "/")
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
            val userFlip = edit.flipHorizontal || edit.flipVertical
            val needsTransform = exifRotation != 0 || exifFlipped || userRotation != 0f || userFlip
            if (needsTransform) {
                val matrix = Matrix().apply {
                    if (exifRotation != 0) postRotate(exifRotation.toFloat())
                    if (exifFlipped) postScale(-1f, 1f)
                    if (userRotation != 0f) postRotate(userRotation)
                    // Flip rotate ke baad: editor me jo dikhta hai wahi save hota hai.
                    if (userFlip) postScale(if (edit.flipHorizontal) -1f else 1f, if (edit.flipVertical) -1f else 1f)
                }
                val oriented = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
                )
                if (oriented !== bitmap) {
                    bitmap.recycle()
                    bitmap = oriented
                }
            }

            // Straighten crop se pehle: crop fractions straighten ke baad wale (same size) frame ke hain.
            if (abs(edit.straightenDegrees) >= MIN_STRAIGHTEN_DEGREES) {
                val straightened = straightenBitmap(bitmap, edit.straightenDegrees)
                if (straightened !== bitmap) {
                    bitmap.recycle()
                    bitmap = straightened
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

            val colors = colorMatrix(edit.filter, edit.brightness, edit.contrast, edit.saturation)
            if (colors != null) {
                val colored = applyColorMatrix(bitmap, colors)
                if (colored !== bitmap) {
                    bitmap.recycle()
                    bitmap = colored
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

    /**
     * Editor ke filter ids, dikhne ke order me. Pehla "Original" (koi change nahi). UI chips aur tests yahi list use karte hain;
     * naya filter jodna ho to yahan id, [filterMatrix] me matrix, aur strings.xml (en + hi) me label add karo.
     */
    val FILTER_IDS: List<String> = listOf(
        "Original", "Mono", "Warm", "Cool",
        "Vivid", "Dramatic", "Fade", "Vintage", "Sepia", "Noir", "Sunset", "Forest",
    )

    /** Contrast ka 4x5 matrix: 128 (middle grey) fixed rehta hai. */
    internal fun contrastMatrix(scale: Float): FloatArray {
        val shift = 128f * (1f - scale)
        return floatArrayOf(
            scale, 0f, 0f, 0f, shift,
            0f, scale, 0f, 0f, shift,
            0f, 0f, scale, 0f, shift,
            0f, 0f, 0f, 1f, 0f,
        )
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
        // Rang gehre + halka contrast.
        "Vivid" -> concatColorMatrices(saturationMatrix(1.4f), contrastMatrix(1.08f))
        // Mazboot contrast, thoda extra saturation.
        "Dramatic" -> concatColorMatrices(saturationMatrix(1.15f), contrastMatrix(1.3f))
        // Faded look: kam saturation, kaale thode uthe hue (matte).
        "Fade" -> concatColorMatrices(
            concatColorMatrices(saturationMatrix(0.8f), contrastMatrix(0.88f)),
            floatArrayOf(
                1f, 0f, 0f, 0f, 8f,
                0f, 1f, 0f, 0f, 8f,
                0f, 0f, 1f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // Purani film: kam saturation, halka warm tint, thoda faded.
        "Vintage" -> concatColorMatrices(
            concatColorMatrices(saturationMatrix(0.7f), contrastMatrix(0.94f)),
            floatArrayOf(
                1.08f, 0f, 0f, 0f, 14f,
                0f, 1.0f, 0f, 0f, 6f,
                0f, 0f, 0.82f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // Classic sepia (standard coefficients).
        "Sepia" -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        // Black & white + tez contrast.
        "Noir" -> concatColorMatrices(saturationMatrix(0f), contrastMatrix(1.35f))
        // Golden-hour: gehra warm, halka gulabi.
        "Sunset" -> concatColorMatrices(
            saturationMatrix(1.2f),
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 10f,
                0f, 0.97f, 0f, 0f, 0f,
                0f, 0f, 0.92f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // Hara-bhara: green zyada, red/blue thoda kam.
        "Forest" -> concatColorMatrices(
            saturationMatrix(1.1f),
            floatArrayOf(
                0.94f, 0f, 0f, 0f, 0f,
                0f, 1.1f, 0f, 0f, 4f,
                0f, 0f, 0.94f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
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

    private fun applyColorMatrix(source: Bitmap, matrix: FloatArray): Bitmap =
        Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
            })
        }

    /** Image ko [degrees] se ghumake wapas usi size ke frame me, itna zoom ke saath ki frame ke kone khali na dikhen. */
    private fun straightenBitmap(source: Bitmap, degrees: Float): Bitmap {
        val w = source.width
        val h = source.height
        val scale = straightenCoverScale(w, h, degrees)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postRotate(degrees, w / 2f, h / 2f)
            postScale(scale, scale, w / 2f, h / 2f)
        }
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    /**
     * Straighten ke baad frame khali na dikhe, isliye image ko itna bada karna padta hai (1.0 = degrees 0).
     * Frame ko -degrees se ghumane par uska bounding box image ke andar aana chahiye:
     * scale = cos + sin * (lambi side / chhoti side). Zara sa margin (0.4%) kinaron ki blending ke liye.
     * Editor preview aur saved copy dono yahi function use karte hain, taaki dono bilkul same dikhen.
     */
    fun straightenCoverScale(width: Int, height: Int, degrees: Float): Float {
        if (width <= 0 || height <= 0 || degrees == 0f) return 1f
        val radians = Math.toRadians(min(abs(degrees), 45f).toDouble())
        val longOverShort = max(width, height).toDouble() / min(width, height)
        return ((cos(radians) + sin(radians) * longOverShort) * 1.004).toFloat()
    }

    /**
     * Brightness/contrast/saturation (-100..100) aur filter ka milkar ek 4x5 colour matrix; kuch bhi set nahi to null.
     * Order: saturation -> contrast -> brightness -> filter. Preview aur saved copy dono yahi use karte hain.
     */
    fun colorMatrix(filter: String, brightness: Float, contrast: Float, saturation: Float): FloatArray? {
        val filterMatrix = filterMatrix(filter)
        val b = brightness.coerceIn(-100f, 100f)
        val c = contrast.coerceIn(-100f, 100f)
        val s = saturation.coerceIn(-100f, 100f)
        if (b == 0f && c == 0f && s == 0f) return filterMatrix
        val contrastScale = 1f + c / 100f
        val contrastShift = 128f * (1f - contrastScale)
        val brightnessShift = b / 100f * BRIGHTNESS_OFFSET_RANGE
        var m = saturationMatrix(1f + s / 100f)
        m = concatColorMatrices(m, floatArrayOf(
            contrastScale, 0f, 0f, 0f, contrastShift,
            0f, contrastScale, 0f, 0f, contrastShift,
            0f, 0f, contrastScale, 0f, contrastShift,
            0f, 0f, 0f, 1f, 0f,
        ))
        m = concatColorMatrices(m, floatArrayOf(
            1f, 0f, 0f, 0f, brightnessShift,
            0f, 1f, 0f, 0f, brightnessShift,
            0f, 0f, 1f, 0f, brightnessShift,
            0f, 0f, 0f, 1f, 0f,
        ))
        if (filterMatrix != null) m = concatColorMatrices(m, filterMatrix)
        return m
    }

    /** Android ke ColorMatrix.setSaturation jaisa matrix (pure math, taaki JVM unit test me chale). */
    internal fun saturationMatrix(saturation: Float): FloatArray {
        val inv = 1f - saturation
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        return floatArrayOf(
            r + saturation, g, b, 0f, 0f,
            r, g + saturation, b, 0f, 0f,
            r, g, b + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Do 4x5 colour matrices: pehle [first] lagta hai, uske baad [then]. */
    internal fun concatColorMatrices(first: FloatArray, then: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += then[row * 5 + k] * first[k * 5 + col]
                if (col == 4) sum += then[row * 5 + 4]
                out[row * 5 + col] = sum
            }
        }
        return out
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