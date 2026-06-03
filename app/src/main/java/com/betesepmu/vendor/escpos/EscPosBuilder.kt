package com.betesepmu.vendor.escpos

import java.io.ByteArrayOutputStream

enum class Align(val value: Int) { LEFT(0), CENTER(1), RIGHT(2) }

/**
 * Fluent builder that accumulates ESC/POS bytes. Pure JVM — no Android types — so the
 * entire command layer is unit-testable without hardware. Call [build] to obtain the
 * byte stream to hand to a transport's `sendRaw`.
 *
 * The builder owns the *currently selected* code page so that [text] encodes correctly.
 * Always start a job with [initialize] (emits `ESC @`) to clear leaked state from a
 * previous job — alignment, size and emphasis are sticky on real heads.
 */
class EscPosBuilder(private var codePage: CodePage = CodePage.DEFAULT) {

    private val out = ByteArrayOutputStream(1024)

    fun raw(bytes: ByteArray): EscPosBuilder = apply { out.write(bytes) }
    fun raw(byte: Int): EscPosBuilder = apply { out.write(byte) }

    fun initialize(): EscPosBuilder = apply {
        out.write(EscPos.INIT)
        selectCodePage(codePage)
    }

    fun selectCodePage(page: CodePage): EscPosBuilder = apply {
        codePage = page
        out.write(EscPos.codeTable(page.escTable))
    }

    // ---- Text ---------------------------------------------------------------
    /** Append raw text (no trailing newline) encoded with the active code page. */
    fun text(s: String): EscPosBuilder = apply { out.write(codePage.encode(s)) }

    /** Append text followed by a line feed. */
    fun line(s: String = ""): EscPosBuilder = apply {
        out.write(codePage.encode(s))
        out.write(EscPos.LF.toInt())
    }

    fun newline(n: Int = 1): EscPosBuilder = apply { repeat(n.coerceAtLeast(0)) { out.write(EscPos.LF.toInt()) } }

    fun feed(lines: Int): EscPosBuilder = apply { out.write(EscPos.feed(lines)) }

    // ---- Styling ------------------------------------------------------------
    fun align(a: Align): EscPosBuilder = apply { out.write(EscPos.align(a.value)) }
    fun bold(on: Boolean): EscPosBuilder = apply { out.write(EscPos.bold(on)) }
    fun underline(weight: Int): EscPosBuilder = apply { out.write(EscPos.underline(weight)) }
    fun reverse(on: Boolean): EscPosBuilder = apply { out.write(EscPos.reverse(on)) }
    fun font(fontB: Boolean): EscPosBuilder = apply { out.write(EscPos.font(if (fontB) 1 else 0)) }
    fun size(widthMul: Int, heightMul: Int): EscPosBuilder = apply { out.write(EscPos.size(widthMul, heightMul)) }
    fun normalSize(): EscPosBuilder = size(1, 1)
    fun lineSpacing(dots: Int): EscPosBuilder = apply { out.write(EscPos.lineSpacing(dots)) }
    fun defaultLineSpacing(): EscPosBuilder = apply { out.write(EscPos.LINE_SPACING_DEFAULT) }

    /** Apply [style], run [block], then reset emphasis/size/align to defaults. */
    inline fun styled(style: TextStyle, block: EscPosBuilder.() -> Unit): EscPosBuilder {
        apply(style)
        block()
        return resetStyle()
    }

    fun apply(style: TextStyle): EscPosBuilder = apply {
        align(style.align)
        bold(style.bold)
        underline(style.underline)
        reverse(style.reverse)
        font(style.fontB)
        size(style.widthMul, style.heightMul)
    }

    fun resetStyle(): EscPosBuilder = apply {
        bold(false); underline(0); reverse(false); size(1, 1); align(Align.LEFT)
    }

    // ---- Graphics & codes ---------------------------------------------------
    /** Append already-packed `GS v 0` raster bytes (produced by the render pipeline). */
    fun rasterImage(rasterBytes: ByteArray): EscPosBuilder = apply { out.write(rasterBytes) }

    fun qr(
        data: String,
        moduleSize: Int = 6,
        ec: QrErrorCorrection = QrErrorCorrection.M,
    ): EscPosBuilder = apply { out.write(Barcodes.qrCode(data, moduleSize, ec)) }

    fun barcode(
        type: BarcodeType,
        data: String,
        heightDots: Int = 80,
        widthModule: Int = 3,
        hri: HriPosition = HriPosition.BELOW,
    ): EscPosBuilder = apply { out.write(Barcodes.barcode(type, data, heightDots, widthModule, hri)) }

    // ---- Hardware actions ---------------------------------------------------
    fun cut(full: Boolean = false, feedDots: Int = 0): EscPosBuilder = apply {
        if (feedDots > 0) out.write(EscPos.cutFeed(feedDots)) else out.write(EscPos.cut(full))
    }

    fun cashDrawer(pin: Int = 0): EscPosBuilder = apply { out.write(EscPos.cashDrawer(pin)) }
    fun beep(times: Int = 1, duration: Int = 4): EscPosBuilder = apply { out.write(EscPos.beep(times, duration)) }

    fun build(): ByteArray = out.toByteArray()
    fun size(): Int = out.size()
}

/**
 * Immutable bundle of text attributes used by [EscPosBuilder.apply] and the bitmap
 * renderer so both back-ends honour the same styling.
 */
data class TextStyle(
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
    val underline: Int = 0,
    val reverse: Boolean = false,
    val fontB: Boolean = false,
    val widthMul: Int = 1,
    val heightMul: Int = 1,
) {
    companion object {
        val NORMAL = TextStyle()
        val TITLE = TextStyle(align = Align.CENTER, bold = true, widthMul = 2, heightMul = 2)
        val HEADING = TextStyle(bold = true)
        val CENTER = TextStyle(align = Align.CENTER)
        val CENTER_BOLD = TextStyle(align = Align.CENTER, bold = true)
        val RIGHT = TextStyle(align = Align.RIGHT)
        val EMPHASISED = TextStyle(align = Align.CENTER, bold = true, widthMul = 2, heightMul = 1)
    }
}
