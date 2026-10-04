package com.fastgallery.app.ui

import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import android.widget.OverScroller
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.fastgallery.app.data.GalleryPreferences
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings as SettingsGear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.Disposable
import com.fastgallery.app.R
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.GridEntry
import com.fastgallery.app.data.GridModel
import com.fastgallery.app.data.MediaItem
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import com.fastgallery.app.data.GallerySort
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlin.math.pow

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

/** Chaude screen (tablet / landscape) pe columns user ke chune hue count ke hisaab se proportionally badhte hain. */
fun effectiveColumns(columns: Int, screenWidthDp: Int): Int {
    if (columns == 0) return maxOf(2, screenWidthDp / 112)
    val factor = (screenWidthDp / 400f).coerceAtLeast(1f)
    return (columns.coerceIn(2, 8) * factor).roundToInt().coerceIn(2, 16)
}

private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 8

/** Slow scroll me scroll ki disha me itni rows ke thumbnails pehle se memory-cache me (0 = prefetch band). */
internal const val PREFETCH_ROWS = 3

/** Prefetch ke cells ki upar seema: tablet / zyada columns (16 tak) par rows * columns se memory-cache na bhar jaye. */
internal const val PREFETCH_MAX_CELLS = 36

/** Kitne cells aage prefetch karne hain: PREFETCH_ROWS rows, par PREFETCH_MAX_CELLS se zyada nahi (kam se kam 1 row). */
internal fun prefetchCellCount(columns: Int): Int =
    (PREFETCH_ROWS * columns).coerceAtMost(PREFETCH_MAX_CELLS).coerceAtLeast(columns)

/**
 * Grid cells par `Modifier.animateItem()` (trash/favorite/filter par smooth khisakna). A/B ke liye switch: false karke
 * `ScrollBenchmarks` chalao aur frame time pehle/baad compare karo. Fayda na dikhe to true hi rehne do.
 */
private const val GRID_ITEM_ANIMATION = true

/**
 * Pull-to-refresh: grid ke top pe neeche kheencho to MediaStore dobara load hota hai.
 * Indicator top bar ke neeche dikhta hai (topPadding), warna wo bar ke peeche chhup jaata.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
    ) { content() }
}

/**
 * Grid <-> Viewer bridge: viewer close hote waqt kisi bhi index ki thumbnail ka window rect chahiye
 * (user ne viewer me swipe karke doosri photo pe pahunch ke close kiya ho tab bhi).
 * Baseline tap ke waqt (system bars dikh rahe hote hain) ki grid-origin hai: viewer me bars chhupne se grid ka
 * layout badalta hai, isliye rect hamesha is baseline + item ke content-offset se banta hai.
 */
class GridOriginLookup {
    /** Index ki thumbnail ka window rect; zaroorat ho to grid ko scroll karke dikhata hai. Na mile to null. */
    var rectFor: (suspend (Int) -> Rect?)? = null
    internal var baseX = 0f
    internal var baseY = 0f
    /** Content area (padding ke bina) ki height aur sticky header ki height, tap ke waqt ki. */
    internal var contentHeight = 0f
    internal var topMargin = 0f
}

/** Grid ne aakhri baar thumbnails kis px size pe decode kiye (0 = abhi pata nahi). Viewer open-placeholder ke liye. */
@Volatile
internal var lastGridThumbPx: Int = 0

/**
 * Pinch ke dauran grid par `graphicsLayer` scale `scale` (pivot `origin`) lagta hai. Pointer position us layer ke bahar
 * (scale se pehle ke space) me aati hai, jabki LazyGrid ke item offsets layer ke andar ke hain. Ye position ko wapas
 * layer ke andar ke space me laata hai: `q = origin + (p - origin) / scale`. Scale 1 (ya be-matlab) = jaisa hai waisa.
 */
internal fun unscaleAround(p: Offset, origin: Offset, scale: Float): Offset =
    if (scale == 1f || !scale.isFinite() || scale <= 0f) p else origin + (p - origin) / scale

/**
 * Grid ke andar ke touch point `pos` ke neeche ka media cell (header / cells ke beech ki gap = null).
 * LazyGrid ke item offsets contentPadding (top bar ki height, left padding) ke bina hote hain; touch point grid ke
 * andar ka hai, isliye padding hata ke content coordinates me lakar match karte hain.
 * Drag-select aur tap dono yahi use karte hain.
 */
