package com.betesepmu.vendor.spooler

import com.betesepmu.vendor.data.SettingsRepository
import com.betesepmu.vendor.printer.PrintOutcome
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.printer.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * The serialized print spooler. A single worker coroutine drains a channel so two jobs can
 * never write to one head at once. Each job: connects the active transport, checks head
 * status (fails fast on paper-out), prints the requested copies, and retries transient
 * failures with exponential backoff. State is published through [jobs] for the queue UI.
 */
class PrintQueue(
    private val scope: CoroutineScope,
    private val transports: TransportManager,
    private val settings: SettingsRepository,
) {
    private val _jobs = MutableStateFlow<List<PrintJob>>(emptyList())
    val jobs: StateFlow<List<PrintJob>> = _jobs

    private val channel = Channel<Long>(Channel.UNLIMITED)
    private val ids = AtomicLong(0)

    val hasActiveWork: Boolean get() = _jobs.value.any { it.isActive }

    init { scope.launch { worker() } }

    fun enqueue(
        title: String,
        source: JobSource,
        payload: PrintPayload,
        copies: Int = settings.current.copies,
    ): Long {
        val id = ids.incrementAndGet()
        val job = PrintJob(id, title, source, payload, copies.coerceAtLeast(1))
        _jobs.update { (it + job).takeLast(MAX_HISTORY) }
        channel.trySend(id)
        return id
    }

    fun cancel(id: Long) = mutate(id) {
        if (it.status == JobStatus.QUEUED) it.copy(status = JobStatus.CANCELLED, message = "Cancelled") else it
    }

    fun retry(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
            mutate(id) { it.copy(status = JobStatus.QUEUED, message = "Re-queued", updatedAt = now()) }
            channel.trySend(id)
        }
    }

    fun clearFinished() = _jobs.update { list -> list.filter { it.isActive } }

    // ---- Worker -------------------------------------------------------------
    private suspend fun worker() {
        for (id in channel) {
            val job = _jobs.value.firstOrNull { it.id == id } ?: continue
            if (job.status == JobStatus.CANCELLED) continue
            process(job)
        }
    }

    private suspend fun process(job: PrintJob) {
        mutate(job.id) { it.copy(status = JobStatus.PRINTING, attempts = it.attempts + 1, message = "Connecting…", updatedAt = now()) }
        val s = settings.current

        transports.connect(s.transportPreference)
        val status = transports.refreshStatus()
        if (status.paperOut) { mutate(job.id) { it.copy(status = JobStatus.FAILED, message = "Out of paper", updatedAt = now()) }; return }
        if (status.coverOpen) { mutate(job.id) { it.copy(status = JobStatus.FAILED, message = "Cover open", updatedAt = now()) }; return }

        val copies = job.copies.coerceAtLeast(1)
        var outcome = PrintOutcome.fail("Not started")
        for (c in 1..copies) {
            val label = if (copies > 1) "Printing copy $c of $copies…" else "Printing…"
            mutate(job.id) { it.copy(message = label, updatedAt = now()) }
            outcome = submitWithRetry(job.payload, s)
            if (!outcome.success) break
        }

        mutate(job.id) {
            if (outcome.success)
                it.copy(status = JobStatus.COMPLETED, message = "Printed" + if (copies > 1) " ×$copies" else "", updatedAt = now())
            else
                it.copy(status = JobStatus.FAILED, message = outcome.message, updatedAt = now())
        }
    }

    private suspend fun submitWithRetry(payload: PrintPayload, settingsSnapshot: com.betesepmu.vendor.core.PrintSettings): PrintOutcome {
        var outcome = PrintOutcome.fail("Not started")
        for (attempt in 1..MAX_ATTEMPTS) {
            outcome = runCatching { transports.submit(payload, settingsSnapshot) }
                .getOrElse { PrintOutcome.fail(it.message ?: "Submit error") }
            if (outcome.success) return outcome
            if (attempt < MAX_ATTEMPTS) {
                delay(BACKOFF_BASE_MS * attempt)
                transports.connect(settingsSnapshot.transportPreference) // recover dropped binding
            }
        }
        return outcome
    }

    private fun mutate(id: Long, transform: (PrintJob) -> PrintJob) =
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val BACKOFF_BASE_MS = 600L
        const val MAX_HISTORY = 100
    }
}
