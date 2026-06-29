package com.komsomol.rustream.ui.screens.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.search.NnmCookieStore
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import com.komsomol.rustream.data.search.SearchRepository
import com.komsomol.rustream.data.settings.SettingsRepository
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.domain.model.ContentCategory
import com.komsomol.rustream.domain.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<SearchResult>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

data class SourceStatus(val name: String, val enabled: Boolean, val ready: Boolean)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val settings: SettingsRepository,
    private val rtCookies: RuTrackerCookieStore,
    private val nnmCookies: NnmCookieStore,
    private val downloadRepo: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query    = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow(ContentCategory.ALL)
    val category: StateFlow<ContentCategory> = _category.asStateFlow()

    private val _activeStatuses = MutableStateFlow<List<SourceStatus>>(emptyList())
    val activeStatuses: StateFlow<List<SourceStatus>> = _activeStatuses.asStateFlow()

    private var searchJob: Job? = null

    init { refreshStatuses() }

    fun refreshStatuses() = viewModelScope.launch {
        val all = listOf(
            SourceStatus("Kinozal",   settings.kinozalEnabled.first(),   settings.kinozalEnabled.first()),
            SourceStatus("RuTor",     settings.ruTorEnabled.first(),     settings.ruTorEnabled.first()),
            SourceStatus("YTS",       settings.ytsEnabled.first(),       settings.ytsEnabled.first()),
            SourceStatus("RuTracker", settings.ruTrackerEnabled.first(), settings.ruTrackerEnabled.first() && rtCookies.isLoggedIn()),
            SourceStatus("NNM-Club",  settings.nnmEnabled.first(),       settings.nnmEnabled.first() && nnmCookies.isLoggedIn()),
        )
        _activeStatuses.value = all.filter { it.enabled }
    }

    fun onQueryChange(q: String) { _query.value = q }

    fun onCategoryChange(cat: ContentCategory) {
        _category.value = cat
        if (_query.value.isNotBlank()) search()
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        refreshStatuses()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val results = repository.search(q, _category.value)
                _uiState.value = if (results.isEmpty()) SearchUiState.Error("Ничего не найдено")
                                 else SearchUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error("Ошибка: ${e.message}")
            }
        }
    }

    fun startMagnet(result: SearchResult) = viewModelScope.launch {
        downloadRepo.startMagnet(result)
        Log.d("SearchVM", "Magnet started: ${result.title}")
    }

    fun startTorrentUrl(result: SearchResult) = viewModelScope.launch {
        downloadRepo.startTorrentUrl(result)
        Log.d("SearchVM", "Torrent started: ${result.title}")
    }
}
