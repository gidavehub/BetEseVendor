package com.betesepmu.vendor.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.betesepmu.vendor.BetEseApp
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.document.BitmapReceiptRenderer
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.printer.TransportKind
import com.betesepmu.vendor.printer.bluetooth.BondedPrinter
import com.betesepmu.vendor.printer.preview.PreviewItem
import com.betesepmu.vendor.samples.SampleDoc
import com.betesepmu.vendor.spooler.JobSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** What the full-screen preview overlay is currently showing. */
data class PreviewUi(val title: String, val bitmap: Bitmap?, val loading: Boolean)

/**
 * Single ViewModel bridging the [com.betesepmu.vendor.di.AppContainer] to Compose.
 * Re-exposes the container's reactive state and turns UI gestures into spooler/preview
 * actions. An [AndroidViewModel] so the default factory can supply the [Application].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = BetEseApp.container(app)

    val settings: StateFlow<PrintSettings> = container.settingsRepository.settings
    val status = container.transportManager.status
    val activeTransport = container.transportManager.active
    val jobs = container.printQueue.jobs
    val previews = container.transportManager.previews

    private val _preview = MutableStateFlow<PreviewUi?>(null)
    val preview: StateFlow<PreviewUi?> = _preview
    private var previewSample: SampleDoc? = null

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        viewModelScope.launch { container.transportManager.connect(settings.value.transportPreference) }
        // When the active transport is the on-screen preview, automatically open the
        // full-screen overlay as each new job lands — otherwise the user sees "Printed"
        // in the snackbar with no visible output and assumes nothing happened.
        viewModelScope.launch {
            var lastShownId = -1L
            previews.collect { list ->
                val latest = list.firstOrNull() ?: return@collect
                if (latest.id > lastShownId) {
                    lastShownId = latest.id
                    if (activeTransport.value.kind == TransportKind.PREVIEW) {
                        previewSample = null
                        _preview.value = PreviewUi(latest.label, latest.bitmap, loading = false)
                    }
                }
            }
        }
    }

    fun reconnect() = viewModelScope.launch {
        val s = container.transportManager.connect(settings.value.transportPreference)
        messages.tryEmit(s.message)
    }

    // ---- Samples ------------------------------------------------------------
    fun sampleList(): List<SampleDoc> = container.samples().all()

    /** Quick "does it print?" action used from Home — prints the sales receipt sample. */
    fun samplesQuickTest() {
        val catalog = container.samples()
        val doc = catalog.byId("receipt") ?: catalog.all().first()
        printSample(doc)
    }

    fun printSample(doc: SampleDoc) {
        container.enqueue(doc.title, JobSource.SAMPLE, PrintPayload.ReceiptDoc(doc.build()))
        messages.tryEmit("\"${doc.title}\" sent to queue")
    }

    fun previewSampleDoc(doc: SampleDoc) {
        previewSample = doc
        _preview.value = PreviewUi(doc.title, null, loading = true)
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                BitmapReceiptRenderer(settings.value).render(doc.build())
            }
            _preview.value = PreviewUi(doc.title, bmp, loading = false)
        }
    }

    fun showPreviewItem(item: PreviewItem) {
        previewSample = null
        _preview.value = PreviewUi(item.label, item.bitmap, loading = false)
    }

    fun closePreview() { _preview.value = null }

    fun printFromPreview() {
        val doc = previewSample
        if (doc != null) printSample(doc)
        else _preview.value?.bitmap?.let {
            container.enqueue("Reprint", JobSource.MANUAL, PrintPayload.Image(it))
            messages.tryEmit("Sent to queue")
        }
    }

    // ---- Queue --------------------------------------------------------------
    fun retry(id: Long) = container.printQueue.retry(id)
    fun cancel(id: Long) = container.printQueue.cancel(id)
    fun clearFinished() = container.printQueue.clearFinished()

    // ---- Settings -----------------------------------------------------------
    fun update(transform: (PrintSettings) -> PrintSettings) {
        container.settingsRepository.update(transform)
        if (settings.value.httpServerEnabled) container.ensureSpoolerRunning()
    }

    // ---- Bluetooth ----------------------------------------------------------
    /** Paired Bluetooth printers (post-permission). Empty if permission missing or none paired. */
    fun pairedBluetoothPrinters(): List<BondedPrinter> = container.transportManager.bluetooth.pairedDevices()

    fun bluetoothAdapterAvailable(): Boolean = container.transportManager.bluetooth.hasAdapter()
    fun bluetoothAdapterEnabled(): Boolean = container.transportManager.bluetooth.isAdapterEnabled()
    fun bluetoothPermissionMissing(): Boolean = container.transportManager.bluetooth.permissionMissing()

    /** Save the chosen printer, switch the transport preference to BT and reconnect. */
    fun selectBluetoothPrinter(printer: BondedPrinter) {
        update {
            it.copy(
                bluetoothDeviceAddress = printer.address,
                bluetoothDeviceName = printer.name,
                transportPreference = com.betesepmu.vendor.core.TransportPreference.BLUETOOTH,
            )
        }
        reconnect()
        messages.tryEmit("Selected ${printer.name}")
    }

    fun clearBluetoothPrinter() {
        update { it.copy(bluetoothDeviceAddress = "", bluetoothDeviceName = "") }
        messages.tryEmit("Bluetooth printer cleared")
    }

    // ---- Save / share -------------------------------------------------------
    fun savePreview(context: Context) {
        val bmp = _preview.value?.bitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = saveToGallery(context, bmp)
            messages.tryEmit(if (ok) "Saved to Pictures/BetEseVendor" else "Could not save image")
        }
    }

    fun sharePreview(context: Context) {
        val bmp = _preview.value?.bitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "betese_preview.png")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { messages.tryEmit("Share failed: ${it.message}") }
        }
    }

    private fun saveToGallery(context: Context, bmp: Bitmap): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "betese_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BetEseVendor")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "BetEseVendor").apply { mkdirs() }
            val file = File(dir, "betese_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    }.getOrDefault(false)
}