private fun LazyGridLayoutInfo.mediaCellAt(pos: Offset, originX: Float, entries: List<GridEntry>): LazyGridItemInfo? {
    val px = pos.x - originX
    val py = pos.y - beforeContentPadding
    val hit = visibleItemsInfo.firstOrNull {
        px >= it.offset.x && px < it.offset.x + it.size.width &&
            py >= it.offset.y && py < it.offset.y + it.size.height
    } ?: return null
    return if (entries.getOrNull(hit.index) is GridEntry.Media) hit else null
}

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    padding: PaddingValues,
    selected: Set<String>,
    columns: Int,
    /** Favorite items ke keys: grid me heart badge. */
    favoriteKeys: Set<String> = emptySet(),
    flingFriction: Float = 0.015f,
    /** Background me bana grid data (entries, day groups, header positions): composition me kuch banta nahi. */
    model: GridModel,
    /**
     * Scroll state bahar se: caller tab ke hisaab se alag state deta hai, taaki tab badalkar wapas aane par
     * position wahin mile. Filter/sort/search badalne par top pe wapas le jaana caller ka kaam hai.
     */
    gridState: LazyGridState = rememberLazyGridState(),
    sort: GallerySort = GallerySort.DATE_NEWEST,
    /** index + tapped thumbnail ka window rect (viewer open-transition ke liye; na mile to null). */
    onOpen: (Int, Rect?) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    /** Drag-to-select: poora naya selection (keys) ek saath set karta hai. */
    onSetSelection: (Set<String>) -> Unit,
    onPinchColumns: (Int) -> Unit,
    onLoadMore: () -> Unit,
    /** Scrubber pakadte hi: poori library load karwane ke liye (taaki handle poore range me chale). */
    onScrubStart: () -> Unit = {},
    /** false = picker (single) mode: date header tap se selection nahi hota. */
    daySelectEnabled: Boolean = true,
    /** Viewer ke close-transition ke liye: koi bhi index ki thumbnail ka rect yahan se milta hai. */
    originLookup: GridOriginLookup? = null,
) {
    val entries = model.entries
    val dayGroups = model.dayGroups
    // Fast scroll me (fling / scrubber jump) naye cells grey placeholder rehte hain; ruk ke thumbnail load hote hain.
    val fastScrolling = rememberFastScrolling(gridState)
    // Lambda (value nahi): Thumb ise composition me nahi padhta, to fling shuru/band par saare cells recompose nahi hote.
    val deferThumbLoad = remember(fastScrolling) { { fastScrolling.value } }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selected)
    // Cells ko selected/favorite Boolean ke roop me nahi, ek stable holder (`GridMarks`) ke roop me milta hai; har cell apni
    // membership khud derive karta hai. Isse selection badalne par (drag-select ke har step) MediaGrid ka item block aur
    // saare visible cells dobara nahi chalte, sirf jinki value flip hui. Likhna composition me (rememberUpdatedState jaisa):
    // cells layout me, MediaGrid ki composition apply hone ke baad compose hote hain.
    val marks = remember { GridMarks() }
    marks.selected = selected
    marks.favorites = favoriteKeys
    val currentOriginLookup by rememberUpdatedState(originLookup)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnToggleSelection by rememberUpdatedState(onToggleSelection)
    val currentDayGroups by rememberUpdatedState(dayGroups)
    val setSelection by rememberUpdatedState(onSetSelection)
    // remember: ek hi instance, taaki Thumb / header recompose-skip na tute (upar ke `State`s se hamesha taaza value padhta hai).
    val toggleDay: (Any) -> Unit = remember {
        { headerKey: Any ->
            val keys = currentDayGroups[headerKey].orEmpty()
            if (keys.isNotEmpty()) {
                val base = currentSelected
                val all = keys.all { it in base }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                setSelection(if (all) base - keys.toSet() else base + keys)
            }
        }
    }
    val selectAction: (MediaItem) -> Unit = remember { { item: MediaItem -> currentOnToggleSelection(item) } }
    val currentEntries by rememberUpdatedState(entries)
    val currentColumns by rememberUpdatedState(columns)
    val changeColumns by rememberUpdatedState(onPinchColumns)
    val gridFlingBehavior = rememberGridFlingBehavior(flingFriction)
    // Cell size ek hi baar nikalo (har Thumb me BoxWithConstraints subcompose bahut slow tha).
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val adaptiveColumns = maxOf(MIN_COLUMNS, screenWidthDp / 112)
    val gridColumns = effectiveColumns(columns, screenWidthDp)
    val thumbPx = remember(gridColumns, screenWidthDp, density.density) {
        val cols = gridColumns
        ((screenWidthDp - 2f * (cols - 1)) / cols * density.density).toInt().coerceIn(64, 1024)
    }
    // Viewer ki placeholder thumbnail isi size se mangti hai => grid ki memory-cache entry hit hoti hai.
    SideEffect { lastGridThumbPx = thumbPx }
    // Slow scroll / ruke grid me scroll ki disha me agli PREFETCH_ROWS rows ke thumbnails memory-cache me pehle se
    // bhar do (API 29+: MediaStore ke system-cached thumbs; API 26-28: LegacyThumbFetcher, 3 parallel tak). Fast scroll shuru hote hi (ya naya position aate hi)
    // pichhle prefetch cancel; wahi cells ab dikhne lagte hain to unki apni request chalti hai.
    val prefetchCtx = LocalContext.current
    LaunchedEffect(gridState, thumbPx, gridColumns) {
        val loader = prefetchCtx.imageLoader
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            when {
                fastScrolling.value || visible.isEmpty() -> null
                gridState.lastScrolledForward -> visible.last().index + 1
                else -> visible.first().index - 1
            }?.let { it to gridState.lastScrolledForward }
        }.distinctUntilChanged().collectLatest { target ->
            if (target == null) return@collectLatest
            val (from, forward) = target
            val list = currentEntries
            val pending = ArrayList<Disposable>()
            try {
                var queued = 0
                var i = from
                val limit = prefetchCellCount(gridColumns)
                while (queued < limit && i in list.indices) {
                    (list[i] as? GridEntry.Media)?.let { m ->
                        queued++
                        if (!isThumbCached(prefetchCtx, m.item.uri, thumbPx)) {
                            pending += loader.enqueue(thumbImageRequest(prefetchCtx, m.item.uri, thumbPx))
                        }
                    }
                    i += if (forward) 1 else -1
                }
                awaitCancellation()
            } finally {
                pending.forEach { it.dispose() }
            }
        }
    }
    // Live pinch: ungliyon ke saath grid smoothly scale hota hai; scale limit paar hote hi columns badalte hain
    // aur scale ko compensate kar dete hain (cell ka size continuous rehta hai). Chhodne par scale 1 pe settle.
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var pinchOrigin by remember { mutableStateOf(Offset.Zero) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Trash/favorite/filter se items badalne par cells smoothly khisakte hain (animateItem).
    // Pinch ke dauran band: columns badalne par scale pehle se compensate hota hai, dobara animate karne se jhatka aata.
    val animateItems by remember { derivedStateOf { GRID_ITEM_ANIMATION && pinchScale == 1f } }
    // Sticky date header overlay alag composable (StickyDateHeader): pinned header badalne par poora MediaGrid recompose nahi hota.
    // Yahan sirf uski height ka holder: viewer-open ke click me (composition ke bahar) padhi jati hai.
    val stickyHeight = remember { mutableIntStateOf(0) }
    LaunchedEffect(gridState, items.size) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisible ->
            val total = gridState.layoutInfo.totalItemsCount
            if (lastVisible >= 0 && lastVisible >= total - 18) onLoadMore()
        }
    }
    var gridWindowPos by remember { mutableStateOf(Offset.Zero) }
    val contentOriginXPx = with(density) { padding.calculateLeftPadding(androidx.compose.ui.platform.LocalLayoutDirection.current).toPx() }
    val currentOriginX by rememberUpdatedState(contentOriginXPx)
    // Cell ka "activate": selection mode me toggle, warna viewer kholna (tapped thumbnail ke window rect ke saath).
    // Touch tap (grid-level gesture) aur TalkBack double-tap (Thumb ka semantics onClick) dono yahi se guzarte hain.
    val activate: (MediaItem, Int) -> Unit = remember(gridState) {
        { item: MediaItem, index: Int ->
            if (currentSelected.isNotEmpty()) currentOnToggleSelection(item)
            else {
                val li = gridState.layoutInfo
                val info = li.visibleItemsInfo.firstOrNull { it.key == item.key }
                val rect = info?.let {
                    // Item offset contentPadding ke bina hai: window position me padding jodo.
                    val left = gridWindowPos.x + currentOriginX + it.offset.x
                    val top = gridWindowPos.y + li.beforeContentPadding + it.offset.y
                    Rect(left, top, left + it.size.width, top + it.size.height)
                }
                currentOriginLookup?.let { l ->
                    l.baseX = gridWindowPos.x + currentOriginX
                    l.baseY = gridWindowPos.y + li.beforeContentPadding
                    l.contentHeight = (li.viewportSize.height - li.beforeContentPadding - li.afterContentPadding).toFloat()
                    l.topMargin = stickyHeight.intValue.toFloat()
                }
                currentOnOpen(index, rect)
            }
        }
    }
    if (originLookup != null) {
        // entry.index (viewer ka index) -> grid entries ki position
        val entryPosByIndex = remember(entries) {
            HashMap<Int, Int>().also { m -> entries.forEachIndexed { pos, e -> if (e is GridEntry.Media) m[e.index] = pos } }
        }
        DisposableEffect(originLookup, entryPosByIndex) {
            val finder: suspend (Int) -> Rect? = finder@{ index ->
                val pos = entryPosByIndex[index] ?: return@finder null
                fun find() = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pos }
                var info = find()
                val comfortable = info != null &&
                    info.offset.y >= originLookup.topMargin &&
                    info.offset.y + info.size.height <= originLookup.contentHeight
                if (!comfortable) {
                    // Thumbnail screen ke bahar (ya header/bar ke neeche): grid ko beech me scroll karo (viewer ke peeche, dikhta nahi).
                    val cell = gridState.layoutInfo.visibleItemsInfo
                        .firstOrNull { currentEntries.getOrNull(it.index) is GridEntry.Media }?.size?.height ?: 0
                    gridState.scrollToItem(pos, -((originLookup.contentHeight - cell) / 2f).toInt())
                    repeat(2) { withFrameNanos { } }
                    info = find()
                }
                info?.let {
                    val left = originLookup.baseX + it.offset.x
                    val top = originLookup.baseY + it.offset.y
                    Rect(left, top, left + it.size.width, top + it.size.height)
                }
            }
            originLookup.rectFor = finder
            onDispose { if (originLookup.rectFor === finder) originLookup.rectFor = null }
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { gridWindowPos = it.positionInWindow() }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            flingBehavior = gridFlingBehavior,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Drag-to-select: long-press se selection shuru, phir ungli ghumao to beech ke sab select
                    // (wapas aao to shrink). Anchor pehle se selected tha to ye deselect mode hai.
                    // Kinare (top/bottom) ke paas ungli le jao to grid khud scroll hota hai.
                    val edgePx = 80.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

                        fun hitEntryIndex(pos: Offset): Int =
                            gridState.layoutInfo.mediaCellAt(pos, currentOriginX, currentEntries)?.index ?: -1

                        val anchorIndex = hitEntryIndex(down.position)
                        if (anchorIndex < 0) return@awaitEachGesture
                        val base = currentSelected
                        val anchorKey = (currentEntries[anchorIndex] as GridEntry.Media).item.key
                        val deselect = anchorKey in base
                        var lastIndex = anchorIndex
                        var lastPos = down.position

                        fun applyRange(index: Int) {
                            val from = minOf(anchorIndex, index)
                            val to = maxOf(anchorIndex, index)
                            val keys = ArrayList<String>(to - from + 1)
                            for (i in from..to) (currentEntries.getOrNull(i) as? GridEntry.Media)?.let { keys += it.item.key }
                            setSelection(if (deselect) base - keys.toSet() else base + keys)
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        applyRange(anchorIndex)

                        val autoScroll = scope.launch {
                            while (true) {
                                delay(16)
                                // Grid top/bottom bar ke neeche tak failta hai (contentPadding), isliye edge zone
                                // padding ke BAAD se shuru hota hai: dikhne wale content ke upar/neeche kinare par ungli
                                // le jane se hi auto-scroll chalta hai, ungli ko status/top bar tak nahi le jana padta.
                                val info = gridState.layoutInfo
                                val h = info.viewportSize.height.toFloat()
                                val topZoneEnd = info.beforeContentPadding + edgePx
                                val bottomZoneStart = h - info.afterContentPadding - edgePx
                                val speed = when {
                                    lastPos.y < topZoneEnd -> -(topZoneEnd - lastPos.y) / edgePx
                                    lastPos.y > bottomZoneStart -> (lastPos.y - bottomZoneStart) / edgePx
                                    else -> 0f
                                }.coerceIn(-1f, 1f)
                                if (speed != 0f) {
                                    gridState.scrollBy(speed * 28f)
                                    val idx = hitEntryIndex(lastPos)
                                    if (idx >= 0 && idx != lastIndex) {
                                        lastIndex = idx
                                        applyRange(idx)
                                    }
                                }
                            }
                        }
                        try {
                            // Initial pass: grid ka scroll hamare drag ko na chheen sake.
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } >= 2) break
                                lastPos = change.position
                                val idx = hitEntryIndex(lastPos)
                                if (idx >= 0 && idx != lastIndex) {
                                    lastIndex = idx
                                    applyRange(idx)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                event.changes.forEach { it.consume() }
                            } while (change.pressed)
                        } finally {
                            autoScroll.cancel()
                        }
                    }
                }
                // Tap: har cell par alag `clickable` nahi; ek hi grid-level hit-test. Scroll (inner) drag consume kare to tap
                // apne aap cancel; long-press / pinch Initial pass me consume karte hain, to unke baad tap nahi chalta.
                // Header ka apna `clickable` hai (wo tap consume kar leta hai, yahan hit-test header par null deta hai).
                .pointerInput(activate) {
                    detectTapGestures(
                        onTap = { pos ->
                            // Pinch settle ke dauran grid par scale laga hota hai: touch ko layer ke andar ke space me lao.
                            val p = unscaleAround(pos, pinchOrigin, pinchScale)
                            val hit = gridState.layoutInfo.mediaCellAt(p, currentOriginX, currentEntries)
                            val media = hit?.let { currentEntries.getOrNull(it.index) as? GridEntry.Media }
                            if (media != null) activate(media.item, media.index)
                        },
                    )
                }
                .pointerInput(adaptiveColumns) {
                    awaitEachGesture {
                        // Initial pass: do ungliyan hote hi hum pehle dekhte hain, taaki scroll/tap na chale.
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var lastSpan = 0f
                        var pinching = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val active = event.changes.filter { it.pressed }
                            if (active.size >= 2) {
                                val centerX = active.map { it.position.x }.average().toFloat()
                                val centerY = active.map { it.position.y }.average().toFloat()
                                val span = active.map {
                                    hypot(
                                        (it.position.x - centerX).toDouble(),
                                        (it.position.y - centerY).toDouble(),
                                    )
                                }.average().toFloat() * 2f
                                if (!pinching) {
                                    pinching = true
                                    settleJob?.cancel()
                                    lastSpan = span
                                } else if (lastSpan > 0f && span > 0f) {
                                    val zoom = span / lastSpan
                                    lastSpan = span
                                    pinchOrigin = Offset(centerX, centerY)
                                    var s = pinchScale * zoom
                                    val c = (if (currentColumns == 0) adaptiveColumns else currentColumns)
                                        .coerceIn(MIN_COLUMNS, MAX_COLUMNS)
                                    val inRatio = c.toFloat() / (c - 1).coerceAtLeast(1)
                                    val outRatio = (c + 1f) / c
                                    when {
                                        c > MIN_COLUMNS && s >= inRatio.pow(0.6f) -> {
                                            changeColumns(c - 1)
                                            s /= inRatio
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        c < MAX_COLUMNS && s <= outRatio.pow(-0.6f) -> {
                                            changeColumns(c + 1)
                                            s *= outRatio
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        // Limit pe halka rubber-band, uske aage nahi.
                                        else -> s = s.coerceIn(
                                            if (c < MAX_COLUMNS) 0.4f else 0.88f,
                                            if (c > MIN_COLUMNS) 2.5f else 1.12f,
                                        )
                                    }
                                    pinchScale = s
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pinching) {
                                // Ek ungli bachi hai: jab tak sab na uthein, tap/scroll mat chalne do.
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (pinching) {
                            settleJob?.cancel()
                            settleJob = scope.launch {
                                animate(
                                    initialValue = pinchScale,
                                    targetValue = 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                ) { value, _ -> pinchScale = value }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    val s = pinchScale
                    scaleX = s
                    scaleY = s
                    if (s != 1f) {
                        transformOrigin = TransformOrigin(
                            pinchOrigin.x / size.width.coerceAtLeast(1f),
                            pinchOrigin.y / size.height.coerceAtLeast(1f),
                        )
                        clip = true
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
                val itemModifier = if (animateItems) Modifier.animateItem() else Modifier
                when (entry) {
                    is GridEntry.Header -> GridDayHeader(
                        label = entry.label,
                        keys = dayGroups[entry.key],
                        marks = marks,
                        daySelectEnabled = daySelectEnabled,
                        onToggle = { toggleDay(entry.key) },
                        modifier = itemModifier,
                    )
                    is GridEntry.Media -> Thumb(
                        item = entry.item,
                        index = entry.index,
                        sizePx = thumbPx,
                        marks = marks,
                        onActivate = activate,
                        onSelectAction = selectAction,
                        modifier = itemModifier,
                        deferLoad = deferThumbLoad,
                    )
                }
            }
        }
        StickyDateHeader(
            gridState = gridState,
            model = model,
            topPadding = padding.calculateTopPadding(),
            heightPx = stickyHeight,
            marks = marks,
            daySelectEnabled = daySelectEnabled,
            onToggle = toggleDay,
        )
        if (items.size > 50) {
            FastScroller(
                gridState = gridState,
                entries = entries,
                sort = sort,
                contentPadding = padding,
                onScrubStart = onScrubStart,
            )
        }
    }
}


/**
 * Sticky date header overlay: content area ke top pe full-width. Pinned header hamesha top pe; agla header upar aate hi
 * use dheere upar dhakel deta hai (LazyVerticalGrid me stickyHeader nahi hai).
 *
 * Alag composable isliye: pinned header (`pinnedIndex`) har din ki boundary par badalta hai. Ye State MediaGrid ki body me
 * padha jata to scroll me har boundary par poora MediaGrid recompose hota; ab sirf ye overlay hota hai.
 * `pushPx` sirf graphicsLayer (draw phase) me padha jata hai, to har frame par recompose nahi.
 */
@Composable
private fun BoxScope.StickyDateHeader(
    gridState: LazyGridState,
    model: GridModel,
    topPadding: Dp,
    heightPx: MutableIntState,
    marks: GridMarks,
    daySelectEnabled: Boolean,
    onToggle: (Any) -> Unit,
) {
    val headerIndices = model.headerIndices
    // Pinned header = aakhri header jiska index pehle visible item se pehle (ya barabar) hai.
    val pinnedIndex by remember(headerIndices) {
        derivedStateOf {
            if (headerIndices.isEmpty()) -1
            else {
                val pos = java.util.Arrays.binarySearch(headerIndices, gridState.firstVisibleItemIndex)
                val at = if (pos >= 0) pos else -pos - 2
                if (at >= 0) headerIndices[at] else -1
            }
        }
    }
    // Agla header pinned header ke itna paas aaye to pinned header utna upar khisakta hai (<= 0).
    // Offsets ka origin (content padding) maane bina, pehle visible item se relative doori nikalte hain.
    val pushPx by remember(headerIndices) {
        derivedStateOf {
            val pinned = pinnedIndex
            val height = heightPx.intValue
            if (pinned < 0 || height <= 0) 0
            else {
                val next = headerIndices.getOrNull(java.util.Arrays.binarySearch(headerIndices, pinned) + 1)
                val info = gridState.layoutInfo.visibleItemsInfo
                val nextInfo = if (next == null) null else info.firstOrNull { it.index == next }
                val firstInfo = info.firstOrNull { it.index == gridState.firstVisibleItemIndex }
                if (nextInfo == null || firstInfo == null) 0
                else {
                    val contentTop = firstInfo.offset.y + gridState.firstVisibleItemScrollOffset
                    (nextInfo.offset.y - contentTop - height).coerceIn(-height, 0)
                }
            }
        }
    }
    val header = model.entries.getOrNull(pinnedIndex) as? GridEntry.Header ?: return
    val keys = model.dayGroups[header.key].orEmpty()
    // Content area ke top pe clip: pushed-up header top bar / filter chip ke peeche nahi, apni hi patti me gayab hota hai.
    val heightDp = with(LocalDensity.current) { heightPx.intValue.toDp() }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(top = topPadding)
            .fillMaxWidth()
            .then(if (heightPx.intValue > 0) Modifier.height(heightDp) else Modifier)
            .clipToBounds(),
    ) {
        GridDayHeader(
            label = header.label,
            keys = keys,
            marks = marks,
            daySelectEnabled = daySelectEnabled,
            onToggle = { onToggle(header.key) },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { if (it.height != heightPx.intValue) heightPx.intValue = it.height }
                .graphicsLayer { translationY = pushPx.toFloat() }
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

/**
 * Date header + uski selection state. `selectionMode` / `allSelected` yahin `marks` se derive hote hain (composition me
 * MediaGrid nahi padhta), to selection badalne par sirf wahi header recompose hota hai jiski value flip hui.
 * Grid ka header aur sticky overlay dono yehi use karte hain.
 */
@Composable
private fun GridDayHeader(
    label: String,
    keys: List<String>?,
    marks: GridMarks,
    daySelectEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionMode by remember(marks) { derivedStateOf { marks.selected.isNotEmpty() } }
    val allSelected by remember(marks, keys) {
        derivedStateOf {
            val sel = marks.selected
            !keys.isNullOrEmpty() && keys.all { it in sel }
        }
    }
    DateHeaderText(
        label,
        modifier,
        selectable = daySelectEnabled && !keys.isNullOrEmpty(),
        selectionMode = selectionMode,
        allSelected = allSelected,
        onToggle = onToggle,
    )
}

/**
 * Grid ka date header; sticky overlay bhi yehi use karta hai taaki dono ki height/style bilkul barabar rahe.
 * selectable: header tap se us din ki sab photos select/deselect. Selection mode me end pe circle dikhta hai
 * (jagah hamesha reserve rehti hai, isliye header ki height selection ke saath nahi badalti).
 */
@Composable
private fun DateHeaderText(
    label: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selectionMode: Boolean = false,
    allSelected: Boolean = false,
    onToggle: () -> Unit = {},
) {
    val clickLabel = stringResource(if (allSelected) R.string.header_deselect_day else R.string.header_select_day)
    Row(
        modifier
            .then(if (selectable) Modifier.clickable(onClickLabel = clickLabel, onClick = onToggle) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (selectable) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (selectionMode) {
                    if (allSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(20.dp)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    }
                }
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
        columns = GridCells.Adaptive(minSize = 160.dp),
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

/** Icon (24dp) + gap: section ke neeche ka content isi se align hota hai (premium settings look). */
private val SettingIndent = 40.dp

@Composable
private fun SettingIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}

@Composable
private fun SettingTitle(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Settings ka ek group: upar chhota label, neeche rounded card (premium apps jaisa). */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    padding: PaddingValues,
    theme: String,
    columns: Int,
    hiddenCount: Int,
    lockedCount: Int,
    sort: GallerySort,
    onSort: (GallerySort) -> Unit,
    slideshowMs: Int,
    onSlideshowMs: (Int) -> Unit,
    videoAutoplay: Boolean,
    onVideoAutoplay: (Boolean) -> Unit,
    videoMuted: Boolean,
    onVideoMuted: (Boolean) -> Unit,
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    trashCount: Int,
    onOpenTrash: () -> Unit,
    onTheme: (String) -> Unit,
    onColumns: (Int) -> Unit,
    onOpenHidden: () -> Unit,
    onOpenLocked: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // --- Display ---
        item {
            SettingsCard(stringResource(R.string.settings_display)) {
                SettingTitle(PaletteIcon, stringResource(R.string.settings_appearance))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = SettingIndent, top = 8.dp, bottom = 8.dp),
                ) {
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
                CardDivider()
                SettingTitle(ImageVector.vectorResource(R.drawable.ic_photos), stringResource(R.string.settings_grid_columns, columns.coerceAtLeast(2)))
                Slider(
                    value = columns.coerceIn(2, 8).toFloat(),
                    onValueChange = { onColumns(it.toInt().coerceIn(2, 8)) },
                    valueRange = 2f..8f,
                    steps = 5,
                    modifier = Modifier.padding(start = SettingIndent),
                )
                Text(
                    stringResource(R.string.settings_pinch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = SettingIndent, bottom = 8.dp),
                )
                CardDivider()
                SettingTitle(SortIcon, stringResource(R.string.settings_default_sort))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = SettingIndent, top = 8.dp, bottom = 8.dp),
                ) {
                    listOf(
                        GallerySort.DATE_NEWEST to R.string.sort_newest,
                        GallerySort.DATE_OLDEST to R.string.sort_oldest,
                        GallerySort.NAME to R.string.sort_name,
                        GallerySort.SIZE_LARGEST to R.string.sort_largest,
                    ).forEach { (value, labelRes) ->
                        FilterChip(selected = sort == value, onClick = { onSort(value) }, label = { Text(stringResource(labelRes)) })
                    }
                }
                CardDivider()
                SettingSwitchRow(VibrationIcon, stringResource(R.string.settings_haptics), haptics, onHaptics)
            }
        }
        // --- Video ---
        item {
            SettingsCard(stringResource(R.string.settings_video)) {
                SettingTitle(RepeatIcon, stringResource(R.string.settings_slideshow_speed))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = SettingIndent, top = 8.dp, bottom = 8.dp),
                ) {
                    listOf(2000, 3000, 5000, 8000).forEach { ms ->
                        FilterChip(
                            selected = slideshowMs == ms,
                            onClick = { onSlideshowMs(ms) },
                            label = { Text(stringResource(R.string.settings_seconds, (ms / 1000).toString())) },
                        )
                    }
                }
                CardDivider()
                SettingSwitchRow(Icons.Filled.PlayArrow, stringResource(R.string.settings_video_autoplay), videoAutoplay, onVideoAutoplay)
                SettingSwitchRow(if (videoMuted) VolumeOffIcon else VolumeUpIcon, stringResource(R.string.settings_video_muted), videoMuted, onVideoMuted)
            }
        }
        // --- Private albums ---
        item {
            SettingsCard(stringResource(R.string.settings_private_albums)) {
                Text(
                    stringResource(R.string.settings_private_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                SummaryRow(ImageVector.vectorResource(R.drawable.ic_albums), stringResource(R.string.hidden_albums), hiddenCount, onOpenHidden)
                CardDivider()
                SummaryRow(Icons.Filled.Lock, stringResource(R.string.locked_albums), lockedCount, onOpenLocked)
            }
        }
        // --- Trash ---
        item {
            SettingsCard(stringResource(R.string.nav_trash)) {
                Text(
                    stringResource(R.string.settings_trash_hint, (GalleryPreferences.TRASH_RETENTION_MS / 86_400_000L).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                SummaryRow(Icons.Filled.Delete, stringResource(R.string.settings_trash_open), trashCount, onOpenTrash)
            }
        }
        // --- About ---
        item {
            val ctx = LocalContext.current
            val versionName = remember(ctx) {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            val privacyUrl = stringResource(R.string.privacy_policy_url)
            SettingsCard(stringResource(R.string.settings_about)) {
                InfoRow(Icons.Filled.SettingsGear, stringResource(R.string.settings_version), versionName)
                if (privacyUrl.startsWith("https://")) {
                    CardDivider()
                    LinkRow(Icons.Filled.Lock, stringResource(R.string.settings_privacy_policy)) { openLink(ctx, privacyUrl) }
                }
                CardDivider()
                LinkRow(Icons.Filled.Favorite, stringResource(R.string.settings_rate_app)) { openPlayListing(ctx) }
            }
        }
    }
}

private fun openLink(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}

/** Play Store app me apni listing kholta hai; Play Store na ho to browser me. */
private fun openPlayListing(ctx: android.content.Context) {
    val pkg = ctx.packageName
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
    } catch (_: ActivityNotFoundException) {
        openLink(ctx, "https://play.google.com/store/apps/details?id=$pkg")
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(16.dp))
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
        Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp),
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
            HorizontalDivider()
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
