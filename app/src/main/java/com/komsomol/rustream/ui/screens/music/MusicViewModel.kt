package com.komsomol.rustream.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.music.MusicRepository
import com.komsomol.rustream.data.music.PlayerController
import com.komsomol.rustream.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val player: PlayerController
) : ViewModel() {

    val tracks     = repo.tracks
    val scanning   = repo.scanning
    val current    = player.current
    val playing    = player.playing
    val positionMs = player.positionMs
    val durationMs = player.durationMs

    init { refresh() }

    fun refresh() = viewModelScope.launch { repo.scan() }
    fun play(t: Track) = player.play(t, tracks.value)
    fun toggle() = player.toggle()
    fun next() = player.next()
    fun prev() = player.prev()
    fun seekTo(ms: Long) = player.seekTo(ms)
}
