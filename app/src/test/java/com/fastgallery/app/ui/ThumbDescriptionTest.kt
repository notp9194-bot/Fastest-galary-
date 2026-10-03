package com.fastgallery.app.ui

import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class ThumbDescriptionTest {
    private lateinit var oldZone: TimeZone

    @Before fun setUp() {
        oldZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun tearDown() = TimeZone.setDefault(oldZone)

    @Test fun usesDateTakenWhenPresent() {
        assertEquals(5_000L, thumbDateMillis(dateTaken = 5_000L, dateAdded = 99L))
    }

    @Test fun fallsBackToDateAddedSecondsAsMillis() {
        assertEquals(99_000L, thumbDateMillis(dateTaken = 0L, dateAdded = 99L))
        assertEquals(99_000L, thumbDateMillis(dateTaken = -1L, dateAdded = 99L))
    }

    @Test fun reusesOneFormatForSameLocale() {
        val f = CachedDateFormatter()
        val a = f.format(0L, Locale.US)
        val b = f.format(86_400_000L, Locale.US)
        val c = f.format(172_800_000L, Locale.US)
        assertEquals(1, f.builds)
        assertNotEquals(a, b)
        assertNotEquals(b, c)
    }

    @Test fun rebuildsWhenLocaleChanges() {
        val f = CachedDateFormatter()
        val us = f.format(0L, Locale.US)
        val de = f.format(0L, Locale.GERMANY)
        assertEquals(2, f.builds)
        assertNotEquals(us, de)
        f.format(0L, Locale.GERMANY)
        assertEquals(2, f.builds)
    }

    @Test fun formatsLikeJavaMediumDate() {
        val expected = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.US).format(java.util.Date(1_700_000_000_000L))
        assertEquals(expected, CachedDateFormatter().format(1_700_000_000_000L, Locale.US))
    }
}
