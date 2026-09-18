package com.opentunes.app

import android.app.Application
import com.opentunes.app.core.config.AppConfig
import com.opentunes.app.core.download.DownloadManager
import com.opentunes.app.core.library.LibraryRepository
import com.opentunes.app.core.youtube.YouTubeMusicExtractor

class OpenTunesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        AppConfig.init(this)
        DownloadManager.init(this)
        YouTubeMusicExtractor.init()
        LibraryRepository.init(this)
        com.opentunes.app.core.library.RecentlyPlayedManager.init(this)
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val downloadChannel = android.app.NotificationChannel(
                "opentunes_downloads",
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress for audio tracks"
                setShowBadge(false)
            }
            val mediaChannel = android.app.NotificationChannel(
                "opentunes_media_playback",
                "Media Playback",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio playback and lock screen controls"
                setShowBadge(false)
            }
            nm?.createNotificationChannel(downloadChannel)
            nm?.createNotificationChannel(mediaChannel)
        }
    }
}
