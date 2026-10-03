package com.fastgallery.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSwipeTest {
    private val w = 1000f
    private val minFling = 60f
    private val flingV = 1800f

    private fun resolve(tab: Int, raw: Float, v: Float = 0f, rtl: Boolean = false, count: Int = 5) =
        resolveTabSwipe(tab, count, raw, v, w, minFling, flingV, rtl)

    @Test fun stepDirectionLtrAndRtl() {
        assertEquals(-1, tabSwipeStep(50f, rtl = false))
        assertEquals(1, tabSwipeStep(-50f, rtl = false))
        assertEquals(1, tabSwipeStep(50f, rtl = true))
        assertEquals(-1, tabSwipeStep(-50f, rtl = true))
    }

    @Test fun farDragCommits() {
        assertEquals(2, resolve(tab = 1, raw = -300f))
        assertEquals(0, resolve(tab = 1, raw = 300f))
    }

    @Test fun shortSlowDragSnapsBack() {
        assertNull(resolve(tab = 1, raw = -150f, v = -200f))
    }

    @Test fun fastFlickCommitsEvenWhenShort() {
        assertEquals(2, resolve(tab = 1, raw = -120f, v = -2500f))
    }

    @Test fun flickAgainstDragDirectionDoesNotCommit() {
        assertNull(resolve(tab = 1, raw = -120f, v = 2500f))
    }

    @Test fun tinyFlickIgnored() {
        assertNull(resolve(tab = 1, raw = -20f, v = -3000f))
    }

    @Test fun noTabBeyondEnds() {
        assertNull(resolve(tab = 0, raw = 600f))
        assertNull(resolve(tab = 4, raw = -600f))
    }

    @Test fun rtlFlipsTarget() {
        assertEquals(2, resolve(tab = 1, raw = 300f, rtl = true))
    }

    @Test fun zeroWidthOrZeroDragIsNull() {
        assertNull(resolveTabSwipe(1, 5, 0f, 0f, w, minFling, flingV, false))
        assertNull(resolveTabSwipe(1, 5, 300f, 0f, 0f, minFling, flingV, false))
    }
}
