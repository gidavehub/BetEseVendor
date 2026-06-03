package com.betesepmu.vendor.printer.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.betesepmu.vendor.core.ConnectionState
import com.betesepmu.vendor.core.PaperWidth
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.core.PrinterCapabilities
import com.betesepmu.vendor.core.PrinterStatus
import com.betesepmu.vendor.document.BitmapReceiptRenderer
import com.betesepmu.vendor.escpos.EscPos
import com.betesepmu.vendor.printer.Payloads
import com.betesepmu.vendor.printer.PrintOutcome
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.printer.PrinterTransport
import com.betesepmu.vendor.printer.TransportKind
import com.betesepmu.vendor.render.RasterEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

/** A device the user has already paired in Android Settings. */
data class BondedPrinter(val name: String, val address: String)

/**
 * Bluetooth Classic (SPP / RFCOMM) transport for external thermal printers — what
 * cheap battery-powered receipt printers and the printer module inside many POS sleds
 * actually expose. The integrated printer on some "Android POS" terminals is also just
 * a Bluetooth module behind the case, so this is the path that "sometimes works" on
 * those devices when the Sunmi AIDL service isn't present.
 *
 * Pairing happens once in Android Settings (better UX than reimplementing the bond
 * flow); here we only connect to already-paired devices by MAC. On Android 12+ we need
 * BLUETOOTH_CONNECT at runtime — [permissionMissing] surfaces that as a clean error
 * instead of a SecurityException, and the Settings screen has the grant button.
 */
class BluetoothTransport(private val context: Context) : PrinterTransport {

    override val name: String get() = currentName.ifBlank { "Bluetooth printer" }
    override val kind = TransportKind.BLUETOOTH

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var connectionState: ConnectionState = ConnectionState.DISCONNECTED
    @Volatile private var currentAddress: String = ""
    @Volatile private var currentName: String = ""

    /** Called by [com.betesepmu.vendor.printer.TransportManager] before connect() — the
     *  MAC address comes from [PrintSettings.bluetoothDeviceAddress]. */
    fun targetDevice(address: String, name: String) {
        val newAddr = address.trim()
        if (newAddr != currentAddress) closeQuietly()
        currentAddress = newAddr
        currentName = name.trim()
    }

    override fun state(): ConnectionState = connectionState

