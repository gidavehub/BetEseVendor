package com.betesepmu.vendor.escpos

import java.nio.charset.Charset

/**
 * Character code-table handling — the "silent killer" of receipt printing.
 *
 * An ESC/POS head keeps an internal table selected with `ESC t n`. The bytes we send
 * must be encoded with the *matching* host charset, otherwise accented / non-Latin
 * characters print as garbage. Each entry pairs the printer table index ([escTable])
 * with the JVM [Charset] used to encode text for it.
 *
 * Android's charset support is ICU-backed and does not always expose the legacy IBM
 * code pages, so [resolveCharset] falls back gracefully to ISO-8859-1 (Latin-1), which
 * is adequate for English / French / Portuguese receipts common across African SMEs.
 */
enum class CodePage(
    val displayName: String,
    val escTable: Int,
    private val charsetNames: List<String>,
) {
    PC437_USA("PC437 (USA / Std Europe)", 0, listOf("IBM437", "Cp437", "ISO-8859-1")),
    PC850_MULTILINGUAL("PC850 (Multilingual Latin-1)", 2, listOf("IBM850", "Cp850", "ISO-8859-1")),
    PC860_PORTUGUESE("PC860 (Portuguese)", 3, listOf("IBM860", "Cp860", "ISO-8859-1")),
    PC863_CANADIAN_FRENCH("PC863 (Canadian French)", 4, listOf("IBM863", "Cp863", "ISO-8859-1")),
    PC865_NORDIC("PC865 (Nordic)", 5, listOf("IBM865", "Cp865", "ISO-8859-1")),
    WPC1252("Windows-1252 (Western Europe)", 16, listOf("windows-1252", "Cp1252", "ISO-8859-1")),
    PC858_EURO("PC858 (Euro)", 19, listOf("IBM00858", "Cp858", "ISO-8859-1")),
    ISO_8859_1("ISO-8859-1 (Latin-1)", 6, listOf("ISO-8859-1")),
    UTF8("UTF-8 (modern heads only)", 255, listOf("UTF-8"));

    /** The first charset name in [charsetNames] the JVM actually provides. */
    val charset: Charset by lazy { resolveCharset(charsetNames) }

    /** Encode [text] for this code page, replacing unmappable characters with '?'. */
    fun encode(text: String): ByteArray = text.toByteArray(charset)

    companion object {
        /** Sensible default for Latin-script receipts; broadly supported on Android. */
        val DEFAULT = WPC1252

        fun fromName(name: String?): CodePage =
            entries.firstOrNull { it.name == name } ?: DEFAULT

        private fun resolveCharset(candidates: List<String>): Charset {
            for (name in candidates) {
                try {
                    if (Charset.isSupported(name)) return Charset.forName(name)
                } catch (_: Exception) {
                    // try next candidate
                }
            }
            return Charsets.ISO_8859_1
        }
    }
}
