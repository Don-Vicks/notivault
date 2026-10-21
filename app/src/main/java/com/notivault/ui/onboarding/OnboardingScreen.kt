package com.notivault.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notivault.capture.NotiListenerService
import com.notivault.data.prefs.SettingsStore
import com.notivault.security.LockManager
import com.notivault.ui.components.PIN_LENGTH
import com.notivault.ui.components.PermissionCard
import com.notivault.ui.components.PinDots
import com.notivault.ui.components.PinPad
import com.notivault.ui.components.pinKeySize
import com.notivault.ui.motion.VaultPolygons
import com.notivault.ui.motion.rememberBreathingShape
import com.notivault.ui.motion.rememberMorphTo
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val lock: LockManager,
) : ViewModel() {
    fun finish(pin: String?) = viewModelScope.launch {
        if (pin != null) settings.setPin(pin)
        settings.setOnboardingDone()
        lock.unlock()
    }
}

private data class IntroPage(val icon: ImageVector, val title: String, val body: String)

private val intro = listOf(
    IntroPage(Icons.Outlined.NotificationsActive, "Every message, kept",
        "NotiVault saves each notification the moment it arrives, so you keep a record of what was sent to you."),
    IntroPage(Icons.Outlined.DeleteSweep, "Deleted? Still yours",
        "When someone deletes a message for everyone, your copy stays and gets marked Recovered."),
    IntroPage(Icons.Outlined.WifiOff, "Private by design",
        "Everything is encrypted on this phone. The app has no internet permission, so nothing can leave it."),
)

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val total = intro.size + 2 // intro pages + permissions + PIN

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        StepIndicator(step, total, Modifier.padding(Space.lg))
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { s ->
            when {
                s < intro.size -> IntroStep(intro[s], s, onNext = { step++ })
                s == intro.size -> PermissionStep(onNext = { step++ })
                else -> PinSetup(onDone = { vm.finish(it) })
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int, total: Int, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val w by animateDpAsState(if (i == step) 28.dp else 8.dp, label = "w")
            val c by animateColorAsState(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "c")
            Box(Modifier.height(8.dp).width(w).clip(RoundedCornerShape(50)).background(c))
        }
    }
}

@Composable
private fun IntroStep(page: IntroPage, index: Int, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        if (index == 0) VaultMark(size = 180, locked = true)
        else Box(Modifier.size(150.dp).clip(rememberBreathingShape(VaultPolygons.tour[index], VaultPolygons.tour[(index + 1) % VaultPolygons.tour.size]))
            .background(if (index == 1) Vault.colors.recoveredContainer else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(page.icon, null, Modifier.size(64.dp),
                tint = if (index == 1) Vault.colors.recovered else MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(Space.xl))
        Text(page.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Vault.colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
            Text("Continue", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun PermissionStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val listener = remember(tick) { NotiListenerService.isEnabled(context) }
    val postGranted = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val battery = remember(tick) { isIgnoringBattery(context) }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var disclosure by remember { mutableStateOf(false) }

    // Google Play prominent disclosure: shown in-app, before the system permission screen.
    if (disclosure) androidx.compose.material3.AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text("NotiVault will read your notifications") },
        text = {
            Text(
                "To keep a record of messages sent to you, NotiVault reads the content of notifications from the apps " +
                    "you choose, including sender names, message text and image previews. This data is stored encrypted on " +
                    "this phone only. NotiVault has no internet access and never shares, uploads or sells it. You can " +
                    "exclude apps, pause recording or erase everything at any time.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                disclosure = false
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("I agree, continue") }
        },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } },
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg)) {
        Text("Turn on capture", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Space.xs))
        Text("Three quick switches. The first one is required.", style = MaterialTheme.typography.bodyLarge, color = Vault.colors.textSecondary)
        Spacer(Modifier.height(Space.lg))
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            PermissionCard(Icons.Outlined.Notifications, "Notification access",
                "Lets NotiVault read incoming notifications. Required.", listener, "Allow") {
                disclosure = true
            }
            PermissionCard(Icons.Outlined.NotificationsActive, "Recovery alerts",
                "Tells you when a message was deleted and your copy was kept.", postGranted, "Allow") {
                if (Build.VERSION.SDK_INT >= 33) postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionCard(Icons.Outlined.BatteryChargingFull, "Run in background",
                "Stops Android from putting the listener to sleep.", battery, "Allow") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }
        }
        OemTip()
        Spacer(Modifier.height(Space.xl))
        Button(onClick = onNext, enabled = listener, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
            Text(if (listener) "Continue" else "Allow notification access to continue")
        }
    }
}

@Composable
private fun OemTip() {
    val tip = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "On Xiaomi: Settings › Apps › NotiVault › Autostart → On, and Battery saver → No restrictions."
        "samsung" -> "On Samsung: Settings › Battery › Background usage limits → add NotiVault to Never sleeping apps."
        "oppo", "realme", "oneplus" -> "On ${Build.MANUFACTURER}: Settings › Battery › NotiVault → Allow background activity and Auto launch."
        "huawei", "honor" -> "On ${Build.MANUFACTURER}: Settings › Battery › App launch › NotiVault → Manage manually, all switches on."
        "vivo" -> "On Vivo: Settings › Battery › Background power consumption → allow NotiVault."
        else -> return
    }
    Spacer(Modifier.height(Space.md))
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
        Row(Modifier.padding(Space.md)) {
            Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Space.sm))
            Text(tip, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun isIgnoringBattery(context: Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@Composable
fun PinSetup(onDone: (String?) -> Unit, allowSkip: Boolean = true) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(rememberMorphTo(if (first == null) VaultPolygons.squircle else VaultPolygons.cookie))
            .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(Space.sm))
        Text(if (first == null) "Set a 6-digit PIN" else "Confirm your PIN", style = MaterialTheme.typography.headlineSmall)
        Text(if (error) "PINs didn't match, start again" else "Keeps your vault private if someone else picks up your phone.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = if (error) Vault.colors.danger else Vault.colors.textSecondary)
        Spacer(Modifier.height(Space.lg))
        PinDots(pin.length, error, shake)
        Spacer(Modifier.weight(1f).heightIn(min = Space.md))
        PinPad(
            onDigit = { d ->
                if (pin.length >= PIN_LENGTH) return@PinPad
                error = false; pin += d
                if (pin.length == PIN_LENGTH) scope.launch {
                    when (first) {
                        null -> { first = pin; pin = "" }
                        pin -> onDone(pin)
                        else -> { error = true; shake++; first = null; pin = "" }
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            keySize = pinKeySize(LocalConfiguration.current.screenHeightDp.dp),
        )
        Spacer(Modifier.height(Space.xs))
        TextButton(onClick = { onDone(null) }) { Text(if (allowSkip) "Skip for now" else "Cancel") }
    }
}
