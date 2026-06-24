package com.komsomol.rustream.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val darkTheme        = repo.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val downloadPath     = repo.downloadPath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "/sdcard/RuStream")
    val ruTrackerLogin   = repo.ruTrackerLogin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ruTrackerPassword= repo.ruTrackerPassword.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ruTorEnabled     = repo.ruTorEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ruTrackerEnabled = repo.ruTrackerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDarkTheme(v: Boolean)        = viewModelScope.launch { repo.setDarkTheme(v) }
    fun setDownloadPath(v: String)      = viewModelScope.launch { repo.setDownloadPath(v) }
    fun setRuTrackerLogin(v: String)    = viewModelScope.launch { repo.setRuTrackerLogin(v) }
    fun setRuTrackerPassword(v: String) = viewModelScope.launch { repo.setRuTrackerPassword(v) }
    fun setRuTorEnabled(v: Boolean)     = viewModelScope.launch { repo.setRuTorEnabled(v) }
    fun setRuTrackerEnabled(v: Boolean) = viewModelScope.launch { repo.setRuTrackerEnabled(v) }
}
