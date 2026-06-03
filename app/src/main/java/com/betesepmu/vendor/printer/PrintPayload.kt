package com.betesepmu.vendor.printer

import android.graphics.Bitmap
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.document.Receipt
import com.betesepmu.vendor.document.receipt

/**
 * What a print job carries. Every integration surface (samples, intent, HTTP, AIDL, the
 * print service) normalises its input into one of these so transports stay simple.
 */
sealed interface PrintPayload {
    /** A fully-modelled document — the richest form; renders to bytes and to preview. */
    data class ReceiptDoc(val receipt: Receipt) : PrintPayload

    /** Pre-encoded ESC/POS bytes from an external caller; passed straight to the head. */
    class Raw(val bytes: ByteArray) : PrintPayload

    /** A single bitmap (e.g. a rasterised PDF page from the Print Service). */
    data class Image(val bitmap: Bitmap) : PrintPayload

    /** Plain text, wrapped to the paper width. */
    data class PlainText(val text: String) : PrintPayload

    /** Short human description for the queue UI. */
    val describe: String
        get() = when (this) {
            is ReceiptDoc -> "Document (${receipt.elements.size} elements)"
            is Raw -> "Raw ESC/POS (${bytes.size} bytes)"
            is Image -> "Image ${bitmap.width}×${bitmap.height}"
            is PlainText -> "Text (${text.length} chars)"
        }
}

/** Normalises non-receipt payloads into a [Receipt] so both renderers can consume them. */
object Payloads {
    /** Returns a [Receipt] for everything except [PrintPayload.Raw] (which is byte-only). */
    fun toReceipt(payload: PrintPayload, settings: PrintSettings): Receipt? = when (payload) {
        is PrintPayload.ReceiptDoc -> payload.receipt
        is PrintPayload.Raw -> null
        is PrintPayload.Image -> receipt {
            image(payload.bitmap, widthPercent = 100)
            feed(2)
            if (settings.openCashDrawer) drawer()
            if (settings.beepOnComplete) beep(1)
            if (settings.autoCut) cut()
        }
        is PrintPayload.PlainText -> receipt {
            for (l in payload.text.split("\n")) text(l)
            feed(2)
            if (settings.openCashDrawer) drawer()
            if (settings.beepOnComplete) beep(1)
            if (settings.autoCut) cut()
        }
    }
}
