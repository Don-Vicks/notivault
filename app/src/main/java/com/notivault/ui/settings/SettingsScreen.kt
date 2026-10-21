package com.notivault.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notivault.BuildConfigInfo
import com.notivault.data.prefs.Settings
import com.notivault.data.prefs.SettingsStore
import com.notivault.data.prefs.ThemeMode
import com.notivault.data.repo.BackupManager
import com.notivault.data.repo.VaultRepository
import com.notivault.ui.components.SectionHeader
import com.notivault.ui.components.SettingRow
import com.notivault.ui.components.SettingSwitchRow
import com.notivault.ui.lock.Biometrics
import com.notivault.ui.theme.Space
import com.notivault.ui.theme.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val repo: VaultRepository,
    private val backup: BackupManager,
) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun theme(m: ThemeMode) = viewModelScope.launch { store.setThemeMode(m) }
    fun dynamic(on: Boolean) = viewModelScope.launch { store.setDynamicColor(on) }
    fun biometric(on: Boolean) = viewModelScope.launch { store.setBiometric(on) }
    fun alerts(on: Boolean) = viewModelScope.launch { store.setRecoveredAlerts(on) }
    fun messagingOnly(on: Boolean) = viewModelScope.launch { store.setMessagingOnly(on) }
    fun autoLock(m: Int) = viewModelScope.launch { store.setAutoLockMinutes(m) }
    fun disableLock() = viewModelScope.launch { store.disableLock() }
    fun retention(days: Int) = viewModelScope.launch {
        store.setRetentionDays(days)
        if (days > 0) repo.purgeOlderThan(System.currentTimeMillis() - days * 86_400_000L)
    }
    fun wipe(ctx: Context) = viewModelScope.launch { repo.wipeAll(); toast(ctx, "Vault erased") }

    fun export(ctx: Context, uri: Uri, pw: String) = viewModelScope.launch {
        runCatching { backup.export(uri, pw.toCharArray()) }
            .onSuccess { toast(ctx, "Backed up $it messages") }
            .onFailure { toast(ctx, "Backup failed: ${it.message}") }
    }

    fun import(ctx: Context, uri: Uri, pw: String) = viewModelScope.launch {
        runCatching { backup.import(uri, pw.toCharArray()) }
            .onSuccess { toast(ctx, "Restored $it new messages") }
            .onFailure { toast(ctx, if (it is javax.crypto.AEADBadTagException) "Wrong password" else "Restore failed: ${it.message}") }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

private enum class PwMode { Export, Import }

@Composable
fun SettingsScreen(listenerOn: Boolean, onSetPin: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pwFor by remember { mutableStateOf<Pair<PwMode, Uri>?>(null) }
    var retentionOpen by remember { mutableStateOf(false) }
    var autoLockOpen by remember { mutableStateOf(false) }
    var wipeOpen by remember { mutableStateOf(false) }

    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { pwFor = PwMode.Export to it }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pwFor = PwMode.Import to it }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Space.xl)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = Space.md, top = Space.lg))

        SectionHeader("Capture")
        SettingRow(Icons.Outlined.Sensors, if (listenerOn) "Listening" else "Paused",
            if (listenerOn) "Notification access granted. New messages are being saved."
            else "Tap to grant notification access.",
            onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            trailing = {
                Text(if (listenerOn) "●" else "○", color = if (listenerOn) Vault.colors.success else Vault.colors.danger)
            })
        SettingSwitchRow(Icons.Outlined.NotificationsActive, "Recovery alerts",
            "Notify me when a sender deletes a message I have saved", s.recoveredAlerts, vm::alerts)
        SettingSwitchRow(Icons.Outlined.Forum, "Messaging & social apps only",
            "Skip shopping, news and system notifications. WhatsApp, Telegram, Instagram, Signal and 20+ more stay on.",
            s.messagingOnly, vm::messagingOnly)

        SectionHeader("Security")
        SettingSwitchRow(Icons.Outlined.Lock, "App lock", if (s.lockEnabled) "6-digit PIN required to open" else "Off", s.lockEnabled) { on ->
            if (on) onSetPin() else vm.disableLock()
        }
        if (s.lockEnabled) {
            if (Biometrics.available(context)) {
                SettingSwitchRow(Icons.Outlined.Fingerprint, "Unlock with biometrics", null, s.biometricEnabled) { on ->
                    if (!on) vm.biometric(false)
                    else (context as? FragmentActivity)?.let { Biometrics.prompt(it, "Confirm biometrics", onSuccess = { vm.biometric(true) }) }
                }
            }
            SettingRow(Icons.Outlined.Timer, "Auto-lock", autoLockLabel(s.autoLockMinutes), onClick = { autoLockOpen = true })
            SettingRow(Icons.Outlined.Lock, "Change PIN", onClick = onSetPin)
        }

        SectionHeader("Appearance")
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Outlined.DarkMode, null, tint = Vault.colors.textSecondary)
            Spacer(Modifier.padding(start = Space.md))
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(selected = s.themeMode == m, onClick = { vm.theme(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.name) }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            SettingSwitchRow(Icons.Outlined.AutoAwesome, "Match wallpaper colours",
                "Recovered messages always stay amber", s.dynamicColor, vm::dynamic)
        }

        SectionHeader("Storage")
        SettingRow(Icons.Outlined.History, "Keep history", retentionLabel(s.retentionDays), onClick = { retentionOpen = true })
        SettingRow(Icons.Outlined.Upload, "Export encrypted backup", "Password-protected .nvault file",
            onClick = { exportLauncher.launch("notivault-$stamp.nvault") })
        SettingRow(Icons.Outlined.Download, "Restore from backup", "Merges with what's already here",
            onClick = { importLauncher.launch(arrayOf("*/*")) })
        SettingRow(Icons.Outlined.DeleteForever, "Erase vault", "Permanently delete every saved message", onClick = { wipeOpen = true })

        SectionHeader("Privacy")
        SettingRow(Icons.Outlined.WifiOff, "Offline by design",
            "NotiVault has no internet permission. Your messages are encrypted with a hardware-backed key and never leave this phone.")
        Text("NotiVault ${BuildConfigInfo.versionName(context)}", style = MaterialTheme.typography.labelSmall,
            color = Vault.colors.textSecondary, modifier = Modifier.padding(Space.md))
    }

    pwFor?.let { (mode, uri) ->
        PasswordDialog(
            title = if (mode == PwMode.Export) "Protect your backup" else "Unlock backup",
            confirmLabel = if (mode == PwMode.Export) "Export" else "Restore",
            requireConfirm = mode == PwMode.Export,
            onDismiss = { pwFor = null },
            onConfirm = { pw ->
                pwFor = null
                if (mode == PwMode.Export) vm.export(context, uri, pw) else vm.import(context, uri, pw)
            },
        )
    }
    if (retentionOpen) ChoiceDialog("Keep history", listOf(0, 30, 90, 365), s.retentionDays, ::retentionLabel,
        onDismiss = { retentionOpen = false }) { vm.retention(it); retentionOpen = false }
    if (autoLockOpen) ChoiceDialog("Auto-lock", listOf(0, 1, 5, 15), s.autoLockMinutes, ::autoLockLabel,
        onDismiss = { autoLockOpen = false }) { vm.autoLock(it); autoLockOpen = false }
    if (wipeOpen) AlertDialog(
        onDismissRequest = { wipeOpen = false },
        title = { Text("Erase everything?") },
        text = { Text("All saved messages and recovered receipts will be deleted. This can't be undone. Export a backup first if you might need them.") },
        confirmButton = { TextButton(onClick = { wipeOpen = false; vm.wipe(context) }) { Text("Erase", color = Vault.colors.danger) } },
        dismissButton = { TextButton(onClick = { wipeOpen = false }) { Text("Cancel") } },
    )
}

private fun retentionLabel(d: Int) = if (d == 0) "Forever" else "$d days"
private fun autoLockLabel(m: Int) = if (m == 0) "Immediately" else "After $m min in background"

@Composable
private fun ChoiceDialog(title: String, options: List<Int>, selected: Int, label: (Int) -> String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().height(Space.touch), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = o == selected, onClick = { onPick(o) })
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        })
}

@Composable
private fun PasswordDialog(title: String, confirmLabel: String, requireConfirm: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val ok = pw.length >= 8 && (!requireConfirm || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (requireConfirm) Text("You'll need this password to restore. It can't be recovered.",
                    style = MaterialTheme.typography.bodyMedium, color = Vault.colors.textSecondary)
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(pw, { pw = it }, label = { Text("Password (8+ characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (requireConfirm) {
                    Spacer(Modifier.height(Space.xs))
                    OutlinedTextField(pw2, { pw2 = it }, label = { Text("Confirm password") }, singleLine = true,
                        isError = pw2.isNotEmpty() && pw2 != pw,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = ok) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

