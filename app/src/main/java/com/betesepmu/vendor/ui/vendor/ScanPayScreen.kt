package com.betesepmu.vendor.ui.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.model.Ticket
import com.betesepmu.vendor.model.TicketStatus
import com.betesepmu.vendor.ui.components.InfoRow
import com.betesepmu.vendor.ui.components.SectionCard
import com.betesepmu.vendor.vendor.VendorViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ScanPayScreen(vm: VendorViewModel, onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Ticket?>(null) }
    var notFound by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank() || searching) return
        searching = true; notFound = false; result = null
        scope.launch {
            val ticket = vm.lookupTicket(query.trim())
            searching = false
            result = ticket
            notFound = ticket == null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Find Ticket") {
            Text("Enter or scan a ticket number or booking code.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Ticket # / booking code") },
                    singleLine = true,
                    enabled = !searching,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { search() }, enabled = !searching && query.isNotBlank(), modifier = Modifier.height(56.dp)) {
                    if (searching) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                    else Icon(Icons.Filled.Search, "Find")
                }
            }
            if (notFound) Text("No ticket found for \"${query.trim()}\".", color = cs.error, fontSize = 13.sp)
        }

        result?.let { ticket ->
            SectionCard("Ticket ${ticket.id}") {
                StatusLine(ticket)
                InfoRow("Placed", ticket.timestamp.toString())
                ticket.vendorName?.takeIf { it.isNotBlank() }?.let { InfoRow("Vendor", it) }
                InfoRow("Selections", ticket.selections.size.toString())
                ticket.selections.forEach { s ->
                    Text("• ${s.betType.label} — ${s.raceName}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                InfoRow("Total stake", gmd(ticket.totalCost))
                if ((ticket.winnings ?: 0.0) > 0.0) {
                    InfoRow("Winnings", gmd(ticket.winnings!!), valueColor = cs.primary)
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ticket.isPayable && (ticket.winnings ?: 0.0) > 0.0) {
                        Button(
                            onClick = {
                                actionBusy = true
                                scope.launch {
                                    vm.payout(ticket)
                                        .onSuccess { result = ticket.copy(status = TicketStatus.PAID); onMessage("Paid ${gmd(ticket.winnings ?: 0.0)}") }
                                        .onFailure { onMessage(it.message ?: "Payout failed") }
                                    actionBusy = false
                                }
                            },
                            enabled = !actionBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text("PAY OUT", fontWeight = FontWeight.Bold) }
                    }
                    OutlinedButton(onClick = { vm.reprint(ticket) }, enabled = !actionBusy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Print, null, Modifier.width(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reprint")
                    }
                }
                if (ticket.isCancelable) {
                    OutlinedButton(
                        onClick = {
                            actionBusy = true
                            scope.launch {
                                vm.cancel(ticket)
                                    .onSuccess { result = ticket.copy(status = TicketStatus.CANCELED); onMessage("Ticket canceled") }
                                    .onFailure { onMessage(it.message ?: "Cancel failed") }
                                actionBusy = false
                            }
                        },
                        enabled = !actionBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel ticket") }
                }
            }
        }

        BookingCard(vm, onMessage)
        RecentTicketsCard(vm)
    }
}

@Composable
private fun BookingCard(vm: VendorViewModel, onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    SectionCard("Pay a Booking") {
        Text("Enter a booking code to pay for and print a booked ticket.", fontSize = 12.sp, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Booking code") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (code.isBlank()) return@Button
                    busy = true
                    scope.launch {
                        vm.payForBooking(code.trim())
                            .onSuccess { code = "" }
                            .onFailure { onMessage(it.message ?: "Booking failed") }
                        busy = false
                    }
                },
                enabled = !busy && code.isNotBlank(),
                modifier = Modifier.height(56.dp),
            ) { Text(if (busy) "…" else "Pay") }
        }
    }
}

@Composable
private fun RecentTicketsCard(vm: VendorViewModel) {
    val cs = MaterialTheme.colorScheme
    val recent by vm.recent.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshRecent() }
    SectionCard("Recent Tickets") {
        if (recent.isEmpty()) {
            Text("No recent tickets.", color = cs.onSurfaceVariant)
        } else {
            recent.take(10).forEach { ticket ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${ticket.id}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${SimpleDateFormat("dd MMM HH:mm", Locale.US).format(ticket.timestamp)} · ${gmd(ticket.totalCost)} · ${ticket.status}",
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.reprint(ticket) }) { Text("Reprint") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(ticket: Ticket) {
    val cs = MaterialTheme.colorScheme
    val color = when (ticket.status) {
        TicketStatus.PAID -> cs.tertiary
        TicketStatus.WINNING -> cs.primary
        TicketStatus.LOST -> cs.error
        TicketStatus.CANCELED -> cs.onSurfaceVariant
        else -> cs.primary
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Status", color = cs.onSurfaceVariant)
        Text(ticket.status.uppercase(), fontWeight = FontWeight.Black, color = color)
    }
}
