package com.fastgallery.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.MediaFilter
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaOperations
import com.fastgallery.app.ui.AlbumsGrid
import com.fastgallery.app.ui.CenterMessage
import com.fastgallery.app.ui.GalleryTheme
import com.fastgallery.app.ui.MediaGrid
import com.fastgallery.app.ui.PermissionScreen
import com.fastgallery.app.ui.SettingsScreen
import com.fastgallery.app.ui.Viewer
import com.fastgallery.app.ui.shareItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GalleryRoot() }
    }
}

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(GalleryPreferences.theme(ctx)) }
    GalleryTheme(theme) {
        GalleryContent(vm, theme) {
            GalleryPreferences.setTheme(ctx, it)
            theme = it
            ctx.findActivity()?.recreate()
        }
    }
}

private fun authenticateAlbum(activity: Activity?, onSuccess: () -> Unit, onFailure: () -> Unit) {
    val host = activity as? FragmentActivity ?: run { onFailure(); return }
    val authenticators = if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (BiometricManager.from(host).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onFailure()
        return
    }
    val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock private album")
        .setSubtitle("Confirm your device identity")
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryContent(
    vm: GalleryViewModel,
    theme: String,
    onTheme: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val currentList by vm.displayItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasMediaAccess(ctx)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.load()
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            permissionLauncher.launch(mediaPermissions())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.refreshIfNeeded()
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var albumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
    var search by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(GallerySort.DATE_NEWEST) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.ALL) }
    var columns by rememberSaveable { mutableIntStateOf(GalleryPreferences.columns(ctx)) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var approvalAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val needsCompleteLibrary = tab != 0 ||
        albumId != null ||
        search.isNotBlank() ||
        sort != GallerySort.DATE_NEWEST ||
        filter != MediaFilter.ALL

    LaunchedEffect(tab, albumId, search, sort, filter, state.itemsVersion) {
        vm.setQuery(
            GalleryQuery(
                tab = tab,
                albumId = albumId,
                search = search,
                sort = sort,
                filter = filter,
            ),
        )
        if (needsCompleteLibrary) vm.loadAll()
    }

    val approvalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = approvalAction
        approvalAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
        else Toast.makeText(ctx, "Android did not approve this media change.", Toast.LENGTH_SHORT).show()
    }
    fun requestApproval(sender: android.content.IntentSender, action: () -> Unit) {
        approvalAction = action
        approvalLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }
    fun deleteMedia(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val action: () -> Unit = {
            runCatching {
                items.forEach { MediaOperations.permanentlyDelete(ctx, it) }
                items.forEach { GalleryPreferences.setTrashed(ctx, it, false) }
                vm.refreshPreferences()
                vm.load()
                selected = emptySet()
                Toast.makeText(ctx, "Selected media deleted", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(ctx, "Could not delete media: ${it.message ?: "permission denied"}", Toast.LENGTH_LONG).show()
            }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val request = MediaStore.createDeleteRequest(ctx.contentResolver, items.map { it.uri })
                requestApproval(request.intentSender, action)
            }.onFailure { Toast.makeText(ctx, "Delete permission could not be requested.", Toast.LENGTH_LONG).show() }
        } else action()
    }
    fun renameMedia(item: MediaItem, name: String) {
        val action: () -> Unit = {
            runCatching {
                MediaOperations.rename(ctx, item, name)
                vm.load()
                Toast.makeText(ctx, "Renamed", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(ctx, "Could not rename: ${it.message ?: "permission denied"}", Toast.LENGTH_LONG).show() }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                requestApproval(MediaStore.createWriteRequest(ctx.contentResolver, listOf(item.uri)).intentSender, action)
            }.onFailure { Toast.makeText(ctx, "Write permission could not be requested.", Toast.LENGTH_LONG).show() }
        } else action()
    }
    fun setTrashed(item: MediaItem, value: Boolean) {
        GalleryPreferences.setTrashed(ctx, item, value)
        vm.refreshPreferences()
        Toast.makeText(ctx, if (value) "Moved to Fast Gallery Trash" else "Restored", Toast.LENGTH_SHORT).show()
    }

    val inAlbum = tab == 1 && albumId != null
    val title = when {
        tab == 4 -> "Settings"
        inAlbum -> state.albums.firstOrNull { it.id == albumId }?.name ?: "Album"
        selected.isNotEmpty() -> "${selected.size} selected"
        tab == 0 -> "Photos"
        tab == 1 -> "Albums"
        tab == 2 -> "Favorites"
        else -> "Trash"
    }

    BackHandler(enabled = viewerIndex >= 0) { viewerIndex = -1 }
    BackHandler(enabled = viewerIndex < 0 && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = viewerIndex < 0 && albumId != null) { albumId = null }
    BackHandler(enabled = viewerIndex < 0 && tab == 4) { tab = 0 }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (searchOpen && selected.isEmpty()) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                singleLine = true,
                                placeholder = { Text("Search photos, videos, albums") },
                            )
                        } else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        if (inAlbum || selected.isNotEmpty()) {
                            TextButton(onClick = {
                                if (selected.isNotEmpty()) selected = emptySet() else albumId = null
                            }) { Text("Back") }
                        }
                    },
                    actions = {
                        if (selected.isNotEmpty()) {
                            TextButton(onClick = {
                                shareItems(ctx, currentList.filter { it.key in selected })
                            }) { Text("Share") }
                            TextButton(onClick = {
                                currentList.filter { it.key in selected }.forEach { GalleryPreferences.toggleFavorite(ctx, it) }
                                vm.refreshPreferences()
                            }) { Text("Favorite") }
                            TextButton(onClick = {
                                currentList.filter { it.key in selected }.forEach { setTrashed(it, tab != 3) }
                                selected = emptySet()
                            }) { Text(if (tab == 3) "Restore" else "Trash") }
                            TextButton(onClick = { deleteMedia(currentList.filter { it.key in selected }) }) { Text("Delete") }
                        } else if (tab != 4) {
                            TextButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Text(if (searchOpen) "Done" else "Search") }
                            TextButton(onClick = { showFilters = true }) { Text("Sort") }
                        }
                    },
                )
            },
            bottomBar = {
                if (viewerIndex < 0 && selected.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        NavigationBar(
                            modifier = Modifier.clip(RoundedCornerShape(26.dp)),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                        ) {
                            val destinations = remember {
                                listOf("Photos", "Albums", "Favorites", "Trash", "Settings")
                            }
                            val itemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            destinations.forEachIndexed { index, label ->
                                val isSelected = tab == index
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.08f else 1f,
                                    label = "navigation_icon_$index",
                                )
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        tab = index
                                        albumId = null
                                        if (index != 0) searchOpen = false
                                    },
                                    colors = itemColors,
                                    icon = {
                                        val iconTint = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        when (index) {
                                            0 -> GridTabGlyph(iconTint, iconScale)
                                            1 -> FolderTabGlyph(iconTint, iconScale)
                                            else -> Icon(
                                                imageVector = when (index) {
                                                    2 -> Icons.Filled.Favorite
                                                    3 -> Icons.Filled.Delete
                                                    else -> Icons.Filled.Settings
                                                },
                                                contentDescription = label,
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                },
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            when {
                !granted -> PermissionScreen(padding) { permissionLauncher.launch(mediaPermissions()) }
                state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                needsCompleteLibrary && state.hasMore && tab != 4 ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                tab == 4 -> SettingsScreen(
                    padding = padding,
                    theme = theme,
                    columns = columns,
                    albums = state.albums,
                    hidden = state.hiddenAlbumIds,
                    locked = state.lockedAlbumIds,
                    onTheme = onTheme,
                    onColumns = { columns = it; GalleryPreferences.setColumns(ctx, it) },
                    onHidden = { id, hidden ->
                        GalleryPreferences.setAlbumHidden(ctx, id, hidden)
                        vm.refreshPreferences()
                    },
                    onLocked = { id, locked ->
                        GalleryPreferences.setAlbumLocked(ctx, id, locked)
                        vm.refreshPreferences()
                    },
                )
                tab == 1 && !inAlbum -> {
                    val albums = state.albums.filter {
                        it.id.toString() !in state.hiddenAlbumIds &&
                            (search.isBlank() || it.name.contains(search, true))
                    }
                    if (albums.isEmpty()) CenterMessage("No albums found", padding)
                    else AlbumsGrid(albums, padding) { album ->
                        val open = { albumId = album.id }
                        if (album.id.toString() in state.lockedAlbumIds) {
                            authenticateAlbum(ctx.findActivity(), open) {
                                Toast.makeText(ctx, "Album authentication failed.", Toast.LENGTH_SHORT).show()
                            }
                        } else open()
                    }
                }
                currentList.isEmpty() -> CenterMessage(
                    when (tab) {
                        2 -> "No favorites yet"
                        3 -> "Trash is empty"
                        else -> "No matching photos or videos"
                    },
                    padding,
                )
                else -> MediaGrid(
                    items = currentList,
                    padding = padding,
                    selected = selected,
                    columns = columns,
                    flingFriction = if (tab == 0) 0.007f else 0.015f,
                    itemsVersion = state.itemsVersion,
                    onOpen = { viewerIndex = it },
                    onToggleSelection = { item ->
                        selected = if (item.key in selected) selected - item.key else selected + item.key
                    },
                    onPinchColumns = { next ->
                        columns = next.coerceIn(2, 8)
                        GalleryPreferences.setColumns(ctx, columns)
                    },
                    onLoadMore = vm::loadNextPage,
                )
            }
        }
        if (viewerIndex >= 0 && currentList.isNotEmpty()) {
            Viewer(
                items = currentList,
                startIndex = viewerIndex,
                favoriteKeys = state.favoriteKeys,
                trashedKeys = state.trashKeys,
                onLoadMore = vm::loadNextPage,
                onClose = { viewerIndex = -1 },
                onFavorite = { GalleryPreferences.toggleFavorite(ctx, it); vm.refreshPreferences() },
                onSetTrashed = ::setTrashed,
                onDelete = { deleteMedia(listOf(it)) },
                onRename = ::renameMedia,
                onCopyOrMove = { item, folder, move ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.copyToAlbum(ctx, item, folder) }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess && result.getOrNull() != null) {
                                Toast.makeText(ctx, "Copied to $folder", Toast.LENGTH_SHORT).show()
                                vm.load()
                                if (move) deleteMedia(listOf(item))
                            } else Toast.makeText(ctx, "Copy failed: ${result.exceptionOrNull()?.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onWallpaper = { item ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.setWallpaper(ctx, item) }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, if (result.isSuccess) "Wallpaper set" else "Wallpaper failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onEdit = { item, edit ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.saveEditedCopy(ctx, item, edit) }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess && result.getOrNull() != null) {
                                Toast.makeText(ctx, "Edited copy saved", Toast.LENGTH_SHORT).show()
                                vm.load()
                            } else Toast.makeText(ctx, "Edit failed: ${result.exceptionOrNull()?.message ?: "format not supported"}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("Sort and filter") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Sort by", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    listOf(
                        GallerySort.DATE_NEWEST to "Newest first",
                        GallerySort.DATE_OLDEST to "Oldest first",
                        GallerySort.NAME to "Name",
                        GallerySort.SIZE_LARGEST to "Largest first",
                    ).forEach { (value, label) ->
                        TextButton(onClick = { sort = value }) { Text(if (sort == value) "✓ $label" else label) }
                    }
                    Text("Show", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    listOf(
                        MediaFilter.ALL to "All media",
                        MediaFilter.PHOTOS to "Photos",
                        MediaFilter.VIDEOS to "Videos",
                        MediaFilter.GIFS to "GIFs",
                        MediaFilter.RAW to "RAW",
                    ).forEach { (value, label) ->
                        TextButton(onClick = { filter = value }) { Text(if (filter == value) "✓ $label" else label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { sort = GallerySort.DATE_NEWEST; filter = MediaFilter.ALL }) { Text("Reset") } },
        )
    }
}

@Composable
private fun GridTabGlyph(tint: Color, scale: Float) {
    Canvas(
        Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        val side = size.minDimension
        val gap = side * 0.12f
        val cell = (side - gap * 3f) / 2f
        for (row in 0..1) {
            for (column in 0..1) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(gap + column * (cell + gap), gap + row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.18f),
                )
            }
        }
    }
}

@Composable
private fun FolderTabGlyph(tint: Color, scale: Float) {
    Canvas(
        Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        val w = size.width
        val h = size.height
        val folder = Path().apply {
            moveTo(w * 0.09f, h * 0.25f)
            lineTo(w * 0.39f, h * 0.25f)
            lineTo(w * 0.51f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.38f)
            quadraticTo(w * 0.95f, h * 0.38f, w * 0.93f, h * 0.47f)
            lineTo(w * 0.85f, h * 0.77f)
            quadraticTo(w * 0.83f, h * 0.84f, w * 0.76f, h * 0.84f)
            lineTo(w * 0.17f, h * 0.84f)
            quadraticTo(w * 0.09f, h * 0.84f, w * 0.08f, h * 0.76f)
            lineTo(w * 0.06f, h * 0.35f)
            quadraticTo(w * 0.06f, h * 0.25f, w * 0.09f, h * 0.25f)
            close()
        }
        drawPath(path = folder, color = tint)
    }
}