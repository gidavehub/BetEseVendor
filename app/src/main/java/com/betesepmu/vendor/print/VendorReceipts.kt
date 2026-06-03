package com.betesepmu.vendor.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.betesepmu.vendor.R
import com.betesepmu.vendor.bet.Rapport
import com.betesepmu.vendor.core.PrintSettings
import com.betesepmu.vendor.document.Column
import com.betesepmu.vendor.document.Receipt
import com.betesepmu.vendor.document.receipt
import com.betesepmu.vendor.escpos.Align
import com.betesepmu.vendor.escpos.BarcodeType
import com.betesepmu.vendor.escpos.TextStyle
import com.betesepmu.vendor.model.DepositLog
import com.betesepmu.vendor.model.Race
import com.betesepmu.vendor.model.Ticket
import com.betesepmu.vendor.model.WithdrawalRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the [Receipt] documents the vendor terminal prints — tickets, deposit/withdrawal
 * receipts and sales reports — using the existing `receipt { }` DSL so they render to ESC/POS
 * bytes and the on-screen preview identically. Currency is GMD to match the betesepmu
 * printouts.
 */
class VendorReceipts(context: Context) {

    private val logo: Bitmap by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.betese_logo) }

    private fun money(amount: Double): String {
        val rounded = Math.round(amount * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) "GMD ${String.format(Locale.US, "%,d", rounded.toLong())}"
        else "GMD ${String.format(Locale.US, "%,.2f", rounded)}"
    }

    private fun dateTime(date: Date) = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(date)
    private fun dateOnly(date: Date) = SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)

    /** A placed/reprinted bet ticket — the customer's claim slip. */
    fun ticket(ticket: Ticket, settings: PrintSettings): Receipt = receipt {
        image(logo, widthPercent = 45)
        title("BETESE PMU")
        center("Pari Mutuel — Bet Ticket")
        divider()
        row("Ticket #", ticket.id)
        row("Date", dateTime(ticket.timestamp))
        ticket.vendorName?.takeIf { it.isNotBlank() }?.let { row("Vendor", it) }
        ticket.bookingCode?.takeIf { it.isNotBlank() }?.let { row("Booking", it) }
        divider()
        heading("SELECTIONS")
        ticket.selections.forEachIndexed { index, s ->
            text("${index + 1}. ${s.betType.label}", TextStyle.EMPHASISED)
            center(s.raceName)
            val picks = buildString {
                if (s.xCount > 0) append("X".repeat(s.xCount)).append("  ")
                append(s.numbers.joinToString("  "))
            }
            text("Horses: $picks")
            row("Stake  x${s.multiplier}", money(s.lineTotal))
            if (index < ticket.selections.lastIndex) divider('.')
        }
        divider('=')
        columns(
            Column("TOTAL STAKE", weight = 1, align = Align.LEFT, bold = true),
            Column(money(ticket.totalCost), weight = 1, align = Align.RIGHT, bold = true),
        )
        feed(1)
        center("Keep this ticket to claim winnings")
        barcode(ticket.id, type = BarcodeType.CODE128)
        qr("betese-ticket:${ticket.id}", moduleSize = 5)
        feed(1)
        center("Status: ${ticket.status}")
        center(settings.footerText)
        feed(2)
        if (settings.beepOnComplete) beep(1)
        cut()
    }

    /** Receipt for money deposited into a customer's wallet. */
    fun deposit(log: DepositLog, newBalance: Double?, settings: PrintSettings): Receipt = receipt {
        image(logo, widthPercent = 45)
        title("BETESE PMU")
        center("Customer Deposit Receipt")
        divider()
        row("Receipt #", log.id)
        row("Date", dateTime(log.timestamp))
        row("Processed by", log.processedByName)
        divider()
        row("Customer", log.customerName)
        log.customerPhone?.takeIf { it.isNotBlank() }?.let { row("Phone", it) }
        row("Method", log.method)
        divider('=')
        columns(
            Column("AMOUNT", weight = 1, align = Align.LEFT, bold = true),
            Column(money(log.amount), weight = 1, align = Align.RIGHT, bold = true),
        )
        newBalance?.let { row("New Wallet Balance", money(it), boldRight = true) }
        feed(1)
        center("Funds credited to the customer wallet")
        center(settings.footerText)
        feed(2)
        if (settings.beepOnComplete) beep(1)
        cut()
    }

    /** Receipt for a cash withdrawal paid out to a customer. */
    fun withdrawal(request: WithdrawalRequest, processedByName: String, settings: PrintSettings): Receipt = receipt {
        image(logo, widthPercent = 45)
        title("BETESE PMU")
        center("Withdrawal Payout Receipt")
        divider()
        row("Code", request.code)
        row("Date", dateTime(Date()))
        row("Paid by", processedByName)
        divider()
        row("Customer", request.userName)
        request.recipientPhone?.takeIf { it.isNotBlank() }?.let { row("Phone", it) }
        request.payoutMethod?.takeIf { it.isNotBlank() }?.let { row("Method", it) }
        divider('=')
        columns(
            Column("PAID OUT", weight = 1, align = Align.LEFT, bold = true),
            Column(money(request.amount), weight = 1, align = Align.RIGHT, bold = true),
        )
        feed(1)
        center("Cash paid to the customer")
        center(settings.footerText)
        feed(2)
        if (settings.beepOnComplete) beep(1)
        cut()
    }

    /** Official rapport (winning numbers + dividends) for a race that has a result. */
    fun rapport(race: Race, settings: PrintSettings): Receipt = receipt {
        image(logo, widthPercent = 40)
        title("BETESE PMU")
        center("Official Rapport")
        val result = race.result ?: return@receipt
        Rapport.sections(result).forEachIndexed { index, section ->
            val (sectionTitle, numbers, payouts) = section
            if (index > 0) divider('=')
            divider()
            subtitle(sectionTitle)
            row(dateOnly(race.endDate), race.name)
            if (index == 0 && race.nonRunners.isNotEmpty()) {
                center("NP: ${race.nonRunners.joinToString(", ")}")
            }
            divider()
            center("RESULT")
            heading(Rapport.formatNumbers(numbers))
            Rapport.visibleGroups(payouts).forEach { (group, rows) ->
                divider('.')
                text(group.title, TextStyle.EMPHASISED)
                rows.forEach { r -> row(r.label, money(Rapport.value(payouts, r.key) ?: 0.0)) }
            }
        }
        feed(1)
        center("BETESE PMU — OFFICIAL")
        center(dateTime(Date()))
        feed(2)
        if (settings.beepOnComplete) beep(1)
        cut()
    }

    /** Daily sales / end-of-sale summary for the vendor's shift. */
    fun salesReport(
        reportTitle: String,
        vendorName: String,
        day: Date,
        ticketSales: Double,
        ticketsSold: Int,
        paidOut: Double,
        payoutCount: Int,
        net: Double,
        settings: PrintSettings,
    ): Receipt = receipt {
        image(logo, widthPercent = 40)
        title("BETESE PMU")
        center(reportTitle)
        divider()
        row("Vendor", vendorName)
        row("Date", dateOnly(day))
        divider()
        row("Tickets sold", ticketsSold.toString())
        row("Ticket sales", money(ticketSales))
        row("Paid out", money(paidOut))
        row("Payouts", payoutCount.toString())
        divider('=')
        columns(
            Column("NET BALANCE", weight = 1, align = Align.LEFT, bold = true),
            Column(money(net), weight = 1, align = Align.RIGHT, bold = true),
        )
        feed(1)
        center("Official Terminal Report")
        center(dateTime(Date()))
        feed(2)
        if (settings.beepOnComplete) beep(1)
        cut()
    }
}
