package com.pdfimage.cropper

import com.pdfimage.cropper.crop.CropGeometry
import com.pdfimage.cropper.crop.Handle
import com.pdfimage.cropper.crop.NRect
import com.pdfimage.cropper.crop.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {

    @Test fun fromPoints_normalizesAndClamps() {
        val r = CropGeometry.fromPoints(0.8f, 0.9f, -0.5f, 0.2f)
        assertEquals(NRect(0f, 0.2f, 0.8f, 0.9f), r)
    }

    @Test fun move_staysInsidePage() {
        val r = CropGeometry.move(NRect(0.5f, 0.5f, 0.9f, 0.9f), 0.5f, 0.5f)
        assertEquals(1f, r.right, 1e-5f)
        assertEquals(1f, r.bottom, 1e-5f)
        assertEquals(0.4f, r.width, 1e-5f)
    }

    @Test fun resize_enforcesMinimumSizeAndBounds() {
        val r = CropGeometry.resize(NRect(0.2f, 0.2f, 0.6f, 0.6f), Handle.TL, 0.9f, 0.9f)
        assertTrue(r.width >= CropGeometry.MIN_SIZE - 1e-5f)
        assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f)
        val clamped = CropGeometry.resize(NRect(0.2f, 0.2f, 0.6f, 0.6f), Handle.BR, 2f, 2f)
        assertEquals(1f, clamped.right, 1e-5f)
    }

    @Test fun hitTest_prefersCornersThenEdgesThenBody() {
        assertEquals(Handle.TL, CropGeometry.hitTest(100f, 100f, 300f, 300f, 105f, 98f, 20f))
        assertEquals(Handle.L, CropGeometry.hitTest(100f, 100f, 300f, 300f, 102f, 200f, 20f))
        assertEquals(Handle.BODY, CropGeometry.hitTest(100f, 100f, 300f, 300f, 200f, 200f, 20f))
        assertEquals(Handle.NONE, CropGeometry.hitTest(100f, 100f, 300f, 300f, 500f, 500f, 20f))
    }

    @Test fun viewTransform_roundTripAtAnyZoom() {
        for (scale in listOf(1f, 2.5f, 8f)) {
            val t = ViewTransform.fit(1080f, 1800f, 1000f, 1414f, scale, -123f, 77f)
            for (n in listOf(0f, 0.25f, 0.5f, 1f)) {
                assertEquals(n, t.nx(t.sx(n)), 1e-4f)
                assertEquals(n, t.ny(t.sy(n)), 1e-4f)
            }
        }
    }

    @Test fun viewTransform_fitKeepsAspectRatio() {
        val t = ViewTransform.fit(1000f, 1000f, 500f, 1000f, 1f, 0f, 0f)
        assertEquals(500f, t.baseW, 1e-3f)
        assertEquals(1000f, t.baseH, 1e-3f)
        assertEquals(250f, t.baseLeft, 1e-3f)
    }

    @Test fun outputSize_matchesCropAtDpi() {
        // A4 is 595x842 pt. Half-width, quarter-height crop at 144 dpi (scale 2).
        val rect = NRect(0f, 0f, 0.5f, 0.25f)
        val s = CropGeometry.renderScale(rect, 595, 842, 144)
        assertEquals(2f, s, 1e-5f)
        val (w, h) = CropGeometry.outputSize(rect, 595, 842, s)
        assertEquals(595, w)
        assertEquals(421, h)
    }

    @Test fun renderScale_capsHugeOutputs() {
        val s = CropGeometry.renderScale(NRect.FULL, 5000, 5000, 300)
        val (w, h) = CropGeometry.outputSize(NRect.FULL, 5000, 5000, s)
        assertTrue(w <= CropGeometry.MAX_SIDE_PX && h <= CropGeometry.MAX_SIDE_PX)
    }
}
