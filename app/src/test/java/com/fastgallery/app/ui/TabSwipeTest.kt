package com.fastgallery.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSwipeTest {
    private val w = 1000f
    private val minFling = 60f
    private val flingV = 1800f

    // w = 1000 => commit distance 10% = 100.
    private fun resolve(tab: Int, raw: Float, v: Float = 0f, rtl: Boolean = false, count: Int = 5, cap: Float = Float.MAX_VALUE) =
        resolveTabSwipe(tab, count, raw, v, w, minFling, flingV, rtl, cap)

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

    @Test fun smallDragJustPastTenPercentCommits() {
        assertEquals(2, resolve(tab = 1, raw = -110f))
        assertEquals(0, resolve(tab = 1, raw = 110f))
    }

    @Test fun veryShortSlowDragSnapsBack() {
        assertNull(resolve(tab = 1, raw = -50f, v = -200f))
    }

    @Test fun fastFlickCommitsEvenWhenShort() {
        assertEquals(2, resolve(tab = 1, raw = -80f, v = -2500f))
    }

    @Test fun flickAgainstDragDirectionDoesNotCommit() {
        assertNull(resolve(tab = 1, raw = -80f, v = 2500f))
    }

    @Test fun commitDistanceIsCappedOnWideScreens() {
        assertEquals(100f, tabSwipeCommitDistance(1000f), 0f)
        assertEquals(40f, tabSwipeCommitDistance(1000f, 40f), 0f)
        // Cap ke saath 50px bhi kaafi hai; bina cap ke nahi.
        assertEquals(2, resolve(tab = 1, raw = -50f, cap = 40f))
        assertNull(resolve(tab = 1, raw = -50f))
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
