package com.betesepmu.vendor.data

import com.betesepmu.vendor.model.BetSelection
import com.betesepmu.vendor.model.BetTypeOption
import com.betesepmu.vendor.model.ChatMessage
import com.betesepmu.vendor.model.ChatThread
import com.betesepmu.vendor.model.Race
import com.betesepmu.vendor.model.RaceResult
import com.betesepmu.vendor.model.Ticket
import com.betesepmu.vendor.model.TicketStatus
import com.betesepmu.vendor.model.VendorUser
import com.betesepmu.vendor.model.WithdrawalRequest
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Firestore <-> model mapping. betesepmu stores documents with **snake_case** field names and
 * dates as ISO-8601 strings (via `Date.toISOString()`), though some may be Firestore
 * [Timestamp]s. All coercion is defensive so a malformed field never crashes the terminal.
 */

// ---- primitive coercion ----------------------------------------------------

internal fun anyToDate(v: Any?): Date? = when (v) {
    null -> null
    is Timestamp -> v.toDate()
    is Date -> v
    is Number -> Date(v.toLong())
    is String -> parseIsoDate(v)
    else -> null
}

private val isoFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

internal fun parseIsoDate(s: String): Date? {
    if (s.isBlank()) return null
    runCatching { isoFormat.get()!!.parse(s) }.getOrNull()?.let { return it }
    runCatching { Date(s.toLong()) }.getOrNull()?.let { return it }
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)
    }.getOrNull()
}

/** ISO-8601 UTC string, matching JS `Date.toISOString()` — the format betesepmu writes. */
internal fun toIso(date: Date): String = isoFormat.get()!!.format(date)

/** Round to 2 dp the way betesepmu does (`Number(x.toFixed(2))`) to keep money exact. */
internal fun round2(x: Double): Double = Math.round(x * 100.0) / 100.0

internal fun anyToDouble(v: Any?): Double = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

internal fun anyToInt(v: Any?): Int = when (v) {
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: 0
    else -> 0
}

internal fun anyToBool(v: Any?): Boolean = when (v) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v.equals("true", ignoreCase = true) || v == "1"
    else -> false
}

internal fun anyToIntList(v: Any?): List<Int> =
    (v as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: (it as? String)?.toIntOrNull() } ?: emptyList()

internal fun anyToStringList(v: Any?): List<String> =
    (v as? List<*>)?.map { it.toString() } ?: emptyList()

/** Safe string read — never throws even if the stored value isn't a String. */
internal fun DocumentSnapshot.str(field: String): String? = get(field)?.toString()

// ---- selections ------------------------------------------------------------

internal fun selectionFromMap(m: Map<*, *>): BetSelection? {
    val betType = BetTypeOption.fromLabel(m["betType"] as? String) ?: return null
    return BetSelection(
        raceId = m["raceId"] as? String ?: "",
        raceName = m["raceName"] as? String ?: "",
        betType = betType,
        numbers = anyToIntList(m["numbers"]),
        xCount = anyToInt(m["xCount"]),
        cost = anyToDouble(m["cost"]),
        multiplier = anyToInt(m["multiplier"]).coerceAtLeast(1),
        pattern = anyToStringList(m["pattern"]),
    )
}

internal fun BetSelection.toMap(): Map<String, Any?> = mapOf(
    "raceId" to raceId,
    "raceName" to raceName,
    "betType" to betType.label,
    "numbers" to numbers,
    "xCount" to xCount,
    "cost" to cost,
    "multiplier" to multiplier,
    "pattern" to pattern,
)

// ---- documents -------------------------------------------------------------

internal fun DocumentSnapshot.toVendorUser(): VendorUser? {
    val name = str("name") ?: return null
    return VendorUser(
        id = id,
        name = name,
        role = str("role") ?: "Vendor",
        isLocked = anyToBool(get("is_locked")),
        phone = str("phone"),
        walletBalance = anyToDouble(get("wallet_balance")),
        bonusBalance = anyToDouble(get("bonus_balance")),
    )
}

