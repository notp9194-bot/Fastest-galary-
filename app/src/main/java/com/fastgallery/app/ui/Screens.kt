package com.fastgallery.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import android.widget.OverScroller
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fastgallery.app.R
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.GridEntry
import com.fastgallery.app.data.MediaItem

@Composable
private fun rememberGridFlingBehavior(friction: Float): FlingBehavior {
    val context = LocalContext.current
    return remember(context, friction) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (!initialVelocity.isFinite() || abs(initialVelocity) < 1f) return 0f
                val scroller = OverScroller(context).apply { setFriction(friction) }
                scroller.fling(
                    0,
                    0,
                    0,
                    initialVelocity.roundToInt(),
                    0,
                    0,
                    -1_000_000_000,
                    1_000_000_000,
                )
                var lastY = 0
                while (!scroller.isFinished) {
                    withFrameNanos { scroller.computeScrollOffset() }
                    val currentY = scroller.currY
                    val delta = (currentY - lastY).toFloat()
                    if (delta != 0f) {
                        val consumed = scrollBy(delta)
                        lastY = currentY
                        if (abs(delta - consumed) > 1f) {
                            scroller.forceFinished(true)
                            return 0f
                        }
                    }
                }
                return 0f
            }
        }
    }
}

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    padding: PaddingValues,
    selected: Set<String>,
    columns: Int,
    flingFriction: Float = 0.015f,
    contentVersion: Long,
    resetKey: Any = Unit,
    onOpen: (Int) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onPinchColumns: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val entries = remember(contentVersion) { com.fastgallery.app.data.buildEntries(items) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val currentColumns by rememberUpdatedState(columns)
    val changeColumns by rememberUpdatedState(onPinchColumns)
    val gridFlingBehavior = rememberGridFlingBehavior(flingFriction)
    // Cell size ek hi baar nikalo (har Thumb me BoxWithConstraints subcompose bahut slow tha).
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val thumbPx = remember(columns, screenWidthDp, density.density) {
        val cols = if (columns == 0) maxOf(1, screenWidthDp / 112) else columns.coerceIn(2, 8)
        ((screenWidthDp - 2f * (cols - 1)) / cols * density.density).toInt().coerceIn(64, 1024)
    }
    // Filter/sort/search badalne par naye result top se dikhao.
    LaunchedEffect(resetKey) { gridState.scrollToItem(0) }
    LaunchedEffect(gridState, items.size) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisible ->
            val total = gridState.layoutInfo.totalItemsCount
            if (lastVisible >= 0 && lastVisible >= total - 18) onLoadMore()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (columns == 0) GridCells.Adaptive(112.dp) else GridCells.Fixed(columns.coerceIn(2, 8)),
            state = gridState,
            flingBehavior = gridFlingBehavior,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var startSpan = 0f
                    var changed = false
                    do {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.size >= 2) {
                            val centerX = active.map { it.position.x }.average().toFloat()
                            val centerY = active.map { it.position.y }.average().toFloat()
                            val span = active.map {
                                hypot(
                                    (it.position.x - centerX).toDouble(),
                                    (it.position.y - centerY).toDouble(),
                                )
                            }
                                .average().toFloat() * 2f
                            if (startSpan == 0f) startSpan = span
                            else if (!changed && span > startSpan * 1.18f) {
                                changeColumns((currentColumns - 1).coerceAtLeast(2))
                                changed = true
                            } else if (!changed && span < startSpan * 0.82f) {
                                changeColumns((currentColumns + 1).coerceAtMost(8))
                                changed = true
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                items = entries,
                key = { it.key },
                span = { if (it is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { it is GridEntry.Header },
            ) { entry ->
                when (entry) {
                    is GridEntry.Header -> Text(
                        entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    is GridEntry.Media -> Thumb(
                        entry.item,
                        sizePx = thumbPx,
                        selected = entry.item.key in selected,
                        onClick = { if (selected.isNotEmpty()) onToggleSelection(entry.item) else onOpen(entry.index) },
                        onLongClick = { onToggleSelection(entry.item) },
                    )
                }
            }
        }
        if (items.size > 50) {
            BoxWithConstraints(Modifier.align(Alignment.CenterEnd).fillMaxSize()) {
                val fraction = (gridState.firstVisibleItemIndex.toFloat() /
                    gridState.layoutInfo.totalItemsCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (maxHeight.toPx() * fraction).toInt()) }
                        .padding(end = 3.dp)
                        .width(18.dp)
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp)
                        .pointerInput(items.size) {
                            detectVerticalDragGestures { change, _ ->
                                val next = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                scope.launch {
                                    val count = gridState.layoutInfo.totalItemsCount
                                    if (count > 0) gridState.scrollToItem((count * next).toInt().coerceIn(0, count - 1))
                                }
                            }
                        },
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsGrid(
    albums: List<Album>,
    padding: PaddingValues,
    locked: Set<String>,
    pinned: Set<String>,
    onOpen: (Album) -> Unit,
    onHide: (Album) -> Unit,
    onToggleLock: (Album) -> Unit,
    onTogglePin: (Album) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            var menuOpen by remember { mutableStateOf(false) }
            val isLocked = album.id.toString() in locked
            val isPinned = album.id.toString() in pinned
            Box {
                Column(
                    Modifier.combinedClickable(
                        onClick = { onOpen(album) },
                        onLongClick = { menuOpen = true },
                    ),
                ) {
                    BoxWithConstraints(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        val coverSizePx = with(LocalDensity.current) {
                            maxWidth.roundToPx().coerceIn(1, 1024)
                        }
                        if (isLocked) {
                            // Locked album ka cover authentication se pehle nahi dikhna chahiye.
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Filled.Lock,
                                    contentDescription = stringResource(R.string.album_locked),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        } else AsyncImage(
                            model = rememberThumbRequest(album.cover.uri, coverSizePx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isPinned) {
                            androidx.compose.material3.Icon(
                                PinIcon,
                                contentDescription = stringResource(R.string.album_pinned),
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .padding(5.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                    Text(
                        album.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        if (isLocked) stringResource(R.string.album_count_locked, album.count) else "${album.count}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isPinned) R.string.album_unpin else R.string.album_pin)) },
                        onClick = { menuOpen = false; onTogglePin(album) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.album_hide)) },
                        onClick = { menuOpen = false; onHide(album) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isLocked) R.string.album_remove_lock else R.string.album_lock)) },
                        onClick = { menuOpen = false; onToggleLock(album) },
                    )
                }
            }
        }
    }
}

/** Android 14 "Selected photos only" mode ke liye floating banner: baaki media ka access manage karne ka shortcut. */
@Composable
fun PartialAccessBanner(onManage: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.partial_access_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onManage) { Text(stringResource(R.string.partial_access_manage)) }
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.partial_access_dismiss),
                )
            }
        }
    }
}

