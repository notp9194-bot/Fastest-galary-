package com.fastgallery.app

import android.app.Application
import android.app.ActivityManager
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.ui.ThumbData
import com.fastgallery.app.ui.ThumbFetcher
import com.fastgallery.app.ui.ThumbKeyer

/** A shared ImageLoader with cache budgets scaled for low-memory devices. */
class GalleryApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Cold start: prefs file aur ImageLoader background me warm karo, main thread block na ho.
        Thread {
            GalleryPreferences.theme(this)
            Coil.imageLoader(this)
        }.apply { name = "gallery-warmup"; priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val lowRam = activityManager.isLowRamDevice
        val memoryCachePercent = if (lowRam) 0.10 else 0.20
        val diskCacheBytes = if (lowRam) 96L else 192L

        return ImageLoader.Builder(this)
            .components {
                add(ThumbKeyer(), ThumbData::class.java)
                add(ThumbFetcher.Factory(), ThumbData::class.java)
                add(VideoFrameDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(memoryCachePercent).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbs"))
                    .maxSizeBytes(diskCacheBytes * 1024L * 1024L)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
