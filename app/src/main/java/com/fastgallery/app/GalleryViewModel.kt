package com.fastgallery.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.GridEntry
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaRepository
import com.fastgallery.app.data.buildAlbums
import com.fastgallery.app.data.buildEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val entries: List<GridEntry> = emptyList(),
    val albums: List<Album> = emptyList(),
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val resolver = app.contentResolver
    private val repo = MediaRepository(resolver)

    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private var job: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            load(delayMs = 600) // debounce: naye photos aate hi auto refresh
        }
    }

    init {
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    fun load(delayMs: Long = 0) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0) delay(delayMs)
            val items = repo.queryAll()
            val current = _state.value
            if (!current.loading && items == current.items) return@launch
            _state.value = GalleryState(
                loading = false,
                items = items,
                entries = buildEntries(items),
                albums = buildAlbums(items),
            )
        }
    }

    override fun onCleared() {
        resolver.unregisterContentObserver(observer)
    }
}
