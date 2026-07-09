package com.komsomol.rustream.data.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.komsomol.rustream.domain.model.Track
import com.komsomol.rustream.player.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Все методы вызывать с главного потока
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null
    private var queue: List<Track> = emptyList()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            player?.let {
                _positionMs.value = it.currentPosition
                val d = it.duration
                _durationMs.value = if (d > 0) d else 0L
            }
            handler.postDelayed(this, 500)
        }
    }

    // Для MediaSession в PlaybackService
    fun obtainPlayer(): ExoPlayer = ensurePlayer()

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context)
            // Аудиофокус (пауза при звонке) и пауза при отключении наушников
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _current.value = queue.getOrNull(p.currentMediaItemIndex)
            }
        })
        player = p
        handler.post(ticker)
        return p
    }

    fun play(track: Track, all: List<Track>) {
        val p = ensurePlayer()
        queue = all
        val idx = all.indexOfFirst { it.path == track.path }.coerceAtLeast(0)
        val items = all.map { t ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(t.path)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist ?: "RuStream")
                        .build())
                .build()
        }
        p.setMediaItems(items, idx, 0L)
        p.prepare()
        p.play()
        _current.value = track
        // Сервис с MediaSession: уведомление под шторкой и на локскрине.
        // startForegroundService — иначе на Android 12+ сервис не поднимется
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(Intent(context, PlaybackService::class.java))
            } else {
                context.startService(Intent(context, PlaybackService::class.java))
            }
        } catch (_: Exception) {}
    }

    // Полностью закрыть плеер: убрать мини-плеер и уведомление
    fun stopAndClear() {
        player?.stop()
        player?.clearMediaItems()
        _current.value = null
        _playing.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        try {
            context.stopService(Intent(context, PlaybackService::class.java))
        } catch (_: Exception) {}
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() = player?.seekToNextMediaItem() ?: Unit
    fun prev() = player?.seekToPreviousMediaItem() ?: Unit
    fun seekTo(ms: Long) = player?.seekTo(ms) ?: Unit
}
