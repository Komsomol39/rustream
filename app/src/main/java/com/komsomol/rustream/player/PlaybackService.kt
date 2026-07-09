package com.komsomol.rustream.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.komsomol.rustream.data.music.PlayerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// MediaSession вокруг общего ExoPlayer: уведомление под шторкой,
// управление с локскрина и наушников
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var controller: PlayerController

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = controller.obtainPlayer()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        ensureChannel()
    }

    // Пришёл startForegroundService — обязаны сразу уйти в foreground,
    // иначе система убьёт сервис (и уведомления не будет вовсе)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = mediaSession
        if (session != null) {
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
                .setContentTitle(session.player.mediaMetadata.title ?: "RuStream")
                .setContentText(session.player.mediaMetadata.artist ?: "")
                .setContentIntent(session.sessionActivity)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // локскрин
                .setOngoing(true)
                .build()
            startForeground(NOTIF_ID, notif)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Воспроизведение музыки",
                NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 4242
    }
}