    override suspend fun connect(): PrinterStatus = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true) return@withContext PrinterStatus.ready("Connected to ${displayName()}")

        val a = adapter ?: return@withContext PrinterStatus.unavailable("Bluetooth not supported on this device")
        if (!a.isEnabled) { connectionState = ConnectionState.UNAVAILABLE; return@withContext PrinterStatus.unavailable("Bluetooth is off") }
        if (permissionMissing()) { connectionState = ConnectionState.UNAVAILABLE; return@withContext PrinterStatus.unavailable("Bluetooth permission not granted") }
        if (currentAddress.isBlank()) { connectionState = ConnectionState.UNAVAILABLE; return@withContext PrinterStatus.unavailable("No Bluetooth printer selected") }

        connectionState = ConnectionState.CONNECTING
        runCatching {
            val device: BluetoothDevice = a.getRemoteDevice(currentAddress)
            if (currentName.isBlank()) currentName = runCatching { device.name }.getOrNull().orEmpty()
            runCatching { a.cancelDiscovery() }  // discovery interferes with RFCOMM connects
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            output = s.outputStream
            connectionState = ConnectionState.CONNECTED
            PrinterStatus.ready("Connected to ${displayName()}")
        }.getOrElse { e ->
            closeQuietly()
            connectionState = ConnectionState.UNAVAILABLE
            PrinterStatus.unavailable("Bluetooth connect failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun status(): PrinterStatus {
        val s = socket
        return if (s == null || !s.isConnected) PrinterStatus.unavailable(
            if (currentAddress.isBlank()) "No printer selected" else "Disconnected"
        ) else PrinterStatus.ready("Connected to ${displayName()}")
    }

    override fun capabilities(): PrinterCapabilities = PrinterCapabilities(
        vendor = "Bluetooth",
        model = displayName(),
        // Most BT thermal printers (MTP-II, GOOJPRT etc.) are 58mm; user can override in Settings.
        paperWidth = PaperWidth.MM58,
        hasCutter = false,
        hasCashDrawer = false,
        hasBuzzer = false,
        serial = currentAddress,
    )

    override suspend fun submit(payload: PrintPayload, settings: PrintSettings): PrintOutcome = withContext(Dispatchers.IO) {
        // Re-connect on demand if the OS dropped the socket between jobs.
        if (socket?.isConnected != true) {
            val st = connect()
            if (st.connection != ConnectionState.CONNECTED) return@withContext PrintOutcome.fail(st.message)
        }
        val out = output ?: return@withContext PrintOutcome.fail("Not connected")

        // **Graphics-mode rendering for Bluetooth printers.** Cheap BT thermal heads
        // (and the inner-printer-over-BT modules on most POS sleds) have flaky ESC/POS
        // text rendering — they handle the first image command, then their command
        // parser stalls or mis-aligns and the body text never prints. The fix that
        // every working library (RawBT, ESC/POS Thermal Printer, etc.) uses is to
        // render the *entire* receipt to one bitmap and stream it as small raster bands.
        // The head only ever sees "burn these dots", which is the one thing every cheap
        // printer does reliably. Raw payloads still pass through as-is for callers who
        // want to send pre-baked bytes.
        val bytes = when (payload) {
            is PrintPayload.Raw -> payload.bytes
            else -> {
                val receipt = Payloads.toReceipt(payload, settings)
                    ?: return@withContext PrintOutcome.fail("Nothing to render")
                val bmp = BitmapReceiptRenderer(settings).render(receipt)
                val builder = java.io.ByteArrayOutputStream(bmp.width * bmp.height / 8 + 64)
                builder.write(EscPos.INIT)               // ESC @ — start the job clean
                builder.write(RasterEncoder.encode(bmp, settings.paperWidth.dots, settings.ditherMode))
                builder.write(byteArrayOf(0x0A, 0x0A, 0x0A))  // feed lines so content clears head
                if (settings.autoCut) builder.write(EscPos.cutFeed(settings.cutFeedDots))
                builder.toByteArray()
            }
        }
        runCatching {
            // **Single big write — trust RFCOMM flow control.**
            //
            // The previous version chunked at 180 B with a 40 ms inter-chunk delay. That was
            // intended to protect the head from buffer overflow, but it had the opposite
            // effect: a full receipt with logo (~70 KB of raster data) took ~16 seconds to
            // transmit, which is well past the **job timeout** on many cheap BT thermal
            // printers (they auto-cut and finalise after a few seconds of slow data, then
            // drop everything that comes after). Symptom: only the logo prints.
            //
            // Android's BluetoothSocket OutputStream already chunks at the L2CAP MTU and
            // honours RFCOMM's credit-based flow control — `write()` blocks naturally when
            // the receiver can't keep up. Doing our own throttling on top just made things
            // slower, never safer. RawBT works for the same reason: it dumps the bytes and
            // lets the stack do its job.
            out.write(bytes)
            out.flush()
            // Belt-and-braces trailing feeds so the bottom of the receipt clears the head
            // and any cutter blade even when the receipt itself didn't end with feeds.
            out.write(TRAILING_FEEDS)
            out.flush()
            // Generous settle time so the BT stack and printer buffer drain *before* the
            // spooler accepts the next job. Without this, a follow-up print can race the
            // tail of this one through the head.
            delay(FINAL_SETTLE_MS)
            PrintOutcome.OK
        }.getOrElse { e ->
            closeQuietly()
            connectionState = ConnectionState.DISCONNECTED
            PrintOutcome.fail("Bluetooth send failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Paired devices the user has bonded in Android Settings. Empty list if no permission. */
    fun pairedDevices(): List<BondedPrinter> {
        if (permissionMissing()) return emptyList()
        val a = adapter ?: return emptyList()
        return runCatching {
            a.bondedDevices.orEmpty().map { d ->
                BondedPrinter(
                    name = runCatching { d.name }.getOrNull().orEmpty().ifBlank { d.address },
                    address = d.address,
                )
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun isAdapterEnabled(): Boolean = adapter?.isEnabled == true
    fun hasAdapter(): Boolean = adapter != null

    override fun close() = closeQuietly()

    private fun closeQuietly() {
        runCatching { output?.close() }
        runCatching { socket?.close() }
        output = null
        socket = null
        connectionState = ConnectionState.DISCONNECTED
    }

    private fun displayName(): String = when {
        currentName.isNotBlank() -> currentName
        currentAddress.isNotBlank() -> currentAddress
        else -> "Bluetooth printer"
    }

    fun permissionMissing(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    } else false

    companion object {
        // Standard SPP service UUID — every thermal printer that exposes Bluetooth Classic uses this.
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Long enough for the printer to finish burning the bottom of the receipt and
        // perform the cut before we report success and let the spooler start the next job.
        private const val FINAL_SETTLE_MS = 1_000L
        // Extra line feeds appended after the receipt's own bytes so the bottom clears
        // the print head and any cutter blade (some cheap heads hold trailing lines
        // until they see more data).
        private val TRAILING_FEEDS = byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A)
    }
}
