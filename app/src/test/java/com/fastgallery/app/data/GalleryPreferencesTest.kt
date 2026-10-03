package com.fastgallery.app.data

import android.content.Context
import com.fastgallery.app.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GalleryPreferencesTest {
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
    }

    private fun rawPrefs() = ctx.getSharedPreferences("fast_gallery_preferences", Context.MODE_PRIVATE)

    // ---------- sort / filter / albumSort ----------

    @Test
    fun filter_defaultsToAll_andRoundTrips() {
        assertEquals(MediaFilter.ALL, GalleryPreferences.filter(ctx))
        GalleryPreferences.setFilter(ctx, MediaFilter.VIDEOS)
        assertEquals(MediaFilter.VIDEOS, GalleryPreferences.filter(ctx))
    }

    @Test
    fun filter_unknownStoredValue_fallsBackToAll() {
        rawPrefs().edit().putString("gallery_filter", "NOT_A_FILTER").commit()
        assertEquals(MediaFilter.ALL, GalleryPreferences.filter(ctx))
    }

    @Test
    fun sort_defaultsToDateNewest_roundTrips_andFallsBackOnGarbage() {
        assertEquals(GallerySort.DATE_NEWEST, GalleryPreferences.sort(ctx))
        GalleryPreferences.setSort(ctx, GallerySort.SIZE_LARGEST)
        assertEquals(GallerySort.SIZE_LARGEST, GalleryPreferences.sort(ctx))
        rawPrefs().edit().putString("gallery_sort", "???").commit()
        assertEquals(GallerySort.DATE_NEWEST, GalleryPreferences.sort(ctx))
    }

    @Test
    fun albumSort_defaultsToRecent_roundTrips_andFallsBackOnGarbage() {
        assertEquals(AlbumSort.RECENT, GalleryPreferences.albumSort(ctx))
        GalleryPreferences.setAlbumSort(ctx, AlbumSort.COUNT)
        assertEquals(AlbumSort.COUNT, GalleryPreferences.albumSort(ctx))
        rawPrefs().edit().putString("album_sort", "???").commit()
        assertEquals(AlbumSort.RECENT, GalleryPreferences.albumSort(ctx))
    }

    // ---------- favorites ----------

    @Test
    fun toggleFavorite_addsThenRemoves() {
        val item = testItem(1)
        assertFalse(GalleryPreferences.isFavorite(ctx, item))
        GalleryPreferences.toggleFavorite(ctx, item)
        assertTrue(GalleryPreferences.isFavorite(ctx, item))
        assertEquals(setOf(item.key), GalleryPreferences.favorites(ctx))
        GalleryPreferences.toggleFavorite(ctx, item)
        assertFalse(GalleryPreferences.isFavorite(ctx, item))
        assertTrue(GalleryPreferences.favorites(ctx).isEmpty())
    }

    // ---------- trash flags ----------

    @Test
    fun setTrashed_trueThenFalse() {
        val item = testItem(1)
        GalleryPreferences.setTrashed(ctx, item, true)
        assertTrue(GalleryPreferences.isTrashed(ctx, item))
        assertEquals(setOf(item.key), GalleryPreferences.trashed(ctx))
        GalleryPreferences.setTrashed(ctx, item, false)
        assertFalse(GalleryPreferences.isTrashed(ctx, item))
    }

    @Test
    fun setTrashedKeys_recordsTimestamp_andKeepsEarlierTimeOnRepeat() {
        val before = System.currentTimeMillis()
        GalleryPreferences.setTrashedKeys(ctx, listOf("content://x/1"), true)
        val first = GalleryPreferences.trashTimes(ctx).getValue("content://x/1")
        assertTrue(first >= before)

        rawPrefs().edit().putStringSet("trash", setOf("content://x/1|1000")).commit()
        GalleryPreferences.setTrashedKeys(ctx, listOf("content://x/1"), true)
        assertEquals(1000L, GalleryPreferences.trashTimes(ctx).getValue("content://x/1"))
    }

    @Test
    fun setTrashedKeys_emptyCollection_isNoOp() {
        GalleryPreferences.setTrashedKeys(ctx, emptyList(), true)
        assertTrue(GalleryPreferences.trashed(ctx).isEmpty())
    }

    @Test
    fun legacyTrashEntryWithoutTimestamp_getsCurrentTime_andIsPersisted() {
        rawPrefs().edit().putStringSet("trash", setOf("content://x/legacy")).commit()
        val before = System.currentTimeMillis()
        val times = GalleryPreferences.trashTimes(ctx)
        assertTrue(times.getValue("content://x/legacy") >= before)
        // Migrate hone ke baad raw set me "key|time" format hona chahiye.
        val raw = rawPrefs().getStringSet("trash", emptySet())!!
        assertTrue(raw.all { it.substringAfterLast('|').toLongOrNull() != null })
    }

    @Test
    fun trashEntryWithTimestamp_isParsedAsIs() {
        rawPrefs().edit().putStringSet("trash", setOf("content://x/2|123456")).commit()
        assertEquals(mapOf("content://x/2" to 123456L), GalleryPreferences.trashTimes(ctx))
    }

    // ---------- album sets ----------

    @Test
    fun hiddenLockedPinnedAlbums_addAndRemove() {
        GalleryPreferences.setAlbumHidden(ctx, 5, true)
        GalleryPreferences.setAlbumLocked(ctx, 6, true)
        GalleryPreferences.setAlbumPinned(ctx, 7, true)
        assertEquals(setOf("5"), GalleryPreferences.hiddenAlbums(ctx))
        assertEquals(setOf("6"), GalleryPreferences.lockedAlbums(ctx))
        assertEquals(setOf("7"), GalleryPreferences.pinnedAlbums(ctx))

        GalleryPreferences.setAlbumHidden(ctx, 5, false)
        GalleryPreferences.setAlbumLocked(ctx, 6, false)
        GalleryPreferences.setAlbumPinned(ctx, 7, false)
        assertTrue(GalleryPreferences.hiddenAlbums(ctx).isEmpty())
        assertTrue(GalleryPreferences.lockedAlbums(ctx).isEmpty())
        assertTrue(GalleryPreferences.pinnedAlbums(ctx).isEmpty())
    }

    // ---------- simple settings ----------

    @Test
    fun columns_defaultThree_andClampedToRange() {
        assertEquals(3, GalleryPreferences.columns(ctx))
        GalleryPreferences.setColumns(ctx, 100)
        assertEquals(8, GalleryPreferences.columns(ctx))
        GalleryPreferences.setColumns(ctx, 0)
        assertEquals(2, GalleryPreferences.columns(ctx))
        GalleryPreferences.setColumns(ctx, 5)
        assertEquals(5, GalleryPreferences.columns(ctx))
    }

    @Test
    fun themeAndVideoLoop_defaultsAndRoundTrip() {
        assertEquals("system", GalleryPreferences.theme(ctx))
        assertFalse(GalleryPreferences.videoLoop(ctx))
        GalleryPreferences.setTheme(ctx, "dark")
        GalleryPreferences.setVideoLoop(ctx, true)
        assertEquals("dark", GalleryPreferences.theme(ctx))
        assertTrue(GalleryPreferences.videoLoop(ctx))
    }

    // ---------- video positions ----------

    @Test
    fun videoPosition_saveReadAndClear() {
        assertEquals(0L, GalleryPreferences.videoPosition(ctx, "v1"))
        GalleryPreferences.setVideoPosition(ctx, "v1", 4200)
        assertEquals(4200L, GalleryPreferences.videoPosition(ctx, "v1"))
        GalleryPreferences.setVideoPosition(ctx, "v1", 0)
        assertEquals(0L, GalleryPreferences.videoPosition(ctx, "v1"))
    }

    @Test
    fun videoPosition_keyContainingPipe_survivesRoundTrip() {
        GalleryPreferences.setVideoPosition(ctx, "content://a|b", 777)
        assertEquals(777L, GalleryPreferences.videoPosition(ctx, "content://a|b"))
    }

    @Test
    fun videoPosition_keepsOnlyLatestHundred() {
        (0..100).forEach { GalleryPreferences.setVideoPosition(ctx, "k$it", 1000L + it) }
        val remaining = (0..100).count { GalleryPreferences.videoPosition(ctx, "k$it") > 0 }
        assertEquals(100, remaining)
    }

    @Test
    fun videoPosition_malformedStoredEntries_areSkipped() {
        rawPrefs().edit().putStringSet("video_positions", setOf("broken", "only|one", "good|500|99")).commit()
        assertEquals(500L, GalleryPreferences.videoPosition(ctx, "good"))
        assertEquals(0L, GalleryPreferences.videoPosition(ctx, "broken"))
    }
}
