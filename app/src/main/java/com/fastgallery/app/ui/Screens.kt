package com.fastgallery.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.hypot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.GridEntry
import com.fastgallery.app.data.MediaItem

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    padding: PaddingValues,
    selected: Set<String>,
    columns: Int,
    onOpen: (Int) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onPinchColumns: (Int) -> Unit,
) {
    val entries = remember(items) { com.fastgallery.app.data.buildEntries(items) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val currentColumns by rememberUpdatedState(columns)
    val changeColumns by rememberUpdatedState(onPinchColumns)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (columns == 0) GridCells.Adaptive(112.dp) else GridCells.Fixed(columns.coerceIn(2, 8)),
            state = gridState,
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


@Composable
fun AlbumsGrid(
    albums: List<Album>,
    padding: PaddingValues,
    onOpen: (Album) -> Unit,
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
            Column(Modifier.clickable { onOpen(album) }) {
                AsyncImage(
                    model = rememberImageRequest(album.cover.uri, 384),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
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

@Composable
fun CenterMessage(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "Photos aur videos dikhane ke liye access chahiye",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAllow, modifier = Modifier.padding(top = 20.dp)) { Text("Allow access") }
        OutlinedButton(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Open settings") }
    }
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    theme: String,
    columns: Int,
    albums: List<Album>,
    hidden: Set<String>,
    locked: Set<String>,
    onTheme: (String) -> Unit,
    onColumns: (Int) -> Unit,
    onHidden: (Long, Boolean) -> Unit,
    onLocked: (Long, Boolean) -> Unit,
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
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    if (theme == value) Button(onClick = { onTheme(value) }) { Text(label) }
                    else OutlinedButton(onClick = { onTheme(value) }) { Text(label) }
                }
            }
        }
        item {
            Divider()
            Text("Grid columns: ${columns.coerceAtLeast(2)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = columns.coerceIn(2, 8).toFloat(),
                onValueChange = { onColumns(it.toInt().coerceIn(2, 8)) },
                valueRange = 2f..8f,
                steps = 5,
            )
            Text("Pinch the grid to change columns quickly.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Divider()
            Text("Albums · hide or lock", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("Hidden albums disappear from the Albums tab. Locked albums ask for device authentication when opened.", style = MaterialTheme.typography.bodySmall)
        }
        items(albums, key = { "settings_${it.id}" }) { album ->
            Column {
                Text(album.name, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Hide", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = album.id.toString() in hidden, onCheckedChange = { onHidden(album.id, it) })
                    Text("Lock", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                    Switch(checked = album.id.toString() in locked, onCheckedChange = { onLocked(album.id, it) })
                }
            }
        }
    }
}
