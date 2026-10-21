package com.notivault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notivault.data.prefs.Settings
import com.notivault.data.prefs.SettingsStore
import com.notivault.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsStore: SettingsStore,
    lockManager: LockManager,
) : ViewModel() {
    /** null until DataStore has loaded, so we don't flash onboarding for returning users. */
    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val locked = lockManager.locked

    private val _listenerEnabled = MutableStateFlow(true)
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()
    fun refreshListener(enabled: Boolean) { _listenerEnabled.value = enabled }

    private val _deepLinkMessage = MutableStateFlow<Long?>(null)
    val deepLinkMessage = _deepLinkMessage.asStateFlow()
    fun openMessage(id: Long?) { _deepLinkMessage.value = id }
}
