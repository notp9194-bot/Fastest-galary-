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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fastgallery.app.data.AlbumSort
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.ui.SelectAllIcon
import com.fastgallery.app.ui.SelectionActionBar
import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.MediaFilter
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaOperations
import com.fastgallery.app.data.sortAlbums
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.fastgallery.app.ui.AlbumsGrid
import com.fastgallery.app.ui.EmptyState
import com.fastgallery.app.ui.SkeletonGrid
import com.fastgallery.app.ui.GalleryTheme
import com.fastgallery.app.ui.ManagedAlbumsScreen
import com.fastgallery.app.ui.MediaGrid
import com.fastgallery.app.ui.PartialAccessBanner
import com.fastgallery.app.ui.PermissionScreen
import com.fastgallery.app.ui.SettingsScreen
import com.fastgallery.app.ui.SortIcon
import com.fastgallery.app.ui.Viewer
import com.fastgallery.app.ui.shareItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // MediaStore query composition se pehle hi shuru: Compose setup ke saath parallel chalti hai.
        val vm = ViewModelProvider(this)[GalleryViewModel::class.java]
        if (hasMediaAccess(this)) vm.refreshIfNeeded()
        setContent { GalleryRoot(vm) }
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

private fun albumAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else BiometricManager.Authenticators.BIOMETRIC_WEAK

