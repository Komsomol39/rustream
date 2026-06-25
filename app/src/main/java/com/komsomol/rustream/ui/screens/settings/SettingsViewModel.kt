package com.komsomol.rustream.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val cookieStore: RuTrackerCookieStore
) : ViewModel() {

    val darkTheme        = repo.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ruTorEnabled     = repo.ruTorEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ruTrackerEnabled = repo.ruTrackerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _ruTrackerLoggedIn = MutableStateFlow(cookieStore.isLoggedIn())
    val ruTrackerLoggedIn: StateFlow<Boolean> = _ruTrackerLoggedIn.asStateFlow()

    fun setDarkTheme(v: Boolean)        = viewModelScope.launch { repo.setDarkTheme(v) }
    fun setRuTorEnabled(v: Boolean)     = viewModelScope.launch { repo.setRuTorEnabled(v) }
    fun setRuTrackerEnabled(v: Boolean) = viewModelScope.launch { repo.setRuTrackerEnabled(v) }

    fun onRuTrackerLoginSuccess() {
        _ruTrackerLoggedIn.value = cookieStore.isLoggedIn()
    }

    fun logoutRuTracker() {
        cookieStore.clearCookies()
        _ruTrackerLoggedIn.value = false
        viewModelScope.launch { repo.setRuTrackerEnabled(false) }
    }
}
