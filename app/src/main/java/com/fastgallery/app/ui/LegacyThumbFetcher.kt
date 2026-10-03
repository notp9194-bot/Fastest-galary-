package com.fastgallery.app.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import coil.ImageLoader
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.fastgallery.app.data.MediaOperations
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * API 26-28 ke liye thumbnail fetcher (API 29+ par `ThumbFetcher` / `loadThumbnail` chalta hai).
 *
 * Pehle yahan Coil original file (aur video ka frame) har baar dobara decode karta tha, kyunki Coil ka disk cache sirf
 * network fetcher ke liye hai. Ab: pehle apna disk cache (chhota WebP/JPEG thumbnail) dekho, na mile to ek baar decode
 * karke wahan likh do. Agli baar (aur memory cache evict hone ke baad bhi) sasta small-file decode.
 *
 * Limit: cache key = uri + size. Doosre app ne wahi file jagah par badal di (naya MediaStore id nahi) to purana thumbnail
 * tab tak dikhega jab tak LRU use hata na de (is app ke edits nayi file banate hain, to unpar asar nahi).
 */
internal const val MAX_PARALLEL_LEGACY_THUMB_LOADS = 3

private val legacyPermits = Semaphore(MAX_PARALLEL_LEGACY_THUMB_LOADS)
private const val LEGACY_THUMB_QUALITY = 85

/** Lambi (panorama jaisi) image me thumbnail ki lambi side size ki itni guna se zyada nahi. */
private const val LEGACY_THUMB_MAX_LONG_SIDE_FACTOR = 3

/**
 * Decode ka inSampleSize (power of 2): sabse bada jisme chhoti side abhi bhi >= size rahe. Chhoti side size se kam na ho,
 * taaki grid (ContentScale.Crop) me thumbnail blurry na ho; baaki exact scale `legacyThumbTargetSize` se.
 */
internal fun legacyThumbSampleSize(width: Int, height: Int, size: Int): Int {
    if (width <= 0 || height <= 0 || size <= 0) return 1
    val shortSide = min(width, height)
    var sample = 1
    while (shortSide / (sample * 2) >= size) sample *= 2
    return sample
}

/**
 * Thumbnail ka final (width, height): chhoti side == size (kabhi upscale nahi), aspect ratio wahi. Bahut lambi image me
 * lambi side `size * LEGACY_THUMB_MAX_LONG_SIDE_FACTOR` se zyada nahi (memory), tab chhoti side size se thodi kam ho sakti hai.
 */
internal fun legacyThumbTargetSize(width: Int, height: Int, size: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || size <= 0) return width to height
    val shortSide = min(width, height)
    if (shortSide <= size) return width to height
    var scale = size.toFloat() / shortSide
    val longSide = max(width, height)
    if (longSide * scale > size * LEGACY_THUMB_MAX_LONG_SIDE_FACTOR) {
        scale = size * LEGACY_THUMB_MAX_LONG_SIDE_FACTOR.toFloat() / longSide
    }
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/** Legacy thumbnails ka disk cache (sirf API < 29 par banta hai). `cache/` me hai, system saaf kar sakta hai. */
object LegacyThumbCache {
    @Volatile
    private var cache: DiskCache? = null

    fun get(context: Context): DiskCache = cache ?: synchronized(this) {
        cache ?: build(context.applicationContext).also { cache = it }
    }

    private fun build(context: Context): DiskCache {
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        val maxMb = if (lowRam) 96L else 192L
        return DiskCache.Builder()
            .directory(context.cacheDir.resolve("thumbs_v2"))
            .maxSizeBytes(maxMb * 1024L * 1024L)
            .build()
    }
}

