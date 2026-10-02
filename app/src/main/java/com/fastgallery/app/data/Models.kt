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
) {
    /** Images aur videos ke IDs collide ho sakte hain, isliye alag key. */
    val key: Long get() = id * 2 + if (isVideo) 1 else 0
}

data class Album(
    val id: Long,
    val name: String,
    val cover: MediaItem,
    val count: Int,
)

sealed interface GridEntry {
    val key: Any

    data class Header(val label: String) : GridEntry {
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
            out += GridEntry.Header(fmt.format(Date(ms)))
        }
        out += GridEntry.Media(m, i)
    }
    return out
}
