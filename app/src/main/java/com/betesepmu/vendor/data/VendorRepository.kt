package com.betesepmu.vendor.data

import com.betesepmu.vendor.model.BetSelection
import com.betesepmu.vendor.model.ChatMessage
import com.betesepmu.vendor.model.ChatThread
import com.betesepmu.vendor.model.DepositLog
import com.betesepmu.vendor.model.PaymentMethod
import com.betesepmu.vendor.model.Race
import com.betesepmu.vendor.model.Ticket
import com.betesepmu.vendor.model.TicketStatus
import com.betesepmu.vendor.model.VendorUser
import com.betesepmu.vendor.model.WithdrawalRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.random.Random

/**
 * All vendor-side Firestore reads/writes, mirroring the relevant functions in
 * `betesepmu/firebaseClient.ts` so documents stay byte-compatible with the web dashboards.
 * Money writes go through Firestore transactions exactly like the web app.
 */
class VendorRepository(private val firestore: FirebaseFirestore) {

    // ---- races -------------------------------------------------------------

    /** Live stream of all races (the UI filters to the active ones it cares about). */
    fun racesFlow(): Flow<List<Race>> = callbackFlow {
        val registration = firestore.collection(RACES).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull { it.toRace() } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Races whose betting window is still open, soonest first. */
    suspend fun activeRaces(now: Date = Date()): List<Race> = runCatching {
        firestore.collection(RACES).get().await()
            .documents.mapNotNull { it.toRace() }
            .filter { it.endDate.after(now) }
            .sortedBy { it.endDate.time }
    }.getOrDefault(emptyList())

    // ---- tickets -----------------------------------------------------------

    /**
     * Write a new vendor ticket. Mirrors `dbPlaceBet` for the Terminal channel: id is an
     * 8-digit number, vendor_id is the staff id, no wallet charge (cash taken at the counter).
     */
    suspend fun placeBet(vendor: VendorUser, selections: List<BetSelection>, totalCost: Double): Result<Ticket> {
        if (selections.isEmpty()) return Result.failure(Exception("The bet slip is empty."))
        val id = (10_000_000 + Random.nextInt(90_000_000)).toString()
        val now = Date()
        val ticket = Ticket(
            id = id,
            timestamp = now,
            vendorId = vendor.id,
            vendorName = vendor.name,
            status = TicketStatus.ACTIVE,
            selections = selections,
            totalCost = round2(totalCost),
            transactionChannel = "Terminal",
        )
        val payload = mapOf(
            "id" to id,
            "timestamp" to toIso(now),
            "vendor_id" to vendor.id,
            "vendor_name" to vendor.name,
            "transaction_channel" to "Terminal",
            "customer_id" to null,
            "status" to TicketStatus.ACTIVE,
            "booking_code" to null,
            "selections" to selections.map { it.toMap() },
            "total_cost" to ticket.totalCost,
            "winnings" to null,
            "winnings_breakdown" to null,
            "paid_at" to null,
            "paid_by_id" to null,
            "paid_by_name" to null,
            "canceled_at" to null,
            "canceled_by_id" to null,
            "canceled_by_name" to null,
        )
        return runCatching {
            firestore.collection(TICKETS).document(id).set(payload).await()
            ticket
        }
    }

    /** Find a ticket by document id, falling back to its booking code. */
    suspend fun findTicket(reference: String): Ticket? {
        val ref = reference.trim()
        if (ref.isEmpty()) return null
        return runCatching {
            val byId = firestore.collection(TICKETS).document(ref).get().await()
            if (byId.exists()) return@runCatching byId.toTicket()
            firestore.collection(TICKETS)
                .whereEqualTo("booking_code", ref.uppercase())
                .limit(1).get().await()
                .documents.firstOrNull()?.toTicket()
        }.getOrNull()
    }

    /** Cancel an Active/Booked ticket. Mirrors `dbCancelTicket`'s status guard. */
    suspend fun cancelTicket(ticketId: String, vendor: VendorUser): Result<Unit> {
        val ref = firestore.collection(TICKETS).document(ticketId)
        return runCatching {
            firestore.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw Exception("Ticket not found.")
                val status = snap.str("status") ?: ""
                if (status != TicketStatus.ACTIVE && status != TicketStatus.BOOKED) {
                    throw Exception("Ticket cannot be canceled while status is $status.")
                }
                tx.update(
                    ref, mapOf(
                        "status" to TicketStatus.CANCELED,
                        "canceled_at" to toIso(Date()),
                        "canceled_by_id" to vendor.id,
                        "canceled_by_name" to vendor.name,
                    )
                )
                null
            }.await()
            Unit
        }
    }

