package com.fastgallery.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fastgallery.app.ui.PipController
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Button
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fastgallery.app.data.AlbumSort
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.ui.SelectAllIcon
import com.fastgallery.app.ui.SelectionActionBar
import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.GridModel
import com.fastgallery.app.data.MediaFilter
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaOperations
import com.fastgallery.app.data.TransferJob
import com.fastgallery.app.data.TransferRules
import com.fastgallery.app.data.buildAlbums
import com.fastgallery.app.data.itemsToTransfer
import com.fastgallery.app.data.sortAlbums
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.fastgallery.app.ui.AlbumPickerSheet
import com.fastgallery.app.ui.AlbumsGrid
import com.fastgallery.app.ui.EmptyState
import com.fastgallery.app.ui.SkeletonGrid
import com.fastgallery.app.ui.GalleryTheme
import com.fastgallery.app.ui.ManagedAlbumsScreen
import com.fastgallery.app.ui.MediaGrid
import com.fastgallery.app.ui.RefreshableBox
import com.fastgallery.app.ui.PartialAccessBanner
import com.fastgallery.app.ui.PermissionScreen
import com.fastgallery.app.ui.SettingsScreen
import com.fastgallery.app.ui.SortIcon
import com.fastgallery.app.ui.Viewer
import com.fastgallery.app.ui.BulkProgress
import com.fastgallery.app.ui.HapticsGate
import com.fastgallery.app.ui.TabSwipeContainer
import com.fastgallery.app.ui.friendlyError
import com.fastgallery.app.ui.BulkProgressBar
import com.fastgallery.app.ui.shareItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // MediaStore query composition se pehle hi shuru: Compose setup ke saath parallel chalti hai.
        val vm = ViewModelProvider(this)[GalleryViewModel::class.java]
        val access = hasMediaAccess(this)
        if (access) vm.refreshIfNeeded()
        // Splash ko koi hold nahi: pehla frame bante hi hat jaati hai (system icon flash). Pehla content:
        // cache hit pe pichhla pehla page, warna SkeletonGrid; asli data aate hi replace.
        setContent { GalleryRoot(vm) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.setInPip(isInPictureInPictureMode)
    }
}

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(GalleryPreferences.theme(ctx)) }
    var haptics by remember { mutableStateOf(GalleryPreferences.hapticsEnabled(ctx)) }
    GalleryTheme(theme) {
        HapticsGate(haptics) {
            GalleryContent(
                vm, theme,
                onTheme = {
                    GalleryPreferences.setTheme(ctx, it)
                    theme = it
                    ctx.findActivity()?.recreate()
                },
                haptics = haptics,
                onHaptics = { haptics = it; GalleryPreferences.setHapticsEnabled(ctx, it) },
            )
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
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    // Doosre app ne picker ki tarah kholi ho (GET_CONTENT / PICK) to non-null.
    val pick = remember { PickRequest.from(ctx.findActivity()?.intent) }
    val finishPick: (List<MediaItem>) -> Unit = { picked ->
        val act = ctx.findActivity()
        if (act != null && picked.isNotEmpty()) {
            val clip = android.content.ClipData.newRawUri(null, picked.first().uri)
            picked.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it.uri)) }
            act.setResult(
                Activity.RESULT_OK,
                android.content.Intent().apply {
                    data = picked.first().uri
                    clipData = clip
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            act.finish()
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val displayResult by vm.displayItems.collectAsStateWithLifecycle(
        initialValue = GalleryDisplayResult(),
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Toast ki jagah: ek hi snackbar host (Scaffold / Viewer ke upar), naya message purane ko hata deta hai.
    // long = true: error / lamba message, dismiss button ke saath.
    fun notify(message: String, long: Boolean = false) {
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = long,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
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
    var viewerOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var viewerOriginIndex by remember { mutableIntStateOf(-1) }
    val gridOriginLookup = remember { com.fastgallery.app.ui.GridOriginLookup() }
    // Settings sub-screens: 0 = main, 1 = hidden albums, 2 = locked albums
    var settingsPage by rememberSaveable { mutableIntStateOf(0) }
    var albumMenuOpen by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showAlbumSort by remember { mutableStateOf(false) }
    var albumSort by remember { mutableStateOf(GalleryPreferences.albumSort(ctx)) }
    var pinnedAlbums by remember { mutableStateOf(GalleryPreferences.pinnedAlbums(ctx)) }
    var sort by rememberSaveable { mutableStateOf(GalleryPreferences.sort(ctx)) }
    var filter by rememberSaveable { mutableStateOf(pick?.filter ?: GalleryPreferences.filter(ctx)) }
    // Sort/filter prefs me save. Picker mode me filter save nahi hota (wo doosre app ki request se aata hai).
    LaunchedEffect(sort) { GalleryPreferences.setSort(ctx, sort) }
    LaunchedEffect(filter) { if (pick == null) GalleryPreferences.setFilter(ctx, filter) }
    var columns by rememberSaveable { mutableIntStateOf(GalleryPreferences.columns(ctx)) }
    var slideshowMs by remember { mutableIntStateOf(GalleryPreferences.slideshowDelayMs(ctx)) }
    var videoAutoplay by remember { mutableStateOf(GalleryPreferences.videoAutoplay(ctx)) }
    var videoMuted by remember { mutableStateOf(GalleryPreferences.videoMuted(ctx)) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // Multi-select se Copy / Move: album picker sheet jin items ke liye khuli hai (null = band). Dono alag state:
    // mode action chunte waqt hi tay hota hai, picker me badalta nahi.
    var copyTargets by remember { mutableStateOf<List<MediaItem>?>(null) }
    var moveTargets by remember { mutableStateOf<List<MediaItem>?>(null) }
    var approvalAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var approvalDenied by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Bulk delete/copy ka progress. Cancel flag background loop padhta hai (coroutine cancel nahi: cleanup Main pe chahiye).
    var bulk by remember { mutableStateOf<BulkProgress?>(null) }
    val bulkCancel = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
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
    // Har grid-wali tab ka apna scroll state (Photos / Favorites / Trash), album ka alag (album badalne par naya).
    // Tab badalkar wapas aane par position wahin milti hai; rotation / recreate me bhi bachti hai (saveable).
    val photosGridState = rememberLazyGridState()
    val favoritesGridState = rememberLazyGridState()
    val trashGridState = rememberLazyGridState()
    val albumGridState = rememberSaveable(albumId, saver = LazyGridState.Saver) { LazyGridState() }
    // Filter/sort/search badalne par naye result top se dikhao: sab tabs ke states top pe (sort Settings tab se
    // bhi badalta hai, tab grid composed nahi hota, isliye yahan central). Pehli composition me kuch nahi (restore na tute).
    val gridResetFirstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(sort, filter, search) {
        if (gridResetFirstRun[0]) {
            gridResetFirstRun[0] = false
            return@LaunchedEffect
        }
        photosGridState.scrollToItem(0)
        favoritesGridState.scrollToItem(0)
        trashGridState.scrollToItem(0)
        albumGridState.scrollToItem(0)
    }
    val displayContextStale =
        displayResult.query?.tab != tab || displayResult.query?.albumId != albumId
    val currentList = if (displayContextStale) emptyList() else displayResult.items
    val currentModel = if (displayContextStale) GridModel.EMPTY else displayResult.model
    // Copy/move picker me dikhne wale albums (hidden/locked hata ke): viewer aur multi-select dono yahi use karte hain.
    val pickerAlbums = remember(state.albums, state.hiddenAlbumIds, state.lockedAlbumIds, currentList) {
        // Albums poore load na hue ho (state.albums khali) to abhi load hui list se banao.
        val base = state.albums.ifEmpty { buildAlbums(currentList) }
        base.filter {
            it.id.toString() !in state.hiddenAlbumIds && it.id.toString() !in state.lockedAlbumIds
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val needsCompleteLibrary = tab != 0 ||
        albumId != null ||
        search.isNotBlank() ||
        sort != GallerySort.DATE_NEWEST ||
        filter != MediaFilter.ALL

    LaunchedEffect(activeQuery, state.itemsVersion, state.loadingMore, state.hasMore) {
        vm.setQuery(activeQuery)
        if (needsCompleteLibrary) vm.loadAll()
    }

    // Viewer me aakhri item trash/delete hone par list khali ho jaati hai: viewer band karo, warna
    // viewerIndex >= 0 rehta hai aur bottom nav bar wapas nahi aata. Load ke dauran (temporary khali list) band nahi karte.
    LaunchedEffect(
        viewerIndex, currentList.isEmpty(), displayContextStale,
        state.loading, state.hasMore, needsCompleteLibrary,
    ) {
        if (viewerIndex >= 0 && currentList.isEmpty() && !displayContextStale &&
            !state.loading && !(needsCompleteLibrary && state.hasMore)
        ) {
            viewerIndex = -1
        }
    }

    val approvalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = approvalAction
        val denied = approvalDenied
        approvalAction = null
        approvalDenied = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
        else if (denied != null) denied()
        else notify(ctx.getString(R.string.msg_approval_denied))
    }
    // action LAST rakha hai: baaki call sites trailing lambda (requestApproval(sender) { ... }) use karte hain.
    fun requestApproval(sender: android.content.IntentSender, onDenied: (() -> Unit)? = null, action: () -> Unit) {
        approvalAction = action
        approvalDenied = onDenied
        approvalLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }
    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        val undoLabel = ctx.getString(R.string.action_undo)
        // Lagataar actions par snackbar queue na bane: purana hatao, naya dikhao.
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    /**
     * movedTo != null => ye delete "Move" ka doosra half hai (copy ho chuki): messages Move ke hisaab se aate hain
     * ("Moved to X"), aur approval deny hone par saaf bataya jaata hai ki sirf copy bani, original rakha gaya.
     */
    fun deleteMedia(
        items: List<MediaItem>,
        movedTo: String? = null,
        onMoveUndo: (() -> Unit)? = null,
        doneMessage: String? = null,
    ) {
        if (items.isEmpty()) return
        val action: () -> Unit = action@{
            if (bulk != null) return@action
            val total = items.size
            val step = maxOf(1, total / 100)
            bulkCancel.set(false)
            selected = emptySet()
            bulk = BulkProgress(ctx.getString(R.string.bulk_deleting, 0, total), 0f)
            scope.launch {
                var done = 0
                val deletedKeys = ArrayList<String>(total)
                // Delete IO thread pe: pehle poora loop main thread pe chalta tha (bahut items par UI atak jaata tha).
                val failure: Throwable? = withContext(Dispatchers.IO) {
                    runCatching {
                        for (item in items) {
                            if (bulkCancel.get()) break
                            MediaOperations.permanentlyDelete(ctx, item)
                            deletedKeys += item.key
                            done++
                            if (done % step == 0 || done == total) {
                                bulk = BulkProgress(ctx.getString(R.string.bulk_deleting, done, total), done / total.toFloat())
                            }
                        }
                    }.exceptionOrNull()
                }
                GalleryPreferences.setTrashedKeys(ctx, deletedKeys, false)
                bulk = null
                vm.refreshPreferences()
                vm.load()
                when {
                    movedTo != null && failure != null ->
                        notify(ctx.getString(R.string.msg_move_delete_failed, movedTo, friendlyError(ctx, failure)), long = true)
                    movedTo != null && done < total ->
                        notify(ctx.getString(R.string.msg_move_partial, done, total, movedTo), long = true)
                    movedTo != null -> {
                        val msg = if (total == 1) ctx.getString(R.string.msg_moved, movedTo)
                        else ctx.getString(R.string.msg_moved_n, total, movedTo)
                        // Move ke baad Undo: copies ko unke original album me wapas bhejta hai.
                        if (onMoveUndo != null) showUndoSnackbar(msg, onMoveUndo) else notify(msg)
                    }
                    failure != null -> notify(ctx.getString(R.string.msg_delete_failed, friendlyError(ctx, failure)), long = true)
                    done < total -> notify(ctx.getString(R.string.msg_delete_partial, done, total))
                    else -> notify(doneMessage ?: ctx.getString(R.string.msg_deleted))
                }
            }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val request = MediaStore.createDeleteRequest(ctx.contentResolver, items.map { it.uri })
                requestApproval(
                    request.intentSender,
                    onDenied = movedTo?.let { dest -> { notify(ctx.getString(R.string.msg_move_denied, dest), long = true) } },
                    action = action,
                )
            }.onFailure {
                if (movedTo != null) notify(ctx.getString(R.string.msg_move_denied, movedTo), long = true)
                else notify(ctx.getString(R.string.msg_delete_request_failed), long = true)
            }
        } else action()
    }
    fun renameMedia(item: MediaItem, name: String) {
        val action: () -> Unit = {
            runCatching {
                MediaOperations.rename(ctx, item, name)
                vm.load()
                notify(ctx.getString(R.string.msg_renamed))
            }.onFailure { notify(ctx.getString(R.string.msg_rename_failed, friendlyError(ctx, it)), long = true) }
            Unit
        }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                requestApproval(MediaStore.createWriteRequest(ctx.contentResolver, listOf(item.uri)).intentSender, action = action)
            }.onFailure { notify(ctx.getString(R.string.msg_write_request_failed), long = true) }
        } else action()
    }
    /**
     * Copy / move (ek ya kai items). Ek item par pehle jaisa byte-progress; kai items par "x of y" + overall bar.
     * Move = poori copy safal hone ke BAAD hi un items ke originals delete (Android approval ke saath).
     * Pehli failure / Cancel par ruk jaata hai: jo copy ho chuke wo rehte hain, originals koi delete nahi hota.
     */
    fun copyOrMoveMedia(items: List<MediaItem>, folder: String, move: Boolean, destPath: String?) {
        val todo = itemsToTransfer(items, move, destPath)
        runTransfer(todo.map { TransferJob(it, folder, destPath) }, move, folder, undoable = true)
    }
    /**
     * Asli copy/move loop. Har job ka apna destination (folder, destPath): Undo me har item apne original album me
     * wapas jaata hai. undoable = true par result snackbar me Undo aata hai (API 29+): Copy ka Undo copies delete karta
     * hai, Move ka Undo copies ko unke original album me wapas move karta hai (naam wahi rehta hai).
     */
    fun runTransfer(jobs: List<TransferJob>, move: Boolean, doneLabel: String, undoable: Boolean) {
        if (jobs.isEmpty() || bulk != null) return
        bulkCancel.set(false)
        selected = emptySet()
        val total = jobs.size
        val single = total == 1
        val singleLabel = ctx.getString(if (move) R.string.bulk_moving else R.string.bulk_copying)
        fun labelAt(n: Int) =
            if (single) singleLabel else ctx.getString(if (move) R.string.bulk_moving_n else R.string.bulk_copying_n, n, total)
        bulk = BulkProgress(labelAt(1), if (single) null else 0f)
        scope.launch(Dispatchers.IO) {
            val copied = ArrayList<MediaItem>(total)
            val made = ArrayList<Pair<MediaItem, MediaItem>>(total) // (source, destination me bani copy)
            var failure: Throwable? = null
            var cancelled = false
            for ((index, job) in jobs.withIndex()) {
                if (bulkCancel.get()) { cancelled = true; break }
                if (!single) bulk = BulkProgress(labelAt(index + 1), index / total.toFloat())
                val result = runCatching {
                    MediaOperations.copyToAlbum(
                        ctx, job.item, job.folder, job.destPath,
                        onProgress = { f ->
                            bulk = BulkProgress(
                                labelAt(index + 1),
                                if (single) f.takeIf { it >= 0f } else (index + f.coerceAtLeast(0f)) / total,
                            )
                        },
                        isCancelled = { bulkCancel.get() },
                        keepName = move, // Move = original naam; "(copy)" suffix sirf Copy me
                    )
                }
                val error = result.exceptionOrNull()
                val newUri = result.getOrNull()
                when {
                    error is MediaOperations.CopyCancelledException -> { cancelled = true; break }
                    error != null -> { failure = error; break }
                    newUri == null -> { failure = IllegalStateException("Could not create destination media"); break }
                    else -> {
                        copied += job.item
                        made += job.item to job.item.copy(uri = requireNotNull(newUri))
                    }
                }
            }
            withContext(Dispatchers.Main) {
                bulk = null
                if (copied.isNotEmpty()) vm.load()
                val completed = failure == null && !cancelled
                val canUndo = TransferRules.canUndo(Build.VERSION.SDK_INT, move, completed, undoable, made.map { it.first })
                val moveUndo: (() -> Unit)? = if (canUndo && move) {
                    {
                        val label = TransferRules.undoLabel(made.map { it.first }, ctx.getString(R.string.label_original_albums))
                        runTransfer(TransferRules.undoJobs(made), move = true, doneLabel = label, undoable = false)
                    }
                } else null
                when {
                    // Move adhoora reh gaya: originals koi nahi hata, isliye "Copied X of Y" hi sach hai.
                    !completed && copied.isNotEmpty() ->
                        notify(ctx.getString(R.string.msg_copy_partial, copied.size, total), long = true)
                    failure != null ->
                        notify(ctx.getString(if (move) R.string.msg_move_failed else R.string.msg_copy_failed, friendlyError(ctx, failure)), long = true)
                    cancelled -> notify(ctx.getString(if (move) R.string.msg_move_cancelled else R.string.msg_copy_cancelled))
                    // Move me yahan "Copied" nahi dikhate: result "Moved to X" delete ke baad deleteMedia dikhata hai.
                    move -> Unit
                    else -> {
                        val msg = if (single) ctx.getString(R.string.msg_copied, doneLabel)
                        else ctx.getString(R.string.msg_copied_n, copied.size, doneLabel)
                        if (canUndo) {
                            showUndoSnackbar(msg) {
                                deleteMedia(made.map { it.second }, doneMessage = ctx.getString(R.string.msg_copy_undone))
                            }
                        } else notify(msg)
                    }
                }
                if (move && completed && copied.isNotEmpty()) deleteMedia(copied, movedTo = doneLabel, onMoveUndo = moveUndo)
            }
        }
    }
    /**
     * Trash / restore.
     * API 30+: asli system trash (MediaStore.createTrashRequest). Files doosre apps me bhi trash hoti hain
     * aur MediaStore 30 din baad khud delete kar deta hai.
     * API < 30: system trash nahi hai, isliye app-level flag + 30 din baad auto-delete (fallback).
     */
    // Undo: trash ke baad snackbar. API 30+ pe items system trash me gaye the, wapas laane ke liye system approval
    // dobara aata hai (MANAGE_MEDIA nahi hai); API < 30 pe sirf app-level flag hatta hai, turant.
    fun undoTrash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val message = ctx.getString(R.string.msg_restored)
        if (Build.VERSION.SDK_INT < 30) {
            GalleryPreferences.setTrashedKeys(ctx, items.map { it.key }, false)
            vm.refreshPreferences()
            notify(message)
            return
        }
        runCatching {
            val request = MediaStore.createTrashRequest(ctx.contentResolver, items.map { it.uri }, false)
            requestApproval(request.intentSender) {
                vm.load()
                notify(message)
            }
        }.onFailure { notify(ctx.getString(R.string.msg_trash_request_failed), long = true) }
    }
    fun showTrashedSnackbar(items: List<MediaItem>) =
        showUndoSnackbar(ctx.getString(R.string.msg_trashed)) { undoTrash(items) }
    // Restore ka Undo: items ko wapas trash me bhejo. Split item.isTrashed se hota hai (restore se pehle ki
    // halat): API 30+ pe system-trashed items system trash me, baaki (legacy/API < 30) app-level flag me.
    fun undoRestore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val message = ctx.getString(R.string.msg_trashed)
        val systemItems = if (Build.VERSION.SDK_INT >= 30) items.filter { it.isTrashed } else emptyList()
        val systemSet = systemItems.toSet()
        val flagItems = items.filter { it !in systemSet }
        if (flagItems.isNotEmpty()) {
            GalleryPreferences.setTrashedKeys(ctx, flagItems.map { it.key }, true)
            vm.refreshPreferences()
        }
        if (systemItems.isEmpty()) {
            notify(message)
            return
        }
        runCatching {
            val request = MediaStore.createTrashRequest(ctx.contentResolver, systemItems.map { it.uri }, true)
            requestApproval(request.intentSender) {
                vm.load()
                notify(message)
            }
        }.onFailure { notify(ctx.getString(R.string.msg_trash_request_failed), long = true) }
    }
    fun showRestoredSnackbar(items: List<MediaItem>) =
        showUndoSnackbar(ctx.getString(R.string.msg_restored)) { undoRestore(items) }
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
        if (systemItems.isEmpty()) {
            if (value) showTrashedSnackbar(items) else showRestoredSnackbar(items)
            return
        }
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val request = MediaStore.createTrashRequest(ctx.contentResolver, systemItems.map { it.uri }, value)
            requestApproval(request.intentSender) {
                vm.load()
                if (value) showTrashedSnackbar(items) else showRestoredSnackbar(items)
            }
        }.onFailure { notify(ctx.getString(R.string.msg_trash_request_failed), long = true) }
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
                notify(ctx.getString(R.string.msg_trash_migrated))
            }
        }.onFailure { notify(ctx.getString(R.string.msg_trash_request_failed), long = true) }
    }

    fun hideAlbum(id: Long) {
        GalleryPreferences.setAlbumHidden(ctx, id, true)
        vm.refreshPreferences()
        if (albumId == id) albumId = null
        notify(ctx.getString(R.string.msg_album_hidden), long = true)
    }
    fun unhideAlbum(id: Long) {
        GalleryPreferences.setAlbumHidden(ctx, id, false)
        vm.refreshPreferences()
        notify(ctx.getString(R.string.msg_album_unhidden))
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
        notify(msg)
    }
    fun unlockAlbum(id: Long) {
        val done = {
            GalleryPreferences.setAlbumLocked(ctx, id, false)
            vm.refreshPreferences()
            notify(ctx.getString(R.string.msg_lock_removed))
        }
        val activity = ctx.findActivity()
        // Removing a lock needs the same authentication as opening; without any device lock there is nothing to protect.
        if (!canAuthenticateAlbum(activity)) done()
        else authenticateAlbum(activity, done) {
            notify(ctx.getString(R.string.msg_lock_remove_auth_failed))
        }
    }

    val inAlbum = tab == 1 && albumId != null
    val title = when {
        tab == 4 -> stringResource(
            when (settingsPage) { 1 -> R.string.hidden_albums; 2 -> R.string.locked_albums; else -> R.string.nav_settings }
        )
        inAlbum -> state.albums.firstOrNull { it.id == albumId }?.name ?: stringResource(R.string.title_album_fallback)
        selected.isNotEmpty() -> stringResource(R.string.selected_count, selected.size)
        pick != null && tab == 0 -> stringResource(R.string.pick_title)
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

    // Left/right swipe se tab badalna: sirf top-level list screens par, selection/search/album/settings sub-page/picker me nahi.
    val switchTab: (Int) -> Unit = { index ->
        tab = index
        albumId = null
        settingsPage = 0
        if (index != 0) searchOpen = false
    }
    val swipeTabsEnabled = granted && pick == null && viewerIndex < 0 && selected.isEmpty() &&
        albumId == null && !searchOpen && settingsPage == 0 && bulk == null

    // Tablet / landscape (>= 600dp): neeche ke pill ki jagah side Navigation Rail.
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 600
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // Rail/pill viewer ke peeche bhi composed rehte hain (viewer unhe dhak leta hai): nahi to open pe gayab
            // aur close ke baad achanak wapas bante the (jhatka).
            if (wideLayout) {
                GalleryNavRail(
                    tab = tab,
                    onSelect = { index ->
                        tab = index
                        albumId = null
                        settingsPage = 0
                        if (index != 0) searchOpen = false
                    },
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Rail ke saath side ka system inset dobara na lage.
                    .then(
                        if (wideLayout) {
                            Modifier.consumeWindowInsets(
                                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Start),
                            )
                        } else Modifier,
                    ),
            ) {
        Scaffold(
            // Viewer khula ho to snackbar neeche root-level host se dikhta hai (Scaffold viewer ke peeche hai).
            snackbarHost = { if (viewerIndex < 0) SnackbarHost(snackbarHostState) },
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
                                tick()
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
                if (viewerIndex < 0 && selected.isNotEmpty() && pick != null) {
                    // Picker mode: trash/delete actions nahi, sirf "Done".
                    Surface(
                        modifier = Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Button(
                            onClick = { finishPick(currentList.filter { it.key in selected }) },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        ) { Text(stringResource(R.string.pick_done, selected.size)) }
                    }
                } else if (viewerIndex < 0 && selected.isNotEmpty()) {
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
                        onCopy = { copyTargets = picked },
                        onMove = { moveTargets = picked },
                        onTrashOrRestore = {
                            selected = emptySet()
                            trashMedia(picked, tab != 3)
                        },
                        onDelete = { deleteMedia(picked) },
                    )
                } else if (!wideLayout) {
                    Surface(
                        modifier = Modifier
                            .navigationBarsPadding()
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
                            // System nav bar ka inset pill ke bahar (Surface pe) lagta hai, andar nahi; warna pill neeche tak lambi ho jaati thi.
                            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
        ) { scaffoldPadding ->
            // Filter laga ho to grid ke upar "Filter: Videos ✕" chip ki patti dikhti hai; grid/empty state uske neeche shuru hote hain.
            val chipHeight = 48.dp
            val showFilterChip = pick == null && granted && filter != MediaFilter.ALL &&
                tab != 4 && !(tab == 1 && !inAlbum)
            val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
            val padding = if (showFilterChip) {
                PaddingValues(
                    start = scaffoldPadding.calculateStartPadding(layoutDir),
                    top = scaffoldPadding.calculateTopPadding() + chipHeight,
                    end = scaffoldPadding.calculateEndPadding(layoutDir),
                    bottom = scaffoldPadding.calculateBottomPadding(),
                )
            } else scaffoldPadding
            TabSwipeContainer(
                tab = tab,
                tabCount = 5,
                enabled = swipeTabsEnabled,
                onTabChange = switchTab,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !granted -> PermissionScreen(scaffoldPadding) { permissionLauncher.launch(mediaPermissions()) }
                    state.loading -> SkeletonGrid(columns, padding)
                    // System trash ki query abhi baaki: "Trash khali" ka galat flash nahi, skeleton.
                    tab == 3 && !state.trashLoaded -> SkeletonGrid(columns, padding)
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
                        sort = sort,
                        onSort = { sort = it },
                        slideshowMs = slideshowMs,
                        onSlideshowMs = { slideshowMs = it; GalleryPreferences.setSlideshowDelayMs(ctx, it) },
                        videoAutoplay = videoAutoplay,
                        onVideoAutoplay = { videoAutoplay = it; GalleryPreferences.setVideoAutoplay(ctx, it) },
                        videoMuted = videoMuted,
                        onVideoMuted = { videoMuted = it; GalleryPreferences.setVideoMuted(ctx, it) },
                        haptics = haptics,
                        onHaptics = onHaptics,
                        trashCount = trashedKeys.size,
                        onOpenTrash = { tab = 3; albumId = null; settingsPage = 0; searchOpen = false },
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
                                        notify(ctx.getString(R.string.msg_album_auth_failed))
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
                    else -> RefreshableBox(
                        refreshing = refreshing,
                        onRefresh = vm::refresh,
                        topPadding = padding.calculateTopPadding(),
                    ) { MediaGrid(
                        items = currentList,
                        padding = padding,
                        selected = selected,
                        columns = columns,
                        favoriteKeys = state.favoriteKeys,
                        flingFriction = if (tab == 0) 0.007f else 0.015f,
                        model = currentModel,
                        gridState = when {
                            albumId != null -> albumGridState
                            tab == 2 -> favoritesGridState
                            tab == 3 -> trashGridState
                            else -> photosGridState
                        },
                        sort = sort,
                        onScrubStart = vm::loadAll,
                        originLookup = gridOriginLookup,
                        onOpen = { index, rect ->
                            // Cache wali (stale ho sakti) list: asli refresh hone tak open/pick nahi.
                            if (state.fromCache) return@MediaGrid
                            if (pick != null) {
                                // Picker: single me turant wapas, multiple me pehla item select (phir tap se toggle).
                                val item = currentList.getOrNull(index)
                                if (item != null) {
                                    if (pick.multiple) selected = setOf(item.key) else finishPick(listOf(item))
                                }
                            } else {
                                viewerOrigin = rect
                                viewerOriginIndex = index
                                viewerIndex = index
                            }
                        },
                        onToggleSelection = { item ->
                            if (state.fromCache) return@MediaGrid
                            if (pick != null && !pick.multiple) {
                                finishPick(listOf(item))
                            } else {
                                tick()
                                selected = if (item.key in selected) selected - item.key else selected + item.key
                            }
                        },
                        onSetSelection = { if (!state.fromCache && (pick == null || pick.multiple)) selected = it },
                        onPinchColumns = { next ->
                            columns = next.coerceIn(2, 8)
                            GalleryPreferences.setColumns(ctx, columns)
                        },
                        onLoadMore = vm::loadNextPage,
                        daySelectEnabled = !state.fromCache && (pick == null || pick.multiple),
                    ) }
                }
            if (showFilterChip) {
                val filterLabel = stringResource(
                    when (filter) {
                        MediaFilter.PHOTOS -> R.string.filter_photos
                        MediaFilter.VIDEOS -> R.string.filter_videos
                        MediaFilter.GIFS -> R.string.filter_gifs
                        MediaFilter.RAW -> R.string.filter_raw
                        MediaFilter.ALL -> R.string.filter_all
                    },
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = scaffoldPadding.calculateTopPadding())
                        .fillMaxWidth()
                        .height(chipHeight),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InputChip(
                            selected = true,
                            onClick = { filter = MediaFilter.ALL },
                            label = { Text(stringResource(R.string.filter_active_chip, filterLabel)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_clear_filter),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
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
                origin = viewerOrigin,
                originIndex = viewerOriginIndex,
                originLookup = gridOriginLookup,
                onFavorite = { tick(); GalleryPreferences.toggleFavorite(ctx, it); vm.refreshPreferences() },
                onSetTrashed = { item, value -> trashMedia(listOf(item), value) },
                onDelete = { deleteMedia(listOf(it)) },
                onRename = ::renameMedia,
                albums = pickerAlbums,
                onCopyOrMove = { item, folder, move, destPath -> copyOrMoveMedia(listOf(item), folder, move, destPath) },
                onWallpaper = { item ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.setWallpaper(ctx, item) }
                        withContext(Dispatchers.Main) {
                            notify(if (result.isSuccess) ctx.getString(R.string.msg_wallpaper_set) else ctx.getString(R.string.msg_wallpaper_failed, friendlyError(ctx, result.exceptionOrNull())), long = true)
                        }
                    }
                },
                onEdit = { item, edit ->
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { MediaOperations.saveEditedCopy(ctx, item, edit) }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess && result.getOrNull() != null) {
                                notify(ctx.getString(R.string.msg_edit_saved))
                                vm.load()
                            } else notify(ctx.getString(R.string.msg_edit_failed, friendlyError(ctx, result.exceptionOrNull(), R.string.err_unsupported)), long = true)
                        }
                    }
                },
            )
        }
        copyTargets?.let { targets ->
            AlbumPickerSheet(
                sources = targets,
                move = false,
                albums = pickerAlbums,
                onDismiss = { copyTargets = null },
                onPick = { name, path, _ -> copyOrMoveMedia(targets, name, false, path); copyTargets = null },
            )
        }
        moveTargets?.let { targets ->
            AlbumPickerSheet(
                sources = targets,
                move = true,
                albums = pickerAlbums,
                onDismiss = { moveTargets = null },
                onPick = { name, path, _ -> copyOrMoveMedia(targets, name, true, path); moveTargets = null },
            )
        }
        BulkProgressBar(
            progress = bulk,
            onCancel = { bulkCancel.set(true) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (viewerIndex >= 0) 72.dp else 88.dp),
        )
        if (viewerIndex >= 0) {
            // Viewer ke bottom bar ke upar.
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp),
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

@Composable
private fun GalleryNavRail(tab: Int, onSelect: (Int) -> Unit) {
    val destinations = listOf(
        stringResource(R.string.nav_photos),
        stringResource(R.string.nav_albums),
        stringResource(R.string.nav_favorites),
        stringResource(R.string.nav_trash),
        stringResource(R.string.nav_settings),
    )
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.weight(1f))
        destinations.forEachIndexed { index, label ->
            val isSelected = tab == index
            val tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(index) },
                icon = {
                    when (index) {
                        0 -> GridTabGlyph(tint, 1f)
                        1 -> FolderTabGlyph(tint, 1f)
                        else -> Icon(
                            imageVector = when (index) {
                                2 -> Icons.Filled.Favorite
                                3 -> Icons.Filled.Delete
                                else -> Icons.Filled.Settings
                            },
                            contentDescription = label,
                        )
                    }
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = true,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