private fun canAuthenticateAlbum(activity: Activity?): Boolean {
    val host = activity as? FragmentActivity ?: return false
    return BiometricManager.from(host).canAuthenticate(albumAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun authenticateAlbum(activity: Activity?, onSuccess: () -> Unit, onFailure: () -> Unit) {
    val host = activity as? FragmentActivity ?: run { onFailure(); return }
    if (!canAuthenticateAlbum(host)) {
        onFailure()
        return
    }
    val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(host.getString(R.string.auth_title))
        .setSubtitle(host.getString(R.string.auth_subtitle))
        .setAllowedAuthenticators(albumAuthenticators())
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
    val displayResult by vm.displayItems.collectAsStateWithLifecycle(
        initialValue = GalleryDisplayResult(),
    )
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasMediaAccess(ctx)) }
    var partialAccess by remember { mutableStateOf(isPartialMediaAccess(ctx)) }
    var partialBannerDismissed by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(ctx)
        partialAccess = isPartialMediaAccess(ctx)
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
        partialAccess = isPartialMediaAccess(ctx)
        if (!partialAccess) partialBannerDismissed = false
        if (granted) vm.refreshIfNeeded()
    }

    // App background me jaaye to locked album dobara lock (rotation/theme recreate pe nahi).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (ctx.findActivity()?.isChangingConfigurations != true) vm.relockAlbums()
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var albumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
    // Settings sub-screens: 0 = main, 1 = hidden albums, 2 = locked albums
    var settingsPage by rememberSaveable { mutableIntStateOf(0) }
    var albumMenuOpen by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showAlbumSort by remember { mutableStateOf(false) }
    var albumSort by remember { mutableStateOf(GalleryPreferences.albumSort(ctx)) }
    var pinnedAlbums by remember { mutableStateOf(GalleryPreferences.pinnedAlbums(ctx)) }
    var sort by rememberSaveable { mutableStateOf(GallerySort.DATE_NEWEST) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.ALL) }
    var columns by rememberSaveable { mutableIntStateOf(GalleryPreferences.columns(ctx)) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var approvalAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Locked album bina authentication ke kabhi khula na rahe (process restore / relock ke baad bhi).
    val openAlbumLockedOut = albumId?.let { id ->
        id.toString() in state.lockedAlbumIds && vm.unlockedAlbumId != id
    } == true
    LaunchedEffect(openAlbumLockedOut) {
        if (openAlbumLockedOut) {
            albumId = null
            viewerIndex = -1
            selected = emptySet()
        }
    }
    val activeQuery = GalleryQuery(
        tab = tab,
        albumId = albumId,
        search = search,
        sort = sort,
        filter = filter,
    )
    val displayContextStale =
        displayResult.query?.tab != tab || displayResult.query?.albumId != albumId
    val currentList = if (displayContextStale) emptyList() else displayResult.items
    val needsCompleteLibrary = tab != 0 ||
        albumId != null ||
        search.isNotBlank() ||
        sort != GallerySort.DATE_NEWEST ||
        filter != MediaFilter.ALL

    LaunchedEffect(activeQuery, state.itemsVersion, state.loadingMore, state.hasMore) {
        vm.setQuery(activeQuery)
        if (needsCompleteLibrary) vm.loadAll()
    }

    val approvalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = approvalAction
        approvalAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
        else Toast.makeText(ctx, ctx.getString(R.string.msg_approval_denied), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(ctx, ctx.getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(ctx, ctx.getString(R.string.msg_delete_failed, it.message ?: ctx.getString(R.string.msg_permission_denied)), Toast.LENGTH_LONG).show()
            }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val request = MediaStore.createDeleteRequest(ctx.contentResolver, items.map { it.uri })
                requestApproval(request.intentSender, action)
            }.onFailure { Toast.makeText(ctx, ctx.getString(R.string.msg_delete_request_failed), Toast.LENGTH_LONG).show() }
        } else action()
    }
    fun renameMedia(item: MediaItem, name: String) {
        val action: () -> Unit = {
            runCatching {
                MediaOperations.rename(ctx, item, name)
                vm.load()
                Toast.makeText(ctx, ctx.getString(R.string.msg_renamed), Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(ctx, ctx.getString(R.string.msg_rename_failed, it.message ?: ctx.getString(R.string.msg_permission_denied)), Toast.LENGTH_LONG).show() }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                requestApproval(MediaStore.createWriteRequest(ctx.contentResolver, listOf(item.uri)).intentSender, action)
            }.onFailure { Toast.makeText(ctx, ctx.getString(R.string.msg_write_request_failed), Toast.LENGTH_LONG).show() }
        } else action()
    }
    /**
     * Trash / restore.
     * API 30+: asli system trash (MediaStore.createTrashRequest). Files doosre apps me bhi trash hoti hain
     * aur MediaStore 30 din baad khud delete kar deta hai.
     * API < 30: system trash nahi hai, isliye app-level flag + 30 din baad auto-delete (fallback).
     */
    fun trashMedia(items: List<MediaItem>, value: Boolean) {
        if (items.isEmpty()) return
        val systemItems = when {
            Build.VERSION.SDK_INT < 30 -> emptyList()
            value -> items
            else -> items.filter { it.isTrashed }
        }
        val systemSet = systemItems.toSet()
        val flagItems = items.filter { it !in systemSet }
        if (flagItems.isNotEmpty()) {
            GalleryPreferences.setTrashedKeys(ctx, flagItems.map { it.key }, value)
            vm.refreshPreferences()
        }
        val message = ctx.getString(if (value) R.string.msg_trashed else R.string.msg_restored)
        if (systemItems.isEmpty()) {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val request = MediaStore.createTrashRequest(ctx.contentResolver, systemItems.map { it.uri }, value)
            requestApproval(request.intentSender) {
                vm.load()
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            }
        }.onFailure { Toast.makeText(ctx, ctx.getString(R.string.msg_trash_request_failed), Toast.LENGTH_LONG).show() }
    }
    fun migrateLegacyTrash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val request = MediaStore.createTrashRequest(ctx.contentResolver, items.map { it.uri }, true)
            requestApproval(request.intentSender) {
                GalleryPreferences.setTrashedKeys(ctx, items.map { it.key }, false)
                vm.refreshPreferences()
                vm.load()
                Toast.makeText(ctx, ctx.getString(R.string.msg_trash_migrated), Toast.LENGTH_SHORT).show()
            }
        }.onFailure { Toast.makeText(ctx, ctx.getString(R.string.msg_trash_request_failed), Toast.LENGTH_LONG).show() }
    }

    fun hideAlbum(id: Long) {
        GalleryPreferences.setAlbumHidden(ctx, id, true)
        vm.refreshPreferences()
        if (albumId == id) albumId = null
        Toast.makeText(ctx, ctx.getString(R.string.msg_album_hidden), Toast.LENGTH_LONG).show()
    }
    fun unhideAlbum(id: Long) {
        GalleryPreferences.setAlbumHidden(ctx, id, false)
        vm.refreshPreferences()
        Toast.makeText(ctx, ctx.getString(R.string.msg_album_unhidden), Toast.LENGTH_SHORT).show()
    }
    fun lockAlbum(id: Long) {
        GalleryPreferences.setAlbumLocked(ctx, id, true)
        // Jo album abhi khula hai wo is session me khula rahe; baaki sab ke liye lock turant lagu.
        if (albumId == id) vm.markAlbumUnlocked(id)
        vm.refreshPreferences()
        val msg = ctx.getString(
            if (canAuthenticateAlbum(ctx.findActivity())) R.string.msg_album_locked
            else R.string.msg_album_locked_no_screen_lock
        )
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
    fun unlockAlbum(id: Long) {
        val done = {
            GalleryPreferences.setAlbumLocked(ctx, id, false)
            vm.refreshPreferences()
            Toast.makeText(ctx, ctx.getString(R.string.msg_lock_removed), Toast.LENGTH_SHORT).show()
        }
        val activity = ctx.findActivity()
        // Removing a lock needs the same authentication as opening; without any device lock there is nothing to protect.
        if (!canAuthenticateAlbum(activity)) done()
        else authenticateAlbum(activity, done) {
            Toast.makeText(ctx, ctx.getString(R.string.msg_lock_remove_auth_failed), Toast.LENGTH_SHORT).show()
        }
    }

    val inAlbum = tab == 1 && albumId != null
    val title = when {
        tab == 4 -> stringResource(
            when (settingsPage) { 1 -> R.string.hidden_albums; 2 -> R.string.locked_albums; else -> R.string.nav_settings }
        )
        inAlbum -> state.albums.firstOrNull { it.id == albumId }?.name ?: stringResource(R.string.title_album_fallback)
        selected.isNotEmpty() -> stringResource(R.string.selected_count, selected.size)
        tab == 0 -> stringResource(R.string.nav_photos)
        tab == 1 -> stringResource(R.string.nav_albums)
        tab == 2 -> stringResource(R.string.nav_favorites)
        else -> stringResource(R.string.nav_trash)
    }

    val trashedKeys = remember(state.trashKeys, state.trashItems) {
        state.trashKeys + state.trashItems.map { it.key }
    }
    var migrateDismissed by rememberSaveable { mutableStateOf(false) }
    val legacyTrash = if (tab == 3 && Build.VERSION.SDK_INT >= 30 && !displayContextStale && !migrateDismissed) {
        currentList.filter { !it.isTrashed }
    } else emptyList()
    if (legacyTrash.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { migrateDismissed = true },
            title = { Text(stringResource(R.string.migrate_title)) },
            text = {
                Text(stringResource(R.string.migrate_body, legacyTrash.size))
            },
            confirmButton = {
                TextButton(onClick = { migrateDismissed = true; migrateLegacyTrash(legacyTrash) }) { Text(stringResource(R.string.action_move)) }
            },
            dismissButton = { TextButton(onClick = { migrateDismissed = true }) { Text(stringResource(R.string.action_later)) } },
        )
    }

    BackHandler(enabled = viewerIndex >= 0) { viewerIndex = -1 }
    BackHandler(enabled = viewerIndex < 0 && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = viewerIndex < 0 && albumId != null) { albumId = null }
    BackHandler(enabled = viewerIndex < 0 && tab == 4) { tab = 0 }
    BackHandler(enabled = viewerIndex < 0 && tab == 4 && settingsPage != 0) { settingsPage = 0 }

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
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                            )
                        } else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        if (inAlbum || selected.isNotEmpty() || (tab == 4 && settingsPage != 0)) {
                            IconButton(onClick = {
                                if (selected.isNotEmpty()) selected = emptySet()
                                else if (tab == 4) settingsPage = 0
                                else albumId = null
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        if (selected.isNotEmpty()) {
                            val allSelected = currentList.isNotEmpty() && currentList.all { it.key in selected }
                            IconButton(onClick = {
                                selected = if (allSelected) emptySet() else currentList.map { it.key }.toSet()
                            }) {
                                Icon(
                                    SelectAllIcon,
                                    contentDescription = stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all),
                                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else if (tab != 4) {
                            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) {
                                Icon(
                                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = stringResource(if (searchOpen) R.string.action_close_search else R.string.action_search),
                                )
                            }
                            IconButton(onClick = { if (tab == 1 && !inAlbum) showAlbumSort = true else showFilters = true }) {
                                Icon(SortIcon, contentDescription = stringResource(R.string.action_sort))
                            }
                            val openAlbum = albumId
                            if (inAlbum && openAlbum != null) {
                                Box {
                                    IconButton(onClick = { albumMenuOpen = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.album_options))
                                    }
                                    DropdownMenu(expanded = albumMenuOpen, onDismissRequest = { albumMenuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.album_hide)) },
                                            onClick = { albumMenuOpen = false; hideAlbum(openAlbum) },
                                        )
                                        val isLocked = openAlbum.toString() in state.lockedAlbumIds
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (isLocked) R.string.album_remove_lock else R.string.album_lock)) },
                                            onClick = {
                                                albumMenuOpen = false
                                                if (isLocked) unlockAlbum(openAlbum) else lockAlbum(openAlbum)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (viewerIndex < 0 && selected.isNotEmpty()) {
                    val picked = currentList.filter { it.key in selected }
                    SelectionActionBar(
                        inTrash = tab == 3,
                        allFavorite = picked.isNotEmpty() && picked.all { it.key in state.favoriteKeys },
                        onShare = { shareItems(ctx, picked) },
                        onFavorite = {
                            // Sab pehle se favorite hain to hata do, warna jo nahi hain unhe add karo.
                            val allFav = picked.all { it.key in state.favoriteKeys }
                            picked.filter { (it.key in state.favoriteKeys) == allFav }
                                .forEach { GalleryPreferences.toggleFavorite(ctx, it) }
                            vm.refreshPreferences()
                        },
                        onTrashOrRestore = {
                            selected = emptySet()
                            trashMedia(picked, tab != 3)
                        },
                        onDelete = { deleteMedia(picked) },
                    )
                } else if (viewerIndex < 0) {
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
                            val destinations = listOf(
                                stringResource(R.string.nav_photos),
                                stringResource(R.string.nav_albums),
                                stringResource(R.string.nav_favorites),
                                stringResource(R.string.nav_trash),
                                stringResource(R.string.nav_settings),
                            )
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
                                        settingsPage = 0
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
            Box(Modifier.fillMaxSize()) {
                when {
                    !granted -> PermissionScreen(padding) { permissionLauncher.launch(mediaPermissions()) }
                    state.loading -> SkeletonGrid(columns, padding)
                    needsCompleteLibrary && state.hasMore && tab != 4 -> SkeletonGrid(columns, padding)
                    tab == 4 && settingsPage == 1 -> ManagedAlbumsScreen(
                        padding = padding,
                        locked = false,
                        albums = state.albums,
                        ids = state.hiddenAlbumIds,
                        onRemove = ::unhideAlbum,
                        onAdd = ::hideAlbum,
                    )
                    tab == 4 && settingsPage == 2 -> ManagedAlbumsScreen(
                        padding = padding,
                        locked = true,
                        albums = state.albums,
                        ids = state.lockedAlbumIds,
                        onRemove = ::unlockAlbum,
                        onAdd = ::lockAlbum,
                    )
                    tab == 4 -> SettingsScreen(
                        padding = padding,
                        theme = theme,
                        columns = columns,
                        hiddenCount = state.hiddenAlbumIds.size,
                        lockedCount = state.lockedAlbumIds.size,
                        onTheme = onTheme,
                        onColumns = { columns = it; GalleryPreferences.setColumns(ctx, it) },
                        onOpenHidden = { settingsPage = 1 },
                        onOpenLocked = { settingsPage = 2 },
                    )
                    tab == 1 && !inAlbum -> {
                        val albums = sortAlbums(
                            state.albums.filter {
                                it.id.toString() !in state.hiddenAlbumIds &&
                                    (search.isBlank() || it.name.contains(search, true))
                            },
                            albumSort,
                            pinnedAlbums,
                        )
                        if (albums.isEmpty()) {
                            val searching = search.isNotBlank()
                            EmptyState(
                                icon = if (searching) Icons.Filled.Search else ImageVector.vectorResource(R.drawable.ic_albums),
                                title = stringResource(if (searching) R.string.empty_filtered_title else R.string.empty_albums_title),
                                subtitle = stringResource(if (searching) R.string.empty_filtered_subtitle else R.string.empty_albums_subtitle),
                                padding = padding,
                            )
                        }
                        else AlbumsGrid(
                            albums = albums,
                            padding = padding,
                            locked = state.lockedAlbumIds,
                            pinned = pinnedAlbums,
                            onTogglePin = { album ->
                                val pin = album.id.toString() !in pinnedAlbums
                                GalleryPreferences.setAlbumPinned(ctx, album.id, pin)
                                pinnedAlbums = GalleryPreferences.pinnedAlbums(ctx)
                            },
                            onOpen = { album ->
                                val open = { albumId = album.id }
                                if (album.id.toString() in state.lockedAlbumIds) {
                                    val unlockAndOpen = {
                                        vm.markAlbumUnlocked(album.id)
                                        open()
                                    }
                                    authenticateAlbum(ctx.findActivity(), unlockAndOpen) {
                                        Toast.makeText(ctx, ctx.getString(R.string.msg_album_auth_failed), Toast.LENGTH_SHORT).show()
                                    }
                                } else open()
                            },
                            onHide = { hideAlbum(it.id) },
                            onToggleLock = { album ->
                                if (album.id.toString() in state.lockedAlbumIds) unlockAlbum(album.id) else lockAlbum(album.id)
                            },
                        )
                    }
                    displayContextStale -> SkeletonGrid(columns, padding)
                    currentList.isEmpty() -> {
                        val filtered = search.isNotBlank() || filter != MediaFilter.ALL
                        val (icon, titleRes, subtitleRes) = when {
                            filtered -> Triple(Icons.Filled.Search, R.string.empty_filtered_title, R.string.empty_filtered_subtitle)
                            tab == 2 -> Triple(Icons.Filled.FavoriteBorder, R.string.empty_favorites_title, R.string.empty_favorites_subtitle)
                            tab == 3 -> Triple(Icons.Filled.Delete, R.string.empty_trash_title, R.string.empty_trash_subtitle)
                            else -> Triple(ImageVector.vectorResource(R.drawable.ic_photos), R.string.empty_media_title, R.string.empty_media_subtitle)
                        }
                        EmptyState(icon, stringResource(titleRes), stringResource(subtitleRes), padding)
                    }
                    else -> MediaGrid(
                        items = currentList,
                        padding = padding,
                        selected = selected,
                        columns = columns,
                        flingFriction = if (tab == 0) 0.007f else 0.015f,
                        contentVersion = displayResult.version,
                        resetKey = Triple(sort, filter, search),
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
            if (granted && partialAccess && !partialBannerDismissed) {
                PartialAccessBanner(
                    onManage = { permissionLauncher.launch(mediaPermissions()) },
                    onDismiss = { partialBannerDismissed = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            }
        }
        if (viewerIndex >= 0 && currentList.isNotEmpty()) {
            Viewer(
                items = currentList,
                startIndex = viewerIndex,
                favoriteKeys = state.favoriteKeys,
                trashedKeys = trashedKeys,
                onLoadMore = vm::loadNextPage,
                onClose = { viewerIndex = -1 },
                onFavorite = { GalleryPreferences.toggleFavorite(ctx, it); vm.refreshPreferences() },
                onSetTrashed = { item, value -> trashMedia(listOf(item), value) },
                onDelete = { deleteMedia(listOf(it)) },
                onRename = ::renameMedia,
                onCopyOrMove = { item, folder, move ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.copyToAlbum(ctx, item, folder) }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess && result.getOrNull() != null) {
                                Toast.makeText(ctx, ctx.getString(R.string.msg_copied, folder), Toast.LENGTH_SHORT).show()
                                vm.load()
                                if (move) deleteMedia(listOf(item))
                            } else Toast.makeText(ctx, ctx.getString(R.string.msg_copy_failed, result.exceptionOrNull()?.message ?: ctx.getString(R.string.msg_unknown_error)), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onWallpaper = { item ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.setWallpaper(ctx, item) }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, if (result.isSuccess) ctx.getString(R.string.msg_wallpaper_set) else ctx.getString(R.string.msg_wallpaper_failed, result.exceptionOrNull()?.message ?: ctx.getString(R.string.msg_unknown_error)), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onEdit = { item, edit ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.saveEditedCopy(ctx, item, edit) }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess && result.getOrNull() != null) {
                                Toast.makeText(ctx, ctx.getString(R.string.msg_edit_saved), Toast.LENGTH_SHORT).show()
                                vm.load()
                            } else Toast.makeText(ctx, ctx.getString(R.string.msg_edit_failed, result.exceptionOrNull()?.message ?: ctx.getString(R.string.msg_format_not_supported)), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }

    if (showAlbumSort) {
        AlertDialog(
            onDismissRequest = { showAlbumSort = false },
            title = { Text(stringResource(R.string.album_sort_title)) },
            text = {
                androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
                    listOf(
                        AlbumSort.RECENT to R.string.album_sort_recent,
                        AlbumSort.NAME to R.string.album_sort_name,
                        AlbumSort.COUNT to R.string.album_sort_count,
                    ).forEach { (value, labelRes) ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = albumSort == value,
                                    role = androidx.compose.ui.semantics.Role.RadioButton,
                                    onClick = {
                                        albumSort = value
                                        GalleryPreferences.setAlbumSort(ctx, value)
                                        showAlbumSort = false
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = albumSort == value, onClick = null)
                            Text(stringResource(labelRes), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAlbumSort = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text(stringResource(R.string.filter_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.sort_by), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    listOf(
                        GallerySort.DATE_NEWEST to R.string.sort_newest,
                        GallerySort.DATE_OLDEST to R.string.sort_oldest,
                        GallerySort.NAME to R.string.sort_name,
                        GallerySort.SIZE_LARGEST to R.string.sort_largest,
                    ).forEach { (value, labelRes) ->
                        val label = stringResource(labelRes)
                        TextButton(onClick = { sort = value }) { Text(if (sort == value) "✓ $label" else label) }
                    }
                    Text(stringResource(R.string.filter_show), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    listOf(
                        MediaFilter.ALL to R.string.filter_all,
                        MediaFilter.PHOTOS to R.string.filter_photos,
                        MediaFilter.VIDEOS to R.string.filter_videos,
                        MediaFilter.GIFS to R.string.filter_gifs,
                        MediaFilter.RAW to R.string.filter_raw,
                    ).forEach { (value, labelRes) ->
                        val label = stringResource(labelRes)
                        TextButton(onClick = { filter = value }) { Text(if (filter == value) "✓ $label" else label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text(stringResource(R.string.action_done)) } },
            dismissButton = { TextButton(onClick = { sort = GallerySort.DATE_NEWEST; filter = MediaFilter.ALL }) { Text(stringResource(R.string.action_reset)) } },
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