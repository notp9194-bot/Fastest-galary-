package com.fastgallery.app.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fastgallery.app.R
import com.fastgallery.app.data.CropRect
import com.fastgallery.app.data.ImageEdit
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.MediaOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class CropHandle { None, Move, TopLeft, TopRight, BottomLeft, BottomRight, Left, Top, Right, Bottom }

private val FULL_IMAGE = Rect(0f, 0f, 1f, 1f)

/** Full-screen editor: live preview (rotation, filter), draggable crop box with optional fixed ratio. */
@Composable
fun EditDialog(item: MediaItem, onDismiss: () -> Unit, onSave: (ImageEdit) -> Unit) {
    val context = LocalContext.current
    val preview by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { runCatching { MediaOperations.loadEditPreview(context, item.uri) }.getOrNull() }
    }
    var quarterTurns by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("Original") }
    var aspect by remember { mutableStateOf<Float?>(null) }
    var crop by remember { mutableStateOf(FULL_IMAGE) }

    val base = preview
    val shown = remember(base, quarterTurns) {
        if (base == null || quarterTurns == 0) base
        else Bitmap.createBitmap(
            base, 0, 0, base.width, base.height,
            Matrix().apply { postRotate(quarterTurns * 90f) }, true,
        )
    }

    fun resetCrop(newAspect: Float?, turns: Int) {
        val b = base ?: return
        val swap = turns % 2 != 0
        crop = initialCrop(newAspect, if (swap) b.height else b.width, if (swap) b.width else b.height)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101010), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_cancel), tint = Color.White)
                    }
                    Text(
                        stringResource(R.string.edit_title, item.name),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        enabled = shown != null,
                        onClick = {
                            val full = crop.left <= 0.001f && crop.top <= 0.001f && crop.right >= 0.999f && crop.bottom >= 0.999f
                            onSave(
                                ImageEdit(
                                    rotationDegrees = (quarterTurns * 90).toFloat(),
                                    crop = if (full) null else CropRect(crop.left, crop.top, crop.right, crop.bottom),
                                    filter = filter,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.edit_save_copy)) }
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (shown == null) {
                        CircularProgressIndicator()
                    } else {
                        CropPreview(
                            bitmap = shown,
                            filter = filter,
                            crop = crop,
                            aspect = aspect,
                            onCropChange = { crop = it },
                        )
                    }
                }

                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            quarterTurns = (quarterTurns + 3) % 4
                            resetCrop(aspect, quarterTurns)
                        }) {
                            Icon(
                                Icons.Filled.Refresh, stringResource(R.string.edit_rotate_left),
                                tint = Color.White, modifier = Modifier.graphicsLayer { scaleX = -1f },
                            )
                        }
                        IconButton(onClick = {
                            quarterTurns = (quarterTurns + 1) % 4
                            resetCrop(aspect, quarterTurns)
                        }) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.edit_rotate_right), tint = Color.White)
                        }
                        listOf(
                            R.string.crop_free to null,
                            R.string.crop_square to 1f,
                            R.string.crop_4_3 to (4f / 3f),
                            R.string.crop_3_4 to (3f / 4f),
                            R.string.crop_16_9 to (16f / 9f),
                        ).forEach { (labelRes, ratio) ->
                            EditChip(
                                selected = aspect == ratio,
                                onClick = {
                                    aspect = ratio
                                    resetCrop(ratio, quarterTurns)
                                },
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // First value is ImageEdit's internal id (matched in MediaOperations); label is display-only.
                        listOf(
                            "Original" to R.string.filter_original,
                            "Mono" to R.string.filter_mono,
                            "Warm" to R.string.filter_warm,
                            "Cool" to R.string.filter_cool,
                        ).forEach { (option, labelRes) ->
                            EditChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(stringResource(labelRes)) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.edit_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Largest centred crop (as fractions of the image) matching [aspect] (width / height); whole image when free. */
private fun initialCrop(aspect: Float?, imgW: Int, imgH: Int): Rect {
    if (aspect == null || imgW <= 0 || imgH <= 0) return FULL_IMAGE
    val imageRatio = imgW.toFloat() / imgH
    val w = if (imageRatio > aspect) aspect / imageRatio else 1f
    val h = if (imageRatio > aspect) 1f else imageRatio / aspect
    return Rect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
}

@Composable
private fun CropPreview(
    bitmap: Bitmap,
    filter: String,
    crop: Rect,
    aspect: Float?,
    onCropChange: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val slopPx = with(density) { 36.dp.toPx() }
    val minSidePx = with(density) { 56.dp.toPx() }
    val handleLen = with(density) { 22.dp.toPx() }
    val handleStroke = with(density) { 4.dp.toPx() }
    val lineStroke = with(density) { 1.5.dp.toPx() }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val colorFilter = remember(filter) {
        MediaOperations.filterMatrix(filter)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }
    val currentCrop by rememberUpdatedState(crop)
    val currentOnChange by rememberUpdatedState(onCropChange)

    Canvas(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(bitmap, aspect) {
                fun dispRect() = fitRect(size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height)
                var handle = CropHandle.None
                var pointer = Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        val box = toDisplay(currentCrop, dispRect())
                        handle = hitTest(box, start, slopPx)
                        pointer = when (handle) {
                            CropHandle.TopLeft -> box.topLeft
                            CropHandle.TopRight -> box.topRight
                            CropHandle.BottomLeft -> box.bottomLeft
                            CropHandle.BottomRight -> box.bottomRight
                            CropHandle.Left -> Offset(box.left, box.center.y)
                            CropHandle.Right -> Offset(box.right, box.center.y)
                            CropHandle.Top -> Offset(box.center.x, box.top)
                            CropHandle.Bottom -> Offset(box.center.x, box.bottom)
                            else -> start
                        }
                    },
                    onDragEnd = { handle = CropHandle.None },
                    onDragCancel = { handle = CropHandle.None },
                ) { change, drag ->
                    if (handle == CropHandle.None) return@detectDragGestures
                    change.consume()
                    val disp = dispRect()
                    val box = toDisplay(currentCrop, disp)
                    val updated = if (handle == CropHandle.Move) {
                        val dx = drag.x.coerceIn(disp.left - box.left, disp.right - box.right)
                        val dy = drag.y.coerceIn(disp.top - box.top, disp.bottom - box.bottom)
                        box.translate(dx, dy)
                    } else {
                        pointer += drag
                        resizeBox(box, handle, pointer, disp, aspect, minSidePx)
                    }
                    currentOnChange(fromDisplay(updated, disp))
                }
            },
    ) {
        val disp = fitRect(size.width, size.height, bitmap.width, bitmap.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(disp.left.toInt(), disp.top.toInt()),
            dstSize = IntSize(disp.width.toInt(), disp.height.toInt()),
            colorFilter = colorFilter,
        )
        val box = toDisplay(crop, disp)
        val dim = Color.Black.copy(alpha = 0.6f)
        drawRect(dim, Offset(disp.left, disp.top), Size(disp.width, box.top - disp.top))
        drawRect(dim, Offset(disp.left, box.bottom), Size(disp.width, disp.bottom - box.bottom))
        drawRect(dim, Offset(disp.left, box.top), Size(box.left - disp.left, box.height))
        drawRect(dim, Offset(box.right, box.top), Size(disp.right - box.right, box.height))

        // Rule-of-thirds grid + border.
        val grid = Color.White.copy(alpha = 0.45f)
        for (i in 1..2) {
            val x = box.left + box.width * i / 3f
            val y = box.top + box.height * i / 3f
            drawLine(grid, Offset(x, box.top), Offset(x, box.bottom), lineStroke)
            drawLine(grid, Offset(box.left, y), Offset(box.right, y), lineStroke)
        }
        drawRect(Color.White, box.topLeft, box.size, style = Stroke(lineStroke))

        // Corner handles (L-shaped) and edge-centre handles.
        val l = min(handleLen, min(box.width, box.height) / 3f)
        fun corner(c: Offset, dx: Float, dy: Float) {
            drawLine(Color.White, c, Offset(c.x + dx * l, c.y), handleStroke, StrokeCap.Square)
            drawLine(Color.White, c, Offset(c.x, c.y + dy * l), handleStroke, StrokeCap.Square)
        }
        corner(box.topLeft, 1f, 1f)
        corner(box.topRight, -1f, 1f)
        corner(box.bottomLeft, 1f, -1f)
        corner(box.bottomRight, -1f, -1f)
        val half = l / 2f
        drawLine(Color.White, Offset(box.center.x - half, box.top), Offset(box.center.x + half, box.top), handleStroke, StrokeCap.Square)
        drawLine(Color.White, Offset(box.center.x - half, box.bottom), Offset(box.center.x + half, box.bottom), handleStroke, StrokeCap.Square)
        drawLine(Color.White, Offset(box.left, box.center.y - half), Offset(box.left, box.center.y + half), handleStroke, StrokeCap.Square)
        drawLine(Color.White, Offset(box.right, box.center.y - half), Offset(box.right, box.center.y + half), handleStroke, StrokeCap.Square)
    }
}

/** Where a bitmap of [bw]x[bh] lands when fitted (centred) inside a [w]x[h] area. */
private fun fitRect(w: Float, h: Float, bw: Int, bh: Int): Rect {
    if (bw <= 0 || bh <= 0 || w <= 0f || h <= 0f) return Rect(0f, 0f, w, h)
    val scale = min(w / bw, h / bh)
    val dw = bw * scale
    val dh = bh * scale
    val left = (w - dw) / 2f
    val top = (h - dh) / 2f
    return Rect(left, top, left + dw, top + dh)
}

private fun toDisplay(crop: Rect, disp: Rect) = Rect(
    disp.left + crop.left * disp.width,
    disp.top + crop.top * disp.height,
    disp.left + crop.right * disp.width,
    disp.top + crop.bottom * disp.height,
)

private fun fromDisplay(box: Rect, disp: Rect): Rect {
    if (disp.width <= 0f || disp.height <= 0f) return FULL_IMAGE
    return Rect(
        ((box.left - disp.left) / disp.width).coerceIn(0f, 1f),
        ((box.top - disp.top) / disp.height).coerceIn(0f, 1f),
        ((box.right - disp.left) / disp.width).coerceIn(0f, 1f),
        ((box.bottom - disp.top) / disp.height).coerceIn(0f, 1f),
    )
}

private fun hitTest(box: Rect, p: Offset, slop: Float): CropHandle {
    val nearL = abs(p.x - box.left) <= slop
    val nearR = abs(p.x - box.right) <= slop
    val nearT = abs(p.y - box.top) <= slop
    val nearB = abs(p.y - box.bottom) <= slop
    val withinX = p.x >= box.left - slop && p.x <= box.right + slop
    val withinY = p.y >= box.top - slop && p.y <= box.bottom + slop
    return when {
        nearL && nearT -> CropHandle.TopLeft
        nearR && nearT -> CropHandle.TopRight
        nearL && nearB -> CropHandle.BottomLeft
        nearR && nearB -> CropHandle.BottomRight
        nearT && withinX -> CropHandle.Top
        nearB && withinX -> CropHandle.Bottom
        nearL && withinY -> CropHandle.Left
        nearR && withinY -> CropHandle.Right
        box.contains(p) -> CropHandle.Move
        else -> CropHandle.None
    }
}

/**
 * Resizes [box] so the dragged handle follows [p], staying inside [bounds] and at least [minSide] wide/tall.
 * With a fixed [aspect] (width / height) the box keeps that ratio; the opposite corner/edge stays anchored.
 */
private fun resizeBox(box: Rect, handle: CropHandle, p: Offset, bounds: Rect, aspect: Float?, minSide: Float): Rect {
    val px = p.x.coerceIn(bounds.left, bounds.right)
    val py = p.y.coerceIn(bounds.top, bounds.bottom)
    if (aspect == null) {
        var l = box.left; var t = box.top; var r = box.right; var b = box.bottom
        when (handle) {
            CropHandle.TopLeft -> { l = px; t = py }
            CropHandle.TopRight -> { r = px; t = py }
            CropHandle.BottomLeft -> { l = px; b = py }
            CropHandle.BottomRight -> { r = px; b = py }
            CropHandle.Left -> l = px
            CropHandle.Right -> r = px
            CropHandle.Top -> t = py
            CropHandle.Bottom -> b = py
            else -> Unit
        }
        l = min(l, r - minSide); t = min(t, b - minSide)
        r = max(r, l + minSide); b = max(b, t + minSide)
        // Re-clamp in case the minimum pushed a side out of the image.
        if (r > bounds.right) { r = bounds.right; l = r - minSide }
        if (b > bounds.bottom) { b = bounds.bottom; t = b - minSide }
        return Rect(l, t, r, b)
    }

    val minW = max(minSide, minSide * aspect)
    return when (handle) {
        CropHandle.TopLeft, CropHandle.TopRight, CropHandle.BottomLeft, CropHandle.BottomRight -> {
            val anchorX = if (handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft) box.right else box.left
            val anchorY = if (handle == CropHandle.TopLeft || handle == CropHandle.TopRight) box.bottom else box.top
            val goLeft = handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft
            val goUp = handle == CropHandle.TopLeft || handle == CropHandle.TopRight
            val roomX = if (goLeft) anchorX - bounds.left else bounds.right - anchorX
            val roomY = if (goUp) anchorY - bounds.top else bounds.bottom - anchorY
            val maxW = min(roomX, roomY * aspect)
            val wanted = max(abs(px - anchorX), abs(py - anchorY) * aspect)
            val w = wanted.coerceIn(min(minW, maxW), maxW)
            val h = w / aspect
            Rect(
                if (goLeft) anchorX - w else anchorX,
                if (goUp) anchorY - h else anchorY,
                if (goLeft) anchorX else anchorX + w,
                if (goUp) anchorY else anchorY + h,
            )
        }
        CropHandle.Left, CropHandle.Right -> {
            val goLeft = handle == CropHandle.Left
            val anchorX = if (goLeft) box.right else box.left
            val cy = box.center.y
            val roomX = if (goLeft) anchorX - bounds.left else bounds.right - anchorX
            val maxH = 2f * min(cy - bounds.top, bounds.bottom - cy)
            val maxW = min(roomX, maxH * aspect)
            val w = abs(px - anchorX).coerceIn(min(minW, maxW), maxW)
            val h = w / aspect
            Rect(if (goLeft) anchorX - w else anchorX, cy - h / 2f, if (goLeft) anchorX else anchorX + w, cy + h / 2f)
        }
        CropHandle.Top, CropHandle.Bottom -> {
            val goUp = handle == CropHandle.Top
            val anchorY = if (goUp) box.bottom else box.top
            val cx = box.center.x
            val roomY = if (goUp) anchorY - bounds.top else bounds.bottom - anchorY
            val maxW = min(2f * min(cx - bounds.left, bounds.right - cx), roomY * aspect)
            val w = (abs(py - anchorY) * aspect).coerceIn(min(minW, maxW), maxW)
            val h = w / aspect
            Rect(cx - w / 2f, if (goUp) anchorY - h else anchorY, cx + w / 2f, if (goUp) anchorY else anchorY + h)
        }
        else -> box
    }
}
