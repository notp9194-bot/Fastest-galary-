package com.fastgallery.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {
    @Test fun underOneMinute() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9_999))
        assertEquals("0:59", formatDuration(59_000))
    }

    @Test fun minutes() {
        assertEquals("1:00", formatDuration(60_000))
        assertEquals("12:05", formatDuration(725_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test fun hours() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals("10:00:09", formatDuration(36_009_000))
    }
}
