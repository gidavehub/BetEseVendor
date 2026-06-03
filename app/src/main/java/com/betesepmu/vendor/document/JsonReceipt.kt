package com.betesepmu.vendor.document

import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.escpos.BarcodeType
import com.betesepmu.vendor.escpos.TextStyle
import org.json.JSONObject

/**
 * Parses a small, friendly JSON receipt description into a [Receipt]. This is the wire
 * format other apps use to print through BetEse Vendor over HTTP or AIDL without knowing
 * anything about ESC/POS.
 *
 * ```
 * { "elements": [
 *     { "type": "title",   "text": "BetEse Shop" },
 *     { "type": "row",     "left": "Item", "right": "D 100.00" },
 *     { "type": "divider", "char": "-" },
 *     { "type": "qr",      "data": "https://betese.example", "size": 6 },
 *     { "type": "cut" }
 * ] }
 * ```
 * Top-level `{ "text": "…" }` or `{ "title": "…" }` shorthands are also accepted.
 */
object JsonReceipt {

    fun parse(json: String): Receipt {
        val root = JSONObject(json)
        return receipt {
            val arr = root.optJSONArray("elements")
            if (arr == null) {
                root.optString("title").takeIf { it.isNotEmpty() }?.let { title(it) }
                root.optString("text").takeIf { it.isNotEmpty() }?.let { text(it) }
                if (root.optBoolean("cut", false)) cut()
            } else {
                for (i in 0 until arr.length()) element(arr.getJSONObject(i))
            }
        }
    }

    private fun ReceiptBuilder.element(o: JSONObject) {
        when (o.optString("type").lowercase()) {
            "title" -> title(o.getString("text"))
            "subtitle" -> subtitle(o.getString("text"))
            "heading" -> heading(o.getString("text"))
            "center", "centre" -> center(o.getString("text"))
            "emphasised", "emphasized" -> emphasised(o.getString("text"))
            "text" -> text(o.getString("text"), styleOf(o))
            "row" -> row(o.optString("left"), o.optString("right"), o.optBoolean("bold", false))
            "divider" -> divider(o.optString("char", "-").firstOrNull() ?: '-')
            "feed" -> feed(o.optInt("lines", 1))
            "qr" -> qr(o.getString("data"), o.optInt("size", 6), o.optBoolean("native", true))
            "barcode" -> barcode(o.getString("data"), barcodeOf(o.optString("format", "CODE128")), o.optBoolean("native", true))
            "cut" -> cut()
            "drawer" -> drawer(o.optInt("pin", 0))
            "beep" -> beep(o.optInt("times", 1))
            else -> if (o.has("text")) text(o.getString("text"))
        }
    }

    private fun styleOf(o: JSONObject): TextStyle {
        val size = o.optInt("size", 1).coerceIn(1, 8)
        return TextStyle(
            align = alignOf(o.optString("align", "left")),
            bold = o.optBoolean("bold", false),
            underline = if (o.optBoolean("underline", false)) 1 else 0,
            reverse = o.optBoolean("reverse", false),
            fontB = o.optBoolean("fontB", false),
            widthMul = o.optInt("width", size),
            heightMul = o.optInt("height", size),
        )
    }

    private fun alignOf(s: String): Align = when (s.lowercase()) {
        "center", "centre" -> Align.CENTER
        "right" -> Align.RIGHT
        else -> Align.LEFT
    }

    private fun barcodeOf(s: String): BarcodeType = when (s.uppercase().replace("-", "").replace("_", "")) {
        "UPCA" -> BarcodeType.UPC_A
        "UPCE" -> BarcodeType.UPC_E
        "EAN13" -> BarcodeType.EAN13
        "EAN8" -> BarcodeType.EAN8
        "CODE39" -> BarcodeType.CODE39
        "ITF" -> BarcodeType.ITF
        "CODABAR" -> BarcodeType.CODABAR
        "CODE93" -> BarcodeType.CODE93
        else -> BarcodeType.CODE128
    }
}
