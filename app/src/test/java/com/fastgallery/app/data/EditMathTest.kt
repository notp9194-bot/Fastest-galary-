package com.fastgallery.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.cos
import kotlin.math.sin

/** Editor ka pure math: straighten zoom aur brightness/contrast/saturation colour matrix. */
@RunWith(RobolectricTestRunner::class)
class EditMathTest {
    /** 4x5 matrix ko (r, g, b) pixel par lagao (alpha 1). */
    private fun apply(m: FloatArray, r: Float, g: Float, b: Float): FloatArray =
        FloatArray(3) { row -> m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] + m[row * 5 + 4] }

    @Test
    fun straightenZeroDegreesNeedsNoZoom() {
        assertEquals(1f, MediaOperations.straightenCoverScale(4000, 3000, 0f), 0f)
    }

    @Test
    fun straightenIgnoresInvalidSize() {
        assertEquals(1f, MediaOperations.straightenCoverScale(0, 3000, 10f), 0f)
        assertEquals(1f, MediaOperations.straightenCoverScale(4000, 0, 10f), 0f)
    }

    @Test
    fun straightenSquareAt45DegreesIsRootTwoWithMargin() {
        assertEquals(1.4142135f * 1.004f, MediaOperations.straightenCoverScale(100, 100, 45f), 1e-3f)
    }

    @Test
    fun straightenIsSymmetricForLeftAndRight() {
        assertEquals(
            MediaOperations.straightenCoverScale(4000, 3000, 10f),
            MediaOperations.straightenCoverScale(4000, 3000, -10f),
            0f,
        )
    }

    @Test
    fun straightenScaleCoversTheFrameButNotMuchMore() {
        val w = 4000
        val h = 3000
        val degrees = 10f
        val r = Math.toRadians(degrees.toDouble())
        // Frame ke bounding box ko image ke andar aana hai: dono taraf ka zaroori zoom.
        val needW = (w * cos(r) + h * sin(r)) / w
        val needH = (w * sin(r) + h * cos(r)) / h
        val need = maxOf(needW, needH).toFloat()
        val scale = MediaOperations.straightenCoverScale(w, h, degrees)
        assertTrue("scale $scale must cover $need", scale >= need)
        assertTrue("scale $scale should stay within 1% of $need", scale <= need * 1.01f)
    }

    @Test
    fun neutralColorValuesWithOriginalFilterGiveNoMatrix() {
        assertNull(MediaOperations.colorMatrix("Original", 0f, 0f, 0f))
    }

    @Test
    fun neutralSlidersReturnTheFilterMatrixUntouched() {
        assertArrayEquals(
            MediaOperations.filterMatrix("Warm"),
            MediaOperations.colorMatrix("Warm", 0f, 0f, 0f),
            0f,
        )
    }

    @Test
    fun brightnessShiftsEveryChannelByTheSameAmount() {
        val m = MediaOperations.colorMatrix("Original", 50f, 0f, 0f)!!
        // +50 => offset 40 (+100 = 80 ka aadha).
        val out = apply(m, 0f, 0f, 0f)
        assertEquals(40f, out[0], 1e-3f)
        assertEquals(40f, out[1], 1e-3f)
        assertEquals(40f, out[2], 1e-3f)
    }

    @Test
    fun contrastKeepsMiddleGreyAndPushesOthersAway() {
        val m = MediaOperations.colorMatrix("Original", 0f, 100f, 0f)!!
        assertEquals(128f, apply(m, 128f, 128f, 128f)[0], 1e-3f)
        assertEquals(256f, apply(m, 192f, 192f, 192f)[0], 1e-3f)
        assertEquals(0f, apply(m, 64f, 64f, 64f)[0], 1e-3f)
    }

    @Test
    fun fullDesaturationMakesAllChannelsEqual() {
        val m = MediaOperations.colorMatrix("Original", 0f, 0f, -100f)!!
        val out = apply(m, 200f, 100f, 50f)
        // Luminance: 0.213 * 200 + 0.715 * 100 + 0.072 * 50
        assertEquals(117.7f, out[0], 1e-2f)
        assertEquals(out[0], out[1], 1e-3f)
        assertEquals(out[0], out[2], 1e-3f)
    }

    @Test
    fun valuesBeyondSliderRangeAreClamped() {
        assertArrayEquals(
            MediaOperations.colorMatrix("Original", 100f, 0f, 0f),
            MediaOperations.colorMatrix("Original", 500f, 0f, 0f),
            0f,
        )
    }

    @Test
    fun concatAppliesFirstMatrixBeforeSecond() {
        val doubleRed = floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val plusTenRed = floatArrayOf(
            1f, 0f, 0f, 0f, 10f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        // Pehle double, phir +10: 5 -> 10 -> 20. Ulta: 5 -> 15 -> 30.
        assertEquals(20f, apply(MediaOperations.concatColorMatrices(doubleRed, plusTenRed), 5f, 0f, 0f)[0], 1e-3f)
        assertEquals(30f, apply(MediaOperations.concatColorMatrices(plusTenRed, doubleRed), 5f, 0f, 0f)[0], 1e-3f)
    }

    @Test
    fun imageEditDefaultsMeanNoChange() {
        val edit = ImageEdit()
        assertEquals(0f, edit.rotationDegrees, 0f)
        assertNull(edit.crop)
        assertEquals("Original", edit.filter)
        assertEquals(false, edit.flipHorizontal)
        assertEquals(false, edit.flipVertical)
        assertEquals(0f, edit.straightenDegrees, 0f)
        assertEquals(0f, edit.brightness, 0f)
        assertEquals(0f, edit.contrast, 0f)
        assertEquals(0f, edit.saturation, 0f)
    }
}
