package com.fastgallery.app.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class GridTapTest {
    @Test fun scaleOneIsIdentity() {
        val p = Offset(120f, 340f)
        val r = unscaleAround(p, Offset(50f, 60f), 1f)
        assertEquals(120f, r.x, 0.001f)
        assertEquals(340f, r.y, 0.001f)
    }

    @Test fun invertsGraphicsLayerScaleAroundOrigin() {
        // graphicsLayer: p = origin + scale * (q - origin). unscaleAround ko wapas q dena chahiye.
        val origin = Offset(100f, 200f)
        for (scale in listOf(0.4f, 0.88f, 1.12f, 2.5f)) {
            val q = Offset(150f, 260f)
            val p = Offset(origin.x + scale * (q.x - origin.x), origin.y + scale * (q.y - origin.y))
            val r = unscaleAround(p, origin, scale)
            assertEquals(q.x, r.x, 0.01f)
            assertEquals(q.y, r.y, 0.01f)
        }
    }

    @Test fun originItselfNeverMoves() {
        val origin = Offset(100f, 200f)
        val r = unscaleAround(origin, origin, 2f)
        assertEquals(100f, r.x, 0.001f)
        assertEquals(200f, r.y, 0.001f)
    }

    @Test fun badScaleFallsBackToIdentity() {
        val p = Offset(10f, 20f)
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val r = unscaleAround(p, Offset(5f, 5f), scale)
            assertEquals(10f, r.x, 0.001f)
            assertEquals(20f, r.y, 0.001f)
        }
    }
}
