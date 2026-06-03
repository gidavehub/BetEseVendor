package com.betesepmu.vendor.integration

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.BetEseApp
import com.betesepmu.vendor.core.PaperWidth
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.spooler.JobSource

/**
 * Registers BetEse Vendor as a system print service, so any app's standard **Print** button
 * can target the built-in head. We advertise a single roll printer, receive the framework's
 * PDF, rasterise each page to the paper's dot width, and hand the pages to the spooler.
 */
class BetEsePrintService : PrintService() {

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = Session()

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        if (printJob.isStarted || printJob.isQueued) printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        if (printJob.isQueued) printJob.start()
        val container = BetEseApp.container(this)
        val settings = container.settingsRepository.current
        val widthDots = settings.paperWidth.dots
        val data = printJob.document?.data
        if (data == null) { printJob.fail("No document data"); return }

        try {
            PdfRenderer(data).use { renderer ->
                val name = printJob.document?.info?.name ?: "Document"
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = widthDots.toFloat() / page.width
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = createBitmap(widthDots, h)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        container.enqueue("Print: $name (p${i + 1})", JobSource.PRINT_SERVICE, PrintPayload.Image(bmp))
                    }
                }
            }
            printJob.complete()
        } catch (e: Exception) {
            printJob.fail(e.message ?: "Render failed")
        }
    }

    /** Advertises one continuous-roll, monochrome printer for the built-in head. */
    private inner class Session : PrinterDiscoverySession() {

        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
            val id = generatePrinterId("betese_builtin")
            val settings = BetEseApp.container(this@BetEsePrintService).settingsRepository.current
            val widthMils = if (settings.paperWidth == PaperWidth.MM80) 3150 else 2283

            val caps = PrinterCapabilitiesInfo.Builder(id)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .addMediaSize(PrintAttributes.MediaSize("betese_roll", "Receipt roll", widthMils, 11811), true)
                .addResolution(PrintAttributes.Resolution("203dpi", "203 dpi", 203, 203), true)
                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()

            val printer = PrinterInfo.Builder(id, "BetEse Built-in Printer", PrinterInfo.STATUS_IDLE)
                .setCapabilities(caps)
                .build()
            addPrinters(listOf(printer))
        }

        override fun onStopPrinterDiscovery() {}
        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {}
        override fun onStartPrinterStateTracking(printerId: PrinterId) {}
        override fun onStopPrinterStateTracking(printerId: PrinterId) {}
        override fun onDestroy() {}
    }
}
