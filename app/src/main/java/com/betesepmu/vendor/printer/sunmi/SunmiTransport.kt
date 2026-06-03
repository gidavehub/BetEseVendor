package com.betesepmu.vendor.printer.sunmi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.betesepmu.vendor.core.ConnectionState
import com.betesepmu.vendor.core.PaperWidth
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.core.PrinterCapabilities
import com.betesepmu.vendor.core.PrinterStatus
import com.betesepmu.vendor.document.EscPosReceiptRenderer
import com.betesepmu.vendor.printer.Payloads
import com.betesepmu.vendor.printer.PrintOutcome
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.printer.PrinterTransport
import com.betesepmu.vendor.printer.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService

/**
 * Built-in head on Sunmi terminals via the bound `woyou.aidlservice.jiuiv5` service.
 *
 * Everything is routed through `sendRAWData`, so our own ESC/POS engine stays the single
 * source of truth and the design ports to iMin/Telpo by swapping only this binding. The
 * AIDL connection drops occasionally — [onServiceDisconnected] auto-rebinds. On a device
 * without the service (e.g. a regular phone) [connect] reports UNAVAILABLE and the
 * `TransportManager` falls back to the preview transport.
 */
class SunmiTransport(private val context: Context) : PrinterTransport {

    override val name = "Sunmi Built-in"
    override val kind = TransportKind.SUNMI

