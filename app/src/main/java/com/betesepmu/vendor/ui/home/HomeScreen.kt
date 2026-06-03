package com.betesepmu.vendor.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.core.ConnectionState
import com.betesepmu.vendor.ui.MainViewModel
import com.betesepmu.vendor.ui.components.BrandLogo
import com.betesepmu.vendor.ui.components.InfoRow
import com.betesepmu.vendor.ui.components.SectionCard
import com.betesepmu.vendor.ui.components.StatusPill
import com.betesepmu.vendor.ui.components.ToggleRow

@Composable
fun HomeScreen(vm: MainViewModel, onOpenSamples: () -> Unit, onOpenQueue: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val transport by vm.activeTransport.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val previews by vm.previews.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Brand header
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("BetEse Vendor", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Text("On-device thermal print broker", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }

        // Printer status
        SectionCard("Printer", icon = Icons.Filled.Print) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(transport.name, fontWeight = FontWeight.Medium)
                val (txt, bg, fg) = when {
                    status.paperOut -> Triple("Out of paper", cs.errorContainer, cs.onErrorContainer)
                    status.coverOpen -> Triple("Cover open", cs.errorContainer, cs.onErrorContainer)
                    status.ready -> Triple("Ready", cs.primaryContainer, cs.onPrimaryContainer)
                    status.connection == ConnectionState.UNAVAILABLE -> Triple(status.message, cs.secondaryContainer, cs.onSecondaryContainer)
                    else -> Triple(status.message, cs.surfaceVariant, cs.onSurfaceVariant)
                }
                StatusPill(txt, bg, fg)
            }
            InfoRow("Paper width", settings.paperWidth.label)
            InfoRow("Code page", settings.codePage.displayName)
            InfoRow("Connection", status.message)
            OutlinedButton(onClick = { vm.reconnect() }) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reconnect / refresh")
            }
        }

        // Quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.samplesQuickTest() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Test print")
            }
            OutlinedButton(onClick = onOpenSamples, modifier = Modifier.weight(1f)) {
                Text("All samples"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
            }
        }

        // Integration surfaces
        SectionCard("Integration surfaces", icon = Icons.Filled.Router) {
            ToggleRow(
                title = "Local HTTP print API",
                subtitle = if (settings.httpServerEnabled) "Listening on port ${settings.httpPort} — POST /print" else "Let on-device web/PWA apps print",
                checked = settings.httpServerEnabled,
                onCheckedChange = { on -> vm.update { it.copy(httpServerEnabled = on) } },
            )
            InfoRow("System Print Service", "Registered")
            InfoRow("Share / betese:// URI", "Enabled")
            InfoRow("AIDL broker", "com.betesepmu.vendor.PRINT_BROKER")
        }

        // Recent previews
        if (previews.isNotEmpty()) {
            SectionCard("Recent previews") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    previews.take(8).forEach { item ->
                        Image(
                            bitmap = item.bitmap.asImageBitmap(),
                            contentDescription = item.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 84.dp, height = 120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Color.White)
                                .clickable { vm.showPreviewItem(item) },
                        )
                    }
                }
            }
        }

        // Queue summary
        SectionCard("Queue") {
            val active = jobs.count { it.isActive }
            InfoRow("Active jobs", active.toString())
            InfoRow("Total in history", jobs.size.toString())
            OutlinedButton(onClick = onOpenQueue) { Text("Open queue") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
