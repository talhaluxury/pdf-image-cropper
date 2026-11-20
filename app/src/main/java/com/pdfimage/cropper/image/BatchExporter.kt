package com.pdfimage.cropper.image

import android.graphics.Bitmap
import com.pdfimage.cropper.crop.CropRegion
import com.pdfimage.cropper.crop.ExportOptions
import com.pdfimage.cropper.pdf.PdfSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ItemResult(val name: String, val saved: SavedFile?, val error: String?)

/**
 * Exports every crop as its own image. Strictly sequential (one bitmap alive at a time),
 * which bounds memory use regardless of how many crops are selected.
 */
class BatchExporter(private val saver: GallerySaver) {

    suspend fun run(
        source: PdfSource,
        crops: List<CropRegion>,
        options: ExportOptions,
        onStart: (index: Int, total: Int, name: String) -> Unit,
        onItem: (ItemResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        val perPage = HashMap<Int, Int>()
        crops.forEachIndexed { i, crop ->
            ensureActive()
            val no = (perPage[crop.page] ?: 0) + 1
            perPage[crop.page] = no
            val name = FileNames.build(crop.page, no, options.format)
            onStart(i, crops.size, name)

            var bmp: Bitmap? = null
            val result = try {
                bmp = source.renderRegion(crop.page, crop.rect, options.resolution.dpi)
                ItemResult(name, saver.save(bmp, name, options.format, options.quality), null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                ItemResult(name, null, "Not enough memory - try a lower resolution")
            } catch (e: Exception) {
                ItemResult(name, null, describe(e))
            } finally {
                bmp?.recycle()
            }
            onItem(result)
        }
    }

    private fun describe(e: Exception): String {
        val m = e.message.orEmpty()
        return if (m.contains("ENOSPC") || m.contains("No space", ignoreCase = true)) "Storage is full"
        else if (m.isNotBlank()) m else "Unknown error"
    }
}