internal fun DocumentSnapshot.toRace(): Race? {
    val name = str("name") ?: return null
    return Race(
        id = id,
        name = name,
        venue = str("venue"),
        startDate = anyToDate(get("start_date")) ?: Date(),
        endDate = anyToDate(get("end_date")) ?: Date(),
        horseCount = anyToInt(get("horse_count")),
        nonRunners = anyToIntList(get("non_runners")),
        result = (get("result") as? Map<*, *>)?.let(::raceResultFromMap),
        disabledBetTypes = anyToStringList(get("disabled_bet_types")).mapNotNull { BetTypeOption.fromLabel(it) },
    )
}

/** The `result` object inside a race doc is stored camelCase (the JS `RaceResult`). */
internal fun raceResultFromMap(m: Map<*, *>): RaceResult? {
    val winning = anyToIntList(m["winningNumbers"])
    if (winning.isEmpty()) return null
    return RaceResult(
        winningNumbers = winning,
        payouts = payoutsFromAny(m["payouts"]),
        bracketWinningNumbers = (m["bracketWinningNumbers"])?.let { anyToIntList(it) }?.ifEmpty { null },
        bracketPayouts = (m["bracketPayouts"] as? Map<*, *>)?.let { payoutsFromAny(it) },
        bracket2WinningNumbers = (m["bracket2WinningNumbers"])?.let { anyToIntList(it) }?.ifEmpty { null },
        bracket2Payouts = (m["bracket2Payouts"] as? Map<*, *>)?.let { payoutsFromAny(it) },
    )
}

/** Coerce a payouts map (camelCase key -> dividend) keeping only positive values. */
private fun payoutsFromAny(v: Any?): Map<String, Double> {
    val map = v as? Map<*, *> ?: return emptyMap()
    val out = LinkedHashMap<String, Double>()
    for ((k, raw) in map) {
        val key = k?.toString() ?: continue
        val value = anyToDouble(raw)
        if (value > 0.0) out[key] = value
    }
    return out
}

internal fun DocumentSnapshot.toChatThread(): ChatThread? {
    val participants = anyToStringList(get("participant_ids"))
    if (participants.isEmpty()) return null
    return ChatThread(
        id = id,
        participantIds = participants,
        name = str("name"),
        isBroadcast = anyToBool(get("is_broadcast")),
        lastMessageTimestamp = anyToDate(get("last_message_timestamp")),
    )
}

internal fun DocumentSnapshot.toChatMessage(): ChatMessage? {
    val threadId = str("thread_id") ?: return null
    return ChatMessage(
        id = id,
        threadId = threadId,
        senderId = str("sender_id") ?: "",
        senderName = str("sender_name") ?: "",
        content = str("content") ?: "",
        timestamp = anyToDate(get("timestamp")) ?: Date(),
        readByIds = anyToStringList(get("read_by_ids")),
    )
}

internal fun DocumentSnapshot.toTicket(): Ticket {
    val selections = (get("selections") as? List<*>)
        ?.mapNotNull { (it as? Map<*, *>)?.let(::selectionFromMap) }
        ?: emptyList()
    return Ticket(
        id = id,
        timestamp = anyToDate(get("timestamp")) ?: Date(),
        vendorId = str("vendor_id") ?: "",
        vendorName = str("vendor_name"),
        status = str("status") ?: TicketStatus.ACTIVE,
        customerId = str("customer_id"),
        bookingCode = str("booking_code"),
        selections = selections,
        totalCost = anyToDouble(get("total_cost")),
        winnings = get("winnings")?.let { anyToDouble(it) },
        transactionChannel = str("transaction_channel"),
    )
}

internal fun DocumentSnapshot.toWithdrawalRequest(): WithdrawalRequest? {
    val code = str("code") ?: return null
    return WithdrawalRequest(
        id = id,
        userId = str("user_id") ?: "",
        userName = str("user_name") ?: "",
        amount = anyToDouble(get("amount")),
        status = str("status") ?: "Pending",
        code = code,
        requestedAt = anyToDate(get("requested_at")) ?: Date(),
        payoutMethod = str("payout_method"),
        recipientPhone = str("recipient_phone"),
    )
}
