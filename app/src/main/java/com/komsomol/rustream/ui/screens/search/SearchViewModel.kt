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

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val settings: SettingsRepository,
    private val rtCookies: RuTrackerCookieStore,
    private val nnmCookies: NnmCookieStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow(ContentCategory.ALL)
    val category: StateFlow<ContentCategory> = _category.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(q: String) { _query.value = q }

    fun onCategoryChange(cat: ContentCategory) {
        _category.value = cat
        if (_query.value.isNotBlank()) search()
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading

            // Debug: логируем состояние источников
            val rtEnabled  = settings.ruTrackerEnabled.first()
            val nnmEnabled = settings.nnmEnabled.first()
            val rtLogged   = rtCookies.isLoggedIn()
            val nnmLogged  = nnmCookies.isLoggedIn()
            val nnmRaw     = nnmCookies.getRawCookies()
            Log.d("SearchVM", "RT enabled=$rtEnabled logged=$rtLogged")
            Log.d("SearchVM", "NNM enabled=$nnmEnabled logged=$nnmLogged")
            Log.d("SearchVM", "NNM raw cookies: $nnmRaw")

            try {
                val results = repository.search(q, _category.value)
                Log.d("SearchVM", "Results: ${results.size}")
                _uiState.value = if (results.isEmpty())
                    SearchUiState.Error("Ничего не найдено")
                else
                    SearchUiState.Success(results)
            } catch (e: Exception) {
                Log.e("SearchVM", "Search error", e)
                _uiState.value = SearchUiState.Error("Ошибка: ${e.message}")
            }
        }
    }
}
