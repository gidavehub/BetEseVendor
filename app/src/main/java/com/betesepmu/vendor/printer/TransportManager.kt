package com.betesepmu.vendor.printer

import android.content.Context
import com.betesepmu.vendor.core.ConnectionState
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.core.PrinterStatus
import com.betesepmu.vendor.core.TransportPreference
import com.betesepmu.vendor.data.SettingsRepository
import com.betesepmu.vendor.printer.bluetooth.BluetoothTransport
import com.betesepmu.vendor.printer.preview.PreviewTransport
import com.betesepmu.vendor.printer.sunmi.SunmiTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the available transports and decides which one is active. AUTO prefers the Sunmi
 * built-in head, then falls back to a saved Bluetooth printer, and finally to the on-screen
 * preview — so the app is always usable. Exposes reactive [active] / [status] for the UI
 * and the [PreviewTransport.previews] stream for the preview surface.
 *
 * Holds a [SettingsRepository] reference so transports can be configured (e.g. Bluetooth
 * target MAC) from the persisted settings without callers having to plumb it through.
 */
class TransportManager(context: Context, private val settingsRepository: SettingsRepository) {

    val preview = PreviewTransport()
    val sunmi = SunmiTransport(context.applicationContext)
    val bluetooth = BluetoothTransport(context.applicationContext)

    private val _active = MutableStateFlow<PrinterTransport>(preview)
    val active: StateFlow<PrinterTransport> = _active

    private val _status = MutableStateFlow(PrinterStatus.UNKNOWN)
    val status: StateFlow<PrinterStatus> = _status

    val previews get() = preview.previews

    /** Connect according to [preference], updating [active] and [status]. */
    suspend fun connect(preference: TransportPreference): PrinterStatus {
        val s = settingsRepository.current
        return when (preference) {
            TransportPreference.PREVIEW -> use(preview)
            TransportPreference.SUNMI -> use(sunmi)
            TransportPreference.BLUETOOTH -> {
                bluetooth.targetDevice(s.bluetoothDeviceAddress, s.bluetoothDeviceName)
                use(bluetooth)
            }
            TransportPreference.AUTO -> {
                // 1) Try the Sunmi built-in head.
                val st = sunmi.connect()
                if (st.connection == ConnectionState.CONNECTED) {
                    setActive(sunmi, st); return st
                }
                // 2) Fall back to a saved Bluetooth printer, if any.
                if (s.bluetoothDeviceAddress.isNotBlank() && bluetooth.hasAdapter() && !bluetooth.permissionMissing()) {
                    bluetooth.targetDevice(s.bluetoothDeviceAddress, s.bluetoothDeviceName)
                    val bt = bluetooth.connect()
                    if (bt.connection == ConnectionState.CONNECTED) {
                        setActive(bluetooth, bt); return bt
                    }
                }
                // 3) Finally, the on-screen preview so the app is always functional.
                use(preview)
            }
        }
    }

    private suspend fun use(t: PrinterTransport): PrinterStatus {
        val s = t.connect()
        setActive(t, s)
        return s
    }

    private fun setActive(t: PrinterTransport, s: PrinterStatus) {
        _active.value = t
        _status.value = s
    }

    suspend fun refreshStatus(): PrinterStatus {
        val s = _active.value.status()
        _status.value = s
        return s
    }

    suspend fun submit(payload: PrintPayload, settings: PrintSettings): PrintOutcome =
        _active.value.submit(payload, settings)

    fun shutdown() {
        sunmi.close()
        bluetooth.close()
        preview.close()
    }
}
