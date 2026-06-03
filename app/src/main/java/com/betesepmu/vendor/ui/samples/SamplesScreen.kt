package com.betesepmu.vendor.ui.samples

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.betesepmu.vendor.samples.SampleDoc
import com.betesepmu.vendor.ui.MainViewModel
import com.betesepmu.vendor.ui.components.sampleIcon

@Composable
fun SamplesScreen(vm: MainViewModel) {
    val samples = remember { vm.sampleList() }
    LazyColumn(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Print samples", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Preview on-screen or send to the head to test every part of the pipeline.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(samples) { doc ->
            SampleCard(doc, onPreview = { vm.previewSampleDoc(doc) }, onPrint = { vm.printSample(doc) })
        }
    }
}

@Composable
private fun SampleCard(doc: SampleDoc, onPreview: () -> Unit, onPrint: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sampleIcon(doc.iconKey), null, tint = cs.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(doc.description, fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Visibility, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Preview")
                }
                Button(onClick = onPrint, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Print")
                }
            }
        }
    }
}
