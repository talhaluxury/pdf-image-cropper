package com.pdfimage.cropper.crop

/** Rectangle in normalized page coordinates (0..1), independent of screen size, zoom or DPI. */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val FULL = NRect(0f, 0f, 1f, 1f)
    }
}

data class CropRegion(
    val id: String,
    val page: Int,
    val rect: NRect,
    val order: Long,
    val selected: Boolean = true
)

enum class ImageFormat(val ext: String, val mime: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

enum class Resolution(val label: String, val dpi: Int) {
    OPTIMIZED("Optimized (100 dpi)", 100),
    ORIGINAL("Original page size (72 dpi)", 72),
    STANDARD("Standard (150 dpi)", 150),
    HIGH("High quality (300 dpi)", 300)
}

data class ExportOptions(
    val format: ImageFormat = ImageFormat.JPG,
    val quality: Int = 90,
    val resolution: Resolution = Resolution.STANDARD
)

data class PdfInfo(val uri: String, val name: String, val pageCount: Int)
