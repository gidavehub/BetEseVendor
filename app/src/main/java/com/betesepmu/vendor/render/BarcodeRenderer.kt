package com.betesepmu.vendor.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.escpos.BarcodeType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders QR codes and 1D barcodes to [Bitmap]s with ZXing. Used as the universal
 * fallback when a head rejects the native `GS k` / `GS ( k` commands, and to show codes
 * in the on-screen preview. Native commands stay the default (sharper, faster); this is
 * the safety net that makes "it always prints something" true.
 */
object BarcodeRenderer {

    fun qr(content: String, sizePx: Int = 360, ecLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ecLevel,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        return matrix.toBitmap()
    }

    fun barcode(content: String, type: BarcodeType, widthPx: Int = 360, heightPx: Int = 120): Bitmap {
        val format = type.toZxing()
        val hints = mapOf(EncodeHintType.MARGIN to 4)
        val matrix = MultiFormatWriter().encode(content, format, widthPx, heightPx, hints)
        return matrix.toBitmap()
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val bmp = createBitmap(width, height)
        val px = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) px[row + x] = if (get(x, y)) Color.BLACK else Color.WHITE
        }
        bmp.setPixels(px, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun BarcodeType.toZxing(): BarcodeFormat = when (this) {
        BarcodeType.UPC_A -> BarcodeFormat.UPC_A
        BarcodeType.UPC_E -> BarcodeFormat.UPC_E
        BarcodeType.EAN13 -> BarcodeFormat.EAN_13
        BarcodeType.EAN8 -> BarcodeFormat.EAN_8
        BarcodeType.CODE39 -> BarcodeFormat.CODE_39
        BarcodeType.ITF -> BarcodeFormat.ITF
        BarcodeType.CODABAR -> BarcodeFormat.CODABAR
        BarcodeType.CODE93 -> BarcodeFormat.CODE_93
        BarcodeType.CODE128 -> BarcodeFormat.CODE_128
    }
}
