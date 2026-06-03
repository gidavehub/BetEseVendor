package com.betesepmu.vendor.document

import com.betesepmu.vendor.escpos.Align

/**
 * Monospaced text layout used by both the ESC/POS and bitmap renderers, so column
 * alignment is identical on paper and on screen. Pure string maths → unit-testable.
 */
object Layout {

    fun divider(width: Int, ch: Char = '-'): String = ch.toString().repeat(width.coerceAtLeast(0))

    /** Fit [text] into [width] columns: pad with spaces per [align], truncate if too long. */
    fun pad(text: String, width: Int, align: Align): String {
        if (width <= 0) return ""
        if (text.length == width) return text
        if (text.length > width) return text.substring(0, width)
        val gap = width - text.length
        return when (align) {
            Align.LEFT -> text + " ".repeat(gap)
            Align.RIGHT -> " ".repeat(gap) + text
            Align.CENTER -> {
                val left = gap / 2
                " ".repeat(left) + text + " ".repeat(gap - left)
            }
        }
    }

    /**
     * Lay a list of [Column]s onto a single line of [totalChars]. Column widths are
     * distributed by weight; the final column absorbs any rounding remainder.
     */
    fun columns(cells: List<Column>, totalChars: Int): String {
        if (cells.isEmpty() || totalChars <= 0) return ""
        val totalWeight = cells.sumOf { it.weight.coerceAtLeast(1) }
        val widths = IntArray(cells.size)
        var used = 0
        for (i in cells.indices) {
            widths[i] = totalChars * cells[i].weight.coerceAtLeast(1) / totalWeight
            used += widths[i]
        }
        widths[cells.lastIndex] += totalChars - used // soak up rounding
        val sb = StringBuilder()
        for (i in cells.indices) sb.append(pad(cells[i].text, widths[i], cells[i].align))
        return sb.toString()
    }

    /** Greedy word-wrap; falls back to hard-splitting tokens longer than [width]. */
    fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) return listOf(text)
        val lines = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isEmpty()) { lines += ""; continue }
            var current = StringBuilder()
            for (word in paragraph.split(" ")) {
                var w = word
                while (w.length > width) { // hard-split overlong tokens
                    if (current.isNotEmpty()) { lines += current.toString(); current = StringBuilder() }
                    lines += w.substring(0, width)
                    w = w.substring(width)
                }
                when {
                    current.isEmpty() -> current.append(w)
                    current.length + 1 + w.length <= width -> current.append(' ').append(w)
                    else -> { lines += current.toString(); current = StringBuilder(w) }
                }
            }
            lines += current.toString()
        }
        return lines
    }
}
