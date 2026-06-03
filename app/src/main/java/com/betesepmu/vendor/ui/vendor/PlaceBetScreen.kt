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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.bet.PmuPricing
import com.betesepmu.vendor.model.BetSelection
import com.betesepmu.vendor.model.BetTypeOption
import com.betesepmu.vendor.model.Race
import com.betesepmu.vendor.ui.components.SectionCard
import com.betesepmu.vendor.vendor.VendorViewModel

@Composable
fun PlaceBetScreen(vm: VendorViewModel, onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val races by vm.races.collectAsStateWithLifecycle()
    val slip by vm.slip.collectAsStateWithLifecycle()
    val slipTotal by vm.slipTotal.collectAsStateWithLifecycle()
    val placing by vm.placing.collectAsStateWithLifecycle()

    val activeRaces = remember(races) { races.filter { it.endDate.time > System.currentTimeMillis() } }

    var selectedRace by remember { mutableStateOf<Race?>(null) }
    var selectedBetType by remember { mutableStateOf<BetTypeOption?>(null) }
    var selectedNumbers by remember { mutableStateOf<List<Int>>(emptyList()) }
    var xCount by remember { mutableIntStateOf(0) }

    // Default to the soonest active race.
    if (selectedRace == null && activeRaces.isNotEmpty()) selectedRace = activeRaces.first()
    // Drop a race that has since closed.
    if (selectedRace != null && activeRaces.none { it.id == selectedRace!!.id }) {
        selectedRace = activeRaces.firstOrNull()
        selectedNumbers = emptyList(); xCount = 0
    }

    fun resetSelection() { selectedNumbers = emptyList(); xCount = 0; selectedBetType = null }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1) Race
        SectionCard("1 · Select Race") {
            if (activeRaces.isEmpty()) {
                Text("No active races right now.", color = cs.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeRaces.forEach { race ->
                        FilterChip(
                            selected = selectedRace?.id == race.id,
                            onClick = { selectedRace = race; resetSelection() },
                            label = { Text(race.name) },
                        )
                    }
                }
            }
        }

        val race = selectedRace
        if (race != null) {
            // 2) Bet type + horses
            SectionCard("2 · Bet Type & Horses") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BetTypeOption.entries.forEach { bt ->
                        val enabled = bt !in race.disabledBetTypes
                        FilterChip(
                            selected = selectedBetType == bt,
                            enabled = enabled,
                            onClick = { selectedBetType = bt; selectedNumbers = emptyList(); xCount = 0 },
                            label = { Text(bt.label, fontSize = 12.sp) },
                        )
                    }
                }

                selectedBetType?.let { bt ->
                    Spacer(Modifier.height(4.dp))
                    val min = PmuPricing.minHorses(bt)
                    Text(
                        "Pick the horses (min $min). Use X for any/banker.",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                    HorseGrid(
                        horseCount = race.horseCount,
                        nonRunners = race.nonRunners,
                        selected = selectedNumbers,
                        onToggle = { n ->
                            selectedNumbers = if (n in selectedNumbers) selectedNumbers - n else selectedNumbers + n
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X (banker count)", Modifier.weight(1f))
                        IconButton(onClick = { if (xCount > 0) xCount-- }) { Icon(Icons.Filled.Remove, "Fewer X") }
                        Text("$xCount", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        IconButton(onClick = { xCount++ }) { Icon(Icons.Filled.Add, "More X") }
                    }

                    val previewCost = PmuPricing.costFor(bt, selectedNumbers.size, xCount)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Unit stake", color = cs.onSurfaceVariant)
                        Text(gmd(previewCost), fontWeight = FontWeight.Bold, color = cs.primary)
                    }

                    Button(
                        onClick = {
                            val err = vm.addSelection(race, bt, selectedNumbers.sorted(), xCount)
                            if (err != null) onMessage(err) else resetSelection()
                        },
                        enabled = (selectedNumbers.size + xCount) > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ADD TO SLIP", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // 3) Bet slip
        SectionCard("Bet Slip") {
            if (slip.isEmpty()) {
                Text("Slip is empty. Add a selection above.", color = cs.onSurfaceVariant)
            } else {
                slip.forEachIndexed { index, sel ->
                    SlipRow(
                        sel = sel,
                        onInc = { vm.setMultiplier(index, sel.multiplier + 1) },
                        onDec = { vm.setMultiplier(index, sel.multiplier - 1) },
                        onRemove = { vm.removeSelection(index) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(gmd(slipTotal), fontWeight = FontWeight.Black, fontSize = 18.sp, color = cs.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.clearSlip() }, modifier = Modifier.weight(1f), enabled = !placing) { Text("Clear") }
                    Button(
                        onClick = { vm.placeBet() },
                        enabled = !placing && slip.isNotEmpty(),
                        modifier = Modifier.weight(2f).height(50.dp),
                    ) { Text(if (placing) "PLACING…" else "PLACE BET & PRINT", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun HorseGrid(horseCount: Int, nonRunners: List<Int>, selected: List<Int>, onToggle: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..horseCount.coerceAtLeast(0)).forEach { n ->
            val scratched = n in nonRunners
            val isSel = n in selected
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        scratched -> cs.surfaceVariant
                        isSel -> cs.primary
                        else -> cs.secondaryContainer
                    },
                ),
                modifier = Modifier.size(46.dp),
                onClick = { if (!scratched) onToggle(n) },
                enabled = !scratched,
            ) {
                Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "$n",
                        fontWeight = FontWeight.Bold,
                        color = when {
                            scratched -> cs.onSurfaceVariant
                            isSel -> cs.onPrimary
                            else -> cs.onSecondaryContainer
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlipRow(sel: BetSelection, onInc: () -> Unit, onDec: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(sel.betType.label, fontWeight = FontWeight.SemiBold)
            Text(sel.raceName, fontSize = 12.sp, color = cs.onSurfaceVariant)
            val picks = buildString {
                if (sel.xCount > 0) append("X".repeat(sel.xCount)).append(" ")
                append(sel.numbers.joinToString(" "))
            }
            Text(picks, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        IconButton(onClick = onDec) { Icon(Icons.Filled.Remove, "Fewer") }
        Text("x${sel.multiplier}", fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
        IconButton(onClick = onInc) { Icon(Icons.Filled.Add, "More") }
        Text(gmd(sel.lineTotal), fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove", tint = cs.error) }
    }
}
