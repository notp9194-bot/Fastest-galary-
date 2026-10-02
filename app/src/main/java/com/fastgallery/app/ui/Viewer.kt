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
) {
    val ctx = LocalContext.current
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

    val current = items.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                onDismiss = onClose,
            )
        }
        AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.TopStart)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
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
        AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter)) {
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
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(stringResource(R.string.details_title)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    rows.forEach { (label, value) ->
                        Text("$label: $value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.action_close)) } },
        )
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
) {
    var scale by remember(item.key) { mutableFloatStateOf(1f) }
    var offset by remember(item.key) { mutableStateOf(Offset.Zero) }
    var box by remember(item.key) { mutableStateOf(IntSize.Zero) }
    var dismissOffset by remember(item.key) { mutableFloatStateOf(0f) }
    val decodeSize = if (isCurrent) {
        (maxOf(box.width, box.height) * 1.5f).toInt().coerceIn(512, 2560)
    } else {
        512
    }
    val closeThreshold = with(LocalDensity.current) { 120.dp.toPx() }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        val maxX = box.width * (scale - 1f) / 2f
        val maxY = box.height * (scale - 1f) / 2f
        offset = if (scale <= 1f) Offset.Zero else Offset(
            (offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY),
        )
    }
    var modifier = Modifier.fillMaxSize()
        .pointerInput(item.key, scale, onDismiss) {
            if (scale <= 1.01f) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        dismissOffset = (dismissOffset + dragAmount)
                            .coerceIn(-size.height.toFloat(), size.height.toFloat())
                        change.consume()
                    },
                    onDragEnd = {
                        if (abs(dismissOffset) >= closeThreshold) onDismiss()
                        else dismissOffset = 0f
                    },
                    onDragCancel = { dismissOffset = 0f },
                )
            }
        }
        .onSizeChanged { box = it }
        .pointerInput(item.key) {
        detectTapGestures(onTap = { onTap() }, onDoubleTap = {
            if (!item.isVideo) {
                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
            }
        })
    }
    if (!item.isVideo) modifier = modifier.transformable(transformState, canPan = { scale > 1f })
    Box(modifier.graphicsLayer {
        translationY = dismissOffset
        val height = size.height.coerceAtLeast(1f)
        alpha = 1f - (abs(dismissOffset) / height).coerceIn(0f, 0.65f)
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