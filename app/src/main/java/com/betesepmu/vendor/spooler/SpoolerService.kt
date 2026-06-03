package com.betesepmu.vendor.spooler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.betesepmu.vendor.BetEseApp
import com.betesepmu.vendor.MainActivity
import com.betesepmu.vendor.R
import com.betesepmu.vendor.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the broker process alive so print jobs survive the UI
 * backgrounding, owns the persistent notification, and runs the local HTTP surface for as
 * long as it is enabled. It self-stops when the queue is idle and HTTP is off.
 *
 * The [PrintQueue] worker itself lives on the app-scope in [AppContainer]; this service is
 * purely about process priority + lifecycle, the classic spooler arrangement.
 */
class SpoolerService : LifecycleService() {

    private lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = BetEseApp.container(this)
        createChannel()
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground(statusText(container.printQueue.jobs.value))
        syncHttp()
        return START_STICKY
    }

    private fun observe() {
        lifecycleScope.launch {
            container.printQueue.jobs.collect { jobs ->
                notificationManager().notify(NOTIF_ID, buildNotification(statusText(jobs)))
                if (jobs.none { it.isActive } && !container.settingsRepository.current.httpServerEnabled) {
                    ServiceCompat.stopForeground(this@SpoolerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        lifecycleScope.launch {
            container.settingsRepository.settings.collect { syncHttp() }
        }
    }

    private fun syncHttp() {
        val enabled = container.settingsRepository.current.httpServerEnabled
        val server = container.httpServer
        if (enabled && !server.isRunning) server.start()
        if (!enabled && server.isRunning) server.stop()
    }

    private fun statusText(jobs: List<PrintJob>): String {
        val active = jobs.count { it.isActive }
        val http = if (container.settingsRepository.current.httpServerEnabled) " · HTTP :${container.httpServer.port}" else ""
        return (if (active > 0) "$active job${if (active > 1) "s" else ""} in queue" else "Ready") + http
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BetEse Vendor")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Print spooler", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the BetEse Vendor print queue status"
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        container.httpServer.stop()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "betese_spooler"
        const val NOTIF_ID = 4201
    }
}
