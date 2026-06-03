package com.betesepmu.vendor.data

import android.content.Context
import android.content.SharedPreferences
import com.betesepmu.vendor.core.DitherMode
import com.betesepmu.vendor.core.PaperWidth
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.core.TransportPreference
import com.betesepmu.vendor.escpos.CodePage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive, SharedPreferences-backed store for [PrintSettings]. Kept dependency-free
 * (no DataStore) and exposes a [StateFlow] so the Settings screen and renderers always
 * see the latest configuration. Persist by passing a transform to [update].
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("betese_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<PrintSettings> = _settings

    val current: PrintSettings get() = _settings.value

    fun update(transform: (PrintSettings) -> PrintSettings) {
        val next = transform(_settings.value)
        persist(next)
        _settings.value = next
    }

    private fun load(): PrintSettings = PrintSettings(
        paperWidth = PaperWidth.fromName(prefs.getString(K_PAPER, null)),
        codePage = CodePage.fromName(prefs.getString(K_CODEPAGE, null)),
        ditherMode = DitherMode.fromName(prefs.getString(K_DITHER, null)),
        density = prefs.getInt(K_DENSITY, 8),
        autoCut = prefs.getBoolean(K_AUTOCUT, true),
        cutFeedDots = prefs.getInt(K_CUTFEED, 80),
        openCashDrawer = prefs.getBoolean(K_DRAWER, false),
        beepOnComplete = prefs.getBoolean(K_BEEP, true),
        copies = prefs.getInt(K_COPIES, 1),
        transportPreference = TransportPreference.fromName(prefs.getString(K_TRANSPORT, null)),
        bluetoothDeviceAddress = prefs.getString(K_BT_ADDR, null).orEmpty(),
        bluetoothDeviceName = prefs.getString(K_BT_NAME, null).orEmpty(),
        httpServerEnabled = prefs.getBoolean(K_HTTP, false),
        httpPort = prefs.getInt(K_HTTP_PORT, 9100),
        businessName = prefs.getString(K_BIZ_NAME, null) ?: PrintSettings().businessName,
        businessAddress = prefs.getString(K_BIZ_ADDR, null) ?: PrintSettings().businessAddress,
        businessPhone = prefs.getString(K_BIZ_PHONE, null) ?: PrintSettings().businessPhone,
        currencySymbol = prefs.getString(K_CURRENCY, null) ?: PrintSettings().currencySymbol,
        footerText = prefs.getString(K_FOOTER, null) ?: PrintSettings().footerText,
    )

    private fun persist(s: PrintSettings) {
        prefs.edit().apply {
            putString(K_PAPER, s.paperWidth.name)
            putString(K_CODEPAGE, s.codePage.name)
            putString(K_DITHER, s.ditherMode.name)
            putInt(K_DENSITY, s.density)
            putBoolean(K_AUTOCUT, s.autoCut)
            putInt(K_CUTFEED, s.cutFeedDots)
            putBoolean(K_DRAWER, s.openCashDrawer)
            putBoolean(K_BEEP, s.beepOnComplete)
            putInt(K_COPIES, s.copies)
            putString(K_TRANSPORT, s.transportPreference.name)
            putString(K_BT_ADDR, s.bluetoothDeviceAddress)
            putString(K_BT_NAME, s.bluetoothDeviceName)
            putBoolean(K_HTTP, s.httpServerEnabled)
            putInt(K_HTTP_PORT, s.httpPort)
            putString(K_BIZ_NAME, s.businessName)
            putString(K_BIZ_ADDR, s.businessAddress)
            putString(K_BIZ_PHONE, s.businessPhone)
            putString(K_CURRENCY, s.currencySymbol)
            putString(K_FOOTER, s.footerText)
        }.apply()
    }

    private companion object {
        const val K_PAPER = "paper_width"
        const val K_CODEPAGE = "code_page"
        const val K_DITHER = "dither_mode"
        const val K_DENSITY = "density"
        const val K_AUTOCUT = "auto_cut"
        const val K_CUTFEED = "cut_feed"
        const val K_DRAWER = "cash_drawer"
        const val K_BEEP = "beep"
        const val K_COPIES = "copies"
        const val K_TRANSPORT = "transport"
        const val K_BT_ADDR = "bt_addr"
        const val K_BT_NAME = "bt_name"
        const val K_HTTP = "http_enabled"
        const val K_HTTP_PORT = "http_port"
        const val K_BIZ_NAME = "biz_name"
        const val K_BIZ_ADDR = "biz_addr"
        const val K_BIZ_PHONE = "biz_phone"
        const val K_CURRENCY = "currency"
        const val K_FOOTER = "footer"
    }
}