class LegacyThumbFetcher(
    private val data: ThumbData,
    private val options: Options,
    private val diskCache: DiskCache,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val key = "legacy:${data.uri}:${data.size}"
        readCached(key)?.let { return result(it) }
        // Bhaari decode sirf kuch ek saath: purane phones par bade originals ek saath decode karna OOM/jank deta hai.
        val bitmap = legacyPermits.withPermit {
            // Isi key ka doosra request (prefetch + cell) pehle bhar chuka ho sakta hai.
            readCached(key) ?: run {
                currentCoroutineContext().ensureActive()
                val decoded = decodeLegacyThumb(options.context, data.uri, data.size)
                    ?: throw IOException("Could not decode thumbnail")
                // Cancel ho chuka ho (cell scroll se nikal gaya) to adhura kaam disk par mat likho.
                currentCoroutineContext().ensureActive()
                writeCached(key, decoded)
                decoded
            }
        }
        return result(bitmap)
    }

    private fun result(bitmap: Bitmap) = DrawableResult(
        drawable = BitmapDrawable(options.context.resources, bitmap),
        isSampled = true,
        dataSource = DataSource.DISK,
    )

    private fun readCached(key: String): Bitmap? {
        val snapshot = diskCache.openSnapshot(key) ?: return null
        val bitmap = snapshot.use { BitmapFactory.decodeFile(it.data.toString()) }
        // Kharab/adhuri file: hata do, dobara bana lenge.
        if (bitmap == null) diskCache.remove(key)
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun writeCached(key: String, bitmap: Bitmap) {
        val editor = diskCache.openEditor(key) ?: return
        try {
            // Alpha wali (PNG/GIF/WebP) me WebP, baaki me JPEG: transparent hissa JPEG me kaala ho jaata.
            val format = when {
                !bitmap.hasAlpha() -> Bitmap.CompressFormat.JPEG
                Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.WEBP
            }
            editor.data.toFile().outputStream().buffered().use { out ->
                if (!bitmap.compress(format, LEGACY_THUMB_QUALITY, out)) throw IOException("compress failed")
            }
            editor.commit()
        } catch (error: Exception) {
            runCatching { editor.abort() }
        }
    }

    class Factory(private val diskCache: DiskCache) : Fetcher.Factory<ThumbData> {
        override fun create(data: ThumbData, options: Options, imageLoader: ImageLoader): Fetcher =
            LegacyThumbFetcher(data, options, diskCache)
    }
}

private fun isVideoUri(context: Context, uri: Uri): Boolean =
    if (uri.authority == "media") {
        // MediaStore uri: .../external/video/media/<id> vs .../external/images/media/<id>.
        uri.pathSegments.contains("video")
    } else {
        context.contentResolver.getType(uri)?.startsWith("video/") == true
    }

internal fun decodeLegacyThumb(context: Context, uri: Uri, size: Int): Bitmap? =
    if (isVideoUri(context, uri)) decodeLegacyVideo(context, uri, size) else decodeLegacyImage(context, uri, size)

private fun decodeLegacyImage(context: Context, uri: Uri, size: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = legacyThumbSampleSize(bounds.outWidth, bounds.outHeight, size)
    }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val scaled = scaleToThumb(decoded, size)
    // EXIF rotation / flip pehle se lagao (API 29+ ke loadThumbnail jaisa: thumbnail seedha upright).
    val (rotation, flipped) = MediaOperations.readExifTransform(context, uri)
    if (rotation == 0 && !flipped) return scaled
    val matrix = Matrix().apply {
        if (rotation != 0) postRotate(rotation.toFloat())
        if (flipped) postScale(-1f, 1f)
    }
    val oriented = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
    if (oriented !== scaled) scaled.recycle()
    return oriented
}

private fun decodeLegacyVideo(context: Context, uri: Uri, size: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        // Pehla frame (container ka rotation pehle se laga hota hai).
        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        return scaleToThumb(frame, size)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun scaleToThumb(bitmap: Bitmap, size: Int): Bitmap {
    val (w, h) = legacyThumbTargetSize(bitmap.width, bitmap.height, size)
    if (w == bitmap.width && h == bitmap.height) return bitmap
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}
