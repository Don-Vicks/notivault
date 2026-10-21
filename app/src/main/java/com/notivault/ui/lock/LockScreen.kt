package com.notivault.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.notivault.data.prefs.SettingsStore
import com.notivault.security.LockManager
import com.notivault.ui.components.PIN_LENGTH
import com.notivault.ui.components.PinDots
import com.notivault.ui.components.PinPad
import com.notivault.ui.components.pinKeySize
import com.notivault.ui.onboarding.VaultMark
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import com.notivault.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(val settings: SettingsStore, private val lock: LockManager) : ViewModel() {
    fun unlock() = lock.unlock()
}

@Composable
fun LockScreen(biometricEnabled: Boolean, vm: LockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf(false) }

    // Let the shackle spring open before the vault fades in.
    fun open() = scope.launch {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        opening = true
        delay(Motion.emphasized.toLong())
        vm.unlock()
    }
    val canBio = biometricEnabled && Biometrics.available(context)

    fun bio() {
        (context as? FragmentActivity)?.let { Biometrics.prompt(it, "Unlock NotiVault", onSuccess = { open() }) }
    }
    LaunchedEffect(Unit) { if (canBio) bio() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VaultMark(size = 88, locked = !opening)
        Spacer(Modifier.height(Space.lg))
        Text("Vault locked", style = MaterialTheme.typography.headlineSmall)
        Text(if (error) "Wrong PIN, try again" else "Enter your PIN",
            style = MaterialTheme.typography.bodyMedium, color = if (error) Vault.colors.danger else Vault.colors.textSecondary)
        Spacer(Modifier.height(Space.lg))
        PinDots(pin.length, error, shake)
        Spacer(Modifier.height(Space.xl))
        PinPad(
            onDigit = { d ->
                if (pin.length >= PIN_LENGTH) return@PinPad
                error = false
                pin += d
                if (pin.length == PIN_LENGTH) scope.launch {
                    if (vm.settings.verifyPin(pin)) open() else { error = true; shake++; pin = "" }
                }
            },
            onBackspace = { pin = pin.dropLast(1); error = false },
            onBiometric = if (canBio) ::bio else null,
            keySize = pinKeySize(LocalConfiguration.current.screenHeightDp.dp),
        )
    }
}
