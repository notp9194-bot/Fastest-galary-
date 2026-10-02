package com.fastgallery.app.ui

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options

/** Thumbnail request: MediaStore uri + target square size in px. */
data class ThumbData(val uri: Uri, val size: Int)

class ThumbKeyer : Keyer<ThumbData> {
    override fun key(data: ThumbData, options: Options): String = "thumb:${data.uri}:${data.size}"
}

/**
 * Android ka apna pre-generated/cached MediaStore thumbnail use karta hai (API 29+).
 * Full-resolution JPEG/video decode skip hota hai, isliye first open pe grid turant bharta hai.
 */
class ThumbFetcher(
    private val data: ThumbData,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        if (Build.VERSION.SDK_INT < 29) throw UnsupportedOperationException("API 29+ required")
        val bitmap = options.context.contentResolver
            .loadThumbnail(data.uri, Size(data.size, data.size), null)
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<ThumbData> {
        override fun create(data: ThumbData, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options)
    }
}
