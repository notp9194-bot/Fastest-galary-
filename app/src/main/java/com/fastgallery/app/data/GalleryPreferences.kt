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