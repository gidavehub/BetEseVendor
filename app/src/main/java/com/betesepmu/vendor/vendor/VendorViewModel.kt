package com.betesepmu.vendor.vendor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.betesepmu.vendor.BetEseApp
import com.betesepmu.vendor.bet.PmuPricing
import com.betesepmu.vendor.document.Receipt
import com.betesepmu.vendor.model.BetSelection
import com.betesepmu.vendor.model.BetTypeOption
import com.betesepmu.vendor.model.ChatMessage
import com.betesepmu.vendor.model.ChatThread
import com.betesepmu.vendor.model.DepositLog
import com.betesepmu.vendor.model.PaymentMethod
import com.betesepmu.vendor.model.Race
import com.betesepmu.vendor.model.Ticket
import com.betesepmu.vendor.model.TicketStatus
import com.betesepmu.vendor.model.VendorUser
import com.betesepmu.vendor.model.WithdrawalRequest
import com.betesepmu.vendor.print.VendorReceipts
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.spooler.JobSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today's running totals for the vendor's shift, shown on Finance and printed on reports. */
data class ShiftSummary(
    val ticketSales: Double,
    val ticketsSold: Int,
    val paidOut: Double,
    val payoutCount: Int,
) {
    val net: Double get() = ticketSales - paidOut
}

/**
 * Bridges the auth + vendor repositories and the print engine to Compose. Holds the login
 * gate, the live race list, the in-progress bet slip, and the vendor actions (place bet,
 * payout, cancel, deposit, withdraw, reports) — each of which prints through the existing
 * spooler via [BetEseApp]'s container.
 */
class VendorViewModel(app: Application) : AndroidViewModel(app) {

    private val container = BetEseApp.container(app)
    private val auth = container.authRepository
    private val repo = container.vendorRepository
    private val receipts = VendorReceipts(app)

    val currentUser: StateFlow<VendorUser?> = auth.currentUser
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // ---- login -------------------------------------------------------------

    private val _loggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun login(identifier: String, password: String) {
        if (_loggingIn.value) return
        _loggingIn.value = true
        _loginError.value = null
        viewModelScope.launch {
            val result = auth.login(identifier, password)
            _loggingIn.value = false
            result.onSuccess {
                messages.tryEmit("Welcome, ${it.name}")
                refreshRecent()
            }.onFailure { _loginError.value = it.message ?: "Login failed." }
        }
    }

    fun clearLoginError() { _loginError.value = null }

    fun logout() {
        auth.logout()
        _slip.value = emptyList()
        _lastTicket.value = null
        _recent.value = emptyList()
    }

    // ---- races -------------------------------------------------------------

