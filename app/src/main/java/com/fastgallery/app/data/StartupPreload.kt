package com.fastgallery.app.data

import android.app.Application
import java.util.concurrent.FutureTask

/** Pehle page ki cache + uske saath chahiye prefs sets, ek saath padhe hue. */
class FirstPageSnapshot(
    val items: List<MediaItem>,
    val favorites: Set<String>,
    val trashKeys: Set<String>,
    val trashTimes: Map<String, Long>,
    val hidden: Set<String>,
    val locked: Set<String>,
)

/**
 * Cold start: disk cache + prefs ka kaam `Application.onCreate` se hi background thread par shuru ho jaata hai,
 * Activity/ViewModel ban-ne ka intezaar kiye bina. ViewModel `consume()` se wahi result le leta hai
 * (kaam pehle khatam ho chuka ho to turant, warna bacha hua intezaar).
 *
 * One-shot hai: sirf process ki pehli ViewModel ko milta hai. Baad me (ViewModel dobara bane) normal raasta.
 */
object StartupPreload {
    private var task: FutureTask<FirstPageSnapshot?>? = null

    @Synchronized
    fun start(app: Application) {
        if (task != null) return
        val t = FutureTask<FirstPageSnapshot?> { read(app) }
        task = t
        Thread(t, "gallery-preload").start()
    }

    /** null = preload shuru hi nahi hua. Non-null task ka `get()` null de sakta hai (cache nahi mili). */
    @Synchronized
    fun consume(): FutureTask<FirstPageSnapshot?>? = task.also { task = null }

    /** Preload ka wahi kaam jo ViewModel pehle khud karta tha. IO / background thread se bulao. */
    fun read(app: Application): FirstPageSnapshot? {
        val items = FirstPageCache.shared(app).read()?.takeIf { it.isNotEmpty() } ?: return null
        return FirstPageSnapshot(
            items = items,
            favorites = GalleryPreferences.favorites(app),
            trashKeys = GalleryPreferences.trashed(app),
            trashTimes = GalleryPreferences.trashTimes(app),
            hidden = GalleryPreferences.hiddenAlbums(app),
            locked = GalleryPreferences.lockedAlbums(app),
        )
    }
}
