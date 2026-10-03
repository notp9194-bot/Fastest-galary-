package com.fastgallery.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyThumbTest {
    @Test fun sampleSize_keepsShortSideAtLeastTarget() {
        // 3000x4000, target 360: 3000/8 = 375 >= 360, 3000/16 = 187 < 360 => 8
        assertEquals(8, legacyThumbSampleSize(3000, 4000, 360))
        assertEquals(8, legacyThumbSampleSize(4000, 3000, 360))
        // Chhoti image: koi downsample nahi.
        assertEquals(1, legacyThumbSampleSize(100, 80, 360))
        assertEquals(1, legacyThumbSampleSize(500, 400, 360))
        assertEquals(2, legacyThumbSampleSize(720, 900, 360))
    }

    @Test fun sampleSize_badInput() {
        assertEquals(1, legacyThumbSampleSize(0, 100, 360))
        assertEquals(1, legacyThumbSampleSize(100, 100, 0))
    }

    @Test fun targetSize_shortSideBecomesSize_aspectKept() {
        assertEquals(360 to 480, legacyThumbTargetSize(375, 500, 360))
        assertEquals(480 to 360, legacyThumbTargetSize(500, 375, 360))
        assertEquals(360 to 360, legacyThumbTargetSize(1000, 1000, 360))
    }

    @Test fun targetSize_neverUpscales() {
        assertEquals(100 to 80, legacyThumbTargetSize(100, 80, 360))
        assertEquals(360 to 200, legacyThumbTargetSize(360, 200, 360))
    }

    @Test fun targetSize_capsVeryLongImages() {
        // Panorama 2000x200, size 100: seedha scale 0.5 => lambi side 1000 > 300, isliye 300 par cap (scale 0.15).
        val (w, h) = legacyThumbTargetSize(2000, 200, 100)
        assertEquals(300, w)
        assertEquals(30, h)
        assertTrue(w.toFloat() / h > 9.9f && w.toFloat() / h < 10.1f)
    }
}
