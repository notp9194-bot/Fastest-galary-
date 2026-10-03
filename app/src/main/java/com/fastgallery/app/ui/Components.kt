package com.fastgallery.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.fastgallery.app.R
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.data.isGif
import com.fastgallery.app.data.isRaw

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

/** Material "Palette" icon (extended icons dependency ke bina). Settings > Appearance. */
val PaletteIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Palette",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 " +
                "-0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5L16,16c2.76,0 5,-2.24 5,-5 0,-4.42 " +
                "-4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12z" +
                "M9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8z" +
                "M14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8z" +
                "M17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "Vibration" icon (extended icons dependency ke bina). Settings > Haptics. */
val VibrationIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Vibration",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M0,15h2L2,9L0,9v6zM3,17h2L5,7L3,7v10zM22,9v6h2L24,9h-2zM19,17h2L21,7h-2v10z" +
                "M16.5,3h-9C6.67,3 6,3.67 6,4.5v15c0,0.83 0.67,1.5 1.5,1.5h9c0.83,0 1.5,-0.67 1.5,-1.5v-15" +
                "c0,-0.83 -0.67,-1.5 -1.5,-1.5zM16,19L8,19L8,5h8v14z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
fun rememberImageRequest(uri: Uri, size: Int): ImageRequest {
    val ctx = LocalContext.current
    return remember(uri, size) { ImageRequest.Builder(ctx).data(uri).size(size).build() }
}

/**
 * Viewer ka decode size: screen ke bade side ka 1.5x (zoom me sharp rahe), 512..2560 px.
 * Display aur prefetch dono yahi use karte hain, taaki memory-cache entry same rahe.
 */
fun viewerDecodeSize(widthPx: Int, heightPx: Int): Int =
    (maxOf(widthPx, heightPx) * 1.5f).toInt().coerceIn(512, 2560)

/**
 * Viewer ki full-size request. Display (AsyncImage) aur neighbour prefetch (imageLoader.enqueue) dono isi ko
 * use karte hain: data/size/scale same => memory-cache hit => swipe pe agli photo turant sharp dikhti hai.
 * Crossfade sirf yahin hai (grid ka ImageLoader crossfade(false) hi rehta hai, grid me fade se scroll bhari lagta).
 * Memory-cache hit pe Coil crossfade apne aap skip kar deta hai.
 */
fun viewerImageRequest(ctx: Context, uri: Uri, size: Int): ImageRequest =
    ImageRequest.Builder(ctx)
        .data(uri)
        .size(size)
        .scale(Scale.FIT)
        .crossfade(VIEWER_CROSSFADE_MS)
        .build()

private const val VIEWER_CROSSFADE_MS = 160

@Composable
fun rememberViewerRequest(uri: Uri, size: Int): ImageRequest {
    val ctx = LocalContext.current
    return remember(uri, size) { viewerImageRequest(ctx, uri, size) }
}

/**
 * Grid/album thumbs ki request: API 29+ pe MediaStore ke system-cached thumbnails (full decode se bahut fast),
 * API 26-28 pe `LegacyThumbFetcher` (sampled decode + apna disk cache). Dono `ThumbData` se chalte hain, to memory-cache
 * key (`ThumbKeyer`) har API par same banti hai. Display (AsyncImage) aur prefetch dono yahi use karte hain.
 */
fun thumbImageRequest(ctx: Context, uri: Uri, size: Int, fadeIn: Boolean = false): ImageRequest {
    val data: Any = ThumbData(uri, size)
    return ImageRequest.Builder(ctx)
        .data(data)
        .size(size)
        .precision(Precision.INEXACT)
        .apply { if (fadeIn) crossfade(THUMB_FADE_IN_MS) }
        .build()
}

@Composable
fun rememberThumbRequest(uri: Uri, size: Int, fadeIn: Boolean = false): ImageRequest {
    val ctx = LocalContext.current
    return remember(uri, size, fadeIn) { thumbImageRequest(ctx, uri, size, fadeIn) }
}

/** Thumbnail memory-cache me pehle se hai? (`ThumbKeyer` ki key, har API par.) */
internal fun isThumbCached(ctx: Context, uri: Uri, size: Int): Boolean =
    ctx.imageLoader.memoryCache?.get(MemoryCache.Key("thumb:$uri:$size")) != null

