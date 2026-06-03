package com.betesepmu.vendor.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.render.BarcodeRenderer
import com.betesepmu.vendor.render.RasterEncoder

/**
 * Renders the *same* [Receipt] used by [EscPosReceiptRenderer] to a tall [Bitmap] that
 * mimics a thermal paper roll. This is the heart of the testable, hardware-free preview:
 * monospaced text at the head's real character budget, real dithering of images, real
 * ZXing codes. What you see here is what the head burns.
 */
class BitmapReceiptRenderer(private val settings: PrintSettings) {

    private val dots = settings.paperWidth.dots
    private val cols = settings.paperWidth.charsFontA

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.BLACK
        textSize = 24f
    }
    private val baseTextSize: Float
    private val charWidth: Float
    private val baseLineHeight: Float
    private val ascent: Float

    init {
        val cw0 = basePaint.measureText("0").coerceAtLeast(1f)
        baseTextSize = 24f * (dots.toFloat() / cols) / cw0
        basePaint.textSize = baseTextSize
        charWidth = basePaint.measureText("0")
        val fm = basePaint.fontMetrics
        baseLineHeight = fm.descent - fm.ascent
        ascent = fm.ascent
    }

    private class Op(val height: Int, val draw: (Canvas, Float) -> Unit)

    fun render(receipt: Receipt): Bitmap {
        val pad = (baseLineHeight * 0.6f)
        val ops = ArrayList<Op>()
        for (el in receipt.elements) ops += buildOp(el)
        val total = (pad * 2 + ops.sumOf { it.height }).toInt().coerceAtLeast(1)
        val bmp = createBitmap(dots, total)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = pad
        for (op in ops) { op.draw(canvas, y); y += op.height }
        return bmp
    }

    private fun textPaint(style: com.betesepmu.vendor.escpos.TextStyle): Paint =
        Paint(basePaint).apply {
            textSize = baseTextSize * style.heightMul
            textScaleX = style.widthMul.toFloat()
            typeface = if (style.bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            isUnderlineText = style.underline > 0
            if (style.reverse) color = Color.BLACK
        }

    private fun xFor(align: Align, contentWidth: Float): Float = when (align) {
        Align.LEFT -> 0f
        Align.CENTER -> (dots - contentWidth) / 2f
        Align.RIGHT -> dots - contentWidth
    }

    private fun buildOp(el: ReceiptElement): Op = when (el) {
        is ReceiptElement.Text -> {
            val effCols = (cols / el.style.widthMul).coerceAtLeast(1)
            val lines = Layout.wrap(el.text, effCols)
            val p = textPaint(el.style)
            val lh = baseLineHeight * el.style.heightMul
            Op((lh * lines.size).toInt()) { c, top ->
                var ly = top
                for (line in lines) {
                    val w = p.measureText(line) * 1f
                    if (el.style.reverse) {
                        val bg = Paint().apply { color = Color.BLACK }
                        c.drawRect(0f, ly, dots.toFloat(), ly + lh, bg)
                        p.color = Color.WHITE
                    }
                    c.drawText(line, xFor(el.style.align, w), ly - ascent * el.style.heightMul, p)
                    if (el.style.reverse) p.color = Color.BLACK
                    ly += lh
                }
            }
        }

        is ReceiptElement.Columns -> {
            val line = Layout.columns(el.cells, cols)
            Op(baseLineHeight.toInt()) { c, top ->
                // Draw per cell so bold columns render bold, positioned by char index.
                var charIdx = 0
                val totalWeight = el.cells.sumOf { it.weight.coerceAtLeast(1) }
                var used = 0
                for ((i, cell) in el.cells.withIndex()) {
                    val w = if (i == el.cells.lastIndex) cols - used
                    else cols * cell.weight.coerceAtLeast(1) / totalWeight
                    used += w
                    val seg = line.substring(charIdx, (charIdx + w).coerceAtMost(line.length))
                    charIdx += w
                    val p = textPaint(com.betesepmu.vendor.escpos.TextStyle(bold = cell.bold))
                    c.drawText(seg, charIdx.minus(w) * charWidth, top - ascent, p)
                }
            }
        }

        is ReceiptElement.Divider -> {
            val p = textPaint(com.betesepmu.vendor.escpos.TextStyle())
            Op(baseLineHeight.toInt()) { c, top ->
                c.drawText(Layout.divider(cols, el.char), 0f, top - ascent, p)
            }
        }

        is ReceiptElement.Feed -> Op((baseLineHeight * el.lines).toInt()) { _, _ -> }

        is ReceiptElement.Image -> {
            val target = (dots * el.widthPercent / 100).coerceIn(8, dots)
            val mono = RasterEncoder.ditheredPreview(el.bitmap, target, settings.ditherMode)
            Op(mono.height) { c, top ->
                c.drawBitmap(mono, xFor(el.align, mono.width.toFloat()), top, null)
            }
        }

        is ReceiptElement.Qr -> {
            val px = (el.moduleSize * 40).coerceIn(120, dots)
            val bmp = BarcodeRenderer.qr(el.data, px)
            Op(bmp.height) { c, top -> c.drawBitmap(bmp, xFor(el.align, bmp.width.toFloat()), top, null) }
        }

        is ReceiptElement.Barcode -> {
            val w = (dots * 0.8f).toInt()
            val bmp = BarcodeRenderer.barcode(el.data, el.type, w, el.heightDots)
            Op(bmp.height) { c, top -> c.drawBitmap(bmp, xFor(el.align, bmp.width.toFloat()), top, null) }
        }

        ReceiptElement.Cut -> Op((baseLineHeight * 1.4f).toInt()) { c, top ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }
            val midY = top + baseLineHeight * 0.7f
            c.drawLine(0f, midY, dots.toFloat(), midY, p)
        }

        is ReceiptElement.Drawer -> annotation("〘 cash drawer kick 〙")
        is ReceiptElement.Beep -> annotation("🔔 beep x${el.times}")
    }

    private fun annotation(label: String): Op {
        val p = Paint(basePaint).apply {
            textSize = baseTextSize * 0.8f; color = Color.GRAY
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
        }
        return Op((baseLineHeight * 0.9f).toInt()) { c, top ->
            val w = p.measureText(label)
            c.drawText(label, (dots - w) / 2f, top - ascent * 0.8f, p)
        }
    }
}
