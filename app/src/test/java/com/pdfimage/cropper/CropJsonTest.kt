package com.pdfimage.cropper

import com.pdfimage.cropper.crop.CropRegion
import com.pdfimage.cropper.crop.NRect
import com.pdfimage.cropper.data.CropJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropJsonTest {
    @Test fun session_roundTrips() {
        val crops = listOf(
            CropRegion("a", 0, NRect(0.1f, 0.2f, 0.5f, 0.6f), 1L),
            CropRegion("b", 0, NRect(0.5f, 0.2f, 0.9f, 0.6f), 2L, selected = false),
            CropRegion("c", 4, NRect(0f, 0f, 1f, 1f), 3L)
        )
        val decoded = CropJson.decode(CropJson.encode(crops, setOf(0, 4)))
        assertEquals(crops, decoded.crops)
        assertEquals(setOf(0, 4), decoded.pages)
    }

    @Test fun corruptOrMissingSession_isEmptyNotCrash() {
        assertTrue(CropJson.decode(null).crops.isEmpty())
        assertTrue(CropJson.decode("{not json").crops.isEmpty())
    }
}
