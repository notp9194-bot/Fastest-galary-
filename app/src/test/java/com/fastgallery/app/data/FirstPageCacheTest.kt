package com.fastgallery.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.fastgallery.app.testItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FirstPageCacheTest {
    private lateinit var context: Context
    private lateinit var cache: FirstPageCache

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        cache = FirstPageCache(context, "test_first_page.bin")
        cache.clear()
    }

    @After
    fun tearDown() = cache.clear()

    @Test
    fun read_withoutFile_isMiss() {
        assertNull(cache.read())
    }

    @Test
    fun saveThenRead_roundTripsFieldsAndRebuildsUri() {
        val items = listOf(
            testItem(7, bucketId = 42L, bucketName = "Screenshots", dateAdded = 1_700_000_000L),
            testItem(9, isVideo = true, mime = "video/mp4", name = "clip.mp4").copy(durationMs = 4_500L),
        )
        cache.save(items)

        val loaded = FirstPageCache(context, "test_first_page.bin").read()
        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertEquals(42L, loaded[0].bucketId) // hidden/locked filter isi pe chalta hai
        assertEquals("Screenshots", loaded[0].bucketName)
        assertEquals(
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 7),
            loaded[0].uri,
        )
        assertEquals(
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, 9),
            loaded[1].uri,
        )
        assertEquals(4_500L, loaded[1].durationMs)
        assertEquals(true, loaded[1].isVideo)
    }

    @Test
    fun save_respectsLimit() {
        cache.save((1L..10L).map { testItem(it) }, limit = 3)
        assertEquals(3, cache.read()!!.size)
    }

    @Test
    fun corruptFile_isMissAndDeleted() {
        File(context.noBackupFilesDir, "test_first_page.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(cache.read())
        assertEquals(false, File(context.noBackupFilesDir, "test_first_page.bin").exists())
    }

    @Test
    fun clear_removesCache() {
        cache.save(listOf(testItem(1)))
        cache.clear()
        assertNull(cache.read())
    }
}