@Composable
fun PermissionScreen(padding: PaddingValues, onAllow: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAllow, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(R.string.permission_allow)) }
        OutlinedButton(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.permission_open_settings)) }
    }
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    theme: String,
    columns: Int,
    hiddenCount: Int,
    lockedCount: Int,
    onTheme: (String) -> Unit,
    onColumns: (Int) -> Unit,
    onOpenHidden: () -> Unit,
    onOpenLocked: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(
                    "system" to R.string.theme_system,
                    "light" to R.string.theme_light,
                    "dark" to R.string.theme_dark,
                ).forEach { (value, labelRes) ->
                    val label = stringResource(labelRes)
                    if (theme == value) Button(onClick = { onTheme(value) }) { Text(label) }
                    else OutlinedButton(onClick = { onTheme(value) }) { Text(label) }
                }
            }
        }
        item {
            Divider()
            Text(stringResource(R.string.settings_grid_columns, columns.coerceAtLeast(2)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = columns.coerceIn(2, 8).toFloat(),
                onValueChange = { onColumns(it.toInt().coerceIn(2, 8)) },
                valueRange = 2f..8f,
                steps = 5,
            )
            Text(stringResource(R.string.settings_pinch_hint), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Divider()
            Text(stringResource(R.string.settings_private_albums), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.settings_private_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SummaryRow(stringResource(R.string.hidden_albums), hiddenCount, onOpenHidden) }
        item { SummaryRow(stringResource(R.string.locked_albums), lockedCount, onOpenLocked) }
    }
}

@Composable
private fun SummaryRow(label: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            "$count  ›",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Manage screen for hidden (locked = false) or locked (locked = true) albums.
 * Lists only albums currently in that state; "Add album" opens a searchable picker.
 */
@Composable
fun ManagedAlbumsScreen(
    padding: PaddingValues,
    locked: Boolean,
    albums: List<Album>,
    ids: Set<String>,
    onRemove: (Long) -> Unit,
    onAdd: (Long) -> Unit,
) {
    val removeLabel = stringResource(if (locked) R.string.managed_unlock else R.string.managed_unhide)
    val byId = remember(albums) { albums.associateBy { it.id.toString() } }
    val managed = remember(ids, byId) {
        ids.sortedWith(compareBy({ byId[it] == null }, { byId[it]?.name?.lowercase() ?: it }))
    }
    var picker by remember { mutableStateOf(false) }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                stringResource(if (locked) R.string.managed_locked_desc else R.string.managed_hidden_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { picker = true }, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.managed_add_album))
            }
        }
        if (managed.isEmpty()) {
            item {
                Text(
                    stringResource(if (locked) R.string.managed_none_locked else R.string.managed_none_hidden),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(managed, key = { "managed_$it" }) { id ->
            val album = byId[id]
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        album?.name ?: stringResource(R.string.managed_unavailable),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (album != null) {
                        Text(
                            "${album.count}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                id.toLongOrNull()?.let { longId ->
                    TextButton(onClick = { onRemove(longId) }) { Text(removeLabel) }
                }
            }
            Divider()
        }
    }
    if (picker) {
        var query by remember { mutableStateOf("") }
        val candidates = remember(albums, ids, query) {
            albums.filter { it.id.toString() !in ids && (query.isBlank() || it.name.contains(query, true)) }
                .sortedBy { it.name.lowercase() }
        }
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text(stringResource(if (locked) R.string.managed_pick_lock_title else R.string.managed_pick_hide_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.managed_search_albums)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            stringResource(R.string.managed_no_albums_found),
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp),
                        ) {
                            items(candidates, key = { "pick_${it.id}" }) { album ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onAdd(album.id); picker = false }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        album.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${album.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
