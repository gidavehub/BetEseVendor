package com.betesepmu.vendor.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.spooler.JobStatus
import com.betesepmu.vendor.spooler.PrintJob
import com.betesepmu.vendor.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueueScreen(vm: MainViewModel) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Print queue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearFinished() }) { Text("Clear finished") }
        }
        Spacer(Modifier.size(8.dp))
        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No print jobs yet", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(jobs.reversed()) { job -> JobRow(job, onRetry = { vm.retry(job.id) }, onCancel = { vm.cancel(job.id) }) }
            }
        }
    }
}

@Composable
private fun JobRow(job: PrintJob, onRetry: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = when (job.status) {
        JobStatus.COMPLETED -> Icons.Filled.CheckCircle to cs.primary
        JobStatus.FAILED -> Icons.Filled.ErrorOutline to cs.error
        JobStatus.CANCELLED -> Icons.Filled.Close to cs.onSurfaceVariant
        else -> Icons.Filled.HourglassEmpty to cs.secondary
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.title, fontWeight = FontWeight.SemiBold)
                Text("${job.source.label} · ${job.message}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Text(time(job.updatedAt), fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            when (job.status) {
                JobStatus.FAILED, JobStatus.CANCELLED -> IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, "Retry") }
                JobStatus.QUEUED -> IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") }
                else -> {}
            }
        }
    }
}

private fun time(ts: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ts))
