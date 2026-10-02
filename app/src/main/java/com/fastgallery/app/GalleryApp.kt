package com.fastgallery.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Ek hi tuned ImageLoader: bada memory cache + disk cache + video frame thumbnails. */
class GalleryApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.30).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbs"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
}
