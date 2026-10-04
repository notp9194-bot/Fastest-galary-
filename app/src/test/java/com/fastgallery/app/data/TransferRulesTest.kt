package com.fastgallery.app.data

import com.fastgallery.app.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferRulesTest {
    private fun src(id: Long, bucket: String = "Camera", path: String = "DCIM/Camera/") =
        testItem(id, bucketName = bucket).copy(relativePath = path)

    @Test
    fun undoNeedsApi29AndCompletedTransfer() {
        val s = listOf(src(1))
        assertTrue(TransferRules.canUndo(29, move = false, completed = true, undoable = true, sources = s))
        assertFalse(TransferRules.canUndo(28, move = false, completed = true, undoable = true, sources = s))
        assertFalse(TransferRules.canUndo(34, move = false, completed = false, undoable = true, sources = s))
        assertFalse(TransferRules.canUndo(34, move = false, completed = true, undoable = false, sources = s))
        assertFalse(TransferRules.canUndo(34, move = false, completed = true, undoable = true, sources = emptyList()))
    }

    @Test
    fun moveUndoNeedsOriginalPathCopyUndoDoesNot() {
        val noPath = listOf(src(1), src(2, path = ""))
        assertFalse(TransferRules.canUndo(34, move = true, completed = true, undoable = true, sources = noPath))
        assertTrue(TransferRules.canUndo(34, move = false, completed = true, undoable = true, sources = noPath))
    }

    @Test
    fun undoJobsSendEachCopyBackToItsOwnAlbum() {
        val a = src(1, "Camera", "DCIM/Camera/")
        val b = src(2, "WhatsApp", "Pictures/WhatsApp/")
        val made = listOf(a to testItem(101), b to testItem(102))
        val jobs = TransferRules.undoJobs(made)
        assertEquals(listOf(101L, 102L), jobs.map { it.item.id })
        assertEquals(listOf("Camera", "WhatsApp"), jobs.map { it.folder })
        assertEquals(listOf("DCIM/Camera/", "Pictures/WhatsApp/"), jobs.map { it.destPath })
    }

    @Test
    fun undoLabelUsesAlbumNameOnlyWhenSingle() {
        assertEquals("Camera", TransferRules.undoLabel(listOf(src(1), src(2)), "original albums"))
        assertEquals("original albums", TransferRules.undoLabel(listOf(src(1), src(2, "WhatsApp")), "original albums"))
    }
}
