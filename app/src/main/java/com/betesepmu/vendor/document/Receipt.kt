package com.betesepmu.vendor.document

import android.graphics.Bitmap
import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.escpos.BarcodeType
import com.betesepmu.vendor.escpos.QrErrorCorrection
import com.betesepmu.vendor.escpos.TextStyle

/**
 * Device-independent description of a printable document — the broker's intermediate
 * representation. A [Receipt] is rendered to ESC/POS bytes by `EscPosReceiptRenderer`
 * **and** to an on-screen bitmap by `BitmapReceiptRenderer`, so the preview matches what
 * a head actually prints. Build one with the [receipt] DSL.
 */
data class Receipt(val elements: List<ReceiptElement>)

/** A cell in a [Columns] row. Widths are distributed by [weight] across the line. */
data class Column(
    val text: String,
    val weight: Int = 1,
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
)

sealed interface ReceiptElement {
    data class Text(val text: String, val style: TextStyle = TextStyle.NORMAL) : ReceiptElement
    data class Columns(val cells: List<Column>) : ReceiptElement
    data class Divider(val char: Char = '-') : ReceiptElement
    data class Feed(val lines: Int = 1) : ReceiptElement
    data class Image(
        val bitmap: Bitmap,
        val widthPercent: Int = 100,
        val align: Align = Align.CENTER,
    ) : ReceiptElement
    data class Qr(
        val data: String,
        val moduleSize: Int = 6,
        val ec: QrErrorCorrection = QrErrorCorrection.M,
        val align: Align = Align.CENTER,
        val native: Boolean = true,
    ) : ReceiptElement
    data class Barcode(
        val data: String,
        val type: BarcodeType = BarcodeType.CODE128,
        val heightDots: Int = 90,
        val widthModule: Int = 3,
        val align: Align = Align.CENTER,
        val native: Boolean = true,
    ) : ReceiptElement
    object Cut : ReceiptElement
    data class Drawer(val pin: Int = 0) : ReceiptElement
    data class Beep(val times: Int = 1, val duration: Int = 4) : ReceiptElement
}

/** Entry point for the DSL: `val r = receipt { title("Shop"); divider(); ... }`. */
fun receipt(block: ReceiptBuilder.() -> Unit): Receipt = ReceiptBuilder().apply(block).build()

/** Mutable accumulator behind the [receipt] DSL. */
class ReceiptBuilder {
    private val items = mutableListOf<ReceiptElement>()

    fun add(element: ReceiptElement) { items += element }

    fun text(text: String, style: TextStyle = TextStyle.NORMAL) = add(ReceiptElement.Text(text, style))
    fun title(text: String) = add(ReceiptElement.Text(text, TextStyle.TITLE))
    fun subtitle(text: String) = add(ReceiptElement.Text(text, TextStyle.CENTER_BOLD))
    fun center(text: String) = add(ReceiptElement.Text(text, TextStyle.CENTER))
    fun heading(text: String) = add(ReceiptElement.Text(text, TextStyle.HEADING))
    fun emphasised(text: String) = add(ReceiptElement.Text(text, TextStyle.EMPHASISED))

    fun columns(vararg cells: Column) = add(ReceiptElement.Columns(cells.toList()))
    fun row(left: String, right: String, boldRight: Boolean = false) = columns(
        Column(left, weight = 2, align = Align.LEFT),
        Column(right, weight = 1, align = Align.RIGHT, bold = boldRight),
    )

    fun divider(char: Char = '-') = add(ReceiptElement.Divider(char))
    fun feed(lines: Int = 1) = add(ReceiptElement.Feed(lines))
    fun image(bitmap: Bitmap, widthPercent: Int = 100, align: Align = Align.CENTER) =
        add(ReceiptElement.Image(bitmap, widthPercent, align))
    fun qr(data: String, moduleSize: Int = 6, native: Boolean = true) =
        add(ReceiptElement.Qr(data, moduleSize, native = native))
    fun barcode(data: String, type: BarcodeType = BarcodeType.CODE128, native: Boolean = true) =
        add(ReceiptElement.Barcode(data, type, native = native))
    fun cut() = add(ReceiptElement.Cut)
    fun drawer(pin: Int = 0) = add(ReceiptElement.Drawer(pin))
    fun beep(times: Int = 1, duration: Int = 4) = add(ReceiptElement.Beep(times, duration))

    fun build(): Receipt = Receipt(items.toList())
}
