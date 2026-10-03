package com.fastgallery.app

import android.app.Application
import android.app.ActivityManager
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.decode.GifDecoder
import coil.memory.MemoryCache
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.ui.ThumbData
import com.fastgallery.app.ui.ThumbFetcher
import com.fastgallery.app.ui.ThumbKeyer

/**
 * A shared ImageLoader with a memory cache scaled for low-memory devices.
 *
 * Disk cache jaan-boojh ke band hai (`diskCache(null)`): Coil ka disk cache sirf network fetcher
 * (HttpUriFetcher) bharta hai. Yahan ke thumbnails `ThumbFetcher` (MediaStore.loadThumbnail, jiska apna
 * system cache hai) ya local content:// decode se aate hain, jo Coil ke disk cache ko kabhi nahi chhute,
 * to purana 192 MB `cache/thumbs` hamesha khali tha. Null na dene par Coil 2.7 khud default disk cache banata.
 */
class GalleryApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Cold start: prefs file aur ImageLoader background me warm karo, main thread block na ho.
        Thread {
            GalleryPreferences.theme(this)
            Coil.imageLoader(this)
            // Purane versions ka khali disk-cache folder hata do (bachi hui journal files wagairah).
            cacheDir.resolve("thumbs").takeIf { it.exists() }?.deleteRecursively()
        }.apply { name = "gallery-warmup"; priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val lowRam = activityManager.isLowRamDevice
        val memoryCachePercent = if (lowRam) 0.10 else 0.20

        return ImageLoader.Builder(this)
            .components {
                add(ThumbKeyer(), ThumbData::class.java)
                add(ThumbFetcher.Factory(), ThumbData::class.java)
                add(VideoFrameDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(memoryCachePercent).build() }
            .diskCache(null)
            .allowRgb565(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
