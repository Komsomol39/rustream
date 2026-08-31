package com.komsomol.rustream.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.music.MusicRepository
import com.komsomol.rustream.data.music.PlayerController
import com.komsomol.rustream.domain.model.ArtistGroup
import kotlinx.coroutines.flow.combine
import com.komsomol.rustream.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val player: PlayerController
) : ViewModel() {

    val artists = repo.artists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val scanning   = repo.scanning
    val current    = player.current
    val playing    = player.playing
    val positionMs = player.positionMs
    val durationMs = player.durationMs

    // Режим выбора для объединения
    private val _selectMode = MutableStateFlow(false)
    val selectMode: StateFlow<Boolean> = _selectMode.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { repo.scan() }

    /** Имя папки исполнителя, в которую попал только что скачанный файл. */
    suspend fun artistForFile(path: String): String? = repo.artistForFile(path)

    fun enterSelect(firstArtist: String) {
        _selectMode.value = true
        _selected.value = setOf(firstArtist)
    }
    fun toggleSelect(artist: String) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (!add(artist)) remove(artist)
        }
    }
    fun cancelSelect() { _selectMode.value = false; _selected.value = emptySet() }

    fun mergeSelected() {
        val names = _selected.value.toList()
        cancelSelect()
        if (names.size >= 2) viewModelScope.launch { repo.mergeArtists(names) }
    }

    fun unmerge(name: String) = viewModelScope.launch { repo.unmergeArtist(name) }

    fun deleteTrack(path: String) = viewModelScope.launch { repo.deleteFile(path) }

    fun playFrom(t: Track, all: List<Track>) = player.play(t, all)

    // ---- Поиск по всей библиотеке ----

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    /**
     * Ищем по всей библиотеке сразу, независимо от исполнителя: совпадение
     * по названию, имени файла или исполнителю. Порядок результатов и будет
     * плейлистом, если включить трек прямо отсюда.
     */
    val searchResults: StateFlow<List<Track>> =
        combine(repo.artists, _query) { groups, q ->
            val needle = q.trim().lowercase()
            if (needle.isEmpty()) emptyList()
            else groups.flatMap { it.tracks }
                .filter { t ->
                    t.title.lowercase().contains(needle) ||
                    t.fileName.lowercase().contains(needle) ||
                    (t.artist?.lowercase()?.contains(needle) == true)
                }
                .sortedBy { it.title.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val shuffle    = player.shuffle
    val repeatMode = player.repeatMode

    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat()   = player.cycleRepeat()

    // Играть всю библиотеку с первого трека
    fun playAll() {
        val all = artists.value.flatMap { it.tracks }
        val first = all.firstOrNull() ?: return
        player.play(first, all)
    }

    fun stopPlayback() = player.stopAndClear()
    fun toggle() = player.toggle()
    fun next() = player.next()
    fun prev() = player.prev()
    fun seekTo(ms: Long) = player.seekTo(ms)
}
