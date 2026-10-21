package com.notivault.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.notivault.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Locks the vault on cold start and after the app sits in the background past the auto-lock delay. */
@Singleton
class LockManager @Inject constructor(private val settings: SettingsStore) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = 0L

    init {
        scope.launch { if (!settings.current().lockEnabled) _locked.value = false }
    }

    fun unlock() { _locked.value = false }

    override fun onStop(owner: LifecycleOwner) { backgroundedAt = System.currentTimeMillis() }

    override fun onStart(owner: LifecycleOwner) {
        if (backgroundedAt == 0L) return
        scope.launch {
            val s = settings.current()
            val elapsed = System.currentTimeMillis() - backgroundedAt
            if (s.lockEnabled && elapsed >= s.autoLockMinutes * 60_000L) _locked.value = true
        }
    }
}
