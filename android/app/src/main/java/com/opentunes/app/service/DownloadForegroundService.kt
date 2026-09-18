package com.opentunes.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.opentunes.app.MainActivity
import com.opentunes.app.core.download.DownloadManager
import com.opentunes.app.core.models.TrackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "opentunes_downloads"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenTunes:DownloadWakeLock").apply {
            acquire(60 * 60 * 1000L) // 1 hour max
        }

        startForegroundNotification("OpenTunes Downloader", "Starting downloads...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification("OpenTunes Downloader", "Processing downloads...")
        startMonitoring()
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(title: String, content: String, progress: Int = 0) {
        try {
            val notification = buildNotification(title, content, progress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(title: String, content: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 1..99) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (true) {
                val tasks = DownloadManager.tasks.value
                val active = tasks.filter {
                    it.status.value in listOf(
                        TrackStatus.DOWNLOADING,
                        TrackStatus.MATCHING,
                        TrackStatus.CONVERTING,
                        TrackStatus.TAGGING,
                        TrackStatus.QUEUED
                    )
                }

                if (active.isEmpty()) {
                    delay(2000)
                    val stillActive = DownloadManager.tasks.value.any {
                        it.status.value in listOf(
                            TrackStatus.DOWNLOADING,
                            TrackStatus.MATCHING,
                            TrackStatus.CONVERTING,
                            TrackStatus.TAGGING,
                            TrackStatus.QUEUED
                        )
                    }
                    if (!stillActive) {
                        stopSelf()
                        break
                    }
                }

                val current = active.firstOrNull { it.status.value == TrackStatus.DOWNLOADING } ?: active.first()
                val percent = current.progressPercent.value.toInt()
                val speed = current.speedStr.value
                val statusText = "${current.status.value.displayName}: ${current.track.title} ${if (speed.isNotEmpty()) "($speed)" else ""}"

                val notif = buildNotification(
                    title = "Downloading (${active.size} remaining)",
                    content = statusText,
                    progress = percent
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notif)

                delay(1000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active OpenTunes downloads and progress"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
