package com.fastgallery.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import kotlin.math.max

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
    onCopyOrMove: (MediaItem, String, Boolean) -> Unit,
    onWallpaper: (MediaItem) -> Unit,
    onEdit: (MediaItem, ImageEdit) -> Unit,
    /** Grid me tap hui thumbnail ki window-bounds + uska index: open/close transition isi se chalta hai. */
    origin: Rect? = null,
    originIndex: Int = -1,
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
    var folderText by remember { mutableStateOf("") }
    var moveAfterCopy by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = ctx.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(slideshow, items.size) {
        while (slideshow && items.isNotEmpty()) {
            delay(2800)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    LaunchedEffect(pager.currentPage, items.size) {
        if (items.isNotEmpty() && pager.currentPage >= items.size - 3) loadMore()
    }

    // Open/close transition: thumbnail ki jagah se full screen tak (aur wapas). enter 0 = thumbnail, 1 = full.
    val enter = remember { Animatable(if (origin != null) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) enter.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                enter.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                onClose()
            }
        }
    }
    BackHandler(enabled = true) { requestClose() }

    val current = items.getOrNull(pager.currentPage)
    // Thumbnail sirf tabhi match hota hai jab user abhi bhi usi photo pe ho jis se khola tha.
    val animRect = if (pager.currentPage == originIndex) origin else null
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
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { items[it].key },
        ) { page ->
            ViewerPage(
                item = items[page],
                isCurrent = page == pager.currentPage,
                onTap = { chrome = !chrome },
                chrome = chrome,
                onHideChrome = { chrome = false },
                onDismiss = requestClose,
                onSwipeUp = { details = items[page] },
            )
        }
        }
        AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.TopStart).graphicsLayer(block = chromeAlpha)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
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
                IconButton(onClick = { slideshow = !slideshow }) {
                    Icon(
                        if (slideshow) PauseIcon else Icons.Filled.PlayArrow,
                        stringResource(if (slideshow) R.string.action_slideshow_stop else R.string.action_slideshow_start),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { current?.let { shareItem(ctx, it) } }) {
                    Icon(Icons.Filled.Share, stringResource(R.string.action_share), tint = Color.White)
                }
            }
        }
        AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer(block = chromeAlpha)) {
            Row(
                // Videos me player ke controls (seek bar etc.) is bar ke upar aate hain.
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
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
                                    folderText = item.bucketName
                                    moveAfterCopy = false
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
                    .navigationBarsPadding(),
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
        AlertDialog(
            onDismissRequest = { copyTarget = null },
            title = { Text(stringResource(R.string.copy_title)) },
            text = {
                Column {
                    OutlinedTextField(value = folderText, onValueChange = { folderText = it }, label = { Text(stringResource(R.string.copy_folder_name)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.copy_delete_original))
                        Switch(checked = moveAfterCopy, onCheckedChange = { moveAfterCopy = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onCopyOrMove(item, folderText, moveAfterCopy); copyTarget = null }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { copyTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    editTarget?.let { item -> EditDialog(item, onDismiss = { editTarget = null }) { edit ->
        onEdit(item, edit)
        editTarget = null
    } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerPage(
    item: MediaItem,
    isCurrent: Boolean,
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
    val decodeSize = if (isCurrent) {
        (maxOf(box.width, box.height) * 1.5f).toInt().coerceIn(512, 2560)
    } else {
        512
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
    val transformState = rememberTransformableState { zoom, pan, _ ->
        zoomJob?.cancel()
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = clampOffset(offset + pan, scale)
    }
    var modifier = Modifier.fillMaxSize()
        .pointerInput(item.key, scale) {
            if (scale <= 1.01f) {
                // Neeche kheencho = band; upar kheencho = details sheet. Dono me chhoda to wapas spring.
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        dismissOffset = (dismissOffset + dragAmount)
                            .coerceIn(-closeThreshold, size.height.toFloat())
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            dismissOffset >= closeThreshold -> currentOnDismiss()
                            dismissOffset <= -swipeUpThreshold -> { currentOnSwipeUp(); settle() }
                            else -> settle()
                        }
                    },
                    onDragCancel = { settle() },
                )
            }
        }
        .onSizeChanged { box = it }
        .pointerInput(item.key) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { tap ->
                if (!item.isVideo) {
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
            })
        }
    if (!item.isVideo) modifier = modifier.transformable(transformState, canPan = { scale > 1f })
    Box(modifier.graphicsLayer {
        // Upar kheenchte waqt halka damping; neeche kheenchte waqt fade (band hone ka sanket).
        translationY = if (dismissOffset < 0f) dismissOffset * 0.5f else dismissOffset
        val height = size.height.coerceAtLeast(1f)
        alpha = 1f - (dismissOffset.coerceAtLeast(0f) / height).coerceIn(0f, 0.65f)
    }) {
        if (item.isVideo) {
            VideoPlayer(item, isCurrent = isCurrent, controlsVisible = chrome, onHideControls = onHideChrome)
        } else {
            AsyncImage(
                model = rememberImageRequest(item.uri, decodeSize),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
            )
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
