package com.betesepmu.vendor.integration

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import com.betesepmu.vendor.BetEseApp
import com.betesepmu.vendor.document.JsonReceipt
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.spooler.JobSource

/**
 * The developer-facing intent surface — the most-used path for other apps. Invisible
 * (translucent theme): it parses the incoming intent, enqueues a job, toasts, and finishes.
 *
 * Supported entry points:
 *  - `ACTION_SEND` of `text/plain` (the system Share sheet).
 *  - `VIEW` of a `betese://print?...` URI with `text`, `json` or `base64` query params.
 *  - `com.betesepmu.vendor.action.PRINT` with `EXTRA_TEXT` / `json` / `escpos_base64`.
 */
class PrintIntentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = parse(intent)
        if (payload == null) {
            toast("BetEse Vendor: nothing to print")
        } else {
            BetEseApp.container(this).enqueue("Shared print", JobSource.INTENT, payload)
            toast("Sent to BetEse Vendor")
        }
        finish()
    }

    private fun parse(intent: Intent?): PrintPayload? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { PrintPayload.PlainText(it) }
            Intent.ACTION_VIEW -> intent.data?.let { fromUri(it) }
            else -> fromExtras(intent)
        } ?: fromExtras(intent)
    }

    private fun fromUri(uri: Uri): PrintPayload? {
        uri.getQueryParameter("base64")?.let { return PrintPayload.Raw(Base64.decode(it, Base64.URL_SAFE or Base64.DEFAULT)) }
        uri.getQueryParameter("json")?.let { return PrintPayload.ReceiptDoc(JsonReceipt.parse(it)) }
        uri.getQueryParameter("text")?.let { return PrintPayload.PlainText(it) }
        return null
    }

    private fun fromExtras(intent: Intent): PrintPayload? {
        intent.getStringExtra(EXTRA_ESCPOS_BASE64)?.let { return PrintPayload.Raw(Base64.decode(it, Base64.DEFAULT)) }
        intent.getStringExtra(EXTRA_JSON)?.let { return PrintPayload.ReceiptDoc(JsonReceipt.parse(it)) }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { return PrintPayload.PlainText(it) }
        intent.getStringExtra(EXTRA_TEXT)?.let { return PrintPayload.PlainText(it) }
        return null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val ACTION_PRINT = "com.betesepmu.vendor.action.PRINT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_JSON = "json"
        const val EXTRA_ESCPOS_BASE64 = "escpos_base64"
    }
}
