package com.fastgallery.app.ui

import android.text.format.Formatter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.GridEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fast scroll handle + date scrubber.
 *
 *  - Scroll karte hi handle fade-in hota hai, ruk ke ~1.5s baad fade-out.
 *  - Handle pakad ke upar/neeche drag karo: grid turant us position pe jump karta hai aur
 *    handle ke baaju me bubble dikhta hai (Date sort me "Sep 2026", Name sort me pehla akshar,
 *    Size sort me file size).
 *  - Handle sirf tab touch leta hai jab visible ho, isliye normal scroll/tap kabhi nahi rukte.
 */
@Composable
fun BoxScope.FastScroller(
    gridState: LazyGridState,
    entries: List<GridEntry>,
    sort: GallerySort,
    contentPadding: PaddingValues,
    onScrubStart: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val handleHeight = 56.dp
    val handlePx = with(density) { handleHeight.toPx() }
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentEntries by rememberUpdatedState(entries)

    var trackPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var visible by remember { mutableStateOf(false) }

    val monthFormat = remember {
        android.icu.text.DateFormat.getInstanceForSkeleton("yMMM", Locale.getDefault())
    }

    // Scroll/drag ke dauran dikhao, ruk jaane ke baad thodi der me chhupa do.
    val scrollInProgress = gridState.isScrollInProgress
    LaunchedEffect(scrollInProgress, dragging) {
        if (scrollInProgress || dragging) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 120 else 300),
        label = "fast_scroller_alpha",
    )
    val pillWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "fast_scroller_pill")

    // Drag ke target index pe latest-wins scroll (events pile-up nahi hote).
    LaunchedEffect(gridState) {
        snapshotFlow { targetIndex }
            .filter { it >= 0 }
            .collectLatest { gridState.scrollToItem(it) }
    }

    fun scrubRange(): Int {
        val info = gridState.layoutInfo
        return (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
    }

    fun scrollFraction(): Float {
        if (!gridState.canScrollForward) return if (gridState.canScrollBackward) 1f else 0f
        val range = scrubRange().coerceAtLeast(1)
        return (gridState.firstVisibleItemIndex.toFloat() / range).coerceIn(0f, 1f)
    }

    val bubbleLabel by remember(sort) {
        derivedStateOf {
            val index = targetIndex
            if (index < 0) "" else scrubLabel(currentEntries, index, sort) { millis, bytes ->
                when (sort) {
                    GallerySort.SIZE_LARGEST -> Formatter.formatShortFileSize(ctx, bytes)
                    else -> monthFormat.format(Date(millis))
                }
            }
        }
    }

    // Label badalne par halka tick (sirf drag ke dauran).
    LaunchedEffect(dragging) {
        if (dragging) {
            snapshotFlow { bubbleLabel }
                .distinctUntilChanged()
                .drop(1)
                .collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }

    Box(
        Modifier
            .matchParentSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .onSizeChanged { trackPx = it.height },
    ) {
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val travel = (trackPx - handlePx).coerceAtLeast(0f)
                    val fraction = if (dragging) dragFraction else scrollFraction()
                    IntOffset(0, (travel * fraction).roundToInt())
                }
                .graphicsLayer { this.alpha = alpha.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragging && bubbleLabel.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(
                        bubbleLabel,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            // Touch zone handle se chaudi (40dp) taaki pakadna aasaan ho; sirf visible hone par active.
            Box(
                Modifier
                    .width(40.dp)
                    .height(handleHeight)
                    .then(
                        if (visible) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dragging = true
                                        dragFraction = scrollFraction()
                                        targetIndex = (dragFraction * scrubRange()).roundToInt()
                                        currentOnScrubStart()
                                    },
                                    onDragEnd = { dragging = false; targetIndex = -1 },
                                    onDragCancel = { dragging = false; targetIndex = -1 },
                                ) { change, dy ->
                                    change.consume()
                                    val travel = (trackPx - handlePx).coerceAtLeast(1f)
                                    dragFraction = (dragFraction + dy / travel).coerceIn(0f, 1f)
                                    targetIndex = (dragFraction * scrubRange()).roundToInt()
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(end = 3.dp)
                        .width(pillWidth)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** Index pe (ya uske aas-paas) wale media item ke hisaab se bubble text. Headers ke liye agla media dekhta hai. */
private inline fun scrubLabel(
    entries: List<GridEntry>,
    index: Int,
    sort: GallerySort,
    format: (millis: Long, bytes: Long) -> String,
): String {
    if (entries.isEmpty()) return ""
    val start = index.coerceIn(0, entries.lastIndex)
    var media: GridEntry.Media? = null
    var i = start
    while (i < entries.size && media == null) {
        media = entries[i] as? GridEntry.Media
        i++
    }
    i = start
    while (i >= 0 && media == null) {
        media = entries[i] as? GridEntry.Media
        i--
    }
    val item = media?.item ?: return ""
    return when (sort) {
        GallerySort.NAME -> item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        else -> format(item.dateAdded * 1000L, item.sizeBytes)
    }
}
