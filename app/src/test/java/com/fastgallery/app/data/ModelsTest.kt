package com.fastgallery.app.data

import com.fastgallery.app.DAY_SECONDS
import com.fastgallery.app.DAY_START
import com.fastgallery.app.testAlbum
import com.fastgallery.app.testItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class ModelsTest {
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

    // ---------- buildEntries ----------

    @Test
    fun buildEntries_emptyList_givesNoEntries() {
        assertTrue(buildEntries(emptyList()).isEmpty())
    }

    @Test
    fun buildEntries_sameDay_singleHeader() {
        val items = listOf(
            testItem(1, dateAdded = DAY_START + 10 * 3600),
            testItem(2, dateAdded = DAY_START + 3600),
        )
        val entries = buildEntries(items)
        assertEquals(3, entries.size)
        assertEquals("Wed, 15 Nov 2023", (entries[0] as GridEntry.Header).label)
        assertTrue(entries[1] is GridEntry.Media)
        assertTrue(entries[2] is GridEntry.Media)
    }

    @Test
    fun buildEntries_newDay_startsNewHeader() {
        val items = listOf(
            testItem(1, dateAdded = DAY_START + 100),
            testItem(2, dateAdded = DAY_START - 100),
        )
        val entries = buildEntries(items)
        assertEquals(4, entries.size)
        assertEquals("Wed, 15 Nov 2023", (entries[0] as GridEntry.Header).label)
        assertEquals("Tue, 14 Nov 2023", (entries[2] as GridEntry.Header).label)
    }

    @Test
    fun buildEntries_midnightBoundary_splitsAtExactSecond() {
        val items = listOf(
            testItem(1, dateAdded = DAY_START),
            testItem(2, dateAdded = DAY_START - 1),
        )
        val headers = buildEntries(items).filterIsInstance<GridEntry.Header>()
        assertEquals(2, headers.size)
    }

    @Test
    fun buildEntries_groupsByLocalTimezone() {
        // IST midnight (15 Nov 00:00) = 14 Nov 18:30 UTC. UTC me ye dono ek din ke hain, IST me alag.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val istMidnight = DAY_START - 19_800L
        val items = listOf(
            testItem(1, dateAdded = istMidnight),
            testItem(2, dateAdded = istMidnight - 60),
        )
        val headers = buildEntries(items).filterIsInstance<GridEntry.Header>()
        assertEquals(listOf("Wed, 15 Nov 2023", "Tue, 14 Nov 2023"), headers.map { it.label })
    }

    @Test
    fun buildEntries_mediaIndexIsPositionInInputList() {
        val items = listOf(
            testItem(1, dateAdded = DAY_START + 5),
            testItem(2, dateAdded = DAY_START + 4),
            testItem(3, dateAdded = DAY_START - DAY_SECONDS),
        )
        val media = buildEntries(items).filterIsInstance<GridEntry.Media>()
        assertEquals(listOf(0, 1, 2), media.map { it.index })
        assertEquals(items.map { it.key }, media.map { it.item.key })
    }

    @Test
    fun buildEntries_entryCountIsItemsPlusHeaders() {
        val items = (0 until 6).map { testItem(it.toLong(), dateAdded = DAY_START - it * DAY_SECONDS) }
        assertEquals(12, buildEntries(items).size)
    }

    @Test
    fun buildEntries_nonDateOrder_keepsHeaderKeysUnique() {
        // Name/Size sort me ek hi din ke items door-door aate hain. Repeat label ki key unique na ho
        // to LazyVerticalGrid "Key was already used" ke saath crash karta hai.
        val items = listOf(
            testItem(1, dateAdded = DAY_START + 10),
            testItem(2, dateAdded = DAY_START - DAY_SECONDS),
            testItem(3, dateAdded = DAY_START + 20),
            testItem(4, dateAdded = DAY_START - DAY_SECONDS),
        )
        val entries = buildEntries(items)
        assertEquals(4, entries.filterIsInstance<GridEntry.Header>().size)
        val keys = entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun headerKey_firstOccurrenceKeepsPlainLabelKey() {
        assertEquals("h_Wed, 15 Nov 2023", GridEntry.Header("Wed, 15 Nov 2023").key)
        assertFalse(GridEntry.Header("x", occurrence = 1).key == GridEntry.Header("x").key)
    }

    // ---------- buildAlbums ----------

    @Test
    fun buildAlbums_groupsByBucket_withCountAndFirstItemAsCover() {
        val items = listOf(
            testItem(1, bucketId = 10, bucketName = "Camera", dateAdded = DAY_START + 30),
            testItem(2, bucketId = 20, bucketName = "Screenshots", dateAdded = DAY_START + 20),
            testItem(3, bucketId = 10, bucketName = "Camera", dateAdded = DAY_START + 10),
        )
        val albums = buildAlbums(items)
        assertEquals(2, albums.size)
        val camera = albums.first { it.id == 10L }
        assertEquals(2, camera.count)
        assertEquals("Camera", camera.name)
        assertEquals(items[0].key, camera.cover.key)
    }

    @Test
    fun buildAlbums_sortedByCoverDateNewestFirst() {
        val items = listOf(
            testItem(1, bucketId = 10, dateAdded = DAY_START + 1),
            testItem(2, bucketId = 20, dateAdded = DAY_START + 3),
            testItem(3, bucketId = 30, dateAdded = DAY_START + 2),
        )
        assertEquals(listOf(20L, 30L, 10L), buildAlbums(items).map { it.id })
    }

    // ---------- sortAlbums ----------

    private val albums = listOf(
        testAlbum(1, "camera", coverDate = DAY_START + 10, count = 50),
        testAlbum(2, "Downloads", coverDate = DAY_START + 30, count = 5),
        testAlbum(3, "Art", coverDate = DAY_START + 20, count = 200),
    )

    @Test
    fun sortAlbums_recent_newestCoverFirst() {
        assertEquals(listOf(2L, 3L, 1L), sortAlbums(albums, AlbumSort.RECENT, emptySet()).map { it.id })
    }

    @Test
    fun sortAlbums_name_isCaseInsensitive() {
        assertEquals(listOf(3L, 1L, 2L), sortAlbums(albums, AlbumSort.NAME, emptySet()).map { it.id })
    }

    @Test
    fun sortAlbums_count_largestFirst() {
        assertEquals(listOf(3L, 1L, 2L), sortAlbums(albums, AlbumSort.COUNT, emptySet()).map { it.id })
    }

    @Test
    fun sortAlbums_pinnedGoFirst_keepingChosenSortInsideEachGroup() {
        val result = sortAlbums(albums, AlbumSort.NAME, pinned = setOf("2", "1"))
        // Pinned (camera, Downloads) naam ke order me, phir baaki (Art).
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun sortAlbums_pinnedIdThatDoesNotExist_isIgnored() {
        val result = sortAlbums(albums, AlbumSort.COUNT, pinned = setOf("999"))
        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
    }

    @Test
    fun sortAlbums_emptyList() {
        assertTrue(sortAlbums(emptyList(), AlbumSort.RECENT, setOf("1")).isEmpty())
    }

    // ---------- matchesFilter / isRaw ----------

    private val photo = testItem(1, mime = "image/jpeg")
    private val video = testItem(2, name = "clip.mp4", mime = "video/mp4", isVideo = true)
    private val gif = testItem(3, name = "fun.gif", mime = "image/gif")
    private val dngByMime = testItem(4, name = "shot.dng", mime = "image/x-adobe-dng")
    private val nefByExtension = testItem(5, name = "IMG_0001.NEF", mime = "image/jpeg")

    @Test
    fun filter_all_acceptsEverything() {
        listOf(photo, video, gif, dngByMime, nefByExtension).forEach {
            assertTrue(it.matchesFilter(MediaFilter.ALL))
        }
    }

    @Test
    fun filter_photos_excludesVideosGifsAndRaw() {
        assertTrue(photo.matchesFilter(MediaFilter.PHOTOS))
        assertFalse(video.matchesFilter(MediaFilter.PHOTOS))
        assertFalse(gif.matchesFilter(MediaFilter.PHOTOS))
        assertFalse(dngByMime.matchesFilter(MediaFilter.PHOTOS))
        assertFalse(nefByExtension.matchesFilter(MediaFilter.PHOTOS))
    }

    @Test
    fun filter_videos_onlyVideos() {
        assertTrue(video.matchesFilter(MediaFilter.VIDEOS))
        assertFalse(photo.matchesFilter(MediaFilter.VIDEOS))
    }

    @Test
    fun filter_gifs_matchesMimeCaseInsensitive() {
        assertTrue(gif.matchesFilter(MediaFilter.GIFS))
        assertTrue(testItem(6, mime = "IMAGE/GIF").matchesFilter(MediaFilter.GIFS))
        assertFalse(photo.matchesFilter(MediaFilter.GIFS))
    }

    @Test
    fun filter_raw_matchesByMimeOrExtension() {
        assertTrue(dngByMime.matchesFilter(MediaFilter.RAW))
        assertTrue(nefByExtension.matchesFilter(MediaFilter.RAW))
        assertTrue(testItem(7, name = "a.cr3", mime = "application/octet-stream").matchesFilter(MediaFilter.RAW))
        assertFalse(photo.matchesFilter(MediaFilter.RAW))
        assertFalse(testItem(8, name = "noextension", mime = "image/jpeg").matchesFilter(MediaFilter.RAW))
    }

    @Test
    fun itemsToTransfer_copyKeepsAll_moveSkipsItemsAlreadyInDestination() {
        val inCamera = testItem(1).copy(relativePath = "DCIM/Camera/")
        val inPictures = testItem(2).copy(relativePath = "Pictures/Trip/")
        val items = listOf(inCamera, inPictures)

        assertEquals(items, itemsToTransfer(items, move = false, destRelativePath = "DCIM/Camera/"))
        assertEquals(listOf(inPictures), itemsToTransfer(items, move = true, destRelativePath = "DCIM/Camera/"))
        // Slash ka farak matter nahi karta.
        assertEquals(listOf(inPictures), itemsToTransfer(items, move = true, destRelativePath = "/DCIM/Camera"))
        // Naya album (path null): koi item pehle se wahan nahi.
        assertEquals(items, itemsToTransfer(items, move = true, destRelativePath = null))
        assertTrue(itemsToTransfer(listOf(inCamera), move = true, destRelativePath = "DCIM/Camera/").isEmpty())
    }
}
