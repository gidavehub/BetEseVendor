package com.betesepmu.vendor.core

/** Coarse connection state of a transport, surfaced on the Home screen. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, UNAVAILABLE }

/**
 * Health of the physical head at a point in time. Built either from a vendor SDK status
 * call (Sunmi `updatePrinterState`) or from the `DLE EOT` real-time status byte.
 */
data class PrinterStatus(
    val connection: ConnectionState,
    val ready: Boolean,
    val paperOut: Boolean = false,
    val paperLow: Boolean = false,
    val coverOpen: Boolean = false,
    val overheated: Boolean = false,
    val message: String = "",
) {
    companion object {
        val UNKNOWN = PrinterStatus(ConnectionState.DISCONNECTED, ready = false, message = "Unknown")
        fun ready(msg: String = "Ready") = PrinterStatus(ConnectionState.CONNECTED, ready = true, message = msg)
        fun unavailable(msg: String) = PrinterStatus(ConnectionState.UNAVAILABLE, ready = false, message = msg)
    }
}

/** What a given head can do — read once on connect and used to gate features in the UI. */
data class PrinterCapabilities(
    val vendor: String,
    val model: String,
    val paperWidth: PaperWidth,
    val dpi: Int = 203,
    val hasCutter: Boolean = false,
    val hasCashDrawer: Boolean = false,
    val hasBuzzer: Boolean = false,
    val supportsRawEscPos: Boolean = true,
    val serial: String = "",
) {
    companion object {
        fun preview(paperWidth: PaperWidth) = PrinterCapabilities(
            vendor = "BetEse",
            model = "On-screen Preview",
            paperWidth = paperWidth,
            hasCutter = true,
            hasCashDrawer = true,
            hasBuzzer = true,
        )
    }
}