/** Video/GIF/RAW badge ka shape aur scrim: har cell ke har recompose par naya `RoundedCornerShape` na bane. */
private val BadgeShape = RoundedCornerShape(6.dp)
private val BadgeScrim = Color.Black.copy(alpha = 0.55f)

/** Thumb ka default `deferLoad`: kabhi defer nahi. Ek hi instance, taaki default lambda par recompose-skip na tute. */
private val NEVER_DEFER: () -> Boolean = { false }

/** Selected cell ka halka andhera (har draw par naya `Color` na bane). */
private val SelectedScrim = Color.Black.copy(alpha = 0.28f)

/**
 * Grid ke saare cells ka selection / favorite ka ek shared "view".
 *
 * Pehle `selected` / `favorite` har cell ko Boolean ki tarah milte the, to selection badalte hi (drag-select ke har step)
 * MediaGrid ka item block dobara chalta, har cell ko naye lambdas milte aur saare visible cells recompose hote.
 * Ab cells ko ye ek stable holder milta hai (jo kabhi badalta nahi); har cell `derivedStateOf` se sirf apni membership
 * padhta hai, to sirf wahi cells recompose hote jinki value sach me flip hui.
 *
 * `referentialEqualityPolicy`: naya Set instance aate hi readers dobara jaanch lete hain, badi selection par O(n) `equals` nahi.
 * Likhne wala sirf `MediaGrid` hai (composition me, `rememberUpdatedState` ki tarah).
 */
@Stable
class GridMarks {
    var selected: Set<String> by mutableStateOf<Set<String>>(emptySet(), referentialEqualityPolicy())
    var favorites: Set<String> by mutableStateOf<Set<String>>(emptySet(), referentialEqualityPolicy())
}

/**
 * Grid ka ek media cell.
 *
 * Tap ya long-press yahan handle nahi hota: `MediaGrid` ek hi grid-level gesture se hit-test karke khud `onActivate` /
 * drag-select chalata hai (pehle har cell par alag `clickable` + `Interaction` state + ripple tha). Yahan sirf TalkBack ke
 * liye semantics hain: "activate" (`onActivate`) aur "select" (`onSelectAction`). Dono lambda poore grid ke liye ek hi
 * instance hain, isliye cell skip ho sakta hai.
 *
 * `selected` / `favorite` `marks` se derive hote hain (GridMarks ka doc dekho). Badge aur overlay alag composables me padhte
 * hain, aur semantics block me padhte hain: flip par sirf wahi hisse recompose / re-semantics hote hain, poora cell nahi.
 */
