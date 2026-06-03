package com.betesepmu.vendor.printer.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.core.ConnectionState
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.core.PrinterCapabilities
import com.betesepmu.vendor.core.PrinterStatus
import com.betesepmu.vendor.document.BitmapReceiptRenderer
import com.betesepmu.vendor.printer.PrintOutcome
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.printer.Payloads
import com.betesepmu.vendor.printer.PrinterTransport
import com.betesepmu.vendor.printer.TransportKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A rendered job kept for display on the Home / preview screens. */
data class PreviewItem(
    val id: Long,
    val bitmap: Bitmap,
    val label: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * The hardware-free backend: "printing" renders the job to a bitmap that looks like a
 * thermal paper roll and pushes it onto [previews]. This is what makes BetEse Vendor
 * usable and demoable on *any* Android device, and the built-in way to "test the printing
 * with" the bundled samples before a real head is connected.
 */
class PreviewTransport : PrinterTransport {

    override val name = "On-screen Preview"
    override val kind = TransportKind.PREVIEW

    private val _previews = MutableStateFlow<List<PreviewItem>>(emptyList())
    val previews: StateFlow<List<PreviewItem>> = _previews

    private var counter = 0L

    override suspend fun connect(): PrinterStatus = PrinterStatus.ready("Preview ready")
    override fun state(): ConnectionState = ConnectionState.CONNECTED
    override suspend fun status(): PrinterStatus = PrinterStatus.ready("Preview ready")
    override fun capabilities(): PrinterCapabilities =
        PrinterCapabilities.preview(com.betesepmu.vendor.core.PaperWidth.MM58)

    override suspend fun submit(payload: PrintPayload, settings: PrintSettings): PrintOutcome {
        val bitmap = when (val r = Payloads.toReceipt(payload, settings)) {
            null -> rawPlaceholder((payload as PrintPayload.Raw).bytes, settings)
            else -> BitmapReceiptRenderer(settings).render(r)
        }
        val item = PreviewItem(++counter, bitmap, payload.describe)
        _previews.update { (listOf(item) + it).take(MAX_KEPT) }
        return PrintOutcome(true, "Rendered to preview")
    }

    override fun close() { _previews.value = emptyList() }

    /** Bytes-only jobs can't be rasterised faithfully without an interpreter — show a card. */
    private fun rawPlaceholder(bytes: ByteArray, settings: PrintSettings): Bitmap {
        val w = settings.paperWidth.dots
        val hex = bytes.take(64).joinToString(" ") { "%02X".format(it) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE; color = Color.BLACK; textSize = 22f
        }
        val lh = (p.fontMetrics.descent - p.fontMetrics.ascent)
        val chars = (w / p.measureText("0")).toInt().coerceAtLeast(8)
        val lines = mutableListOf("RAW ESC/POS PAYLOAD", "${bytes.size} bytes", "")
        hex.chunked(chars).forEach { lines += it }
        val bmp = createBitmap(w, ((lines.size + 2) * lh).toInt())
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = lh
        for (line in lines) { c.drawText(line, 6f, y, p); y += lh }
        return bmp
    }

    companion object { private const val MAX_KEPT = 12 }
}
