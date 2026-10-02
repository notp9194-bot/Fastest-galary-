package com.fastgallery.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.fastgallery.app.data.MediaItem

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
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = rememberThumbRequest(item.uri, sizePx),
            contentDescription = null,
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
                    "Selected",
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
    ctx.startActivity(Intent.createChooser(send, "Share selected media"))
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
