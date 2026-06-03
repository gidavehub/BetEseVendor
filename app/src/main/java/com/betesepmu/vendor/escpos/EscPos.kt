package com.betesepmu.vendor.escpos

/**
 * Low-level ESC/POS control bytes and command fragments.
 *
 * This object is intentionally free of any Android dependency so the whole command
 * layer can be unit-tested on the JVM. Everything here returns raw [ByteArray]s that
 * are concatenated by [EscPosBuilder].
 *
 * Reference: Epson ESC/POS Command Reference. Commands are widely supported by the
 * built-in print heads on Sunmi / iMin / Telpo terminals via their `sendRAWData` hook.
 */
object EscPos {
    // ---- Control characters -------------------------------------------------
    const val NUL: Byte = 0x00
    const val LF: Byte = 0x0A   // line feed / print
    const val CR: Byte = 0x0D
    const val HT: Byte = 0x09   // horizontal tab
    const val FF: Byte = 0x0C   // form feed (page mode)
    const val CAN: Byte = 0x18
    const val ESC: Byte = 0x1B
    const val GS: Byte = 0x1D
    const val FS: Byte = 0x1C
    const val DLE: Byte = 0x10

    // ---- Initialisation -----------------------------------------------------
    /** ESC @ — reset the printer to power-on defaults. Always send at job start. */
    val INIT = byteArrayOf(ESC, '@'.code.toByte())

    // ---- Line feeds ---------------------------------------------------------
    fun feed(lines: Int): ByteArray =
        if (lines <= 0) ByteArray(0) else byteArrayOf(ESC, 'd'.code.toByte(), lines.coerceIn(0, 255).toByte())

    fun feedDots(dots: Int): ByteArray =
        byteArrayOf(ESC, 'J'.code.toByte(), dots.coerceIn(0, 255).toByte())

    // ---- Alignment ----------------------------------------------------------
    /** ESC a n — 0 left, 1 center, 2 right. */
    fun align(value: Int): ByteArray = byteArrayOf(ESC, 'a'.code.toByte(), value.coerceIn(0, 2).toByte())

    // ---- Character styling --------------------------------------------------
    /** ESC E n — emphasised / bold. */
    fun bold(on: Boolean): ByteArray = byteArrayOf(ESC, 'E'.code.toByte(), if (on) 1 else 0)

    /** ESC - n — underline: 0 off, 1 one-dot, 2 two-dot. */
    fun underline(weight: Int): ByteArray = byteArrayOf(ESC, '-'.code.toByte(), weight.coerceIn(0, 2).toByte())

    /** GS B n — white/black reverse. */
    fun reverse(on: Boolean): ByteArray = byteArrayOf(GS, 'B'.code.toByte(), if (on) 1 else 0)

    /** ESC { n — upside-down printing. */
    fun upsideDown(on: Boolean): ByteArray = byteArrayOf(ESC, '{'.code.toByte(), if (on) 1 else 0)

    /** ESC M n — font: 0 = Font A (12x24), 1 = Font B (9x17). */
    fun font(value: Int): ByteArray = byteArrayOf(ESC, 'M'.code.toByte(), value.coerceIn(0, 1).toByte())

    /**
     * GS ! n — character size. Width and height multipliers are 1..8.
     * Upper nibble = width-1, lower nibble = height-1.
     */
    fun size(widthMul: Int, heightMul: Int): ByteArray {
        val w = (widthMul.coerceIn(1, 8) - 1) shl 4
        val h = (heightMul.coerceIn(1, 8) - 1)
        return byteArrayOf(GS, '!'.code.toByte(), (w or h).toByte())
    }

    /** ESC SP n — right-side character spacing in dots. */
    fun charSpacing(dots: Int): ByteArray = byteArrayOf(ESC, ' '.code.toByte(), dots.coerceIn(0, 255).toByte())

    /** ESC 3 n — line spacing in dots (default ~30). */
    fun lineSpacing(dots: Int): ByteArray = byteArrayOf(ESC, '3'.code.toByte(), dots.coerceIn(0, 255).toByte())

    /** ESC 2 — restore default line spacing. */
    val LINE_SPACING_DEFAULT = byteArrayOf(ESC, '2'.code.toByte())

    // ---- Code page ----------------------------------------------------------
    /** ESC t n — select character code table. See [CodePage]. */
    fun codeTable(n: Int): ByteArray = byteArrayOf(ESC, 't'.code.toByte(), n.coerceIn(0, 255).toByte())

    /** ESC R n — international character set. */
    fun internationalCharset(n: Int): ByteArray = byteArrayOf(ESC, 'R'.code.toByte(), n.coerceIn(0, 15).toByte())

    // ---- Paper cut ----------------------------------------------------------
    /** GS V m — 0/48 full cut, 1/49 partial cut. */
    fun cut(full: Boolean): ByteArray = byteArrayOf(GS, 'V'.code.toByte(), if (full) 0 else 1)

    /** GS V 66 n — feed n dots then partial cut. The most reliable cut on most heads. */
    fun cutFeed(dots: Int): ByteArray = byteArrayOf(GS, 'V'.code.toByte(), 66, dots.coerceIn(0, 255).toByte())

    // ---- Cash drawer --------------------------------------------------------
    /**
     * ESC p m t1 t2 — pulse the cash-drawer kick connector.
     * @param pin 0 → pin 2, 1 → pin 5.
     */
    fun cashDrawer(pin: Int = 0, onMs: Int = 100, offMs: Int = 100): ByteArray {
        val m = if (pin == 0) 0 else 1
        return byteArrayOf(
            ESC, 'p'.code.toByte(), m.toByte(),
            (onMs / 2).coerceIn(0, 255).toByte(),
            (offMs / 2).coerceIn(0, 255).toByte()
        )
    }

    // ---- Buzzer / beep ------------------------------------------------------
    /** ESC B n t — buzzer (supported by many ESC/POS heads incl. Sunmi). */
    fun beep(times: Int = 1, durationUnits: Int = 4): ByteArray =
        byteArrayOf(ESC, 'B'.code.toByte(), times.coerceIn(1, 9).toByte(), durationUnits.coerceIn(1, 9).toByte())

    // ---- Status query (Automatic Status Back) -------------------------------
    /** DLE EOT n — real-time status transmission. n: 1 printer, 2 offline, 3 error, 4 paper. */
    fun realtimeStatus(n: Int): ByteArray = byteArrayOf(DLE, 0x04, n.coerceIn(1, 4).toByte())
}
