package com.pdfimage.cropper

import com.pdfimage.cropper.crop.ImageFormat
import com.pdfimage.cropper.image.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals as eq
import org.junit.Test

class FileNamesTest {
    @Test fun names_followSpec() {
        assertEquals("PDF_Image_Page_01_Crop_01.jpg", FileNames.build(0, 1, ImageFormat.JPG))
        assertEquals("PDF_Image_Page_02_Crop_03.png", FileNames.build(1, 3, ImageFormat.PNG))
        assertEquals("PDF_Image_Page_100_Crop_12.jpg", FileNames.build(99, 12, ImageFormat.JPG))
    }

    @Test fun sevenCropsAcrossThreePages_getSevenUniqueNames() {
        // Page 1: 2 crops, page 2: 4 crops, page 3: 1 crop (the spec's example).
        val perPage = mapOf(0 to 2, 1 to 4, 2 to 1)
        val names = perPage.flatMap { (page, n) -> (1..n).map { FileNames.build(page, it, ImageFormat.JPG) } }
        eq(7, names.size)
        eq(7, names.toSet().size)
    }
}
