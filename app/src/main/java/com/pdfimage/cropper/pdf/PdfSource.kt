package com.pdfimage.cropper.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pdfimage.cropper.crop.CropGeometry
import com.pdfimage.cropper.crop.NRect
import java.io.Closeable
import java.io.IOException
import kotlin.math.roundToInt

class PdfOpenException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { PASSWORD, CORRUPT, EMPTY, UNREADABLE }
}

/**
 * Thin wrapper around [PdfRenderer]. PdfRenderer allows only one open page at a time,
 * so every access is serialized through [lock].
 */
class PdfSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    private val lock = Any()
    val pageCount: Int = renderer.pageCount

    fun pageSize(pageIndex: Int): Pair<Int, Int> = synchronized(lock) {
        val page = renderer.openPage(pageIndex)
        try { page.width to page.height } finally { page.close() }
    }

    /** Renders the whole page scaled to [targetWidth] pixels wide (used for thumbnails and the editor). */
    fun renderFit(pageIndex: Int, targetWidth: Int): Bitmap = synchronized(lock) {
        val page = renderer.openPage(pageIndex)
        try {
            val s = targetWidth / page.width.toFloat()
            val h = (page.height * s).roundToInt().coerceAtLeast(1)
            draw(page, NRect.FULL, s, targetWidth, h, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        } finally { page.close() }
    }

    /**
     * Renders only [rect] of the page at [dpi]. Only the cropped area is ever allocated,
     * so exporting a small crop from a huge page stays cheap.
     */
    fun renderRegion(pageIndex: Int, rect: NRect, dpi: Int): Bitmap = synchronized(lock) {
        val page = renderer.openPage(pageIndex)
        try {
            val s = CropGeometry.renderScale(rect, page.width, page.height, dpi)
            val (bw, bh) = CropGeometry.outputSize(rect, page.width, page.height, s)
            draw(page, rect, s, bw, bh, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        } finally { page.close() }
    }

    private fun draw(page: PdfRenderer.Page, rect: NRect, s: Float, bw: Int, bh: Int, mode: Int): Bitmap {
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        try {
            bmp.eraseColor(Color.WHITE)
            val m = Matrix().apply {
                setScale(s, s)
                postTranslate(-rect.left * page.width * s, -rect.top * page.height * s)
            }
            page.render(bmp, null, m, mode)
            return bmp
        } catch (e: Throwable) {
            bmp.recycle()
            throw e
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfSource {
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                throw PdfOpenException(PdfOpenException.Kind.UNREADABLE, e)
            } ?: throw PdfOpenException(PdfOpenException.Kind.UNREADABLE)

            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: SecurityException) {
                pfd.close(); throw PdfOpenException(PdfOpenException.Kind.PASSWORD, e)
            } catch (e: IOException) {
                pfd.close(); throw PdfOpenException(PdfOpenException.Kind.CORRUPT, e)
            } catch (e: Exception) {
                pfd.close(); throw PdfOpenException(PdfOpenException.Kind.CORRUPT, e)
            }
            if (renderer.pageCount == 0) {
                renderer.close(); pfd.close()
                throw PdfOpenException(PdfOpenException.Kind.EMPTY)
            }
            return PdfSource(pfd, renderer)
        }
    }
}