@Composable
fun Thumb(
    item: MediaItem,
    /** Viewer ka index (`GridEntry.Media.index`): `onActivate` ko wapas milta hai. */
    index: Int,
    sizePx: Int,
    marks: GridMarks,
    onActivate: (MediaItem, Int) -> Unit,
    onSelectAction: (MediaItem) -> Unit,
    /** Grid se aaya modifier (jaise animateItem()); sabse pehle lagta hai. */
    modifier: Modifier = Modifier,
    /**
     * true = fast scroll chal raha hai: jis cell ka thumbnail memory-cache me nahi, wo grey placeholder rehta hai
     * (decode shuru nahi hota). Scroll dheema/ruka hote hi load hota hai (halka fade-in). Jo cell ek baar load ho chuka
     * wo kabhi wapas grey nahi hota.
     *
     * Lambda isliye (Boolean nahi): grid ka "fast scroll" State composition me padha jaye to uske har badlav
     * (fling shuru/band) par saare visible cells recompose hote. Ab ise composition me nahi, sirf zarurat par
     * (grey cell ke liye LaunchedEffect me `snapshotFlow`) padhte hain, baaki cells ko kuch nahi hota.
     */
    deferLoad: () -> Boolean = NEVER_DEFER,
) {
    val ctx = LocalContext.current
    val startedLoaded = remember(item.uri, sizePx) {
        // withoutReadObservation: yahan State padhne se ye cell uske peeche recompose na ho.
        !Snapshot.withoutReadObservation { deferLoad() } || isThumbCached(ctx, item.uri, sizePx)
    }
    var loadNow by remember(item.uri, sizePx) { mutableStateOf(startedLoaded) }
    val currentDeferLoad by rememberUpdatedState(deferLoad)
    if (!loadNow) {
        // Sirf grey (abhi load na hua) cell: fast scroll khatam hone ka intezaar, phir load. Ek baar load => wapas grey nahi.
        LaunchedEffect(item.uri, sizePx) {
            snapshotFlow { currentDeferLoad() }.first { !it }
            loadNow = true
        }
    }
    // Is cell ki membership: sirf apni value flip hone par readers invalidate hote hain (baaki cells ko kuch nahi).
    val key = item.key
    val selected = remember(marks, key) { derivedStateOf { key in marks.selected } }
    val favorite = remember(marks, key) { derivedStateOf { key in marks.favorites } }
    // TalkBack ke labels (date, "Selected", "Favorite", "Select") cell banate waqt nahi, balki semantics ko jab
    // zarurat ho (TalkBack on) tabhi bante hain: pehle har cell par 4 stringResource + naya DateFormat banta tha.
    val configuration = LocalConfiguration.current
    val res = remember(ctx, configuration) { ctx.resources }
    // Badges: GIF/RAW label thumbnail ke wahi corner me aate hain jahan video ki duration (dono kabhi saath nahi).
    val typeBadge = remember(item.mime, item.name) {
        when {
            item.isVideo -> null
            item.isGif() -> R.string.badge_gif
            item.isRaw() -> R.string.badge_raw
            else -> null
        }
    }
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Ek hi TalkBack node: "Photo, 12 Mar 2025" + selected state. Touch tap/long-press grid-level gesture se aata hai;
            // TalkBack double-tap yahan ke `onClick` action se chalta hai (touch events ke bina).
            .semantics(mergeDescendants = true) {
                val dateLabel = ThumbDateFormatter.format(thumbDateMillis(item.dateTaken, item.dateAdded))
                contentDescription = if (item.isVideo) {
                    res.getString(R.string.thumb_video_desc, formatDuration(item.durationMs), dateLabel)
                } else {
                    res.getString(R.string.thumb_photo_desc, dateLabel)
                }
                val isSelected = selected.value
                val isFavorite = favorite.value
                if (isSelected || isFavorite) {
                    val selectedLabel = res.getString(R.string.thumb_selected)
                    val favoriteLabel = res.getString(R.string.thumb_favorite)
                    stateDescription = when {
                        isSelected && isFavorite -> "$selectedLabel, $favoriteLabel"
                        isSelected -> selectedLabel
                        else -> favoriteLabel
                    }
                }
                onClick { onActivate(item, index); true }
                onLongClick(label = res.getString(R.string.thumb_select_action)) { onSelectAction(item); true }
            }
    ) {
        if (loadNow) {
            AsyncImage(
                model = rememberThumbRequest(item.uri, sizePx, fadeIn = !startedLoaded),
                contentDescription = null, // label parent ke merged semantics me hai (lazy)
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(BadgeScrim, BadgeShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    remember(item.durationMs) { formatDuration(item.durationMs) },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (typeBadge != null) {
            Text(
                stringResource(typeBadge),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(BadgeScrim, BadgeShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        FavoriteBadge(favorite)
        SelectedOverlay(selected)
    }
}

/** Heart badge (bottom-left). Alag composable: favorite flip par sirf ye recompose hota hai, poora `Thumb` nahi. */
@Composable
private fun BoxScope.FavoriteBadge(favorite: State<Boolean>) {
    if (!favorite.value) return
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .padding(4.dp)
            .size(20.dp)
            .background(BadgeScrim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF6B81), modifier = Modifier.size(12.dp))
    }
}

/** Selection ka andhera + check. Alag composable: select/deselect par sirf wahi cell ka ye hissa recompose hota hai. */
@Composable
private fun SelectedOverlay(selected: State<Boolean>) {
    if (!selected.value) return
    Box(
        Modifier.fillMaxSize().background(SelectedScrim),
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

fun formatDuration(ms: Long): String {
    // String.format nahi: har call par Formatter + parse allocate hota tha (video cell bind par hazaron baar).
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val sb = StringBuilder(8)
    if (h > 0) {
        sb.append(h).append(':')
        if (m < 10) sb.append('0')
    }
    sb.append(m).append(':')
    if (sec < 10) sb.append('0')
    return sb.append(sec).toString()
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
