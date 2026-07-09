package com.komsomol.rustream.data.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.komsomol.rustream.domain.model.Track
import com.komsomol.rustream.player.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Фасад над MediaController: тот же API, что и раньше,
// но плеером владеет PlaybackService (канон media3)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "PlayerController"
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var queue: List<Track> = emptyList()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            controller?.let {
                _positionMs.value = it.currentPosition
                val d = it.duration
                _durationMs.value = if (d > 0) d else 0L
            }
            handler.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = controller?.currentMediaItemIndex ?: return
            _current.value = queue.getOrNull(idx)
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffle.value = enabled
        }
        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
        }
    }

    // Выполнить действие на контроллере; при необходимости — подключиться.
    // Само создание MediaController поднимает PlaybackService.
    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) { action(c); return }
        synchronized(pending) { pending.add(action) }
        if (connecting) return
        connecting = true
        val token = SessionToken(context,
            ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val ctl = future.get()
                controller = ctl
                ctl.addListener(listener)
                handler.removeCallbacks(ticker)
                handler.post(ticker)
                val actions = synchronized(pending) {
                    val copy = pending.toList(); pending.clear(); copy
                }
                actions.forEach { it(ctl) }
            } catch (e: Exception) {
                Log.e(TAG, "controller connect failed: " + e)
                synchronized(pending) { pending.clear() }
            }
            connecting = false
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(track: Track, all: List<Track>) {
        withController { c ->
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
            c.setMediaItems(items, idx, 0L)
            c.prepare()
            c.play()
            _current.value = track
        }
    }

    fun stopAndClear() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
        _current.value = null
        _playing.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
    }

    fun pauseForExternal() { controller?.pause() }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun toggleShuffle() = withController { c ->
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        _shuffle.value = c.shuffleModeEnabled
    }

    fun cycleRepeat() = withController { c ->
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _repeatMode.value = next
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit
    fun prev() = controller?.seekToPreviousMediaItem() ?: Unit
    fun seekTo(ms: Long) = controller?.seekTo(ms) ?: Unit
}
