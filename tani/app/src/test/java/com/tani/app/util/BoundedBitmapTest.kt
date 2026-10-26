package com.tani.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedBitmapTest {
    @Test fun smallImagesAreDecodedAtFullSize() {
        assertEquals(1, sampleSizeFor(1200, 800, 1600))
        assertEquals(1, sampleSizeFor(1600, 1600, 1600))
    }

    @Test fun cameraPhotosAreSampledWithoutDroppingBelowTheTarget() {
        // A 48MP photo (8000x6000) is decoded at 2000x1500, not 8000x6000.
        assertEquals(4, sampleSizeFor(8000, 6000, 1600))
        assertEquals(2, sampleSizeFor(3200, 2400, 1600))
        assertEquals(1, sampleSizeFor(3199, 2400, 1600))
        assertEquals(4, sampleSizeFor(3000, 4096, 1024))
    }
}
