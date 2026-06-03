package com.betesepmu.vendor.integration

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.betesepmu.vendor.BetEseApp
import com.betesepmu.vendor.IBetEsePrinter
import com.betesepmu.vendor.document.JsonReceipt
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.spooler.JobSource
import org.json.JSONObject

/**
 * Bound service exposing [IBetEsePrinter] for in-process or cross-app integration. SWIPE
 * (or any sister app) binds with action `com.betesepmu.vendor.PRINT_BROKER` and calls
 * `printText` / `printJson` / `printRaw`, which funnel into the same spooler the UI uses.
 */
class BetEseBrokerService : Service() {

    private val binder = object : IBetEsePrinter.Stub() {
        override fun printText(text: String?): Long =
            container().enqueue("AIDL text", JobSource.AIDL, PrintPayload.PlainText(text ?: ""))

        override fun printJson(json: String?): Long =
            container().enqueue("AIDL document", JobSource.AIDL, PrintPayload.ReceiptDoc(JsonReceipt.parse(json ?: "{}")))

        override fun printRaw(data: ByteArray?): Long =
            container().enqueue("AIDL raw", JobSource.AIDL, PrintPayload.Raw(data ?: ByteArray(0)))

        override fun status(): String {
            val st = container().transportManager.status.value
            val t = container().transportManager.active.value
            return JSONObject().apply {
                put("transport", t.name)
                put("ready", st.ready)
                put("paperOut", st.paperOut)
                put("message", st.message)
            }.toString()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun container() = BetEseApp.container(this)
}
