package com.komsomol.rustream.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.data.torrent.TorrentEngine
import com.komsomol.rustream.domain.model.DownloadItem
import com.komsomol.rustream.domain.model.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val engine: TorrentEngine,
    private val repository: DownloadRepository
) : ViewModel() {

    val downloads = engine.downloads
        .map { it.values.sortedByDescending { d -> d.addedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pause(id: String) = repository.pause(id)
    fun resume(id: String) = repository.resume(id)
    fun remove(id: String) = repository.remove(id)
}
