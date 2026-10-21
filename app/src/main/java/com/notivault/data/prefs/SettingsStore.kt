package com.notivault.data.prefs

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notivault.data.crypto.PasswordCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { System, Dark, Light }

data class Settings(
    val onboardingDone: Boolean = false,
    val retentionDays: Int = 0,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 1,
    val dynamicColor: Boolean = false,
    val recoveredAlerts: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val listenerConnected: Boolean = false,
    val lastHeartbeat: Long = 0,
    /** Only record apps with a messaging/social profile (WhatsApp, Telegram, Instagram…). */
    val messagingOnly: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val retention = intPreferencesKey("retention_days")
        val lock = booleanPreferencesKey("lock_enabled")
        val biometric = booleanPreferencesKey("biometric_enabled")
        val autoLock = intPreferencesKey("auto_lock_minutes")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val alerts = booleanPreferencesKey("recovered_alerts")
        val theme = stringPreferencesKey("theme_mode")
        val connected = booleanPreferencesKey("listener_connected")
        val heartbeat = longPreferencesKey("last_heartbeat")
        val messagingOnly = booleanPreferencesKey("messaging_only")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[K.onboarding] ?: false,
        retentionDays = this[K.retention] ?: 0,
        lockEnabled = this[K.lock] ?: false,
        biometricEnabled = this[K.biometric] ?: false,
        autoLockMinutes = this[K.autoLock] ?: 1,
        dynamicColor = this[K.dynamic] ?: false,
        recoveredAlerts = this[K.alerts] ?: true,
        themeMode = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
        listenerConnected = this[K.connected] ?: false,
        lastHeartbeat = this[K.heartbeat] ?: 0,
        messagingOnly = this[K.messagingOnly] ?: false,
    )

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboarding] = true }
    suspend fun setRetentionDays(days: Int) = context.dataStore.edit { it[K.retention] = days }
    suspend fun setBiometric(enabled: Boolean) = context.dataStore.edit { it[K.biometric] = enabled }
    suspend fun setAutoLockMinutes(m: Int) = context.dataStore.edit { it[K.autoLock] = m }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[K.dynamic] = enabled }
    suspend fun setRecoveredAlerts(enabled: Boolean) = context.dataStore.edit { it[K.alerts] = enabled }
    suspend fun setMessagingOnly(on: Boolean) = context.dataStore.edit { it[K.messagingOnly] = on }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }

    suspend fun setListenerConnected(connected: Boolean) = context.dataStore.edit {
        it[K.connected] = connected
        it[K.heartbeat] = System.currentTimeMillis()
    }

    suspend fun heartbeat() = context.dataStore.edit { it[K.heartbeat] = System.currentTimeMillis() }

    suspend fun setPin(pin: String) {
        val salt = PasswordCrypto.randomBytes(16)
        val hash = PasswordCrypto.hashPin(pin, salt)
        context.dataStore.edit {
            it[K.pinSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[K.pinHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[K.lock] = true
        }
    }

    suspend fun disableLock() = context.dataStore.edit {
        it.remove(K.pinHash); it.remove(K.pinSalt)
        it[K.lock] = false
        it[K.biometric] = false
    }

    suspend fun verifyPin(pin: String): Boolean {
        val p = context.dataStore.data.first()
        val salt = p[K.pinSalt] ?: return false
        val hash = p[K.pinHash] ?: return false
        return PasswordCrypto.verifyPin(pin, Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP))
    }
}
