package com.betesepmu.vendor.ui.vendor

import java.util.Locale

/** GMD money formatting for on-screen vendor UI (matches the printed receipts). */
internal fun gmd(amount: Double): String {
    val rounded = Math.round(amount * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) "GMD ${String.format(Locale.US, "%,d", rounded.toLong())}"
    else "GMD ${String.format(Locale.US, "%,.2f", rounded)}"
}
