package com.fastgallery.app

import android.util.Log
import android.app.Application
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.FirstPageCache
import com.fastgallery.app.data.GridModel
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.MediaFilter
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaOperations
import com.fastgallery.app.data.MediaRepository
import com.fastgallery.app.data.buildAlbums
import com.fastgallery.app.data.buildGridModel
import com.fastgallery.app.data.matchesFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class GalleryState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val itemsVersion: Long = 0L,
    val albums: List<Album> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val trashKeys: Set<String> = emptySet(),
    /** System (MediaStore) trash ke items, API 30+. */
    val trashItems: List<MediaItem> = emptyList(),
    /** Purane/fallback trash flags: key -> trash time (ms). */
    val trashTimes: Map<String, Long> = emptyMap(),
    /** Is session me authenticate hua locked album. Process/background ke baad null ho jaata hai. */
    val unlockedAlbumId: Long? = null,
    val hiddenAlbumIds: Set<String> = emptySet(),
    val lockedAlbumIds: Set<String> = emptySet(),
    /**
     * true = items disk ki pehle-page cache se aaye hain (stale ho sakte hain). Asli MediaStore query
     * khatam hone par false. Tab tak UI tap/open/select block karta hai.
     */
    val fromCache: Boolean = false,
    /**
     * System trash ki query (API 30+) ab pehle page ke baad alag se aati hai (tap/open usse nahi rukta).
     * Jab tak false, Trash tab skeleton dikhata hai ("Trash khali hai" ka jhootha flash na aaye).
     */
    val trashLoaded: Boolean = false,
)

data class GalleryQuery(
    val tab: Int = 0,
    val albumId: Long? = null,
    val search: String = "",
    val sort: GallerySort = GallerySort.DATE_NEWEST,
    val filter: MediaFilter = MediaFilter.ALL,
)

