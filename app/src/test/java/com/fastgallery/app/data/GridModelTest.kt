package com.fastgallery.app.data

import com.fastgallery.app.DAY_SECONDS
import com.fastgallery.app.DAY_START
import com.fastgallery.app.testItem
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class GridModelTest {
    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    @Test
    fun empty_returnsSharedEmptyModel() {
        assertSame(GridModel.EMPTY, buildGridModel(emptyList()))
    }

    @Test
    fun entriesMatchBuildEntries_andHeaderIndicesPointAtHeaders() {
        val items = listOf(
            testItem(1, dateAdded = DAY_START + DAY_SECONDS + 100),
            testItem(2, dateAdded = DAY_START + DAY_SECONDS + 50),
            testItem(3, dateAdded = DAY_START + 10),
        )
        val model = buildGridModel(items)

        assertEquals(buildEntries(items), model.entries)
        // Header, Media, Media, Header, Media
        assertArrayEquals(intArrayOf(0, 3), model.headerIndices)
        model.headerIndices.forEach { assertTrue(model.entries[it] is GridEntry.Header) }
    }

    @Test
    fun dayGroups_listMediaKeysUnderEachHeader() {
        val items = listOf(
            testItem(1, dateAdded = DAY_START + DAY_SECONDS + 100),
            testItem(2, dateAdded = DAY_START + DAY_SECONDS + 50),
            testItem(3, dateAdded = DAY_START + 10),
        )
        val model = buildGridModel(items)
        val firstHeader = model.entries[0].key
        val secondHeader = model.entries[3].key

        assertEquals(listOf(items[0].key, items[1].key), model.dayGroups[firstHeader])
        assertEquals(listOf(items[2].key), model.dayGroups[secondHeader])
        assertEquals(2, model.dayGroups.size)
    }
}
