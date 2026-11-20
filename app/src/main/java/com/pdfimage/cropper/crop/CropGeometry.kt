package com.pdfimage.cropper.crop

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Handle { NONE, DRAW, BODY, TL, TR, BL, BR, L, R, T, B }

/**
 * Maps between normalized page coordinates and screen pixels.
 * screen = (base + n * baseSize) * scale + offset, so the mapping is exact at every zoom level.
 */
class ViewTransform(
    val baseLeft: Float, val baseTop: Float, val baseW: Float, val baseH: Float,
    val scale: Float, val offsetX: Float, val offsetY: Float
) {
    fun sx(n: Float) = (baseLeft + n * baseW) * scale + offsetX
    fun sy(n: Float) = (baseTop + n * baseH) * scale + offsetY
    fun nx(x: Float) = ((x - offsetX) / scale - baseLeft) / baseW
    fun ny(y: Float) = ((y - offsetY) / scale - baseTop) / baseH

    companion object {
        fun fit(
            viewW: Float, viewH: Float, imgW: Float, imgH: Float,
            scale: Float, offsetX: Float, offsetY: Float
        ): ViewTransform {
            val s = min(viewW / imgW, viewH / imgH)
            val w = imgW * s
            val h = imgH * s
            return ViewTransform((viewW - w) / 2f, (viewH - h) / 2f, w, h, scale, offsetX, offsetY)
        }
    }
}

object CropGeometry {
    const val MIN_SIZE = 0.02f
    const val DISCARD_SIZE = 0.01f
    const val MAX_SIDE_PX = 8000

    private fun c(v: Float) = v.coerceIn(0f, 1f)

    fun fromPoints(ax: Float, ay: Float, bx: Float, by: Float): NRect {
        val x1 = c(ax); val x2 = c(bx); val y1 = c(ay); val y2 = c(by)
        return NRect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }

    fun move(r: NRect, dx: Float, dy: Float): NRect {
        val l = (r.left + dx).coerceIn(0f, max(0f, 1f - r.width))
        val t = (r.top + dy).coerceIn(0f, max(0f, 1f - r.height))
        return NRect(l, t, l + r.width, t + r.height)
    }

    fun resize(r: NRect, h: Handle, x: Float, y: Float): NRect {
        var l = r.left; var t = r.top; var rt = r.right; var b = r.bottom
        val px = c(x); val py = c(y)
        when (h) {
            Handle.TL -> { l = px; t = py }
            Handle.TR -> { rt = px; t = py }
            Handle.BL -> { l = px; b = py }
            Handle.BR -> { rt = px; b = py }
            Handle.L -> l = px
            Handle.R -> rt = px
            Handle.T -> t = py
            Handle.B -> b = py
            else -> Unit
        }
        if (h == Handle.TL || h == Handle.BL || h == Handle.L) l = min(l, rt - MIN_SIZE)
        if (h == Handle.TR || h == Handle.BR || h == Handle.R) rt = max(rt, l + MIN_SIZE)
        if (h == Handle.TL || h == Handle.TR || h == Handle.T) t = min(t, b - MIN_SIZE)
        if (h == Handle.BL || h == Handle.BR || h == Handle.B) b = max(b, t + MIN_SIZE)
        return NRect(l.coerceAtLeast(0f), t.coerceAtLeast(0f), rt.coerceAtMost(1f), b.coerceAtMost(1f))
    }

    /** Hit test in screen pixels. Corners win over edges, edges win over the body. */
    fun hitTest(l: Float, t: Float, r: Float, b: Float, x: Float, y: Float, slop: Float): Handle {
        fun near(ax: Float, ay: Float) = abs(x - ax) <= slop && abs(y - ay) <= slop
        if (near(l, t)) return Handle.TL
        if (near(r, t)) return Handle.TR
        if (near(l, b)) return Handle.BL
        if (near(r, b)) return Handle.BR
        val inX = x >= l - slop && x <= r + slop
        val inY = y >= t - slop && y <= b + slop
        if (inY && abs(x - l) <= slop) return Handle.L
        if (inY && abs(x - r) <= slop) return Handle.R
        if (inX && abs(y - t) <= slop) return Handle.T
        if (inX && abs(y - b) <= slop) return Handle.B
        if (x in l..r && y in t..b) return Handle.BODY
        return Handle.NONE
    }

    /** Pixels-per-point scale for a crop at [dpi], reduced if the longest side would exceed [maxSide]. */
    fun renderScale(r: NRect, pageWPts: Int, pageHPts: Int, dpi: Int, maxSide: Int = MAX_SIDE_PX): Float {
        val s = dpi / 72f
        val longest = max(r.width * pageWPts, r.height * pageHPts) * s
        return if (longest > maxSide) s * maxSide / longest else s
    }

    fun outputSize(r: NRect, pageWPts: Int, pageHPts: Int, scale: Float): Pair<Int, Int> =
        max(1, (r.width * pageWPts * scale).roundToInt()) to max(1, (r.height * pageHPts * scale).roundToInt())
}
