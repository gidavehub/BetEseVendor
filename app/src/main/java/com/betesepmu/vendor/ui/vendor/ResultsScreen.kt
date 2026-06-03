package com.betesepmu.vendor.ui.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.bet.Rapport
import com.betesepmu.vendor.model.Race
import com.betesepmu.vendor.ui.components.SectionCard
import com.betesepmu.vendor.vendor.VendorViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ResultsScreen(vm: VendorViewModel) {
    val cs = MaterialTheme.colorScheme
    val races by vm.racesWithResults.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (races.isEmpty()) {
            SectionCard("Results") {
                Text("No race results available yet.", color = cs.onSurfaceVariant)
            }
        }
        races.forEach { race -> ResultCard(race) { vm.printRapport(race) } }
    }
}

@Composable
private fun ResultCard(race: Race, onPrint: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val result = race.result ?: return
    val date = SimpleDateFormat("dd MMM yyyy", Locale.US).format(race.endDate)

    SectionCard(race.name) {
        Text(date, fontSize = 12.sp, color = cs.onSurfaceVariant)
        if (race.nonRunners.isNotEmpty()) {
            Text("Non-runners: ${race.nonRunners.joinToString(", ")}", fontSize = 12.sp, color = cs.error)
        }

        Rapport.sections(result).forEachIndexed { index, section ->
            val (title, numbers, payouts) = section
            if (index > 0) HorizontalDivider()
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)

            // Winning numbers
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    Rapport.formatNumbers(numbers),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = cs.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }

            // Payout groups
            Rapport.visibleGroups(payouts).forEach { (group, rows) ->
                Text(group.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.primary)
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label, fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Text(gmd(Rapport.value(payouts, r.key) ?: 0.0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.size(2.dp))
        OutlinedButton(onClick = onPrint, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Print Rapport")
        }
    }
}
