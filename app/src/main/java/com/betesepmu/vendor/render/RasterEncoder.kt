package com.betesepmu.vendor.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.core.DitherMode
import com.betesepmu.vendor.escpos.EscPos
import java.io.ByteArrayOutputStream

/**
 * Converts an Android [Bitmap] into `GS v 0` raster bytes the head understands.
 *
 * Pipeline: scale to the target dot width → greyscale → [Dithering] to 1-bit → pack
 * MSB-first into rows → emit in horizontal *bands* (cheap heads have small line buffers
 * and choke on a single huge raster, so we cap each band's height).
 */
object RasterEncoder {

    // Small bands are *much* more tolerant of cheap printers. A 384-wide × 255-row band
    // is ~12 KB of raster data inside a single GS v 0 — many BT thermal heads can't
    // process that quickly enough and stall, dropping everything after the image. 24-row
    // bands (~1.2 KB each) burn fast and let the head's command parser stay responsive,
    // and if a single band gets garbled the rest of the receipt still prints intact.
    private const val BAND_HEIGHT = 24

    /**
     * @param widthDots target width in dots; the bitmap is scaled to exactly this width.
     *                  Pass the paper's full dot width for full-bleed images.
     */
    fun encode(
        bitmap: Bitmap,
        widthDots: Int,
        mode: DitherMode = DitherMode.FLOYD_STEINBERG,
        threshold: Int = 128,
    ): ByteArray {
        val mono = toMono(bitmap, widthDots, mode, threshold)
        return pack(mono.bits, mono.width, mono.height)
    }

    /** Pack a 1-bit raster into one or more `GS v 0` commands. */
    fun pack(bits: BooleanArray, width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val bytesPerRow = (width + 7) / 8
        var y0 = 0
        while (y0 < height) {
            val band = minOf(BAND_HEIGHT, height - y0)
            // GS v 0 m xL xH yL yH
            out.write(byteArrayOf(EscPos.GS, 'v'.code.toByte(), '0'.code.toByte(), 0))
            out.write(byteArrayOf((bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte()))
            out.write(byteArrayOf((band and 0xFF).toByte(), ((band shr 8) and 0xFF).toByte()))
            for (y in y0 until y0 + band) {
                for (bx in 0 until bytesPerRow) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x < width && bits[y * width + x]) b = b or (0x80 shr bit)
                    }
                    out.write(b)
                }
            }
            y0 += band
        }
        return out.toByteArray()
    }

    /** A black/white bitmap showing exactly what the head will burn — used for previews. */
    fun ditheredPreview(
        bitmap: Bitmap,
        widthDots: Int,
        mode: DitherMode = DitherMode.FLOYD_STEINBERG,
        threshold: Int = 128,
    ): Bitmap {
        val mono = toMono(bitmap, widthDots, mode, threshold)
        val result = createBitmap(mono.width, mono.height)
        val px = IntArray(mono.width * mono.height) { if (mono.bits[it]) Color.BLACK else Color.WHITE }
        result.setPixels(px, 0, mono.width, 0, 0, mono.width, mono.height)
        return result
    }

    private data class Mono(val bits: BooleanArray, val width: Int, val height: Int)

    private fun toMono(bitmap: Bitmap, widthDots: Int, mode: DitherMode, threshold: Int): Mono {
        val w = widthDots.coerceAtLeast(8)
        val h = (bitmap.height.toLong() * w / bitmap.width).toInt().coerceAtLeast(1)
        val scaled = bitmap.scale(w, h)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = Dithering.toGrayscale(pixels)
        val bits = Dithering.dither(gray, w, h, mode, threshold)
        return Mono(bits, w, h)
    }
}
