package com.betesepmu.vendor.document

import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.escpos.EscPosBuilder
import com.betesepmu.vendor.render.BarcodeRenderer
import com.betesepmu.vendor.render.ImageOps
import com.betesepmu.vendor.render.RasterEncoder

/**
 * Renders a [Receipt] to ESC/POS bytes for the configured [PrintSettings].
 *
 * Text obeys the paper's monospaced character budget (scaled down for double-size text)
 * and wraps cleanly; images and code fallbacks are composited to the full dot width so
 * justification is reliable across heads. The matching on-screen output is produced by
 * [BitmapReceiptRenderer] from the *same* [Receipt].
 */
object EscPosReceiptRenderer {

    fun render(receipt: Receipt, settings: PrintSettings): ByteArray {
        val b = EscPosBuilder(settings.codePage)
        val cols = settings.paperWidth.charsFontA
        val dots = settings.paperWidth.dots
        b.initialize()

        for (el in receipt.elements) {
            when (el) {
                is ReceiptElement.Text -> {
                    val effectiveCols = (cols / el.style.widthMul).coerceAtLeast(1)
                    b.apply(el.style)
                    for (lineText in Layout.wrap(el.text, effectiveCols)) b.line(lineText)
                    b.resetStyle()
                }

                is ReceiptElement.Columns -> {
                    b.resetStyle()
                    b.align(Align.LEFT)
                    val widthsLine = Layout.columns(el.cells, cols)
                    // Re-split the laid-out line by cell so per-column bold is honoured.
                    var cursor = 0
                    val totalWeight = el.cells.sumOf { it.weight.coerceAtLeast(1) }
                    var used = 0
                    for ((idx, cell) in el.cells.withIndex()) {
                        val w = if (idx == el.cells.lastIndex) cols - used
                        else cols * cell.weight.coerceAtLeast(1) / totalWeight
                        used += w
                        val seg = widthsLine.substring(cursor, (cursor + w).coerceAtMost(widthsLine.length))
                        cursor += w
                        b.bold(cell.bold).text(seg).bold(false)
                    }
                    b.newline()
                }

                is ReceiptElement.Divider -> b.line(Layout.divider(cols, el.char))
                is ReceiptElement.Feed -> b.feed(el.lines)

                is ReceiptElement.Image -> {
                    val target = (dots * el.widthPercent / 100).coerceIn(8, dots)
                    val placed = ImageOps.placeOnWidth(el.bitmap, dots, target, el.align)
                    b.align(Align.LEFT)
                    b.rasterImage(RasterEncoder.encode(placed, dots, settings.ditherMode))
                    b.newline()
                }

                is ReceiptElement.Qr -> {
                    if (el.native) {
                        b.align(el.align).qr(el.data, el.moduleSize, el.ec).newline()
                        b.align(Align.LEFT)
                    } else {
                        val px = (el.moduleSize * 40).coerceIn(120, dots)
                        val bmp = BarcodeRenderer.qr(el.data, px)
                        val placed = ImageOps.placeOnWidth(bmp, dots, px, el.align)
                        b.align(Align.LEFT).rasterImage(RasterEncoder.encode(placed, dots, settings.ditherMode)).newline()
                    }
                }

                is ReceiptElement.Barcode -> {
                    if (el.native) {
                        b.align(el.align).barcode(el.type, el.data, el.heightDots, el.widthModule).newline()
                        b.align(Align.LEFT)
                    } else {
                        val bmp = BarcodeRenderer.barcode(el.data, el.type, (dots * 0.8).toInt(), el.heightDots)
                        val placed = ImageOps.placeOnWidth(bmp, dots, (dots * 0.8).toInt(), el.align)
                        b.align(Align.LEFT).rasterImage(RasterEncoder.encode(placed, dots, settings.ditherMode)).newline()
                    }
                }

                ReceiptElement.Cut -> b.cut(full = false, feedDots = settings.cutFeedDots)
                is ReceiptElement.Drawer -> b.cashDrawer(el.pin)
                is ReceiptElement.Beep -> b.beep(el.times, el.duration)
            }
        }
        return b.build()
    }
}
