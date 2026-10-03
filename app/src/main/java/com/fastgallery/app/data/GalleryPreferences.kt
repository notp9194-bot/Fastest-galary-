package com.fastgallery.app.data

import android.content.Context

/** Small, device-local gallery state; media files remain in the user's normal storage. */
object GalleryPreferences {
    const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    private const val FILE = "fast_gallery_preferences"
    private const val FAVORITES = "favorites"
    private const val TRASH = "trash"
    private const val HIDDEN_ALBUMS = "hidden_albums"
    private const val LOCKED_ALBUMS = "locked_albums"
    private const val PINNED_ALBUMS = "pinned_albums"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun readSet(context: Context, key: String): Set<String> = prefs(context).getStringSet(key, emptySet())?.toSet() ?: emptySet()

    fun favorites(context: Context) = readSet(context, FAVORITES)
    fun trashed(context: Context): Set<String> = readTrashMap(context).keys.toSet()
    /** Fallback (API < 30) / purane trash flags: key -> trash karne ka time (ms). */
    fun trashTimes(context: Context): Map<String, Long> = readTrashMap(context)
    fun hiddenAlbums(context: Context) = readSet(context, HIDDEN_ALBUMS)
    fun lockedAlbums(context: Context) = readSet(context, LOCKED_ALBUMS)
    fun pinnedAlbums(context: Context) = readSet(context, PINNED_ALBUMS)
    fun isFavorite(context: Context, item: MediaItem) = item.key in favorites(context)
    fun isTrashed(context: Context, item: MediaItem) = item.key in readTrashMap(context)

    fun toggleFavorite(context: Context, item: MediaItem) =
        updateSet(context, FAVORITES, item.key, item.key !in favorites(context))

    fun setTrashed(context: Context, item: MediaItem, value: Boolean) =
        setTrashedKeys(context, listOf(item.key), value)

    fun setTrashedKeys(context: Context, keys: Collection<String>, value: Boolean) {
        if (keys.isEmpty()) return
        val map = readTrashMap(context).toMutableMap()
        val now = System.currentTimeMillis()
        keys.forEach { if (value) map.putIfAbsent(it, now) else map.remove(it) }
        writeTrashMap(context, map)
    }

    fun setAlbumHidden(context: Context, id: Long, value: Boolean) =
        updateSet(context, HIDDEN_ALBUMS, id.toString(), value)

    fun setAlbumLocked(context: Context, id: Long, value: Boolean) =
        updateSet(context, LOCKED_ALBUMS, id.toString(), value)

    fun setAlbumPinned(context: Context, id: Long, value: Boolean) =
        updateSet(context, PINNED_ALBUMS, id.toString(), value)

    fun albumSort(context: Context): AlbumSort =
        runCatching { AlbumSort.valueOf(prefs(context).getString("album_sort", null) ?: "") }
            .getOrDefault(AlbumSort.RECENT)
    fun setAlbumSort(context: Context, value: AlbumSort) { prefs(context).edit().putString("album_sort", value.name).apply() }

    /** Photos grid ka sort/filter: app band karke kholne par bhi yaad rehta hai. */
    fun sort(context: Context): GallerySort =
        runCatching { GallerySort.valueOf(prefs(context).getString("gallery_sort", null) ?: "") }
            .getOrDefault(GallerySort.DATE_NEWEST)
    fun setSort(context: Context, value: GallerySort) { prefs(context).edit().putString("gallery_sort", value.name).apply() }
    fun filter(context: Context): MediaFilter =
        runCatching { MediaFilter.valueOf(prefs(context).getString("gallery_filter", null) ?: "") }
            .getOrDefault(MediaFilter.ALL)
    fun setFilter(context: Context, value: MediaFilter) { prefs(context).edit().putString("gallery_filter", value.name).apply() }

    /** Slideshow me har photo kitni der dikhe (ms). */
    fun slideshowDelayMs(context: Context): Int = prefs(context).getInt("slideshow_delay_ms", DEFAULT_SLIDESHOW_MS).coerceIn(1000, 15000)
    fun setSlideshowDelayMs(context: Context, value: Int) { prefs(context).edit().putInt("slideshow_delay_ms", value.coerceIn(1000, 15000)).apply() }
    const val DEFAULT_SLIDESHOW_MS = 3000

    /** Viewer me video khulte hi apne aap chale (default band). */
    fun videoAutoplay(context: Context): Boolean = prefs(context).getBoolean("video_autoplay", false)
    fun setVideoAutoplay(context: Context, value: Boolean) { prefs(context).edit().putBoolean("video_autoplay", value).apply() }

