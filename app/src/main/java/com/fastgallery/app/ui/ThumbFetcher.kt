package com.fastgallery.app.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Thumbnail request: MediaStore uri + target square size in px. */
data class ThumbData(val uri: Uri, val size: Int)

class ThumbKeyer : Keyer<ThumbData> {
    override fun key(data: ThumbData, options: Options): String = "thumb:${data.uri}:${data.size}"
}

/**
 * Ek saath kitne `loadThumbnail` Binder calls chal sakte hain. Coil ka default fetcher dispatcher (Dispatchers.IO)
 * 64 threads tak jaane deta hai: fast scroll me itne calls MediaProvider ke Binder pool (~16 threads) ko bhar dete
 * hain aur app ki apni queries (load/refresh) bhi unke peeche atak jaati hain. 8 par pool me jagah bachti hai.
 * Ye number device par naapke tune karo (6/8/12); fayda na dikhe to bada kar do (jaise 64) = pehle jaisa.
 */
internal const val MAX_PARALLEL_THUMB_LOADS = 8

private val thumbPermits = Semaphore(MAX_PARALLEL_THUMB_LOADS)

/**
 * Android ka apna pre-generated/cached MediaStore thumbnail use karta hai (API 29+).
 * Full-resolution JPEG/video decode skip hota hai, isliye first open pe grid turant bharta hai.
 *
 * - Parallel calls `MAX_PARALLEL_THUMB_LOADS` tak limited (permit ka intezaar cancel-safe hai).
 * - Cell scroll se bahar gaya (request cancel hui) to chalta hua `loadThumbnail` bhi `CancellationSignal` se
 *   rok diya jata hai: Binder thread jaldi khali hota hai, scroll me ab bhi dikhne wale thumbnails ko jagah milti hai.
 */
class ThumbFetcher(
    private val data: ThumbData,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        if (Build.VERSION.SDK_INT < 29) throw UnsupportedOperationException("API 29+ required")
        val bitmap = thumbPermits.withPermit { loadCancellable() }
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun loadCancellable(): Bitmap {
        val signal = CancellationSignal()
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause -> if (cause != null) signal.cancel() }
        try {
            return options.context.contentResolver.loadThumbnail(data.uri, Size(data.size, data.size), signal)
        } catch (e: OperationCanceledException) {
            // Hamne hi cancel kiya: Coil ko error result nahi, cancellation chahiye.
            throw CancellationException("thumbnail load cancelled").apply { initCause(e) }
        } finally {
            handle?.dispose()
        }
    }

    class Factory : Fetcher.Factory<ThumbData> {
        override fun create(data: ThumbData, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options)
    }
}
