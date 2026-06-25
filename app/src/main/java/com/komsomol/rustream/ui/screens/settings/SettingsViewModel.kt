package com.komsomol.rustream.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.search.NnmCookieStore
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import com.komsomol.rustream.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val rtCookies: RuTrackerCookieStore,
    private val nnmCookies: NnmCookieStore
) : ViewModel() {

    val darkTheme        = repo.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ruTorEnabled     = repo.ruTorEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ruTrackerEnabled = repo.ruTrackerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val kinozalEnabled   = repo.kinozalEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val nnmEnabled       = repo.nnmEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _rtLoggedIn  = MutableStateFlow(rtCookies.isLoggedIn())
    val ruTrackerLoggedIn: StateFlow<Boolean> = _rtLoggedIn.asStateFlow()

    private val _nnmLoggedIn = MutableStateFlow(nnmCookies.isLoggedIn())
    val nnmLoggedIn: StateFlow<Boolean> = _nnmLoggedIn.asStateFlow()

    // Debug: показываем имена куков в UI
    private val _nnmCookieDebug = MutableStateFlow("")
    val nnmCookieDebug: StateFlow<String> = _nnmCookieDebug.asStateFlow()

    init { refreshDebug() }

    private fun refreshDebug() {
        val raw = nnmCookies.getRawCookies()
        val keys = raw.split(";").mapNotNull { part ->
            val eq = part.indexOf("=")
            if (eq > 0) part.substring(0, eq).trim() else null
        }
        _nnmCookieDebug.value = if (keys.isEmpty()) "(нет куков)" else keys.joinToString(", ")
    }

    fun setDarkTheme(v: Boolean)        = viewModelScope.launch { repo.setDarkTheme(v) }
    fun setRuTorEnabled(v: Boolean)     = viewModelScope.launch { repo.setRuTorEnabled(v) }
    fun setRuTrackerEnabled(v: Boolean) = viewModelScope.launch { repo.setRuTrackerEnabled(v) }
    fun setKinozalEnabled(v: Boolean)   = viewModelScope.launch { repo.setKinozalEnabled(v) }
    fun setNnmEnabled(v: Boolean)       = viewModelScope.launch { repo.setNnmEnabled(v) }

    fun onRuTrackerLoginSuccess() { _rtLoggedIn.value = rtCookies.isLoggedIn() }
    fun onNnmLoginSuccess() {
        _nnmLoggedIn.value = nnmCookies.isLoggedIn()
        refreshDebug()
    }

    fun logoutRuTracker() {
        rtCookies.clearCookies(); _rtLoggedIn.value = false
        viewModelScope.launch { repo.setRuTrackerEnabled(false) }
    }
    fun logoutNnm() {
        nnmCookies.clearCookies(); _nnmLoggedIn.value = false
        _nnmCookieDebug.value = "(нет куков)"
        viewModelScope.launch { repo.setNnmEnabled(false) }
    }
}