    /** Video bina awaaz ke shuru ho (default band). */
    fun videoMuted(context: Context): Boolean = prefs(context).getBoolean("video_muted", false)
    fun setVideoMuted(context: Context, value: Boolean) { prefs(context).edit().putBoolean("video_muted", value).apply() }

    /** App ke andar haptic feedback (selection tick, long-press, scrubber). Default on. */
    fun hapticsEnabled(context: Context): Boolean = prefs(context).getBoolean("haptics_enabled", true)
    fun setHapticsEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("haptics_enabled", value).apply() }

    /** Video loop on/off (sab videos ke liye ek hi setting). */
    fun videoLoop(context: Context): Boolean = prefs(context).getBoolean("video_loop", false)
    fun setVideoLoop(context: Context, value: Boolean) { prefs(context).edit().putBoolean("video_loop", value).apply() }

    /** Har video ki aakhri position (ms). Sirf pichhle MAX_VIDEO_POSITIONS videos yaad rehte hain. */
    private const val VIDEO_POSITIONS = "video_positions"
    private const val MAX_VIDEO_POSITIONS = 100

    fun videoPosition(context: Context, key: String): Long = readVideoPositions(context)[key]?.first ?: 0L

    /** positionMs <= 0 = is video ki saved position hata do. */
    fun setVideoPosition(context: Context, key: String, positionMs: Long) {
        val map = readVideoPositions(context).toMutableMap()
        if (positionMs <= 0L) {
            if (map.remove(key) == null) return
        } else {
            map[key] = positionMs to System.currentTimeMillis()
            if (map.size > MAX_VIDEO_POSITIONS) {
                map.entries.sortedBy { it.value.second }
                    .take(map.size - MAX_VIDEO_POSITIONS)
                    .forEach { map.remove(it.key) }
            }
        }
        prefs(context).edit().putStringSet(VIDEO_POSITIONS, map.mapTo(HashSet()) { "${it.key}|${it.value.first}|${it.value.second}" }).apply()
    }

    // Entry "key|positionMs|savedAtMs".
    private fun readVideoPositions(context: Context): Map<String, Pair<Long, Long>> {
        val raw = prefs(context).getStringSet(VIDEO_POSITIONS, emptySet()) ?: emptySet()
        val map = HashMap<String, Pair<Long, Long>>()
        for (entry in raw) {
            val parts = entry.split('|')
            if (parts.size < 3) continue
            val savedAt = parts[parts.size - 1].toLongOrNull() ?: continue
            val pos = parts[parts.size - 2].toLongOrNull() ?: continue
            map[parts.subList(0, parts.size - 2).joinToString("|")] = pos to savedAt
        }
        return map
    }

    fun theme(context: Context): String = prefs(context).getString("theme", "system") ?: "system"
    fun setTheme(context: Context, value: String) { prefs(context).edit().putString("theme", value).apply() }
    fun columns(context: Context): Int = prefs(context).getInt("grid_columns", 3)
    fun setColumns(context: Context, value: Int) { prefs(context).edit().putInt("grid_columns", value.coerceIn(2, 8)).apply() }

    // Trash entries "key|timestampMs" format me. Bina timestamp wali purani entries ko pehli read pe
    // abhi ka time mil jaata hai, taaki unka 30 din ka timer shuru ho.
    private fun readTrashMap(context: Context): Map<String, Long> {
        val raw = prefs(context).getStringSet(TRASH, emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()
        var migrated = false
        val map = LinkedHashMap<String, Long>()
        for (entry in raw) {
            val split = entry.lastIndexOf('|')
            val stamp = if (split > 0) entry.substring(split + 1).toLongOrNull() else null
            if (stamp != null) {
                map[entry.substring(0, split)] = stamp
            } else {
                map[entry] = now
                migrated = true
            }
        }
        if (migrated) writeTrashMap(context, map)
        return map
    }

    private fun writeTrashMap(context: Context, map: Map<String, Long>) {
        prefs(context).edit().putStringSet(TRASH, map.mapTo(HashSet()) { "${it.key}|${it.value}" }).apply()
    }

    private fun updateSet(context: Context, key: String, value: String, add: Boolean) {
        val next = readSet(context, key).toMutableSet()
        if (add) next.add(value) else next.remove(value)
        prefs(context).edit().putStringSet(key, next).apply()
    }
}