    /** Pay out a winning/active ticket. Mirrors `dbPayoutTicket`. */
    suspend fun payoutTicket(ticketId: String, amount: Double, vendor: VendorUser): Result<Unit> {
        val ref = firestore.collection(TICKETS).document(ticketId)
        return runCatching {
            firestore.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) throw Exception("Ticket not found.")
                when (snap.str("status") ?: "") {
                    TicketStatus.PAID -> throw Exception("Ticket already paid.")
                    TicketStatus.ACTIVE, TicketStatus.WINNING -> Unit
                    else -> throw Exception("Cannot pay ticket with status ${snap.str("status")}.")
                }
                tx.update(
                    ref, mapOf(
                        "status" to TicketStatus.PAID,
                        "winnings" to round2(amount),
                        "paid_at" to toIso(Date()),
                        "paid_by_id" to vendor.id,
                        "paid_by_name" to vendor.name,
                    )
                )
                null
            }.await()
            Unit
        }
    }

    /** Recent tickets for this vendor, newest first (sorted client-side to avoid an index). */
    suspend fun recentVendorTickets(vendorId: String, limit: Int = 50): List<Ticket> = runCatching {
        firestore.collection(TICKETS)
            .whereEqualTo("vendor_id", vendorId)
            .get().await()
            .documents.map { it.toTicket() }
            .sortedByDescending { it.timestamp.time }
            .take(limit)
    }.getOrDefault(emptyList())

    /**
     * Pay for a previously-booked ticket by its booking code: flip Booked → Active and attach
     * this vendor. Mirrors `dbPayForBooking`. Returns the now-active ticket.
     */
    suspend fun payForBooking(bookingCode: String, vendor: VendorUser): Result<Ticket> {
        val code = bookingCode.trim()
        if (code.isEmpty()) return Result.failure(Exception("Enter a booking code."))
        val match = runCatching {
            firestore.collection(TICKETS)
                .whereEqualTo("booking_code", code)
                .whereEqualTo("status", TicketStatus.BOOKED)
                .limit(1).get().await()
                .documents.firstOrNull()
        }.getOrNull() ?: return Result.failure(Exception("No booked ticket found for \"$code\"."))

        val ref = match.reference
        return runCatching {
            firestore.runTransaction { tx ->
                val snap = tx.get(ref)
                if ((snap.str("status") ?: "") != TicketStatus.BOOKED) throw Exception("This booking has already been paid.")
                tx.update(
                    ref, mapOf(
                        "status" to TicketStatus.ACTIVE,
                        "vendor_id" to vendor.id,
                        "vendor_name" to vendor.name,
                        "paid_at" to toIso(java.util.Date()),
                    )
                )
                snap.toTicket()
            }.await().copy(status = TicketStatus.ACTIVE, vendorId = vendor.id, vendorName = vendor.name)
        }
    }

    // ---- chat --------------------------------------------------------------

    /** Threads the vendor (or "ALL_VENDORS" broadcasts) take part in, newest activity first. */
    fun threadsFlow(vendorId: String): Flow<List<ChatThread>> = callbackFlow {
        val registration = firestore.collection(CHAT_THREADS)
            .whereArrayContainsAny("participant_ids", listOf(vendorId, "ALL_VENDORS"))
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val threads = snap?.documents?.mapNotNull { it.toChatThread() }
                    ?.sortedByDescending { it.lastMessageTimestamp?.time ?: 0L }
                    ?: emptyList()
                trySend(threads)
            }
        awaitClose { registration.remove() }
    }

    /** Messages in a thread, oldest first (sorted client-side to avoid a composite index). */
    fun messagesFlow(threadId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = firestore.collection(CHAT_MESSAGES)
            .whereEqualTo("thread_id", threadId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val msgs = snap?.documents?.mapNotNull { it.toChatMessage() }
                    ?.sortedBy { it.timestamp.time }
                    ?: emptyList()
                trySend(msgs)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Send a chat message. When [threadId] is null a new thread is created with [recipients]
     * (mirrors `dbSendChatMessage`). Returns the resolved thread id.
     */
    suspend fun sendMessage(
        threadId: String?,
        sender: VendorUser,
        content: String,
        recipients: List<String>,
    ): Result<String> {
        val text = content.trim()
        if (text.isEmpty()) return Result.failure(Exception("Message is empty."))
        return runCatching {
            val nowIso = toIso(java.util.Date())
            val resolvedThreadId = threadId ?: run {
                val normalized = recipients.ifEmpty { listOf("BACK_OFFICE") }
                val isBroadcast = normalized.contains("ALL_VENDORS")
                val participants = (listOf(sender.id) + normalized).distinct()
                val newId = "th-${System.currentTimeMillis()}-${Random.nextInt(9999)}"
                firestore.collection(CHAT_THREADS).document(newId).set(
                    mapOf(
                        "id" to newId,
                        "participant_ids" to participants,
                        "name" to when {
                            isBroadcast -> "Broadcast to All Vendors"
                            normalized.contains("PAYMASTER") -> "Paymaster"
                            normalized.contains("CUSTOMER_SERVICE") -> "Customer Service"
                            else -> "Back Office"
                        },
                        "is_broadcast" to isBroadcast,
                        "last_message_timestamp" to nowIso,
                    )
                ).await()
                newId
            }
            val msgId = "msg-${System.currentTimeMillis()}-${Random.nextInt(9999)}"
            firestore.collection(CHAT_MESSAGES).document(msgId).set(
                mapOf(
                    "id" to msgId,
                    "thread_id" to resolvedThreadId,
                    "sender_id" to sender.id,
                    "sender_name" to sender.name,
                    "content" to text,
                    "timestamp" to nowIso,
                    "read_by_ids" to listOf(sender.id),
                    "content_type" to "text",
                    "audio_base64" to null,
                    "audio_duration" to null,
                )
            ).await()
            firestore.collection(CHAT_THREADS).document(resolvedThreadId)
                .update("last_message_timestamp", nowIso).await()
            resolvedThreadId
        }
    }

    // ---- customers ---------------------------------------------------------

    /** Look up a Customer by phone, then by name. Used by the deposit panel. */
    suspend fun findCustomer(query: String): VendorUser? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val users = firestore.collection(USERS)
        fun firstCustomer(docs: List<com.google.firebase.firestore.DocumentSnapshot>): VendorUser? =
            docs.firstOrNull { (it.str("role") ?: "").equals("Customer", ignoreCase = true) }?.toVendorUser()
        return runCatching {
            firstCustomer(users.whereEqualTo("phone", q).limit(5).get().await().documents)
                ?: firstCustomer(users.whereEqualTo("name", q).limit(5).get().await().documents)
                ?: firstCustomer(users.whereEqualTo("name_lower", q.lowercase()).limit(5).get().await().documents)
        }.getOrNull()
    }

    // ---- finance -----------------------------------------------------------

    /**
     * Record a cash/mobile-money deposit for a customer: bump the wallet (+ total deposited,
     * + first_deposit_at) and write a `deposit_logs` entry, atomically. Mirrors
     * `dbApplyCustomerDeposit` + `dbInsertDepositLog`. Bonus awards are not applied here.
     */
    suspend fun recordDeposit(
        customer: VendorUser,
        amount: Double,
        method: PaymentMethod,
        processor: VendorUser,
    ): Result<DepositLog> {
        if (amount <= 0) return Result.failure(Exception("Enter an amount greater than zero."))
        val amt = round2(amount)
        val now = Date()
        val logId = "DEP-${now.time}-${Random.nextInt(1000, 9999)}"
        val userRef = firestore.collection(USERS).document(customer.id)
        val logRef = firestore.collection(DEPOSIT_LOGS).document(logId)
        return runCatching {
            firestore.runTransaction { tx ->
                val snap = tx.get(userRef)
                if (!snap.exists()) throw Exception("Customer not found.")
                val wallet = anyToDouble(snap.get("wallet_balance"))
                val deposited = anyToDouble(snap.get("total_deposited_amount"))
                val update = hashMapOf<String, Any>(
                    "wallet_balance" to round2(wallet + amt),
                    "total_deposited_amount" to round2(deposited + amt),
                )
                if (snap.get("first_deposit_at") == null) update["first_deposit_at"] = toIso(now)
                tx.update(userRef, update)
                tx.set(
                    logRef, mapOf(
                        "id" to logId,
                        "customer_id" to customer.id,
                        "customer_name" to customer.name,
                        "customer_phone" to customer.phone,
                        "amount" to amt,
                        "bonus_awarded" to null,
                        "bonus_adjustment" to null,
                        "processed_by_id" to processor.id,
                        "processed_by_name" to processor.name,
                        "timestamp" to toIso(now),
                        "method" to method.label,
                        "transaction_id" to null,
                        "note" to null,
                    )
                )
                null
            }.await()
            DepositLog(
                id = logId,
                customerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phone,
                amount = amt,
                processedById = processor.id,
                processedByName = processor.name,
                timestamp = now,
                method = method.label,
            )
        }
    }

    /**
     * Settle a customer withdrawal by its code: debit the wallet and mark the request
     * Completed. Mirrors `dbProcessWithdrawalRequest`. Returns the settled request.
     */
    suspend fun processWithdrawal(code: String, processor: VendorUser): Result<WithdrawalRequest> {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return Result.failure(Exception("Enter a withdrawal code."))

        val match = runCatching {
            firestore.collection(WITHDRAWALS)
                .whereEqualTo("code", trimmed)
                .whereEqualTo("status", "Pending")
                .limit(1).get().await()
                .documents.firstOrNull()
        }.getOrNull() ?: return Result.failure(Exception("No pending withdrawal found for code \"$trimmed\"."))

        val reqRef = match.reference
        return runCatching {
            firestore.runTransaction { tx ->
                val reqSnap = tx.get(reqRef)
                if ((reqSnap.str("status") ?: "") != "Pending") throw Exception("This withdrawal is no longer pending.")
                val userId = reqSnap.str("user_id") ?: throw Exception("Withdrawal has no customer attached.")
                val userRef = firestore.collection(USERS).document(userId)
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) throw Exception("Customer not found.")
                val amount = anyToDouble(reqSnap.get("amount"))
                val balance = anyToDouble(userSnap.get("wallet_balance"))
                if (balance < amount) throw Exception("Customer wallet has insufficient balance.")

                tx.update(userRef, mapOf<String, Any>("wallet_balance" to round2(balance - amount)))
                tx.update(
                    reqRef, mapOf(
                        "status" to "Completed",
                        "processed_by" to processor.id,
                        "processed_by_name" to processor.name,
                        "completed_at" to toIso(Date()),
                    )
                )
                reqSnap.toWithdrawalRequest()?.copy(status = "Completed")
            }.await() ?: throw Exception("Withdrawal record is incomplete.")
        }
    }

    private companion object {
        const val RACES = "races"
        const val TICKETS = "tickets"
        const val USERS = "users"
        const val DEPOSIT_LOGS = "deposit_logs"
        const val WITHDRAWALS = "withdrawal_requests"
        const val CHAT_THREADS = "chat_threads"
        const val CHAT_MESSAGES = "chat_messages"
    }
}