data class GalleryDisplayResult(
    val query: GalleryQuery? = null,
    val items: List<MediaItem> = emptyList(),
    /** `items` se bana grid data (headers, day groups): background me banta hai, UI bas dikhata hai. */
    val model: GridModel = GridModel.EMPTY,
    /** Har naye derived result pe badhta hai; UI isse grid ke entries rebuild karta hai. */
    val version: Long = 0L,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        const val FIRST_PAGE_SIZE = 90      // pehla paint: sirf itna chahiye
        const val PAGE_SIZE = 300           // scroll pe agle pages
        const val BULK_PAGE_SIZE = 2000     // search/albums/sort ke liye full load
        const val MAX_REFRESH_WINDOW = 6000
        const val REFRESH_DEBOUNCE_MS = 700L
        const val SEARCH_DEBOUNCE_MS = 180L
    }

    /** UI ke liye snapshot state: composition ko turant dikhta hai (race se bachne ke liye). */
    var unlockedAlbumId by mutableStateOf<Long?>(null)
        private set

    private val resolver = app.contentResolver
    private val repository = MediaRepository(resolver)
    private val firstPageCache = FirstPageCache(app)
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    /** Pull-to-refresh indicator: sirf user ke swipe se shuru hone wale refresh me true. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _query = MutableStateFlow(GalleryQuery())
    private var lastSearchSeen = ""
    // Debounce sirf search typing pe; tab/data change pe turant.
    private val settledQuery: Flow<GalleryQuery> = _query.debounce { q ->
        val typing = q.search != lastSearchSeen
        lastSearchSeen = q.search
        if (typing) SEARCH_DEBOUNCE_MS else 0L
    }
    val displayItems: Flow<GalleryDisplayResult> = combine(_state, settledQuery) { gallery, query ->
        gallery to query
    }
        .mapLatest { (gallery, query) ->
            val (items, model) = withContext(Dispatchers.Default) {
                val derived = if (requiresCompleteLibrary(query) && gallery.hasMore) {
                    emptyList()
                } else {
                    deriveDisplayItems(gallery, query)
                }
                derived to buildGridModel(derived)
            }
            GalleryDisplayResult(query, items, model, displayVersion.incrementAndGet())
        }

    private val displayVersion = java.util.concurrent.atomic.AtomicLong(0L)

    private var pageJob: Job? = null
    private var loadAllJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshPending = true
    private var lastSuccessfulRefreshMs = 0L
    private var loadGeneration = 0L

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshPending = true
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch {
                delay(REFRESH_DEBOUNCE_MS)
                refreshJob = null
                load()
            }
        }
    }

    init {
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        showCachedFirstPage()
    }

    /**
     * Cold start: pichhli baar ka pehla page disk se turant dikhao (asli query ka intezaar kiye bina).
     * Sirf tab lagta hai jab asli load abhi tak kuch na laya ho; warna asli data hi jeetta hai.
     * Hidden/locked/trash/favorite prefs bhi saath me load hote hain, taaki cache se private album
     * ki photos ek pal ke liye bhi na dikhein.
     */
    private fun showCachedFirstPage() {
        val app = getApplication<Application>()
        if (!hasMediaAccess(app)) return
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val items = firstPageCache.read()?.takeIf { it.isNotEmpty() } ?: return@withContext null
                CachedFirstPage(
                    items = items,
                    favorites = GalleryPreferences.favorites(app),
                    trashKeys = GalleryPreferences.trashed(app),
                    trashTimes = GalleryPreferences.trashTimes(app),
                    hidden = GalleryPreferences.hiddenAlbums(app),
                    locked = GalleryPreferences.lockedAlbums(app),
                )
            } ?: return@launch
            // Atomic: asli load pehle aa chuka (loading == false) ya items aa chuke ho to cache skip.
            _state.update { cur ->
                if (cur.loading && cur.items.isEmpty()) {
                    cur.copy(
                        loading = false,
                        hasMore = true, // abhi sirf pehla page; albums/search/sort ke liye poori library baaki
                        items = snapshot.items,
                        itemsVersion = cur.itemsVersion + 1,
                        albums = emptyList(),
                        favoriteKeys = snapshot.favorites,
                        trashKeys = snapshot.trashKeys,
                        trashTimes = snapshot.trashTimes,
                        hiddenAlbumIds = snapshot.hidden,
                        lockedAlbumIds = snapshot.locked,
                        fromCache = true,
                    )
                } else cur
            }
        }
    }

    private class CachedFirstPage(
        val items: List<MediaItem>,
        val favorites: Set<String>,
        val trashKeys: Set<String>,
        val trashTimes: Map<String, Long>,
        val hidden: Set<String>,
        val locked: Set<String>,
    )

    fun setQuery(query: GalleryQuery) {
        _query.value = query
    }

    /**
     * Resume only performs I/O when the MediaStore observer has marked the cache dirty.
     */
    fun refreshIfNeeded() {
        if (refreshJob?.isActive == true) return
        if (_state.value.loading && pageJob?.isActive == true) return
        if (lastSuccessfulRefreshMs == 0L || refreshPending) load()
    }

    /** Pull-to-refresh: load() chalao aur khatam hone par indicator band karo. */
    fun refresh() {
        _refreshing.value = true
        load()
    }

    /**
     * Refreshes the first window. Existing content stays visible while it is refreshed.
     */
    fun load() {
        refreshJob?.cancel()
        refreshJob = null
        val generation = ++loadGeneration
        pageJob?.cancel()
        loadAllJob?.cancel()
        val previous = _state.value
        // Refresh pe pehle jitna window already loaded tha utna hi lao, taaki scroll jump na ho.
        val windowSize = if (previous.items.isEmpty()) FIRST_PAGE_SIZE
        else previous.items.size.coerceIn(FIRST_PAGE_SIZE, MAX_REFRESH_WINDOW)
        _state.value = previous.copy(
            loading = previous.items.isEmpty(),
            loadingMore = false,
            // Cache wali partial list pe hasMore true rehne do, warna albums/favorites partial data ko poora maan lenge.
            hasMore = previous.fromCache,
        )
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                purgeExpiredFallbackTrash()
                coroutineScope {
                    // System trash query pehle page ke saath parallel chalti hai, par usse rukte nahi:
                    // page aate hi publish (tap/open allow), trash baad me.
                    val trashQuery = async { repository.queryTrashed() }
                    val page = repository.queryPage(offset = 0, pageSize = windowSize)
                    coroutineContext.ensureActive()
                    if (generation != loadGeneration) return@coroutineScope
                    val nextItems = page.items
                    // Asli data aa gaya: cache wali list replace (delete hui photos hat jaati hain), tap allow.
                    if (nextItems.isEmpty()) firstPageCache.clear()
                    else firstPageCache.save(nextItems, FIRST_PAGE_SIZE)
                    _state.value = _state.value.copy(
                        fromCache = false,
                        loading = false,
                        loadingMore = false,
                        hasMore = page.hasMore,
                        items = nextItems,
                        itemsVersion = _state.value.itemsVersion + 1,
                        albums = if (page.hasMore) emptyList() else buildAlbums(nextItems),
                        favoriteKeys = GalleryPreferences.favorites(getApplication()),
                        trashKeys = GalleryPreferences.trashed(getApplication()),
                        trashTimes = GalleryPreferences.trashTimes(getApplication()),
                        hiddenAlbumIds = GalleryPreferences.hiddenAlbums(getApplication()),
                        lockedAlbumIds = GalleryPreferences.lockedAlbums(getApplication()),
                    )
                    lastSuccessfulRefreshMs = SystemClock.elapsedRealtime()
                    refreshPending = false
                    _refreshing.value = false

                    val systemTrash = trashQuery.await()
                    coroutineContext.ensureActive()
                    if (generation != loadGeneration) return@coroutineScope
                    _state.update { it.copy(trashItems = systemTrash, trashLoaded = true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("GalleryViewModel", "Load failed", error)
                if (generation == loadGeneration) {
                    // Query fail hui: stale cache wali list dikhate rehna galat hoga, isliye hata do.
                    val stale = _state.value.fromCache
                    _state.value = _state.value.copy(
                        loading = false,
                        loadingMore = false,
                        fromCache = false,
                        trashLoaded = true,
                        items = if (stale) emptyList() else _state.value.items,
                        hasMore = if (stale) false else _state.value.hasMore,
                    )
                    _refreshing.value = false
                }
            }
        }
    }

    /**
     * Fetches the next bounded page when the grid or viewer approaches its end.
     */
    fun loadNextPage() {
        val current = _state.value
        if (current.fromCache || current.loading || current.loadingMore || !current.hasMore) return
        pageJob?.cancel()
        val generation = loadGeneration
        val offset = current.items.size
        _state.value = current.copy(loadingMore = true)
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val page = repository.queryPage(offset = offset, pageSize = PAGE_SIZE)
                coroutineContext.ensureActive()
                if (generation != loadGeneration) return@launch
                appendPage(offset, page.items, page.hasMore)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("GalleryViewModel", "Load next page failed", error)
                if (generation == loadGeneration) {
                    _state.value = _state.value.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Complete data is needed for search, non-date sorts, albums, favorites and trash.
     * Those views opt in to loading remaining pages rather than showing incomplete results.
     */
    fun loadAll() {
        val current = _state.value
        if (current.fromCache || !current.hasMore || current.loading || current.loadingMore || loadAllJob?.isActive == true) return
        pageJob?.cancel()
        val generation = loadGeneration
        _state.value = current.copy(loadingMore = true)
        loadAllJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var hasMore = true
                while (hasMore) {
                    coroutineContext.ensureActive()
                    if (generation != loadGeneration) return@launch
                    val snapshot = _state.value
                    val offset = snapshot.items.size
                    val page = repository.queryPage(offset = offset, pageSize = BULK_PAGE_SIZE)
                    coroutineContext.ensureActive()
                    if (generation != loadGeneration) return@launch
                    appendPage(offset, page.items, page.hasMore, keepLoading = page.hasMore)
                    hasMore = page.hasMore
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("GalleryViewModel", "Load all failed", error)
                if (generation == loadGeneration) {
                    _state.value = _state.value.copy(loadingMore = false)
                }
            }
        }
    }

    private fun appendPage(
        offset: Int,
        pageItems: List<MediaItem>,
        hasMore: Boolean,
        keepLoading: Boolean = false,
    ) {
        val current = _state.value
        if (current.items.size != offset) {
            _state.value = current.copy(loadingMore = false)
            return
        }
        val knownKeys = current.items.asSequence().map { it.key }.toHashSet()
        val appended = pageItems.filter { knownKeys.add(it.key) }
        val allItems = current.items + appended
        _state.value = current.copy(
            loading = false,
            loadingMore = keepLoading,
            hasMore = hasMore,
            items = allItems,
            itemsVersion = current.itemsVersion + 1,
            albums = if (hasMore) emptyList() else buildAlbums(allItems),
        )
    }

    /** 30 din purane fallback-trash items (API < 30) ko asli me delete karo. API 30+ pe system khud karta hai. */
    private fun purgeExpiredFallbackTrash() {
        if (Build.VERSION.SDK_INT >= 30) return
        val app = getApplication<Application>()
        val cutoff = System.currentTimeMillis() - GalleryPreferences.TRASH_RETENTION_MS
        val expired = GalleryPreferences.trashTimes(app).filterValues { it < cutoff }.keys
        if (expired.isEmpty()) return
        val done = ArrayList<String>()
        for (key in expired) {
            val ok = runCatching {
                MediaOperations.permanentlyDeleteUri(app, android.net.Uri.parse(key)) >= 0
            }.onFailure { Log.w("GalleryViewModel", "Expired trash purge failed: $key", it) }.getOrDefault(false)
            if (ok) done += key
        }
        GalleryPreferences.setTrashedKeys(app, done, false)
    }

    /** Locked album authenticate ho gaya: sirf isi album ko is session me kholne do. */
    fun markAlbumUnlocked(id: Long) {
        unlockedAlbumId = id
        _state.value = _state.value.copy(unlockedAlbumId = id)
    }

    /** App background me gaya: locked album dobara lock. */
    fun relockAlbums() {
        unlockedAlbumId = null
        if (_state.value.unlockedAlbumId != null) _state.value = _state.value.copy(unlockedAlbumId = null)
    }

    fun refreshPreferences() {
        _state.value = _state.value.copy(
            favoriteKeys = GalleryPreferences.favorites(getApplication()),
            trashKeys = GalleryPreferences.trashed(getApplication()),
            trashTimes = GalleryPreferences.trashTimes(getApplication()),
            hiddenAlbumIds = GalleryPreferences.hiddenAlbums(getApplication()),
            lockedAlbumIds = GalleryPreferences.lockedAlbums(getApplication()),
        )
    }

    override fun onCleared() {
        resolver.unregisterContentObserver(observer)
        pageJob?.cancel()
        loadAllJob?.cancel()
        refreshJob?.cancel()
    }

    private fun requiresCompleteLibrary(query: GalleryQuery): Boolean =
        query.tab != 0 ||
            query.albumId != null ||
            query.search.isNotBlank() ||
            query.sort != GallerySort.DATE_NEWEST ||
            query.filter != MediaFilter.ALL
}

/** Tab/album/search/filter/sort ke hisaab se grid ke items. Pure function hai (unit tests isi ko check karte hain). */
internal fun deriveDisplayItems(gallery: GalleryState, query: GalleryQuery): List<MediaItem> {
    if (query.tab == 4 || (query.tab == 1 && query.albumId == null)) return emptyList()
    val hidden = gallery.hiddenAlbumIds.mapNotNullTo(HashSet()) { it.toLongOrNull() }
    val locked = gallery.lockedAlbumIds.mapNotNullTo(HashSet()) { it.toLongOrNull() }
    // Hidden + locked albums Photos / Favorites / Trash / search me kabhi nahi dikhte.
    // Locked album sirf authenticate hone ke baad apne album screen me khulta hai.
    val restricted = HashSet<Long>(hidden).apply { addAll(locked) }
    val baseItems = when {
        query.tab == 3 -> (gallery.trashItems + gallery.items.filter { it.key in gallery.trashKeys })
            .filter { it.bucketId !in restricted }
            .sortedByDescending { it.dateAdded }
        query.tab == 2 -> gallery.items.filter {
            it.key in gallery.favoriteKeys &&
                it.key !in gallery.trashKeys &&
                it.bucketId !in restricted
        }
        query.albumId != null -> {
            val id: Long = query.albumId
            if (id in locked && gallery.unlockedAlbumId != id) emptyList()
            else gallery.items.filter { it.bucketId == id && it.key !in gallery.trashKeys }
        }
        // Common case (koi hidden/locked/trash nahi): list copy hi skip.
        restricted.isEmpty() && gallery.trashKeys.isEmpty() -> gallery.items
        else -> gallery.items.filter {
            it.bucketId !in restricted && it.key !in gallery.trashKeys
        }
    }
    val filtered = baseItems.asSequence()
        .filter { it.matchesFilter(query.filter) }
        .filter {
            query.search.isBlank() ||
                it.name.contains(query.search, ignoreCase = true) ||
                it.bucketName.contains(query.search, ignoreCase = true)
        }
        .toList()
    return when (query.sort) {
        // MediaStore already emits each page in this exact date order.
        GallerySort.DATE_NEWEST -> filtered
        GallerySort.DATE_OLDEST -> filtered.sortedBy { it.dateAdded }
        GallerySort.NAME -> filtered.sortedBy { it.name.lowercase() }
        GallerySort.SIZE_LARGEST -> filtered.sortedByDescending { it.sizeBytes }
    }
}
