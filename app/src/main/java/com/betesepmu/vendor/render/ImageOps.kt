package com.betesepmu.vendor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.escpos.Align

/** Bitmap helpers for the render pipeline. */
object ImageOps {

    /**
     * Scale [src] to [targetWidthDots] and place it on a white canvas exactly
     * [fullWidthDots] wide, justified per [align].
     *
     * Heads disagree about whether `ESC a` justifies a raster image, so we bake the
     * horizontal offset into the bitmap itself — the result always prints where intended.
     */
    fun placeOnWidth(src: Bitmap, fullWidthDots: Int, targetWidthDots: Int, align: Align): Bitmap {
        val tw = targetWidthDots.coerceIn(1, fullWidthDots)
        val th = (src.height.toLong() * tw / src.width).toInt().coerceAtLeast(1)
        val scaled = src.scale(tw, th)
        if (tw == fullWidthDots) return scaled
        val canvasBmp = createBitmap(fullWidthDots, th)
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.WHITE)
        val left = when (align) {
            Align.LEFT -> 0
            Align.CENTER -> (fullWidthDots - tw) / 2
            Align.RIGHT -> fullWidthDots - tw
        }
        canvas.drawBitmap(scaled, left.toFloat(), 0f, null)
        return canvasBmp
    }
}
