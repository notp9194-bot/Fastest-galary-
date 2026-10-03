package com.fastgallery.app.data

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val dateAdded: Long, // seconds
    val isVideo: Boolean,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val dateTaken: Long = 0L,
    val relativePath: String = "",
    /** true = system (MediaStore) trash me hai, API 30+. */
    val isTrashed: Boolean = false,
    /** System trash se auto-delete ka time (seconds); 0 = unknown. */
    val trashExpiresSec: Long = 0L,
) {
    /** Images aur videos ke IDs collide ho sakte hain; URI is the stable cross-table key. */
    val key: String get() = uri.toString()
}

data class Album(
    val id: Long,
    val name: String,
    val cover: MediaItem,
    val count: Int,
)

sealed interface GridEntry {
    val key: Any

    data class Header(val label: String, val millis: Long = 0L) : GridEntry {
        override val key: Any get() = "h_$label"
    }

    data class Media(val item: MediaItem, val index: Int) : GridEntry {
        override val key: Any get() = item.key
    }
}

fun buildAlbums(items: List<MediaItem>): List<Album> =
    items.groupBy { it.bucketId }
        .map { (id, list) -> Album(id, list.first().bucketName, list.first(), list.size) }
        .sortedByDescending { it.cover.dateAdded }

/** Din ke hisaab se headers; index = diye gaye list ke andar position (viewer ke liye). */
fun buildEntries(items: List<MediaItem>): List<GridEntry> {
    val fmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    val tz = TimeZone.getDefault()
    val out = ArrayList<GridEntry>(items.size + 64)
    var lastDay = Long.MIN_VALUE
    for ((i, m) in items.withIndex()) {
        val ms = m.dateAdded * 1000L
        val day = (ms + tz.getOffset(ms)) / 86_400_000L
        if (day != lastDay) {
            lastDay = day
            out += GridEntry.Header(fmt.format(Date(ms)), ms)
        }
        out += GridEntry.Media(m, i)
    }
    return out
}

enum class GallerySort { DATE_NEWEST, DATE_OLDEST, NAME, SIZE_LARGEST }
enum class AlbumSort { RECENT, NAME, COUNT }

/** Pinned albums hamesha upar (apne beech chune hue sort me), baaki unke neeche. */
fun sortAlbums(albums: List<Album>, sort: AlbumSort, pinned: Set<String>): List<Album> {
    val base = when (sort) {
        AlbumSort.RECENT -> albums.sortedByDescending { it.cover.dateAdded }
        AlbumSort.NAME -> albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        AlbumSort.COUNT -> albums.sortedByDescending { it.count }
    }
    return base.filter { it.id.toString() in pinned } + base.filter { it.id.toString() !in pinned }
}

enum class MediaFilter { ALL, PHOTOS, VIDEOS, GIFS, RAW }

fun MediaItem.matchesFilter(filter: MediaFilter): Boolean = when (filter) {
    MediaFilter.ALL -> true
    MediaFilter.PHOTOS -> !isVideo && !mime.contains("gif", true) && !isRaw()
    MediaFilter.VIDEOS -> isVideo
    MediaFilter.GIFS -> mime.contains("gif", true)
    MediaFilter.RAW -> isRaw()
}

fun MediaItem.isRaw(): Boolean =
    mime.contains("dng", true) || mime.contains("raw", true) ||
        name.substringAfterLast('.', "").lowercase() in setOf("dng", "nef", "cr2", "cr3", "arw", "orf", "rw2", "raf")
