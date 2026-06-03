package com.betesepmu.vendor.spooler

import com.betesepmu.vendor.printer.PrintPayload

enum class JobStatus { QUEUED, PRINTING, COMPLETED, FAILED, CANCELLED }

/** Where a job entered the broker from — shown in the queue and useful for debugging. */
enum class JobSource(val label: String) {
    SAMPLE("Sample"),
    INTENT("Intent / URI"),
    HTTP("HTTP"),
    PRINT_SERVICE("Print Service"),
    AIDL("AIDL"),
    MANUAL("Manual"),
}

/**
 * A unit of work in the spooler. Immutable; the queue replaces it with a [copy] on every
 * state change so the UI's [kotlinx.coroutines.flow.StateFlow] recomposes cleanly.
 */
data class PrintJob(
    val id: Long,
    val title: String,
    val source: JobSource,
    val payload: PrintPayload,
    val copies: Int = 1,
    val status: JobStatus = JobStatus.QUEUED,
    val attempts: Int = 0,
    val message: String = "Queued",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isTerminal: Boolean get() = status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED
    val isActive: Boolean get() = status == JobStatus.QUEUED || status == JobStatus.PRINTING
}
