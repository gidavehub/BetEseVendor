package com.betesepmu.vendor.escpos

import java.io.ByteArrayOutputStream

/** Position of the human-readable interpretation (HRI) text around a 1D barcode. */
enum class HriPosition(val value: Int) { NONE(0), ABOVE(1), BELOW(2), BOTH(3) }

/**
 * 1D barcode symbologies, addressed through the `GS k m ...` (function B, m = 65..73)
 * form which carries an explicit data length and is the most portable.
 */
enum class BarcodeType(val code: Int) {
    UPC_A(65),
    UPC_E(66),
    EAN13(67),
    EAN8(68),
    CODE39(69),
    ITF(70),
    CODABAR(71),
    CODE93(72),
    CODE128(73),
}

/** QR error-correction level for the native `GS ( k` command. */
enum class QrErrorCorrection(val value: Int) { L(48), M(49), Q(50), H(51) }

/**
 * Builders for the printer's *native* barcode / QR commands. These are sharp and fast
 * because the head renders them itself. When a head rejects them, the rendering pipeline
 * falls back to a rasterised image (see `BarcodeRenderer`).
 */
object Barcodes {

    /**
     * Native 1D barcode via `GS k`.
     * @param widthModule module width 2..6 dots (`GS w n`).
     * @param heightDots bar height in dots (`GS h n`).
     */
    fun barcode(
        type: BarcodeType,
        data: String,
        heightDots: Int = 80,
        widthModule: Int = 3,
        hri: HriPosition = HriPosition.BELOW,
        hriFontB: Boolean = false,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // HRI position + font
        out.write(byteArrayOf(EscPos.GS, 'H'.code.toByte(), hri.value.toByte()))
        out.write(byteArrayOf(EscPos.GS, 'f'.code.toByte(), if (hriFontB) 1 else 0))
        // Height + width
        out.write(byteArrayOf(EscPos.GS, 'h'.code.toByte(), heightDots.coerceIn(1, 255).toByte()))
        out.write(byteArrayOf(EscPos.GS, 'w'.code.toByte(), widthModule.coerceIn(2, 6).toByte()))
        // Data (function B): GS k m n d1..dn
        val payload = data.toByteArray(Charsets.US_ASCII)
        out.write(byteArrayOf(EscPos.GS, 'k'.code.toByte(), type.code.toByte(), payload.size.coerceAtMost(255).toByte()))
        out.write(payload, 0, payload.size.coerceAtMost(255))
        return out.toByteArray()
    }

    /**
     * Native QR code via the `GS ( k` family.
     * @param moduleSize dot size of each QR module, 1..16.
     */
    fun qrCode(
        data: String,
        moduleSize: Int = 6,
        ecLevel: QrErrorCorrection = QrErrorCorrection.M,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val cn = 49 // QR code function group

        // Select model 2
        out.write(byteArrayOf(EscPos.GS, '('.code.toByte(), 'k'.code.toByte(), 4, 0, cn.toByte(), 65, 50, 0))
        // Module size
        out.write(byteArrayOf(EscPos.GS, '('.code.toByte(), 'k'.code.toByte(), 3, 0, cn.toByte(), 67, moduleSize.coerceIn(1, 16).toByte()))
        // Error correction
        out.write(byteArrayOf(EscPos.GS, '('.code.toByte(), 'k'.code.toByte(), 3, 0, cn.toByte(), 69, ecLevel.value.toByte()))
        // Store data : pL pH = len + 3
        val bytes = data.toByteArray(Charsets.UTF_8)
        val len = bytes.size + 3
        val pL = (len and 0xFF)
        val pH = (len shr 8) and 0xFF
        out.write(byteArrayOf(EscPos.GS, '('.code.toByte(), 'k'.code.toByte(), pL.toByte(), pH.toByte(), cn.toByte(), 80, 48))
        out.write(bytes)
        // Print
        out.write(byteArrayOf(EscPos.GS, '('.code.toByte(), 'k'.code.toByte(), 3, 0, cn.toByte(), 81, 48))
        return out.toByteArray()
    }
}
