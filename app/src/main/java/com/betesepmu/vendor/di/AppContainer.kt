package com.betesepmu.vendor.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.betesepmu.vendor.data.AuthRepository
import com.betesepmu.vendor.data.SettingsRepository
import com.betesepmu.vendor.data.VendorRepository
import com.betesepmu.vendor.integration.http.PrintBrokerHttpServer
import com.google.firebase.firestore.FirebaseFirestore
import com.betesepmu.vendor.printer.PrintPayload
import com.betesepmu.vendor.printer.TransportManager
import com.betesepmu.vendor.samples.SampleCatalog
import com.betesepmu.vendor.spooler.JobSource
import com.betesepmu.vendor.spooler.PrintQueue
import com.betesepmu.vendor.spooler.SpoolerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container (lighter and more build-robust than Hilt for a single
 * module). One instance lives on the [com.betesepmu.vendor.BetEseApp]; everything —
 * UI, spooler service, and every integration surface — pulls its collaborators from here,
 * so there is exactly one settings store, one transport manager and one print queue.
 */
class AppContainer(val context: Context) {

    /** App-lifetime scope for the spooler worker; survives Activity/Service churn. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository = SettingsRepository(context)
    val transportManager = TransportManager(context, settingsRepository)
    val printQueue = PrintQueue(appScope, transportManager, settingsRepository)

    /** Firebase backend (project betesepmu-4ffc7). One Firestore client for the whole app. */
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val authRepository: AuthRepository by lazy { AuthRepository(context, firestore) }
    val vendorRepository: VendorRepository by lazy { VendorRepository(firestore) }

    val httpServer: PrintBrokerHttpServer by lazy { PrintBrokerHttpServer(this) }

    fun samples(): SampleCatalog = SampleCatalog(context, settingsRepository.current)

    /** Single entry point used by every surface to submit work and keep the process alive. */
    fun enqueue(title: String, source: JobSource, payload: PrintPayload, copies: Int? = null): Long {
        val id = printQueue.enqueue(title, source, payload, copies ?: settingsRepository.current.copies)
        ensureSpoolerRunning()
        return id
    }

    fun ensureSpoolerRunning() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, SpoolerService::class.java))
        }
    }
}
