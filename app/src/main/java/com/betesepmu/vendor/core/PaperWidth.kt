package com.betesepmu.vendor.core

/**
 * Physical roll width of the built-in head, with the derived dot and character budgets.
 *
 * 58 mm heads expose ~384 printable dots (32 chars in Font A); 80 mm heads expose
 * ~576 dots (48 chars). These numbers drive both the raster width and the
 * monospaced column layout used for receipts.
 */
enum class PaperWidth(
    val mm: Int,
    val dots: Int,
    val charsFontA: Int,
    val charsFontB: Int,
    val label: String,
) {
    MM58(58, 384, 32, 42, "58 mm (384 dots)"),
    MM80(80, 576, 48, 64, "80 mm (576 dots)");

    fun chars(fontB: Boolean): Int = if (fontB) charsFontB else charsFontA

    companion object {
        fun fromName(name: String?): PaperWidth = entries.firstOrNull { it.name == name } ?: MM58
    }
}

/** How a colour/greyscale image is reduced to the head's 1-bit (black/white) reality. */
enum class DitherMode(val label: String) {
    FLOYD_STEINBERG("Floyd–Steinberg (best photos)"),
    ATKINSON("Atkinson (crisp, lighter)"),
    THRESHOLD("Threshold (line art / logos)");

    companion object {
        fun fromName(name: String?): DitherMode = entries.firstOrNull { it.name == name } ?: FLOYD_STEINBERG
    }
}
