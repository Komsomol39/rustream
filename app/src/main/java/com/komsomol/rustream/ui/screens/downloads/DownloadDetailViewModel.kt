package com.komsomol.rustream.ui.screens.downloads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.domain.model.TorrentFileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadDetailViewModel @Inject constructor(
    private val repo: DownloadRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val id: String = savedStateHandle.get<String>("id") ?: ""

    val item = repo.downloads.map { it[id] }

    private val _files = MutableStateFlow<List<TorrentFileEntry>>(emptyList())
    val files: StateFlow<List<TorrentFileEntry>> = _files.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _files.value = repo.getFiles(id)
                delay(1000)
            }
        }
    }

    fun toggle(index: Int, enabled: Boolean) {
        repo.setFileEnabled(id, index, enabled)
        _files.value = repo.getFiles(id)
    }

    fun setAll(enabled: Boolean) {
        repo.setAllFilesEnabled(id, enabled)
        _files.value = repo.getFiles(id)
    }
}
