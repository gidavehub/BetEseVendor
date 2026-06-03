package com.betesepmu.vendor.printer

import com.betesepmu.vendor.core.ConnectionState
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.core.PrinterCapabilities
import com.betesepmu.vendor.core.PrinterStatus

/** Which concrete backend a transport is. Drives icon/label choices in the UI. */
enum class TransportKind { SUNMI, BLUETOOTH, PREVIEW }

/** Result of a single print submission. */
data class PrintOutcome(val success: Boolean, val message: String = "") {
    companion object {
        val OK = PrintOutcome(true, "Printed")
        fun fail(message: String) = PrintOutcome(false, message)
    }
}

/**
 * The one abstraction the spooler talks to. Concrete transports own the messy hardware
 * reality (binding the Sunmi `woyou` service, or rendering to screen). [submit] is
 * deliberately high-level — it accepts a [PrintPayload] and renders it the way that makes
 * sense for the backend (ESC/POS bytes for a head, a bitmap for the preview) — while
 * [PrintSettings] carries paper width, code page, dithering and cut/drawer behaviour.
 */
interface PrinterTransport {
    val name: String
    val kind: TransportKind

    /** Establish the backing connection; returns the resulting status. */
    suspend fun connect(): PrinterStatus

    fun state(): ConnectionState
    suspend fun status(): PrinterStatus
    fun capabilities(): PrinterCapabilities

    /** Render [payload] per [settings] and push it to the backend. */
    suspend fun submit(payload: PrintPayload, settings: PrintSettings): PrintOutcome

    fun close()
}
