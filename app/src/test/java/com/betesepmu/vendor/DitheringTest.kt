package com.betesepmu.vendor

import com.betesepmu.vendor.render.Dithering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DitheringTest {

    @Test fun luminance_of_pure_colours() {
        assertEquals(0, Dithering.luminance(0xFF000000.toInt()))   // black
        assertEquals(255, Dithering.luminance(0xFFFFFFFF.toInt())) // white
    }

    @Test fun transparent_pixels_composite_over_white() {
        // Fully transparent black should read as white paper.
        assertEquals(255, Dithering.luminance(0x00000000))
    }

    @Test fun threshold_marks_dark_pixels_black() {
        val gray = intArrayOf(10, 200, 127, 128)
        val bits = Dithering.threshold(gray, 4, 1, 128)
        assertTrue(bits[0])   // 10  < 128 -> black
        assertFalse(bits[1])  // 200 >= 128 -> white
        assertTrue(bits[2])   // 127 < 128 -> black
        assertFalse(bits[3])  // 128 not < 128 -> white
    }

    @Test fun floyd_steinberg_preserves_dimensions_and_black_field() {
        val gray = IntArray(16) { 0 } // all black
        val bits = Dithering.floydSteinberg(gray, 4, 4)
        assertEquals(16, bits.size)
        assertTrue(bits.all { it })
    }
}
