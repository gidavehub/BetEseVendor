package com.betesepmu.vendor.ui.settings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.core.DitherMode
import com.betesepmu.vendor.core.PaperWidth
import com.betesepmu.vendor.core.TransportPreference
import com.betesepmu.vendor.escpos.CodePage
import com.betesepmu.vendor.printer.bluetooth.BondedPrinter
import com.betesepmu.vendor.ui.MainViewModel
import com.betesepmu.vendor.ui.components.DropdownSetting
import com.betesepmu.vendor.ui.components.InfoRow
import com.betesepmu.vendor.ui.components.SectionCard
import com.betesepmu.vendor.ui.components.Stepper
import com.betesepmu.vendor.ui.components.ToggleRow
import com.betesepmu.vendor.vendor.VendorViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, vendorVm: VendorViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val user by vendorVm.currentUser.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        SectionCard("Account", icon = Icons.Filled.AccountCircle) {
            InfoRow("Signed in as", user?.name ?: "—")
            InfoRow("Role", user?.role ?: "—")
            user?.phone?.takeIf { it.isNotBlank() }?.let { InfoRow("Phone", it) }
            OutlinedButton(onClick = { vendorVm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Log out")
            }
        }

        SectionCard("Print Settings", icon = Icons.Filled.Print) {
            DropdownSetting("Paper width", PaperWidth.entries, s.paperWidth, { it.label }) { v -> vm.update { it.copy(paperWidth = v) } }
            DropdownSetting("Transport", TransportPreference.entries, s.transportPreference, { it.label }) { v -> vm.update { it.copy(transportPreference = v) }; vm.reconnect() }
            DropdownSetting("Code page", CodePage.entries, s.codePage, { it.displayName }) { v -> vm.update { it.copy(codePage = v) } }
            DropdownSetting("Dithering", DitherMode.entries, s.ditherMode, { it.label }) { v -> vm.update { it.copy(ditherMode = v) } }
            Text("Print density: ${s.density}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = s.density.toFloat(), onValueChange = { vm.update { st -> st.copy(density = it.toInt()) } }, valueRange = 0f..15f, steps = 14)
            Stepper("Copies", s.copies, 1..9) { v -> vm.update { it.copy(copies = v) } }
        }

        // Bluetooth picker — only shown when relevant (BT chosen, or AUTO so the user can
        // pre-configure a BT fallback). Same flow RawBT uses: list paired devices, pick one.
        if (s.transportPreference == TransportPreference.BLUETOOTH || s.transportPreference == TransportPreference.AUTO) {
            BluetoothSection(vm)
        }

        SectionCard("Behaviour", icon = Icons.Filled.Build) {
            ToggleRow("Auto-cut after job", "Feed and cut at end of receipt", s.autoCut) { on -> vm.update { it.copy(autoCut = on) } }
            Text("Cut feed: ${s.cutFeedDots} dots", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = s.cutFeedDots.toFloat(), onValueChange = { vm.update { st -> st.copy(cutFeedDots = it.toInt()) } }, valueRange = 0f..160f)
            ToggleRow("Open cash drawer", "Pulse the kick connector on print", s.openCashDrawer) { on -> vm.update { it.copy(openCashDrawer = on) } }
            ToggleRow("Beep on complete", "Buzzer at end of job", s.beepOnComplete) { on -> vm.update { it.copy(beepOnComplete = on) } }
        }

        SectionCard("Local HTTP API", icon = Icons.Filled.Router) {
            ToggleRow("Enable HTTP print server", "POST /print from on-device web apps", s.httpServerEnabled) { on -> vm.update { it.copy(httpServerEnabled = on) } }
            OutlinedTextField(
                value = s.httpPort.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { p -> if (p in 1..65535) vm.update { st -> st.copy(httpPort = p) } } },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("Business identity", icon = Icons.Filled.Store) {
            TextSetting("Business name", s.businessName) { v -> vm.update { it.copy(businessName = v) } }
            TextSetting("Address", s.businessAddress) { v -> vm.update { it.copy(businessAddress = v) } }
            TextSetting("Phone", s.businessPhone) { v -> vm.update { it.copy(businessPhone = v) } }
            TextSetting("Currency symbol", s.currencySymbol) { v -> vm.update { it.copy(currencySymbol = v) } }
            TextSetting("Receipt footer", s.footerText) { v -> vm.update { it.copy(footerText = v) } }
        }

        SectionCard("About") {
            InfoRow("App", "BetEse Vendor")
            InfoRow("Package", "com.betesepmu.vendor")
            InfoRow("Role", "On-device thermal print broker")
        }
    }
}

@Composable
private fun BluetoothSection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // bump to force the paired-devices list to re-read (after grant / refresh button).
    var refreshTick by remember { mutableIntStateOf(0) }
    var permGranted by remember { mutableStateOf(!vm.bluetoothPermissionMissing()) }
    val adapterAvailable = remember { vm.bluetoothAdapterAvailable() }
    val adapterEnabled = vm.bluetoothAdapterEnabled()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permGranted = result.values.all { it } && !vm.bluetoothPermissionMissing()
        refreshTick++
    }
    LaunchedEffect(Unit) { permGranted = !vm.bluetoothPermissionMissing() }

    val paired: List<BondedPrinter> = remember(refreshTick, permGranted, s.bluetoothDeviceAddress) {
        if (permGranted) vm.pairedBluetoothPrinters() else emptyList()
    }

    SectionCard("Bluetooth printer", icon = Icons.Filled.Bluetooth) {
        if (!adapterAvailable) {
            Text(
                "This device has no Bluetooth adapter.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // Selected device summary
        val selectedLabel = when {
            s.bluetoothDeviceName.isNotBlank() -> s.bluetoothDeviceName
            s.bluetoothDeviceAddress.isNotBlank() -> s.bluetoothDeviceAddress
            else -> "(none)"
        }
        InfoRow("Selected printer", selectedLabel)
        if (s.bluetoothDeviceAddress.isNotBlank()) InfoRow("MAC address", s.bluetoothDeviceAddress)

        if (!adapterEnabled) {
            Text(
                "Bluetooth is off. Turn it on in Android Settings, then refresh.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Text("Open Bluetooth")
                }
                OutlinedButton(onClick = { refreshTick++ }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh")
                }
            }
            return@SectionCard
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !permGranted) {
            Text(
                "Grant the Bluetooth permission so BetEse Vendor can read paired printers and open a connection.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {
                permLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                    )
                )
            }) { Text("Grant Bluetooth permission") }
            return@SectionCard
        }

        // Paired devices list
        if (paired.isEmpty()) {
            Text(
                "No paired Bluetooth printers found. Pair your printer in Android Settings, then refresh.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Text("Pair a printer")
                }
                OutlinedButton(onClick = { refreshTick++ }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh")
                }
            }
            return@SectionCard
        }

        Text("Paired devices", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        paired.forEach { d ->
            val selected = d.address == s.bluetoothDeviceAddress
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(d.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    TextButton(onClick = { vm.selectBluetoothPrinter(d) }) { Text("Select") }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh list")
            }
            if (s.bluetoothDeviceAddress.isNotBlank()) {
                OutlinedButton(onClick = { vm.clearBluetoothPrinter() }) { Text("Clear selection") }
            }
            OutlinedButton(onClick = { vm.reconnect() }) { Text("Test connect") }
        }
    }
}

@Composable
private fun TextSetting(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
