package com.fastgallery.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Itni tez (screen height / sec) scroll ho to "fast scroll" maana jata hai: naye cells grey rehte hain, thumbnail load nahi hota. */
internal const val FAST_SCROLL_ENTER_SCREENS_PER_SEC = 2.5f

/** Fast scroll se bahar aane ki speed (hysteresis: enter se kam, taaki threshold par flicker na ho). */
internal const val FAST_SCROLL_EXIT_SCREENS_PER_SEC = 1.2f

/** Speed kitne ms ke gap par naapi jati hai. Bahar aane par thumbnails isi ke aas-paas load shuru karte hain. */
internal const val FAST_SCROLL_SAMPLE_MS = 80L

/** Fade-in ka option (ms) `thumbImageRequest(fadeIn = true)` ke liye; grid ke cells ab fade nahi karte (tiny -> poora seedha). */
internal const val THUMB_FADE_IN_MS = 120

/**
 * Progressive load: fast scroll ke baad grey cell pehle itne chhote (px) thumbnail se bharta hai (kuch ms, kam memory),
 * phir poora thumbnail aata hai. Blurry preview turant dikhta hai, khaali grey nahi.
 */
internal const val TINY_THUMB_PX = 40

/** Scroll dheema hone par poore thumbnails ek saath nahi: cells itne slots me bante hain, har slot STEP ms baad. */
internal const val THUMB_STAGGER_SLOTS = 12
internal const val THUMB_STAGGER_STEP_MS = 14L

/** Is cell ke poore thumbnail ka delay (ms): index se 0..(SLOTS-1)*STEP ke beech, taaki decode ka burst fail jaye. */
internal fun thumbStaggerDelayMs(index: Int): Long = index.mod(THUMB_STAGGER_SLOTS) * THUMB_STAGGER_STEP_MS

/** Hysteresis: abhi fast hai to jab tak speed EXIT se upar hai fast; nahi hai to ENTER ya usse upar jate hi fast. */
internal fun nextFastScrollState(fast: Boolean, screensPerSec: Float): Boolean =
    if (fast) screensPerSec > FAST_SCROLL_EXIT_SCREENS_PER_SEC else screensPerSec >= FAST_SCROLL_ENTER_SCREENS_PER_SEC

/**
 * Grid kitni tez scroll ho raha hai: true = fast (fling / fast-scroller jump), false = dheere ya ruka hua.
 * Scroll chalte waqt hi sample hota hai (ruka hua grid koi kaam nahi karta). Speed ek beech wale visible item ke
 * pixel-offset badlav se nikalti hai (header/row height se asar nahi); item gayab ho gaya = bada jump = fast.
 */
@Composable
internal fun rememberFastScrolling(gridState: LazyGridState): State<Boolean> {
    val fast = remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) {
                fast.value = false
                return@collectLatest
            }
            fun tracked() = gridState.layoutInfo.visibleItemsInfo.let { it.getOrNull(it.size / 2) }
            var lastIndex = -1
            var lastY = 0
            tracked()?.let { lastIndex = it.index; lastY = it.offset.y }
            var lastNanos = withFrameNanos { it }
            try {
                while (true) {
                    delay(FAST_SCROLL_SAMPLE_MS)
                    val now = withFrameNanos { it }
                    val info = gridState.layoutInfo
                    val viewportPx = (info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding)
                        .coerceAtLeast(1).toFloat()
                    val dtSec = (now - lastNanos) / 1_000_000_000f
                    lastNanos = now
                    val prev = if (lastIndex >= 0) info.visibleItemsInfo.firstOrNull { it.index == lastIndex } else null
                    val screensPerSec = when {
                        dtSec <= 0f || lastIndex < 0 -> 0f
                        prev == null -> FAST_SCROLL_ENTER_SCREENS_PER_SEC * 2f // item ab dikh hi nahi raha: bada jump
                        else -> abs(prev.offset.y - lastY) / viewportPx / dtSec
                    }
                    tracked()?.let { lastIndex = it.index; lastY = it.offset.y }
                    fast.value = nextFastScrollState(fast.value, screensPerSec)
                }
            } finally {
                // Scroll ruka (ya effect cancel hua) => ab thumbnails load hone do.
                fast.value = false
            }
        }
    }
    return fast
}
