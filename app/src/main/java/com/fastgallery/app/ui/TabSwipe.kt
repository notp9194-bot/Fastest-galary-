package com.fastgallery.app.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Itna (width ka hissa) kheencho to chhodte hi tab badal jata hai. */
internal const val TAB_SWIPE_COMMIT_FRACTION = 0.28f

/** Ungli kis taraf gayi usse kaunsa tab: LTR me ungli daayein = pichhla tab, bayein = agla. RTL me ulta. */
internal fun tabSwipeStep(raw: Float, rtl: Boolean): Int = if ((raw > 0f) != rtl) -1 else 1

/**
 * Chhodte waqt faisla: kaunse tab par jaana hai, ya null (wapas wahin).
 * Do raaste: kaafi door tak kheencha (width ka 28%), ya chhota par tez flick (velocity ke saath).
 * Pehle/aakhri tab ke aage koi tab nahi, isliye null.
 */
internal fun resolveTabSwipe(
    tab: Int,
    tabCount: Int,
    raw: Float,
    velocityX: Float,
    widthPx: Float,
    minFlingDistancePx: Float,
    flingVelocityPx: Float,
    rtl: Boolean,
): Int? {
    if (raw == 0f || widthPx <= 0f) return null
    val target = tab + tabSwipeStep(raw, rtl)
    if (target !in 0 until tabCount) return null
    val distance = abs(raw)
    val farEnough = distance > widthPx * TAB_SWIPE_COMMIT_FRACTION
    val flung = distance > minFlingDistancePx &&
        abs(velocityX) > flingVelocityPx &&
        (velocityX > 0f) == (raw > 0f)
    return if (farEnough || flung) target else null
}

/**
 * Left/right swipe se tab badalna (Instagram/Google Photos jaisa feel):
 *  - Content ungli ke saath chalta hai (halka fade), pehle/aakhri tab ke aage rubber-band resistance.
 *  - Chhodne par: kaafi door ya tez flick -> purana content bahar slide, naya opposite side se andar aata hai
 *    (halka haptic tick). Warna spring se wapas.
 *  - Child gestures ko kuch nahi chhinta: ye Main pass me child ke BAAD dekhta hai, to drag-select, pinch, slider,
 *    fast scroller, vertical scroll pehle apna kaam kar lete hain. Gesture tabhi lagta hai jab chaal saaf horizontal ho
 *    (dx, dy se ~1.6x zyada) aur kisi ne use consume na kiya ho.
 *  - Screen ke dono kinare (~20dp) system back-gesture ke liye chhode gaye hain.
 * enabled = false (selection, search, album ke andar, viewer, picker...) me kuch nahi hota.
 */
@Composable
fun TabSwipeContainer(
    tab: Int,
    tabCount: Int,
    enabled: Boolean,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    val currentTab by rememberUpdatedState(tab)
    val currentOnTabChange by rememberUpdatedState(onTabChange)
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    // Slide animation chal rahi ho to naya swipe shuru nahi hota (tab beech me badalna avoid).
    var busy by remember { mutableStateOf(false) }

    Box(
        modifier
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled, tabCount, rtl) {
                if (!enabled) return@pointerInput
                val edgePx = 20.dp.toPx()
                val minFlingDistancePx = 24.dp.toPx()
                val flingVelocityPx = 700.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (busy || width <= 0f || down.position.x < edgePx || down.position.x > width - edgePx) {
                        return@awaitEachGesture
                    }
                    val slop = viewConfiguration.touchSlop
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    // 1) Intent: saaf horizontal chaal chahiye. Child ne consume kiya / do ungliyan / vertical ho gaya -> chhod do.
                    var dx = 0f
                    var dy = 0f
                    var locked = false
                    while (!locked) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        if (event.changes.count { it.pressed } >= 2) return@awaitEachGesture
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (abs(dy) > slop && abs(dy) >= abs(dx)) return@awaitEachGesture
                        if (abs(dx) > slop && abs(dx) > abs(dy) * 1.6f) {
                            locked = true
                            change.consume()
                        }
                    }

                    // 2) Drag: content ungli ke saath. Slop utni der ka offset hata do taaki shuru me jhatka na lage.
                    var raw = dx - slop * if (dx > 0f) 1f else -1f
                    fun applyOffset() {
                        val hasNeighbor = (currentTab + tabSwipeStep(raw, rtl)) in 0 until tabCount
                        offsetPx = if (hasNeighbor) raw else raw * 0.25f
                    }
                    applyOffset()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        raw += change.positionChange().x
                        change.consume()
                        applyOffset()
                    }

                    // 3) Chhodna: commit ya wapas.
                    val velocityX = tracker.calculateVelocity().x
                    val target = resolveTabSwipe(
                        tab = currentTab,
                        tabCount = tabCount,
                        raw = raw,
                        velocityX = velocityX,
                        widthPx = width,
                        minFlingDistancePx = minFlingDistancePx,
                        flingVelocityPx = flingVelocityPx,
                        rtl = rtl,
                    )
                    val from = offsetPx
                    busy = true
                    scope.launch {
                        try {
                            if (target != null) {
                                val dir = if (raw > 0f) 1f else -1f
                                animate(from, dir * width, animationSpec = tween(150, easing = FastOutLinearInEasing)) { v, _ ->
                                    offsetPx = v
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTabChange(target)
                                // Naya content opposite side se andar aata hai (same frame me, beech me khali frame nahi).
                                offsetPx = -dir * width * 0.22f
                                animate(offsetPx, 0f, animationSpec = tween(260, easing = LinearOutSlowInEasing)) { v, _ ->
                                    offsetPx = v
                                }
                            } else {
                                animate(
                                    from,
                                    0f,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                                ) { v, _ -> offsetPx = v }
                            }
                        } finally {
                            offsetPx = 0f
                            busy = false
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = offsetPx
                val w = widthPx.toFloat()
                if (w > 0f) alpha = 1f - 0.4f * (abs(offsetPx) / w).coerceIn(0f, 1f)
            },
        content = content,
    )
}
