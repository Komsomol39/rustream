package com.komsomol.rustream.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.komsomol.rustream.R
import com.komsomol.rustream.data.torrent.TorrentEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TorrentService : Service() {

    @Inject lateinit var engine: TorrentEngine

    companion object {
        const val CHANNEL_ID = "rustream_downloads"
        const val NOTIF_ID   = 1001
        const val ACTION_STOP = "com.komsomol.rustream.STOP_TORRENT"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        engine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RuStream")
            .setContentText("Загрузки активны")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Загрузки", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Фоновые загрузки торрентов" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
