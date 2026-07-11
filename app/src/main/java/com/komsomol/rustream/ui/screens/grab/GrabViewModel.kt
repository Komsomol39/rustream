package com.komsomol.rustream.ui.screens.grab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.grab.GrabRepository
import com.komsomol.rustream.domain.model.GrabResult
import com.komsomol.rustream.domain.model.GrabFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GrabUiState {
    object Idle : GrabUiState()
    object Loading : GrabUiState()
    data class Success(val results: List<GrabResult>) : GrabUiState()
    data class Error(val message: String) : GrabUiState()
}

@HiltViewModel
class GrabViewModel @Inject constructor(
    private val repo: GrabRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GrabUiState>(GrabUiState.Idle)
    val uiState: StateFlow<GrabUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val downloads = repo.downloads
    val formatQuery = repo.formatQuery

    private val _engineMsg = MutableStateFlow<String?>(null)
    val engineMsg: StateFlow<String?> = _engineMsg.asStateFlow()

    private var job: Job? = null

    fun updateEngine() = viewModelScope.launch {
        _engineMsg.value = "Обновляю yt-dlp..."
        _engineMsg.value = repo.updateYtDlp()
    }

    fun onQueryChange(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = GrabUiState.Loading
            try {
                val results = repo.search(q)
                _uiState.value = if (results.isEmpty())
                    GrabUiState.Error("Ничего не найдено")
                else GrabUiState.Success(results)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = GrabUiState.Error("Ошибка: " + (e.message ?: "?"))
            }
        }
    }

    // Показать варианты качества для вставленной ссылки
    fun queryFormats(url: String) = repo.queryFormats(url)
    fun dismissFormats() = repo.dismissFormats()
    fun downloadFormat(url: String, title: String, fmt: GrabFormat) =
        repo.startFormat(url, title, fmt)

    fun dismiss(id: String) = repo.dismiss(id)
    fun cancel(id: String)  = repo.cancel(id)
}
