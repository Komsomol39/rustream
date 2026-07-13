package com.komsomol.rustream.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komsomol.rustream.data.search.KinozalCookieStore
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
    private val nnmCookies: NnmCookieStore,
    private val kinozalCookies: KinozalCookieStore
) : ViewModel() {

    val darkTheme        = repo.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoUpdateCheck  = repo.autoUpdateCheck.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ruTorEnabled     = repo.ruTorEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ruTrackerEnabled = repo.ruTrackerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val kinozalEnabled   = repo.kinozalEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val nnmEnabled       = repo.nnmEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ytsEnabled       = repo.ytsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val tpbEnabled       = repo.tpbEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val newpipeEnabled   = repo.newpipeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val mediaFolders = repo.mediaFolders.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rtLoggedIn = MutableStateFlow(rtCookies.isLoggedIn())
    val ruTrackerLoggedIn: StateFlow<Boolean> = _rtLoggedIn.asStateFlow()

    private val _nnmLoggedIn = MutableStateFlow(nnmCookies.isLoggedIn())
    val nnmLoggedIn: StateFlow<Boolean> = _nnmLoggedIn.asStateFlow()

    private val _kinozalLoggedIn = MutableStateFlow(kinozalCookies.isLoggedIn())
    val kinozalLoggedIn: StateFlow<Boolean> = _kinozalLoggedIn.asStateFlow()

    fun setDarkTheme(v: Boolean)        = viewModelScope.launch { repo.setDarkTheme(v) }
    fun setAutoUpdateCheck(v: Boolean)  = viewModelScope.launch { repo.setAutoUpdateCheck(v) }
    fun setRuTorEnabled(v: Boolean)     = viewModelScope.launch { repo.setRuTorEnabled(v) }
    fun setRuTrackerEnabled(v: Boolean) = viewModelScope.launch { repo.setRuTrackerEnabled(v) }
    fun setKinozalEnabled(v: Boolean)   = viewModelScope.launch { repo.setKinozalEnabled(v) }
    fun setNnmEnabled(v: Boolean)       = viewModelScope.launch { repo.setNnmEnabled(v) }
    fun setYtsEnabled(v: Boolean)       = viewModelScope.launch { repo.setYtsEnabled(v) }
    fun setTpbEnabled(v: Boolean)       = viewModelScope.launch { repo.setTpbEnabled(v) }
    fun setNewpipeEnabled(v: Boolean)   = viewModelScope.launch { repo.setNewpipeEnabled(v) }

    fun addMediaFolder(path: String) = viewModelScope.launch { repo.addMediaFolder(path) }
    fun removeMediaFolder(path: String) = viewModelScope.launch { repo.removeMediaFolder(path) }

    fun onRuTrackerLoginSuccess() { _rtLoggedIn.value = rtCookies.isLoggedIn() }
    fun onKinozalLoginSuccess() { _kinozalLoggedIn.value = kinozalCookies.isLoggedIn() }
    fun logoutKinozal() { kinozalCookies.clearCookies(); _kinozalLoggedIn.value = false }
    fun onNnmLoginSuccess()       { _nnmLoggedIn.value = nnmCookies.isLoggedIn() }

    fun logoutRuTracker() {
        rtCookies.clearCookies(); _rtLoggedIn.value = false
        viewModelScope.launch { repo.setRuTrackerEnabled(false) }
    }
    fun logoutNnm() {
        nnmCookies.clearCookies(); _nnmLoggedIn.value = false
        viewModelScope.launch { repo.setNnmEnabled(false) }
    }
}
