package com.fastgallery.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.fastgallery.app.R
import com.fastgallery.app.data.MediaItem

/** Material "Sort" icon (extended icons dependency ke bina). */
val SortIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Sort",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString("M3,18h6v-2L3,16v2zM3,6v2h18L21,6L3,6zM3,13h12v-2L3,11v2z").toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "Pause" icon (extended icons dependency ke bina). */
val PauseIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString("M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z").toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
fun rememberImageRequest(uri: Uri, size: Int): ImageRequest {
    val ctx = LocalContext.current
    return remember(uri, size) { ImageRequest.Builder(ctx).data(uri).size(size).build() }
}

/**
 * Grid/album thumbs: API 29+ pe MediaStore ke system-cached thumbnails (full decode se bahut fast),
 * purane Android pe sampled Coil decode.
 */
@Composable
fun rememberThumbRequest(uri: Uri, size: Int): ImageRequest {
    val ctx = LocalContext.current
    return remember(uri, size) {
        val data: Any = if (Build.VERSION.SDK_INT >= 29) ThumbData(uri, size) else uri
        ImageRequest.Builder(ctx)
            .data(data)
            .size(size)
            .precision(Precision.INEXACT)
            .build()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    sizePx: Int,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    /** true = long-press grid-level gesture (drag-to-select) handle karta hai; yahan sirf TalkBack action. */
    longPressHandledByGrid: Boolean = false,
    /** Grid se aaya modifier (jaise animateItem()); sabse pehle lagta hai. */
    modifier: Modifier = Modifier,
) {
    val dateLabel = remember(item.dateTaken, item.dateAdded) {
        val millis = if (item.dateTaken > 0L) item.dateTaken else item.dateAdded * 1000L
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))
    }
    val description = if (item.isVideo) {
        stringResource(R.string.thumb_video_desc, formatDuration(item.durationMs), dateLabel)
    } else {
        stringResource(R.string.thumb_photo_desc, dateLabel)
    }
    val selectedLabel = stringResource(R.string.thumb_selected)
    val selectLabel = stringResource(R.string.thumb_select_action)
    val longClickAction = onLongClick
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (longPressHandledByGrid) Modifier.clickable(onClick = onClick)
                else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            )
            // Ek hi TalkBack node: "Photo, 12 Mar 2025" + selected state.
            .semantics(mergeDescendants = true) {
                if (selected) stateDescription = selectedLabel
                if (longPressHandledByGrid) onLongClick(label = selectLabel) { longClickAction(); true }
            }
    ) {
        AsyncImage(
            model = rememberThumbRequest(item.uri, sizePx),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    formatDuration(item.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (selected) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.TopEnd,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(22.dp),
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun shareItem(ctx: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mime
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

fun shareItems(ctx: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (items.all { it.isVideo }) "video/*" else if (items.all { !it.isVideo }) "image/*" else "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_selected)))
}

fun openVideo(ctx: Context, item: MediaItem) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, item.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(view)
    } catch (_: ActivityNotFoundException) {
    }
}


/**
 * Loading placeholder: media grid ki shape ke skeleton cells (halka pulse), spinner ki jagah.
 * Scroll band hai; asli grid aate hi replace ho jaata hai.
 */
@Composable
fun SkeletonGrid(columns: Int, padding: PaddingValues) {
    val cols = effectiveColumns(columns, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)
    val loadingLabel = stringResource(R.string.loading)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    val cellColor = MaterialTheme.colorScheme.surfaceVariant
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().semantics { contentDescription = loadingLabel },
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(cols * 14) {
            Box(
                Modifier
                    .aspectRatio(1f)
                    .graphicsLayer { alpha = pulse }
                    .background(cellColor)
            )
        }
    }
}

/** Empty screen: round icon badge + title + optional hint. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String?, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
