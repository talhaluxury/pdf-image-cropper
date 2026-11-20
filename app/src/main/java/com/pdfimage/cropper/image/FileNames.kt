package com.pdfimage.cropper.image

import com.pdfimage.cropper.crop.ImageFormat
import java.util.Locale

object FileNames {
    /** e.g. PDF_Image_Page_01_Crop_02.jpg  (pageIndex is zero-based, cropNo is one-based) */
    fun build(pageIndex: Int, cropNo: Int, format: ImageFormat): String =
        String.format(Locale.US, "PDF_Image_Page_%02d_Crop_%02d.%s", pageIndex + 1, cropNo, format.ext)
}
