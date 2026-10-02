package com.fastgallery.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.MediaFilter
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaRepository
import com.fastgallery.app.data.buildAlbums
import com.fastgallery.app.data.matchesFilter
import kotlinx.coroutines.CancellationException
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
    val hiddenAlbumIds: Set<String> = emptySet(),
    val lockedAlbumIds: Set<String> = emptySet(),
)

data class GalleryQuery(
    val tab: Int = 0,
    val albumId: Long? = null,
    val search: String = "",
    val sort: GallerySort = GallerySort.DATE_NEWEST,
    val filter: MediaFilter = MediaFilter.ALL,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        const val PAGE_SIZE = 250
        const val REFRESH_DEBOUNCE_MS = 700L
        const val SEARCH_DEBOUNCE_MS = 180L
    }

    private val resolver = app.contentResolver
    private val repository = MediaRepository(resolver)
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private val _query = MutableStateFlow(GalleryQuery())
    val displayItems: Flow<List<MediaItem>> = combine(_state, _query) { gallery, query ->
        gallery to query
    }
        .debounce(SEARCH_DEBOUNCE_MS)
        .mapLatest { (gallery, query) ->
            withContext(Dispatchers.Default) {
                if (requiresCompleteLibrary(query) && gallery.hasMore) {
                    emptyList()
                } else {
                    deriveDisplayItems(gallery, query)
                }
            }
        }

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
    }

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
        _state.value = previous.copy(
            loading = previous.items.isEmpty(),
            loadingMore = false,
            hasMore = false,
        )
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val page = repository.queryPage(offset = 0, pageSize = PAGE_SIZE)
                coroutineContext.ensureActive()
                if (generation != loadGeneration) return@launch
                val nextItems = page.items
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    hasMore = page.hasMore,
                    items = nextItems,
                    itemsVersion = _state.value.itemsVersion + 1,
                    albums = if (page.hasMore) emptyList() else buildAlbums(nextItems),
                    favoriteKeys = GalleryPreferences.favorites(getApplication()),
                    trashKeys = GalleryPreferences.trashed(getApplication()),
                    hiddenAlbumIds = GalleryPreferences.hiddenAlbums(getApplication()),
                    lockedAlbumIds = GalleryPreferences.lockedAlbums(getApplication()),
                )
                lastSuccessfulRefreshMs = SystemClock.elapsedRealtime()
                refreshPending = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == loadGeneration) {
                    _state.value = _state.value.copy(loading = false, loadingMore = false)
                }
            }
        }
    }

    /**
     * Fetches the next bounded page when the grid or viewer approaches its end.
     */
    fun loadNextPage() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
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
            } catch (_: Exception) {
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
        if (!current.hasMore || current.loading || current.loadingMore || loadAllJob?.isActive == true) return
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
                    val page = repository.queryPage(offset = offset, pageSize = PAGE_SIZE)
                    coroutineContext.ensureActive()
                    if (generation != loadGeneration) return@launch
                    appendPage(offset, page.items, page.hasMore, keepLoading = page.hasMore)
                    hasMore = page.hasMore
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
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

    fun refreshPreferences() {
        _state.value = _state.value.copy(
            favoriteKeys = GalleryPreferences.favorites(getApplication()),
            trashKeys = GalleryPreferences.trashed(getApplication()),
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

    private fun deriveDisplayItems(gallery: GalleryState, query: GalleryQuery): List<MediaItem> {
        if (query.tab == 4 || (query.tab == 1 && query.albumId == null)) return emptyList()
        val baseItems = when {
            query.tab == 3 -> gallery.items.filter { it.key in gallery.trashKeys }
            query.tab == 2 -> gallery.items.filter {
                it.key in gallery.favoriteKeys &&
                    it.key !in gallery.trashKeys &&
                    it.bucketId.toString() !in gallery.hiddenAlbumIds
            }
            query.albumId != null -> gallery.items.filter {
                it.bucketId == query.albumId && it.key !in gallery.trashKeys
            }
            else -> gallery.items.filter {
                it.bucketId.toString() !in gallery.hiddenAlbumIds && it.key !in gallery.trashKeys
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

    private fun requiresCompleteLibrary(query: GalleryQuery): Boolean =
        query.tab != 0 ||
            query.albumId != null ||
            query.search.isNotBlank() ||
            query.sort != GallerySort.DATE_NEWEST ||
            query.filter != MediaFilter.ALL
}