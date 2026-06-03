package com.betesepmu.vendor.render

import com.betesepmu.vendor.core.DitherMode

/**
 * 1-bit reduction algorithms. A thermal dot is either burned or not, so every image
 * must pass through here. These functions are pure (operate on packed-ARGB / greyscale
 * [IntArray]s) and therefore unit-testable without an Android [android.graphics.Bitmap].
 *
 * Output is a [BooleanArray] in row-major order where `true` == burn a black dot.
 */
object Dithering {

    /** ITU-R BT.601 luma of a packed ARGB pixel, blended over white for transparency. */
    fun luminance(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        var r = (argb ushr 16) and 0xFF
        var g = (argb ushr 8) and 0xFF
        var b = argb and 0xFF
        if (a < 255) { // composite over white paper
            val inv = 255 - a
            r = (r * a + 255 * inv) / 255
            g = (g * a + 255 * inv) / 255
            b = (b * a + 255 * inv) / 255
        }
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    fun toGrayscale(pixels: IntArray): IntArray = IntArray(pixels.size) { luminance(pixels[it]) }

    fun dither(gray: IntArray, w: Int, h: Int, mode: DitherMode, threshold: Int = 128): BooleanArray =
        when (mode) {
            DitherMode.FLOYD_STEINBERG -> floydSteinberg(gray, w, h, threshold)
            DitherMode.ATKINSON -> atkinson(gray, w, h, threshold)
            DitherMode.THRESHOLD -> threshold(gray, w, h, threshold)
        }

    fun threshold(gray: IntArray, w: Int, h: Int, threshold: Int = 128): BooleanArray =
        BooleanArray(w * h) { gray[it] < threshold }

    /** Classic error-diffusion: best for photographs / shaded logos. */
    fun floydSteinberg(gray: IntArray, w: Int, h: Int, threshold: Int = 128): BooleanArray {
        val buf = IntArray(gray.size) { gray[it] }
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = buf[i].coerceIn(0, 255)
                val black = old < threshold
                out[i] = black
                val newVal = if (black) 0 else 255
                val err = old - newVal
                diffuse(buf, w, h, x + 1, y, err * 7 / 16)
                diffuse(buf, w, h, x - 1, y + 1, err * 3 / 16)
                diffuse(buf, w, h, x, y + 1, err * 5 / 16)
                diffuse(buf, w, h, x + 1, y + 1, err * 1 / 16)
            }
        }
        return out
    }

    /** Atkinson diffuses only 6/8 of the error — crisper text, lighter midtones. */
    fun atkinson(gray: IntArray, w: Int, h: Int, threshold: Int = 128): BooleanArray {
        val buf = IntArray(gray.size) { gray[it] }
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = buf[i].coerceIn(0, 255)
                val black = old < threshold
                out[i] = black
                val err = (old - if (black) 0 else 255) / 8
                diffuse(buf, w, h, x + 1, y, err)
                diffuse(buf, w, h, x + 2, y, err)
                diffuse(buf, w, h, x - 1, y + 1, err)
                diffuse(buf, w, h, x, y + 1, err)
                diffuse(buf, w, h, x + 1, y + 1, err)
                diffuse(buf, w, h, x, y + 2, err)
            }
        }
        return out
    }

    private fun diffuse(buf: IntArray, w: Int, h: Int, x: Int, y: Int, err: Int) {
        if (x < 0 || x >= w || y < 0 || y >= h) return
        val i = y * w + x
        buf[i] = (buf[i] + err).coerceIn(0, 255)
    }
}
