package com.komsomol.rustream.ui.screens.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.video.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val repo: VideoRepository
) : ViewModel() {

    val videos   = repo.videos
    val scanning = repo.scanning

    init { refresh() }

    fun refresh() = viewModelScope.launch { repo.scan() }
    fun delete(path: String) = repo.deleteFile(path)
}
