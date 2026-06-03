package com.betesepmu.vendor.ui.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.ui.components.BrandLogo
import com.betesepmu.vendor.vendor.VendorViewModel

private enum class VendorView { DASHBOARD, PLACE_BET, SCAN_PAY, FINANCE, RESULTS, CHAT }

@Composable
fun VendorDashboardScreen(vm: VendorViewModel) {
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val lastTicket by vm.lastTicket.collectAsStateWithLifecycle()
    val onMessage: (String) -> Unit = { vm.messages.tryEmit(it) }

    var view by remember { mutableStateOf(VendorView.DASHBOARD) }

    // "Ticket placed" confirmation after a successful bet.
    lastTicket?.let { ticket ->
        AlertDialog(
            onDismissRequest = { vm.dismissLastTicket() },
            title = { Text("Ticket Placed") },
            text = { Text("Ticket #${ticket.id}\nTotal ${gmd(ticket.totalCost)}\n\nThe ticket has been sent to the printer.") },
            confirmButton = { TextButton(onClick = { vm.dismissLastTicket() }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { vm.reprint(ticket) }) { Text("Reprint") } },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Hello, ${user?.name ?: "Vendor"}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(user?.role ?: "Vendor", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (view != VendorView.DASHBOARD) {
                OutlinedButton(onClick = { view = VendorView.DASHBOARD }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Menu")
                }
            }
        }

        when (view) {
            VendorView.DASHBOARD -> DashboardHome(vm = vm, onNavigate = { view = it })
            VendorView.PLACE_BET -> PlaceBetScreen(vm, onMessage)
            VendorView.SCAN_PAY -> ScanPayScreen(vm, onMessage)
            VendorView.FINANCE -> FinanceScreen(vm, onMessage)
            VendorView.RESULTS -> ResultsScreen(vm)
            VendorView.CHAT -> ChatScreen(vm, onMessage)
        }
    }
}

@Composable
private fun DashboardHome(
    vm: VendorViewModel,
    onNavigate: (VendorView) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val recent by vm.recent.collectAsStateWithLifecycle()
    val lastTicket by vm.lastTicket.collectAsStateWithLifecycle()
    val summary = remember(recent) { vm.shiftSummary() }
    val reference = lastTicket ?: recent.firstOrNull()

    // Top cards
    reference?.let { ticket ->
        Card(
            colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LAST TICKET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer)
                    Text("#${ticket.id}", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = { vm.reprint(ticket) }) {
                    Icon(Icons.Filled.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reprint")
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SHIFT TOTAL SALES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                Text(gmd(summary.ticketSales), fontSize = 22.sp, fontWeight = FontWeight.Black, color = cs.onPrimaryContainer)
                Text("${summary.ticketsSold} ticket(s) today", fontSize = 11.sp, color = cs.onPrimaryContainer)
            }
            OutlinedButton(onClick = { vm.printSalesReport(endOfSale = false) }) {
                Icon(Icons.Filled.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Print Sales")
            }
        }
    }

    // Menu grid
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        MenuButton("Place Bet", "New ticket", Icons.Filled.Casino, Modifier.weight(1f)) { onNavigate(VendorView.PLACE_BET) }
        MenuButton("Scan / Pay", "Payout", Icons.Filled.Payments, Modifier.weight(1f)) { onNavigate(VendorView.SCAN_PAY) }
        MenuButton("Finance", "Deposit / withdraw", Icons.Filled.AccountBalanceWallet, Modifier.weight(1f)) { onNavigate(VendorView.FINANCE) }
        MenuButton("Rapport", "Print results", Icons.Filled.Print, Modifier.weight(1f)) { onNavigate(VendorView.RESULTS) }
        MenuButton("Results", "Race results", Icons.Filled.EmojiEvents, Modifier.weight(1f)) { onNavigate(VendorView.RESULTS) }
        MenuButton("Chat", "Support", Icons.AutoMirrored.Filled.Chat, Modifier.weight(1f)) { onNavigate(VendorView.CHAT) }
    }
}

@Composable
private fun MenuButton(label: String, subtext: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.height(110.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtext, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}

