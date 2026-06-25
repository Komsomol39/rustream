package com.komsomol.rustream.ui.screens.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.search.NnmCookieStore
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import com.komsomol.rustream.data.search.SearchRepository
import com.komsomol.rustream.data.settings.SettingsRepository
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
    private val nnmCookies: NnmCookieStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query    = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow(ContentCategory.ALL)
    val category: StateFlow<ContentCategory> = _category.asStateFlow()

    private val _sourceStatuses = MutableStateFlow<List<SourceStatus>>(emptyList())
    val sourceStatuses: StateFlow<List<SourceStatus>> = _sourceStatuses.asStateFlow()

    private var searchJob: Job? = null

    init { refreshSourceStatuses() }

    fun refreshSourceStatuses() = viewModelScope.launch {
        val statuses = listOf(
            SourceStatus("Kinozal",   settings.kinozalEnabled.first(),   settings.kinozalEnabled.first()),
            SourceStatus("RuTor",     settings.ruTorEnabled.first(),     settings.ruTorEnabled.first()),
            SourceStatus("RuTracker", settings.ruTrackerEnabled.first(), settings.ruTrackerEnabled.first() && rtCookies.isLoggedIn()),
            SourceStatus("NNM-Club",  settings.nnmEnabled.first(),       settings.nnmEnabled.first() && nnmCookies.isLoggedIn()),
        )
        _sourceStatuses.value = statuses
        Log.d("SearchVM", "Statuses: $statuses")
    }

    fun onQueryChange(q: String) { _query.value = q }

    fun onCategoryChange(cat: ContentCategory) {
        _category.value = cat
        if (_query.value.isNotBlank()) search()
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        refreshSourceStatuses()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val results = repository.search(q, _category.value)
                Log.d("SearchVM", "Got ${results.size} results")
                _uiState.value = if (results.isEmpty())
                    SearchUiState.Error("Ничего не найдено (включено: ${_sourceStatuses.value.filter { it.ready }.map { it.name }})")
                else
                    SearchUiState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Error", e)
                _uiState.value = SearchUiState.Error("Ошибка: ${e.message}")
            }
        }
    }
}
