package com.fastgallery.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    current?.name ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { slideshow = !slideshow }) {
                    Text(if (slideshow) "Pause" else "Slide", color = Color.White)
                }
                IconButton(onClick = { current?.let { shareItem(ctx, it) } }) {
                    Icon(Icons.Filled.Share, "Share", tint = Color.White)
                }
            }
        }
        AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                current?.let { item ->
                    ControlButton(if (item.key in favoriteKeys) "★ Saved" else "☆ Favorite") { onFavorite(item) }
                    ControlButton(if (item.key in trashedKeys) "Restore" else "Trash") {
                        onSetTrashed(item, item.key !in trashedKeys)
                    }
                    ControlButton("Delete") { onDelete(item) }
                    ControlButton("Rename") {
                        renameText = item.name
                        renameTarget = item
                    }
                    ControlButton("Copy / move") {
                        folderText = item.bucketName
                        moveAfterCopy = false
                        copyTarget = item
                    }
                    ControlButton("Edit") { if (!item.isVideo) editTarget = item }
                    ControlButton("Details") { details = item }
                    ControlButton("Wallpaper") { if (!item.isVideo) onWallpaper(item) }
                }
            }
        }
    }

    details?.let { item ->
        val rows = remember(item.key) { MediaOperations.exifDetails(ctx, item) }
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("Photo details") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    rows.forEach { (label, value) ->
                        Text("$label: $value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } },
        )
    }
    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename media") },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    label = { Text("File name") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(item, renameText); renameTarget = null }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }
    copyTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { copyTarget = null },
            title = { Text("Copy or move to album") },
            text = {
                Column {
                    OutlinedTextField(value = folderText, onValueChange = { folderText = it }, label = { Text("Album / folder name") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete original after copy")
                        Switch(checked = moveAfterCopy, onCheckedChange = { moveAfterCopy = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onCopyOrMove(item, folderText, moveAfterCopy); copyTarget = null }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { copyTarget = null }) { Text("Cancel") } },
        )
    }
    editTarget?.let { item -> EditDialog(item.name, onDismiss = { editTarget = null }) { edit ->
        onEdit(item, edit)
        editTarget = null
    } }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label, color = Color.White, maxLines = 1) }
}

@Composable
private fun EditDialog(name: String, onDismiss: () -> Unit, onSave: (ImageEdit) -> Unit) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var crop by remember { mutableStateOf<Float?>(null) }
    var filter by remember { mutableStateOf("Original") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit · $name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rotate: ${rotation.toInt()}°")
                Slider(value = rotation, onValueChange = { rotation = (it / 90f).toInt() * 90f }, valueRange = 0f..270f, steps = 2)
                Text("Crop")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Free" to null, "Square" to 1f, "4:3" to (4f / 3f)).forEach { (label, ratio) ->
                        if (crop == ratio) Button(onClick = { crop = ratio }) { Text(label) }
                        else OutlinedButton(onClick = { crop = ratio }) { Text(label) }
                    }
                }
                Text("Filter")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Original", "Mono", "Warm", "Cool").forEach { option ->
                        if (filter == option) Button(onClick = { filter = option }) { Text(option) }
                        else OutlinedButton(onClick = { filter = option }) { Text(option) }
                    }
                }
                Text("Saves a new edited copy; the original stays unchanged.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ImageEdit(rotation, crop, filter)) }) { Text("Save copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerPage(
    item: MediaItem,
    isCurrent: Boolean,
    onTap: () -> Unit,
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
            VideoPlayer(item)
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
                    "RAW",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}