package com.komsomol.rustream.player

import android.app.PendingIntent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.komsomol.rustream.R
import com.komsomol.rustream.data.music.PlayerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// MediaSession вокруг общего ExoPlayer. media3 сам держит foreground-уведомление,
// пока идёт воспроизведение — и показывает его на локскрине.
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var controller: PlayerController

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Провайдер уведомления с нашим каналом
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel)
                .build()
        )

        val player = controller.obtainPlayer()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // При смахивании приложения из недавних — если не играет, гасим сервис
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
    }
}
