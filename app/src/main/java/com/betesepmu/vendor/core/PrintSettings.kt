package com.betesepmu.vendor.core

import com.betesepmu.vendor.escpos.CodePage

/** Which transport the broker should prefer when several are viable. */
enum class TransportPreference(val label: String) {
    AUTO("Auto-detect (built-in → Bluetooth → preview)"),
    SUNMI("Sunmi built-in (woyou service)"),
    BLUETOOTH("Bluetooth printer (paired)"),
    PREVIEW("On-screen preview (no hardware)");

    companion object {
        fun fromName(name: String?): TransportPreference = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Everything the broker needs to turn a document into bytes and push it to a head.
 * Persisted by `SettingsRepository` and surfaced in the Settings screen. Immutable —
 * the repository emits a new instance on every change so Compose recomposes cleanly.
 */
data class PrintSettings(
    val paperWidth: PaperWidth = PaperWidth.MM58,
    val codePage: CodePage = CodePage.DEFAULT,
    val ditherMode: DitherMode = DitherMode.FLOYD_STEINBERG,
    val density: Int = 8,                 // 0..15, mapped to head density where supported
    val autoCut: Boolean = true,
    val cutFeedDots: Int = 80,            // feed before cut so content clears the blade
    val openCashDrawer: Boolean = false,
    val beepOnComplete: Boolean = true,
    val copies: Int = 1,
    val transportPreference: TransportPreference = TransportPreference.AUTO,

    // Bluetooth printer selection (paired devices only — discovery happens in Android Settings).
    // Empty string means "no device chosen yet". MAC is what we reconnect by; name is shown to the user.
    val bluetoothDeviceAddress: String = "",
    val bluetoothDeviceName: String = "",

    // Local HTTP integration surface
    val httpServerEnabled: Boolean = false,
    val httpPort: Int = 9100,

    // Business identity printed on sample documents & receipts
    val businessName: String = "BetEse Vendor",
    val businessAddress: String = "Kairaba Avenue, Serrekunda, The Gambia",
    val businessPhone: String = "+220 000 0000",
    val currencySymbol: String = "D",     // Gambian Dalasi by default
    val footerText: String = "Thank you for your patronage!",
) {
    val charsPerLine: Int get() = paperWidth.charsFontA
}
