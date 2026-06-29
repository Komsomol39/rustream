package com.komsomol.rustream.ui.screens.downloads

import androidx.lifecycle.ViewModel
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.domain.model.DownloadItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadRepository
) : ViewModel() {

    val downloads = repo.downloads.map { it.values.sortedByDescending { d -> d.addedAt } }

    fun pause(item: DownloadItem)  = repo.pause(item.id)
    fun resume(item: DownloadItem) = repo.resume(item.id)
    fun remove(item: DownloadItem) = repo.remove(item.id, deleteFiles = false)
}