    val races: StateFlow<List<Race>> = repo.racesFlow()
        .map { list -> list.sortedBy { it.endDate.time } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun activeRaces(now: Date = Date()): List<Race> = races.value.filter { it.endDate.after(now) }

    // ---- bet slip ----------------------------------------------------------

    private val _slip = MutableStateFlow<List<BetSelection>>(emptyList())
    val slip: StateFlow<List<BetSelection>> = _slip.asStateFlow()
    val slipTotal: StateFlow<Double> = _slip
        .map { sels -> sels.sumOf { it.lineTotal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Add a selection to the slip. Returns an error message, or null on success. */
    fun addSelection(race: Race, betType: BetTypeOption, numbers: List<Int>, xCount: Int): String? {
        val min = PmuPricing.minHorses(betType)
        if (numbers.size + xCount < min) return "Select at least $min horses for ${betType.label}."
        val unitCost = PmuPricing.costFor(betType, numbers.size, xCount)
        if (unitCost <= 0.0) return "That combination is not available for ${betType.label}."
        _slip.value = _slip.value + BetSelection(
            raceId = race.id,
            raceName = race.name,
            betType = betType,
            numbers = numbers,
            xCount = xCount,
            cost = unitCost,
            multiplier = 1,
            pattern = List(xCount) { "X" } + numbers.map { it.toString() },
        )
        return null
    }

    fun removeSelection(index: Int) {
        _slip.value = _slip.value.filterIndexed { i, _ -> i != index }
    }

    fun setMultiplier(index: Int, multiplier: Int) {
        _slip.value = _slip.value.mapIndexed { i, s ->
            if (i == index) s.copy(multiplier = multiplier.coerceAtLeast(1)) else s
        }
    }

    fun clearSlip() { _slip.value = emptyList() }

    // ---- placed tickets ----------------------------------------------------

    private val _lastTicket = MutableStateFlow<Ticket?>(null)
    val lastTicket: StateFlow<Ticket?> = _lastTicket.asStateFlow()
    fun dismissLastTicket() { _lastTicket.value = null }

    private val _recent = MutableStateFlow<List<Ticket>>(emptyList())
    val recent: StateFlow<List<Ticket>> = _recent.asStateFlow()

    private val _placing = MutableStateFlow(false)
    val placing: StateFlow<Boolean> = _placing.asStateFlow()

    fun placeBet() {
        val user = currentUser.value ?: return
        val selections = _slip.value
        if (selections.isEmpty() || _placing.value) return
        _placing.value = true
        viewModelScope.launch {
            val total = selections.sumOf { it.lineTotal }
            val result = repo.placeBet(user, selections, total)
            _placing.value = false
            result.onSuccess { ticket ->
                _lastTicket.value = ticket
                _slip.value = emptyList()
                printDoc("Ticket ${ticket.id}", receipts.ticket(ticket, settings()))
                messages.tryEmit("Ticket ${ticket.id} placed")
                refreshRecent()
            }.onFailure { messages.tryEmit(it.message ?: "Could not place bet.") }
        }
    }

    fun reprint(ticket: Ticket) {
        printDoc("Reprint ${ticket.id}", receipts.ticket(ticket, settings()))
        messages.tryEmit("Reprinting ticket ${ticket.id}")
    }

    suspend fun lookupTicket(reference: String): Ticket? = repo.findTicket(reference)

    /** Pay for a booked ticket by code, then print it. */
    suspend fun payForBooking(code: String): Result<Ticket> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repo.payForBooking(code, user).onSuccess { ticket ->
            printDoc("Ticket ${ticket.id}", receipts.ticket(ticket, settings()))
            messages.tryEmit("Booking paid — ticket ${ticket.id}")
            refreshRecent()
        }
    }

    // ---- results / rapport -------------------------------------------------

    /** Races that already have an official result, most recent first. */
    val racesWithResults: StateFlow<List<Race>> = races
        .map { list -> list.filter { it.hasResult }.sortedByDescending { it.endDate.time } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun printRapport(race: Race) {
        if (race.result == null) return
        printDoc("Rapport ${race.name}", receipts.rapport(race, settings()))
        messages.tryEmit("Rapport for ${race.name} sent to printer")
    }

    // ---- chat --------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatThreads: StateFlow<List<ChatThread>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.threadsFlow(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chatMessages(threadId: String): Flow<List<ChatMessage>> = repo.messagesFlow(threadId)

    suspend fun sendChat(threadId: String?, content: String, recipients: List<String>): Result<String> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repo.sendMessage(threadId, user, content, recipients)
    }

    suspend fun payout(ticket: Ticket): Result<Unit> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repo.payoutTicket(ticket.id, ticket.winnings ?: 0.0, user).onSuccess {
            messages.tryEmit("Paid out ticket ${ticket.id}")
            refreshRecent()
        }
    }

    suspend fun cancel(ticket: Ticket): Result<Unit> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repo.cancelTicket(ticket.id, user).onSuccess {
            messages.tryEmit("Canceled ticket ${ticket.id}")
            refreshRecent()
        }
    }

    // ---- finance -----------------------------------------------------------

    suspend fun findCustomer(query: String): VendorUser? = repo.findCustomer(query)

    suspend fun deposit(customer: VendorUser, amount: Double, method: PaymentMethod): Result<DepositLog> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repo.recordDeposit(customer, amount, method, user).onSuccess { log ->
            printDoc("Deposit ${log.id}", receipts.deposit(log, customer.walletBalance + log.amount, settings()))
            messages.tryEmit("Deposited ${log.amount} for ${customer.name}")
        }
    }

    suspend fun processWithdrawal(code: String): Result<WithdrawalRequest> {
        val user = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in."))
        return repo.processWithdrawal(code, user).onSuccess { request ->
            printDoc("Withdrawal ${request.code}", receipts.withdrawal(request, user.name, settings()))
            messages.tryEmit("Paid out ${request.amount} (${request.code})")
        }
    }

    fun shiftSummary(now: Date = Date()): ShiftSummary {
        val today = todaysTickets(now)
        val countable = today.filter { it.status != TicketStatus.CANCELED && it.status != TicketStatus.BOOKED }
        val paid = today.filter { it.status == TicketStatus.PAID }
        return ShiftSummary(
            ticketSales = countable.sumOf { it.totalCost },
            ticketsSold = countable.size,
            paidOut = paid.sumOf { it.winnings ?: 0.0 },
            payoutCount = paid.size,
        )
    }

    fun printSalesReport(endOfSale: Boolean) {
        val user = currentUser.value ?: return
        val now = Date()
        val s = shiftSummary(now)
        val title = if (endOfSale) "End of Sale Report" else "Daily Sales Report"
        printDoc(
            title,
            receipts.salesReport(title, user.name, now, s.ticketSales, s.ticketsSold, s.paidOut, s.payoutCount, s.net, settings()),
        )
        messages.tryEmit("$title sent to printer")
    }

    fun refreshRecent() {
        val user = currentUser.value ?: return
        viewModelScope.launch { _recent.value = repo.recentVendorTickets(user.id) }
    }

    // ---- helpers -----------------------------------------------------------

    private fun todaysTickets(now: Date): List<Ticket> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(now)
        return _recent.value.filter { fmt.format(it.timestamp) == today }
    }

    private fun settings() = container.settingsRepository.current

    private fun printDoc(title: String, doc: Receipt) {
        container.enqueue(title, JobSource.MANUAL, PrintPayload.ReceiptDoc(doc))
    }
}
