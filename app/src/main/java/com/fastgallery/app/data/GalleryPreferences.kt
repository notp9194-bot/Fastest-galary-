package com.fastgallery.app.data

import android.content.Context

/** Small, device-local gallery state; media files remain in the user's normal storage. */
object GalleryPreferences {
    private const val FILE = "fast_gallery_preferences"
    private const val FAVORITES = "favorites"
    private const val TRASH = "trash"
    private const val HIDDEN_ALBUMS = "hidden_albums"
    private const val LOCKED_ALBUMS = "locked_albums"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun readSet(context: Context, key: String): Set<String> = prefs(context).getStringSet(key, emptySet())?.toSet() ?: emptySet()

    fun favorites(context: Context) = readSet(context, FAVORITES)
    fun trashed(context: Context) = readSet(context, TRASH)
    fun hiddenAlbums(context: Context) = readSet(context, HIDDEN_ALBUMS)
    fun lockedAlbums(context: Context) = readSet(context, LOCKED_ALBUMS)
    fun isFavorite(context: Context, item: MediaItem) = item.key in favorites(context)
    fun isTrashed(context: Context, item: MediaItem) = item.key in trashed(context)

    fun toggleFavorite(context: Context, item: MediaItem) =
        updateSet(context, FAVORITES, item.key, item.key !in favorites(context))

    fun setTrashed(context: Context, item: MediaItem, value: Boolean) =
        updateSet(context, TRASH, item.key, value)

    fun setAlbumHidden(context: Context, id: Long, value: Boolean) =
        updateSet(context, HIDDEN_ALBUMS, id.toString(), value)

    fun setAlbumLocked(context: Context, id: Long, value: Boolean) =
        updateSet(context, LOCKED_ALBUMS, id.toString(), value)

    fun theme(context: Context): String = prefs(context).getString("theme", "system") ?: "system"
    fun setTheme(context: Context, value: String) { prefs(context).edit().putString("theme", value).apply() }
    fun columns(context: Context): Int = prefs(context).getInt("grid_columns", 3)
    fun setColumns(context: Context, value: Int) { prefs(context).edit().putInt("grid_columns", value.coerceIn(2, 8)).apply() }

    private fun updateSet(context: Context, key: String, value: String, add: Boolean) {
        val next = readSet(context, key).toMutableSet()
        if (add) next.add(value) else next.remove(value)
        prefs(context).edit().putStringSet(key, next).apply()
    }
}