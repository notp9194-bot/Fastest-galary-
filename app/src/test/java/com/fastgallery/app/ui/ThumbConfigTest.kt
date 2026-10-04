package com.fastgallery.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbConfigTest {
    @Test fun hardwareThumbsNeedApi28() {
        assertFalse(canUseHardwareThumb(26))
        assertFalse(canUseHardwareThumb(27))
        assertTrue(canUseHardwareThumb(28))
        assertTrue(canUseHardwareThumb(34))
    }
}
