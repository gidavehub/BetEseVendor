package com.betesepmu.vendor.samples

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.betesepmu.vendor.R
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.document.Column
import com.betesepmu.vendor.document.Receipt
import com.betesepmu.vendor.document.receipt
import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.escpos.BarcodeType
import com.betesepmu.vendor.escpos.TextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A named, buildable test document shown on the Samples screen. */
data class SampleDoc(
    val id: String,
    val title: String,
    val description: String,
    val iconKey: String,
    val build: () -> Receipt,
)

/**
 * The print samples BetEse Vendor ships with — "test the printing with". Each exercises a
 * different part of the pipeline (text styling, columns, dithering, code pages, native vs
 * rasterised codes, hardware actions) so a new install can be validated end to end.
 */
class SampleCatalog(private val context: Context, private val settings: PrintSettings) {

    private val logo: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.betese_logo)
    }

    fun all(): List<SampleDoc> = listOf(
        SampleDoc("receipt", "Sales Receipt", "Logo, itemised columns, totals, QR verification", "receipt", ::retailReceipt),
        SampleDoc("betslip", "Bet Slip", "Selections, odds, stake, payout, barcode + QR", "bet", ::betSlip),
        SampleDoc("invoice", "Invoice", "Bill-to block, line items, tax, payment QR", "invoice", ::invoice),
        SampleDoc("barcodes", "Barcode Sheet", "CODE128, EAN-13, CODE39 native symbologies", "barcode", ::barcodeSheet),
        SampleDoc("qr", "QR Code Card", "Large native QR with caption", "qr", ::qrCard),
        SampleDoc("logo", "Logo Dither Test", "Logo at 100% and 60% width", "image", ::logoTest),
        SampleDoc("density", "Density / Greyscale", "Gradient + solids to tune dithering", "gradient", ::densityTest),
        SampleDoc("styles", "Alignment & Fonts", "Align, bold, underline, sizes, reverse, Font B", "format", ::stylesTest),
        SampleDoc("codepage", "Code Page Test", "Accented & symbol characters", "language", ::codePageTest),
        SampleDoc("drawer", "Cash Drawer Kick", "Pulse the drawer + beep", "drawer", ::drawerTest),
        SampleDoc("cut", "Auto-Cut Test", "Feed and cut the paper", "cut", ::cutterTest),
    )

    fun byId(id: String): SampleDoc? = all().firstOrNull { it.id == id }

    // ---- Helpers ------------------------------------------------------------
    private fun money(amount: Double) = "${settings.currencySymbol} ${String.format(Locale.US, "%,.2f", amount)}"
    private fun now(pattern: String) = SimpleDateFormat(pattern, Locale.US).format(Date())

    // ---- Samples ------------------------------------------------------------
    private fun retailReceipt(): Receipt = receipt {
        image(logo, widthPercent = 45)
        title(settings.businessName)
        center(settings.businessAddress)
        center("Tel: ${settings.businessPhone}")
        feed(1)
        divider()
        row("Receipt #", "BE-${now("yyMMdd")}-0427")
        row("Date", now("dd MMM yyyy HH:mm"))
        row("Cashier", "Awa J.")
        divider()
        columns(
            Column("ITEM", weight = 3, align = Align.LEFT, bold = true),
            Column("QTY", weight = 1, align = Align.CENTER, bold = true),
            Column("TOTAL", weight = 2, align = Align.RIGHT, bold = true),
        )
        divider()
        item("Rice 5kg bag", 2, 450.0)
        item("Cooking oil 1L", 3, 145.0)
        item("Sugar 1kg", 4, 75.0)
        item("Maggi cubes (pack)", 1, 60.0)
        item("Soft drink 50cl", 6, 35.0)
        divider()
        val subtotal = 2 * 450.0 + 3 * 145.0 + 4 * 75.0 + 60.0 + 6 * 35.0
        val vat = subtotal * 0.15
        row("Subtotal", money(subtotal))
        row("VAT (15%)", money(vat))
        divider('=')
        columns(
            Column("TOTAL", weight = 1, align = Align.LEFT, bold = true),
            Column(money(subtotal + vat), weight = 1, align = Align.RIGHT, bold = true),
        )
        feed(1)
        row("Paid (Cash)", money(2000.0))
        row("Change", money(2000.0 - (subtotal + vat)))
        feed(1)
        center("Scan to verify this receipt")
        qr("https://verify.betese.example/r/BE-0427", moduleSize = 5)
        feed(1)
        center(settings.footerText)
        center("Powered by BetEse Vendor")
        feed(2)
        if (settings.openCashDrawer) drawer()
        if (settings.beepOnComplete) beep(1)
        cut()
    }

    private fun com.betesepmu.vendor.document.ReceiptBuilder.item(name: String, qty: Int, unit: Double) {
        columns(
            Column(name, weight = 3, align = Align.LEFT),
            Column(qty.toString(), weight = 1, align = Align.CENTER),
            Column(money(qty * unit), weight = 2, align = Align.RIGHT),
        )
    }

    private fun betSlip(): Receipt = receipt {
        image(logo, widthPercent = 40)
        title("BET SLIP")
        center("BetEse Sports — Licensed Vendor")
        divider()
        row("Slip ID", "BES-7741-2290")
        row("Placed", now("dd MMM yyyy HH:mm"))
        row("Vendor", "Serrekunda Branch")
        divider()
        heading("SELECTIONS")
        selection("Scorpions vs Eagles", "1X2 — Home", "1.85")
        selection("Real M. vs Sevilla", "Over 2.5", "1.72")
        selection("Lakers vs Celtics", "Money Line — LAL", "2.10")
        divider()
        row("Total Odds", "6.68", boldRight = true)
        row("Stake", money(100.0))
        divider('=')
        emphasised("WIN ${money(668.0)}")
        feed(1)
        center("Scan or show barcode to cash out")
        barcode("7741229000", type = BarcodeType.CODE128)
        feed(1)
        qr("https://bet.betese.example/slip/BES-7741-2290", moduleSize = 5)
        feed(1)
        center("Bet responsibly. 18+")
        center(settings.footerText)
        feed(2)
        beep(2)
        cut()
    }

    private fun com.betesepmu.vendor.document.ReceiptBuilder.selection(match: String, market: String, odds: String) {
        text(match, TextStyle(bold = true))
        columns(
            Column("  $market", weight = 3, align = Align.LEFT),
            Column(odds, weight = 1, align = Align.RIGHT),
        )
    }

    private fun invoice(): Receipt = receipt {
        image(logo, widthPercent = 40)
        title(settings.businessName)
        center(settings.businessAddress)
        feed(1)
        emphasised("INVOICE")
        row("Invoice #", "INV-2026-0192")
        row("Date", now("dd MMM yyyy"))
        row("Due", "Net 14 days")
        divider()
        heading("BILL TO")
        text("Tendaba Camp Ltd")
        text("Lower River Region")
        text("VAT: GMB-552-018")
        divider()
        columns(
            Column("DESCRIPTION", weight = 3, align = Align.LEFT, bold = true),
            Column("AMOUNT", weight = 2, align = Align.RIGHT, bold = true),
        )
        divider()
        row("Website design & build", money(15000.0))
        row("Hosting (12 months)", money(6000.0))
        row("On-site training (2 days)", money(8000.0))
        divider()
        val sub = 15000.0 + 6000.0 + 8000.0
        val disc = 2000.0
        val tax = (sub - disc) * 0.15
        row("Subtotal", money(sub))
        row("Discount", "-${money(disc)}")
        row("VAT (15%)", money(tax))
        divider('=')
        row("TOTAL DUE", money(sub - disc + tax), boldRight = true)
        feed(1)
        center("Pay online")
        qr("https://pay.betese.example/inv/INV-2026-0192", moduleSize = 5)
        feed(1)
        center("Thank you for your business")
        feed(2)
        cut()
    }

    private fun barcodeSheet(): Receipt = receipt {
        title("BARCODE TEST")
        divider()
        text("CODE128", TextStyle.CENTER_BOLD)
        barcode("BETESE-2026", type = BarcodeType.CODE128)
        feed(1)
        text("EAN-13", TextStyle.CENTER_BOLD)
        barcode("5901234123457", type = BarcodeType.EAN13)
        feed(1)
        text("CODE39", TextStyle.CENTER_BOLD)
        barcode("BES123", type = BarcodeType.CODE39)
        feed(1)
        center("Native symbologies via GS k")
        feed(2)
        cut()
    }

    private fun qrCard(): Receipt = receipt {
        title("QR CODE")
        divider()
        qr("https://betese.example", moduleSize = 8)
        feed(1)
        center("https://betese.example")
        center("Native QR via GS ( k")
        feed(2)
        cut()
    }

    private fun logoTest(): Receipt = receipt {
        title("LOGO / DITHER")
        divider()
        center("100% width")
        image(logo, widthPercent = 100)
        feed(1)
        center("60% width")
        image(logo, widthPercent = 60)
        feed(1)
        center("Floyd–Steinberg by default")
        feed(2)
        cut()
    }

    private fun densityTest(): Receipt = receipt {
        title("DENSITY TEST")
        divider()
        center("Greyscale gradient")
        image(gradientBitmap(), widthPercent = 100)
        feed(1)
        center("Solid grey patches")
        image(patchesBitmap(), widthPercent = 100)
        feed(1)
        center("Tune dither mode in Settings")
        feed(2)
        cut()
    }

    private fun stylesTest(): Receipt = receipt {
        title("STYLE TEST")
        divider()
        text("Left aligned", TextStyle(align = Align.LEFT))
        text("Centre aligned", TextStyle(align = Align.CENTER))
        text("Right aligned", TextStyle(align = Align.RIGHT))
        divider()
        text("Bold text", TextStyle(bold = true))
        text("Underlined", TextStyle(underline = 1))
        text("Reversed", TextStyle(reverse = true, align = Align.CENTER))
        divider()
        text("Double width", TextStyle(widthMul = 2))
        text("Double height", TextStyle(heightMul = 2))
        text("BIG", TextStyle(widthMul = 2, heightMul = 2, align = Align.CENTER, bold = true))
        divider()
        text("Font B (narrow)", TextStyle(fontB = true))
        feed(2)
        cut()
    }

    private fun codePageTest(): Receipt = receipt {
        title("CODE PAGE")
        divider()
        center("Active: ${settings.codePage.displayName}")
        divider()
        text("Accents: café crème déjà vu")
        text("More: naïve, façade, piñata")
        text("Nordic: smørrebrød, Åland")
        text("Symbols: ${settings.currencySymbol} £ € ¥ ° ½")
        text("Punct: « guillemets » – —")
        feed(1)
        center("Change code page in Settings")
        feed(2)
        cut()
    }

    private fun drawerTest(): Receipt = receipt {
        title("DRAWER TEST")
        divider()
        center("Pulsing cash drawer now")
        center("(needs a drawer on the kick port)")
        feed(1)
        drawer(pin = 0)
        beep(1)
        feed(2)
        cut()
    }

    private fun cutterTest(): Receipt = receipt {
        title("CUTTER TEST")
        divider()
        center("Feeding then cutting…")
        feed(3)
        cut()
    }

    // ---- Generated bitmaps for the density test -----------------------------
    private fun gradientBitmap(): Bitmap {
        val w = settings.paperWidth.dots
        val h = 120
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun patchesBitmap(): Bitmap {
        val w = settings.paperWidth.dots
        val h = 90
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val shades = intArrayOf(20, 80, 140, 200)
        val pw = w / shades.size
        val p = Paint()
        shades.forEachIndexed { i, s ->
            p.color = Color.rgb(s, s, s)
            canvas.drawRect((i * pw).toFloat(), 0f, ((i + 1) * pw).toFloat(), h.toFloat(), p)
        }
        return bmp
    }
}
