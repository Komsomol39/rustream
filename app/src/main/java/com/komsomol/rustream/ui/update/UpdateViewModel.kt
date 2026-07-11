package com.komsomol.rustream.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.settings.SettingsRepository
import com.komsomol.rustream.data.update.UpdateInfo
import com.komsomol.rustream.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repo: UpdateRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val currentVersion: String get() = repo.currentVersionName()

    /** Тихая проверка при запуске: уважает настройку, при "нет обновлений" молчит */
    fun checkOnLaunch() = viewModelScope.launch {
        if (!settings.autoUpdateCheck.first()) return@launch
        val info = repo.check() ?: return@launch
        _state.value = UpdateUiState.Available(info)
    }

    /** Ручная проверка из настроек: показывает и отрицательный результат */
    fun checkNow() = viewModelScope.launch {
        _state.value = UpdateUiState.Checking
        val info = repo.check()
        _state.value = if (info != null) UpdateUiState.Available(info)
                       else UpdateUiState.UpToDate
    }

    fun downloadAndInstall(info: UpdateInfo) = viewModelScope.launch {
        _state.value = UpdateUiState.Downloading(0f)
        try {
            val file = repo.downloadApk(info) { p ->
                _state.value = UpdateUiState.Downloading(p)
            }
            _state.value = UpdateUiState.Idle
            repo.installApk(file)
        } catch (e: Exception) {
            _state.value = UpdateUiState.Error(e.message ?: "Ошибка загрузки")
        }
    }

    fun dismiss() { _state.value = UpdateUiState.Idle }
}
