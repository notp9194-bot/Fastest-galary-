package com.fastgallery.app

import com.fastgallery.app.data.GallerySort
import com.fastgallery.app.data.MediaFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeriveDisplayItemsTest {
    private val a = testItem(1, name = "Charlie.jpg", dateAdded = DAY_START + 40, sizeBytes = 100, bucketId = 1, bucketName = "Camera")
    private val b = testItem(2, name = "alpha.mp4", mime = "video/mp4", isVideo = true, dateAdded = DAY_START + 30, sizeBytes = 900, bucketId = 1, bucketName = "Camera")
    private val c = testItem(3, name = "Delta.png", dateAdded = DAY_START + 20, sizeBytes = 500, bucketId = 2, bucketName = "Screenshots")
    private val d = testItem(4, name = "beta.gif", mime = "image/gif", dateAdded = DAY_START + 10, sizeBytes = 300, bucketId = 3, bucketName = "Memes")
    private val all = listOf(a, b, c, d)

    private fun ids(list: List<com.fastgallery.app.data.MediaItem>) = list.map { it.id }

    private fun gallery(
        trashKeys: Set<String> = emptySet(),
        trashItems: List<com.fastgallery.app.data.MediaItem> = emptyList(),
        favorites: Set<String> = emptySet(),
        hidden: Set<String> = emptySet(),
        locked: Set<String> = emptySet(),
        unlocked: Long? = null,
    ) = GalleryState(
        loading = false,
        items = all,
        trashKeys = trashKeys,
        trashItems = trashItems,
        favoriteKeys = favorites,
        hiddenAlbumIds = hidden,
        lockedAlbumIds = locked,
        unlockedAlbumId = unlocked,
    )

    // ---------- tabs ----------

    @Test
    fun photosTab_noRestrictions_returnsEverythingInOriginalOrder() {
        assertEquals(ids(all), ids(deriveDisplayItems(gallery(), GalleryQuery())))
    }

    @Test
    fun settingsTab_andAlbumsListTab_haveNoItems() {
        assertTrue(deriveDisplayItems(gallery(), GalleryQuery(tab = 4)).isEmpty())
        assertTrue(deriveDisplayItems(gallery(), GalleryQuery(tab = 1, albumId = null)).isEmpty())
    }

    @Test
    fun photosTab_hidesFlagTrashedItems() {
        val result = deriveDisplayItems(gallery(trashKeys = setOf(testKey(2))), GalleryQuery())
        assertEquals(listOf(1L, 3L, 4L), ids(result))
    }

    @Test
    fun photosTab_hidesHiddenAndLockedAlbums() {
        val result = deriveDisplayItems(gallery(hidden = setOf("2"), locked = setOf("3")), GalleryQuery())
        assertEquals(listOf(1L, 2L), ids(result))
    }

    @Test
    fun favoritesTab_onlyFavorites_notTrashed_notRestricted() {
        val g = gallery(
            favorites = setOf(testKey(1), testKey(2), testKey(3), testKey(4)),
            trashKeys = setOf(testKey(1)),
            hidden = setOf("3"),
        )
        assertEquals(listOf(2L, 3L), ids(deriveDisplayItems(g, GalleryQuery(tab = 2))))
    }

    @Test
    fun trashTab_combinesSystemTrashAndFlagged_newestFirst_andSkipsRestricted() {
        val systemTrashed = testItem(9, dateAdded = DAY_START + 35, bucketId = 5, isTrashed = true)
        val hiddenTrashed = testItem(10, dateAdded = DAY_START + 99, bucketId = 7, isTrashed = true)
        val g = gallery(
            trashItems = listOf(hiddenTrashed, systemTrashed),
            trashKeys = setOf(testKey(3)),
            hidden = setOf("7"),
        )
        // systemTrashed (+35) pehle, phir flagged c (+20); hidden album ka item nahi.
        assertEquals(listOf(9L, 3L), ids(deriveDisplayItems(g, GalleryQuery(tab = 3))))
    }

    // ---------- albums ----------

    @Test
    fun albumView_showsOnlyThatBucket_withoutTrashed() {
        val g = gallery(trashKeys = setOf(testKey(2)))
        assertEquals(listOf(1L), ids(deriveDisplayItems(g, GalleryQuery(tab = 1, albumId = 1))))
    }

    @Test
    fun lockedAlbum_isEmptyUntilUnlocked() {
        val locked = gallery(locked = setOf("2"))
        assertTrue(deriveDisplayItems(locked, GalleryQuery(tab = 1, albumId = 2)).isEmpty())

        val wrongUnlock = gallery(locked = setOf("2"), unlocked = 1)
        assertTrue(deriveDisplayItems(wrongUnlock, GalleryQuery(tab = 1, albumId = 2)).isEmpty())

        val unlocked = gallery(locked = setOf("2"), unlocked = 2)
        assertEquals(listOf(3L), ids(deriveDisplayItems(unlocked, GalleryQuery(tab = 1, albumId = 2))))
    }

    // ---------- filter ----------

    @Test
    fun filter_isApplied() {
        assertEquals(listOf(2L), ids(deriveDisplayItems(gallery(), GalleryQuery(filter = MediaFilter.VIDEOS))))
        assertEquals(listOf(4L), ids(deriveDisplayItems(gallery(), GalleryQuery(filter = MediaFilter.GIFS))))
        assertEquals(listOf(1L, 3L), ids(deriveDisplayItems(gallery(), GalleryQuery(filter = MediaFilter.PHOTOS))))
    }

    @Test
    fun filter_worksInsideTrashAndFavoritesToo() {
        val g = gallery(trashKeys = setOf(testKey(1), testKey(2)), favorites = setOf(testKey(3), testKey(4)))
        assertEquals(listOf(2L), ids(deriveDisplayItems(g, GalleryQuery(tab = 3, filter = MediaFilter.VIDEOS))))
        assertEquals(listOf(4L), ids(deriveDisplayItems(g, GalleryQuery(tab = 2, filter = MediaFilter.GIFS))))
    }

    // ---------- search ----------

    @Test
    fun search_matchesNameOrAlbumName_caseInsensitive() {
        assertEquals(listOf(1L), ids(deriveDisplayItems(gallery(), GalleryQuery(search = "charlie"))))
        assertEquals(listOf(1L, 2L), ids(deriveDisplayItems(gallery(), GalleryQuery(search = "CAMERA"))))
    }

    @Test
    fun search_blankIsIgnored_noMatchGivesEmpty() {
        assertEquals(ids(all), ids(deriveDisplayItems(gallery(), GalleryQuery(search = "   "))))
        assertTrue(deriveDisplayItems(gallery(), GalleryQuery(search = "zzz")).isEmpty())
    }

    @Test
    fun search_andFilter_combine() {
        val result = deriveDisplayItems(gallery(), GalleryQuery(search = "camera", filter = MediaFilter.PHOTOS))
        assertEquals(listOf(1L), ids(result))
    }

    // ---------- sort ----------

    @Test
    fun sort_dateNewest_keepsInputOrder() {
        assertEquals(ids(all), ids(deriveDisplayItems(gallery(), GalleryQuery(sort = GallerySort.DATE_NEWEST))))
    }

    @Test
    fun sort_dateOldest() {
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(deriveDisplayItems(gallery(), GalleryQuery(sort = GallerySort.DATE_OLDEST))))
    }

    @Test
    fun sort_name_ignoresCase() {
        // alpha(2), beta(4), Charlie(1), Delta(3): case-sensitive sort hota to Charlie/Delta pehle aate.
        assertEquals(listOf(2L, 4L, 1L, 3L), ids(deriveDisplayItems(gallery(), GalleryQuery(sort = GallerySort.NAME))))
    }

    @Test
    fun sort_size_largestFirst() {
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(deriveDisplayItems(gallery(), GalleryQuery(sort = GallerySort.SIZE_LARGEST))))
    }
}
