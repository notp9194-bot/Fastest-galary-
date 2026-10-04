package com.fastgallery.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollPlaceholderTest {
    @Test fun slowScrollStaysNotFast() {
        assertFalse(nextFastScrollState(false, 0.5f))
        assertFalse(nextFastScrollState(false, FAST_SCROLL_ENTER_SCREENS_PER_SEC - 0.1f))
    }

    @Test fun enteringFastNeedsEnterSpeed() {
        assertTrue(nextFastScrollState(false, FAST_SCROLL_ENTER_SCREENS_PER_SEC))
        assertTrue(nextFastScrollState(false, 10f))
    }

    @Test fun hysteresisKeepsFastBetweenExitAndEnter() {
        val between = (FAST_SCROLL_EXIT_SCREENS_PER_SEC + FAST_SCROLL_ENTER_SCREENS_PER_SEC) / 2f
        assertTrue(nextFastScrollState(true, between))
        assertFalse(nextFastScrollState(false, between))
    }

    @Test fun leavesFastWhenSpeedDropsToExit() {
        assertFalse(nextFastScrollState(true, FAST_SCROLL_EXIT_SCREENS_PER_SEC))
        assertFalse(nextFastScrollState(true, 0f))
    }

    @Test fun exitIsBelowEnter() {
        assertTrue(FAST_SCROLL_EXIT_SCREENS_PER_SEC < FAST_SCROLL_ENTER_SCREENS_PER_SEC)
    }

    @Test
    fun prefetchCountIsRowsTimesColumnsButCapped() {
        assertEquals(PREFETCH_ROWS * 3, prefetchCellCount(3))
        assertEquals(PREFETCH_ROWS * 8, prefetchCellCount(8))
        assertEquals(PREFETCH_MAX_CELLS, prefetchCellCount(16)) // tablet: cap
        assertTrue(prefetchCellCount(16) >= 16) // kam se kam ek row
    }

    @Test
    fun thumbStaggerSpreadsCellsAcrossSlotsWithoutExceedingMax() {
        val max = (THUMB_STAGGER_SLOTS - 1) * THUMB_STAGGER_STEP_MS
        val delays = (0 until 200).map { thumbStaggerDelayMs(it) }
        assertEquals(0L, thumbStaggerDelayMs(0))
        assertEquals(THUMB_STAGGER_STEP_MS, thumbStaggerDelayMs(1))
        assertEquals(0L, thumbStaggerDelayMs(THUMB_STAGGER_SLOTS)) // slot wapas 0 se
        assertTrue(delays.all { it in 0..max })
        assertEquals(THUMB_STAGGER_SLOTS, delays.distinct().size)
    }
}
