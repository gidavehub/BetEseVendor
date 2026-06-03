package com.betesepmu.vendor.model

import java.util.Date

/**
 * Domain models for the vendor terminal. These mirror `betesepmu/types.ts` so the native
 * app reads and writes the exact same Firestore documents as the betesepmu web platform
 * (project `betesepmu-4ffc7`). Firestore field names are snake_case; the mapping lives in
 * `com.betesepmu.vendor.data.Mappers`.
 */

/** The 11 PMU bet types. [label] is the exact string stored in `selections[].betType`. */
enum class BetTypeOption(val label: String) {
    SimpleGagnant("Simple Gagnant"),
    SimplePlace("Simple Placé"),
    CoupleGagnant("Couplé Gagnant"),
    CouplePlace("Couplé Placé"),
    Tierce("Tiercé"),
    Quarte("Quarté+"),
    Quinte("Quinté+"),
    Multi4("Multi 4"),
    Multi5("Multi 5"),
    Multi6("Multi 6"),
    Multi7("Multi 7");

    companion object {
        fun fromLabel(label: String?): BetTypeOption? = entries.firstOrNull { it.label == label }
    }
}

/** A logged-in staff member (Vendor / Supervisor / Admin). Mirrors `users` collection. */
data class VendorUser(
    val id: String,
    val name: String,
    val role: String,
    val isLocked: Boolean = false,
    val phone: String? = null,
    val walletBalance: Double = 0.0,
    val bonusBalance: Double = 0.0,
) {
    val isCustomer: Boolean get() = role.equals("Customer", ignoreCase = true)
}

/** A race that bets can be placed on. Mirrors `races` collection. */
data class Race(
    val id: String,
    val name: String,
    val venue: String? = null,
    val startDate: Date,
    val endDate: Date,
    val horseCount: Int,
    val nonRunners: List<Int> = emptyList(),
    val result: RaceResult? = null,
    val disabledBetTypes: List<BetTypeOption> = emptyList(),
) {
    val hasResult: Boolean get() = result != null
}

/**
 * Official result + dividends for a race (the `result` object inside a `races` doc — stored
 * camelCase). [payouts] maps a payout key (e.g. "simpleGagnant", "quinteOrdre") to its
 * dividend; only keys with a positive value are meaningful. Bracket variants cover ties.
 */
data class RaceResult(
    val winningNumbers: List<Int>,
    val payouts: Map<String, Double>,
    val bracketWinningNumbers: List<Int>? = null,
    val bracketPayouts: Map<String, Double>? = null,
    val bracket2WinningNumbers: List<Int>? = null,
    val bracket2Payouts: Map<String, Double>? = null,
)

/** One line on a bet slip / ticket. Stored as a camelCase object inside `tickets.selections`. */
data class BetSelection(
    val raceId: String,
    val raceName: String,
    val betType: BetTypeOption,
    val numbers: List<Int>,
    val xCount: Int = 0,
    val cost: Double = 0.0,
    val multiplier: Int = 1,
    val pattern: List<String> = emptyList(),
) {
    /** Cost charged for this line = unit cost × how many times it is played. */
    val lineTotal: Double get() = cost * multiplier
}

/** Ticket statuses as stored in `tickets.status`. */
object TicketStatus {
    const val ACTIVE = "Active"
    const val WINNING = "Winning"
    const val LOST = "Lost"
    const val CANCELED = "Canceled"
    const val BOOKED = "Booked"
    const val PAID = "Paid"
}

/** A placed ticket. Mirrors `tickets` collection. */
data class Ticket(
    val id: String,
    val timestamp: Date,
    val vendorId: String,
    val vendorName: String? = null,
    val status: String = TicketStatus.ACTIVE,
    val customerId: String? = null,
    val bookingCode: String? = null,
    val selections: List<BetSelection> = emptyList(),
    val totalCost: Double = 0.0,
    val winnings: Double? = null,
    val transactionChannel: String? = null,
) {
    val isCancelable: Boolean get() = status == TicketStatus.ACTIVE || status == TicketStatus.BOOKED
    val isPayable: Boolean get() = status == TicketStatus.ACTIVE || status == TicketStatus.WINNING
}

/** Methods a vendor can record a deposit / process a withdrawal with. */
enum class PaymentMethod(val label: String) {
    Cash("Cash"),
    Wave("Wave"),
    AfriMoney("AfriMoney"),
}

/** A recorded customer deposit. Mirrors `deposit_logs` collection. */
data class DepositLog(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String? = null,
    val amount: Double,
    val processedById: String,
    val processedByName: String,
    val timestamp: Date,
    val method: String,
)

/** A customer withdrawal request a vendor pays out by code. Mirrors `withdrawal_requests`. */
data class WithdrawalRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val amount: Double,
    val status: String,
    val code: String,
    val requestedAt: Date,
    val payoutMethod: String? = null,
    val recipientPhone: String? = null,
)

/** A support/broadcast conversation the vendor takes part in. Mirrors `chat_threads`. */
data class ChatThread(
    val id: String,
    val participantIds: List<String>,
    val name: String? = null,
    val isBroadcast: Boolean = false,
    val lastMessageTimestamp: Date? = null,
)

/** A single chat message. Mirrors `chat_messages`. */
data class ChatMessage(
    val id: String,
    val threadId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Date,
    val readByIds: List<String> = emptyList(),
)

/** Who a vendor can start a new support conversation with. */
enum class ChatRecipient(val label: String, val participants: List<String>) {
    BackOffice("Back Office", listOf("BACK_OFFICE")),
    Paymaster("Paymaster (cash/payment help)", listOf("BACK_OFFICE", "PAYMASTER")),
    CustomerService("Customer Service", listOf("BACK_OFFICE", "CUSTOMER_SERVICE")),
}