    @Volatile private var service: IWoyouService? = null
    @Volatile private var connectionState: ConnectionState = ConnectionState.DISCONNECTED
    private var connectSignal: CompletableDeferred<Boolean>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IWoyouService.Stub.asInterface(binder)
            connectionState = ConnectionState.CONNECTED
            connectSignal?.complete(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connectionState = ConnectionState.DISCONNECTED
            tryBind() // auto-rebind: the inner service is known to drop occasionally
        }
    }

    override fun state(): ConnectionState = connectionState

    override suspend fun connect(): PrinterStatus {
        if (service != null) return status()
        connectionState = ConnectionState.CONNECTING
        val signal = CompletableDeferred<Boolean>()
        connectSignal = signal
        if (!tryBind()) {
            connectionState = ConnectionState.UNAVAILABLE
            return PrinterStatus.unavailable("Sunmi print service not found on this device")
        }
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { signal.await() } ?: false
        if (!connected) {
            connectionState = ConnectionState.UNAVAILABLE
            return PrinterStatus.unavailable("Timed out binding Sunmi service")
        }
        runCatching { service?.printerInit(null) }
        return status()
    }

    private fun tryBind(): Boolean = try {
        val intent = Intent().apply {
            setPackage(SUNMI_PACKAGE)
            action = SUNMI_ACTION
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    } catch (_: Exception) {
        false
    }

    override suspend fun status(): PrinterStatus {
        val svc = service ?: return PrinterStatus.unavailable("Disconnected")
        return try {
            mapState(svc.updatePrinterState())
        } catch (_: Exception) {
            // Some firmwares throw if queried mid-job; assume reachable.
            PrinterStatus(ConnectionState.CONNECTED, ready = true, message = "Connected")
        }
    }

    override fun capabilities(): PrinterCapabilities {
        val svc = service
        val model = runCatching { svc?.printerModal() }.getOrNull() ?: "Sunmi Built-in"
        val serial = runCatching { svc?.serial() }.getOrNull() ?: ""
        return PrinterCapabilities(
            vendor = "Sunmi",
            model = model,
            paperWidth = if (model.contains("80")) PaperWidth.MM80 else PaperWidth.MM58,
            hasCutter = model.contains("cut", ignoreCase = true),
            hasCashDrawer = true,
            hasBuzzer = true,
            serial = serial,
        )
    }

    override suspend fun submit(payload: PrintPayload, settings: PrintSettings): PrintOutcome {
        val svc = service ?: return PrintOutcome.fail("Sunmi head not connected")
        val bytes = when (payload) {
            is PrintPayload.Raw -> payload.bytes
            else -> {
                val r = Payloads.toReceipt(payload, settings)
                    ?: return PrintOutcome.fail("Nothing to render")
                EscPosReceiptRenderer.render(r, settings)
            }
        }
        return sendRaw(svc, bytes)
    }

    /**
     * Sunmi's `sendRAWData` has a **per-call cap** (~16 KB on most firmwares — exact size
     * varies by model) and silently drops anything past it. With a logo plus a full receipt
     * we routinely cross that line, which produced the "only the top of the slip prints"
     * symptom: the logo's raster bytes fit, the text bytes after them are lost.
     *
     * The fix matches what RawBT and the Sunmi printer demo do:
     *  1. Open a buffered transaction with [IWoyouService.enterPrinterBuffer] so the head
     *     accumulates the job instead of trying to print each chunk inline.
     *  2. Feed the bytes in small chunks, awaiting each callback before sending the next
     *     so we never overrun the head's input buffer.
     *  3. Add a final [IWoyouService.lineWrap] so the printed content clears the head and
     *     the cutter blade.
     *  4. Flush with [IWoyouService.exitPrinterBuffer]`(true)` + a belt-and-braces
     *     [IWoyouService.commitPrinterBuffer] — different firmwares respond to one or the
     *     other, and calling both is harmless.
     *
     * All optional buffered-mode methods are wrapped in [runCatching] so a stripped-down
     * firmware that lacks them still gets the chunked sends and works.
     */
    private suspend fun sendRaw(svc: IWoyouService, bytes: ByteArray): PrintOutcome {
        var enteredBuffer = false
        try {
            runCatching { svc.enterPrinterBuffer(true) }.onSuccess { enteredBuffer = true }

            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK_BYTES, bytes.size)
                val chunk = bytes.copyOfRange(offset, end)
                val chunkDone = CompletableDeferred<PrintOutcome>()
                runCatching { svc.sendRAWData(chunk, makeCallback(chunkDone)) }
                    .onFailure { return PrintOutcome.fail("sendRAWData threw at $offset: ${it.message}") }
                val r = withTimeoutOrNull(CHUNK_TIMEOUT_MS) { chunkDone.await() }
                    ?: return finish(svc, enteredBuffer, commit = false, PrintOutcome.fail("Chunk timeout at byte $offset of ${bytes.size}"))
                if (!r.success) return finish(svc, enteredBuffer, commit = false, r)
                offset = end
            }

            // Pad-feed so the printed content clears the head / cutter blade. lineWrap may
            // not be honoured by every firmware — runCatching keeps us moving if not.
            val padDone = CompletableDeferred<PrintOutcome>()
            runCatching { svc.lineWrap(3, makeCallback(padDone)) }
            withTimeoutOrNull(FLUSH_TIMEOUT_MS) { padDone.await() }

            return finish(svc, enteredBuffer, commit = true, PrintOutcome.OK)
        } catch (e: Exception) {
            return finish(svc, enteredBuffer, commit = false, PrintOutcome.fail("Send failed: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /** Always flush/commit on the way out, no matter how we got here. */
    private fun finish(
        svc: IWoyouService,
        enteredBuffer: Boolean,
        commit: Boolean,
        outcome: PrintOutcome,
    ): PrintOutcome {
        if (enteredBuffer) runCatching { svc.exitPrinterBuffer(commit) }
        runCatching { svc.commitPrinterBuffer() }
        return outcome
    }

    private fun makeCallback(done: CompletableDeferred<PrintOutcome>) = object : ICallback.Stub() {
        override fun onRunResult(isSuccess: Boolean) {
            complete(if (isSuccess) PrintOutcome.OK else PrintOutcome.fail("Head reported failure"))
        }
        override fun onReturnString(result: String?) { /* not used for raw */ }
        override fun onRaiseException(code: Int, msg: String?) {
            complete(PrintOutcome.fail("Printer exception $code: ${msg ?: ""}"))
        }
        override fun onPrintResult(code: Int, msg: String?) {
            complete(PrintOutcome(code == 0, msg ?: if (code == 0) "Printed" else "Code $code"))
        }
        private fun complete(o: PrintOutcome) { if (!done.isCompleted) done.complete(o) }
    }

    override fun close() {
        runCatching { context.unbindService(connection) }
        service = null
        connectionState = ConnectionState.DISCONNECTED
    }

    /**
     * Map the Sunmi `updatePrinterState` code to our status model.
     *
     * Sunmi *documents* fixed codes (1=normal, 4=paperOut, 5=overheat, 6=cover open, 7=cutter),
     * but real-world firmware drifts hard: many devices return non-1 codes for *normal* states
     * (notably 5 after a recent print — the head is just warm, not overheated). RawBT just
     * sends the bytes and trusts the head's print callback, which is why it "always works".
     *
     * So we only flag the two conditions a user can act on — **paper out** and **cover open** —
     * everything else is reported Ready with the raw code in the message for diagnostics.
     * The print callback in [sendRaw] will still surface a real hardware failure if the head
     * actually refuses the job.
     */
    private fun mapState(code: Int): PrinterStatus = when (code) {
        4 -> PrinterStatus(ConnectionState.CONNECTED, ready = false, paperOut = true, message = "Out of paper")
        6 -> PrinterStatus(ConnectionState.CONNECTED, ready = false, coverOpen = true, message = "Cover open")
        505 -> PrinterStatus.unavailable("Printer not detected")
        else -> PrinterStatus.ready(when (code) {
            1 -> "Ready"
            2 -> "Ready (preparing)"
            3 -> "Ready (link noisy)"
            5 -> "Ready (head warm)"
            7 -> "Ready (cutter warning)"
            else -> "Ready (state $code)"
        })
    }

    // Small wrappers so capabilities() reads cleanly and tolerates AIDL quirks.
    private fun IWoyouService.printerModal(): String? = getPrinterModal()
    private fun IWoyouService.serial(): String? = getPrinterSerialNo()

    companion object {
        private const val SUNMI_PACKAGE = "woyou.aidlservice.jiuiv5"
        private const val SUNMI_ACTION = "woyou.aidlservice.jiuiv5.IWoyouService"
        private const val CONNECT_TIMEOUT_MS = 4_000L
        // Per-call cap: kept well below the ~16 KB at which Sunmi firmwares truncate.
        // 1 KB chunks let even huge receipts (multi-image catalogues) print intact.
        private const val CHUNK_BYTES = 1024
        // Each chunk completes fast on the head; a few seconds is plenty even when the
        // head is mid-burn. Total job time scales with size but never blocks on a single chunk.
        private const val CHUNK_TIMEOUT_MS = 6_000L
        private const val FLUSH_TIMEOUT_MS = 3_000L
    }
}
