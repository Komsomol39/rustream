package com.komsomol.rustream.data.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.komsomol.rustream.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Все методы должны вызываться с главного потока (из ViewModel так и происходит)
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

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context).build()
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
        p.setMediaItems(all.map { MediaItem.fromUri(Uri.fromFile(File(it.path))) }, idx, 0L)
        p.prepare()
        p.play()
        _current.value = track
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() = player?.seekToNextMediaItem() ?: Unit
    fun prev() = player?.seekToPreviousMediaItem() ?: Unit
    fun seekTo(ms: Long) = player?.seekTo(ms) ?: Unit
}
