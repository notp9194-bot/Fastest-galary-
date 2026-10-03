@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fastgallery.app.ui

import androidx.compose.animation.AnimatedVisibility
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.fastgallery.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.data.ImageEdit
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaOperations
import com.fastgallery.app.data.isRaw
import com.fastgallery.app.findActivity
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(
    items: List<MediaItem>,
    startIndex: Int,
    favoriteKeys: Set<String>,
    trashedKeys: Set<String>,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    onFavorite: (MediaItem) -> Unit,
    onSetTrashed: (MediaItem, Boolean) -> Unit,
    onDelete: (MediaItem) -> Unit,
    onRename: (MediaItem, String) -> Unit,
    onCopyOrMove: (MediaItem, String, Boolean, String?) -> Unit,
    /** Copy/move picker me dikhne wale albums (hidden/locked hata ke). Khali ho to sirf "New album" dikhega. */
    albums: List<Album> = emptyList(),
    onWallpaper: (MediaItem) -> Unit,
    onEdit: (MediaItem, ImageEdit) -> Unit,
    /** Grid me tap hui thumbnail ki window-bounds + uska index: open/close transition isi se chalta hai. */
    origin: Rect? = null,
    originIndex: Int = -1,
    /** Close ke waqt doosri photo pe swipe karne ke baad bhi uski thumbnail pe shrink karne ke liye. */
    originLookup: GridOriginLookup? = null,
    /** true = bahar ki URI (Open with): sirf dekhna/zoom/details/share; edit-trash-rename-slideshow nahi. */
    readOnly: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val loadMore by rememberUpdatedState(onLoadMore)
    var chrome by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<MediaItem?>(null) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var copyTarget by remember { mutableStateOf<MediaItem?>(null) }
    var editTarget by remember { mutableStateOf<MediaItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moreMenu by remember { mutableStateOf(false) }

    // System bars: open animation ke baad chhupte hain, close animation SHURU hote hi wapas aate hain.
    // (Pehle open pe turant chhupte aur close khatam hone ke baad aate the, isse peeche ka screen jhatke se relayout hota tha.)
    val barsController = remember(ctx) {
        ctx.findActivity()?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    DisposableEffect(barsController) {
        barsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Viewer kisi aur raaste se bhi hat sake (jaise list khali) to bars wapas aa jayen.
        onDispose {
            barsController?.show(WindowInsetsCompat.Type.systemBars())
            // Video me swipe se badli brightness Viewer band hote hi wapas (system/auto).
            VideoBrightness.restore(ctx.findActivity())
        }
    }
    LaunchedEffect(slideshow, items.size) {
        while (slideshow && items.isNotEmpty()) {
            delay(GalleryPreferences.slideshowDelayMs(ctx).toLong())
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    LaunchedEffect(pager.currentPage, items.size) {
        if (items.isNotEmpty() && pager.currentPage >= items.size - 3) loadMore()
    }
    // Neighbour preload: low-RAM device pe band (wahan purana behaviour: neighbours 512 px).
    val preloadFull = remember(ctx) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice != true
    }

    // Open/close transition: thumbnail ki jagah se full screen tak (aur wapas). enter 0 = thumbnail, 1 = full.
    val enter = remember { Animatable(if (origin != null) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // Ruk jaane (settled) ke baad agli aur pichhli photo ko full size me memory cache me daal do. Swipe pe wo
    // page cache-hit se turant sharp dikhta hai (pehle neighbours 512 px pe aate the aur fir full decode hota tha).
    // Fling ke dauran settledPage nahi badalta, isliye beech ki photos ke liye faltu decode nahi chalte.
    LaunchedEffect(pager.settledPage, rootSize, items.size, preloadFull) {
        if (!preloadFull || rootSize.width <= 0 || rootSize.height <= 0) return@LaunchedEffect
        val size = viewerDecodeSize(rootSize.width, rootSize.height)
        val loader = ctx.imageLoader
        for (index in intArrayOf(pager.settledPage + 1, pager.settledPage - 1)) {
            val neighbour = items.getOrNull(index) ?: continue
            if (neighbour.isVideo) continue
            loader.enqueue(viewerImageRequest(ctx, neighbour.uri, size))
        }
    }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) enter.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        if (!closing) barsController?.hide(WindowInsetsCompat.Type.systemBars())
    }
    // Transition ki thumbnail: open pe tap hui thumbnail; close pe jis photo pe ho uski (requestClose me set hoti hai).
    // enter == 1 pe rect ka asar nahi hota, isliye close se pehle badalna safe hai.
    var animRect by remember { mutableStateOf(origin) }
    // Grid thumbnail center-crop (square) hota hai, viewer photo Fit. Transition me photo ko crop se fit tak scale karte hain
    // (aspect = photo ka w/h; 0 = pata nahi / video => koi correction nahi).
    var animAspect by remember {
        mutableFloatStateOf(
            if (origin == null) 0f
            else items.getOrNull(pager.currentPage)?.takeIf { !it.isVideo }?.let { cachedAspect(ctx, it.uri) } ?: 0f
        )
    }
    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            // Bars abhi laao: relayout viewer ke opaque rehte hue hota hai, close animation ke baad nahi.
            barsController?.show(WindowInsetsCompat.Type.systemBars())
            scope.launch {
                val page = pager.currentPage
                // Jis photo se khola usi pe ho to saved rect; warna grid se us photo ki thumbnail (zaroorat ho to scroll karke).
                animRect = if (page == originIndex && origin != null) origin
                else withTimeoutOrNull(500) { originLookup?.rectFor?.invoke(page) }
                animAspect = if (animRect == null) 0f
                else items.getOrNull(page)?.takeIf { !it.isVideo }?.let { cachedAspect(ctx, it.uri) } ?: 0f
                enter.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                onClose()
            }
        }
    }
    BackHandler(enabled = true) { requestClose() }

    val current = items.getOrNull(pager.currentPage)
    val chromeAlpha: GraphicsLayerScope.() -> Unit = {
        alpha = ((enter.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
    }
    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Color.Black))
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = enter.value
                    val w = rootSize.width.toFloat()
                    val h = rootSize.height.toFloat()
                    val r = animRect
                    if (r != null && w > 0f && h > 0f) {
                        val s0 = max(r.width / w, r.height / h)
                        val sc = s0 + (1f - s0) * p
                        scaleX = sc
                        scaleY = sc
                        translationX = (r.center.x - w / 2f) * (1f - p)
                        translationY = (r.center.y - h / 2f) * (1f - p)
                    } else {
                        val sc = 0.94f + 0.06f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = p
                    }
                }
                .drawWithContent {
                    val p = enter.value
                    val r = animRect
                    if (r != null && p < 1f && size.width > 0f && size.height > 0f) {
                        val s0 = max(r.width / size.width, r.height / size.height)
                        val sc = s0 + (1f - s0) * p
                        val cw = (r.width + (size.width - r.width) * p) / sc
                        val ch = (r.height + (size.height - r.height) * p) / sc
                        val left = (size.width - cw) / 2f
                        val top = (size.height - ch) / 2f
                        clipRect(left, top, left + cw, top + ch) { this@drawWithContent.drawContent() }
                    } else drawContent()
                },
        ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Crop -> fit: shuru me photo thumbnail ki tarah rect ko poora bhare, p = 1 tak normal Fit.
                val p = enter.value
                val r = animRect
                val a = animAspect
                val w = rootSize.width.toFloat()
                val h = rootSize.height.toFloat()
                if (r != null && a > 0f && p < 1f && w > 0f && h > 0f) {
                    val s0 = max(r.width / w, r.height / h)
                    val fitH = min(w / a, h)
                    val coverH = max(r.width / a, r.height)
                    val k0 = coverH / (fitH * s0)
                    val k = k0 + (1f - k0) * p
                    scaleX = k
                    scaleY = k
                }
            },
            beyondViewportPageCount = 0,
            key = { items[it].key },
        ) { page ->
            ViewerPage(
                item = items[page],
                isCurrent = page == pager.currentPage,
                fullSizeAlways = preloadFull,
                // Jis thumbnail se viewer khula uski grid-size entry memory cache me hai: placeholder turant aayega.
                thumbSizePx = if (page == originIndex && lastGridThumbPx > 0) lastGridThumbPx else 512,
                useThumbPlaceholder = !readOnly,
                onTap = { chrome = !chrome },
                chrome = chrome,
                onHideChrome = { chrome = false },
                onDismiss = requestClose,
                onSwipeUp = { details = items[page] },
            )
        }
        }
        AnimatedVisibility(chrome && !PipController.inPip, modifier = Modifier.align(Alignment.TopStart).graphicsLayer(block = chromeAlpha)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = requestClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
                }
                Text(
                    current?.name ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!readOnly) {
                IconButton(onClick = { slideshow = !slideshow }) {
                    Icon(
                        if (slideshow) PauseIcon else Icons.Filled.PlayArrow,
                        stringResource(if (slideshow) R.string.action_slideshow_stop else R.string.action_slideshow_start),
                        tint = Color.White,
                    )
                }
                }
                IconButton(onClick = { current?.let { shareItem(ctx, it) } }) {
                    Icon(Icons.Filled.Share, stringResource(R.string.action_share), tint = Color.White)
                }
            }
        }
        if (!readOnly) AnimatedVisibility(chrome && !PipController.inPip, modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer(block = chromeAlpha)) {
            Row(
                // Videos me player ke controls (seek bar etc.) is bar ke upar aate hain.
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                current?.let { item ->
                    val isFavorite = item.key in favoriteKeys
                    val isTrashed = item.key in trashedKeys
                    IconButton(onClick = { onFavorite(item) }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            stringResource(if (isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add),
                            tint = if (isFavorite) Color(0xFFFF6B81) else Color.White,
                        )
                    }
                    if (!item.isVideo) {
                        IconButton(onClick = { editTarget = item }) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit), tint = Color.White)
                        }
                    }
                    IconButton(onClick = { onSetTrashed(item, !isTrashed) }) {
                        Icon(
                            if (isTrashed) Icons.Filled.Refresh else Icons.Filled.Delete,
                            stringResource(if (isTrashed) R.string.action_restore else R.string.action_trash),
                            tint = Color.White,
                        )
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), tint = Color.White)
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                onClick = {
                                    moreMenu = false
                                    renameText = item.name
                                    renameTarget = item
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_copy_move)) },
                                onClick = {
                                    moreMenu = false
                                    copyTarget = item
                                },
                            )
                            if (!item.isVideo) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_wallpaper)) },
                                    onClick = { moreMenu = false; onWallpaper(item) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_details)) },
                                onClick = { moreMenu = false; details = item },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete_permanently)) },
                                onClick = { moreMenu = false; onDelete(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    details?.let { item ->
        val rows = remember(item.key) { MediaOperations.exifDetails(ctx, item) }
        ModalBottomSheet(onDismissRequest = { details = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
            ) {
                Text(
                    stringResource(R.string.details_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.4f).padding(end = 12.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.rename_file_name)) }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(item, renameText); renameTarget = null }) { Text(stringResource(R.string.rename_confirm)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    copyTarget?.let { item ->
        AlbumPickerSheet(
            source = item,
            albums = albums,
            onDismiss = { copyTarget = null },
            onPick = { name, path, move -> onCopyOrMove(item, name, move, path); copyTarget = null },
        )
    }
    editTarget?.let { item -> EditDialog(item, onDismiss = { editTarget = null }) { edit ->
        onEdit(item, edit)
        editTarget = null
    } }
}

/**
 * Memory cache me pade thumbnail (grid / viewer placeholder) se photo ka dikhne wala aspect ratio (w/h).
 * Thumbnail me EXIF rotation pehle se lagi hoti hai, isliye MediaStore ke width/height se behtar hai.
 * Na mile (ya API < 29) to 0 = pata nahi; tab transition me crop-to-fit correction nahi lagta.
 */
internal fun cachedAspect(ctx: Context, uri: Uri): Float {
    if (Build.VERSION.SDK_INT < 29) return 0f
    val cache = ctx.imageLoader.memoryCache ?: return 0f
    for (size in intArrayOf(lastGridThumbPx, 512)) {
        if (size <= 0) continue
        val bitmap = cache[MemoryCache.Key("thumb:$uri:$size")]?.bitmap ?: continue
        if (bitmap.width > 0 && bitmap.height > 0) return bitmap.width.toFloat() / bitmap.height
    }
    return 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerPage(
    item: MediaItem,
    isCurrent: Boolean,
    fullSizeAlways: Boolean,
    thumbSizePx: Int,
    useThumbPlaceholder: Boolean,
    onTap: () -> Unit,
    chrome: Boolean,
    onHideChrome: () -> Unit,
    onDismiss: () -> Unit,
    onSwipeUp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scale by remember(item.key) { mutableFloatStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }
    var box by remember(item.key) { mutableStateOf(IntSize.Zero) }
    var dismissOffset by remember(item.key) { mutableFloatStateOf(0f) }
    var zoomJob by remember(item.key) { mutableStateOf<Job?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    // Video gestures (double-tap seek, long-press 2x, brightness/volume): VideoPlayer apne lambdas yahan register karta hai.
    val videoGestures = remember(item.key) { VideoGestureHandler() }
    // Current (ya preload on ho to har) page full size pe; low-RAM pe neighbours halke (512) rehte hain.
    val decodeSize = if (isCurrent || fullSizeAlways) viewerDecodeSize(box.width, box.height) else 512
    // Thumbnail placeholder tab tak dikhta hai jab tak full image aakar crossfade na kar le.
    var showThumb by remember(item.key) { mutableStateOf(useThumbPlaceholder && !item.isVideo) }
    var fullLoaded by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(fullLoaded) {
        if (fullLoaded) {
            delay(220) // crossfade (160 ms) poora hone do, tabhi thumb hatao (transparent PNG ke neeche na dikhe)
            showThumb = false
        }
    }
    val density = LocalDensity.current
    val closeThreshold = with(density) { 120.dp.toPx() }
    val swipeUpThreshold = with(density) { 72.dp.toPx() }

    fun clampOffset(o: Offset, s: Float): Offset {
        if (s <= 1f) return Offset.Zero
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val settle: () -> Unit = {
        scope.launch {
            animate(dismissOffset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { v, _ -> dismissOffset = v }
        }
    }
    // Pinch ka focal point (ungliyon ka beech): transformable callback me centroid nahi aata,
    // isliye neeche ka observer (Initial pass, kuch consume nahi karta) ise yahan rakhta hai.
    val pinchFocal = remember(item.key) { floatArrayOf(Float.NaN, Float.NaN) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        zoomJob?.cancel()
        val newScale = (scale * zoom).coerceIn(1f, 6f)
        val z = newScale / scale
        // Ungliyon ke neeche ka content apni jagah rahe: o' = (focal - c)(1 - z) + z*o + pan.
        val center = Offset(box.width / 2f, box.height / 2f)
        val focal = if (pinchFocal[0].isNaN()) center else Offset(pinchFocal[0], pinchFocal[1])
        val next = if (z != 1f) (focal - center) * (1f - z) + offset * z + pan else offset + pan
        scale = newScale
        offset = clampOffset(next, newScale)
    }
    var modifier = Modifier.fillMaxSize()
        .pointerInput(item.key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val active = event.changes.filter { it.pressed }
                    if (active.size >= 2) {
                        pinchFocal[0] = active.map { it.position.x }.average().toFloat()
                        pinchFocal[1] = active.map { it.position.y }.average().toFloat()
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(item.key, scale) {
            if (scale <= 1.01f) {
                // Neeche kheencho = band; upar kheencho = details sheet. Dono me chhoda to wapas spring.
                // Video me left/right side-zone ka vertical swipe iski jagah brightness/volume badalta hai
                // (beech ka zone pehle jaisa: close / details).
                var adjust: VideoAdjust? = null
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        adjust = if (item.isVideo) adjustZoneAt(start.x, size.width.toFloat()) else null
                        adjust?.let { videoGestures.adjustStart(it) }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val kind = adjust
                        if (kind != null) {
                            videoGestures.adjustBy(kind, dragAmount, size.height.toFloat())
                        } else {
                            dismissOffset = (dismissOffset + dragAmount)
                                .coerceIn(-closeThreshold, size.height.toFloat())
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (adjust != null) {
                            adjust = null
                            videoGestures.adjustEnd()
                        } else when {
                            dismissOffset >= closeThreshold -> currentOnDismiss()
                            dismissOffset <= -swipeUpThreshold -> { currentOnSwipeUp(); settle() }
                            else -> settle()
                        }
                    },
                    onDragCancel = {
                        if (adjust != null) {
                            adjust = null
                            videoGestures.adjustEnd()
                        } else settle()
                    },
                )
            }
        }
        .onSizeChanged { box = it }
        .pointerInput(item.key) {
            detectTapGestures(
                // Video: double-tap seek ke turant baad ke single taps bhi seek karte hain; baaki tap chrome toggle.
                onTap = { tap ->
                    if (!(item.isVideo && videoGestures.tapAsSeek(tap.x, size.width.toFloat()))) onTap()
                },
                // Video me long-press = 2x (chhodte hi band). Photo me long-press pehle jaisa (None) rehta hai.
                onLongPress = if (item.isVideo) ({ _: Offset -> videoGestures.hold(true) }) else null,
                onPress = { if (item.isVideo) { tryAwaitRelease(); videoGestures.hold(false) } },
                onDoubleTap = { tap ->
                if (item.isVideo) {
                    videoGestures.doubleTap(tap.x, size.width.toFloat())
                } else {
                    // Double-tap: tap wali jagah pe smooth zoom-in (2.5x); dobara double-tap pe smooth zoom-out.
                    zoomJob?.cancel()
                    val fromScale = scale
                    val fromOffset = offset
                    val zoomIn = scale <= 1.05f
                    val toScale = if (zoomIn) 2.5f else 1f
                    val toOffset = if (zoomIn) {
                        val center = Offset(box.width / 2f, box.height / 2f)
                        clampOffset((center - tap) * (toScale - 1f), toScale)
                    } else Offset.Zero
                    zoomJob = scope.launch {
                        animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { f, _ ->
                            scale = fromScale + (toScale - fromScale) * f
                            offset = fromOffset + (toOffset - fromOffset) * f
                        }
                    }
                }
            },
            )
        }
    if (!item.isVideo) modifier = modifier.transformable(transformState, canPan = { scale > 1f })
    Box(modifier.graphicsLayer {
        // Upar kheenchte waqt halka damping; neeche kheenchte waqt fade (band hone ka sanket).
        translationY = if (dismissOffset < 0f) dismissOffset * 0.5f else dismissOffset
        val height = size.height.coerceAtLeast(1f)
        alpha = 1f - (dismissOffset.coerceAtLeast(0f) / height).coerceIn(0f, 0.65f)
    }) {
        if (item.isVideo) {
            VideoPlayer(item, isCurrent = isCurrent, controlsVisible = chrome, onHideControls = onHideChrome, gestures = videoGestures)
        } else {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
            ) {
                if (showThumb) {
                    AsyncImage(
                        model = rememberThumbRequest(item.uri, thumbSizePx),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Page ka size pata hone se pehle full decode shuru nahi karte (pehle 512 px pe ek bekaar decode hota tha).
                if (box.width > 0 && box.height > 0) {
                    AsyncImage(
                        model = rememberViewerRequest(item.uri, decodeSize),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { fullLoaded = true },
                    )
                }
            }
            if (item.isRaw()) {
                Text(
                    stringResource(R.string.badge_raw),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
